/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

/** ISS-1097: structural renderer tests — escaping, balance, nesting, non-ASCII byte-offset slicing, and engine error paths.
  *
  * These tests exercise `HtmlHighlightRenderer.render` directly with crafted `HighlightSpan`s (no FFI / grammar loading), so they run on all platforms (JVM/JS/Native).
  *
  * Expected-value provenance: ssg-highlight is SSG-native (CLAUDE.md: "ssg-highlight wraps tree-sitter via FFI and has no original-src mapping"). The canonical semantics come from the renderer's own
  * documented contract in `HtmlHighlightRenderer.scala`:
  *   - HTML metacharacters `& < > " '` are escaped as `&amp; &lt; &gt; &quot; &#39;` by `appendEscaped`;
  *   - every `<span>` opened is closed (balanced HTML);
  *   - nested/overlapping spans use the close-and-reopen policy documented in the header;
  *   - byte offsets into the UTF-8 encoding of the source string are used for slicing.
  *
  * Non-ASCII byte-offset tests verify that `HighlightSpan` byte offsets correctly slice multi-byte UTF-8 characters. Note: ISS-1092 tracks a SEPARATE bug where the Scala.js tree-sitter engine feeds
  * UTF-16 offsets into the byte slicer — these renderer-level tests supply correct byte offsets directly and are therefore unaffected by ISS-1092.
  */
final class HtmlHighlightRendererIss1097Suite extends munit.FunSuite {

  // ── Helpers ──────────────────────────────────────────────────────────────

  /** Count non-overlapping occurrences of `needle` in `haystack`. */
  private def countOccurrences(haystack: String, needle: String): Int = {
    var count = 0
    var idx   = haystack.indexOf(needle)
    while (idx >= 0) {
      count += 1
      idx = haystack.indexOf(needle, idx + needle.length)
    }
    count
  }

  /** Strip all HTML tags; on correct rendering this yields the escaped source exactly once. */
  private def stripTags(html: String): String =
    html.replaceAll("<[^>]*>", "")

  /** Assert that every `<span` has a matching `</span>` (open count == close count). */
  private def assertBalanced(html: String)(implicit loc: munit.Location): Unit = {
    val opens  = countOccurrences(html, "<span")
    val closes = countOccurrences(html, "</span>")
    assertEquals(opens, closes, s"unbalanced HTML — $opens opens vs $closes closes in: $html")
  }

  // ── 1. Escaping ─────────────────────────────────────────────────────────

  test("ISS-1097 escaping: all five HTML metacharacters are escaped inside a span") {
    // Source contains all five characters that appendEscaped must handle: & < > " '
    // Byte layout (all ASCII, so byte offset == char offset):
    //   0:& 1:< 2:> 3:" 4:'
    val source = "&<>\"'"
    val spans  = Seq(HighlightSpan(0, 5, "meta"))
    val html   = HtmlHighlightRenderer.render(source, spans)
    assertEquals(
      html,
      "<span class=\"hl-meta\">&amp;&lt;&gt;&quot;&#39;</span>"
    )
  }

  test("ISS-1097 escaping: metacharacters are escaped OUTSIDE spans too") {
    // Source: "a<b" — no spans at all, the whole thing must be escaped.
    val source = "a<b"
    val html   = HtmlHighlightRenderer.render(source, Seq.empty)
    assertEquals(html, "a&lt;b")
    // Verify the raw '<' does NOT appear in the text content:
    assert(!stripTags(html).contains("<"), s"raw '<' leaked through escaping in: $html")
  }

  test("ISS-1097 escaping: mixed spanned and unspanned regions all escape correctly") {
    // Source: x&y<z (5 ASCII bytes); span covers only [2,3) = "y"
    // Expected: "x" escaped outside, "&" escaped outside, "y" inside span, "<" escaped outside, "z" outside.
    val source = "x&y<z"
    val spans  = Seq(HighlightSpan(2, 3, "var"))
    val html   = HtmlHighlightRenderer.render(source, spans)
    assertEquals(
      html,
      "x&amp;<span class=\"hl-var\">y</span>&lt;z"
    )
    // Verify text content after tag stripping is fully escaped (no raw & or <):
    val text = stripTags(html)
    assert(!text.contains("&<"), s"raw metacharacters in text content: $text")
  }

  // ── 2. Balance ──────────────────────────────────────────────────────────

  test("ISS-1097 balance: single span produces exactly one open and one close") {
    val html = HtmlHighlightRenderer.render("abcdef", Seq(HighlightSpan(1, 4, "k")))
    assertBalanced(html)
    assertEquals(countOccurrences(html, "<span"), 1)
    assertEquals(countOccurrences(html, "</span>"), 1)
  }

