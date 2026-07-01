# Error Contracts — the shared diagnostics envelope and per-module adoption plan

**Issue:** ISS-1102 [R0610-P2] (design + plan; per-module wiring is separate issue work).
**Review basis:** `docs/reviews/codebase-review-2026-06-10.md` section 6 — seven
incompatible error contracts across modules (SassException hierarchy, bare
RuntimeException, ParseError-or-error-span, in-band HTML comment, silent
passthrough, Option conflating four conditions).

This document is written to be executable: each module section names the exact
public entry point (file:line), its current error behavior, the exact adapter
change, what must NOT change, and a fallback. An implementer should be able to
execute one module's adoption from this document alone.

---

## 1. The shared types

All in `ssg-commons/src/main/scala/ssg/commons/Diagnostics.scala`
(package `ssg.commons`; compiles on JVM, Scala.js, Scala Native; ssg-commons is
a dependency of every other module, so every facade can use these types).

| Type | Purpose |
|---|---|
| `Severity` | `Debug < Info < Warning < Error` (ascending ordinals; `isAtLeast` for threshold checks; stable `label`) |
| `SourcePosition` | Lowest-common-denominator position: optional `source`, 1-based `line`/`column`/`endLine`/`endColumn`, 0-based `[offset, endOffset)` |
| `Diagnostic` | `severity + component + message + position + code + cause`; `causeChain` (cycle-capped); `render` → `[component] severity: message (pos)` |
| `DiagResult[+A]` | `value: Option[A]` + `diagnostics: Vector[Diagnostic]`; three states (below); `map`/`flatMap` accumulate diagnostics; `fromEither`, `sequence` |

### 1.1 The three states of `DiagResult`

| State | Definition | Who uses it |
|---|---|---|
| **success** | `value.isDefined` and no `Severity.Error` diagnostic (warnings allowed) | sass output + deprecation warnings; liquid WARN-mode clean render; every happy path |
| **degraded** | `value.isDefined` WITH at least one `Severity.Error` diagnostic — output exists but is a substitute | mermaid error-diagram SVG; katex error-HTML span (`throwOnError = false`) |
| **failure** | `value.isEmpty` (diagnostics explain why) | sass compile error, liquid/js/graphviz parse error, highlight config error |

Severity policy for "returned the input unchanged" degradation (minify, js
compressor): that is a **`Warning` + success**, not degraded — the output is
still correct content, merely unoptimized, matching jekyll-minifier's
`Jekyll.logger.warn` precedent and `ssg.site`'s existing
`BuildDiagnostic(stage = Minify, severity = Warning)`. Degraded (`Error` +
value) is reserved for cases where the requested artifact is absent and a
substitute was rendered (mermaid/katex error markup).

### 1.2 Non-negotiable adapter rules

1. **Facade-level only.** Ported modules keep their internal exception
   hierarchies untouched — port fidelity is inviolable. The envelope wraps at
   the module's public entry point.
2. **Additive only.** Every adapter is a NEW method named `<entry>Result`
   (e.g. `compileStringResult`, `renderResult`) beside the existing entry
   point. Existing signatures, thrown exceptions, and their tests do not
   change.
3. **Specific catches only.** Adapters catch the module-native exception types
   by name (`SassException`, `LiquidException`, `JsParseError`,
   `ParseException`, katex `ParseError`, `IllegalArgumentException` where that
   is the documented native contract). Never a blanket catch-everything
   (anti-cheat C12). Exceptions outside the native failure contract (genuine
   bugs) keep propagating.
4. **Component names** are module names: `"ssg-md"`, `"ssg-liquid"`,
   `"ssg-sass"`, `"ssg-minify"`, `"ssg-js"`, `"ssg-katex"`, `"ssg-mermaid"`,
   `"ssg-graphviz"`, `"ssg-highlight"`, `"ssg-site"`.
5. **The native exception rides along** as `Diagnostic.cause`
   (`Diagnostic.fromThrowable`), so callers that need the full native error
   (e.g. `SassException.span.highlight()`) can still reach it.
6. **Every adapter ships with differential tests**: one asserting the failure
   input produces the expected `Diagnostic` (severity, component, code,
   position values — structure/value assertions, not non-emptiness), one
   asserting the happy path is a clean success, and — where degradation exists
   — one asserting the degraded value equals what the legacy entry point
   returns for the same input.

### 1.3 Position mapping table (the four+ incompatible native types)

`SourcePosition` convention: `line`/`column` 1-based when present;
`offset`/`endOffset` 0-based, end-exclusive; offset UNIT is whatever the
producing module counts natively (chars everywhere except ssg-highlight,
which counts UTF-8 bytes).

