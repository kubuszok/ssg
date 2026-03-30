# Java to Scala 3 Conversion Rules

These rules apply when converting Java source files (from flexmark-java, liqp) to Scala 3.

## Procedure

### Step 1: File Setup
- Create the Scala file in the correct module (`ssg-md/` or `ssg-liquid/`)
- Add Apache 2.0 license header with original source attribution
- Use split package declarations: `package ssg` / `package md` / `package core`

### Step 2: Class Structure
- `public class Foo extends Bar implements Baz` → `class Foo extends Bar with Baz`
- `public final class` → `final class`
- `public abstract class` → `abstract class`
- `public interface` → `trait`
- `public enum` → `enum ... extends java.lang.Enum`
- All `case class` must be `final`

### Step 3: Access Modifiers
- `public` → (remove, Scala default is public)
- `private` → `private`
- `protected` → `protected`
- `package-private` → `private[packagename]`

### Step 4: Fields and Properties
- `private Type field` with getter/setter → `var field: Type`
- `private final Type field` with getter → `val field: Type`
- No-logic `getX()`/`setX(v)` → public `var x`
- With-logic getters/setters → `def x: T` + `def x_=(v: T): Unit`
- Uninitialized `var` (`= _`) → `= scala.compiletime.uninitialized`

### Step 5: Type Mappings
- `String` → `String` (same)
- `int`/`Integer` → `Int`
- `long`/`Long` → `Long`
- `boolean`/`Boolean` → `Boolean`
- `double`/`Double` → `Double`
- `float`/`Float` → `Float`
- `byte`/`Byte` → `Byte`
- `char`/`Character` → `Char`
- `void` → `Unit`
- `Object` → `Any` or `AnyRef`
- Arrays: `Type[]` → `Array[Type]`

### Step 6: Collections
- `java.util.List<T>` → `scala.collection.mutable.Buffer[T]` or `List[T]`
- `java.util.ArrayList<T>` → `scala.collection.mutable.ArrayBuffer[T]`
- `java.util.Map<K,V>` → `scala.collection.mutable.Map[K,V]` or `Map[K,V]`
- `java.util.HashMap<K,V>` → `scala.collection.mutable.HashMap[K,V]`
- `java.util.Set<T>` → `scala.collection.mutable.Set[T]`
- `java.util.Iterator<T>` → `Iterator[T]`
- `java.util.Collections.unmodifiableList()` → `.toList`
- `java.util.Collections.emptyList()` → `Nil` or `List.empty`

### Step 7: Null Handling
- `if (x == null)` → use `Nullable[A]` opaque type
- `@Nullable` annotations → `Nullable[Type]`
- Nullable parameters → `Nullable[Type]` with `.getOrElse`, `.fold`, etc.
- **Never use `orNull`** except at Java interop boundaries (requires `@nowarn` + comment)

### Step 8: Control Flow
- `return value` → `scala.util.boundary { ... boundary.break(value) }`
- `for (int i = 0; i < n; i++)` → `for (i <- 0 until n)`
- `for (Type x : collection)` → `for (x <- collection)`
- `while` loops → same syntax in Scala
- `break` in loops → `scala.util.boundary { ... boundary.break() }`
- `continue` → inner boundary/break
- `switch/case` → `match/case`
- `try/catch` → `try ... catch { case e: ExType => ... }`

### Step 9: Generics
- `<T extends Foo>` → `[T <: Foo]`
- `<T super Foo>` → `[T >: Foo]`
- `<?>` → `[?]` or `[_]`
- `<T extends Foo & Bar>` → `[T <: Foo & Bar]`

### Step 10: Exceptions
- `throws Exception` → remove (Scala has no checked exceptions)
- Custom exceptions → extend appropriate Scala exception types

### Step 11: Static Members
- `static` methods/fields → companion object members
- `static final` constants → `val` in companion object
- `static` inner classes → top-level or nested without `static`

### Step 12: Anonymous Classes and Lambdas
- `new Interface() { ... }` → `new Interface { ... }` or lambda if SAM
- `(x) -> expr` → `x => expr`
- `(x, y) -> expr` → `(x, y) => expr`

### Step 13: String Operations
- `String.format()` → `s"..."` string interpolation
- `+` concatenation → `s"..."` where clearer
- `StringBuilder` → `StringBuilder` (same in Scala)

### Step 14: Annotations
- `@Override` → `override` keyword (not annotation)
- `@Deprecated` → `@deprecated("reason", "version")`
- `@SuppressWarnings` → `@nowarn` with specific filter

### Step 15: Inner Classes/Interfaces
- Non-static inner classes → nested classes
- Static inner classes → companion object or top-level
- Anonymous inner classes → lambdas or `new Trait { ... }`

### Step 16: Comparison
- `instanceof` → `isInstanceOf` or pattern match
- `.equals()` → `==`
- `==` (reference equality) → `eq`
- `Comparable<T>` → `Ordered[T]` or `Ordering[T]`
- `Comparator<T>` → `given Ordering[T]`

### Step 17: Threading/Concurrency
- `synchronized` → `synchronized { ... }`
- `volatile` → `@volatile`
- `java.util.concurrent.*` → consider Scala alternatives

### Step 18: I/O
- `java.io.*` → prefer Scala wrappers or keep as-is for cross-platform
- `System.out.println` → `println`
- `System.err.println` → `System.err.println`

### Step 19: Final Review
- Remove all `public` keywords
- Remove all `static` keywords (move to companion objects)
- Remove all `implements` (use `with`)
- Ensure no bare `null` usage
- Ensure no `return` statements
- Run `ssg-dev quality scan` to verify
