package ru.sportzal.app.ui.theme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
object SportzalColors { val Canvas=Color(0xFFF2F4F5);val Surface=Color.White;val SurfaceMuted=Color(0xFFE8ECEF);val Ink=Color(0xFF15191C);val InkSecondary=Color(0xFF59656D);val Line=Color(0xFFD7DDE1);val Primary=Color(0xFF2457D6);val PrimaryPressed=Color(0xFF1B43A7);val Timer=Color(0xFFA95300);val Success=Color(0xFF287A4B);val Warning=Color(0xFFA95300);val Danger=Color(0xFFB3261E);val Focus=Color(0xFF2457D6) }
val NumericTextStyle=TextStyle(fontFamily=FontFamily.Default,fontFeatureSettings="tnum")
val MinimumTouchTarget=48.dp
private val colors=lightColorScheme(primary=SportzalColors.Primary,onPrimary=Color.White,background=SportzalColors.Canvas,onBackground=SportzalColors.Ink,surface=SportzalColors.Surface,onSurface=SportzalColors.Ink,surfaceVariant=SportzalColors.SurfaceMuted,onSurfaceVariant=SportzalColors.InkSecondary,outline=SportzalColors.Line,error=SportzalColors.Danger)
@Composable fun SportzalTheme(content:@Composable()->Unit){MaterialTheme(colorScheme=colors,typography=Typography(),shapes=Shapes(),content=content)}