| Module | Native type (file:line) | Native fields & base | Mapping into `SourcePosition` |
|---|---|---|---|
| ssg-sass | `FileSpan`/`FileLocation` — `ssg-sass/src/main/scala/ssg/sass/util/SourceSpan.scala:124` / `:101` | `start`/`end: FileLocation(offset, line, column)`, all **0-based** (SourceSpan.scala:54,70 comments); `sourceUrl: Nullable[String]` | `source = span.sourceUrl.toOption`, `line = span.start.line + 1`, `column = span.start.column + 1`, `endLine = span.end.line + 1`, `endColumn = span.end.column + 1`, `offset = span.start.offset`, `endOffset = span.end.offset` |
| ssg-katex | `SourceLocation` — `ssg-katex/src/main/scala/ssg/katex/SourceLocation.scala:22-26`; surfaced as `ParseError.position`/`length` (`ParseError.scala:31-35`) | `start`/`end` **0-based char offsets**, end-exclusive; no line/column exists | `offset = e.position`, `endOffset = e.position + e.length` (only when both `Nullable`s are present — `SourcePosition.offsetRange(p, p + len)`); line/column stay `None` |
| ssg-highlight | `HighlightSpan` — `ssg-highlight/src/main/scala/ssg/highlight/HighlightSpan.scala:5` | `startByte`/`endByte` **0-based UTF-8 byte offsets** | `offset = startByte`, `endOffset = endByte` (byte unit — document at the call site); today's `HighlightError` cases carry no position at all, so highlight diagnostics use `position = None` until ISS-1371 gives the FFI layer a failure signal |
| ssg-liquid | `LiquidException` — `ssg-liquid/src/main/scala/ssg/liquid/exceptions/LiquidException.scala:30-31` | `line` **1-based**, `charPositionInLine` **0-based** (ANTLR convention kept by the liqp port) | `line = e.line`, `column = e.charPositionInLine + 1` |
| ssg-js | `JsParseError` — `ssg-js/src/main/scala/ssg/js/parse/Tokenizer.scala:33-39` | `line` **1-based**, `col` **0-based**, `pos` **0-based char offset** (Tokenizer.scala:67-70 initialize `line = 1`, `col = 0`) | `source = e.filename`, `line = e.line`, `column = e.col + 1`, `offset = e.pos` |
| ssg-mermaid | `ParseException` — `ssg-mermaid/src/main/scala/ssg/mermaid/parse/ParserBase.scala:34` | `line`/`col` **1-based** | `line = e.line`, `column = e.col` (no +1) |
| ssg-graphviz | `Token` — `ssg-graphviz/src/main/scala/ssg/graphviz/parse/DotScanner.scala:23` | `line`/`col` **1-based** (DotScanner.scala:28-29 initialize both to 1) | today unreachable — the thrown `IllegalArgumentException`s embed line/col in the message TEXT only; `position = None` until a structured exception exists (section 2.8) |

The `+1` cells above are the exact off-by-one traps; the mapping tests in each
wiring issue must pin them with literal expected values (e.g. sass error at
0-based line 2 ⇒ `SourcePosition.line == Some(3)`).

---

## 2. Per-module adoption plan

Legend per module: **Entry point** (file:line) → **Current behavior** →
**Adapter** → **Must NOT change** → **Fallback** → **Wiring issue**.

### 2.1 ssg-md (flexmark-java port)

- **Entry point:** `Markdown.render(markdown: String): String` —
  `ssg-md/src/main/scala/ssg/md/Markdown.scala:77-80` (and the `DataHolder`
  overload at `:94-97`; `Markdown.parse` at `:47-48`/`:61-62`). Lower level:
  `Parser.parse` `ssg-md/src/main/scala/ssg/md/parser/Parser.scala:104-131`
  (String overload `:142-159`, `parseReader` `:172-190`),
  `HtmlRenderer.render` `ssg-md/src/main/scala/ssg/md/html/HtmlRenderer.scala:134-138`.
- **Current behavior:** CommonMark-lenient — malformed markdown never fails;
  there is NO error collection (the review's "collects errors" phrasing does
  not match the code: no `Document` error list exists). What CAN escape:
  `IllegalArgumentException` for `ReplacedBasedSequence` input
  (Parser.scala:108), `IOException` from `parseReader` (declared at
  Parser.scala:172), and platform resource-loading failures (entity tables on
  Scala.js — see review P0-3).
