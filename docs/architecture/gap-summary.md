# SSG Gap Summary — Unified Audit Report (2026-04-07)

## Context

This document is the synthesis of a full-codebase audit campaign across the four
SSG library ports (`ssg-md`, `ssg-liquid`, `ssg-minify`, `ssg-js`) against their
respective original sources (`flexmark-java`, `liqp`, `jekyll-minifier`, `terser`).
Nine parallel audit agents read every in-scope file, re-verified files previously
marked `pass` from lenient earlier audits, and persisted findings into the audit
and issues databases. The goal is a single actionable picture of where SSG stands
vs. its upstream libraries so that remediation can be planned, not just triaged.

The campaign was motivated by the observation that the "1645 tests passing"
headline didn't match the reality of stubs, `TODO`s, `// break` comments
replacing `break` statements, `Nullable.empty.getOrElse(null).asInstanceOf[…]`
casts, and whole formatter responsibilities reduced to `/* would do X here */`.
Each per-package audit was instructed to distrust pre-existing `pass` verdicts
and re-verify; this approach surfaced **dozens of major bugs in files that were
previously marked clean**.

---

## Scope and coverage

| Module | Files on disk | In audit DB | Re-verified this pass |
|---|---:|---:|---|
| `ssg-md` | 770 | 768 | full (parser/html/formatter/ast + util + all ext/*) |
| `ssg-liquid` | 121 | 121 | **partial** — 11 root files only; filters/blocks/tags/nodes/exceptions/antlr re-dispatch in progress |
| `ssg-minify` | 13 | 13 | full |
| `ssg-js` | 40 | 40 | full |
| **Total** | **944** | **942** | 2 files still missing in DB (ISS-001 ArrayUtils; ISS-dummy cleaned) |

**Test coverage (audit DB `tested:` flag):** 636 yes / 276 no / 30 partial.
Files without tests are concentrated in ssg-liquid filters, ssg-md ext/ helper
classes, and all of ssg-js/compress.

---

## Overall status

| | ssg-md | ssg-liquid | ssg-minify | ssg-js | **Total** |
|---|---:|---:|---:|---:|---:|
| pass | 677 | 97 | 3 | 26 | **803** |
| minor_issues | 72 | 19 | 10 | 5 | **106** |
| major_issues | 19 | 5 | 0 | 9 | **33** |
| **Total** | **768** | **121** | **13** | **40** | **942** |
| % pass | 88.2% | 80.2% | 23.1% | 65.0% | 85.2% |
| % major | 2.5% | 4.1% | 0% | 22.5% | 3.5% |

**Issues DB: 98 open issues** (ISS-001..ISS-097, renumbered IDs).
Severity after normalization¹: 47 high, 27 medium, 24 low.

¹ *A few agents used non-canonical `major`/`minor` severity labels instead of
`high`/`low`. 17 rows have `severity=major`, 9 have `severity=minor`. These will
be normalized in a follow-up pass.*

---

## Per-module summary

### ssg-md (flexmark-java port)

- **Core packages** (parser/html/formatter/ast): **4 previously-pass-marked
  files contain silent major bugs**. This was the single most alarming finding
  of the campaign — validates the re-verify-everything approach.
- **util packages**: solid. 252 files, 236 pass, 15 minor (mostly cosmetic
  getter conventions), 1 major (Escaping, already known).
- **Extensions**: tables/gitlab/abbreviation essentially clean; attributes/toc
  have known stubs; gfm-tasklist / jekyll-tag / emoji / autolink have multiple
  silent correctness bugs introduced by the port; the "deferred set"
  (enumerated-reference / media-tags / resizable-image / youtube-embedded) is
  actually *better* than the old audit notes claimed, but has test-coverage
  gaps.
- **Spec conformance**: still zero. ssg-md ships seven CommonMark `spec.*.txt`
  files as resources but no runner loads them; only the flexmark-internal
  `ast_spec.md` executes. This is ISS-002, the single largest correctness risk.

### ssg-liquid (liqp port)

- **Template / TemplateParser / Where / Date** rewrites all confirmed stubs or
  partial ports (ISS-007..030, from earlier gap analysis).
- **Root files** (LValue, Insertions, etc.) re-verified this pass — mostly OK,
  one cross-platform PlainBigDecimal edge case noted.
- **Filters / blocks / tags / nodes / exceptions / antlr packages**: the
  initial re-audit agent was rate-limited after ~11 files. A narrower
  re-dispatch is in progress. Until it reports, the ~97 `pass` rows in these
  packages should be treated as **unverified** — the earlier audit was
  lenient and similar problems to ssg-md are likely.

### ssg-minify (jekyll-minifier port)

- 0 major issues. Port covers structural shape of jekyll-minifier.
- 10/13 files have minor issues; all are option-fidelity gaps (flags declared
  in `MinifyOptions` but never consumed by the corresponding minifier, missing
  htmlcompressor/cssminify2 options, `compress_*` toggles dead, license-comment
  preservation missing, quote-stripping over-conservative).
- No file has test coverage. ISS-036-style fixture-driven coverage would catch
  most of these automatically.

### ssg-js (terser port)

- **Worst state by ratio** — 22.5% of files (9/40) are major_issues. Every file
  in `compress/` is either major or minor.
- Root causes are structural, not per-file:
  - `TreeTransformer` is an alias of `TreeWalker` (ISS-057) — the whole
    `transform.js` dispatch is unported
  - `Compressor.compress` is a 22-line orchestrator that never invokes
    `ScopeAnalysis`/`ReduceVars`/`GlobalDefs`
  - `SymbolDef` integration is missing (ISS-032), so DCE/inlining/constant
    folding all silently no-op
  - `OutputStream.getSymbolName` ignores `SymbolDef.mangledName`, so name
    mangling has zero output effect even when `Mangler` runs
  - `optimizeTree` recurses into only ~10 node types — optimizations never
    fire inside loops, switches, classes, or function bodies
- `ast/` and `parse/` are mostly clean. The broken seam is the cross-package
  wiring.
- No Terser fixture tests ingested (ISS-036). 168 upstream fixtures exist and
  use a simple DSL that would be trivial to parse.

---

## Top 20 gaps by impact

Ranked by *correctness blast radius* — each row lists the ISS-ID, module,
severity, and one-line fix description. Items marked † are silent correctness
bugs in `pass`-previously-marked files (highest priority to fix).

| # | ISS | Module | Severity | Gap |
|---|---|---|---|---|
| 1 | **ISS-097** † | ssg-md | major | `Formatter.collectedNodes` never populated — `nodesOfType()`/`reversedNodesOfType()` silently return `NULL_ITERABLE`, breaking any formatter that depends on pre-collected nodes (reference repository, TOC, footnotes). Stub comment: `/* collectNodeTypes would be used here */`. |
| 2 | **ISS-093** † | ssg-md | major | `HtmlRenderer` captures `renderersList = nodeRenderers.toList` **before** populating it, then passes the frozen empty list to every `DelegatingNodeRendererFactoryWrapper`. Dependency-ordering between delegating renderers is silently broken. |
| 3 | **ISS-094** † | ssg-md | major | `DocumentParser.preProcessParagraph` (L661,677) uses `continue = false // return` which only affects the outer `while`; the inner `for (factory ← stage)` keeps calling `factory.preProcessBlock` **on the detached block** after unlink. |
| 4 | **ISS-095** † | ssg-md | major | `RefNode.getReferenceNode(Document)` returns `null` with stale comment "Parser.REFERENCES not yet ported" — but it *is* now ported. Reference resolution from Document context is silently broken. |
| 5 | **ISS-002** | ssg-md | high | No CommonMark spec runner wired. 7 `spec.*.txt` files ship as resources; none are loaded. ssg-md is not certified against any CommonMark version — this is the biggest single correctness risk in the project. |
| 6 | **ISS-055** | ssg-md | high | `TocUtils` is missing 5 markdown-side helpers (`getTocPrefix`, `getSimTocPrefix`, `renderTocContent`, `markdownHeaderTexts`, `renderMarkdownToc`). **Blocks both ISS-003 and ISS-004** — fix this first. |
| 7 | **ISS-003** | ssg-md | high | `AttributesNodeFormatter` is a stub; markdown round-trip of `{#id .class}` blocks is broken. Blocked by ISS-055. |
| 8 | **ISS-004** | ssg-md | high | `SimTocNodeFormatter` is a stub. Blocked by ISS-055. |
| 9 | **ISS-057** | ssg-js | high | `TreeTransformer` is an alias of `TreeWalker`. Entire `transform.js` dispatch (~50 `def_transform` clauses, 323 LOC) unported. Foundational gap. |
| 10 | **ISS-032** | ssg-js | high | `SymbolDef`/scope integration missing across all compress passes. Blocks DCE, inlining, constant folding, pure-call elision. |
| 11 | **ISS-031** | ssg-js | high | Multi-pass compression loop unimplemented (`Compressor.scala:1021-1033`). Single-pass only; compression tests disabled because the loop hangs. `TerserJsCompressor` is effectively parse+re-emit. |
| 12 | **ISS-059** | ssg-js | high | `OutputStream.getSymbolName` ignores `SymbolDef.mangledName`. Name mangling produces **zero output difference** even when Mangler runs. |
| 13 | **ISS-084/086** † | ssg-md | high | `IncludeNodePostProcessor` has both break-after-resolve semantics broken (loops keep running after a hit) **and** `DependencyResolver.resolveFlatDependencies` never invoked (topological ordering lost). Jekyll `{% include %}` unreliable. |
| 14 | **ISS-045** † | ssg-md | high | `AutolinkNodePostProcessor`: when `node.parent` is already `TextBase`, Java falls back to `node.insertBefore(...)`; Scala uses `textBase.foreach(_.appendChild(...))` which **silently no-ops** when `textBase` is empty `Nullable`. Silent loss of nodes. |
| 15 | **ISS-066** † | ssg-md | high | `EnumeratedReferenceNodeFormatter` has three integration gaps with `AttributesNodeFormatter`: `null` passed for `ATTRIBUTE_UNIQUIFICATION_CATEGORY_MAP`, `getEncodedIdAttribute` not invoked, missing `getAfterDependents` override. Category uniquification silently disabled. |
| 16 | **ISS-016** | ssg-liquid | high | `filters/Where.scala` inline rewrite missing dotted paths, POJO support, `nil`/`empty`/`blank` handling, collection-property matching, numeric coercion. Entire `filters/where/*` (5 files) skipped. |
| 17 | **ISS-017/018** | ssg-liquid | high | `filters/Date.scala`: ISO-8601-only parsing (original supports 63 fallback patterns), ~15 strftime directives missing. `filters/date/*` (5 files) skipped. |
| 18 | **ISS-007..011** | ssg-liquid | high | `Template.scala` at 14% size ratio vs Java. Missing render-time guard, max-template-size, `errors()`, multiple render overloads, `REGISTRY_ROOT_FOLDER` (breaks `include_relative`), env-map configurator; `EAGER` vs `LAZY` identical. |
| 19 | **ISS-058** | ssg-js | high | `GlobalDefs.resolveDefs` is dead code: never called from `Compressor.compress`, and even if called uses `walk` instead of `transform` so before-callback replacements are discarded. `global_defs` option silently no-ops. |
| 20 | **ISS-068** † | ssg-md | major | `QuoteDelimiterProcessorBase` opener/closer loops diverge from Java — Java returns on **first** matching delimiter; Scala keeps searching. Plus `return true` (`// scalastyle:ignore`) left over from incomplete return→boundary migration. Can mis-pair smart quotes. |

---

## Cross-cutting bug patterns

Beyond the per-file gaps, the audit surfaced five **systematic** bug shapes
that recur across many files. These aren't independent bugs — they are each
one incomplete conversion step that manifested in dozens of files.

### Pattern 1 — Java `break`/`return` was not migrated to `boundary/break`

**Manifestations:**
- `break` replaced with a `// break` comment or a `var done = false` flag that
  only the outer loop sees, leaving inner loops running (ISS-065, ISS-084,
  ISS-089, ISS-094)
- `return` replaced with `var continue = false // return` that doesn't
  short-circuit the method (ISS-094)
- `return true/false` left raw with a `// scalastyle:ignore` comment (ISS-068,
  plus `WikiNode.setLink/setLinkChars`)
- `scala.util.boundary` never imported in files that clearly need it

**Scope:** confirmed in at least 6 files across 5 packages
(parser/internal, ast/, ext/enumerated/reference, ext/jekyll/tag,
ext/typographic, ext/definition, and likely many more untouched by this
audit). Every file that used `break`/`return` in a nested loop in the original
Java should be re-read.

**Root cause:** the automated conversion pipeline skipped the
`return`/`break` → `boundary/break` rewrite, and the follow-up was delayed
("TODO: use boundary") and then forgotten once tests passed (because tests
didn't cover the affected branches).

### Pattern 2 — Stale "not yet ported" stubs that are now fixable

**Manifestations:**
- `RefNode.getReferenceNode(Document)` returns `null` with comment
  "Parser.REFERENCES not yet ported" — it *is* ported now (ISS-095)
- `Heading.anchorRefText` hardcodes `trimLeadingSpaces=true` with stale "not
  yet ported" comment — the DataKeys exist (ISS-096)
- `ParagraphItemContainer.isParagraphWrappingDisabled` uses `Any` for
  `listOptions` because "ssg.md.parser.ListOptions was not yet ported"
  (ISS-090, ISS-079)
- Multiple `appendNonTranslating → append` shims with comment "not yet
  ported" — the method path is now available (ISS-083 et al.)
- `Formatter.collectedNodes` stub: `/* collectNodeTypes would be used here */`
  (ISS-097)

**Scope:** at least 7 files. Every file with a comment containing "not yet
ported", "will be handled below", "would be used here", or "for now" should
be re-read and re-wired to its now-available dependency.

**Root cause:** files were ported in topological order with stubs for
not-yet-ported dependencies. When those dependencies became available, the
stubs were not revisited.

### Pattern 3 — Constructor args silently dropped

**Manifestations:**
- `AsideBlock(chars, segments)` drops `segments` (ISS-070)
- `MacroDefinitionBlock(chars, segments)` drops `segments` (ISS-072)
- Same pattern expected in other `Block` subclasses that override constructors

**Scope:** 2 confirmed, likely more. Any `Block` subclass with a multi-arg
constructor should be cross-checked against the Java original's field
assignments.

**Root cause:** when the Java `super(chars, segments)` call was mechanically
translated, the body was omitted under the assumption that it was boilerplate.

### Pattern 4 — `null` casts to satisfy non-`Nullable` trait returns

**Manifestations:**
- `MacroReference` has 5 sites doing
  `Nullable.empty.getOrElse(null).asInstanceOf[MacroDefinitionBlock]`
  because `ReferencingNode[R, B]` declares `getReferenceNode(...): B`
  rather than `Nullable[B]` (ISS-074)
- `TaskListFormatOptions.def this() = this(null)` passes raw null at a
  non-interop boundary (ISS-064)
- Similar patterns expected in other files implementing `ReferencingNode`
  and `ReferenceRepository` subclasses

**Scope:** 2+ confirmed. The fix is a **trait-level retype**: change
`ReferencingNode[R, B].getReferenceNode(...): B` to
`Nullable[B]` and propagate.

**Root cause:** traits were retained with original Java signatures even
when the Scala port switched all return paths to Nullable.

### Pattern 5 — Stub methods marked with `@nowarn("unused")` instead of fixed

**Manifestations:**
- `EnumeratedReferenceNodeRenderer` has a field marked
  `@nowarn("msg=unused private member") // stub: will be used when rendering is completed`
  — the field was actually *used* and the "stub" comment was misleading;
  my earlier audit misread this and marked the file `major_issues`
- Similar misleading `@nowarn` comments in other `*PreProcessor` files

**Scope:** confirmed in 2 files. Risk: **audit drift** — the `@nowarn`
comment was not reliable for determining real port status.

**Root cause:** `@nowarn` was added defensively during the conversion to
silence warnings, and the comments drifted as the code evolved.

---

## Recommended execution plan

Ranked by a combination of (a) impact on correctness and (b) unblock
dependencies for other fixes.

### Wave 1 — Unblock everything else

- **ISS-002** — Wire the CommonMark spec runners. Fixing this will immediately
  expose dozens of behavioral gaps via test failures, turning unknowns into
  knowns. Without this, any other fix is guessing.
- **ISS-055** — Port `TocUtils` markdown-side helpers. This unblocks both
  ISS-003 and ISS-004.
- **ISS-036** — Ingest Terser `test/compress/*.js` fixtures into a runner.
  Same value for ssg-js as ISS-002 is for ssg-md — turns an unknown baseline
  into a measured one.
- **ISS-029** — Port the missing liqp test suites (TemplateSuite, DateFilter,
  WhereFilter, LValue, Lexer, Parser, Node, IncludeRelative). Same value for
  ssg-liquid.

### Wave 2 — Fix the systematic cross-cutting bugs (Pattern 1 and Pattern 2)

These are cheap per-file but need a coordinated sweep to avoid re-introducing
them.

- **Sweep all files for `// break` / `// return` comments and `var done`/`var continue` flag patterns.** Replace with `scala.util.boundary` / `break(())`. Files already identified: ISS-065, ISS-068, ISS-084, ISS-089, ISS-094. Expand the sweep to every file that uses `break` or `return` in the original Java. This is a one-day refactor that will fix maybe 15–30 more bugs we haven't found yet.
- **Sweep all files for stale "not yet ported" comments and re-wire.** Files already identified: ISS-090, ISS-095, ISS-096, ISS-097, plus the `appendNonTranslating` shims in ISS-083 et al. This is pattern matching.

### Wave 3 — Fix the 4 silent major bugs in ssg-md core

- ISS-097 Formatter.collectedNodes
- ISS-093 HtmlRenderer empty delegates
- ISS-094 DocumentParser inner-loop leak (part of Wave 2 sweep)
- ISS-095 RefNode.getReferenceNode(Document) (part of Wave 2 sweep)

Plus ISS-003 / ISS-004 once ISS-055 is done.

### Wave 4 — ssg-js structural fixes

Blocked by the foundational gaps — can't be done in isolation.

- ISS-057 Implement `TreeTransformer` (port `transform.js` dispatch)
- ISS-032 SymbolDef/scope integration across compress passes
- ISS-058 Wire `GlobalDefs.resolveDefs` into `Compressor.compress` (depends on ISS-057)
- ISS-059 Fix `OutputStream.getSymbolName` to consult `SymbolDef.mangledName`
- ISS-031 Implement multi-pass compression loop
- ISS-060 Extend `Compressor.optimizeTree` to recurse into all node types

### Wave 5 — ssg-liquid Template / TemplateParser / Where / Date rewrites

The gap-analysis doc already scopes these; implementation is a multi-week effort.

### Wave 6 — ssg-minify option fidelity

All medium/low priority. Can be batched once someone is making a minify pass.

### Wave 7 — Pattern 3 (dropped constructor args) and Pattern 4 (null casts)

Cheap mechanical fixes; do in the same PR as the ReferencingNode retype.

### Wave 8 — Extension test coverage

Per-extension placeholder `*ExtensionSuite` files replaced with real
`Combo*SpecTest` runners (ISS-073, ISS-075, ISS-077, ISS-078). Each is
a ~1-day port of the corresponding flexmark fixture.

---

## Known blind spots

1. **ssg-liquid re-audit is partial**. Only 11 of ~110 `pass`-marked files in
   filters/blocks/tags/nodes/exceptions/antlr were re-read this pass. A
   narrower re-dispatch is in progress. Until it reports, those files should
   be treated as "believed pass but unverified". Given the systematic Pattern 1
   / Pattern 2 bugs found elsewhere, expect 5–10 more major findings there.

2. **ssg-sass is entirely out of scope** for this audit. It has its own gap
   analysis doc (`sass-port.md` skeleton) and should be audited separately.

3. **Cross-platform regex drift** was not systematically verified. Files with
   hand-rolled regex rewrites (autolink URL_PATTERN, EMAIL_PATTERN;
   abbreviation boundary workarounds; wiki-link, media-tags, youtube, resizable
   patterns) diverge from `java.util.regex` in ways the test suite doesn't
   currently exercise. Needs a parallel pass: take every regex in the port,
   run upstream fixtures against it on all three platforms, record diffs.

4. **Upstream-bug preservation vs fix policy** is inconsistent. Several files
   faithfully replicate known flexmark bugs
   (`MarkdownTable.forAllBodyRows` passing `ALL_HEADER_ROWS`,
   `TocBlockBase.segments` `System.arraycopy(src=dst)`,
   `AbbreviationBlockParser.tryStart` indent bug,
   `FootnoteBlock.addFirstReferenceOffset` inverted comparison). The project
   rule is "fix bugs, don't work around them", but these weren't flagged as
   bugs by the original audit because the rule is typically applied to
   port-introduced bugs, not upstream-inherited ones. Need a policy decision
   on whether to fix upstream bugs or file them upstream.

5. **Severity labels are inconsistent**. Some agents used
   `major`/`minor`/`correctness`/`completeness`/`porting`/`nullable`/
   `behavior`/`logic-bug` where the canonical vocabulary is
   `high`/`medium`/`low` plus a small category set. 17 rows have
   `severity=major`, 9 have `severity=minor`. Needs normalization in a
   follow-up.

6. **Audit DB has two files missing** (942 rows vs 944 files on disk). One is
   the known ISS-001 `ArrayUtils.scala` (intentionally skipped but migration
   DB says ported). The other needs identification.

---

## Data sources

- Audit DB: `scripts/data/audit.tsv`, queried via `ssg-dev db audit list/stats`
- Issues DB: `scripts/data/issues.tsv`, queried via `ssg-dev db issues list/stats`
- Per-module gap docs:
  - `docs/architecture/flexmark-port.md` — ssg-md (earlier gap analysis)
  - `docs/architecture/liquid-port-gap-analysis.md` — ssg-liquid (ISS-007..030 scope)
  - `docs/architecture/jekyll-minifier-port.md` — ssg-minify
  - `docs/architecture/terser-port.md` — ssg-js
  - `docs/architecture/liqp-port.md`, `sass-port.md`, `minify-port.md`, `js-port.md` — methodology skeletons
