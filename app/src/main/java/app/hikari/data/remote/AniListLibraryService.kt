package app.hikari.data.remote

import app.hikari.core.auth.SecureTokenStore
import app.hikari.core.model.LibraryEntry
import app.hikari.core.model.MediaSummary
import app.hikari.core.model.MediaType
import app.hikari.core.model.ScoreFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class LibrarySnapshot(
    val entries: List<LibraryEntry>,
    val scoreFormat: ScoreFormat,
)

@Singleton
class AniListLibraryService @Inject constructor(
    private val client: OkHttpClient,
    private val tokenStore: SecureTokenStore,
) {
    suspend fun library(type: MediaType): LibrarySnapshot = withContext(Dispatchers.IO) {
        val query = "query(\$userId:Int!,\$type:MediaType!){MediaListCollection(userId:\$userId,type:\$type){lists{name entries{id status score progress media{id type title{userPreferred romaji english native} coverImage{large} averageScore episodes chapters}}}}}"
        val viewer = execute("query{Viewer{id mediaListOptions{scoreFormat}}}", emptyMap()).getJSONObject("data").getJSONObject("Viewer")
        val scoreFormat = parseScoreFormat(viewer.getJSONObject("mediaListOptions").optString("scoreFormat"))
        val lists = execute(query, mapOf("userId" to viewer.getInt("id"), "type" to type.name))
            .getJSONObject("data").getJSONObject("MediaListCollection").getJSONArray("lists")

        val unique = linkedMapOf<Int, LibraryEntry>()
        for (i in 0 until lists.length()) {
            val entries = lists.getJSONObject(i).optJSONArray("entries") ?: continue
            for (j in 0 until entries.length()) {
                val entry = entries.getJSONObject(j)
                val id = entry.optInt("id", 0)
                if (id == 0 || unique.containsKey(id)) continue
                val media = entry.optJSONObject("media") ?: continue
                val mediaId = media.optInt("id", 0)
                if (mediaId == 0) continue
                val titleObject = media.optJSONObject("title")
                val title = titleObject?.let {
                    listOf("userPreferred", "english", "romaji", "native").asSequence()
                        .mapNotNull { key -> if (it.has(key) && !it.isNull(key)) it.optString(key).trim() else null }
                        .firstOrNull { value -> value.isNotBlank() && value != "null" }
                } ?: "Untitled #$mediaId"
                unique[id] = LibraryEntry(
                    id = id,
                    media = MediaSummary(
                        id = mediaId,
                        type = type,
                        title = title,
                        coverUrl = media.optJSONObject("coverImage")?.optString("large")?.takeIf { it.isNotBlank() },
                        averageScore = media.optInt("averageScore").takeIf { it != 0 },
                        episodesOrChapters = (if (type == MediaType.MANGA) media.optInt("chapters") else media.optInt("episodes")).takeIf { it != 0 },
                    ),
                    status = entry.optString("status", "UNKNOWN"),
                    progress = entry.optInt("progress"),
                    score = entry.optDouble("score", 0.0).takeIf { it > 0.0 },
                )
            }
        }
        return@withContext LibrarySnapshot(unique.values.toList(), scoreFormat)
    }

    suspend fun updateEntry(entry: LibraryEntry, status: String, progress: Int, score: Double) = withContext(Dispatchers.IO) {
        val mutation = "mutation(\$id:Int,\$mediaId:Int,\$status:MediaListStatus,\$score:Float,\$progress:Int){SaveMediaListEntry(id:\$id,mediaId:\$mediaId,status:\$status,score:\$score,progress:\$progress){id status progress score}}"
        execute(
            mutation,
            mapOf(
                "id" to entry.id,
                "mediaId" to entry.media.id,
                "status" to status,
                "score" to score,
                "progress" to progress,
            ),
        )
    }

    private fun parseScoreFormat(value: String): ScoreFormat = when (value) {
        "POINT_10_DECIMAL" -> ScoreFormat.POINT_10_DECIMAL
        "POINT_10" -> ScoreFormat.POINT_10
        "POINT_5" -> ScoreFormat.POINT_5
        "POINT_3" -> ScoreFormat.POINT_3
        else -> ScoreFormat.POINT_100
    }

    private fun execute(query: String, variables: Map<String, Any?>): JSONObject {
        val body = JSONObject().put("query", query).put("variables", JSONObject(variables)).toString()
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
