## ssg-minify Architecture — jekyll-minifier Port

## Overview

`ssg-minify` is a cross-platform HTML/CSS/JS/JSON minifier ported from
[jekyll-minifier](https://github.com/digitalsparky/jekyll-minifier) (Ruby, MIT)
and the gems it delegates to: `htmlcompressor`, `cssminify2`, `uglifier`/`terser`,
`json-minify`. It targets JVM, Scala.js, and Scala Native.

The Jekyll plugin layer (Ruby site hooks, config loading, file scanning) is
out of scope — `ssg-minify` exposes a pure library API consumed by `ssg`.

## Module Structure

```
ssg-minify/src/main/scala/ssg/minify/
├── Minifier.scala              File-type dispatcher
├── MinifyOptions.scala         Top-level options
├── FileType.scala              HTML/CSS/JS/JSON enum
├── JsCompressor.scala          Pluggable JS-compressor SPI
├── html/HtmlMinifier.scala     htmlcompressor port
├── html/PreservedBlock.scala   <pre>/<script>/<style>/user pattern stash
├── css/CssMinifier.scala       cssminify2 port
├── js/JsMinifier.scala         Basic state-machine fallback compressor
└── json/JsonMinifier.scala     json-minify port
```

`TerserJsCompressorAdapter` (in the `ssg` aggregator module) wires `ssg-js`
into the `JsCompressor` SPI so HTML inline `<script>` blocks get full AST-based
minification.

## LOC Ratio

| Subsystem | Original | Scala | Ratio |
|-----------|----------|-------|-------|
| HTML (htmlcompressor) | ~1100 (Ruby+Java backend) | 550 | 0.50× |
| CSS  (cssminify2)     | ~250 (Ruby) | 329 | 1.30× |
| JS   (basic fallback) | n/a (Terser is the real impl) | 332 | n/a |
| JSON (json-minify)    | ~120 (JS) | 115 | 0.96× |
| Top-level dispatcher  | ~150 | 173 | 1.15× |

CSS and JSON are essentially 1:1. The HTML port at 0.5× is a yellow flag —
several htmlcompressor edge cases (see Gaps) are missing.

## Feature Gaps vs Originals

### HTML — htmlcompressor

Implemented: comment removal (with conditional-comment retention), doctype
simplification, whitespace collapsing (inter-tag, intra-tag, multi-space),
boolean attribute simplification, default-attribute removal (`script type`,
`style type`, `link type`, `form method`, `input type`), `http(s)://` and
`javascript:` protocol removal, preserved blocks (`<pre>`, `<textarea>`,
`<script>`, `<style>`), user `preservePatterns`, inline CSS/JS compression,
graceful degradation on parse error.

Not implemented:

- **`preserveLineBreaks` option is dead** — declared in `HtmlMinifyOptions`
  but never read by `HtmlMinifier.doMinify`. (issue: dead-option)
- **Quote-removal is over-conservative** — only strips quotes from
  `[A-Za-z0-9_-]+` values. htmlcompressor strips them from any attribute
  value not containing whitespace, `=`, `<`, `>`, or quotes.
- **No built-in SSI / JSP / ASP / PHP preservation patterns**
  (`<!--# ... -->`, `<% ... %>`, `<?php ... ?>`, `<?= ... ?>`).
  Workaround: add to `preservePatterns`.
- **No CDATA-section handling** — `<![CDATA[...]]>` is not specially preserved.
- **No empty-attribute removal** (`alt=""` etc.).
- **No attribute order normalization, no `rel`/`rev` shortening, no
  `aria-*`/`data-*` policy.**

### CSS — cssminify2

Implemented: comment stripping, whitespace collapsing, trailing-`;` removal,
empty-rule removal, 6→3 hex shortening, `0px`→`0` for 14 units, string and
`url(...)` preservation.

Not implemented:

- **`rgb(...)`/`rgba(...)` → hex** conversion.
- **Named colors → hex** (`white`→`#fff`).
- **Shorthand collapsing** (`margin: 1px 2px 1px 2px` → `1px 2px`).
- **`font-weight: bold`→`700`** and similar keyword→numeric.
- **Vendor-prefix culling.**
- **`@supports`/`@container` query-specific handling.**
- **No source maps.**

### JS — basic fallback (`js/JsMinifier.scala`)

This is a deliberately limited regex/state-machine. Real minification is
delegated to `ssg-js` via `TerserJsCompressorAdapter`. The fallback only:
strips comments, collapses whitespace, preserves strings/templates/regexes,
preserves ASI-relevant newlines.

Everything Terser does — mangling, DCE, constant folding, inlining, scope
analysis, source maps — lives in `ssg-js` (see [`terser-port.md`](terser-port.md)
for that gap report).

### JSON — json-minify

Implemented: whitespace removal, `//` and `/* */` comment removal, full string
preservation including escapes and unicode.

Not implemented (micro-optimizations the original gem doesn't do either):
number normalization (`1.0`→`1`, `0.5`→`.5`, exponent rewriting).

## Spec / Test Suite

Neither `jekyll-minifier` nor its delegated gems publish a substantial test
corpus we can ingest wholesale. The closest reusable suite is
[html-minifier-terser](https://github.com/terser/html-minifier-terser)'s
`tests/minifier.spec.js`, which is a superset of htmlcompressor's behavior
and could be ported as a fixture-driven munit suite.

Current `ssg-minify` test count: 113 across 6 suites (HTML 29, CSS 27, JS 20,
JSON 19, dispatcher 17, smoke 1). All green on JVM/JS/Native.

## Roadmap to Production-Ready

1. Implement `preserveLineBreaks` (it's already in the public API).
2. Broaden HTML quote-stripping to match htmlcompressor's rule.
3. Add a built-in `serverTags` preserve preset (SSI/JSP/PHP/ASP).
4. CDATA preservation in HTML.
5. CSS color folding (rgb→hex, names→hex) and shorthand collapsing.
6. Ingest html-minifier-terser's spec fixtures as a property-style suite.
7. Track ssg-js Terser parity separately — see `terser-port.md`.
