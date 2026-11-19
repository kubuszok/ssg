---
description: Load the SSG-specific conversion rules (per-source-language docs, type mappings, package, Nullable and style conventions) for porting Java/Dart/Ruby source files to Scala 3; the general guides are in the re-scale-porting plugin
---

The general procedure lives in the `re-scale-porting` plugin:
`/re-scale-porting:re-scale-porting-guide-conversion` (with `-guide-code-style`,
`-guide-control-flow`, `-guide-nullable`, `-guide-verification`). Load it first; the SSG
rules below override it wherever they differ.

Load the SSG conversion rules for the source language of $ARGUMENTS (detect it from the
file extension .java, .dart, .rb or the containing directory).

For Java sources (flexmark-java, liqp):
$READ docs/contributing/conversion-rules-java.md

For Dart sources (dart-sass):
$READ docs/contributing/conversion-rules-dart.md

For Ruby sources (jekyll-minifier):
$READ docs/contributing/conversion-rules-ruby.md

Always load the type mappings (flexmark-java → ssg.md, liqp → ssg.liquid, dart-sass →
ssg.sass, jekyll-minifier → ssg.html, Java/Dart collections → Scala):
$READ docs/contributing/type-mappings.md

SSG conventions (details in the docs named):
- Code style, license header: `docs/contributing/code-style.md`. Split packages:
  `package ssg` / `package md` / `package core`, never flat.
- Nullable: `lowlevel.Nullable` from [lls](https://github.com/kubuszok/lls); two re-export
  files provide package-local aliases in ssg-md and ssg-sass. Patterns:
  `docs/contributing/nullable-guide.md`.
- Control flow (`boundary`/`break`): `docs/contributing/control-flow-guide.md`.
- Post-conversion checks and status progression: `docs/contributing/verification-checklist.md`.
