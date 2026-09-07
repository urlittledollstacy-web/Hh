package app.hikari

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.hikari.core.auth.AniListAuthManager
import app.hikari.core.auth.AuthCallbackResult
import app.hikari.core.auth.AuthStartResult
import app.hikari.core.auth.SecureTokenStore
import app.hikari.core.model.MediaSummary
import app.hikari.core.model.MediaType
import app.hikari.data.remote.AniListGraphQlService
import app.hikari.library.LibraryScreen
import app.hikari.profile.ProfileDetails
import coil3.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var authManager: AniListAuthManager
    @Inject lateinit var tokenStore: SecureTokenStore
    private var signedIn by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        signedIn = tokenStore.accessToken() != null
        setContent { HikariApp(signedIn, ::startAniListLogin, ::signOut) }
        handleOAuthIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun startAniListLogin() {
        when (authManager.startLogin(this)) {
            AuthStartResult.Started -> Unit
            AuthStartResult.MissingClientId -> Toast.makeText(this, "AniList login is not configured yet.", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleOAuthIntent(intent: Intent) {
        when (val result = authManager.handleCallback(intent)) {
            AuthCallbackResult.Success -> { signedIn = true; Toast.makeText(this, "Connected to AniList.", Toast.LENGTH_SHORT).show() }
            is AuthCallbackResult.Error -> Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            null -> Unit
        }
    }

    private fun signOut() {
        authManager.logout()
        signedIn = false
        Toast.makeText(this, "Signed out of AniList.", Toast.LENGTH_SHORT).show()
    }
}

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home), Discover("Discover", Icons.Outlined.CompassCalibration), Library("Library", Icons.Outlined.BookmarkBorder), Calendar("Calendar", Icons.Outlined.CalendarMonth), Profile("Profile", Icons.Outlined.PersonOutline)
}
private enum class AppTheme { System, Amoled, Aurora }
private enum class NavigationStyle { Blur, Liquid, Off }

data class HomeUiState(val trending: List<MediaSummary> = emptyList(), val airing: List<MediaSummary> = emptyList(), val loading: Boolean = true, val refreshing: Boolean = false, val error: String? = null)

@HiltViewModel
class HomeViewModel @Inject constructor(private val api: AniListGraphQlService) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    init { refresh() }
    fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(refreshing = true, error = null)
        val trending = runCatching { api.trending(MediaType.ANIME) }.getOrDefault(emptyList())
        val airing = runCatching { api.airingSoon() }.getOrDefault(emptyList())
        _state.value = HomeUiState(trending, airing, false, false, if (trending.isEmpty() && airing.isEmpty()) "AniList is unavailable right now. Try again in a moment." else null)
    }
}

data class SearchUiState(val query: String = "", val results: List<MediaSummary> = emptyList(), val searching: Boolean = false)
@HiltViewModel
class SearchViewModel @Inject constructor(private val api: AniListGraphQlService) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    fun setQuery(value: String) { _state.value = _state.value.copy(query = value) }
    fun search() = viewModelScope.launch { val q = _state.value.query.trim(); if (q.isBlank()) return@launch; _state.value = _state.value.copy(searching = true); _state.value = _state.value.copy(results = api.search(q, MediaType.ANIME), searching = false) }
}

