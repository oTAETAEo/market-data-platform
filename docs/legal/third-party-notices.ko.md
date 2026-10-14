# 오픈소스 고지

[English](third-party-notices.en.md) | [고지 인덱스](../../THIRD_PARTY_NOTICES.md)

## 검토 범위와 상태

검토일은 2026-10-14입니다. Gradle 설정, 로컬 Python 패키지 메타데이터, 외부 TradingAgents 소스와 macOS arm64 개발 배포본을 기준으로 합니다. 여기의 버전은 검토 시점의 값이며 모든 운영체제나 이후 릴리스에 자동 적용되지 않습니다.

이 문서는 주요 구성요소에 대한 **부분 고지 목록**입니다. 전체 간접 의존성 및 네이티브 코드 SBOM은 아직 작성하지 않았으며, 상용 출시의 법적 적합성을 보증하지 않습니다. 현재 앱에는 이 목록을 표시하는 고지 화면이나 설치 파일에 원문을 포함하는 자동화가 없습니다.

프로젝트 자체 코드의 라이선스는 아직 선택되지 않았습니다. 제3자 라이선스를 프로젝트 전체에 임의로 적용하지 않습니다. 공개 저장소와 오픈소스 라이선스 부여는 별개의 일입니다. [GitHub 안내](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository)

## 주요 JVM 및 화면 구성요소

