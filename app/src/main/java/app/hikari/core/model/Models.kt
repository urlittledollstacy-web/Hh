package app.hikari.core.model

enum class MediaType { ANIME, MANGA }

enum class ScoreFormat {
    POINT_100,
    POINT_10_DECIMAL,
    POINT_10,
    POINT_5,
    POINT_3,
}

data class MediaSummary(
    val id: Int,
    val type: MediaType,
    val title: String,
    val coverUrl: String?,
    val averageScore: Int?,
    val episodesOrChapters: Int?,
)

data class MediaRelation(
    val relationType: String,
    val media: MediaSummary,
)

data class MediaCharacter(
    val id: Int,
    val name: String,
    val imageUrl: String?,
    val role: String,
    val voiceActorName: String?,
    val voiceActorImageUrl: String?,
)

data class MediaStaff(
    val id: Int,
    val name: String,
    val imageUrl: String?,
    val roles: List<String>,
)

data class MediaDetail(
    val summary: MediaSummary,
    val description: String?,
    val status: String?,
    val format: String?,
    val duration: Int?,
    val startDate: String?,
    val endDate: String?,
    val season: String?,
    val seasonYear: Int?,
    val genres: List<String>,
    val tags: List<String>,
    val popularity: Int?,
    val favourites: Int?,
    val bannerUrl: String?,
    val studios: List<String>,
    val relations: List<MediaRelation>,
    val characters: List<MediaCharacter>,
    val staff: List<MediaStaff>,
)

data class AniListUser(val id: Int, val name: String, val avatarUrl: String?, val about: String?)

data class LibraryEntry(val id: Int, val media: MediaSummary, val status: String, val progress: Int, val score: Double?)

data class AiringScheduleEntry(
    val id: Long,
    val airingAt: Long,
    val timeUntilAiring: Long,
    val episode: Int,
    val mediaId: Int,
    val mediaType: MediaType,
    val title: String,
    val coverUrl: String?,
    val averageScore: Int?,
    val totalEpisodes: Int?,
)
