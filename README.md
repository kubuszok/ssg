# SSG — Scala Static Site Generator

A cross-platform Scala 3 static site generator targeting JVM, Scala.js, and
Scala Native — without external binary dependencies. Built by porting
battle-tested libraries to idiomatic Scala 3.

> [!warning]
> Work in progress - components are ported from other languages, but have not yet been composed into a working static site generator.

## Modules

| Module | Source Library | Language | Purpose | Status |
|--------|---------------|----------|---------|--------|
| `ssg-md` | [flexmark-java](https://github.com/vsch/flexmark-java) 0.64.8 | Java | Markdown engine | 5889 tests |
| `ssg-liquid` | [liqp](https://github.com/bkiers/Liqp) 0.9.2 | Java | Liquid template engine | 863 tests |
| `ssg-sass` | [dart-sass](https://github.com/sass/dart-sass) 1.99.0 | Dart | SASS/SCSS compiler | 13865/13902 sass-spec (99.7%) |
| `ssg-minify` | [jekyll-minifier](https://github.com/digitalsparky/jekyll-minifier) 0.2.2 | Ruby | HTML/CSS/JS/JSON minification | 121 tests |
| `ssg-js` | [terser](https://github.com/terser/terser) 5.46.1 | JavaScript | JavaScript compiler/minifier | 2518 tests |
| `ssg-katex` | [KaTeX](https://github.com/KaTeX/KaTeX) 0.16.45 | TypeScript | Math typesetting engine | 648 tests |
| `ssg-mermaid` | [Mermaid](https://github.com/mermaid-js/mermaid) 11.0.0 | TypeScript | Diagramming engine (31 types) | 543 tests |
| `ssg-highlight` | [tree-sitter](https://github.com/tree-sitter/tree-sitter) | C/Rust | Syntax highlighting | 73 grammars |
| `ssg-graphviz` | — | — | Graphviz DOT renderer (4 layout engines) | 99 tests |
| `ssg-graphs-commons` | — | — | Shared graph layout + SVG infrastructure | — |
| `ssg-data-commons` | — | — | Shared data view abstractions | — |
| `ssg-commons` | — | — | Shared cross-platform utilities | — |
| `ssg-site` | — | — | Site pipeline (SSG-native glue) | — |
| `ssg` | — | — | Aggregator (depends on all above) | — |

All completed modules pass tests on **JVM, Scala.js, and Scala Native**.

## Building

Requires: JDK 21+, sbt 1.12+, Scala 3.8.4

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

### ssg-katex (Math Typesetting)

Ports KaTeX — the fast LaTeX math typesetting library — to Scala 3.
Server-side rendering: LaTeX expression in, HTML+MathML string out.

Key features:
- Full LaTeX parser with macro expansion (180+ built-in macros)
- 57 parse node types, 45 function implementations (fractions, roots, accents, etc.)
- HTML output builder with font metrics and delimiter sizing
- MathML output for accessibility
- 42 environment implementations (array, matrix, aligned, cases, CD, etc.)

Usage: `ssg.katex.KaTeX.renderToString("\\frac{1}{2}")`

### ssg-mermaid (Diagramming Engine)

Ports Mermaid — the text-based diagramming library — to Scala 3.
Server-side SVG rendering: diagram text in, SVG string out.

Key features:
- All 31 diagram types: flowchart, sequence, class, state, ER, pie, gantt,
  timeline, mindmap, git graph, architecture, and 20 more
- Custom SVG builder replacing D3.js (no browser DOM required)
- dagre Sugiyama graph layout algorithm (rank assignment, crossing
  minimization, coordinate assignment)
- 12 node shapes, 7 curve types, 9 arrow markers
- 4 themes (default, dark, forest, neutral)
- Hand-written parsers for all 31 diagram grammars (replacing Jison/Langium)

Usage: `ssg.mermaid.Mermaid.render("graph TD\\n    A-->B")`

### ssg-graphviz (Graphviz DOT Renderer)

Server-side Graphviz DOT rendering: DOT text in, SVG string out.
Original SSG implementation (not a port of an external library),
reusing the graph layout and SVG infrastructure from ssg-graphs-commons.

Key features:
- Full DOT language parser (recursive descent, 13 grammar productions)
- 4 layout engines: dot (dagre/Sugiyama hierarchical), neato (Kamada-Kawai
  spring/force-directed), circo (circular), twopi (radial tree)
- Node shapes: ellipse, box/rect, circle, diamond, plaintext
- DOT attribute support: styling, labels, colors, fonts, rankdir
- Arrow markers for directed graphs, dashed/dotted edge styles

Usage: `ssg.graphviz.Graphviz.render("digraph { A -> B -> C }")`

### ssg-highlight (Syntax Highlighting)

Tree-sitter-based syntax highlighting supporting 73 programming language
grammars. Unlike the other modules (which are source-level ports),
ssg-highlight wraps tree-sitter via platform-specific FFI: JEP 454
(Panama) on JVM, WASM bindings on Scala.js, and direct C interop on
Scala Native.

## Project Structure

```
ssg/
├── ssg-commons/        Shared cross-platform utilities
├── ssg-graphs-commons/ Shared graph layout + SVG infrastructure
├── ssg-md/             Markdown engine (flexmark-java port)
├── ssg-liquid/         Liquid template engine (liqp port)
├── ssg-sass/           SASS/SCSS compiler (dart-sass port)
├── ssg-minify/         HTML/CSS/JS/JSON minification (jekyll-minifier port)
├── ssg-js/             JavaScript compiler/minifier (Terser port)
├── ssg-katex/          Math typesetting engine (KaTeX port)
├── ssg-mermaid/        Diagramming engine (Mermaid port, 31 diagram types)
├── ssg-graphviz/       Graphviz DOT renderer (4 layout engines)
├── ssg-highlight/      Syntax highlighting (tree-sitter, 73 grammars)
├── ssg/                Aggregator module
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
| KaTeX | MIT | Khan Academy |
| Mermaid | MIT | Knut Sveidqvist |
| tree-sitter | MIT | 2018 Max Brunsfeld |
| dagre/graphlib | MIT | Chris Pettitt |
| D3 curves | ISC | Mike Bostock |

See [LICENSE](LICENSE) for full third-party notices.

## AI-Assisted Development

The Java/JavaScript/Ruby/Dart-to-Scala 3 port was performed primarily using AI code generation tools
(Anthropic Claude Code). All AI-generated code was reviewed, tested, and audited
against the original sources by the project maintainer. Each ported file
contains a header comment documenting its original source, migration notes, and
audit date.