| 구성요소 | 확인 버전 | 라이선스 및 출처 | 현재 사용 형태 |
| --- | --- | --- | --- |
| Lightweight Charts | 5.2.1 | [Apache-2.0 및 출처 표시 안내](https://github.com/tradingview/lightweight-charts/tree/v5.2.1#license) | WebView가 CDN에서 고정 버전 로드 |
| Compose Multiplatform | 1.6.11 | [Apache-2.0](https://github.com/JetBrains/compose-multiplatform/blob/v1.6.11/LICENSE.txt) | 앱의 데스크톱 UI |
| Kotlin | 플러그인 1.9.24 | [Apache-2.0](https://github.com/JetBrains/kotlin/blob/v1.9.24/license/LICENSE.txt) | 빌드 및 Kotlin 런타임; 모든 런타임 JAR 버전과 동일하다는 뜻은 아님 |
| Compose Richtext | 0.17.0 | [Apache-2.0](https://github.com/halilozercan/compose-richtext/blob/v0.17.0/LICENSE) | 마크다운 리포트 표시 |
| Spring Boot / Spring Framework | 3.5.10 / 6.2.15 | [Boot](https://github.com/spring-projects/spring-boot/blob/v3.5.10/LICENSE.txt), [Framework](https://github.com/spring-projects/spring-framework/blob/v6.2.15/LICENSE.txt): Apache-2.0 | 수집기 등의 런타임 의존성 |
| Jackson | 2.19.4 | [Apache-2.0](https://github.com/FasterXML/jackson-databind/blob/jackson-databind-2.19.4/LICENSE) | JSON 처리 |
| Logback | 1.5.25 | [EPL-2.0 또는 LGPL-2.1](https://logback.qos.ch/license.html) | 로그; 적용할 라이선스와 배포 의무 별도 정리 필요 |
| Skiko | macOS 0.8.4; 추가 Linux 런타임 JAR 0.7.63도 관찰됨 | [Apache-2.0](https://github.com/JetBrains/skiko/blob/57dea506d176724c82db1606edf0123ab60904f4/LICENSE) | 네이티브 렌더링; 실제 포함 대상 재확인 필요 |
| Skia 및 네이티브 의존성 | Skiko 0.8.4 기준 `m116-47d3027-1` | [Skia BSD-3-Clause](https://github.com/JetBrains/skia/blob/47d3027b8cbeea76135c0995d5886aa2e58e7512/LICENSE), 포함 라이브러리는 별도 조건 | libjpeg-turbo, libpng, HarfBuzz 등을 포함한 전체 목록 미검증 |
| Liberica/OpenJDK | 개발 빌드 JDK 21.0.9+11-LTS; 번들 `release`는 21.0.9 | GPL-2.0 및 해당 Classpath 예외, 추가 네이티브 조건 | 축소 JDK 런타임을 앱에 포함 |
| JavaFX | 21.0.10, upstream 빌드 21.0.10+2 | [GPL-2.0 및 해당 Classpath 예외](../../licenses/javafx/LICENSE), 포함 라이브러리는 별도 조건 | JavaFX JAR와 WebView 네이티브 라이브러리 포함 |

Kafka, Reactor, Netty, CommonMark, OkHttp 및 기타 간접 의존성도 최종 릴리스 목록에 포함해 별도로 확인해야 합니다. 위 표에서 누락됐다고 해서 배포물에 없거나 의무가 없다는 뜻은 아닙니다. 최종 버전은 Gradle 해석 결과와 실제 설치 파일을 기준으로 기록합니다.

## TradingView 고지

원본 NOTICE는 [보존 파일](../../licenses/lightweight-charts/NOTICE)에 있습니다.

```text
TradingView Lightweight Charts™
Copyright (с) 2025 TradingView, Inc. https://www.tradingview.com/
```

현재 앱은 TradingView 웹 서비스나 Advanced Charts가 아니라 Lightweight Charts를 사용합니다. 차트 화면에 `Charts by TradingView` 링크가 있으나, 배포 전 공식 NOTICE의 출처 고지를 사용자에게 보이는 위치에도 제공해야 합니다. 이 문서를 GitHub에 게시하는 것만으로 앱 화면의 고지 의무를 완료했다고 보지 않습니다. 링크 요건과 출처 고지는 [v5.2.1 공식 안내](https://github.com/tradingview/lightweight-charts/tree/v5.2.1#license)를 따릅니다.

이 라이브러리에 포함된 tslib 일부 코드는 BSD-0-Clause로 안내돼 있습니다. Apache 고지로 다른 구성요소의 라이선스를 덮어쓰지 않습니다. TradingView 또는 다른 프로젝트가 이 앱을 후원하거나 인증한다는 뜻으로 이름·상표를 사용하지 않습니다.

## Python 구성요소

TradingAgents와 Python 환경은 현재 설치 파일에 번들되지 않고 외부 로컬 경로로 참조됩니다. 아래 항목은 개발 환경에서 확인한 목록이며 완전한 배포 잠금 파일은 아닙니다. [Python 배포 문서](python-distribution.ko.md)에서 실제 배포 경계와 미완료 항목을 확인합니다.

| 구성요소 | 개발 환경 버전 | 확인한 라이선스 / 출처 |
| --- | --- | --- |
| TradingAgents | 0.3.1, 커밋 `a33fd4c0f134485a43553a2c23a63cb14adbd88f` | [Apache-2.0 원문](../../licenses/tradingagents/LICENSE) |
| backtrader | 1.9.78.123 | [GPLv3+ 패키지 정보](https://pypi.org/project/backtrader/1.9.78.123/), [GPLv3 원문](../../licenses/backtrader/LICENSE) |
| stockstats | 0.6.9 | [BSD-3-Clause](https://github.com/jealous/stockstats/blob/master/LICENSE.txt) |
| pandas | 3.0.6 | 개발 패키지 메타데이터: BSD-3-Clause; 포함된 제3자 고지 별도 확인 |
| yfinance | 1.7.0 | [Apache-2.0 및 Yahoo 데이터 권리 안내](https://github.com/ranaroussi/yfinance#readme) |
| langchain-core / langgraph | 1.6.6 / 1.2.12 | 개발 패키지 메타데이터: MIT |
| certifi | 2026.7.22 | 개발 패키지 메타데이터: MPL-2.0 |
| orjson | 3.12.0 | 개발 패키지 메타데이터: `MPL-2.0 AND (Apache-2.0 OR MIT)` |

메타데이터만으로 모든 내장 코드의 라이선스를 확인했다고 보지 않습니다. Python 바이너리, 휠에 포함된 네이티브 라이브러리 및 실제 포함 패키지의 원문을 최종 배포 전에 조사합니다.

## 조건을 읽을 때

- Apache-2.0 구성요소는 원문 사본, 해당 저작권·NOTICE를 보존하고 수정한 외부 파일에는 변경 사실을 표시합니다. 별도 상용 계약을 체결하지 않은 독립 앱에 앱 전체 소스 공개를 일반적으로 강제하는 조건은 아닙니다. [원문](../../licenses/Apache-2.0.txt)
- Classpath 예외는 지정된 코드에 대해 독립 앱 모듈의 다른 라이선스를 허용하지만, 재배포하는 JDK·JavaFX 자체의 GPL 의무나 WebKit 등 별도 라이선스를 없애지 않습니다.
- GPL·LGPL·EPL·MPL 구성요소는 같은 조건이 아닙니다. 해당 구성요소와 결합·수정·배포 방식에 따라 소스 제공과 이용자의 권리 보장 조건을 확인합니다. GPL도 유료 판매 자체를 금지하지 않습니다.
- 라이선스 요약은 원문을 대체하지 않으며, 이 문서는 새 계약이나 소스 제공 제안서가 아닙니다.

## 별도 권리와 미완료 항목

오픈소스 이용 허가는 거래소 시장 데이터, Yahoo 데이터, 뉴스·소셜 데이터, LLM 서비스의 이용·재판매 권한을 포함하지 않습니다. 이들의 약관, API 비용 및 유료 투자 분석 서비스 관련 규제는 별도 검토 대상이며 이번 문서에서 승인한 것으로 보지 않습니다.

- [x] 주요 구성요소의 출처와 검토 범위 문서화
- [x] 선택한 라이선스 원문과 Lightweight Charts NOTICE 보존
- [ ] 프로젝트 자체 라이선스 선택 및 적용
- [ ] 모든 실제 배포 파일에 대한 간접·네이티브 라이선스 목록 확정
- [ ] JavaFX·Skiko/Skia 등의 구성요소별 고지 전체 수집
- [ ] 설치 파일의 라이선스 포함 자동화 및 사용자용 고지 표시
- [ ] 대응 런타임 소스 제공과 GPL/LGPL 조건 충족 검증
- [ ] Python 배포 의존성 고정 및 불필요한 패키지 제외 검증

라이선스 원문은 [원문 목록](../../licenses/README.md), 런타임 대응 소스는 [런타임 안내](runtime-sources.ko.md)를 참조합니다.
