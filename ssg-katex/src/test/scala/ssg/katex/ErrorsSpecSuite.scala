/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Tests for error messages, error positions, and throwOnError behavior.
 *
 * Original source: katex test/errors-spec.ts
 */
package ssg
package katex

import scala.language.implicitConversions

import TestHelpers.*

class ErrorsSpecSuite extends KaTeXTestSuite {

  // ===========================================================================
  // Parser: #handleInfixNodes
  // ===========================================================================

  test("Parser: #handleInfixNodes: rejects repeated infix operators") {
    assertFailsWithParseError("1\\over 2\\over 3",
                              "only one infix operator per group at position 9: " +
                                "1\\over 2\\̲o̲v̲e̲r̲ ̲3"
    )
  }

  test("Parser: #handleInfixNodes: rejects conflicting infix operators") {
    assertFailsWithParseError("1\\over 2\\choose 3",
                              "only one infix operator per group at position 9: " +
                                "1\\over 2\\̲c̲h̲o̲o̲s̲e̲ ̲3"
    )
  }

  // ===========================================================================
  // Parser: #handleSupSubscript
  // ===========================================================================

  test("Parser: #handleSupSubscript: rejects ^ at end of group") {
    assertFailsWithParseError("{1^}", "Expected group after '^' at position 3: {1^̲}")
  }

  test("Parser: #handleSupSubscript: rejects _ at end of input") {
    assertFailsWithParseError("1_", "Expected group after '_' at position 2: 1_̲")
  }

  test("Parser: #handleSupSubscript: rejects \\sqrt as argument to ^") {
    assertFailsWithParseError("1^\\sqrt{2}",
                              "Got function '\\sqrt' with no arguments as superscript" +
                                " at position 3: 1^\\̲s̲q̲r̲t̲{2}"
    )
  }

  // ===========================================================================
  // Parser: #parseAtom
  // ===========================================================================

  test("Parser: #parseAtom: rejects \\limits without operator") {
    assertFailsWithParseError("\\alpha\\limits\\omega",
                              "Limit controls must follow a math operator" +
                                " at position 7: \\alpha\\̲l̲i̲m̲i̲t̲s̲\\omega"
    )
  }

  test("Parser: #parseAtom: rejects \\limits at the beginning of the input") {
    assertFailsWithParseError("\\limits\\omega",
                              "Limit controls must follow a math operator" +
                                " at position 1: \\̲l̲i̲m̲i̲t̲s̲\\omega"
    )
  }

  test("Parser: #parseAtom: rejects double superscripts") {
    assertFailsWithParseError("1^2^3", "Double superscript at position 4: 1^2^̲3")
    assertFailsWithParseError("1^{2+3}_4^5", "Double superscript at position 10: 1^{2+3}_4^̲5")
  }

  test("Parser: #parseAtom: rejects double superscripts involving primes") {
    assertFailsWithParseError("1'_2^3", "Double superscript at position 5: 1'_2^̲3")
    assertFailsWithParseError("1^2'", "Double superscript at position 4: 1^2'̲")
    assertFailsWithParseError("1^2_3'", "Double superscript at position 6: 1^2_3'̲")
    assertFailsWithParseError("1'_2'", "Double superscript at position 5: 1'_2'̲")
  }

  test("Parser: #parseAtom: rejects double subscripts") {
    assertFailsWithParseError("1_2_3", "Double subscript at position 4: 1_2_̲3")
    assertFailsWithParseError("1_{2+3}^4_5", "Double subscript at position 10: 1_{2+3}^4_̲5")
  }

  // ===========================================================================
  // Parser: #parseImplicitGroup
  // ===========================================================================

  test("Parser: #parseImplicitGroup: reports unknown environments") {
    assertFailsWithParseError("\\begin{foo}bar\\end{foo}",
                              "No such environment: foo at position 7:" +
                                " \\begin{̲f̲o̲o̲}̲bar\\end{foo}"
    )
  }

