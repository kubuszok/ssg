/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Test for ISS-1089: verb delimiter check uses Character.isLetter (Unicode)
 * instead of ASCII-only [a-zA-Z] check matching upstream Lexer.ts:60.
 *
 * Upstream KaTeX Lexer.ts:59-60 defines unstarred \verb delimiter as
 * [^*a-zA-Z] — any char that is NOT '*' and NOT an ASCII letter. The SSG
 * port used Character.isLetter which rejects any Unicode letter (e.g. e with
 * acute, u with diaeresis, Greek alpha), diverging from KaTeX behavior.
 *
 * Expected values derived from KaTeX Lexer.ts regex semantics:
 * - \verb with non-ASCII letter delimiter (e.g. U+00E9): ACCEPTED
 *   (not in [a-zA-Z], not '*')
 * - \verb with ASCII non-letter delimiter (e.g. '|'): ACCEPTED
 * - \verb* with any delimiter: ACCEPTED ([^] matches any char)
 * - \verb with ASCII letter delimiter (e.g. 'a'): REJECTED
 *   (in [a-zA-Z])
 */
package ssg
package katex

import ssg.katex.parse.{ Lexer, SettingsLike }

class VerbNonAsciiDelimiterIss1089Suite extends munit.FunSuite with KaTeXTestSuite {

  // Lightweight SettingsLike for direct Lexer tests
  private object TestSettings extends SettingsLike {
    def reportNonstrict(errorCode: String, errorMsg: String): Unit = ()
  }

  // ------------------------------------------------------------------
  // RED case: non-ASCII letter delimiter must be ACCEPTED
  // Upstream Lexer.ts:60 [^*a-zA-Z] does NOT exclude Unicode letters.
  // ------------------------------------------------------------------

  test("Lexer accepts non-ASCII letter as unstarred verb delimiter (ISS-1089)") {
    // \verb followed by e-acute (U+00E9) as delimiter, content "code", closing e-acute
    val input = "\\verbécodeé"
    val lexer = new Lexer(input, TestSettings)
    val token = lexer.lex()
    // The entire \verb<delim>content<delim> becomes the token text
    assertEquals(token.text, input)
  }

  test("renderToString accepts non-ASCII letter verb delimiter (ISS-1089)") {
    // \verb with e-acute delimiter around "hello" — must render without error
    val output = KaTeX.renderToString("\\verbéhelloé")
    assert(output.contains("hello"), s"Expected rendered output to contain 'hello' but got: $output")
  }

  test("Lexer accepts Greek letter as unstarred verb delimiter (ISS-1089)") {
    // Greek alpha (U+03B1) is a Unicode letter but NOT in [a-zA-Z]
    val input = "\\verbαtextα"
    val lexer = new Lexer(input, TestSettings)
    val token = lexer.lex()
    assertEquals(token.text, input)
  }

  test("Lexer accepts u-with-diaeresis as unstarred verb delimiter (ISS-1089)") {
    // U+00FC (u with diaeresis) — Unicode letter, NOT ASCII letter
    val input = "\\verbüxyü"
    val lexer = new Lexer(input, TestSettings)
    val token = lexer.lex()
    assertEquals(token.text, input)
  }

  // ------------------------------------------------------------------
  // CONTROL cases: behavior that must remain unchanged
  // ------------------------------------------------------------------

  test("Lexer accepts ASCII non-letter delimiter for unstarred verb (control)") {
    val input = "\\verb|code|"
    val lexer = new Lexer(input, TestSettings)
    val token = lexer.lex()
    assertEquals(token.text, "\\verb|code|")
  }

  test("Lexer accepts starred verb with any delimiter (control)") {
    val input = "\\verb*|code|"
    val lexer = new Lexer(input, TestSettings)
    val token = lexer.lex()
    assertEquals(token.text, "\\verb*|code|")
  }

  test("Lexer rejects ASCII letter delimiter for unstarred verb (control)") {
    // 'a' is in [a-zA-Z] so it is NOT a valid unstarred delimiter.
    // tryLexVerb returns null; the main regex matches "\verbacodea" as a
    // control word (\\[a-zA-Z@]+), so token.text is the whole string but
    // the parser will NOT recognize it as a verb (Parser.scala:971 checks
    // ^\\verb[^a-zA-Z]). Verify via renderToString which throws ParseError
    // because \verbacodea is an undefined control sequence.
    val caught = intercept[ParseError] {
      KaTeX.renderToString("\\verbacodea")
    }
    assert(
      caught.getMessage.contains("Undefined control sequence"),
      s"Expected 'Undefined control sequence' but got: ${caught.getMessage}"
    )
  }

  test("Lexer accepts starred verb with ASCII letter delimiter (control)") {
    // Starred \verb* accepts ANY delimiter including letters — Lexer.ts:59 [^]
    val input = "\\verb*XcodeX"
    val lexer = new Lexer(input, TestSettings)
    val token = lexer.lex()
    assertEquals(token.text, "\\verb*XcodeX")
  }
}