- **Adapter:** in `Markdown.scala`, add
  `def renderResult(markdown: String): DiagResult[String]` (and the
  `DataHolder` overload + `parseResult` variants): call the existing
  `render`/`parse` inside `try`; catch `e: IllegalArgumentException` →
  `DiagResult.failure(Diagnostic.fromThrowable(Severity.Error, "ssg-md", e, code = Some("invalid-input")))`;
  in any reader-based variant catch `e: java.io.IOException` → code
  `"io-error"`. Everything else (renderer bugs, JS resource loading) keeps
  propagating. Happy path → `DiagResult.success(html)`.
- **Must NOT change:** `Parser`, `HtmlRenderer`, `Node`, `BasedSequence`
  internals (flexmark port fidelity); the existing throwing entry points; the
  lenient never-fails parse semantics (leniency is upstream behavior, not a
  gap).
- **Fallback:** if touching `Markdown.scala` is blocked, wrap at the consumer:
  `ssg-site/src/main/scala/ssg/site/Site.scala:228-229` (`mdParser.parse` /
  `mdRenderer.render`) with the same specific catches recording
  `BuildDiagnostic(stage = BuildStage.Markdown)`.
- **Wiring issue:** none open — file a new module issue before adopting
  (candidate recorded in the ISS-1102 delivery report).

### 2.2 ssg-liquid (liqp port)

- **Entry points:** `TemplateParser.parse(input: String): Template` —
  `ssg-liquid/src/main/scala/ssg/liquid/TemplateParser.scala:101-102` (with
  location `:112-113`, path/file/stream/reader `:72-98`);
  `Template.render(variables): String` —
  `ssg-liquid/src/main/scala/ssg/liquid/Template.scala:72-73` (no-arg `:76-77`,
  `renderToObject` `:87-116`); collected render errors exposed via
  `Template.errors(): JList[Exception]` — Template.scala:67-69.
- **Current behavior:** parse failures throw `LiquidException`
  (`exceptions/LiquidException.scala:30-31`, fields `line` 1-based /
  `charPositionInLine` 0-based; thrown unconditionally via
  `LiquidParser.reportTokenError`, `parser/LiquidParser.scala:80-94`,
  regardless of error mode). Render failures depend on
  `TemplateParser.ErrorMode` (TemplateParser.scala:155-157): `STRICT` throws;
  `WARN`/`LAX` collect into `TemplateContext.errorsList`
  (TemplateContext.scala:143-148) and keep rendering. Flavor defaults
  (TemplateParser.scala:66-72): JEKYLL (the SSG default) = `WARN`.
  `renderToObject` additionally throws bare `RuntimeException` for
  size/time-limit violations (Template.scala:88-89, 106-112) and
  `ExceededMaxIterationsException` can escape loops
  (`exceptions/ExceededMaxIterationsException.scala:22`).
- **Adapter:**
  - `TemplateParser.parseResult(input: String, location: FilePath): DiagResult[Template]`
    (+ String-only overload): catch `e: LiquidException` →
    `DiagResult.failure(Diagnostic.fromThrowable(Severity.Error, "ssg-liquid", e, position = Some(SourcePosition(line = Some(e.line), column = Some(e.charPositionInLine + 1))), code = Some("parse-error")))`.
  - `Template.renderResult(variables): DiagResult[String]`: call `render`
    inside `try`; catch `e: LiquidException` → failure (code
    `"render-error"`, same position mapping); catch
    `e: ExceededMaxIterationsException` → failure (code `"iteration-limit"`).
    After a successful render, drain `errors()` — each collected exception
    becomes `Diagnostic.fromThrowable(Severity.Error, "ssg-liquid", e, code = Some("render-error"))`,
    yielding a **degraded** result (output was produced with suppressed
    errors — the WARN-mode contract made uniform). Empty `errors()` → clean
    success. Do NOT catch bare `RuntimeException`: the limit guards throw
    untyped `RuntimeException` faithful to liqp; catching that type would be a
    blanket catch. Leave them propagating and note it as a known hole of the
    adapter (typed limit exceptions would be a liqp-fidelity question, not
    adapter scope).
- **Must NOT change:** `LiquidException` fields (liqp port), `ErrorMode`
  semantics including the ISS-1369 strictVariables→STRICT coupling, the
  `errors(): JList[Exception]` contract, all existing parse/render signatures.
- **Fallback:** wrap at `Site.scala:223-224` (page body) and `:506-507`
  (layout chain) with the same catches.
- **Wiring issue:** none open — file a new module issue (ISS-1369 is adjacent
  context, not wiring).

### 2.3 ssg-sass (dart-sass port)

