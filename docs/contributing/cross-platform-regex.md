# Cross-Platform Regex Guide

Scala Native uses Google's **re2** regex engine, which intentionally disables
features that can cause catastrophic backtracking (ReDoS). Scala.js uses the
browser's regex engine, which supports most JVM features but has some gaps.

All regex in SSG **must work on all three platforms** (JVM, Scala.js, Scala Native).

## Features NOT Supported on Scala Native (re2)

| Feature | Syntax | Status |
|---------|--------|--------|
| Lookahead | `(?=...)`, `(?!...)` | **Not supported** |
| Lookbehind | `(?<=...)`, `(?<!...)` | **Not supported** |
| Backreferences | `\1`, `\2` | **Not supported** |
| Literal quoting | `\Q...\E` | **Not supported** |
| Unicode categories | `\p{Xx}`, `\p{IsXxx}` | **Not supported** |
| Char class intersection | `[...&&[...]]` | **Not supported** |
| Inline flags | `(?i:...)`, `(?s:...)` | **Not supported** |
| Atomic groups | `(?>...)` | **Not supported** |

### Supported on all platforms

| Feature | Syntax | Notes |
|---------|--------|-------|
| Global flags | `(?i)`, `(?s)`, `(?m)` at pattern start | Works on all |
| `Pattern.CASE_INSENSITIVE` | Flag on `Pattern.compile()` | Works on all |
| Character classes | `\w`, `\d`, `\s`, `[a-z]` | Works on all |
| Quantifiers | `*`, `+`, `?`, `{n,m}` | Works on all |
| Non-greedy | `*?`, `+?` | Works on all |
| Capture groups | `(...)`, `$1` in replacement | Works on all |
| Non-capturing groups | `(?:...)` | Works on all |
| Alternation | `a\|b` | Works on all |
| Anchors | `^`, `$`, `\b` | Works on all |
| `Pattern.quote()` | Escapes metacharacters | Works on all (no `\Q..\E` internally on Native) |

## Workaround Patterns

### 1. Lookahead → Programmatic Post-Match Check

**Instead of**: `(?=...)` or `(?!...)` in the regex

**Do**: Match without the lookahead, then check the condition in code.

```scala
// BAD: negative lookahead — fails on Scala Native
val HtmlComment = "<!--(?!\\[if)(.*?)-->".r

// GOOD: match all comments, filter conditionals in code
private def removeComments(html: String): String = {
  // ... state machine that checks for "<!--[if" before skipping
  if (html.regionMatches(i, "<!--[if", 0, 7)) {
    // conditional comment — preserve
  } else {
    // regular comment — skip
  }
}
```

**Real examples**:
- `ssg-minify/.../HtmlMinifier.scala` — HTML comment removal
- `ssg-md/.../FencedCodeBlockParser.scala` — fence character validation
- `ssg-md/.../ListBlockParser.scala` — space/tab after list marker
- `ssg-md/.../Parsing.scala` — link destinations

### 2. Backreferences → Match + Programmatic Check

**Instead of**: `\1` to match repeated characters

**Do**: Match the general pattern, then verify in code.

```scala
// BAD: backreferences — fails on Scala Native
val LongHex = "#([0-9a-fA-F])\\1([0-9a-fA-F])\\2([0-9a-fA-F])\\3".r

// GOOD: match all 6-digit hex, check for pairs in code
val SixDigitHex = "#([0-9a-fA-F]{6})".r
SixDigitHex.replaceAllIn(css, m => {
  val hex = m.group(1)
  if (hex.charAt(0) == hex.charAt(1) &&
      hex.charAt(2) == hex.charAt(3) &&
      hex.charAt(4) == hex.charAt(5)) {
    s"#${hex.charAt(0)}${hex.charAt(2)}${hex.charAt(4)}"
  } else {
    m.matched
  }
})
```

**Real example**: `ssg-minify/.../CssMinifier.scala` — hex color shortening

### 3. `\Q...\E` / `Pattern.quote()` → RegexCompat.regexEscape()

**Instead of**: `"\\Q" + str + "\\E"` or `Pattern.quote(str)` in pattern building

**Do**: Use `RegexCompat.regexEscape()` (defined in `ssg-md`).

