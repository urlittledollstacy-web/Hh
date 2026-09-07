from pathlib import Path

service = Path('app/src/main/java/app/hikari/data/remote/AniListGraphQlService.kt')
text = service.read_text()
marker = '    suspend fun viewerProfile(): AniListProfile {'
if 'suspend fun personalizedRecommendations()' not in text:
    addition = '''    suspend fun personalizedRecommendations(): List<MediaSummary> = runCatching {
        val viewer = execute("query{Viewer{id}}", emptyMap())
            .getJSONObject("data")
            .getJSONObject("Viewer")
        val userId = viewer.getInt("id")

        val anime = loadRecommendationCandidates(userId, MediaType.ANIME)
        val manga = loadRecommendationCandidates(userId, MediaType.MANGA)
        val owned = anime.first + manga.first
        val candidates = anime.second + manga.second
        val grouped = linkedMapOf<Pair<Int, MediaType>, RecommendationCandidate>()

        candidates.forEach { candidate ->
            val key = candidate.media.id to candidate.media.type
            if (key in owned) return@forEach
            val current = grouped[key]
            if (current == null) {
                grouped[key] = candidate.copy(occurrences = 1)
            } else {
                grouped[key] = current.copy(
                    occurrences = current.occurrences + 1,
                    ratingTotal = current.ratingTotal + candidate.ratingTotal,
                )
            }
        }

        grouped.values
            .sortedWith(
                compareByDescending<RecommendationCandidate> { it.occurrences }
                    .thenByDescending { it.ratingTotal }
                    .thenByDescending { it.media.averageScore ?: 0 },
            )
            .map { it.media }
            .distinctBy { it.id to it.type }
            .take(10)
    }.getOrDefault(emptyList())

    private suspend fun loadRecommendationCandidates(
        userId: Int,
        type: MediaType,
    ): Pair<Set<Pair<Int, MediaType>>, List<RecommendationCandidate>> {
        val query = "query(\\$userId:Int!){Page(page:1,perPage:8){mediaList(userId:\\$userId,type:${type.name},sort:UPDATED_TIME_DESC){media{id recommendations(page:1,perPage:4){nodes{rating mediaRecommendation{id type title{userPreferred romaji english native} coverImage{large} averageScore episodes chapters}}}}}}}"
        val page = execute(query, mapOf("userId" to userId)
            ).getJSONObject("data").getJSONObject("Page")
        val entries = page.optJSONArray("mediaList") ?: JSONArray()
        val owned = mutableSetOf<Pair<Int, MediaType>>()
        val candidates = mutableListOf<RecommendationCandidate>()

        for (i in 0 until entries.length()) {
            val media = entries.optJSONObject(i)?.optJSONObject("media") ?: continue
            val sourceId = media.optInt("id", 0)
            if (sourceId != 0) owned += sourceId to type
            val nodes = media.optJSONObject("recommendations")?.optJSONArray("nodes") ?: continue
            for (j in 0 until nodes.length()) {
                val node = nodes.optJSONObject(j) ?: continue
                val recommendation = node.optJSONObject("mediaRecommendation") ?: continue
                val id = recommendation.optInt("id", 0)
                if (id == 0) continue
                val recommendationType = runCatching {
                    MediaType.valueOf(recommendation.optString("type"))
                }.getOrNull() ?: continue
                val title = preferredTitle(recommendation.optJSONObject("title") ?: JSONObject())
                candidates += RecommendationCandidate(
                    media = MediaSummary(
                        id = id,
                        type = recommendationType,
                        title = title,
                        coverUrl = recommendation.optJSONObject("coverImage")?.optString("large"),
                        averageScore = recommendation.optInt("averageScore").takeIf { it != 0 },
                        episodesOrChapters = (if (recommendationType == MediaType.MANGA) recommendation.optInt("chapters") else recommendation.optInt("episodes")).takeIf { it != 0 },
                    ),
                    ratingTotal = node.optInt("rating", 0).coerceAtLeast(0),
                )
            }
        }
        return owned to candidates
    }

    private data class RecommendationCandidate(
        val media: MediaSummary,
        val ratingTotal: Int,
        val occurrences: Int = 0,
    )

'''
    if marker not in text:
        raise SystemExit('viewerProfile marker not found')
    service.write_text(text.replace(marker, addition + marker, 1))

main = Path('app/src/main/java/app/hikari/MainActivity.kt')
text = main.read_text()
old = '        item { if (state.loading) LoadingRow() else if (state.trending.isEmpty()) EmptyMessage("No trending anime found.") else MediaRow(state.trending, false, onMediaClick) }\n    }\n}'
new = '        item { if (state.loading) LoadingRow() else if (state.trending.isEmpty()) EmptyMessage("No trending anime found.") else MediaRow(state.trending, false, onMediaClick) }\n        item { HomeRecommendationsSection(onMediaClick) }\n    }\n}'
if 'HomeRecommendationsSection(onMediaClick)' not in text:
    if old not in text:
        raise SystemExit('HomeScreen insertion point not found')
    main.write_text(text.replace(old, new, 1))

# Preserve the fixes that made the current search build compile.
replacements = {
    'EmptyMessage("No tags or genres found for "${state.query.trim()}".")': 'EmptyMessage("No tags or genres found for \\\"${state.query.trim()}\\\".")',
    'EmptyMessage("No results found for "${state.query.trim()}".")': 'EmptyMessage("No results found for \\\"${state.query.trim()}\\\".")',
}
text = main.read_text()
for old, new in replacements.items():
    text = text.replace(old, new)
main.write_text(text)
