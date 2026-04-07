# ssg-sass Architecture — dart-sass Port

## Overview

`ssg-sass` is the cross-platform SASS/SCSS compiler ported from
[dart-sass](https://github.com/sass/dart-sass) (Dart, MIT). Target: full
SASS specification compliance, no native binding, runs on JVM / Scala.js /
Scala Native.

Status: in progress (see `project_ssg_sass_port` memory).

## Gap Analysis (TODO — apply methodology from `flexmark-port.md`)

This section will be filled in when an LOC + stub + spec audit is run on
ssg-sass against `original-src/dart-sass/lib/src`. The procedure is the same
as the one applied to ssg-md in `flexmark-port.md`:

1. Per-package LOC ratio (`dart` files vs `scala` files), flag any package
   below ~0.55.
2. Stub sweep over `ssg-sass/src/main/scala` for `TODO|FIXME|HACK|???|
   UnsupportedOperationException|NotImplementedError|simplif|for now|stub|
   not yet ported`.
3. Spec coverage: list every test fixture under
   `original-src/dart-sass/test/` (the official sass-spec) and verify which
   are loaded by ssg-sass test runners. The dart-sass project pulls in the
   normative `sass-spec` repository — that should become the conformance
   target for ssg-sass.
4. Audit DB: every confirmed gap → `ssg-dev db audit set ... --status
   minor_issues|major_issues`. Major gaps → `ssg-dev db issues add`.

### Definition of done

- All sass-spec test cases run by ssg-sass with a published pass rate per
  category (parser, evaluator, color, math, modules, etc.).
- No production-path `UnsupportedOperationException` / `???` / "for now"
  shortcuts.
- LOC ratio per package within the expected band, or any deviation explained
  in this doc.
