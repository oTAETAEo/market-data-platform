# Market Data Platform

> **Binance·Alpaca 실시간 시장 데이터를 수집하고 Kafka 이벤트로 처리하는 모노레포 기반 MSA 플랫폼**

Binance Spot과 Alpaca IEX WebSocket 데이터를 수집해 공통 도메인 이벤트로 정규화하고, Kafka 토픽으로 발행합니다. 이후 저장·피드 서비스는 공급자별 원본 형식을 알 필요 없이 공통 이벤트를 소비합니다.

## Architecture

<img width="1276" height="896" alt="Market Data Platform Architecture" src="https://github.com/user-attachments/assets/6daa7060-8161-40a9-b795-7a88b5e9c48e" />

## Data Flow

```
Provider WebSocket
  → Provider DTO
  → Common Domain Event
  → Kafka Topic
  → Consumer Services
```

| 계층 | 역할 |
| --- | --- |
| Collector Services | 공급자별 WebSocket 연결, 인증, 구독, DTO 파싱 및 재연결 처리 |
| core-domain | 체결·호가·캔들·실패 이벤트의 공통 계약 제공 |
| Kafka Cluster | 이벤트 종류별 토픽을 통한 비동기 데이터 전달 |
| Consumer Services | Redis·MongoDB 저장 및 SSE/WebSocket 실시간 피드 제공 |

## Modules

```
market-data-platform/
├── core-domain/                 # 공통 이벤트 계약
├── binance-collector-service/   # Binance Spot collector
├── alpaca-collector-service/    # Alpaca IEX collector
├── docker-compose.yml           # 로컬 Kafka 실행
├── build.gradle
└── settings.gradle
```

| 모듈 | 책임 | 상태 |
| --- | --- | --- |
| `core-domain` | 공통 이벤트 모델과 스키마 계약 | 구현됨 |
| `binance-collector-service` | BTCUSDT 체결·최우선 호가·1분봉 수집 | 구현됨 |
| `alpaca-collector-service` | AAPL 체결 수집 및 quote·bar 확장 | 체결 구현, 확장 진행 예정 |
| `market-data-storage-service` | 최신 시세·시계열 데이터 저장 | 예정 |
| `market-data-feed-service` | 클라이언트 실시간 전송 | 예정 |

## Event & Topic Contract

| 데이터 | 공통 이벤트 | Kafka 토픽 | Kafka key |
| --- | --- | --- | --- |
| 체결 | `MarketTickEvent` | `market.trade.v1` | `provider |
| 최우선 호가 | `MarketBookTickerEvent` | `market.quote.v1` | `provider |
| 1분봉·정정 캔들 | `MarketCandleEvent` | `market.bar.v1` | `provider |
| 파싱·검증 실패 | `MarketDlqEvent` | `market.dlq.v1` | 원본 이벤트 key |

토픽은 종목별이 아니라 **이벤트 종류별**로 구분합니다. 예를 들어 `BTCUSDT`, `AAPL`, `ETHUSDT`의 체결 이벤트는 모두 `market.trade.v1`으로 발행하고, Kafka key와 이벤트의 `symbol`로 종목을 구분합니다.

## Provider Mapping

| 데이터 | Binance Spot | Alpaca IEX | 공통 이벤트 |
| --- | --- | --- | --- |
| 체결 | `@trade` | `trades` | `MarketTickEvent` |
| 최우선 호가 | `@bookTicker` | `quotes` | `MarketBookTickerEvent` |
| 1분봉 | `@kline_1m` | `bars` | `MarketCandleEvent` |
| 1분봉 정정 | 진행 중 캔들 갱신 | `updatedBars` | `MarketCandleEvent` |
| 오류 | 파싱·검증 예외 | 파싱·검증 예외 | `MarketDlqEvent` |

> Binance는 진행 중인 1분봉을 반복 갱신합니다. Alpaca `bars`는 확정 봉을, `updatedBars`는 늦은 체결에 따른 과거 봉 정정을 전달합니다.

## Quick Start

### 1. Kafka 실행

```bash
docker compose up -d
```

### 2. Alpaca 환경변수 설정

```bash
export ALPACA_API_KEY='YOUR_ALPACA_API_KEY'
export ALPACA_SECRET_KEY='YOUR_ALPACA_SECRET_KEY'
```

### 3. 빌드 및 collector 실행

```bash
./gradlew clean build

# Binance collector
./gradlew :binance-collector-service:bootRun

# Alpaca collector
./gradlew :alpaca-collector-service:bootRun
```

### 4. Kafka 이벤트 확인

```bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic market.trade.v1 \
  --from-beginning
```

## Configuration

Alpaca API key와 secret은 Git에 커밋하지 않고 환경변수로 주입합니다.

```yaml
alpaca:
  websocket:
    url: wss://stream.data.alpaca.markets/v2/iex
    key: ${ALPACA_API_KEY}
    secret: ${ALPACA_SECRET_KEY}
    symbols: AAPL
```

`.env`, `application-local.yml`, 인증서·키 파일, Docker 로컬 데이터는 `.gitignore`로 제외합니다.

## Roadmap

| 단계 | 작업 |
| --- | --- |
| 1 | Alpaca `quotes`, `bars`, `updatedBars` 수집 추가 |
| 2 | storage-service에서 Redis·MongoDB 저장 구현 |
| 3 | feed-service의 SSE/WebSocket 전송 구현 |
| 4 | 데이터 신선도·DLQ·consumer lag 관측성 추가 |
| 5 | 서비스별 Docker 이미지와 CI/CD 배포 구성 |