/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package minify

/** Oracle-cited tests for ISS-1026: `Minifier.fnmatch` must reproduce CRuby `File.fnmatch(pattern, path)` with NO flags — the only form jekyll-minifier uses (rb:1093 `File.fnmatch(e, file_name)`).
  *
  * Every case below was pinned against the REAL CRuby oracle on this machine (`ruby 2.6.10p210`), invoked exactly as `ruby -e 'p File.fnmatch(ARGV[0],ARGV[1])' -- <pat> <str>`. Each test carries the
  * oracle invocation and its printed result in its comment.
  *
  * `fnmatch` is private, so these drive it through the public `Minifier.minifyFile` exclude path (rb:1091-1093 `exclude.any? { |e| e == file_name || File.fnmatch(e, file_name) }`): a pattern that
  * fnmatch-matches the path makes the file pass through unchanged; one that does not lets it minify. The probe paths are chosen so the pattern is NEVER string-equal to the path, isolating the fnmatch
  * arm from the `e == file_name` arm (rb:1093). CSS is used because the minifier visibly rewrites it.
  */
final class FnmatchOracleIss1026Suite extends munit.FunSuite {

  // Input the CSS minifier provably changes (spaces around `:`/`;`, comment), so passthrough vs
  // minification is observable. minify(...) != cssInput is asserted once below as a sanity guard.
  private val cssInput = "body { color : red ; }\n/* banner */\n"

  private val cssMinified = Minifier.minify(cssInput, FileType.Css)

  test("sanity: the css minifier actually transforms cssInput") {
    assertNotEquals(cssMinified, cssInput, "probe relies on minify changing the input")
  }

  /** Assert `File.fnmatch(pattern, path)` == expected, verified through minifyFile passthrough.
    *
    * A true fnmatch -> excluded -> result == cssInput (passthrough). A false fnmatch (with pattern != path so the `==` arm cannot fire) -> minified -> result == cssMinified. Requires `path` to end in
    * `.css` so fileTypeFromPath picks Css, and `pattern != path`.
    */
  private def assertFnmatch(pattern: String, path: String, expected: Boolean)(implicit
    loc: munit.Location
  ): Unit = {
    assert(pattern != path, s"probe must isolate the fnmatch arm: pattern == path ($pattern)")
    val options = MinifyOptions(exclude = List(pattern))
    val result  = Minifier.minifyFile(cssInput, path, options)
    if (expected) {
      assertEquals(result, cssInput, s"fnmatch($pattern, $path) should be true -> passthrough")
    } else {
      assertEquals(result, cssMinified, s"fnmatch($pattern, $path) should be false -> minified")
    }
  }

  // ---------------------------------------------------------------------------
  // Finding 1 — leading-period rule (FNM_PERIOD on by default).
  // A `.` at index 0 of the WHOLE string is matched only by an explicit literal `.`.
  // ---------------------------------------------------------------------------

  test("F1: '*.css' does NOT match '.hidden.css' — ruby -e p File.fnmatch('*.css','.hidden.css') => false") {
    assertFnmatch("*.css", ".hidden.css", expected = false)
  }

  test("F1: '?profile.css' does NOT match '.profile.css' — ruby File.fnmatch('?profile.css','.profile.css') => false") {
    assertFnmatch("?profile.css", ".profile.css", expected = false)
  }

  test("F1: '[.]profile.css' does NOT match '.profile.css' — ruby File.fnmatch('[.]profile.css','.profile.css') => false") {
    assertFnmatch("[.]profile.css", ".profile.css", expected = false)
  }

  test(
    "F1: '*' even expanding to zero cannot eat a leading dot: '*.css' vs '.css' — ruby File.fnmatch('*.css','.css') => false"
  ) {
    // Distinct from the probe above: here `*` would match zero chars and `.css` literal would match
    // `.css`, yet FNM_PERIOD still forbids it.
    assertFnmatch("*.css", ".css", expected = false)
  }

  test(
    "F1: explicit literal dot DOES match a leading dot: '.*' vs '.profile.css' — ruby File.fnmatch('.*','.profile.css') => true"
  ) {
    assertFnmatch(".*", ".profile.css", expected = true)
  }

  // Rule-1 PATHNAME-interaction: with FNM_PATHNAME OFF there is NO per-component dot rule — only
  // index 0 of the whole string is protected, so `*`/`[..]` span `/` onto a dot freely.
  test(
    "F1/PATHNAME: 'a/*.css' matches 'a/.b.css' — ruby File.fnmatch('a/*.css','a/.b.css') => true (no per-component dot rule)"
  ) {
    assertFnmatch("a/*.css", "a/.b.css", expected = true)
  }

  test(
    "F1/PATHNAME: 'a/[.]b.css' matches 'a/.b.css' — ruby File.fnmatch('a/[.]b.css','a/.b.css') => true (class eats non-leading dot)"
  ) {
    assertFnmatch("a/[.]b.css", "a/.b.css", expected = true)
  }

