# Market Data Platform

암호화폐 시장 데이터를 로컬에서 수집하고, AI 분석에 사용할 수 있는 형태로 준비합니다.

Market Data Platform은 Binance, Bybit 같은 거래소의 공개 암호화폐 선물 시장 데이터를 수집하고, 거래소별 메시지를 공통 시장 이벤트로 정규화하기 위한 오픈소스 Java 프로젝트입니다.

현재는 Binance USD-M Futures와 Bybit Linear의 candle 데이터 수집에 집중하고 있습니다. 장기적으로는 사용자가 관심 있는 심볼의 시장 데이터를 로컬에서 수집하고, 정규화된 스트림을 확인하며, 원할 때만 AI 분석을 요청할 수 있는 로컬 우선 데스크톱 앱을 목표로 합니다.


## AI에게 타점 분석을 요청하기 전에

어려운 부분은 LLM에 프롬프트를 보내는 것이 아니라, 모델이 판단할 수 있을 만큼 깨끗하고 최근성이 있으며 일관된 시장 맥락을 준비하는 것입니다.

거래소의 원본 스트림은 시끄럽고, 거래소마다 형식이 다르며, 그대로 AI provider에 보내기에는 데이터 양이 너무 클 수 있습니다. 이 프로젝트는 수집과 정규화 계층을 로컬에 두고, 이후 TradingAgents나 다른 LLM workflow에서 사용할 수 있는 작은 시장 요약을 준비하는 방향으로 설계합니다.


## 경계 한눈에 보기

```text
거래소 공개 스트림
        ↓
로컬 collector
        ↓
공통 시장 이벤트
        ↓
로컬 market engine
        ├─ 상태 표시
        ├─ 로컬 저장
        └─ 요청 시 AI 분석 context 생성
```

| 로컬에 남는 것 | 사용자 요청 시에만 외부로 나가는 것 |
| --- | --- |
| 거래소 메시지, 정규화 이벤트, 로컬 버퍼, 로컬 저장 데이터, API key | 선택한 AI provider로 전송되는 요약 시장 context |


## 현재 제공되는 것

| 영역 | 현재 상태 |
| --- | --- |
| Core domain | `MarketCandleEvent`, `MarketDlqEvent`, 공통 이벤트 계약 |
| Binance collector | Binance USD-M Futures Kline WebSocket 수집과 정규화 |
| Bybit collector | Bybit Linear Kline WebSocket 수집과 정규화 |
| Desktop app | 실시간 수집 상태, AI 설정, TradingAgents 리포트를 제공하는 Compose 앱 |
| Build system | Gradle 멀티 모듈 JVM 프로젝트 |
| Local infrastructure | 로컬 스트림 검증을 위한 Docker Compose 파일 |
| Documentation | Collector MVP 범위와 이벤트 계약 문서 |


## 의도적으로 집중하는 범위

이 프로젝트는 자동 매매 봇, 호스팅형 시그널 서비스, 거래소 개인 계정 관리 도구가 아닙니다.

기본 범위에는 주문 실행이 포함되지 않습니다. 현재 공개 시장 데이터 수집 범위에서는 거래소 API key도 필요하지 않습니다. 이 프로젝트는 무거운 분석, 저장소, 데스크톱 UI 계층을 붙이기 전에 로컬 수집과 정규화된 시장 이벤트를 먼저 안정화하는 데 집중합니다.


## 데이터 범위

현재 구현은 candle 데이터에서 시작합니다. 이후 단기 트레이딩 분석에 필요한 입력을 중심으로 시장 데이터 모델을 확장할 계획입니다.

| 데이터 | 역할 | 상태 |
| --- | --- | --- |
| Kline / Candle | 차트와 지표 계산을 위한 OHLCV 기준 데이터 | 사용 가능 |
| Trade | 체결 흐름과 매수/매도 압력 판단 | 예정 |
| Ticker / Mark Price / Index Price | 현재가와 선물 기준 가격 확인 | 예정 |
| Open Interest | 포지션 유입과 이탈 흐름 판단 | 예정 |
| Funding Rate | 롱/숏 과열과 파생시장 쏠림 판단 | 예정 |
| Order Book Top N | spread, 유동성, 호가 불균형 판단 | 예정 |


## 리포지토리 구조

```text
market-data-platform/
├── apps/
│   └── desktop/
├── modules/
│   ├── core-domain/
│   ├── collector-binance/
│   ├── collector-bybit/
│   ├── market-engine/
│   ├── ai-adapter/
│   └── local-storage/
├── docs/
├── docker-compose.yml
├── build.gradle
└── settings.gradle
```