- **Entry point:** `Compile.compileString(...): CompileResult` —
  `ssg-sass/src/main/scala/ssg/sass/Compile.scala:50` (17 parameters;
  `CompileResult(css, sourceMap, loadedUrls, warnings: List[String])` at
  Compile.scala:32-37). JVM file entry: `CompileFile.compile` —
  `ssg-sass/src/main/scalajvm/ssg/sass/CompileJvm.scala:33-39`.
  `Compile.compile(path)` (Compile.scala:149) throws
  `UnsupportedOperationException` on every platform BY DESIGN (fail-fast,
  documented at Compile.scala:144-146) — it is not an adoption target.
- **Current behavior:** throws the `SassException` hierarchy
  (`ssg-sass/src/main/scala/ssg/sass/SassException.scala`: base `:30` with
  `sassMessage`, `span: FileSpan`, `loadedUrls`; `MultiSpanSassException:125`,
  `SassRuntimeException:144`, `MultiSpanSassRuntimeException:159`,
  `SassFormatException:176`, `MultiSpanSassFormatException:196`;
  `SassScriptException:212` extends RuntimeException directly but is
  converted to `SassRuntimeException` before escaping the evaluator).
  Warnings/deprecations go through the injectable `ssg.sass.Logger` (a
  faithful dart-sass port, intentionally NOT unified with
  `ssg.commons.Logger` — see `ssg-commons/src/main/scala/ssg/commons/Logger.scala:16-19`)
  and come back as `CompileResult.warnings`.
- **Adapter:** in `Compile.scala`, add `compileStringResult` with the same
  parameter list returning `DiagResult[CompileResult]`:
  - `try` the existing `compileString`.
  - `catch e: SassException` →
    `DiagResult.failure(Diagnostic.fromThrowable(Severity.Error, "ssg-sass", e, position = Some(spanPosition(e.span)), code = Some(codeFor(e))))`
    where `spanPosition` implements the section 1.3 sass row
    (`e.span.sourceUrl.toOption`, 0-based +1 for line/column, raw offsets) and
    `codeFor` maps the subclass: `SassFormatException` → `"format-error"`,
    `SassRuntimeException` → `"runtime-error"`, otherwise `"compile-error"`.
  - On success: `DiagResult(Some(result), result.warnings.toVector.map(w => Diagnostic.warning("ssg-sass", w)))`
    — success-with-warnings, `isSuccess` stays true.
  - Add the mirroring `CompileFile.compileResult` on JVM delegating the same
    way.
- **Must NOT change:** the `SassException` hierarchy and `FileSpan`
  0-based semantics (dart-sass port fidelity + covenants), `ssg.sass.Logger`,
  the `CompileResult` shape, `Compile.compile`'s documented fail-fast
  contract, the 17-parameter signature (its shape is a separate options-API
  concern, out of scope here).
- **Fallback:** `Site.scala:178-208` already catches `SassException` into
  `BuildDiagnostic(stage = Sass, severity = Error)` — extend that catch to
  populate the position via the same span mapping.
- **Wiring issue:** none open specifically (ISS-1275 covers sass-spec
  error-path conformance, adjacent) — file a new module issue.

### 2.4 ssg-minify (jekyll-minifier port)

- **Entry points:** `HtmlMinifier.minify` —
  `ssg-minify/src/main/scala/ssg/minify/html/HtmlMinifier.scala:66-85`
  (signature includes `logger: ssg.minify.Logger = ssg.minify.Logger.quiet`);
  facade `Minifier.minifyHtml/minifyCss/minifyJs/minifyJson/minify/minifyFile`
  — `ssg-minify/src/main/scala/ssg/minify/Minifier.scala:34-97`;
  `CssMinifier.minify` `css/CssMinifier.scala:43-59`; `JsMinifier.minify`
  `js/JsMinifier.scala:52-57`; `JsonMinifier.minify` `json/JsonMinifier.scala:39-44`.
- **Current behavior:** `HtmlMinifier.minify` catches `Exception`
  (HtmlMinifier.scala:78-84 — pre-existing jekyll-minifier degradation
  semantics, made honest by ISS-1028), calls
  `logger.warn("HTML compression failed: ...")` and returns the input
  unchanged. The other minifiers propagate exceptions. `ssg.minify.Logger` is
  an alias of `ssg.commons.Logger`
  (`ssg-minify/src/main/scala/ssg/minify/Logger.scala:28,33`).
- **Adapter:** add `HtmlMinifier.minifyResult(input, options, jsCompressor, jsCompressorOpts): DiagResult[String]`
  implemented WITHOUT any new catch: call the existing
  `minify(input, options, jsCompressor, jsCompressorOpts, logger = collector)`
  where `collector` is a private `ssg.commons.Logger` that appends each
  warned message to a local buffer; afterwards return
  `DiagResult(Some(output), buffer.toVector.map(msg => Diagnostic.warning("ssg-minify", msg, code = Some("html-compression-failed"))))`.
  Per the section 1.1 severity policy this is Warning + success (content
  correct, unoptimized). Add the same-shaped `Minifier.minifyFileResult`
  facade so ssg-site can consume one envelope for all file types.
