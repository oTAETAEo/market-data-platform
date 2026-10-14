@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.marketdata.desktop.ui.watchlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.marketdata.desktop.model.MarketAsset
import com.marketdata.desktop.model.MarketCatalog
import com.marketdata.desktop.presentation.WorkspaceViewModel
import com.marketdata.desktop.ui.components.CoinMark
import com.marketdata.desktop.ui.components.SearchField
import com.marketdata.desktop.ui.components.rememberDropdownPositionProvider
import com.marketdata.desktop.ui.theme.DeskColors

@Composable
internal fun MarketSearch(viewModel: WorkspaceViewModel, onSelect: (MarketAsset) -> Unit) {
    val density = LocalDensity.current
    val positionProvider = rememberDropdownPositionProvider()
    var fieldWidth by remember { mutableStateOf(0.dp) }
    var focused by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    var activeIndex by remember(viewModel.query) { mutableStateOf(0) }
    val matches = viewModel.searchResults
    val custom = if (matches.isEmpty()) MarketCatalog.fromInput(viewModel.query) else null
    val results = if (custom != null) listOf(custom) else matches
    val open = focused && !dismissed && viewModel.query.isNotBlank()
    val choose: (MarketAsset) -> Unit = { asset ->
        dismissed = true
        onSelect(asset)
    }
    Box(Modifier.widthIn(max = 560.dp).fillMaxWidth().onSizeChanged {
        fieldWidth = with(density) { it.width.toDp() }
    }) {
        SearchField(viewModel.query, { viewModel.updateQuery(it); dismissed = false }, "종목 검색", Modifier.fillMaxWidth(),
            inputModifier = Modifier.onPointerEvent(PointerEventType.Press) { dismissed = false }.onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false
                else when {
                    event.key == Key.Escape && open -> { dismissed = true; true }
                    event.key == Key.DirectionDown && results.isNotEmpty() -> {
                        dismissed = false
                        activeIndex = if (open) (activeIndex + 1).coerceAtMost(results.lastIndex) else 0
                        true
                    }
                    event.key == Key.DirectionUp && open && results.isNotEmpty() -> {
                        activeIndex = (activeIndex - 1).coerceAtLeast(0); true
                    }
                    event.key == Key.Enter && open && results.isNotEmpty() -> {
                        choose(results[activeIndex.coerceIn(results.indices)]); true
                    }
                    else -> false
                }
            }, onFocusChange = { focused = it; if (it) dismissed = false })
        if (open && fieldWidth > 0.dp) Popup(positionProvider, onDismissRequest = { dismissed = true },
            // Keep typing focus in the search field while suggestions are visible.
            properties = PopupProperties(focusable = false)) {
            Surface(Modifier.width(fieldWidth).semantics { contentDescription = "종목 검색 결과" },
                color = DeskColors.panel, border = BorderStroke(1.dp, DeskColors.inputLine), shape = RoundedCornerShape(8.dp)) {
                val scroll = rememberScrollState()
                var menuHeight by remember { mutableStateOf(0) }
                LaunchedEffect(activeIndex, menuHeight) {
                    val rowHeight = with(density) { 62.dp.roundToPx() }
                    val viewport = menuHeight - with(density) { 12.dp.roundToPx() }
                    if (viewport > 0) {
                        val top = activeIndex * rowHeight
                        if (top < scroll.value) scroll.scrollTo(top)
                        else if (top + rowHeight > scroll.value + viewport) scroll.scrollTo(top + rowHeight - viewport)
                    }
                }
                Column(Modifier.heightIn(max = 300.dp).onSizeChanged { menuHeight = it.height }
                    .verticalScroll(scroll).padding(6.dp)) {
                    if (results.isEmpty()) Text("검색 결과가 없습니다", color = DeskColors.muted,
                        style = MaterialTheme.typography.body2, modifier = Modifier.padding(12.dp))
                    results.forEachIndexed { index, asset ->
                        val interaction = remember(asset.ticker) { MutableInteractionSource() }
                        val hovered by interaction.collectIsHoveredAsState()
                        Row(Modifier.fillMaxWidth().height(62.dp)
                            .background(if (hovered || activeIndex == index) DeskColors.hover else DeskColors.panel,
                                RoundedCornerShape(4.dp))
                            .hoverable(interaction)
                            .focusProperties { canFocus = false }
                            .clickable(interactionSource = interaction, indication = null) { choose(asset) }
                            .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CoinMark(asset, 28.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(asset.ticker, color = DeskColors.text, style = MaterialTheme.typography.subtitle2,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (custom != null) "직접 입력 · 상장 미확인" else asset.name,
                                    color = DeskColors.muted, style = MaterialTheme.typography.caption,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(8.dp))
                            if (custom != null) Icon(Icons.Default.Add, "직접 입력 종목 추가", tint = DeskColors.green, modifier = Modifier.size(16.dp))
                            else if (asset.ticker == viewModel.selection.asset.ticker) Icon(Icons.Default.Check,
                                "선택한 종목", tint = DeskColors.green, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
