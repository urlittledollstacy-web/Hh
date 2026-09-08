package app.hikari.media

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkAdd
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
import app.hikari.data.remote.AniListLibraryService
import app.hikari.data.remote.AniListMediaDetailService
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MediaDetailState { data object Loading : MediaDetailState; data class Ready(val media: MediaDetail) : MediaDetailState; data class Error(val message: String) : MediaDetailState }
@HiltViewModel class MediaDetailsViewModel @Inject constructor(private val service: AniListMediaDetailService) : ViewModel() { private val _state = MutableStateFlow<MediaDetailState>(MediaDetailState.Loading); val state: StateFlow<MediaDetailState> = _state.asStateFlow(); fun load(id: Int) { viewModelScope.launch { _state.value = MediaDetailState.Loading; _state.value = runCatching { service.detail(id) }.fold({ MediaDetailState.Ready(it) }, { MediaDetailState.Error("Couldn't load this title. Try again.") }) } } }
sealed interface MediaTrackingState { data object Hidden : MediaTrackingState; data object Loading : MediaTrackingState; data class Ready(val entry: LibraryEntry, val scoreFormat: ScoreFormat, val saving: Boolean = false, val error: String? = null) : MediaTrackingState; data class Error(val message: String) : MediaTrackingState }
@HiltViewModel class MediaTrackingViewModel @Inject constructor(private val service: AniListLibraryService) : ViewModel() { private val _state = MutableStateFlow<MediaTrackingState>(MediaTrackingState.Hidden); val state: StateFlow<MediaTrackingState> = _state.asStateFlow(); fun open(media: MediaSummary) { viewModelScope.launch { _state.value = MediaTrackingState.Loading; runCatching { service.library(media.type) }.fold({ snapshot -> _state.value = MediaTrackingState.Ready(snapshot.entries.firstOrNull { it.media.id == media.id } ?: LibraryEntry(0, media, "PLANNING", 0, null), snapshot.scoreFormat) }, { _state.value = MediaTrackingState.Error("Couldn't load your AniList list. Make sure you're signed in and try again.") }) } }; fun dismiss() { _state.value = MediaTrackingState.Hidden }; fun save(entry: LibraryEntry, status: String, progress: Int, score: Double) { val current = _state.value as? MediaTrackingState.Ready ?: return; _state.value = current.copy(saving = true, error = null); viewModelScope.launch { runCatching { service.updateEntry(entry, status, progress, score) }.fold({ _state.value = MediaTrackingState.Hidden }, { _state.value = current.copy(saving = false, error = "Couldn't save your AniList changes. Try again.") }) } }; fun retry(media: MediaSummary) = open(media) }