- **Must NOT change:** the existing `minify` signature and its Logger channel
  (ISS-1028 contract, test-locked by `HtmlMinifierDiagnosticsIss1028Suite`);
  the return-input-unchanged degradation (jekyll-minifier.rb:1013-1015
  fidelity); the existing catch block (pre-existing, logged, issue-covered —
  do not widen or narrow it).
- **Fallback:** `Site.minifyContent` (Site.scala:345-386) already records
  `BuildDiagnostic(stage = Minify, severity = Warning)`; keep that path and
  skip the module-level envelope.
- **Wiring issue:** ISS-1028 (resolved) established the Logger channel this
  adapter rides on; the `DiagResult` facade itself needs a new issue.
  ISS-1215 (open) tracks the missing Site-level minify-Warning test.

### 2.5 ssg-js (terser port)

- **Entry points:** `Terser.minify(code, options): MinifyResult` —
  `ssg-js/src/main/scala/ssg/js/Terser.scala:212-223`
  (`minifyToString` `:571-572`, `minifyFiles` `:231`);
  `TerserJsCompressor.compress(input, logger = quiet): String` —
  `ssg-js/src/main/scala/ssg/js/TerserJsCompressor.scala:39-50`.
- **Current behavior:** `Terser.minify` throws `JsParseError`
  (`parse/Tokenizer.scala:33-39`: `message`, `filename`, `line` 1-based,
  `col` 0-based, `pos` 0-based char offset; exception message
  `"SyntaxError: $message ($filename:$line:$col)"`). `TerserJsCompressor`
  catches `Exception` (TerserJsCompressor.scala:43-49 — ISS-1052), warns via
  the injected `ssg.commons.Logger`, returns the input unchanged.
- **Adapter:**
  - `Terser.minifyResult(code, options): DiagResult[MinifyResult]`: catch
    `e: JsParseError` →
    `DiagResult.failure(Diagnostic.fromThrowable(Severity.Error, "ssg-js", e, position = Some(SourcePosition(source = Some(e.filename), line = Some(e.line), column = Some(e.col + 1), offset = Some(e.pos))), code = Some("parse-error")))`.
    Nothing else is caught — non-`JsParseError` throws are compressor bugs and
    keep propagating.
  - `TerserJsCompressor.compressResult(input): DiagResult[String]`: same
    collecting-logger technique as ssg-minify (call the existing `compress`
    with a buffering logger; warned messages become
    `Diagnostic.warning("ssg-js", msg, code = Some("js-compression-failed"))`;
    Warning + success per section 1.1).
- **Must NOT change:** `JsParseError` shape (terser port), `Terser.minify`'s
  throwing contract and `MinifyResult` shape, `TerserJsCompressor`'s existing
  catch + Logger channel (ISS-1052 contract, test-locked by
  `TerserJsCompressorDiagnosticsIss1052Suite`).
- **Fallback:** wrap at the consumer adapter
  `ssg-site/src/main/scala/ssg/TerserJsCompressorAdapter.scala:22-50`, which
  already owns a catch-and-log block.
- **Wiring issue:** ISS-1052 (resolved) established the Logger channel; the
  `DiagResult` facade needs a new issue.

### 2.6 ssg-katex (KaTeX port)

- **Entry points:** `KaTeX.renderToString(expression, options: Settings)` —
  `ssg-katex/src/main/scala/ssg/katex/KaTeX.scala:61`; the `KaTeXOptions`
  overload `KaTeX.scala:86`; statics
  `KaTeXOptions.renderToString` — `ssg-katex/src/main/scala/ssg/katex/KaTeXOptions.scala:110-116`.
- **Current behavior:** throws `ParseError`
  (`ssg-katex/src/main/scala/ssg/katex/ParseError.scala:25-36`: `rawMessage`,
  `position: Nullable[Int]` 0-based char offset, `length: Nullable[Int]`).
  With `Settings.throwOnError = false`, a `ParseError` is instead rendered
  IN-BAND as an error HTML span (class `katex-error`, colored by
  `errorColor`) — KaTeX.scala:102-115 (`renderError`); non-`ParseError`
  throwables always rethrow. This in-band HTML is one of the review's seven
  contracts.
