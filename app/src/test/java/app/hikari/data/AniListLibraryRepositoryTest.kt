package app.hikari.data

import app.hikari.core.model.LibraryEntry
import app.hikari.core.model.MediaSummary
import app.hikari.core.model.MediaType
import app.hikari.core.model.ScoreFormat
import app.hikari.data.remote.AniListLibraryRemote
import app.hikari.data.remote.LibrarySnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AniListLibraryRepositoryTest {
    @Test
    fun `anime and manga snapshots remain separated`() = runBlocking {
        val remote = FakeLibraryRemote()
        val repository = AniListLibraryRepository(remote)

        repository.refresh(MediaType.ANIME)
        repository.refresh(MediaType.MANGA)

        assertEquals(listOf(ANIME_ENTRY), repository.state.value[MediaType.ANIME]?.entries)
        assertEquals(listOf(MANGA_ENTRY), repository.state.value[MediaType.MANGA]?.entries)
    }

    @Test
    fun `mutation response is published even when full refresh fails`() = runBlocking {
        val remote = FakeLibraryRemote()
        val repository = AniListLibraryRepository(remote)
        repository.refresh(MediaType.ANIME)
        remote.failNextAnimeRefresh = true
        val change = async(start = CoroutineStart.UNDISPATCHED) { repository.changes.first() }

        repository.updateEntry(MediaType.ANIME, ANIME_ENTRY, "COMPLETED", 12, 90.0)

        val saved = repository.state.value.getValue(MediaType.ANIME).entries.single()
        assertEquals("COMPLETED", saved.status)
        assertEquals(12, saved.progress)
        assertEquals(90.0, saved.score ?: 0.0, 0.0)
        assertEquals(MediaType.ANIME, change.await())
    }

    @Test
    fun `same-type operations are serialized while different types remain independent`() = runBlocking {
        val remote = FakeLibraryRemote()
        val repository = AniListLibraryRepository(remote)

        val animeOne = async { repository.refresh(MediaType.ANIME) }
        val animeTwo = async { repository.refresh(MediaType.ANIME) }
        val manga = async { repository.refresh(MediaType.MANGA) }
        animeOne.await()
        animeTwo.await()
        manga.await()

        assertEquals(1, remote.maxConcurrentByType.getValue(MediaType.ANIME))
        assertEquals(1, remote.maxConcurrentByType.getValue(MediaType.MANGA))
    }

    @Test
    fun `clear removes every account snapshot`() = runBlocking {
        val repository = AniListLibraryRepository(FakeLibraryRemote())
        repository.refresh(MediaType.ANIME)
        repository.clear()

        assertNull(repository.state.value[MediaType.ANIME])
        assertNull(repository.state.value[MediaType.MANGA])
    }

    @Test
    fun `request started before account clear cannot republish old data`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val remote = object : AniListLibraryRemote {
            override suspend fun library(type: MediaType): LibrarySnapshot {
                started.complete(Unit)
                release.await()
                return LibrarySnapshot(listOf(ANIME_ENTRY), ScoreFormat.POINT_100)
            }

            override suspend fun updateEntry(
                entry: LibraryEntry,
                status: String,
                progress: Int,
                score: Double,
            ) = entry
        }
        val repository = AniListLibraryRepository(remote)
        val refresh = async { repository.refresh(MediaType.ANIME) }
        started.await()

        repository.clear()
        release.complete(Unit)
        refresh.await()

        assertNull(repository.state.value[MediaType.ANIME])
    }

    private class FakeLibraryRemote : AniListLibraryRemote {
        var failNextAnimeRefresh = false
        val maxConcurrentByType = MediaType.entries.associateWith { 0 }.toMutableMap()
        private val activeByType = MediaType.entries.associateWith { 0 }.toMutableMap()

        override suspend fun library(type: MediaType): LibrarySnapshot {
            synchronized(this) {
                activeByType[type] = activeByType.getValue(type) + 1
                maxConcurrentByType[type] = maxOf(maxConcurrentByType.getValue(type), activeByType.getValue(type))
            }
            try {
                yield()
                if (type == MediaType.ANIME && failNextAnimeRefresh) {
                    failNextAnimeRefresh = false
                    error("refresh failed")
                }
                return LibrarySnapshot(
                    entries = listOf(if (type == MediaType.ANIME) ANIME_ENTRY else MANGA_ENTRY),
                    scoreFormat = ScoreFormat.POINT_100,
                )
            } finally {
                synchronized(this) { activeByType[type] = activeByType.getValue(type) - 1 }
            }
        }

        override suspend fun updateEntry(
            entry: LibraryEntry,
            status: String,
            progress: Int,
            score: Double,
        ): LibraryEntry = entry.copy(status = status, progress = progress, score = score)
    }

    private companion object {
        val ANIME_ENTRY = LibraryEntry(
            id = 1,
            media = MediaSummary(10, MediaType.ANIME, "Anime", null, null, 12),
            status = "CURRENT",
            progress = 3,
            score = null,
        )
        val MANGA_ENTRY = LibraryEntry(
            id = 2,
            media = MediaSummary(20, MediaType.MANGA, "Manga", null, null, 50),
            status = "CURRENT",
            progress = 8,
            score = null,
        )
    }
}
