# 런타임 소스 안내

[English](runtime-sources.en.md) | [오픈소스 고지](third-party-notices.ko.md)

> 이 문서는 upstream 소스의 출처를 안내하는 개발 자료입니다. 법적 소스 제공 제안서가 아니며, 배포 의무 충족을 선언하지 않습니다. 대응 소스 아카이브를 이 저장소의 Releases에 함께 제공하는 작업은 아직 완료되지 않았습니다.

## 기준 배포본

- 검토일: 2026-10-14
- 프로젝트 기준 커밋: `c157dc888377ccf4e1858fe09f18ed93c9b21c70`
- 플랫폼: macOS arm64 개발 배포본
- 빌드 JDK: BellSoft Liberica 21.0.9+11-LTS
- 번들 런타임의 `release` 파일: `JAVA_VERSION="21.0.9"`
- JavaFX: Gradle 의존성 21.0.10, upstream 빌드 21.0.10+2
- Skiko: macOS 0.8.4; 추가로 포함된 Linux 0.7.63 런타임 JAR도 관찰됨

이 정보는 Windows·Linux 배포본까지 검증했다는 뜻이 아닙니다. JDK 공급자와 버전은 빌드 환경에 따라 달라질 수 있습니다. Skiko 0.7.63을 포함한 추가 파일은 최종 배포 범위를 다시 확인해야 합니다.

## 소스 출처

