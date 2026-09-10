# Collector MVP 1.0.0

## 목표

1차 Collector MVP는 암호화폐 선물 타점 분석을 위한 시장 데이터 수집 기반을 만드는 단계입니다.

이 단계에서는 분석, 지표 계산, 진입 판단, 주문 실행을 하지 않습니다. 목표는 거래소별 WebSocket 메시지를 읽고, 앱 내부에서 사용할 수 있는 공통 시장 이벤트로 정규화하는 것입니다.

```text
Exchange WebSocket
  → Collector
  → Normalize
  → MarketCandleEvent
```

완료 기준은 Binance USD-M Futures와 Bybit Linear의 BTCUSDT 15분봉 메시지가 동일한 `MarketCandleEvent` 계약으로 정규화되는 것입니다.


## 범위

| 포함 | 제외 |
| --- | --- |
| Binance USD-M Futures Kline WebSocket | RSI, EMA, MACD 계산 |
| Bybit Linear Kline WebSocket | MarketSnapshot 생성 |
| 거래소별 Kline DTO 파싱 | Entry Agent |
| `MarketCandleEvent` 정규화 | trade, ticker, OI, funding |
| 정규화 실패 이벤트 모델링 | HTTP 조회 API |
| 로컬 검증용 출력 경로 | 주문 실행 |


## 수집 흐름

Binance와 Bybit의 원본 메시지 구조는 서로 다르지만, collector 밖으로 나가는 candle 이벤트는 동일한 의미 구조를 유지합니다.

```text
Binance USD-M Futures Kline JSON
        ↓
BinanceMessageMapper
        ↓
MarketCandleEvent
```

```text
Bybit Linear Kline JSON
        ↓
BybitMessageMapper
        ↓
MarketCandleEvent
```


## 이벤트 계약

`MarketCandleEvent`는 거래소 API 모양이 아니라 앱이 필요한 의미 기준으로 유지합니다.

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

`closed=false` 이벤트는 실시간 UI 갱신과 진행 중인 candle 상태 표시에 사용할 수 있습니다. `closed=true` 이벤트는 이후 지표 계산, candle 확정 처리, 저장소 upsert 기준으로 사용할 수 있습니다.


## 이벤트 식별 기준

정규화된 candle 이벤트는 같은 시장 데이터를 같은 기준으로 식별할 수 있어야 합니다.

기본 식별 key:

```text
venue:symbol:interval
```

예시:

```text
BINANCE_USDM_FUTURES:BTCUSDT:15m
BYBIT_LINEAR:BTCUSDT:15m
```

이 key는 이후 로컬 이벤트 버스, 저장소 upsert, 실시간 UI 갱신, AI context 생성에서 동일하게 사용할 수 있습니다.


## 검증 경로

현재 구현에서는 정규화된 이벤트를 로컬에서 확인하기 위한 출력 경로로 Kafka를 사용합니다. Kafka는 Collector MVP의 검증 수단이며, 로컬 앱 방향에서는 내부 이벤트 버스 또는 로컬 저장소가 같은 역할을 맡을 수 있습니다.

현재 검증 대상:

```text
Provider WebSocket
  → Message Mapper
  → MarketCandleEvent
  → Local verification output
```


## 현재 상태

- [x] Alpaca collector 제거
- [x] `core-domain` candle 계약 정리
- [x] Binance USD-M Futures Kline 적용
- [x] Bybit Linear Kline 적용
- [x] Binance mapper 테스트
- [x] Bybit mapper 테스트
- [ ] Binance 실시간 collector 통합 검증
- [ ] Bybit 실시간 collector 통합 검증


## 다음 단계

Collector 계약이 안정화되면 다음 순서로 확장합니다.

1. `MarketTradeEvent`, `MarketTickerEvent` 추가
2. `MarketOpenInterestEvent`, `MarketFundingRateEvent` 추가
3. 로컬 이벤트 버스 추가
4. memory-only rolling buffer 추가
5. SQLite 또는 DuckDB 저장소 추가
6. 1s/5s 단위 feature aggregation 추가
7. AI analysis context builder 추가
8. TradingAgents 또는 LLM adapter 연결
