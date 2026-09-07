package app.hikari.data.remote

import app.hikari.core.auth.SecureTokenStore
import app.hikari.core.model.AiringScheduleEntry
import app.hikari.core.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AniListCalendarService @Inject constructor(
    private val client: OkHttpClient,
    private val tokenStore: SecureTokenStore,
) {
    suspend fun airingSchedule(from: Long, to: Long): List<AiringScheduleEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val results = mutableListOf<AiringScheduleEntry>()
            for (page in 1..6) {
                val query = "query(\$page:Int!,\$from:Int!,\$to:Int!){Page(page:\$page,perPage:50){pageInfo{hasNextPage}airingSchedules(airingAt_greater:\$from,airingAt_lesser:\$to,sort:TIME_ASC){id airingAt timeUntilAiring episode media{id type title{userPreferred english romaji native} coverImage{large} averageScore episodes chapters}}}}"
                val pageObject = execute(query, mapOf("page" to page, "from" to from, "to" to to))
                    .getJSONObject("data").getJSONObject("Page")
                val data = pageObject.getJSONArray("airingSchedules")
                for (index in 0 until data.length()) {
                    val item = data.getJSONObject(index)
                    val media = item.getJSONObject("media")
                    results += AiringScheduleEntry(
                        id = item.getLong("id"),
                        airingAt = item.getLong("airingAt"),
                        timeUntilAiring = item.optLong("timeUntilAiring"),
                        episode = item.optInt("episode"),
                        mediaId = media.getInt("id"),
                        mediaType = runCatching { MediaType.valueOf(media.optString("type")) }.getOrDefault(MediaType.ANIME),
                        title = preferredTitle(media.optJSONObject("title")),
                        coverUrl = media.optJSONObject("coverImage")?.optString("large")?.takeIf { it.isNotBlank() },
                        averageScore = media.optInt("averageScore").takeIf { it > 0 },
                        totalEpisodes = media.optInt("episodes").takeIf { it > 0 },
                    )
                }
                if (!pageObject.getJSONObject("pageInfo").optBoolean("hasNextPage")) break
            }
            results.distinctBy { it.id }.sortedBy { it.airingAt }
        }.getOrDefault(emptyList())
    }

    private fun execute(query: String, variables: Map<String, Any?>): JSONObject {
        val body = JSONObject().put("query", query).put("variables", JSONObject(variables)).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("https://graphql.anilist.co").post(body).apply {
            tokenStore.accessToken()?.let { header("Authorization", "Bearer $it") }
            header("Accept", "application/json")
        }.build()
        return client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            check(response.isSuccessful) { "AniList calendar request failed (${response.code})" }
            JSONObject(payload).also { result ->
                check(!result.has("errors")) { result.getJSONArray("errors").toString() }
            }
        }
    }
}

private fun preferredTitle(title: JSONObject?): String = title?.let {
    listOf("userPreferred", "english", "romaji", "native")
        .asSequence()
        .map { key -> it.optString(key) }
        .firstOrNull { value -> value.isNotBlank() && value != "null" }
} ?: "Untitled"
