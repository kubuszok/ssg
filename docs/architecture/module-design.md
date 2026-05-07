# Module Design

How each SSG module maps to its source library.

## ssg-md (Markdown)

**Source**: flexmark-java (https://github.com/vsch/flexmark-java) 0.64.8
**Language**: Java
**License**: BSD-2-Clause

flexmark-java is a highly modular markdown parser with ~43 sub-modules. SSG ports these
as packages within a single `ssg-md` module:

| flexmark Module | SSG Package | Status |
|----------------|-------------|--------|
| flexmark (core) | `ssg.md.ast`, `ssg.md.parser`, `ssg.md.html` | Complete |
| flexmark-util (11 modules) | `ssg.md.util.*` | Complete |
| flexmark-formatter | `ssg.md.formatter` | Complete |
| 26 extensions | `ssg.md.ext.*` | Complete |

**Key challenge**: `BasedSequence` is a complex string abstraction used throughout
flexmark. It was ported first and correctly.

See [flexmark-port.md](flexmark-port.md) for the full post-mortem.

## ssg-liquid (Liquid Templates)

**Source**: liqp (https://github.com/bkiers/Liqp) 0.9.2
**Language**: Java
**License**: MIT

liqp implements the Liquid template language. Key components:

| Component | SSG Package | Notes |
|-----------|-------------|-------|
| Parser | `ssg.liquid.parser` | Hand-written lexer/parser (replaces ANTLR) |
| Template | `ssg.liquid` | Template compilation and rendering |
| Tags | `ssg.liquid.tags` | 8 simple tags |
| Blocks | `ssg.liquid.blocks` | 10 block tags |
| Filters | `ssg.liquid.filters` | 58 filters |
| Nodes | `ssg.liquid.nodes` | 19 AST node types |

**Key challenge**: liqp uses ANTLR for parsing. ANTLR generates Java code (JVM-only).
For cross-platform SSG, a hand-rolled 3-mode lexer + recursive descent parser was built
from the grammar.

See [liqp-port.md](liqp-port.md) for the architecture overview.

## ssg-sass (SASS/SCSS Compiler)

**Source**: dart-sass (https://github.com/sass/dart-sass) 1.99.0
**Language**: Dart
**License**: MIT

dart-sass is the reference implementation of the Sass language. Components:

| Component | SSG Package | Notes |
|-----------|-------------|-------|
| AST | `ssg.sass.ast` | Stylesheet, rules, expressions, selectors |
| Parser | `ssg.sass.parse` | SCSS, Sass, CSS, media/keyframe/at-root parsers |
| Evaluator | `ssg.sass.visitor` | EvaluateVisitor, SerializeVisitor |
| Values | `ssg.sass.value` | Colors (17 spaces), numbers (compound units), strings |
| Functions | `ssg.sass.functions` | 130+ built-in functions across 8 modules |
| Extensions | `ssg.sass.extend` | @extend and selector unification |
| Importers | `ssg.sass.importer` | Filesystem, MapImporter, PackageImporter |
| Utils | `ssg.sass.util` | Character classification, span tracking |

**Key challenge**: Largest port in the project. Dart's null-safety maps well
to `Nullable[A]`, and its class hierarchy maps to Scala sealed traits. NativeMath
FFI provides IEEE 754 precision parity with dart-sass.

See [sass-port.md](sass-port.md) for the architecture overview.

## ssg-minify (HTML/CSS/JS/JSON Minification)

**Source**: jekyll-minifier (https://github.com/digitalsparky/jekyll-minifier) 0.2.2
**Language**: Ruby
**License**: MIT

jekyll-minifier is a gem that delegates to external minifiers. SSG reimplements
minification in pure Scala:

| Component | SSG Package | Notes |
|-----------|-------------|-------|
| HTML minifier | `ssg.minify.html` | htmlcompressor port |
| CSS minifier | `ssg.minify.css` | cssminify2 port |
| JS minifier | `ssg.minify.js` | Basic state-machine fallback |
| JSON minifier | `ssg.minify.json` | json-minify port |
| JsCompressor SPI | `ssg.minify` | Pluggable JS compressor trait |

The `TerserJsCompressorAdapter` (in `ssg` aggregator) wires ssg-js into the
`JsCompressor` SPI for full AST-based JS minification.

See [jekyll-minifier-port.md](jekyll-minifier-port.md) for the architecture overview.

## ssg-js (JavaScript Compiler/Minifier)

**Source**: terser (https://github.com/terser/terser) 5.46.1
**Language**: JavaScript
**License**: BSD-2-Clause

Terser is a JavaScript parser/compressor/mangler forked from UglifyJS. Components:

| Component | SSG Package | Notes |
|-----------|-------------|-------|
| Parser | `ssg.js` | Recursive descent, ~130 AST node types |
| Code generator | `ssg.js` | Minified and beautified output |
| Scope analysis | `ssg.js` | Variable mangling |
| Compressor | `ssg.js.compress` | 25+ optimization passes |
| AST | `ssg.js.ast` | 9 files covering all node types |

**Key challenge**: Re2 regex engine on Scala Native doesn't support lookahead/backreference,
requiring workarounds for patterns used in the JS parser.

See [terser-port.md](terser-port.md) for the architecture overview.

## ssg-highlight (Syntax Highlighting)

**Source**: [tree-sitter](https://github.com/tree-sitter/tree-sitter)
**Language**: C/Rust
**License**: MIT

Tree-sitter-based syntax highlighting with 73 grammar languages. Unlike the
other modules (which are source-level ports), ssg-highlight wraps tree-sitter
via platform-specific FFI:

| Platform | Integration |
|----------|-------------|
| JVM | JEP 454 (Panama Foreign Function API) |
| Scala.js | tree-sitter WASM bindings |
| Scala Native | Direct C interop via `ts_wrappers.c` |

## ssg-commons (Shared Utilities)

Cross-platform utilities shared across modules. Contains common abstractions
that don't belong to any single port.

## ssg (Aggregator)

Depends on all modules above. Provides the `TerserJsCompressorAdapter` that
wires ssg-js into ssg-minify's `JsCompressor` SPI.
