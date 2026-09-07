package app.hikari

import androidx.compose.runtime.Composable
import app.hikari.core.model.MediaSummary

/**
 * Recommendation content was intentionally removed from Home.
 * Kept as a no-op compatibility shim until the Home screen call site is consolidated.
 */
@Composable
fun HomeRecommendationsSection(onMediaClick: (MediaSummary) -> Unit) = Unit
