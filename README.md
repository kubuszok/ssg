# SSG — Scala Static Site Generator

A cross-platform Scala 3 static site generator targeting JVM, Scala.js, and
Scala Native — without external binary dependencies. Built by porting
battle-tested libraries to idiomatic Scala 3.

## Modules

| Module | Source Library | Language | Purpose | Status |
|--------|---------------|----------|---------|--------|
| `ssg-md` | [flexmark-java](https://github.com/vsch/flexmark-java) 0.64.8 | Java | Markdown engine | 1645/1645 tests |
| `ssg-liquid` | [liqp](https://github.com/bkiers/Liqp) 0.9.2 | Java | Liquid template engine | 280/280 tests |
| `ssg-sass` | [dart-sass](https://github.com/sass/dart-sass) 1.99 | Dart | SASS/SCSS compiler | 13865/13902 sass-spec (99.7%) |
| `ssg-minify` | [jekyll-minifier](https://github.com/digitalsparky/jekyll-minifier) | Ruby | HTML/CSS/JS/JSON minification | 113/113 tests |
| `ssg-js` | [terser](https://github.com/terser/terser) | JavaScript | JavaScript compiler/minifier | 116/116 tests |
| `ssg` | — | — | Aggregator (depends on all above) | — |

All completed modules pass tests on **JVM, Scala.js, and Scala Native**.

## Building

Requires: JDK 21+, sbt 1.12+, Scala 3.8.2

```bash
# Compile all modules on default (JVM) platform
re-scale build compile

# Compile on all platforms (JVM + JS + Native)
re-scale build compile --all

# Run tests for a specific module
re-scale test unit --module ssg-liquid

# Full 3-platform verification
re-scale test verify
```

## Architecture

Each module ports a source library to Scala 3 with these principles:

- **No external binary dependencies** — pure Scala on all platforms
- **Cross-platform** — JVM, Scala.js, Scala Native from the same source
- **Idiomatic Scala 3** — `enum`, `boundary`/`break`, opaque types, no `return`
- **Original tests ported** — comprehensive munit test suites

### ssg-md (Markdown)

Ports flexmark-java's modular Markdown parser with 20+ extensions
(tables, TOC, footnotes, admonitions, emoji, etc.).

### ssg-liquid (Liquid Templates)

Ports liqp's Liquid template engine with a hand-written lexer/parser
(replacing ANTLR), 58 filters, 17 tags/blocks, and an extensible flavor
system (Jekyll default, open to Shopify/Cobalt.rs/MkDocs configurations).

Key replacements:
- ANTLR → hand-written 3-mode lexer + recursive descent parser
- Jackson → `LiquidSupport` trait
- strftime4j → `DateTimeFormatter` via scala-java-time polyfill

### ssg-sass (SASS/SCSS Compiler)

Ports dart-sass — the reference implementation of the Sass language — to
Scala 3. Full compilation pipeline: SCSS/Sass/CSS parsing, evaluation,
module system (`@use`/`@forward`), `@extend` resolution, and CSS serialization.

Key features:
- All 17 color spaces (oklch, lab, display-p3, etc.) with gamut mapping
- Full module system (`@use`, `@forward`, `@import`) with configuration
- `@extend` selector resolution with identity-based trimming
- 130+ built-in functions across 8 modules (color, math, string, list, map, meta, selector)
- NativeMath FFI (JVM calls native C `libm` via JEP 454) for IEEE 754 precision parity with dart-sass
- Indented syntax (`.sass`) via faithful state-machine parser

The 37 non-passing tests are: 32 whitespace mismatches against older sass-spec
expected outputs (our output matches dart-sass 1.99), and 5 tests that
dart-sass itself marks as `:todo:`.

### ssg-minify (HTML/CSS/JS/JSON Minification)

Ports jekyll-minifier as pure Scala minification functions for HTML, CSS,
JavaScript, and JSON. Full HTML minification (comment removal, whitespace
collapsing, attribute optimization, inline CSS/JS compression). Pluggable
`JsCompressor` trait allows using the basic whitespace-only minifier or
the full Terser engine from ssg-js.

### ssg-js (JavaScript Compiler/Minifier)

Ports Terser (fork of UglifyJS) — a full JavaScript compiler with:
- ES2020+ parser (recursive descent, ~130 AST node types)
- Code generator (minified and beautified output)
- Scope analysis and variable mangling
- Compressor with 25+ per-node optimizations (dead code elimination,
  constant folding, statement tightening)
- Pure Scala, cross-platform (no regex lookahead/backreference)

Usage: `ssg.js.Terser.minifyToString("var x = 1 + 2;")` or integrate
with ssg-minify via `ssg.TerserJsCompressorAdapter`.

## Project Structure

```
ssg/
├── ssg-md/          Markdown engine (flexmark-java port)
├── ssg-liquid/      Liquid template engine (liqp port)
├── ssg-sass/        SASS/SCSS compiler (dart-sass port)
├── ssg-minify/      HTML/CSS/JS/JSON minification (jekyll-minifier port)
├── ssg-js/          JavaScript compiler/minifier (Terser port)
├── ssg/             Aggregator module
├── original-src/    Reference sources (git submodules, not compiled)
├── .rescale/        re-scale project config + data
├── docs/            Architecture and conversion guides
└── project/         sbt build configuration
```

## Documentation

- [CLAUDE.md](CLAUDE.md) — project conventions and tooling
- [docs/contributing/](docs/contributing/) — conversion guides, code style, type mappings
- [docs/contributing/cross-platform-regex.md](docs/contributing/cross-platform-regex.md) — regex limitations on Scala Native/JS
- [docs/architecture/](docs/architecture/) — module design, build structure, port status

## License

SSG source code is licensed under the [Apache License 2.0](LICENSE).

This project contains code ported from third-party libraries under their
original licenses:

| Library | License | Copyright |
|---------|---------|-----------|
| flexmark-java | BSD 2-Clause | 2015-2016 Atlassian, 2016-2018 Vladimir Schneider |
| liqp | MIT | 2010-2013 Bart Kiers |
| dart-sass | MIT | 2016 Google Inc. |
| jekyll-minifier | MIT | 2014-2024 DigitalSparky |
| terser | BSD 2-Clause | 2012 Mihai Bazon |

See [LICENSE](LICENSE) for full third-party notices.
