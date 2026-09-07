package app.hikari.data.remote

import app.hikari.core.auth.SecureTokenStore
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

/** Thin AniList GraphQL transport. Queries intentionally request only fields each UI needs. */
@Singleton
class AniListGraphQlService @Inject constructor(
    private val client: OkHttpClient,
    private val tokenStore: SecureTokenStore,
) {
    suspend fun trending(type: MediaType, page: Int = 1): List<MediaSummary> = mediaPage(
        "query(\$page:Int!, \$type:MediaType!){Page(page:\$page,perPage:20){media(type:\$type,sort:TRENDING_DESC){id type title{romaji english} coverImage{large} averageScore episodes chapters}}}",
        mapOf("page" to page, "type" to type.name), type,
    )

    suspend fun search(query: String, type: MediaType?, page: Int = 1): List<MediaSummary> = mediaPage(
        "query(\$page:Int!, \$search:String!, \$type:MediaType){Page(page:\$page,perPage:20){media(search:\$search,type:\$type,sort:SEARCH_MATCH){id type title{romaji english} coverImage{large} averageScore episodes chapters}}}",
        buildMap { put("page", page); put("search", query); put("type", type?.name) }, type,
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

private fun JSONArray.toMediaList(requestedType: MediaType?): List<MediaSummary> = List(length()) { index ->
    val item = getJSONObject(index)
    val title = item.getJSONObject("title").optString("english").ifBlank { item.getJSONObject("title").optString("romaji") }
    MediaSummary(
        id = item.getInt("id"), type = requestedType ?: MediaType.valueOf(item.getString("type")), title = title,
        coverUrl = item.optJSONObject("coverImage")?.optString("large"), averageScore = item.optInt("averageScore").takeIf { it != 0 },
        episodesOrChapters = (if (requestedType == MediaType.MANGA) item.optInt("chapters") else item.optInt("episodes")).takeIf { it != 0 },
    )
}
