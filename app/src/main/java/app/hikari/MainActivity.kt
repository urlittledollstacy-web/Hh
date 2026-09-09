package app.hikari

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hikari.calendar.CalendarScreen
import app.hikari.core.auth.AniListAuthManager
import app.hikari.core.auth.AuthCallbackResult
import app.hikari.core.auth.AuthStartResult
import app.hikari.core.auth.SecureTokenStore
import app.hikari.core.model.MediaSummary
import app.hikari.core.model.MediaType
import app.hikari.data.AniListLibraryRepository
import app.hikari.data.remote.AniListGraphQlService
import app.hikari.data.remote.SearchTaxonomy
import app.hikari.data.remote.SearchTaxonomyKind
import app.hikari.library.LibraryScreen
import app.hikari.media.MediaDetailsScreen
import app.hikari.profile.ProfileDetails
import coil3.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var authManager: AniListAuthManager
    @Inject lateinit var tokenStore: SecureTokenStore
    @Inject lateinit var libraryRepository: AniListLibraryRepository
    private var signedIn by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); signedIn = tokenStore.accessToken() != null; setContent { HikariApp(signedIn, ::startAniListLogin, ::signOut) }; handleOAuthIntent(intent) }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleOAuthIntent(intent) }
    private fun startAniListLogin() { when (authManager.startLogin(this)) { AuthStartResult.Started -> Unit; AuthStartResult.MissingClientId -> Toast.makeText(this, "AniList login is not configured yet.", Toast.LENGTH_LONG).show() } }
    private fun handleOAuthIntent(intent: Intent) { when (val result = authManager.handleCallback(intent)) { AuthCallbackResult.Success -> { libraryRepository.clear(); signedIn = true; Toast.makeText(this, "Connected to AniList.", Toast.LENGTH_SHORT).show() }; is AuthCallbackResult.Error -> Toast.makeText(this, result.message, Toast.LENGTH_LONG).show(); null -> Unit } }
    private fun signOut() { authManager.logout(); libraryRepository.clear(); signedIn = false; Toast.makeText(this, "Signed out of AniList.", Toast.LENGTH_SHORT).show() }
}

private enum class Destination(val label: String, val icon: ImageVector) { Home("Home", Icons.Outlined.Home), Discover("Discover", Icons.Outlined.Search), Library("Library", Icons.Outlined.BookmarkBorder), Calendar("Calendar", Icons.Outlined.CalendarMonth), Profile("Profile", Icons.Outlined.PersonOutline) }
private enum class AppTheme { System, Amoled, White }
private enum class AppPalette { Pink, HotPink, Cyan, Red, Purple }
private enum class NavigationStyle { Blur, Liquid, Off }
private const val HIKARI_AVATAR_PREFS = "hikari_home_avatar"
private const val HIKARI_AVATAR_PATH = "path"
private const val HIKARI_SETTINGS_PREFS = "hikari_settings"
private const val HIKARI_THEME_KEY = "theme"
private const val HIKARI_PALETTE_KEY = "palette"
private const val HIKARI_NAV_STYLE_KEY = "navigation_style"

data class HomeUiState(val trending: List<MediaSummary> = emptyList(), val airing: List<MediaSummary> = emptyList(), val loading: Boolean = true, val refreshing: Boolean = false, val error: String? = null)
@HiltViewModel
class HomeViewModel @Inject constructor(private val api: AniListGraphQlService) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            var trendTick = 0
            while (true) {
                delay(60_000)
                refreshAiring()
                trendTick++
                if (trendTick >= 5) {
                    trendTick = 0
                    refreshTrendingSilently()
                }
            }
        }
    }

    fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(refreshing = true, error = null)
        val (trending, airing) = coroutineScope {
            val trendingRequest = async { loadPreservingCancellation { api.trending(MediaType.ANIME) } }
            val airingRequest = async { loadPreservingCancellation { api.airingSoon() } }
            trendingRequest.await() to airingRequest.await()
        }
        val previous = _state.value
        _state.value = previous.copy(
            trending = trending ?: previous.trending,
            airing = airing ?: previous.airing,
            loading = false,
            refreshing = false,
            error = if (trending == null && airing == null) "AniList is unavailable right now. Try again in a moment." else null,
        )
    }

    private fun refreshAiring() = viewModelScope.launch {
        val airing = loadPreservingCancellation { api.airingSoon() } ?: return@launch
        _state.value = _state.value.copy(airing = airing, loading = false)
    }

    private fun refreshTrendingSilently() = viewModelScope.launch {
        val trending = loadPreservingCancellation { api.trending(MediaType.ANIME) } ?: return@launch
        _state.value = _state.value.copy(trending = trending, loading = false)
    }

    private suspend fun <T> loadPreservingCancellation(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }
}

