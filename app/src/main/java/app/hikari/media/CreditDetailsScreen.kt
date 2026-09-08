package app.hikari.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hikari.core.auth.SecureTokenStore
import app.hikari.core.model.MediaType
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

internal enum class CreditType { CHARACTER, PERSON }
internal data class CreditTarget(val id: Int, val type: CreditType)
internal data class CreditMedia(val id: Int, val type: MediaType, val title: String, val coverUrl: String?)
internal data class CreditDetail(val name: String, val imageUrl: String?, val description: String?, val subtitle: String?, val media: List<CreditMedia>)

@Singleton
internal class AniListCreditService @Inject constructor(private val client: OkHttpClient, private val tokenStore: SecureTokenStore) {
    suspend fun character(id: Int): CreditDetail = withContext(Dispatchers.IO) {
        val query = """
            query(${'$'}id:Int!){ Character(id:${'$'}id){ name{full} image{large} description media(perPage:18){nodes{id type title{userPreferred romaji english native} coverImage{large}}} } }
        """.trimIndent()
        val character = execute(query, mapOf("id" to id)).getJSONObject("data").getJSONObject("Character")
        CreditDetail(character.optJSONObject("name")?.optString("full").orEmpty().ifBlank { "Unknown character" }, character.optJSONObject("image")?.optString("large")?.takeIf { it.isNotBlank() }, cleanDescription(character.optString("description")), "Character", mediaList(character.optJSONObject("media")?.optJSONArray("nodes")))
    }
    suspend fun person(id: Int): CreditDetail = withContext(Dispatchers.IO) {
        val query = """
            query(${'$'}id:Int!){ Staff(id:${'$'}id){ name{full} image{large} description primaryOccupations staffMedia(perPage:18){nodes{id type title{userPreferred romaji english native} coverImage{large}}} characterMedia(perPage:18){nodes{id type title{userPreferred romaji english native} coverImage{large}}} } }
        """.trimIndent()
        val person = execute(query, mapOf("id" to id)).getJSONObject("data").getJSONObject("Staff")
        val occupations = person.optJSONArray("primaryOccupations")?.toStringList().orEmpty()
        val staffMedia = mediaList(person.optJSONObject("staffMedia")?.optJSONArray("nodes"))
        val characterMedia = mediaList(person.optJSONObject("characterMedia")?.optJSONArray("nodes"))
        CreditDetail(person.optJSONObject("name")?.optString("full").orEmpty().ifBlank { "Unknown person" }, person.optJSONObject("image")?.optString("large")?.takeIf { it.isNotBlank() }, cleanDescription(person.optString("description")), occupations.joinToString(" • ").takeIf { it.isNotBlank() }, (staffMedia + characterMedia).distinctBy { it.id to it.type })
    }
    private fun mediaList(nodes: JSONArray?): List<CreditMedia> = nodes?.let { List(it.length()) { i -> media(it.getJSONObject(i)) }.filterNotNull() }.orEmpty()
    private fun media(value: JSONObject?): CreditMedia? { if (value == null) return null; val type = runCatching { MediaType.valueOf(value.optString("type")) }.getOrNull() ?: return null; val title = value.optJSONObject("title")?.let { title -> listOf("userPreferred", "english", "romaji", "native").asSequence().map { title.optString(it) }.firstOrNull { it.isNotBlank() && it != "null" } } ?: "Untitled"; return CreditMedia(value.optInt("id"), type, title, value.optJSONObject("coverImage")?.optString("large")?.takeIf { it.isNotBlank() }) }
    private suspend fun execute(query: String, variables: Map<String, Any?>): JSONObject { val body = JSONObject().put("query", query).put("variables", JSONObject(variables)).toString().toRequestBody("application/json".toMediaType()); val request = Request.Builder().url("https://graphql.anilist.co").post(body).apply { tokenStore.accessToken()?.let { header("Authorization", "Bearer $it") }; header("Accept", "application/json") }.build(); client.newCall(request).execute().use { response -> val payload = response.body?.string().orEmpty(); check(response.isSuccessful) { "AniList request failed (${response.code})" }; return JSONObject(payload).also { check(!it.has("errors")) { it.getJSONArray("errors").toString() } } } }
}

