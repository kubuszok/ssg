# Dart to Scala 3 Conversion Rules

These rules apply when converting Dart source files (from dart-sass) to Scala 3.

## Key Differences from Java Conversion

Dart is more similar to Scala than Java in some ways (null-safety, closures, mixins)
but has unique constructs that need careful mapping.

## Type System

| Dart | Scala 3 |
|------|---------|
| `int` | `Int` (or `Long` for 64-bit) |
| `double` | `Double` |
| `bool` | `Boolean` |
| `String` | `String` |
| `num` | `Double` or union type |
| `dynamic` | `Any` |
| `void` | `Unit` |
| `Object` | `AnyRef` |
| `Never` | `Nothing` |
| `Null` | `Null` |
| `var x = ...` | `var x = ...` (type inferred) |
| `final x = ...` | `val x = ...` |
| `const x = ...` | `val x = ...` (compile-time) |
| `late var x` | `lazy val x` or `var x = uninitialized` |
| `late final x` | `lazy val x` |

## Null Safety

| Dart | Scala 3 |
|------|---------|
| `Type?` | `Nullable[Type]` |
| `Type` (non-nullable) | `Type` |
| `x!` (null assertion) | `x.nn` or explicit check |
| `x?.method()` | `x.map(_.method())` |
| `x ?? default` | `x.getOrElse(default)` |
| `x ??= value` | `if (x.isEmpty) x = value.asNullable` |
| `x?.method() ?? default` | `x.fold(default)(_.method())` |

## Classes and Objects

| Dart | Scala 3 |
|------|---------|
| `class Foo extends Bar with Mixin` | `class Foo extends Bar with Mixin` |
| `abstract class` | `abstract class` or `trait` |
| `mixin Foo on Bar` | `trait Foo { self: Bar => }` |
| `sealed class` | `sealed trait` or `sealed abstract class` |
| `extension type` | opaque type |
| `factory Foo.name()` | `def apply()` or `def name()` in companion |
| `Foo._()` (private constructor) | `private def this()` or sealed trait |
| `static` members | companion object members |
| `const Foo()` | companion object `val` |

## Collections

| Dart | Scala 3 |
|------|---------|
| `List<T>` | `List[T]` or `ArrayBuffer[T]` (mutable) |
| `Map<K,V>` | `Map[K,V]` or `HashMap[K,V]` (mutable) |
| `Set<T>` | `Set[T]` or `HashSet[T]` (mutable) |
| `Iterable<T>` | `Iterable[T]` |
| `[1, 2, 3]` (list literal) | `List(1, 2, 3)` |
| `{1, 2, 3}` (set literal) | `Set(1, 2, 3)` |
| `{'a': 1}` (map literal) | `Map("a" -> 1)` |
| `list.add(x)` | `list += x` or `list.appended(x)` |
| `list.removeAt(i)` | `list.remove(i)` |
| `list.where(pred)` | `list.filter(pred)` |
| `list.firstWhere(pred)` | `list.find(pred)` |
| `list.expand(fn)` | `list.flatMap(fn)` |
| `list.fold(init, fn)` | `list.foldLeft(init)(fn)` |

## Async/Await

| Dart | Scala 3 |
|------|---------|
| `Future<T>` | `scala.concurrent.Future[T]` |
| `async` function | function returning `Future[T]` |
| `await expr` | `Await.result(expr)` or `for` comprehension |
| `Stream<T>` | `Iterator[T]` or `LazyList[T]` |
| `yield` (in generator) | `yield` in `for` comprehension |
| `yield*` | `++` or `flatMap` |
| `Completer<T>` | `Promise[T]` |

## Pattern Matching

| Dart | Scala 3 |
|------|---------|
| `switch (x) { case ... }` | `x match { case ... }` |
| `if (x case Pattern())` | `x match { case Pattern() => ... }` |
| `is Type` | `isInstanceOf[Type]` or `case _: Type` |
| `as Type` | `asInstanceOf[Type]` or typed pattern |

## Control Flow

- `return` → use `boundary`/`break` (same as Java rules)
- `break`/`continue` → boundary/break patterns
- `for (var x in collection)` → `for (x <- collection)`
- `for (var i = 0; i < n; i++)` → `for (i <- 0 until n)`
- Dart `switch` expressions → Scala `match` expressions

## String Interpolation

| Dart | Scala 3 |
|------|---------|
| `'$variable'` | `s"$variable"` |
| `'${expr}'` | `s"${expr}"` |
| `'''multi-line'''` | `"""multi-line"""` |

## Enums

| Dart | Scala 3 |
|------|---------|
| `enum Color { red, green, blue }` | `enum Color { case Red, Green, Blue }` |
| `enum Color { red(1); final int value; const Color(this.value); }` | `enum Color(val value: Int) extends java.lang.Enum { case Red extends Color(1) }` |

## Visibility

| Dart | Scala 3 |
|------|---------|
| `_privateName` (leading underscore) | `private` modifier |
| No modifier (library-public) | No modifier (public) |
| `@visibleForTesting` | `private[packagename]` |
