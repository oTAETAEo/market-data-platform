package com.marketdata.desktop.ui.report

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.marketdata.desktop.presentation.WorkspaceViewModel
import com.marketdata.desktop.ui.analysis.agentTiles
import com.marketdata.desktop.ui.components.CoinMark
import com.marketdata.desktop.ui.components.StatusTag
import com.marketdata.desktop.ui.components.ToolButton
import com.marketdata.desktop.ui.theme.DeskColors

@Composable
internal fun DetailPanel(viewModel: WorkspaceViewModel, modifier: Modifier, scrollable: Boolean = true,
                        scrollState: ScrollState = rememberScrollState()) {
    val tile = agentTiles.find { it.id == viewModel.selectedAgent }
    val result = viewModel.activeRun?.result
    val scrollModifier = if (scrollable) Modifier.verticalScroll(scrollState) else Modifier
    Column(modifier.then(scrollModifier).padding(22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (tile == null) "분석 브리핑" else "에이전트 리포트", style = MaterialTheme.typography.subtitle1)
            Spacer(Modifier.weight(1f))
            if (tile != null) ToolButton(Icons.Default.Close, "브리핑으로 돌아가기", size = 28.dp) { viewModel.showAgent(null) }
            else Icon(Icons.Default.Info, "분석 상태", tint = DeskColors.muted, modifier = Modifier.size(17.dp))
        }
        if (tile != null) {
            Box(Modifier.size(56.dp).background(tile.color.copy(alpha = 0.14f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(tile.icon, null, tint = tile.color, modifier = Modifier.size(30.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(tile.name, style = MaterialTheme.typography.h5)
                Text(tile.subtitle, color = tile.color, style = MaterialTheme.typography.body2)
            }
            Divider(color = DeskColors.line)
            val report = result?.reports()?.find { it.agentName() == tile.id }
            StatusTag(if (report == null) "분석 대기" else report.status(),
                if (report == null || report.status() == "SKIPPED") DeskColors.muted else DeskColors.green)
            Text(report?.summary() ?: "아직 분석 결과가 없습니다.", color = DeskColors.muted, style = MaterialTheme.typography.body1)
            MarkdownReport(report?.summary() ?: "아직 분석 결과가 없습니다.")
            Text(tile.focus, color = tile.color, style = MaterialTheme.typography.body2)
        } else {
            Column(Modifier.fillMaxWidth().background(DeskColors.green.copy(alpha = 0.065f)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoinMark(viewModel.selection.asset, 30.dp)
                    Spacer(Modifier.width(9.dp))
                    Text("${viewModel.selection.asset.ticker} / USDT", style = MaterialTheme.typography.subtitle2)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("최종 판단", color = DeskColors.green, style = MaterialTheme.typography.caption)
                    Text(if (result == null) "분석 대기" else decisionLabel(result.decision()), style = MaterialTheme.typography.h4)
                    Text(if (result == null) "아직 생성된 리포트가 없습니다." else "TradingAgents가 시장 스냅샷을\n분석한 결과입니다.", color = DeskColors.muted, style = MaterialTheme.typography.body2)
                }
                Divider(color = DeskColors.green.copy(alpha = 0.18f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("리스크", color = DeskColors.muted, style = MaterialTheme.typography.caption)
                    Text(if (result == null) "평가 전" else result.risk(), color = DeskColors.gold, style = MaterialTheme.typography.caption)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Text("분석 컨텍스트", style = MaterialTheme.typography.subtitle2)
                DetailRow("거래소", viewModel.selection.exchange)
                DetailRow("마켓", viewModel.selection.asset.symbol)
                DetailRow("타임프레임", viewModel.selection.timeframe)
                DetailRow("수집된 캔들", "${viewModel.marketStatus.candleCount()}개")
                DetailRow("데이터 연결", viewModel.marketStatus.connection(),
                    if (viewModel.marketStatus.connection() == "LIVE") DeskColors.green else DeskColors.gold)
            }
            Divider(color = DeskColors.line)
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("분석 파이프라인", style = MaterialTheme.typography.subtitle2)
                PipelineRow("01", "시장 분석", "4명의 애널리스트", DeskColors.blue)
                PipelineRow("02", "시나리오 검토", "상승 · 하락 리서치", DeskColors.coral)
                PipelineRow("03", "판단과 리스크", "트레이더 · 리스크 · 포트폴리오", DeskColors.green)
            }
        }
        Divider(color = DeskColors.line)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Lock, null, tint = DeskColors.muted, modifier = Modifier.size(14.dp))
            Text("이 기기의 세션", color = DeskColors.muted, style = MaterialTheme.typography.caption)
        }
    }
}

private fun decisionLabel(value: String) = when (value) {
    "BUY" -> "매수"
    "OVERWEIGHT" -> "비중 확대"
    "HOLD" -> "관망"
    "UNDERWEIGHT" -> "비중 축소"
    "SELL" -> "매도"
    else -> "판단 미완료"
}

@Composable
private fun PipelineRow(number: String, title: String, subtitle: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(30.dp).border(1.dp, DeskColors.line, CircleShape), contentAlignment = Alignment.Center) {
            Text(number, color = color, style = MaterialTheme.typography.caption)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.subtitle2)
            Text(subtitle, color = DeskColors.muted, style = MaterialTheme.typography.caption)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, color: Color = DeskColors.text) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = DeskColors.muted, style = MaterialTheme.typography.body2)
        Text(value, color = color, style = MaterialTheme.typography.body2)
    }
}
