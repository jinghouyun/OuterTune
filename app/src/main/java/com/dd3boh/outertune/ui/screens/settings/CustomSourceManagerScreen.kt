package com.dd3boh.outertune.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dd3boh.outertune.constants.TopBarInsets
import com.dd3boh.outertune.remote.CustomSource
import com.dd3boh.outertune.remote.CustomSourceStore
import com.dd3boh.outertune.ui.component.button.IconButton
import com.dd3boh.outertune.ui.utils.backToMain
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSourceManagerScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val store = remember { CustomSourceStore(context) }
    var sources by remember { mutableStateOf(store.getAll()) }
    var editing by remember { mutableStateOf<CustomSource?>(null) }
    var showForm by remember { mutableStateOf(false) }

    if (showForm) {
        SourceForm(
            existing = editing,
            onDismiss = { showForm = false; editing = null },
            onSave = { src ->
                store.add(src)
                sources = store.getAll()
                showForm = false
                editing = null
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            },
            onDelete = { src ->
                store.remove(src.id)
                sources = store.getAll()
                showForm = false
                editing = null
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自定义源管理") },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { editing = null; showForm = true }) {
                        Icon(Icons.Rounded.Add, "添加")
                    }
                },
                windowInsets = TopBarInsets,
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        if (sources.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text("还没有自定义源，点击右上角添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(sources, key = { it.id }) { src ->
                    var showMenu by remember { mutableStateOf(false) }
                    ElevatedCard(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { editing = src; showForm = true }
                    ) {
                        Row(
                            Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(src.name, style = MaterialTheme.typography.bodyLarge)
                                Text(src.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Switch(
                                checked = src.enabled,
                                onCheckedChange = {
                                    store.update(src.copy(enabled = it))
                                    sources = store.getAll()
                                }
                            )
                        }
                    }
                    if (showMenu) {
                        AlertDialog(
                            onDismissRequest = { showMenu = false },
                            title = { Text(src.name) },
                            text = { Text(src.baseUrl) },
                            confirmButton = { TextButton(onClick = { showMenu = false; editing = src; showForm = true }) { Text("编辑") } },
                            dismissButton = {
                                TextButton(onClick = { showMenu = false; store.remove(src.id); sources = store.getAll() }) {
                                    Text("删除", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceForm(
    existing: CustomSource?,
    onDismiss: () -> Unit,
    onSave: (CustomSource) -> Unit,
    onDelete: (CustomSource) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl ?: "") }
    var searchPath by remember { mutableStateOf(existing?.searchPath ?: "/search") }
    var urlPath by remember { mutableStateOf(existing?.urlPath ?: "/url") }
    var lyricPath by remember { mutableStateOf(existing?.lyricPath ?: "/lyric") }
    var picPath by remember { mutableStateOf(existing?.picPath ?: "/pic") }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (existing == null) "添加自定义源" else "编辑自定义源") })
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("源名称 *") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("基础 API 地址 *") }, placeholder = { Text("https://example.com/api/") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = searchPath, onValueChange = { searchPath = it }, label = { Text("搜索接口路径") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = urlPath, onValueChange = { urlPath = it }, label = { Text("播放地址接口路径") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = lyricPath, onValueChange = { lyricPath = it }, label = { Text("歌词接口路径") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = picPath, onValueChange = { picPath = it }, label = { Text("封面接口路径") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用", modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (name.isBlank() || baseUrl.isBlank()) return@Button
                    onSave((existing ?: CustomSource(name = name, baseUrl = baseUrl)).copy(
                        name = name, baseUrl = baseUrl, searchPath = searchPath.ifBlank { "/search" },
                        urlPath = urlPath.ifBlank { "/url" }, lyricPath = lyricPath.ifBlank { "/lyric" },
                        picPath = picPath.ifBlank { "/pic" }, enabled = enabled
                    ))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存") }
            if (existing != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDeleteConfirm && existing != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除该自定义源？") },
            confirmButton = { TextButton(onClick = { showDeleteConfirm = false; onDelete(existing) }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }
}