@Composable
private fun HikariApp(signedIn: Boolean, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    var destination by remember { mutableStateOf(Destination.Home) }
    var theme by remember { mutableStateOf(AppTheme.System) }
    var navStyle by remember { mutableStateOf(NavigationStyle.Blur) }
    val colors = hikariColors(theme, isSystemInDarkTheme())
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth >= 700.dp) Row(Modifier.fillMaxSize()) {
                    NavigationRail(destination, { destination = it }, navStyle)
                    AppContent(destination, theme, navStyle, signedIn, onSignIn, onSignOut, { theme = it }, { navStyle = it }, PaddingValues(0.dp), { destination = it })
                } else Box(Modifier.fillMaxSize()) {
                    AppContent(destination, theme, navStyle, signedIn, onSignIn, onSignOut, { theme = it }, { navStyle = it }, PaddingValues(bottom = 104.dp), { destination = it })
                    BottomNavigation(destination, { destination = it }, navStyle, Modifier.align(Alignment.BottomCenter).padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AppContent(destination: Destination, theme: AppTheme, navStyle: NavigationStyle, signedIn: Boolean, onSignIn: () -> Unit, onSignOut: () -> Unit, onTheme: (AppTheme) -> Unit, onNavStyle: (NavigationStyle) -> Unit, padding: PaddingValues, onDestination: (Destination) -> Unit) {
    when (destination) {
        Destination.Home -> HomeScreen(padding, onSearch = { onDestination(Destination.Discover) })
        Destination.Discover -> DiscoverScreen(padding)
        Destination.Library -> LibraryScreen(signedIn, padding)
        Destination.Calendar -> PlaceholderScreen("Airing calendar", "THIS WEEK", "See upcoming episodes at a glance.", "View today's airing", padding)
        Destination.Profile -> ProfileScreen(theme, navStyle, signedIn, onSignIn, onSignOut, onTheme, onNavStyle, padding)
    }
}

@Composable
private fun HomeScreen(padding: PaddingValues, onSearch: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    LazyColumn(contentPadding = PaddingValues(20.dp, 26.dp, 20.dp, padding.calculateBottomPadding() + 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Good evening", color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Find your next favorite.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }; Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Text("H", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) } } }
        item { Row(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onSearch).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Search, "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(10.dp)); Text("Search anime, manga, people...", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        if (state.error != null) item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text(state.error.orEmpty(), Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer); TextButton(onClick = vm::refresh) { Text("Retry") } } } }
        item { SectionTitle("Airing soon", "See all") }
        item { if (state.loading) LoadingRow() else if (state.airing.isEmpty()) EmptyMessage("No upcoming episodes found.") else MediaRow(state.airing, true) }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Trending now", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); IconButton(onClick = vm::refresh, enabled = !state.refreshing) { if (state.refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Refresh, "Refresh") } } }
        item { if (state.loading) LoadingRow() else if (state.trending.isEmpty()) EmptyMessage("No trending anime found.") else MediaRow(state.trending) }
    }
}

@Composable
private fun DiscoverScreen(padding: PaddingValues, vm: SearchViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    LazyColumn(contentPadding = PaddingValues(20.dp, 28.dp, 20.dp, padding.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Discover", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("Search AniList", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = state.query, onValueChange = vm::setQuery, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Anime or manga") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }); Button(onClick = vm::search, enabled = state.query.isNotBlank() && !state.searching) { Text("Go") } } }
        if (state.searching) item { CircularProgressIndicator() }
        if (state.results.isEmpty() && !state.searching) item { EmptyMessage(if (state.query.isBlank()) "Try a title such as Frieren or One Piece." else "No results found.") }
        if (state.results.isNotEmpty()) item { SearchResults(state.results) }
    }
}

@Composable private fun SearchResults(media: List<MediaSummary>) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { media.forEach { item -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)) { item.coverUrl?.let { AsyncImage(model = it, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(item.averageScore?.let { "★ $it%" } ?: "AniList", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
@Composable private fun LoadingRow() { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { Box(Modifier.width(126.dp).aspectRatio(.7f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) } } }
@Composable private fun EmptyMessage(text: String) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp)) }
@Composable private fun SectionTitle(title: String, action: String? = null) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); action?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) } } }

