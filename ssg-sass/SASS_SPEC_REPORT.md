# ssg-sass × sass-spec Compliance Report

This document records the first honest measurement of `ssg-sass` against
the official [sass-spec](https://github.com/sass/sass-spec) language
specification test suite.

## How it was measured

- **Harness**: `ssg-sass/src/test/scala-jvm/ssg/sass/SassSpecRunner.scala`
  (JVM-only, munit, always passes — this is a measurement, not an
  assertion).
- **Input corpus**: `original-src/sass-spec/spec/` at commit
  `39c45e7275efad1ead54a17cbeee282fdd8d81d3` (submodule).
- **Scope**: every self-contained test case — one loose `input.scss`
  with sibling `output.css`/`error`, plus entries inside HRX archives
  whose directory has no other `.scss`/`.sass` siblings and whose
  source has no `@use`/`@forward`/`@import` of a non-`sass:` URL.
  Multi-file tests (the majority of HRX archives) are skipped: we do
  not yet wire an in-memory importer across HRX entries.
- **Pipeline**: `Compile.compileString(source, style = Expanded)` only.
  Expanded output, default precision, no custom importers, no
  `options.yml` honoring.
- **Pass criteria**:
  - `Pass` — normalized CSS (trailing whitespace stripped, blank-line
    runs collapsed, trimmed) matches the expected `output.css`.
  - `ExpectedErrorOk` — dart-sass expected an error and we also threw.
  - Anything else is a failure.

Run it with: `ssg-dev test unit --jvm --module ssg-sass --only ssg.sass.SassSpecRunner`.
The runner writes per-failure details to
`ssg-sass/target/sass-spec-failures.txt`.

## Headline numbers

| Metric                        |  Count |     % |
|-------------------------------|-------:|------:|
| Total self-contained cases    | 11,797 | 100.0 |
| **Passing (total)**           |  **2,439** | **20.7** |
| &nbsp;&nbsp;exact-output match |  1,462 | 12.4 |
| &nbsp;&nbsp;expected-error match |  977 |  8.3 |
| Failing (total)               |  9,358 | 79.3 |

So the real baseline is **20.7 % sass-spec conformance** on
self-contained cases, on top of the 532 unit tests that already pass.
The remaining ~79 % of sass-spec's multi-file archives are not included
in the denominator at all.

## Failure categories

Counts are over the 9,358 failures only.

| Category                    | Count | Bucket | Notes |
|-----------------------------|------:|--------|-------|
| `wrong-output`              | 6,752 | Wrong output | Compiles, but serialized CSS differs from expected after normalization. Dominant bucket; mostly small formatting / numeric / selector / color drift. |
| `expected-error-not-raised` | 1,095 | Missing error | dart-sass expects an error, we silently compile. Validation-layer gaps. |
| `evaluator-error`           |   991 | Evaluator error | We threw a `SassException` on valid SCSS (the fingerprint isn't a parse keyword). Includes unsupported features surfaced as runtime errors. |
| `index-bounds`              |   233 | Uncaught | `IndexOutOfBoundsException` — a raw crash, not a `SassException`. Unchecked AST accesses. |
| `uncaught-SassFormatException` | 155 | Parse error | Parser rejected valid SCSS. Classified as uncaught because the exception name isn't `SassException`. |
| `whitespace-only`           |   111 | Whitespace diff | Output identical after stripping all whitespace — pure formatting drift. |
| `empty-output`              |    13 | Empty output | We produced no CSS at all while dart-sass produced some. |
| `uncaught-SassScriptException` | 3 | Uncaught | Internal SassScript exception escaped the compile pipeline. |
| `stack-overflow`            |     3 | Crash | Deep recursion in `visitContentRule` content-rule tracking. Known issue. |
| `no-such-element`           |     2 | Uncaught | `NoSuchElementException` on an `Option.get` or map lookup. |

### Bucket rollup (maps to the task's requested categories)

| Requested category                 | Count |
|------------------------------------|------:|
| Parse error (our parser rejects valid SCSS) | ~155 (`uncaught-SassFormatException`) |
| Wrong output (compiles but diffs) | 6,752 |
| Output whitespace diff only       |   111 |
| Evaluator error (threw on valid)  |   991 + 233 + 3 + 3 + 2 + 13 ≈ 1,245 (uncaught crashes + empty output) |
| Expected-error-not-raised         | 1,095 |
| Feature not implemented           | included in `wrong-output` and `evaluator-error` — not cleanly separable without manual triage |

## Caveats & what this does and doesn't measure

- **Only self-contained cases.** The corpus size (11,797) is a subset
  of sass-spec — multi-file HRX tests (`@use './other'`,
  `@forward './mid'`, fixtures loading siblings) are excluded. A later
  pass that builds an in-memory `MapImporter` per HRX directory will
  roughly double the denominator.
- **No options.yml honoring.** sass-spec tests can specify a precision,
  an output style (`compressed`), ignored warnings, dart-sass version
  gating, and `todo`/`ignore_for` flags. We run every case in
  expanded/default mode. This almost certainly inflates the
  `wrong-output` bucket.
- **No warning comparison.** dart-sass expected warnings in
  `warning` files are ignored entirely.
- **Normalization is lenient.** We strip trailing whitespace, collapse
  blank-line runs, and trim. Exact-byte conformance would be lower.
- **`expected-error-ok` is liberal.** We count any thrown exception as
  matching an expected error, without comparing the message. Strict
  matching would move some of these back into the failure set.
- **HRX filter is conservative.** Self-containment is detected
  syntactically (regex). Tests using built-in `sass:*` modules are
  correctly kept.

## Next steps (out of scope for this agent)

1. Fix the `visitContentRule` recursion bug that produces the
   `stack-overflow` bucket.
2. Wire an in-memory `MapImporter` per HRX archive so cross-file tests
   contribute to the denominator. This is the single biggest coverage
   multiplier.
3. Honor `options.yml`: `precision`, `output_style`, `ignore_for`.
4. Triage the `wrong-output` bucket by grouping diffs by first
   differing line shape (numeric, color, selector, at-rule) to find
   the highest-leverage fixes.
5. Normalize `uncaught-SassFormatException` / `index-bounds` /
   `SassScriptException` so parser/evaluator bugs surface as
   `SassException` with spans, not raw crashes.
