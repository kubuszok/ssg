---
description: Load and apply the SSG code style rules including license headers, braces, split packages, and naming conventions
---

Load and follow the SSG code style rules.

$READ docs/contributing/code-style.md

Apply these rules to all SSG Scala files. Key rules:
- Apache 2.0 license header with original source attribution
- Braces required (`-no-indent`): `{}` for all trait, class, enum, method defs
- Split packages: `package ssg` / `package md` / `package core` (never flat)
- No `return`: use `scala.util.boundary`/`break`
- No `null`: use `Nullable[A]` opaque type
- All `case class` must be `final`
- No Java-style getters/setters
