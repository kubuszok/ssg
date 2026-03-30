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
| jekyll-minifier | Ruby | `ssg-html` | HTML/JS/CSS minification |

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
- Use `ssg-dev` commands or `sbt --client` — never bare `sbt`
- **sbt hangs on build.sbt errors**: kill with `ssg-dev proc kill-sbt`, fix, retry

## Project Structure

| Directory | Purpose |
|-----------|---------|
| `ssg-md/` | Markdown engine (flexmark-java port) |
| `ssg-liquid/` | Liquid template engine (liqp port) |
| `ssg-sass/` | SASS/SCSS compiler (dart-sass port) |
| `ssg-html/` | HTML/JS/CSS minification (jekyll-minifier port) |
| `ssg/` | Aggregator module (depends on all 4 above) |
| `scripts/` | `ssg-dev` CLI toolkit (Scala CLI, no sbt) |
| `scripts/data/` | TSV databases (migration, issues, audit) |
| `original-src/` | Reference sources (git submodules, not compiled) |
| `original-src/flexmark-java/` | Local flexmark-java reference |
| `original-src/liqp/` | Local liqp reference |
| `original-src/dart-sass/` | Local dart-sass reference |
| `original-src/jekyll-minifier/` | Local jekyll-minifier reference |
| `docs/` | Architecture, conversion guides |
| `project/` | sbt build configuration |

## CLI Toolkit: `ssg-dev`

**Use `ssg-dev` commands for all development tasks.** The PreToolUse hook validates
all Bash commands — if denied, use the suggested alternative.

**It's not `./ssg-dev`, your hooks add it to `$PATH`, and it's defined in `scripts/bin/ssg-dev`.**

| Command | Purpose |
|---------|---------|
| `ssg-dev build compile [--jvm/--js/--native/--all] [--module M]` | Compile |
| `ssg-dev build compile --errors-only` | Compile showing only errors |
| `ssg-dev build compile --warnings` | Compile showing warnings + errors |
| `ssg-dev build compile-fmt` | Compile, format, compile again |
| `ssg-dev build fmt` | Scalafmt |
| `ssg-dev build publish-local [--jvm/--js/--native/--all]` | Publish to local Maven |
| `ssg-dev build kill-sbt` | Kill sbt server |
| `ssg-dev test unit [--jvm/--js/--native/--all] [--module M] [--only SUITE]` | Unit tests |
| `ssg-dev test verify` | Full 3-platform verification |
| `ssg-dev quality scan [--return/--null/--todo/--java-syntax/--all] [--summary]` | Quality scans |
| `ssg-dev quality grep <pattern> [--count/--files-only]` | Code search |
| `ssg-dev quality scalafix <rule> [--file PATH]` | Run Scalafix rule |
| `ssg-dev compare file <path> [--lib L]` | Show original/SSG file paths |
| `ssg-dev compare package <pkg> [--lib L]` | List files in original package |
| `ssg-dev compare find <pattern> [--lib L]` | Find files in original source |
| `ssg-dev compare status [--lib L] [--module M]` | Porting status |
| `ssg-dev compare next-batch [-n N] [--lib L]` | Suggest next files to port |
| `ssg-dev git status/diff/log/blame/branch/tags` | Git read-only |
| `ssg-dev git stage/commit/push` | Git write |
| `ssg-dev git gh pr list/view/diff/checks` | GitHub PR operations |
| `ssg-dev git gh issue list/view` | GitHub issues |
| `ssg-dev db migration stats/list/get/set/sync` | Migration database |
| `ssg-dev db issues stats/list/add/resolve` | Issues database |
| `ssg-dev db audit stats/list/get/set` | Audit database |
| `ssg-dev proc list/kill/kill-sbt` | Process management |

Use `ssg-dev db` for all migration/issues/audit queries — never grep TSV files.

## Bash Restrictions

**The PreToolUse hook validates ALL Bash commands.** Only `ssg-dev`, `sbt --client`,
`git`, `cargo`, `npm`, `npx`, and `scala-cli` are allowed directly. All other commands
are denied or redirected to dedicated tools:

- **Denied**: `python`/`python3`, `kill`/`pkill`, `rm -rf`, `sbt` (without `--client`)
- **Redirected to tools**: `grep`→Grep, `find`/`ls`→Glob, `cat`/`head`/`tail`→Read, `sed`/`awk`→Edit
- **Use `ssg-dev`** for builds, tests, git, quality scans, process management, and database queries
- **Use dedicated tools** (`Grep`, `Glob`, `Read`, `Edit`) for code search and file operations

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

## Source Reference

Path mappings for each library:

| Library | Original Path | SSG Path |
|---------|--------------|----------|
| flexmark-java | `original-src/flexmark-java/<module>/src/main/java/com/vladsch/flexmark/` | `ssg-md/src/main/scala/ssg/md/` |
| liqp | `original-src/liqp/src/main/java/liqp/` | `ssg-liquid/src/main/scala/ssg/liquid/` |
| dart-sass | `original-src/dart-sass/lib/src/` | `ssg-sass/src/main/scala/ssg/sass/` |
| jekyll-minifier | `original-src/jekyll-minifier/lib/` | `ssg-html/src/main/scala/ssg/html/` |

**Never fetch from GitHub** — always use the local submodule copies.

## Audit System

Per-file audit trail comparing every SSG Scala file against its original source.
Each audited file gets a `Migration notes:` block in its header comment.

- **Commands**: `/audit-file <path>`, `/audit-package <pkg>`, `/audit-status`
- **Database**: `ssg-dev db audit stats`, `ssg-dev db audit list --package <pkg>`
- **Statuses**: `pass`, `minor_issues`, `major_issues`
- **In-file notes**: `Renames`, `Convention`, `Idiom`, `Audited` date

## Documentation

| Path | Content |
|------|---------|
| `docs/contributing/` | Conversion guides per language, code style, tooling |
| `docs/architecture/` | Build structure, cross-platform settings, module design |
| `scripts/data/` | TSV databases (migration, issues, audit) |
