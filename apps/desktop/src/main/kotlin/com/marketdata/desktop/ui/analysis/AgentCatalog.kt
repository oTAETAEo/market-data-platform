package com.marketdata.desktop.ui.analysis

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.marketdata.desktop.model.AgentGroup
import com.marketdata.desktop.ui.theme.DeskColors

internal data class AgentTile(
    val id: String, val name: String, val subtitle: String, val focus: String,
    val group: AgentGroup, val color: Color, val icon: ImageVector
)

internal val agentTiles = listOf(
    AgentTile("Technical Analyst", "기술적 분석", "Technical analyst", "추세 · 모멘텀 · 주요 가격대", AgentGroup.ANALYST, DeskColors.blue, Icons.AutoMirrored.Filled.List),
    AgentTile("Sentiment Analyst", "시장 심리", "Sentiment analyst", "투자 심리 · 소셜 센티먼트", AgentGroup.ANALYST, DeskColors.coral, Icons.Default.FavoriteBorder),
    AgentTile("News Analyst", "뉴스 분석", "News analyst", "주요 이슈 · 시장 촉매", AgentGroup.ANALYST, DeskColors.gold, Icons.Default.DateRange),
    AgentTile("Fundamentals Analyst", "펀더멘털", "Fundamentals analyst", "프로젝트 · 기초 체력", AgentGroup.ANALYST, DeskColors.green, Icons.Default.AccountBox),
    AgentTile("Bull Researcher", "상승 시나리오", "Bullish researcher", "상승 근거와 진입 확인 조건", AgentGroup.RESEARCH, DeskColors.green, Icons.Default.KeyboardArrowUp),
    AgentTile("Bear Researcher", "하락 시나리오", "Bearish researcher", "하락 근거와 시나리오 무효화", AgentGroup.RESEARCH, DeskColors.coral, Icons.Default.KeyboardArrowDown),
    AgentTile("Trader Agent", "트레이더", "Trader agent", "관찰 계획 · 매매 판단", AgentGroup.EXECUTION, DeskColors.blue, Icons.Default.PlayArrow),
    AgentTile("Risk Management", "리스크 관리", "Risk management", "변동성 · 유동성 · 손실 한도", AgentGroup.EXECUTION, DeskColors.gold, Icons.Default.Lock),
    AgentTile("Portfolio Manager", "최종 판단", "Portfolio manager", "판단 검토 · 최종 리포트", AgentGroup.EXECUTION, DeskColors.green, Icons.Default.CheckCircle)
)
