package com.marketdata.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketdata.core.event.MarketCandleEvent;
import com.marketdata.engine.MarketContext;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class LocalTradingAgentsAdapter implements TradingAgentsAdapter {
    private static final Duration TIMEOUT = Duration.ofMinutes(15);

    private final Path python;
    private final Path tradingAgentsRepository;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile Path workerScript;

    public LocalTradingAgentsAdapter(Path python, Path tradingAgentsRepository) {
        this.python = python.toAbsolutePath();
        this.tradingAgentsRepository = tradingAgentsRepository.toAbsolutePath();
    }

    @Override
    public AnalysisResult analyze(MarketContext context, AiSettings settings, Consumer<String> onProgress) {
        if (!settings.configured()) throw new IllegalStateException("AI API 키를 설정해 주세요.");
        verifyPaths();
        String requestId = UUID.randomUUID().toString();
        Process process = startProcess(false);
        CompletableFuture<List<String>> output = CompletableFuture.supplyAsync(() -> readLines(process));
        try {
            mapper.writeValue(process.getOutputStream(), request(requestId, context, settings));
            process.getOutputStream().write('\n');
            process.getOutputStream().close();
            if (!process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("AI 분석 제한 시간을 초과했습니다.");
            }
            AnalysisResult result = null;
            for (String line : output.join()) {
                JsonNode message = mapper.readTree(line);
                String type = message.path("type").asText();
                if ("progress".equals(type)) onProgress.accept(message.path("stage").asText());
                if ("error".equals(type)) throw new IllegalStateException(message.path("message").asText());
                if ("result".equals(type)) result = parseResult(message);
            }
            if (process.exitValue() != 0 || result == null) {
                throw new IllegalStateException("TradingAgents 분석 결과를 받지 못했습니다.");
            }
            return result;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("AI 분석이 중단되었습니다.", interrupted);
        } catch (IllegalStateException failure) {
            throw failure;
        } catch (Exception failure) {
            process.destroyForcibly();
            throw new IllegalStateException("TradingAgents 분석을 완료하지 못했습니다.", failure);
        }
    }

    public Map<String, Object> checkRuntime() {
        verifyPaths();
        Process process = startProcess(true);
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Python 환경 확인 시간이 초과됐습니다.");
            }
            List<String> lines = readLines(process);
            if (process.exitValue() != 0 || lines.isEmpty()) throw new IllegalStateException("Python 환경을 확인할 수 없습니다.");
            return mapper.readValue(lines.getLast(), new TypeReference<>() {});
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Python 환경 확인이 중단되었습니다.", interrupted);
        } catch (Exception failure) {
            throw new IllegalStateException("Python 환경을 확인할 수 없습니다.", failure);
        }
    }

    private Process startProcess(boolean check) {
        try {
            List<String> command = new ArrayList<>(List.of(python.toString(), worker().toString(),
                    "--repo", tradingAgentsRepository.toString()));
            if (check) command.add("--check");
            return new ProcessBuilder(command).directory(tradingAgentsRepository.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (Exception failure) {
            throw new IllegalStateException("Python 프로세스를 시작할 수 없습니다.", failure);
        }
    }

    private Map<String, Object> request(String requestId, MarketContext context, AiSettings settings) {
        Map<String, Object> contextMap = new LinkedHashMap<>();
        contextMap.put("exchange", context.exchange());
        contextMap.put("symbol", context.symbol());
        contextMap.put("timeframe", context.timeframe());
        contextMap.put("candleCount", context.candleCount());
        contextMap.put("generatedAt", context.generatedAt().toString());
        contextMap.put("highlights", context.highlights());
        contextMap.put("candles", context.candles().stream().map(this::candle).toList());
        return Map.of(
                "requestId", requestId,
                "context", contextMap,
                "settings", Map.of("provider", settings.provider(), "quickModel", settings.quickModel(),
                        "deepModel", settings.deepModel(), "apiKey", settings.apiKey())
        );
    }

    private Map<String, Object> candle(MarketCandleEvent candle) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("provider", candle.provider()); value.put("venue", candle.venue());
        value.put("symbol", candle.symbol()); value.put("interval", candle.interval());
        value.put("open", candle.open()); value.put("high", candle.high()); value.put("low", candle.low());
        value.put("close", candle.close()); value.put("volume", candle.volume());
        value.put("openTime", candle.openTime().toString()); value.put("closeTime", candle.closeTime().toString());
        value.put("closed", candle.closed()); value.put("eventTime", candle.eventTime().toString());
        return value;
    }

    private AnalysisResult parseResult(JsonNode node) {
        List<AgentReport> reports = new ArrayList<>();
        for (JsonNode report : node.path("reports")) {
            reports.add(new AgentReport(report.path("agentName").asText(), report.path("status").asText(),
                    report.path("summary").asText()));
        }
        return new AnalysisResult(node.path("symbol").asText(), node.path("decision").asText(),
                node.path("risk").asText(), Instant.parse(node.path("generatedAt").asText()), List.copyOf(reports));
    }

    private List<String> readLines(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().filter(line -> !line.isBlank()).toList();
        } catch (Exception failure) {
            throw new IllegalStateException("Python 응답을 읽을 수 없습니다.", failure);
        }
    }

    private synchronized Path worker() throws Exception {
        if (workerScript != null) return workerScript;
        try (InputStream source = getClass().getResourceAsStream("/tradingagents_worker.py")) {
            if (source == null) throw new IllegalStateException("TradingAgents worker resource is missing");
            Path target = Files.createTempFile("tradingagents-worker-", ".py");
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            target.toFile().deleteOnExit();
            workerScript = target;
            return target;
        }
    }

    private void verifyPaths() {
        if (!Files.isExecutable(python)) throw new IllegalStateException("Python 실행 파일을 찾을 수 없습니다: " + python);
        if (!Files.isDirectory(tradingAgentsRepository)) throw new IllegalStateException("TradingAgents 경로를 찾을 수 없습니다: " + tradingAgentsRepository);
    }
}
