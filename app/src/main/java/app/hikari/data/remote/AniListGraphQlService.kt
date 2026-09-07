package app.hikari.data.remote

import app.hikari.core.auth.SecureTokenStore
import app.hikari.core.model.MediaSummary
import app.hikari.core.model.MediaType
import app.hikari.profile.AniListProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Thin AniList GraphQL transport used by the first real-data UI slice. */
@Singleton
class AniListGraphQlService @Inject constructor(
    private val client: OkHttpClient,
    private val tokenStore: SecureTokenStore,
) {
    suspend fun trending(type: MediaType, page: Int = 1): List<MediaSummary> = runCatching {
        mediaPage(
            "query(\$page:Int!, \$type:MediaType!){Page(page:\$page,perPage:20){media(type:\$type,sort:TRENDING_DESC){id type title{userPreferred romaji english native} coverImage{large} averageScore episodes chapters}}}",
            mapOf("page" to page, "type" to type.name), type,
        )
    }.getOrDefault(emptyList())

    suspend fun airingSoon(page: Int = 1, perPage: Int = 12, now: Long = System.currentTimeMillis() / 1000): List<MediaSummary> = runCatching {
        val query = "query(\$page:Int!, \$perPage:Int!, \$now:Int!){Page(page:\$page,perPage:\$perPage){airingSchedules(airingAt_greater:\$now,sort:TIME_ASC){episode airingAt media{id type title{userPreferred romaji english native} coverImage{large} averageScore episodes chapters}}}}"
        val data = execute(query, mapOf("page" to page, "perPage" to perPage, "now" to now))
            .getJSONObject("data").getJSONObject("Page").getJSONArray("airingSchedules")
        List(data.length()) { index ->
            val media = data.getJSONObject(index).getJSONObject("media")
            val titleObject = media.getJSONObject("title")
            val title = preferredTitle(titleObject)
            MediaSummary(
                id = media.getInt("id"), type = MediaType.ANIME, title = title,
                coverUrl = media.optJSONObject("coverImage")?.optString("large"),
                averageScore = media.optInt("averageScore").takeIf { it != 0 },
                episodesOrChapters = media.optInt("episodes").takeIf { it != 0 },
            )
        }
    }.getOrDefault(emptyList())

    suspend fun search(query: String, type: MediaType?, page: Int = 1): List<MediaSummary> = runCatching {
        mediaPage(
            "query(\$page:Int!, \$search:String!, \$type:MediaType){Page(page:\$page,perPage:20){media(search:\$search,type:\$type,sort:SEARCH_MATCH){id type title{userPreferred romaji english native} coverImage{large} averageScore episodes chapters}}}",
            buildMap { put("page", page); put("search", query); put("type", type?.name) }, type,
        )
    }.getOrDefault(emptyList())

    suspend fun viewerProfile(): AniListProfile {
        val viewer = execute(
            "query{Viewer{id name avatar{large} bannerImage about statistics{anime{count episodesWatched minutesWatched meanScore} manga{count chaptersRead volumesRead meanScore}}}}",
            emptyMap(),
        ).getJSONObject("data").getJSONObject("Viewer")

        val statistics = viewer.optJSONObject("statistics") ?: JSONObject()
        val anime = statistics.optJSONObject("anime") ?: JSONObject()
        val manga = statistics.optJSONObject("manga") ?: JSONObject()

        val aggregateAnimeCount = anime.optInt("count")
        val aggregateEpisodesWatched = anime.optInt("episodesWatched")
        val aggregateDaysWatched = anime.optInt("minutesWatched") / 1440.0
        val aggregateAnimeMeanScore = anime.optDouble("meanScore", 0.0)
        val aggregateMangaCount = manga.optInt("count")
        val aggregateChaptersRead = manga.optInt("chaptersRead")
        val aggregateVolumesRead = manga.optInt("volumesRead")
        val aggregateMangaMeanScore = manga.optDouble("meanScore", 0.0)

        val syncedStats = runCatching {
            loadViewerListStats(viewer.getInt("id"))
        }.getOrNull()

        val stats = syncedStats ?: ListStats(
            animeCount = aggregateAnimeCount,
            episodesWatched = aggregateEpisodesWatched,
            daysWatched = aggregateDaysWatched,
            animeMeanScore = aggregateAnimeMeanScore,
            mangaCount = aggregateMangaCount,
            chaptersRead = aggregateChaptersRead,
            volumesRead = aggregateVolumesRead,
            mangaMeanScore = aggregateMangaMeanScore,
        )

        return AniListProfile(
            id = viewer.getInt("id"), name = viewer.getString("name"),
            avatarUrl = viewer.optJSONObject("avatar")?.optString("large"),
            bannerUrl = viewer.optionalText("bannerImage"),
            about = viewer.optionalText("about"),
            animeCount = stats.animeCount,
            episodesWatched = stats.episodesWatched,
            daysWatched = stats.daysWatched,
            animeMeanScore = stats.animeMeanScore,
            mangaCount = stats.mangaCount,
            chaptersRead = stats.chaptersRead,
            volumesRead = stats.volumesRead,
            daysRead = 0.0,
            mangaMeanScore = stats.mangaMeanScore,
        )
    }

    private suspend fun loadViewerListStats(userId: Int): ListStats {
        val animeQuery = "query(\$userId:Int!){MediaListCollection(userId:\$userId,type:ANIME){lists{name entries{id score progress media{id duration}}}}}"
        val mangaQuery = "query(\$userId:Int!){MediaListCollection(userId:\$userId,type:MANGA){lists{name entries{id score progress progressVolumes media{id}}}}}"

        val animeLists = execute(animeQuery, mapOf("userId" to userId))
            .getJSONObject("data").getJSONObject("MediaListCollection").getJSONArray("lists")
        val mangaLists = execute(mangaQuery, mapOf("userId" to userId))
            .getJSONObject("data").getJSONObject("MediaListCollection").getJSONArray("lists")

        val animeEntries = uniqueListEntries(animeLists)
        val mangaEntries = uniqueListEntries(mangaLists)

        val animeScored = animeEntries.mapNotNull { it.optDouble("score", 0.0).takeIf { score -> score > 0.0 } }
        val mangaScored = mangaEntries.mapNotNull { it.optDouble("score", 0.0).takeIf { score -> score > 0.0 } }
        val animeMinutes = animeEntries.sumOf { entry ->
            val progress = entry.optInt("progress")
            val duration = entry.optJSONObject("media")?.optInt("duration") ?: 0
            progress * duration
        }

        return ListStats(
            animeCount = animeEntries.size,
            episodesWatched = animeEntries.sumOf { it.optInt("progress") },
            daysWatched = animeMinutes / 1440.0,
            animeMeanScore = animeScored.average().takeIf { !it.isNaN() } ?: 0.0,
            mangaCount = mangaEntries.size,
            chaptersRead = mangaEntries.sumOf { it.optInt("progress") },
            volumesRead = mangaEntries.sumOf { it.optInt("progressVolumes") },
            mangaMeanScore = mangaScored.average().takeIf { !it.isNaN() } ?: 0.0,
        )
    }

    private fun uniqueListEntries(lists: JSONArray): List<JSONObject> {
        val entriesById = linkedMapOf<Int, JSONObject>()
        for (i in 0 until lists.length()) {
            val values = lists.getJSONObject(i).optJSONArray("entries") ?: continue
            for (j in 0 until values.length()) {
                val entry = values.getJSONObject(j)
                val entryId = entry.optInt("id", 0)
                if (entryId != 0) entriesById.putIfAbsent(entryId, entry)
            }
        }
        return entriesById.values.toList()
    }

    private data class ListStats(
        val animeCount: Int,
        val episodesWatched: Int,
        val daysWatched: Double,
        val animeMeanScore: Double,
        val mangaCount: Int,
        val chaptersRead: Int,
        val volumesRead: Int,
        val mangaMeanScore: Double,
    )

    private suspend fun mediaPage(query: String, variables: Map<String, Any?>, requestedType: MediaType?): List<MediaSummary> {
        val data = execute(query, variables).getJSONObject("data").getJSONObject("Page").getJSONArray("media")
        return data.toMediaList(requestedType)
    }

    private suspend fun execute(query: String, variables: Map<String, Any?>): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().put("query", query).put("variables", JSONObject(variables)).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("https://graphql.anilist.co").post(body).apply {
            tokenStore.accessToken()?.let { header("Authorization", "Bearer $it") }
            header("Accept", "application/json")
        }.build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            check(response.isSuccessful) { "AniList request failed (${response.code})" }
            JSONObject(payload).also { result -> check(!result.has("errors")) { result.getJSONArray("errors").toString() } }
        }
    }
}

private fun JSONObject.optionalText(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

private fun preferredTitle(title: JSONObject): String = listOf("userPreferred", "english", "romaji", "native")
    .asSequence()
    .map { title.optString(it) }
    .firstOrNull { it.isNotBlank() && it != "null" }
    ?: "Untitled"

private fun JSONArray.toMediaList(requestedType: MediaType?): List<MediaSummary> = List(length()) { index ->
    val item = getJSONObject(index)
    val type = requestedType ?: MediaType.valueOf(item.getString("type"))
    val title = preferredTitle(item.getJSONObject("title"))
    MediaSummary(
        id = item.getInt("id"), type = type, title = title,
        coverUrl = item.optJSONObject("coverImage")?.optString("large"), averageScore = item.optInt("averageScore").takeIf { it != 0 },
        episodesOrChapters = (if (type == MediaType.MANGA) item.optInt("chapters") else item.optInt("episodes")).takeIf { it != 0 },
    )
}