  test("ISS-1097 balance: multiple disjoint spans are all balanced") {
    val html = HtmlHighlightRenderer.render(
      "abcdefghij",
      Seq(
        HighlightSpan(0, 2, "a"),
        HighlightSpan(4, 6, "b"),
        HighlightSpan(8, 10, "c")
      )
    )
    assertBalanced(html)
    assertEquals(countOccurrences(html, "<span"), 3)
  }

  test("ISS-1097 balance: nested spans are balanced") {
    val html = HtmlHighlightRenderer.render(
      "abcdefghij",
      Seq(
        HighlightSpan(0, 10, "outer"),
        HighlightSpan(3, 7, "inner")
      )
    )
    assertBalanced(html)
    // 2 opens (outer + inner), 2 closes
    assertEquals(countOccurrences(html, "<span"), 2)
  }

  test("ISS-1097 balance: overlapping spans are balanced (close-and-reopen policy)") {
    val html = HtmlHighlightRenderer.render(
      "0123456789",
      Seq(
        HighlightSpan(0, 6, "a"),
        HighlightSpan(4, 9, "b")
      )
    )
    assertBalanced(html)
    // The text content must equal the original source (each byte once):
    assertEquals(stripTags(html), "0123456789")
  }

  // ── 3. Nesting ──────────────────────────────────────────────────────────

  test("ISS-1097 nesting: inner span opens inside outer and closes before outer closes") {
    // outer [0,8) wraps "function", inner [4,7) wraps "tio" inside it.
    val source = "function"
    val spans  = Seq(
      HighlightSpan(0, 8, "keyword"),
      HighlightSpan(4, 7, "substr")
    )
    val html = HtmlHighlightRenderer.render(source, spans)
    assertEquals(
      html,
      "<span class=\"hl-keyword\">func<span class=\"hl-substr\">tio</span>n</span>"
    )
  }

  test("ISS-1097 nesting: three-level deep nesting renders correctly") {
    // "abcdefgh" (8 bytes): outer [0,8), mid [1,7), inner [3,5)
    val source = "abcdefgh"
    val spans  = Seq(
      HighlightSpan(0, 8, "L1"),
      HighlightSpan(1, 7, "L2"),
      HighlightSpan(3, 5, "L3")
    )
    val html = HtmlHighlightRenderer.render(source, spans)
    assertEquals(
      html,
      "<span class=\"hl-L1\">a<span class=\"hl-L2\">bc<span class=\"hl-L3\">de</span>fg</span>h</span>"
    )
  }

  test("ISS-1097 nesting: adjacent non-overlapping spans at same level") {
    // "abcdef": [0,3) and [3,6) — back to back, no overlap, no nesting.
    val source = "abcdef"
    val spans  = Seq(
      HighlightSpan(0, 3, "first"),
      HighlightSpan(3, 6, "second")
    )
    val html = HtmlHighlightRenderer.render(source, spans)
    assertEquals(
      html,
      "<span class=\"hl-first\">abc</span><span class=\"hl-second\">def</span>"
    )
  }

  test("ISS-1097 nesting: overlapping spans with close-and-reopen produce correct structure") {
    // "abcdef" (6 bytes): span a [0,4), span b [2,6) — overlap at [2,4).
    // Per the close-and-reopen policy:
    //   - bytes [0,2): a open -> "ab"
    //   - bytes [2,4): b opens inside a -> "cd", at boundary 4: a ends, so b is closed, a is
    //     closed, b is re-opened
    //   - bytes [4,6): re-opened b -> "ef", then b closes
    val source = "abcdef"
    val spans  = Seq(
      HighlightSpan(0, 4, "a"),
      HighlightSpan(2, 6, "b")
    )
    val html = HtmlHighlightRenderer.render(source, spans)
    assertEquals(
      html,
      "<span class=\"hl-a\">ab<span class=\"hl-b\">cd</span></span><span class=\"hl-b\">ef</span>"
    )
  }

  // ── 4. Non-ASCII (renderer-level, correct byte offsets) ─────────────────

  test("ISS-1097 non-ASCII: 2-byte UTF-8 char (e-acute) highlighted by byte offsets") {
    // "cafe" + U+00E9 (e-acute) = "café"
    // UTF-8 bytes: c=0x63(0) a=0x61(1) f=0x66(2) e-acute=0xC3,0xA9(3,4)
    // Total: 5 bytes, 4 chars.
    // Span [3,5) covers the e-acute (both bytes of the 2-byte sequence).
    // Note: ISS-1092 tracks the separate JS-engine offset bug; this test supplies
    // correct byte offsets directly and is unaffected.
    val source = "café"
    val spans  = Seq(HighlightSpan(3, 5, "accent"))
    val html   = HtmlHighlightRenderer.render(source, spans)
    assertEquals(
      html,
      "caf<span class=\"hl-accent\">é</span>"
    )
  }

