package app.hikari.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hikari.core.model.AiringScheduleEntry
import app.hikari.core.model.MediaSummary
import app.hikari.data.remote.AniListCalendarService
import app.hikari.data.remote.AniListLibraryService
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class CalendarDay(val start: Long, val label: String, val number: String, val isToday: Boolean)

data class CalendarUiState(
    val days: List<CalendarDay> = emptyList(),
    val selectedDay: Int = 0,
    val entries: List<AiringScheduleEntry> = emptyList(),
    val myEntries: List<AiringScheduleEntry> = emptyList(),
    val showMine: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarApi: AniListCalendarService,
    private val libraryApi: AniListLibraryService,
) : ViewModel() {
    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()
    private var libraryIds: Set<Int> = emptySet()
    private var libraryLoaded = false
    private var allEntries: List<AiringScheduleEntry> = emptyList()

    init { refresh() }

    fun selectDay(index: Int) {
        val current = _state.value
        if (index !in current.days.indices) return
        _state.value = current.copy(selectedDay = index, entries = entriesFor(index, current.showMine))
    }

    fun setShowMine(value: Boolean) {
        val current = _state.value
        if (!value) {
            _state.value = current.copy(showMine = false, entries = entriesFor(current.selectedDay, false))
            return
        }
        if (libraryLoaded) {
            _state.value = current.copy(showMine = true, entries = entriesFor(current.selectedDay, true))
            return
        }
        _state.value = current.copy(showMine = true, loading = true, error = null)
        viewModelScope.launch {
            loadLibrary()
            val latest = _state.value
            _state.value = latest.copy(
                showMine = true,
                loading = false,
                entries = entriesFor(latest.selectedDay, true),
            )
        }
    }

    fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        val days = buildDays()
        val start = days.firstOrNull()?.start ?: dayStart(System.currentTimeMillis())
        val end = (days.lastOrNull()?.start ?: start) + DAY_SECONDS
        val result = runCatching { calendarApi.airingSchedule(start, end) }
        val wantsMine = _state.value.showMine
        if (wantsMine) loadLibrary()
        allEntries = result.getOrDefault(emptyList()).distinctBy { it.id }.sortedBy { it.airingAt }
        _state.value = CalendarUiState(
            days = days,
            selectedDay = 0,
            entries = entriesFor(days, 0, wantsMine),
            myEntries = allEntries.filter { it.mediaId in libraryIds },
            showMine = wantsMine,
            loading = false,
            error = result.exceptionOrNull()?.message?.takeIf { allEntries.isEmpty() },
        )
    }

    private suspend fun loadLibrary() {
        val library = runCatching { libraryApi.library(app.hikari.core.model.MediaType.ANIME) }.getOrNull()
        libraryIds = library?.entries.orEmpty().filter { it.status == "CURRENT" }.map { it.media.id }.toSet()
        libraryLoaded = library != null
    }

    private fun entriesFor(index: Int, showMine: Boolean): List<AiringScheduleEntry> {
        return entriesFor(_state.value.days, index, showMine)
    }

    private fun entriesFor(
        days: List<CalendarDay>,
        index: Int,
        showMine: Boolean,
    ): List<AiringScheduleEntry> {
        val day = days.getOrNull(index) ?: return emptyList()
        val source = if (showMine) allEntries.filter { it.mediaId in libraryIds } else allEntries
        return source
            .filter { it.airingAt >= day.start && it.airingAt < day.start + DAY_SECONDS }
            .sortedBy { it.airingAt }
    }

    private fun buildDays(): List<CalendarDay> {
        val todayStart = dayStart(System.currentTimeMillis())
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        return (0 until 14).map { offset ->
            val seconds = todayStart + offset * DAY_SECONDS
            CalendarDay(seconds, dayFormat.format(Date(seconds * 1000)), SimpleDateFormat("d", Locale.getDefault()).format(Date(seconds * 1000)), offset == 0)
        }
    }

    private fun dayStart(millis: Long): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        return calendar.timeInMillis / 1000
    }

    companion object { private const val DAY_SECONDS = 86_400L }
}

@Composable
fun CalendarScreen(
    padding: PaddingValues,
    onMediaClick: (MediaSummary) -> Unit,
    signedIn: Boolean,
    vm: CalendarViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 26.dp, 20.dp, padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Calendar", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Never miss an episode", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = vm::refresh, enabled = !state.loading) {
                    if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Refresh, "Refresh")
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                items(state.days.size) { index ->
                    val day = state.days[index]
                    val selected = index == state.selectedDay
                    Column(
                        Modifier.width(58.dp).clip(RoundedCornerShape(18.dp)).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).clickable { vm.selectDay(index) }.padding(vertical = 11.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(day.label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(day.number, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        if (day.isToday) Box(Modifier.padding(top = 5.dp).size(4.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary))
                    }
                }
            }
        }
        if (signedIn) item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                CalendarFilter("All", !state.showMine, Modifier.weight(1f)) { vm.setShowMine(false) }
                CalendarFilter("My Watching", state.showMine, Modifier.weight(1f)) { vm.setShowMine(true) }
            }
        }
        if (state.error != null) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Couldn't load the airing schedule.", Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = vm::refresh) { Text("Retry") }
                }
            }
        }
        if (!state.loading && state.entries.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(if (state.showMine) "Nothing from your watching list" else "No episodes scheduled", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (state.showMine) "You're all caught up for this day." else "Try another day in the calendar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.entries.isNotEmpty()) {
            item { Text(if (state.showMine) "Your schedule" else "Airing schedule", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(state.entries, key = { it.id }) { entry ->
                AiringCard(entry) { onMediaClick(MediaSummary(entry.mediaId, entry.mediaType, entry.title, entry.coverUrl, entry.averageScore, entry.totalEpisodes)) }
            }
        }
    }
}

@Composable
private fun CalendarFilter(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick).padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun AiringCard(entry: AiringScheduleEntry, onClick: () -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(entry.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000)
        }
    }
    val time = remember(entry.airingAt) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(entry.airingAt * 1000)) }
    val countdown = formatCountdown(entry.airingAt * 1000 - now)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(entry.coverUrl, contentDescription = entry.title, modifier = Modifier.size(76.dp, 106.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Episode ${entry.episode}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(time, fontWeight = FontWeight.SemiBold)
                Text(if (entry.airingAt * 1000 <= now) "Airing now" else countdown, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                entry.averageScore?.let { Text("★ ${it / 10f}", fontWeight = FontWeight.Bold) }
                entry.totalEpisodes?.let { total -> if (total > 0) Text("/ $total", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

private fun formatCountdown(millis: Long): String {
    if (millis <= 0) return "Airing now"
    val minutes = millis / 60_000
    val days = minutes / (60 * 24)
    val hours = (minutes / 60) % 24
    val mins = minutes % 60
    return when {
        days > 0 -> "in ${days}d ${hours}h"
        hours > 0 -> "in ${hours}h ${mins}m"
        else -> "in ${mins}m"
    }
}