@Composable
private fun MediaRow(media: List<MediaSummary>, airing: Boolean = false) { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(media, key = { it.id }) { item -> Column(Modifier.width(126.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Box(Modifier.fillMaxWidth().aspectRatio(.7f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) { item.coverUrl?.let { AsyncImage(model = it, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }; Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); Text(if (airing && item.episodesOrChapters != null) "Episode ${item.episodesOrChapters}" else item.averageScore?.let { "★ $it%" } ?: "AniList", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }

@Composable
private fun PlaceholderScreen(title: String, eyebrow: String, description: String, action: String, padding: PaddingValues) { Column(Modifier.fillMaxSize().padding(start = 24.dp, top = 36.dp, end = 24.dp, bottom = padding.calculateBottomPadding()), verticalArrangement = Arrangement.spacedBy(16.dp)) { Text(eyebrow, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp); Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant); Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text(action) } } }

@Composable
private fun ProfileScreen(theme: AppTheme, navStyle: NavigationStyle, signedIn: Boolean, onSignIn: () -> Unit, onSignOut: () -> Unit, onTheme: (AppTheme) -> Unit, onNavStyle: (NavigationStyle) -> Unit, padding: PaddingValues) {
    LazyColumn(contentPadding = PaddingValues(20.dp, 28.dp, 20.dp, padding.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Profile", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item { Text(if (signedIn) "Connected to AniList" else "Browsing as guest", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SettingGroup("Appearance") { Text("Theme", fontWeight = FontWeight.Medium); ChoiceRow(AppTheme.entries.map { it.name }, theme.name) { onTheme(AppTheme.valueOf(it)) }; Text("Navigation", fontWeight = FontWeight.Medium); ChoiceRow(NavigationStyle.entries.map { it.name }, navStyle.name) { onNavStyle(NavigationStyle.valueOf(it)) } } }
        item { SettingGroup("Account") { if (signedIn) { ProfileDetails(); Button(onClick = onSignOut, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)) { Text("Sign out", color = MaterialTheme.colorScheme.onSurface) } } else { Text("Sign in with AniList", fontWeight = FontWeight.Medium); Text("Sync your library and profile", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Button(onClick = onSignIn, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Continue with AniList") } } } }
        item { SettingGroup("Privacy") { Text("Your data stays yours", fontWeight = FontWeight.Medium); Text("No ads, analytics, or streaming features", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}
@Composable private fun SettingGroup(title: String, content: @Composable () -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() } } } }
@Composable private fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { option -> val active = option == selected; Box(Modifier.clip(CircleShape).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface).clickable { onSelect(option) }.padding(horizontal = 14.dp, vertical = 10.dp)) { Text(option, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp) } } } }

@Composable
private fun BottomNavigation(selected: Destination, onSelect: (Destination) -> Unit, style: NavigationStyle, modifier: Modifier) { val surface = when (style) { NavigationStyle.Off -> MaterialTheme.colorScheme.surface; NavigationStyle.Blur -> MaterialTheme.colorScheme.surface.copy(alpha = .95f); NavigationStyle.Liquid -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .9f) }; Row(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(surface).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .15f), RoundedCornerShape(28.dp)).padding(5.dp), horizontalArrangement = Arrangement.SpaceEvenly) { Destination.entries.forEach { NavigationItem(it, it == selected) { onSelect(it) } } } }
@Composable private fun NavigationRail(selected: Destination, onSelect: (Destination) -> Unit, style: NavigationStyle) { Column(Modifier.fillMaxHeight().width(96.dp).padding(12.dp).clip(RoundedCornerShape(28.dp)).background(if (style == NavigationStyle.Off) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .8f)).padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Destination.entries.forEach { NavigationItem(it, it == selected) { onSelect(it) } } } }
@Composable private fun NavigationItem(destination: Destination, selected: Boolean, onClick: () -> Unit) { Column(Modifier.width(64.dp).height(64.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(destination.icon, destination.label, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp)); Text(destination.label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) } }

private fun hikariColors(theme: AppTheme, systemDark: Boolean) = if (theme == AppTheme.System && !systemDark) lightColorScheme(primary = Color(0xFF7054B8), onPrimary = Color.White, background = Color(0xFFFAF8FF), surface = Color.White, surfaceVariant = Color(0xFFF0EDF5), onSurface = Color(0xFF1C1B20), onSurfaceVariant = Color(0xFF5F5B66)) else darkColorScheme(primary = if (theme == AppTheme.Amoled) Color(0xFFD2C1FF) else Color(0xFFC5B3FF), onPrimary = Color(0xFF2A1750), background = Color.Black, surface = if (theme == AppTheme.Amoled) Color.Black else Color(0xFF0C0C0F), surfaceVariant = if (theme == AppTheme.Aurora) Color(0xFF17151E) else Color(0xFF111114), onSurface = Color(0xFFF1EDF5), onSurfaceVariant = Color(0xFFBDB8C5), outline = Color(0xFF5A5660), errorContainer = Color(0xFF4A171A), onErrorContainer = Color(0xFFFFDAD6))
