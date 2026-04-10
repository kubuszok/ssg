# CLAUDE.md

SSG (Scala Static Site Generator) is a cross-platform Scala 3 project porting
several libraries to create a Jekyll-like static site generator targeting JVM,
Scala.js, and Scala Native — without external binary dependencies.

## Libraries Being Ported

| Source Library | Language | SSG Module | Purpose |
|---------------|----------|------------|---------|
| flexmark-java | Java | `ssg-md` | Markdown engine |
| liqp | Java | `ssg-liquid` | Liquid template engine |
| dart-sass | Dart | `ssg-sass` | SASS/SCSS compiler |
| jekyll-minifier | Ruby | `ssg-minify` | HTML/JS/CSS/JSON minification |
| terser | JavaScript | `ssg-js` | JavaScript compiler/minifier |

## Build Rules

- Scala **3.8.2**, compiler flags: `-deprecation -feature -no-indent -rewrite -Werror`
- **Linter flags**: `-Wimplausible-patterns -Wrecurse-with-default -Wenum-comment-discard -Wunused:imports,privates,locals,patvars,nowarn`
- **Braces required** (`-no-indent`): `{}` for all `trait`, `class`, `enum`, method defs
- **Split packages**: `package ssg` / `package md` / `package core` (never flat)
- **No `return`**: use `scala.util.boundary`/`break`
- **No `null`**: use `Nullable[A]` opaque type. **Never use `orNull`** except at Java interop boundaries (requires `@nowarn` + comment)
- **No comment removal**: preserve all original comments
- **No `scala.Enumeration`**: use Scala 3 `enum`, preferably `extends java.lang.Enum`
- **Case classes must be `final`**: all `case class` declarations require `final`
- **No Java-style getters/setters**: no-logic `getX()`/`setX(v)` → public `var x`; with-logic → `def x: T` + `def x_=(v: T): Unit`
- **Fix bugs, don't work around them**: when a test reveals a pre-existing bug, fix it in source
- **All 3 platforms are baseline**: JVM, JS, Native — changes must be non-regressing on all
- Use `re-scale` commands or `sbt --client` — never bare `sbt` (avoids the JVM startup tax on every invocation)
- **sbt server stuck?** kill with `re-scale proc kill --kind sbt --dir .`, fix the cause, retry

## Project Structure

| Directory | Purpose |
|-----------|---------|
| `ssg-md/` | Markdown engine (flexmark-java port) |
| `ssg-liquid/` | Liquid template engine (liqp port) |
| `ssg-sass/` | SASS/SCSS compiler (dart-sass port) |
| `ssg-minify/` | HTML/JS/CSS/JSON minification (jekyll-minifier port) |
| `ssg-js/` | JavaScript compiler/minifier (Terser port) |
| `ssg/` | Aggregator module (depends on all 4 above) |
| `.rescale/` | Per-project re-scale config + data |
| `.rescale/data/` | TSV databases (migration, issues, audit, skip-policy, sass-spec-baseline, port-tasks) |
| `.rescale/claude-hooks.yaml` | (optional) per-project hook overrides |
| `.rescale/doctor.yaml` | (optional) dev-environment bootstrap steps |
| `.rescale/runners.yaml` | (optional) test-runner adapters (e.g. sass-spec) |
| `original-src/` | Reference sources (git submodules, not compiled) |
| `original-src/flexmark-java/` | Local flexmark-java reference |
| `original-src/liqp/` | Local liqp reference |
| `original-src/dart-sass/` | Local dart-sass reference |
| `original-src/jekyll-minifier/` | Local jekyll-minifier reference |
| `docs/` | Architecture, conversion guides |
| `project/` | sbt build configuration |

## CLI Toolkit: `re-scale`

**Use `re-scale` commands for all development tasks.** The PreToolUse hook
delegates to `re-scale hook`, which validates every Bash command — if
denied, use the suggested alternative.

Source repo: <https://github.com/kubuszok/re-scale>. Install via
`scripts/install.sh` from a clone of that repo (builds the Scala
Native binary + wrapper and copies them into `$HOME/bin/`).

| Command | Purpose |
|---------|---------|
| `re-scale build compile [--module M] [--jvm/--js/--native/--all] [--errors-only]` | Compile via `sbt --client` |
| `re-scale build compile-fmt` | Run scalafmt then compile |
| `re-scale build fmt` | Run `scalafmtAll` |
| `re-scale build publish-local [--module M] [--jvm/--js/--native/--all]` | Publish to local Maven |
| `re-scale build kill-sbt` | Shut down the sbt server |
| `re-scale test unit [--module M] [--jvm/--js/--native/--all] [--only SUITE]` | Run unit tests |
| `re-scale test verify` | Compile every module on every platform (JVM × JS × Native) |
| `re-scale enforce shortcuts [--src DIRS] [--file F] [--covenanted]` | Scan for shortcut/stub markers |
| `re-scale enforce stale-stubs [--src DIRS]` | Two-pass scan for stale "not yet ported" comments |
| `re-scale enforce verify --file <path> \| --all` | Re-verify covenanted file(s) |
| `re-scale enforce skip-policy [list \| add <path> <tool>]` | Manage the skip-policy allow list |
| `re-scale enforce compare --port <scala> --source <java\|dart> [--strict]` | Cross-language method-set + body comparison |
| `re-scale git status/diff/log/blame/branch/tags` | Git read-only |
| `re-scale git stage/commit/push` | Git write |
| `re-scale git gh pr list/view/diff/checks` | GitHub PR operations |
| `re-scale git gh issue list/view` | GitHub issues |
| `re-scale db migration list/get/set/stats` | Migration database |
| `re-scale db issues list/add/resolve/stats` | Issues database |
| `re-scale db audit list/get/set/stats` | Audit database |
| `re-scale db merge --target <tsv> --source <tsv> [--strategy ...]` | Cross-branch TSV reconciliation |
| `re-scale proc list [--kind sbt\|java\|metals] [--dir DIR]` | List sbt/java/metals processes with cwd |
| `re-scale proc kill --pid N \| --kind ... --dir DIR` | Targeted process termination |
| `re-scale doctor [--ci]` | Run `.rescale/doctor.yaml` bootstrap steps |
| `re-scale runner <name> [--mode MODE] [args...]` | Dispatch a runner from `.rescale/runners.yaml` |

