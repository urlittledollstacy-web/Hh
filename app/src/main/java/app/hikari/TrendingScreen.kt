package app.hikari

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hikari.core.model.MediaSummary
import app.hikari.core.model.MediaType
import app.hikari.data.remote.AniListGraphQlService
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrendingUiState(
    val items: List<MediaSummary> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = true,
)

@HiltViewModel
class TrendingViewModel @Inject constructor(
    private val api: AniListGraphQlService,
) : ViewModel() {
    private var page = 0
    private val _state = MutableStateFlow(TrendingUiState())
    val state: StateFlow<TrendingUiState> = _state.asStateFlow()

    init {
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || !current.hasMore) return
        viewModelScope.launch {
            val nextPage = page + 1
            _state.value = current.copy(loadingMore = true)
            val next = api.trending(MediaType.ANIME, nextPage)
            page = nextPage
            val merged = (_state.value.items + next).distinctBy { it.id to it.type }
            _state.value = TrendingUiState(
                items = merged,
                loading = false,
                loadingMore = false,
                hasMore = next.size >= 20,
            )
        }
    }
}

@Composable
fun TrendingScreen(
    padding: PaddingValues,
    onMediaClick: (MediaSummary) -> Unit,
    onBack: () -> Unit,
    vm: TrendingViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, state.items.size, state.hasMore, state.loadingMore) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.distinctUntilChanged().collect { lastVisible ->
            if (state.hasMore && !state.loadingMore && lastVisible >= state.items.size - 4) {
                vm.loadMore()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("Trending now", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("What's hot on AniList", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (state.loading && state.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No trending anime found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                state = gridState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(state.items, key = { "${it.type}:${it.id}" }) { media ->
                    TrendingCard(media, onMediaClick)
                }
                if (state.loadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendingCard(media: MediaSummary, onMediaClick: (MediaSummary) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable { onMediaClick(media) },
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            media.coverUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = media.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Text(media.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            media.averageScore?.let { "★ $it%" } ?: "AniList",
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
