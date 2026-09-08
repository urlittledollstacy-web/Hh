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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import app.hikari.core.model.MediaSummary
import app.hikari.core.model.MediaType
import app.hikari.core.model.ScoreFormat
import app.hikari.data.AniListLibraryRepository
import app.hikari.data.local.HikariFavoritesRepository
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import app.hikari.core.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: AniListLibraryRepository,
    private val favorites: HikariFavoritesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()
    private var selectedType = MediaType.ANIME
    private var observationJob: Job? = null
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L

    init {
        viewModelScope.launch {
            favorites.observeFavorites().collect { items ->
                _state.value = _state.value.copy(favorites = items)
            }
        }
    }

    fun selectType(type: MediaType) {
        selectedType = type
        observationJob?.cancel()
        refreshJob?.cancel()
        refreshGeneration++
        _state.value = _state.value.copy(entries = emptyList(), loading = true, error = null)

        observationJob = viewModelScope.launch {
            library.observe(type).filterNotNull().collect { snapshot ->
                if (selectedType == type) {
                    _state.value = _state.value.copy(
                        entries = snapshot.entries,
                        scoreFormat = snapshot.scoreFormat,
                        error = null,
                    )
                }
            }
        }
        refresh(type)
    }

    fun refresh(type: MediaType = selectedType) {
        if (type != selectedType) return
        refreshJob?.cancel()
        val requestGeneration = ++refreshGeneration
        _state.value = _state.value.copy(loading = true, error = null)
        refreshJob = viewModelScope.launch {
            try {
                library.refresh(type)
                if (selectedType == type && refreshGeneration == requestGeneration) {
                    _state.value = _state.value.copy(loading = false, error = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (selectedType == type && refreshGeneration == requestGeneration) {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = error.message ?: "Couldn't load your library.",
                    )
                }
            }
        }
    }

    fun save(type: MediaType, entry: LibraryEntry, status: String, progress: Int, score: Double, onDone: () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(saving = true, error = null)
        try {
            library.updateEntry(type, entry, status, progress, score)
            _state.value = _state.value.copy(saving = false)
            onDone()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            _state.value = _state.value.copy(
                saving = false,
                error = error.message ?: "Couldn't save your AniList changes.",
            )
        }
    }

    fun toggleFavorite(media: MediaSummary) = viewModelScope.launch {
        val current = _state.value.favorites.any { it.id == media.id && it.type == media.type }
        favorites.toggle(media, current)
    }
}

data class LibraryUiState(
    val entries: List<LibraryEntry> = emptyList(),
    val favorites: List<MediaSummary> = emptyList(),
    val scoreFormat: ScoreFormat = ScoreFormat.POINT_100,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
)

@Composable
fun LibraryScreen(
    signedIn: Boolean,
    padding: PaddingValues,
    onMediaClick: (MediaSummary) -> Unit,
    vm: LibraryViewModel = hiltViewModel(),
) {
    var type by remember { mutableStateOf(MediaType.ANIME) }
    var status by remember { mutableStateOf("ALL") }
    var selected by remember { mutableStateOf<LibraryEntry?>(null) }
    val state by vm.state.collectAsState()

    LaunchedEffect(signedIn, type) { if (signedIn) vm.selectType(type) }

    if (!signedIn) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Library", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Connect AniList to sync your anime and manga lists in real time.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val filteredFavorites = state.favorites.filter { it.type == type }
    val filteredEntries = when (status) {
        "ALL" -> state.entries
        "FAVORITES" -> emptyList()
        else -> state.entries.filter { it.status == status }
    }
    val statuses = listOf("ALL", "CURRENT", "PLANNING", "COMPLETED", "REPEATING", "PAUSED", "DROPPED", "FAVORITES")

    LazyColumn(
        contentPadding = PaddingValues(20.dp, 28.dp, 20.dp, padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Library", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("AniList tracking + Hikari favorites", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.refresh(type) }, enabled = !state.loading && !state.saving) {
                    if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Refresh, "Refresh library")
                }
            }
        }
        item { ChoiceRow(listOf("ANIME", "MANGA"), type.name) { type = MediaType.valueOf(it); status = "ALL"; selected = null } }
        item { ChoiceRow(statuses, status) { status = it } }
        if (status == "FAVORITES") {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Favorite, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Hikari Favorites", fontWeight = FontWeight.Bold)
                            Text("Private to this app · never synced to AniList", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (state.error != null && status != "FAVORITES") item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.error.orEmpty(), Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { vm.refresh(type) }) { Text("Retry") }
                }
            }
        }
        if (!state.loading && state.error == null && ((status == "FAVORITES" && filteredFavorites.isEmpty()) || (status != "FAVORITES" && filteredEntries.isEmpty()))) {
            item {
                Text(
                    if (status == "FAVORITES") "No Hikari favorites yet. Tap the heart on a title to save it here."
                    else if (status == "ALL") "Your ${type.name.lowercase()} library is empty."
                    else "No titles in ${statusLabel(status, type)}.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (status == "FAVORITES") {
            items(filteredFavorites, key = { "favorite-${it.type}-${it.id}" }) { media ->
                FavoriteCard(media, onClick = { onMediaClick(media) }, onToggle = { vm.toggleFavorite(media) })
            }
        } else {
            items(filteredEntries, key = { it.id }) { entry -> LibraryCard(entry, type, state.scoreFormat) { selected = entry } }
        }
    }

    selected?.let { entry ->
        LibraryEditorDialog(
            entry = entry,
            type = type,
            scoreFormat = state.scoreFormat,
            saving = state.saving,
            onDismiss = { selected = null },
            onSave = { newStatus, progress, score -> vm.save(type, entry, newStatus, progress, score) { selected = null } },
        )
    }
}

