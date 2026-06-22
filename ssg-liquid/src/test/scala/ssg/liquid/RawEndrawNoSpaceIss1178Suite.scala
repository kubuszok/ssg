/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

/** Red tests for ISS-1178: `{% endraw-%}` (NO space before the whitespace-control close) must be recognized — the raw block must CLOSE and the raw body must be preserved verbatim, with `-%}`
  * stripping the whitespace that FOLLOWS the tag.
  *
  * liqp oracle (original-src/liqp/src/main/antlr4/liquid/parser/v4):
  *   - LiquidLexer.g4:212 `RawStart : 'raw' WhitespaceChar* '%}' -> popMode, pushMode(IN_RAW);` opens raw mode after `{% raw %}`.
  *   - LiquidLexer.g4:284 (mode IN_RAW) `RawEnd : '{%' WhitespaceChar* 'endraw' -> popMode;` matches the LITERAL `endraw` only — it does NOT consume any following `-`. WhitespaceChar* between `{%`
  *     and `endraw` is optional, so `{%endraw`, `{% endraw` and the body-immediately-followed `{% endraw` all match. The `-` is NOT part of the matched id.
  *   - LiquidParser.g4:121-122 `raw_tag : TagStart RawStart raw_body RawEnd TagEnd ;` — after `RawEnd` a separate `TagEnd` token closes the tag.
  *   - LiquidLexer.g4:129-134 `TagEnd : ... | '-%}' WhitespaceChar* | '%}' ;` — the close token for `endraw` is an ordinary `TagEnd`, so `-%}` is a legal close immediately after `endraw` (no
  *     intervening space required) and `'-%}' WhitespaceChar*` strips the whitespace that follows the tag. Therefore, in liqp, `{% raw %}{{a}}{% endraw-%}   TRAILING` yields raw body "{{a}}" and
  *     renders "TRAILING" with the inter-tag whitespace stripped.
  *
  * Bug in the SSG port (LiquidLexer.scala:479-527, scanRawBody): the tag id inside the raw block is scanned via `isIdContinue` (line 494), which treats `-` as an identifier char. For `{% endraw-%}`
  * the id scan consumes `"endraw-"`, so the `id == "endraw"` check (line 498) FAILS, no endraw TAG_START/RAW/TAG_END tokens are emitted, the block is never terminated and the remainder of the
  * template is swallowed/corrupted. This is the same family as ISS-1011 (commit 756fa0a6), which fixed the SPACE variant `{% endraw -%}` but left the NO-space variant `{% endraw-%}` unfixed.
  */
final class RawEndrawNoSpaceIss1178Suite extends munit.FunSuite {

  /* Control: the plain `{% endraw %}` form already works — raw body is literal,
   * trailing text renders verbatim (no whitespace stripping). Must keep passing. */
  test("ISS-1178 control: plain endraw renders trailing text") {
    assertEquals(
      Template.parse("{% raw %}{{a}}{% endraw %}   TRAILING").render(),
      "{{a}}   TRAILING"
    )
  }

  /* Control: the SPACE-before-`-%}` variant `{% endraw -%}` already works (ISS-1011) —
   * raw body is literal, `-%}` strips trailing whitespace. Must keep passing. */
  test("ISS-1178 control: endraw with space before -%} strips trailing whitespace") {
    assertEquals(
      Template.parse("{% raw %}{{a}}{% endraw -%}   TRAILING").render(),
      "{{a}}TRAILING"
    )
  }

  /* Red: `{% endraw-%}` (no space before `-%}`) must be recognized — the raw block
   * closes, "{{a}}" is preserved verbatim (raw blocks don't interpolate), and the
   * `-%}` strips the whitespace that follows the tag so TRAILING still renders. */
  test("ISS-1178 red: endraw-%} (no space) closes block and strips trailing whitespace") {
    assertEquals(
      Template.parse("{% raw %}{{a}}{% endraw-%}   TRAILING").render(),
      "{{a}}TRAILING"
    )
  }

  /* Red: the raw body must be preserved verbatim when closed with `-%}` and NO
   * space before it; the trailing text must follow (stripped of leading whitespace). */
  test("ISS-1178 red: endraw-%} (no space) preserves raw body verbatim") {
    assertEquals(
      Template.parse("{% raw %}BODY{% endraw-%}\n  after").render(),
      "BODYafter"
    )
  }
}