| 모듈 | 책임 |
| --- | --- |
| `apps:desktop` | Compose for Desktop 실행 앱과 단일 사용자 제어 화면 |
| `modules:core-domain` | 공통 시장 이벤트 record와 공유 계약 |
| `modules:collector-binance` | Binance USD-M Futures public Kline 수집 |
| `modules:collector-bybit` | Bybit Linear public Kline 수집 |
| `modules:market-engine` | 500개 제한 rolling buffer와 불변 AI 분석 context |
| `modules:ai-adapter` | 로컬 Python TradingAgents와 LLM provider 호출 경계 |
| `modules:local-storage` | SQLite, DuckDB 등 로컬 저장 정책 |


## 이벤트 예시

정규화된 candle event는 다음과 같은 형태를 가집니다.

```json
{
  "provider": "BINANCE",
  "venue": "BINANCE_USDM_FUTURES",
  "assetClass": "CRYPTO_FUTURES",
  "symbol": "BTCUSDT",
  "interval": "15m",
  "open": 64320.1,
  "high": 64510.5,
  "low": 64280.2,
  "close": 64490.7,
  "volume": 120.5,
  "closed": false
}
```


## 소스에서 실행하기

필요한 환경:

- Java 21
- Python 3.10 이상, 현재 개발 환경은 3.13
- `uv`
- `/Users/apple/Desktop/TradingAgents`

TradingAgents용 Python 환경을 한 번 준비합니다.

```bash
uv venv --python 3.13 .venv-tradingagents
uv pip install --python .venv-tradingagents/bin/python -e /Users/apple/Desktop/TradingAgents
```

프로젝트를 빌드합니다.

```bash
./gradlew clean build
```

데스크톱 앱을 실행합니다.

```bash
./gradlew :apps:desktop:run
```

앱의 설정 아이콘에서 AI 제공자, 모델, API 키를 입력합니다. 키는 현재 세션에만 유지됩니다.


## 프로젝트 방향

계획하고 있는 방향은 로컬 우선 시장 분석 앱입니다.

```text
apps/desktop              # Compose for Desktop UI
modules/market-engine     # rolling buffer, feature 계산, AI context builder
modules/local-storage     # memory-only와 local persistence 정책
modules/ai-adapter        # TradingAgents와 LLM provider 연동
```

의도하는 데스크톱 흐름은 다음과 같습니다.

```text
1. 거래소와 심볼을 선택합니다.
2. 공개 시장 데이터를 로컬에서 수집합니다.
3. 최근 스트림을 정규화하고 요약합니다.
4. Analyze 버튼을 누릅니다.
5. 요약된 시장 context만 선택한 AI provider에 보냅니다.
6. 로컬 UI에서 분석 결과를 확인합니다.
```


## 로드맵

### Market Data

- [x] `MarketCandleEvent`
- [x] `MarketDlqEvent`
- [x] Binance USD-M Futures Kline 수집
- [x] Binance Kline to `MarketCandleEvent` 정규화
- [x] Bybit Linear Kline 수집
- [x] Bybit Kline to `MarketCandleEvent` 정규화
- [ ] `MarketTradeEvent`
- [ ] `MarketTickerEvent`
- [ ] `MarketOpenInterestEvent`
- [ ] `MarketFundingRateEvent`
- [ ] Order book top N summary

### Local Engine

- [x] 데스크톱 인프로세스 수집 경로
- [x] Memory-only rolling buffer
- [ ] SQLite 또는 DuckDB 저장
- [ ] Retention policy
- [ ] 1s/5s aggregation
- [ ] Volume, volatility, trade imbalance feature
- [x] 캔들 스냅샷 기반 AI analysis context builder

### AI Analysis

- [x] 로컬 Python TradingAgents adapter
- [x] 세션 단위 LLM provider·model configuration
- [x] 메모리 전용 API key policy
- [x] Analysis request/result model
- [x] Analysis report model

### Desktop App

- [x] Compose for Desktop 앱 모듈
- [x] Exchange selection UI
- [x] Symbol selection UI
- [x] 자동 collection lifecycle
- [x] Realtime collection status UI
- [x] AI 설정, analysis button, agent result view


## 문서

| 문서 | 내용 |
| --- | --- |
| [Collector MVP 1.0.0](docs/collector/collector-mvp-1.0.0.md) | Collector 범위, 이벤트 계약, 완료 기준 |
| [데스크톱 화면](docs/desktop-design.md) | 화면 구성, 반응형 동작, 창 프레임과 검증 방법 |
| [데스크톱-백엔드 연결](docs/desktop-backend-integration.md) | 패키지별 책임, Java 모듈 호출 경계, 수집기·저장소·TradingAgents 연결 방향 |
