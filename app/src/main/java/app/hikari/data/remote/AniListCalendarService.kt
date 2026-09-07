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
        val results = mutableListOf<AiringScheduleEntry>()
        // Keep the calendar responsive: four pages cover a broad set of currently
        // releasing anime without the previous ten sequential network requests.
        for (page in 1..4) {
            val query = "query(\$page:Int!){Page(page:\$page,perPage:50){pageInfo{hasNextPage}media(type:ANIME,status:RELEASING){id title{userPreferred english romaji native} coverImage{large} averageScore episodes nextAiringEpisode{id airingAt timeUntilAiring episode}}}}"
            val pageObject = execute(query, mapOf("page" to page))
                .getJSONObject("data").getJSONObject("Page")
            val data = pageObject.getJSONArray("media")
            for (index in 0 until data.length()) {
                val media = data.getJSONObject(index)
                val next = media.optJSONObject("nextAiringEpisode") ?: continue
                val airingAt = next.getLong("airingAt")
                if (airingAt < from || airingAt >= to) continue
                results += AiringScheduleEntry(
                    id = next.getLong("id"),
                    airingAt = airingAt,
                    timeUntilAiring = next.optLong("timeUntilAiring"),
                    episode = next.optInt("episode"),
                    mediaId = media.getInt("id"),
                    mediaType = MediaType.ANIME,
                    title = preferredTitle(media.optJSONObject("title")),
                    coverUrl = media.optJSONObject("coverImage")?.optString("large")?.takeIf { it.isNotBlank() },
                    averageScore = media.optInt("averageScore").takeIf { it > 0 },
                    totalEpisodes = media.optInt("episodes").takeIf { it > 0 },
                )
            }
            if (!pageObject.getJSONObject("pageInfo").optBoolean("hasNextPage")) break
        }
        results.distinctBy { it.id }.sortedBy { it.airingAt }
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
