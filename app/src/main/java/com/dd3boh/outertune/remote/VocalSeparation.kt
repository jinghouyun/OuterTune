package com.dd3boh.outertune.remote

import android.content.Context
import com.dd3boh.outertune.remote.sources.CustomSourceImpl
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/**
 * A vocal separation record. Status: processing / done / failed.
 * vocalUrl/accompanimentUrl are URLs; empty while processing.
 * note carries a human-readable failure reason.
 */
data class VocalSeparationRecord(
    val songId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val status: String = "processing", // processing / done / failed
    val vocalUrl: String = "",
    val accompanimentUrl: String = "",
    val note: String = "",
    val createTime: Long = System.currentTimeMillis(),
)

/**
 * Stores vocal separation records in SharedPreferences as JSON.
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
                note = o.optString("note", ""),
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
                put("note", r.note)
                put("createTime", r.createTime)
            })
        }
        prefs.edit().putString("records", arr.toString()).apply()
    }
}

/**
 * Vocal separator that follows the song's own source. Built-in sources (wy/mg/tx/kg/kw)
 * do not support separation; only custom sources expose a /separate endpoint.
 */
class VocalSeparator(
    @Suppress("UNUSED_PARAMETER") private val context: Context,
    private val repository: RemoteMusicRepository,
) {
    private val store = VocalSeparationStore(context)

    /** Submit a separation job for the song identified by [record.songId] (a mediaId). */
    suspend fun submit(record: VocalSeparationRecord): VocalSeparationRecord {
        store.upsert(record.copy(status = "processing", note = ""))

        // 1. resolve which source this song belongs to
        val source = repository.parseSourceId(record.songId)?.let { repository.sourceByIdPublic(it) }
        if (source !is CustomSourceImpl || !source.supportsSeparation) {
            return fail(record, "该音源不支持人声分离，请使用自定义源")
        }

        // 2. resolve the real playable stream url
        val streamUrl = runCatching { repository.resolveStreamUrl(record.songId) }.getOrNull()
        if (streamUrl.isNullOrBlank()) {
            return fail(record, "无法获取播放地址，请先播放该歌曲")
        }

        // 3. submit the separation job to the custom source
        return try {
            val body = JSONObject().apply {
                put("url", streamUrl)
                put("models", "vocals,instrumental")
            }
            val resp = RemoteHttp.postJson(source.separateEndpoint(), body.toString())
            val json = JSONObject(resp)
            when {
                json.has("vocals_url") && json.has("instrumental_url") ->
                    buildDone(record, json.getString("vocals_url"), json.getString("instrumental_url"))
                json.optString("status") == "processing" && json.has("job_id") ->
                    pollUntilDone(source, record, json.getString("job_id"))
                else -> fail(record, "分离接口返回异常")
            }
        } catch (e: Exception) {
            fail(record, "分离请求失败: ${e.message ?: ""}")
        }
    }

    private suspend fun pollUntilDone(
        source: CustomSourceImpl,
        record: VocalSeparationRecord,
        jobId: String,
    ): VocalSeparationRecord {
        repeat(40) { // ~2 minutes max
            delay(3000)
            try {
                val resp = RemoteHttp.get(source.separateStatusEndpoint(jobId))
                val json = JSONObject(resp)
                if (json.has("vocals_url") && json.has("instrumental_url")) {
                    return buildDone(record, json.getString("vocals_url"), json.getString("instrumental_url"))
                }
            } catch (_: Exception) {
                // keep polling
            }
        }
        return fail(record, "分离超时")
    }

    private fun buildDone(record: VocalSeparationRecord, vocal: String, instrumental: String): VocalSeparationRecord {
        val done = record.copy(status = "done", vocalUrl = vocal, accompanimentUrl = instrumental, note = "")
        store.upsert(done)
        return done
    }

    private fun fail(record: VocalSeparationRecord, reason: String): VocalSeparationRecord {
        val failed = record.copy(status = "failed", note = reason)
        store.upsert(failed)
        return failed
    }

    fun getStore() = store
}
