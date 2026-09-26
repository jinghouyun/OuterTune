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
    val enabled: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("baseUrl", baseUrl)
        put("searchPath", searchPath)
        put("urlPath", urlPath)
        put("lyricPath", lyricPath)
        put("picPath", picPath)
        put("enabled", enabled)
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
            enabled = o.optBoolean("enabled", true),
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
        save(getAll().filterNot { it.id == id })
    }

    private fun save(list: List<CustomSource>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("sources", arr.toString()).apply()
    }
}
