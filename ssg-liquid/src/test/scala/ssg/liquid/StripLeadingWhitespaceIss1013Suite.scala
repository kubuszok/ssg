/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

/** Red tests for ISS-1013: `withStripSpaceAroundTags` must strip whitespace on BOTH sides of a tag, but the SSG port only strips the TRAILING side.
  *
  * liqp oracle (original-src/liqp/src/main/antlr4/liquid/parser/v4/LiquidLexer.g4):
  *   - LEADING (before opening `{{`/`{%`), lines 86-95: `SpaceOrTab* '{{'` when `stripSpacesAroundTags && stripSingleLine`; `WhitespaceChar* '{{'` when `stripSpacesAroundTags && !stripSingleLine`
  *     (same rules for `{%`). So with the flag set, the whitespace immediately preceding an opening tag delimiter is consumed.
  *   - TRAILING (after closing `}}`/`%}`), lines 122-131: `'}}' SpaceOrTab* LineBreak?` (singleLine) / `'}}' WhitespaceChar*` (full) — strips whitespace following the closing delimiter.
  *   - Asymmetry under `stripSingleLine`: LEADING removes only spaces/tabs (`SpaceOrTab*`, NO linebreak), while TRAILING removes spaces/tabs plus at most one linebreak.
  *
  * Bug in the SSG port (LiquidLexer.scala): `handlePostTagStripping` (lines 467-492) implements only the TRAILING half — it consumes whitespace AFTER `%}`/`}}` when `stripSpacesAroundTags` is set.
  * The LEADING half is absent: `scanDefault` (lines 59-72) emits the accumulated text via `emitText` immediately before `scanOutputTag`/`scanTagStart` and never trims that text's trailing
  * whitespace when `stripSpacesAroundTags` is set, so the whitespace BEFORE a tag survives, diverging from liqp's `stripSpacesAroundTags`.
  *
  * Empirically observed CURRENT (buggy) outputs vs liqp-expected are recorded inline below.
  */
final class StripLeadingWhitespaceIss1013Suite extends munit.FunSuite {

  /* Baseline (passes today): with the flag OFF, both the leading and the trailing
   * whitespace around the tag are preserved — proving the source contains it. */
  test("ISS-1013 baseline: flag off preserves leading and trailing whitespace") {
    val parser   = new TemplateParser.Builder().build()
    val rendered = parser.parse("a   {% assign foo = 1 %}   b").render().replace(' ', '.')
    assertEquals(rendered, "a......b")
  }

  /* Control (passes today): the TRAILING half already works — with the flag ON,
   * whitespace AFTER `%}` is stripped. Proves only one side is broken. */
  test("ISS-1013 control: flag on strips trailing whitespace") {
    val parser   = new TemplateParser.Builder().withStripSpaceAroundTags(true).build()
    val rendered = parser.parse("a{% assign foo = 1 %}   b").render().replace(' ', '.')
    assertEquals(rendered, "ab")
  }

  /* Red (must fail today): with `withStripSpaceAroundTags(true)`, liqp strips the
   * LEADING `"   "` before `{%` (full mode: `WhitespaceChar* '{%'`) as well as the
   * trailing whitespace after `%}`, yielding "ab". Today the trailing side is
   * stripped but the leading side survives, so the actual output is "a   b". */
  test("ISS-1013 red: flag on must strip leading whitespace before a tag") {
    val parser   = new TemplateParser.Builder().withStripSpaceAroundTags(true).build()
    val rendered = parser.parse("a   {% assign foo = 1 %}   b").render().replace(' ', '.')
    // liqp-expected (leading stripped): "ab"; current actual (leading survives): "a...b"
    assertEquals(rendered, "ab")
  }

  /* Red (must fail today): the asymmetry under `stripSingleLine`. liqp's LEADING rule
   * is `SpaceOrTab* '{%'` — it removes the spaces/tabs immediately before `{%` but NOT
   * the preceding linebreak. For "a\n   {% assign foo = 1 %}b" liqp strips the three
   * leading spaces (keeping the `\n`) -> "a\nb". Today the leading spaces survive, so
   * the actual output is "a\n   b". */
  test("ISS-1013 red: single-line flag strips leading spaces but keeps the linebreak") {
    val parser = new TemplateParser.Builder().withStripSpaceAroundTags(true, true).build()
    val rendered =
      parser.parse("a\n   {% assign foo = 1 %}b").render().replace(' ', '.').replace("\n", "<NL>")
    // liqp-expected (leading spaces stripped, linebreak kept): "a<NL>b";
    // current actual (leading spaces survive): "a<NL>...b"
    assertEquals(rendered, "a<NL>b")
  }
}
