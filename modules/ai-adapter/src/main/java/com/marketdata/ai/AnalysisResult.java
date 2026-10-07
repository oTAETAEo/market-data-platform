package com.marketdata.ai;

import java.time.Instant;
import java.util.List;

public record AnalysisResult(
        String symbol,
        String decision,
        String risk,
        Instant generatedAt,
        List<AgentReport> reports
) {
}
