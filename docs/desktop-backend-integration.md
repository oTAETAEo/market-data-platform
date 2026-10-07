# 데스크톱과 백엔드 연결

## 실행 흐름

데스크톱 앱과 Java 모듈은 같은 JVM에서 직접 호출됩니다. 별도 HTTP 서버는 사용하지 않습니다.

```mermaid
flowchart LR
    Exchange[Binance / Bybit 공개 API] --> Client[로컬 수집기]
    Client --> Engine[MarketEngine]
    Engine --> Buffer[심볼별 500개 캔들 버퍼]
    Buffer --> VM[WorkspaceViewModel]
    VM --> UI[Compose UI]
    VM --> UseCase[AnalyzeMarketUseCase]
    UseCase --> Snapshot[불변 MarketContext]
    Snapshot --> Adapter[LocalTradingAgentsAdapter]
    Adapter --> Worker[일회성 Python 프로세스]
    Worker --> TA[/Users/apple/Desktop/TradingAgents]
    TA --> LLM[사용자가 선택한 AI 제공자]
    LLM --> UI
```

객체는 [DesktopCompositionRoot](../apps/desktop/src/main/kotlin/com/marketdata/desktop/application/DesktopCompositionRoot.kt)에서 조립합니다. 앱 종료 시 수집기와 재연결 스케줄러를 함께 닫습니다.

## 구현된 범위

| 영역 | 현재 동작 |
| --- | --- |
| Binance | USD-M Futures REST 300봉 초기 조회 후 Kline WebSocket 연결 |
| Bybit | Linear Futures REST 300봉 초기 조회 후 Kline WebSocket 연결 |
| 수집 전환 | 종목·거래소·타임프레임 변경 시 이전 연결 종료 후 새 연결 시작 |
| 메모리 엔진 | `openTime` 기준 업서트, 확정 봉 보호, 역순 이벤트 무시, 스트림당 최근 500개 보관 |
| 분석 준비 | 확정 봉 200개 이상일 때 분석 버튼 활성화 |
| AI 설정 | 앱에서 제공자·빠른 모델·심층 모델·API 키 입력 |
| API 키 | 파일에 저장하지 않고 현재 앱 메모리와 일회성 Python 표준입력에서만 사용 |
| TradingAgents | 기술적 분석 → 상승·하락 토론 → 트레이더 → 리스크 → 포트폴리오 판단 실행 |
| 분석 데이터 | 앱이 수집한 선물 캔들만 기술 분석 도구에 주입하며 주식 데이터로 대체하지 않음 |
| 결과 | 에이전트별 리포트와 최종 Buy/Overweight/Hold/Underweight/Sell 판단 표시 |

TradingAgents에는 현재 `market` analyst만 활성화합니다. 암호화폐 선물에 기업 펀더멘털은 적용하지 않으며, 실시간 뉴스·소셜 소스도 아직 연결되지 않았습니다. 해당 세 패널은 결과를 만들지 않고 `SKIPPED`로 표시합니다.

주문 실행 기능은 없습니다. 모든 결과는 분석 리포트입니다.

## 패키지 책임

| 경로 | 책임 |
| --- | --- |
| `apps/desktop/.../application/MarketDataRuntime.kt` | 거래소 수집기 생성·전환·종료 |
| `apps/desktop/.../application/AnalyzeMarketUseCase.kt` | 시장 스냅샷과 AI 어댑터 호출 순서 |
| `apps/desktop/.../presentation/WorkspaceViewModel.kt` | 화면 상태, AI 설정, 분석 실행과 기록 |
| `modules:collector-binance` | Binance 공개 REST·WebSocket 수집 |
| `modules:collector-bybit` | Bybit 공개 REST·WebSocket 수집 |
| `modules:market-engine` | 캔들 검증·업서트·제한 버퍼·불변 분석 컨텍스트 |
| `modules:ai-adapter` | JSONL 프로토콜, Python 프로세스, 결과 매핑 |
| `/Users/apple/Desktop/TradingAgents` | 기존 Python 멀티 에이전트 그래프 |

기존 Spring/Kafka 수집 코드는 모듈에 남아 있지만 데스크톱 실행 경로에서는 생성하지 않습니다. 로컬 앱은 `BinanceMarketDataClient`와 `BybitMarketDataClient`를 직접 생성해 `MarketEngine.accept`로 전달합니다.

