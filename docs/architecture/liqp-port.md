# ssg-liquid Architecture — liqp Port

## Overview

`ssg-liquid` is a cross-platform Liquid template engine ported from
[liqp](https://github.com/bkiers/Liqp) 0.9.2 (Java, MIT license) to
idiomatic Scala 3. It targets JVM, Scala.js, and Scala Native.

## Dependency Replacements

| Original | Problem | Replacement |
|----------|---------|-------------|
| ANTLR 4 | JVM-only parser generator | Hand-written lexer + recursive descent parser |
| Jackson | JVM reflection for POJO→Map | `LiquidSupport` trait |
| strftime4j | JVM-only date formatting | `DateTimeFormatter` via scala-java-time polyfill |

## Module Structure

```
ssg-liquid/src/main/scala/ssg/liquid/
├── Template.scala              Parse + render API
├── TemplateParser.scala        Builder-pattern configuration
├── TemplateContext.scala        Scoped variable storage
├── LValue.scala                Type conversion utilities
├── Insertion.scala             Base for tags and blocks
├── Insertions.scala            Tag/block registry
├── parser/
│   ├── Token.scala             60+ token types
│   ├── LiquidLexer.scala       3-mode tokenizer (DEFAULT/IN_TAG/IN_RAW)
│   ├── LiquidParser.scala      Recursive descent → LNode AST
│   ├── Flavor.scala            LIQUID, JEKYLL, LIQP presets
│   ├── LiquidSupport.scala     Object→Map conversion trait
│   └── Inspectable.scala       Marker trait
├── nodes/                      19 AST node types
├── blocks/                     10 block tags (if, for, case, …)
├── tags/                       8 simple tags (assign, include, …)
├── filters/                    58 filters + registry
├── antlr/
│   ├── NameResolver.scala      Template resolution trait
│   └── LocalFSNameResolver.scala  JVM file-system resolver
└── exceptions/                 4 exception types
```

## Hand-Written Parser

The ANTLR grammar (287-line lexer + 371-line parser) was replaced with a
hand-written implementation in two files:

**LiquidLexer** — State-machine with 3 modes:
- `DEFAULT`: Scans for `{{` and `{%` delimiters, collects plain text
- `IN_TAG`: Tokenizes operators, identifiers, strings, numbers
- `IN_RAW`: Collects raw text until `{% endraw %}`

Supports whitespace stripping (`{%-`/`-%}`, `{{-`/`-}}`).

**LiquidParser** — Recursive descent producing LNode AST directly:
- No intermediate parse tree (combines ANTLR parser + NodeVisitor)
- Operator precedence: `or` > `and` > comparison > `contains` > term
- Flavor-dependent syntax via configuration flags

## Flavor System

Each `Flavor` is a configuration bundle with defaults:

| Setting | LIQUID | JEKYLL | LIQP |
|---------|--------|--------|------|
| Snippets folder | `snippets` | `_includes` | `snippets` |
| Error mode | STRICT | WARN | STRICT |
| Include style | Liquid | Jekyll | Liquid |
| Where style | Liquid | Jekyll | Liquid |
| Evaluate in output | No | No | Yes |
| Strict typed exprs | Yes | Yes | No |

Custom flavors can be created via `TemplateParser.Builder` by composing
filter sets, tag sets, and behavior flags.

## Cross-Platform Strategy

| Issue | Solution |
|-------|----------|
| `java.time` not on JS/Native | scala-java-time + scala-java-locales polyfills |
| `java.util.Locale.ENGLISH` | Polyfill provides it |
| `java.util.Stack` | Replaced with `ArrayDeque` |
| `java.text.DecimalFormat` | Replaced with `BigDecimal.setScale` |
| `BigDecimal.toPlainString` broken on Native | Manual sci-notation removal |
| Regex `(?s)` DOTALL not on Native | Manual string processing for strip_html |
| Regex `(?!…)` lookahead not on Native | Manual entity detection for escape_once |
| Regex `++` possessive quantifier not on Native | Standard `+` quantifier |
| `StringBuilder.append(str, i, j)` wrong on Native | `str.substring(i, j)` then append |

## Dependencies

```scala
"io.github.cquiroz" %%% "scala-java-time"    % "2.6.0"
"io.github.cquiroz" %%% "scala-java-locales" % "1.5.4"
```

## Test Coverage

280 tests across 12 munit suites, all passing on JVM, JS, and Native.
