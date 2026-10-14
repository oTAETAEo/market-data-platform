# Python 배포 의존성

[English](python-distribution.en.md) | [오픈소스 고지](third-party-notices.ko.md)

## 현재 배포 경계

검토 기준은 2026-10-14입니다. 현재 Compose 배포 설정은 JVM 앱과 JDK·JavaFX 런타임을 대상으로 합니다. TradingAgents checkout과 Python 가상환경은 `.app`에 포함하지 않고 외부 로컬 경로에서 실행합니다.

앱 내부 worker 스크립트는 JVM 리소스로 포함됩니다. 이 스크립트가 외부 TradingAgents를 불러오는 것과 TradingAgents 소스·Python 환경 자체를 설치 파일에 포함하는 것은 구분해야 합니다. 외부 프로세스라는 이유만으로 라이선스 검토가 면제되는 것은 아닙니다.

| 구분 | 현재 상태 | 근거 |
| --- | --- | --- |
| Python 실행 파일 | 외부 가상환경 또는 `MARKET_DATA_PYTHON` 경로 | [DesktopCompositionRoot](../../apps/desktop/src/main/kotlin/com/marketdata/desktop/application/DesktopCompositionRoot.kt) |
| TradingAgents 소스 | 외부 checkout 또는 `TRADINGAGENTS_REPO` 경로 | [DesktopCompositionRoot](../../apps/desktop/src/main/kotlin/com/marketdata/desktop/application/DesktopCompositionRoot.kt) |
| worker 스크립트 | JVM 리소스에 포함, 임시 파일로 실행 | [LocalTradingAgentsAdapter](../../modules/ai-adapter/src/main/java/com/marketdata/ai/LocalTradingAgentsAdapter.java) |
| 분석 데이터 도구 | 앱의 확정 캔들 스냅샷을 pandas·stockstats로 처리 | [tradingagents_worker.py](../../modules/ai-adapter/src/main/resources/tradingagents_worker.py) |
| 설치 환경 | 외부 TradingAgents를 editable install | [소스 실행 안내](../../README.md) |

## 검토한 TradingAgents 버전

- 프로젝트 버전: 0.3.1
- 커밋: `a33fd4c0f134485a43553a2c23a63cb14adbd88f`
- 프로젝트 라이선스: [Apache-2.0 원문](../../licenses/tradingagents/LICENSE)
- 선언된 의존성: [고정 커밋의 pyproject.toml](https://github.com/TauricResearch/TradingAgents/blob/a33fd4c0f134485a43553a2c23a63cb14adbd88f/pyproject.toml)

이 버전의 `pyproject.toml`에는 `>=` 의존성이 다수 포함됩니다. 새로 설치하면 검토 때와 다른 패키지·버전이 해석될 수 있습니다. 여기의 개발 환경 버전 표는 배포용 lockfile이 아니며, 기존 가상환경을 그대로 복사해 출시할 수 있다는 의미도 아닙니다.

주요 관찰 버전과 라이선스는 [오픈소스 고지](third-party-notices.ko.md)에 기록했습니다. Python 환경 전체의 간접 의존성·네이티브 휠 검토는 아직 완료하지 않았습니다.

## backtrader 확인 사항

`backtrader>=1.9.78.123`은 TradingAgents의 일반 의존성으로 선언돼 있으며 개발 환경에 1.9.78.123이 설치되어 있습니다. [패키지 라이선스](https://pypi.org/project/backtrader/1.9.78.123/)는 GPLv3+입니다.

검토한 TradingAgents 소스와 앱 worker에서 `backtrader` 사용 참조는 확인되지 않았습니다. 이는 배포 환경에서의 동적 사용 가능성까지 증명하는 실행 테스트가 아닙니다. 설치됐다는 이유만으로 앱 전체가 GPL이 된다고 단정하지도 않습니다.

현재 문서 작업은 패키지를 제거하거나 upstream 의존성 선언을 변경하지 않습니다. 나중에 배포 구성에서 제외하려면 필요한 기능을 확인하고 선언·설치 과정에서 다시 추가되지 않도록 관리한 뒤 분석 경로를 검증해야 합니다. 포함해 배포한다면 GPL 의무와 실제 결합 범위를 검토해야 합니다. 사용자가 별도로 설치하게 만드는 방법도 자동 면제 수단으로 취급하지 않습니다.

## 데이터와 서비스 조건

현재 worker는 선택한 종목의 확정 캔들 스냅샷을 데이터·지표 도구로 연결하고, 뉴스·소셜·기업 펀더멘털 분석은 제외합니다. 이것을 향후 모든 Yahoo 또는 다른 공급자 요청이 차단된다는 보장으로 해석하지 않습니다.

`yfinance` 코드는 Apache-2.0이지만 [공식 안내](https://github.com/ranaroussi/yfinance#readme)는 Yahoo 데이터의 약관과 개인용 이용 범위를 별도로 확인하라고 명시합니다. 거래소 시장 데이터, 뉴스, LLM 서비스 권리·비용도 Python 패키지 라이선스와 별개입니다.

## 배포 준비 체크리스트

- [x] 현재 외부 Python/TradingAgents 실행 경계 기록
- [x] TradingAgents 커밋 및 주요 개발 패키지 라이선스 기록
- [x] backtrader GPL 및 yfinance 데이터 권리 주의 사항 기록
- [ ] Python 번들 여부와 플랫폼별 제공 방식 결정
- [ ] 필요한 기능 기준의 직접·간접 패키지 목록 확정
- [ ] Python 버전·upstream 커밋·전체 의존성 버전·아티팩트 해시 고정
- [ ] 별도 깨끗한 환경에서 설치·초기화·분석 경로 검증
- [ ] backtrader 등 제외 대상이 다시 설치되거나 사용되지 않는지 검증
- [ ] 실제 배포 휠·네이티브 라이브러리·Python 런타임의 원문 고지 수집
- [ ] 포함한 GPL/LGPL/MPL 등 구성요소의 해당 소스 및 재배포 조건 정리
- [ ] 릴리스별 실제 패키지 목록과 고지를 설치 파일에 포함

Python을 번들하는 방식으로 바꿀 때는 이 문서와 최종 릴리스 목록을 함께 갱신합니다. 앱 코드 라이선스, TradingAgents 라이선스, 개별 패키지 라이선스 및 데이터 이용 약관을 서로 대체하지 않습니다.
