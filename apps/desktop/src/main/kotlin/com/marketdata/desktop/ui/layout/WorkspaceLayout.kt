package com.marketdata.desktop.ui.layout

import com.marketdata.desktop.model.AgentGroup

internal enum class WorkspaceLayout {
    WIDE, MEDIUM, NARROW;

    val hasSidebar get() = this != NARROW
    val hasDetailPanel get() = this == WIDE

    companion object {
        fun forWidth(width: Float) = when {
            width >= 1280 -> WIDE
            width >= 800 -> MEDIUM
            else -> NARROW
        }
    }
}

internal fun sidebarWidth(windowWidth: Float) = (windowWidth * 0.17f).coerceIn(192f, 240f)
internal fun detailPanelWidth(windowWidth: Float) = (windowWidth * 0.22f).coerceIn(272f, 336f)
internal fun adjustedDetailPanelWidth(current: Float?, default: Float, dragDelta: Float, maximum: Float) =
    ((current ?: default) - dragDelta).coerceIn(272f, maximum)

internal fun agentColumnCount(width: Float, group: AgentGroup): Int = when {
    group == AgentGroup.ANALYST && width >= 760 -> 4
    group == AgentGroup.EXECUTION && width >= 660 -> 3
    width >= 420 -> 2
    else -> 1
}