  // ---------------------------------------------------------------------------
  // Finding 2 — unterminated `[` fails the whole match (NO literal-`[` fallback).
  // ---------------------------------------------------------------------------

  test("F2: unterminated '[ab*' does NOT match '[ab.css' — ruby File.fnmatch('[ab*','[ab.css') => false") {
    assertFnmatch("[ab*", "[ab.css", expected = false)
  }

  // ---------------------------------------------------------------------------
  // Finding 3 — `\` escapes inside `[...]` (FNM_NOESCAPE off).
  // ---------------------------------------------------------------------------

  test(
    "F3: '[a\\-c]x.css' treats '-' as literal, NOT a range — does NOT match 'bx.css' — ruby File.fnmatch('[a\\-c]x.css','bx.css') => false"
  ) {
    assertFnmatch("[a\\-c]x.css", "bx.css", expected = false)
  }

  test("F3: '[a\\-c]x.css' matches '-x.css' (literal '-') — ruby File.fnmatch('[a\\-c]x.css','-x.css') => true") {
    assertFnmatch("[a\\-c]x.css", "-x.css", expected = true)
  }

  test("F3: '[\\]]x.css' matches ']x.css' (escaped ']') — ruby File.fnmatch('[\\]]x.css',']x.css') => true") {
    assertFnmatch("[\\]]x.css", "]x.css", expected = true)
  }

  test("F3: '[a\\-c]x.css' still matches its real members 'ax.css'/'cx.css' — ruby ... 'ax.css'/'cx.css' => true") {
    assertFnmatch("[a\\-c]x.css", "ax.css", expected = true)
    assertFnmatch("[a\\-c]x.css", "cx.css", expected = true)
  }

  // ---------------------------------------------------------------------------
  // Finding 1 (bounce 2) — an ESCAPED endpoint still forms a RANGE; only an escaped DASH kills it.
  // CRuby `dir.c bracket()` reads each endpoint with an optional escape consumed FIRST, THEN checks a
  // RAW `-` for a range, then reads the second endpoint (escape-aware). So `[\a-c]` and `[a-\c]` are
  // both the range a..c, whereas `[a\-c]` (escaped dash) is the literals a,-,c.
  // ---------------------------------------------------------------------------

  test(
    "F1b: '[\\a-c]x.css' is the RANGE a..c (escaped first endpoint) — matches 'bx.css' — ruby File.fnmatch('[\\a-c]x.css','bx.css') => true"
  ) {
    assertFnmatch("[\\a-c]x.css", "bx.css", expected = true)
  }

  test("F1b: '[\\a-c]x.css' range hits its bounds 'ax.css'/'cx.css' — ruby ... => true") {
    assertFnmatch("[\\a-c]x.css", "ax.css", expected = true)
    assertFnmatch("[\\a-c]x.css", "cx.css", expected = true)
  }

  test(
    "F1b: '[\\a-c]x.css' is a range, so literal '-' is NOT a member — does NOT match '-x.css' — ruby File.fnmatch('[\\a-c]x.css','-x.css') => false"
  ) {
    assertFnmatch("[\\a-c]x.css", "-x.css", expected = false)
  }

  test("F1b: '[\\a-c]x.css' range excludes out-of-range 'dx.css' — ruby File.fnmatch('[\\a-c]x.css','dx.css') => false") {
    assertFnmatch("[\\a-c]x.css", "dx.css", expected = false)
  }

  test(
    "F1b: '[a-\\c]x.css' is the RANGE a..c (escaped second endpoint) — matches 'bx.css' — ruby File.fnmatch('[a-\\c]x.css','bx.css') => true"
  ) {
    assertFnmatch("[a-\\c]x.css", "bx.css", expected = true)
  }

  test(
    "F1b/regression: the existing escaped-DASH cases stay literal — '[a\\-c]x.css' does NOT range, no 'bx.css' — ruby File.fnmatch('[a\\-c]x.css','bx.css') => false"
  ) {
    // Guards the asymmetry: an escaped DASH (`\-`) is a literal member, not a range introducer, even
    // though escaped ENDPOINTS (above) still form ranges. (Mirrors the F3 literal-dash probes.)
    assertFnmatch("[a\\-c]x.css", "bx.css", expected = false)
    assertFnmatch("[a\\-c]x.css", "-x.css", expected = true)
  }

  // ---------------------------------------------------------------------------
  // Finding 2 (bounce 2) — CRuby fnmatch steps CHARACTERS (codepoints), not UTF-16 code units. Astral
  // chars (😀 = U+1F600, two UTF-16 units) count as ONE character. These literals live in shared
  // src/test and run on JVM, Scala.js AND Scala Native; verified against the CRuby oracle.
  // ---------------------------------------------------------------------------

  test("F2b: '?' matches the single astral char '😀' (one codepoint) — ruby File.fnmatch('?','😀') => true") {
    assertFnmatch("?x.css", "😀x.css", expected = true) // 😀 = U+1F600
  }

