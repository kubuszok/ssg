/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

/** Pins the exact rendering chosen for cases the ISS-1091 red suite leaves to the
  * implementer (partial overlap, identical ranges, zero-width spans), and the documented
  * overlap policy in `HtmlHighlightRenderer`'s header: **close-and-reopen (split on
  * overlap)**.
  *
  * Expected-value provenance: ssg-highlight is SSG-native (no original-src mapping), so the
  * canonical contract is the renderer's own header comment plus the universal invariants
  * (each byte once, balanced tags, no stack underflow) that the red suite already enforces.
  * These tests pin the concrete byte-for-byte output the chosen policy produces, so any
  * future change to the policy is visible and deliberate. All sources are ASCII, so byte
  * offsets equal char offsets.
  */
final class HtmlHighlightRendererOverlapPolicySuite extends munit.FunSuite {

  test("policy: partial overlap splits the later span (close-and-reopen)") {
    // [0,5)a + [3,8)b over "0123456789": at offset 5, span a must close, but b (opened at
    // 3) is above it on the stack, so b is closed first, a is popped, and b is re-opened —
    // splitting b across the </span> of a.
    val html = HtmlHighlightRenderer.render(
      "0123456789",
      Seq(HighlightSpan(0, 5, "a"), HighlightSpan(3, 8, "b")),
    )
    assertEquals(
      html,
      "<span class=\"hl-a\">012<span class=\"hl-b\">34</span></span><span class=\"hl-b\">567</span>89",
    )
  }

  test("policy: identical ranges nest in input order, second inside first") {
    // [0,3)a + [0,3)b over "abc": both open at 0 (a first, then b — equal endByte keeps
    // input order), both close at 3 (b on top closes first).
    val html = HtmlHighlightRenderer.render(
      "abc",
      Seq(HighlightSpan(0, 3, "a"), HighlightSpan(0, 3, "b")),
    )
    assertEquals(
      html,
      "<span class=\"hl-a\"><span class=\"hl-b\">abc</span></span>",
    )
  }

  test("policy: span starting at the same offset opens widest-first") {
    // [2,5)wide + [2,3)narrow over "abcdef": both start at 2; the wider one opens first so
    // it wraps the narrower.
    val html = HtmlHighlightRenderer.render(
      "abcdef",
      Seq(HighlightSpan(2, 3, "narrow"), HighlightSpan(2, 5, "wide")),
    )
    assertEquals(
      html,
      "ab<span class=\"hl-wide\"><span class=\"hl-narrow\">c</span>de</span>f",
    )
  }

  test("policy: zero-width spans emit nothing") {
    val html = HtmlHighlightRenderer.render(
      "abc",
      Seq(HighlightSpan(1, 1, "empty")),
    )
    assertEquals(html, "abc")
  }

  test("policy: inverted span (start > end) is dropped, source still rendered once") {
    val html = HtmlHighlightRenderer.render(
      "abc",
      Seq(HighlightSpan(2, 1, "bad")),
    )
    assertEquals(html, "abc")
  }

  test("policy: offsets past the source length are clamped, not thrown") {
    // endByte beyond length clamps to length; the whole source is wrapped.
    val html = HtmlHighlightRenderer.render(
      "abc",
      Seq(HighlightSpan(0, 99, "k")),
    )
    assertEquals(html, "<span class=\"hl-k\">abc</span>")
  }

  test("policy: three-way overlap stays balanced and emits each byte once") {
    // a[0,4) b[2,6) c[4,8) over "01234567": chained partial overlaps.
    val html = HtmlHighlightRenderer.render(
      "01234567",
      Seq(HighlightSpan(0, 4, "a"), HighlightSpan(2, 6, "b"), HighlightSpan(4, 8, "c")),
    )
    assertEquals(
      html,
      "<span class=\"hl-a\">01<span class=\"hl-b\">23</span></span>" +
        "<span class=\"hl-b\"><span class=\"hl-c\">45</span></span>" +
        "<span class=\"hl-c\">67</span>",
    )
  }
}
