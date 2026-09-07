package app.hikari.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hikari.core.model.LibraryEntry
import app.hikari.core.model.MediaType
import app.hikari.core.model.ScoreFormat
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

@Composable
fun MediaTrackingDialog(
    entry: LibraryEntry,
    type: MediaType,
    scoreFormat: ScoreFormat,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, Int, Double) -> Unit,
) {
    var selectedStatus by remember(entry.id, entry.status) { mutableStateOf(entry.status) }
    var progress by remember(entry.id, entry.progress) { mutableStateOf(entry.progress.toFloat()) }
    var score by remember(entry.id, entry.score, scoreFormat) { mutableStateOf((entry.score ?: 0.0).toFloat()) }
    val maxProgress = entry.media.episodesOrChapters?.coerceAtLeast(0) ?: 0
    val safeProgress = if (maxProgress > 0) progress.coerceIn(0f, maxProgress.toFloat()) else progress.coerceAtLeast(0f)
    val scoreConfig = scoreConfig(scoreFormat)
    val safeScore = score.coerceIn(0f, scoreConfig.max)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (entry.id > 0) "Edit ${entry.media.title}" else "Add ${entry.media.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                entry.media.coverUrl?.let {
                    AsyncImage(model = it, contentDescription = entry.media.title, modifier = Modifier.fillMaxWidth().size(170.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
                }
                Text(if (type == MediaType.ANIME) "Anime tracking" else "Manga tracking", color = MaterialTheme.colorScheme.primary)
                Text("Status", fontWeight = FontWeight.SemiBold)
                ChoiceRow(selectedStatus) { selectedStatus = it }
                Text(if (type == MediaType.ANIME) "Episodes watched" else "Chapters read", fontWeight = FontWeight.SemiBold)
                if (maxProgress > 0) {
                    Slider(value = safeProgress, onValueChange = { progress = it.roundToInt().toFloat() }, valueRange = 0f..maxProgress.toFloat(), steps = (maxProgress - 1).coerceAtLeast(0))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { progress = (safeProgress - 1f).coerceAtLeast(0f) }) { Icon(Icons.Outlined.Remove, "Decrease progress") }
                        Text(safeProgress.roundToInt().toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                        IconButton(onClick = { progress += 1f }) { Icon(Icons.Outlined.Add, "Increase progress") }
                    }
                }
                Text("${safeProgress.roundToInt()} ${if (type == MediaType.ANIME) "episodes" else "chapters"}${maxProgress.takeIf { it > 0 }?.let { " / $it" } ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text("Your score", fontWeight = FontWeight.SemiBold)
                Slider(value = safeScore, onValueChange = { score = snapScore(it, scoreFormat) }, valueRange = 0f..scoreConfig.max, steps = scoreConfig.steps)
                Text(scoreDisplay(safeScore.toDouble(), scoreFormat), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text(scoreConfig.helper, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedStatus, safeProgress.roundToInt(), safeScore.toDouble()) }, enabled = !saving) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save to AniList")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } },
    )
}

@Composable
private fun ChoiceRow(selected: String, onSelect: (String) -> Unit) {
    val options = listOf("CURRENT", "PLANNING", "COMPLETED", "REPEATING", "PAUSED", "DROPPED")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { option ->
            val active = option == selected
            Box(Modifier.clip(RoundedCornerShape(18.dp)).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).clickable { onSelect(option) }.padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text(optionLabel(option), color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
            }
        }
    }
}

private fun optionLabel(value: String): String = when (value) {
    "CURRENT" -> "Watching"
    "PLANNING" -> "Planning"
    "REPEATING" -> "Rewatching"
    "COMPLETED" -> "Completed"
    "PAUSED" -> "Paused"
    "DROPPED" -> "Dropped"
    else -> value.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private data class ScoreConfig(val max: Float, val steps: Int, val helper: String)

private fun scoreConfig(format: ScoreFormat): ScoreConfig = when (format) {
    ScoreFormat.POINT_100 -> ScoreConfig(100f, 99, "100 point · drag to choose")
    ScoreFormat.POINT_10_DECIMAL -> ScoreConfig(10f, 19, "10 point decimal · 0.5 steps")
    ScoreFormat.POINT_10 -> ScoreConfig(10f, 9, "10 point · whole numbers")
    ScoreFormat.POINT_5 -> ScoreConfig(5f, 4, "5 star · AniList setting")
    ScoreFormat.POINT_3 -> ScoreConfig(3f, 2, "3 point smiley · AniList setting")
}

private fun snapScore(value: Float, format: ScoreFormat): Float = when (format) {
    ScoreFormat.POINT_10_DECIMAL -> (value * 2f).roundToInt() / 2f
    else -> value.roundToInt().toFloat()
}

private fun scoreDisplay(score: Double, format: ScoreFormat): String = when (format) {
    ScoreFormat.POINT_100 -> "${formatScore(score)} / 100"
    ScoreFormat.POINT_10_DECIMAL -> "${"%.1f".format(score)} / 10"
    ScoreFormat.POINT_10 -> "${formatScore(score)} / 10"
    ScoreFormat.POINT_5 -> "${"★".repeat(score.roundToInt())}${"☆".repeat((5 - score.roundToInt()).coerceAtLeast(0))}  ${score.roundToInt()} / 5"
    ScoreFormat.POINT_3 -> when (score.roundToInt()) {
        1 -> ":(  1 / 3"
        2 -> ":|  2 / 3"
        3 -> ":)  3 / 3"
        else -> "Not rated"
    }
}

private fun formatScore(score: Double): String = if (score % 1.0 == 0.0) score.toInt().toString() else String.format("%.1f", score)
