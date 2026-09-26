package com.dd3boh.outertune.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dd3boh.outertune.constants.TopBarInsets
import com.dd3boh.outertune.remote.CustomSource
import com.dd3boh.outertune.remote.CustomSourceStore
import com.dd3boh.outertune.remote.RemoteHttp
import com.dd3boh.outertune.ui.component.button.IconButton
import com.dd3boh.outertune.ui.utils.backToMain
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Parse an imported JSON config into a list of CustomSource (id auto-generated).
 * Supports: { "sources": [...] }, a top-level array, or a single {name, api/baseUrl/url}.
 * Returns only entries with a resolvable baseUrl.
 */
private fun parseImportedSources(json: String): List<CustomSource> {
    val out = mutableListOf<CustomSource>()
    val roots = mutableListOf<JSONObject>()
    runCatching {
        val trimmed = json.trim()
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { roots.add(it) }
        } else {
            val obj = JSONObject(trimmed)
            obj.optJSONArray("sources")?.let { arr ->
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { roots.add(it) }
            } ?: roots.add(obj)
        }
    }
    roots.forEach { o ->
        runCatching {
            val baseUrl = o.optString("baseUrl").ifBlank {
                o.optString("api").ifBlank { o.optString("url") }
            }
            if (baseUrl.isBlank()) return@runCatching
            out.add(
                CustomSource(
                    id = UUID.randomUUID().toString(),
                    name = o.optString("name").ifBlank { "未命名源" },
                    baseUrl = baseUrl,
                    searchPath = o.optString("searchPath", "/search").ifBlank { "/search" },
                    urlPath = o.optString("urlPath", "/url").ifBlank { "/url" },
                    lyricPath = o.optString("lyricPath", "/lyric").ifBlank { "/lyric" },
                    picPath = o.optString("picPath", "/pic").ifBlank { "/pic" },
                    separatePath = o.optString("separatePath", "/separate").ifBlank { "/separate" },
                    enabled = o.optBoolean("enabled", true),
                )
            )
        }
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSourceManagerScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { CustomSourceStore(context) }
    var sources by remember { mutableStateOf(store.getAll()) }
    var editing by remember { mutableStateOf<CustomSource?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showOnlineImport by remember { mutableStateOf(false) }

    fun refresh() { sources = store.getAll() }

    /** Deduplicate against existing names, appending (2),(3)... */
    fun uniqueName(base: String): String {
        val existing = sources.map { it.name }.toMutableSet()
        var name = base
        var i = 2
        while (name in existing) { name = "$base($i)"; i++ }
        return name
    }

    /** Derive a friendly source name from a base URL (host, without www.). */
    fun nameFromUrl(url: String): String {
        return runCatching {
            val u = java.net.URL(url)
            val host = u.host.removePrefix("www.")
            if (host.isBlank()) "自定义源" else host
        }.getOrDefault("自定义源")
    }

    /** Add the raw URL itself as a baseUrl source (LX Music compatible behaviour). */
    fun addUrlAsBaseSource(url: String): String {
        val normalized = if (url.endsWith("/")) url else "$url/"
        val name = uniqueName(nameFromUrl(url))
        store.add(CustomSource(name = name, baseUrl = normalized))
        refresh()
        return name
    }

    /**
     * Lenient online import, compatible with LX Music's behaviour:
     *  1. GET the URL (UA, 30s). If it returns a JSON config with sources -> import them.
     *  2. If JSON parsing yields no sources (or the response is HTML/JS/anything) -> treat the
     *     URL itself as a baseUrl and add it.
     *  3. If the network call fails -> still add the URL as a baseUrl (many APIs return 404
     *     on GET / but serve /search fine).
     *  Never rejects a URL outright.
     */
    suspend fun importFromUrlLenient(raw: String): String {
        val target = raw.trim()
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            return "URL 必须以 http:// 或 https:// 开头"
        }
        val ua = mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
        val body = try {
            RemoteHttp.getLongTimeout(target, ua)
        } catch (e: Exception) {
            // Network/HTTP error: still add as a base source, per LX Music tolerance.
            val name = addUrlAsBaseSource(target)
            return "无法访问(${e.message ?: "网络错误"})，仍已添加：$name，请到搜索页测试"
        }

        // Try to interpret the body as a JSON config (formats A/B/C).
        val parsed = runCatching { parseImportedSources(body) }.getOrDefault(emptyList())
        if (parsed.isNotEmpty()) {
            var count = 0
            parsed.forEach { s ->
                runCatching {
                    store.add(s.copy(name = uniqueName(s.name)))
                    count++
                }
            }
            refresh()
            return "成功导入 $count 个源"
        }

        // No sources found in the body -> treat the URL itself as the API base URL.
        val name = addUrlAsBaseSource(target)
        return "已添加源：$name，请到搜索页测试是否可用"
    }

    fun importJsonString(json: String) {
        val parsed = runCatching { parseImportedSources(json) }.getOrDefault(emptyList())
        if (parsed.isEmpty()) {
            Toast.makeText(context, "导入失败：URL 无法访问或格式不正确", Toast.LENGTH_LONG).show()
            return
        }
        var count = 0
        parsed.forEach { s ->
            runCatching {
                store.add(s.copy(name = uniqueName(s.name)))
                count++
            }
        }
        refresh()
        Toast.makeText(context, "成功导入 $count 个源", Toast.LENGTH_LONG).show()
    }

    val localImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                Toast.makeText(context, "导入失败：无法读取文件", Toast.LENGTH_LONG).show()
            } else {
                importJsonString(text)
            }
        }
    }

    if (showForm) {
        SourceForm(
            existing = editing,
            onDismiss = { showForm = false; editing = null },
            onSave = { src ->
                store.add(src)
                refresh()
                showForm = false
                editing = null
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            },
            onDelete = { src ->
                store.remove(src.id)
                refresh()
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
                    IconButton(onClick = { showImportMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, "更多")
                    }
                    DropdownMenu(expanded = showImportMenu, onDismissRequest = { showImportMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("添加") },
                            onClick = { showImportMenu = false; editing = null; showForm = true }
                        )
                        DropdownMenuItem(
                            text = { Text("在线导入") },
                            onClick = { showImportMenu = false; showOnlineImport = true }
                        )
                        DropdownMenuItem(
                            text = { Text("本地导入") },
                            onClick = {
                                showImportMenu = false
                                localImportLauncher.launch(arrayOf("application/json"))
                            }
                        )
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
                    Text(
                        "还没有自定义源",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点击右上角 + 添加，或用更多菜单在线/本地导入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Row {
                        OutlinedButton(onClick = { showOnlineImport = true }) { Text("在线导入") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { localImportLauncher.launch(arrayOf("application/json")) }) { Text("本地导入") }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(sources, key = { it.id }) { src ->
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
                                    refresh()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showOnlineImport) {
        var url by remember { mutableStateOf("") }
        var importing by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!importing) showOnlineImport = false },
            title = { Text("在线导入") },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("配置 JSON 链接") },
                    placeholder = { Text("https://xxx.com/api.json") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !importing && url.isNotBlank(),
                    onClick = {
                        importing = true
                        val target = url.trim()
                        scope.launch {
                            val msg = withContext(Dispatchers.IO) {
                                importFromUrlLenient(target)
                            }
                            showOnlineImport = false
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                ) { Text(if (importing) "导入中…" else "导入") }
            },
            dismissButton = { TextButton(onClick = { showOnlineImport = false }) { Text("取消") } }
        )
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
    var separatePath by remember { mutableStateOf(existing?.separatePath ?: "/separate") }
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
            OutlinedTextField(value = separatePath, onValueChange = { separatePath = it }, label = { Text("人声分离接口路径（可选）") }, modifier = Modifier.fillMaxWidth())
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
                        picPath = picPath.ifBlank { "/pic" }, separatePath = separatePath.ifBlank { "/separate" },
                        enabled = enabled
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