  test("Parser: #parseImplicitGroup: reports mismatched environments") {
    assertFailsWithParseError(
      "\\begin{pmatrix}1&2\\\\3&4\\end{bmatrix}+5",
      "Mismatch: \\begin{pmatrix} matched by \\end{bmatrix}" +
        " at position 24: …matrix}1&2\\\\3&4\\̲e̲n̲d̲{bmatrix}+5"
    )
  }

  // ===========================================================================
  // Parser: #parseFunction
  // ===========================================================================

  test("Parser: #parseFunction: rejects math-mode functions in text mode") {
    assertFailsWithParseError(
      "\\text{\\sqrt2 is irrational}",
      "Can't use function '\\sqrt' in text mode" +
        " at position 7: \\text{\\̲s̲q̲r̲t̲2 is irrational…"
    )
  }

  test("Parser: #parseFunction: rejects text-mode-only functions in math mode") {
    assertFailsWithParseError("$", "Can't use function '$' in math mode at position 1: $̲")
  }

  test("Parser: #parseFunction: rejects strict-mode text-mode-only functions in math mode") {
    assertFailsWithParseError(
      "\\'echec",
      "LaTeX-incompatible input " +
        "and strict mode is set to 'error': LaTeX's accent \\' works " +
        "only in text mode [mathVsTextAccents]",
      strictSettings
    )
  }

  // ===========================================================================
  // Parser: #parseArguments
  // ===========================================================================

  test("Parser: #parseArguments: complains about missing argument at end of input") {
    assertFailsWithParseError("2\\sqrt",
                              "Expected group as argument to '\\sqrt'" +
                                " at end of input: 2\\sqrt"
    )
  }

  test("Parser: #parseArguments: complains about missing argument at end of group") {
    assertFailsWithParseError("1^{2\\sqrt}",
                              "Expected group as argument to '\\sqrt'" +
                                " at position 10: 1^{2\\sqrt}̲"
    )
  }

  test("Parser: #parseArguments: complains about functions as arguments to others") {
    assertFailsWithParseError(
      "\\sqrt\\over2",
      "Got function '\\over' with no arguments as argument to" +
        " '\\sqrt' at position 6: \\sqrt\\̲o̲v̲e̲r̲2"
    )
  }

  // ===========================================================================
  // Parser: #parseGroup
  // ===========================================================================

  test("Parser: #parseGroup: complains about undefined control sequence") {
    assertFailsWithParseError("\\xyz",
                              "Undefined control sequence: \\xyz" +
                                " at position 1: \\̲x̲y̲z̲"
    )
  }

  // ===========================================================================
  // Parser: #verb
  // ===========================================================================

  test("Parser: #verb: complains about mismatched \\verb with end of string") {
    assertFailsWithParseError("\\verb|hello", "\\verb ended by end of line instead of matching delimiter")
  }

  test("Parser: #verb: complains about mismatched \\verb with end of line") {
    assertFailsWithParseError("\\verb|hello\nworld|", "\\verb ended by end of line instead of matching delimiter")
  }

  // ===========================================================================
  // Parser.expect calls: #parseInput expecting EOF
  // ===========================================================================

  test("Parser.expect calls: #parseInput expecting EOF: complains about extra }") {
    assertFailsWithParseError("{1+2}}", "Expected 'EOF', got '}' at position 6: {1+2}}̲")
  }

  test("Parser.expect calls: #parseInput expecting EOF: complains about extra \\end") {
    assertFailsWithParseError("x\\end{matrix}",
                              "Expected 'EOF', got '\\end' at position 2:" +
                                " x\\̲e̲n̲d̲{matrix}"
    )
  }

  test("Parser.expect calls: #parseInput expecting EOF: complains about top-level &") {
    assertFailsWithParseError("1&2", "Expected 'EOF', got '&' at position 2: 1&̲2")
  }

