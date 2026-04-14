# SSG Code Style Guide

## License Header

Every Scala file must start with:

```scala
/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: <original-file-path>
 * Original: Copyright (c) <year> <original-author>
 * Original license: <license>
 *
 * Migration notes:
 *   Renames: <package/class renames>
 *   Convention: <notable convention changes>
 *   Idiom: <Scala idiom replacements>
 *   Audited: <date>
 */
```

## Formatting Rules

- **Braces required** (`-no-indent`): `{}` for all `trait`, `class`, `enum`, method defs
- **Split packages**: `package ssg` / `package md` / `package core` (never `package ssg.md.core`)
- **Max line length**: 200 characters (enforced by scalafmt)
- **Imports**: sorted alphabetically (AsciiSortImports)
- **Modifiers**: sorted (SortModifiers)

## Naming Conventions

- Classes/traits/objects: `PascalCase`
- Methods/values/variables: `camelCase`
- Constants: `PascalCase` (Scala convention) or `UPPER_SNAKE_CASE` if matching original
- Packages: `lowercase`
- Type parameters: single uppercase letter (`T`, `A`, `K`, `V`)

## Prohibited Patterns

- **No `return`**: use `scala.util.boundary`/`break`
- **No `null`**: use `Nullable[A]` opaque type
- **No `scala.Enumeration`**: use Scala 3 `enum`
- **No `orNull`**: except at Java interop boundaries (requires `@nowarn` + comment)
- **No Java-style getters/setters**: `getX()`/`setX(v)` → `var x` or `def x`/`def x_=`

## Required Patterns

- All `case class` declarations must be `final`
- All comments from the original source must be preserved
- Use `scala.compiletime.uninitialized` for uninitialized vars (not `= _`)

## Compiler Flags

```
-deprecation -feature -no-indent -Werror
-Wimplausible-patterns -Wrecurse-with-default -Wenum-comment-discard
-Wunused:imports,privates,locals,patvars,nowarn
```
