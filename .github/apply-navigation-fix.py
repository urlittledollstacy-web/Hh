from pathlib import Path
import re

main = Path('app/src/main/java/app/hikari/MainActivity.kt')
text = main.read_text()

for line in [
    'import androidx.activity.BackEventCompat\n',
    'import androidx.activity.compose.PredictiveBackHandler\n',
    'import kotlinx.coroutines.CancellationException\n',
    'import androidx.compose.ui.platform.LocalDensity\n',
    'import androidx.compose.ui.unit.IntOffset\n',
    'import kotlin.math.roundToInt\n',
]:
    text = text.replace(line, '')

imports = '''import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideIntoContainer
import androidx.compose.animation.slideOutOfContainer
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
'''
anchor = 'import androidx.compose.foundation.background\n'
text = text.replace(anchor, imports + anchor, 1)

new_hikari = r'''@Composable private fun HikariApp(signedIn: Boolean, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    var destination by remember { mutableStateOf(Destination.Home) }
    var theme by remember { mutableStateOf(AppTheme.System) }
    var palette by remember { mutableStateOf(AppPalette.Pink) }
    var navStyle by remember { mutableStateOf(NavigationStyle.Blur) }
    val navController = rememberNavController()
    val mediaEntries = remember { mutableStateMapOf<String, MediaSummary>() }
    var nextMediaToken by remember { mutableIntStateOf(0) }
    val colors = hikariColors(theme, isSystemInDarkTheme(), palette)

    fun navigateToMedia(summary: MediaSummary) {
        val token = "m${++nextMediaToken}"
        mediaEntries[token] = summary
        navController.navigate("media/$token")
    }

    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val basePadding = if (maxWidth >= 700.dp) PaddingValues(0.dp) else PaddingValues(bottom = 104.dp)

                NavHost(
                    navController = navController,
                    startDestination = "shell",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
                    },
                    exitTransition = {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
                    },
                    popEnterTransition = {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
                    },
                    popExitTransition = {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
                    },
                ) {
                    composable("shell") {
                        if (maxWidth >= 700.dp) {
                            Row(Modifier.fillMaxSize()) {
                                NavigationRail(destination, { destination = it }, navStyle)
                                AppContent(
                                    destination, theme, palette, navStyle, signedIn, onSignIn, onSignOut,
                                    { theme = it }, { palette = it }, { navStyle = it }, basePadding,
                                    { destination = it }, ::navigateToMedia, { navController.navigate("trending") }
                                )
                            }
                        } else {
                            Box(Modifier.fillMaxSize()) {
                                AppContent(
                                    destination, theme, palette, navStyle, signedIn, onSignIn, onSignOut,
                                    { theme = it }, { palette = it }, { navStyle = it }, basePadding,
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
                        route = "media/{token}",
                        arguments = listOf(navArgument("token") { type = NavType.StringType })
                    ) { entry ->
                        val token = entry.arguments?.getString("token")
                        val summary = token?.let(mediaEntries::get)
                        if (summary == null) {
                            LaunchedEffect(token) { navController.popBackStack() }
                        } else {
                            DisposableEffect(token) {
                                onDispose { mediaEntries.remove(token) }
                            }
                            MediaDetailsScreen(
                                summary = summary,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
'''
pattern = r'@Composable private fun HikariApp\(signedIn: Boolean, onSignIn: \(\) -> Unit, onSignOut: \(\) -> Unit\) \{.*?\n\}\n\n@Composable private fun AppContent'
replacement = new_hikari + '\n@Composable private fun AppContent'
text, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
if count != 1:
    raise SystemExit('Could not replace HikariApp')
main.write_text(text)

media = Path('app/src/main/java/app/hikari/media/MediaDetailsScreen.kt')
m = media.read_text()
m = m.replace('import androidx.compose.foundation.layout.Box\n', 'import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.BoxWithConstraints\nimport androidx.compose.foundation.layout.offset\n', 1)
m = m.replace('import androidx.compose.runtime.Composable\n', 'import androidx.activity.BackEventCompat\nimport androidx.activity.compose.PredictiveBackHandler\nimport androidx.compose.runtime.Composable\n', 1)
m = m.replace('import androidx.compose.ui.layout.ContentScale\n', 'import androidx.compose.ui.layout.ContentScale\nimport androidx.compose.ui.platform.LocalDensity\n', 1)
m = m.replace('import kotlinx.coroutines.flow.MutableStateFlow\n', 'import kotlinx.coroutines.CancellationException\nimport kotlinx.coroutines.flow.MutableStateFlow\n', 1)
m = m.replace('import androidx.compose.ui.unit.dp\n', 'import androidx.compose.ui.unit.IntOffset\nimport androidx.compose.ui.unit.dp\n', 1)
m = m.replace('import javax.inject.Inject\n', 'import javax.inject.Inject\nimport kotlin.math.roundToInt\n', 1)

new_media = r'''@Composable fun MediaDetailsScreen(summary: MediaSummary, onBack: () -> Unit, vm: MediaDetailsViewModel = hiltViewModel(), trackingVm: MediaTrackingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val trackingState by trackingVm.state.collectAsState()
    var currentSummary by remember(summary.id) { mutableStateOf(summary) }
    var history by remember(summary.id) { mutableStateOf(emptyList<MediaHistoryEntry>()) }
    var creditTarget by remember(summary.id) { mutableStateOf<CreditTarget?>(null) }
    var backProgress by remember { mutableStateOf(0f) }
    var backSwipeEdge by remember { mutableStateOf(BackEventCompat.EDGE_LEFT) }

    LaunchedEffect(currentSummary.id) { vm.load(currentSummary.id) }
    val currentMedia = (state as? MediaDetailState.Ready)?.media

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
                onBack = {},
                onOpenRelation = {},
                onOpenTracking = {},
                onOpenCredit = {}
            )
        }

        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val direction = if (backSwipeEdge == BackEventCompat.EDGE_RIGHT) -1 else 1
        val offsetX = if (creditTarget == null && history.isNotEmpty()) (direction * widthPx * backProgress).roundToInt() else 0
        Box(Modifier.fillMaxSize().offset { IntOffset(offsetX, 0) }) {
            when (val current = state) {
                MediaDetailState.Loading -> DetailLoading { goBack() }
                is MediaDetailState.Error -> DetailError(current.message, { goBack() }) { vm.load(currentSummary.id) }
                is MediaDetailState.Ready -> DetailContent(
                    current.media,
                    { goBack() },
                    { relation ->
                        history = history + MediaHistoryEntry(currentSummary, current.media)
                        currentSummary = relation
                    },
                    { trackingVm.open(it) }
                ) { creditTarget = it }
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

'''
pattern = r'@Composable fun MediaDetailsScreen\(summary: MediaSummary, onBack: \(\) -> Unit, vm: MediaDetailsViewModel = hiltViewModel\(\), trackingVm: MediaTrackingViewModel = hiltViewModel\(\)\) \{.*?\n\}\n@Composable private fun TrackingLoadingDialog'
replacement = new_media + '@Composable private fun TrackingLoadingDialog'
m, count = re.subn(pattern, replacement, m, count=1, flags=re.S)
if count != 1:
    raise SystemExit('Could not replace MediaDetailsScreen')
media.write_text(m)
