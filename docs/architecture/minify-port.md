# ssg-minify Architecture — jekyll-minifier Port

## Overview

`ssg-minify` is the cross-platform HTML/JS/CSS/JSON minifier ported from
[jekyll-minifier](https://github.com/digitalsparky/jekyll-minifier) (Ruby, MIT).
JS minification is delegated to `ssg-js` (Terser port). Target: feature parity
with jekyll-minifier so SSG can drop-in replace it.

## Gap Analysis (TODO — apply methodology from `flexmark-port.md`)

Procedure (same as ssg-md):

1. **LOC ratio** — `original-src/jekyll-minifier/lib/**/*.rb` vs
   `ssg-minify/src/main/scala/ssg/minify/**/*.scala`. Ruby is much terser than
   Scala, so the expected ratio here is *inverted*: scala/ruby ≥ 1.5.
2. **Stub sweep** over `ssg-minify/src/main/scala`.
3. **Spec coverage** — jekyll-minifier ships test fixtures under `spec/` and
   `tests/`; verify all are exercised by ssg-minify test runners. Also pull
   the underlying minifier reference suites (HtmlCompressor / CSSO / Terser
   test corpora) where applicable.
4. **Audit DB** — record gaps via `ssg-dev db audit set`.

### Known sub-component status

- **HTML**: ported, tested.
- **CSS**: ported, tested.
- **JSON**: ported, tested.
- **JS**: wired to `ssg-js` (Terser port) via `TerserJsCompressorAdapter` —
  see `ssg-js`'s own gap analysis (`js-port.md`).

### Definition of done

- Same minified output as jekyll-minifier on its full test suite.
- No production-path stubs or "basic only" comments.
