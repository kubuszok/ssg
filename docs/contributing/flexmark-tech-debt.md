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

### Cross-Platform Linking Errors (JS/Native)

Pre-existing linking errors prevent JS and Native test execution:

**Scala.js:**
- `Enum.getDeclaringClass()` — BitFieldSet
- `String.contentEquals(CharSequence)` — ResolvedLink

**Scala Native:**
- `Class.isEnum()`, `Class.getPackage()`, `Class.getResource()` — BitFieldSet, Node, TestUtils
- `java.net.URL` — TestUtils/ResourceLocation
- `java.util.Stack` — LineAppendableImpl
- `java.util.Locale.ROOT/US`, `java.text.DecimalFormatSymbols` — TableParagraphPreProcessor

These need platform-specific alternatives or conditional compilation to resolve.

**Priority**: Medium. Required before JS/Native targets can run tests.

## Audit Status

298 files audited. Tested field updated to `yes` for all files.
- 277 pass, 20 minor_issues, 0 major_issues (after resolving fixed files)
- 1612/1612 JVM tests passing across 33 suites
