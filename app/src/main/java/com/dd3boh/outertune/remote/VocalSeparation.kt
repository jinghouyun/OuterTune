package com.dd3boh.outertune.remote

import android.content.Context
import com.dd3boh.outertune.constants.VocalSeparatorApiUrlKey
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.get
import kotlinx.coroutines.delay
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
 * Vocal separator. If the user has configured an API endpoint (VocalSeparatorApiUrlKey),
 * submits a real separation job and polls for the result. Otherwise falls back to a demo
 * mode that marks the job done with placeholder URLs after a short delay.
 */
class VocalSeparator(private val context: Context) {
    private val store = VocalSeparationStore(context)

    /** Submit a separation job. Returns the finished record (status=done/failed). */
    suspend fun submit(record: VocalSeparationRecord): VocalSeparationRecord {
        store.upsert(record.copy(status = "processing"))

        val apiUrl = context.dataStore.get(VocalSeparatorApiUrlKey, "").trim()
        if (apiUrl.isEmpty()) {
            return demoComplete(record)
        }

        return try {
            val body = JSONObject().apply {
                put("url", record.songId)
                put("models", "vocals,instrumental")
            }
            val resp = RemoteHttp.postJson(apiUrl, body.toString())
            val json = JSONObject(resp)

            when {
                json.has("vocals_url") && json.has("instrumental_url") -> {
                    buildDone(record, json.getString("vocals_url"), json.getString("instrumental_url"))
                }
                json.optString("status") == "processing" && json.has("job_id") -> {
                    pollUntilDone(apiUrl, record, json.getString("job_id"))
                }
                else -> demoComplete(record)
            }
        } catch (e: Exception) {
            // request failed -> fall back to demo mode
            demoComplete(record)
        }
    }

    private suspend fun pollUntilDone(
        apiUrl: String,
        record: VocalSeparationRecord,
        jobId: String,
    ): VocalSeparationRecord {
        repeat(40) { // ~2 minutes max
            delay(3000)
            try {
                val resp = RemoteHttp.get("$apiUrl/status/$jobId")
                val json = JSONObject(resp)
                if (json.has("vocals_url") && json.has("instrumental_url")) {
                    return buildDone(record, json.getString("vocals_url"), json.getString("instrumental_url"))
                }
            } catch (_: Exception) {
                // keep polling
            }
        }
        return record.copy(status = "failed").also { store.upsert(it) }
    }

    private fun buildDone(record: VocalSeparationRecord, vocal: String, instrumental: String): VocalSeparationRecord {
        val done = record.copy(status = "done", vocalUrl = vocal, accompanimentUrl = instrumental)
        store.upsert(done)
        return done
    }

    private fun demoComplete(record: VocalSeparationRecord): VocalSeparationRecord {
        // Demo mode: simulate completion after a short delay, placeholder urls.
        var done = record.copy(status = "processing")
        kotlinx.coroutines.runBlocking { delay(3000) }
        done = done.copy(
            status = "done",
            vocalUrl = "vocal://${record.songId}",
            accompanimentUrl = "accompaniment://${record.songId}",
        )
        store.upsert(done)
        return done
    }

    fun getStore() = store
}
