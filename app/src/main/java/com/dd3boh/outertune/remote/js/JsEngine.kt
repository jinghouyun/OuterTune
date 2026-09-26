package com.dd3boh.outertune.remote.js

import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import com.dd3boh.outertune.remote.RemoteHttp
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Static metadata parsed from a script's leading `/* @name ... */` block comment. */
data class ScriptInfo(
    val name: String,
    val version: String = "",
    val author: String = "",
    val homepage: String = "",
    val description: String = "",
)

/**
 * Parser for the leading block comment every lx-music user script MUST start with.
 * The header uses @name / @version / @author / @homepage / @description tags.
 * Returns null when the file does not begin with a block comment (mirrors lx-music-mobile's
 * addUserApi rejection).
 */
object ScriptHeader {
    private val blockComment = Regex("""^/\*[\s\S]+?\*/""")
    private val tagLine = Regex("""(?m)^\s?\*\s?@(\w+)\s+(.+)$""")

    fun parse(script: String): ScriptInfo? {
        val trimmed = script.trimStart()
        val match = blockComment.find(trimmed) ?: return null
        val fields = HashMap<String, String>()
        tagLine.findAll(match.value).forEach {
            fields[it.groupValues[1].lowercase()] = it.groupValues[2].trim()
        }
        if (fields.isEmpty()) return null
        return ScriptInfo(
            name = fields["name"]?.take(24)?.ifBlank { null }
                ?: "user_api_${System.currentTimeMillis()}",
            version = fields["version"]?.take(36) ?: "",
            author = fields["author"]?.take(56) ?: "",
            homepage = fields["homepage"]?.take(1024) ?: "",
            description = fields["description"]?.take(36) ?: "",
        )
    }
}

/**
 * A Rhino sandbox for a single lx-music-mobile compatible user script.
 *
 * Architecture (aligned with lx-music-mobile's `user-api-preload.js`):
 *  1. We enter an interpreted [Context] (optimizationLevel = -1, required on ART).
 *  2. We inject native bridges (`__lx_request`, `__lx_on`, `__lx_send`, crypto/buffer).
 *  3. We evaluate [PRELOAD_JS] which builds `globalThis.lx = { EVENT_NAMES, request, on, send,
 *     utils, env, version, currentScriptInfo }`.
 *  4. We evaluate the user script. The script registers a request handler via `on(EVENT_NAMES.request,
 *     ...)` and reports its declared capabilities via `send(EVENT_NAMES.inited, {status, sources})`.
 *
 * `lx.request(url, options, callback)` is executed **synchronously** on OkHttp, then invokes the
 * JS callback immediately. Because Rhino has no event loop, this guarantees the Promises scripts
 * return are already settled by the time we call them; we then drain microtasks with
 * [Context.processMicrotasks] to unwrap the result.
 */
class LxScriptEngine(private val info: ScriptInfo) {

    private var scope: ScriptableObject? = null
    private val lock = Any()

    /** sourceKey (kw/kg/tx/wy/mg) -> set of actions the script declared it can perform. */
    @Volatile
    var sources: Map<String, Set<String>> = emptyMap()
        private set

    /** Human-readable reason the script failed to initialize (null on success). */
    @Volatile
    var initError: String? = null
        private set

    private var requestHandler: Function? = null

    val isLoaded: Boolean get() = scope != null

