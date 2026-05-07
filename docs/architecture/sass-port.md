# ssg-sass Architecture — dart-sass Port

## Overview

`ssg-sass` is the cross-platform SASS/SCSS compiler ported from
[dart-sass](https://github.com/sass/dart-sass) 1.99.0 (Dart, MIT). Full
SASS specification compliance, no native bindings, runs on JVM / Scala.js /
Scala Native.

Status: **complete** — 13,865/13,902 sass-spec tests passing (99.73%).

## Compilation Pipeline

```
SCSS/Sass/CSS source
  → Parser (ScssParser / SassParser / CssParser)
  → AST (Stylesheet, rules, expressions)
  → EvaluateVisitor (variable evaluation, @use/@forward, @extend, built-ins)
  → CSS AST (CssStylesheet)
  → SerializeVisitor (CSS output with invisible-node pruning)
```

## Module Structure

```
ssg-sass/src/main/scala/ssg/sass/
├── Compile.scala              Entry point: compileString / compileToResult
├── ast/                       Stylesheet and CSS AST nodes, selector types
├── parse/                     ScssParser, SassParser, CssParser,
│                              MediaQueryParser, KeyframeSelectorParser,
│                              AtRootQueryParser
├── visitor/                   EvaluateVisitor, SerializeVisitor,
│                              FindDependenciesVisitor
├── value/                     SassColor (17 color spaces), SassNumber
│                              (compound units), SassList, SassMap,
│                              SassString, SassFunction, SassMixin,
│                              SassCalculation
├── functions/                 130+ built-in functions: ColorFunctions,
│                              MathFunctions, StringFunctions, ListFunctions,
│                              MapFunctions, MetaFunctions, SelectorFunctions
├── extend/                    @extend resolution, ExtensionStore,
│                              selector unification
├── importer/                  FilesystemImporter, MapImporter,
│                              PackageImporter, ImportCache
├── logger/                    DeprecationProcessing, warning infrastructure
├── util/                      Character classification, span tracking
├── Environment.scala          Scope chain, closures, module namespaces
├── Configuration.scala        @use with (...) config propagation
├── Module.scala               BuiltInModule, ForwardedView, ShadowedView
└── EvaluationContext.scala    Thread-local evaluation state stack
```

## Key Features

- **All 17 color spaces** (oklch, lab, display-p3, etc.) with gamut mapping
- **Full module system** (`@use`, `@forward`, `@import`) with configuration
  propagation and private-member hiding
- **`@extend` selector resolution** with identity-based trimming and
  trailing-combinator merging
- **130+ built-in functions** across 8 modules (color, math, string, list,
  map, meta, selector) plus CSS Math 3 calc functions
- **NativeMath FFI** (JVM calls native C `libm` via JEP 454) for IEEE 754
  precision parity with dart-sass
- **Indented syntax** (`.sass`) via faithful state-machine parser
- **Deprecation framework** matching dart-sass warnings (slash-div, elseif,
  color-functions, etc.)
- **Strict CssParser** for plain `.css` files (rejects Sass-only syntax)

## Non-passing Tests

The 37 non-passing sass-spec tests are: 32 whitespace mismatches against
older sass-spec expected outputs (our output matches dart-sass 1.99), and
5 tests that dart-sass itself marks as `:todo:`.

## Out of Scope

| Upstream path | Reason |
|---------------|--------|
| `lib/src/embedded/**` | Embedded compiler protocol (protobuf, isolates) |
| `lib/src/js/**` | Dart-to-JS API, legacy Node.js bindings |
| `lib/src/executable/**` | CLI: options, watch, REPL |
| `lib/src/callable/async.dart` | Async evaluation path (we port sync only) |
| `lib/src/importer/js_to_dart/**` | Node/JS importer bridges |

## Cross-Platform Status

| Platform | Tests | Status |
|----------|-------|--------|
| JVM | 13,865/13,902 | 99.73% |
| Scala.js | 13,865/13,902 | 99.73% |
| Scala Native | 13,865/13,902 | 99.73% |