  test("ISS-1097 non-ASCII: span covering mixed ASCII and multi-byte chars") {
    // "x = π" (x space = space pi)
    // UTF-8 bytes: x=0x78(0) ' '=0x20(1) ==0x3D(2) ' '=0x20(3) pi=0xCF,0x80(4,5)
    // Total: 6 bytes, 5 chars.
    // Span [0,6) covers the entire source including the 2-byte pi.
    val source = "x = π"
    val spans  = Seq(HighlightSpan(0, 6, "expr"))
    val html   = HtmlHighlightRenderer.render(source, spans)
    assertEquals(
      html,
      "<span class=\"hl-expr\">x = π</span>"
    )
  }

  test("ISS-1097 non-ASCII: partial span over multi-byte string slices correctly") {
    // "élève" (e-acute, l, e-grave, v, e)
    // UTF-8 bytes: e-acute=0xC3,0xA9(0,1) l=0x6C(2) e-grave=0xC3,0xA8(3,4) v=0x76(5) e=0x65(6)
    // Total: 7 bytes, 5 chars.
    // Span [0,2) covers just the e-acute; span [3,5) covers just the e-grave.
    val source = "élève"
    val spans  = Seq(
      HighlightSpan(0, 2, "a1"),
      HighlightSpan(3, 5, "a2")
    )
    val html = HtmlHighlightRenderer.render(source, spans)
    assertEquals(
      html,
      "<span class=\"hl-a1\">é</span>l<span class=\"hl-a2\">è</span>ve"
    )
  }

  test("ISS-1097 non-ASCII: 3-byte UTF-8 char (CJK) highlighted correctly") {
    // "世界" = two CJK characters, each 3 bytes in UTF-8.
    // UTF-8 bytes: 0xE4,0xB8,0x96(0,1,2) 0xE7,0x95,0x8C(3,4,5)
    // Total: 6 bytes, 2 chars.
    // Span [0,3) covers the first CJK char; span [3,6) covers the second.
    val source = "世界"
    val spans  = Seq(
      HighlightSpan(0, 3, "c1"),
      HighlightSpan(3, 6, "c2")
    )
    val html = HtmlHighlightRenderer.render(source, spans)
    assertEquals(
      html,
      "<span class=\"hl-c1\">世</span><span class=\"hl-c2\">界</span>"
    )
  }

  test("ISS-1097 non-ASCII: escaping works correctly with multi-byte chars around metacharacters") {
    // "a<é>&b" — ASCII metacharacters interspersed with a multi-byte char.
    // UTF-8 bytes: a=0x61(0) <=0x3C(1) e-acute=0xC3,0xA9(2,3) >=0x3E(4) &=0x26(5) b=0x62(6)
    // Total: 7 bytes.
    // Span [1,5) covers "<e-acute>" — the span includes metacharacters and the multi-byte char.
    val source = "a<é>&b"
    val spans  = Seq(HighlightSpan(1, 5, "mix"))
    val html   = HtmlHighlightRenderer.render(source, spans)
    assertEquals(
      html,
      "a<span class=\"hl-mix\">&lt;é&gt;</span>&amp;b"
    )
  }

  // ── 5. Engine error paths ───────────────────────────────────────────────

  test("ISS-1097 error path: unknown language returns None, no exception") {
    val highlighter = SyntaxHighlighter.default
    val result      = highlighter.highlight("fn main() {}", "totally_nonexistent_lang_xyz_42")
    assertEquals(result, None)
  }

  test("ISS-1097 error path: empty string language returns None, no exception") {
    val highlighter = SyntaxHighlighter.default
    val result      = highlighter.highlight("some code", "")
    assertEquals(result, None)
  }

  test("ISS-1097 error path: supportsLanguage returns false for unknown language") {
    val highlighter = SyntaxHighlighter.default
    assertEquals(highlighter.supportsLanguage("totally_nonexistent_lang_xyz_42"), false)
  }

  // ── 6. Renderer edge cases (structural) ─────────────────────────────────

  test("ISS-1097 renderer: capture name with dots becomes CSS class with hyphens") {
    // captureName "variable.builtin" -> CSS class "hl-variable-builtin"
    val html = HtmlHighlightRenderer.render("abc", Seq(HighlightSpan(0, 3, "variable.builtin")))
    assert(html.contains("hl-variable-builtin"), s"dotted capture name not converted to hyphenated CSS class: $html")
    assertEquals(
      html,
      "<span class=\"hl-variable-builtin\">abc</span>"
    )
  }

  test("ISS-1097 renderer: empty source with no spans produces empty string") {
    val html = HtmlHighlightRenderer.render("", Seq.empty)
    assertEquals(html, "")
  }

  test("ISS-1097 renderer: empty source with a span that gets clamped to zero-width produces empty string") {
    // Span [0,5) on an empty source: clamped to [0,0), which is zero-width and dropped.
    val html = HtmlHighlightRenderer.render("", Seq(HighlightSpan(0, 5, "k")))
    assertEquals(html, "")
  }
}
