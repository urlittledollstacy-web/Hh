package app.hikari.data.local

import app.hikari.core.model.MediaSummary
import app.hikari.core.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HikariFavoritesRepository @Inject constructor(
    private val dao: HikariFavoriteDao,
) {
    fun observeIsFavorite(mediaId: Int, type: MediaType): Flow<Boolean> = dao.observeIsFavorite(mediaId, type)

    fun observeFavorites(): Flow<List<MediaSummary>> = dao.observeAll().map { favorites ->
        favorites.map { favorite ->
            MediaSummary(
                id = favorite.mediaId,
                type = favorite.type,
                title = favorite.title,
                coverUrl = favorite.coverUrl,
                averageScore = favorite.averageScore,
                episodesOrChapters = favorite.episodesOrChapters,
            )
        }
    }

    suspend fun toggle(media: MediaSummary, isFavorite: Boolean) {
        if (isFavorite) {
            dao.remove(media.id, media.type)
        } else {
            dao.add(
                HikariFavoriteEntity(
                    mediaId = media.id,
                    type = media.type,
                    title = media.title,
                    coverUrl = media.coverUrl,
                    averageScore = media.averageScore,
                    episodesOrChapters = media.episodesOrChapters,
                ),
            )
        }
    }
}
