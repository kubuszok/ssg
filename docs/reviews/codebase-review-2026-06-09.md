# SSG Codebase Review

**Date:** 2026-06-09  
**Method:** Six parallel module review agents (source reads, audit verification, enforcement scans)  
**Scope:** All published modules + aggregator + build infrastructure

---

## Executive Summary

SSG is a cross-platform Scala 3 port of Jekyll-like tooling (markdown, Liquid, Sass, KaTeX, Mermaid, Terser, etc.). The codebase is mature in structure but uneven in completeness.

| Area | Status |
|------|--------|
| **Migration** | 1,179 files ported; 50 not started (samples only); 307 intentionally skipped |
| **Audit** | 924 pass / 139 minor / **45 major** (many entries stale — see below) |
| **Covenant verify** | **984 pass / 99 fail** — headers often claim completeness while enforcement disagrees |
| **Test coverage (audited)** | 688 yes / **390 no** / 30 partial |
| **Open issues (TSV)** | 7 tracked (KaTeX only); 5 substantively stale |

**Highest-risk areas:** ssg-js compression pipeline, ssg-sass API wiring gaps, Liquid file inclusion (path traversal on JVM), cross-module API inconsistency, stale audit/issues databases.

---

## Review Methodology

### What was actually reviewed

| Agent scope | Depth |
|-------------|-------|
| **ssg-md** (~764 files) | All 19 `major_issues` audit rows verified line-by-line; 25+ files read; 7 Java counterparts compared; full shortcut scan (0 hits in main) |
| **ssg-sass** | EvaluateVisitor covenant verify; all sass `major_issues` checked; Compile/Importer API traced; extend package compared |
| **ssg-liquid + ssg-minify + ssg-js** | All focal security/API files read; all `major_issues` cross-checked; DropUnused vs upstream `drop-unused.js` compared |
| **ssg-mermaid + graphviz + graphs-commons** | 9 diagram implementations read fully; all 30 test suites inventoried; security layers on SVG traced |
| **ssg-katex + highlight + commons** | Core entry points + ISS-935–941 verified in source; 3 platform FFI impls read |
| **Build + cross-cutting** | `build.sbt`, aggregator, docs drift, security grep, 99 covenant failures enumerated, issues TSV verified |

### Limitations

- **Not done:** exhaustive line-by-line parity of every file vs every original (~2,000+ main sources)
- **Not done:** full `re-scale test verify` on all platforms (JVM × JS × Native)
- **Blocked:** `original-src/mermaid` and `original-src/katex` are empty submodules — upstream TS comparison impossible for those two libraries

---

## Verified Findings

### Security — highest confidence

#### Liquid file inclusion has no root jail (JVM)

Both resolvers read arbitrary paths without containment checks:

```scala
// LocalFSNameResolver.scala
if (directPath.isAbsolute) {
  val absPath = directPath.toAbsolute
  val content = FileOps.readString(absPath)  // bypasses root entirely
}
val path = FilePath.of(root).resolve(resolvedName).toAbsolute
val content = FileOps.readString(path)  // ../ not blocked
```

`IncludeRelative` resolves via `rootPath.resolve(includeResource)` with the same gap. Matches upstream liqp behavior, but is a real risk for multi-tenant SSG hosts.

**Positive:** No `ProcessBuilder` / `Runtime.exec` anywhere in Scala sources.

#### Mermaid SVG security posture

| Layer | Status |
|-------|--------|
| `SvgMarkup.escapeTextContent` | Primary defense — XML-escapes all text nodes |
| `TextUtils.sanitizeText` | Partial — regex strip of `<script>`, `on*`, etc.; not DOMPurify-equivalent |
| `themeCSS` | **Risk** — injected raw into `<style>` CDATA |
| `securityLevel`, `sanitizeUrl`, `htmlLabels` | **Defined but never enforced** in render path |
| `Utils.sanitizeUrl` | Implemented but never called |

#### Other security notes

- **ssg-md:** Included markdown is parsed and spliced into AST (classic Jekyll semantics); risk depends on resolver configuration
- **ssg-liquid:** No global auto-escape; standard Liquid semantics — XSS if untrusted templates/data
- **ssg-minify:** Broad exception swallowing in `HtmlMinifier` and `TerserJsCompressor`
- **ssg-highlight:** JVM Panama FFI and Native `@extern` — parses untrusted source only, no code execution

