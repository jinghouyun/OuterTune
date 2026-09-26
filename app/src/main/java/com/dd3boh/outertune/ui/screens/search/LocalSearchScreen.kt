package com.dd3boh.outertune.ui.screens.search

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.LocalSnackbarHostState
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.CONTENT_TYPE_LIST
import com.dd3boh.outertune.constants.ListItemHeight
import com.dd3boh.outertune.constants.ListThumbnailSize
import com.dd3boh.outertune.constants.ThumbnailCornerRadius
import com.dd3boh.outertune.constants.SwipeToQueueKey
import com.dd3boh.outertune.db.entities.Album
import com.dd3boh.outertune.db.entities.Artist
import com.dd3boh.outertune.db.entities.Playlist
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.playback.queues.RemoteQueue
import com.dd3boh.outertune.remote.RemoteSong
import com.dd3boh.outertune.ui.component.ChipsRow
import com.dd3boh.outertune.ui.component.EmptyPlaceholder
import com.dd3boh.outertune.ui.component.LazyColumnScrollbar
import com.dd3boh.outertune.ui.component.items.AlbumListItem
import com.dd3boh.outertune.ui.component.items.ArtistListItem
import com.dd3boh.outertune.ui.component.items.PlaylistListItem
import com.dd3boh.outertune.ui.component.items.SongListItem
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.utils.makeTimeString
import com.dd3boh.outertune.viewmodels.LocalFilter
import com.dd3boh.outertune.viewmodels.LocalSearchViewModel
import com.dd3boh.outertune.viewmodels.RemoteSearchViewModel
import com.dd3boh.outertune.viewmodels.RemoteSourceTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(FlowPreview::class)
@Composable
fun LocalSearchScreen(
    query: String,
    navController: NavController,
    onDismiss: () -> Unit,
    viewModel: LocalSearchViewModel = hiltViewModel(),
    remoteViewModel: RemoteSearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()

    val swipeEnabled by rememberPreference(SwipeToQueueKey, true)

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val searchFilter by viewModel.filter.collectAsState()
    val result by viewModel.result.collectAsState()

    val remoteSourceTab by remoteViewModel.sourceTab.collectAsState()
    val remoteTabs by remoteViewModel.tabs.collectAsState()
    val remoteUiState by remoteViewModel.uiState.collectAsState()

    val lazyListState = rememberLazyListState()
    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect {
                keyboardController?.hide()
            }
    }

    LaunchedEffect(query) {
        snapshotFlow { query }.debounce { 300L }.collectLatest {
            viewModel.query.value = it
            remoteViewModel.query.value = it
        }
    }

    Column(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
    ) {
        // Source selector: local library vs remote online sources (incl. custom)
        ChipsRow(
            chips = remoteTabs.map { it to it.label },
            currentValue = remoteSourceTab,
            onValueUpdate = { remoteViewModel.sourceTab.value = it },
            isLoading = { tab -> tab == remoteSourceTab && remoteUiState.loading }
        )

        Crossfade(
            targetState = remoteSourceTab,
            label = "searchSourceCrossfade"
        ) { tab ->
            when (tab) {
                RemoteSourceTab.LOCAL -> LocalResults(
                    result = result,
                    searchFilter = searchFilter,
                    viewModel = viewModel,
                    query = query,
                    navController = navController,
                    onDismiss = onDismiss,
                    isPlaying = isPlaying,
                    mediaMetadata = mediaMetadata,
                    swipeEnabled = swipeEnabled,
                    snackbarHostState = snackbarHostState,
                    lazyListState = lazyListState,
                    density = density,
                )

                else -> {
                    val downloadingSet by remoteViewModel.downloadingIds.collectAsState()
                    val hotSearch by remoteViewModel.hotSearch.collectAsState()
                    val searchHistory by remoteViewModel.searchHistory.collectAsState()
                    RemoteResults(
                    uiState = remoteUiState,
                    query = query,
                    isPlaying = isPlaying,
                    mediaMetadata = mediaMetadata,
                    downloadingSet = downloadingSet,
                    hotSearch = hotSearch,
                    searchHistory = searchHistory,
                    onHotSearchClick = { remoteViewModel.query.value = it },
                    onRemoveHistory = { remoteViewModel.removeHistory(it) },
                    onClearHistory = { remoteViewModel.clearHistory() },
                    onPlay = { song ->
                        val all = remoteUiState.songs
                        scope.launch(Dispatchers.IO) {
                            // insert all results so the player/notification/history can resolve them
                            all.forEach { database.insert(it.toMediaMetadata()) }
                            val metadataList = all.map { it.toMediaMetadata() }
                            val startIndex = metadataList.indexOfFirst { it.id == song.id }
                            playerConnection.service.playQueue(
                                RemoteQueue(
                                    title = "${remoteSourceTab.label}: $query",
                                    items = metadataList,
                                    startIndex = startIndex.coerceAtLeast(0),
                                )
                            )
                        }
                    },
                    onDownload = { song -> remoteViewModel.download(song) }
                )
                }
            }
        }
    }
    LazyColumnScrollbar(
        state = lazyListState,
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .align(Alignment.BottomCenter)
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun LocalResults(
    result: com.dd3boh.outertune.viewmodels.LocalSearchResult,
    searchFilter: LocalFilter,
    viewModel: LocalSearchViewModel,
    query: String,
    navController: NavController,
    onDismiss: () -> Unit,
    isPlaying: Boolean,
    mediaMetadata: MediaMetadata?,
    swipeEnabled: Boolean,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    density: androidx.compose.ui.unit.Density,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val searchedSongsLabel = stringResource(R.string.queue_searched_songs)
    Column {
        ChipsRow(
            chips = listOf(
                LocalFilter.ALL to stringResource(R.string.filter_all),
                LocalFilter.SONG to stringResource(R.string.filter_songs),
                LocalFilter.ALBUM to stringResource(R.string.filter_albums),
                LocalFilter.ARTIST to stringResource(R.string.filter_artists),
                LocalFilter.PLAYLIST to stringResource(R.string.filter_playlists)
            ),
            currentValue = searchFilter,
            onValueUpdate = { viewModel.filter.value = it }
        )

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom + WindowInsetsSides.Start + WindowInsetsSides.End).asPaddingValues(),
            modifier = Modifier.weight(1f)
        ) {
            result.map.forEach { (filter, items) ->
                if (result.filter == LocalFilter.ALL) {
                    item(key = filter) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ListItemHeight)
                                .clickable { viewModel.filter.value = filter }
                                .padding(start = 12.dp, end = 18.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    when (filter) {
                                        LocalFilter.SONG -> R.string.filter_songs
                                        LocalFilter.ALBUM -> R.string.filter_albums
                                        LocalFilter.ARTIST -> R.string.filter_artists
                                        LocalFilter.PLAYLIST -> R.string.filter_playlists
                                        LocalFilter.ALL -> R.string.filter_all
                                    }
                                ),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.AutoMirrored.Rounded.NavigateNext, contentDescription = null)
                        }
                    }
                }

                val thumbnailSize = (ListThumbnailSize.value * density.density).roundToInt()
                items(items = items, key = { it.id }, contentType = { CONTENT_TYPE_LIST }) { item ->
                    when (item) {
                        is Song -> SongListItem(
                            song = item,
                            navController = navController,
                            snackbarHostState = snackbarHostState,
                            isActive = item.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            inSelectMode = false,
                            isSelected = false,
                            onSelectedChange = { },
                            swipeEnabled = swipeEnabled,
                            thumbnailSize = thumbnailSize,
                            onPlay = {
                                val songs = result.map
                                    .getOrDefault(LocalFilter.SONG, emptyList())
                                    .filterIsInstance<Song>()
                                    .map { it.toMediaMetadata() }
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = "$searchedSongsLabel $query",
                                        items = songs,
                                        startIndex = songs.indexOfFirst { it.id == item.id }
                                    )
                                )
                            },
                            modifier = Modifier.animateItem()
                        )
                        is Album -> AlbumListItem(
                            album = item,
                            isActive = item.id == mediaMetadata?.album?.id,
                            isPlaying = isPlaying,
                            modifier = Modifier
                                .clickable { onDismiss(); navController.navigate("album/${item.id}") }
                                .animateItem()
                        )
                        is Artist -> ArtistListItem(
                            artist = item,
                            modifier = Modifier
                                .clickable { onDismiss(); navController.navigate("artist/${item.id}") }
                                .animateItem()
                        )
                        is Playlist -> PlaylistListItem(
                            playlist = item,
                            modifier = Modifier
                                .clickable { onDismiss(); navController.navigate("local_playlist/${item.id}") }
                                .animateItem()
                        )
                    }
                }
            }

            if (result.query.isNotEmpty() && result.map.isEmpty()) {
                item(key = "no_result") {
                    EmptyPlaceholder(
                        icon = Icons.Rounded.Search,
                        text = stringResource(R.string.no_results_found),
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteResults(
    uiState: com.dd3boh.outertune.viewmodels.RemoteSearchUiState,
    query: String,
    isPlaying: Boolean,
    mediaMetadata: MediaMetadata?,
    downloadingSet: Set<String>,
    hotSearch: List<String>,
    searchHistory: List<String>,
    onHotSearchClick: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onPlay: (RemoteSong) -> Unit,
    onDownload: (RemoteSong) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
            query.isBlank() -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (searchHistory.isNotEmpty()) {
                    item {
                        Text("搜索历史", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                    }
                    items(searchHistory) { h ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            androidx.compose.material3.AssistChip(
                                onClick = { onHotSearchClick(h) },
                                label = { Text(h) },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onRemoveHistory(h) }) {
                                Icon(Icons.Rounded.Close, null, Modifier.size(18.dp))
                            }
                        }
                    }
                    item {
                        TextButton(onClick = onClearHistory) { Text("清空历史") }
                        Spacer(Modifier.height(16.dp))
                    }
                }
                if (hotSearch.isNotEmpty()) {
                    item {
                        Text("热门搜索", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                    }
                    items(hotSearch) { w ->
                        androidx.compose.material3.AssistChip(
                            onClick = { onHotSearchClick(w) },
                            label = { Text(w) },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                if (searchHistory.isEmpty() && hotSearch.isEmpty()) {
                    item {
                        EmptyPlaceholder(
                            icon = Icons.Rounded.Cloud,
                            text = "输入关键词搜索在线音乐",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
            uiState.songs.isEmpty() -> EmptyPlaceholder(
                icon = Icons.Rounded.Cloud,
                text = "未找到结果",
                modifier = Modifier.align(Alignment.Center)
            )
            else -> LazyColumn(
                contentPadding = LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Start + WindowInsetsSides.End)
                    .asPaddingValues(),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items = uiState.songs, key = { it.id }) { song ->
                    RemoteSongRow(
                        song = song,
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        isDownloading = downloadingSet.contains(song.id),
                        onClick = { onPlay(song) },
                        onDownload = { onDownload(song) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteSongRow(
    song: RemoteSong,
    isActive: Boolean,
    isPlaying: Boolean,
    isDownloading: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(ListItemHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(6.dp)
                .size(ListThumbnailSize)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(ThumbnailCornerRadius)),
            contentAlignment = Alignment.Center
        ) {
            if (song.thumbnailUrl != null) {
                AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Rounded.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = buildString {
                    append(song.artists.joinToString("、"))
                    if (song.albumName != null) {
                        append(" · ")
                        append(song.albumName)
                    }
                    append(" · ")
                    append(makeTimeString(song.durationSec * 1000L))
                },
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(6.dp)
                .clickable(onClick = onDownload),
            contentAlignment = Alignment.Center
        ) {
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = "下载",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
