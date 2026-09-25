/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */
package com.dd3boh.outertune.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import com.dd3boh.outertune.constants.AutoPlayOnLaunchKey
import com.dd3boh.outertune.constants.AutoOpenPlayerKey
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
import com.dd3boh.outertune.constants.TopBarInsets
import com.dd3boh.outertune.constants.VocalSeparatorApiUrlKey
import com.dd3boh.outertune.ui.component.ColumnWithContentPadding
import com.dd3boh.outertune.ui.component.EditTextPreference
import com.dd3boh.outertune.ui.component.ListPreference
import com.dd3boh.outertune.ui.component.PreferenceGroupTitle
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.component.button.IconButton
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteSourceSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (quality, onQualityChange) = rememberPreference(RemoteSourceQualityKey, "128k")
    val (wyEnabled, onWyChange) = rememberPreference(RemoteSourceWyEnabledKey, true)
    val (mgEnabled, onMgChange) = rememberPreference(RemoteSourceMgEnabledKey, true)
    val (txEnabled, onTxChange) = rememberPreference(RemoteSourceTxEnabledKey, true)
    val (kgEnabled, onKgChange) = rememberPreference(RemoteSourceKgEnabledKey, true)
    val (kwEnabled, onKwChange) = rememberPreference(RemoteSourceKwEnabledKey, true)
    val (autoPlay, onAutoPlay) = rememberPreference(AutoPlayOnLaunchKey, false)
    val (autoOpen, onAutoOpen) = rememberPreference(AutoOpenPlayerKey, false)
    val (rememberPos, onRememberPos) = rememberPreference(RememberPlaybackPositionKey, true)
    val (notifyArt, onNotifyArt) = rememberPreference(NotificationArtworkKey, true)
    val (showTrans, onShowTrans) = rememberPreference(ShowTranslationKey, true)
    val (showRoma, onShowRoma) = rememberPreference(ShowRomanizationKey, false)
    val (s2t, onS2t) = rememberPreference(S2TConvertKey, false)
    val (vocalApiUrl, onVocalApiUrl) = rememberPreference(VocalSeparatorApiUrlKey, "")

    ColumnWithContentPadding(
        modifier = Modifier.fillMaxHeight(),
        columnModifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroupTitle(title = "播放音质")
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            ListPreference(
                title = { Text("默认音质") },
                icon = { Icon(Icons.Rounded.HighQuality, null) },
                selectedValue = quality,
                values = listOf("128k", "320k", "flac"),
                valueText = {
                    when (it) {
                        "128k" -> "标准 (128kbps)"
                        "320k" -> "高品 (320kbps)"
                        "flac" -> "无损 (FLAC)"
                        else -> it
                    }
                },
                onValueSelected = onQualityChange
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        PreferenceGroupTitle(title = "音源开关")
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference("网易云音乐", Icons.Rounded.Cloud, wyEnabled, onWyChange)
            SwitchPreference("咪咕音乐", Icons.Rounded.MusicNote, mgEnabled, onMgChange)
            SwitchPreference("QQ音乐", Icons.Rounded.MusicNote, txEnabled, onTxChange)
            SwitchPreference("酷狗音乐", Icons.Rounded.MusicNote, kgEnabled, onKgChange)
            SwitchPreference("酷我音乐", Icons.Rounded.MusicNote, kwEnabled, onKwChange)
        }
        Spacer(modifier = Modifier.height(16.dp))

        PreferenceGroupTitle(title = "播放行为")
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference("启动后自动播放", Icons.Rounded.PlayArrow, autoPlay, onAutoPlay)
            SwitchPreference("启动后打开播放页", Icons.Rounded.PlayArrow, autoOpen, onAutoOpen)
            SwitchPreference("记住播放进度", Icons.Rounded.PlayArrow, rememberPos, onRememberPos)
            SwitchPreference("通知栏显示封面", Icons.Rounded.Settings, notifyArt, onNotifyArt)
        }
        Spacer(modifier = Modifier.height(16.dp))

        PreferenceGroupTitle(title = "歌词")
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference("显示翻译歌词", Icons.Rounded.Translate, showTrans, onShowTrans)
            SwitchPreference("显示罗马音", Icons.Rounded.Translate, showRoma, onShowRoma)
            SwitchPreference("简体转繁体显示", Icons.Rounded.Translate, s2t, onS2t)
        }
        Spacer(modifier = Modifier.height(16.dp))

        PreferenceGroupTitle(title = "人声分离")
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            EditTextPreference(
                title = { Text("分离 API 地址") },
                icon = { Icon(Icons.Rounded.Mic, null) },
                value = vocalApiUrl,
                onValueChange = onVocalApiUrl,
            )
            PreferenceEntry(
                title = { Text("人声分离列表") },
                description = "查看和管理已分离的歌曲",
                icon = { Icon(Icons.Rounded.Mic, null) },
                onClick = { navController.navigate("vocal_separation") }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        PreferenceGroupTitle(title = "备份与恢复")
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val db = com.dd3boh.outertune.LocalDatabase.current
            var message by remember { mutableStateOf<String?>(null) }

            val createLauncher = rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                scope.launch {
                    val json = com.dd3boh.outertune.utils.BackupManager.exportBackup(context, db)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    message = "备份已保存"
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            val openLauncher = rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                scope.launch {
                    val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return@launch
                    val result = com.dd3boh.outertune.utils.BackupManager.importBackup(context, db, json)
                    android.widget.Toast.makeText(context, result, android.widget.Toast.LENGTH_LONG).show()
                }
            }

            PreferenceEntry(
                title = { Text("备份到文件") },
                description = "导出歌单和设置为 JSON",
                icon = { Icon(Icons.Rounded.Settings, null) },
                onClick = { createLauncher.launch("outertune_backup.json") }
            )
            PreferenceEntry(
                title = { Text("从文件恢复") },
                description = "从 JSON 文件恢复歌单和设置",
                icon = { Icon(Icons.Rounded.Settings, null) },
                onClick = { openLauncher.launch(arrayOf("application/json")) }
            )
        }
    }

    TopAppBar(
        title = { Text("第三方音源") },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
            }
        },
        windowInsets = TopBarInsets,
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun SwitchPreference(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SwitchPreference(
        title = { Text(title) },
        icon = { Icon(icon, null) },
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun PreferenceEntry(
    title: @Composable () -> Unit,
    description: String?,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    com.dd3boh.outertune.ui.component.PreferenceEntry(
        title = title,
        description = description,
        icon = icon,
        onClick = onClick
    )
}
