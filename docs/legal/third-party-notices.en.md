# Third-Party Software Notices

[한국어](third-party-notices.ko.md) | [Notice index](../../THIRD_PARTY_NOTICES.md)

## Review Scope and Status

Reviewed on 2026-10-14 against Gradle configuration, local Python package metadata, the external TradingAgents checkout and a macOS arm64 development bundle. Versions are observations at that baseline, not guarantees for other platforms or future releases.

This is a **partial notice inventory** of major components. A complete transitive/native SBOM has not been prepared, and this document does not certify commercial-release compliance. The application currently has neither a notice viewer for this inventory nor automation to include these texts in installers.

The license for the project's own code has not yet been selected. Third-party licenses are not arbitrarily applied to the entire project. Publishing a repository and granting an open-source license are separate actions. [GitHub guidance](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository)

## Major JVM and UI Components

| Component | Observed version | License and source | Current use |
| --- | --- | --- | --- |
| Lightweight Charts | 5.2.1 | [Apache-2.0 and attribution guidance](https://github.com/tradingview/lightweight-charts/tree/v5.2.1#license) | WebView loads a pinned version from a CDN |
| Compose Multiplatform | 1.6.11 | [Apache-2.0](https://github.com/JetBrains/compose-multiplatform/blob/v1.6.11/LICENSE.txt) | Desktop UI |
| Kotlin | Plugin 1.9.24 | [Apache-2.0](https://github.com/JetBrains/kotlin/blob/v1.9.24/license/LICENSE.txt) | Build and Kotlin runtime; not a statement that every runtime JAR has this version |
| Compose Richtext | 0.17.0 | [Apache-2.0](https://github.com/halilozercan/compose-richtext/blob/v0.17.0/LICENSE) | Markdown report rendering |
| Spring Boot / Spring Framework | 3.5.10 / 6.2.15 | [Boot](https://github.com/spring-projects/spring-boot/blob/v3.5.10/LICENSE.txt), [Framework](https://github.com/spring-projects/spring-framework/blob/v6.2.15/LICENSE.txt): Apache-2.0 | Runtime dependencies used by collectors and other modules |
| Jackson | 2.19.4 | [Apache-2.0](https://github.com/FasterXML/jackson-databind/blob/jackson-databind-2.19.4/LICENSE) | JSON processing |
| Logback | 1.5.25 | [EPL-2.0 or LGPL-2.1](https://logback.qos.ch/license.html) | Logging; applicable license and release obligations need separate documentation |
| Skiko | macOS 0.8.4; an additional Linux runtime JAR 0.7.63 was observed | [Apache-2.0](https://github.com/JetBrains/skiko/blob/57dea506d176724c82db1606edf0123ab60904f4/LICENSE) | Native rendering; confirm the final set of shipped artifacts |
| Skia and native dependencies | `m116-47d3027-1` for Skiko 0.8.4 | [Skia BSD-3-Clause](https://github.com/JetBrains/skia/blob/47d3027b8cbeea76135c0995d5886aa2e58e7512/LICENSE); dependencies have separate terms | Complete inventory, including libjpeg-turbo, libpng and HarfBuzz, remains unverified |
| Liberica/OpenJDK | Build JDK 21.0.9+11-LTS; bundled `release` identifies 21.0.9 | GPL-2.0 with the applicable Classpath exception and additional native terms | A reduced JDK runtime is bundled |
| JavaFX | 21.0.10, upstream build 21.0.10+2 | [GPL-2.0 with the applicable Classpath exception](../../licenses/javafx/LICENSE); dependencies have separate terms | JavaFX JARs and WebView native libraries are bundled |

Kafka, Reactor, Netty, CommonMark, OkHttp and other transitive dependencies also need individual entries in the final release inventory. Absence from this table does not mean absence from the distribution or absence of obligations. Record final versions from resolved Gradle dependencies and the actual installer.

## TradingView Attribution

The original NOTICE is preserved in the [local copy](../../licenses/lightweight-charts/NOTICE).

```text
TradingView Lightweight Charts™
Copyright (с) 2025 TradingView, Inc. https://www.tradingview.com/
```

The application uses Lightweight Charts, not the TradingView web service or Advanced Charts. The chart currently includes a `Charts by TradingView` link, but the official attribution notice must also be provided in a user-accessible location before release. Posting this document on GitHub does not complete the application's attribution requirements. Follow the [v5.2.1 guidance](https://github.com/tradingview/lightweight-charts/tree/v5.2.1#license) for the link and notice requirements.

The library also identifies portions of tslib under BSD-0-Clause. Apache notices do not replace other components' licenses. Do not use TradingView or other project names or trademarks to imply sponsorship or endorsement of this application.

## Python Components

TradingAgents and its Python environment are currently referenced through external local paths, not bundled in the installer. The following observations are from the development environment, not a complete distribution lockfile. See [Python distribution dependencies](python-distribution.en.md) for the boundary and open tasks.

| Component | Development version | Observed license / source |
| --- | --- | --- |
| TradingAgents | 0.3.1, commit `a33fd4c0f134485a43553a2c23a63cb14adbd88f` | [Apache-2.0 text](../../licenses/tradingagents/LICENSE) |
| backtrader | 1.9.78.123 | [GPLv3+ package metadata](https://pypi.org/project/backtrader/1.9.78.123/), [GPLv3 text](../../licenses/backtrader/LICENSE) |
| stockstats | 0.6.9 | [BSD-3-Clause](https://github.com/jealous/stockstats/blob/master/LICENSE.txt) |
| pandas | 3.0.6 | Development package metadata: BSD-3-Clause; inspect incorporated third-party notices separately |
| yfinance | 1.7.0 | [Apache-2.0 and Yahoo data-rights guidance](https://github.com/ranaroussi/yfinance#readme) |
| langchain-core / langgraph | 1.6.6 / 1.2.12 | Development package metadata: MIT |
| certifi | 2026.7.22 | Development package metadata: MPL-2.0 |
| orjson | 3.12.0 | Development package metadata: `MPL-2.0 AND (Apache-2.0 OR MIT)` |

Metadata alone does not establish the license of every incorporated source file. Review the Python binary, native libraries inside wheels and original terms of every package actually distributed before release.

## Reading the Conditions

- For Apache-2.0 components, retain the license copy and applicable copyright/NOTICE material, and mark changes to modified third-party files. These terms generally do not require publishing all source of an independent application absent a separate agreement. [Original terms](../../licenses/Apache-2.0.txt)
- The Classpath exception permits independent application modules to use different licenses for designated code. It does not remove GPL obligations for redistributing the JDK/JavaFX itself or separate terms for libraries such as WebKit.
- GPL, LGPL, EPL and MPL are not interchangeable. Examine source availability and user-rights requirements based on the component and how it is combined, modified and distributed. GPL does not prohibit charging for copies.
- Summaries do not replace original license terms. This document is not a new agreement or a written source offer.

## Separate Rights and Open Tasks

Open-source permission does not grant commercial-use or resale rights for exchange market data, Yahoo data, news/social data or LLM services. Their terms, API costs and regulations applicable to paid investment-analysis services require separate review; this document does not approve those uses.

- [x] Document the major components' origin and review scope
- [x] Preserve selected original licenses and the Lightweight Charts NOTICE
- [ ] Select and apply the project's own license
- [ ] Finalize transitive/native license inventory for every shipped artifact
- [ ] Collect the complete component-specific notices for JavaFX, Skiko/Skia and other dependencies
- [ ] Automate license inclusion in installers and user-accessible notices
- [ ] Verify corresponding runtime source provision and GPL/LGPL compliance
- [ ] Lock Python distribution dependencies and validate exclusions

See [original texts](../../licenses/README.md) and [runtime sources](runtime-sources.en.md).
