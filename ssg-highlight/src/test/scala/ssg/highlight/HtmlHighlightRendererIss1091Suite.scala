/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

/** Red tests for ISS-1091 (R0610-P1): `HtmlHighlightRenderer.render(source, spans)` drops nested captures and emits unbalanced HTML on overlapping spans.
  *
  * Expected-value provenance (C11): ssg-highlight is SSG-native — it wraps tree-sitter via FFI and has no original-src mapping (CLAUDE.md), so the canonical semantics come from the renderer's own
  * documented contract and from tree-sitter's strict node-nesting property:
  *   - every source byte is emitted exactly once, HTML-escaped (`& < > " '`);
  *   - every `<span>` opened is closed — the output is balanced HTML;
  *   - capture ranges produced by a tree-sitter tree query are nested-or-disjoint (syntax tree nodes nest strictly), so a capture contained in another must render as a nested `<span>` — the same
  *     semantics as tree-sitter-highlight's `HtmlRenderer`, whose HighlightStart/Source/HighlightEnd event model produces properly nested tags.
  *
  * Bug being demonstrated (HtmlHighlightRenderer.scala lines 15-31): spans are sorted by `(startByte, -endByte)` (outer-first); the outer span is emitted whole including its closing `</span>`, after
  * which every inner span satisfies `startByte < pos` and either
  *   - `endByte <= pos`: it is SILENTLY SKIPPED (nested captures never rendered), or
  *   - `endByte > pos` (partial overlap): a stray `</span>` is appended (the previous span was already closed), the text `[pos, endByte)` is re-emitted (duplication), a `<span>` is opened that is
  *     never closed, and `pos` does not advance, so the tail from `pos` is emitted a second time.
  *
  * The renderer is a pure function of `(String, Seq[HighlightSpan])` and needs no FFI or grammar loading, so this suite extends plain munit.FunSuite and runs on all platforms. All sources are ASCII,
  * so byte offsets equal char offsets.
  */
final class HtmlHighlightRendererIss1091Suite extends munit.FunSuite {

  private def countOccurrences(haystack: String, needle: String): Int = {
    var count = 0
    var idx   = haystack.indexOf(needle)
    while (idx >= 0) {
      count += 1
      idx = haystack.indexOf(needle, idx + needle.length)
    }
    count
  }

  /** Strips all tags; on a correct rendering this must yield the escaped source exactly once (catches both text loss and text duplication).
    */
  private def stripTags(html: String): String =
    html.replaceAll("<[^>]*>", "")

  /** Walks `<span`/`</span>` tags left to right tracking nesting depth; returns the minimum depth seen. A negative result means a `</span>` appeared with no matching open tag (stack underflow).
    * Escaped text never contains a raw `<`, so the scan is unambiguous.
    */
  private def minTagDepth(html: String): Int = {
    var depth = 0
    var min   = 0
    var i     = 0
    while (i < html.length)
      if (html.startsWith("</span>", i)) {
        depth -= 1
        if (depth < min) {
          min = depth
        }
        i += "</span>".length
      } else if (html.startsWith("<span", i)) {
        depth += 1
        i += "<span".length
      } else {
        i += 1
      }
    min
  }

  // ── Red: nested captures must be rendered as nested spans ─────────────

  test("ISS-1091: nested capture is rendered as a nested span, not dropped") {
    val source = "def foo(x)" // 10 ASCII bytes
    val spans  = Seq(
      HighlightSpan(0, 10, "function"), // outer: whole source
      HighlightSpan(4, 7, "name") // inner: "foo", fully inside the outer span
    )
    // Today the inner span hits the silent-skip branch and the output is
    // <span class="hl-function">def foo(x)</span> — the "name" capture vanishes.
    assertEquals(
      HtmlHighlightRenderer.render(source, spans),
      "<span class=\"hl-function\">def <span class=\"hl-name\">foo</span>(x)</span>"
    )
  }

