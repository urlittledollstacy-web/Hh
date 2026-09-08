package app.hikari.data

import app.hikari.core.model.LibraryEntry
import app.hikari.core.model.MediaType
import app.hikari.data.remote.AniListLibraryService
import app.hikari.data.remote.LibrarySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AniListLibraryRepository @Inject constructor(
    private val remote: AniListLibraryService,
) {
    private val snapshots = MutableStateFlow<Map<MediaType, LibrarySnapshot>>(emptyMap())
    private val locks = MediaType.values().associateWith { Mutex() }

    private val _changes = MutableSharedFlow<MediaType>(
        extraBufferCapacity = MediaType.values().size,
    )
    val changes: Flow<MediaType> = _changes.asSharedFlow()

    fun observe(type: MediaType): Flow<LibrarySnapshot?> =
        snapshots.map { it[type] }.distinctUntilChanged()

    suspend fun refresh(type: MediaType): LibrarySnapshot =
        locks.getValue(type).withLock {
            remote.library(type).also { snapshot ->
                snapshots.update { it + (type to snapshot) }
            }
        }

    suspend fun save(
        entry: LibraryEntry,
        status: String,
        progress: Int,
        score: Double,
    ): LibrarySnapshot {
        val type = entry.media.type
        val snapshot = locks.getValue(type).withLock {
            remote.updateEntry(entry, status, progress, score)
            remote.library(type).also { refreshed ->
                snapshots.update { it + (type to refreshed) }
            }
        }
        _changes.emit(type)
        return snapshot
    }
}
