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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.datastore.preferences.core.edit

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
        root.put("version", 1)

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

        "恢复完成：$importedSettings 项设置"
    }
}
