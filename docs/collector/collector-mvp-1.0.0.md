# Collector MVP 1.0.0

## 목표

1차 MVP는 코인 선물 타점 판단 서비스를 위한 데이터 수집 기반을 만드는 단계입니다. 이 단계에서는 분석, 지표 계산, 진입 판단을 하지 않고 다음 흐름만 완성합니다.

```text
Exchange WebSocket
  → Collector
  → Normalize
  → Kafka market.candle.v1
```

완료 기준은 Binance USD-M Futures와 Bybit Linear의 BTCUSDT 15분봉이 동일한 `MarketCandleEvent`로 Kafka에 발행되는 것입니다.

## 범위

| 포함 | 제외 |
| --- | --- |
| Binance USD-M Futures Kline WebSocket | RSI, EMA, MACD 계산 |
| Bybit Linear Kline WebSocket | MarketSnapshot 생성 |
| `MarketCandleEvent` 정규화 | Entry Agent |
| `market.candle.v1` 발행 | trade, ticker, OI, funding |
| DLQ 발행 | HTTP 조회 API |

## Kafka 계약

| Topic | Event | Key |
| --- | --- | --- |
| `market.candle.v1` | `MarketCandleEvent` | `venue:symbol:interval` |
| `market.dlq.v1` | `MarketDlqEvent` | `eventId` |

Key 예시:

```text
BINANCE_USDM_FUTURES:BTCUSDT:15m
BYBIT_LINEAR:BTCUSDT:15m
```

## 이벤트 계약

`MarketCandleEvent`는 거래소 API 모양이 아니라 시스템이 필요한 의미 기준으로 유지합니다.

```java
public record MarketCandleEvent(
        String eventId,
        String providerEventId,
        String provider,
        String venue,
        String assetClass,
        String symbol,
        String interval,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        Instant openTime,
        Instant closeTime,
        boolean closed,
        Instant eventTime,
        Instant receivedAt,
        int schemaVersion
) {
}
```

`closed=false` 이벤트는 실시간 UI 갱신에, `closed=true` 이벤트는 이후 지표 계산과 캔들 확정 처리에 사용합니다.

## 구현 순서

1. Alpaca collector 제거
2. `core-domain`을 캔들 중심 계약으로 정리
3. Binance Futures Kline 구독과 정규화 적용
4. Bybit Linear Kline 구독과 정규화 적용
5. 두 collector가 같은 Kafka topic/key 규칙으로 발행하는지 테스트

## 다음 단계

1차 MVP 이후에 `trade`, `ticker`, `openInterest`, `fundingRate` 순서로 데이터 타입을 추가합니다. `MarketSnapshot`과 Agent 계층은 collector 계약이 안정화된 뒤 별도 consumer 서비스로 붙입니다.