Use `re-scale db` for all migration/issues/audit queries — never read TSVs by hand.

### Sass-port workflow note

The legacy `ssg-dev port list/next/baseline/done/blocker/note/snapshot/report`
task workflow (driven by `.rescale/data/port-tasks.tsv` and the SassSpec
runner) is sass-port-specific and **not** in re-scale's core. The TSV
files have been moved to `.rescale/data/` for consistency, but until
either (a) a `re-scale tasks` generic task module ships or (b) the
sass-spec workflow gets wired through `.rescale/runners.yaml`, the
sass-port branch will need to either work around it manually or build
its own thin wrapper. See `re-scale/docs/cross-flavor-diff.md` for
the rationale.

## Bash Restrictions

**The PreToolUse hook validates ALL Bash commands.** Only `re-scale`,
`sbt --client`, `git`, `cargo`, `npm`, `npx`, and `scala-cli` are allowed
directly. All other commands are denied or redirected to dedicated tools:

- **Denied**: `python`/`python3`, `kill`/`pkill`, `rm -rf`, `sbt` (without `--client`)
- **Redirected to tools**: `grep`→Grep, `find`/`ls`→Glob, `cat`/`head`/`tail`→Read, `sed`/`awk`→Edit
- **Use `re-scale`** for builds, tests, git, process management, enforcement, and database queries
- **Use dedicated tools** (`Grep`, `Glob`, `Read`, `Edit`) for code search and file operations

Per-project rule overrides live at `.rescale/claude-hooks.yaml`.

## Skill Dispatch Rules

Load the relevant skill when working on specific areas:

| Context | Skill to load |
|---------|---------------|
| Converting a Java/Dart/Ruby file to Scala | `/guide-conversion` |
| Code style, license headers, formatting | `/guide-code-style` |
| Replacing return/break/continue | `/guide-control-flow` |
| Nullable patterns, null safety | `/guide-nullable` |
| Source→SSG class/package renames | `/guide-type-mappings` |
| Post-conversion verification | `/guide-verification` |
| sbt build config, projectMatrix | `/arch-build` |
| Cross-platform settings | `/arch-cross-platform` |
| Regex cross-platform (re2/JS) | `/guide-regex` |
| Auditing a file | `/audit-file <path>` |
| Auditing a package | `/audit-package <pkg>` |
| Migration progress | `/check-progress` |
| Finding code issues | `/find-issues` |

## Conversion Guides

- [Java conversion rules](docs/contributing/conversion-rules-java.md) — Java→Scala 3 (flexmark, liqp)
- [Dart conversion rules](docs/contributing/conversion-rules-dart.md) — Dart→Scala 3 (dart-sass)
- [Ruby conversion rules](docs/contributing/conversion-rules-ruby.md) — Ruby→Scala 3 (jekyll-minifier)
- [Type mappings](docs/contributing/type-mappings.md) — package/class renames per library
- [Code style](docs/contributing/code-style.md) — license header template, formatting
- [Nullable guide](docs/contributing/nullable-guide.md) — `Nullable[A]` opaque type
- [Control flow guide](docs/contributing/control-flow-guide.md) — `boundary`/`break` patterns
- [Verification checklist](docs/contributing/verification-checklist.md) — post-conversion checks
- [Cross-platform regex](docs/contributing/cross-platform-regex.md) — re2/JS regex limitations and workarounds

## Source Reference

Path mappings for each library:

| Library | Original Path | SSG Path |
|---------|--------------|----------|
| flexmark-java | `original-src/flexmark-java/<module>/src/main/java/com/vladsch/flexmark/` | `ssg-md/src/main/scala/ssg/md/` |
| liqp | `original-src/liqp/src/main/java/liqp/` | `ssg-liquid/src/main/scala/ssg/liquid/` |
| dart-sass | `original-src/dart-sass/lib/src/` | `ssg-sass/src/main/scala/ssg/sass/` |
| jekyll-minifier | `original-src/jekyll-minifier/lib/` | `ssg-minify/src/main/scala/ssg/minify/` |
| terser | `original-src/terser/lib/` | `ssg-js/src/main/scala/ssg/js/` |

**Never fetch from GitHub** — always use the local submodule copies.

## Audit System

Per-file audit trail comparing every SSG Scala file against its original source.
Each audited file gets a `Migration notes:` block in its header comment.

- **Commands**: `/audit-file <path>`, `/audit-package <pkg>`, `/audit-status`
- **Database**: `re-scale db audit stats`, `re-scale db audit list --package <pkg>`
- **Statuses**: `pass`, `minor_issues`, `major_issues`
- **In-file notes**: `Renames`, `Convention`, `Idiom`, `Audited` date

## Documentation

| Path | Content |
|------|---------|
| `docs/contributing/` | Conversion guides per language, code style, tooling |
| `docs/architecture/` | Build structure, cross-platform settings, module design |
| `.rescale/data/` | TSV databases (migration, issues, audit, skip-policy, sass-spec-baseline, port-tasks) |
