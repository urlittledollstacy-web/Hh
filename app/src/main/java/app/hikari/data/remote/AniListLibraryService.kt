package app.hikari.data.remote

import app.hikari.core.auth.SecureTokenStore
import app.hikari.core.model.LibraryEntry
import app.hikari.core.model.MediaSummary
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
class AniListLibraryService @Inject constructor(
    private val client: OkHttpClient,
    private val tokenStore: SecureTokenStore,
) {
    suspend fun library(type: MediaType): List<LibraryEntry> = withContext(Dispatchers.IO) {
        val query = "query(\$userId:Int!,\$type:MediaType!){MediaListCollection(userId:\$userId,type:\$type){lists{name entries{id status score progress media{id type title{romaji english} coverImage{large} averageScore episodes chapters}}}}}"
        val viewer = execute("query{Viewer{id}}", emptyMap()).getJSONObject("data").getJSONObject("Viewer")
        val lists = execute(query, mapOf("userId" to viewer.getInt("id"), "type" to type.name))
            .getJSONObject("data").getJSONObject("MediaListCollection").getJSONArray("lists")

        val unique = linkedMapOf<Int, LibraryEntry>()
        for (i in 0 until lists.length()) {
            val entries = lists.getJSONObject(i).optJSONArray("entries") ?: continue
            for (j in 0 until entries.length()) {
                val entry = entries.getJSONObject(j)
                val id = entry.optInt("id", 0)
                if (id == 0 || unique.containsKey(id)) continue
                val media = entry.getJSONObject("media")
                val title = media.getJSONObject("title").optString("english").ifBlank {
                    media.getJSONObject("title").optString("romaji")
                }
                unique[id] = LibraryEntry(
                    id = id,
                    media = MediaSummary(
                        id = media.getInt("id"),
                        type = type,
                        title = title,
                        coverUrl = media.optJSONObject("coverImage")?.optString("large"),
                        averageScore = media.optInt("averageScore").takeIf { it != 0 },
                        episodesOrChapters = (
                            if (type == MediaType.MANGA) media.optInt("chapters")
                            else media.optInt("episodes")
                        ).takeIf { it != 0 },
                    ),
                    status = entry.optString("status", "UNKNOWN"),
                    progress = entry.optInt("progress"),
                    score = entry.optDouble("score", 0.0).takeIf { it > 0.0 },
                )
            }
        }
        return@withContext unique.values.toList()
    }

    private fun execute(query: String, variables: Map<String, Any?>): JSONObject {
        val body = JSONObject()
            .put("query", query)
            .put("variables", JSONObject(variables))
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(body)
            .apply {
                tokenStore.accessToken()?.let { header("Authorization", "Bearer $it") }
                header("Accept", "application/json")
            }
            .build()
        return client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            check(response.isSuccessful) { "AniList library request failed (${response.code})" }
            JSONObject(payload).also { result ->
                check(!result.has("errors")) { result.getJSONArray("errors").toString() }
            }
        }
    }
}
