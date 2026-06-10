# SSG Codebase Review

**Date:** 2026-06-10
**Method:** Seven parallel module review agents; each verified the 2026-06-09 review's findings against current source, then covered fresh ground — including upstream comparison for KaTeX and Mermaid, whose submodules were empty yesterday and are now initialized (katex = v0.16.45 exactly, mermaid = v11.0.0 exactly; the odd `git describe` strings are just old reachable tags).
**Scope:** All modules, build/CI, cross-cutting API, databases.
**Tests run during review:** ssg-liquid JVM 867/867, ssg-minify JVM 121/121, ssg-mermaid JVM 543/543, ssg-graphviz JVM 347/347, ssg-js JVM 2500/2522 reported green (but see ssg-js section).

---

## Executive Summary

The library ports are individually strong at the core-algorithm level, but the project fails the user-facing test in three systemic ways:

1. **The static site generator does not exist.** The `ssg/` aggregator is two files: a `Version` constant and a 4-line adapter. There is no pipeline (md → liquid → sass → minify), no `_config.yml` loading, no layouts/permalinks/collections, no main entry point, no integration test, and no bridge between flexmark's front-matter AST and liquid's `DataView`. Nothing proves the 12 modules compose.

2. **Public APIs silently discard options across nearly every module.** ssg-sass drops 5 of `compileString`'s parameters; ssg-js's `compress = true` *disables* compression; ssg-mermaid ignores ~12 documented config fields plus `%%{init:}%%` and frontmatter config entirely; ssg-minify's `jsCompressorOpts` is unfillable. The pattern is identical everywhere: the API mirrors the upstream surface, the wiring was never finished, and no test passes a non-default option — so nothing caught it.

3. **The green dashboards overstate reality.** ssg-js is "2500 passed, 0 failed" — but 1507 of 2522 tests (60%) are `.fail`-pinned expected failures; true conformance is ~40%. Covenant headers say `full-port` on files whose own headers document 23% coverage. 5 of 7 open issues are already fixed; 11+ of 19 ssg-md `major_issues` audit rows describe fixed code while the two real ssg-md gaps are in no database. The CI enforce gate is `continue-on-error`. Codecov uploads from a stale Scala-version path and silently fails every run.

**Per-platform reality check:** "All 3 platforms are baseline" does not currently hold. `FileOps` throws `UnsupportedOperationException` on Native and JS (ssg-commons), so Liquid `{% include_relative %}` / file-based templates crash on 2 of 3 platforms; ssg-md's Scala.js resource loading only works inside this repo's build tree (HTML entity decoding throws downstream); the sass-spec 99.73% proof is JVM-only; the ssg-js compressor test suites are JVM-only.

---

## 1. Things intended to be ported but not ported

