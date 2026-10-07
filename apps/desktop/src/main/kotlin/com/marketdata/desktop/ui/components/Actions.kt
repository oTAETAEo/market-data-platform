@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.marketdata.desktop.ui.components

import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marketdata.desktop.ui.theme.DeskColors

@Composable
internal fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = if (selected) DeskColors.text else DeskColors.elevated) {
        Text(label, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 8.dp),
            color = if (selected) DeskColors.background else DeskColors.muted, style = MaterialTheme.typography.subtitle2)
    }
}

@Composable
internal fun ToolButton(icon: ImageVector, label: String, tint: Color = DeskColors.muted, size: Dp = 32.dp, onClick: () -> Unit) {
    TooltipArea(tooltip = {
        Surface(color = DeskColors.hover, shape = RoundedCornerShape(4.dp), elevation = 4.dp) {
            Text(label, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.caption)
        }
    }) {
        IconButton(onClick, modifier = Modifier.size(size)) { Icon(icon, label, tint = tint, modifier = Modifier.size(19.dp)) }
    }
}
