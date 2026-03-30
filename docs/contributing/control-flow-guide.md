# Control Flow Guide: boundary/break Patterns

SSG uses `scala.util.boundary` and `scala.util.boundary.break` to replace
`return`, `break`, and `continue` statements from Java/Dart/Ruby.

## Import

```scala
import scala.util.boundary
import scala.util.boundary.break
```

## Pattern 1: Replacing `return`

### Java/Dart
```java
Type method(args) {
  if (condition) return earlyValue;
  // ... rest of method
  return normalValue;
}
```

### Scala 3
```scala
def method(args): Type = boundary {
  if (condition) break(earlyValue)
  // ... rest of method
  normalValue
}
```

## Pattern 2: Replacing `break` in loops

### Java/Dart
```java
for (item in collection) {
  if (condition) break;
  process(item);
}
```

### Scala 3
```scala
boundary {
  for (item <- collection) {
    if (condition) break()
    process(item)
  }
}
```

## Pattern 3: Replacing `continue`

### Java/Dart
```java
for (item in collection) {
  if (skipCondition) continue;
  process(item);
}
```

### Scala 3
```scala
for (item <- collection) {
  boundary {
    if (skipCondition) break()
    process(item)
  }
}
```

## Pattern 4: Labeled breaks (nested loops)

### Java
```java
outer:
for (i = 0; i < n; i++) {
  for (j = 0; j < m; j++) {
    if (condition) break outer;
  }
}
```

### Scala 3
```scala
boundary {
  for (i <- 0 until n) {
    for (j <- 0 until m) {
      if (condition) break()
    }
  }
}
```

## Pattern 5: Return with value from loop

### Java
```java
Type findFirst(List<Type> items) {
  for (Type item : items) {
    if (matches(item)) return item;
  }
  return null;
}
```

### Scala 3
```scala
def findFirst(items: List[Type]): Nullable[Type] = boundary {
  for (item <- items) {
    if (matches(item)) break(item.asNullable)
  }
  Nullable.Null
}
```

Or more idiomatically:
```scala
def findFirst(items: List[Type]): Nullable[Type] =
  items.find(matches).map(_.asNullable).getOrElse(Nullable.Null)
```
