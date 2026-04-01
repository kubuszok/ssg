# flexmark-java Port to Scala 3

Post-mortem documentation for the flexmark-java → `ssg-md` porting effort.

## Overview

**Source**: flexmark-java 0.64.8 (Java, BSD-2-Clause)
**Target**: `ssg-md` module (Scala 3.8.2, cross-platform: JVM, Scala.js, Scala Native)
**Result**: ~790+ production files, 1612/1612 tests passing across 33 test suites

## What Was Ported

### 43 Modules → Single `ssg-md` Module

All flexmark-java sub-modules were merged into a single `ssg-md` Scala module with
packages mirroring the original structure:

| Category | Modules | SSG Package | Files |
|----------|---------|-------------|-------|
| Utilities (11) | flexmark-util-{misc,visitor,collection,data,sequence,html,options,builder,ast,dependency,format} | `ssg.md.util.*` | ~299 |
| Core (1) | flexmark (ast, parser, html, formatter) | `ssg.md.{ast,parser,html,formatter}` | ~209 |
| Jekyll Extensions (9) | yaml-front-matter, jekyll-front-matter, jekyll-tag, tables, gfm-strikethrough, gfm-tasklist, autolink, emoji, typographic | `ssg.md.ext.*` | ~117 |
| Superset Extensions (16) | footnotes, abbreviation, definition, toc, attributes, anchorlink, aside, admonition, ins, superscript, escaped-character, wikilink, gitlab, macros, gfm-issues, gfm-users | `ssg.md.ext.*` | ~196 |
| Deferred Extensions (4) | enumerated-reference, media-tags, resizable-image, youtube-embedded | `ssg.md.ext.*` | ~55 |
| Test Infrastructure (3) | flexmark-test-util, flexmark-test-specs, flexmark-core-test | `ssg.md.test.*` | ~31 |

### 14 Modules Skipped

| Module | Reason |
|--------|--------|
| flexmark-osgi | OSGi irrelevant for Scala |
| flexmark-all | Aggregator POM only |
| flexmark-util-experimental | Unstable API, unused by core |
| flexmark-pdf-converter | JVM-only (OpenHTMLToPDF) |
| flexmark-docx-converter | JVM-only (docx4j) |
| flexmark-html2md-converter | Reverse direction, not needed for SSG |
| flexmark-jira-converter | JIRA markup not needed |
| flexmark-youtrack-converter | YouTrack markup not needed |
| flexmark-profile-pegdown | Backward compat API |
| flexmark-ext-zzzzzz | Test template extension |
| flexmark-ext-spec-example | flexmark dev tooling |
| flexmark-ext-xwiki-macros | XWiki-specific |
| flexmark-tree-iteration | Not imported by core or extensions |
| flexmark-integration-test | Integration test harness |

Per-extension skips: all `*JiraRenderer` and `*YouTrackRenderer` files (~12 files).

## Key Architectural Decisions

### 1. Single Module Architecture

flexmark-java uses ~43 Maven modules. SSG merges them into one `ssg-md` module because:
- sbt `projectMatrix` makes cross-platform builds simpler with fewer modules
- No need for separate JAR artifacts — SSG consumes everything internally
- Reduces build complexity and compilation time

### 2. Nullable[A] Opaque Type

Borrowed from SGE (`sge/src/main/scala/sge/utils/Nullable.scala`). An allocation-free
`Option` alternative using an opaque type with `NestedNone` sentinel for null tracking.

**Critical interaction**: `DataValueFactory.apply` returns `Nullable[T]`. The opaque type's
`NestedNone` value must NOT leak into Java `HashMap` storage. All `DataSet.getOrCompute`
and `MutableDataSet.getOrCompute` methods call `.orNull` before storing/returning.

### 3. BasedSequence F-Bounded Polymorphism

`IRichSequence[T <: IRichSequence[T]]` preserved faithfully from Java's
`IRichSequence<T extends IRichSequence<T>>`. No simplification attempted —
the segment builder and offset tracking depend on exact semantics.

### 4. JUnit4 → munit Test Adaptation

flexmark's test infrastructure is built on JUnit4's `@RunWith(Parameterized.class)`.
In munit, this becomes dynamic test registration:

```scala
abstract class SpecTestSuite extends munit.FunSuite {
  // Read spec file at init, register each example as a test
  examples.foreach { example =>
    test(s"${example.section} - ${example.exampleNumber}") {
      assertEquals(renderHtml(example, options), example.html)
    }
  }
}
```

### 5. StringSequenceBuilder Returns String

`StringSequenceBuilder.toSequence` returns `segments.toString` (immutable `String`)
instead of the mutable `StringBuilder` itself. This prevents aliasing bugs where
the returned sequence is mutated after being stored in `LineInfo`.

## Systemic Bugs Found During Porting

These bugs affected large numbers of tests and revealed fundamental Java→Scala
differences that apply to any similar porting effort.

