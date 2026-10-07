package com.marketdata.desktop.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object DeskColors {
    val background = Color(0xFF080909)
    val panel = Color(0xFF121414)
    val elevated = Color(0xFF1C1F1E)
    val hover = Color(0xFF252A27)
    val line = Color(0xFF2C302E)
    val text = Color(0xFFF0F3F1)
    val muted = Color(0xFF9BA49E)
    val green = Color(0xFF8AE6AD)
    val blue = Color(0xFF9BBDF6)
    val coral = Color(0xFFF2AA98)
    val gold = Color(0xFFE7CF8D)
}

@Composable
internal fun MarketTheme(content: @Composable () -> Unit) {
    val base = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp)
    MaterialTheme(
        colors = darkColors(
            primary = DeskColors.green,
            primaryVariant = DeskColors.green,
            secondary = DeskColors.blue,
            background = DeskColors.background,
            surface = DeskColors.panel,
            onPrimary = DeskColors.background,
            onSecondary = DeskColors.background,
            onBackground = DeskColors.text,
            onSurface = DeskColors.text,
            error = DeskColors.coral
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
