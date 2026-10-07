package com.marketdata.desktop.ui.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.marketdata.desktop.model.MarketAsset
import com.marketdata.desktop.model.WorkspacePage
import com.marketdata.desktop.presentation.WorkspaceViewModel
import com.marketdata.desktop.ui.analysis.AnalysisContent
import com.marketdata.desktop.ui.history.HistoryContent
import com.marketdata.desktop.ui.layout.WorkspaceLayout
import com.marketdata.desktop.ui.layout.detailPanelWidth
import com.marketdata.desktop.ui.layout.sidebarWidth
import com.marketdata.desktop.ui.report.DetailPanel
import com.marketdata.desktop.ui.settings.AiSettingsDialog
import com.marketdata.desktop.ui.theme.DeskColors
import com.marketdata.desktop.ui.watchlist.AssetPicker
import com.marketdata.desktop.ui.watchlist.Watchlist
import kotlinx.coroutines.launch

@Composable
internal fun MarketWorkspace(viewModel: WorkspaceViewModel, titleBar: @Composable () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var addDialog by remember { mutableStateOf(false) }
    var showMarkets by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val analysisScroll = rememberScrollState()
    val detailScroll = rememberScrollState()
    LaunchedEffect(viewModel.selection) { analysisScroll.scrollTo(0) }
    LaunchedEffect(viewModel.selectedAgent, viewModel.activeRun) { detailScroll.scrollTo(0) }
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshMarketStatus()
            kotlinx.coroutines.delay(1_000)
        }
    }
    val analyze = { scope.launch { viewModel.analyze() }; Unit }

    Surface(Modifier.fillMaxSize(), color = DeskColors.background) {
        BoxWithConstraints {
            val layout = WorkspaceLayout.forWidth(maxWidth.value)
            val sidebarSize = sidebarWidth(maxWidth.value).dp
            val detailSize = detailPanelWidth(maxWidth.value).dp
            val compact = !layout.hasDetailPanel || maxHeight < 800.dp
            val marketListVisible = !layout.hasSidebar && (showMarkets || viewModel.query.isNotBlank())
            val selectAsset: (MarketAsset) -> Unit = { asset ->
                viewModel.select(asset = asset)
                if (!layout.hasSidebar) {
                    viewModel.updateQuery("")
                    showMarkets = false
                }
            }
            Column(Modifier.fillMaxSize()) {
                titleBar()
                TopBar(viewModel, layout, marketListVisible, onSettings = { showSettings = true }) {
                    if (marketListVisible) {
                        viewModel.updateQuery("")
                        showMarkets = false
                    } else showMarkets = true
                }
                Row(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    AnimatedVisibility(layout.hasSidebar,
                        enter = expandHorizontally(tween(180), expandFrom = Alignment.Start) + fadeIn(tween(150)),
                        exit = shrinkHorizontally(tween(180), shrinkTowards = Alignment.Start) + fadeOut(tween(100))) {
                        Row {
                            Watchlist(viewModel, Modifier.width(sidebarSize).fillMaxHeight(),
                                onSelect = selectAsset, onAdd = { addDialog = true })
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                    if (marketListVisible) {
                        Watchlist(viewModel, Modifier.weight(1f).fillMaxHeight(), onSelect = selectAsset, onAdd = { addDialog = true })
                    } else Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(DeskColors.panel)) {
                        WorkspaceTabs(viewModel)
                        if (viewModel.page == WorkspacePage.HISTORY) {
                            HistoryContent(viewModel)
                        } else {
                            AnalysisContent(viewModel, compact, inlineDetails = !layout.hasDetailPanel,
                                analysisScroll = analysisScroll, detailScroll = detailScroll)
                        }
                    }
                    AnimatedVisibility(layout.hasDetailPanel,
                        enter = expandHorizontally(tween(180), expandFrom = Alignment.End) + fadeIn(tween(150)),
                        exit = shrinkHorizontally(tween(180), shrinkTowards = Alignment.End) + fadeOut(tween(100))) {
                        Row {
                            Spacer(Modifier.width(8.dp))
                            DetailPanel(viewModel, Modifier.width(detailSize).fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp)).background(DeskColors.panel), scrollState = detailScroll)
                        }
                    }
                }
                AnalysisBar(viewModel, layout, analyze)
            }
        }
    }
    if (addDialog) AssetPicker(viewModel, onDismiss = { addDialog = false; showMarkets = false })
    if (showSettings) AiSettingsDialog(viewModel, onDismiss = { showSettings = false })
}
