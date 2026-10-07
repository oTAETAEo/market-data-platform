package com.marketdata.desktop.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.marketdata.desktop.presentation.WorkspaceViewModel
import com.marketdata.desktop.ui.components.CoinMark
import com.marketdata.desktop.ui.components.StatusTag
import com.marketdata.desktop.ui.theme.DeskColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("MM.dd  HH:mm").withZone(ZoneId.systemDefault())

@Composable
internal fun HistoryContent(viewModel: WorkspaceViewModel) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("분석 기록", style = MaterialTheme.typography.h5)
        Spacer(Modifier.height(8.dp))
        Text("현재 세션 · ${viewModel.history.size}개의 샘플 리포트", color = DeskColors.muted, style = MaterialTheme.typography.body2)
        Spacer(Modifier.height(24.dp))
        if (viewModel.history.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.DateRange, null, tint = DeskColors.muted, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(18.dp))
                Text("아직 분석 기록이 없습니다", style = MaterialTheme.typography.subtitle1)
                Spacer(Modifier.height(8.dp))
                Text("생성된 리포트 0개", color = DeskColors.muted, style = MaterialTheme.typography.body2)
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(viewModel.history) { run ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(DeskColors.elevated).clickable { viewModel.openRun(run) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoinMark(run.selection.asset, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(run.selection.asset.symbol, style = MaterialTheme.typography.subtitle1)
                        Text("${run.selection.exchange} · ${run.selection.timeframe} · ${dateFormat.format(run.result.generatedAt())}", color = DeskColors.muted, style = MaterialTheme.typography.caption)
                    }
                    StatusTag("샘플", DeskColors.gold)
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "리포트 열기", tint = DeskColors.muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
