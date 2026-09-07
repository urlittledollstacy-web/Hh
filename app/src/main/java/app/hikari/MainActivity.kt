package app.hikari

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

@Composable
private fun HikariApp() {
    var destination by remember { mutableStateOf(Destination.Home) }
    var theme by remember { mutableStateOf(AppTheme.Aurora) }
    var navigationStyle by remember { mutableStateOf(NavigationStyle.Blur) }
    val colors = hikariColors(theme)

    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
            BoxWithAdaptiveNavigation(
                destination = destination,
                onDestination = { destination = it },
                theme = theme,
                navigationStyle = navigationStyle,
                onTheme = { theme = it },
                onNavigationStyle = { navigationStyle = it },
            )
        }
    }
}

@Composable
private fun BoxWithAdaptiveNavigation(
    destination: Destination,
    onDestination: (Destination) -> Unit,
    theme: AppTheme,
    navigationStyle: NavigationStyle,
    onTheme: (AppTheme) -> Unit,
    onNavigationStyle: (NavigationStyle) -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 700.dp
        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(destination, onDestination, navigationStyle)
                AppContent(destination, theme, navigationStyle, onTheme, onNavigationStyle, PaddingValues(0.dp))
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                AppContent(destination, theme, navigationStyle, onTheme, onNavigationStyle, PaddingValues(bottom = 104.dp))
                FloatingNavigationBar(
                    selected = destination,
                    onDestination = onDestination,
                    style = navigationStyle,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                )
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
        Destination.Discover -> DiscoverScreen(contentPadding)
        Destination.Library -> LibraryScreen(contentPadding)
        Destination.Calendar -> CalendarScreen(contentPadding)
        Destination.Profile -> ProfileScreen(theme, navigationStyle, onTheme, onNavigationStyle, contentPadding)
    }
}

@Composable
private fun HomeScreen(contentPadding: PaddingValues) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, top = 26.dp, end = 20.dp, bottom = contentPadding.calculateBottomPadding() + 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Good evening", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Find your next favorite.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Text("H", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
            }
        }
        item { SearchPrompt() }
        item { SectionTitle("Continue") }
        item {
            ContinueCard("Frieren: Beyond Journey's End", "Episode 21 of 28", 0.75f, Color(0xFF836DF2), Color(0xFF273D71))
        }
        item { SectionTitle("Airing soon", "See all") }
        item { MediaRow(AIRING) }
        item { SectionTitle("Trending now", "View charts") }
        item { MediaRow(TRENDING) }
    }
}

@Composable
private fun DiscoverScreen(contentPadding: PaddingValues) = PlaceholderScreen(
    title = "Discover",
    eyebrow = "CURATED FOR YOU",
    description = "Browse trending, seasonal, popular, and top-rated anime and manga without leaving your flow.",
    action = "Explore anime",
    padding = contentPadding,
)

@Composable
private fun LibraryScreen(contentPadding: PaddingValues) = PlaceholderScreen(
    title = "Your library",
    eyebrow = "ANIList SYNC",
    description = "Sign in to keep your watching and reading progress beautifully organized across AniList.",
    action = "Sign in with AniList",
    padding = contentPadding,
)

@Composable
private fun CalendarScreen(contentPadding: PaddingValues) = PlaceholderScreen(
    title = "Airing calendar",
    eyebrow = "THIS WEEK",
    description = "See each new episode at a glance and enable only the reminders you want.",
    action = "View today's airing",
    padding = contentPadding,
)

@Composable
private fun PlaceholderScreen(title: String, eyebrow: String, description: String, action: String, padding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 24.dp, top = 36.dp, end = 24.dp, bottom = padding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(eyebrow, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text(action) }
    }
}

@Composable
private fun ProfileScreen(
    theme: AppTheme,
    navigationStyle: NavigationStyle,
    onTheme: (AppTheme) -> Unit,
    onNavigationStyle: (NavigationStyle) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, top = 28.dp, end = 20.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("Profile", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item { Text("Browsing as guest", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SettingGroup("Appearance") { ThemeSelector(theme, onTheme); NavigationSelector(navigationStyle, onNavigationStyle) } }
        item { SettingGroup("Account") { SettingRow("Sign in with AniList", "Sync your library and profile") } }
        item { SettingGroup("Privacy") { SettingRow("Your data stays yours", "No ads, analytics, or streaming features") } }
    }
}

@Composable
private fun SettingGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
        }
    }
}