enum class SearchMediaFilter(val label: String, val type: MediaType?) { ALL("All", null), ANIME("Anime", MediaType.ANIME), MANGA("Manga", MediaType.MANGA) }
enum class SearchMode { MEDIA, TAGS_GENRES }
data class SearchUiState(val query: String = "", val results: List<MediaSummary> = emptyList(), val taxonomyResults: List<SearchTaxonomy> = emptyList(), val searching: Boolean = false, val loadingMore: Boolean = false, val page: Int = 1, val hasMore: Boolean = false, val filter: SearchMediaFilter = SearchMediaFilter.ALL, val mode: SearchMode = SearchMode.MEDIA, val taxonomy: SearchTaxonomy? = null, val error: String? = null)

private data class SearchRequestKey(
    val query: String,
    val filter: SearchMediaFilter,
    val mode: SearchMode,
    val taxonomy: SearchTaxonomy?,
)

@HiltViewModel
class SearchViewModel @Inject constructor(private val api: AniListGraphQlService) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var generation = 0L

    fun toggleMode() {
        invalidateRequests()
        val next = if (_state.value.mode == SearchMode.MEDIA) SearchMode.TAGS_GENRES else SearchMode.MEDIA
        _state.value = _state.value.copy(mode = next, query = "", results = emptyList(), taxonomyResults = emptyList(), searching = false, loadingMore = false, page = 1, hasMore = false, taxonomy = null, error = null)
    }

    fun setQuery(value: String) {
        invalidateRequests()
        val normalized = value.take(80)
        _state.value = _state.value.copy(query = normalized, results = emptyList(), taxonomyResults = emptyList(), searching = false, loadingMore = false, page = 1, hasMore = false, taxonomy = null, error = null)
        if (normalized.trim().isBlank()) return
        launchFirstPage(if (_state.value.mode == SearchMode.MEDIA) 350 else 180)
    }

    fun setFilter(filter: SearchMediaFilter) {
        if (_state.value.filter == filter) return
        invalidateRequests()
        _state.value = _state.value.copy(filter = filter, results = emptyList(), searching = false, loadingMore = false, page = 1, hasMore = false, taxonomy = null, error = null)
        if (_state.value.mode == SearchMode.MEDIA && _state.value.query.trim().isNotBlank()) launchFirstPage(180)
    }

    fun selectTaxonomy(item: SearchTaxonomy) {
        invalidateRequests()
        _state.value = _state.value.copy(mode = SearchMode.MEDIA, query = item.name, taxonomy = item, taxonomyResults = emptyList(), results = emptyList(), searching = false, loadingMore = false, page = 1, hasMore = false, error = null)
        launchFirstPage(0)
    }

    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || current.searching || !current.hasMore || current.query.trim().isBlank()) return
        val key = requestKey(current)
        val requestGeneration = generation
        val page = current.page + 1
        _state.value = current.copy(loadingMore = true, error = null)
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            try {
                val next = search(key, page)
                if (!isCurrent(key, requestGeneration)) return@launch
                _state.value = _state.value.copy(
                    results = (_state.value.results + next).distinctBy { it.id to it.type },
                    page = page,
                    hasMore = next.size >= 20,
                    loadingMore = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (isCurrent(key, requestGeneration)) {
                    _state.value = _state.value.copy(loadingMore = false, error = "Couldn't load more AniList results.")
                }
            }
        }
    }

    private fun launchFirstPage(delayMillis: Long) {
        val key = requestKey(_state.value)
        val requestGeneration = generation
        searchJob = viewModelScope.launch {
            delay(delayMillis)
            if (!isCurrent(key, requestGeneration)) return@launch
            _state.value = _state.value.copy(searching = true, loadingMore = false, page = 1, hasMore = false, error = null)
            try {
                if (key.mode == SearchMode.TAGS_GENRES) {
                    val matches = api.searchTaxonomies(key.query)
                    if (!isCurrent(key, requestGeneration)) return@launch
                    _state.value = _state.value.copy(taxonomyResults = matches, searching = false)
                } else {
                    val results = search(key, 1)
                    if (!isCurrent(key, requestGeneration)) return@launch
                    _state.value = _state.value.copy(results = results, searching = false, page = 1, hasMore = results.size >= 20)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (isCurrent(key, requestGeneration)) {
                    _state.value = _state.value.copy(searching = false, error = "Couldn't search AniList. Try again.")
                }
            }
        }
    }

    private suspend fun search(key: SearchRequestKey, page: Int): List<MediaSummary> =
        key.taxonomy?.let { api.searchByTaxonomy(it, key.filter.type, page) }
            ?: api.search(key.query, key.filter.type, page)

    private fun requestKey(state: SearchUiState) = SearchRequestKey(
        query = state.query.trim(),
        filter = state.filter,
        mode = state.mode,
        taxonomy = state.taxonomy,
    )

    private fun isCurrent(key: SearchRequestKey, requestGeneration: Long): Boolean =
        generation == requestGeneration && requestKey(_state.value) == key

    private fun invalidateRequests() {
        generation++
        searchJob?.cancel()
        loadMoreJob?.cancel()
    }

    override fun onCleared() {
        invalidateRequests()
        super.onCleared()
    }
}