- **Adapter:** `KaTeX.renderToStringResult(expression, options): DiagResult[String]`
  added in `KaTeX.scala` (in-module, so it can reach the private
  `renderError` path):
  - run the render pipeline inside `try`; catch `e: ParseError`:
    - if `options.throwOnError` →
      `DiagResult.failure(Diagnostic.fromThrowable(Severity.Error, "ssg-katex", e, position = positionOf(e), code = Some("parse-error")))`;
    - else → `DiagResult.degraded(errorHtml, sameDiagnostic)` where
      `errorHtml` comes from the SAME `renderError` markup the legacy path
      emits (in-module access; do not re-render by re-throwing).
  - `positionOf(e)`: `Some(SourcePosition.offsetRange(p, p + len))` when both
    `e.position` and `e.length` are present, else `None` (section 1.3 katex
    row).
  - Callers get the same bytes the legacy entry point produces AND a
    machine-readable diagnostic — the in-band contract becomes inspectable
    without HTML parsing.
- **Must NOT change:** `ParseError` fields (KaTeX port), the error-HTML markup
  (char-level KaTeX fidelity, covered by upstream fixtures),
  `Settings.throwOnError` semantics, the `Settings` vs `KaTeXOptions` duality
  (a separate API issue — ISS-941 is adjacent; do not fold it into wiring).
- **Fallback:** wrap OUTSIDE the module: `try renderToString(expr, settings-with-throwOnError=true)`,
  catch `ParseError` → build the diagnostic, then re-call with
  `throwOnError = false` for the substitute HTML (renders twice on failure;
  acceptable because failures are rare and inputs small).
- **Wiring issue:** none open — file a new module issue.

### 2.7 ssg-mermaid

- **Entry point:** `Mermaid.render(input, config): String` —
  `ssg-mermaid/src/main/scala/ssg/mermaid/Mermaid.scala:100`; dispatch `try`
  at `:180-181`, `catch e: ParseException` at `:260-269` — if
  `effectiveConfig.suppressErrorRendering` (field defined at
  MermaidConfig.scala:128, default false; the catch consults the EFFECTIVE
  config, i.e. after frontmatter/init merging — not the raw `config` argument)
  rethrow, else `ErrorDiagram.renderError(e.getMessage, effectiveConfig)`
  (`diagrams/error_/ErrorDiagram.scala:32-36`). This is the ISS-1068 contract.
- **Current behavior:** parse failure → error-diagram SVG (indistinguishable
  from a successful diagram by type — only by content) or, with
  `suppressErrorRendering = true`, a thrown `ParseException`
  (`parse/ParserBase.scala:34`: `message`, `line` 1-based, `col` 1-based).
  Draw-phase failures escape as `IllegalStateException` /
  `IndexOutOfBoundsException` / `IllegalArgumentException` /
  `NoSuchElementException` from diagram DBs — upstream mermaid conflates
  parse and draw (noted out-of-scope in the ISS-1068 resolution).
- **Adapter:** `Mermaid.renderResult(input, config): DiagResult[String]` added
  in `Mermaid.scala` (in-module, sharing the same effective-config
  computation as `render`): replicate the dispatch `try` of `:180-269`, and
  in `catch e: ParseException` build
  `diag = Diagnostic.fromThrowable(Severity.Error, "ssg-mermaid", e, position = Some(SourcePosition.lineColumn(e.line, e.col)), code = Some("parse-error"))`,
  then:
  - `effectiveConfig.suppressErrorRendering == true` → `DiagResult.failure(diag)`
    (no substitute output requested);
  - else → `DiagResult.degraded(ErrorDiagram.renderError(e.getMessage, effectiveConfig), diag)`
    with the SAME `effectiveConfig` argument the legacy catch passes — byte
    equality with `render`'s error SVG is the adapter's invariant (assert it
    in the wiring test).
  Draw-phase exceptions stay uncaught (rule 3; the parse/draw conflation is
  an upstream architecture question, not adapter scope). The unknown-type
  path (`DiagramType.Unknown` → ErrorDiagram, also from ISS-1068) becomes
  `DiagResult.degraded(errorSvg, Diagnostic.error("ssg-mermaid", "No diagram type detected...", code = Some("unknown-diagram-type")))`.
- **Must NOT change:** `Mermaid.render`'s contract (test-locked by
  `MermaidIss1068Suite`), `ParseException` fields, `ErrorDiagram` markup,
  `suppressErrorRendering` semantics on both paths.
- **Fallback:** call `render` with
  `config.copy(suppressErrorRendering = true)` from outside and catch
  `ParseException` — loses byte-parity with the error diagram unless the
  caller re-renders it via `ErrorDiagram.renderError(e.getMessage, config)`
  (public), which uses the user config rather than the effective config; the
  in-module adapter avoids exactly this drift.
- **Wiring issue:** ISS-1068 (resolved) established the current contract; the
  `DiagResult` facade needs a new issue.

