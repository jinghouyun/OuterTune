package com.dd3boh.outertune.remote.js

import android.content.Context
import android.util.Log
import com.dd3boh.outertune.remote.CustomSourceStore
import com.dd3boh.outertune.remote.RemoteLyric

/**
 * Owns the single **active** lx-music user-script engine (at most one script active at a time,
 * mirroring lx-music-mobile's `setUserApi`).
 *
 * The active script acts as an *enhancement layer* over the built-in sources: it does not provide
 * search. When playback needs a musicUrl / lyric / pic for a built-in source (kw/kg/tx/wy/mg) and
 * the active script declared support for that source+action, we ask the script first.
 */
class LxScriptManager(private val context: Context) {

    private val store = CustomSourceStore(context)
    private val jsStore = JsScriptStore(context)

    @Volatile
    private var engine: LxScriptEngine? = null

    /** id of the currently active CustomSource (an isJs entry), or null. */
    @Volatile
    var activeId: String? = null
        private set

    /**
     * Reload the engine to match [CustomSourceStore.activeJsId]. Called lazily before any script
     * use (so settings changes take effect on next playback without cross-component wiring), and
     * explicitly from the settings screen after activation.
     */
    fun reload() {
        val want = store.activeJsId()
        if (want == activeId && engine != null) return
        release()
        if (want.isNullOrBlank()) return
        val src = store.get(want)?.takeIf { it.isJs } ?: return
        val script = jsStore.read(want) ?: run { release(); return }
        runCatching {
            val header = ScriptHeader.parse(script)
            val eng = LxScriptEngine(
                ScriptInfo(
                    name = header?.name ?: src.name,
                    version = header?.version ?: "",
                    author = header?.author ?: "",
                    homepage = header?.homepage ?: "",
                    description = header?.description ?: "",
                )
            )
            eng.load(script)
            engine = eng
            activeId = want
            Log.i("LxScript", "active script loaded: ${src.name}, sources=${eng.sources}")
        }.onFailure {
            Log.e("LxScript", "failed to load script ${src.name}", it)
            release()
        }
    }

    fun release() {
        runCatching { engine?.close() }
        engine = null
        activeId = null
    }

    fun supports(source: String, action: String): Boolean =
        engine?.sources?.get(source)?.contains(action) == true

    /** True when a script is loaded and usable (even if it declared nothing). */
    val isActive: Boolean get() = engine != null

    // ------------------------------------------------------------------ action attempts

    fun tryMusicUrl(source: String, quality: String, musicInfo: Map<String, Any?>): String? {
        val eng = engine ?: return null
        if (!supports(source, "musicUrl")) return null
        return runCatching {
            val info = mapOf("type" to quality, "musicInfo" to musicInfo)
            val r = eng.callAction(source, "musicUrl", info)
            (r as? String)?.takeIf { it.isNotBlank() && it.startsWith("http") && it.length <= 2048 }
        }.onFailure { Log.e("LxScript", "musicUrl failed for $source", it) }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    fun tryLyric(source: String, musicInfo: Map<String, Any?>): RemoteLyric? {
        val eng = engine ?: return null
        if (!supports(source, "lyric")) return null
        return runCatching {
            val r = eng.callAction(source, "lyric", mapOf("musicInfo" to musicInfo))
            val m = r as? Map<String, Any?> ?: return@runCatching null
            val lyric = m["lyric"]?.toString()?.takeIf { it.isNotBlank() && it.length <= 51200 }
                ?: return@runCatching null
            RemoteLyric(
                lyric = lyric,
                translated = m["tlyric"]?.toString()?.takeIf { it.isNotBlank() && it.length <= 5120 },
                roman = m["rlyric"]?.toString()?.takeIf { it.isNotBlank() && it.length <= 5120 },
            )
        }.onFailure { Log.e("LxScript", "lyric failed for $source", it) }.getOrNull()
    }

    fun tryPic(source: String, musicInfo: Map<String, Any?>): String? {
        val eng = engine ?: return null
        if (!supports(source, "pic")) return null
        return runCatching {
            val r = eng.callAction(source, "pic", mapOf("musicInfo" to musicInfo))
            (r as? String)?.takeIf { it.isNotBlank() && it.startsWith("http") && it.length <= 2048 }
        }.onFailure { Log.e("LxScript", "pic failed for $source", it) }.getOrNull()
    }
}
