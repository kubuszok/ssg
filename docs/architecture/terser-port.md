## ssg-js Architecture — Terser Port

## Overview

`ssg-js` is a cross-platform JavaScript parser, scope analyzer, AST,
compressor, mangler, and code generator ported from
[Terser](https://github.com/terser/terser) v5.46.1 (JavaScript, BSD-2) to
idiomatic Scala 3. It targets JVM, Scala.js, and Scala Native.

The CLI (`bin/terser`, `lib/cli.js`) and Node-specific bindings are out of
scope. Everything else — including the full ECMAScript parser, scope
analysis, compression passes, mangling, and code generation — is in scope.

`ssg-js` exposes a `Terser.minify(code, options)` entry point that mirrors
Terser's `minify()` API.

## Module Structure

```
ssg-js/src/main/scala/ssg/js/
├── Terser.scala                   minify() entry point
├── Parser.scala                   parse.js port
├── OutputStream.scala             output.js port
├── Compressor.scala               compress/index.js orchestration
├── ScopeAnalysis.scala            scope.js (figure_out_scope)
├── Mangler.scala                  scope.js (mangling)
├── PropMangler.scala              propmangle.js
├── ast/
│   ├── AstNode.scala              base + tree-walker
│   ├── AstClasses.scala
│   ├── AstDefinitions.scala
│   ├── AstExpressions.scala
│   ├── AstStatements.scala
│   ├── AstSymbols.scala
│   ├── AstConstants.scala
│   ├── AstScope.scala
│   ├── AstSize.scala              size.js
│   ├── AstEquivalent.scala        equivalent-to.js
│   └── AstToken.scala
├── compress/
│   ├── Common.scala
│   ├── CompressorFlags.scala
│   ├── Evaluate.scala             evaluate.js
│   ├── DropSideEffectFree.scala   drop-side-effect-free.js
│   ├── DropUnused.scala           drop-unused.js
│   ├── ReduceVars.scala           reduce-vars.js
│   ├── Inference.scala            inference.js
│   ├── Inline.scala               inline.js
│   ├── TightenBody.scala          tighten-body.js
│   ├── GlobalDefs.scala           global-defs.js
│   └── NativeObjects.scala        native-objects.js
├── TerserJsCompressor.scala       JsCompressor SPI bridge to ssg-minify
└── util/FirstInStatement.scala
```

## LOC Ratio — and Where the Bodies Are Buried

| Terser source | LOC | ssg-js | LOC | Ratio | Status |
|---------------|-----|--------|-----|-------|--------|
| `parse.js` | 3630 | `Parser.scala` | 3173 | 0.87× | Largely complete |
| `ast.js` | 3476 | `ast/*.scala` (×11) | 2640 | 0.76× | Node coverage near-complete |
| `output.js` | 2538 | `OutputStream.scala` | 1841 | 0.73× | Largely complete |
| `scope.js` | 1069 | `ScopeAnalysis`+`Mangler`+`PropMangler` | 1932 | 1.81× | Restructured, present |
| `compress/index.js` | **4129** | `Compressor.scala` | **1067** | **0.26×** | **GAPING HOLE** |
| `compress/evaluate.js` | 529 | `Evaluate.scala` | 739 | 1.40× | OK |
| `compress/drop-side-effect-free.js` | 388 | `DropSideEffectFree.scala` | 505 | 1.30× | OK |
| `compress/drop-unused.js` | 506 | `DropUnused.scala` | 263 | **0.52×** | **PARTIAL** |
| `compress/reduce-vars.js` | 865 | `ReduceVars.scala` | 388 | **0.45×** | **PARTIAL** |
| `compress/inference.js` | 1132 | `Inference.scala` | 813 | 0.72× | Scope-blocked |
| `compress/inline.js` | 684 | `Inline.scala` | 246 | **0.36×** | **PARTIAL** |
| `compress/tighten-body.js` | 1532 | `TightenBody.scala` | 540 | **0.35×** | **PARTIAL** |
| `compress/global-defs.js` | 93 | `GlobalDefs.scala` | 262 | 2.82× | OK |
| `minify.js` | 413 | `Terser.scala` | 93 | **0.23×** | **PARTIAL** |
| `equivalent-to.js` | 304 | `AstEquivalent.scala` | 274 | 0.90× | OK |
| `size.js` | 506 | `AstSize.scala` | 329 | 0.65× | Acceptable |
| `propmangle.js` | 435 | `PropMangler.scala` | 694 | 1.60× | OK |
| `transform.js` | 324 | merged into `AstNode` | — | — | OK |
| `sourcemap.js` | 149 | **MISSING** | 0 | **0×** | **GAP** |
| `mozilla-ast.js` | 2099 | **MISSING** | 0 | **0×** | **GAP** |
| `utils/*.js` | ~600 | partial / scattered | — | partial | Partial |

**Totals**: Terser ≈ 22 000 LOC (excluding CLI/tests). ssg-js ≈ 14 000 LOC.
At a typical Scala-vs-JS ratio of ~0.75–0.85×, a complete port should land
near 17 000–19 000 LOC. The ~4 000 LOC shortfall is concentrated in
`compress/index.js` (orchestration), `tighten-body`, `inline`, `reduce-vars`,
`drop-unused`, `minify.js`, plus the missing `sourcemap.js` and
`mozilla-ast.js`.

## Critical Gaps

### 1. Multi-pass compression loop is unimplemented

`Compressor.scala` does a single pass. Terser iterates passes until the AST
stops changing (or `compress.passes` is hit). `TerserSuite` notes
"Compression tests are marked TODO until the Compressor's multi-pass loop is
debugged (currently hangs)" — i.e. compression is **not usable today**.

`Compressor.scala:1021-1033` carries the TODO. Until this is fixed, the
`TerserJsCompressorAdapter` consumed by `ssg-minify` is effectively a parse +
re-emit pipeline, not a real compressor.

### 2. SymbolDef / scope integration is the dominant blocker

~50 of the ~125 TODOs in the compress passes are gated on having
`SymbolDef` properly threaded through scope analysis. This blocks:

- safe variable inlining (`Inline.scala:74,86,138,175`),
- unused-binding detection (`DropUnused.scala:173-184`),
- fixed-value lookup in constant folding (`Evaluate.scala:255,452`),
- tighten-body optimizations (`TightenBody.scala:96,248,257,492`),
- pure-function call elision (`Compressor.scala:107,116`),
- global hoisting (`Compressor.scala:286`).

The scope analyzer exists; the `compress/` consumers do not yet read it.

### 3. Source maps — not started

`sourcemap.js` (149 LOC) has no Scala counterpart, and `OutputStream` does
not track original positions. Source-map output is impossible today.

### 4. Missing specialized passes

Not present in `compress/`: `collapse-vars`, `negate-iife`, `wrap-iife`,
`hoist_vars`, `hoist_funs` (partial), `drop_console` (no dedicated handling),
logical-assignment lowering, `pure_funcs`/`pure_getters` honoring beyond a
stub.

### 5. SpiderMonkey AST (`mozilla-ast.js`, 2099 LOC) — missing

Used by tools that consume Terser's AST as Mozilla-format JSON. Lower
priority but part of the public surface.

### 6. `minify.js` orchestration is a stub

Terser's `minify()` (413 LOC) sequences parse → figure_out_scope → compress
loop → mangle → propmangle → output → sourcemap, with rich error
diagnostics, format normalization, ecma version handling, mangle-cache
plumbing, and option resolution. `Terser.scala` (93 LOC) covers the happy
path only.

### 7. No CLI (intentional, out of scope)

## Parser Coverage

`Parser.scala` covers ES5 → ES2022 cleanly: arrow functions, classes (incl.
class fields), template literals, destructuring, spread/rest, async/await,
exponentiation, optional catch binding, optional chaining (`?.`), nullish
coalescing (`??`), logical assignment (`||=`/`&&=`/`??=`), `import`/`export`,
dynamic `import()`, `import.meta`. **No JSX, no Flow types, no TypeScript.**
Top-level `await` is unverified.

## AST Node Coverage

Terser declares ~133 `AST_*` classes. The Scala port covers them across
`ast/AstStatements`, `ast/AstExpressions`, `ast/AstSymbols`,
`ast/AstDefinitions`, `ast/AstClasses`, `ast/AstConstants`. Spot checks
suggest near-complete coverage — node *types* are not the gap.

## Test Suite

Terser ships **168 fixture files** in `test/compress/*.js` and **38 mocha
files** in `test/mocha/`, covering tens of thousands of compression cases.

`ssg-js` ships **6 munit suites** (`TerserSuite`, `ParserSuite`,
`OutputSuite`, `ScopeSuite`, `AstSuite`, `JsSuite`). Coverage of the
compress passes is essentially nil — `TerserSuite` even comments out its
compression assertions pending the multi-pass loop fix.

**The Terser `test/compress/*.js` files are written in a homemade DSL**
(`name: { options: {...} input: {...} expect: {...} }`) that is trivial to
parse and would give us a regression suite of thousands of cases for free.
This is the single highest-leverage test investment.

[test262](https://github.com/tc39/test262) — the official ECMAScript
conformance suite — could additionally be wired in to validate the parser.

## Public API Surface

```scala
object Terser {
  def minify(code: String, options: MinifyOptions = MinifyOptions.Defaults): MinifyResult
  def minifyToString(code: String, options: MinifyOptions = MinifyOptions.Defaults): String
}
```

`MinifyOptions` mirrors Terser's `{ parse, compress, mangle, output }`
nesting but does not yet expose every Terser option. `TerserJsCompressor`
(the `JsCompressor` SPI implementation consumed by `ssg-minify`) calls
`minifyToString` with defaults and swallows exceptions to fall back to the
input — i.e. the bridge offers no per-call tuning.

## Roadmap to Production-Ready

The work, in dependency order:

1. **Fix the multi-pass compress loop** (`Compressor.scala`). Without this
   nothing else in `compress/` matters.
2. **Thread SymbolDef through scope analysis** so `ReduceVars`, `DropUnused`,
   `Inference`, `Inline`, `TightenBody`, and `Evaluate` can read fixed/used
   state. Burns down ~50 TODOs.
3. **Ingest Terser `test/compress/*.js`** as munit fixtures via a small DSL
   parser. Use as the regression gate for #1 and #2.
4. **Port the missing passes**: `collapse-vars`, `negate-iife`, `wrap-iife`,
   `hoist_vars`, `drop_console`, logical-assignment lowering.
5. **Source maps** — port `sourcemap.js`, add position tracking to
   `OutputStream`.
6. **Flesh out `Terser.minify`** to match `minify.js` orchestration, error
   shape, ecma normalization, mangle cache, format options.
7. **Expose Terser options** through `TerserJsCompressorAdapter` so
   `ssg-minify` callers can tune compression.
8. **`mozilla-ast.js`** — last, only if a consumer needs it.
9. **test262 parser conformance** — optional, as a parser-only sanity gate.