```scala
// BAD: \Q..\E — fails on Scala Native
val pattern = Pattern.compile("\\Q" + userInput + "\\E")

// GOOD: manual metacharacter escaping
val pattern = Pattern.compile(RegexCompat.regexEscape(userInput))
```

**Note**: `Pattern.quote()` itself works on all platforms (it escapes internally).
The issue is only when using `\Q..\E` directly in pattern strings.

**Real examples**:
- `ssg-md/.../RegexCompat.scala` — the utility itself
- `ssg-md/.../AbbreviationNodePostProcessor.scala` — abbreviation matching
- `ssg-md/.../Parsing.scala` — list item markers

### 4. Unicode Categories → Explicit Codepoint Ranges

**Instead of**: `\p{Pc}`, `\p{Pd}`, `\p{IsAlphabetic}`, etc.

**Do**: Enumerate the actual Unicode codepoints.

```scala
// BAD: Unicode property — fails on Scala Native
val punct = "\\p{Pc}\\p{Pd}".r

// GOOD: explicit ranges
val UNICODE_Pc = "\u203F\u2040"               // connector punctuation
val UNICODE_Pd = "\\u2010-\\u2015\\u2E3A\\u2E3B" // dash punctuation
```

**Real example**: `ssg-md/.../Parsing.scala` — `UNICODE_PUNCT_ALL` constant (lines 311-350)

### 5. Inline Flags → Global Flag or Explicit Alternation

**Instead of**: `(?i:TOC)` inside a pattern

**Do**: Use `Pattern.CASE_INSENSITIVE` flag on the whole pattern, or spell out alternatives.

```scala
// BAD: inline case-insensitive flag — fails on Scala Native
val pattern = Pattern.compile("^\\[(?i:TOC)]$")

// GOOD option A: global flag
val pattern = Pattern.compile("^\\[TOC]$", Pattern.CASE_INSENSITIVE)

// GOOD option B: explicit alternation
val pattern = Pattern.compile("^\\[[Tt][Oo][Cc]]$")
```

**Real examples**:
- `ssg-md/.../TocBlockParser.scala`
- `ssg-md/.../HtmlDeepParser.scala`

### 6. Complex Patterns → State Machines

When a regex needs multiple unsupported features (lookahead + DOTALL + backrefs),
replace the entire regex with a character-by-character state machine using
`boundary`/`break` for control flow.

```scala
import scala.util.boundary
import scala.util.boundary.break

private def findClosingTag(html: String, start: Int, len: Int, closeTag: String): Int = {
  boundary[Int] {
    var i = start + 1
    while (i + closeTag.length <= len) {
      if (html.regionMatches(true, i, closeTag, 0, closeTag.length)) {
        break(i)
      }
      i += 1
    }
    -1
  }
}
```

**Real examples**:
- `ssg-minify/.../HtmlMinifier.scala` — comment removal, inline compression
- `ssg-minify/.../PreservedBlock.scala` — tag extraction
- `ssg-liquid/.../Strip_HTML.scala` — HTML tag stripping
- `ssg-liquid/.../Escape_Once.scala` — entity detection

## Debugging Regex Failures

**Symptom**: A test suite has 1 passing test and N-1 failures on Native.

**Cause**: An object with a `val` regex field uses an unsupported feature. The
object fails to initialize at class-load time, causing ALL tests that reference
it to fail — not just the regex-specific test.

**Fix**: Check all `private val ... = "...".r` patterns in the object for
unsupported features. The offending regex is typically the first `val` that
uses backreferences, lookahead, or Unicode properties.

## Testing

All regex-dependent code is tested on all 3 platforms:
```
ssg-dev test unit --all --module <module>
```

The `ssg-md` module has a dedicated `RegexCompatibilityTest` suite
(`ssg-md/src/test/scala/ssg/md/core/test/RegexCompatibilityTest.scala`)
that specifically validates cross-platform regex behavior.

## Key Utility: `regionMatches`

`String.regionMatches(ignoreCase, offset, other, otherOffset, len)` is the
Swiss Army knife for cross-platform string matching. It works identically on
all platforms and replaces many regex patterns:

- Case-insensitive substring match without `(?i)`
- Literal prefix/suffix checking without `\Q..\E`
- Tag detection without lookahead
