# Ruby to Scala 3 Conversion Rules

These rules apply when converting Ruby source files (from jekyll-minifier) to Scala 3.

## Approach

Ruby code is dynamically typed and heavily uses metaprogramming. Unlike Java and Dart,
**line-by-line porting is not practical**. Instead:

1. **Understand the algorithm** — read the Ruby code to understand what it does
2. **Write Scala from the spec** — implement the same behavior in idiomatic Scala 3
3. **Verify equivalence** — use tests to ensure output matches

## Key Ruby → Scala Mappings

### Types

| Ruby | Scala 3 |
|------|---------|
| `String` | `String` |
| `Integer`/`Fixnum` | `Int` or `Long` |
| `Float` | `Double` |
| `true`/`false` | `Boolean` |
| `nil` | `Nullable[A]` (never raw `null`) |
| `Array` | `List[T]` or `ArrayBuffer[T]` |
| `Hash` | `Map[K,V]` or `HashMap[K,V]` |
| `Symbol` | `String` or enum |
| `Regexp` | `scala.util.matching.Regex` |
| `Proc`/`lambda` | `T => U` function type |
| `Block (&block)` | `T => U` parameter |

### Control Flow

| Ruby | Scala 3 |
|------|---------|
| `if/elsif/else/end` | `if/else if/else` |
| `unless` | `if (!...)` |
| `case/when` | `match/case` |
| `while/until` | `while` |
| `begin/rescue/ensure` | `try/catch/finally` |
| `return` | `boundary`/`break` |
| `next` (in block) | `boundary`/`break` |
| `break` (exit block) | `boundary`/`break` |

### Classes and Modules

| Ruby | Scala 3 |
|------|---------|
| `class Foo < Bar` | `class Foo extends Bar` |
| `module Mixin` | `trait Mixin` |
| `include Mixin` | `extends/with Mixin` |
| `attr_accessor :x` | `var x: T` |
| `attr_reader :x` | `val x: T` or `def x: T` |
| `def initialize(...)` | primary constructor |
| `self.method` (class method) | companion object method |
| `Foo.new(...)` | `Foo(...)` |

### String Operations

| Ruby | Scala 3 |
|------|---------|
| `"#{expr}"` | `s"${expr}"` |
| `.gsub(pattern, replacement)` | `.replaceAll(pattern, replacement)` |
| `.sub(pattern, replacement)` | `.replaceFirst(pattern, replacement)` |
| `.match(pattern)` | `pattern.findFirstMatchIn(str)` |
| `.scan(pattern)` | `pattern.findAllIn(str)` |
| `.strip` | `.trim` |
| `.split(sep)` | `.split(sep)` |

### Regex

| Ruby | Scala 3 |
|------|---------|
| `/pattern/` | `"pattern".r` |
| `/pattern/i` | `"(?i)pattern".r` |
| `/pattern/m` | `"(?s)pattern".r` |
| `=~` | `.matches` or `findFirstIn` |
| `$1`, `$2` | `m.group(1)`, `m.group(2)` |

### Iteration

| Ruby | Scala 3 |
|------|---------|
| `.each { \|x\| ... }` | `.foreach(x => ...)` |
| `.map { \|x\| ... }` | `.map(x => ...)` |
| `.select { \|x\| ... }` | `.filter(x => ...)` |
| `.reject { \|x\| ... }` | `.filterNot(x => ...)` |
| `.reduce { \|acc, x\| ... }` | `.foldLeft(init)((acc, x) => ...)` |
| `.any? { \|x\| ... }` | `.exists(x => ...)` |
| `.all? { \|x\| ... }` | `.forall(x => ...)` |
| `.flat_map { \|x\| ... }` | `.flatMap(x => ...)` |

## Jekyll-Minifier Specifics

The jekyll-minifier gem works by:
1. Hooking into Jekyll's site generation pipeline
2. Processing HTML output through regex-based minification
3. Optionally minifying inline JavaScript and CSS
4. Preserving content within `<pre>`, `<script>`, `<style>` tags

For SSG, we reimplement this as:
- A pure function `minifyHtml(input: String, options: MinifyOptions): String`
- Configurable options for what to minify
- Consider using Scala XML for better HTML handling than regex
