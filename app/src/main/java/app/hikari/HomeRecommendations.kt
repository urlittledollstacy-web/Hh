package app.hikari

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hikari.core.auth.SecureTokenStore
import app.hikari.core.model.MediaSummary
import app.hikari.data.remote.AniListGraphQlService
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeRecommendationsUiState(
    val items: List<MediaSummary> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val available: Boolean = true,
)

@HiltViewModel
class HomeRecommendationsViewModel @Inject constructor(
    private val api: AniListGraphQlService,
    private val tokenStore: SecureTokenStore,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeRecommendationsUiState())
    val state: StateFlow<HomeRecommendationsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        if (tokenStore.accessToken() == null) {
            _state.value = HomeRecommendationsUiState(loading = false, available = false)
            return@launch
        }
        _state.value = _state.value.copy(refreshing = true, available = true)
        val items = runCatching { api.personalizedRecommendations() }.getOrDefault(emptyList())
        _state.value = HomeRecommendationsUiState(items = items, loading = false, refreshing = false, available = true)
    }
}

@Composable
fun HomeRecommendationsSection(
    onMediaClick: (MediaSummary) -> Unit,
    vm: HomeRecommendationsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    if (!state.available) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Recommended for you",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = vm::refresh, enabled = !state.refreshing) {
                if (state.refreshing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Refresh, "Refresh recommendations")
                }
            }
        }

        when {
            state.loading -> RecommendationLoadingRow()
            state.items.isEmpty() -> Text(
                "Add a few anime or manga to your AniList to unlock recommendations.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            else -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 8.dp),
            ) {
                items(state.items, key = { "${it.type}:${it.id}" }) { media ->
                    RecommendationCard(media, onMediaClick)
                }
            }
        }
    }
}

@Composable
private fun RecommendationCard(media: MediaSummary, onMediaClick: (MediaSummary) -> Unit) {
    Column(
        Modifier.width(150.dp).clickable { onMediaClick(media) },
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().height(218.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = media.coverUrl,
                contentDescription = media.title,
                modifier = Modifier.fillMaxWidth().height(218.dp).clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Text(
            media.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        media.averageScore?.let {
            Text(
                "★ ${it}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecommendationLoadingRow() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(3) {
            Card(
                modifier = Modifier.width(150.dp).height(218.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {}
        }
    }
}
