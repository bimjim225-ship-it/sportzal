package ru.sportzal.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The single application-level inset boundary; screens must not apply system-bar padding again. */
@Composable
fun AppSafeArea(
    modifier: Modifier = Modifier,
    safeDrawingInsets: WindowInsets = WindowInsets.safeDrawing,
    imeInsets: WindowInsets = WindowInsets.ime,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.fillMaxSize()
            .windowInsetsPadding(safeDrawingInsets)
            .windowInsetsPadding(imeInsets),
    ) { content() }
}
