package com.marketdata.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marketdata.desktop.model.MarketAsset
import com.marketdata.desktop.ui.theme.DeskColors

@Composable
internal fun CoinMark(asset: MarketAsset, size: Dp) {
    val color = when (asset.ticker) {
        "BTC", "DOGE" -> DeskColors.gold
        "ETH", "LINK" -> DeskColors.blue
        "SOL" -> DeskColors.green
        "AVAX" -> DeskColors.coral
        else -> DeskColors.text
    }
    Box(Modifier.size(size).background(color.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
        Text(asset.mark, color = color, fontSize = if (size >= 60.dp) 34.sp else if (size >= 36.dp) 22.sp else 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun StatusTag(text: String, color: Color) {
    Text(text, color = color, style = MaterialTheme.typography.caption,
        modifier = Modifier.background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
}

@Composable
internal fun StatusDot(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(5.dp).background(color, CircleShape))
        Text(text, color = color, style = MaterialTheme.typography.caption)
    }
}