---

### ssg-js — confirmed functional bug

`DropUnused` Pass 3 uses `walk` instead of upstream `transform`:

```scala
// DropUnused.scala:128-131
val transformer = new Pass3Transformer(ctx)
// Use walk since transform is not yet implemented on AstScope
self.walk(transformer)
```

Upstream (`drop-unused.js:464`): `self.transform(tt)`. The comment is **incorrect** — `AstNode.transform()` exists. This is the highest-confidence compressor bug.

**Partially stale audit claim:** `OutputStream` ignoring `mangledName` is **fixed** at `OutputStream.scala:1710-1714`.

**Other ssg-js gaps (confirmed or likely):**

- `Terser.scala` ~23% of upstream `minify.js` (110 vs 412 LOC)
- `collapse_vars` entirely missing from `TightenBody`
- 14 files fail covenant verify
- `transform.js` not ported as a file, but `AstNode.transform()` mechanism exists — operational gap is callers like `DropUnused` not using it correctly

---

### ssg-sass — confirmed API wiring gaps

`Compile.compileString` accepts parameters that are **not wired** (silent no-ops):

| Parameter | Wired? |
|-----------|--------|
| `importers` | **NO** |
| `loadPaths` | **NO** |
| `functions` | **NO** |
| `quietDeps` | **NO** (`_quietDeps` hardcoded `false` in evaluator) |
| `charset` | **PARTIAL** — not forwarded to serializer |
| `url`, `importCache`, deprecation flags | YES |

**Other confirmed gaps:**

- `StylesheetGraph` — 89 LOC, cycle detection only; no mtime invalidation or incremental recompile
- Source maps minimal — no `sourcesContent`/`file` fields
- `EvaluateVisitor` fails covenant verify (intentional `UnsupportedOperationException` at lines 4391, 4808 mirroring dart-sass)
- `EvaluateVisitor:1446` — incomplete callable type dispatch

**Stale audit claims:** SassParser (659 LOC, covenant PASS), CssParser (718 LOC with AST validation), EvaluateVisitor LoC ratio, SerializeVisitor LoC ratio.

---

### ssg-md — confirmed gap

**`FileUriContentResolver` is not ported** despite `migration.tsv` claiming it is. `IncludeNodePostProcessor` omits Java's default factory fallback when `CONTENT_RESOLVER_FACTORIES` is empty — Jekyll `{% include file.md %}` won't resolve filesystem paths out of the box.

**Duplicate DataKey bug (upstream, faithfully ported):**

```scala
// JekyllTagExtension.scala
val LINK_RESOLVER_FACTORIES:    DataKey[...] = new DataKey(..., "LINK_RESOLVER_FACTORIES", ...)
val CONTENT_RESOLVER_FACTORIES: DataKey[...] = new DataKey(..., "LINK_RESOLVER_FACTORIES", ...)
```

**Module health:** Strongest module overall — 764/764 covenanted, 0 shortcut hits in main sources, extensive `Combo*SpecTest` harness.

---

### KaTeX — issues database stale

| Issue | Status |
|-------|--------|
| ISS-935 (`protocolRegex` missing `(?i)`) | **FIXED** — `(?i)` at `Utils.scala:83-84` |
| ISS-936 (extra `*_CRAMPED` exports) | **FIXED** |
| ISS-937 (critical `\verb` regex crash) | **FIXED** — manual `tryLexVerb` in `Lexer.scala:134-161` |
| ISS-938 (`[\s\S]` newline semantics) | **FIXED** — newline-before-delimiter check at line 149 |
| ISS-939 (BMP comment dropped) | Minor / cosmetic |
| ISS-940 (`ParseNodeSizing.size` should be `Int`) | **FIXED** |
| ISS-941 (`mathmlBuilder` should be mandatory) | **STILL OPEN** |

Stale `AnyRef` placeholders remain in `FunctionDef.scala` and `EnvironmentDef.scala` despite `Parser`/`Options` being ported.

---

### Infrastructure

