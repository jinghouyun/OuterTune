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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dd3boh.outertune.constants.TopBarInsets
import com.dd3boh.outertune.remote.CustomSource
import com.dd3boh.outertune.remote.CustomSourceStore
import com.dd3boh.outertune.remote.RemoteHttp
import com.dd3boh.outertune.remote.js.JsScriptStore
import com.dd3boh.outertune.remote.js.ScriptHeader
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
 * Handles common LX/OuterTune share formats.
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
            val root = when {
                obj.optJSONObject("sources") != null -> obj
                obj.optJSONObject("config") != null -> obj.optJSONObject("config")!!
                obj.optJSONObject("api") != null -> obj.optJSONObject("api")!!
                obj.optJSONObject("server") != null -> obj.optJSONObject("server")!!
                else -> obj
            }
            root.optJSONArray("sources")?.let { arr ->
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { roots.add(it) }
            } ?: root.optJSONArray("list")?.let { arr ->
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { roots.add(it) }
            } ?: roots.add(root)
        }
    }
    roots.forEach { o ->
        runCatching {
            val baseUrl = extractBaseUrl(o) ?: return@runCatching
            out.add(
                CustomSource(
                    id = UUID.randomUUID().toString(),
                    name = o.optString("name").ifBlank { hostOf(baseUrl) ?: "未命名源" },
                    baseUrl = normalizeBaseUrl(baseUrl),
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

private fun extractBaseUrl(o: JSONObject): String? {
    val fields = listOf("baseUrl", "api", "url", "host", "server", "serverUrl", "apiUrl", "endpoint")
    for (f in fields) {
        when (val v = o.opt(f)) {
            is String -> validHttpUrl(v)?.let { return it }
            is JSONObject -> {
                validHttpUrl(v.optString("base"))?.let { return it }
                validHttpUrl(v.optString("url"))?.let { return it }
                validHttpUrl(v.optString("host"))?.let { return it }
            }
            else -> {}
        }
    }
    return null
}

private fun validHttpUrl(s: String?): String? {
    if (s.isNullOrBlank()) return null
    if (!s.startsWith("http://") && !s.startsWith("https://")) return null
    return s.takeIf { !hostOf(it).isNullOrBlank() }
}

private fun normalizeBaseUrl(url: String): String =
    if (url.endsWith("/")) url else "$url/"

private fun hostOf(url: String): String? = runCatching {
    java.net.URL(url).host.takeIf { it.isNotBlank() }
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSourceManagerScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { CustomSourceStore(context) }
    val jsStore = remember { JsScriptStore(context) }
    var sources by remember { mutableStateOf(store.getAll()) }
    var activeJsId by remember { mutableStateOf(store.activeJsId()) }
    var editing by remember { mutableStateOf<CustomSource?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showOnlineImport by remember { mutableStateOf(false) }

    fun refresh() {
        sources = store.getAll()
        activeJsId = store.activeJsId()
    }

    fun uniqueName(base: String): String {
        val existing = sources.map { it.name }.toMutableSet()
        var name = base
        var i = 2
        while (name in existing) { name = "$base($i)"; i++ }
        return name
    }

    fun nameFromUrl(url: String): String = runCatching {
        java.net.URL(url).host.removePrefix("www.").ifBlank { "自定义源" }
    }.getOrDefault("自定义源")

    fun addUrlAsBaseSource(url: String): String {
        require(hostOf(url) != null) { "invalid url: $url" }
        val name = uniqueName(nameFromUrl(url))
        store.add(CustomSource(name = name, baseUrl = normalizeBaseUrl(url)))
        refresh()
        return name
    }

    /** Persist a JS script. Requires a valid leading `/* @name ... */` header block comment. */
    fun addJsScriptSource(script: String, scriptUrl: String?): String? {
        val header = ScriptHeader.parse(script) ?: return null
        val id = UUID.randomUUID().toString()
        jsStore.save(id, script)
        val name = uniqueName(header.name.ifBlank { scriptUrl?.let { nameFromUrl(it) } ?: "JS音源" })
        store.add(
            CustomSource(
                id = id,
                name = name,
                baseUrl = "",
                enabled = true,
                isJs = true,
                scriptUrl = scriptUrl,
                jsVersion = header.version.ifBlank { null },
                jsAuthor = header.author.ifBlank { null },
                jsHomepage = header.homepage.ifBlank { null },
            )
        )
        refresh()
        return name
    }

    suspend fun importFromUrlLenient(raw: String): String {
        val target = raw.trim()
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            return "URL 必须以 http:// 或 https:// 开头"
        }
        val ua = mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
        val body = try {
            RemoteHttp.getLongTimeout(target, ua)
        } catch (e: Exception) {
            val name = addUrlAsBaseSource(target)
            return "无法访问(${e.message ?: "网络错误"})，仍已添加：$name"
        }

        // A real lx-music JS script must start with a header block comment.
        if (ScriptHeader.parse(body) != null) {
            val name = runCatching { addJsScriptSource(body, target) }.getOrNull()
                ?: return "JS 脚本保存失败"
            return "已导入 JS 脚本源：$name"
        }

        val parsed = runCatching { parseImportedSources(body) }.getOrDefault(emptyList())
        if (parsed.isNotEmpty()) {
            var count = 0
            parsed.forEach { s ->
                runCatching { store.add(s.copy(name = uniqueName(s.name))); count++ }
            }
            refresh()
            return "成功导入 $count 个源"
        }

        val name = addUrlAsBaseSource(target)
        return "已添加源：$name"
    }

    fun importText(text: String) {
        when {
            ScriptHeader.parse(text) != null -> {
                val name = runCatching { addJsScriptSource(text, null) }.getOrNull()
                Toast.makeText(
                    context,
                    if (name != null) "已导入 JS 脚本源：$name" else "JS 脚本缺少 /* @name ... */ 头部注释，已拒绝",
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {
                val parsed = runCatching { parseImportedSources(text) }.getOrDefault(emptyList())
                if (parsed.isEmpty()) {
                    Toast.makeText(context, "导入失败：无法识别内容（JS 脚本需以 /* @name ... */ 开头）", Toast.LENGTH_LONG).show()
                    return
                }
                var count = 0
                parsed.forEach { s -> runCatching { store.add(s.copy(name = uniqueName(s.name))); count++ } }
                refresh()
                Toast.makeText(context, "成功导入 $count 个源", Toast.LENGTH_LONG).show()
            }
        }
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
            } else importText(text)
        }
    }

    if (showForm) {
        SourceForm(
            existing = editing,
            onDismiss = { showForm = false; editing = null },
            onSave = { src -> store.add(src); refresh(); showForm = false; editing = null },
            onDelete = { src ->
                store.remove(src.id)
                if (src.isJs) jsStore.delete(src.id)
                refresh()
                showForm = false
                editing = null
            }
        )
        return
    }

    val jsSources = sources.filter { it.isJs }
    val restSources = sources.filter { !it.isJs }

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
                            text = { Text("添加 REST 源") },
                            onClick = { showImportMenu = false; editing = null; showForm = true }
                        )
                        DropdownMenuItem(
                            text = { Text("在线导入") },
                            onClick = { showImportMenu = false; showOnlineImport = true }
                        )
                        DropdownMenuItem(
                            text = { Text("本地导入 (.js / .json)") },
                            onClick = {
                                showImportMenu = false
                                localImportLauncher.launch(arrayOf("application/json", "text/javascript", "text/plain", "application/octet-stream"))
                            }
                        )
                    }
                },
                windowInsets = TopBarInsets,
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // ---------------- JS script sources ----------------
            item(key = "js_header") {
                Text(
                    "JS 脚本源（增强内置源播放/歌词/封面）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (jsSources.isEmpty()) {
                item(key = "js_empty") {
                    Text(
                        "尚未导入 JS 脚本。导入后选择一个启用，它会接管内置源的播放地址解析。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            items(jsSources, key = { "js_${it.id}" }) { src ->
                val active = activeJsId == src.id
                ElevatedCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = active,
                            onClick = {
                                store.setActiveJsId(if (active) null else src.id)
                                refresh()
                            }
                        )
                        Column(Modifier.weight(1f).clickable {
                            store.setActiveJsId(if (active) null else src.id); refresh()
                        }) {
                            Text(src.name, style = MaterialTheme.typography.bodyLarge)
                            val sub = listOfNotNull(
                                src.jsVersion?.takeIf { it.isNotBlank() }?.let { "v$it" },
                                src.jsAuthor?.takeIf { it.isNotBlank() }?.let { "by $it" }
                            ).joinToString(" · ")
                            if (sub.isNotBlank()) {
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Text(
                                if (active) "已启用" else "点按启用为增强脚本",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            store.remove(src.id)
                            jsStore.delete(src.id)
                            refresh()
                        }) {
                            Icon(Icons.Rounded.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // ---------------- REST custom sources ----------------
            item(key = "rest_header") {
                Text(
                    "REST API 自定义源",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (restSources.isEmpty()) {
                item(key = "rest_empty") {
                    Text(
                        "没有 REST 自定义源。用右上角 + 或更多菜单导入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            items(restSources, key = { "rest_${it.id}" }) { src ->
                ElevatedCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { editing = src; showForm = true }
                ) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(src.name, style = MaterialTheme.typography.bodyLarge)
                            Text(src.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Switch(
                            checked = src.enabled,
                            onCheckedChange = { store.update(src.copy(enabled = it)); refresh() }
                        )
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
                    label = { Text("脚本 / 配置 URL") },
                    placeholder = { Text("https://xxx.com/source.js") },
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
                            val msg = withContext(Dispatchers.IO) { importFromUrlLenient(target) }
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
        topBar = { TopAppBar(title = { Text(if (existing == null) "添加 REST 源" else "编辑 REST 源") }) }
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
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
                    if (hostOf(baseUrl) == null) return@Button
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