@Composable
private fun ThemeSelector(selected: AppTheme, onSelected: (AppTheme) -> Unit) {
    Text("Theme", fontWeight = FontWeight.Medium)
    ChoiceRow(AppTheme.entries.map { it.name }, selected.name, { onSelected(AppTheme.valueOf(it)) })
}

@Composable
private fun NavigationSelector(selected: NavigationStyle, onSelected: (NavigationStyle) -> Unit) {
    Text("Navigation", fontWeight = FontWeight.Medium)
    ChoiceRow(NavigationStyle.entries.map { it.name }, selected.name, { onSelected(NavigationStyle.valueOf(it)) })
}

@Composable
private fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val active = option == selected
            Surface(
                modifier = Modifier.clip(CircleShape).clickable { onSelect(option) },
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                shape = CircleShape,
            ) { Text(option, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String) {
    Column { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
private fun ContinueCard(title: String, progress: String, fraction: Float, accent: Color, shade: Color) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 76.dp, height = 104.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(accent, shade))))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(progress, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

private data class Media(val title: String, val detail: String, val colors: List<Color>)
private val AIRING = listOf(Media("The Apothecary Diaries", "Ep. 18 · in 2h", listOf(Color(0xFF28403C), Color(0xFFAE6649))), Media("One Piece", "Ep. 1143 · Today", listOf(Color(0xFF315BA8), Color(0xFFDFB55D))), Media("Solo Leveling", "Ep. 12 · Tomorrow", listOf(Color(0xFF4F3F7B), Color(0xFF172037))))
private val TRENDING = listOf(Media("Frieren", "98% match", listOf(Color(0xFF83B5D9), Color(0xFF5F6AB3))), Media("Dandadan", "Trending #2", listOf(Color(0xFFD66D7D), Color(0xFF543D87))), Media("Orb", "Trending #3", listOf(Color(0xFFCC8E48), Color(0xFF403661))))

@Composable
private fun MediaRow(media: List<Media>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 12.dp)) {
        items(media) { item ->
            Column(Modifier.width(126.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(.7f).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(item.colors)))
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(item.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FloatingNavigationBar(selected: Destination, onDestination: (Destination) -> Unit, style: NavigationStyle, modifier: Modifier = Modifier) {
    val surface = when (style) { NavigationStyle.Off -> MaterialTheme.colorScheme.surface; NavigationStyle.Blur -> MaterialTheme.colorScheme.surface.copy(alpha = .92f); NavigationStyle.Liquid -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .78f) }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(surface).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (style == NavigationStyle.Off) .25f else .12f), RoundedCornerShape(28.dp)).padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) { Destination.entries.forEach { NavigationItem(it, selected == it, { onDestination(it) }) } }
}

@Composable
private fun NavigationRail(selected: Destination, onDestination: (Destination) -> Unit, style: NavigationStyle) {
    Column(
        Modifier.fillMaxHeight().width(92.dp).padding(12.dp).clip(RoundedCornerShape(28.dp)).background(if (style == NavigationStyle.Off) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f)).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { Destination.entries.forEach { NavigationItem(it, selected == it, { onDestination(it) }) } }
}

@Composable
private fun NavigationItem(destination: Destination, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1f else .9f, tween(180), label = "navigation scale")
    Column(
        Modifier.size(width = 64.dp, height = 54.dp).clip(RoundedCornerShape(18.dp)).background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .16f) else Color.Transparent).clickable(onClick = onClick).scale(scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(destination.icon, contentDescription = destination.label, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        if (selected) Text(destination.label, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

private fun hikariColors(theme: AppTheme): ColorScheme = when (theme) {
    AppTheme.System -> androidx.compose.material3.darkColorScheme(primary = Color(0xFFBFC6FF), secondary = Color(0xFFC5C4DD), background = Color(0xFF121318), surface = Color(0xFF1A1B20), surfaceVariant = Color(0xFF282931))
    AppTheme.Amoled -> androidx.compose.material3.darkColorScheme(primary = Color(0xFFC1B9FF), secondary = Color(0xFFD0C6FF), background = Color.Black, surface = Color.Black, surfaceVariant = Color(0xFF111114))
    AppTheme.Aurora -> androidx.compose.material3.darkColorScheme(primary = Color(0xFFBBA4FF), onPrimary = Color(0xFF271756), secondary = Color(0xFF78D5FF), background = Color(0xFF11111A), surface = Color(0xFF1A1927), surfaceVariant = Color(0xFF26243A), onSurfaceVariant = Color(0xFFC9C5D8))
}

@Preview(showBackground = true, backgroundColor = 0xFF11111A, widthDp = 393)
@Composable
private fun HikariPreview() { HikariApp() }
