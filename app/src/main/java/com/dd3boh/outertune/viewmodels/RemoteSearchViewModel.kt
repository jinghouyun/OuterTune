package com.dd3boh.outertune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    MG("mg");

    companion object {
        val labels = mapOf(
            LOCAL to "本地",
            WY to "网易云",
            MG to "咪咕"
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
) : ViewModel() {

    val query = MutableStateFlow("")
    val sourceTab = MutableStateFlow(RemoteSourceTab.LOCAL)

    private val _uiState = MutableStateFlow(RemoteSearchUiState())
    val uiState = _uiState.asStateFlow()

    init {
        combine(query, sourceTab) { q, tab -> q to tab }
            .debounce(350L)
            .onEach { (q, tab) ->
                if (tab == RemoteSourceTab.LOCAL || q.isBlank()) {
                    _uiState.value = RemoteSearchUiState()
                    return@onEach
                }
                _uiState.value = RemoteSearchUiState(loading = true)
                val results = repository.search(tab.sourceId!!, q, page = 1, limit = 30)
                _uiState.value = RemoteSearchUiState(songs = results)
            }
            .launchIn(viewModelScope)
    }
}
