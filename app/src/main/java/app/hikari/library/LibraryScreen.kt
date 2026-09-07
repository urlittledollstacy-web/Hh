package app.hikari.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hikari.core.model.LibraryEntry
import app.hikari.core.model.MediaType
import app.hikari.data.remote.AniListGraphQlService
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(private val api: AniListGraphQlService) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    fun load(type: MediaType) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { api.library(type) }
            .onSuccess { entries -> _state.value = LibraryUiState(entries = entries, loading = false) }
            .onFailure { error -> _state.value = LibraryUiState(loading = false, error = error.message ?: "Couldn't load your library.") }
    }
}

data class LibraryUiState(
    val entries: List<LibraryEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

@Composable
fun LibraryScreen(signedIn: Boolean, padding: PaddingValues, vm: LibraryViewModel = hiltViewModel()) {
    var type by remember { mutableStateOf(MediaType.ANIME) }
    var status by remember { mutableStateOf("ALL") }
    val state by vm.state.collectAsState()

    LaunchedEffect(signedIn, type) {
        if (signedIn) vm.load(type)
    }

    if (!signedIn) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Library", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Connect AniList to sync your anime and manga lists in real time.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val availableStatuses = listOf("ALL") + state.entries.map { it.status }.distinct()
    val filtered = if (status == "ALL") state.entries else state.entries.filter { it.status == status }

    LazyColumn(
        contentPadding = PaddingValues(20.dp, 28.dp, 20.dp, padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Library", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Synced from AniList", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.load(type) }, enabled = !state.loading) {
                    if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, "Refresh library")
                }
            }
        }
        item { ChoiceRow(listOf("ANIME", "MANGA"), type.name) { type = MediaType.valueOf(it); status = "ALL" } }
        item { ChoiceRow(availableStatuses, status) { status = it } }
        if (state.error != null) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.error.orEmpty(), Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { vm.load(type) }) { Text("Retry") }
                }
            }
        }
        if (!state.loading && state.error == null && filtered.isEmpty()) {
            item { Text(if (status == "ALL") "Your ${type.name.lowercase()} library is empty." else "No titles in ${statusLabel(status)}.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(filtered, key = { it.id }) { entry -> LibraryCard(entry, type) }
    }
}

@Composable
private fun LibraryCard(entry: LibraryEntry, type: MediaType) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(72.dp).aspectRatio(.7f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)) {
                entry.media.coverUrl?.let { AsyncImage(model = it, contentDescription = entry.media.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.media.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(statusLabel(entry.status), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text(progressLabel(entry, type), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            entry.score?.takeIf { it > 0 }?.let { Text("${formatScore(it)} ★", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { option ->
            val active = option == selected
            Box(
                Modifier.clip(RoundedCornerShape(18.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) { Text(optionLabel(option), color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp) }
        }
    }
}

private fun optionLabel(value: String): String = when (value) {
    "ALL" -> "All"
    "CURRENT" -> "Watching"
    "REPEATING" -> "Rewatching"
    "REREADING" -> "Rereading"
    "COMPLETED" -> "Completed"
    "PLANNING" -> "Planning"
    "PAUSED" -> "Paused"
    "DROPPED" -> "Dropped"
    else -> value.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun statusLabel(value: String): String = optionLabel(value)

private fun progressLabel(entry: LibraryEntry, type: MediaType): String = when (type) {
    MediaType.ANIME -> "${entry.progress} episodes${entry.media.episodesOrChapters?.let { " / $it" } ?: ""}"
    MediaType.MANGA -> "${entry.progress} chapters${entry.media.episodesOrChapters?.let { " / $it" } ?: ""}"
}

private fun formatScore(score: Double): String = if (score % 1.0 == 0.0) score.toInt().toString() else "%.1f".format(score)
