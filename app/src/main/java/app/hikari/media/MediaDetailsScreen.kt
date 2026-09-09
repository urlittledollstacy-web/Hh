package app.hikari.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hikari.core.model.LibraryEntry
import app.hikari.core.model.MediaCharacter
import app.hikari.core.model.MediaDetail
import app.hikari.core.model.MediaRelation
import app.hikari.core.model.MediaStaff
import app.hikari.core.model.MediaSummary
import app.hikari.core.model.ScoreFormat
import app.hikari.data.AniListLibraryRepository
import app.hikari.data.local.HikariFavoritesRepository
import app.hikari.data.remote.AniListMediaDetailService
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

sealed interface MediaDetailState {
    data object Loading : MediaDetailState
    data class Ready(val media: MediaDetail, val isFavorite: Boolean) : MediaDetailState
    data class Error(val message: String) : MediaDetailState
}

@HiltViewModel
class MediaDetailsViewModel @Inject constructor(
    private val service: AniListMediaDetailService,
    private val favorites: HikariFavoritesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<MediaDetailState>(MediaDetailState.Loading)
    val state: StateFlow<MediaDetailState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var favoriteJob: Job? = null
    private var requestGeneration = 0L
    private var currentFavorite = false

    fun load(summary: MediaSummary) {
        val current = _state.value
        if (current is MediaDetailState.Ready && current.media.summary.id == summary.id && current.media.summary.type == summary.type) {
            return
        }

        val generation = ++requestGeneration
        loadJob?.cancel()
        favoriteJob?.cancel()
        currentFavorite = false
        _state.value = MediaDetailState.Loading

        favoriteJob = viewModelScope.launch {
            favorites.observeIsFavorite(summary.id, summary.type).collect { isFavorite ->
                if (generation != requestGeneration) return@collect
                currentFavorite = isFavorite
                val current = _state.value
                if (current is MediaDetailState.Ready && current.media.summary.id == summary.id) {
                    _state.value = current.copy(isFavorite = isFavorite)
                }
            }
        }

        loadJob = viewModelScope.launch {
            try {
                val media = service.detail(summary.id)
                if (generation != requestGeneration || media.summary.id != summary.id) return@launch
                _state.value = MediaDetailState.Ready(media, currentFavorite)
                try {
                    favorites.refreshMetadata(media.summary)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // Favorite metadata repair must not replace valid media details with an error.
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (generation == requestGeneration) {
                    _state.value = MediaDetailState.Error("Couldn't load this title. Try again.")
                }
            }
        }
    }

    fun toggleFavorite() {
        val current = _state.value as? MediaDetailState.Ready ?: return
        viewModelScope.launch {
            try {
                favorites.toggle(current.media.summary, current.isFavorite)
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }
}

sealed interface MediaTrackingState { data object Hidden : MediaTrackingState; data object Loading : MediaTrackingState; data class Ready(val entry: LibraryEntry, val scoreFormat: ScoreFormat, val saving: Boolean = false, val error: String? = null) : MediaTrackingState; data class Error(val message: String) : MediaTrackingState }

@HiltViewModel
class MediaTrackingViewModel @Inject constructor(
    private val library: AniListLibraryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<MediaTrackingState>(MediaTrackingState.Hidden)
    val state: StateFlow<MediaTrackingState> = _state.asStateFlow()
    private var activeMedia: MediaSummary? = null
    private var observationJob: Job? = null
    private var refreshJob: Job? = null
    private var saveJob: Job? = null

    fun open(media: MediaSummary) {
        activeMedia = media
        observationJob?.cancel()
        refreshJob?.cancel()
        _state.value = MediaTrackingState.Loading

        observationJob = viewModelScope.launch {
            library.observe(media.type).filterNotNull().collect { snapshot ->
                if (activeMedia?.let { it.id == media.id && it.type == media.type } != true) return@collect
                val current = _state.value as? MediaTrackingState.Ready
                _state.value = MediaTrackingState.Ready(
                    entry = snapshot.entries.firstOrNull { it.media.id == media.id }
                        ?: LibraryEntry(0, media, "PLANNING", 0, null),
                    scoreFormat = snapshot.scoreFormat,
                    saving = current?.saving == true,
                    error = current?.error,
                )
            }
        }

        refreshJob = viewModelScope.launch {
            try {
                library.refresh(media.type)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                val current = _state.value
                _state.value = if (current is MediaTrackingState.Ready) {
                    current.copy(error = "Couldn't refresh your AniList list. Showing the last synced value.")
                } else {
                    MediaTrackingState.Error("Couldn't load your AniList list. Make sure you're signed in and try again.")
                }
            }
        }
    }

    fun dismiss() {
        activeMedia = null
        observationJob?.cancel()
        refreshJob?.cancel()
        saveJob?.cancel()
        _state.value = MediaTrackingState.Hidden
    }

    fun save(entry: LibraryEntry, status: String, progress: Int, score: Double) {
        val current = _state.value as? MediaTrackingState.Ready ?: return
        _state.value = current.copy(saving = true, error = null)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            try {
                library.updateEntry(entry.media.type, entry, status, progress, score)
                saveJob = null
                dismiss()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                val latest = _state.value as? MediaTrackingState.Ready ?: current
                _state.value = latest.copy(
                    saving = false,
                    error = "Couldn't save your AniList changes. Try again.",
                )
            }
        }
    }

    fun retry(media: MediaSummary) = open(media)
}

@Composable fun MediaDetailsScreen(summary: MediaSummary, onBack: () -> Unit, vm: MediaDetailsViewModel = hiltViewModel(), trackingVm: MediaTrackingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val trackingState by trackingVm.state.collectAsState()
    var currentSummary by remember(summary.id) { mutableStateOf(summary) }
    var history by remember(summary.id) { mutableStateOf(emptyList<MediaHistoryEntry>()) }
    var creditTarget by remember(summary.id) { mutableStateOf<CreditTarget?>(null) }
    var backProgress by remember { mutableStateOf(0f) }
    var backSwipeEdge by remember { mutableStateOf(BackEventCompat.EDGE_LEFT) }

    LaunchedEffect(currentSummary.id, currentSummary.type) { vm.load(currentSummary) }
    val currentMedia = (state as? MediaDetailState.Ready)?.media?.takeIf {
        it.summary.id == currentSummary.id && it.summary.type == currentSummary.type
    }

    fun goBack() {
        if (history.isNotEmpty()) {
            val previous = history.last()
            currentSummary = previous.summary
            history = history.dropLast(1)
        } else {
            onBack()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        history.lastOrNull()?.detail?.let { previous ->
            DetailContent(
                media = previous,
                isFavorite = false,
                onBack = {},
                onOpenRelation = {},
                onOpenTracking = {},
                onToggleFavorite = {},
                onOpenCredit = {}
            )
        }

        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val direction = if (backSwipeEdge == BackEventCompat.EDGE_RIGHT) -1 else 1
        val offsetX = if (creditTarget == null && history.isNotEmpty()) (direction * widthPx * backProgress).roundToInt() else 0
        Box(Modifier.fillMaxSize().offset { IntOffset(offsetX, 0) }) {
            when (val current = state) {
                MediaDetailState.Loading -> DetailLoading { goBack() }
                is MediaDetailState.Error -> DetailError(current.message, { goBack() }) { vm.load(currentSummary) }
                is MediaDetailState.Ready -> {
                    if (current.media.summary.id != currentSummary.id || current.media.summary.type != currentSummary.type) {
                        DetailLoading { goBack() }
                    } else {
                        DetailContent(
                            current.media,
                            current.isFavorite,
                            { goBack() },
                            { relation ->
                                history = history + MediaHistoryEntry(currentSummary, current.media)
                                currentSummary = relation
                            },
                            { trackingVm.open(it) },
                            vm::toggleFavorite,
                        ) { creditTarget = it }
                    }
                }
            }
        }

        creditTarget?.let { target ->
            val creditOffset = (direction * widthPx * backProgress).roundToInt()
            Box(Modifier.fillMaxSize().offset { IntOffset(creditOffset, 0) }) {
                CreditDetailsScreen(
                    target,
                    { creditTarget = null },
                    onMediaClick = { id, type ->
                        currentMedia?.let { detail -> history = history + MediaHistoryEntry(currentSummary, detail) }
                        currentSummary = MediaSummary(id, type, "", null, null, null)
                        creditTarget = null
                    }
                )
            }
        }

        PredictiveBackHandler(enabled = creditTarget != null || history.isNotEmpty()) { progressFlow ->
            try {
                progressFlow.collect { event ->
                    backProgress = event.progress
                    backSwipeEdge = event.swipeEdge
                }
                if (creditTarget != null) {
                    creditTarget = null
                } else {
                    goBack()
                }
                backProgress = 0f
            } catch (e: CancellationException) {
                backProgress = 0f
                throw e
            }
        }
    }

    when (val tracking = trackingState) {
        MediaTrackingState.Hidden -> Unit
        MediaTrackingState.Loading -> TrackingLoadingDialog()
        is MediaTrackingState.Ready -> MediaTrackingDialog(tracking.entry, tracking.entry.media.type, tracking.scoreFormat, tracking.saving, tracking.error, { trackingVm.dismiss() }) { status, progress, score -> trackingVm.save(tracking.entry, status, progress, score) }
        is MediaTrackingState.Error -> TrackingErrorDialog(tracking.message, { trackingVm.dismiss() }) { trackingVm.retry(currentSummary) }
    }
}

private data class MediaHistoryEntry(val summary: MediaSummary, val detail: MediaDetail)

@Composable private fun TrackingLoadingDialog() { AlertDialog(onDismissRequest = {}, title = { Text("AniList tracking") }, text = { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }, confirmButton = {}) }
@Composable private fun TrackingErrorDialog(message: String, onDismiss: () -> Unit, onRetry: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("AniList tracking") }, text = { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }, confirmButton = { Button(onClick = onRetry) { Text("Retry") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }
@Composable private fun DetailLoading(onBack: () -> Unit) { Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { IconButton(onClick = onBack, modifier = Modifier.padding(12.dp)) { Icon(Icons.Outlined.ArrowBack, "Back") }; Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } } }
@Composable private fun DetailError(message: String, onBack: () -> Unit, onRetry: () -> Unit) { Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { IconButton(onClick = onBack, modifier = Modifier.padding(12.dp)) { Icon(Icons.Outlined.ArrowBack, "Back") }; Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(16.dp)); Button(onClick = onRetry) { Text("Retry") } } } }
@Composable private fun DetailContent(media: MediaDetail, isFavorite: Boolean, onBack: () -> Unit, onOpenRelation: (MediaSummary) -> Unit, onOpenTracking: (MediaSummary) -> Unit, onToggleFavorite: () -> Unit, onOpenCredit: (CreditTarget) -> Unit) { LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(bottom = 36.dp)) { item { Box(Modifier.fillMaxWidth().aspectRatio(3f).background(MaterialTheme.colorScheme.surfaceVariant)) { media.bannerUrl?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }; IconButton(onClick = onBack, modifier = Modifier.padding(12.dp).align(Alignment.TopStart).background(MaterialTheme.colorScheme.surface.copy(alpha = .86f), RoundedCornerShape(50))) { Icon(Icons.Outlined.ArrowBack, "Back") } } }; item { MediaIdentity(media, isFavorite, onOpenTracking, onToggleFavorite) }; item { InfoStrip(media) }; media.description?.takeIf { it.isNotBlank() }?.let { description -> item { DetailSection("The story") { Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge) } } }; if (media.genres.isNotEmpty()) item { DetailSection("Genres") { TagRow(media.genres) } }; if (media.tags.isNotEmpty()) item { DetailSection("Themes & tags") { TagRow(media.tags.take(16)) } }; if (media.characters.isNotEmpty()) item { CastSection(media.characters, onOpenCredit) }; if (media.staff.isNotEmpty()) item { StaffSection(media.staff, onOpenCredit) }; if (media.studios.isNotEmpty()) item { DetailSection("Production") { ProductionBlock(media.studios) } }; if (media.relations.isNotEmpty()) { item { DetailSection("Universe") { Text("Connected stories and editions", color = MaterialTheme.colorScheme.onSurfaceVariant) } }; items(media.relations.take(12), key = { "relation-${it.relationType}-${it.media.id}" }) { relation -> RelationCard(relation, onOpenRelation) } } } }
@Composable private fun MediaIdentity(media: MediaDetail, isFavorite: Boolean, onOpenTracking: (MediaSummary) -> Unit, onToggleFavorite: () -> Unit) { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp), verticalAlignment = Alignment.Bottom) { Box(Modifier.width(126.dp).aspectRatio(.7f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) { media.summary.coverUrl?.let { AsyncImage(model = it, contentDescription = media.summary.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }; Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f).padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(media.summary.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis); Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { MetaPill(if (media.summary.type.name == "ANIME") "ANIME" else "MANGA", true); media.status?.let { MetaPill(it.replace('_', ' ').uppercase(), false) } }; media.summary.averageScore?.let { score -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Icon(Icons.Outlined.Star, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary); Text("${score / 10f}", fontWeight = FontWeight.Bold); Text("/ 10", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) } } } }; Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Button(onClick = { onOpenTracking(media.summary) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Outlined.BookmarkAdd, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Track on AniList") }; IconButton(onClick = onToggleFavorite) { Icon(if (isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, if (isFavorite) "Remove from Hikari favorites" else "Add to Hikari favorites", tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun InfoStrip(media: MediaDetail) { LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) { media.format?.let { item { StatPill("FORMAT", it.replace('_', ' ')) } }; media.summary.episodesOrChapters?.let { value -> item { StatPill(if (media.summary.type.name == "ANIME") "EPISODES" else "CHAPTERS", value.toString()) } }; media.duration?.let { item { StatPill("RUNTIME", "${it}m") } }; media.seasonYear?.let { year -> item { StatPill("SEASON", listOfNotNull(media.season, year.toString()).joinToString(" ")) } }; media.startDate?.let { item { StatPill("STARTED", it) } }; media.endDate?.let { item { StatPill("ENDED", it) } }; media.popularity?.let { item { StatPill("POPULARITY", compactNumber(it)) } }; media.favourites?.let { item { StatPill("ANILIST FAVORITES", compactNumber(it)) } } } }
@Composable private fun StatPill(label: String, value: String) { Column(Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) } }
@Composable private fun MetaPill(text: String, accent: Boolean) { Text(text, Modifier.clip(RoundedCornerShape(8.dp)).background(if (accent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 7.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable private fun CastSection(characters: List<MediaCharacter>, onOpenCredit: (CreditTarget) -> Unit) { DetailSection("Cast") { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(characters, key = { it.id }) { character -> Column(Modifier.width(132.dp).clickable { onOpenCredit(CreditTarget(character.id, CreditType.CHARACTER)) }, verticalArrangement = Arrangement.spacedBy(7.dp)) { Box(Modifier.fillMaxWidth().height(178.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) { character.imageUrl?.let { AsyncImage(model = it, contentDescription = character.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }; Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = .82f)).padding(8.dp)) { Text(character.role, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } }; Text(character.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); character.voiceActorName?.let { Text("VA · $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) } } } } } }
@Composable private fun StaffSection(staff: List<MediaStaff>, onOpenCredit: (CreditTarget) -> Unit) { DetailSection("Key staff") { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(staff, key = { it.id }) { person -> Column(Modifier.width(118.dp).clickable { onOpenCredit(CreditTarget(person.id, CreditType.PERSON)) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) { Box(Modifier.size(92.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) { person.imageUrl?.let { AsyncImage(model = it, contentDescription = person.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }; Text(person.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(person.roles.joinToString(" • "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) } } } } }
@Composable private fun ProductionBlock(studios: List<String>) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(18.dp)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }; Spacer(Modifier.width(12.dp)); Column { Text("Studios", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text(studios.joinToString(" • "), fontWeight = FontWeight.SemiBold) } } } }
@Composable private fun DetailSection(title: String, content: @Composable () -> Unit) { Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); content() } }
@Composable private fun TagRow(tags: List<String>) { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(tags) { tag -> Text(tag, Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1) } } }
@Composable private fun RelationCard(relation: MediaRelation, onOpenRelation: (MediaSummary) -> Unit) { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onOpenRelation(relation.media) }.padding(9.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(58.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surface)) { relation.media.coverUrl?.let { AsyncImage(model = it, contentDescription = relation.media.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }; Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(relation.relationType.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Text(relation.media.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(if (relation.media.type.name == "ANIME") "Anime" else "Manga", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) } } }
private fun compactNumber(value: Int): String = when { value >= 1_000_000 -> "${value / 1_000_000}.${(value / 100_000) % 10}M"; value >= 1_000 -> "${value / 1_000}.${(value / 100) % 10}K"; else -> value.toString() }