@Composable fun MediaDetailsScreen(summary: MediaSummary, onBack: () -> Unit, vm: MediaDetailsViewModel = hiltViewModel(), trackingVm: MediaTrackingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState(); val trackingState by trackingVm.state.collectAsState(); var currentSummary by remember(summary.id) { mutableStateOf(summary) }; var history by remember(summary.id) { mutableStateOf(emptyList<MediaSummary>()) }; var creditTarget by remember(summary.id) { mutableStateOf<CreditTarget?>(null) }
    LaunchedEffect(currentSummary.id) { vm.load(currentSummary.id) }; val goBack = { if (history.isNotEmpty()) { currentSummary = history.last(); history = history.dropLast(1) } else onBack() }; val openRelation: (MediaSummary) -> Unit = { relation -> history = history + currentSummary; currentSummary = relation }
    creditTarget?.let { target -> CreditDetailsScreen(target, { creditTarget = null }) { id, type -> history = history + currentSummary; currentSummary = MediaSummary(id, type, "", null, null, null); creditTarget = null }; return }
    when (val current = state) { MediaDetailState.Loading -> DetailLoading { goBack() }; is MediaDetailState.Error -> DetailError(current.message, { goBack() }) { vm.load(currentSummary.id) }; is MediaDetailState.Ready -> DetailContent(current.media, { goBack() }, openRelation, { trackingVm.open(it) }) { creditTarget = it } }
    when (val tracking = trackingState) { MediaTrackingState.Hidden -> Unit; MediaTrackingState.Loading -> TrackingLoadingDialog(); is MediaTrackingState.Ready -> MediaTrackingDialog(tracking.entry, tracking.entry.media.type, tracking.scoreFormat, tracking.saving, tracking.error, { trackingVm.dismiss() }) { status, progress, score -> trackingVm.save(tracking.entry, status, progress, score) }; is MediaTrackingState.Error -> TrackingErrorDialog(tracking.message, { trackingVm.dismiss() }) { trackingVm.retry(currentSummary) } }
}
@Composable private fun TrackingLoadingDialog() { AlertDialog(onDismissRequest = {}, title = { Text("AniList tracking") }, text = { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }, confirmButton = {}) }
@Composable private fun TrackingErrorDialog(message: String, onDismiss: () -> Unit, onRetry: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("AniList tracking") }, text = { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }, confirmButton = { Button(onClick = onRetry) { Text("Retry") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }
@Composable private fun DetailLoading(onBack: () -> Unit) { Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { IconButton(onClick = onBack, modifier = Modifier.padding(12.dp)) { Icon(Icons.Outlined.ArrowBack, "Back") }; Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } } }
@Composable private fun DetailError(message: String, onBack: () -> Unit, onRetry: () -> Unit) { Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { IconButton(onClick = onBack, modifier = Modifier.padding(12.dp)) { Icon(Icons.Outlined.ArrowBack, "Back") }; Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(16.dp)); Button(onClick = onRetry) { Text("Retry") } } } }
@Composable private fun DetailContent(media: MediaDetail, onBack: () -> Unit, onOpenRelation: (MediaSummary) -> Unit, onOpenTracking: (MediaSummary) -> Unit, onOpenCredit: (CreditTarget) -> Unit) { LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(bottom = 36.dp)) { item { Box(Modifier.fillMaxWidth().aspectRatio(3f).background(MaterialTheme.colorScheme.surfaceVariant)) { media.bannerUrl?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }; IconButton(onClick = onBack, modifier = Modifier.padding(12.dp).align(Alignment.TopStart).background(MaterialTheme.colorScheme.surface.copy(alpha = .86f), RoundedCornerShape(50))) { Icon(Icons.Outlined.ArrowBack, "Back") } } }; item { MediaIdentity(media, onOpenTracking) }; item { InfoStrip(media) }; media.description?.takeIf { it.isNotBlank() }?.let { description -> item { DetailSection("The story") { Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge) } } }; if (media.genres.isNotEmpty()) item { DetailSection("Genres") { TagRow(media.genres) } }; if (media.tags.isNotEmpty()) item { DetailSection("Themes & tags") { TagRow(media.tags.take(16)) } }; if (media.characters.isNotEmpty()) item { CastSection(media.characters, onOpenCredit) }; if (media.staff.isNotEmpty()) item { StaffSection(media.staff, onOpenCredit) }; if (media.studios.isNotEmpty()) item { DetailSection("Production") { ProductionBlock(media.studios) } }; if (media.relations.isNotEmpty()) { item { DetailSection("Universe") { Text("Connected stories and editions", color = MaterialTheme.colorScheme.onSurfaceVariant) } }; items(media.relations.take(12), key = { "relation-${it.relationType}-${it.media.id}" }) { relation -> RelationCard(relation, onOpenRelation) } } } }
@Composable private fun MediaIdentity(media: MediaDetail, onOpenTracking: (MediaSummary) -> Unit) { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp), verticalAlignment = Alignment.Bottom) { Box(Modifier.width(126.dp).aspectRatio(.7f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) { media.summary.coverUrl?.let { AsyncImage(model = it, contentDescription = media.summary.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }; Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f).padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(media.summary.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis); Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { MetaPill(if (media.summary.type.name == "ANIME") "ANIME" else "MANGA", true); media.status?.let { MetaPill(it.replace('_', ' ').uppercase(), false) } }; media.summary.averageScore?.let { score -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Icon(Icons.Outlined.Star, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary); Text("${score / 10f}", fontWeight = FontWeight.Bold); Text("/ 10", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) } } } }; Button(onClick = { onOpenTracking(media.summary) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Outlined.BookmarkAdd, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Track on AniList") } }
@Composable private fun InfoStrip(media: MediaDetail) { LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) { media.format?.let { item { StatPill("FORMAT", it.replace('_', ' ')) } }; media.summary.episodesOrChapters?.let { value -> item { StatPill(if (media.summary.type.name == "ANIME") "EPISODES" else "CHAPTERS", value.toString()) } }; media.duration?.let { item { StatPill("RUNTIME", "${it}m") } }; media.seasonYear?.let { year -> item { StatPill("SEASON", listOfNotNull(media.season, year.toString()).joinToString(" ")) } }; media.startDate?.let { item { StatPill("STARTED", it) } }; media.endDate?.let { item { StatPill("ENDED", it) } }; media.popularity?.let { item { StatPill("POPULARITY", compactNumber(it)) } }; media.favourites?.let { item { StatPill("FAVORITES", compactNumber(it)) } } } }
@Composable private fun StatPill(label: String, value: String) { Column(Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) } }
@Composable private fun MetaPill(text: String, accent: Boolean) { Text(text, Modifier.clip(RoundedCornerShape(8.dp)).background(if (accent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 7.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable private fun CastSection(characters: List<MediaCharacter>, onOpenCredit: (CreditTarget) -> Unit) { DetailSection("Cast") { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(characters, key = { it.id }) { character -> Column(Modifier.width(132.dp).clickable { onOpenCredit(CreditTarget(character.id, CreditType.CHARACTER)) }, verticalArrangement = Arrangement.spacedBy(7.dp)) { Box(Modifier.fillMaxWidth().height(178.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) { character.imageUrl?.let { AsyncImage(model = it, contentDescription = character.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }; Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = .82f)).padding(8.dp)) { Text(character.role, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } }; Text(character.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); character.voiceActorName?.let { Text("VA · $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) } } } } } }
@Composable private fun StaffSection(staff: List<MediaStaff>, onOpenCredit: (CreditTarget) -> Unit) { DetailSection("Key staff") { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(staff, key = { it.id }) { person -> Column(Modifier.width(118.dp).clickable { onOpenCredit(CreditTarget(person.id, CreditType.PERSON)) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) { Box(Modifier.size(92.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) { person.imageUrl?.let { AsyncImage(model = it, contentDescription = person.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }; Text(person.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(person.roles.joinToString(" • "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) } } } } }
@Composable private fun ProductionBlock(studios: List<String>) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(18.dp)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }; Spacer(Modifier.width(12.dp)); Column { Text("Studios", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text(studios.joinToString(" • "), fontWeight = FontWeight.SemiBold) } } } }
@Composable private fun DetailSection(title: String, content: @Composable () -> Unit) { Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); content() } }
@Composable private fun TagRow(tags: List<String>) { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(tags) { tag -> Text(tag, Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1) } } }
@Composable private fun RelationCard(relation: MediaRelation, onOpenRelation: (MediaSummary) -> Unit) { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onOpenRelation(relation.media) }.padding(9.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(58.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surface)) { relation.media.coverUrl?.let { AsyncImage(model = it, contentDescription = relation.media.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }; Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(relation.relationType.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Text(relation.media.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(if (relation.media.type.name == "ANIME") "Anime" else "Manga", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) } } }
private fun compactNumber(value: Int): String = when { value >= 1_000_000 -> "${value / 1_000_000}.${(value / 100_000) % 10}M"; value >= 1_000 -> "${value / 1_000}.${(value / 100) % 10}K"; else -> value.toString() }