private fun JSONArray.toStringList(): List<String> = List(length()) { getString(it) }.filter { it.isNotBlank() }
private fun cleanDescription(value: String?): String? = value?.replace(Regex("<[^>]*>"), "")?.trim()?.takeIf { it.isNotBlank() && it != "null" }
internal sealed interface CreditUiState { data object Loading : CreditUiState; data class Ready(val detail: CreditDetail) : CreditUiState; data class Error(val message: String) : CreditUiState }

@HiltViewModel
internal class CreditDetailsViewModel @Inject constructor(private val service: AniListCreditService) : ViewModel() {
    private val _state = MutableStateFlow<CreditUiState>(CreditUiState.Loading); val state: StateFlow<CreditUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var generation = 0L

    fun load(target: CreditTarget) {
        val requestGeneration = ++generation
        loadJob?.cancel()
        _state.value = CreditUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                val detail = if (target.type == CreditType.CHARACTER) service.character(target.id) else service.person(target.id)
                if (requestGeneration == generation) _state.value = CreditUiState.Ready(detail)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (requestGeneration == generation) {
                    _state.value = CreditUiState.Error("Couldn't load this profile. Try again.")
                }
            }
        }
    }
}

@Composable
internal fun CreditDetailsScreen(target: CreditTarget, onBack: () -> Unit, onMediaClick: (Int, MediaType) -> Unit, vm: CreditDetailsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState(); LaunchedEffect(target) { vm.load(target) }
    when (val current = state) { CreditUiState.Loading -> CreditLoading(onBack); is CreditUiState.Error -> CreditError(current.message, onBack) { vm.load(target) }; is CreditUiState.Ready -> CreditContent(current.detail, target.type, onBack, onMediaClick) }
}
@Composable private fun CreditLoading(onBack: () -> Unit) { Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { IconButton(onClick = onBack, modifier = Modifier.padding(12.dp)) { Icon(Icons.Outlined.ArrowBack, "Back") }; Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } } }
@Composable private fun CreditError(message: String, onBack: () -> Unit, onRetry: () -> Unit) { Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { IconButton(onClick = onBack, modifier = Modifier.padding(12.dp)) { Icon(Icons.Outlined.ArrowBack, "Back") }; Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(16.dp)); Button(onClick = onRetry) { Text("Retry") } } } }
@Composable private fun CreditContent(detail: CreditDetail, type: CreditType, onBack: () -> Unit, onMediaClick: (Int, MediaType) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(bottom = 36.dp)) {
        item { Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") }; Text(if (type == CreditType.CHARACTER) "Character" else "Staff", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) } }
        item { Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(if (type == CreditType.CHARACTER) 150.dp else 128.dp).clip(if (type == CreditType.CHARACTER) RoundedCornerShape(24.dp) else CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) { detail.imageUrl?.let { AsyncImage(model = it, contentDescription = detail.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }; Spacer(Modifier.height(14.dp)); Text(detail.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center); detail.subtitle?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp)) } } }
        detail.description?.let { description -> item { Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Text("About", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge) } } }
        if (detail.media.isNotEmpty()) { item { Text(if (type == CreditType.CHARACTER) "Appears in" else "Credits", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }; items(detail.media, key = { "${it.type}-${it.id}" }) { media -> Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onMediaClick(media.id, media.type) }.padding(9.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(58.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surface)) { media.coverUrl?.let { AsyncImage(model = it, contentDescription = media.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }; Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(media.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(if (media.type == MediaType.ANIME) "Anime" else "Manga", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) } } } }
    }
}