| 구성요소 | 검토 기준 소스 | 확인할 내용 |
| --- | --- | --- |
| Liberica JDK 21.0.9+11 | [공급자 소스 아카이브](https://github.com/bell-sw/Liberica/releases/download/21.0.9%2B11/bellsoft-jdk21.0.9%2B11-src.tar.gz), [공급자 조건](https://bell-sw.com/liberica_eula/) | 실제 사용 바이너리와의 대응, 빌드·패치 및 모듈별 고지 |
| JavaFX 21.0.10+2 | [고정 커밋 소스](https://github.com/openjdk/jfx21u/tree/46ca2bddc12d85d98f306ef0ccfcb1f478881fb2), [LICENSE 보존 파일](../../licenses/javafx/LICENSE) | Maven 바이너리와 소스·패치·빌드 자료의 대응 확인 |
| WebKit 622.1 | [JavaFX 내 네이티브 소스](https://github.com/openjdk/jfx21u/tree/46ca2bddc12d85d98f306ef0ccfcb1f478881fb2/modules/javafx.web/src/main/native), [WebKit 고지와 LGPL 조항](https://github.com/openjdk/jfx21u/blob/46ca2bddc12d85d98f306ef0ccfcb1f478881fb2/modules/javafx.web/src/main/legal/webkit.md) | JavaFX 적용 수정본, LGPL 및 내장 구성요소의 별도 조건 |
| GLib 2.82.4 | [JavaFX 포함 소스](https://github.com/openjdk/jfx21u/tree/46ca2bddc12d85d98f306ef0ccfcb1f478881fb2/modules/javafx.media/src/main/native/gstreamer/3rd_party/glib) | JavaFX media 라이브러리의 LGPL 고지 및 대응 소스 |
| GStreamer 1.24.10 | [JavaFX 포함 소스](https://github.com/openjdk/jfx21u/tree/46ca2bddc12d85d98f306ef0ccfcb1f478881fb2/modules/javafx.media/src/main/native/gstreamer/gstreamer-lite) | gstreamer-lite 수정본의 LGPL 고지 및 대응 소스 |
| Skiko 0.8.4 | [고정 커밋](https://github.com/JetBrains/skiko/tree/57dea506d176724c82db1606edf0123ab60904f4) | Apache-2.0, 빌드 의존성과 네이티브 고지 |
| Skia `m116-47d3027-1` | [고정 소스](https://github.com/JetBrains/skia/tree/47d3027b8cbeea76135c0995d5886aa2e58e7512), [DEPS](https://github.com/JetBrains/skia/blob/47d3027b8cbeea76135c0995d5886aa2e58e7512/DEPS) | BSD-3-Clause 및 빌드에 실제 포함된 제3자 구성요소별 조건 |
| 추가 Skiko 0.7.63 | [고정 커밋](https://github.com/JetBrains/skiko/tree/328d5e565f1968a3bcb071efe18841916cf5a033) | 이 런타임의 별도 네이티브 의존성과 포함 필요성 |

위 링크는 출처를 추적하기 위한 것입니다. 링크가 존재한다는 사실만으로 정확한 대응 소스 제공, 장기 가용성 또는 필요한 빌드 자료 제공이 완료된 것으로 보지 않습니다. 모든 구성요소에 동일한 소스 제공 의무가 있다는 뜻도 아닙니다. GPL/LGPL 조건과 Apache/BSD 고지 조건은 구성요소별로 구분합니다.

## 현재 확인한 것과 남은 것

현재 `.app`에는 다음 경로가 있습니다.

```text
MarketDataPlatform.app/Contents/runtime/Contents/Home/legal/
MarketDataPlatform.app/Contents/app/resources/javafx/
MarketDataPlatform.app/Contents/app/libskiko-macos-arm64.dylib
```

JDK의 `legal/`에는 `LICENSE`, `ADDITIONAL_LICENSE_INFO`, `ASSEMBLY_EXCEPTION` 및 FreeType 등 제3자 고지가 있습니다. 실제 공급자 배포본의 이 디렉터리를 유지해야 하며, JavaFX LICENSE 파일 하나로 대체하지 않습니다.

검사한 JavaFX JAR와 Skiko JAR에서는 필요한 라이선스·고지 항목이 충분히 확인되지 않았습니다. 번들 안에서 대응 소스 아카이브나 유효한 소스 제공 제안서도 확인되지 않았습니다. 다른 릴리스 자료를 통한 제공 여부는 미검증입니다.

Classpath 예외는 독립 앱 모듈의 라이선스를 허용하는 조건이지 JDK·JavaFX의 재배포 의무를 삭제하는 조건이 아닙니다. 별도 LGPL 구성요소의 경우 대응 라이브러리 소스, 필요한 고지 및 사용자의 수정·디버깅·교체/재링크 권리를 확인해야 합니다. 앱 서명, 네이티브 파일 추출 및 로딩 방식이 이를 만족하는지는 아직 검증하지 않았습니다.

## 릴리스별로 기록할 항목

배포할 때는 upstream 홈페이지 주소만 기록하지 말고 실제 배포 파일 기준으로 다음 항목을 확정합니다.

| 항목 | 현재 상태 |
| --- | --- |
| 앱 릴리스 태그와 플랫폼별 바이너리 목록 | 정식 릴리스 기준 미확정 |
| 런타임 공급자·정확한 버전·소스 커밋 | 위 개발 기준만 기록 |
| 배포 바이너리와 대응 소스 아카이브의 SHA-256 | 미작성 |
| 적용한 패치·수정 사실·필요한 빌드 스크립트 | 대응 여부 검증 미완료 |
| 구성요소별 라이선스와 NOTICE 포함 위치 | JDK legal 관찰, 전체 수집 미완료 |
| 소스 제공 방식과 실제 다운로드 위치 | upstream 출처만 기록, 릴리스 소스 제공 미완료 |
| 소스 제공 제안 방식을 선택한 경우 유효기간·운영 책임 | 방식을 선택하지 않았으며 제공을 약속하는 문서 아님 |
| LGPL 라이브러리 수정·교체/재링크 검증 | 미완료 |

GitHub Releases에 배포본과 대응 소스 아카이브를 함께 제공하는 방법을 검토할 수 있습니다. 정확한 제공 방식은 라이선스 조항과 배포 형태에 맞게 결정해야 하며, 소스 링크 또는 소스 아카이브 하나만으로 모든 의무가 충족된다고 단정하지 않습니다.