  test("ISS-1091: two-level nesting renders three properly nested spans") {
    val source = "abcdefghij" // 10 ASCII bytes
    val spans  = Seq(
      HighlightSpan(0, 10, "outer"),
      HighlightSpan(2, 8, "mid"),
      HighlightSpan(4, 6, "inner")
    )
    // [0,10) wraps "abcdefghij", [2,8) wraps "cdefgh" inside it, [4,6) wraps "ef" inside that.
    assertEquals(
      HtmlHighlightRenderer.render(source, spans),
      "<span class=\"hl-outer\">ab<span class=\"hl-mid\">cd<span class=\"hl-inner\">ef</span>gh</span>ij</span>"
    )
  }

  // ── Red: balance invariants under partial overlap ─────────────────────

  test("ISS-1091: partial overlap keeps HTML balanced and emits each byte exactly once") {
    val source = "0123456789" // 10 ASCII bytes
    val html   = HtmlHighlightRenderer.render(
      source,
      Seq(
        HighlightSpan(0, 5, "a"),
        HighlightSpan(3, 8, "b") // overlaps [3,5) with the previous span
      )
    )
    // The exact rendering of partially-overlapping spans (split point, which span is
    // re-opened) is the implementer's choice within the balanced-nesting contract; the
    // implementer MUST state and test its chosen overlap policy in the fix commit.
    // This red commit pins only the invariants that any correct policy satisfies:
    assertEquals(
      countOccurrences(html, "<span"),
      countOccurrences(html, "</span>"),
      s"open/close span tag counts must match in: $html"
    )
    assertEquals(
      stripTags(html),
      source,
      s"stripping tags must yield the escaped source exactly once (no loss, no duplication) in: $html"
    )
    assert(
      minTagDepth(html) >= 0,
      s"a </span> appeared with no matching <span (stack underflow) in: $html"
    )
  }

  // ── Red: identical-range spans must both be rendered ──────────────────

  test("ISS-1091: identical-range spans are both rendered, nested and balanced") {
    val source = "abc"
    val html   = HtmlHighlightRenderer.render(
      source,
      Seq(
        HighlightSpan(0, 3, "a"),
        HighlightSpan(0, 3, "b")
      )
    )
    // Sorting by (startByte, -endByte) puts the identical ranges adjacent; today the first
    // is emitted whole and the second hits the silent-skip branch and is dropped. Both
    // captures must appear (one nested in the other), balanced, with the text once.
    assert(html.contains("hl-a"), s"capture 'a' missing in: $html")
    assert(html.contains("hl-b"), s"capture 'b' missing in: $html")
    assertEquals(
      countOccurrences(html, "<span"),
      countOccurrences(html, "</span>"),
      s"open/close span tag counts must match in: $html"
    )
    assertEquals(stripTags(html), "abc", s"text must appear exactly once in: $html")
    assert(minTagDepth(html) >= 0, s"stack underflow in: $html")
  }

  // ── Controls (must pass today — prove the assertions are sound) ───────

  test("ISS-1091 control: single span renders exact output") {
    val source = "val x = 1"
    assertEquals(
      HtmlHighlightRenderer.render(source, Seq(HighlightSpan(0, 3, "keyword"))),
      "<span class=\"hl-keyword\">val</span> x = 1"
    )
  }

  test("ISS-1091 control: escapes & < > \" ' inside and outside spans") {
    // index:  0:a 1:& 2:" 3:b 4:space 5:< 6:c 7:> 8:space 9:' 10:d 11:'
    val source = "a&\"b <c> 'd'"
    assertEquals(
      HtmlHighlightRenderer.render(source, Seq(HighlightSpan(5, 8, "tag"))),
      "a&amp;&quot;b <span class=\"hl-tag\">&lt;c&gt;</span> &#39;d&#39;"
    )
  }

  test("ISS-1091 control: empty span list renders the escaped source") {
    assertEquals(HtmlHighlightRenderer.render("a&b", Seq.empty), "a&amp;b")
  }
}
