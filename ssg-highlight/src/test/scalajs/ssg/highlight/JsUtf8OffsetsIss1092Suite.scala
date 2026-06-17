/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

/** Red tests for ISS-1092 (R0610): the Scala.js platform stores web-tree-sitter's `c.node.startIndex`/`c.node.endIndex` — UTF-16 code-unit indices into the JS source string — verbatim into
  * `HighlightSpan(startByte, endByte, captureName)` (TreeSitterPlatformImpl.scala, scalajs, line 58 and 73-77), but `HtmlHighlightRenderer.render` slices `source.getBytes("UTF-8")` by those offsets
  * (HtmlHighlightRenderer.scala line 31), the same way JVM and Native do. Any non-ASCII source therefore mis-slices on JS only.
  *
  * Expected-value provenance: ssg-highlight is SSG-native (wraps tree-sitter via FFI, no original-src mapping — CLAUDE.md), so the canonical offset semantics are the JVM/Native FFI contract: the JVM impl
  * feeds `source.getBytes("UTF-8")` to `ts_parser_parse_string` and fills spans from `ts_node_start_byte`/`ts_node_end_byte` — i.e. `HighlightSpan.startByte`/`endByte` are UTF-8 byte offsets, which is
  * exactly what the renderer's `getBytes("UTF-8")` slicing consumes. The companion control suite `JvmUtf8OffsetsIss1092Suite` (src/test/scalajvm) asserts the identical source/query/expectations on JVM and
  * passes today, proving the expected values are the correct ones.
  *
  * Empirical probe (node v24.12.0, the same web-tree-sitter.js bundled in `com.kubuszok % wasm-provider-tree-sitter % 0.1.0`, build.sbt line 190): for the fixture below the `1` token reports
  * `startIndex = 18, endIndex = 19` (UTF-16 code units) while its UTF-8 byte offsets are 23 and 24.
  *
  * Fixture byte math (one character of each multi-byte UTF-8 width before the captured token):
  * {{{
  * source = "// α€😀\nconst x = 1;"            (α = U+03B1, € = U+20AC, 😀 = U+1F600)
  * UTF-8  bytes:      "// " 0..2 | α 3..4 (2B) | € 5..7 (3B) | 😀 8..11 (4B) | \n 12 | "const x = 1;" 13..24 → '1' at [23,24), total 25 bytes
  * UTF-16 code units: "// " 0..2 | α 3 | € 4 | 😀 5..6 (surrogate pair) | \n 7 | "const x = 1;" 8..19 → '1' at [18,19), total 20 units
  * }}}
  * So the correct span is [23,24) and the buggy JS span is [18,19); rendering the buggy span slices UTF-8 bytes 18..19 = the space after `const` (bytes 13..17 are `const`), wrapping `" "` instead of `"1"`.
  *
  * Provisioning (not committed; required for this suite to run rather than skip): extract the `wasm/` directory of the coursier-cached
  * `com/kubuszok/wasm-provider-tree-sitter/0.1.0/wasm-provider-tree-sitter-0.1.0.jar` (the exact artifact this module's JS build depends on) into `$TREE_SITTER_WASM_DIR` (default `/tmp/ts-wasm`,
  * build.sbt line 194), dropping the `wasm/` prefix and the macOS `._*` AppleDouble entries, yielding `web-tree-sitter.js`, `web-tree-sitter.wasm` and `grammars/tree-sitter-*.wasm`.
  */
final class JsUtf8OffsetsIss1092Suite extends munit.FunSuite {

  /** One 2-byte (α), one 3-byte (€) and one 4-byte (😀, a UTF-16 surrogate pair) character before the captured token — see byte math in the suite header. */
  private val source = "// α€😀\nconst x = 1;"

  /** Query capturing exactly one node, the number literal `1`. */
  private val query = "(number) @constant.numeric"

  /** UTF-8 byte offsets of the `1` token (the JVM/Native FFI contract; see header). The buggy JS impl reports the UTF-16 code-unit offsets [18,19) instead. */
  private val expectedSpans = Seq(HighlightSpan(23, 24, "constant.numeric"))

  /** The correct render wraps the `1` token; the buggy JS render wraps the space after `const` (UTF-8 bytes [18,19)). */
  private val expectedHtml = "// α€😀\nconst x = <span class=\"hl-constant-numeric\">1</span>;"

  private def grammarAvailable: Boolean = TreeSitterPlatform.availableGrammars.contains("javascript")

  test("ISS-1092: JS platform spans carry UTF-8 byte offsets (JVM/Native contract), not UTF-16 code-unit indices") {
    assume(grammarAvailable, "ISS-1161: JS grammar loading unavailable in this environment (no wasm files under TREE_SITTER_WASM_DIR)")
    val spans = TreeSitterPlatform.highlight(source, "javascript", query)
    assertEquals(spans, expectedSpans)
  }

  test("ISS-1092: rendered HTML wraps the captured token `1`, not a mis-sliced fragment of the UTF-8 source") {
    assume(grammarAvailable, "ISS-1161: JS grammar loading unavailable in this environment (no wasm files under TREE_SITTER_WASM_DIR)")
    val spans = TreeSitterPlatform.highlight(source, "javascript", query)
    val html  = HtmlHighlightRenderer.render(source, spans)
    assertEquals(html, expectedHtml)
  }
}
