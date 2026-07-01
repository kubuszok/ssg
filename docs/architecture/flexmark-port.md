# flexmark-java Port to Scala 3

Post-mortem documentation for the flexmark-java → `ssg-md` porting effort.

## Overview

**Source**: flexmark-java 0.64.8 (Java, BSD-2-Clause)
**Target**: `ssg-md` module (Scala 3.8.2, cross-platform: JVM, Scala.js, Scala Native)
**Result**: ~790+ production files, 1645/1645 tests passing on JVM and Scala Native

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
| Regex Compatibility | 1 | 28 | 100% |
| Diagnostic (MdSuite) | 1 | 4 | 100% |
| **Total** | **55** | **1645** | **100%** |

All 1645 tests pass on both JVM and Scala Native.

## Cross-Platform Status

| Platform | Compile | Link | Tests |
|----------|---------|------|-------|
| JVM | Pass | Pass | 1645/1645 (100%) |
| Scala Native | Pass | Pass | 1645/1645 (100%) |
| Scala.js | Pass | Test link fails | Blocked by `getResourceAsStream` in test code |

### Cross-Platform Fixes Applied

All JVM-only APIs were replaced to make production code fully portable:

- **Nullable NestedNone**: `case class` → regular class (avoids `Product with Serializable`
  in opaque union type erasure — Scala Native's `Pattern` lacks `Serializable`)
- **17+ regex patterns**: lookaheads (`?=`, `?!`), Unicode categories (`\p{Pc}` etc.),
  character class intersection (`&&`), `\Q..\E` quoting — all rewritten with
  cross-platform alternatives and documented for future revert (scala-native#4810)
- **Abbreviation `\b` boundaries**: `UNICODE_CHARACTER_CLASS` flag → programmatic
  word boundary check (handles non-ASCII abbreviations like É.U.)
- **`Class.isInstance(null)`**: JVM returns false, Native NPEs → check `isDefined` first
- **BitFieldSet**: enum reflection → `EnumBitField[E]` type class with pre-computed masks
- **ThreadLocal**, **String.format(Locale)**, **java.util.Stack**, **java.net.URL**,
  **MessageFormat**, **Class.getPackage**, **StringBuilder.getChars** — all replaced
- **Test infrastructure**: `java.io.File`, `java.net.URL`, `Class.getResource` → string ops
- **Build config**: `embedResources=true`, `multithreading=false` for Native

### Scala.js Remaining Work

Scala.js test linking requires `Class.getResourceAsStream` which is not available in
the JS environment. Options: Node.js `fs` module for test resources, or embed spec
files as string constants. This is a test-only issue — production code links on JS.

## Migration & Audit Statistics

**Migration DB**: 871 ported, 179 skipped, 581 remaining (other libraries)
**Audit DB** (latest): 419 files audited — 387 pass, 32 minor issues, 0 major issues. Test coverage: 298 yes / 121 no.

---

## Gap Analysis (2026-04-07)

This section is the result of a measured (not estimated) audit of the ssg-md
port against the original flexmark-java sources. The earlier "99%+ complete"
framing in this document was based on test pass rate alone — passing tests only
prove what was tested, not what was implemented. The findings below identify the
gaps that block ssg-md from being a 1:1 functional replacement of flexmark-java.

### Methodology

- **LOC ratio**: per-module count of `*.java` (main only) under
  `original-src/flexmark-java/<module>/src/main/java` vs the corresponding
  `ssg-md/src/main/scala/ssg/md/<package>` LOC. Target ratio for an idiomatic
  Scala 3 port is ~0.55–0.95. ssg-md preserves Java structure faithfully (braces
  required, license headers, no aggressive collapsing) and clusters near 1.0 in
  most packages — useful as a *floor*, not a maximum.
- **Stub sweep**: targeted Grep over `ssg-md/src/main/scala` for `TODO`, `FIXME`,
  `HACK`, `???`, `UnsupportedOperationException`, `NotImplementedError`, and
  prose markers (`simplif`, `for now`, `placeholder`, `stub`, `not yet ported`).
- **Spec coverage**: cross-reference of every `.txt`/`.md` spec resource shipped
  in `ssg-md/src/test/resources` against actual loaders in
  `ssg-md/src/test/scala`.
- **Audit DB**: cross-check that files with confirmed gaps are reflected in
  `ssg-dev db audit`.

### LOC Ratio (measured)

| flexmark module | java LOC (main) | ssg-md package | scala LOC | ratio |
|---|---:|---|---:|---:|
| flexmark (core) | 23 217 | `ast`+`parser`+`html`+`formatter` | 21 757 | 0.94 |
| flexmark-util-sequence | 14 878 | `util/sequence` | (incl. in 30 506 util total) | ~1.0 |
| flexmark-util-format | 5 216 | `util/format` | — | ~1.0 |
| flexmark-util-misc | 3 802 | `util/misc` | — | ~1.0 |
| flexmark-util-ast | 3 161 | `util/ast` | — | ~1.0 |
| flexmark-util-collection | 2 920 | `util/collection` | — | ~1.0 |
| flexmark-util-html | 2 166 | `util/html` | — | ~1.0 |
| flexmark-util-data | 1 034 | `util/data` | — | ~1.0 |
| flexmark-util-dependency | 564 | `util/dependency` | — | ~1.0 |
| flexmark-util-options | 297 | `util/options` | — | ~1.0 |
| flexmark-util-visitor | 183 | `util/visitor` | — | ~1.0 |
| flexmark-util-builder | 177 | `util/builder` | — | ~1.0 |
| **util total** | **34 398** | `util/*` | **30 506** | **0.89** |
| flexmark-ext-toc | 2 508 | `ext/toc` | 1 968 | 0.78 |
| flexmark-ext-attributes | 1 931 | `ext/attributes` | 1 191 | **0.62** ⚠ |
| flexmark-ext-tables | 1 651 | `ext/tables` | 1 448 | 0.88 |
| flexmark-ext-enumerated-reference | 1 501 | `ext/enumerated` | 1 418 | 0.94 |
| flexmark-ext-gitlab | 1 135 | `ext/gitlab` | 970 | 0.85 |
| flexmark-ext-wikilink | 1 079 | `ext/wikilink` | 913 | 0.85 |
| flexmark-ext-definition | 980 | `ext/definition` | 925 | 0.94 |
| flexmark-ext-footnotes | 967 | `ext/footnotes` | 854 | 0.88 |
| flexmark-ext-macros | 919 | `ext/macros` | 772 | 0.84 |
| flexmark-ext-admonition | 831 | `ext/admonition` | 680 | 0.82 |
| flexmark-ext-jekyll-tag | 829 | `ext/jekyll/tag` | (incl. in 1 054) | 0.93* |
| flexmark-ext-emoji | 794 | `ext/emoji` | 717 | 0.90 |
| flexmark-ext-abbreviation | 764 | `ext/abbreviation` | 710 | 0.93 |
| flexmark-ext-gfm-strikethrough | 710 | `ext/gfm/strikethrough` | (incl. in 1 819) | 0.89* |
| flexmark-ext-typographic | 646 | `ext/typographic` | 640 | 0.99 |
| flexmark-ext-gfm-tasklist | 626 | `ext/gfm/tasklist` | — | 0.89* |
| flexmark-ext-media-tags | 494 | `ext/media` | 648 | 1.31 |
| flexmark-ext-aside | 445 | `ext/aside` | 439 | 0.99 |
| flexmark-ext-yaml-front-matter | 434 | `ext/yaml` | 440 | 1.01 |
| flexmark-ext-gfm-users | 360 | `ext/gfm/users` | — | 0.89* |
| flexmark-ext-jekyll-front-matter | 352 | `ext/jekyll/front` | — | 0.93* |
| flexmark-ext-gfm-issues | 350 | `ext/gfm/issues` | — | 0.89* |
| flexmark-ext-autolink | 314 | `ext/autolink` | 362 | 1.15 |
| flexmark-ext-superscript | 300 | `ext/superscript` | 250 | 0.83 |
| flexmark-ext-ins | 300 | `ext/ins` | 250 | 0.83 |
| flexmark-ext-escaped-character | 265 | `ext/escaped` | 284 | 1.07 |
| flexmark-ext-anchorlink | 256 | `ext/anchorlink` | 283 | 1.10 |
| flexmark-ext-resizable-image | 242 | `ext/resizable` | 234 | 0.97 |
| flexmark-ext-youtube-embedded | 227 | `ext/youtube` | 267 | 1.18 |

`*` jekyll-* and gfm-* extensions are bundled into shared parent packages —
ratios are aggregate across the cluster.

**Findings**: only one extension is significantly under-ported by LOC:
**flexmark-ext-attributes (0.62)**. All other ratios are within or above the
expected band. Note: a healthy ratio is necessary but not sufficient — TODOs and
stubs (below) are the more reliable signal.

### Production stubs and shortcuts (must-fix for 1.0)

These markers indicate code paths that are intentionally incomplete in
production source (`ssg-md/src/main/scala`). They are not test-harness TODOs.

**Major (functional gap)**:

1. `ext/attributes/internal/AttributesNodeFormatter.scala:21,36` —
   *"Full AttributesNodeFormatter port pending"* / *"implement full attribute
   formatting"*. Markdown→markdown formatting of `{#id .class}` blocks is not
   implemented. Aligns with the 0.62 LOC ratio for ext-attributes.
2. `ext/toc/internal/SimTocNodeFormatter.scala:21,34` —
   *"Full SimTocNodeFormatter port pending"* / *"implement SimToc formatting"*.
   Simulated-TOC formatter is a stub.
3. `ext/enumerated/reference/internal/EnumeratedReferenceNodeRenderer.scala:27`
   and `EnumeratedReferenceParagraphPreProcessor.scala:31` — fields marked
   `// stub: will be used when rendering is completed`. Enumerated references
   may not render with correct ordinals.
4. `formatter/MarkdownWriter.scala:79–84` — nested block-quote prefix handling
   is **simplified**. Round-trip formatting of nested `>` quotes will lose
   structure beyond one level.
5. `util/sequence/Escaping.scala:79,102,137` — explicit comments
   *"Simplified Replacer"*, *"stubs that will be filled in when Html5Entities is
   available"*, *"simplified version that handles backslash escapes only"*.
   Numeric/named HTML entity decoding in `Escaping` is degraded.
6. `util/sequence/Escaping.scala:576,581` — `normalizeEOL(BasedSequence,
   ReplacedTextMapper)` and `normalizeEndWithEOL(BasedSequence,
   ReplacedTextMapper)` throw `UnsupportedOperationException("not yet ported")`.
7. `ext/jekyll/tag/internal/IncludeNodePostProcessor.scala:54` —
   `FileUriContentResolver not yet ported - using configured factories only`.
   Jekyll `{% include %}` cannot resolve `file://` URIs.
8. `ext/emoji/internal/EmojiNodeFormatter.scala:35` and
   `ext/enumerated/reference/internal/EnumeratedReferenceNodeFormatter.scala:41`
   — `appendNonTranslating not yet ported - using append for now`. Translation
   round-trips will leak text instead of producing placeholders.

**Minor (edge case / quality)**:

9. `ast/Heading.scala:55` — *"are not yet ported; using defaults (trim both)
   for now"* — heading trim options ignored.
10. `ast/HtmlEntity.scala:37` — completion-marker hint missing.
11. `ext/autolink/internal/AutolinkNodePostProcessor.scala:46,317` — autolink
    optimization disabled with TODO.
12. `util/misc/Utils.scala:69` — utility methods to be rewritten on
    BasedSequence.
13. `parser/internal/DocumentParser.scala:316`,
    `util/sequence/SegmentedSequenceFull.scala:47`,
    `util/sequence/LineAppendableImpl.scala:528`,
    `html/HtmlRenderer.scala:64`,
    `ext/tables/internal/TableNodeFormatter.scala:168` — `// HACK:` comments
    inherited from upstream flexmark-java; verify each is a faithful port and
    not an SSG simplification.

**Not gaps** (intentional Java→Scala interop): the
`UnsupportedOperationException` in `util/collection/{MapEntry,OrderedMap,
OrderedMultiMap}.scala` and `util/ast/Document.scala` mirror Java's
unmodifiable-iterator pattern in the originals.

### Spec coverage (missing CommonMark conformance)

ssg-md ships **7 CommonMark spec files** as test resources but **none of them
are loaded by any test runner**:

| Spec file (`ssg-md/src/test/resources/ssg/md/test/specs/`) | Loaded by | Examples |
|---|---|---|
| `spec.txt` (current) | **NOT RUN** | ~650 |
| `spec.0.30.txt` | **NOT RUN** | ~649 |
| `spec.0.29.txt` | **NOT RUN** | ~624 |
| `spec.0.28.txt` | **NOT RUN** | ~624 |
| `spec.0.27.txt` | **NOT RUN** | ~613 |
| `spec.0.26.txt` | **NOT RUN** | ~613 |
| `com.vladsch.flexmark.test.specs.txt` | **NOT RUN** | — |

The only core runner — `ComboCoreSpecTest` — loads
`/ssg/md/core/test/ast_spec.md`, which is **flexmark's internal AST-based test
file**, not the normative CommonMark spec. ast_spec.md contains targeted
regression cases but is not a substitute for normative conformance.

**Implication**: the "1645/1645 tests passing" headline does **not** include
CommonMark spec conformance. ssg-md is *not* certified against any CommonMark
version. This is the single largest gap to close.

**Extension spec runners** *are* present and execute their `*_ast_spec.md`
files for: abbreviation, admonition, anchorlink, aside, attributes, autolink,
core, definition, emoji, escaped, footnotes, gfm-issues, gfm-strikethrough,
gfm-tasklist, gfm-users, gitlab, ins, jekyll-front-matter, jekyll-tag, macros,
superscript, tables, toc, sim-toc, typographic, wikilink, yaml-front-matter
(27 Combo*SpecTest classes). Missing Combo runner — only `*ExtensionSuite`
placeholder TODOs:

- `ext/enumerated/reference/EnumeratedReferenceExtensionSuite.scala`
- `ext/media/tags/MediaTagsExtensionSuite.scala`
- `ext/resizable/image/ResizableImageExtensionSuite.scala`
- `ext/youtube/embedded/YouTubeLinkExtensionSuite.scala`

These four extensions have spec `.md` files in `src/test/resources` but no
runner that compares rendered output against expected sections.

### Audit DB cross-check

Current audit DB state (from `ssg-dev db audit stats`):

- 419 of ~790 production files audited (53%)
- 387 pass / 32 minor / 0 major
- 121 audited files marked `tested: no`

The 13 production stubs above are not all reflected as `minor_issues` /
`major_issues` rows. Items #1, #2, #3 (AttributesNodeFormatter,
SimTocNodeFormatter, EnumeratedReferenceNodeRenderer) should be **`major_issues`**
because they leave whole formatter/renderer responsibilities unimplemented.

### Skipped-on-purpose modules (justified)

These flexmark-java modules are intentionally not ported. Listed for
completeness so future audits don't re-flag them:

| Module | Java LOC | Reason |
|---|---:|---|
| flexmark-pdf-converter | 153 (+ deps) | Output format; OpenHTMLToPDF JVM-only |
| flexmark-docx-converter | 9 478 | Output format; docx4j JVM-only |
| flexmark-html2md-converter | 4 144 | Reverse direction (HTML→MD), out of scope |
| flexmark-jira-converter | 457 | Output format, out of scope |
| flexmark-youtrack-converter | 500 | Output format, out of scope |
| flexmark-profile-pegdown | 254 | Legacy compatibility shim |
| flexmark-osgi | 0 (manifest) | OSGi bundling, no source |
| flexmark-all | 0 | Aggregator pom only |
| flexmark-util-experimental | 2 553 | Marked unstable upstream |
| flexmark-ext-spec-example | 1 232 | Flexmark dev tooling for spec authoring |
| flexmark-ext-zzzzzz | 1 266 | Extension template / dev tooling |
| flexmark-ext-xwiki-macros | 952 | XWiki output, out of scope |
| flexmark-tree-iteration | 1 501 | Generic tree utility, unused by core |
| flexmark-integration-test | 0 | Replaced by ssg-md test suites |

Total skipped: ~22 500 LOC.

### Definition of done for ssg-md 1.0

Ranked by impact:

1. **Wire CommonMark spec runners** for `spec.txt` and `spec.0.26..0.30.txt`
   in `ssg-md/src/test/scala/ssg/md/core/test/`. Goal: pass rate published per
   spec version. This is the headline gap.
2. **Finish AttributesNodeFormatter** (item #1 above) — round-trip markdown
   formatting of attribute blocks.
3. **Finish SimTocNodeFormatter** (item #2 above).
4. **Finish EnumeratedReferenceNodeRenderer / PreProcessor** (item #3 above).
5. **Implement nested block-quote prefix handling** in `MarkdownWriter`
   (item #4).
6. **Port `Html5Entities` and the `Escaping` ReplacedTextMapper variants**
   (items #5–#6).
7. **Implement `appendNonTranslating`** in MarkdownWriter; remove the two
   `// using append for now` shims in emoji/enumerated formatters (item #8).
8. **Port `FileUriContentResolver`** for Jekyll includes (item #7).
9. **Add Combo*SpecTest runners** for enumerated-reference, media-tags,
   resizable-image, youtube-embedded (the four placeholder ExtensionSuites).
10. **Audit the remaining ~370 unaudited files** and bring `tested: yes`
    coverage to 100%.
11. **Scala.js test resource loader** (separate, known effort).

After (1) is wired, the *real* ssg-md conformance number replaces the current
test-pass headline.
