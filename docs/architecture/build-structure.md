# SSG Build Structure

## Overview

SSG uses sbt with sbt-projectmatrix for cross-platform compilation to JVM, Scala.js, and Scala Native.

## Module Layout

| Module | Directory | Purpose | Platforms |
|--------|-----------|---------|-----------|
| `ssg-md` | `ssg-md/` | Markdown engine (flexmark-java port) | JVM, JS, Native |
| `ssg-liquid` | `ssg-liquid/` | Liquid template engine (liqp port) | JVM, JS, Native |
| `ssg-sass` | `ssg-sass/` | SASS/SCSS compiler (dart-sass port) | JVM, JS, Native |
| `ssg-html` | `ssg-html/` | HTML/JS minification (jekyll-minifier port) | JVM, JS, Native |
| `ssg` | `ssg/` | Aggregator (depends on all 4 above) | JVM, JS, Native |

## sbt Project IDs

Each module generates 3 sbt subprojects:

| Module | JVM | Scala.js | Scala Native |
|--------|-----|----------|--------------|
| `ssg-md` | `ssg-md` | `ssg-mdJS` | `ssg-mdNative` |
| `ssg-liquid` | `ssg-liquid` | `ssg-liquidJS` | `ssg-liquidNative` |
| `ssg-sass` | `ssg-sass` | `ssg-sassJS` | `ssg-sassNative` |
| `ssg-html` | `ssg-html` | `ssg-htmlJS` | `ssg-htmlNative` |
| `ssg` | `ssg` | `ssgJS` | `ssgNative` |

Total: 15 sbt subprojects.

## Shared Settings

Defined in `project/SsgSettings.scala`:

- `scalaVersion`: 3.8.2
- `commonSettings`: Compiler flags, test framework, dependencies
- `jvmSettings`: Fork enabled
- `jsSettings`: (empty, for future Scala.js config)
- `nativeSettings`: (empty, for future Scala Native config)

## Source Layout

Each module follows the standard sbt layout:

```
ssg-md/
├── src/
│   ├── main/
│   │   └── scala/
│   │       └── ssg/
│   │           └── md/
│   │               ├── package.scala
│   │               ├── ast/
│   │               ├── parser/
│   │               ├── html/
│   │               └── ext/
│   └── test/
│       └── scala/
│           └── ssg/
│               └── md/
│                   └── MdSuite.scala
```

## Dependencies

### External
- MUnit 1.2.3 (test only)
- MUnit ScalaCheck 1.0.0 (test only)

### Internal
- `ssg` depends on `ssg-md`, `ssg-liquid`, `ssg-sass`, `ssg-html`

## Build Commands

```
ssg-dev build compile [--jvm] [--js] [--native] [--all] [--module M]
ssg-dev build compile-fmt
ssg-dev build fmt
ssg-dev build publish-local
ssg-dev build kill-sbt
```

Or directly via sbt:
```
sbt --client "ssg-md/compile"
sbt --client "ssg-mdJS/compile"
sbt --client "ssg-mdNative/compile"
```
