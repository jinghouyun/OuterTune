package com.dd3boh.outertune.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.dd3boh.outertune.constants.TopBarInsets
import com.dd3boh.outertune.remote.VocalSeparationRecord
import com.dd3boh.outertune.remote.VocalSeparationStore
import com.dd3boh.outertune.remote.VocalSeparator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation.NavController
import com.dd3boh.outertune.ui.component.button.IconButton
import com.dd3boh.outertune.ui.utils.backToMain

@HiltViewModel
class VocalSeparationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: com.dd3boh.outertune.remote.RemoteMusicRepository,
) : ViewModel() {
    private val store = VocalSeparationStore(context)
    private val _records = MutableStateFlow<List<VocalSeparationRecord>>(emptyList())
    val records = _records.asStateFlow()

    // 0 = 原声, 1 = 人声, 2 = 伴奏
    val trackMode = MutableStateFlow(0)

    init { refresh() }

    fun refresh() { _records.value = store.getAll() }

    fun startSeparation(songId: String, title: String, artist: String, cover: String?) {
        viewModelScope.launch {
            VocalSeparator(context, repository).submit(
                VocalSeparationRecord(songId, title, artist, cover, status = "processing")
            )
            refresh()
        }
    }

    fun delete(songId: String) {
        store.delete(songId)
        refresh()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocalSeparationScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: VocalSeparationViewModel = hiltViewModel(),
) {
    val records by viewModel.records.collectAsState()
    val trackMode by viewModel.trackMode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("人声分离") },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                    }
                },
                windowInsets = TopBarInsets,
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("暂无分离记录", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("在歌曲菜单中选择\"人声分离\"开始", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Column(Modifier.padding(padding)) {
                // Track mode selector
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("原声" to 0, "人声" to 1, "伴奏" to 2).forEach { (label, mode) ->
                        FilterChip(
                            selected = trackMode == mode,
                            onClick = { viewModel.trackMode.value = mode },
                            label = { Text(label) },
                            leadingIcon = {
                                Icon(
                                    when (mode) { 1 -> Icons.Rounded.Mic; 2 -> Icons.Rounded.GraphicEq; else -> Icons.Rounded.MusicNote },
                                    null, Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    items(records, key = { it.songId }) { record ->
                        VocalSepRow(record = record, onDelete = { viewModel.delete(record.songId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun VocalSepRow(record: VocalSeparationRecord, onDelete: () -> Unit) {
    ElevatedCard(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = record.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(record.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(record.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (record.status == "failed" && record.note.isNotBlank()) {
                    Text(record.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
                }
            }
            when (record.status) {
                "processing" -> {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("分离中…", style = MaterialTheme.typography.labelSmall)
                }
                "done" -> Icon(Icons.Rounded.CheckCircle, "完成", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                else -> Icon(Icons.Rounded.Error, "失败", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, "删除", Modifier.size(20.dp))
            }
        }
    }
}
