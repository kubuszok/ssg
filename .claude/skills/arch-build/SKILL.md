---
description: Load the sbt projectMatrix build structure and cross-platform settings reference
---

Load the SSG build structure reference.

$READ docs/architecture/build-structure.md

SSG uses sbt-projectmatrix 0.11.0 with 5 modules:
- ssg-md (JVM, JS, Native) — flexmark-java port
- ssg-liquid (JVM, JS, Native) — liqp port
- ssg-sass (JVM, JS, Native) — dart-sass port
- ssg-html (JVM, JS, Native) — jekyll-minifier port
- ssg (JVM, JS, Native) — aggregator depending on all 4

Settings are in project/SsgSettings.scala.