@Composable private fun HikariApp(signedIn: Boolean, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { context.getSharedPreferences(HIKARI_SETTINGS_PREFS, Context.MODE_PRIVATE) }
    var destination by remember { mutableStateOf(Destination.Home) }
    var theme by remember { mutableStateOf(loadEnumPreference(settings, HIKARI_THEME_KEY, AppTheme.System)) }
    var palette by remember { mutableStateOf(loadEnumPreference(settings, HIKARI_PALETTE_KEY, AppPalette.Pink)) }
    var navStyle by remember { mutableStateOf(loadEnumPreference(settings, HIKARI_NAV_STYLE_KEY, NavigationStyle.Blur)) }
    val navController = rememberNavController()
    val colors = hikariColors(theme, isSystemInDarkTheme(), palette)

    fun navigateToMedia(summary: MediaSummary) {
        navController.navigate("media/${summary.id}/${summary.type.name}")
    }

    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val basePadding = if (maxWidth >= 700.dp) PaddingValues(0.dp) else PaddingValues(bottom = 104.dp)

                NavHost(
                    navController = navController,
                    startDestination = "shell",
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable("shell") {
                        if (maxWidth >= 700.dp) {
                            Row(Modifier.fillMaxSize()) {
                                NavigationRail(destination, { destination = it }, navStyle)
                                AppContent(
                                    destination, theme, palette, navStyle, signedIn, onSignIn, onSignOut,
                                    { value -> theme = value; settings.edit().putString(HIKARI_THEME_KEY, value.name).apply() },
                                    { value -> palette = value; settings.edit().putString(HIKARI_PALETTE_KEY, value.name).apply() },
                                    { value -> navStyle = value; settings.edit().putString(HIKARI_NAV_STYLE_KEY, value.name).apply() }, basePadding,
                                    { destination = it }, ::navigateToMedia, { navController.navigate("trending") }
                                )
                            }
                        } else {
                            Box(Modifier.fillMaxSize()) {
                                AppContent(
                                    destination, theme, palette, navStyle, signedIn, onSignIn, onSignOut,
                                    { value -> theme = value; settings.edit().putString(HIKARI_THEME_KEY, value.name).apply() },
                                    { value -> palette = value; settings.edit().putString(HIKARI_PALETTE_KEY, value.name).apply() },
                                    { value -> navStyle = value; settings.edit().putString(HIKARI_NAV_STYLE_KEY, value.name).apply() }, basePadding,
                                    { destination = it }, ::navigateToMedia, { navController.navigate("trending") }
                                )
                                BottomNavigation(
                                    destination, { destination = it }, navStyle,
                                    Modifier.align(Alignment.BottomCenter).padding(16.dp)
                                )
                            }
                        }
                    }

                    composable("trending") {
                        TrendingScreen(
                            padding = PaddingValues(0.dp),
                            onMediaClick = ::navigateToMedia,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = "media/{id}/{type}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.IntType },
                            navArgument("type") { type = NavType.StringType },
                        )
                    ) { entry ->
                        val id = entry.arguments?.getInt("id")
                        val type = entry.arguments?.getString("type")?.let { value ->
                            runCatching { MediaType.valueOf(value) }.getOrNull()
                        }
                        if (id == null || type == null) {
                            LaunchedEffect(id, type) { navController.popBackStack() }
                        } else {
                            MediaDetailsScreen(
                                summary = MediaSummary(id, type, "", null, null, null),
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun <T : Enum<T>> loadEnumPreference(prefs: android.content.SharedPreferences, key: String, default: T): T =
    prefs.getString(key, null)?.let { value -> runCatching { enumValueOf<T>(value) }.getOrNull() } ?: default

@Composable private fun AppContent(destination: Destination, theme: AppTheme, palette: AppPalette, navStyle: NavigationStyle, signedIn: Boolean, onSignIn: () -> Unit, onSignOut: () -> Unit, onTheme: (AppTheme) -> Unit, onPalette: (AppPalette) -> Unit, onNavStyle: (NavigationStyle) -> Unit, padding: PaddingValues, onDestination: (Destination) -> Unit, onMediaClick: (MediaSummary) -> Unit, onTrending: () -> Unit) { when (destination) { Destination.Home -> HomeScreen(padding, { onDestination(Destination.Discover) }, { onDestination(Destination.Calendar) }, onTrending, onMediaClick); Destination.Discover -> DiscoverScreen(padding, onMediaClick); Destination.Library -> LibraryScreen(signedIn, padding, onMediaClick); Destination.Calendar -> CalendarScreen(padding, onMediaClick, signedIn); Destination.Profile -> ProfileScreen(theme, palette, navStyle, signedIn, onSignIn, onSignOut, onTheme, onPalette, onNavStyle, padding) } }
@Composable private fun HomeScreen(padding: PaddingValues, onSearch: () -> Unit, onCalendar: () -> Unit, onTrending: () -> Unit, onMediaClick: (MediaSummary) -> Unit, vm: HomeViewModel = hiltViewModel()) { val state by vm.state.collectAsState(); val context = LocalContext.current; val scope = rememberCoroutineScope(); var localAvatarPath by remember { mutableStateOf(loadHomeAvatarPath(context)) }; val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) scope.launch { val savedPath = withContext(Dispatchers.IO) { saveHomeAvatar(context, uri, localAvatarPath) }; if (savedPath != null) localAvatarPath = savedPath } }; val avatarModel = localAvatarPath?.let(::File); LazyColumn(contentPadding = PaddingValues(20.dp, 26.dp, 20.dp, padding.calculateBottomPadding() + 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) { item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Good evening", color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Find your next favorite.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }; Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable { picker.launch("image/*") }, contentAlignment = Alignment.Center) { if (avatarModel != null) AsyncImage(model = avatarModel, contentDescription = "Change Hikari profile picture", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Text("H", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) } } }; item { Row(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onSearch).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Search, "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(10.dp)); Text("Search anime, manga, people...", color = MaterialTheme.colorScheme.onSurfaceVariant) } }; if (state.error != null) item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text(state.error.orEmpty(), Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer); TextButton(onClick = vm::refresh) { Text("Retry") } } } }; item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Airing soon", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); TextButton(onClick = onCalendar) { Text("See all") } } }; item { if (state.loading) LoadingRow() else if (state.airing.isEmpty()) EmptyMessage("No upcoming episodes found.") else MediaRow(state.airing, true, onMediaClick) }; item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Trending now", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); TextButton(onClick = onTrending) { Text("See all") } } }; item { if (state.loading) LoadingRow() else if (state.trending.isEmpty()) EmptyMessage("No trending anime found.") else MediaRow(state.trending, false, onMediaClick) } } }
@Composable private fun DiscoverScreen(padding: PaddingValues, onMediaClick: (MediaSummary) -> Unit, vm: SearchViewModel = hiltViewModel()) { val state by vm.state.collectAsState(); LazyColumn(contentPadding = PaddingValues(20.dp, 28.dp, 20.dp, padding.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { Text("Discover", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text(if (state.mode == SearchMode.MEDIA) "Search AniList instantly" else "Browse AniList tags and genres", color = MaterialTheme.colorScheme.onSurfaceVariant) }; item { OutlinedTextField(value = state.query, onValueChange = vm::setQuery, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(if (state.mode == SearchMode.MEDIA) "Search anime, manga..." else "Search tags or genres...") }, leadingIcon = { Icon(Icons.Outlined.Search, "Search") }, trailingIcon = { Row(verticalAlignment = Alignment.CenterVertically) { if (state.query.isNotBlank()) TextButton(onClick = { vm.setQuery("") }) { Text("Clear") }; IconButton(onClick = vm::toggleMode) { PixelSearchModeIcon(state.mode == SearchMode.TAGS_GENRES) } } }) }; if (state.mode == SearchMode.MEDIA) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { SearchMediaFilter.entries.forEach { filter -> val active = filter == state.filter; Box(Modifier.clip(CircleShape).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).clickable { vm.setFilter(filter) }.padding(horizontal = 16.dp, vertical = 10.dp)) { Text(filter.label, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) } } } }; if (state.searching) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } }; state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }; if (state.query.isBlank()) item { EmptyMessage(if (state.mode == SearchMode.MEDIA) "Start typing — Hikari will show suggestions automatically." else "Search for a genre or tag, then tap it to browse matching media.") } else if (state.error == null && !state.searching && state.mode == SearchMode.TAGS_GENRES && state.taxonomyResults.isEmpty()) item { EmptyMessage("No tags or genres found for \"${state.query.trim()}\".") } else if (state.error == null && !state.searching && state.mode == SearchMode.MEDIA && state.results.isEmpty()) item { EmptyMessage("No results found for \"${state.query.trim()}\".") } else if (state.mode == SearchMode.TAGS_GENRES && state.taxonomyResults.isNotEmpty()) { item { Text("Tags & genres", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }; items(state.taxonomyResults, key = { "${it.kind}:${it.id}" }) { item -> Card(Modifier.fillMaxWidth().clickable { vm.selectTaxonomy(item) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.SemiBold); Text(if (item.kind == SearchTaxonomyKind.GENRE) "Genre" else "Tag", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }; Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } else if (state.results.isNotEmpty()) { item { Text(if (state.taxonomy != null) "${state.taxonomy!!.name} results" else if (state.query.trim().length == 1) "Suggestions" else "Results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }; item { SearchResults(state.results, onMediaClick) }; if (state.hasMore) item { Button(onClick = vm::loadMore, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth()) { if (state.loadingMore) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Load more") } } } } }
@Composable private fun PixelSearchModeIcon(active: Boolean) { val on = MaterialTheme.colorScheme.primary; val off = MaterialTheme.colorScheme.onSurfaceVariant; Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { repeat(3) { row -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { repeat(3) { col -> Box(Modifier.size(4.dp).background(if ((row + col) % 2 == 0) on else off)) } } } } }
@Composable private fun SearchResults(media: List<MediaSummary>, onMediaClick: (MediaSummary) -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { media.forEach { item -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onMediaClick(item) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)) { item.coverUrl?.let { AsyncImage(model = it, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(buildString { append(if (item.type == MediaType.ANIME) "Anime" else "Manga"); item.averageScore?.let { append("  •  ★ $it%") }; item.episodesOrChapters?.let { append("  •  ${if (item.type == MediaType.ANIME) "$it eps" else "$it ch"}") } }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
@Composable private fun LoadingRow() { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { Box(Modifier.width(126.dp).aspectRatio(.7f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) } } }
@Composable private fun EmptyMessage(text: String) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp)) }
@Composable private fun MediaRow(media: List<MediaSummary>, airing: Boolean = false, onMediaClick: (MediaSummary) -> Unit) { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(media, key = { it.id }) { item -> Column(Modifier.width(126.dp).clickable { onMediaClick(item) }, verticalArrangement = Arrangement.spacedBy(7.dp)) { Box(Modifier.fillMaxWidth().aspectRatio(.7f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) { item.coverUrl?.let { AsyncImage(model = it, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }; Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); Text(if (airing && item.episodesOrChapters != null) "Episode ${item.episodesOrChapters}" else item.averageScore?.let { "★ $it%" } ?: "AniList", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
@Composable private fun ProfileScreen(theme: AppTheme, palette: AppPalette, navStyle: NavigationStyle, signedIn: Boolean, onSignIn: () -> Unit, onSignOut: () -> Unit, onTheme: (AppTheme) -> Unit, onPalette: (AppPalette) -> Unit, onNavStyle: (NavigationStyle) -> Unit, padding: PaddingValues) { LazyColumn(contentPadding = PaddingValues(20.dp, 28.dp, 20.dp, padding.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { item { Text("Profile", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }; item { Text(if (signedIn) "Connected to AniList" else "Browsing as guest", color = MaterialTheme.colorScheme.onSurfaceVariant) }; item { SettingGroup("Appearance") { Text("Theme", fontWeight = FontWeight.Medium); ChoiceRow(AppTheme.entries.map { it.name }, theme.name) { onTheme(AppTheme.valueOf(it)) }; Text("Palette", fontWeight = FontWeight.Medium); ChoiceRow(AppPalette.entries.map { it.name }, palette.name) { onPalette(AppPalette.valueOf(it)) }; Text("Navigation", fontWeight = FontWeight.Medium); ChoiceRow(NavigationStyle.entries.map { it.name }, navStyle.name) { onNavStyle(NavigationStyle.valueOf(it)) } } }; item { SettingGroup("Account") { if (signedIn) { ProfileDetails(); Button(onClick = onSignOut, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)) { Text("Sign out", color = MaterialTheme.colorScheme.onSurface) } } else { Text("Sign in with AniList", fontWeight = FontWeight.Medium); Text("Sync your library and profile", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Button(onClick = onSignIn, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Continue with AniList") } } } }; item { SettingGroup("Privacy") { Text("Your data stays yours", fontWeight = FontWeight.Medium); Text("No ads, analytics, or streaming features", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
@Composable private fun SettingGroup(title: String, content: @Composable () -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() } } } }
@Composable private fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { option -> val active = option == selected; Box(Modifier.clip(CircleShape).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface).clickable { onSelect(option) }.padding(horizontal = 14.dp, vertical = 10.dp)) { Text(option, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp) } } } }
@Composable private fun BottomNavigation(selected: Destination, onSelect: (Destination) -> Unit, style: NavigationStyle, modifier: Modifier) { val surface = when (style) { NavigationStyle.Off -> MaterialTheme.colorScheme.surface; NavigationStyle.Blur -> MaterialTheme.colorScheme.surface.copy(alpha = .95f); NavigationStyle.Liquid -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .9f) }; Row(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(surface).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .15f), RoundedCornerShape(28.dp)).padding(5.dp), horizontalArrangement = Arrangement.SpaceEvenly) { Destination.entries.forEach { NavigationItem(it, it == selected) { onSelect(it) } } } }
@Composable private fun NavigationRail(selected: Destination, onSelect: (Destination) -> Unit, style: NavigationStyle) { Column(Modifier.fillMaxHeight().width(96.dp).padding(12.dp).clip(RoundedCornerShape(28.dp)).background(if (style == NavigationStyle.Off) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .8f)).padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Destination.entries.forEach { NavigationItem(it, it == selected) { onSelect(it) } } }
@Composable private fun NavigationItem(destination: Destination, selected: Boolean, onClick: () -> Unit) { Column(Modifier.width(64.dp).height(64.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(destination.icon, destination.label, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp)); Text(destination.label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) } }
private fun hikariColors(theme: AppTheme, systemDark: Boolean, palette: AppPalette) = run { val primary = when (palette) { AppPalette.Pink -> Color(0xFFFFD1DC); AppPalette.HotPink -> Color(0xFFFF2E63); AppPalette.Cyan -> Color(0xFF30E3CA); AppPalette.Red -> Color(0xFFFF3B4D); AppPalette.Purple -> Color(0xFF8C00FF) }; val onPrimary = when (palette) { AppPalette.Pink -> Color(0xFF4A2731); AppPalette.HotPink -> Color(0xFF2B0712); AppPalette.Cyan -> Color(0xFF00332D); AppPalette.Red -> Color(0xFF3B070B); AppPalette.Purple -> Color.White }; if (theme == AppTheme.System && !systemDark) lightColorScheme(primary = primary, onPrimary = onPrimary, background = Color(0xFFFAF8FF), surface = Color.White, surfaceVariant = Color(0xFFF0EDF5), onSurface = Color(0xFF1C1B20), onSurfaceVariant = Color(0xFF5F5B66)) else if (theme == AppTheme.White) lightColorScheme(primary = primary, onPrimary = onPrimary, background = Color.White, surface = Color.White, surfaceVariant = Color(0xFFF3F1F5), onSurface = Color(0xFF1C1B20), onSurfaceVariant = Color(0xFF5F5B66)) else darkColorScheme(primary = primary, onPrimary = onPrimary, background = Color.Black, surface = Color(0xFF0C0C0F), surfaceVariant = Color(0xFF111114), onSurface = Color(0xFFF1EDF5), onSurfaceVariant = Color(0xFFBDB8C5), outline = Color(0xFF5A5660), errorContainer = Color(0xFF4A171A), onErrorContainer = Color(0xFFFFDAD6)) }
private fun loadHomeAvatarPath(context: Context): String? = context.getSharedPreferences(HIKARI_AVATAR_PREFS, Context.MODE_PRIVATE).getString(HIKARI_AVATAR_PATH, null)?.takeIf { File(it).exists() }
private suspend fun saveHomeAvatar(context: Context, uri: android.net.Uri, previousPath: String?): String? { val extension = when (context.contentResolver.getType(uri)) { "image/gif" -> "gif"; "image/png" -> "png"; "image/webp" -> "webp"; "image/jpeg" -> "jpg"; else -> "img" }; val file = File(context.filesDir, "hikari_avatar_${System.currentTimeMillis()}.$extension"); return runCatching { context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } } ?: return null; context.getSharedPreferences(HIKARI_AVATAR_PREFS, Context.MODE_PRIVATE).edit().putString(HIKARI_AVATAR_PATH, file.absolutePath).apply(); previousPath?.let { File(it).delete() }; file.absolutePath }.getOrNull() }
