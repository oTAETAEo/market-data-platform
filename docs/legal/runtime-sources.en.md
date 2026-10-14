# Runtime Source References

[한국어](runtime-sources.ko.md) | [Third-party notices](third-party-notices.en.md)

> This is a development index of upstream source references, not a written source offer or a declaration of compliance. Hosting corresponding-source archives alongside releases in this repository has not been completed.

## Bundle Baseline

- Review date: 2026-10-14
- Project baseline commit: `c157dc888377ccf4e1858fe09f18ed93c9b21c70`
- Platform: macOS arm64 development bundle
- Build JDK: BellSoft Liberica 21.0.9+11-LTS
- Bundled runtime `release` file: `JAVA_VERSION="21.0.9"`
- JavaFX: Gradle dependency 21.0.10, upstream build 21.0.10+2
- Skiko: macOS 0.8.4; an additional Linux runtime JAR 0.7.63 was observed

This does not establish verification of Windows or Linux distributions. JDK vendor and version can vary with the build environment. Additional artifacts, including Skiko 0.7.63, require a final distribution-scope review.

## Source References

| Component | Baseline source | Review requirements |
| --- | --- | --- |
| Liberica JDK 21.0.9+11 | [Vendor source archive](https://github.com/bell-sw/Liberica/releases/download/21.0.9%2B11/bellsoft-jdk21.0.9%2B11-src.tar.gz), [vendor terms](https://bell-sw.com/liberica_eula/) | Correspondence to the actual binary, build/patch materials and module-specific notices |
| JavaFX 21.0.10+2 | [Pinned source commit](https://github.com/openjdk/jfx21u/tree/46ca2bddc12d85d98f306ef0ccfcb1f478881fb2), [preserved LICENSE](../../licenses/javafx/LICENSE) | Verify correspondence between Maven binaries and source, patches and build materials |
| WebKit 622.1 | [JavaFX native source](https://github.com/openjdk/jfx21u/tree/46ca2bddc12d85d98f306ef0ccfcb1f478881fb2/modules/javafx.web/src/main/native), [WebKit notices and LGPL terms](https://github.com/openjdk/jfx21u/blob/46ca2bddc12d85d98f306ef0ccfcb1f478881fb2/modules/javafx.web/src/main/legal/webkit.md) | JavaFX modifications, LGPL and separate incorporated-component terms |
| GLib 2.82.4 | [JavaFX-incorporated source](https://github.com/openjdk/jfx21u/tree/46ca2bddc12d85d98f306ef0ccfcb1f478881fb2/modules/javafx.media/src/main/native/gstreamer/3rd_party/glib) | LGPL notices and corresponding library source for JavaFX media |
| GStreamer 1.24.10 | [JavaFX-incorporated source](https://github.com/openjdk/jfx21u/tree/46ca2bddc12d85d98f306ef0ccfcb1f478881fb2/modules/javafx.media/src/main/native/gstreamer/gstreamer-lite) | LGPL notices and corresponding source for the modified gstreamer-lite |
| Skiko 0.8.4 | [Pinned commit](https://github.com/JetBrains/skiko/tree/57dea506d176724c82db1606edf0123ab60904f4) | Apache-2.0, build dependencies and native notices |
| Skia `m116-47d3027-1` | [Pinned source](https://github.com/JetBrains/skia/tree/47d3027b8cbeea76135c0995d5886aa2e58e7512), [DEPS](https://github.com/JetBrains/skia/blob/47d3027b8cbeea76135c0995d5886aa2e58e7512/DEPS) | BSD-3-Clause and terms of each dependency actually compiled into the build |
| Additional Skiko 0.7.63 | [Pinned commit](https://github.com/JetBrains/skiko/tree/328d5e565f1968a3bcb071efe18841916cf5a033) | Its separate native dependency inventory and whether it needs to ship |

These links support provenance tracking. Their presence does not establish exact corresponding-source fulfillment, long-term availability or provision of required build materials. Nor does every listed component have identical source obligations: distinguish GPL/LGPL requirements from Apache/BSD notice requirements.

## Observations and Remaining Work

The current `.app` contains these paths:

```text
MarketDataPlatform.app/Contents/runtime/Contents/Home/legal/
MarketDataPlatform.app/Contents/app/resources/javafx/
MarketDataPlatform.app/Contents/app/libskiko-macos-arm64.dylib
```

The JDK's `legal/` directory contains `LICENSE`, `ADDITIONAL_LICENSE_INFO`, `ASSEMBLY_EXCEPTION` and third-party notices such as FreeType. Preserve the actual vendor distribution's materials rather than replacing them with a single JavaFX LICENSE.

Sufficient license/notice entries were not identified in the inspected JavaFX and Skiko JARs. No corresponding-source archive or compliant written source offer was identified in the bundle. Provision through other release materials remains unverified.

The Classpath exception permits licensing independent application modules; it does not remove obligations for redistributing the JDK or JavaFX. Separately licensed LGPL components require review of corresponding library source, notices and users' modification, debugging and replacement/relinking rights. Compliance of the application's signing, native-library extraction and loading mechanism has not been verified.

## Per-Release Records

For a release, record the actual distributed artifacts, not just upstream homepages.

| Record | Current status |
| --- | --- |
| App release tag and platform-specific binary inventory | Formal release baseline not finalized |
| Runtime vendor, exact version and source commit | Development baseline only, as above |
| SHA-256 of distributed binaries and corresponding-source archives | Not prepared |
| Applied patches, modification notices and required build scripts | Correspondence not fully verified |
| Location of component-specific licenses and NOTICE material | JDK legal materials observed; complete collection pending |
| Source provision method and actual download location | Upstream references only; release source provision pending |
| Validity period and operational responsibility if a written-offer method is chosen | No method selected; this document does not promise provision |
| LGPL modification and replacement/relinking verification | Pending |

Providing binaries and corresponding-source archives together through GitHub Releases is one option to evaluate. Select a method consistent with each license and distribution form; a link or an archive alone does not necessarily fulfill every obligation.
