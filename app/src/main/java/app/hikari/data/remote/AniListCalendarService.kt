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
        val query = "query(\$from:Int!,\$to:Int!){Page(page:1,perPage:50){airingSchedules(airingAt_greater:\$from,airingAt_lesser:\$to,sort:TIME_ASC){id airingAt timeUntilAiring episode media{id type title{userPreferred english romaji native} coverImage{large} averageScore episodes chapters}}}}"
        runCatching {
            val data = execute(query, mapOf("from" to from, "to" to to))
                .getJSONObject("data").getJSONObject("Page").getJSONArray("airingSchedules")
            List(data.length()) { index ->
                val item = data.getJSONObject(index)
                val media = item.getJSONObject("media")
                val title = preferredTitle(media.optJSONObject("title"))
                AiringScheduleEntry(
                    id = item.getLong("id"),
                    airingAt = item.getLong("airingAt"),
                    timeUntilAiring = item.optLong("timeUntilAiring"),
                    episode = item.optInt("episode"),
                    mediaId = media.getInt("id"),
                    mediaType = runCatching { MediaType.valueOf(media.optString("type")) }.getOrDefault(MediaType.ANIME),
                    title = title,
                    coverUrl = media.optJSONObject("coverImage")?.optString("large")?.takeIf { it.isNotBlank() },
                    averageScore = media.optInt("averageScore").takeIf { it > 0 },
                    totalEpisodes = media.optInt("episodes").takeIf { it > 0 },
                )
            }
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
