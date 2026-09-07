package app.hikari.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

/**
 * Compatibility overload for callers that do not need media-detail navigation.
 * The full LibraryScreen exposes onMediaClick for favorites; this keeps older
 * call sites source-compatible while the navigation wiring is completed.
 */
@Composable
fun LibraryScreen(
    signedIn: Boolean,
    padding: PaddingValues,
) {
    LibraryScreen(
        signedIn = signedIn,
        padding = padding,
        onMediaClick = {},
    )
}
