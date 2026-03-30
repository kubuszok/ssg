# Source Library → SSG Type Mappings

## flexmark-java → ssg.md

| Original Package | SSG Package |
|-----------------|-------------|
| `com.vladsch.flexmark.ast` | `ssg.md.ast` |
| `com.vladsch.flexmark.parser` | `ssg.md.parser` |
| `com.vladsch.flexmark.html` | `ssg.md.html` |
| `com.vladsch.flexmark.formatter` | `ssg.md.formatter` |
| `com.vladsch.flexmark.util.ast` | `ssg.md.util.ast` |
| `com.vladsch.flexmark.util.data` | `ssg.md.util.data` |
| `com.vladsch.flexmark.util.sequence` | `ssg.md.util.sequence` |
| `com.vladsch.flexmark.util.misc` | `ssg.md.util.misc` |
| `com.vladsch.flexmark.util.collection` | `ssg.md.util.collection` |
| `com.vladsch.flexmark.ext.*` | `ssg.md.ext.*` |

## liqp → ssg.liquid

| Original Package | SSG Package |
|-----------------|-------------|
| `liqp` | `ssg.liquid` |
| `liqp.parser` | `ssg.liquid.parser` |
| `liqp.nodes` | `ssg.liquid.nodes` |
| `liqp.tags` | `ssg.liquid.tags` |
| `liqp.filters` | `ssg.liquid.filters` |
| `liqp.exceptions` | `ssg.liquid.exceptions` |

## dart-sass → ssg.sass

| Original Path | SSG Package |
|--------------|-------------|
| `lib/src/ast/` | `ssg.sass.ast` |
| `lib/src/visitor/` | `ssg.sass.visitor` |
| `lib/src/parse/` | `ssg.sass.parse` |
| `lib/src/evaluate/` | `ssg.sass.evaluate` |
| `lib/src/value/` | `ssg.sass.value` |
| `lib/src/util/` | `ssg.sass.util` |
| `lib/src/extend/` | `ssg.sass.extend` |
| `lib/src/importer/` | `ssg.sass.importer` |

## jekyll-minifier → ssg.html

| Ruby Concept | SSG Package |
|-------------|-------------|
| HTML minification | `ssg.html.minify` |
| CSS minification | `ssg.html.css` |
| JS minification | `ssg.html.js` |
| Configuration | `ssg.html.config` |

## Common Java → Scala Collection Mappings

| Java | Scala |
|------|-------|
| `java.util.List<T>` | `scala.collection.mutable.Buffer[T]` |
| `java.util.ArrayList<T>` | `scala.collection.mutable.ArrayBuffer[T]` |
| `java.util.LinkedList<T>` | `scala.collection.mutable.ListBuffer[T]` |
| `java.util.Map<K,V>` | `scala.collection.mutable.Map[K,V]` |
| `java.util.HashMap<K,V>` | `scala.collection.mutable.HashMap[K,V]` |
| `java.util.LinkedHashMap<K,V>` | `scala.collection.mutable.LinkedHashMap[K,V]` |
| `java.util.Set<T>` | `scala.collection.mutable.Set[T]` |
| `java.util.HashSet<T>` | `scala.collection.mutable.HashSet[T]` |
| `java.util.Stack<T>` | `scala.collection.mutable.Stack[T]` |
| `java.util.Queue<T>` | `scala.collection.mutable.Queue[T]` |
| `java.util.Iterator<T>` | `Iterator[T]` |
| `java.util.Collections.unmodifiableList()` | `.toList` |
| `java.util.Collections.emptyList()` | `List.empty` or `Nil` |
