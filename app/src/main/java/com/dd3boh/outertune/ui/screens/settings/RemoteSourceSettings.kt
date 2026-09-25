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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.RemoteSourceKgEnabledKey
import com.dd3boh.outertune.constants.RemoteSourceKwEnabledKey
import com.dd3boh.outertune.constants.RemoteSourceMgEnabledKey
import com.dd3boh.outertune.constants.RemoteSourceQualityKey
import com.dd3boh.outertune.constants.RemoteSourceTxEnabledKey
import com.dd3boh.outertune.constants.RemoteSourceWyEnabledKey
import com.dd3boh.outertune.constants.TopBarInsets
import com.dd3boh.outertune.ui.component.ColumnWithContentPadding
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
            SwitchPreference(
                title = { Text("网易云音乐") },
                icon = { Icon(Icons.Rounded.Cloud, null) },
                checked = wyEnabled,
                onCheckedChange = onWyChange
            )
            SwitchPreference(
                title = { Text("咪咕音乐") },
                icon = { Icon(Icons.Rounded.MusicNote, null) },
                checked = mgEnabled,
                onCheckedChange = onMgChange
            )
            SwitchPreference(
                title = { Text("QQ音乐") },
                icon = { Icon(Icons.Rounded.MusicNote, null) },
                checked = txEnabled,
                onCheckedChange = onTxChange
            )
            SwitchPreference(
                title = { Text("酷狗音乐") },
                icon = { Icon(Icons.Rounded.MusicNote, null) },
                checked = kgEnabled,
                onCheckedChange = onKgChange
            )
            SwitchPreference(
                title = { Text("酷我音乐") },
                icon = { Icon(Icons.Rounded.MusicNote, null) },
                checked = kwEnabled,
                onCheckedChange = onKwChange
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