| Finding | Detail |
|---------|--------|
| Covenant failures | **99 files** fail `re-scale enforce verify --all` (62 ssg-md, 14 ssg-js, 13 ssg-sass, 9 ssg-liquid, 1 ssg-minify) |
| Nullable fragmentation | **Three incompatible implementations:** `ssg.commons`, `ssg.liquid`, `lowlevel` (lls) |
| Audit DB | **Materially stale** — 14+ ssg-md `major_issues` describe bugs already fixed |
| Docs drift | Scala 3.8.4 vs documented 3.8.3; 13 modules vs documented 8; `project/SsgSettings.scala` does not exist |
| Version constants | Mix of SSG snapshot (`0.1.0-SNAPSHOT`), upstream versions (mermaid `11.0.0`, katex `0.16.45`), and `LibVersion` in sass |

---

## Module Health Summary

| Module | Verdict |
|--------|---------|
| **ssg-md** | Strongest. 764/764 covenanted, 0 shortcut hits. Real gap: Jekyll file includes (`FileUriContentResolver`). Audit DB needs refresh. |
| **ssg-mermaid** | Broad (30 diagram types, 30 test suites). Security config fields unwired. Empty submodule blocks upstream audit. |
| **ssg-katex** | Mature (~175 tests). Stale `AnyRef` placeholders; issues DB out of date. |
| **ssg-graphviz** | Clean, small, well-tested. |
| **ssg-highlight** | 73 language smoke tests. Nested span rendering drops inner captures; `supportsLanguage` vs `supportedLanguages` disagree. |
| **ssg-sass** | Large evaluator (5713 LOC) but public API params lie; incremental compile missing. |
| **ssg-liquid** | Path traversal risk; several audit entries stale. |
| **ssg-js** | Worst correctness gap. `DropUnused` bug confirmed; Terser ~23% of upstream. |
| **ssg-minify** | Clean production tree (0 shortcuts). Error swallowing; `JsCompressorOptions` dead API. |
| **ssg-commons** | Thin, zero tests, no path-security helpers. Native FileOps unimplemented. |

---

## Stale Audit / First-Review Corrections

| Claim | Correction |
|-------|------------|
| `AttributesNodeFormatter` / `SimTocNodeFormatter` are stubs | **Fixed** — full implementations (audit stale) |
| `Formatter.nodesOfType()` always empty | **Fixed** — uses `NodeCollectingVisitor` |
| KaTeX ISS-937 critical `\verb` regex crash | **Fixed** — manual `tryLexVerb` |
| ssg-js mangling has no output effect | **Stale** — `getSymbolName` uses `mangledName` |
| ssg-sass EvaluateVisitor at 40% LOC | **Stale** — now 5713 vs 4938 Dart; gaps are behavioral/wiring |
| `transform.js` entirely unported | **Partially stale** — `AstNode.transform()` exists; `DropUnused` doesn't use it |
| `IncludeRelative` does nothing different from `Include` | **Stale** — relative resolution implemented |
| `parser/LiquidSupport.scala` stub | **Obsolete** — file removed; replaced by `DataView` |
| `Minifier` toggles never read (ISS-042) | **Stale** — `minify()` honors `compressCss/Js/Json` |

### ssg-md major_issues verified as fixed (re-audit candidates)

- `ast/RefNode.scala` — `getReferenceNode` now delegates to `Parser.REFERENCES`
- `ext/attributes/internal/AttributesNodeFormatter.scala` — full 681-line formatter
- `formatter/Formatter.scala` — `NodeCollectingVisitor` populates `nodesOfType`
- `parser/internal/DocumentParser.scala` — `preProcessParagraph` uses `boundary`/`break`
- `util/sequence/Escaping.scala` — `Html5Entities` ported
- `ext/toc/TocUtils.scala`, `ext/toc/internal/SimTocNodeFormatter.scala` — complete
- `ext/macros/internal/MacroDefinitionBlockParser.scala` — GitLab block-quote pass-through present

---

## Cross-Module API Inconsistencies

### Error handling has no unified contract

| Module | Success | Failure |
|--------|---------|---------|
| mermaid | `String` (SVG) | `ParseException`; HTML comment for unsupported types |
| graphviz | `String` | `IllegalArgumentException` |
| katex | `String` | `ParseError`; optional inline span if `throwOnError=false` |
| sass | `CompileResult` | `SassException` hierarchy |
| liquid | `String` / `DataView` | `LiquidException` + collected `errors()` |
| minify (html) | `String` | Swallows exceptions, returns input |
| js (TerserJsCompressor) | `String` | Swallows exceptions, returns input |
| highlight | `Option[String]` | `None` (ambiguous semantics) |

