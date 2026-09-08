package app.hikari.profile

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val PROFILE_PICTURE_PREFS = "hikari_profile_picture"
private const val PROFILE_PICTURE_PATH = "path"

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
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var localAvatarPath by remember { mutableStateOf(loadProfilePicturePath(context)) }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) {
                    scope.launch {
                        val savedPath = withContext(Dispatchers.IO) { saveProfilePicture(context, uri, localAvatarPath) }
                        localAvatarPath = savedPath
                    }
                }
            }
            val avatarModel = localAvatarPath?.let(::File) ?: profile.avatarUrl

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)) {
                    profile.bannerUrl?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Crop) }
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp)
                            .size(74.dp)
                            .clip(CircleShape)
                            .background(if (avatarModel == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { picker.launch("image/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarModel != null) {
                            AsyncImage(model = avatarModel, contentDescription = "Change profile picture", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Crop)
                        } else {
                            Text("H", color = MaterialTheme.colorScheme.onPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        }
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

private suspend fun saveProfilePicture(context: Context, uri: android.net.Uri, previousPath: String?): String? {
    val resolver = context.contentResolver
    val extension = when (resolver.getType(uri)) {
        "image/gif" -> "gif"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/jpeg" -> "jpg"
        else -> "img"
    }
    val file = File(context.filesDir, "profile_picture_${System.currentTimeMillis()}.$extension")
    return runCatching {
        resolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } } ?: return null
        context.getSharedPreferences(PROFILE_PICTURE_PREFS, Context.MODE_PRIVATE).edit().putString(PROFILE_PICTURE_PATH, file.absolutePath).apply()
        previousPath?.let { File(it).delete() }
        file.absolutePath
    }.getOrNull()
}

private fun loadProfilePicturePath(context: Context): String? = context
    .getSharedPreferences(PROFILE_PICTURE_PREFS, Context.MODE_PRIVATE)
    .getString(PROFILE_PICTURE_PATH, null)
    ?.takeIf { File(it).exists() }

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
