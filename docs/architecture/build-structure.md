# SSG Build Structure

## Overview

SSG uses sbt with sbt-projectmatrix for cross-platform compilation to JVM, Scala.js, and Scala Native.

## Module Layout

| Module | Directory | Purpose | Platforms |
|--------|-----------|---------|-----------|
| `ssg-commons` | `ssg-commons/` | Shared utilities | JVM, JS, Native |
| `ssg-md` | `ssg-md/` | Markdown engine (flexmark-java port) | JVM, JS, Native |
| `ssg-liquid` | `ssg-liquid/` | Liquid template engine (liqp port) | JVM, JS, Native |
| `ssg-sass` | `ssg-sass/` | SASS/SCSS compiler (dart-sass port) | JVM, JS, Native |
| `ssg-minify` | `ssg-minify/` | HTML/CSS/JS/JSON minification (jekyll-minifier port) | JVM, JS, Native |
| `ssg-js` | `ssg-js/` | JavaScript compiler/minifier (Terser port) | JVM, JS, Native |
| `ssg-highlight` | `ssg-highlight/` | Syntax highlighting (tree-sitter) | JVM, JS, Native |
| `ssg` | `ssg/` | Aggregator (depends on all above) | JVM, JS, Native |

## sbt Project IDs

Each module generates 3 sbt subprojects:

| Module | JVM | Scala.js | Scala Native |
|--------|-----|----------|--------------|
| `ssg-commons` | `ssg-commons` | `ssg-commonsJS` | `ssg-commonsNative` |
| `ssg-md` | `ssg-md` | `ssg-mdJS` | `ssg-mdNative` |
| `ssg-liquid` | `ssg-liquid` | `ssg-liquidJS` | `ssg-liquidNative` |
| `ssg-sass` | `ssg-sass` | `ssg-sassJS` | `ssg-sassNative` |
| `ssg-minify` | `ssg-minify` | `ssg-minifyJS` | `ssg-minifyNative` |
| `ssg-js` | `ssg-js` | `ssg-jsJS` | `ssg-jsNative` |
| `ssg-highlight` | `ssg-highlight` | `ssg-highlightJS` | `ssg-highlightNative` |
| `ssg` | `ssg` | `ssgJS` | `ssgNative` |

Total: 24 sbt subprojects (8 modules x 3 platforms).

## Shared Settings

Defined in `project/SsgSettings.scala`:

- `scalaVersion`: 3.8.2
- `commonSettings`: Compiler flags, test framework, dependencies
- `jvmSettings`: Fork enabled
- `jsSettings`: Scala.js configuration
- `nativeSettings`: Scala Native configuration

## Source Layout

Each module follows the standard sbt layout:

```
ssg-md/
├── src/
│   ├── main/
│   │   ├── scala/
│   │   │   └── ssg/
│   │   │       └── md/
│   │   │           ├── ast/
│   │   │           ├── parser/
│   │   │           ├── html/
│   │   │           └── ext/
│   │   ├── scalajvm/        (JVM-specific sources)
│   │   ├── scalajs/         (Scala.js-specific sources)
│   │   └── scalanative/     (Scala Native-specific sources)
│   └── test/
│       └── scala/
│           └── ssg/
│               └── md/
```

## Dependencies

### External
- MUnit (test only)

### Internal
- `ssg` depends on `ssg-commons`, `ssg-md`, `ssg-liquid`, `ssg-sass`, `ssg-minify`, `ssg-js`, `ssg-highlight`

## Build Commands

```
re-scale build compile [--jvm] [--js] [--native] [--all] [--module M]
re-scale build compile-fmt
re-scale build fmt
re-scale build publish-local [--module M] [--jvm/--js/--native/--all]
re-scale build kill-sbt
```

Or directly via sbt:
```
sbt --client "ssg-md/compile"
sbt --client "ssg-mdJS/compile"
sbt --client "ssg-mdNative/compile"
```