@Composable
private fun FavoriteCard(media: MediaSummary, onClick: () -> Unit, onToggle: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(72.dp).aspectRatio(.7f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)) {
                media.coverUrl?.let { AsyncImage(model = it, contentDescription = media.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(media.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (media.type == MediaType.ANIME) "Anime" else "Manga", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onToggle) { Icon(Icons.Outlined.Favorite, "Remove from Hikari favorites", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun LibraryCard(entry: LibraryEntry, type: MediaType, scoreFormat: ScoreFormat, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(72.dp).aspectRatio(.7f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)) {
                entry.media.coverUrl?.let { AsyncImage(model = it, contentDescription = entry.media.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.media.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(statusLabel(entry.status, type), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text(progressLabel(entry, type), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            entry.score?.let { Text(scoreDisplay(it, scoreFormat), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun LibraryEditorDialog(entry: LibraryEntry, type: MediaType, scoreFormat: ScoreFormat, saving: Boolean, onDismiss: () -> Unit, onSave: (String, Int, Double) -> Unit) {
    var selectedStatus by remember(entry.id, entry.status) { mutableStateOf(entry.status) }
    var progress by remember(entry.id, entry.progress) { mutableStateOf(entry.progress.toFloat()) }
    var score by remember(entry.id, entry.score, scoreFormat) { mutableStateOf((entry.score ?: 0.0).toFloat()) }
    val maxProgress = entry.media.episodesOrChapters?.coerceAtLeast(0) ?: 0
    val safeProgress = if (maxProgress > 0) progress.coerceIn(0f, maxProgress.toFloat()) else progress.coerceAtLeast(0f)
    val scoreConfig = scoreConfig(scoreFormat)
    val safeScore = score.coerceIn(0f, scoreConfig.max)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(entry.media.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                entry.media.coverUrl?.let { AsyncImage(model = it, contentDescription = entry.media.title, modifier = Modifier.fillMaxWidth().aspectRatio(1.7f).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit) }
                Text(if (type == MediaType.ANIME) "Anime tracking" else "Manga tracking", color = MaterialTheme.colorScheme.primary)
                Text("Status", fontWeight = FontWeight.SemiBold)
                ChoiceRow(statusOptions(type), selectedStatus) { selectedStatus = it }
                Text(progressLabel(type), fontWeight = FontWeight.SemiBold)
                if (maxProgress > 0) Slider(value = safeProgress, onValueChange = { progress = it.roundToInt().toFloat() }, valueRange = 0f..maxProgress.toFloat(), steps = (maxProgress - 1).coerceAtLeast(0))
                else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { progress = (safeProgress - 1f).coerceAtLeast(0f) }) { Icon(Icons.Outlined.Remove, "Decrease progress") }
                    Text(safeProgress.roundToInt().toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    IconButton(onClick = { progress += 1f }) { Icon(Icons.Outlined.Add, "Increase progress") }
                }
                Text("${safeProgress.roundToInt()} ${if (type == MediaType.ANIME) "episodes" else "chapters"}${maxProgress.takeIf { it > 0 }?.let { " / $it" } ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text("Your score", fontWeight = FontWeight.SemiBold)
                Slider(value = safeScore, onValueChange = { score = snapScore(it, scoreFormat) }, valueRange = 0f..scoreConfig.max, steps = scoreConfig.steps)
                Text(scoreDisplay(safeScore.toDouble(), scoreFormat), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text(scoreConfig.helper, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        },
        confirmButton = { Button(onClick = { onSave(selectedStatus, safeProgress.roundToInt(), safeScore.toDouble()) }, enabled = !saving) { if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save to AniList") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } },
    )
}

private data class ScoreConfig(val max: Float, val steps: Int, val helper: String)
private fun scoreConfig(format: ScoreFormat): ScoreConfig = when (format) {
    ScoreFormat.POINT_100 -> ScoreConfig(100f, 99, "100 point · drag to choose")
    ScoreFormat.POINT_10_DECIMAL -> ScoreConfig(10f, 19, "10 point decimal · 0.5 steps")
    ScoreFormat.POINT_10 -> ScoreConfig(10f, 9, "10 point · whole numbers")
    ScoreFormat.POINT_5 -> ScoreConfig(5f, 4, "5 star · AniList setting")
    ScoreFormat.POINT_3 -> ScoreConfig(3f, 2, "3 point smiley · AniList setting")
}
private fun snapScore(value: Float, format: ScoreFormat): Float = when (format) { ScoreFormat.POINT_10_DECIMAL -> (value * 2f).roundToInt() / 2f; else -> value.roundToInt().toFloat() }
private fun scoreDisplay(score: Double, format: ScoreFormat): String = when (format) {
    ScoreFormat.POINT_100 -> "${formatScore(score)} / 100"
    ScoreFormat.POINT_10_DECIMAL -> "${"%.1f".format(score)} / 10"
    ScoreFormat.POINT_10 -> "${formatScore(score)} / 10"
    ScoreFormat.POINT_5 -> "${"★".repeat(score.roundToInt())}${"☆".repeat((5 - score.roundToInt()).coerceAtLeast(0))}  ${score.roundToInt()} / 5"
    ScoreFormat.POINT_3 -> when (score.roundToInt()) { 1 -> ":(  1 / 3"; 2 -> ":|  2 / 3"; 3 -> ":)  3 / 3"; else -> "Not rated" }
}

@Composable
private fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { option ->
            val active = option == selected
            Box(Modifier.clip(RoundedCornerShape(18.dp)).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).clickable { onSelect(option) }.padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text(optionLabel(option), color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
            }
        }
    }
}

private fun statusOptions(type: MediaType): List<String> = listOf("CURRENT", "PLANNING", "COMPLETED", "REPEATING", "PAUSED", "DROPPED")
private fun optionLabel(value: String): String = when (value) {
    "ALL" -> "All"; "CURRENT" -> "Watching"; "PLANNING" -> "Planning"; "REPEATING" -> "Rewatching"; "REREADING" -> "Rereading"; "COMPLETED" -> "Completed"; "PAUSED" -> "Paused"; "DROPPED" -> "Dropped"; "FAVORITES" -> "♥ Favorites"; else -> value.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
private fun statusLabel(value: String, type: MediaType): String = when (type) { MediaType.ANIME -> optionLabel(value); MediaType.MANGA -> when (value) { "CURRENT" -> "Reading"; "PLANNING" -> "Plan to read"; "REPEATING" -> "Rereading"; else -> optionLabel(value) } }
private fun progressLabel(entry: LibraryEntry, type: MediaType): String = when (type) { MediaType.ANIME -> "${entry.progress} episodes${entry.media.episodesOrChapters?.let { " / $it" } ?: ""}"; MediaType.MANGA -> "${entry.progress} chapters${entry.media.episodesOrChapters?.let { " / $it" } ?: ""}" }
private fun progressLabel(type: MediaType): String = if (type == MediaType.ANIME) "Episodes watched" else "Chapters read"
private fun formatScore(score: Double): String = if (score % 1.0 == 0.0) score.toInt().toString() else "%.1f".format(score)
