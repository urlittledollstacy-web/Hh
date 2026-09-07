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
            "query(\$page:Int!, \$type:MediaType!){Page(page:\$page,perPage:20){media(type:\$type,sort:TRENDING_DESC){id type title{romaji english} coverImage{large} averageScore episodes chapters}}}",
            mapOf("page" to page, "type" to type.name), type,
        )
    }.getOrDefault(emptyList())

    suspend fun airingSoon(page: Int = 1, perPage: Int = 12, now: Long = System.currentTimeMillis() / 1000): List<MediaSummary> = runCatching {
        val query = "query(\$page:Int!, \$perPage:Int!, \$now:Int!){Page(page:\$page,perPage:\$perPage){airingSchedules(airingAt_greater:\$now,sort:TIME_ASC){episode airingAt media{id type title{romaji english} coverImage{large} averageScore episodes chapters}}}}"
        val data = execute(query, mapOf("page" to page, "perPage" to perPage, "now" to now))
            .getJSONObject("data").getJSONObject("Page").getJSONArray("airingSchedules")
        List(data.length()) { index ->
            val media = data.getJSONObject(index).getJSONObject("media")
            val titleObject = media.getJSONObject("title")
            val title = titleObject.optString("english").ifBlank { titleObject.optString("romaji") }
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
            "query(\$page:Int!, \$search:String!, \$type:MediaType){Page(page:\$page,perPage:20){media(search:\$search,type:\$type,sort:SEARCH_MATCH){id type title{romaji english} coverImage{large} averageScore episodes chapters}}}",
            buildMap { put("page", page); put("search", query); put("type", type?.name) }, type,
        )
    }.getOrDefault(emptyList())

    suspend fun viewerProfile(): AniListProfile = execute(
        "query{Viewer{id name avatar{large} bannerImage about statistics{anime{count episodesWatched minutesWatched meanScore} manga{count chaptersRead volumesRead meanScore}}}}",
        emptyMap(),
    ).getJSONObject("data").getJSONObject("Viewer").let { viewer ->
        val statistics = viewer.optJSONObject("statistics") ?: JSONObject()
        val anime = statistics.optJSONObject("anime") ?: JSONObject()
        val manga = statistics.optJSONObject("manga") ?: JSONObject()
        AniListProfile(
            id = viewer.getInt("id"), name = viewer.getString("name"),
            avatarUrl = viewer.optJSONObject("avatar")?.optString("large"),
            bannerUrl = viewer.optionalText("bannerImage"),
            about = viewer.optionalText("about"),
            animeCount = anime.optInt("count"), episodesWatched = anime.optInt("episodesWatched"),
            daysWatched = anime.optInt("minutesWatched") / 1440.0, animeMeanScore = anime.optDouble("meanScore", 0.0),
            mangaCount = manga.optInt("count"), chaptersRead = manga.optInt("chaptersRead"), volumesRead = manga.optInt("volumesRead"),
            daysRead = 0.0, mangaMeanScore = manga.optDouble("meanScore", 0.0),
        )
    }

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

private fun JSONArray.toMediaList(requestedType: MediaType?): List<MediaSummary> = List(length()) { index ->
    val item = getJSONObject(index)
    val title = item.getJSONObject("title").optString("english").ifBlank { item.getJSONObject("title").optString("romaji") }
    MediaSummary(
        id = item.getInt("id"), type = requestedType ?: MediaType.valueOf(item.getString("type")), title = title,
        coverUrl = item.optJSONObject("coverImage")?.optString("large"), averageScore = item.optInt("averageScore").takeIf { it != 0 },
        episodesOrChapters = (if (requestedType == MediaType.MANGA) item.optInt("chapters") else item.optInt("episodes")).takeIf { it != 0 },
    )
}
