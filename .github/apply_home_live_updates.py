from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRANCH = "codex/create-personal-anilist-android-client"

main = ROOT / "app/src/main/java/app/hikari/MainActivity.kt"
text = main.read_text()

old_home_vm_start = '''data class HomeUiState(val trending: List<MediaSummary> = emptyList(), val airing: List<MediaSummary> = emptyList(), val loading: Boolean = true, val refreshing: Boolean = false, val error: String? = null)\n\n@HiltViewModel\nclass HomeViewModel @Inject constructor(private val api: AniListGraphQlService) : ViewModel() {\n    private val _state = MutableStateFlow(HomeUiState())\n    val state: StateFlow<HomeUiState> = _state.asStateFlow()\n    init { refresh() }\n    fun refresh() = viewModelScope.launch {\n        _state.value = _state.value.copy(refreshing = true, error = null)\n        val (trending, airing) = coroutineScope {\n            val trendingDeferred = async { runCatching { api.trending(MediaType.ANIME) }.getOrDefault(emptyList()) }\n            val airingDeferred = async { runCatching { api.airingSoon() }.getOrDefault(emptyList()) }\n            trendingDeferred.await() to airingDeferred.await()\n        }\n        _state.value = HomeUiState(trending, airing, false, false, if (trending.isEmpty() && airing.isEmpty()) "AniList is unavailable right now. Try again in a moment." else null)\n    }\n}\n'''

new_home_vm = '''data class HomeUiState(val trending: List<MediaSummary> = emptyList(), val airing: List<MediaSummary> = emptyList(), val loading: Boolean = true, val refreshing: Boolean = false, val error: String? = null)\n\n@HiltViewModel\nclass HomeViewModel @Inject constructor(private val api: AniListGraphQlService) : ViewModel() {\n    private val _state = MutableStateFlow(HomeUiState())\n    val state: StateFlow<HomeUiState> = _state.asStateFlow()\n\n    init {\n        refresh()\n        viewModelScope.launch {\n            var trendTick = 0\n            while (true) {\n                delay(60_000)\n                refreshAiring()\n                trendTick++\n                if (trendTick >= 5) {\n                    trendTick = 0\n                    refreshTrendingSilently()\n                }\n            }\n        }\n    }\n\n    fun refresh() = viewModelScope.launch {\n        _state.value = _state.value.copy(refreshing = true, error = null)\n        val (trending, airing) = coroutineScope {\n            val trendingDeferred = async { runCatching { api.trending(MediaType.ANIME) }.getOrDefault(emptyList()) }\n            val airingDeferred = async { runCatching { api.airingSoon() }.getOrDefault(emptyList()) }\n            trendingDeferred.await() to airingDeferred.await()\n        }\n        _state.value = HomeUiState(\n            trending = trending,\n            airing = airing,\n            loading = false,\n            refreshing = false,\n            error = if (trending.isEmpty() && airing.isEmpty()) "AniList is unavailable right now. Try again in a moment." else null,\n        )\n    }\n\n    private fun refreshAiring() = viewModelScope.launch {\n        val airing = runCatching { api.airingSoon() }.getOrDefault(emptyList())\n        _state.value = _state.value.copy(airing = airing, loading = false)\n    }\n\n    private fun refreshTrendingSilently() = viewModelScope.launch {\n        val trending = runCatching { api.trending(MediaType.ANIME) }.getOrDefault(emptyList())\n        _state.value = _state.value.copy(trending = trending, loading = false)\n    }\n}\n'''
if old_home_vm_start not in text:
    raise SystemExit("HomeViewModel block not found")
text = text.replace(old_home_vm_start, new_home_vm, 1)

old_app_content = 'Destination.Home -> HomeScreen(padding, onSearch = { onDestination(Destination.Discover) }, onMediaClick = onMediaClick)'
new_app_content = 'Destination.Home -> HomeScreen(padding, onSearch = { onDestination(Destination.Discover) }, onCalendar = { onDestination(Destination.Calendar) }, onMediaClick = onMediaClick)'
if old_app_content not in text:
    raise SystemExit("Home AppContent call not found")
text = text.replace(old_app_content, new_app_content, 1)

