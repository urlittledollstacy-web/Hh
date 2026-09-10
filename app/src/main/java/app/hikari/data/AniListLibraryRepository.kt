package app.hikari.data

import app.hikari.core.model.LibraryEntry
import app.hikari.core.model.MediaType
import app.hikari.data.remote.AniListLibraryRemote
import app.hikari.data.remote.LibrarySnapshot
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide source of truth for the signed-in user's AniList tracking data.
 *
 * Anime and manga are stored and serialized independently. A successful mutation
 * is published from AniList's mutation response while holding the same per-type
 * lock, so observers receive the server-confirmed entry immediately without a
 * potentially stale follow-up library read overwriting it.
 */
@Singleton
class AniListLibraryRepository @Inject constructor(
    private val remote: AniListLibraryRemote,
) {
    private val snapshots = MutableStateFlow<Map<MediaType, LibrarySnapshot>>(emptyMap())
    private val locks = MediaType.entries.associateWith { Mutex() }
    private val accountGeneration = AtomicLong(0L)
    private val _changes = MutableSharedFlow<MediaType>(extraBufferCapacity = MediaType.entries.size)
    private val _sessionVersion = MutableStateFlow(0L)

    val state: StateFlow<Map<MediaType, LibrarySnapshot>> = snapshots.asStateFlow()
    val changes: SharedFlow<MediaType> = _changes.asSharedFlow()
    val sessionVersion: StateFlow<Long> = _sessionVersion.asStateFlow()

    fun observe(type: MediaType): Flow<LibrarySnapshot?> =
        state.map { it[type] }.distinctUntilChanged()

    suspend fun refresh(type: MediaType): LibrarySnapshot = locks.getValue(type).withLock {
        val generation = accountGeneration.get()
        val snapshot = remote.library(type)
        publishIfCurrent(type, snapshot, generation)
        snapshot
    }

    suspend fun updateEntry(
        type: MediaType,
        entry: LibraryEntry,
        status: String,
        progress: Int,
        score: Double,
    ): LibrarySnapshot = locks.getValue(type).withLock {
        require(entry.media.type == type) { "Library entry type does not match the selected library." }
        val generation = accountGeneration.get()
        val savedEntry = remote.updateEntry(entry, status, progress, score)
        val current = snapshots.value[type]
        val snapshot = mergeSavedEntry(current, savedEntry)
        if (publishIfCurrent(type, snapshot, generation)) {
            _changes.emit(type)
        }
        snapshot
    }

    /** Prevent data from a previous account being shown after sign-out/sign-in. */
    fun clear() {
        accountGeneration.incrementAndGet()
        snapshots.value = emptyMap()
        _sessionVersion.value += 1
    }

    private fun publishIfCurrent(
        type: MediaType,
        snapshot: LibrarySnapshot,
        generation: Long,
    ): Boolean {
        if (accountGeneration.get() != generation) return false
        snapshots.update { current -> current + (type to snapshot) }
        return true
    }

    private fun mergeSavedEntry(
        current: LibrarySnapshot?,
        saved: LibraryEntry,
    ): LibrarySnapshot {
        val entries = current?.entries.orEmpty().toMutableList()
        val index = entries.indexOfFirst { it.id == saved.id || it.media.id == saved.media.id }
        if (index >= 0) entries[index] = saved else entries += saved
        return LibrarySnapshot(
            entries = entries,
            scoreFormat = current?.scoreFormat ?: app.hikari.core.model.ScoreFormat.POINT_100,
        )
    }
}
