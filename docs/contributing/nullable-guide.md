# Nullable[A] Guide

SSG uses an opaque type `Nullable[A]` to handle null-safety at the type level.

## Definition

```scala
opaque type Nullable[+A] = A | Null
```

## Core Operations

| Operation | Signature | Purpose |
|-----------|-----------|---------|
| `asNullable` | `A => Nullable[A]` | Wrap a value |
| `nn` | `Nullable[A] => A` | Unwrap (throws if null) |
| `getOrElse` | `Nullable[A] => A => A` | Default if null |
| `fold` | `Nullable[A] => B => (A => B) => B` | Transform or default |
| `map` | `Nullable[A] => (A => B) => Nullable[B]` | Transform if non-null |
| `flatMap` | `Nullable[A] => (A => Nullable[B]) => Nullable[B]` | Chain nullable ops |
| `foreach` | `Nullable[A] => (A => Unit) => Unit` | Side-effect if non-null |
| `isDefined` | `Nullable[A] => Boolean` | True if non-null |
| `isEmpty` | `Nullable[A] => Boolean` | True if null |

## Standard Patterns

### Pattern 1: Null-or-value
```scala
val result = nullable.getOrElse(defaultValue)
```

### Pattern 2: Null-or-throw
```scala
val result = nullable.fold(throw new SsgError("missing"))(identity)
// or simply:
val result = nullable.nn  // throws NullPointerException
```

### Pattern 3: Null-or-compute
```scala
val result = nullable.fold(computeDefault())(a => transform(a))
```

### Pattern 4: Non-null side-effect
```scala
nullable.foreach { a =>
  doSomething(a)
}
```

### Pattern 5: Boolean checks
```scala
if (nullable.isDefined) { ... }
if (nullable.isEmpty) { ... }
```

### Pattern 6: Chaining
```scala
val result = first.flatMap(a => second(a)).getOrElse(default)
```

## Converting from Java null

```scala
// Java: if (x == null) return default; else return x.process();
// Scala:
val xNullable: Nullable[X] = x  // x may be null at boundary
xNullable.fold(default)(_.process())
```

## Converting from Dart null-safety

```scala
// Dart: x?.method() ?? default
// Scala:
x.map(_.method()).getOrElse(default)

// Dart: x!
// Scala:
x.nn
```