  test(
    "F2b: '??' does NOT match the single astral char '😀' (it is ONE char, not two) — ruby File.fnmatch('??','😀') => false"
  ) {
    assertFnmatch("??x.css", "😀x.css", expected = false) // 😀 = U+1F600
  }

  test("F2b: '[😀]' matches '😀' (astral class member) — ruby File.fnmatch('[😀]','😀') => true") {
    assertFnmatch("[😀]x.css", "😀x.css", expected = true) // 😀 = U+1F600
  }

  test("F2b/BMP-control: '?' still matches a single BMP char 'a' — ruby File.fnmatch('?','a') => true") {
    // Codepoint control: a plain BMP char is one codepoint, so `?` matches it exactly as before.
    assertFnmatch("?x.css", "ax.css", expected = true)
  }

  // ---------------------------------------------------------------------------
  // Finding 4 — dangling trailing `\` matches nothing (is dropped).
  // ---------------------------------------------------------------------------

  test("F4: 'a.css\\' matches 'a.css' (trailing backslash dropped) — ruby File.fnmatch('a.css\\','a.css') => true") {
    assertFnmatch("a.css\\", "a.css", expected = true)
  }

  test(
    "F4: dangling '\\' is dropped, not matched as a char: 'xy.css\\' vs 'x.css' — ruby File.fnmatch('xy.css\\','x.css') => false"
  ) {
    // The trailing `\` matches NO char (it is dropped), so the pattern is effectively `xy.css`, which
    // does not match `x.css`. (The directly analogous 'a.css\' vs 'a.css\' => false probe from the
    // audit cannot be exercised through minifyFile: there pattern == path, so rb:1093's `e ==
    // file_name` arm would exclude it regardless of fnmatch. This string-distinct probe pins the same
    // semantics — the `\` is dropped rather than matching a literal backslash — through the fnmatch arm.)
    assertFnmatch("xy.css\\", "x.css", expected = false)
  }

  // ---------------------------------------------------------------------------
  // Finding 5 — first `]` closes the class; `[]` is empty (matches nothing).
  // ---------------------------------------------------------------------------

  test(
    "F5: '[]a]x.css' does NOT match 'ax.css' — ruby File.fnmatch('[]a]x.css','ax.css') => false (empty class + literal 'a]')"
  ) {
    // Ruby reads `[]` as an EMPTY class (first `]` closes it -> matches nothing), then literal `a]x.css`.
    assertFnmatch("[]a]x.css", "ax.css", expected = false)
  }

  test("F5: '[]a]x.css' does NOT match ']x.css' — ruby File.fnmatch('[]a]x.css',']x.css') => false") {
    assertFnmatch("[]a]x.css", "]x.css", expected = false)
  }

  test("F5: empty class consumes nothing, so the literal tail must follow: '[]a]x.css' vs 'a]x.css' — ruby ... => false") {
    // Confirms `[]` is not zero-width-pass: even the literal continuation `a]x.css` cannot match,
    // because the empty class itself fails on the single char it is asked to match.
    assertFnmatch("[]a]x.css", "a]x.css", expected = false)
  }

  test(
    "F5: negated empty class '[!]' matches any one char — '[!]x.css' vs 'qx.css' — ruby File.fnmatch('[!]x.css','qx.css') => true"
  ) {
    // Complements the empty-class case: `[!]` (first `]` closes after `!`) is a NEGATED empty class,
    // i.e. matches any single char. ruby -e 'p File.fnmatch("[!]x.css","qx.css")' => true
    assertFnmatch("[!]x.css", "qx.css", expected = true)
  }

  // ---------------------------------------------------------------------------
  // C-1 hardening — last-star backtracking is O(n*m), not exponential.
  // Timing-sanity: pathological star patterns that would blow up an exponential backtracker must
  // resolve quickly with identical (oracle-verified) results.
  // ---------------------------------------------------------------------------

  test(
    "C1: many-star no-match resolves fast — ruby File.fnmatch('*a*a*a*a*a*a*b.css','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.css') => false"
  ) {
    // 30 'a's, no 'b' before .css: an exponential matcher would explode; last-star backtracking is linear.
    val path = "a" * 30 + ".css"
    val t0   = System.nanoTime()
    assertFnmatch("*a*a*a*a*a*a*b.css", path, expected = false)
    val elapsedMs = (System.nanoTime() - t0) / 1000000L
    assert(elapsedMs < 2000L, s"many-star no-match should be fast, took ${elapsedMs}ms")
  }

  test("C1: many-star match resolves fast — ruby File.fnmatch('*a*a*a*a*a*b.css','aaaaaaaaaaaaaaaaaaaaaaaaaab.css') => true") {
    val path = "a" * 26 + "b.css"
    val t0   = System.nanoTime()
    assertFnmatch("*a*a*a*a*a*b.css", path, expected = true)
    val elapsedMs = (System.nanoTime() - t0) / 1000000L
    assert(elapsedMs < 2000L, s"many-star match should be fast, took ${elapsedMs}ms")
  }
}
