@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.marketdata.desktop.ui.analysis

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marketdata.desktop.model.AgentGroup
import com.marketdata.desktop.presentation.WorkspaceViewModel
import com.marketdata.desktop.ui.components.ChoiceChip
import com.marketdata.desktop.ui.components.CoinMark
import com.marketdata.desktop.ui.components.DropdownControl
import com.marketdata.desktop.ui.components.StatusDot
import com.marketdata.desktop.ui.components.StatusTag
import com.marketdata.desktop.ui.components.ToolButton
import com.marketdata.desktop.ui.report.DetailPanel
import com.marketdata.desktop.ui.theme.DeskColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
internal fun AnalysisContent(viewModel: WorkspaceViewModel, compact: Boolean, inlineDetails: Boolean,
                            analysisScroll: ScrollState, detailScroll: ScrollState) {
    if (inlineDetails && viewModel.selectedAgent != null) {
        DetailPanel(viewModel, Modifier.fillMaxSize(), scrollState = detailScroll)
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(analysisScroll).padding(if (compact) 16.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CoinMark(viewModel.selection.asset, if (compact) 44.dp else 62.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                if (!compact) Text("마켓 워크스페이스", color = DeskColors.muted, style = MaterialTheme.typography.caption)
                Text(viewModel.selection.asset.name, style = if (compact) MaterialTheme.typography.h5 else MaterialTheme.typography.h4,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${viewModel.selection.asset.ticker} / USDT", color = DeskColors.muted, style = MaterialTheme.typography.body2)
            }
            ToolButton(Icons.Default.Star, "${viewModel.selection.asset.ticker} 즐겨찾기 변경", tint = if (viewModel.selection.asset.ticker in viewModel.favorites) DeskColors.green else DeskColors.muted) {
                viewModel.toggleFavorite(viewModel.selection.asset.ticker)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            DropdownControl(viewModel.selection.exchange, listOf("BINANCE", "BYBIT"), "거래소") { viewModel.select(exchange = it) }
            DropdownControl(viewModel.selection.timeframe, listOf("1m", "5m", "15m", "1h", "4h"), "타임프레임") { viewModel.select(timeframe = it) }
            Spacer(Modifier.weight(1f))
            StatusDot(connectionLabel(viewModel.marketStatus.connection()),
                if (viewModel.marketStatus.connection() == "LIVE") DeskColors.green else DeskColors.muted)
        }
        if (!compact) Row(Modifier.fillMaxWidth().border(BorderStroke(1.dp, DeskColors.line), RoundedCornerShape(6.dp)).padding(vertical = 16.dp)) {
            Metric("수집된 캔들", viewModel.marketStatus.candleCount().toString(), "개", Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(44.dp).background(DeskColors.line))
            Metric("분석 타임프레임", viewModel.selection.timeframe, "", Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(44.dp).background(DeskColors.line))
            Metric("최근 분석", viewModel.activeRun?.let { timeFormat.format(it.result.generatedAt()) } ?: "아직 없음", "", Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("에이전트 분석", style = MaterialTheme.typography.h6)
                Spacer(Modifier.width(10.dp))
                Text("09", color = DeskColors.muted, style = MaterialTheme.typography.subtitle2)
                Spacer(Modifier.weight(1f))
                if (viewModel.activeRun != null) StatusTag("AI 리포트", DeskColors.green)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AgentGroup.entries.forEach { group -> ChoiceChip(group.label, viewModel.group == group) { viewModel.filterByGroup(group) } }
            }
        }
        if (viewModel.error != null) {
            Row(Modifier.fillMaxWidth().background(DeskColors.coral.copy(alpha = 0.1f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = DeskColors.coral, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(viewModel.error!!, color = DeskColors.coral, style = MaterialTheme.typography.body2)
            }
        }
        for (group in listOf(AgentGroup.ANALYST, AgentGroup.RESEARCH, AgentGroup.EXECUTION)) {
            if (viewModel.group == AgentGroup.ALL || viewModel.group == group) {
                AgentSection(group, viewModel, compact)
            }
        }
        if (inlineDetails) {
            Divider(color = DeskColors.line)
            DetailPanel(viewModel, Modifier.fillMaxWidth(), scrollable = false)
        }
        Text(if (viewModel.marketStatus.analysisReady()) "시장 데이터 준비 완료" else "확정 캔들 200개 수집 중",
            style = MaterialTheme.typography.caption, color = DeskColors.muted)
    }
}

private fun connectionLabel(value: String) = when (value) {
    "LIVE" -> "실시간 연결"
    "CONNECTING" -> "연결 중"
    "RECONNECTING" -> "재연결 중"
    "ERROR" -> "연결 오류"
    else -> "연결 대기"
}

@Composable
private fun Metric(label: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = DeskColors.muted, style = MaterialTheme.typography.caption)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (unit.isNotEmpty()) Text(unit, color = DeskColors.muted, style = MaterialTheme.typography.caption)
        }
    }
}
