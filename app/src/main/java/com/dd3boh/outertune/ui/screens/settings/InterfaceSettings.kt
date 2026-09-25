/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
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
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.TextFields
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
import com.dd3boh.outertune.constants.AudioReleaseOnFocusLossKey
import com.dd3boh.outertune.constants.HomeHorizontalScrollKey
import com.dd3boh.outertune.constants.KeepScreenOn
import com.dd3boh.outertune.constants.KeepScreenOnKey
import com.dd3boh.outertune.constants.ListFontSizeKey
import com.dd3boh.outertune.constants.ListRowHeightKey
import com.dd3boh.outertune.constants.ListShowThumbnailKey
import com.dd3boh.outertune.constants.ReserveStatusBarKey
import com.dd3boh.outertune.constants.TopBarInsets
import com.dd3boh.outertune.ui.component.ColumnWithContentPadding
import com.dd3boh.outertune.ui.component.EnumListPreference
import com.dd3boh.outertune.ui.component.ListPreference
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.PreferenceGroupTitle
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.component.button.IconButton
import com.dd3boh.outertune.ui.screens.settings.fragments.SwipeGesturesFrag
import com.dd3boh.outertune.ui.screens.settings.fragments.TabArrangementFrag
import com.dd3boh.outertune.ui.screens.settings.fragments.TabExtrasFrag
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterfaceSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {

    ColumnWithContentPadding(
        modifier = Modifier.fillMaxHeight(),
        columnModifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.grp_layout)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            TabArrangementFrag()
        }
        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            TabExtrasFrag()
        }
        Spacer(modifier = Modifier.height(16.dp))

        PreferenceGroupTitle(
            title = "列表显示"
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            val (showThumb, onShowThumbChange) = rememberPreference(ListShowThumbnailKey, true)
            val (fontSize, onFontSizeChange) = rememberPreference(ListFontSizeKey, "medium")
            val (rowHeight, onRowHeightChange) = rememberPreference(ListRowHeightKey, "normal")

            SwitchPreference(
                title = { Text("显示封面") },
                icon = { Icon(Icons.Rounded.ThumbUp, null) },
                checked = showThumb,
                onCheckedChange = onShowThumbChange
            )
            ListPreference(
                title = { Text("字体大小") },
                icon = { Icon(Icons.Rounded.TextFields, null) },
                selectedValue = fontSize,
                values = listOf("small", "medium", "large"),
                valueText = {
                    when (it) {
                        "small" -> "小"
                        "large" -> "大"
                        else -> "中"
                    }
                },
                onValueSelected = onFontSizeChange
            )
            ListPreference(
                title = { Text("行高") },
                icon = { Icon(Icons.Rounded.ViewList, null) },
                selectedValue = rowHeight,
                values = listOf("compact", "normal", "loose"),
                valueText = {
                    when (it) {
                        "compact" -> "紧凑"
                        "loose" -> "宽松"
                        else -> "标准"
                    }
                },
                onValueSelected = onRowHeightChange
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        PreferenceGroupTitle(
            title = "其他界面"
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            val (homeHScroll, onHomeHScrollChange) = rememberPreference(HomeHorizontalScrollKey, true)
            val (reserveBar, onReserveBarChange) = rememberPreference(ReserveStatusBarKey, true)
            val (audioRelease, onAudioReleaseChange) = rememberPreference(AudioReleaseOnFocusLossKey, false)

            SwitchPreference(
                title = { Text("首页分类横向滚动") },
                icon = { Icon(Icons.Rounded.ViewList, null) },
                checked = homeHScroll,
                onCheckedChange = onHomeHScrollChange
            )
            SwitchPreference(
                title = { Text("内容避开状态栏") },
                icon = { Icon(Icons.Rounded.Tab, null) },
                checked = reserveBar,
                onCheckedChange = onReserveBarChange
            )
            SwitchPreference(
                title = { Text("空闲/结束后卸载音频") },
                description = "播放进入 Idle/Ended 时释放播放器",
                icon = { Icon(Icons.Rounded.ThumbUp, null) },
                checked = audioRelease,
                onCheckedChange = onAudioReleaseChange
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        PreferenceGroupTitle(
            title = stringResource(R.string.grp_behavior)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            SwipeGesturesFrag()
        }
        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            val (keepScreenOn, onKeepScreenOnChanged) = rememberEnumPreference(
                key = KeepScreenOnKey,
                defaultValue = KeepScreenOn.LYRICS
            )

            EnumListPreference(
                title = { Text(stringResource(R.string.keep_screen_on)) },
                icon = { Icon(Icons.Rounded.Tab, null) },
                selectedValue = keepScreenOn,
                onValueSelected = onKeepScreenOnChanged,
                valueText = {
                    when (it) {
                        KeepScreenOn.NEVER -> stringResource(R.string.keep_screen_on_never)
                        KeepScreenOn.LYRICS -> stringResource(R.string.keep_screen_on_lyrics)
                        KeepScreenOn.PLAYER -> stringResource(R.string.keep_screen_on_player)
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(48.dp))

        PreferenceGroupTitle(
            title = stringResource(R.string.more_settings)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.appearance)) },
                icon = { Icon(Icons.Rounded.Palette, null) },
                onClick = { navController.navigate("settings/appearance") }
            )
        }
    }


    TopAppBar(
        title = { Text(stringResource(R.string.grp_interface)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null
                )
            }
        },
        windowInsets = TopBarInsets,
        scrollBehavior = scrollBehavior
    )
}

