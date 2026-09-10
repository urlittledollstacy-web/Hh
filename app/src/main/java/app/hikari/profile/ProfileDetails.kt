package app.hikari.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage

@Composable
fun ProfileDetails(vm: ProfileViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    when {
        state.loading -> Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.profile != null -> {
            val profile = state.profile!!
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)) {
                    profile.bannerUrl?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Crop) }
                    Box(
                        Modifier.align(Alignment.BottomStart).padding(14.dp).size(74.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        profile.avatarUrl?.let { AsyncImage(model = it, contentDescription = profile.name, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Crop) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("AniList ID ${profile.id}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = vm::refresh, enabled = !state.refreshing) {
                        if (state.refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Refresh, "Refresh profile")
                    }
                }
                profile.about?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4) }
                Text("Anime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                StatsCard(
                    listOf(
                        "Titles" to profile.animeCount.toString(),
                        "Episodes" to profile.episodesWatched.toString(),
                        "Days watched" to formatDays(profile.daysWatched),
                        "Mean score" to formatScore(profile.animeMeanScore),
                    )
                )
                Text("Manga", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                StatsCard(
                    listOf(
                        "Titles" to profile.mangaCount.toString(),
                        "Chapters" to profile.chaptersRead.toString(),
                        "Volumes" to profile.volumesRead.toString(),
                        "Mean score" to formatScore(profile.mangaMeanScore),
                    )
                )
            }
        }
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun StatsCard(stats: List<Pair<String, String>>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            stats.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            Text(value, fontWeight = FontWeight.Bold)
                            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun formatDays(days: Double): String = if (days >= 100 || days % 1.0 == 0.0) "${days.toInt()}d" else "%.1fd".format(days)
private fun formatScore(score: Double): String = if (score == 0.0) "—" else "%.1f".format(score)
