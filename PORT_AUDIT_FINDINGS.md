# SSG port audit (manual double-check)

**Purpose:** Living log of gaps between `original-src/` and the Scala modules (`ssg-md`, `ssg-liquid`, `ssg-sass`, `ssg-minify`, `ssg-js`). This file is **append-only by convention**: add new dated sections at the bottom under “Append log” so work is not lost to chat compaction.

**Scope note:** The upstream trees are large (on the order of **~1,091** flexmark `src/main/java` files, **~370** dart-sass `lib/**/*.dart` files, **~135** liqp `src/main/java` files, **~26** terser `lib/**/*.js` files, plus Ruby specs). This document **does not** yet list every pairwise diff. It records **verified** structural gaps (whole modules absent), **confirmed** stub/partial markers in the port, and a **template** for agents to extend. Systematic 1:1 file comparison should continue by following `Ported from:` headers in Scala sources and opening the cited original path.

**Convention for new entries (agents):**

```text
### `relative/path/InSsg.scala` (or original-only path if nothing ported)
- **Original:** `original-src/...` (exact path)
- **Status:** missing | stubbed | simplified | partial | wrong API
- **Detail:** one or two precise sentences; cite behavior, thrown exception, or comment line if helpful.
- **Agent / date:** optional
```

---

## Inventory snapshot (counts, approximate)

| Upstream | Rough main-source count (Glob in repo) | SSG module | Scala `src/main/scala` count (Glob) |
|----------|----------------------------------------|------------|-------------------------------------|
| flexmark-java `**/src/main/java/**/*.java` | 1091 | `ssg-md` | 771 |
| dart-sass `lib/**/*.dart` | 370 | `ssg-sass` | 130 |
| liqp `src/main/java/**/*.java` | 135 | `ssg-liquid` | 131 |
| terser `lib/**/*.js` (excluding under `test/`) | 26 | `ssg-js` | 46 |
| jekyll-minifier `lib/**/*.rb` | 2 (+ large inline module in `.rb`) | `ssg-minify` | 13 |

Counts are a **coverage thermometer only**: many Scala files fold multiple originals, and many upstream files are `package-info` or unrelated to the Jekyll-like subset.

---

## A. Original components with **no** corresponding SSG area (or entire subsystem omitted)

These are **not** wired into SSG under an obvious parallel package; treating them as **unported** unless a future audit maps them elsewhere.

### A.1 flexmark-java — **whole Maven modules** absent from `ssg-md`

The following **`flexmark-java/`** artifacts exist under `original-src` but have **no** matching extension or converter tree under `ssg-md` (search: no `docx`, `pdf`, `jira`, `youtrack`, `html2md`, `osgi`, `profile`, `xwiki`, `tree.iteration`, `zzzzzz` packages in `ssg-md` main sources):

| Module | Role (from upstream naming) |
|--------|-----------------------------|
| `flexmark-docx-converter` | Markdown → DOCX |
| `flexmark-pdf-converter` | PDF output |
| `flexmark-jira-converter` | Jira wiki conversion |
| `flexmark-youtrack-converter` | YouTrack wiki conversion |
| `flexmark-html2md-converter` | HTML → Markdown |
| `flexmark-osgi` | OSGi bundle glue |
| `flexmark-profile-pegdown` | Pegdown compatibility profile |
| `flexmark-ext-xwiki-macros` | XWiki macro syntax extension |
| `flexmark-ext-spec-example` | **Extension** for spec-example blocks (test helpers like `SpecExample` *are* ported under `ssg-md/src/test`, but the **extension** itself is not present under `ssg-md/.../ext/`) |
| `flexmark-ext-zzzzzz` | Sample / placeholder extension |
| `flexmark-tree-iteration` | Alternative tree-iteration utilities |
| `flexmark-util-experimental` | Experimental collections / sequence helpers (`KeyedItemFactoryMap`, `PositionList`, …) — **no** `ssg.md...experimental` package |

Test-only upstream modules (`flexmark-core-test`, `flexmark-integration-test`, `flexmark-test-specs`, …) are **not** expected to exist as production Scala; some spec harness pieces are ported under `ssg-md/src/test`.

### A.2 dart-sass — **directories under `lib/src/`** not represented in `ssg-sass`

