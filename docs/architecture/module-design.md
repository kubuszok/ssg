# Module Design

How each SSG module maps to its source library.

## ssg-md (Markdown)

**Source**: flexmark-java (https://github.com/vsch/flexmark-java)
**Language**: Java
**License**: BSD-2-Clause

flexmark-java is a highly modular markdown parser with ~30 sub-modules. SSG ports these
as packages within a single `ssg-md` module:

| flexmark Module | SSG Package | Priority |
|----------------|-------------|----------|
| flexmark (core) | `ssg.md.core` | High — port first |
| flexmark-util | `ssg.md.util` | High — foundation types |
| flexmark-html | `ssg.md.html` | High — HTML rendering |
| flexmark-formatter | `ssg.md.formatter` | Medium |
| flexmark-ext-tables | `ssg.md.ext.tables` | Medium |
| flexmark-ext-gfm-* | `ssg.md.ext.gfm` | Medium |
| flexmark-ext-yaml-front-matter | `ssg.md.ext.yaml` | High — Jekyll uses YAML |
| flexmark-ext-toc | `ssg.md.ext.toc` | Low |
| Other extensions | `ssg.md.ext.*` | Low |

**Key challenge**: `BasedSequence` is a complex string abstraction used throughout
flexmark. It must be ported first and correctly.

## ssg-liquid (Liquid Templates)

**Source**: liqp (https://github.com/bkiers/Liqp)
**Language**: Java
**License**: MIT

liqp implements the Liquid template language. Key components:

| Component | SSG Package | Notes |
|-----------|-------------|-------|
| Parser | `ssg.liquid.parser` | **ANTLR grammar → hand-rolled parser** |
| Template | `ssg.liquid.template` | Template compilation and rendering |
| Tags | `ssg.liquid.tags` | Built-in tags (if, for, assign, etc.) |
| Filters | `ssg.liquid.filters` | Built-in filters (date, upcase, etc.) |
| Nodes | `ssg.liquid.nodes` | AST node types |

**Key challenge**: liqp uses ANTLR for parsing. ANTLR generates Java code (JVM-only).
For cross-platform SSG, we must hand-roll a recursive descent parser from the grammar.

## ssg-sass (SASS/SCSS Compiler)

**Source**: dart-sass (https://github.com/sass/dart-sass)
**Language**: Dart
**License**: MIT

dart-sass is the reference implementation of the Sass language. Components:

| Component | SSG Package | Notes |
|-----------|-------------|-------|
| AST | `ssg.sass.ast` | Stylesheet, rules, expressions |
| Parser | `ssg.sass.parse` | SCSS and indented syntax parsers |
| Evaluator | `ssg.sass.evaluate` | CSS generation from AST |
| Values | `ssg.sass.value` | Sass value types (colors, numbers, strings) |
| Visitors | `ssg.sass.visitor` | AST traversal |
| Extensions | `ssg.sass.extend` | @extend and selector logic |
| Importers | `ssg.sass.importer` | @import and @use resolution |
| Utils | `ssg.sass.util` | Character classification, span tracking |

**Key challenge**: This is the largest library to port. Dart's null-safety maps well
to `Nullable[A]`, and its class hierarchy maps to Scala sealed traits.

## ssg-html (HTML/JS Minification)

**Source**: jekyll-minifier (https://github.com/digitalsparky/jekyll-minifier)
**Language**: Ruby
**License**: MIT

jekyll-minifier is a small gem that minifies HTML/JS/CSS output. Components:

| Component | SSG Package | Notes |
|-----------|-------------|-------|
| HTML minifier | `ssg.html.minify` | Regex-based HTML minification |
| JS minifier | `ssg.html.js` | Basic JS minification |
| CSS minifier | `ssg.html.css` | Basic CSS minification |
| Config | `ssg.html.config` | Minification options |

**Approach**: Reimplement from algorithm spec rather than line-by-line port.
Consider using Scala XML for better HTML handling than regex.

**Key challenge**: The Ruby gem uses regex extensively. We should evaluate whether
a proper HTML parser would be more reliable.

## Porting Order

Recommended order for porting work:

1. **ssg-html** — smallest, quickest win, can be used immediately
2. **ssg-md** — start with core + util (BasedSequence), then parser, then HTML renderer
3. **ssg-liquid** — start with parser (hand-rolled from ANTLR grammar), then tags/filters
4. **ssg-sass** — largest, start with value types and AST, then parser, then evaluator