### 2.8 ssg-graphviz (SSG-native, not a port)

- **Entry points:** `Graphviz.render(input, config): String` —
  `ssg-graphviz/src/main/scala/ssg/graphviz/Graphviz.scala:22`;
  `Graphviz.parse(input): DotGraph` — `Graphviz.scala:16`.
- **Current behavior:** lexical and syntactic failures throw plain
  `IllegalArgumentException` (scanner: `parse/DotScanner.scala:102-104,
  160-162, 209-211, 242-244, 280-282`; parser: `parse/DotParser.scala:33-35,
  110, 281-283, 292-294, 323-325, 336-338`). `Token` (DotScanner.scala:23)
  carries 1-based `line`/`col`, but the exceptions embed them ONLY in message
  text (e.g. `"Unexpected character 'x' at line 3, col 7"`) — no structured
  position. Layout/render phases throw nothing (`NumberFormatException` is
  handled internally).
- **Adapter (two steps, both in the wiring issue):**
  1. Because ssg-graphviz is SSG-native (no port-fidelity constraint on
     internals), introduce
     `final class DotParseException(message: String, val line: Int, val col: Int) extends IllegalArgumentException(message)`
     in `ssg.graphviz.parse` and throw it at the 11 sites above, passing the
     line/col those sites already interpolate into their messages. Existing
     `catch`/tests keyed on `IllegalArgumentException` and on message text
     keep working (subclass + same message).
  2. `Graphviz.renderResult(input, config): DiagResult[String]` (and
     `parseResult`): catch `e: DotParseException` →
     `DiagResult.failure(Diagnostic.fromThrowable(Severity.Error, "ssg-graphviz", e, position = Some(SourcePosition.lineColumn(e.line, e.col)), code = Some("parse-error")))`.
- **Must NOT change:** `parse(input): DotGraph` and `render` signatures; the
  exception MESSAGE formats (tests may pin them; `DotParseException` must
  reuse them verbatim); `IllegalArgumentException` compatibility (hence
  subclassing, not replacement).
- **Fallback:** if step 1 is rejected, catch `e: IllegalArgumentException` in
  `renderResult` with `position = None` — documented as the graphviz row of
  section 1.3; the message still contains the human-readable location.
- **Wiring issue:** none open — file a new module issue.

### 2.9 ssg-highlight (tree-sitter wrapper)

- **Entry point:** `SyntaxHighlighter.highlight(source, language): Either[HighlightError, String]`
  — `ssg-highlight/src/main/scala/ssg/highlight/SyntaxHighlighter.scala:7`
  (default implementation
  `TreeSitterHighlighter` — `TreeSitterHighlighter.scala:9-30`).
- **Current behavior:** the ISS-1096 contract — `Left` of
  `enum HighlightError` (`HighlightError.scala:17-21`: `UnknownLanguage`,
  `MissingQuery`, `QueryLoadFailed`; field-less cases), `Right(html)` on
  success INCLUDING zero-capture parses (supported-but-tokenless is success,
  mutation-locked). The 4th failure mode (parser/FFI failure) is currently
  indistinguishable from zero captures — ISS-1371 (open) tracks giving the
  platform layer a failure signal.
- **Adapter:** the closest-to-trivial one — add (as an extension method on
  `SyntaxHighlighter` or a default trait method)
  `def highlightResult(source: String, language: String): DiagResult[String] = DiagResult.fromEither(highlight(source, language))(err => Diagnostic.error("ssg-highlight", messageFor(err), code = Some(err.name)))`
  where `err.name` is the stable enum name (`"UnknownLanguage"`,
  `"MissingQuery"`, `"QueryLoadFailed"`) and `messageFor` renders a short
  human sentence per case. `position = None` (the error cases carry none;
  `HighlightSpan.startByte/endByte` — `HighlightSpan.scala:5` — are UTF-8
  byte offsets and only describe successful captures).
