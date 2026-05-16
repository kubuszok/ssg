---
description: Types from lls (lowlevel.*) used in SSG — Nullable, MkArray, ArrayView, collections
---

## lls Types in SSG

SSG depends on [lls](https://github.com/kubuszok/lls) (`com.kubuszok:lls`) for
shared zero-allocation types. The canonical source is `../lls/` — changes to
`lowlevel.*` types go to lls, not ssg. The dependency is declared in ssg-commons
and transitively available in all SSG modules.

### Types from lls used in SSG

| lls type | Purpose | SSG usage |
|----------|---------|-----------|
| `lowlevel.Nullable[A]` | Allocation-free Option (opaque union type) | ~8600 uses across all modules |
| `lowlevel.MkArray[A]` | Sealed type class for unboxed array ops | Available but not yet used |
| `lowlevel.ArrayView[A,_,_]` | Zero-allocation array iteration | Available via `arr.leanView` |
| `lowlevel.util.DynamicArray[A]` | Unboxed resizable array | Available but not yet used |
| `lowlevel.util.ObjectMap[K,V]` | Fibonacci hashing map | Available but not yet used |
| `lowlevel.util.Sort` | TimSort facade | Available but not yet used |
| `lowlevel.math.MathUtils` | Fast math (sin tables, interpolation) | Available but not yet used |

### Nullable

SSG's Nullable comes from lls (`lowlevel.Nullable`). Two re-export files provide
package-local aliases so existing code doesn't need import changes:

- `ssg-md/src/main/scala/ssg/md/Nullable.scala` — re-exports for `ssg.md` package
- `ssg-sass/src/main/scala/ssg/sass/Nullable.scala` — re-exports for `ssg.sass` package

All other modules import directly: `import lowlevel.Nullable`

Key methods:
```scala
Nullable(value)          // wrap (null-safe — null becomes empty)
Nullable.empty[A]        // empty value
Nullable.Null[A]         // alias for empty
value.isDefined          // true if non-empty
value.isEmpty            // true if empty
value.getOrElse(default) // unwrap with fallback
value.get                // force unwrap (throws NPE if empty)
value.map(f)             // transform if present
value.flatMap(f)         // chain nullable operations
value.foreach(f)         // side-effect if present
value.fold(empty)(f)     // fold to result
value.filter(p)          // keep if predicate holds
value.toOption           // convert to Option[A]
value.orNull             // @deprecated — only for Java interop boundaries
```

### ArrayView zero-allocation patterns

Use `arr.leanView` for zero-allocation iteration over `Array[T]` and `IArray[T]`:

```scala
import lowlevel.leanView

// Simple foreach (no lambda boxing):
arr.leanView.foreach(elem => doSomething(elem))

// With index (no tuple allocation):
arr.leanView.zipWithIndex.foreach { (elem, i) => doSomething(elem, i) }

// Filtered:
arr.leanView.withFilter(_.isValid).foreach(process)

// Map to new array (needs MkArray[B]):
val result: Array[Int] = arr.leanView.map(_.size)
```

Best for **composite operations** (zipWithIndex, filter+map) where stdlib would allocate intermediaries.
Do NOT use for simple foreach/map on a single array — stdlib JIT already optimizes those.

Do NOT use `leanView` for:
- Non-unit stride loops (`i += 5`)
- Early exit / break patterns
- Reverse iteration
- Mutable accumulation with complex state

### Common mistakes

1. **Importing from old package** — use `import lowlevel.Nullable`, not `import ssg.commons.Nullable` (the latter no longer exists)
2. **Using `orNull` without `@nowarn`** — `orNull` is `@deprecated` to force explicit acknowledgment at Java interop boundaries; add `@nowarn("msg=deprecated")` with a comment explaining why