### Naming / type conflicts

- **`MinifyOptions`** in both `ssg-minify` and `ssg-js`
- **Three `Nullable` types** — incompatible opaque types across module boundaries
- **`Version` semantics differ** — SSG snapshot vs upstream library version per module
- **`JsCompressorOptions`** trait has no concrete implementation — dead API
- **`TerserJsCompressorAdapter`** only in aggregator `ssg/` module, not in `ssg-minify` or `ssg-js`

### Fragmented entry points

- **md:** No `Markdown.render()` facade — requires `Parser` + `HtmlRenderer` builders
- **sass:** `Compile.compile(path)` throws; real file API is JVM-only `CompileFile` with fewer options
- **katex:** `KaTeX.renderToString` takes `Settings`; parallel `KaTeXOptions` API undocumented in primary signature
- **highlight:** No `package.scala`, no `Version`, minimal Scaladoc

---

## Prioritized Recommendations

### P0 — Fix before production use

1. **Liquid path sandboxing** — `normalize` + verify resolved path under root; reject absolute includes in site mode
2. **Fix `DropUnused`** — Pass 3 should use `transform`, not `walk`
3. **Port `FileUriContentResolver`** + default fallback in `IncludeNodePostProcessor`
4. **Refresh audit/issues DB** — close 14+ stale ssg-md majors, 5 stale KaTeX issues, obsolete `LiquidSupport` entry

### P1 — Correctness for SSG workflows

5. **Wire `Compile.compileString` dead params** (`loadPaths`, `importers`, `functions`, `quietDeps`)
6. **Mermaid:** wire or remove `securityLevel`/`sanitizeUrl`; sanitize `themeCSS`
7. **Consolidate `Nullable`** — adopt sass pattern (`type alias → lowlevel`) project-wide

### P2 — Integrator experience

8. Rename `ssg.js.MinifyOptions` → `TerserOptions`
9. Unify/document error policy per module
10. Add `Markdown.render()` facade; highlight `package.scala` + docs
11. Move `TerserJsCompressorAdapter` into `ssg-minify` or `ssg-js`
12. Implement or remove `JsCompressorOptions`

### P3 — Hygiene

13. Initialize `original-src/mermaid` and `original-src/katex` submodules
14. Re-run `/audit-package` on ssg-md, ssg-sass, ssg-js, ssg-liquid
15. Add security tests: include path traversal, mermaid `themeCSS` injection
16. Sync docs (`build-structure.md`, `nullable-guide.md`) with actual `build.sbt` and Nullable design
17. Triage 99 covenant verify failures

---

## What's in Good Shape

- **Project structure:** clear per-library modules, cross-platform matrix (JVM/JS/Native), `re-scale` tooling, audit/migration databases
- **ssg-md core:** largely ported and well-tested; flexmark parity is the strongest module
- **ssg-minify:** HTML/CSS/JSON minification relatively complete; clean shortcut scan
- **ssg-graphviz:** original implementation with clean facade and good tests
- **ssg-mermaid:** 31 diagram types with dedicated test suites
- **Enforcement tooling:** shortcuts, compare, covenant verify catch drift when used
- **Build strictness:** `-Werror` + comprehensive linter flags on all platforms

---

## Agent References

Reviews conducted by parallel agents covering:

- ssg-md: [311caed9-dbc1-48a2-96f4-541e00d8bde2](311caed9-dbc1-48a2-96f4-541e00d8bde2)
- ssg-sass: [915bf3df-d2b6-42f7-98d1-457583a2a63f](915bf3df-d2b6-42f7-98d1-457583a2a63f)
- ssg-liquid/minify/js: [900f80e1-8482-443f-a2b8-e6b43e887086](900f80e1-8482-443f-a2b8-e6b43e887086)
- ssg-mermaid/graphviz: [0485b7af-8bde-4db2-a1f4-731c587cd32a](0485b7af-8bde-4db2-a1f4-731c587cd32a)
- ssg-katex/highlight/commons: [d0041087-4d29-43f6-9dab-083820df3d70](d0041087-4d29-43f6-9dab-083820df3d70)
- Infrastructure: [e4d89957-512d-4889-afee-5de4367b993d](e4d89957-512d-4889-afee-5de4367b993d)
