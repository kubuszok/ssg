# SSG — Scala Static Site Generator

A cross-platform Scala 3 static site generator targeting JVM, Scala.js, and
Scala Native — without external binary dependencies. Built by porting
battle-tested libraries to idiomatic Scala 3.

## Modules

| Module | Source Library | Language | Purpose | Status |
|--------|---------------|----------|---------|--------|
| `ssg-md` | [flexmark-java](https://github.com/vsch/flexmark-java) 0.64.8 | Java | Markdown engine | 1645/1645 tests |
| `ssg-liquid` | [liqp](https://github.com/bkiers/Liqp) 0.9.2 | Java | Liquid template engine | 280/280 tests |
| `ssg-sass` | [dart-sass](https://github.com/sass/dart-sass) | Dart | SASS/SCSS compiler | Planned |
| `ssg-html` | [jekyll-minifier](https://github.com/digitalsparky/jekyll-minifier) | Ruby | HTML/JS/CSS minification | Planned |
| `ssg` | — | — | Aggregator (depends on all above) | — |

All completed modules pass tests on **JVM, Scala.js, and Scala Native**.

## Building

Requires: JDK 21+, sbt 1.12+, Scala 3.8.2

```bash
# Compile all modules on default (JVM) platform
ssg-dev build compile

# Compile on all platforms (JVM + JS + Native)
ssg-dev build compile --all

# Run tests for a specific module
ssg-dev test unit --module ssg-liquid

# Full 3-platform verification
ssg-dev test verify
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

## Project Structure

```
ssg/
├── ssg-md/          Markdown engine (flexmark-java port)
├── ssg-liquid/      Liquid template engine (liqp port)
├── ssg-sass/        SASS/SCSS compiler (dart-sass port) — planned
├── ssg-html/        HTML/JS/CSS minification — planned
├── ssg/             Aggregator module
├── original-src/    Reference sources (git submodules, not compiled)
├── scripts/         ssg-dev CLI toolkit
├── docs/            Architecture and conversion guides
└── project/         sbt build configuration
```

## Documentation

- [CLAUDE.md](CLAUDE.md) — project conventions and tooling
- [docs/contributing/](docs/contributing/) — conversion guides, code style, type mappings
- [docs/architecture/](docs/architecture/) — module design, build structure, port status

## License

SSG source code is licensed under the [Apache License 2.0](LICENSE).

This project contains code ported from third-party libraries under their
original licenses:

| Library | License | Copyright |
|---------|---------|-----------|
| flexmark-java | BSD 2-Clause | 2015-2016 Atlassian, 2016-2018 Vladimir Schneider |
| liqp | MIT | 2010-2013 Bart Kiers |

See [LICENSE](LICENSE) for full third-party notices.
