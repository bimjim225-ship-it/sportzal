package ru.sportzal.app.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

private data class Part(val name: String, val x: Float, val y: Float, val labelX: Float, val labelY: Float)

/** Anchors refer to the inspected 1024×1024 built-in assets, never a user's photograph. */
private val partsByAsset = mapOf(
    "row" to listOf(Part("Сиденье", .22f, .62f, .12f, .80f), Part("Упор", .43f, .44f, .28f, .28f), Part("Выбор веса", .74f, .60f, .91f, .59f)),
    "chest_press" to listOf(Part("Сиденье", .50f, .68f, .48f, .88f), Part("Рукоятки", .33f, .51f, .12f, .39f), Part("Выбор веса", .77f, .66f, .92f, .67f)),
    "lat_pulldown" to listOf(Part("Сиденье", .33f, .74f, .12f, .83f), Part("Упор для ног", .47f, .65f, .30f, .53f), Part("Выбор веса", .67f, .65f, .87f, .65f)),
    "leg_curl" to listOf(Part("Сиденье", .64f, .62f, .82f, .79f), Part("Валик", .40f, .74f, .25f, .87f), Part("Выбор веса", .37f, .48f, .12f, .40f)),
    "leg_press" to listOf(Part("Спинка", .29f, .64f, .12f, .53f), Part("Платформа", .63f, .39f, .49f, .20f), Part("Диски", .84f, .40f, .92f, .29f)),
)

@Composable
fun EquipmentGuide(asset: VisualAsset?, modifier: Modifier = Modifier, imageHeight: Dp = 224.dp) {
    val parts = asset?.let { partsByAsset[it.key] }.orEmpty()
    val letters = listOf("А", "Б", "В")
    val markerSize = 28.dp * LocalDensity.current.fontScale
    val lineColor = MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(if (asset == null) 64.dp else imageHeight)) {
            val side = minOf(maxWidth, maxHeight)
            val labels = parts.map { part -> DpOffset(
                (side * part.labelX - markerSize / 2).coerceIn(0.dp, (side - markerSize).coerceAtLeast(0.dp)),
                (side * part.labelY - markerSize / 2).coerceIn(0.dp, (side - markerSize).coerceAtLeast(0.dp))) }
            Box(Modifier.size(side).align(Alignment.Center)) {
                EquipmentIllustration(asset, Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                    parts.forEachIndexed { index, part ->
                        val start = Offset((labels[index].x + markerSize / 2).toPx(), (labels[index].y + markerSize / 2).toPx())
                        val end = Offset(size.width * part.x, size.height * part.y)
                        drawLine(lineColor, start, end, strokeWidth = 2.dp.toPx())
                        drawCircle(lineColor, radius = 3.dp.toPx(), center = end)
                    }
                }
                parts.forEachIndexed { index, _ ->
                    Surface(Modifier.offset(labels[index].x, labels[index].y).size(markerSize).clearAndSetSemantics {},
                        shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Box(contentAlignment = Alignment.Center) { Text(letters[index], style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
        }
        parts.forEachIndexed { index, part -> Text("${letters[index]} — ${part.name}", style = MaterialTheme.typography.bodyMedium) }
        if (parts.isNotEmpty()) Text("Типовая схема. Буквы обозначают детали, а не положения регулировок.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