old_home_signature = '@Composable\nprivate fun HomeScreen(padding: PaddingValues, onSearch: () -> Unit, onMediaClick: (MediaSummary) -> Unit, vm: HomeViewModel = hiltViewModel()) {'
new_home_signature = '@Composable\nprivate fun HomeScreen(padding: PaddingValues, onSearch: () -> Unit, onCalendar: () -> Unit, onMediaClick: (MediaSummary) -> Unit, vm: HomeViewModel = hiltViewModel()) {'
if old_home_signature not in text:
    raise SystemExit("HomeScreen signature not found")
text = text.replace(old_home_signature, new_home_signature, 1)

old_section = 'item { SectionTitle("Airing soon", "See all") }'
new_section = '''item {\n            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {\n                Text("Airing soon", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))\n                TextButton(onClick = onCalendar) { Text("See all") }\n            }\n        }'''
if old_section not in text:
    raise SystemExit("Airing section title not found")
text = text.replace(old_section, new_section, 1)

main.write_text(text)

calendar = ROOT / "app/src/main/java/app/hikari/data/remote/AniListCalendarService.kt"
text = calendar.read_text()
needle = '''    suspend fun airingSchedule(from: Long, to: Long): List<AiringScheduleEntry> = withContext(Dispatchers.IO) {'''
insert = '''    suspend fun airingSoon(limit: Int = 12, now: Long = System.currentTimeMillis() / 1000): List<AiringScheduleEntry> = withContext(Dispatchers.IO) {\n        val query = "query(\\$now:Int!,\\$limit:Int!){Page(page:1,perPage:100){airingSchedules(notYetAired:true,airingAt_greater:\\$now,sort:TIME_ASC){id airingAt timeUntilAiring episode media{id title{userPreferred english romaji native} coverImage{large} averageScore episodes}}}}"\n        val data = execute(query, mapOf("now" to now, "limit" to limit))\n            .getJSONObject("data").getJSONObject("Page").getJSONArray("airingSchedules")\n        List(minOf(data.length(), limit)) { index ->\n            val item = data.getJSONObject(index)\n            val media = item.getJSONObject("media")\n            AiringScheduleEntry(\n                id = item.getLong("id"),\n                airingAt = item.getLong("airingAt"),\n                timeUntilAiring = item.optLong("timeUntilAiring"),\n                episode = item.optInt("episode"),\n                mediaId = media.getInt("id"),\n                mediaType = MediaType.ANIME,\n                title = preferredTitle(media.optJSONObject("title")),\n                coverUrl = media.optJSONObject("coverImage")?.optString("large")?.takeIf { it.isNotBlank() },\n                averageScore = media.optInt("averageScore").takeIf { it > 0 },\n                totalEpisodes = media.optInt("episodes").takeIf { it > 0 },\n            )\n        }\n    }\n\n'''
if needle not in text:
    raise SystemExit("Calendar service insertion point not found")
text = text.replace(needle, insert + needle, 1)
calendar.write_text(text)

recommendations = ROOT / "app/src/main/java/app/hikari/HomeRecommendations.kt"
text = recommendations.read_text()
old_init = '    init { refresh() }\n\n    fun refresh() = viewModelScope.launch {'
new_init = '''    init {\n        refresh()\n        viewModelScope.launch {\n            while (true) {\n                delay(10 * 60_000L)\n                refresh()\n            }\n        }\n    }\n\n    fun refresh() = viewModelScope.launch {'''
if old_init not in text:
    raise SystemExit("Recommendation init not found")
text = text.replace(old_init, new_init, 1)
text = text.replace('import kotlinx.coroutines.flow.asStateFlow\nimport kotlinx.coroutines.launch', 'import kotlinx.coroutines.flow.asStateFlow\nimport kotlinx.coroutines.delay\nimport kotlinx.coroutines.launch', 1)
recommendations.write_text(text)

# Clean this patcher immediately so it never remains in the final branch.
workflow = ROOT / ".github/workflows/apply-home-live-updates.yml"
if workflow.exists():
    workflow.unlink()
Path(__file__).unlink()

import subprocess
subprocess.run(["git", "config", "user.name", "github-actions[bot]"], check=True)
subprocess.run(["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"], check=True)
subprocess.run(["git", "add", "app/src/main/java/app/hikari/MainActivity.kt", "app/src/main/java/app/hikari/data/remote/AniListCalendarService.kt", "app/src/main/java/app/hikari/HomeRecommendations.kt", ".github"], check=True)
subprocess.run(["git", "commit", "-m", "Fix live Home airing and refresh behavior"], check=True)
subprocess.run(["git", "push", "origin", BRANCH], check=True)