    /** Compile and run preload + user script. Throws on fatal load errors. */
    fun load(script: String) = synchronized(lock) {
        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1 // interpreted — required on Android/ART
            cx.languageVersion = Context.VERSION_ES6
            cx.instructionObserverThreshold = 1_000_000
            val top = cx.initStandardObjects()

            ScriptableObject.putProperty(top, "console", makeConsole(top))
            ScriptableObject.putProperty(top, "__lx_request", LxRequestBridge())
            ScriptableObject.putProperty(top, "__lx_on", OnBridge())
            ScriptableObject.putProperty(top, "__lx_send", SendBridge())
            ScriptableObject.putProperty(top, "__lx_md5", Md5Bridge())
            ScriptableObject.putProperty(top, "__lx_randomBytes", RandomBytesBridge())
            ScriptableObject.putProperty(top, "__lx_aes", AesBridge())
            ScriptableObject.putProperty(top, "__lx_rsa", RsaBridge())
            ScriptableObject.putProperty(top, "__lx_bufFrom", BufferFromBridge())
            ScriptableObject.putProperty(top, "__lx_bufToString", BufferToStringBridge())
            ScriptableObject.putProperty(top, "__lx_scriptInfo", scriptInfoObject(top, script))

            cx.evaluateString(top, PRELOAD_JS, "<user-api-preload>", 1, null)
            cx.evaluateString(top, script, "<user-script>", 1, null)
            scope = top
        } finally {
            Context.exit()
        }
    }

    fun close() = synchronized(lock) {
        scope = null
        requestHandler = null
        sources = emptyMap()
    }

    // ------------------------------------------------------------------ init handling

    private inner class OnBridge : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
            val eventName = Context.toString(args.getOrNull(0))
            val handler = args.getOrNull(1)
            if (eventName == "request" && handler is Function) requestHandler = handler
            return null
        }
    }

    private inner class SendBridge : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
            val eventName = Context.toString(args.getOrNull(0))
            val data = args.getOrNull(1) as? Scriptable
            if (eventName == "inited") handleInited(data)
            // updateAlert / others: ignored on mobile
            return null
        }
    }

    private fun handleInited(data: Scriptable?) {
        if (data == null) {
            initError = "inited event carried no data"
            return
        }
        val status = ScriptableObject.getProperty(data, "status")
        if (status == null || status == Undefined.instance || !Context.toBoolean(status)) {
            initError = "script reported status=false"
            return
        }
        val srcObj = ScriptableObject.getProperty(data, "sources") as? Scriptable
        if (srcObj == null) {
            initError = "inited event missing sources"
            return
        }
        // OuterTune relaxation: built-in sources may override url/lyric/pic (lx-music-mobile only
        // ever calls musicUrl for built-ins, but allowing lyric/pic makes scripts useful here).
        val allowedActions = setOf("musicUrl", "lyric", "pic")
        val whitelist = setOf("kw", "kg", "tx", "wy", "mg", "local")
        val out = HashMap<String, MutableSet<String>>()
        for (id in srcObj.ids) {
            val key = Context.toString(id)
            if (key !in whitelist) continue
            val entry = srcObj.get(Context.toString(id), srcObj) as? Scriptable ?: continue
            if (Context.toString(entry.get("type", entry)) != "music") continue
            val actions = (entry.get("actions", entry) as? NativeArray)
                ?.let { arr -> (0 until arr.length.toInt()).map { Context.toString(arr.get(it, arr)) } }
                ?: emptyList()
            val valid = actions.filter { it in allowedActions }.toMutableSet()
            if (valid.isNotEmpty()) out[key] = valid
        }
        if (out.isEmpty()) initError = "script declared no usable music sources"
        else sources = out
    }

    // ------------------------------------------------------------------ action dispatch

    /**
     * Trigger the script's `request` event with `{source, action, info}` and return the unwrapped
     * result (plain Kotlin types). Throws on script errors / rejections.
     */
    fun callAction(source: String, action: String, info: Map<String, Any?>): Any? = synchronized(lock) {
        val handler = requestHandler ?: throw IllegalStateException("script has no request handler")
        val top = scope ?: throw IllegalStateException("script not loaded")
        val cx = Context.enter()
        try {
            val arg = NativeObject()
            arg.defineProperty("source", source, ScriptableObject.DONTENUM)
            arg.defineProperty("action", action, ScriptableObject.DONTENUM)
            arg.defineProperty("info", mapToJs(cx, top, info), ScriptableObject.DONTENUM)
            val ret = handler.call(cx, top, top, arrayOf<Any?>(arg))
            val settled = settle(cx, top, ret)
            if (settled.error != null) {
                val msg = when (val e = settled.error) {
                    is NativeObject -> e.get("message", e)?.let { Context.toString(it) }
                    else -> Context.toString(e)
                }
                throw RuntimeException(msg ?: "script rejected $action")
            }
            return jsToAny(settled.value)
        } finally {
            Context.exit()
        }
    }

    private class Settled(val value: Any?, val error: Any?)

    /** If [ret] is a thenable, attach capturing callbacks and drain microtasks synchronously. */
    private fun settle(cx: Context, scope: Scriptable, ret: Any?): Settled {
        if (ret == null || ret == Undefined.instance) return Settled(null, null)
        val thenFn = (ret as? Scriptable)?.let { ScriptableObject.getProperty(it, "then") }
        if (thenFn !is Function) return Settled(ret, null)
        val box = arrayOfNulls<Any?>(2) // [value, error]
        val onFulfilled = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                box[0] = args.getOrNull(0); return null
            }
        }
        val onRejected = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                box[1] = args.getOrNull(0); return null
            }
        }
        try {
            ScriptableObject.callMethod(cx, ret, "then", arrayOf<Any?>(onFulfilled, onRejected))
        } catch (e: Exception) {
            return Settled(null, e.message)
        }
        cx.processMicrotasks()
        return Settled(box[0], box[1])
    }

    // ------------------------------------------------------------------ lx.request bridge

    private inner class LxRequestBridge : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
            val url = Context.toString(args.getOrNull(0))
            val opts = args.getOrNull(1) as? Scriptable
            val callback = args.getOrNull(2) as? Function

            val method = opts?.let { it.get("method", opts) }
                ?.takeIf { it != Undefined.instance }
                ?.let { Context.toString(it).uppercase() } ?: "GET"
            val timeoutMs = opts?.let { it.get("timeout", opts) }
                ?.let { Context.toNumber(it).toInt() } ?: 13_000
            val binary = opts?.let { Context.toBoolean(it.get("binary", opts)) } == true
            val headers = readHeaders(opts).toMutableMap()
            if (headers.none { it.key.equals("User-Agent", ignoreCase = true) }) {
                headers["User-Agent"] =
                    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36"
            }
            val bodyVal = opts?.let { it.get("body", opts) }

            val builder = Request.Builder().url(url)
            headers.forEach { (k, v) -> builder.header(k, v) }
            when (method) {
                "POST", "PUT" -> {
                    val bodyStr = bodyVal?.takeIf { it != Undefined.instance }?.let { Context.toString(it) } ?: ""
                    val contentType = "application/x-www-form-urlencoded".toMediaType()
                    val rb = bodyStr.toRequestBody(contentType)
                    if (method == "POST") builder.post(rb) else builder.put(rb)
                }
                "DELETE" -> builder.delete()
                else -> builder.get()
            }

            val client = RemoteHttp.client.newBuilder()
                .connectTimeout(timeoutMs.coerceIn(1_000, 60_000).toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs.coerceIn(1_000, 60_000).toLong(), TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs.coerceIn(1_000, 60_000).toLong(), TimeUnit.MILLISECONDS)
                .build()

            try {
                client.newCall(builder.build()).execute().use { resp ->
                    val bytes = resp.body?.bytes() ?: ByteArray(0)
                    val bodyText = String(bytes, Charsets.UTF_8)
                    val respObj = NativeObject()
                    respObj.defineProperty("statusCode", resp.code, ScriptableObject.DONTENUM)
                    respObj.defineProperty("statusMessage", resp.message ?: "", ScriptableObject.DONTENUM)
                    val hjs = NativeObject()
                    resp.headers.forEach { (k, v) ->
                        if (hjs[k] == null) hjs[k] = v else hjs[k] = "${hjs[k]}, $v"
                    }
                    respObj.defineProperty("headers", hjs, ScriptableObject.DONTENUM)
                    respObj.defineProperty("body", bodyText, ScriptableObject.DONTENUM)
                    val cbBody = if (binary) Base64.encodeToString(bytes, Base64.NO_WRAP) else bodyText
                    callback?.call(cx, scope, scope, arrayOf<Any?>(null, respObj, cbBody))
                }
            } catch (e: Exception) {
                callback?.call(cx, scope, scope, arrayOf<Any?>(e.message ?: "network error", null, null))
            }
            return null
        }

        private fun readHeaders(opts: Scriptable?): Map<String, String> {
            opts ?: return emptyMap()
            val h = opts.get("headers", opts) as? Scriptable ?: return emptyMap()
            val out = LinkedHashMap<String, String>()
            for (id in h.ids) {
                val key = Context.toString(id)
                val v = h.get(key, h)
                if (v != null && v != Undefined.instance) out[key] = Context.toString(v)
            }
            return out
        }
    }

    // ------------------------------------------------------------------ crypto / buffer

    private inner class Md5Bridge : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
            val s = Context.toString(args.getOrNull(0))
            val digest = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    private inner class RandomBytesBridge : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
            val n = Context.toNumber(args.getOrNull(0)).toInt().coerceIn(0, 65536)
            val bytes = ByteArray(n)
            SecureRandom().nextBytes(bytes)
            return bytesToJsArray(bytes)
        }
    }

    private inner class AesBridge : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
            return runCatching {
                val data = toBytes(args.getOrNull(0))
                val mode = Context.toString(args.getOrNull(1)).lowercase()
                val key = toBytes(args.getOrNull(2))
                val iv = toBytes(args.getOrNull(3))
                val cipher = Cipher.getInstance(if ("ecb" in mode) "AES/ECB/PKCS5Padding" else "AES/CBC/PKCS5Padding")
                val ks = SecretKeySpec(key.copyOf(16), "AES")
                if ("ecb" in mode) cipher.init(Cipher.ENCRYPT_MODE, ks)
                else cipher.init(Cipher.ENCRYPT_MODE, ks, IvParameterSpec(iv.copyOf(16)))
                cipher.doFinal(data)
            }.getOrNull()?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        }
    }

    private inner class RsaBridge : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
            // Best-effort NoPadding RSA as in lx-music. Returns null on any failure.
            return runCatching {
                val data = toBytes(args.getOrNull(0))
                val pubPem = Context.toString(args.getOrNull(1))
                val cert = java.security.cert.CertificateFactory.getInstance("X.509")
                    .generateCertificate(pubPem.byteInputStream(Charsets.UTF_8))
                val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, cert.publicKey)
                cipher.doFinal(data)
            }.getOrNull()?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        }
    }

    private inner class BufferFromBridge : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
            val input = args.getOrNull(0) ?: return bytesToJsArray(ByteArray(0))
            val enc = args.getOrNull(1)?.let { Context.toString(it).lowercase() } ?: "utf8"
            return when (enc) {
                "base64" -> bytesToJsArray(Base64.decode(Context.toString(input), Base64.DEFAULT))
                "hex" -> bytesToJsArray(hexToBytes(Context.toString(input)))
                else -> bytesToJsArray(Context.toString(input).toByteArray(Charsets.UTF_8))
            }
        }
    }

    private inner class BufferToStringBridge : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
            val buf = toBytes(args.getOrNull(0))
            val fmt = args.getOrNull(1)?.let { Context.toString(it).lowercase() } ?: "utf8"
            return when (fmt) {
                "base64" -> Base64.encodeToString(buf, Base64.NO_WRAP)
                "hex" -> buf.joinToString("") { "%02x".format(it) }
                else -> String(buf, Charsets.UTF_8)
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun scriptInfoObject(scope: Scriptable, rawScript: String): Scriptable {
        val o = NativeObject()
        o.defineProperty("name", info.name, ScriptableObject.DONTENUM)
        o.defineProperty("version", info.version, ScriptableObject.DONTENUM)
        o.defineProperty("author", info.author, ScriptableObject.DONTENUM)
        o.defineProperty("homepage", info.homepage, ScriptableObject.DONTENUM)
        o.defineProperty("description", info.description, ScriptableObject.DONTENUM)
        o.defineProperty("rawScript", rawScript, ScriptableObject.DONTENUM)
        return o
    }

    private fun makeConsole(scope: Scriptable): Scriptable {
        val c = NativeObject()
        c.defineProperty("log", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                Log.i("LxScript", args.joinToString(" ") { Context.toString(it) }); return null
            }
        }, ScriptableObject.DONTENUM)
        c.defineProperty("error", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                Log.e("LxScript", args.joinToString(" ") { Context.toString(it) }); return null
            }
        }, ScriptableObject.DONTENUM)
        return c
    }

    private fun bytesToJsArray(bytes: ByteArray): NativeArray {
        val arr = NativeArray(bytes.size.toLong())
        bytes.forEachIndexed { i, b -> arr.put(i, arr, b.toInt() and 0xff) }
        return arr
    }

    private fun toBytes(v: Any?): ByteArray = when (v) {
        null -> ByteArray(0)
        is String -> v.toByteArray(Charsets.UTF_8)
        is Number -> byteArrayOf(v.toInt().toByte())
        is NativeArray -> (0 until v.length.toInt()).map {
            (Context.toNumber(v.get(it, v)).toInt() and 0xff).toByte()
        }.toByteArray()
        else -> Context.toString(v).toByteArray(Charsets.UTF_8)
    }

    private fun hexToBytes(s: String): ByteArray {
        val clean = s.trim().removePrefix("0x")
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) out[i] = ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16)).toByte()
        return out
    }

    private fun mapToJs(cx: Context, scope: Scriptable, v: Any?): Any? = when (v) {
        null -> null
        is Map<*, *> -> NativeObject().apply {
            v.forEach { (k, val_) -> this.defineProperty(k.toString(), mapToJs(cx, scope, val_), ScriptableObject.DONTENUM) }
        }
        is List<*> -> NativeArray(v.size.toLong()).apply {
            v.forEachIndexed { i, e -> this.put(i, this, mapToJs(cx, scope, e)) }
        }
        is Boolean -> v
        is Number -> v.toDouble()
        else -> v.toString()
    }

    companion object {
        @JvmStatic
        fun jsToAny(v: Any?): Any? = when (v) {
            null, Undefined.instance -> null
            is NativeArray -> (0 until v.length.toInt()).map { jsToAny(v.get(it, v)) }
            is Scriptable -> {
                val m = LinkedHashMap<String, Any?>()
                for (id in v.ids) {
                    val key = Context.toString(id)
                    m[key] = jsToAny(v.get(key, v))
                }
                m
            }
            is Number -> v.toDouble().let { if (it == it.toLong().toDouble()) it.toLong() else it }
            else -> v
        }

        /** The preload that installs `globalThis.lx` around the injected native bridges. */
        private const val PRELOAD_JS = """
            globalThis.lx = (function () {
                var EVENT_NAMES = { request: 'request', inited: 'inited', updateAlert: 'updateAlert' };
                return {
                    EVENT_NAMES: EVENT_NAMES,
                    env: 'mobile',
                    version: '2.0.0',
                    request: function (url, options, callback) { __lx_request(url, options, callback); },
                    on: function (eventName, handler) {
                        if (eventName === EVENT_NAMES.request) { __lx_on(eventName, handler); }
                    },
                    send: function (eventName, data) { __lx_send(eventName, data); },
                    utils: {
                        crypto: {
                            md5: function (s) { return __lx_md5(s); },
                            randomBytes: function (n) { return __lx_randomBytes(n); },
                            aesEncrypt: function (buf, mode, key, iv) { return __lx_aes(buf, mode, key, iv); },
                            rsaEncrypt: function (buf, publicKey) { return __lx_rsa(buf, publicKey); }
                        },
                        buffer: {
                            from: function (input, enc) { return __lx_bufFrom(input, enc); },
                            bufToString: function (buf, fmt) { return __lx_bufToString(buf, fmt); }
                        }
                    },
                    currentScriptInfo: __lx_scriptInfo
                };
            })();
        """
    }
}
