# 데스크톱 화면

사용자가 제공한 Spotify 데스크톱 화면의 어두운 바탕, 3단 배치, 하단 고정 액션 영역을 참고합니다. 콘텐츠는 암호화폐 분석 작업에 맞게 구성하며 차트는 포함하지 않습니다.

## 구성

- 왼쪽: 관심 종목 목록, 즐겨찾기 필터, 종목 추가.
- 가운데: 선택한 마켓, 거래소와 타임프레임, 에이전트 분석, 세션별 분석 기록.
- 오른쪽: 최종 판단과 분석 컨텍스트. 에이전트를 선택하면 개별 리포트로 전환.
- 하단: 현재 선택한 마켓, AI 분석 실행, AI 설정 상태.

기본 창은 1440 × 940, 최소 크기는 420 × 560입니다.

- 폭 1280 이상: 관심 종목, 분석, 브리핑의 3단 구성.
- 폭 800~1279: 관심 종목과 분석의 2단 구성. 개별 리포트는 중앙에 표시.
- 폭 800 미만: 단일 화면. 메뉴나 검색으로 관심 종목 목록을 열고 종목을 선택하면 분석 화면으로 복귀.

사이드바는 창 폭의 17%(192~240), 브리핑은 22%(272~336) 범위에서 유연하게 조절됩니다. 패널 접기와 펼치기는 180ms 전환을 사용하며, 실제 창 크기 변화는 지연 없이 반영합니다. 선택한 종목·리포트와 스크롤 상태는 레이아웃 전환 시 유지합니다. 필터는 공간이 부족하면 다음 줄로 배치합니다.

넓은 화면에서는 중앙과 오른쪽 리포트 사이 경계선을 좌우로 드래그해 리포트 폭을 조절할 수 있습니다. 최소 리포트 폭은 272dp이며 중앙 분석 영역은 최소 480dp를 유지합니다. 중간·좁은 화면에서는 리포트가 중앙 전체 폭을 사용합니다.

에이전트는 가용 폭에 따라 1~4열로 배치합니다. 단일 열에서는 가로형 항목을 사용합니다. 작은 창과 높이가 낮은 창에서는 종목 헤더를 압축하고 중복된 요약 지표를 생략합니다.

## 창 프레임

왼쪽 상단 로고와 브랜드 문구, 네이티브 창 제목은 표시하지 않습니다. macOS에서는 `apple.awt.fullWindowContent`, `apple.awt.transparentTitleBar`, `apple.awt.windowTitleVisible` 설정으로 콘텐츠를 제목 표시줄까지 확장합니다. 창·콘텐츠·루트 패널 배경은 모두 `#080909`입니다. 기본 창 버튼과 크기 조절을 유지하며 상단 32dp는 창 이동 영역입니다.

이 설정은 [OpenJDK 21의 macOS 창 구현](https://github.com/openjdk/jdk21u/blob/master/src/java.desktop/macosx/classes/sun/lwawt/macosx/CPlatformWindow.java)과 [Compose 창 이동 영역 API](https://kotlinlang.org/docs/multiplatform/compose-desktop-top-level-windows-management.html#make-window-areas-draggable)를 사용합니다. 다른 운영체제의 창 장식은 해당 운영체제 설정을 따릅니다.

## 시각 규칙

- 배경은 `#080909`, 패널은 `#121414`, 개별 분석 항목은 `#1C1F1E`입니다.
- 민트 `#8AE6AD`는 주요 동작과 선택 상태에 사용합니다.
- 블루 `#9BBDF6`, 코랄 `#F2AA98`, 골드 `#E7CF8D`는 에이전트 구분에 사용합니다. 색만으로 상태를 전달하지 않습니다.
- 폰트는 시스템 sans-serif와 한글 폴백을 사용하고 자간은 0으로 유지합니다.
- 개별 항목의 모서리는 최대 8dp입니다. 그림자와 장식용 이미지는 최소화합니다.
- 기존 Compose Material 아이콘을 사용합니다. 아이콘 버튼에는 접근성 이름과 툴팁을 제공합니다.

## 상태와 동작

현재 실행 경로는 공개 거래소 REST·WebSocket, 메모리 엔진, 로컬 Python TradingAgents 브리지로 연결됩니다. 확정 캔들 200개와 AI 설정이 준비되면 분석 버튼이 활성화됩니다. 뉴스·소셜·펀더멘털은 데이터 소스가 없으므로 분석 결과에서 제외 상태로 표시합니다.

에이전트 리포트는 CommonMark 문법으로 렌더링합니다. 제목, 강조, 목록, 인라인 코드, 코드 블록, 인용문, 링크와 표를 지원하며 패널 너비에 맞춰 본문이 다시 배치됩니다.

관심 목록, AI 키, 분석 기록은 현재 앱 세션에만 유지됩니다. 기록은 최근 50개로 제한합니다. 결과는 종목·거래소·타임프레임에 연결되며, 분석 중 선택이 바뀌어도 다른 마켓에 결과를 표시하지 않습니다. 시장 수집과 AI 분석은 UI 스레드 밖에서 실행합니다.

## 구현과 검증

- `Main.kt`, `platform/window/DesktopWindow.kt`: 앱 창, 프레임 색상, 제목, 최소 크기.
- `application/DesktopCompositionRoot.kt`: Java 모듈과 ViewModel의 생성자 연결.
- `application/AnalyzeMarketUseCase.kt`: 시장 컨텍스트 조회와 AI 어댑터 호출.
- `presentation/WorkspaceViewModel.kt`: 선택, 필터, 분석 기록, 비동기 실행 상태의 캡슐화.
- `model/`: 화면에서 사용하는 마켓 모델과 종목 카탈로그.
- `ui/workspace/`: 화면 조합, 상단 탐색, 하단 분석 버튼.
- `ui/analysis/`, `ui/report/`, `ui/history/`, `ui/watchlist/`: 기능별 화면.
- `ui/components/`, `ui/theme/`, `ui/layout/`: 공통 컴포넌트, 시각 규칙, 반응형 기준.

클래스별 책임과 백엔드 연결 순서는 [데스크톱-백엔드 연결 가이드](desktop-backend-integration.md)에 정리합니다.

```bash
./gradlew :apps:desktop:run
./gradlew :apps:desktop:verifyWorkspace
./gradlew :apps:desktop:verifyDesktopUi
./gradlew :apps:desktop:verifyDesktopWindow
./gradlew :apps:desktop:check
./gradlew :apps:desktop:createDistributable
```

`verifyDesktopUi`는 폭 420~1920의 8가지 창 크기에서 렌더링과 입력 동작을 검사합니다. 같은 Compose 장면의 크기를 연속으로 바꾸면서 패널 전환과 선택된 리포트의 유지도 검증합니다. 결과 이미지는 `apps/desktop/build/reports/desktop-ui`에 생성됩니다.

`verifyDesktopWindow`는 화면에 표시하지 않은 실제 ComposeWindow의 네이티브 피어를 생성해 제목, 배경색, 최소 크기, 기본 창 장식과 macOS 프레임 속성을 확인합니다. OS 화면 녹화 권한은 사용하지 않으며, 실제 창 장식의 스크린샷 검사는 포함하지 않습니다.

macOS 앱 이미지에 한해 패키지 버전을 `1.0.0`으로 지정합니다. 현재 JDK 21의 `jpackage`가 앱 이미지 버전에 양수로 시작하는 값을 요구하기 때문이며, 제품의 정식 출시를 의미하지 않습니다. [Compose 패키징 문서](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html#package-version)의 OS별 버전 설정을 사용합니다.
