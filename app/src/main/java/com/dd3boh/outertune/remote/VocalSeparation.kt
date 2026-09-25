package com.dd3boh.outertune.remote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A vocal separation record. Status: processing / done / failed.
 * vocalUrl/accompanimentUrl are file paths or URLs; empty while processing.
 */
data class VocalSeparationRecord(
    val songId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val status: String = "processing", // processing / done / failed
    val vocalUrl: String = "",
    val accompanimentUrl: String = "",
    val createTime: Long = System.currentTimeMillis(),
)

/**
 * Stores vocal separation records in SharedPreferences as JSON.
 * Avoids Room migration for this optional feature.
 */
class VocalSeparationStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("vocal_separation", Context.MODE_PRIVATE)

    fun getAll(): List<VocalSeparationRecord> {
        val raw = prefs.getString("records", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            VocalSeparationRecord(
                songId = o.getString("songId"),
                title = o.getString("title"),
                artist = o.optString("artist"),
                thumbnailUrl = o.optString("thumbnailUrl").ifBlank { null },
                status = o.optString("status", "processing"),
                vocalUrl = o.optString("vocalUrl", ""),
                accompanimentUrl = o.optString("accompanimentUrl", ""),
                createTime = o.optLong("createTime", 0),
            )
        }.sortedByDescending { it.createTime }
    }

    fun upsert(record: VocalSeparationRecord) {
        val list = getAll().filterNot { it.songId == record.songId }.toMutableList()
        list.add(record)
        save(list)
    }

    fun delete(songId: String) {
        save(getAll().filterNot { it.songId == songId })
    }

    fun get(songId: String): VocalSeparationRecord? = getAll().find { it.songId == songId }

    private fun save(list: List<VocalSeparationRecord>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().apply {
                put("songId", r.songId)
                put("title", r.title)
                put("artist", r.artist)
                put("thumbnailUrl", r.thumbnailUrl ?: "")
                put("status", r.status)
                put("vocalUrl", r.vocalUrl)
                put("accompanimentUrl", r.accompanimentUrl)
                put("createTime", r.createTime)
            })
        }
        prefs.edit().putString("records", arr.toString()).apply()
    }
}

/**
 * Demo vocal separator. Submits a "job" and after a short delay marks it done
 * with placeholder URLs. Users can configure a real API endpoint in settings.
 */
class VocalSeparator(private val context: Context) {
    private val store = VocalSeparationStore(context)

    /** Submit a separation job. Returns the record immediately (status=processing). */
    suspend fun submit(record: VocalSeparationRecord): VocalSeparationRecord {
        store.upsert(record.copy(status = "processing"))
        // Demo mode: simulate completion after a delay.
        kotlinx.coroutines.delay(3000)
        val done = record.copy(
            status = "done",
            vocalUrl = "vocal://${record.songId}",
            accompanimentUrl = "accompaniment://${record.songId}",
        )
        store.upsert(done)
        return done
    }

    fun getStore() = store
}
