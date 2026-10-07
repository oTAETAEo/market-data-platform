package com.marketdata.desktop.ui.analysis

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marketdata.desktop.model.AgentGroup
import com.marketdata.desktop.presentation.WorkspaceViewModel
import com.marketdata.desktop.ui.layout.agentColumnCount
import com.marketdata.desktop.ui.theme.DeskColors

@Composable
internal fun AgentSection(group: AgentGroup, viewModel: WorkspaceViewModel, compact: Boolean) {
    val tiles = agentTiles.filter { it.group == group }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(when (group) { AgentGroup.ANALYST -> "시장 읽기"; AgentGroup.RESEARCH -> "두 가지 시나리오"; else -> "판단과 리스크" }, style = MaterialTheme.typography.subtitle1)
            Spacer(Modifier.weight(1f))
            Text(when (group) { AgentGroup.ANALYST -> "ANALYST TEAM"; AgentGroup.RESEARCH -> "RESEARCH TEAM"; else -> "DECISION TEAM" }, style = MaterialTheme.typography.overline, color = DeskColors.muted)
        }
        BoxWithConstraints {
            val columns = agentColumnCount(maxWidth.value, group)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in tiles.chunked(columns)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (tile in row) AgentCard(tile, viewModel, Modifier.weight(1f), compact, horizontal = columns == 1)
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentCard(tile: AgentTile, viewModel: WorkspaceViewModel, modifier: Modifier, compact: Boolean, horizontal: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val selected = viewModel.selectedAgent == tile.id
    val background by animateColorAsState(if (hovered || selected) DeskColors.hover else DeskColors.elevated)
    val reportStatus = viewModel.activeRun?.result?.reports()?.find { it.agentName() == tile.id }?.status()
    val statusLabel = when (reportStatus) { "COMPLETED" -> "완료"; "SKIPPED" -> "제외"; else -> "분석 대기" }
    val cardModifier = modifier.height(if (horizontal) 104.dp else if (compact) 175.dp else 191.dp)
        .clip(RoundedCornerShape(8.dp)).background(background)
        .border(1.dp, if (selected) tile.color else Color.Transparent, RoundedCornerShape(8.dp))
        .hoverable(interaction).clickable { viewModel.showAgent(tile.id) }.padding(if (horizontal) 12.dp else 15.dp)
    if (horizontal) {
        Row(cardModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(tile.color.copy(alpha = 0.14f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(tile.icon, null, tint = tile.color, modifier = Modifier.size(25.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(tile.name, style = MaterialTheme.typography.subtitle1)
                Text(tile.subtitle, color = DeskColors.muted, style = MaterialTheme.typography.caption, maxLines = 1)
                Text(tile.focus, color = DeskColors.muted, style = MaterialTheme.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(statusLabel, color = if (reportStatus == "COMPLETED") DeskColors.green else DeskColors.muted,
                    style = MaterialTheme.typography.caption)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = tile.color, modifier = Modifier.size(16.dp))
        }
        return
    }
    Column(cardModifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(tile.color.copy(alpha = 0.14f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(tile.icon, null, tint = tile.color, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("0${agentTiles.indexOf(tile) + 1}", color = tile.color.copy(alpha = 0.7f), style = MaterialTheme.typography.caption)
        }
        Spacer(Modifier.height(15.dp))
        Text(tile.name, style = MaterialTheme.typography.subtitle1)
        Text(tile.subtitle, color = DeskColors.muted, style = MaterialTheme.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Text(tile.focus, color = DeskColors.muted, style = MaterialTheme.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(statusLabel, color = if (reportStatus == "COMPLETED") DeskColors.green else DeskColors.muted,
                style = MaterialTheme.typography.caption)
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = if (hovered || selected) tile.color else DeskColors.muted, modifier = Modifier.size(16.dp))
        }
    }
}
