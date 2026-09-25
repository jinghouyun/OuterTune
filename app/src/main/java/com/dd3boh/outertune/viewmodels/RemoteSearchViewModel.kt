package com.dd3boh.outertune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.SearchHistory
import com.dd3boh.outertune.playback.DownloadUtil
import com.dd3boh.outertune.remote.RemoteMusicRepository
import com.dd3boh.outertune.remote.RemoteSong
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which search backend the search screen is using. */
enum class RemoteSourceTab(val sourceId: String?) {
    LOCAL(null),
    WY("wy"),
    MG("mg"),
    TX("tx"),
    KG("kg"),
    KW("kw");

    companion object {
        val labels = mapOf(
            LOCAL to "本地",
            WY to "网易云音乐",
            MG to "咪咕音乐",
            TX to "QQ音乐",
            KG to "酷狗音乐",
            KW to "酷我音乐"
        )
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
    private val repository: RemoteMusicRepository,
    private val downloadUtil: DownloadUtil,
    private val database: MusicDatabase,
) : ViewModel() {

    val query = MutableStateFlow("")
    val sourceTab = MutableStateFlow(RemoteSourceTab.LOCAL)

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
                if (tab == RemoteSourceTab.LOCAL || q.isBlank()) {
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
                _uiState.value = RemoteSearchUiState(loading = true)
                val results = repository.search(tab.sourceId!!, q, page = 1, limit = 30)
                val withCovers = repository.enrichCovers(results)
                _uiState.value = RemoteSearchUiState(songs = withCovers)
            }
            .launchIn(viewModelScope)
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