- **Must NOT change:** the `Either` contract and the zero-captures→`Right`
  behavior (both test-locked via `HighlightSuite` /
  `HtmlHighlightRendererIss1097Suite`, ISS-1096); the `HighlightError` enum
  (extend it only via ISS-1371's FFI work); the platform `TreeSitterPlatform`
  API.
- **Fallback:** none needed — `DiagResult.fromEither` exists precisely for
  this module; if extension placement is contentious, a standalone
  `object HighlightFacade` works identically.
- **Wiring issues:** ISS-1096 (resolved — the Either contract), ISS-1371
  (open — FFI failure signal; when it lands, its new error case flows through
  the same adapter unchanged), ISS-1372 (open — tests pinning the two
  currently-unreachable `Left` cases).

### 2.10 ssg-site (SSG-native pipeline — the consumer)

- **Entry point:** `Site.build(config, failOnError = false): BuildResult` —
  `ssg-site/src/main/scala/ssg/site/Site.scala:93`; result types in
  `ssg-site/src/main/scala/ssg/site/BuildResult.scala`:
  `BuildStage` (`:25-35`), `ssg.site.Severity` (`:38-41`, Error/Warning only),
  `BuildDiagnostic(file, stage, severity, message, cause: Nullable[Throwable])`
  (`:59-65`), `BuildResult(written, diagnostics)` (`:77-80`).
- **Current behavior:** the pipeline ALREADY accumulates diagnostics instead
  of throwing (ISS-1210): sass stage catches `SassException`
  (Site.scala:178-208 → Error), layout stage catches
  `MissingLayoutException` / root-jail violations (Site.scala:266-313 →
  Error), minify stage catches
  `IllegalArgumentException`/`IllegalStateException`/`IOException`
  (Site.scala:345-386 → Warning + unminified fallback);
  `LayoutCycleException` deliberately propagates (ISS-1209). Liquid/markdown
  failures currently pass through unadapted at the top level (their adoption
  is step 3 below plus sections 2.1/2.2). `BuildDiagnostic` carries no
  position and duplicates `Severity` locally.
- **Adapter (site is the convergence point — do this LAST, after the
  component facades exist):**
  1. Delete `ssg.site.Severity` and import `ssg.commons.Severity`
     (Error/Warning map 1:1; commons adds Info/Debug). Under `-Werror`, any
     non-exhaustive `match` on the wider enum must gain the new cases or a
     threshold check via `isAtLeast(Severity.Warning)`.
  2. Re-shape `BuildDiagnostic` to embed the shared type:
     `final case class BuildDiagnostic(file: FilePath, stage: BuildStage, diagnostic: Diagnostic)`
     plus forwarding defs (`def severity = diagnostic.severity`,
     `def message = diagnostic.message`,
     `def cause: Nullable[Throwable] = diagnostic.cause.fold(Nullable.empty[Throwable])(Nullable(_))`)
     and an `apply(file, stage, severity, message, cause)` overload building
     the embedded `Diagnostic` with `component = "ssg-site"` so existing
     construction sites compile unchanged.
  3. Convert each stage from catch-and-wrap to envelope consumption as the
     module facades land: e.g. the sass stage becomes
     `Compile.compileStringResult(...).fold(diags => record all with stage = Sass, (result, warnings) => use result.css + record warnings)`
     — positions and codes then flow into `BuildDiagnostic` for free.
  4. `failOnError` keeps its exact meaning: any diagnostic with
     `severity == Severity.Error` triggers the throw.
- **Must NOT change:** the `BuildResult(written, diagnostics)` public shape;
  `failOnError` semantics; `LayoutCycleException` propagation (ISS-1209
  contract); the Phase-3/Phase-4 suite expectations
  (`SiteBuildPhase3Suite`, `SiteBuildPhase4Suite` are byte-exact
  mutation-audited — the re-shape in step 2 must keep them green without
  editing their expectations).
- **Fallback:** keep `BuildDiagnostic` as-is and add only
  `def toDiagnostic: Diagnostic` (component `"ssg-site"`, position `None`) —
  a lossless one-way bridge that still lets CLI/report tooling consume the
  shared type. Choose this if step 2's constructor-compat overload proves too
  intrusive.
- **Wiring issues:** ISS-1209/ISS-1210 (resolved — the current accumulation
  contract), ISS-1215 (open — minify-Warning path test). The
  envelope-consumption re-wiring needs a new issue.

---

## 3. Adoption order and verification

Recommended order (dependency- and risk-sorted): **highlight** (pure
`fromEither`, smallest) → **graphviz** (SSG-native, structured exception) →
**mermaid** / **katex** (degraded-state exercisers) → **sass** / **liquid** /
**js** (position-mapping heavy) → **md** (trivial catches) → **minify**
(collecting logger) → **site** (consumer, last).

Every wiring issue must satisfy the R0610 floor (compile all 3 platforms,
suite named with executed counts, shortcut/stale-stub gates) plus:

- a mapping test per section-1.3 row with LITERAL expected values (the
  off-by-one +1 cells are the point);
- a legacy-parity test: the legacy entry point and the `*Result` adapter
  produce identical output bytes for the same input (success AND
  degraded/fallback paths);
- proof-of-red: reverting the adapter must fail the new tests.

The shared-type unit tests live in
`ssg-commons/src/test/scala/ssg/commons/DiagnosticsIss1102Suite.scala`
(37 tests, all 3 platforms).
