package ru.sportzal.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object SportzalColors {
    val Canvas = Color(0xFFF2F4F5)
    val Surface = Color.White
    val SurfaceMuted = Color(0xFFE8ECEF)
    val Ink = Color(0xFF15191C)
    val InkSecondary = Color(0xFF59656D)
    val Line = Color(0xFFD7DDE1)
    val Primary = Color(0xFF2457D6)
    val PrimaryPressed = Color(0xFF1B43A7)
    val PrimaryContainer = Color(0xFFEAF0FF)
    val ErrorContainer = Color(0xFFFCEAE8)
    val Timer = Color(0xFFA95300)
    val Success = Color(0xFF287A4B)
    val Warning = Color(0xFFA95300)
    val Danger = Color(0xFFB3261E)
    val Focus = Color(0xFF2457D6)
}

val NumericTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontFeatureSettings = "tnum",
    fontSize = 24.sp,
    lineHeight = 28.sp,
    fontWeight = FontWeight.Medium,
)

val MinimumTouchTarget = 48.dp

private val colors = lightColorScheme(
    primary = SportzalColors.Primary,
    onPrimary = Color.White,
    primaryContainer = SportzalColors.PrimaryContainer,
    onPrimaryContainer = SportzalColors.PrimaryPressed,
    secondary = SportzalColors.Primary,
    onSecondary = Color.White,
    secondaryContainer = SportzalColors.PrimaryContainer,
    onSecondaryContainer = SportzalColors.PrimaryPressed,
    tertiary = SportzalColors.Timer,
    background = SportzalColors.Canvas,
    onBackground = SportzalColors.Ink,
    surface = SportzalColors.Surface,
    onSurface = SportzalColors.Ink,
    surfaceVariant = SportzalColors.SurfaceMuted,
    onSurfaceVariant = SportzalColors.InkSecondary,
    outline = SportzalColors.InkSecondary,
    outlineVariant = SportzalColors.Line,
    surfaceContainerLowest = SportzalColors.Surface,
    surfaceContainerLow = SportzalColors.Surface,
    surfaceContainer = SportzalColors.Surface,
    surfaceContainerHigh = SportzalColors.Surface,
    surfaceContainerHighest = SportzalColors.Surface,
    error = SportzalColors.Danger,
    errorContainer = SportzalColors.ErrorContainer,
    onErrorContainer = SportzalColors.Danger,
)

private val typography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)

private val shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp))

@Composable
fun SportzalTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = shapes,
    ) { Surface(Modifier.fillMaxSize(), color = colors.background, content = content) }
}
