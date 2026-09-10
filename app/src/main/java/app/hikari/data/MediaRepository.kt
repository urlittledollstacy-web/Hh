package app.hikari.data

import app.hikari.core.model.MediaSummary
import app.hikari.core.model.MediaType
import app.hikari.data.local.CachedMediaEntity
import app.hikari.data.local.MediaCacheDao
import app.hikari.data.remote.AniListGraphQlService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface MediaRepository {
    fun observeTrending(type: MediaType): Flow<List<MediaSummary>>
    suspend fun refreshTrending(type: MediaType, page: Int = 1)
    suspend fun search(query: String, type: MediaType?, page: Int = 1): List<MediaSummary>
}

@Singleton
class DefaultMediaRepository @Inject constructor(
    private val remote: AniListGraphQlService,
    private val cache: MediaCacheDao,
) : MediaRepository {
    override fun observeTrending(type: MediaType): Flow<List<MediaSummary>> = cache.observe(type, 20).map { rows -> rows.map { it.toModel() } }

    override suspend fun refreshTrending(type: MediaType, page: Int) {
        cache.upsertAll(remote.trending(type, page).map { it.toEntity() })
    }

    override suspend fun search(query: String, type: MediaType?, page: Int) = remote.search(query, type, page)
}

private fun MediaSummary.toEntity() = CachedMediaEntity(id, type, title, coverUrl, averageScore, episodesOrChapters)
private fun CachedMediaEntity.toModel() = MediaSummary(id, type, title, coverUrl, averageScore, count)
