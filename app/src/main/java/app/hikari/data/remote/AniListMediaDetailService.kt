package app.hikari.data.remote

import app.hikari.core.auth.SecureTokenStore
import app.hikari.core.model.MediaCharacter
import app.hikari.core.model.MediaDetail
import app.hikari.core.model.MediaRelation
import app.hikari.core.model.MediaStaff
import app.hikari.core.model.MediaSummary
import app.hikari.core.model.MediaType
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

@Singleton
class AniListMediaDetailService @Inject constructor(
    private val client: OkHttpClient,
    private val tokenStore: SecureTokenStore,
) {
    suspend fun detail(id: Int): MediaDetail = withContext(Dispatchers.IO) {
        val query = """
            query(${'$'}id:Int!){
              Media(id:${'$'}id){
                id type title{userPreferred romaji english native}
                coverImage{large} bannerImage
                description status format episodes chapters duration
                startDate{year month day} endDate{year month day}
                season seasonYear genres
                tags{name rank}
                popularity favourites
                studios{nodes{name}}
                relations{edges{relationType node{id type title{userPreferred romaji english native} coverImage{large} averageScore episodes chapters}}}
                characters(page:1,perPage:12){edges{role node{id name{full} image{large}} voiceActors{id name{full} image{large}}}}
                staff(page:1,perPage:12){edges{role node{id name{full} image{large} primaryOccupations}}}
              }
            }
        """.trimIndent()
        val media = execute(query, mapOf("id" to id))
            .getJSONObject("data").getJSONObject("Media")
        val type = MediaType.valueOf(media.getString("type"))
        val summary = mediaSummary(media, type)
        val start = formatDate(media.optJSONObject("startDate"))
        val end = formatDate(media.optJSONObject("endDate"))
        val tags = media.optJSONArray("tags")?.let { array ->
            List(array.length()) { i -> array.getJSONObject(i).optString("name") }
                .filter { it.isNotBlank() }
                .take(20)
        } ?: emptyList()
        val studios = media.optJSONObject("studios")?.optJSONArray("nodes")?.let { array ->
            List(array.length()) { i -> array.getJSONObject(i).optString("name") }.filter { it.isNotBlank() }
        } ?: emptyList()
        val relations = media.optJSONObject("relations")?.optJSONArray("edges")?.let { array ->
            List(array.length()) { i ->
                val edge = array.getJSONObject(i)
                edge.optJSONObject("node")?.let { node ->
                    MediaRelation(
                        relationType = edge.optString("relationType").replace('_', ' '),
                        media = mediaSummary(node, MediaType.valueOf(node.getString("type"))),
                    )
                }
            }.filterNotNull()
        } ?: emptyList()
        val characters = media.optJSONObject("characters")?.optJSONArray("edges")?.let { array ->
            List(array.length()) { i ->
                val edge = array.getJSONObject(i)
                val node = edge.optJSONObject("node") ?: return@List null
                val actor = edge.optJSONArray("voiceActors")?.optJSONObject(0)
                MediaCharacter(
                    id = node.optInt("id"),
                    name = node.optJSONObject("name")?.optString("full").orEmpty().ifBlank { "Unknown character" },
                    imageUrl = node.optJSONObject("image")?.optString("large")?.takeIf { it.isNotBlank() },
                    role = edge.optionalText("role")?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Character",
                    voiceActorName = actor?.optJSONObject("name")?.optString("full")?.takeIf { it.isNotBlank() },
                    voiceActorImageUrl = actor?.optJSONObject("image")?.optString("large")?.takeIf { it.isNotBlank() },
                )
            }.filterNotNull()
        } ?: emptyList()
        val staff = media.optJSONObject("staff")?.optJSONArray("edges")?.let { array ->
            List(array.length()) { i ->
                val edge = array.getJSONObject(i)
                val node = edge.optJSONObject("node") ?: return@List null
                val occupations = node.optJSONArray("primaryOccupations")?.toStringList().orEmpty()
                val edgeRole = edge.optionalText("role")?.takeIf { it.isNotBlank() }
                MediaStaff(
                    id = node.optInt("id"),
                    name = node.optJSONObject("name")?.optString("full").orEmpty().ifBlank { "Unknown staff" },
                    imageUrl = node.optJSONObject("image")?.optString("large")?.takeIf { it.isNotBlank() },
                    roles = listOfNotNull(edgeRole) + occupations,
                )
            }.filterNotNull().map { it.copy(roles = it.roles.distinct().take(3)) }
        } ?: emptyList()
        MediaDetail(
            summary = summary,
            description = media.optionalText("description")?.replace(Regex("<[^>]*>"), "")?.trim(),
            status = media.optionalText("status")?.replace('_', ' '),
            format = media.optionalText("format")?.replace('_', ' '),
            duration = media.optInt("duration").takeIf { it != 0 },
            startDate = start,
            endDate = end,
            season = media.optionalText("season")?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() },
            seasonYear = media.optInt("seasonYear").takeIf { it != 0 },
            genres = media.optJSONArray("genres")?.toStringList() ?: emptyList(),
            tags = tags,
            popularity = media.optInt("popularity").takeIf { it != 0 },
            favourites = media.optInt("favourites").takeIf { it != 0 },
            bannerUrl = media.optionalText("bannerImage"),
            studios = studios,
            relations = relations,
            characters = characters,
            staff = staff,
        )
    }

    private fun mediaSummary(media: JSONObject, type: MediaType): MediaSummary = MediaSummary(
        id = media.getInt("id"),
        type = type,
        title = preferredTitle(media.getJSONObject("title")),
        coverUrl = media.optJSONObject("coverImage")?.optString("large")?.takeIf { it.isNotBlank() },
        averageScore = media.optInt("averageScore").takeIf { it != 0 },
        episodesOrChapters = (if (type == MediaType.ANIME) media.optInt("episodes") else media.optInt("chapters")).takeIf { it != 0 },
    )

    private suspend fun execute(query: String, variables: Map<String, Any?>): JSONObject {
        val body = JSONObject().put("query", query).put("variables", JSONObject(variables)).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("https://graphql.anilist.co").post(body).apply {
            tokenStore.accessToken()?.let { header("Authorization", "Bearer $it") }
            header("Accept", "application/json")
        }.build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            check(response.isSuccessful) { "AniList request failed (${response.code})" }
            return JSONObject(payload).also { result -> check(!result.has("errors")) { result.getJSONArray("errors").toString() } }
        }
    }
}

private fun JSONObject.optionalText(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

private fun JSONArray.toStringList(): List<String> = List(length()) { getString(it) }.filter { it.isNotBlank() }

private fun preferredTitle(title: JSONObject): String = listOf("userPreferred", "english", "romaji", "native")
    .asSequence().map { title.optString(it) }.firstOrNull { it.isNotBlank() && it != "null" } ?: "Untitled"

private fun formatDate(date: JSONObject?): String? {
    if (date == null) return null
    val year = date.optInt("year")
    val month = date.optInt("month")
    val day = date.optInt("day")
    if (year == 0) return null
    return when {
        month != 0 && day != 0 -> "%04d-%02d-%02d".format(year, month, day)
        month != 0 -> "%04d-%02d".format(year, month)
        else -> year.toString()
    }
}