## Python 환경 준비

이 워크스페이스는 `.venv-tradingagents`를 사용합니다. 가상환경과 패키지는 Git에 포함하지 않습니다.

```bash
uv venv --python 3.13 .venv-tradingagents
uv pip install --python .venv-tradingagents/bin/python -e /Users/apple/Desktop/TradingAgents
```

기본 경로가 다르면 실행 전에 환경변수를 설정합니다.

```bash
export MARKET_DATA_PYTHON=/absolute/path/to/python
export TRADINGAGENTS_REPO=/absolute/path/to/TradingAgents
./gradlew :apps:desktop:run
```

현재 기본값은 다음과 같습니다.

- Python: 프로젝트 루트의 `.venv-tradingagents/bin/python`
- TradingAgents: `$HOME/Desktop/TradingAgents`

## 앱에서 분석하기

1. 앱을 실행하면 기본 선택인 Binance BTCUSDT 15분봉을 수집합니다.
2. 화면의 캔들 수가 200개 이상이고 데이터 상태가 준비 완료인지 확인합니다.
3. 우측 상단 설정 아이콘에서 AI 제공자, 빠른 모델, 심층 모델, API 키를 입력합니다.
4. `AI 분석` 버튼을 누릅니다.
5. 분석이 끝나면 에이전트 카드를 선택해 각 리포트를 확인합니다.

API 키가 필요한 제공자에서 키를 입력하지 않으면 분석 버튼이 활성화되지 않습니다. Ollama, Bedrock, 범용 OpenAI 호환 제공자는 키 없이 설정할 수 있지만 해당 런타임·인증 체인은 사용자가 별도로 준비해야 합니다.

## Python 브리지

[tradingagents_worker.py](../modules/ai-adapter/src/main/resources/tradingagents_worker.py)는 분석마다 새 프로세스로 실행됩니다. 요청에는 `requestId`, AI 설정, 불변 캔들 스냅샷이 들어갑니다. 응답은 JSONL의 `progress`, `report`, `result`, `error` 메시지입니다.

브리지는 다음을 보장합니다.

- 다른 심볼의 캔들, 미래 캔들, 잘못된 OHLCV를 거부합니다.
- 확정 봉 200개 미만이면 실행하지 않습니다.
- 기술 도구의 정확한 가격·지표 값은 전달된 캔들에서만 계산합니다.
- API 키를 로그와 오류 메시지에 포함하지 않습니다.
- 실행별 임시 캐시·메모리 경로를 사용하고 성공·실패 후 정리합니다.
- 15분 제한 시간을 넘긴 프로세스는 JVM에서 종료합니다.

LLM 출력은 확률적이며 투자 결과를 보장하지 않습니다. 모델이 명시적인 `**Rating**`을 반환하지 않으면 앱은 임의로 Hold로 바꾸지 않고 `UNRESOLVED`로 표시합니다.

## 검증 명령

```bash
./gradlew :modules:market-engine:test
./gradlew :modules:collector-binance:test :modules:collector-bybit:test
./gradlew :modules:ai-adapter:test
./gradlew :apps:desktop:verifyLiveMarket
.venv-tradingagents/bin/python -m unittest discover -s modules/ai-adapter/src/test/python -v
./gradlew check :apps:desktop:createDistributable
```

`verifyLiveMarket`는 Binance 공개 네트워크를 사용합니다. 나머지 단위 검사는 외부 LLM 호출이나 유료 API를 사용하지 않습니다. 실제 LLM 호출은 사용자가 앱에 입력한 키로 분석 버튼을 누를 때만 발생합니다.

## 아직 남은 작업

- API 키의 OS Keychain 선택 저장 기능
- 뉴스·소셜·암호화폐 온체인 데이터 소스
- 로컬 분석 기록과 설정 영구 저장
- 누락 캔들 감지와 REST 구간 보완
- Python 실행 환경을 배포 앱에 함께 패키징
- 분석 취소 버튼과 단계별 실시간 리포트 표시
- Open Interest, Funding Rate, Order Book 등 단기 매매 데이터

현재 배포 이미지에는 Python 가상환경이 포함되지 않습니다. 다른 컴퓨터에 배포할 때는 Python 런타임 포함 방식부터 결정해야 합니다.
