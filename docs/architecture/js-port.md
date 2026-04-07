# ssg-js Architecture — Terser Port

## Overview

`ssg-js` is the cross-platform JavaScript compiler/minifier ported from
[Terser](https://github.com/terser/terser) (JavaScript, BSD-2-Clause). It
backs `ssg-minify`'s JS path. Target: pass the full Terser test suite so SSG
can ship JS minification as a single Scala Native binary.

## Gap Analysis (TODO — apply methodology from `flexmark-port.md`)

Procedure (same as ssg-md):

1. **LOC ratio** — `original-src/terser/lib/**/*.js` vs
   `ssg-js/src/main/scala/ssg/js/**/*.scala`. Expected scala/js ratio ≈ 1.0–1.5
   (JS is loose, Scala adds types and braces).
2. **Stub sweep** over `ssg-js/src/main/scala`.
3. **Spec coverage** — Terser ships extensive test corpora under
   `original-src/terser/test/` (compress.js, mocha specs, expected outputs).
   List every fixture and verify it is loaded by an ssg-js runner.
4. **Audit DB** — record gaps.

### Definition of done

- All Terser compress / mangle / parse / format tests pass under ssg-js on
  JVM, Scala.js and Scala Native.
- No production-path `???` / `UnsupportedOperationException` / "basic only"
  shortcuts.
- LOC ratio per AST/parser/compressor/mangler/output package within the
  expected band, or deviations explained.
