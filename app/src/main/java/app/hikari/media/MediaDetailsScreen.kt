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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import app.hikari.core.model.MediaDetail
import app.hikari.core.model.MediaRelation
import app.hikari.core.model.MediaSummary
import app.hikari.data.remote.AniListMediaDetailService
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MediaDetailState {
    data object Loading : MediaDetailState
    data class Ready(val media: MediaDetail) : MediaDetailState
    data class Error(val message: String) : MediaDetailState
}

@HiltViewModel
class MediaDetailsViewModel @Inject constructor(
    private val service: AniListMediaDetailService,
) : ViewModel() {
    private val _state = MutableStateFlow<MediaDetailState>(MediaDetailState.Loading)
    val state: StateFlow<MediaDetailState> = _state.asStateFlow()

    fun load(id: Int) {
        viewModelScope.launch {
            _state.value = MediaDetailState.Loading
            _state.value = runCatching { service.detail(id) }
                .fold({ MediaDetailState.Ready(it) }, { MediaDetailState.Error("Couldn't load this title. Try again.") })
        }
    }
}

@Composable
fun MediaDetailsScreen(summary: MediaSummary, onBack: () -> Unit, vm: MediaDetailsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    var currentSummary by remember(summary.id) { mutableStateOf(summary) }
    var history by remember(summary.id) { mutableStateOf(emptyList<MediaSummary>()) }

    LaunchedEffect(currentSummary.id) { vm.load(currentSummary.id) }

    val goBack = {
        if (history.isNotEmpty()) {
            currentSummary = history.last()
            history = history.dropLast(1)
        } else {
            onBack()
        }
    }
    val openRelation: (MediaSummary) -> Unit = { relation ->
        history = history + currentSummary
        currentSummary = relation
    }

    when (val current = state) {
        MediaDetailState.Loading -> DetailLoading { goBack() }
        is MediaDetailState.Error -> DetailError(current.message, { goBack() }) { vm.load(currentSummary.id) }
        is MediaDetailState.Ready -> DetailContent(current.media, { goBack() }) { relation -> openRelation(relation) }
    }
}

@Composable
private fun DetailLoading(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        IconButton(onClick = onBack, modifier = Modifier.padding(12.dp)) { Icon(Icons.Outlined.ArrowBack, "Back") }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
private fun DetailError(message: String, onBack: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        IconButton(onClick = onBack, modifier = Modifier.padding(12.dp)) { Icon(Icons.Outlined.ArrowBack, "Back") }
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun DetailContent(media: MediaDetail, onBack: () -> Unit, onOpenRelation: (MediaSummary) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().aspectRatio(3f).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    media.bannerUrl?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    IconButton(onClick = onBack, modifier = Modifier.padding(12.dp).align(Alignment.TopStart).background(MaterialTheme.colorScheme.surface.copy(alpha = .85f), RoundedCornerShape(50))) { Icon(Icons.Outlined.ArrowBack, "Back") }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.width(126.dp).aspectRatio(.7f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).offset(y = (-60).dp)) {
                        media.summary.coverUrl?.let { AsyncImage(model = it, contentDescription = media.summary.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f).padding(top = 12.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(media.summary.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        Text(if (media.summary.type.name == "ANIME") "Anime" else "Manga", color = MaterialTheme.colorScheme.primary)
                        media.summary.averageScore?.let { Text("★ $it%", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
        item { Button(onClick = {}, modifier = Modifier.fillMaxWidth().padding(20.dp)) { Text("Add to List") } }
        item { InfoGrid(media) }
        media.description?.takeIf { it.isNotBlank() }?.let { description -> item { DetailSection("Description") { Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        if (media.genres.isNotEmpty()) item { DetailSection("Genres") { TagRow(media.genres) } }
        if (media.tags.isNotEmpty()) item { DetailSection("Tags") { TagRow(media.tags.take(12)) } }
        if (media.studios.isNotEmpty()) item { DetailSection("Studios") { Text(media.studios.joinToString(" • ")) } }
        if (media.relations.isNotEmpty()) {
            item { DetailSection("Relations") { Text("Connected anime and manga", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            items(media.relations.take(12), key = { it.media.id }) { relation -> RelationCard(relation, onOpenRelation) }
        }
    }
}

@Composable
private fun InfoGrid(media: MediaDetail) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            media.format?.let { InfoLine("Format", it) }
            media.status?.let { InfoLine("Status", it) }
            media.summary.episodesOrChapters?.let { InfoLine(if (media.summary.type.name == "ANIME") "Episodes" else "Chapters", it.toString()) }
            media.duration?.let { InfoLine("Duration", "$it min") }
            media.seasonYear?.let { InfoLine("Season", listOfNotNull(media.season, it.toString()).joinToString(" ")) }
            media.startDate?.let { InfoLine("Started", it) }
            media.endDate?.let { InfoLine("Ended", it) }
            media.popularity?.let { InfoLine("Popularity", it.toString()) }
            media.favourites?.let { InfoLine("Favorites", it.toString()) }
        }
    }
}

@Composable private fun InfoLine(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.Medium) } }
@Composable private fun DetailSection(title: String, content: @Composable () -> Unit) { Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); content() } }
@Composable private fun TagRow(tags: List<String>) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { tags.forEach { tag -> Text(tag, Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 6.dp), fontSize = MaterialTheme.typography.labelMedium.fontSize) } } }

@Composable
private fun RelationCard(relation: MediaRelation, onOpenRelation: (MediaSummary) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onOpenRelation(relation.media) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surface)) {
            relation.media.coverUrl?.let { AsyncImage(model = it, contentDescription = relation.media.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(relation.relationType.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(relation.media.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (relation.media.type.name == "ANIME") "Anime" else "Manga", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
