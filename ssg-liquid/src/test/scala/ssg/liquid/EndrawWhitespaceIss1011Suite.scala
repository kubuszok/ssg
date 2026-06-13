/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

/** Red tests for ISS-1011: `{% endraw -%}` must be recognized and must strip the whitespace that FOLLOWS the endraw tag — the text after the raw block must still render, not be swallowed.
  *
  * liqp oracle (original-src/liqp):
  *   - LiquidLexer.g4:212 `RawStart : 'raw' WhitespaceChar* '%}' -> popMode, pushMode(IN_RAW);` opens the raw mode after `{% raw %}`.
  *   - LiquidLexer.g4:284 (mode IN_RAW) `RawEnd : '{%' WhitespaceChar* 'endraw' -> popMode;` matches only `{% ... endraw` (NOT the closing `%}`/`-%}`) and pops back to the default mode.
  *   - LiquidParser.g4:121-122 `raw_tag : TagStart RawStart raw_body RawEnd TagEnd ;` — after `RawEnd` a separate `TagEnd` token closes the tag.
  *   - LiquidLexer.g4:129-133 `TagEnd : ... | '-%}' WhitespaceChar* | '%}' ;` — the closing token for `endraw` is an ordinary `TagEnd`, so `-%}` is a legal close and `'-%}' WhitespaceChar*` consumes
  *     (strips) the whitespace that follows the tag, identically to any other tag. Therefore, in liqp, `{% raw %}BODY{% endraw -%}   TRAILING` yields raw body "BODY" and renders "TRAILING" with the
  *     inter-tag whitespace stripped. This matches the standard Liquid whitespace control rule: `-%}` strips whitespace AFTER the tag; `{%-` strips whitespace BEFORE it.
  *
  * Bug in the SSG port (LiquidLexer.scala:418-426): scanRawBody only accepts a literal `%}` after `endraw`; for `-%}` the check at line 420 fails, no endraw TAG_START/RAW/TAG_END tokens are emitted,
  * the block is never terminated and the remainder of the template is swallowed/corrupted.
  */
final class EndrawWhitespaceIss1011Suite extends munit.FunSuite {

  /* Control: the plain `{% endraw %}` form already works — raw body is literal,
   * trailing text renders verbatim (no whitespace stripping). Must keep passing. */
  test("ISS-1011 control: plain endraw renders trailing text") {
    assertEquals(
      Template.parse("{% raw %}{{a}}{% endraw %}   TRAILING").render(),
      "{{a}}   TRAILING"
    )
  }

  /* Red: `{% endraw -%}` must be recognized; the `-%}` strips the whitespace that
   * follows the tag, and the trailing text must still render (not be swallowed). */
  test("ISS-1011 red: endraw with -%} strips trailing whitespace and keeps trailing text") {
    assertEquals(
      Template.parse("{% raw %}{{a}}{% endraw -%}   TRAILING").render(),
      "{{a}}TRAILING"
    )
  }

  /* Red: the raw body itself must be preserved verbatim when closed with `-%}`,
   * and the trailing text must follow it (stripped of leading whitespace). */
  test("ISS-1011 red: endraw with -%} preserves raw body verbatim") {
    assertEquals(
      Template.parse("{% raw %}BODY{% endraw -%}\n  after").render(),
      "BODYafter"
    )
  }
}
