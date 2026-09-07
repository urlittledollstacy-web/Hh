package app.hikari

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CompassCalibration
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hikari.core.model.MediaSummary
import app.hikari.core.model.MediaType
import app.hikari.data.remote.AniListGraphQlService
import coil3.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HikariApp() }
    }
}

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Discover("Discover", Icons.Outlined.CompassCalibration),
    Library("Library", Icons.Outlined.BookmarkBorder),
    Calendar("Calendar", Icons.Outlined.CalendarMonth),
    Profile("Profile", Icons.Outlined.PersonOutline),
}

private enum class AppTheme { System, Amoled, Aurora }
private enum class NavigationStyle { Blur, Liquid, Off }

data class HomeUiState(
    val trending: List<MediaSummary> = emptyList(),
    val airing: List<MediaSummary> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: AniListGraphQlService,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true, error = null)
            try {
                val trending = async { api.trending(MediaType.ANIME) }
                val airing = async { api.airingSoon() }
                _state.value = HomeUiState(
                    trending = trending.await(),
                    airing = airing.await(),
                    loading = false,
                    refreshing = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    error = e.message ?: "Unable to load AniList data",
                )
            }
        }
    }
}

@Composable
private fun HikariApp() {
    var destination by remember { mutableStateOf(Destination.Home) }
    var theme by remember { mutableStateOf(AppTheme.Aurora) }
    var navigationStyle by remember { mutableStateOf(NavigationStyle.Blur) }
    val colors = hikariColors(theme)

    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                val expanded = maxWidth >= 700.dp
                if (expanded) {
                    Row(Modifier.fillMaxSize()) {
                        NavigationRail(destination, { destination = it }, navigationStyle)
                        AppContent(destination, theme, navigationStyle, { theme = it }, { navigationStyle = it }, PaddingValues(0.dp))
                    }
                } else {
                    Box(Modifier.fillMaxSize()) {
                        AppContent(destination, theme, navigationStyle, { theme = it }, { navigationStyle = it }, PaddingValues(bottom = 104.dp))
                        FloatingNavigationBar(
                            selected = destination,
                            onDestination = { destination = it },
                            style = navigationStyle,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 18.dp, vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppContent(
    destination: Destination,
    theme: AppTheme,
    navigationStyle: NavigationStyle,
    onTheme: (AppTheme) -> Unit,
    onNavigationStyle: (NavigationStyle) -> Unit,
    contentPadding: PaddingValues,
) {
    when (destination) {
        Destination.Home -> HomeScreen(contentPadding)
        Destination.Discover -> PlaceholderScreen("Discover", "CURATED FOR YOU", "Browse trending, seasonal, popular, and top-rated anime and manga.", "Explore anime", contentPadding)
        Destination.Library -> PlaceholderScreen("Your library", "ANILIST SYNC", "Sign in to keep your watching and reading progress organized across AniList.", "Sign in with AniList", contentPadding)
        Destination.Calendar -> PlaceholderScreen("Airing calendar", "THIS WEEK", "See upcoming episodes at a glance.", "View today's airing", contentPadding)
        Destination.Profile -> ProfileScreen(theme, navigationStyle, onTheme, onNavigationStyle, contentPadding)
    }
}

@Composable
private fun HomeScreen(contentPadding: PaddingValues, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, top = 26.dp, end = 20.dp, bottom = contentPadding.calculateBottomPadding() + 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Good evening", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Find your next favorite.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Text("H", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { SearchPrompt() }
        if (state.error != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(state.error ?: "Error", Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = vm::refresh) { Text("Retry") }
                    }
                }
            }
        }
        item {
            SectionTitle("Airing soon", "See all")
        }
        item {
            if (state.loading) LoadingRow() else if (state.airing.isEmpty()) EmptyMessage("No upcoming episodes found.") else MediaRow(state.airing, airing = true)
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Trending now", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::refresh, enabled = !state.refreshing) {
                    if (state.refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Refresh, "Refresh")
                }
            }
        }
        item {
            if (state.loading) LoadingRow() else if (state.trending.isEmpty()) EmptyMessage("No trending anime found.") else MediaRow(state.trending)
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) { Box(Modifier.width(126.dp).aspectRatio(.7f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
private fun SearchPrompt() {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text("Search anime, manga, people...", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(title: String, action: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun MediaRow(media: List<MediaSummary>, airing: Boolean = false) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 12.dp)) {
        items(media, key = { it.id }) { item ->
            Column(Modifier.width(126.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(.7f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    if (item.coverUrl != null) {
                        AsyncImage(model = item.coverUrl, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                val detail = when {
                    airing && item.episodesOrChapters != null -> "Episode ${item.episodesOrChapters}"
                    item.averageScore != null -> "★ ${item.averageScore}%"
                    else -> "AniList"
                }
                Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, eyebrow: String, description: String, action: String, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(start = 24.dp, top = 36.dp, end = 24.dp, bottom = padding.calculateBottomPadding()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(eyebrow, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text(action) }
    }
}

@Composable
private fun ProfileScreen(theme: AppTheme, navigationStyle: NavigationStyle, onTheme: (AppTheme) -> Unit, onNavigationStyle: (NavigationStyle) -> Unit, contentPadding: PaddingValues) {
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, top = 28.dp, end = 20.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Profile", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item { Text("Browsing as guest", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SettingGroup("Appearance") { Text("Theme", fontWeight = FontWeight.Medium); ChoiceRow(AppTheme.entries.map { it.name }, theme.name) { onTheme(AppTheme.valueOf(it)) }; Text("Navigation", fontWeight = FontWeight.Medium); ChoiceRow(NavigationStyle.entries.map { it.name }, navigationStyle.name) { onNavigationStyle(NavigationStyle.valueOf(it)) } } }
        item { SettingGroup("Account") { SettingRow("Sign in with AniList", "Sync your library and profile") } }
        item { SettingGroup("Privacy") { SettingRow("Your data stays yours", "No ads, analytics, or streaming features") } }
    }
}

@Composable private fun SettingGroup(title: String, content: @Composable () -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() } } } }
@Composable private fun SettingRow(title: String, subtitle: String) { Column { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val active = option == selected
            Surface(modifier = Modifier.clip(CircleShape).clickable { onSelect(option) }, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, shape = CircleShape) {
                Text(option, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun FloatingNavigationBar(selected: Destination, onDestination: (Destination) -> Unit, style: NavigationStyle, modifier: Modifier = Modifier) {
    val surface = when (style) { NavigationStyle.Off -> MaterialTheme.colorScheme.surface; NavigationStyle.Blur -> MaterialTheme.colorScheme.surface.copy(alpha = .92f); NavigationStyle.Liquid -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .78f) }
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(surface).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .15f), RoundedCornerShape(28.dp)).padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        Destination.entries.forEach { NavigationItem(it, selected == it) { onDestination(it) } }
    }
}

@Composable
private fun NavigationRail(selected: Destination, onDestination: (Destination) -> Unit, style: NavigationStyle) {
    Column(Modifier.fillMaxHeight().width(92.dp).padding(12.dp).clip(RoundedCornerShape(28.dp)).background(if (style == NavigationStyle.Off) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f)).padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Destination.entries.forEach { NavigationItem(it, selected == it) { onDestination(it) } }
    }
}

@Composable
private fun NavigationItem(destination: Destination, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(destination.icon, destination.label, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(25.dp))
        Text(destination.label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun hikariColors(theme: AppTheme) = darkColorScheme(
    primary = if (theme == AppTheme.Amoled) Color(0xFFD2C1FF) else Color(0xFFC5B3FF),
    onPrimary = Color(0xFF2A1750),
    background = Color.Black,
    surface = Color(0xFF0C0C0F),
    surfaceVariant = Color(0xFF151519),
    onSurface = Color(0xFFF1EDF5),
    onSurfaceVariant = Color(0xFFBDB8C5),
    outline = Color(0xFF5A5660),
    errorContainer = Color(0xFF4A171A),
    onErrorContainer = Color(0xFFFFDAD6),
)