  // ===========================================================================
  // Parser.expect calls: #parseImplicitGroup expecting \right
  // ===========================================================================

  test("Parser.expect calls: #parseImplicitGroup: rejects missing \\right") {
    assertFailsWithParseError("\\left(1+2)",
                              "Expected '\\right', got 'EOF' at end of input:" +
                                " \\left(1+2)"
    )
  }

  test("Parser.expect calls: #parseImplicitGroup: rejects incorrectly scoped \\right") {
    assertFailsWithParseError("{\\left(1+2}\\right)",
                              "Expected '\\right', got '}' at position 11:" +
                                " {\\left(1+2}̲\\right)"
    )
  }

  // ===========================================================================
  // Parser.expect calls: #parseSpecialGroup expecting braces
  // ===========================================================================

  test("Parser.expect calls: #parseSpecialGroup: complains about missing { for color") {
    assertFailsWithParseError("\\textcolor#ffffff{text}",
                              "Invalid color: '#' at position 11:" +
                                " \\textcolor#̲ffffff{text}"
    )
  }

  test("Parser.expect calls: #parseSpecialGroup: complains about missing { for size") {
    assertFailsWithParseError("\\rule{1em}[2em]", "Invalid size: '[' at position 11: \\rule{1em}[̲2em]")
  }

  test("Parser.expect calls: #parseSpecialGroup: complains about missing } for color") {
    assertFailsWithParseError(
      "\\textcolor{#ffffff{text}",
      "Unexpected end of input in a macro argument," +
        " expected '}' at end of input: …r{#ffffff{text}"
    )
  }

  test("Parser.expect calls: #parseSpecialGroup: complains about missing ] for size") {
    assertFailsWithParseError(
      "\\rule[1em{2em}{3em}",
      "Unexpected end of input in a macro argument," +
        " expected ']' at end of input: …e[1em{2em}{3em}"
    )
  }

  test("Parser.expect calls: #parseSpecialGroup: complains about missing ] for size at end of input") {
    assertFailsWithParseError("\\rule[1em",
                              "Unexpected end of input in a macro argument," +
                                " expected ']' at end of input: \\rule[1em"
    )
  }

  test("Parser.expect calls: #parseSpecialGroup: complains about missing } for color at end of input") {
    assertFailsWithParseError(
      "\\textcolor{#123456",
      "Unexpected end of input in a macro argument," +
        " expected '}' at end of input: …xtcolor{#123456"
    )
  }

  // ===========================================================================
  // Parser.expect calls: #parseGroup expecting }
  // ===========================================================================

  test("Parser.expect calls: #parseGroup: at end of file") {
    assertFailsWithParseError("\\sqrt{2", "Expected '}', got 'EOF' at end of input: \\sqrt{2")
  }

  // ===========================================================================
  // Parser.expect calls: #parseOptionalGroup expecting ]
  // ===========================================================================

  test("Parser.expect calls: #parseOptionalGroup: at end of file") {
    assertFailsWithParseError("\\sqrt[3",
                              "Unexpected end of input in a macro argument," +
                                " expected ']' at end of input: \\sqrt[3"
    )
  }

  test("Parser.expect calls: #parseOptionalGroup: before group") {
    assertFailsWithParseError("\\sqrt[3{2}",
                              "Unexpected end of input in a macro argument," +
                                " expected ']' at end of input: \\sqrt[3{2}"
    )
  }

  // ===========================================================================
  // environments.js: parseArray
  // ===========================================================================

  test("environments.js: parseArray: rejects missing \\end") {
    assertFailsWithParseError("\\begin{matrix}1",
                              "Expected & or \\\\ or \\cr or \\end at end of input:" +
                                " \\begin{matrix}1"
    )
  }

  test("environments.js: parseArray: rejects incorrectly scoped \\end") {
    assertFailsWithParseError(
      "{\\begin{matrix}1}\\end{matrix}",
      "Expected & or \\\\ or \\cr or \\end at position 17:" +
        " …\\begin{matrix}1}̲\\end{matrix}"
    )
  }