### 1. StringBuilder.append Dispatch (ALL rendered output broken)

**Root cause**: Scala's `StringBuilder.append(CharSequence, Int, Int)` does not
correctly dispatch to `java.lang.StringBuilder.append(CharSequence, Int, Int)`
when the `CharSequence` argument comes from an opaque type (`Nullable.get`).
Instead of calling `charAt()` on the CharSequence, it calls `toString()` which
produced segment debug format like `((<,0,1)(h1,0,1)...)`.

**Fix**: Use `sb.underlying.append(cs, start, end)` to call the JDK method directly.

**Files affected**: StringSequenceBuilder, Escaping, IRichSequenceBase, RichSequenceBuilder,
DelimitedBuilder — anywhere a Scala `StringBuilder` receives a `CharSequence` argument.

### 2. Nullable Leaking into DataSet (122 tests broken)

**Root cause**: `DataValueFactory.apply` returns `Nullable[T]`. At runtime, `Nullable.empty`
is `NestedNone(0)` — an opaque type wrapper. When stored in `HashMap[DataKeyBase[?], AnyRef]`
via `getOrCompute`, the `NestedNone` object leaks into storage. Later
`.asInstanceOf[T]` on `NestedNone` causes `ClassCastException`.

**Fix**: `DataSet.getOrCompute` and `MutableDataSet.getOrCompute` call `.orNull` on
the factory result before storing/returning.

### 3. ne vs != for Sentinel Identity (2 tests, subtle)

**Root cause**: Java's `!=` is reference equality for objects. Scala's `!=` calls
`.equals()`. `BasedSequence.NULL` is a sentinel with empty content. A zero-length
`BasedSequence` at offset 7 has the same content as `NULL` — both are empty strings.
So `!=` returns `false` (they're "equal"), but `ne` returns `true` (different objects).

**Fix**: Use `ne`/`eq` for all sentinel identity checks against `BasedSequence.NULL`,
`Range.NULL`, etc.

### 4. Agent-Produced Stubs (0% → stubbed files)

**Root cause**: AI agents porting complex files (>500 lines) sometimes created
structurally correct code that compiled but had empty/stubbed method bodies.

**Affected files**: `InlineParserImpl` (~2000 lines, fully stubbed), `DocumentParser`
(incorporateLine drastically simplified), `ListBlockParser` (tryStart always returned none),
`HtmlBlockParser` (simplified), `CoreNodeFormatter` (stubbed), `FormatterUtils` (stubbed),
`AttributesNodePostProcessor` (stubbed), `TocNodeRenderer` (stubbed), `SimTocBlockParser` (stubbed).

**Fix**: Audit-then-test process: run tests per module, identify failing areas, audit
the specific files against Java originals, rewrite stubs to full implementations.

### 5. Html5Entities Init Order (322 tests silently skipped)

**Root cause**: Scala `object` fields initialize top-to-bottom. `NAMED_CHARACTER_REFERENCES`
(which calls `readEntities()`) was declared before `ENTITY_PATH` (used by `readEntities()`).
At init time, `ENTITY_PATH` was still `null`.

**Fix**: Reorder field declarations so `ENTITY_PATH` comes before its consumer.

### 6. boundary/break Scope Confusion (infinite loops, double execution)

**Root cause**: Java `return` exits the method; Java `break` exits the loop. Scala's
`boundary`/`break` exits the *nearest enclosing `boundary`*. When multiple nested
`boundary` blocks exist, `break` may exit the wrong one.

**Fix**: Use type-annotated breaks: `break[Int](0)` for method-level exit vs
`break[Unit](())` for loop-level exit. This forces the compiler to route the break
to the correct boundary.

## Patterns for Future Porters

1. **Audit complex files** (>200 lines) against originals after agent porting
2. **Run tests per-module** before moving to next module
3. **`sb.underlying.append()`** whenever `StringBuilder` + `CharSequence` from opaque types
4. **`ne`/`eq`** for sentinel identity checks, never `!=`/`==`
5. **`Nullable.orNull`** at Java interop boundaries (DataSet, collections)
6. **Type-annotated `break`** when multiple nested `boundary` blocks exist
7. **Field declaration order** matters in Scala `object` — dependencies must come first
8. **Java `==`/`!=` on objects** → Scala `eq`/`ne` for reference equality

## Test Results

| Suite Category | Suites | Tests | Pass Rate |
|---------------|--------|-------|-----------|
| Core CommonMark | 1 | 624 | 100% |
| Jekyll Extensions | 9 | 278 | 100% |
| Superset Extensions | 16 | 685 | 100% |
| Deferred Extensions | 4 | 16 | 100% |
| Basic/Smoke | 3 | 10 | 100% |
| **Total** | **33** | **1612** | **100%** |

## Migration & Audit Statistics

**Migration DB**: 871 ported, 179 skipped, 581 remaining (other libraries)
**Audit DB**: 298 files audited — 270 pass, 22 minor issues, 6 major issues
