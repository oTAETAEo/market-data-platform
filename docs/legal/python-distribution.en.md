# Python Distribution Dependencies

[한국어](python-distribution.ko.md) | [Third-party notices](third-party-notices.en.md)

## Current Distribution Boundary

Reviewed on 2026-10-14. The current Compose distribution targets the JVM application and JDK/JavaFX runtime. The TradingAgents checkout and Python virtual environment are not included in the `.app`; they run from external local paths.

The worker script is included as a JVM resource. Importing external TradingAgents through that script is distinct from bundling TradingAgents source or a Python environment in the installer. Running a component in a separate process does not exempt it from license review.

| Item | Current status | Evidence |
| --- | --- | --- |
| Python executable | External virtual environment or `MARKET_DATA_PYTHON` path | [DesktopCompositionRoot](../../apps/desktop/src/main/kotlin/com/marketdata/desktop/application/DesktopCompositionRoot.kt) |
| TradingAgents source | External checkout or `TRADINGAGENTS_REPO` path | [DesktopCompositionRoot](../../apps/desktop/src/main/kotlin/com/marketdata/desktop/application/DesktopCompositionRoot.kt) |
| Worker script | Included as a JVM resource and executed from a temporary file | [LocalTradingAgentsAdapter](../../modules/ai-adapter/src/main/java/com/marketdata/ai/LocalTradingAgentsAdapter.java) |
| Analysis data tools | pandas/stockstats process the application's finalized-candle snapshot | [tradingagents_worker.py](../../modules/ai-adapter/src/main/resources/tradingagents_worker.py) |
| Installation environment | Editable install of external TradingAgents | [Source-run instructions](../../README.md) |

## Reviewed TradingAgents Revision

- Project version: 0.3.1
- Commit: `a33fd4c0f134485a43553a2c23a63cb14adbd88f`
- Project license: [Apache-2.0 text](../../licenses/tradingagents/LICENSE)
- Declared dependencies: [pinned pyproject.toml](https://github.com/TauricResearch/TradingAgents/blob/a33fd4c0f134485a43553a2c23a63cb14adbd88f/pyproject.toml)

This revision declares many `>=` dependencies. A fresh installation may resolve different packages or versions than those reviewed. The development version inventory is not a distribution lockfile and does not authorize shipping a copied development virtual environment without review.

Major observed versions and licenses are recorded in [third-party notices](third-party-notices.en.md). Complete transitive-package and native-wheel review is still pending.

## Backtrader Findings

TradingAgents declares `backtrader>=1.9.78.123` as a regular dependency, and development package 1.9.78.123 is installed. Its [package license](https://pypi.org/project/backtrader/1.9.78.123/) is GPLv3+.

No backtrader usage reference was found in the reviewed TradingAgents source or application worker. This is not an execution test proving the absence of dynamic use in a release environment. Installation alone also does not establish that the entire application is GPL-covered.

This documentation does not remove the package or alter upstream dependency declarations. To exclude it from a future release, identify the required features, manage declarations and installation so it is not added back, and verify the analysis path. If it ships, review GPL obligations and the actual scope of combination. Requiring users to install it separately is not treated as an automatic exemption either.

## Data and Service Terms

The current worker connects the selected instrument's finalized-candle snapshot to data/indicator tools and excludes news, social and company-fundamental analysts. This is not a guarantee that every future Yahoo or other provider request is blocked.

The yfinance code is Apache-2.0, but its [official guidance](https://github.com/ranaroussi/yfinance#readme) separately flags Yahoo data terms and personal-use limits. Rights and costs for exchange data, news and LLM services are also distinct from Python package licenses.

## Distribution Checklist

- [x] Record the current external Python/TradingAgents execution boundary
- [x] Record the TradingAgents commit and major development-package licenses
- [x] Record backtrader GPL and yfinance data-rights cautions
- [ ] Decide whether to bundle Python and how each platform receives it
- [ ] Finalize direct/transitive packages based on required features
- [ ] Pin Python version, upstream commit, complete dependency versions and artifact hashes
- [ ] Verify installation, initialization and analysis in a separate clean environment
- [ ] Verify excluded packages such as backtrader are not reinstalled or used
- [ ] Collect original notices for actual wheels, native libraries and the Python runtime distributed
- [ ] Document applicable source and redistribution conditions for GPL/LGPL/MPL and other incorporated components
- [ ] Include the release-specific package inventory and notices in the installer

Update this document and the final release inventory when adopting bundled Python. The application license, TradingAgents license, individual package licenses and data-service terms do not replace one another.
