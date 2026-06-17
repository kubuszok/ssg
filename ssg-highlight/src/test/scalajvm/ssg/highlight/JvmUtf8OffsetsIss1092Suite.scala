/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

/** JVM control suite for ISS-1092 (R0610) — companion to `JsUtf8OffsetsIss1092Suite` (src/test/scalajs), which is the red test.
  *
  * Asserts the identical source/query/expected values on the JVM FFI path, proving the expected UTF-8 byte offsets are correct: the JVM impl feeds `source.getBytes("UTF-8")` to
  * `ts_parser_parse_string` and fills `HighlightSpan(startByte, endByte, _)` from `ts_node_start_byte`/`ts_node_end_byte`, so spans are UTF-8 byte offsets and `HtmlHighlightRenderer.render`'s
  * `source.getBytes("UTF-8")` slicing (HtmlHighlightRenderer.scala line 31) is consistent with them. This suite PASSES today; the JS suite fails until the JS platform converts web-tree-sitter's
  * UTF-16 code-unit indices to UTF-8 byte offsets. Byte math for the fixture is in the JS suite's header.
  */
final class JvmUtf8OffsetsIss1092Suite extends munit.FunSuite {

  /** One 2-byte (α), one 3-byte (€) and one 4-byte (😀) character before the captured token — same fixture as `JsUtf8OffsetsIss1092Suite`. */
  private val source = "// α€😀\nconst x = 1;"

  /** Query capturing exactly one node, the number literal `1`. */
  private val query = "(number) @constant.numeric"

  /** UTF-8 byte offsets of the `1` token: bytes 0..12 are `// α€😀\n` (3 + 2 + 3 + 4 + 1), `const x = ` is 13..22, `1` is [23,24). */
  private val expectedSpans = Seq(HighlightSpan(23, 24, "constant.numeric"))

  /** The correct render wraps the `1` token. */
  private val expectedHtml = "// α€😀\nconst x = <span class=\"hl-constant-numeric\">1</span>;"

  test("ISS-1092 control: JVM platform spans carry UTF-8 byte offsets for non-ASCII source") {
    val spans = TreeSitterPlatform.highlight(source, "javascript", query)
    assertEquals(spans, expectedSpans)
  }

  test("ISS-1092 control: JVM rendered HTML wraps the captured token `1`") {
    val spans = TreeSitterPlatform.highlight(source, "javascript", query)
    val html  = HtmlHighlightRenderer.render(source, spans)
    assertEquals(html, expectedHtml)
  }
}
