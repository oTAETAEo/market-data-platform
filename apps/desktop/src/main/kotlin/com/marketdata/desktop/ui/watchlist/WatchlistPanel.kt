package com.marketdata.desktop.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.marketdata.desktop.model.MarketAsset
import com.marketdata.desktop.model.MarketCatalog
import com.marketdata.desktop.presentation.WorkspaceViewModel
import com.marketdata.desktop.ui.components.ChoiceChip
import com.marketdata.desktop.ui.components.CoinMark
import com.marketdata.desktop.ui.components.SearchField
import com.marketdata.desktop.ui.components.ToolButton
import com.marketdata.desktop.ui.theme.DeskColors

@Composable
internal fun Watchlist(viewModel: WorkspaceViewModel, modifier: Modifier, onSelect: (MarketAsset) -> Unit, onAdd: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(DeskColors.panel).padding(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.List, null, tint = DeskColors.muted, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
            Text("관심 종목", style = MaterialTheme.typography.subtitle1)
            Spacer(Modifier.weight(1f))
            ToolButton(Icons.Default.Add, "관심 종목 추가", onClick = onAdd)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("전체", !viewModel.favoritesOnly) { viewModel.filterFavorites(false) }
            ChoiceChip("즐겨찾기", viewModel.favoritesOnly) { viewModel.filterFavorites(true) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("내 마켓", color = DeskColors.muted, style = MaterialTheme.typography.caption)
            Text("${viewModel.visibleAssets.size}개 종목", color = DeskColors.muted, style = MaterialTheme.typography.caption)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(viewModel.visibleAssets, key = { it.ticker }) { asset ->
                val selected = viewModel.selection.asset.ticker == asset.ticker
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (selected) DeskColors.hover else Color.Transparent)
                        .clickable { onSelect(asset) }.padding(horizontal = 10.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoinMark(asset, 36.dp)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(asset.ticker, color = if (selected) DeskColors.green else DeskColors.text, style = MaterialTheme.typography.subtitle1)
                        Text(asset.name, color = DeskColors.muted, style = MaterialTheme.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (selected) Box(Modifier.size(5.dp).background(DeskColors.green, CircleShape))
                }
            }
            if (viewModel.visibleAssets.isEmpty()) item {
                Text("검색 결과가 없습니다", color = DeskColors.muted, style = MaterialTheme.typography.body2, modifier = Modifier.padding(10.dp))
            }
        }
        Divider(color = DeskColors.line)
        Spacer(Modifier.height(17.dp))
        Row(Modifier.padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, tint = DeskColors.green, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(9.dp))
            Text("로컬 워크스페이스", style = MaterialTheme.typography.subtitle2)
        }
        Text("현재 세션", color = DeskColors.muted, style = MaterialTheme.typography.caption, modifier = Modifier.padding(start = 32.dp, top = 6.dp, bottom = 8.dp))
    }
}

@Composable
internal fun AssetPicker(viewModel: WorkspaceViewModel, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.width(410.dp), color = DeskColors.panel, shape = RoundedCornerShape(8.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("관심 종목 추가", style = MaterialTheme.typography.h6)
                    Spacer(Modifier.weight(1f))
                    ToolButton(Icons.Default.Close, "닫기", onClick = onDismiss)
                }
                SearchField(query, { query = it }, "티커 또는 이름", Modifier.fillMaxWidth())
                val matches = MarketCatalog.assets.filter { MarketCatalog.matches(it, query) }
                LazyColumn(Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(matches) { asset ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { viewModel.addAsset(asset); onDismiss() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CoinMark(asset, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(asset.ticker, style = MaterialTheme.typography.subtitle1)
                                Text(asset.name, color = DeskColors.muted, style = MaterialTheme.typography.caption)
                            }
                            Icon(if (viewModel.watchlist.any { it.ticker == asset.ticker }) Icons.Default.Check else Icons.Default.Add, null, tint = DeskColors.green, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (matches.isEmpty()) item { Text("검색 결과가 없습니다", color = DeskColors.muted, modifier = Modifier.padding(10.dp)) }
                }
            }
        }
    }
}
