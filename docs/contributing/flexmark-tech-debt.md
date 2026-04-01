# flexmark Port Technical Debt

Living document tracking remaining technical debt from the flexmark-java → Scala 3 port.

## Resolved

### `ne`/`eq` for BasedSequence.NULL — RESOLVED
Java `!=` is reference equality; Scala `!=` is value equality. All `!= BasedSequence.NULL`
and `== BasedSequence.NULL` patterns have been replaced with `ne`/`eq`. Verified clean:
zero remaining occurrences in codebase.

### `StringBuilder.underlying.append` — RESOLVED
Scala's `StringBuilder.append(CharSequence, Int, Int)` doesn't correctly dispatch to
`java.lang.StringBuilder` when the CharSequence comes from opaque type (`Nullable.get`).
Fixed in all known locations: StringSequenceBuilder, Escaping, IRichSequenceBase,
RichSequenceBuilder, DelimitedBuilder.

### `java.lang.Boolean`/`Integer` → Scala Primitives — RESOLVED
55 occurrences across 14 files replaced with Scala `Boolean`/`Int`. Tests verify correctness.

### `return` keyword → `boundary`/`break` — RESOLVED
All 50 `return` statements across 19 files replaced with `scala.util.boundary`/`break`.
Quality scan confirms zero remaining `return` statements in production code.

### `extends ... with` → `extends ..., ...` — RESOLVED
78 files updated from `extends Foo with Bar` to `extends Foo, Bar` (Scala 3 comma syntax).
Kept `with` in 8 files where preceding type is a Java class (Enum, CharSequence, AbstractSet, etc.).

## Active (to address)

### `java.util` Collections → Scala Collections
Many files use `java.util.ArrayList`, `java.util.HashMap` etc. where Scala
`ArrayBuffer`, `mutable.HashMap` would be more idiomatic.

**Do NOT mass-replace.** Assessment found all candidate files expose Java collections
through public getters, making replacement unsafe without API changes:
- EmojiShortcuts: 3 private HashMaps, but returned via `getEmojiShortcuts`/`getEmojiURIs`
- EmojiReference: ArrayList returned via `getEmojiList` as `ju.List`
- TextNodeMergingList: ArrayList returned via `getMergedList` as `ju.List`
- NodeRepository, DataSet, OrderedSet/Map: implement `java.util.Map`/`Set` interfaces

**Priority**: Low. Keep as-is unless API redesign is planned.

### Interface Flattening (IRichSequence/Base/Impl) — ASSESSED, DEFERRED

Assessment identified 6 I-prefix triplets and 14 Base classes:

| Triplet | Methods | Risk | Recommendation |
|---------|---------|------|----------------|
| IRichSequence/Base/Impl | 773 | CRITICAL | DO NOT flatten |
| ISequenceBuilder | 14 | Medium | Low priority |
| ISegmentBuilder | 23 | Medium | Low priority |
| IBasedSegmentBuilder | 3 | Medium | Low priority |
| IRender | 2 | Low | Safe to flatten (minimal benefit) |
| IParse | 5 | Low | Safe to flatten (minimal benefit) |

**IRichSequence** (773 methods) is the foundation of all markdown parsing. Flattening
would merge 1000+ methods into one trait, lose separation of concerns, and affect
400+ files. Cost far exceeds benefit.

**Priority**: Deferred indefinitely for core types. IRender/IParse can be flattened
opportunistically but benefit is negligible.

### Cross-Platform (JS/Native) — RESOLVED

All JVM-only APIs eliminated from production and test code. Both JVM and
Scala Native pass 1645/1645 tests (100%).

**Fixes applied:**
- Nullable NestedNone: `case class` → regular class (avoids Product/Serializable
  in opaque union type erasure, which caused ClassCastException on Native)
- 17+ regex patterns rewritten: lookaheads, `\p{}` Unicode categories, `[...]&&[^...]`
  character class intersection, `\Q..\E` quoting all replaced with cross-platform
  alternatives. Each pattern has a comment documenting the original and revert
  instructions for when scala-native#4810 ships.
- Abbreviation word boundaries: `\b` + `UNICODE_CHARACTER_CLASS` → programmatic
  boundary check in code (handles non-ASCII abbreviations like É.U.)
- `Class.isInstance(null)` → check `isDefined` first (JVM returns false, Native NPEs)
- BitFieldSet enum reflection → `EnumBitField[E]` type class
- ThreadLocal, String.format(Locale), java.util.Stack, java.net.URL,
  MessageFormat, Class.getPackage, StringBuilder.getChars — all replaced
- Test infrastructure: java.io.File, java.net.URL, Class.getResource → string ops
- Build: `embedResources=true`, `multithreading=false` for Native

**Scala.js status:**
- Production code compiles and links
- Test linking blocked by `Class.getResourceAsStream` in test code (spec file loading)
  and `java.text.DecimalFormat` in munit internals
- Needs JS-specific resource loading (e.g., Node.js fs module) — separate task

**Priority**: Low for JS (functional on JVM + Native, JS needs test infrastructure only).

## Audit Status

298 files audited. Tested field updated to `yes` for all files.
- 278 pass, 20 minor_issues, 0 major_issues
- 1645/1645 JVM tests passing across 55 test suites
- 1645/1645 Native tests passing across 55 test suites
