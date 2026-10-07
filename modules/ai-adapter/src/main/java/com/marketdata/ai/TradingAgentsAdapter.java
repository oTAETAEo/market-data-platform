package com.marketdata.ai;

import com.marketdata.engine.MarketContext;

import java.util.function.Consumer;

public interface TradingAgentsAdapter {

    AnalysisResult analyze(MarketContext context, AiSettings settings, Consumer<String> onProgress);
}
