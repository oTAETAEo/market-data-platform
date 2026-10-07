package com.marketdata.desktop.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.marketdata.desktop.model.WorkspacePage
import com.marketdata.desktop.presentation.WorkspaceViewModel
import com.marketdata.desktop.ui.components.CoinMark
import com.marketdata.desktop.ui.components.SearchField
import com.marketdata.desktop.ui.components.StatusDot
import com.marketdata.desktop.ui.components.StatusTag
import com.marketdata.desktop.ui.components.ToolButton
import com.marketdata.desktop.ui.layout.WorkspaceLayout
import com.marketdata.desktop.ui.theme.DeskColors

@Composable
internal fun TopBar(viewModel: WorkspaceViewModel, layout: WorkspaceLayout, marketListVisible: Boolean,
                    onSettings: () -> Unit, onToggleMarkets: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!layout.hasSidebar) {
            ToolButton(if (marketListVisible) Icons.Default.Close else Icons.Default.Menu,
                if (marketListVisible) "분석 화면으로 돌아가기" else "관심 종목 열기", onClick = onToggleMarkets)
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            SearchField(viewModel.query, { viewModel.updateQuery(it) }, "종목 검색", Modifier.widthIn(max = 560.dp).fillMaxWidth())
        }
        Spacer(Modifier.width(16.dp))
        ToolButton(Icons.Default.Settings, "AI 설정", onClick = onSettings)
    }
}

@Composable
internal fun WorkspaceTabs(viewModel: WorkspaceViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(62.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        for ((page, label) in listOf(WorkspacePage.ANALYSIS to "워크스페이스", WorkspacePage.HISTORY to "분석 기록")) {
            val selected = viewModel.page == page
            Column(Modifier.width(IntrinsicSize.Max).fillMaxHeight().clickable { viewModel.showPage(page) }, verticalArrangement = Arrangement.Center) {
                Spacer(Modifier.weight(1f))
                Text(label, style = MaterialTheme.typography.subtitle2, color = if (selected) DeskColors.text else DeskColors.muted, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) DeskColors.green else Color.Transparent))
            }
        }
        Spacer(Modifier.weight(1f))
        Text("TradingAgents", style = MaterialTheme.typography.caption, color = DeskColors.muted)
    }
    Divider(color = DeskColors.line)
}

@Composable
internal fun AnalysisBar(viewModel: WorkspaceViewModel, layout: WorkspaceLayout, onAnalyze: () -> Unit) {
    val narrow = layout == WorkspaceLayout.NARROW
    Row(Modifier.fillMaxWidth().height(if (narrow) 72.dp else 84.dp).padding(horizontal = if (narrow) 16.dp else 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            CoinMark(viewModel.selection.asset, 36.dp)
            Column {
                Text("${viewModel.selection.asset.ticker} / USDT", style = MaterialTheme.typography.subtitle2)
                Text("${viewModel.selection.exchange} · ${viewModel.selection.timeframe}", color = DeskColors.muted, style = MaterialTheme.typography.caption)
            }
        }
        Row(if (narrow) Modifier else Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onAnalyze, enabled = viewModel.canAnalyze, shape = CircleShape,
                colors = ButtonDefaults.buttonColors(backgroundColor = DeskColors.green),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp), elevation = ButtonDefaults.elevation(0.dp, 0.dp)) {
                if (viewModel.busy) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp, color = DeskColors.background)
                else Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(23.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (viewModel.busy) "분석 중" else "AI 분석")
            }
        }
        if (!narrow) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            StatusDot(if (viewModel.aiConfigured) "AI 설정 완료" else "AI 설정 필요",
                if (viewModel.aiConfigured) DeskColors.green else DeskColors.gold)
        }
    }
}