| Module | Gap | Evidence |
|---|---|---|
| ssg-md | **`FileUriContentResolver` not ported** though migration DB says "ported"; `IncludeNodePostProcessor.scala:60-67` lacks Java's empty-list default-factory fallback → Jekyll `{% include file.md %}` can't resolve filesystem paths, and `HtmlRenderer.scala:369` (`contentResolverFactory` throws) closes the manual-registration door too | CONFIRMED from prior |
| ssg-md | **27 more migration rows marked "ported" with no Scala code** (TableTextCollectingVisitor — genuinely useful; FlatDependency* family; FileUtil; ui/Color+Html* family; 9 Jira/YouTrack renderers). Most are defensible skips but the DB says "ported" | NEW |
| ssg-sass | **Custom functions feature 100% dead**: building blocks exist (`Callable.function`, `Environment.setFunction`) but `compileString(functions=...)` drops the list before the evaluator; no other injection point. dart-sass's one registration loop is the missing port | NEW (extends prior) |
| ssg-sass | **`loadPaths` dead even via ImportCache**: `ImportCache.scala:446` defaults `loadPathImporter = _ => Importer.noOp`; `SASS_PATH` support equally dead | NEW |
| ssg-sass | `StylesheetGraph` lacks `modifiedSince`/`reload`/`remove` — and is **dead code** (zero main-source references; `upstream`/`downstream` never populated) | CONFIRMED + worse |
| ssg-liquid | **Unquoted dotted/slashed include names don't parse**: no `PathSep` token, no `IdChain`; `{% include footer.html %}` throws "Expected TAG_END but got DOT" — in the *default* Jekyll flavor | NEW, high |
| ssg-liquid | `where_exp` can't see variables outside the loop var (`Where_Exp.scala:66-68`) — breaks real Jekyll usage; variable-syntax includes unsupported; `withStripSpaceAroundTags` strips trailing only; liqp's invalid-tag detection state machine unported. All `.fail`-documented, none issue-tracked | NEW |
| ssg-js | `minify.js` orchestration ~23%: no sourceMap orchestration, nameCache, `set_shorthand` propagation, multi-file input, wrap/enclose, timings; compressor never consults `ie8`; `OutputOptions` lacks `ie8`/`safari10`; `computeCharFrequency` has zero callers | CONFIRMED + extended |
| ssg-js | `collapse_vars` claim from prior review **WAS WRONG** — it exists (`TightenBody.scala:1281+`), but 109/183 of its fixture tests are expected-fail | CORRECTED |
| ssg-mermaid | **`%%{init:}%%` directives stripped but never applied** (`DetectType.extractDirectiveBody` zero callers); **YAML frontmatter never parsed** (`removeFrontMatter` zero callers); `Accessibility.scala` zero callers (accTitle/accDescr parsed, never emitted); state-diagram `--` concurrency dividers unsupported; sequence `links`/`properties` silently skipped; gantt `tickInterval`/`until` missing | NEW |
| graphs-commons | **Dagre Brandes-Köpf positioning replaced by a simplified rewrite** (`Position.scala:50-112` vs dagre's ~450-line bk.js); nesting-graph runs *after* rank with `minlen = 0` edges (upstream runs before rank to constrain it) — cluster layout fidelity is provably not dagre's. Violates the project's own "porting is binary" rule | NEW, major |
| ssg-graphviz | **Clusters parsed but never drawn** (DotRenderer has zero cluster handling — renders stray nodes labeled `cluster_0`); HTML-like labels lexed then escaped as literal text; ports parsed, unused; `rank=same` unsupported. Documented only inside a test file header | NEW |
| ssg-commons | **Native `FileOpsPlatform` throws for every operation** ("TODO: Could use POSIX APIs"); Scala Native supports `java.nio.file.Files`, so this is unported-by-choice, with a `full-port` covenant stamp on the file | CONFIRMED + blast radius below |
| ssg-highlight | Native binary embeds 11 grammars absent from `LanguageRegistry` (incl. a suspicious `test` grammar) — never highlightable | NEW |
| ssg-katex | Coverage is otherwise complete (all 45 upstream functions, both environments, 339/337 macros) — the module's real issue is the stale `AnyRef /* Parser */` placeholders forcing 414 `asInstanceOf` casts across 55 files | NEW (positive + debt) |

## 2. Bugs and potential issues

**Highest-confidence functional bugs:**

- **ssg-mermaid: standard frontmatter breaks rendering.** `Mermaid.render` passes raw input through; `FlowchartParser.cleanInput`'s doc claims it strips front matter but the code doesn't; result: any diagram with `---\ntitle: …\n---` (valid Mermaid since v10.5) throws `ParseException`. No frontmatter test exists. (NEW, major)
- **ssg-js `DropUnused` Pass 3** uses `walk` with a *false* "transform is not yet implemented" comment — `Pass3Transformer._visit` discards every replacement node, so unused-assignment elision, unused defun/class removal, and whole-`var` removal are all no-ops. Matches 118/146 expected-fail in its suite. (CONFIRMED, worse than prior)
- **ssg-js stringly-typed option bugs:** `Inline.scala:546` calls `optionBool("inline")` on an `Int`-valued option → IIFE-return inlining can never fire; `pure_getters` defaults to the String `"strict"` → `optionBool` returns false where JS truthiness is true → behavior inverted under defaults; `sequences` limit dead (Boolean short-circuit); `OutputOptions.shorthand` hardcoded `true` (can emit ES6 shorthand in ES5 output); regex `keep_fnames` unreachable. (NEW, high)
- **ssg-katex: `Macros.registerAll()` runs on every render with no idempotence guard** (`KaTeX.scala:44-48`; unlike Functions/Environments) — ~340 re-definitions per call, and user macros registered via `__defineMacro` that shadow builtins are silently clobbered on the next render. (NEW, high)
- **ssg-highlight `HtmlHighlightRenderer.scala:15-30`:** nested captures silently dropped; partial-overlap branch emits a stray `</span>`, opens a never-closed `<span>`, and duplicates the overlapped text — unbalanced HTML whenever captures overlap. Also: JVM/Native use UTF-8 byte offsets, JS uses UTF-16 code-unit indices → non-ASCII sources mis-slice on JS. (CONFIRMED + NEW)
- **ssg-md on Scala.js: resource loading is repo-relative** (`PlatformResourcesImpl.scala:21-33` hardcodes `ssg-md/target/js-3/classes` etc.). Outside this repo: `Html5Entities` throws `IllegalStateException` on first entity (core markdown broken), emoji silently render as literal text, admonition CSS silently empty. (NEW, critical for JS consumers)
- **ssg-minify `Minifier.scala:81`: exclude matching is substring `contains`, not glob** (Ruby uses `File.fnmatch`) — `*.css` patterns never match, `js` over-matches; pre-minified `.min.js`/`.min.css` are re-minified instead of copied through (Ruby passes them through). (NEW, high)
- **ssg-liquid `{% endraw -%}` silently swallows the rest of the template** into the raw body (lexer only accepts literal `%}` after endraw; parser then consumes to EOF). Silent corruption, no test coverage. (NEW)
- **ssg-sass `CompileFile.compile` drops the file URL** (error spans/loadedUrls lose the entry path) **and ignores the file extension** — `.sass`/`.css` parsed as SCSS (dart-sass infers syntax from extension). Silenced deprecations still leak into `CompileResult.warnings`. (NEW)
- **ssg-commons Native `FilePathPlatform.normalize`** turns `"/a/../b"` into relative `"b"` (drops the leading slash); `toAbsolute` fakes absolutization; `cwd` returns `"."`. Zero tests. (NEW)
- **ssg-commons `ssg.commons.Nullable` is a dead, buggy duplicate**: `isNone` treats `NestedNone(1)` (= `Some(None)`) as empty — internally inconsistent; imported nowhere (codebase uses lls `lowlevel.Nullable`). Should be deleted. (NEW)
- **CI:** codecov uploads `target/scala-3.8.3/...` but the build is 3.8.4 → silently never uploads; `release.yml` triggers `ci-release` (→ `publishSigned`) on `pull_request`. (NEW)
- **sass-spec residual failures include 2 uncaught-exception outcomes** (libsass-todo issues 221262/221292) — potential crashes on hostile input — plus user-visible clusters in nested `@media` merging, `@use ... with`, and `@extend` edge cases. (NEW)

**Convention violations found:** ~366 `return` statements in ssg-sass main sources; raw `null` in `ssg.js.Terser` public API (`SourceMapData | Null`) and `Callable.scala:175`; `ssg.liquid.Insertions.get` returns `null`; `return` in highlight's JS platform impl.

## 3. API problems that make implemented features unusable

- **ssg-sass `Compile.compileString`:** `importers`, `loadPaths`, `functions`, `quietDeps` accepted and dropped; `charset` accepted, serializer supports it, call site omits it. The machinery behind `quietDeps` exists and is one constructor parameter away. Note: `logger`, `verbose`, `silenceDeprecations`, `fatalDeprecations`, `futureDeprecations`, and singular `importer` **are** wired (prior review's framing corrected).
- **ssg-sass `Compile.compile(path, …13 options)` throws on every platform including JVM**; its docstring falsely claims a JVM override. The real file API (`CompileFile`) exposes 2 options.
- **ssg-js property mangling is unreachable:** ~700 LOC of PropMangler implemented; `ManglerOptions` has no `properties` field and `Terser.minify` never calls it.
- **ssg-minify `jsCompressorOpts` is unfillable:** `JsCompressorOptions` is an empty trait with zero implementations; the 2-arg `compress(input, options)` *defaults to discarding options*; inline `<script>` compression calls the 1-arg form anyway. Ruby's `terser_args` has no working equivalent end-to-end.
- **ssg-mermaid:** the only config channel is programmatic — markdown authors' `%%{init:}%%`/frontmatter config is ignored or fatal; flowchart `click`/`href` parsed and stored, but no renderer ever emits `<a>`/`xlink:href`; `maxEdges` hardcoded 500 ignoring config.
- **FileOps blast radius:** ssg-liquid's cross-platform `IncludeRelative.scala:46`, `LocalFSNameResolver.scala:43,50`, `TemplateParser.scala:73` call `FileOps.readString` unguarded → throw at render time on Native and JS. `FileOps.isSupported` exists; nothing consults it.
- **ssg-highlight:** `supportsLanguage` (static registry) and `supportedLanguages` (platform runtime) disagree in both directions; JVM lib-load failure surfaces as `ExceptionInInitializerError` with the cause buried.
- **Three incompatible `Nullable` opaque types** (lowlevel/lls, ssg.commons, ssg.liquid) — values can't cross module boundaries without unwrap/rewrap. ssg-commons exists to host exactly this type.

## 4. Missing tests

- **Zero tests pass any non-default compile option** in ssg-sass (grep: no test uses `functions=`, `quietDeps=`, `charset=`, `importers=`, `loadPaths=`) — precisely why five silent no-ops survived. Same disease in mermaid (no "config field X changes output Y" test) and minify (no `exclude` test → glob bug undetected).
- **ssg-js: 3 test files never compile** — `src/test/scala-jvm/` (wrong name; convention is `scalajvm`), so the fixture-runner harness is dead code. 6 suites permanently `assume(false)`-skip compression citing issues marked *resolved*; the compressor suites are JVM-only (JS/Native run only the mocha ports); `Terser.minify` with compression is exercised by essentially one test.
- **Zero test files:** `ssg` aggregator (and CI's `compileOnly` classification means tests added there would never run), `ssg-commons` (9 files used by everything), `ssg-graphs-commons` (60+ layout files, only indirect coverage; dagre invariants untested).
- **ssg-liquid:** STRICT error mode never tested in its throwing configuration; raw-block + whitespace-control combinations untested.
- **ssg-katex:** upstream `unicode-spec.ts` (29 tests) entirely unported — exactly where the port deviates most (hand-rewritten re2-safe regex ranges); ~29 katex-spec cases net missing; SvgGeometry/Stretchy/Delimiter/FontMetrics have no direct tests.
- **ssg-highlight:** 73 smoke tests assert only "contains `<span class="hl-`"; zero tests for the renderer (escaping, balance, nesting — would have caught the renderer bugs), non-ASCII sources, or error paths.
- **ssg-mermaid:** all assertions are substring/smoke; no geometry or structural SVG assertions, no reference comparison vs real mermaid output (ssg-graphviz's `ReferenceComparisonSuite` vs real `dot` is the model to copy).
- **ssg-md:** `ComboTableManipulationSpecTest` and `ComboFormatterIssueSpecTest` not ported though the features are; the include-resolver path broken by the FileUriContentResolver gap is exactly the path with no test.

## 5. Missing proofs the architecture is sound / ready

- **No end-to-end proof at all.** No integration test builds even a single page from markdown + layout + sass + minify. No example site, no golden fixture. The front-matter-AST→DataView bridge needed for the central composition does not exist.
- **Cross-platform proofs are JVM-only where it matters:** sass-spec runner is a JVM suite; ssg-js compress suites JVM-only; no cross-platform consistency test (same input → same output on 3 platforms) anywhere — one such test would expose the highlight UTF-16/UTF-8 divergence immediately. (Positive: CI genuinely *runs* JS and Native tests for published modules — JVM×6 OSes, Native×5.)
- **Concurrency unsound by construction in ssg-sass:** `EvaluationContext`/`CurrentEnvironment` are shared mutable statics with "single-threaded" comments; two parallel `compileString` calls (the obvious SSG build pattern) corrupt each other. Undocumented at the API boundary, unguarded, untested.
- **Enforcement is decorative:** 984 pass / 99 fail covenant verify, byte-identical to yesterday; CI enforce job is `continue-on-error` at both job and step level. Covenant `full-port` stamps coexist with self-documented gaps (Terser.scala header). One genuine method regression flagged: `SelectorFunctions.runExtendPipeline` removed since baseline.
- **Databases can't be trusted in either direction:** issues DB — 5 of 7 open issues already fixed (ISS-935/936/937/938/940); audit DB — 1108 entries vs 1455 current main files (≥347 unaudited, incl. whole modules), stale major rows (Template max-size, TightenBody collapse_vars, OutputStream mangledName all fixed), while real gaps (FileUriContentResolver, JS resources, liquid `.fail` divergences, the 1507 ssg-js pins, 37 sass-spec failures) appear in no database. The sanctioned query path for the sass baseline is broken (`re-scale db sass-spec-baseline stats` crashes on a malformed header).
- **Release readiness:** never tagged/released (only `backup/*` tags); MiMa disabled; snapshots publish on every master push and (bug) on PRs.
- **Dagre cannot even be audited** under project rules: dagre-js isn't vendored in `original-src/`, and its files stamp a mermaid commit as `upstream-commit`.

## 6. Confusing API / pit-of-success failures

- **The worst trap in the codebase: `ssg.js.MinifyOptions(compress = true)` silently disables compression** (`Terser.scala:76-92` matches any Boolean as "disabled"); same for `mangle = true`. Upstream Terser semantics are the opposite. Second place: `CompressorOptions(defaults = false)` is a documented no-op — nothing consults `defaults`.
- **Silent failure as a contract:** minify HTML and TerserJsCompressor catch all exceptions and return input unchanged with no logging channel (Ruby at least warns) — a build ships unminified assets with no way to notice.
- **Seven different error contracts** across modules (throw `SassException` / bare `RuntimeException` / `ParseError`-or-error-span / in-band HTML comment / silent passthrough / `Option[String]` conflating four conditions) — a site build can't handle errors uniformly. Six different options conventions (17-param method / colliding case classes / DataKey builders / mutable Builder / config object / Settings).
- **Entry-point landmines:** `Compile.compile` (richest-looking sass entry point, always throws); `KaTeX.renderToString` takes the *internal* `Settings` type while the user-friendly `KaTeXOptions` renders only via its own companion statics; no `Markdown.render()` facade (two-builder flexmark dance) while mermaid/katex are one-liners.
- **Names that mislead:** two public `MinifyOptions` (+ both have `.Defaults`); `package ssg.liquid.antlr` contains no ANTLR (and hides the resolver SPI); `DiagramType.Graph` never produced; `ClassRenderer` tuned via `config.flowchart.padding`; mermaid `VennDb`/`WardleyDb` headers claim a mermaid origin that doesn't exist (10 of "31" diagram types are not in upstream v11.0.0; 6 aren't in Mermaid at all); `ssg/package.scala` doc references re-exports that don't exist and a module (`ssg-html`) that was renamed.
- **Stale in-code docs mislead auditors:** HtmlMinifier/JsMinifier headers list resolved issues as open gaps; `Terser.scala:14` claims it implements `ssg.minify.JsCompressor` (false); MathFunctions header documents failures since fixed; corrupted scaladoc in `KaTeXOptions.scala:45` ("Googling \"pixel art\" googles for googl."); `KaTeXOptions.output: String` unvalidated (`"mathML"` silently falls back).
- **Docs drift (all confirmed still present):** Scala 3.8.3 documented vs 3.8.4 actual (README, CLAUDE.md, two architecture docs); "8 modules" vs 13; `project/SsgSettings.scala` doesn't exist; architecture docs omit 5 modules. Stray git-tracked `lowlevel/Nullable.scala` at repo root belongs to no sbt project.

---

## Corrections to the 2026-06-09 review

| Prior claim | Correction |
|---|---|
| `collapse_vars` entirely missing | **Wrong** — implemented since 2026-04-11 (`TightenBody.scala:1281+`), though 109/183 fixtures expected-fail |
| sass `EvaluateVisitor` UOEs at 4391/4808 are gaps | **Mostly wrong** — faithful ports of dart-sass's own throws / internal sentinels; only the `PlainCssCallable` dispatch sliver is real (exotic path, low) |
| sass logger/deprecation options unwired | **Wrong** — `logger`, `verbose`, `silenceDeprecations`, `fatalDeprecations`, `futureDeprecations` are wired end-to-end |
| mermaid `themeCSS` raw injection risk | **Partially wrong** — CDATA is `]]>`-split (`SvgMarkup.scala:88-95`); breakout prevented; real issue is themeCSS only works in flowchart |
| katex/mermaid submodules empty, versions unverifiable | **Resolved** — katex = v0.16.45, mermaid = v11.0.0, both exactly as claimed |
| ssg-js DropUnused bug | Confirmed and **worse** — replacement nodes are discarded wholesale; markers never consumed |

## Prioritized recommendations

### P0 — the product
1. **Build the actual SSG pipeline** (or explicitly re-scope the project as a library collection): site config, front-matter→DataView bridge, layouts, one end-to-end integration test producing a real page. Everything else is polish on components nobody can compose.
2. **Implement `FileOps` on Native (and Node JS)** — `java.nio.file.Files` works on Scala Native; this single gap breaks Liquid includes and file-based workflows on 2 of 3 baseline platforms.
3. **Fix ssg-md Scala.js resource loading** (embed resources at build time) — core entity decoding currently throws for any downstream JS consumer.

### P1 — silent-no-op epidemic (small fixes, big honesty gains)
4. ssg-sass: wire `functions` (one loop), `quietDeps` (one constructor param), `charset` (one argument), `importers`/`loadPaths` (real `loadPathImporter`); fix `CompileFile` syntax inference + url; delete or implement `Compile.compile`.
5. ssg-js: fix the `Boolean` arms of `compress`/`mangle` (true = defaults); fix `optionBool` on `inline`/`pure_getters`/`sequences`; fix DropUnused Pass 3 to use `transform`; rename `src/test/scala-jvm` → `scalajvm`; remove the 6 stale `assume(false)` skips.
6. ssg-mermaid: strip + apply frontmatter and `%%{init:}%%`; stop throwing on standard documents; wire or delete the ~12 dead config fields; emit `click`/`href` links.
7. ssg-liquid: lex dotted/slashed include names (default-flavor Jekyll syntax is broken); fix `endraw -%}` swallowing; fix `where_exp` scoping.
8. ssg-minify: fnmatch-style exclude; `.min.*` passthrough; give failures a diagnostics channel instead of silent passthrough.

### P2 — trust the dashboards again
9. Reconcile both DBs: close ISS-935/936/937/938/940; re-stamp 11+ stale ssg-md audit majors; file issues for every finding above and the 47 liquid `.fail`s / 1507 ssg-js pins / 37 sass-spec failures; fix the 28 false "ported" migration rows; fix the sass-spec-baseline TSV header.
10. Make the CI enforce gate blocking (or stage it per-module); fix the codecov path; remove `pull_request` from release.yml.
11. Consolidate `Nullable` to lls project-wide; delete `ssg.commons.Nullable` and root `lowlevel/`; unify error contracts behind a shared result/diagnostics type in ssg-commons.

### P3 — fidelity debt
12. Port dagre bk.js positioning + pre-rank nesting (vendor dagre-js into original-src so it can be audited); graphviz cluster rendering.
13. katex: replace `AnyRef` placeholders with real types (kills 414 casts); guard `Macros.registerAll`; port unicode-spec.
14. highlight: rewrite `HtmlHighlightRenderer` with a proper span stack; unify offset semantics across platforms; add renderer unit tests.
15. Cross-platform consistency tests (same input → same output on JVM/JS/Native) for highlight, sass, md.

## Agent references

- ssg-md: aaf44bb9afa16d74c
- ssg-sass: a20d0f44493412149
- ssg-liquid/ssg-minify: a09762aa06313ec46
- ssg-js: a9f8e92e5626b3d82
- ssg-katex/highlight/commons: afe4a99cc8f65d4fb
- ssg-mermaid/graphviz/graphs-commons: a976c60b116f7ae71
- build/infrastructure/cross-cutting: a61602a7bbd896166
