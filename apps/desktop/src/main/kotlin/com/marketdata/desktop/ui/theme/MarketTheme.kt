package com.marketdata.desktop.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class DeskPalette(
    val background: Color = Color(0xFF080909),
    val panel: Color = Color(0xFF121414),
    val elevated: Color = Color(0xFF1C1F1E),
    val hover: Color = Color(0xFF252A27),
    val line: Color = Color(0xFF2C302E),
    val text: Color = Color(0xFFF0F3F1),
    val muted: Color = Color(0xFF9BA49E),
    val green: Color = Color(0xFF8AE6AD),
    val blue: Color = Color(0xFF9BBDF6),
    val coral: Color = Color(0xFFF2AA98),
    val gold: Color = Color(0xFFE7CF8D)
)

private val darkPalette = DeskPalette()
private val lightPalette = DeskPalette(
    background = Color(0xFFF2F4F3), panel = Color.White, elevated = Color(0xFFF5F7F6),
    hover = Color(0xFFE7ECE9), line = Color(0xFFD5DDD8), text = Color(0xFF19231E),
    muted = Color(0xFF58675F), green = Color(0xFF187342), blue = Color(0xFF305EAD),
    coral = Color(0xFFAD4533), gold = Color(0xFF806319)
)
internal fun deskPalette(dark: Boolean): DeskPalette = if (dark) darkPalette else lightPalette
private val LocalDeskPalette = staticCompositionLocalOf { darkPalette }
internal val DeskColors: DeskPalette
    @Composable get() = LocalDeskPalette.current

@Composable
internal fun MarketTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val base = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp)
    val transition = remember { ThemeTransition(dark) }
    // A chart-free screen has no offscreen frame to synchronize, so do not delay its palette.
    SideEffect { if (transition.renderers.isEmpty()) transition.applyImmediately(dark) }
    LaunchedEffect(dark, transition.renderers.toList()) { transition.apply(dark) }
    val appliedDark = transition.dark
    val palette = deskPalette(appliedDark)
    CompositionLocalProvider(LocalDeskPalette provides palette, LocalThemeTransition provides transition) {
        MaterialTheme(
            colors = (if (appliedDark) darkColors() else lightColors()).copy(
                primary = palette.green,
                primaryVariant = palette.green,
                secondary = palette.blue,
                background = palette.background,
                surface = palette.panel,
                onPrimary = if (appliedDark) palette.background else Color.White,
                onSecondary = if (appliedDark) palette.background else Color.White,
                onBackground = palette.text,
                onSurface = palette.text,
                error = palette.coral,
                onError = if (appliedDark) palette.background else Color.White
            ),
            typography = Typography(
                defaultFontFamily = FontFamily.SansSerif,
                h4 = base.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold),
                h5 = base.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                h6 = base.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
                subtitle1 = base.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                subtitle2 = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                body1 = base.copy(fontSize = 14.sp, lineHeight = 22.sp),
                body2 = base.copy(fontSize = 13.sp, lineHeight = 20.sp),
                button = base.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                caption = base.copy(fontSize = 11.sp, lineHeight = 16.sp),
                overline = base.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium)
            ),
            shapes = Shapes(RoundedCornerShape(6.dp), RoundedCornerShape(8.dp), RoundedCornerShape(8.dp)),
            content = content
        )
    }
}