`ssg-sass` mirrors large parts of `ast`, `parse`, `value`, `visitor`, `extend`, `importer`, `functions`, `logger`, `util`, and `Compile`. The following **upstream subtrees** have **no** parallel top-level package in `ssg-sass` (they are Dart-API, embedded compiler protocol, or CLI):

| Upstream path | Role |
|---------------|------|
| `lib/src/embedded/**` | Embedded compiler / isolate / protobuf protocol (`protofier.dart`, `isolate_dispatcher.dart`, …) |
| `lib/src/js/**` | Dart-to-JS API, legacy JS API, chokidar, parcel watchers, many `legacy/*` value adapters |
| `lib/src/executable/**` | CLI: options, watch, REPL, concurrent execution |
| `lib/src/callable/async.dart` and related async evaluation | Async evaluation path (vs sync visitor port) |
| Many `lib/src/importer/js_to_dart/**`, `legacy_node/**` | Node/JS importer bridges |

**Implication:** behavior that only lives in embedded/JS/executable paths in dart-sass is **out of scope or not implemented** in the current Scala module unless explicitly merged into `Compile.scala` / importers — needs file-by-file confirmation.

### A.3 liqp — **files / packages replaced rather than ported**

| Original path | Status |
|---------------|--------|
| `src/main/antlr4/**` (grammar) + generated parser Java | **Not** present; replaced by hand-written `LiquidLexer.scala` / `LiquidParser.scala` — **semantic parity** must be validated by tests, not line diff. |
| `src/main/java/liqp/parser/v4/NodeVisitor.java` | **Not** ported 1:1; parser builds `LNode` directly. |
| `src/main/java/liqp/antlr/CharStreamWithLocation.java`, `FilterCharStream.java` | **No** Scala counterparts under `ssg/liquid/antlr/` (only `NameResolver`, `LocalFSNameResolver`). Logic may be inlined in the lexer. |
| `src/main/java/liqp/spi/*` (`TypesSupport`, `BasicTypesSupport`, `Java7DateTypesSupport`, `Java8DateTypesSupport`, `SPIHelper`) | **No** Scala files with these types; Jackson `ObjectMapper` configuration was **replaced** (see `LiquidSupport.scala`, `filters/Json.scala`, `where/LiquidWhereImpl.scala` migration notes). **Risk:** subtle JSON / date typing differences vs liqp. |

### A.4 terser — **upstream file with no obvious Scala namesake**

| Original | Notes |
|----------|------|
| `original-src/terser/lib/mozilla-ast.js` | No `mozilla` / Moz AST references in `ssg-js` sources — **likely unported** or folded under another name; requires confirmation. |
| `original-src/terser/lib/cli.js` | CLI entrypoint; **no** `cli` analog in `ssg-js` (expected for a library port). |

### A.5 jekyll-minifier

| Original | Notes |
|----------|------|
| `lib/jekyll-minifier.rb` | **~1.1k lines** of Ruby including `ValidationHelpers`, caching, HTML/CSS/JS/JSON integration with **external gems** (`htmlcompressor`, `cssminify2`, `json/minify`, `terser`). SSG has a **small** facade (`ssg-minify`, ~13 Scala files) reimplementing behavior — **not** a line-by-line Ruby port; parity must be tested. |
| `lib/jekyll-minifier/version.rb` | Version constant only — **omitted** in Scala (normal). |
| `spec/**` | Ruby specs **not** ported as Scala tests. |

---

## B. Ported files with **confirmed** stubs, TODOs, or partial implementation

Entries here are backed by **comments or throws** in-tree (first pass). Expand this list as you open more files.

### `ssg-js` — compressor optimization passes incomplete

- **Status:** **partial** — ScopeAnalysis infinite loop fixed (2026-04-29). Single-pass compression works. Many optimization transforms are incomplete (1,481 of 2,518 compression tests marked `.fail`). The multi-pass convergence loop itself is functional but most individual optimization passes (collapse_vars, reduce_vars, inline, etc.) have gaps.

### `ssg-liquid/src/main/scalajs/ssg/liquid/parser/LiquidSupportPlatform.scala`

- **Status:** **platform stub (by design)** — Scala.js returns empty inspectable map because reflection is unavailable. JVM/Native have real implementation.

### `ssg-md/src/test/scala/ssg/md/test/util/RenderingTestCase.scala`

- **Status:** **test harness simplified** — JUnit `ExpectedException` / comparison behavior called out as stubbed for munit migration.

