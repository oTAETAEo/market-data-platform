package com.marketdata.ai;

import com.marketdata.engine.MarketContext;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

public class StubTradingAgentsAdapter implements TradingAgentsAdapter {

    @Override
    public AnalysisResult analyze(MarketContext context, AiSettings settings, Consumer<String> onProgress) {
        return new AnalysisResult(
                context.symbol(),
                "WAIT_FOR_CONFIRMATION",
                "UNKNOWN",
                Instant.now(),
                List.of(
                        new AgentReport("Technical Analyst", "COMPLETED", "최근 candle context가 연결되면 추세와 주요 가격 구간을 평가합니다."),
                        new AgentReport("Technical Analyst", "COMPLETED", """
                                ## 기술적 분석

                                **추세와 주요 가격 구간**을 평가하는 샘플 리포트입니다.

                                - 단기 추세 확인
                                - 거래량과 변동성 비교

                                | 항목 | 상태 |
                                | --- | --- |
                                | 데이터 | 준비 |
                                | 판단 | 대기 |
                                """),
                        new AgentReport("Sentiment Analyst", "SKIPPED", "뉴스와 소셜 데이터 연결 전까지는 비활성 분석 패널로 유지합니다."),
                        new AgentReport("News Analyst", "SKIPPED", "뉴스 데이터가 연결되지 않았습니다."),
                        new AgentReport("Fundamentals Analyst", "SKIPPED", "암호화폐 분석에서는 기업 펀더멘털을 사용하지 않습니다."),
                        new AgentReport("Bull Researcher", "COMPLETED", "상승 시나리오와 확인 조건을 정리합니다."),
                        new AgentReport("Bear Researcher", "COMPLETED", "하락 시나리오와 무효화 조건을 정리합니다."),
                        new AgentReport("Trader Agent", "COMPLETED", "분석 결과를 바탕으로 실행 가능한 관찰 계획을 생성합니다."),
                        new AgentReport("Risk Management", "COMPLETED", "진입 전 손절, 변동성, 과열 리스크를 점검합니다."),
                        new AgentReport("Portfolio Manager", "COMPLETED", "최종 판단은 주문이 아니라 리포트 형태로만 제공합니다.")
                )
        );
    }
}
