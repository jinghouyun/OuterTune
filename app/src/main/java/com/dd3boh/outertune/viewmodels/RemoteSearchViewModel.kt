package com.dd3boh.outertune.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dd3boh.outertune.constants.SourceNameDisplayKey
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.SearchHistory
import com.dd3boh.outertune.playback.DownloadUtil
import com.dd3boh.outertune.remote.CustomSourceStore
import com.dd3boh.outertune.remote.RemoteMusicRepository
import com.dd3boh.outertune.remote.RemoteSong
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which search backend the search screen is using. sourceId == null means local library. */
data class RemoteSourceTab(val sourceId: String?, val label: String) {
    companion object {
        val LOCAL = RemoteSourceTab(null, "本地")
        /** Aggregate search across all enabled sources. */
        val ALL = RemoteSourceTab("all", "全部")

        private val builtInOriginal = listOf(
            "wy" to "网易云音乐",
            "mg" to "咪咕音乐",
            "tx" to "QQ音乐",
            "kg" to "酷狗音乐",
            "kw" to "酷我音乐",
        )
        private val builtInAlias = listOf(
            "wy" to "wy",
            "mg" to "mg",
            "tx" to "tx",
            "kg" to "kg",
            "kw" to "kw",
        )

        /** Built-in tabs, respecting the original/alias display preference. */
        fun builtIn(alias: Boolean): List<RemoteSourceTab> {
            val pairs = if (alias) builtInAlias else builtInOriginal
            return listOf(LOCAL, ALL) + pairs.map { RemoteSourceTab(it.first, it.second) }
        }
    }
}

data class RemoteSearchUiState(
    val loading: Boolean = false,
    val songs: List<RemoteSong> = emptyList(),
    val error: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class RemoteSearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: RemoteMusicRepository,
    private val downloadUtil: DownloadUtil,
    private val database: MusicDatabase,
) : ViewModel() {

    private val customStore = CustomSourceStore(context)

    val query = MutableStateFlow("")
    val sourceTab = MutableStateFlow(RemoteSourceTab.LOCAL)

    /** All tabs: built-ins + enabled custom sources. Rebuilt by [refreshTabs]. */
    val tabs = MutableStateFlow<List<RemoteSourceTab>>(buildTabs())

    private fun buildTabs(): List<RemoteSourceTab> {
        val alias = context.dataStore.get(SourceNameDisplayKey, "original") == "alias"
        val built = RemoteSourceTab.builtIn(alias)
        val customs = runCatching {
            customStore.getEnabled().map { RemoteSourceTab("custom_${it.id}", it.name) }
        }.getOrDefault(emptyList())
        return built + customs
    }

    /** Call after custom sources are added/edited/deleted so tabs refresh. */
    fun refreshTabs() {
        tabs.value = buildTabs()
    }

    private val _uiState = MutableStateFlow(RemoteSearchUiState())
    val uiState = _uiState.asStateFlow()

    val downloadingIds = MutableStateFlow<Set<String>>(emptySet())

    /** Search history list (most recent first). */
    val searchHistory = MutableStateFlow<List<String>>(emptyList())

    /** Hot search words from NetEase. */
    val hotSearch = MutableStateFlow<List<String>>(emptyList())

    init {
        refreshHistory()
        viewModelScope.launch {
            hotSearch.value = repository.getHotSearch()
        }
        combine(query, sourceTab) { q, tab -> q to tab }
            .debounce(350L)
            .onEach { (q, tab) ->
                if (tab.sourceId == null || q.isBlank()) {
                    _uiState.value = RemoteSearchUiState()
                    return@onEach
                }
                // record search history
                viewModelScope.launch {
                    runCatching {
                        database.insert(SearchHistory(query = q.trim()))
                    }
                    refreshHistory()
                }
                if (tab.sourceId == "all") {
                    searchAll(q)
                    return@onEach
                }
                _uiState.value = RemoteSearchUiState(loading = true)
                val results = repository.search(tab.sourceId!!, q, page = 1, limit = 30)
                val withCovers = repository.enrichCovers(results)
                _uiState.value = RemoteSearchUiState(songs = withCovers)
            }
            .launchIn(viewModelScope)
    }

    /**
     * Aggregate search: concurrently query every enabled source and append results
     * incrementally as each source returns. Failing sources are skipped silently.
     */
    private fun searchAll(q: String) {
        _uiState.value = RemoteSearchUiState(loading = true)
        val accum = mutableListOf<RemoteSong>()
        viewModelScope.launch {
            val ids: List<String> = runCatching { repository.enabledSourceIds() }.getOrDefault(emptyList())
            coroutineScope {
                ids.map { sid ->
                    async(Dispatchers.IO) {
                        runCatching {
                            val res = repository.search(sid, q, page = 1, limit = 30)
                            if (res.isNotEmpty()) {
                                val enriched = repository.enrichCovers(res)
                                synchronized(accum) { accum.addAll(enriched) }
                                _uiState.value = _uiState.value.copy(songs = accum.toList())
                            }
                        }
                    }
                }.awaitAll()
            }
            _uiState.value = _uiState.value.copy(loading = false)
        }
    }

    private fun refreshHistory() {
        viewModelScope.launch {
            runCatching {
                database.searchHistory("").collect { list ->
                    searchHistory.value = list.map { it.query }.take(15)
                }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            runCatching { database.clearSearchHistory() }
            searchHistory.value = emptyList()
        }
    }

    fun removeHistory(query: String) {
        viewModelScope.launch {
            runCatching { database.delete(SearchHistory(query = query)) }
            searchHistory.value = searchHistory.value.filterNot { it == query }
        }
    }

    fun download(song: RemoteSong) {
        if (downloadingIds.value.contains(song.id)) return
        downloadingIds.value = downloadingIds.value + song.id
        viewModelScope.launch {
            try {
                // Make sure the song row exists first, otherwise updateDownloadStatus() (a bare
                // UPDATE) affects 0 rows and the download never shows up in the downloaded list.
                runCatching { database.insert(song.toMediaMetadata()) }
                val url = repository.resolveStreamUrl(song.id)
                if (url != null) {
                    downloadUtil.downloadRemote(song.toMediaMetadata(), url)
                }
            } finally {
                downloadingIds.value = downloadingIds.value - song.id
            }
        }
    }
}