### `ssg-md/src/test/scala/ssg/md/ext/resizable/image/ResizableImageExtensionSuite.scala`

- **Status:** **tests incomplete** — TODO: spec-based rendering once Flexmark spec renderer fully ported.

---

## C. Architectural deltas (not necessarily “bugs”, but violate 1:1 porting)

- **Liquid:** ANTLR → hand-written lexer/parser — **all** `liqp` grammar-driven behavior must be revalidated.
- **Minify:** Ruby gem orchestration → native Scala minifiers — **integration** differs from jekyll-minifier.
- **JS:** Terser `lib/*.js` reorganized into many `ssg-js` Scala files — map by **feature** (compress, parse, scope), not filename.

---

## D. Append log (newest last)

### 2026-04-18 — Initial seed (automated search + spot checks)

- Established module-level **unported** lists for flexmark converters/extensions, dart-sass embedded/js/executable trees, liqp SPI/antlr/NodeVisitor, terser `mozilla-ast.js`, jekyll-minifier monolithic Ruby.
- Confirmed **high-impact partial ports**: `EvaluateVisitor`, `StylesheetParser` stage-1 notes, several md/sass TODO/stub comments.
- **Follow-up:** For each Scala file under `ssg-*/src/main/scala`, if there is a `Ported from:` header, open that original and diff methods/branches; if there is **no** header, either find the conceptual upstream mapping or mark as **SSG-native** to avoid false positives.

### 2026-04-28 — Re-audit sweep + production fixes

- **Resolved B-series findings (removed):**
  - EvaluateVisitor cssStub — all 9 CSS visitor methods fully implemented (5,507 LOC)
  - StylesheetParser Stage 1 limitations — all parsing gaps filled, stale comment removed
  - SelectorList asSassList — fully implemented, stale header updated
  - ColorFunctions stub — was mischaracterized; it's a runtime error handler, not a porting stub
  - Importer stub — full implementation with PackageImporter + NodePackageImporter
  - EmojiNodeFormatter TODO — fixed: now uses `appendNonTranslating`
  - Escaping.scala partial — fixed: all 4 BasedSequence overloads + 3 Replacer branches ported
  - EnumeratedReferenceNodeRenderer — was already fully ported; finding was stale
- **Enforcement scans** (all 5 modules): zero shortcut markers, zero stale stubs
- **Remaining production gaps:** 1 (ssg-js multi-pass compressor, ISS-031/032)
- **Test coverage gaps identified:** ssg-md ~40%, ssg-liquid ~59%, ssg-js ~4%, ssg-minify OK

### 2026-04-29 — Test porting + ScopeAnalysis fix

- **ScopeAnalysis.figureOutScope infinite loop fixed:** Root cause was
  `ScopeContext.scope` initialized to `ast` instead of `null`. The toplevel's
  `parentScope` pointed to itself, creating a cycle in `findVariable`. Fixed
  with `rootInitialized` flag. This unblocks all compression tests with
  undeclared references.
- **Test porting completed — 9,391 tests total (was 2,283):**
  - ssg-md: 5,889 tests (+4,214) — flexmark-util (721), core unit (210),
    core spec (1,398), extensions (244), formatter/translation (1,593), merge (57)
  - ssg-liquid: 863 tests (+484) — parser/lexer (139), blocks (107),
    filters (78), tags (44), nodes (26), root (54), others (36)
  - ssg-js: 2,518 tests (+2,402) — mocha (169), compression (2,078),
    parser errors (29), scope analysis (5), upgraded existing (121)
  - ssg-minify: 121 tests (+8) — malformed input (6), XML (2)
- **Auditor verified:** 0 empty test bodies, 0 missing test files (within
  portability constraints), all test counts match originals
- **Still unportable** (blocked by missing dependencies):
  - ComboEmojiJiraTest (JiraConverterExtension)
  - ComboFormatterIssueSpecTest, ComboSpecExampleFormatterSpecTest (SpecExampleExtension)
  - MergeFormatterCoreTest (SpecExampleExtension)
  - FullOrigSpec tests (FlexmarkSpecExampleRenderer)
  - HtmlBuilderTest, HtmlHelpersTest (java.awt)
  - Terser CLI tests (53), spidermonkey tests (11)

*(Add new dated subsections below.)*
