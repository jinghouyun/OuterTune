package com.dd3boh.outertune.utils

import android.content.Context
import android.util.Log
import com.dd3boh.outertune.constants.AutoOpenPlayerKey
import com.dd3boh.outertune.constants.AutoPlayOnLaunchKey
import com.dd3boh.outertune.constants.NotificationArtworkKey
import com.dd3boh.outertune.constants.RememberPlaybackPositionKey
import com.dd3boh.outertune.constants.RemoteSourceKgEnabledKey
import com.dd3boh.outertune.constants.RemoteSourceKwEnabledKey
import com.dd3boh.outertune.constants.RemoteSourceMgEnabledKey
import com.dd3boh.outertune.constants.RemoteSourceQualityKey
import com.dd3boh.outertune.constants.RemoteSourceTxEnabledKey
import com.dd3boh.outertune.constants.RemoteSourceWyEnabledKey
import com.dd3boh.outertune.constants.S2TConvertKey
import com.dd3boh.outertune.constants.ShowRomanizationKey
import com.dd3boh.outertune.constants.ShowTranslationKey
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.Playlist
import com.dd3boh.outertune.db.entities.PlaylistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.datastore.preferences.core.edit
import java.time.LocalDateTime

/**
 * Simple backup/restore: exports remote-source settings to JSON,
 * and restores them back. Uses android org.json (no extra deps).
 */
object BackupManager {

    private val settingKeys = listOf(
        RemoteSourceQualityKey,
        RemoteSourceWyEnabledKey,
        RemoteSourceMgEnabledKey,
        RemoteSourceTxEnabledKey,
        RemoteSourceKgEnabledKey,
        RemoteSourceKwEnabledKey,
        AutoPlayOnLaunchKey,
        AutoOpenPlayerKey,
        RememberPlaybackPositionKey,
        NotificationArtworkKey,
        ShowTranslationKey,
        ShowRomanizationKey,
        S2TConvertKey,
    )

    suspend fun exportBackup(context: Context, database: MusicDatabase): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", 2)

        // Playlists + their songs
        val playlists = JSONArray()
        runCatching {
            val allPlaylists = database.playlistInLibraryAsc().first()
            allPlaylists.forEach { pl ->
                // skip built-in system playlists
                if (pl.id == PlaylistEntity.LIKED_PLAYLIST_ID || pl.id == PlaylistEntity.DOWNLOADED_PLAYLIST_ID) return@forEach
                val songIds = database.playlistSongs(pl.id).first().map { it.song.id }
                playlists.put(JSONObject().apply {
                    put("id", pl.id)
                    put("name", pl.playlist.name)
                    put("songIds", JSONArray(songIds))
                })
            }
        }.onFailure { Log.e("BackupManager", "export playlists failed", it) }
        root.put("playlists", playlists)

        // Settings
        val settings = JSONObject()
        val prefs = context.dataStore
        for (key in settingKeys) {
            val value = prefs[key] ?: continue
            settings.put(key.name, value.toString())
        }
        root.put("settings", settings)

        root.toString(2)
    }

    suspend fun importBackup(context: Context, database: MusicDatabase, json: String): String = withContext(Dispatchers.IO) {
        val root = JSONObject(json)
        var importedSettings = 0

        runCatching {
            val settings = root.optJSONObject("settings") ?: return@runCatching
            val keys = settings.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = settings.getString(k)
                try {
                    when (k) {
                        RemoteSourceQualityKey.name -> context.dataStore.edit { it[RemoteSourceQualityKey] = v }
                        RemoteSourceWyEnabledKey.name -> context.dataStore.edit { it[RemoteSourceWyEnabledKey] = v.toBoolean() }
                        RemoteSourceMgEnabledKey.name -> context.dataStore.edit { it[RemoteSourceMgEnabledKey] = v.toBoolean() }
                        RemoteSourceTxEnabledKey.name -> context.dataStore.edit { it[RemoteSourceTxEnabledKey] = v.toBoolean() }
                        RemoteSourceKgEnabledKey.name -> context.dataStore.edit { it[RemoteSourceKgEnabledKey] = v.toBoolean() }
                        RemoteSourceKwEnabledKey.name -> context.dataStore.edit { it[RemoteSourceKwEnabledKey] = v.toBoolean() }
                        AutoPlayOnLaunchKey.name -> context.dataStore.edit { it[AutoPlayOnLaunchKey] = v.toBoolean() }
                        AutoOpenPlayerKey.name -> context.dataStore.edit { it[AutoOpenPlayerKey] = v.toBoolean() }
                        RememberPlaybackPositionKey.name -> context.dataStore.edit { it[RememberPlaybackPositionKey] = v.toBoolean() }
                        NotificationArtworkKey.name -> context.dataStore.edit { it[NotificationArtworkKey] = v.toBoolean() }
                        ShowTranslationKey.name -> context.dataStore.edit { it[ShowTranslationKey] = v.toBoolean() }
                        ShowRomanizationKey.name -> context.dataStore.edit { it[ShowRomanizationKey] = v.toBoolean() }
                        S2TConvertKey.name -> context.dataStore.edit { it[S2TConvertKey] = v.toBoolean() }
                        else -> continue
                    }
                    importedSettings++
                } catch (e: Exception) {
                    Log.e("BackupManager", "Failed to set $k", e)
                }
            }
        }.onFailure { Log.e("BackupManager", "import failed", it) }

        // Playlists (version >= 2)
        var importedPlaylists = 0
        runCatching {
            val arr = root.optJSONArray("playlists") ?: return@runCatching
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name", "未命名歌单")
                val songIdsArr = o.optJSONArray("songIds")
                val songIds = ArrayList<String>()
                if (songIdsArr != null) {
                    for (j in 0 until songIdsArr.length()) songIds.add(songIdsArr.getString(j))
                }

                val entity = PlaylistEntity(
                    name = name,
                    bookmarkedAt = LocalDateTime.now(),
                    isLocal = true,
                )
                runCatching {
                    database.insert(entity)
                    val wrapper = Playlist(entity, 0, 0, emptyList())
                    if (songIds.isNotEmpty()) {
                        database.addSongToPlaylist(wrapper, songIds)
                    }
                    importedPlaylists++
                }.onFailure { Log.e("BackupManager", "import playlist '$name' failed", it) }
            }
        }.onFailure { Log.e("BackupManager", "import playlists failed", it) }

        "恢复完成：$importedSettings 项设置，$importedPlaylists 个歌单"
    }
}
