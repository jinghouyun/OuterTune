package com.dd3boh.outertune.remote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A user-defined music source (LX Music compatible API).
 */
data class CustomSource(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val searchPath: String = "/search",
    val urlPath: String = "/url",
    val lyricPath: String = "/lyric",
    val picPath: String = "/pic",
    val separatePath: String = "/separate",
    val enabled: Boolean = true,
    /** True when this source is backed by a user JS script (stored on disk, see JsScriptStore). */
    val isJs: Boolean = false,
    /** Optional remote URL the JS script was fetched from (for future refresh). */
    val scriptUrl: String? = null,
    /** Parsed @version / @author / @homepage from the script header block comment. */
    val jsVersion: String? = null,
    val jsAuthor: String? = null,
    val jsHomepage: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("baseUrl", baseUrl)
        put("searchPath", searchPath)
        put("urlPath", urlPath)
        put("lyricPath", lyricPath)
        put("picPath", picPath)
        put("separatePath", separatePath)
        put("enabled", enabled)
        put("isJs", isJs)
        scriptUrl?.let { put("scriptUrl", it) }
        jsVersion?.let { put("jsVersion", it) }
        jsAuthor?.let { put("jsAuthor", it) }
        jsHomepage?.let { put("jsHomepage", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): CustomSource = CustomSource(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", ""),
            baseUrl = o.optString("baseUrl", ""),
            searchPath = o.optString("searchPath", "/search"),
            urlPath = o.optString("urlPath", "/url"),
            lyricPath = o.optString("lyricPath", "/lyric"),
            picPath = o.optString("picPath", "/pic"),
            separatePath = o.optString("separatePath", "/separate"),
            enabled = o.optBoolean("enabled", true),
            isJs = o.optBoolean("isJs", false),
            scriptUrl = if (o.isNull("scriptUrl")) null else o.optString("scriptUrl", "").ifBlank { null },
            jsVersion = if (o.isNull("jsVersion")) null else o.optString("jsVersion", "").ifBlank { null },
            jsAuthor = if (o.isNull("jsAuthor")) null else o.optString("jsAuthor", "").ifBlank { null },
            jsHomepage = if (o.isNull("jsHomepage")) null else o.optString("jsHomepage", "").ifBlank { null },
        )
    }
}

/**
 * Stores custom sources in SharedPreferences as a JSON array.
 */
class CustomSourceStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("custom_sources", Context.MODE_PRIVATE)

    fun getAll(): List<CustomSource> {
        val raw = prefs.getString("sources", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { CustomSource.fromJson(arr.getJSONObject(it)) }
    }

    fun getEnabled(): List<CustomSource> = getAll().filter { it.enabled }

    fun get(id: String): CustomSource? = getAll().firstOrNull { it.id == id }

    fun add(source: CustomSource) {
        val list = getAll().toMutableList()
        list.removeAll { it.id == source.id }
        list.add(source)
        save(list)
    }

    fun update(source: CustomSource) = add(source)

    fun remove(id: String) {
        if (activeJsId() == id) setActiveJsId(null)
        save(getAll().filterNot { it.id == id })
    }

    /** id of the single active JS enhancement script (at most one, per lx-music-mobile). */
    fun activeJsId(): String? =
        prefs.getString("active_js_id", null)?.takeIf { it.isNotBlank() }

    fun setActiveJsId(id: String?) {
        prefs.edit().apply {
            if (id.isNullOrBlank()) remove("active_js_id") else putString("active_js_id", id)
        }.apply()
    }

    private fun save(list: List<CustomSource>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("sources", arr.toString()).apply()
    }
}