  // ===========================================================================
  // environments.js: array environment
  // ===========================================================================

  test("environments.js: array environment: rejects unknown column types") {
    assertFailsWithParseError("\\begin{array}{cba}\\end{array}",
                              "Unknown column alignment: b at position 16:" +
                                " \\begin{array}{cb̲a}\\end{array}"
    )
  }

  // ===========================================================================
  // functions.js: delimiter functions
  // ===========================================================================

  test("functions.js: delimiter functions: reject invalid opening delimiters") {
    assertFailsWithParseError("\\bigl 1 + 2 \\bigr",
                              "Invalid delimiter '1' after '\\bigl' at position 7:" +
                                " \\bigl 1̲ + 2 \\bigr"
    )
  }

  test("functions.js: delimiter functions: reject invalid closing delimiters") {
    assertFailsWithParseError("\\bigl(1+2\\bigr=3",
                              "Invalid delimiter '=' after '\\bigr' at position 15:" +
                                " \\bigl(1+2\\bigr=̲3"
    )
  }

  test("functions.js: delimiter functions: reject group opening delimiters") {
    assertFailsWithParseError("\\bigl{(}1+2\\bigr)3",
                              "Invalid delimiter type 'ordgroup' at position 6:" +
                                " \\bigl{̲(̲}̲1+2\\bigr)3"
    )
  }

  test("functions.js: delimiter functions: reject group closing delimiters") {
    assertFailsWithParseError("\\bigl(1+2\\bigr{)}3",
                              "Invalid delimiter type 'ordgroup' at position 15:" +
                                " \\bigl(1+2\\bigr{̲)̲}̲3"
    )
  }

  // ===========================================================================
  // functions.js: \begin and \end
  // ===========================================================================

  test("functions.js: \\begin and \\end: reject invalid environment names") {
    assertFailsWithParseError("\\begin x\\end y", "No such environment: x at position 8: \\begin x̲\\end y")
  }

  // ===========================================================================
  // Lexer: #_innerLex
  // ===========================================================================

  test("Lexer: #_innerLex: rejects lone surrogate char") {
    assertFailsWithParseError("\udcba ",
                              "Unexpected character: '\udcba' at position 1:" +
                                " \udcba̲ "
    )
  }

  test("Lexer: #_innerLex: rejects lone backslash at end of input") {
    assertFailsWithParseError("\\", "Unexpected character: '\\' at position 1: \\̲")
  }

  // ===========================================================================
  // Lexer: #_innerLexColor
  // ===========================================================================

  test("Lexer: #_innerLexColor: reject 3-digit hex notation without #") {
    assertFailsWithParseError("\\textcolor{1a2}{foo}",
                              "Invalid color: '1a2'" +
                                " at position 11: \\textcolor{̲1̲a̲2̲}̲{foo}"
    )
  }

  // ===========================================================================
  // Lexer: #_innerLexSize
  // ===========================================================================

  test("Lexer: #_innerLexSize: reject size without unit") {
    assertFailsWithParseError("\\rule{0}{2em}", "Invalid size: '0' at position 6: \\rule{̲0̲}̲{2em}")
  }

  test("Lexer: #_innerLexSize: reject size with bogus unit") {
    assertFailsWithParseError("\\rule{1au}{2em}", "Invalid unit: 'au' at position 6: \\rule{̲1̲a̲u̲}̲{2em}")
  }

  test("Lexer: #_innerLexSize: reject size without number") {
    assertFailsWithParseError("\\rule{em}{2em}", "Invalid size: 'em' at position 6: \\rule{̲e̲m̲}̲{2em}")
  }

  // ===========================================================================
  // Unicode accents
  // ===========================================================================

  test("Unicode accents: should return error for invalid combining characters") {
    assertFailsWithParseError("Ą", "Unknown accent ' ̨' at position 1: Ą̲̲")
  }
}
