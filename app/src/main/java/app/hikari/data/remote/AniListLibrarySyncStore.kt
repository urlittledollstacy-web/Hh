package app.hikari.data.remote

import app.hikari.core.model.LibraryEntry
import app.hikari.core.model.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AniListLibrarySyncStore @Inject constructor() {
    private val _updates = MutableStateFlow<Map<Pair<MediaType, Int>, LibraryEntry>>(emptyMap())
    val updates: StateFlow<Map<Pair<MediaType, Int>, LibraryEntry>> = _updates.asStateFlow()

    fun publish(entry: LibraryEntry) {
        _updates.value = _updates.value + ((entry.media.type to entry.media.id) to entry)
    }
}
