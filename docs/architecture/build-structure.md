# SSG Build Structure

## Overview

SSG uses sbt with sbt-projectmatrix for cross-platform compilation to JVM, Scala.js, and Scala Native.

## Module Layout

| Module | Directory | Purpose | Platforms |
|--------|-----------|---------|-----------|
| `ssg-commons` | `ssg-commons/` | Shared utilities | JVM, JS, Native |
| `ssg-data-commons` | `ssg-data-commons/` | Shared data view abstractions | JVM, JS, Native |
| `ssg-graphs-commons` | `ssg-graphs-commons/` | Shared graph layout + SVG infrastructure | JVM, JS, Native |
| `ssg-graphviz` | `ssg-graphviz/` | Graphviz DOT renderer (4 layout engines) | JVM, JS, Native |
| `ssg-highlight` | `ssg-highlight/` | Syntax highlighting (tree-sitter) | JVM, JS, Native |
| `ssg-js` | `ssg-js/` | JavaScript compiler/minifier (Terser port) | JVM, JS, Native |
| `ssg-katex` | `ssg-katex/` | Math typesetting engine (KaTeX port) | JVM, JS, Native |
| `ssg-liquid` | `ssg-liquid/` | Liquid template engine (liqp port) | JVM, JS, Native |
| `ssg-md` | `ssg-md/` | Markdown engine (flexmark-java port) | JVM, JS, Native |
| `ssg-mermaid` | `ssg-mermaid/` | Diagramming engine (Mermaid port) | JVM, JS, Native |
| `ssg-minify` | `ssg-minify/` | HTML/CSS/JS/JSON minification (jekyll-minifier port) | JVM, JS, Native |
| `ssg-sass` | `ssg-sass/` | SASS/SCSS compiler (dart-sass port) | JVM, JS, Native |
| `ssg-site` | `ssg-site/` | Site pipeline (SSG-native glue) | JVM, JS, Native |
| `ssg` | `ssg/` | Aggregator (depends on all above) | JVM, JS, Native |

## sbt Project IDs

Each module generates 3 sbt subprojects (JVM, Scala.js, Scala Native):

| Module | JVM | Scala.js | Scala Native |
|--------|-----|----------|--------------|
| `ssg-commons` | `ssg-commons` | `ssg-commonsJS` | `ssg-commonsNative` |
| `ssg-data-commons` | `ssg-data-commons` | `ssg-data-commonsJS` | `ssg-data-commonsNative` |
| `ssg-graphs-commons` | `ssg-graphs-commons` | `ssg-graphs-commonsJS` | `ssg-graphs-commonsNative` |
| `ssg-graphviz` | `ssg-graphviz` | `ssg-graphvizJS` | `ssg-graphvizNative` |
| `ssg-highlight` | `ssg-highlight` | `ssg-highlightJS` | `ssg-highlightNative` |
| `ssg-js` | `ssg-js` | `ssg-jsJS` | `ssg-jsNative` |
| `ssg-katex` | `ssg-katex` | `ssg-katexJS` | `ssg-katexNative` |
| `ssg-liquid` | `ssg-liquid` | `ssg-liquidJS` | `ssg-liquidNative` |
| `ssg-md` | `ssg-md` | `ssg-mdJS` | `ssg-mdNative` |
| `ssg-mermaid` | `ssg-mermaid` | `ssg-mermaidJS` | `ssg-mermaidNative` |
| `ssg-minify` | `ssg-minify` | `ssg-minifyJS` | `ssg-minifyNative` |
| `ssg-sass` | `ssg-sass` | `ssg-sassJS` | `ssg-sassNative` |
| `ssg-site` | `ssg-site` | `ssg-siteJS` | `ssg-siteNative` |
| `ssg` | `ssg` | `ssgJS` | `ssgNative` |

Total: 42 sbt subprojects (14 modules x 3 platforms).

## Shared Settings

Defined in `build.sbt` (`commonSettings`) and `project/Versions.scala`:

- `scalaVersion`: 3.8.4 (from `Versions.scala3` in `project/Versions.scala`)
- `commonSettings`: Compiler flags, test framework, dependencies (in `build.sbt`)
- Platform-specific settings are applied per-module via `MatrixAction.ForPlatforms` in `build.sbt`

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
- `ssg-data-commons` depends on `ssg-commons`
- `ssg-graphs-commons` depends on `ssg-commons`
- `ssg-graphviz` depends on `ssg-commons`, `ssg-graphs-commons`
- `ssg-highlight` depends on `ssg-commons`, `ssg-md`
- `ssg-js` depends on `ssg-commons`
- `ssg-katex` depends on `ssg-commons`
- `ssg-liquid` depends on `ssg-commons`, `ssg-data-commons`
- `ssg-md` depends on `ssg-commons`
- `ssg-mermaid` depends on `ssg-commons`, `ssg-data-commons`, `ssg-graphs-commons`
- `ssg-minify` depends on `ssg-commons`
- `ssg-sass` depends on `ssg-commons`
- `ssg-site` depends on `ssg-commons`, `ssg-data-commons`, `ssg-js`, `ssg-liquid`, `ssg-md`, `ssg-minify`, `ssg-sass`
- `ssg` depends on all 13 library modules

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
