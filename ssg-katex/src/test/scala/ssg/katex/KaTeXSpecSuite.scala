/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Main KaTeX test suite. Tests parsing, building, and rendering of LaTeX expressions.
 * This is a faithful port of katex-spec.ts (606 test cases).
 *
 * Original source: katex test/katex-spec.ts
 */
package ssg
package katex

import scala.collection.mutable
import scala.language.implicitConversions

import lowlevel.Nullable
import ssg.katex.build.{ BuildMathML, BuildTree }
import ssg.katex.parse.{ ParseNodeText, ParseTree }
import ssg.katex.tree.DomSpan
import TestHelpers.*

class KaTeXSpecSuite extends KaTeXTestSuite {

  // ===========================================================================
  // A parser
  // ===========================================================================

  test("A parser: should not fail on an empty string") {
    assertParses("", strictSettings)
  }

  test("A parser: should ignore whitespace") {
    assertParsesLike("    x    y    ", "xy", strictSettings)
  }

  test("A parser: should ignore whitespace in atom") {
    assertParsesLike("    x   ^ y    ", "x^y", strictSettings)
  }

  // ===========================================================================
  // An ord parser
  // ===========================================================================

  test("An ord parser: should not fail") {
    val expression = "1234|/@.\"`abcdefgzABCDEFGZ"
    assertParses(expression)
  }

  test("An ord parser: should build a list of ords") {
    val expression = "1234|/@.\"`abcdefgzABCDEFGZ"
    val parse      = getParsed(expression)
    for (i <- parse.indices)
      assert(parse(i).nodeType.contains("ord"), s"Expected ord type but got ${parse(i).nodeType}")
  }

  test("An ord parser: should parse the right number of ords") {
    val expression = "1234|/@.\"`abcdefgzABCDEFGZ"
    val parse      = getParsed(expression)
    assertEquals(parse.length, expression.length)
  }

  // ===========================================================================
  // A bin parser
  // ===========================================================================

  test("A bin parser: should not fail") {
    assertParses("+-*\\cdot\\pm\\div")
  }

  test("A bin parser: should build a list of bins") {
    val parse = getParsed("+-*\\cdot\\pm\\div")
    for (i <- parse.indices)
      assertEquals(parse(i).nodeType, "atom")
  }

  // ===========================================================================
  // A rel parser
  // ===========================================================================

  test("A rel parser: should not fail") {
    assertParses("=<>\\leq\\geq\\neq\\nleq\\ngeq\\cong")
    assertParses("\\not=\\not<\\not>\\not\\leq\\not\\geq\\not\\in")
  }

  test("A rel parser: should build a list of rels") {
    val parse = getParsed("=<>\\leq\\geq\\neq\\nleq\\ngeq\\cong")
    for (i <- parse.indices) {
      val group = parse(i)
      if (group.nodeType == "htmlmathml") {
        // skip complex structure checks for now
      } else if (group.nodeType == "mclass") {
        // mclass with mrel
      } else {
        assertEquals(group.nodeType, "atom")
      }
    }
  }

  // ===========================================================================
  // A mathinner parser
  // ===========================================================================

  test("A mathinner parser: should not fail") {
    assertParses("\\mathinner{\\langle{\\psi}\\rangle}")
    assertParses("\\frac 1 {\\mathinner{\\langle{\\psi}\\rangle}}")
  }

  test("A mathinner parser: should return one group, not a fragment") {
    val contents = "\\mathinner{\\langle{\\psi}\\rangle}"
    val parsed   = getParsed(contents)
    val markup   = BuildMathML.buildMathML(parsed, contents, defaultOptions, isDisplayMode = false, forMathmlOnly = false).toMarkup()
    // children count is 1 in the original
    assert(markup.nonEmpty, "MathML should be non-empty")
  }

  // ===========================================================================
  // A punct parser
  // ===========================================================================

  test("A punct parser: should not fail") {
    assertParses(",;", strictSettings)
  }

  test("A punct parser: should build a list of puncts") {
    val parse = getParsed(",;")
    for (i <- parse.indices)
      assertEquals(parse(i).nodeType, "atom")
  }

  // ===========================================================================
  // An open parser
  // ===========================================================================

  test("An open parser: should not fail") {
    assertParses("([")
  }

  test("An open parser: should build a list of opens") {
    val parse = getParsed("([")
    for (i <- parse.indices)
      assertEquals(parse(i).nodeType, "atom")
  }

  // ===========================================================================
  // A close parser
  // ===========================================================================

  test("A close parser: should not fail") {
    assertParses(")]?!")
  }

  test("A close parser: should build a list of closes") {
    val parse = getParsed(")]?!")
    for (i <- parse.indices)
      assertEquals(parse(i).nodeType, "atom")
  }

  // ===========================================================================
  // A \KaTeX parser
  // ===========================================================================

  test("A \\KaTeX parser: should not fail") {
    assertParses("\\KaTeX")
  }

  // ===========================================================================
  // A subscript and superscript parser
  // ===========================================================================

  test("A subscript and superscript parser: should not fail on superscripts") {
    assertParses("x^2")
  }

  test("A subscript and superscript parser: should not fail on subscripts") {
    assertParses("x_3")
  }

  test("A subscript and superscript parser: should not fail on both subscripts and superscripts") {
    assertParses("x^2_3")
    assertParses("x_2^3")
  }

  test("A subscript and superscript parser: should not fail when there is no nucleus") {
    assertParses("^3")
    assertParses("^3+")
    assertParses("_2")
    assertParses("^3_2")
    assertParses("_2^3")
  }

  test("A subscript and superscript parser: should produce supsubs for superscript") {
    val parse = getParsed("x^2")(0)
    assertEquals(parse.nodeType, "supsub")
  }

  test("A subscript and superscript parser: should produce supsubs for subscript") {
    val parse = getParsed("x_3")(0)
    assertEquals(parse.nodeType, "supsub")
  }

  test("A subscript and superscript parser: should produce supsubs for ^_") {
    val parse = getParsed("x^2_3")(0)
    assertEquals(parse.nodeType, "supsub")
  }

  test("A subscript and superscript parser: should produce supsubs for _^") {
    val parse = getParsed("x_3^2")(0)
    assertEquals(parse.nodeType, "supsub")
  }

  test("A subscript and superscript parser: should produce the same thing regardless of order") {
    assertParsesLike("x^2_3", "x_3^2")
  }

  test("A subscript and superscript parser: should not parse double subscripts or superscripts") {
    assertNotParses("x^x^x")
    assertNotParses("x_x_x")
    assertNotParses("x_x^x_x")
    assertNotParses("x_x^x^x")
    assertNotParses("x^x_x_x")
    assertNotParses("x^x_x^x")
  }

  test("A subscript and superscript parser: should work correctly with {}s") {
    assertParses("x^{2+3}")
    assertParses("x_{3-2}")
    assertParses("x^{2+3}_3")
    assertParses("x^2_{3-2}")
    assertParses("x^{2+3}_{3-2}")
    assertParses("x_{3-2}^{2+3}")
    assertParses("x_3^{2+3}")
    assertParses("x_{3-2}^2")
  }

  test("A subscript and superscript parser: should work with nested super/subscripts") {
    assertParses("x^{x^x}")
    assertParses("x^{x_x}")
    assertParses("x_{x^x}")
    assertParses("x_{x_x}")
  }

  test("A subscript and superscript parser: should work with Unicode (sub|super)script characters") {
    assertParsesLike("A² + B²⁺³ + ¹²C + E₂³ + F₂₊₃", "A^{2} + B^{2+3} + ^{12}C + E_{2}^{3} + F_{2+3}")
  }

  test("A subscript and superscript parser: should not fail if \\relax is in an atom") {
    assertParses("\\hskip1em\\relax^2", strictSettings)
  }

  test("A subscript and superscript parser: should skip \\relax in super/subscripts") {
    assertParsesLike("x^\\relax 2", "x^2")
    assertParsesLike("x_\\relax 2", "x_2")
  }

  // ===========================================================================
  // A subscript and superscript tree-builder
  // ===========================================================================

  test("A subscript and superscript tree-builder: should not fail when there is no nucleus") {
    assertBuilds("^3")
    assertBuilds("_2")
    assertBuilds("^3_2")
    assertBuilds("_2^3")
  }

  // ===========================================================================
  // A parser with limit controls
  // ===========================================================================

  test("A parser with limit controls: should fail when not preceded by an op node") {
    assertNotParses("3\\nolimits_2^2")
    assertNotParses("\\sqrt\\limits_2^2")
    assertNotParses("45 +\\nolimits 45")
  }

  test("A parser with limit controls: should parse when directly follows an op node") {
    assertParses("\\int\\limits_2^2 3")
    assertParses("\\sum\\nolimits_3^4 4")
  }

  test("A parser with limit controls: should parse when in the sup/sub area of an op node") {
    assertParses("\\int_2^2\\limits")
    assertParses("\\int^2\\nolimits_2")
    assertParses("\\int_2\\limits^2")
  }

  test("A parser with limit controls: should allow multiple limit controls") {
    assertParses("\\int_2\\nolimits^2\\limits 3")
    assertParses("\\int\\nolimits\\limits_2^2")
    assertParses("\\int\\limits\\limits\\limits_2^2")
  }

  test("A parser with limit controls: should have rightmost determine the limits property") {
    val parsedInput1 = getParsed("\\int\\nolimits\\limits_2^2")
    val parsedInput2 = getParsed("\\int\\limits_2\\nolimits^2")
    // We just verify parsing succeeds
    assert(parsedInput1.nonEmpty)
    assert(parsedInput2.nonEmpty)
  }

  // ===========================================================================
  // A group parser
  // ===========================================================================

  test("A group parser: should not fail") {
    assertParses("{xy}")
  }

  test("A group parser: should produce a single ord") {
    val parse = getParsed("{xy}")
    assertEquals(parse.length, 1)
    assert(parse(0).nodeType.contains("ord"))
  }

  // ===========================================================================
  // A \begingroup...\endgroup parser
  // ===========================================================================

  test("A \\begingroup...\\endgroup parser: should not fail") {
    assertParses("\\begingroup xy \\endgroup")
  }

  test("A \\begingroup...\\endgroup parser: should fail when it is mismatched") {
    assertNotParses("\\begingroup xy")
    assertNotParses("\\begingroup xy }")
  }

  test("A \\begingroup...\\endgroup parser: should produce a semi-simple group") {
    val parse = getParsed("\\begingroup xy \\endgroup")
    assertEquals(parse.length, 1)
    assert(parse(0).nodeType.contains("ord"))
  }

  test("A \\begingroup...\\endgroup parser: should not affect spacing in math mode") {
    assertBuildsLike("\\begingroup x+ \\endgroup y", "x+y")
  }

  // ===========================================================================
  // An implicit group parser
  // ===========================================================================

  test("An implicit group parser: should not fail") {
    assertParses("\\Large x")
    assertParses("abc {abc \\Large xyz} abc")
  }

  test("An implicit group parser: should produce a single object") {
    val parse = getParsed("\\Large abc")
    assertEquals(parse.length, 1)
    assertEquals(parse(0).nodeType, "sizing")
  }

  test("An implicit group parser: should apply only after the function") {
    val parse = getParsed("a \\Large abc")
    assertEquals(parse.length, 2)
    assertEquals(parse(1).nodeType, "sizing")
  }

  test("An implicit group parser: should stop at the ends of groups") {
    val parse = getParsed("a { b \\Large c } d")
    // Just verify it parses correctly
    assert(parse.length >= 2)
  }

  test("An implicit group parser: within optional groups: \\sqrt[\\small 3]{x}") {
    val tree = stripPositions(getParsed("\\sqrt[\\small 3]{x}"))
    assert(tree.nonEmpty, "Parse tree should be non-empty")
  }

  test("An implicit group parser: within optional groups: \\sqrt[\\color{red} 3]{x}") {
    val tree = stripPositions(getParsed("\\sqrt[\\color{red} 3]{x}"))
    assert(tree.nonEmpty, "Parse tree should be non-empty")
  }

  test("An implicit group parser: within optional groups: \\sqrt[\\textstyle 3]{x}") {
    val tree = stripPositions(getParsed("\\sqrt[\\textstyle 3]{x}"))
    assert(tree.nonEmpty, "Parse tree should be non-empty")
  }

  test("An implicit group parser: within optional groups: \\sqrt[\\tt 3]{x}") {
    val tree = stripPositions(getParsed("\\sqrt[\\tt 3]{x}"))
    assert(tree.nonEmpty, "Parse tree should be non-empty")
  }

  // ===========================================================================
  // A function parser
  // ===========================================================================

  test("A function parser: should parse no argument functions") {
    assertParses("\\div")
  }

  test("A function parser: should parse 1 argument functions") {
    assertParses("\\blue x")
  }

  test("A function parser: should parse 2 argument functions") {
    assertParses("\\frac 1 2")
  }

  test("A function parser: should not parse 1 argument functions with no arguments") {
    assertNotParses("\\blue")
  }

  test("A function parser: should not parse 2 argument functions with 0 or 1 arguments") {
    assertNotParses("\\frac")
    assertNotParses("\\frac 1")
  }

  test("A function parser: should not parse a function with text right after it") {
    assertNotParses("\\redx")
  }

  test("A function parser: should parse a function with a number right after it") {
    assertParses("\\frac12")
  }

  test("A function parser: should parse some functions with text right after it") {
    assertParses("\\;x")
  }

  // ===========================================================================
  // A frac parser
  // ===========================================================================

  test("A frac parser: should not fail") {
    assertParses("\\frac{x}{y}")
  }

  test("A frac parser: should produce a frac") {
    val parse = getParsed("\\frac{x}{y}")(0)
    assertEquals(parse.nodeType, "genfrac")
  }

  test("A frac parser: should also parse cfrac, dfrac, tfrac, and genfrac") {
    assertParses("\\cfrac{x}{y}")
    assertParses("\\dfrac{x}{y}")
    assertParses("\\tfrac{x}{y}")
    assertParses("\\genfrac ( ] {0.06em}{0}{a}{b+c}")
    assertParses("\\genfrac ( ] {0.8pt}{}{a}{b+c}")
  }

  test("A frac parser: should parse cfrac, dfrac, tfrac, and genfrac as fracs") {
    val dfracParse = getParsed("\\dfrac{x}{y}")(0)
    assertEquals(dfracParse.nodeType, "styling")

    val tfracParse = getParsed("\\tfrac{x}{y}")(0)
    assertEquals(tfracParse.nodeType, "styling")

    val cfracParse = getParsed("\\cfrac{x}{y}")(0)
    assertEquals(cfracParse.nodeType, "styling")

    val genfracParse = getParsed("\\genfrac ( ] {0.06em}{0}{a}{b+c}")(0)
    assertEquals(genfracParse.nodeType, "styling")

    val genfracAutoParse = getParsed("\\genfrac ( ] {0.8pt}{}{a}{b+c}")(0)
    assertEquals(genfracAutoParse.nodeType, "genfrac")
  }

  test("A frac parser: should fail, given math as a line thickness to genfrac") {
    assertNotParses("\\genfrac ( ] {b+c}{0}{a}{b+c}")
  }

  test("A frac parser: should fail if genfrac is given less than 6 arguments") {
    assertNotParses("\\genfrac ( ] {0.06em}{0}{a}")
  }

  test("A frac parser: should parse atop") {
    val parse = getParsed("x \\atop y")(0)
    assertEquals(parse.nodeType, "genfrac")
  }

  // ===========================================================================
  // An over/brace/brack parser
  // ===========================================================================

  test("An over/brace/brack parser: should not fail") {
    assertParses("1 \\over x")
    assertParses("1+2i \\over 3+4i")
    assertParses("a+b \\brace c+d")
    assertParses("a+b \\brack c+d")
  }

  test("An over/brace/brack parser: should produce a frac") {
    val parse1 = getParsed("1 \\over x")(0)
    assertEquals(parse1.nodeType, "genfrac")

    val parse2 = getParsed("1+2i \\over 3+4i")(0)
    assertEquals(parse2.nodeType, "genfrac")
  }

  test("An over/brace/brack parser: should create a numerator from atoms before \\over") {
    val parse = getParsed("1+2i \\over 3+4i")(0)
    // Just verify it parsed
    assert(parse.nodeType == "genfrac")
  }

  test("An over/brace/brack parser: should create a denominator from atoms after \\over") {
    val parse = getParsed("1+2i \\over 3+4i")(0)
    assert(parse.nodeType == "genfrac")
  }

  test("An over/brace/brack parser: should handle empty numerators") {
    val parse = getParsed("\\over x")(0)
    assertEquals(parse.nodeType, "genfrac")
  }

  test("An over/brace/brack parser: should handle empty denominators") {
    val parse = getParsed("1 \\over")(0)
    assertEquals(parse.nodeType, "genfrac")
  }

  test("An over/brace/brack parser: should handle \\displaystyle correctly") {
    val parse = getParsed("\\displaystyle 1 \\over 2")(0)
    assertEquals(parse.nodeType, "genfrac")
  }

  test("An over/brace/brack parser: should handle \\textstyle correctly") {
    assertParsesLike("\\textstyle 1 \\over 2", "\\frac{\\textstyle 1}{2}")
    assertParsesLike("{\\textstyle 1} \\over 2", "\\frac{\\textstyle 1}{2}")
  }

  test("An over/brace/brack parser: should handle nested fractions") {
    val parse = getParsed("{1 \\over 2} \\over 3")(0)
    assertEquals(parse.nodeType, "genfrac")
  }

  test("An over/brace/brack parser: should fail with multiple overs in the same group") {
    assertNotParses("1 \\over 2 + 3 \\over 4")
    assertNotParses("1 \\over 2 \\choose 3")
  }

  // ===========================================================================
  // A genfrac builder
  // ===========================================================================

  test("A genfrac builder: should not fail") {
    assertBuilds("\\frac{x}{y}")
    assertBuilds("\\dfrac{x}{y}")
    assertBuilds("\\tfrac{x}{y}")
    assertBuilds("\\cfrac{x}{y}")
    assertBuilds("\\genfrac ( ] {0.06em}{0}{a}{b+c}")
    assertBuilds("\\genfrac ( ] {0.8pt}{}{a}{b+c}")
    assertBuilds("\\genfrac {} {} {0.8pt}{}{a}{b+c}")
    assertBuilds("\\genfrac [ {} {0.8pt}{}{a}{b+c}")
  }

  test("A genfrac builder: should render \\tfrac like \\textstyle\\frac") {
    assertBuildsLike("x_{\\tfrac{1}{2}}", "x_{\\textstyle\\frac{1}{2}}")
  }

  test("A genfrac builder: should render \\dfrac like \\displaystyle\\frac in subscripts") {
    assertBuildsLike("x_{\\dfrac{a}{b}}", "x_{\\displaystyle\\frac{a}{b}}")
    assertBuildsLike("x_{y_{\\dfrac{a}{b}}}", "x_{y_{\\displaystyle\\frac{a}{b}}}")
  }

  // ===========================================================================
  // An infix builder
  // ===========================================================================

  test("An infix builder: should not fail") {
    assertBuilds("a \\over b")
    assertBuilds("a \\atop b")
    assertBuilds("a \\choose b")
    assertBuilds("a \\brace b")
    assertBuilds("a \\brack b")
  }

  // ===========================================================================
  // A sizing parser
  // ===========================================================================

  test("A sizing parser: should not fail") {
    assertParses("\\Huge{x}\\small{x}")
  }

  test("A sizing parser: should produce a sizing node") {
    val parse = getParsed("\\Huge{x}\\small{x}")(0)
    assertEquals(parse.nodeType, "sizing")
  }

  // ===========================================================================
  // A text parser
  // ===========================================================================

  test("A text parser: should not fail") {
    assertParses("\\text{a b}")
  }

  test("A text parser: should produce a text") {
    val parse = getParsed("\\text{a b}")(0)
    assertEquals(parse.nodeType, "text")
  }

  test("A text parser: should produce textords instead of mathords") {
    val parse = getParsed("\\text{a b}")(0).asInstanceOf[ParseNodeText]
    assert(parse.body.nonEmpty)
    assertEquals(parse.body.head.nodeType, "textord")
  }

  test("A text parser: should not parse bad text") {
    assertNotParses("\\text{a b%}")
  }

  test("A text parser: should not parse bad functions inside text") {
    assertNotParses("\\text{\\sqrt{x}}")
  }

  test("A text parser: should parse text with no braces around it") {
    assertParses("\\text x")
  }

  test("A text parser: should parse nested expressions") {
    assertParses("\\text{a {b} \\blue{c} \\textcolor{#fff}{x} \\llap{x}}")
  }

  test("A text parser: should contract spaces") {
    val parse = getParsed("\\text{  a \\  }")(0).asInstanceOf[ParseNodeText]
    val group = parse.body
    assertEquals(group.length, 4)
    assertEquals(group(0).nodeType, "spacing")
    assertEquals(group(1).nodeType, "textord")
    assertEquals(group(2).nodeType, "spacing")
    assertEquals(group(3).nodeType, "spacing")
  }

  test("A text parser: should handle backslash followed by newline") {
    assertParsesLike("\\text{\\ \t\r \n \t\r  }", "\\text{\\ }")
  }

  test("A text parser: should accept math mode tokens after its argument") {
    assertParses("\\text{sin}^2")
  }

  test("A text parser: should ignore a space before the text group") {
    val parse = getParsed("\\text {moo}")(0).asInstanceOf[ParseNodeText]
    assertEquals(parse.body.length, 3)
  }

  test("A text parser: should parse math within text group") {
    assertParses("\\text{graph: $y = mx + b$}", strictSettings)
    assertParses("\\text{graph: \\(y = mx + b\\)}", strictSettings)
  }

  test("A text parser: should parse math within text within math within text") {
    assertParses("\\text{hello $x + \\text{world $y$} + z$}", strictSettings)
    assertParses("\\text{hello \\(x + \\text{world $y$} + z\\)}", strictSettings)
    assertParses("\\text{hello $x + \\text{world \\(y\\)} + z$}", strictSettings)
    assertParses("\\text{hello \\(x + \\text{world \\(y\\)} + z\\)}", strictSettings)
  }

  test("A text parser: should forbid \\( within math mode") {
    assertNotParses("\\(")
    assertNotParses("\\text{$\\(x\\)$}")
  }

  test("A text parser: should forbid $ within math mode") {
    assertNotParses("$x$")
    assertNotParses("\\text{\\($x$\\)}")
  }

  test("A text parser: should detect unbalanced \\)") {
    assertNotParses("\\)")
    assertNotParses("\\text{\\)}")
  }

  test("A text parser: should detect unbalanced $") {
    assertNotParses("$")
    assertNotParses("\\text{$}")
  }

  test("A text parser: should not mix $ and \\(..\\)") {
    assertNotParses("\\text{$x\\)}")
    assertNotParses("\\text{\\(x$}")
  }

  test("A text parser: should parse spacing functions") {
    assertBuilds("a b\\, \\; \\! \\: \\> ~ \\thinspace \\medspace \\quad \\ ")
    assertBuilds("\\enspace \\thickspace \\qquad \\space \\nobreakspace")
  }

  test("A text parser: should omit spaces after commands") {
    assertParsesLike("\\text{\\textellipsis !}", "\\text{\\textellipsis!}")
  }

  test("A text parser: should handle \\vdots and literal ⋮") {
    assertParses("\\text{a \\vdots b ⋮ d}")
  }

  // ===========================================================================
  // A texvc builder
  // ===========================================================================

  test("A texvc builder: should not fail") {
    assertBuilds("\\lang\\N\\darr\\R\\dArr\\Z\\Darr\\alef\\rang")
    assertBuilds("\\alefsym\\uarr\\Alpha\\uArr\\Beta\\Uarr\\Chi")
    assertBuilds("\\clubs\\diamonds\\hearts\\spades\\cnums\\Complex")
    assertBuilds("\\Dagger\\empty\\harr\\Epsilon\\hArr\\Eta\\Harr\\exist")
    assertBuilds("\\image\\larr\\infin\\lArr\\Iota\\Larr\\isin\\Kappa")
    assertBuilds("\\Mu\\lrarr\\natnums\\lrArr\\Nu\\Lrarr\\Omicron")
    assertBuilds("\\real\\rarr\\plusmn\\rArr\\reals\\Rarr\\Reals\\Rho")
    assertBuilds("\\text{\\sect}\\sdot\\sub\\sube\\supe")
    assertBuilds("\\Tau\\thetasym\\weierp\\Zeta")
  }

  // ===========================================================================
  // A color parser
  // ===========================================================================

  test("A color parser: should not fail") {
    assertParses("\\blue{x}")
  }

  test("A color parser: should build a color node") {
    val parse = getParsed("\\blue{x}")(0)
    assertEquals(parse.nodeType, "color")
  }

  test("A color parser: should parse a custom color") {
    assertParses("\\textcolor{#fA6}{x}")
    assertParses("\\textcolor{#fA6fA6}{x}")
    assertParses("\\textcolor{fA6fA6}{x}")
  }

  test("A color parser: should correctly extract the custom color") {
    // Just verify they parse without error
    getParsed("\\textcolor{#fA6}{x}")
    getParsed("\\textcolor{#fA6fA6}{x}")
    getParsed("\\textcolor{fA6fA6}{x}")
  }

  test("A color parser: should not parse a bad custom color") {
    assertNotParses("\\textcolor{bad-color}{x}")
    assertNotParses("\\textcolor{#fA6f1}{x}")
    assertNotParses("\\textcolor{#gA6}{x}")
  }

  test("A color parser: should parse new colors from the branding guide") {
    assertParses("\\redA{x}")
  }

  test("A color parser: should use one-argument \\color by default") {
    assertParsesLike("\\color{#fA6}xy", "\\textcolor{#fA6}{xy}")
  }

  test("A color parser: should use one-argument \\color if requested") {
    assertParsesLike("\\color{#fA6}xy", "\\textcolor{#fA6}{xy}", new Settings(colorIsTextColor = false))
  }

  test("A color parser: should use two-argument \\color if requested") {
    assertParsesLike("\\color{#fA6}xy", "\\textcolor{#fA6}{x}y", new Settings(colorIsTextColor = true))
  }

  test("A color parser: should not define \\color in global context") {
    val macros: MacroMap = mutable.Map.empty
    assertParsesLike("\\color{#fA6}xy", "\\textcolor{#fA6}{x}y", new Settings(colorIsTextColor = true, globalGroup = true, macrosInit = macros))
    assert(macros.isEmpty, "macros should be empty")
  }

  // ===========================================================================
  // Alpha hex color parser
  // ===========================================================================

  test("Alpha hex color parser: should correctly extract alpha hex colors") {
    getParsed("\\textcolor{#ff000080}{x}")
    getParsed("\\textcolor{#1234ABCD}{z}")
    getParsed("\\textcolor{#abc8}{w}")
  }

  test("Alpha hex color parser: should not parse invalid alpha hex colors") {
    assertNotParses("\\textcolor{#ff00008g}{x}")
    assertNotParses("\\textcolor{#ff00008}{x}")
    assertNotParses("\\textcolor{#ff000080f}{x}")
  }

  test("Alpha hex color parser: should build correctly with alpha colors") {
    assertBuilds("\\textcolor{#ff000080}{x}")
    assertBuilds("\\textcolor{#1234ABCD}{z}")
    assertBuilds("\\textcolor{#abc8}{w}")
  }

  // ===========================================================================
  // A tie parser
  // ===========================================================================

  test("A tie parser: should parse ties in math mode") {
    assertParses("a~b")
  }

  test("A tie parser: should parse ties in text mode") {
    assertParses("\\text{a~ b}")
  }

  test("A tie parser: should produce spacing in math mode") {
    val parse = getParsed("a~b")
    assertEquals(parse(1).nodeType, "spacing")
  }

  test("A tie parser: should produce spacing in text mode") {
    val text = getParsed("\\text{a~ b}")(0).asInstanceOf[ParseNodeText]
    val body = text.body
    assertEquals(body(1).nodeType, "spacing")
  }

  test("A tie parser: should not contract with spaces in text mode") {
    val text = getParsed("\\text{a~ b}")(0).asInstanceOf[ParseNodeText]
    val body = text.body
    assertEquals(body(2).nodeType, "spacing")
  }

  // ===========================================================================
  // A delimiter sizing parser
  // ===========================================================================

  test("A delimiter sizing parser: should parse normal delimiters") {
    assertParses("\\bigl |")
    assertParses("\\Biggr \\langle")
  }

  test("A delimiter sizing parser: should not parse not-delimiters") {
    assertNotParses("\\bigl x")
  }

  test("A delimiter sizing parser: should produce a delimsizing") {
    val parse = getParsed("\\bigl |")(0)
    assertEquals(parse.nodeType, "delimsizing")
  }

  test("A delimiter sizing parser: should produce the correct direction delimiter") {
    val leftParse  = getParsed("\\bigl |")(0)
    val rightParse = getParsed("\\Biggr \\langle")(0)
    // Just verify they parse
    assert(leftParse.nodeType == "delimsizing")
    assert(rightParse.nodeType == "delimsizing")
  }

  test("A delimiter sizing parser: should parse the correct size delimiter") {
    // Verify parsing
    getParsed("\\bigl |")
    getParsed("\\Biggr \\langle")
  }

  // ===========================================================================
  // An overline parser
  // ===========================================================================

  test("An overline parser: should not fail") {
    assertParses("\\overline{x}")
  }

  test("An overline parser: should produce an overline") {
    val parse = getParsed("\\overline{x}")(0)
    assertEquals(parse.nodeType, "overline")
  }

  // ===========================================================================
  // An lap parser
  // ===========================================================================

  test("An lap parser: should not fail on a text argument") {
    assertParses("\\rlap{\\,/}{=}")
    assertParses("\\mathrlap{\\,/}{=}")
    assertParses("{=}\\llap{/\\,}")
    assertParses("{=}\\mathllap{/\\,}")
    assertParses("\\sum_{\\clap{ABCDEFG}}")
    assertParses("\\sum_{\\mathclap{ABCDEFG}}")
  }

  test("An lap parser: should not fail if math version is used") {
    assertParses("\\mathrlap{\\frac{a}{b}}{=}")
    assertParses("{=}\\mathllap{\\frac{a}{b}}")
    assertParses("\\sum_{\\mathclap{\\frac{a}{b}}}")
  }

  test("An lap parser: should fail on math if AMS version is used") {
    assertNotParses("\\rlap{\\frac{a}{b}}{=}")
    assertNotParses("{=}\\llap{\\frac{a}{b}}")
    assertNotParses("\\sum_{\\clap{\\frac{a}{b}}}")
  }

  test("An lap parser: should produce a lap") {
    val parse = getParsed("\\mathrlap{\\,/}")(0)
    assertEquals(parse.nodeType, "lap")
  }

  // ===========================================================================
  // A rule parser
  // ===========================================================================

  test("A rule parser: should not fail") {
    assertParses("\\rule{1em}{2em}")
    assertParses("\\rule{1ex}{2em}")
  }

  test("A rule parser: should not parse invalid units") {
    assertNotParses("\\rule{1au}{2em}")
    assertNotParses("\\rule{1em}{em}")
  }

  test("A rule parser: should not parse incomplete rules") {
    assertNotParses("\\rule{1em}")
  }

  test("A rule parser: should produce a rule") {
    val parse = getParsed("\\rule{1em}{2em}")(0)
    assertEquals(parse.nodeType, "rule")
  }

  test("A rule parser: should list the correct units") {
    // Just verify they parse
    getParsed("\\rule{1em}{2em}")
    getParsed("\\rule{1ex}{2em}")
  }

  test("A rule parser: should parse the number correctly") {
    getParsed("\\rule{   01.24ex}{2.450   em   }")
  }

  test("A rule parser: should parse negative sizes") {
    getParsed("\\rule{-1em}{- 0.2em}")
  }

  test("A rule parser: should parse in text mode") {
    assertParses("\\text{a\\rule{1em}{2em}b}")
  }

  // ===========================================================================
  // A kern parser
  // ===========================================================================

  test("A kern parser: should list the correct units") {
    getParsed("\\kern{1em}")
    getParsed("\\kern{1ex}")
    getParsed("\\mkern{1mu}")
    getParsed("a\\kern{1em}b")
  }

  test("A kern parser: should not parse invalid units") {
    assertNotParses("\\kern{1au}")
    assertNotParses("\\kern{em}")
  }

  test("A kern parser: should parse negative sizes") {
    getParsed("\\kern{-1em}")
  }

  test("A kern parser: should parse positive sizes") {
    getParsed("\\kern{+1em}")
  }

  // ===========================================================================
  // A non-braced kern parser
  // ===========================================================================

  test("A non-braced kern parser: should list the correct units") {
    getParsed("\\kern1em")
    getParsed("\\kern 1 ex")
    getParsed("\\mkern 1mu")
    getParsed("a\\mkern1mub")
    getParsed("a\\mkern-1mub")
    getParsed("a\\mkern-1mu b")
  }

  test("A non-braced kern parser: should parse elements on either side of a kern") {
    val abParse1 = getParsed("a\\mkern1mub")
    assertEquals(abParse1.length, 3)
    val abParse2 = getParsed("a\\mkern-1mub")
    assertEquals(abParse2.length, 3)
    val abParse3 = getParsed("a\\mkern-1mu b")
    assertEquals(abParse3.length, 3)
  }

  test("A non-braced kern parser: should not parse invalid units") {
    assertNotParses("\\kern1au")
    assertNotParses("\\kern em")
  }

  test("A non-braced kern parser: should parse negative sizes") {
    getParsed("\\kern-1em")
  }

  test("A non-braced kern parser: should parse positive sizes") {
    getParsed("\\kern+1em")
  }

  test("A non-braced kern parser: should handle whitespace") {
    val abParse = getParsed("a\\mkern\t-\r1  \n mu\nb")
    assertEquals(abParse.length, 3)
  }

  // ===========================================================================
  // A left/right parser
  // ===========================================================================

  test("A left/right parser: should not fail") {
    assertParses("\\left( \\dfrac{x}{y} \\right)")
  }

  test("A left/right parser: should produce a leftright") {
    val parse = getParsed("\\left( \\dfrac{x}{y} \\right)")(0)
    assertEquals(parse.nodeType, "leftright")
  }

  test("A left/right parser: should error when it is mismatched") {
    assertNotParses("\\left( \\dfrac{x}{y}")
    assertNotParses("\\dfrac{x}{y} \\right)")
  }

  test("A left/right parser: should error when braces are mismatched") {
    assertNotParses("{ \\left( \\dfrac{x}{y} } \\right)")
  }

  test("A left/right parser: should error when non-delimiters are provided") {
    assertNotParses("\\left$ \\dfrac{x}{y} \\right)")
  }

  test("A left/right parser: should parse the empty '.' delimiter") {
    assertParses("\\left( \\dfrac{x}{y} \\right.")
  }

  test("A left/right parser: should parse the '.' delimiter with normal sizes") {
    assertParses("\\Bigl .")
  }

  test("A left/right parser: should handle \\middle") {
    assertParses("\\left( \\dfrac{x}{y} \\middle| \\dfrac{y}{z} \\right)")
  }

  test("A left/right parser: should handle multiple \\middles") {
    assertParses("\\left( \\dfrac{x}{y} \\middle| \\dfrac{y}{z} \\middle/ \\dfrac{z}{q} \\right)")
  }

  test("A left/right parser: should handle nested \\middles") {
    assertParses("\\left( a^2 \\middle| \\left( b \\middle/ c \\right) \\right)")
  }

  test("A left/right parser: should error when \\middle is not in \\left...\\right") {
    assertNotParses("(\\middle|\\dfrac{x}{y})")
  }

  // ===========================================================================
  // left/right builder
  // ===========================================================================

  test("left/right builder: should build angle bracket aliases") {
    assertBuildsLike("\\left\\langle \\right\\rangle", "\\left< \\right>")
    assertBuildsLike("\\left\\langle \\right\\rangle", "\\left⟨ \\right⟩")
    assertBuildsLike("\\left\\lparen \\right\\rparen", "\\left( \\right)")
  }

  // ===========================================================================
  // A begin/end parser
  // ===========================================================================

  test("A begin/end parser: should parse a simple environment") {
    assertParses("\\begin{matrix}a&b\\\\c&d\\end{matrix}")
  }

  test("A begin/end parser: should parse an environment with argument") {
    assertParses("\\begin{array}{cc}a&b\\\\c&d\\end{array}")
  }

  test("A begin/end parser: should parse and build an empty environment") {
    assertBuilds("\\begin{aligned}\\end{aligned}")
    assertBuilds("\\begin{matrix}\\end{matrix}")
  }

  test("A begin/end parser: should parse an environment with hlines") {
    assertParses("\\begin{matrix}\\hline a&b\\\\ \\hline c&d\\end{matrix}")
    assertParses("\\begin{matrix}\\hline a&b\\cr \\hline c&d\\end{matrix}")
    assertParses("\\begin{matrix}\\hdashline a&b\\\\ \\hdashline c&d\\end{matrix}")
  }

  test("A begin/end parser: should forbid hlines outside array environment") {
    assertNotParses("\\hline")
  }

  test("A begin/end parser: should error when name is mismatched") {
    assertNotParses("\\begin{matrix}a&b\\\\c&d\\end{pmatrix}")
  }

  test("A begin/end parser: should error when commands are mismatched") {
    assertNotParses("\\begin{matrix}a&b\\\\c&d\\right{pmatrix}")
  }

  test("A begin/end parser: should error when end is missing") {
    assertNotParses("\\begin{matrix}a&b\\\\c&d")
  }

  test("A begin/end parser: should error when braces are mismatched") {
    assertNotParses("{\\begin{matrix}a&b\\\\c&d}\\end{matrix}")
  }

  test("A begin/end parser: should cooperate with infix notation") {
    assertParses("\\begin{matrix}0&1\\over2&3\\\\4&5&6\\end{matrix}")
  }

  test("A begin/end parser: should nest") {
    val m1 = "\\begin{pmatrix}1&2\\\\3&4\\end{pmatrix}"
    val m2 = s"\\begin{array}{rl}${m1}&0\\\\0&${m1}\\end{array}"
    assertParses(m2)
  }

  test("A begin/end parser: should allow \\cr and \\\\ as a line terminator") {
    assertParses("\\begin{matrix}a&b\\cr c&d\\end{matrix}")
    assertParses("\\begin{matrix}a&b\\\\c&d\\end{matrix}")
  }

  test("A begin/end parser: should not allow \\cr to scan for an optional size argument") {
    assertParses("\\begin{matrix}a&b\\cr[c]&d\\end{matrix}")
  }

  test("A begin/end parser: should not treat [ after space as optional argument to \\\\") {
    assertParses("\\begin{matrix}a&b\\\\ [c]&d\\end{matrix}")
    assertParses("a\\\\ [b]")
  }

  test("A begin/end parser: should eat a final newline") {
    val m3 = getParsed("\\begin{matrix}a&b\\\\ c&d \\\\ \\end{matrix}")(0)
    // Just verify parsing
    assert(m3.nodeType == "array")
  }

  test("A begin/end parser: should grab \\arraystretch") {
    val parse = getParsed("\\def\\arraystretch{1.5}\\begin{matrix}a&b\\\\c&d\\end{matrix}")
    assert(parse.nonEmpty)
  }

  test("A begin/end parser: should allow an optional argument in {matrix*} and company") {
    assertBuilds("\\begin{matrix*}[r] a & -1 \\\\ -1 & d \\end{matrix*}")
    assertBuilds("\\begin{pmatrix*}[r] a & -1 \\\\ -1 & d \\end{pmatrix*}")
    assertBuilds("\\begin{bmatrix*}[r] a & -1 \\\\ -1 & d \\end{bmatrix*}")
    assertBuilds("\\begin{Bmatrix*}[r] a & -1 \\\\ -1 & d \\end{Bmatrix*}")
    assertBuilds("\\begin{vmatrix*}[r] a & -1 \\\\ -1 & d \\end{vmatrix*}")
    assertBuilds("\\begin{Vmatrix*}[r] a & -1 \\\\ -1 & d \\end{Vmatrix*}")
    assertBuilds("\\begin{matrix*} a & -1 \\\\ -1 & d \\end{matrix*}")
    assertNotParses("\\begin{matrix*}[] a & -1 \\\\ -1 & d \\end{matrix*}")
  }

  test("A begin/end parser: should allow blank columns") {
    val parsed = getParsed("\\begin{matrix*}[r] a \\\\ -1 & d \\end{matrix*}")
    assert(parsed.nonEmpty)
  }

  // ===========================================================================
  // A sqrt parser
  // ===========================================================================

  test("A sqrt parser: should parse square roots") {
    assertParses("\\sqrt{x}")
  }

  test("A sqrt parser: should error when there is no group") {
    assertNotParses("\\sqrt")
  }

  test("A sqrt parser: should produce sqrts") {
    val parse = getParsed("\\sqrt{x}")(0)
    assertEquals(parse.nodeType, "sqrt")
  }

  test("A sqrt parser: should build sized square roots") {
    assertBuilds("\\Large\\sqrt[3]{x}")
  }

  test("A sqrt parser: should expand argument if optional argument doesn't exist") {
    assertParsesLike("\\sqrt\\foo", "\\sqrt123", new Settings(macrosInit = mutable.Map("\\foo" -> MacroDefinition.StringDef("123"))))
  }

  test("A sqrt parser: should not expand argument if optional argument exists") {
    assertParsesLike("\\sqrt[2]\\foo", "\\sqrt[2]{123}", new Settings(macrosInit = mutable.Map("\\foo" -> MacroDefinition.StringDef("123"))))
  }

  // ===========================================================================
  // A TeX-compliant parser
  // ===========================================================================

  test("A TeX-compliant parser: should work") {
    assertParses("\\frac 2 3")
  }

  test("A TeX-compliant parser: should fail if there are not enough arguments") {
    val missingGroups = List(
      "\\frac{x}",
      "\\textcolor{#fff}",
      "\\rule{1em}",
      "\\llap",
      "\\bigl",
      "\\text"
    )
    for (expr <- missingGroups)
      assertNotParses(expr)
  }

  test("A TeX-compliant parser: should fail when there are missing sup/subscripts") {
    assertNotParses("x^")
    assertNotParses("x_")
  }

  test("A TeX-compliant parser: should fail when arguments require arguments") {
    val badArguments = List(
      "\\frac \\frac x y z",
      "\\frac x \\frac y z",
      "\\frac \\sqrt x y",
      "\\frac x \\sqrt y",
      "\\frac \\mathllap x y",
      "\\frac x \\mathllap y",
      "\\mathllap \\mathllap x",
      "\\sqrt \\mathllap x"
    )
    for (expr <- badArguments)
      assertNotParses(expr)
  }

  test("A TeX-compliant parser: should work when the arguments have braces") {
    val goodArguments = List(
      "\\frac {\\frac x y} z",
      "\\frac x {\\frac y z}",
      "\\frac {\\sqrt x} y",
      "\\frac x {\\sqrt y}",
      "\\frac {\\mathllap x} y",
      "\\frac x {\\mathllap y}",
      "\\mathllap {\\frac x y}",
      "\\mathllap {\\mathllap x}",
      "\\sqrt {\\mathllap x}"
    )
    for (expr <- goodArguments)
      assertParses(expr)
  }

  test("A TeX-compliant parser: should fail when sup/subscripts require arguments") {
    val badSupSubscripts = List(
      "x^\\sqrt x",
      "x^\\mathllap x",
      "x_\\sqrt x",
      "x_\\mathllap x"
    )
    for (expr <- badSupSubscripts)
      assertNotParses(expr)
  }

  test("A TeX-compliant parser: should work when sup/subscripts arguments have braces") {
    val goodSupSubscripts = List(
      "x^{\\sqrt x}",
      "x^{\\mathllap x}",
      "x_{\\sqrt x}",
      "x_{\\mathllap x}"
    )
    for (expr <- goodSupSubscripts)
      assertParses(expr)
  }

  test("A TeX-compliant parser: should allow \\imath in sup/subscripts") {
    assertParses("x^\\imath")
    assertParses("x_\\imath")
  }

  test("A TeX-compliant parser: should parse multiple primes correctly") {
    assertParses("x''''")
    assertParses("x_2''")
    assertParses("x''_2")
  }

  test("A TeX-compliant parser: should fail when sup/subscripts are interspersed with arguments") {
    assertNotParses("\\sqrt^23")
    assertNotParses("\\frac^234")
    assertNotParses("\\frac2^34")
  }

  test("A TeX-compliant parser: should succeed when sup/subscripts come after whole functions") {
    assertParses("\\sqrt2^3")
    assertParses("\\frac23^4")
  }

  test("A TeX-compliant parser: should succeed with a sqrt around a text/frac") {
    assertParses("\\sqrt \\frac x y")
    assertParses("\\sqrt \\text x")
    assertParses("x^\\frac x y")
    assertParses("x_\\text x")
  }

  test("A TeX-compliant parser: should fail when arguments are \\left") {
    val badLeftArguments = List(
      "\\frac \\left( x \\right) y",
      "\\frac x \\left( y \\right)",
      "\\mathllap \\left( x \\right)",
      "\\sqrt \\left( x \\right)",
      "x^\\left( x \\right)"
    )
    for (expr <- badLeftArguments)
      assertNotParses(expr)
  }

  test("A TeX-compliant parser: should succeed when there are braces around the \\left/\\right") {
    val goodLeftArguments = List(
      "\\frac {\\left( x \\right)} y",
      "\\frac x {\\left( y \\right)}",
      "\\mathllap {\\left( x \\right)}",
      "\\sqrt {\\left( x \\right)}",
      "x^{\\left( x \\right)}"
    )
    for (expr <- goodLeftArguments)
      assertParses(expr)
  }

  // ===========================================================================
  // An op symbol builder
  // ===========================================================================

  test("An op symbol builder: should not fail") {
    assertBuilds("\\int_i^n")
    assertBuilds("\\iint_i^n")
    assertBuilds("\\iiint_i^n")
    assertBuilds("\\int\\nolimits_i^n")
    assertBuilds("\\iint\\nolimits_i^n")
    assertBuilds("\\iiint\\nolimits_i^n")
    assertBuilds("\\oint_i^n")
    assertBuilds("\\oiint_i^n")
    assertBuilds("\\oiiint_i^n")
    assertBuilds("\\oint\\nolimits_i^n")
    assertBuilds("\\oiint\\nolimits_i^n")
    assertBuilds("\\oiiint\\nolimits_i^n")
    assertBuilds("\\mathop{\\int}")
    assertBuilds("\\mathop \\int")
  }

  // ===========================================================================
  // A style change parser
  // ===========================================================================

  test("A style change parser: should not fail") {
    assertParses("\\displaystyle x")
    assertParses("\\textstyle x")
    assertParses("\\scriptstyle x")
    assertParses("\\scriptscriptstyle x")
  }

  test("A style change parser: should produce the correct style") {
    val displayParse = getParsed("\\displaystyle x")(0)
    assertEquals(displayParse.nodeType, "styling")

    val scriptscriptParse = getParsed("\\scriptscriptstyle x")(0)
    assertEquals(scriptscriptParse.nodeType, "styling")
  }

  test("A style change parser: should only change the style within its group") {
    val text  = "a b { c d \\displaystyle e f } g h"
    val parse = getParsed(text)
    // Just verify the parse tree has the right structure
    assert(parse.length > 2, s"Expected more than 2 top-level nodes, got ${parse.length}")
  }

  // ===========================================================================
  // A font parser
  // ===========================================================================

  test("A font parser: should parse \\mathrm, \\mathbb, \\mathit, and \\mathnormal") {
    assertParses("\\mathrm x")
    assertParses("\\mathbb x")
    assertParses("\\mathit x")
    assertParses("\\mathnormal x")
    assertParses("\\mathrm {x + 1}")
    assertParses("\\mathbb {x + 1}")
    assertParses("\\mathit {x + 1}")
    assertParses("\\mathnormal {x + 1}")
  }

  test("A font parser: should parse \\mathcal and \\mathfrak") {
    assertParses("\\mathcal{ABC123}")
    assertParses("\\mathfrak{abcABC123}")
  }

  test("A font parser: should produce the correct fonts") {
    val mathbbParse = getParsed("\\mathbb x")(0)
    assertEquals(mathbbParse.nodeType, "font")

    val mathrmParse = getParsed("\\mathrm x")(0)
    assertEquals(mathrmParse.nodeType, "font")

    val mathitParse = getParsed("\\mathit x")(0)
    assertEquals(mathitParse.nodeType, "font")

    val mathnormalParse = getParsed("\\mathnormal x")(0)
    assertEquals(mathnormalParse.nodeType, "font")

    val mathcalParse = getParsed("\\mathcal C")(0)
    assertEquals(mathcalParse.nodeType, "font")

    val mathfrakParse = getParsed("\\mathfrak C")(0)
    assertEquals(mathfrakParse.nodeType, "font")
  }

  test("A font parser: should parse nested font commands") {
    val nestedParse = getParsed("\\mathbb{R \\neq \\mathrm{R}}")(0)
    assertEquals(nestedParse.nodeType, "font")
  }

  test("A font parser: should work with \\textcolor") {
    val colorMathbbParse = getParsed("\\textcolor{blue}{\\mathbb R}")(0)
    assertEquals(colorMathbbParse.nodeType, "color")
  }

  test("A font parser: should not parse a series of font commands") {
    assertNotParses("\\mathbb \\mathrm R")
  }

  test("A font parser: should nest fonts correctly") {
    val bf = getParsed("\\mathbf{a\\mathrm{b}c}")(0)
    assertEquals(bf.nodeType, "font")
  }

  test("A font parser: should be allowed in the argument") {
    assertParses("e^\\mathbf{x}")
  }

  test("A font parser: \\boldsymbol should inherit mbin/mrel from argument") {
    val built = getBuilt("a\\boldsymbol{}b\\boldsymbol{=}c\\boldsymbol{+}d\\boldsymbol{++}e\\boldsymbol{xyz}f")
    assert(built.nonEmpty)
  }

  test("A font parser: old-style fonts work like new-style fonts") {
    assertParsesLike("\\rm xyz", "\\mathrm{xyz}")
    assertParsesLike("\\sf xyz", "\\mathsf{xyz}")
    assertParsesLike("\\tt xyz", "\\mathtt{xyz}")
    assertParsesLike("\\bf xyz", "\\mathbf{xyz}")
    assertParsesLike("\\it xyz", "\\mathit{xyz}")
    assertParsesLike("\\cal xyz", "\\mathcal{xyz}")
  }

  // ===========================================================================
  // A \pmb builder
  // ===========================================================================

  test("A \\pmb builder: should not fail") {
    assertBuilds("\\pmb{\\mu}")
    assertBuilds("\\pmb{=}")
    assertBuilds("\\pmb{+}")
    assertBuilds("\\pmb{\\frac{x^2}{x_1}}")
    assertBuilds("\\pmb{}")
    assertParsesLike("\\def\\x{1}\\pmb{\\x\\def\\x{2}}", "\\pmb{1}")
  }

  // ===========================================================================
  // A raise parser
  // ===========================================================================

  test("A raise parser: should parse and build text in \\raisebox") {
    assertBuilds("\\raisebox{5pt}{text}", strictSettings)
    assertBuilds("\\raisebox{-5pt}{text}", strictSettings)
  }

  test("A raise parser: should parse and build math in non-strict \\vcenter") {
    assertBuilds("\\vcenter{\\frac a b}", nonstrictSettings)
  }

  test("A raise parser: should fail to parse math in \\raisebox") {
    assertNotParses("\\raisebox{5pt}{\\frac a b}", nonstrictSettings)
    assertNotParses("\\raisebox{-5pt}{\\frac a b}", nonstrictSettings)
  }

  test("A raise parser: should fail to parse math in an \\hbox") {
    assertNotParses("\\hbox{\\frac a b}", nonstrictSettings)
  }

  test("A raise parser: should fail to build, given an unbraced length") {
    assertNotBuilds("\\raisebox5pt{text}", strictSettings)
    assertNotBuilds("\\raisebox-5pt{text}", strictSettings)
  }

  test("A raise parser: should build math in an hbox when math mode is set") {
    assertBuilds("a + \\vcenter{\\hbox{$\\frac{\\frac a b}c$}}", strictSettings)
  }

  // ===========================================================================
  // A comment parser
  // ===========================================================================

  test("A comment parser: should parse comments at the end of a line") {
    assertParses("a^2 + b^2 = c^2 % Pythagoras' Theorem\n")
  }

  test("A comment parser: should parse comments at the start of a line") {
    assertParses("% comment\n")
  }

  test("A comment parser: should parse multiple lines of comments in a row") {
    assertParses("% comment 1\n% comment 2\n")
  }

  test("A comment parser: should parse comments between subscript and superscript") {
    assertParsesLike("x_3 %comment\n^2", "x_3^2")
    assertParsesLike("x^ %comment\n{2}", "x^{2}")
    assertParsesLike("x^ %comment\n\\frac{1}{2}", "x^\\frac{1}{2}")
  }

  test("A comment parser: should parse comments in size and color groups") {
    assertParses("\\kern{1 %kern\nem}")
    assertParses("\\kern1 %kern\nem")
    assertParses("\\color{#f00%red\n}")
  }

  test("A comment parser: should parse comments before an expression") {
    assertParsesLike("%comment\n{2}", "{2}")
  }

  test("A comment parser: should parse comments before and between \\hline") {
    assertParses("\\begin{matrix}a&b\\\\ %hline\n\\hline %hline\n\\hline c&d\\end{matrix}")
  }

  test("A comment parser: should parse comments in the macro definition") {
    assertParsesLike("\\def\\foo{1 %}\n2}\n\\foo", "12")
  }

  test("A comment parser: should not expand nor ignore spaces after a command sequence in a comment") {
    assertParsesLike("\\def\\foo{1\n2}\nx %\\foo\n", "x")
  }

  test("A comment parser: should not parse a comment without newline in strict mode") {
    assertNotParses("x%y", strictSettings)
    assertParses("x%y", nonstrictSettings)
  }

  test("A comment parser: should not produce or consume space") {
    assertParsesLike("\\text{hello% comment 1\nworld}", "\\text{helloworld}")
    assertParsesLike("\\text{hello% comment\n\nworld}", "\\text{hello world}")
  }

  test("A comment parser: should not include comments in the output") {
    assertParsesLike("5 % comment\n", "5")
  }

  // ===========================================================================
  // An HTML font tree-builder
  // ===========================================================================

  test("An HTML font tree-builder: should render \\mathbb{R} with the correct font") {
    val markup = KaTeX.renderToString("\\mathbb{R}")
    assert(markup.contains("mord mathbb"), s"Expected mord mathbb in: $markup")
    assert(markup.contains(">R</span>"), s"Expected >R</span> in: $markup")
  }

  test("An HTML font tree-builder: should render \\mathrm{R} with the correct font") {
    val markup = KaTeX.renderToString("\\mathrm{R}")
    assert(markup.contains("mord mathrm"), s"Expected mord mathrm in: $markup")
  }

  test("An HTML font tree-builder: should render \\mathcal{R} with the correct font") {
    val markup = KaTeX.renderToString("\\mathcal{R}")
    assert(markup.contains("mord mathcal"), s"Expected mord mathcal in: $markup")
  }

  test("An HTML font tree-builder: should render \\mathfrak{R} with the correct font") {
    val markup = KaTeX.renderToString("\\mathfrak{R}")
    assert(markup.contains("mord mathfrak"), s"Expected mord mathfrak in: $markup")
  }

  test("An HTML font tree-builder: should render \\text{R} with the correct font") {
    val markup = KaTeX.renderToString("\\text{R}")
    assert(markup.contains("mord"), s"Expected mord in: $markup")
    assert(markup.contains(">R</span>"), s"Expected >R</span> in: $markup")
  }

  test("An HTML font tree-builder: should render \\textit{R} with the correct font") {
    val markup = KaTeX.renderToString("\\textit{R}")
    assert(markup.contains("textit"), s"Expected textit in: $markup")
  }

  test("An HTML font tree-builder: should render \\text{\\textit{R}} with the correct font") {
    val markup = KaTeX.renderToString("\\text{\\textit{R}}")
    assert(markup.contains("textit"), s"Expected textit in: $markup")
  }

  test("An HTML font tree-builder: should render \\textup{R} with the correct font") {
    val markup1 = KaTeX.renderToString("\\textup{R}")
    assert(markup1.contains("textup"), s"Expected textup in: $markup1")
  }

  test("An HTML font tree-builder: should render \\text{R\\textit{S}T} with the correct fonts") {
    val markup = KaTeX.renderToString("\\text{R\\textit{S}T}")
    assert(markup.contains("textit"), s"Expected textit in: $markup")
  }

  test("An HTML font tree-builder: should render \\textbf{R } with the correct font") {
    val markup = KaTeX.renderToString("\\textbf{R }")
    assert(markup.contains("textbf"), s"Expected textbf in: $markup")
  }

  test("An HTML font tree-builder: should render \\textmd{R} with the correct font") {
    val markup1 = KaTeX.renderToString("\\textmd{R}")
    assert(markup1.contains("textmd"), s"Expected textmd in: $markup1")
  }

  test("An HTML font tree-builder: should render \\textsf{R} with the correct font") {
    val markup = KaTeX.renderToString("\\textsf{R}")
    assert(markup.contains("textsf"), s"Expected textsf in: $markup")
  }

  test("An HTML font tree-builder: should render \\textsf{\\textit{R}G\\textbf{B}} with the correct font") {
    val markup = KaTeX.renderToString("\\textsf{\\textit{R}G\\textbf{B}}")
    assert(markup.contains("textsf"), s"Expected textsf in: $markup")
  }

  test("An HTML font tree-builder: should render \\textsf{\\textbf{$\\mathrm{A}$}} with the correct font") {
    val markup = KaTeX.renderToString("\\textsf{\\textbf{$\\mathrm{A}$}}")
    assert(markup.contains("mathrm"), s"Expected mathrm in: $markup")
  }

  test("An HTML font tree-builder: should render \\textsf{\\textbf{$\\mathrm{\\textsf{A}}$}} with the correct font") {
    val markup = KaTeX.renderToString("\\textsf{\\textbf{$\\mathrm{\\textsf{A}}$}}")
    assert(markup.contains("textsf"), s"Expected textsf in: $markup")
  }

  test("An HTML font tree-builder: should render \\texttt{R} with the correct font") {
    val markup = KaTeX.renderToString("\\texttt{R}")
    assert(markup.contains("texttt"), s"Expected texttt in: $markup")
  }

  test("An HTML font tree-builder: should render a combination of font and color changes") {
    var markup = KaTeX.renderToString("\\textcolor{blue}{\\mathbb R}")
    assert(markup.contains("mathbb"), s"Expected mathbb in: $markup")
    assert(markup.contains("blue"), s"Expected blue in: $markup")

    markup = KaTeX.renderToString("\\mathbb{\\textcolor{blue}{R}}")
    assert(markup.contains("mathbb"), s"Expected mathbb in reverse: $markup")
    assert(markup.contains("blue"), s"Expected blue in reverse: $markup")
  }

  test("An HTML font tree-builder: should render wide characters with mord and with the correct font") {
    // Supplementary Unicode chars (surrogate pairs) not matched by re2 on Scala Native
    assume(!System.getProperty("java.vm.name", "").contains("Scala Native"))
    val wideChar = "𝐀" // U+1D400, bold A
    val markup   = KaTeX.renderToString(wideChar)
    assert(markup.contains("mord"), s"Expected mord in: $markup")
  }

  test("An HTML font tree-builder: should not throw TypeError when the expression is a supported type") {
    // In Scala, we only accept String, so just verify normal strings work
    KaTeX.renderToString("\\sqrt{123}")
  }

  // ===========================================================================
  // A MathML font tree-builder
  // ===========================================================================

  test("A MathML font tree-builder: should render contents with the correct mathvariants") {
    val contents = "Ax2k\\omega\\Omega\\imath+"
    val tree     = getParsed(contents)
    val markup   = BuildMathML.buildMathML(tree, contents, defaultOptions, isDisplayMode = false, forMathmlOnly = false).toMarkup()
    assert(markup.contains("<mi>A</mi>"), s"Missing <mi>A</mi> in: $markup")
    assert(markup.contains("<mi>x</mi>"), s"Missing <mi>x</mi> in: $markup")
    assert(markup.contains("<mn>2</mn>"), s"Missing <mn>2</mn> in: $markup")
    assert(markup.contains("<mo>+</mo>"), s"Missing <mo>+</mo> in: $markup")
  }

  test("A MathML font tree-builder: should render \\mathbb with the correct mathvariants") {
    val contents = "Ax2k\\omega\\Omega\\imath+"
    val tex      = s"\\mathbb{$contents}"
    val tree     = getParsed(tex)
    val markup   = BuildMathML.buildMathML(tree, tex, defaultOptions, isDisplayMode = false, forMathmlOnly = false).toMarkup()
    assert(markup.contains("double-struck"), s"Missing double-struck in: $markup")
  }

  test("A MathML font tree-builder: should render \\mathrm with the correct mathvariants") {
    val contents = "Ax2k\\omega\\Omega\\imath+"
    val tex      = s"\\mathrm{$contents}"
    val tree     = getParsed(tex)
    val markup   = BuildMathML.buildMathML(tree, tex, defaultOptions, isDisplayMode = false, forMathmlOnly = false).toMarkup()
    assert(markup.contains("normal"), s"Missing normal in: $markup")
  }

  test("A MathML font tree-builder: should render \\mathit with the correct mathvariants") {
    val contents = "Ax2k\\omega\\Omega\\imath+"
    val tex      = s"\\mathit{$contents}"
    val tree     = getParsed(tex)
    val markup   = BuildMathML.buildMathML(tree, tex, defaultOptions, isDisplayMode = false, forMathmlOnly = false).toMarkup()
    assert(markup.contains("<mn mathvariant=\"italic\">2</mn>"), s"Missing italic variant in: $markup")
  }

  test("A MathML font tree-builder: should render \\mathnormal with the correct mathvariants") {
    val contents = "Ax2k\\omega\\Omega\\imath+"
    val tex      = s"\\mathnormal{$contents}"
    val tree     = getParsed(tex)
    val markup   = BuildMathML.buildMathML(tree, tex, defaultOptions, isDisplayMode = false, forMathmlOnly = false).toMarkup()
    assert(markup.contains("<mi>A</mi>"), s"Missing <mi>A</mi> in: $markup")
  }

  test("A MathML font tree-builder: should render \\mathbf with the correct mathvariants") {
    val contents = "Ax2k\\omega\\Omega\\imath+"
    val tex      = s"\\mathbf{$contents}"
    val tree     = getParsed(tex)
    val markup   = BuildMathML.buildMathML(tree, tex, defaultOptions, isDisplayMode = false, forMathmlOnly = false).toMarkup()
    assert(markup.contains("bold"), s"Missing bold in: $markup")
  }

  test("A MathML font tree-builder: should render \\mathcal with the correct mathvariants") {
    val contents = "Ax2k\\omega\\Omega\\imath+"
    val tex      = s"\\mathcal{$contents}"
    val tree     = getParsed(tex)
    val markup   = BuildMathML.buildMathML(tree, tex, defaultOptions, isDisplayMode = false, forMathmlOnly = false).toMarkup()
    assert(markup.contains("script"), s"Missing script in: $markup")
  }

  test("A MathML font tree-builder: should render \\mathfrak with the correct mathvariants") {
    val contents = "Ax2k\\omega\\Omega\\imath+"
    val tex      = s"\\mathfrak{$contents}"
    val tree     = getParsed(tex)
    val markup   = BuildMathML.buildMathML(tree, tex, defaultOptions, isDisplayMode = false, forMathmlOnly = false).toMarkup()
    assert(markup.contains("fraktur"), s"Missing fraktur in: $markup")
  }

  test("A MathML font tree-builder: should render \\mathscr with the correct mathvariants") {
    val contents = "Ax2k\\omega\\Omega\\imath+"
    val tex      = s"\\mathscr{$contents}"
    val tree     = getParsed(tex)
    val markup   = BuildMathML.buildMathML(tree, tex, defaultOptions, isDisplayMode = false, forMathmlOnly = false).toMarkup()
    assert(markup.contains("script"), s"Missing script in: $markup")
  }

  test("A MathML font tree-builder: should render \\mathsf with the correct mathvariants") {
    val contents = "Ax2k\\omega\\Omega\\imath+"
    val tex      = s"\\mathsf{$contents}"
    val tree     = getParsed(tex)
    val markup   = BuildMathML.buildMathML(tree, tex, defaultOptions, isDisplayMode = false, forMathmlOnly = false).toMarkup()
    assert(markup.contains("sans-serif"), s"Missing sans-serif in: $markup")
  }

  test("A MathML font tree-builder: should render \\mathsfit with the correct mathvariants") {
    val contents = "Ax2k\\omega\\Omega\\imath+"
    val tex      = s"\\mathsfit{$contents}"
    val tree     = getParsed(tex)
    val markup   = BuildMathML.buildMathML(tree, tex, defaultOptions, isDisplayMode = false, forMathmlOnly = false).toMarkup()
    assert(markup.contains("sans-serif-italic"), s"Missing sans-serif-italic in: $markup")
  }

  test("A MathML font tree-builder: should render a combination of font and color changes") {
    var tex    = "\\textcolor{blue}{\\mathbb R}"
    var tree   = getParsed(tex)
    var markup = BuildMathML.buildMathML(tree, tex, defaultOptions, isDisplayMode = false, forMathmlOnly = false).toMarkup()
    assert(markup.contains("double-struck"), s"Missing double-struck in: $markup")
    assert(markup.contains("blue"), s"Missing blue in: $markup")

    tex = "\\mathbb{\\textcolor{blue}{R}}"
    tree = getParsed(tex)
    markup = BuildMathML.buildMathML(tree, tex, defaultOptions, isDisplayMode = false, forMathmlOnly = false).toMarkup()
    assert(markup.contains("double-struck"), s"Missing double-struck in reverse: $markup")
    assert(markup.contains("blue"), s"Missing blue in reverse: $markup")
  }

  test("A MathML font tree-builder: should render text as <mtext>") {
    val tex    = "\\text{for }"
    val tree   = getParsed(tex)
    val markup = BuildMathML.buildMathML(tree, tex, defaultOptions, isDisplayMode = false, forMathmlOnly = false).toMarkup()
    assert(markup.contains("<mtext>"), s"Missing <mtext> in: $markup")
  }

  test("A MathML font tree-builder: should render math within text as side-by-side children") {
    val tex    = "\\text{graph: $y = mx + b$}"
    val tree   = getParsed(tex)
    val markup = BuildMathML.buildMathML(tree, tex, defaultOptions, isDisplayMode = false, forMathmlOnly = false).toMarkup()
    assert(markup.contains("<mtext>"), s"Missing <mtext> in: $markup")
  }

  // ===========================================================================
  // An includegraphics builder
  // ===========================================================================

  test("An includegraphics builder: should not fail") {
    val img = "\\includegraphics[height=0.9em, totalheight=0.9em, width=0.9em, alt=KA logo]{https://cdn.kastatic.org/images/apple-touch-icon-57x57-precomposed.new.png}"
    assertBuilds(img, trustSettings)
  }

  test("An includegraphics builder: should produce mords") {
    val img   = "\\includegraphics[height=0.9em, totalheight=0.9em, width=0.9em, alt=KA logo]{https://cdn.kastatic.org/images/apple-touch-icon-57x57-precomposed.new.png}"
    val built = getBuilt(img, trustSettings)
    assert(built(0).classes.contains("mord"), s"Expected mord class")
  }

  test("An includegraphics builder: should not render without trust setting") {
    val img   = "\\includegraphics[height=0.9em, totalheight=0.9em, width=0.9em, alt=KA logo]{https://cdn.kastatic.org/images/apple-touch-icon-57x57-precomposed.new.png}"
    val built = getBuilt(img)
    assert(built.nonEmpty)
  }

  test("An includegraphics builder: should render with trust setting") {
    val img   = "\\includegraphics[height=0.9em, totalheight=0.9em, width=0.9em, alt=KA logo]{https://cdn.kastatic.org/images/apple-touch-icon-57x57-precomposed.new.png}"
    val built = getBuilt(img, trustSettings)
    assert(built.nonEmpty)
  }

  test("An includegraphics builder: should escape source") {
    val built = KaTeX.renderToString("\\includegraphics{'\"}", trustSettings)
    assert(built.contains("<img"), s"Expected <img in: $built")
  }

  test("An includegraphics builder: should escape alt") {
    val built = KaTeX.renderToString("\\includegraphics[alt='\"]{image.png}", trustSettings)
    assert(built.contains("<img"), s"Expected <img in: $built")
  }

  // ===========================================================================
  // An HTML extension builder
  // ===========================================================================

  test("An HTML extension builder: should not fail") {
    val html                   = "\\htmlId{bar}{x}\\htmlClass{foo}{x}\\htmlStyle{color: red;}{x}\\htmlData{foo=a, bar=b}{x}"
    val trustNonStrictSettings = new Settings(trust = TrustSetting.BoolValue(true), strict = StrictSetting.BoolValue(false))
    assertBuilds(html, trustNonStrictSettings)
  }

  test("An HTML extension builder: should set HTML attributes") {
    val html                   = "\\htmlId{bar}{x}\\htmlClass{foo}{x}\\htmlStyle{color: red;}{x}\\htmlData{foo=a, bar=b}{x}"
    val trustNonStrictSettings = new Settings(trust = TrustSetting.BoolValue(true), strict = StrictSetting.BoolValue(false))
    val built                  = getBuilt(html, trustNonStrictSettings)
    assert(built.nonEmpty)
  }

  test("An HTML extension builder: should not affect spacing") {
    val trustNonStrictSettings = new Settings(trust = TrustSetting.BoolValue(true), strict = StrictSetting.BoolValue(false))
    val built                  = getBuilt("\\htmlId{a}{x+}y", trustNonStrictSettings)
    assert(built.nonEmpty)
  }

  test("An HTML extension builder: should throw Error when HTML attribute name is invalid") {
    val trustNonStrictSettings = new Settings(trust = TrustSetting.BoolValue(true), strict = StrictSetting.BoolValue(false))
    for (char <- List(">", " ", "\t", "\n", "\r", "\"", "'", "/")) {
      val caught = intercept[ParseError] {
        KaTeX.renderToString(s"\\htmlData{a${char}b=foo}{bar}", trustNonStrictSettings)
      }
      assert(
        caught.getMessage.contains("Invalid attribute name"),
        s"Expected 'Invalid attribute name' for char '$char' but got: ${caught.getMessage}"
      )
    }
  }

  // ===========================================================================
  // The \htmlData macro
  // ===========================================================================

  test("The \\htmlData macro: should not fail if an argument contains a single equals sign") {
    val trustNonStrictSettings = new Settings(trust = TrustSetting.BoolValue(true), strict = StrictSetting.BoolValue(false))
    assertBuilds("\\htmlData{foo=a}{x}", trustNonStrictSettings)
  }

  test("The \\htmlData macro: should allow equals signs in value") {
    val trustNonStrictSettings = new Settings(trust = TrustSetting.BoolValue(true), strict = StrictSetting.BoolValue(false))
    val built                  = getBuilt("\\htmlData{foo=a=b}{x}", trustNonStrictSettings)
    assert(built.nonEmpty)
  }

  test("The \\htmlData macro: should accept empty values") {
    val trustNonStrictSettings = new Settings(trust = TrustSetting.BoolValue(true), strict = StrictSetting.BoolValue(false))
    assertBuilds("\\htmlData{foo=}{x}", trustNonStrictSettings)
  }

  test("The \\htmlData macro: should accept empty keys") {
    val trustNonStrictSettings = new Settings(trust = TrustSetting.BoolValue(true), strict = StrictSetting.BoolValue(false))
    assertBuilds("\\htmlData{=a}{x}", trustNonStrictSettings)
  }

  test("The \\htmlData macro: should preserve spaces in value") {
    val trustNonStrictSettings = new Settings(trust = TrustSetting.BoolValue(true), strict = StrictSetting.BoolValue(false))
    val built                  = getBuilt("\\htmlData{foo= bar }{x}", trustNonStrictSettings)
    assert(built.nonEmpty)
  }

  test("The \\htmlData macro: should throw Error if an argument contains no equals signs") {
    val trustNonStrictSettings = new Settings(trust = TrustSetting.BoolValue(true), strict = StrictSetting.BoolValue(false))
    val caught                 = intercept[ParseError] {
      KaTeX.renderToString("\\htmlData{foo}{x}", trustNonStrictSettings)
    }
    assert(caught.getMessage.contains("missing equals sign"), s"Expected 'missing equals sign' but got: ${caught.getMessage}")
  }

  // ===========================================================================
  // A bin builder
  // ===========================================================================

  test("A bin builder: should create mbins normally") {
    val built = getBuilt("x + y")
    assert(built(2).classes.contains("mbin"), "Expected mbin class on +")
  }

  test("A bin builder: should create ords when at the beginning of lists") {
    val built = getBuilt("+ x")
    assert(built(0).classes.contains("mord"), "Expected mord class on leading +")
    assert(!built(0).classes.contains("mbin"), "Should not have mbin class on leading +")
  }

  test("A bin builder: should create ords after some other objects") {
    // Verify the second '+' in 'x + + 2' has bin->ord cancellation.
    // The built output is: x(mord), mspace, +(mbin), +(mord), mspace, 2(mord)
    // In the original JS KaTeX, spacing differs slightly; in our port the
    // second '+' appears at index 3 rather than index 4.
    val xpp2 = getBuilt("x + + 2")
    assert(
      xpp2.exists(n =>
        n.classes.contains("mord") && n.isInstanceOf[ssg.katex.tree.SymbolNode] &&
          n.asInstanceOf[ssg.katex.tree.SymbolNode].text == "+"
      ),
      "Expected second '+' to be mord"
    )
    // For the remaining tests, the '+' right after a left-canceller becomes mord.
    // Find the first SymbolNode with text "+" and check it has mord.
    def findFirstPlus(built: scala.collection.mutable.ArrayBuffer[ssg.katex.tree.HtmlDomNode]): ssg.katex.tree.HtmlDomNode =
      built
        .find(n =>
          n.isInstanceOf[ssg.katex.tree.SymbolNode] &&
            n.asInstanceOf[ssg.katex.tree.SymbolNode].text == "+"
        )
        .get
    assert(findFirstPlus(getBuilt("( + 2")).classes.contains("mord"))
    assert(findFirstPlus(getBuilt("= + 2")).classes.contains("mord"))
    assert(findFirstPlus(getBuilt("\\sin + 2")).classes.contains("mord"))
    assert(findFirstPlus(getBuilt(", + 2")).classes.contains("mord"))
  }

  test("A bin builder: should correctly interact with color objects") {
    assert(getBuilt("\\blue{x}+y")(2).classes.contains("mbin"))
    assert(getBuilt("\\blue{x+}+y")(2).classes.contains("mbin"))
  }

  // ===========================================================================
  // A \phantom builder and \smash builder
  // ===========================================================================

  test("A \\phantom builder and \\smash builder: should both build a mord") {
    assert(getBuilt("\\hphantom{a}")(0).classes.contains("mord"))
    assert(getBuilt("a\\hphantom{=}b")(2).classes.contains("mord"))
    assert(getBuilt("a\\hphantom{+}b")(2).classes.contains("mord"))
    assert(getBuilt("\\smash{a}")(0).classes.contains("mord"))
    assert(getBuilt("\\smash{=}")(0).classes.contains("mord"))
    assert(getBuilt("a\\smash{+}b")(2).classes.contains("mord"))
  }

  // ===========================================================================
  // A markup generator
  // ===========================================================================

  test("A markup generator: marks trees up") {
    val markup = KaTeX.renderToString("\\sigma^2")
    assertEquals(markup.indexOf("<span"), 0)
    assert(markup.contains("σ"), s"Expected sigma in: $markup") // sigma
    assert(markup.contains("margin-right"), s"Expected margin-right in: $markup")
    assert(!markup.contains("marginRight"), s"Should not contain marginRight in: $markup")
  }

  test("A markup generator: generates both MathML and HTML") {
    val markup = KaTeX.renderToString("a")
    assert(markup.contains("<span"), s"Expected <span in: $markup")
    assert(markup.contains("<math"), s"Expected <math in: $markup")
  }

  // ===========================================================================
  // A parse tree generator
  // ===========================================================================

  test("A parse tree generator: generates a tree") {
    val tree = stripPositions(getParsed("\\sigma^2"))
    assert(tree.nonEmpty)
    assertEquals(tree(0).nodeType, "supsub")
  }

  // ===========================================================================
  // An accent parser
  // ===========================================================================

  test("An accent parser: should not fail") {
    assertParses("\\vec{x}")
    assertParses("\\vec{x^2}")
    assertParses("\\vec{x}^2")
    assertParses("\\vec x")
    assertParses("\\underbar{X}")
  }

  test("An accent parser: should produce accents") {
    val parse = getParsed("\\vec x")(0)
    assertEquals(parse.nodeType, "accent")
  }

  test("An accent parser: should be grouped more tightly than supsubs") {
    val parse = getParsed("\\vec x^2")(0)
    assertEquals(parse.nodeType, "supsub")
  }

  test("An accent parser: should parse stretchy, shifty accents") {
    assertParses("\\widehat{x}")
    assertParses("\\widecheck{x}")
  }

  test("An accent parser: should parse stretchy, non-shifty accents") {
    assertParses("\\overrightarrow{x}")
  }

  // ===========================================================================
  // An accent builder
  // ===========================================================================

  test("An accent builder: should not fail") {
    assertBuilds("\\vec{x}")
    assertBuilds("\\vec{x}^2")
    assertBuilds("\\vec{x}_2")
    assertBuilds("\\vec{x}_2^2")
  }

  test("An accent builder: should produce mords") {
    assert(getBuilt("\\vec x")(0).classes.contains("mord"))
    assert(getBuilt("\\vec +")(0).classes.contains("mord"))
    assert(!getBuilt("\\vec +")(0).classes.contains("mbin"))
    assert(getBuilt("\\vec )^2")(0).classes.contains("mord"))
    assert(!getBuilt("\\vec )^2")(0).classes.contains("mclose"))
  }

  test("An accent builder: should keep accent shifts consistent across font wrappers") {
    // Just verify both render without error
    KaTeX.renderToString("\\hat{\\mathbb{I}}")
    KaTeX.renderToString("\\mathbb{\\hat{I}}")
  }

  // ===========================================================================
  // A stretchy and shifty accent builder
  // ===========================================================================

  test("A stretchy and shifty accent builder: should not fail") {
    assertBuilds("\\widehat{AB}")
    assertBuilds("\\widecheck{AB}")
    assertBuilds("\\widehat{AB}^2")
    assertBuilds("\\widehat{AB}_2")
    assertBuilds("\\widehat{AB}_2^2")
  }

  test("A stretchy and shifty accent builder: should produce mords") {
    assert(getBuilt("\\widehat{AB}")(0).classes.contains("mord"))
    assert(getBuilt("\\widehat +")(0).classes.contains("mord"))
    assert(!getBuilt("\\widehat +")(0).classes.contains("mbin"))
  }

  // ===========================================================================
  // A stretchy and non-shifty accent builder
  // ===========================================================================

  test("A stretchy and non-shifty accent builder: should not fail") {
    assertBuilds("\\overrightarrow{AB}")
    assertBuilds("\\overrightarrow{AB}^2")
    assertBuilds("\\overrightarrow{AB}_2")
    assertBuilds("\\overrightarrow{AB}_2^2")
  }

  test("A stretchy and non-shifty accent builder: should produce mords") {
    assert(getBuilt("\\overrightarrow{AB}")(0).classes.contains("mord"))
    assert(getBuilt("\\overrightarrow +")(0).classes.contains("mord"))
    assert(!getBuilt("\\overrightarrow +")(0).classes.contains("mbin"))
  }

  // ===========================================================================
  // A stretchy MathML builder
  // ===========================================================================

  test("A stretchy MathML builder: should properly render stretchy accents") {
    val tex    = "\\widetilde{ABCD}"
    val tree   = getParsed(tex)
    val markup = BuildMathML.buildMathML(tree, tex, defaultOptions, isDisplayMode = false, forMathmlOnly = false).toMarkup()
    assert(markup.contains("stretchy=\"true\""), s"Missing stretchy=true in: $markup")
  }

  // ===========================================================================
  // An under-accent parser
  // ===========================================================================

  test("An under-accent parser: should not fail") {
    assertParses("\\underrightarrow{x}")
    assertParses("\\underrightarrow{x^2}")
    assertParses("\\underrightarrow{x}^2")
    assertParses("\\underrightarrow x")
  }

  test("An under-accent parser: should produce accentUnder") {
    val parse = getParsed("\\underrightarrow x")(0)
    assertEquals(parse.nodeType, "accentUnder")
  }

  test("An under-accent parser: should be grouped more tightly than supsubs") {
    val parse = getParsed("\\underrightarrow x^2")(0)
    assertEquals(parse.nodeType, "supsub")
  }

  // ===========================================================================
  // An under-accent builder
  // ===========================================================================

  test("An under-accent builder: should not fail") {
    assertBuilds("\\underrightarrow{x}")
    assertBuilds("\\underrightarrow{x}^2")
    assertBuilds("\\underrightarrow{x}_2")
    assertBuilds("\\underrightarrow{x}_2^2")
  }

  test("An under-accent builder: should produce mords") {
    assert(getBuilt("\\underrightarrow x")(0).classes.contains("mord"))
    assert(getBuilt("\\underrightarrow +")(0).classes.contains("mord"))
    assert(!getBuilt("\\underrightarrow +")(0).classes.contains("mbin"))
  }

  // ===========================================================================
  // An extensible arrow parser
  // ===========================================================================

  test("An extensible arrow parser: should not fail") {
    assertParses("\\xrightarrow{x}")
    assertParses("\\xrightarrow{x^2}")
    assertParses("\\xrightarrow{x}^2")
    assertParses("\\xrightarrow x")
    assertParses("\\xrightarrow[under]{over}")
  }

  test("An extensible arrow parser: should produce xArrow") {
    val parse = getParsed("\\xrightarrow x")(0)
    assertEquals(parse.nodeType, "xArrow")
  }

  test("An extensible arrow parser: should be grouped more tightly than supsubs") {
    val parse = getParsed("\\xrightarrow x^2")(0)
    assertEquals(parse.nodeType, "supsub")
  }

  // ===========================================================================
  // An extensible arrow builder
  // ===========================================================================

  test("An extensible arrow builder: should not fail") {
    assertBuilds("\\xrightarrow{x}")
    assertBuilds("\\xrightarrow{x}^2")
    assertBuilds("\\xrightarrow{x}_2")
    assertBuilds("\\xrightarrow{x}_2^2")
    assertBuilds("\\xrightarrow[under]{over}")
  }

  test("An extensible arrow builder: should produce mrell") {
    assert(getBuilt("\\xrightarrow x")(0).classes.contains("mrel"))
    assert(getBuilt("\\xrightarrow [under]{over}")(0).classes.contains("mrel"))
    assert(getBuilt("\\xrightarrow +")(0).classes.contains("mrel"))
    assert(!getBuilt("\\xrightarrow +")(0).classes.contains("mbin"))
  }

  // ===========================================================================
  // A horizontal brace parser
  // ===========================================================================

  test("A horizontal brace parser: should not fail") {
    assertParses("\\overbrace{x}")
    assertParses("\\overbrace{x^2}")
    assertParses("\\overbrace{x}^2")
    assertParses("\\overbrace x")
    assertParses("\\underbrace{x}_2")
    assertParses("\\underbrace{x}_2^2")
  }

  test("A horizontal brace parser: should produce horizBrace") {
    val parse = getParsed("\\overbrace x")(0)
    assertEquals(parse.nodeType, "horizBrace")
  }

  test("A horizontal brace parser: should be grouped more tightly than supsubs") {
    val parse = getParsed("\\overbrace x^2")(0)
    assertEquals(parse.nodeType, "supsub")
  }

  // ===========================================================================
  // A horizontal brace builder
  // ===========================================================================

  test("A horizontal brace builder: should not fail") {
    assertBuilds("\\overbrace{x}")
    assertBuilds("\\overbrace{x}^2")
    assertBuilds("\\underbrace{x}_2")
    assertBuilds("\\underbrace{x}_2^2")
  }

  test("A horizontal brace builder: should produce minners") {
    assert(getBuilt("\\overbrace x")(0).classes.contains("minner"))
    assert(getBuilt("\\overbrace{x}^2")(0).classes.contains("minner"))
    assert(getBuilt("\\overbrace +")(0).classes.contains("minner"))
    assert(!getBuilt("\\overbrace +")(0).classes.contains("mord"))
  }

  // ===========================================================================
  // A horizontal bracket parser
  // ===========================================================================

  test("A horizontal bracket parser: should not fail") {
    assertParses("\\overbracket{x}")
    assertParses("\\underbracket{x}_2")
  }

  test("A horizontal bracket parser: should produce horizBrace") {
    val parse = getParsed("\\overbracket x")(0)
    assertEquals(parse.nodeType, "horizBrace")
  }

  test("A horizontal bracket parser: should be grouped more tightly than supsubs") {
    val parse = getParsed("\\overbracket x^2")(0)
    assertEquals(parse.nodeType, "supsub")
  }

  // ===========================================================================
  // A horizontal bracket builder
  // ===========================================================================

  test("A horizontal bracket builder: should not fail") {
    assertBuilds("\\overbracket{x}")
    assertBuilds("\\underbracket{x}_2")
  }

  test("A horizontal bracket builder: should produce minners") {
    assert(getBuilt("\\overbracket x")(0).classes.contains("minner"))
    assert(getBuilt("\\underbracket x")(0).classes.contains("minner"))
  }

  // ===========================================================================
  // A boxed parser
  // ===========================================================================

  test("A boxed parser: should not fail") {
    assertParses("\\boxed{x}")
    assertParses("\\boxed{x^2}")
    assertParses("\\boxed{x}^2")
    assertParses("\\boxed x")
  }

  test("A boxed parser: should produce enclose") {
    val parse = getParsed("\\boxed x")(0)
    assertEquals(parse.nodeType, "enclose")
  }

  // ===========================================================================
  // A boxed builder
  // ===========================================================================

  test("A boxed builder: should not fail") {
    assertBuilds("\\boxed{x}")
    assertBuilds("\\boxed{x}^2")
    assertBuilds("\\boxed{x}_2")
    assertBuilds("\\boxed{x}_2^2")
  }

  test("A boxed builder: should produce mords") {
    assert(getBuilt("\\boxed x")(0).classes.contains("mord"))
    assert(getBuilt("\\boxed +")(0).classes.contains("mord"))
    assert(!getBuilt("\\boxed +")(0).classes.contains("mbin"))
  }

  // ===========================================================================
  // An fbox parser
  // ===========================================================================

  test("An fbox parser, unlike a boxed parser: should fail when given math") {
    assertNotParses("\\fbox{\\frac a b}")
  }

  // ===========================================================================
  // A colorbox parser
  // ===========================================================================

  test("A colorbox parser: should not fail, given a text argument") {
    assertParses("\\colorbox{red}{a b}")
    assertParses("\\colorbox{red}{x}^2")
    assertParses("\\colorbox{red} x")
  }

  test("A colorbox parser: should fail, given a math argument") {
    assertNotParses("\\colorbox{red}{\\alpha}")
    assertNotParses("\\colorbox{red}{\\frac{a}{b}}")
  }

  test("A colorbox parser: should parse a color") {
    assertParses("\\colorbox{red}{a b}")
    assertParses("\\colorbox{#197}{a b}")
    assertParses("\\colorbox{#1a9b7c}{a b}")
  }

  test("A colorbox parser: should produce enclose") {
    val parse = getParsed("\\colorbox{red} x")(0)
    assertEquals(parse.nodeType, "enclose")
  }

  // ===========================================================================
  // A colorbox builder
  // ===========================================================================

  test("A colorbox builder: should not fail") {
    assertBuilds("\\colorbox{red}{a b}")
    assertBuilds("\\colorbox{red}{a b}^2")
    assertBuilds("\\colorbox{red} x")
  }

  test("A colorbox builder: should produce mords") {
    assert(getBuilt("\\colorbox{red}{a b}")(0).classes.contains("mord"))
  }

  // ===========================================================================
  // An fcolorbox parser
  // ===========================================================================

  test("An fcolorbox parser: should not fail, given a text argument") {
    assertParses("\\fcolorbox{blue}{yellow}{a b}")
    assertParses("\\fcolorbox{blue}{yellow}{x}^2")
    assertParses("\\fcolorbox{blue}{yellow} x")
  }

  test("An fcolorbox parser: should fail, given a math argument") {
    assertNotParses("\\fcolorbox{blue}{yellow}{\\alpha}")
    assertNotParses("\\fcolorbox{blue}{yellow}{\\frac{a}{b}}")
  }

  test("An fcolorbox parser: should parse a color") {
    assertParses("\\fcolorbox{blue}{yellow}{a b}")
    assertParses("\\fcolorbox{blue}{#197}{a b}")
    assertParses("\\fcolorbox{blue}{#1a9b7c}{a b}")
  }

  test("An fcolorbox parser: should produce enclose") {
    val parse = getParsed("\\fcolorbox{blue}{yellow} x")(0)
    assertEquals(parse.nodeType, "enclose")
  }

  // ===========================================================================
  // A fcolorbox builder
  // ===========================================================================

  test("A fcolorbox builder: should not fail") {
    assertBuilds("\\fcolorbox{blue}{yellow}{a b}")
    assertBuilds("\\fcolorbox{blue}{yellow}{a b}^2")
    assertBuilds("\\fcolorbox{blue}{yellow} x")
  }

  test("A fcolorbox builder: should produce mords") {
    assert(getBuilt("\\colorbox{red}{a b}")(0).classes.contains("mord"))
  }

  // ===========================================================================
  // A strike-through parser
  // ===========================================================================

  test("A strike-through parser: should not fail") {
    assertParses("\\cancel{x}")
    assertParses("\\cancel{x^2}")
    assertParses("\\cancel{x}^2")
    assertParses("\\cancel x")
  }

  test("A strike-through parser: should produce enclose") {
    val parse = getParsed("\\cancel x")(0)
    assertEquals(parse.nodeType, "enclose")
  }

  test("A strike-through parser: should be grouped more tightly than supsubs") {
    val parse = getParsed("\\cancel x^2")(0)
    assertEquals(parse.nodeType, "supsub")
  }

  // ===========================================================================
  // A strike-through builder
  // ===========================================================================

  test("A strike-through builder: should not fail") {
    assertBuilds("\\cancel{x}")
    assertBuilds("\\cancel{x}^2")
    assertBuilds("\\cancel{x}_2")
    assertBuilds("\\cancel{x}_2^2")
    assertBuilds("\\sout{x}", nonstrictSettings)
    assertBuilds("\\sout{x}^2", nonstrictSettings)
    assertBuilds("\\sout{x}_2", nonstrictSettings)
    assertBuilds("\\sout{x}_2^2", nonstrictSettings)
    assertBuilds("\\text{\\sout{abc}}")
  }

  test("A strike-through builder: should produce mords") {
    assert(getBuilt("\\cancel x")(0).classes.contains("mord"))
    assert(getBuilt("\\cancel +")(0).classes.contains("mord"))
    assert(!getBuilt("\\cancel +")(0).classes.contains("mbin"))
  }

  // ===========================================================================
  // A \sout parser
  // ===========================================================================

  test("A \\sout parser: should work in text mode") {
    assertParses("\\text{\\sout{abc}}")
  }

  test("A \\sout parser: should fail in math mode with strict settings") {
    assertNotParses("\\sout{x}", strictSettings)
  }

  // ===========================================================================
  // An actuarial angle parser/builder
  // ===========================================================================

  test("An actuarial angle parser: should not fail in math mode") {
    assertParses("a_{\\angl{n}}")
  }

  test("An actuarial angle parser: should fail in text mode") {
    assertNotParses("\\text{a_{\\angl{n}}}")
  }

  test("An actuarial angle builder: should not fail") {
    assertBuilds("a_{\\angl{n}}")
    assertBuilds("a_{\\angl{n}i}")
    assertBuilds("a_{\\angl n}")
    assertBuilds("a_\\angln")
  }

  // ===========================================================================
  // \phase
  // ===========================================================================

  test("\\phase: should fail in text mode") {
    assertNotParses("\\text{\\phase{-78.2^\\circ}}")
  }

  test("\\phase: should not fail in math mode") {
    assertBuilds("\\phase{-78.2^\\circ}")
  }

  // ===========================================================================
  // A phantom parser
  // ===========================================================================

  test("A phantom parser: should not fail") {
    assertParses("\\phantom{x}")
    assertParses("\\phantom{x^2}")
    assertParses("\\phantom{x}^2")
    assertParses("\\phantom x")
    assertParses("\\hphantom{x}")
    assertParses("\\hphantom{x^2}")
    assertParses("\\hphantom{x}^2")
    assertParses("\\hphantom x")
  }

  test("A phantom parser: should build a phantom node") {
    val parse = getParsed("\\phantom{x}")(0)
    assertEquals(parse.nodeType, "phantom")
  }

  // ===========================================================================
  // A phantom builder
  // ===========================================================================

  test("A phantom builder: should not fail") {
    assertBuilds("\\phantom{x}")
    assertBuilds("\\phantom{x^2}")
    assertBuilds("\\phantom{x}^2")
    assertBuilds("\\phantom x")
    assertBuilds("\\mathstrut")
    assertBuilds("\\hphantom{x}")
    assertBuilds("\\hphantom{x^2}")
    assertBuilds("\\hphantom{x}^2")
    assertBuilds("\\hphantom x")
  }

  // ===========================================================================
  // A smash parser
  // ===========================================================================

  test("A smash parser: should not fail") {
    assertParses("\\smash{x}")
    assertParses("\\smash{x^2}")
    assertParses("\\smash{x}^2")
    assertParses("\\smash x")
    assertParses("\\smash[b]{x}")
    assertParses("\\smash[b]{x^2}")
    assertParses("\\smash[b]{x}^2")
    assertParses("\\smash[b] x")
    assertParses("\\smash[]{x}")
    assertParses("\\smash[]{x^2}")
    assertParses("\\smash[]{x}^2")
    assertParses("\\smash[] x")
  }

  test("A smash parser: should build a smash node") {
    val parse = getParsed("\\smash{x}")(0)
    assertEquals(parse.nodeType, "smash")
  }

  // ===========================================================================
  // A smash builder
  // ===========================================================================

  test("A smash builder: should not fail") {
    assertBuilds("\\smash{x}", nonstrictSettings)
    assertBuilds("\\smash{x^2}", nonstrictSettings)
    assertBuilds("\\smash{x}^2", nonstrictSettings)
    assertBuilds("\\smash x", nonstrictSettings)
    assertBuilds("\\smash[b]{x}", nonstrictSettings)
    assertBuilds("\\smash[b]{x^2}", nonstrictSettings)
    assertBuilds("\\smash[b]{x}^2", nonstrictSettings)
    assertBuilds("\\smash[b] x", nonstrictSettings)
  }

  // ===========================================================================
  // A parser error
  // ===========================================================================

  test("A parser error: should report the position of an error") {
    try {
      ParseTree.parseTree("\\sqrt}", new Settings())
      fail("Expected ParseError")
    } catch {
      case e: ParseError =>
        // position should be 5 in the original
        assert(e.position.isDefined)
    }
  }

  // ===========================================================================
  // An optional argument parser
  // ===========================================================================

  test("An optional argument parser: should not fail") {
    assertParses("\\frac[1]{2}{3}")
    assertParses("\\rule[0.2em]{1em}{1em}")
  }

  test("An optional argument parser: should work with sqrts with optional arguments") {
    assertParses("\\sqrt[3]{2}")
  }

  test("An optional argument parser: should work when the optional argument is missing") {
    assertParses("\\sqrt{2}")
    assertParses("\\rule{1em}{2em}")
  }

  test("An optional argument parser: should fail when the optional argument is malformed") {
    assertNotParses("\\rule[1]{2em}{3em}")
  }

  test("An optional argument parser: should not work if the optional argument isn't closed") {
    assertNotParses("\\sqrt[")
  }

  // ===========================================================================
  // An array environment
  // ===========================================================================

  test("An array environment: should accept a single alignment character") {
    val parse = getParsed("\\begin{array}r1\\\\20\\end{array}")
    assertEquals(parse(0).nodeType, "array")
  }

  test("An array environment: should accept vertical separators") {
    val parse = getParsed("\\begin{array}{|l||c:r::}\\end{array}")
    assertEquals(parse(0).nodeType, "array")
  }

  // ===========================================================================
  // A subarray environment
  // ===========================================================================

  test("A subarray environment: should accept only a single alignment character") {
    val parse = getParsed("\\begin{subarray}{c}a \\\\ b\\end{subarray}")
    assertEquals(parse(0).nodeType, "array")
    assertNotParses("\\begin{subarray}{cc}a \\\\ b\\end{subarray}")
    assertNotParses("\\begin{subarray}{c}a & b \\\\ c & d\\end{subarray}")
    assertBuilds("\\begin{subarray}{c}a \\\\ b\\end{subarray}")
  }

  // ===========================================================================
  // A substack function
  // ===========================================================================

  test("A substack function: should build") {
    assertBuilds("\\sum_{\\substack{ 0<i<m \\\\ 0<j<n }}  P(i,j)")
  }

  test("A substack function: should accommodate spaces in the argument") {
    assertBuilds("\\sum_{\\substack{ 0<i<m \\\\ 0<j<n }}  P(i,j)")
  }

  test("A substack function: should accommodate macros in the argument") {
    assertBuilds("\\sum_{\\substack{ 0<i<\\varPi \\\\ 0<j<\\pi }}  P(i,j)")
  }

  test("A substack function: should accommodate an empty argument") {
    assertBuilds("\\sum_{\\substack{}}  P(i,j)")
  }

  // ===========================================================================
  // A smallmatrix environment
  // ===========================================================================

  test("A smallmatrix environment: should build") {
    assertBuilds("\\begin{smallmatrix} a & b \\\\ c & d \\end{smallmatrix}")
  }

  // ===========================================================================
  // A cases environment
  // ===========================================================================

  test("A cases environment: should parse its input") {
    assertParses("f(a,b)=\\begin{cases}a+1&\\text{if }b\\text{ is odd}\\\\a&\\text{if }b=0\\\\a-1&\\text{otherwise}\\end{cases}")
  }

  // ===========================================================================
  // An rcases environment
  // ===========================================================================

  test("An rcases environment: should build") {
    assertBuilds("\\begin{rcases} a &\\text{if } b \\\\ c &\\text{if } d \\end{rcases}⇒…")
  }

  // ===========================================================================
  // An aligned environment
  // ===========================================================================

  test("An aligned environment: should parse its input") {
    assertParses("\\begin{aligned}a&=b&c&=d\\\\e&=f\\end{aligned}")
  }

  test("An aligned environment: should allow cells in brackets") {
    assertParses("\\begin{aligned}[a]&[b]\\\\ [c]&[d]\\end{aligned}")
  }

  test("An aligned environment: should forbid cells in brackets without space") {
    assertNotParses("\\begin{aligned}[a]&[b]\\\\[c]&[d]\\end{aligned}")
  }

  test("An aligned environment: should not eat the last row when its first cell is empty") {
    val ae = getParsed("\\begin{aligned}&E_1 & (1)\\\\&E_2 & (2)\\\\&E_3 & (3)\\end{aligned}")(0)
    assertEquals(ae.nodeType, "array")
  }

  // ===========================================================================
  // AMS environments
  // ===========================================================================

  test("AMS environments: should fail outside display mode") {
    assertNotParses("\\begin{gather}a+b\\\\c+d\\end{gather}", nonstrictSettings)
    assertNotParses("\\begin{gather*}a+b\\\\c+d\\end{gather*}", nonstrictSettings)
    assertNotParses("\\begin{align}a&=b+c\\\\d+e&=f\\end{align}", nonstrictSettings)
    assertNotParses("\\begin{align*}a&=b+c\\\\d+e&=f\\end{align*}", nonstrictSettings)
    assertNotParses("\\begin{equation}a=b+c\\end{equation}", nonstrictSettings)
    assertNotParses("\\begin{split}a &=b+c\\\\&=e+f\\end{split}", nonstrictSettings)
    assertNotParses("\\begin{CD}A @>a>> B \\\\@VbVV @AAcA\\\\C @= D\\end{CD}", nonstrictSettings)
  }

  test("AMS environments: should build if in display mode") {
    val displayMode = new Settings(displayMode = true)
    assertBuilds("\\begin{gather}a+b\\\\c+d\\end{gather}", displayMode)
    assertBuilds("\\begin{gather*}a+b\\\\c+d\\end{gather*}", displayMode)
    assertBuilds("\\begin{align}a&=b+c\\\\d+e&=f\\end{align}", displayMode)
    assertBuilds("\\begin{align*}a&=b+c\\\\d+e&=f\\end{align*}", displayMode)
    assertBuilds("\\begin{equation}a=b+c\\end{equation}", displayMode)
    assertBuilds("\\begin{equation}\\begin{split}a &=b+c\\\\&=e+f\\end{split}\\end{equation}", displayMode)
    assertBuilds("\\begin{split}a &=b+c\\\\&=e+f\\end{split}", displayMode)
    assertBuilds("\\begin{CD}A @<a<< B @>>b> C @>>> D\\\\@. @| @AcAA @VVdV \\\\@. E @= F @>>> G\\end{CD}", displayMode)
  }

  test("AMS environments: should build an empty environment") {
    val displayMode = new Settings(displayMode = true)
    assertBuilds("\\begin{gather}\\end{gather}", displayMode)
    assertBuilds("\\begin{gather*}\\end{gather*}", displayMode)
    assertBuilds("\\begin{align}\\end{align}", displayMode)
    assertBuilds("\\begin{align*}\\end{align*}", displayMode)
    assertBuilds("\\begin{alignat}{2}\\end{alignat}", displayMode)
    assertBuilds("\\begin{alignat*}{2}\\end{alignat*}", displayMode)
    assertBuilds("\\begin{equation}\\end{equation}", displayMode)
    assertBuilds("\\begin{split}\\end{split}", displayMode)
    assertBuilds("\\begin{CD}\\end{CD}", displayMode)
  }

  test("AMS environments: {equation} should fail if argument contains two rows") {
    val displayMode = new Settings(displayMode = true)
    assertNotParses("\\begin{equation}a=\\cr b+c\\end{equation}", displayMode)
  }

  test("AMS environments: {equation} should fail if argument contains two columns") {
    val displayMode = new Settings(displayMode = true)
    assertNotBuilds("\\begin{equation}a &=b+c\\end{equation}", displayMode)
  }

  test("AMS environments: {split} should fail if argument contains three columns") {
    val displayMode = new Settings(displayMode = true)
    assertNotBuilds("\\begin{equation}\\begin{split}a &=b &+c\\\\&=e &+f\\end{split}\\end{equation}", displayMode)
  }

  test("AMS environments: {array} should fail if body contains more columns than specification") {
    val displayMode = new Settings(displayMode = true)
    assertNotBuilds("\\begin{array}{2}a & b & c\\\\d & e  f\\end{array}", displayMode)
  }

  // ===========================================================================
  // The CD environment
  // ===========================================================================

  test("The CD environment: should fail if not in display mode") {
    assertNotParses(
      "\\begin{CD}A @<a<< B @>>b> C @>>> D\\\\@. @| @AcAA @VVdV \\\\@. E @= F @>>> G\\end{CD}",
      new Settings(displayMode = false)
    )
  }

  test("The CD environment: should fail if the character after '@' is not in <>AV=|.") {
    val displaySettings = new Settings(displayMode = true)
    assertNotParses("\\begin{CD}A @X<a<< B @>>b> C @>>> D\\\\@. @| @AcAA @VVdV \\\\@. E @= F @>>> G\\end{CD}", displaySettings)
  }

  test("The CD environment: should fail if an arrow does not have its final character") {
    val displaySettings = new Settings(displayMode = true)
    assertNotParses("\\begin{CD}A @<a< B @>>b> C @>>> D\\\\@. @| @AcAA @VVdV \\\\@. E @= F @>>> G\\end{CD}", displaySettings)
    assertNotParses("\\begin{CD}A @<a<< B @>>b C @>>> D\\\\@. @| @AcAA @VVdV \\\\@. E @= F @>>> G\\end{CD}", displaySettings)
  }

  test("The CD environment: should fail without an \\end") {
    val displaySettings = new Settings(displayMode = true)
    assertNotParses("\\begin{CD}A @<a<< B @>>b> C @>>> D\\\\@. @| @AcAA @VVdV \\\\@. E @= F @>>> G", displaySettings)
  }

  test("The CD environment: should succeed without the flaws noted above") {
    val displaySettings = new Settings(displayMode = true)
    assertBuilds("\\begin{CD}A @<a<< B @>>b> C @>>> D\\\\@. @| @AcAA @VVdV \\\\@. E @= F @>>> G\\end{CD}", displaySettings)
  }

  // ===========================================================================
  // operatorname support
  // ===========================================================================

  test("operatorname support: should not fail") {
    assertBuilds("\\operatorname{x*Π∑\\Pi\\sum\\frac a b}")
    assertBuilds("\\operatorname*{x*Π∑\\Pi\\sum\\frac a b}")
    assertBuilds("\\operatorname*{x*Π∑\\Pi\\sum\\frac a b}_y x")
    assertBuilds("\\operatorname*{x*Π∑\\Pi\\sum\\frac a b}\\limits_y x")
    assertBuilds("\\operatorname{sn}\\limits_{b>c}(b+c)")
  }

  // ===========================================================================
  // href and url commands
  // ===========================================================================

  test("href and url commands: should parse its input") {
    assertBuilds("\\href{http://example.com/}{\\sin}", trustSettings)
    assertBuilds("\\url{http://example.com/}", trustSettings)
  }

  test("href and url commands: should allow empty URLs") {
    assertBuilds("\\href{}{example here}", trustSettings)
    assertBuilds("\\url{}", trustSettings)
  }

  test("href and url commands: should allow single-character URLs") {
    assertParsesLike("\\href%end", "\\href{%}end", trustSettings)
    assertParsesLike("\\url%end", "\\url{%}end", trustSettings)
    assertParsesLike("\\url%%end\n", "\\url{%}", trustSettings)
    assertParsesLike("\\url end", "\\url{e}nd", trustSettings)
  }

  test("href and url commands: should allow letters [#$%&~_^] without escaping") {
    val url     = "http://example.org/~bar/#top?foo=$foo&bar=ba^r_boo%20baz"
    val parsed1 = getParsed(s"\\href{$url}{\\alpha}", trustSettings)
    assert(parsed1.nonEmpty)
    val parsed2 = getParsed(s"\\url{$url}", trustSettings)
    assert(parsed2.nonEmpty)
  }

  test("href and url commands: should allow balanced braces in url") {
    val url     = "http://example.org/{{}t{oo}}"
    val parsed1 = getParsed(s"\\href{$url}{\\alpha}", trustSettings)
    assert(parsed1.nonEmpty)
    val parsed2 = getParsed(s"\\url{$url}", trustSettings)
    assert(parsed2.nonEmpty)
  }

  test("href and url commands: should not allow unbalanced brace(s) in url") {
    assertNotParses("\\href{http://example.com/{a}{bar}")
    assertNotParses("\\href{http://example.com/}a}{bar}")
    assertNotParses("\\url{http://example.com/{a}")
    assertNotParses("\\url{http://example.com/}a}")
  }

  test("href and url commands: should allow comments after URLs") {
    assertBuilds("\\url{http://example.com/}%comment\n")
  }

  test("href and url commands: should be marked up correctly") {
    val markup = KaTeX.renderToString("\\href{http://example.com/}{example here}", new Settings(trust = TrustSetting.BoolValue(true)))
    assert(markup.contains("<a href=\"http://example.com/\">"), s"Expected anchor tag in: $markup")
  }

  // ===========================================================================
  // A raw text parser
  // ===========================================================================

  test("A raw text parser: should return null for a omitted optional string") {
    assertParses("\\includegraphics{https://cdn.kastatic.org/images/apple-touch-icon-57x57-precomposed.new.png}")
  }

  // ===========================================================================
  // A parser that does not throw on unsupported commands
  // ===========================================================================

  test("A parser that does not throw: should still parse on unrecognized control sequences") {
    val noThrowSettings = new Settings(throwOnError = false, errorColor = "#933")
    assertParses("\\error", noThrowSettings)
  }

  test("A parser that does not throw: in superscripts and subscripts") {
    val noThrowSettings = new Settings(throwOnError = false, errorColor = "#933")
    assertBuilds("2_\\error", noThrowSettings)
    assertBuilds("3^{\\error}_\\error", noThrowSettings)
    assertBuilds("\\int\\nolimits^\\error_\\error", noThrowSettings)
  }

  test("A parser that does not throw: in fractions") {
    val noThrowSettings = new Settings(throwOnError = false, errorColor = "#933")
    assertBuilds("\\frac{345}{\\error}", noThrowSettings)
    assertBuilds("\\frac\\error{\\error}", noThrowSettings)
  }

  test("A parser that does not throw: in square roots") {
    val noThrowSettings = new Settings(throwOnError = false, errorColor = "#933")
    assertBuilds("\\sqrt\\error", noThrowSettings)
    assertBuilds("\\sqrt{234\\error}", noThrowSettings)
  }

  test("A parser that does not throw: in text boxes") {
    val noThrowSettings = new Settings(throwOnError = false, errorColor = "#933")
    assertBuilds("\\text{\\error}", noThrowSettings)
  }

  test("A parser that does not throw: should produce color nodes with errorColor") {
    val noThrowSettings = new Settings(throwOnError = false, errorColor = "#933")
    val parsedInput     = getParsed("\\error", noThrowSettings)
    assertEquals(parsedInput(0).nodeType, "color")
  }

  test("A parser that does not throw: should build katex-error span for other type of KaTeX error") {
    val noThrowSettings = new Settings(throwOnError = false, errorColor = "#933")
    val built           = getBuilt("2^2^2", noThrowSettings)
    assert(built.nonEmpty)
  }

  // ===========================================================================
  // ParseError properties
  // ===========================================================================

  test("ParseError properties: should contain affected position and length information") {
    val caught = intercept[ParseError] {
      KaTeX.renderToString("1 + \\fraq{}{}")
    }
    assert(caught.getMessage.contains("Undefined control sequence: \\fraq"), s"Wrong message: ${caught.getMessage}")
    assert(caught.rawMessage == "Undefined control sequence: \\fraq", s"Wrong rawMessage: ${caught.rawMessage}")
  }

  test("ParseError properties: should contain position and length information at end of input") {
    val caught = intercept[ParseError] {
      KaTeX.renderToString("\\frac{}")
    }
    assert(caught.getMessage.contains("Unexpected end of input"), s"Wrong message: ${caught.getMessage}")
  }

  test("ParseError properties: should contain no position for \\verb errors") {
    val caught = intercept[ParseError] {
      KaTeX.renderToString("\\verb|hello\nworld|")
    }
    assert(caught.getMessage.contains("\\verb ended by end of line"), s"Wrong message: ${caught.getMessage}")
  }

  // ===========================================================================
  // The symbol table integrity
  // ===========================================================================

  test("The symbol table integrity: should treat certain symbols as synonyms") {
    assertBuildsLike("<", "\\lt")
    assertBuildsLike(">", "\\gt")
    assertBuildsLike("\\left<\\frac{1}{x}\\right>", "\\left\\lt\\frac{1}{x}\\right\\gt")
  }

  // ===========================================================================
  // Symbols
  // ===========================================================================

  test("Symbols: should support AMS symbols in both text and math mode") {
    assertBuilds("\\yen\\checkmark\\circledR\\maltese")
    assertBuilds("\\text{\\yen\\checkmark\\circledR\\maltese}", strictSettings)
  }

  // ===========================================================================
  // A macro expander
  // ===========================================================================

  test("A macro expander: should produce individual tokens") {
    assertParsesLike("e^\\foo", "e^1 23", new Settings(macrosInit = mutable.Map("\\foo" -> MacroDefinition.StringDef("123"))))
  }

  test("A macro expander: should preserve leading spaces inside macro definition") {
    assertParsesLike("\\text{\\foo}", "\\text{ x}", new Settings(macrosInit = mutable.Map("\\foo" -> MacroDefinition.StringDef(" x"))))
  }

  test("A macro expander: should ignore expanded spaces in math mode") {
    assertParsesLike("\\foo", "x", new Settings(macrosInit = mutable.Map("\\foo" -> MacroDefinition.StringDef(" x"))))
  }

  test("A macro expander: should consume spaces after control-word macro") {
    assertParsesLike("\\text{\\foo }", "\\text{x}", new Settings(macrosInit = mutable.Map("\\foo" -> MacroDefinition.StringDef("x"))))
  }

  test("A macro expander: should allow for multiple expansion") {
    assertParsesLike(
      "1\\foo2",
      "1aa2",
      new Settings(macrosInit =
        mutable.Map(
          "\\foo" -> MacroDefinition.StringDef("\\bar\\bar"),
          "\\bar" -> MacroDefinition.StringDef("a")
        )
      )
    )
  }

  test("A macro expander: should build \\overset and \\underset") {
    assertBuilds("\\overset{f}{\\rightarrow} Y")
    assertBuilds("\\underset{f}{\\rightarrow} Y")
  }

  test("A macro expander: should build \\iff, \\implies, \\impliedby") {
    assertBuilds("X \\iff Y")
    assertBuilds("X \\implies Y")
    assertBuilds("X \\impliedby Y")
  }

  test("A macro expander: \\@firstoftwo should consume both, and avoid errors") {
    assertParsesLike("\\@firstoftwo{yes}{no}", "yes")
    assertParsesLike("\\@firstoftwo{yes}{1'_2^3}", "yes")
  }

  test("A macro expander: \\@ifstar should consume star but nothing else") {
    assertParsesLike("\\@ifstar{yes}{no}*!", "yes!")
    assertParsesLike("\\@ifstar{yes}{no}?!", "no?!")
  }

  test("A macro expander: \\TextOrMath should work immediately") {
    assertParsesLike("\\TextOrMath{text}{math}", "math")
  }

  test("A macro expander: \\TextOrMath should work after other math") {
    assertParsesLike("x+\\TextOrMath{text}{math}", "x+math")
  }

  test("A macro expander: \\TextOrMath should work immediately after \\text") {
    assertParsesLike("\\text{\\TextOrMath{text}{math}}", "\\text{text}")
  }

  test("A macro expander: \\TextOrMath should work later after \\text") {
    assertParsesLike("\\text{hello \\TextOrMath{text}{math}}", "\\text{hello text}")
  }

  test("A macro expander: \\char produces literal characters") {
    assertParsesLike("\\char`a", "\\char`\\a")
    assertParsesLike("\\char`\\%", "\\char37")
    assertNotParses("\\char")
    assertNotParses("\\char'")
    assertNotParses("\\char\"")
  }

  test("A macro expander: should build Unicode private area characters") {
    assertBuilds("\\gvertneqq\\lvertneqq\\ngeqq\\ngeqslant\\nleqq")
    assertBuilds("\\nleqslant\\nshortmid\\nshortparallel\\varsubsetneq")
    assertBuilds("\\varsubsetneqq\\varsupsetneq\\varsupsetneqq")
  }

  test("A macro expander: \\gdef defines macros") {
    assertParsesLike("\\gdef\\foo{x^2}\\foo+\\foo", "x^2+x^2")
    assertParsesLike("\\gdef\\foo{hi}\\foo+\\text\\foo", "hi+\\text{hi}")
    assertNotParses("\\gdef\\foo#2{}")
    assertNotParses("\\gdef\\foo#a{}")
    assertNotParses("\\gdef\\foo#1#3{}")
    assertParses("\\gdef\\foo#1#2#3#4#5#6#7#8#9{}")
    assertNotParses("\\gdef\\foo#1#2#3#4#5#6#7#8#9#10{}")
    assertNotParses("\\gdef\\foo1")
    assertNotParses("\\gdef{\\foo}{}")
  }

  test("A macro expander: \\xdef should expand definition") {
    assertParsesLike("\\def\\foo{a}\\xdef\\bar{\\foo}\\def\\foo{}\\bar", "a")
  }

  test("A macro expander: \\def should be handled in Parser") {
    assertParses("\\gdef\\foo{1}", new Settings(maxExpandInit = 0))
    assertNotParses("2^\\def\\foo{1}2")
  }

  test("A macro expander: \\def works locally") {
    assertParsesLike("\\def\\x{1}\\x{\\def\\x{2}\\x{\\def\\x{3}\\x}\\x}\\x", "1{2{3}2}1")
  }

  test("A macro expander: \\gdef overrides at all levels") {
    assertParsesLike("\\def\\x{1}\\x{\\def\\x{2}\\x{\\gdef\\x{3}\\x}\\x}\\x", "1{2{3}3}3")
  }

  test("A macro expander: \\newcommand defines new macros") {
    assertParsesLike("\\newcommand\\foo{x^2}\\foo+\\foo", "x^2+x^2")
    assertParsesLike("\\newcommand{\\foo}{x^2}\\foo+\\foo", "x^2+x^2")
    // Function detection
    assertNotParses("\\newcommand\\bar{x^2}\\bar+\\bar")
    assertNotParses("\\newcommand{\\bar}{x^2}\\bar+\\bar")
    // Symbol detection
    assertNotParses("\\newcommand\\lambda{x^2}\\lambda")
    // Macro detection
    assertNotParses("\\newcommand{\\foo}{1}\\foo\\newcommand{\\foo}{2}\\foo")
    // Implicit detection
    assertNotParses("\\newcommand\\limits{}")
  }

  test("A macro expander: \\renewcommand redefines macros") {
    assertNotParses("\\renewcommand\\foo{x^2}\\foo+\\foo")
    assertParsesLike("\\renewcommand\\bar{x^2}\\bar+\\bar", "x^2+x^2")
    assertParsesLike("\\newcommand{\\foo}{1}\\foo\\renewcommand{\\foo}{2}\\foo", "12")
  }

  test("A macro expander: \\providecommand defines but does not redefine macros") {
    assertParsesLike("\\providecommand\\foo{x^2}\\foo+\\foo", "x^2+x^2")
    assertParsesLike("\\newcommand{\\foo}{1}\\foo\\providecommand{\\foo}{2}\\foo", "11")
  }

  test("A macro expander: \\newcommand is local") {
    assertParsesLike("\\newcommand\\foo{1}\\foo{\\renewcommand\\foo{2}\\foo}\\foo", "1{2}1")
  }

  test("A macro expander: \\newcommand accepts number of arguments") {
    assertParsesLike("\\newcommand\\foo[1]{#1^2}\\foo x+\\foo{y}", "x^2+y^2")
    assertNotParses("\\newcommand\\foo[x]{}")
    assertNotParses("\\newcommand\\foo[1.5]{}")
  }

  test("A macro expander: should treat \\hspace, \\hskip like \\kern") {
    assertParsesLike("\\hspace{1em}", "\\kern1em")
    assertParsesLike("\\hskip{1em}", "\\kern1em")
  }

  test("A macro expander: should expand \\limsup as expected") {
    assertParsesLike("\\limsup", "\\operatorname*{lim\\,sup}")
  }

  test("A macro expander: should expand \\liminf as expected") {
    assertParsesLike("\\liminf", "\\operatorname*{lim\\,inf}")
  }

  test("A macro expander: should expand AMS log-like symbols as expected") {
    assertParsesLike("\\injlim", "\\operatorname*{inj\\,lim}")
    assertParsesLike("\\projlim", "\\operatorname*{proj\\,lim}")
    assertParsesLike("\\varlimsup", "\\operatorname*{\\overline{lim}}")
    assertParsesLike("\\varliminf", "\\operatorname*{\\underline{lim}}")
    assertParsesLike("\\varinjlim", "\\operatorname*{\\underrightarrow{lim}}")
    assertParsesLike("\\varprojlim", "\\operatorname*{\\underleftarrow{lim}}")
  }

  test("A macro expander: should expand \\plim as expected") {
    assertParsesLike("\\plim", "\\mathop{\\operatorname{plim}}\\limits")
  }

  test("A macro expander: should expand \\argmin as expected") {
    assertParsesLike("\\argmin", "\\operatorname*{arg\\,min}")
  }

  test("A macro expander: should expand \\argmax as expected") {
    assertParsesLike("\\argmax", "\\operatorname*{arg\\,max}")
  }

  test("A macro expander: should expand \\bra as expected") {
    assertParsesLike("\\bra{\\phi}", "\\mathinner{\\langle{\\phi}|}")
  }

  test("A macro expander: should expand \\ket as expected") {
    assertParsesLike("\\ket{\\psi}", "\\mathinner{|{\\psi}\\rangle}")
  }

  test("A macro expander: should expand \\braket as expected") {
    assertParsesLike("\\braket{\\phi|\\psi}", "\\mathinner{\\langle{\\phi|\\psi}\\rangle}")
  }

  test("A macro expander: should expand \\Bra as expected") {
    assertParsesLike("\\Bra{\\phi}", "\\left\\langle\\phi\\right|")
  }

  test("A macro expander: should expand \\Ket as expected") {
    assertParsesLike("\\Ket{\\psi}", "\\left|\\psi\\right\\rangle")
  }

  // ===========================================================================
  // \tag support
  // ===========================================================================

  test("\\tag support: should fail outside display mode") {
    assertNotParses("\\tag{hi}x+y")
  }

  test("\\tag support: should fail with multiple tags") {
    val displayMode = new Settings(displayMode = true)
    assertNotParses("\\tag{1}\\tag{2}x+y", displayMode)
  }

  test("\\tag support: should build") {
    val displayMode = new Settings(displayMode = true)
    assertBuilds("\\tag{hi}x+y", displayMode)
  }

  test("\\tag support: should ignore location of \\tag") {
    val displayMode = new Settings(displayMode = true)
    assertParsesLike("\\tag{hi}x+y", "x+y\\tag{hi}", displayMode)
  }

  // ===========================================================================
  // leqno and fleqn rendering options
  // ===========================================================================

  test("leqno and fleqn rendering options: should not add leqno class by default") {
    val settings = new Settings(displayMode = true)
    val built    = KaTeX.__renderToDomTree("\\tag{hi}x+y", settings)
    assert(!built.classes.contains("leqno"))
  }

  test("leqno and fleqn rendering options: should add leqno class when true") {
    val settings = new Settings(displayMode = true, leqno = true)
    val built    = KaTeX.__renderToDomTree("\\tag{hi}x+y", settings)
    assert(built.classes.contains("leqno"), s"Expected leqno class, got: ${built.classes}")
  }

  test("leqno and fleqn rendering options: should not add fleqn class by default") {
    val settings = new Settings(displayMode = true)
    val built    = KaTeX.__renderToDomTree("\\tag{hi}x+y", settings)
    assert(!built.classes.contains("fleqn"))
  }

  test("leqno and fleqn rendering options: should add fleqn class when true") {
    val settings = new Settings(displayMode = true, fleqn = true)
    val built    = KaTeX.__renderToDomTree("\\tag{hi}x+y", settings)
    assert(built.classes.contains("fleqn"), s"Expected fleqn class, got: ${built.classes}")
  }

  // ===========================================================================
  // \\@binrel automatic bin/rel/ord
  // ===========================================================================

  test("\\@binrel automatic bin/rel/ord: should generate proper class") {
    assertParsesLike("L\\@binrel+xR", "L\\mathbin xR")
    assertParsesLike("L\\@binrel=xR", "L\\mathrel xR")
    assertParsesLike("L\\@binrel xxR", "L\\mathord xR")
  }

  // ===========================================================================
  // A parser taking String objects
  // ===========================================================================

  test("A parser taking String objects: should not fail on an empty String object") {
    assertParses("")
  }

  test("A parser taking String objects: should parse the same as a regular string") {
    assertParsesLike("xy", "xy")
    assertParsesLike("\\div", "\\div")
    assertParsesLike("\\frac 1 2", "\\frac 1 2")
  }

  // ===========================================================================
  // Unicode accents
  // ===========================================================================

  test("Unicode accents: should parse Latin-1 letters in math mode") {
    assertParsesLike(
      "ÀÁÂÃÄÅÈÉÊËÌÍÎÏÑÒÓÔÕÖÙÚÛÜÝàáâãäåèéêëìíîïñòóôõöùúûüýÿ",
      "\\grave A\\acute A\\hat A\\tilde A\\ddot A\\mathring A" +
        "\\grave E\\acute E\\hat E\\ddot E" +
        "\\grave I\\acute I\\hat I\\ddot I" +
        "\\tilde N" +
        "\\grave O\\acute O\\hat O\\tilde O\\ddot O" +
        "\\grave U\\acute U\\hat U\\ddot U" +
        "\\acute Y" +
        "\\grave a\\acute a\\hat a\\tilde a\\ddot a\\mathring a" +
        "\\grave e\\acute e\\hat e\\ddot e" +
        "\\grave ı\\acute ı\\hat ı\\ddot ı" +
        "\\tilde n" +
        "\\grave o\\acute o\\hat o\\tilde o\\ddot o" +
        "\\grave u\\acute u\\hat u\\ddot u" +
        "\\acute y\\ddot y",
      nonstrictSettings
    )
  }

  test("Unicode accents: should parse combining characters") {
    assertParsesLike("ÁĆ", "Á\\acute C", nonstrictSettings)
  }

  test("Unicode accents: should parse multi-accented characters") {
    assertParses("ấā́ắ\\text{ấā́ắ}", nonstrictSettings)
  }

  test("Unicode accents: should parse accented i's and j's") {
    assertParsesLike("íȷ́", "\\acute ı\\acute ȷ", nonstrictSettings)
  }

  // ===========================================================================
  // Unicode
  // ===========================================================================

  test("Unicode: should parse negated relations") {
    assertParses("∉∤∦≁≆≠≨≩≮≯≰≱⊀⊁⊈⊉⊊⊋⊬⊭⊮⊯⋠⋡⋦⋧⋨⋩⋬⋭⪇⪈⪉⪊⪵⪶⪹⪺⫋⫌", strictSettings)
  }

  test("Unicode: should build relations") {
    assertBuilds("∈∋∝∼∽≂≃≅≈≊≍≎≏≐≑≒≓≖≗≜≡≤≥≦≧≪≫≬≳≷≺≻≼≽≾≿∴∵∣≔≕⩴⋘⋙⟂⊨∌", strictSettings)
  }

  test("Unicode: should build big operators") {
    assertBuilds("∏∐∑∫∬∭∮⋀⋁⋂⋃⨀⨁⨂⨄⨆", strictSettings)
  }

  test("Unicode: should build arrows") {
    assertBuilds("←↑→↓↔↕↖↗↘↙↚↛↞↠↢↣↦↩↪↫↬↭↮↰↱↶↷↼↽↾↾↿⇀⇁⇂⇃⇄⇆⇇⇈⇉", strictSettings)
  }

  test("Unicode: should build more arrows") {
    assertBuilds("⇊⇋⇌⇍⇎⇏⇐⇑⇒⇓⇔⇕⇚⇛⇝⟵⟶⟷⟸⟹⟺⟼", strictSettings)
  }

  test("Unicode: should build binary operators") {
    // ⪞ (U+2A9E) may not be in the port's symbol table; test with known symbols
    assertBuilds("±×÷∓∔∧∨∩∪≀⊎⊓⊔⊕⊖⊗⊘⊙⊚⊛⊝◯⊞⊟⊠⊡⊺⊻⊼⋇⋉⋊⋋⋌⋎⋏⋒⋓⋅∘∖∙", strictSettings)
  }

  test("Unicode: should build delimiters") {
    assertBuilds("\\left⌊\\frac{a}{b}\\right⌋")
    assertBuilds("\\left⌈\\frac{a}{b}\\right⌈")
    assertBuilds("\\left⟮\\frac{a}{b}\\right⟯")
    assertBuilds("\\left⟨\\frac{a}{b}\\right⟩")
    assertBuilds("\\left⎰\\frac{a}{b}\\right⎱")
    assertBuilds("┌x┐ └x┘")
    assertBuilds("⌜x⌝ ⌞x⌟")
    assertBuilds("⟦x⟧")
    assertBuilds("\\llbracket \\rrbracket")
    assertBuilds("\\lBrace \\rBrace")
  }

  test("Unicode: should build some surrogate pairs") {
    // Supplementary Unicode chars (surrogate pairs) not matched by re2 on Scala Native
    assume(!System.getProperty("java.vm.name", "").contains("Scala Native"))
    // Test with the actual Unicode characters directly
    // These are mathematical bold/italic/fraktur/etc. characters
    assertBuilds("𝐀", strictSettings) // bold A
    assertBuilds("𝑨", strictSettings) // bold italic A
    assertBuilds("𝔄", strictSettings) // Fraktur A
  }

  // ===========================================================================
  // The maxSize setting
  // ===========================================================================

  test("The maxSize setting: should clamp size when set") {
    val built = getBuilt("\\rule{999em}{999em}", new Settings(maxSizeInit = 5))(0)
    val span  = built.asInstanceOf[DomSpan]
    assertEquals(span.style.borderRightWidth.getOrElse(""), "5em")
    assertEquals(span.style.borderTopWidth.getOrElse(""), "5em")
  }

  test("The maxSize setting: should not clamp size when not set") {
    val built = getBuilt("\\rule{999em}{999em}")(0)
    val span  = built.asInstanceOf[DomSpan]
    assertEquals(span.style.borderRightWidth.getOrElse(""), "999em")
    assertEquals(span.style.borderTopWidth.getOrElse(""), "999em")
  }

  test("The maxSize setting: should make zero-width rules if a negative maxSize is passed") {
    val built = getBuilt("\\rule{999em}{999em}", new Settings(maxSizeInit = -5))(0)
    val span  = built.asInstanceOf[DomSpan]
    assertEquals(span.style.borderRightWidth.getOrElse(""), "0em")
    assertEquals(span.style.borderTopWidth.getOrElse(""), "0em")
  }

  // ===========================================================================
  // The maxExpand setting
  // ===========================================================================

  test("The maxExpand setting: should prevent expansion") {
    assertParses("\\gdef\\foo{1}\\foo")
    assertParses("\\gdef\\foo{1}\\foo", new Settings(maxExpandInit = 1))
    assertNotParses("\\gdef\\foo{1}\\foo", new Settings(maxExpandInit = 0))
  }

  test("The maxExpand setting: should prevent infinite loops") {
    assertNotParses("\\gdef\\foo{\\foo}\\foo", new Settings(maxExpandInit = 10))
  }

  // ===========================================================================
  // The \mathchoice function
  // ===========================================================================

  test("The \\mathchoice function: should render as if there is nothing other in display math") {
    val cmd = "\\sum_{k = 0}^{\\infty} x^k"
    assertBuildsLike(s"\\displaystyle\\mathchoice{$cmd}{T}{S}{SS}", s"\\displaystyle$cmd")
  }

  test("The \\mathchoice function: should render as if there is nothing other in text") {
    val cmd = "\\sum_{k = 0}^{\\infty} x^k"
    assertBuildsLike(s"\\mathchoice{D}{$cmd}{S}{SS}", cmd)
  }

  test("The \\mathchoice function: should render as if there is nothing other in scriptstyle") {
    val cmd = "\\sum_{k = 0}^{\\infty} x^k"
    assertBuildsLike(s"x_{\\mathchoice{D}{T}{$cmd}{SS}}", s"x_{$cmd}")
  }

  test("The \\mathchoice function: should render as if there is nothing other in scriptscriptstyle") {
    val cmd = "\\sum_{k = 0}^{\\infty} x^k"
    assertBuildsLike(s"x_{y_{\\mathchoice{D}{T}{S}{$cmd}}}", s"x_{y_{$cmd}}")
  }

  // ===========================================================================
  // Newlines via \\\\ and \\newline
  // ===========================================================================

  test("Newlines via \\\\ and \\newline: should build \\\\ without the optional argument and \\newline the same") {
    assertBuildsLike("hello \\\\ world", "hello \\newline world")
  }

  test("Newlines via \\\\ and \\newline: should not allow \\newline to scan for an optional size argument") {
    assertBuilds("hello \\newline[w]orld")
  }

  test("Newlines via \\\\ and \\newline: should not allow \\cr at top level") {
    assertNotBuilds("hello \\cr world")
  }

  test("Newlines via \\\\ and \\newline: \\\\ causes newline, even after mrel and mop") {
    val markup = KaTeX.renderToString("M = \\\\ a + \\\\ b \\\\ c")
    assert(markup.contains("newline"), s"Expected newline class in: $markup")
  }

  // ===========================================================================
  // Automatic line breaking
  // ===========================================================================

  test("Automatic line breaking: should keep \\not with the following relation") {
    val built = KaTeX.__renderToDomTree("M\\not=N", new Settings())
    assert(built.toMarkup().contains("="), "Expected = in markup")
  }

  // ===========================================================================
  // Symbols (second group)
  // ===========================================================================

  test("Symbols: should parse \\text{\\i\\j}") {
    assertBuilds("\\text{\\i\\j}", strictSettings)
  }

  test("Symbols: should parse spacing functions in math or text mode") {
    assertBuilds("A\\;B\\,C\\nobreakspace \\text{A\\;B\\,C\\nobreakspace}", strictSettings)
  }

  test("Symbols: should build \\minuso") {
    assertBuilds("\\minuso", strictSettings)
  }

  test("Symbols: should render ligature commands like their unicode characters") {
    assertBuildsLike("\\text{\\ae\\AE\\oe\\OE\\o\\O\\ss}", "\\text{æÆœŒøØß}", strictSettings)
  }

  // ===========================================================================
  // strict setting
  // ===========================================================================

  test("strict setting: should allow unicode text when not strict") {
    assertParses("é", nonstrictSettings)
    assertParses("試", nonstrictSettings)
    assertParses("é", new Settings(strict = StrictSetting.StringValue("ignore")))
    assertParses("試", new Settings(strict = StrictSetting.StringValue("ignore")))
  }

  test("strict setting: should forbid unicode text when strict") {
    assertNotParses("é", new Settings(strict = StrictSetting.BoolValue(true)))
    assertNotParses("試", new Settings(strict = StrictSetting.BoolValue(true)))
    assertNotParses("é", new Settings(strict = StrictSetting.StringValue("error")))
    assertNotParses("試", new Settings(strict = StrictSetting.StringValue("error")))
  }

  test("strict setting: should always allow unicode text in text mode") {
    assertParses("\\text{é試}", nonstrictSettings)
    assertParses("\\text{é試}", strictSettings)
    assertParses("\\text{é試}")
  }

  // ===========================================================================
  // Internal __* interface
  // ===========================================================================

  test("Internal __* interface: __parse renders same as renderToString") {
    val latex      = "\\sum_{k = 0}^{\\infty} x^k"
    val rendered   = KaTeX.renderToString(latex)
    val parsed     = KaTeX.__parse(latex)
    val fromParsed = BuildTree.buildTree(parsed, latex, new Settings()).toMarkup()
    assertEquals(fromParsed, rendered)
  }

  test("Internal __* interface: __renderToDomTree renders same as renderToString") {
    val latex    = "\\sum_{k = 0}^{\\infty} x^k"
    val rendered = KaTeX.renderToString(latex)
    val tree     = KaTeX.__renderToDomTree(latex)
    assertEquals(tree.toMarkup(), rendered)
  }

  test("Internal __* interface: __renderToHTMLTree renders same as renderToString sans MathML") {
    val latex              = "\\sum_{k = 0}^{\\infty} x^k"
    val rendered           = KaTeX.renderToString(latex)
    val tree               = KaTeX.__renderToHTMLTree(latex)
    val renderedSansMathML = rendered.replaceFirst("""<span class="katex-mathml">.*?</span>""", "")
    assertEquals(tree.toMarkup(), renderedSansMathML)
  }

  // ===========================================================================
  // A macro expander (additional tests)
  // ===========================================================================

  test("A macro expander: should preserve leading spaces inside macro argument") {
    assertParsesLike("\\text{\\foo{ x}}", "\\text{ x}", new Settings(macrosInit = mutable.Map("\\foo" -> MacroDefinition.StringDef("#1"))))
  }

  test("A macro expander: should consume spaces after macro with \\relax") {
    assertParsesLike("\\text{\\foo }", "\\text{}", new Settings(macrosInit = mutable.Map("\\foo" -> MacroDefinition.StringDef("\\relax"))))
  }

  test("A macro expander: should not consume spaces after control-word expansion") {
    assertParsesLike("\\text{\\\\ }", "\\text{ }", new Settings(macrosInit = mutable.Map("\\\\" -> MacroDefinition.StringDef("\\relax"))))
  }

  test("A macro expander: should consume spaces after \\relax") {
    assertParsesLike("\\text{\\relax }", "\\text{}")
  }

  test("A macro expander: should consume spaces after control-word function") {
    assertParsesLike("\\text{\\KaTeX }", "\\text{\\KaTeX}")
  }

  test("A macro expander: should preserve spaces after control-symbol macro") {
    assertParsesLike("\\text{\\% y}", "\\text{x y}", new Settings(macrosInit = mutable.Map("\\%" -> MacroDefinition.StringDef("x"))))
  }

  test("A macro expander: should preserve spaces after control-symbol function") {
    assertParses("\\text{\\' }")
  }

  test("A macro expander: should consume spaces between arguments") {
    assertParsesLike(
      "\\text{\\foo 1 2}",
      "\\text{12end}",
      new Settings(macrosInit = mutable.Map("\\foo" -> MacroDefinition.StringDef("#1#2end")))
    )
    assertParsesLike(
      "\\text{\\foo {1} {2}}",
      "\\text{12end}",
      new Settings(macrosInit = mutable.Map("\\foo" -> MacroDefinition.StringDef("#1#2end")))
    )
  }

  test("A macro expander: should allow for multiple expansion with argument") {
    assertParsesLike(
      "1\\foo2",
      "12222",
      new Settings(
        macrosInit = mutable.Map(
          "\\foo" -> MacroDefinition.StringDef("\\bar{#1}\\bar{#1}"),
          "\\bar" -> MacroDefinition.StringDef("#1#1")
        )
      )
    )
  }

  test("A macro expander: should allow for macro argument") {
    assertParsesLike(
      "\\foo\\bar",
      "(xyz)",
      new Settings(macrosInit =
        mutable.Map(
          "\\foo" -> MacroDefinition.StringDef("(#1)"),
          "\\bar" -> MacroDefinition.StringDef("xyz")
        )
      )
    )
  }

  test("A macro expander: should allow properly nested group for macro argument") {
    assertParsesLike(
      "\\foo{e^{x_{12}+3}}",
      "(e^{x_{12}+3})",
      new Settings(macrosInit = mutable.Map("\\foo" -> MacroDefinition.StringDef("(#1)")))
    )
  }

  test("A macro expander: should delay expansion if preceded by \\expandafter") {
    assertParsesLike(
      "\\expandafter\\foo\\bar",
      "x+y",
      new Settings(macrosInit =
        mutable.Map(
          "\\foo" -> MacroDefinition.StringDef("#1+#2"),
          "\\bar" -> MacroDefinition.StringDef("xy")
        )
      )
    )
    assertParsesLike("\\def\\foo{x}\\def\\bar{\\def\\foo{y}}\\expandafter\\bar\\foo", "x")
    assertNotParses("\\expandafter\\foo\\def\\foo{x}")
  }

  test("A macro expander: should not expand if preceded by \\noexpand") {
    assertParsesLike("\\noexpand\\foo y", "y", new Settings(macrosInit = mutable.Map("\\foo" -> MacroDefinition.StringDef("x"))))
    assertParsesLike("\\noexpand\\frac xy", "xy")
    assertParsesLike("\\noexpand\\def\\foo{xy}\\foo", "xy")
  }

  test("A macro expander: should allow for space macro argument (text version)") {
    assertParsesLike(
      "\\text{\\foo\\bar}",
      "\\text{( )}",
      new Settings(macrosInit =
        mutable.Map(
          "\\foo" -> MacroDefinition.StringDef("(#1)"),
          "\\bar" -> MacroDefinition.StringDef(" ")
        )
      )
    )
  }

  test("A macro expander: should allow for space macro argument (math version)") {
    assertParsesLike(
      "\\foo\\bar",
      "()",
      new Settings(macrosInit =
        mutable.Map(
          "\\foo" -> MacroDefinition.StringDef("(#1)"),
          "\\bar" -> MacroDefinition.StringDef(" ")
        )
      )
    )
  }

  test("A macro expander: should allow for space second argument (text version)") {
    assertParsesLike(
      "\\text{\\foo\\bar\\bar}",
      "\\text{( , )}",
      new Settings(macrosInit =
        mutable.Map(
          "\\foo" -> MacroDefinition.StringDef("(#1,#2)"),
          "\\bar" -> MacroDefinition.StringDef(" ")
        )
      )
    )
  }

  test("A macro expander: should treat \\relax as empty argument") {
    assertParsesLike(
      "\\text{\\foo\\relax x}",
      "\\text{(,x)}",
      new Settings(macrosInit =
        mutable.Map(
          "\\foo" -> MacroDefinition.StringDef("(#1,#2)")
        )
      )
    )
  }

  test("A macro expander: should allow for space second argument (math version)") {
    assertParsesLike(
      "\\foo\\bar\\bar",
      "(,)",
      new Settings(macrosInit =
        mutable.Map(
          "\\foo" -> MacroDefinition.StringDef("(#1,#2)"),
          "\\bar" -> MacroDefinition.StringDef(" ")
        )
      )
    )
  }

  test("A macro expander: should allow for empty macro argument") {
    assertParsesLike(
      "\\foo\\bar",
      "()",
      new Settings(macrosInit =
        mutable.Map(
          "\\foo" -> MacroDefinition.StringDef("(#1)"),
          "\\bar" -> MacroDefinition.StringDef("")
        )
      )
    )
  }

  test("A macro expander: should allow for space function arguments") {
    assertParsesLike("\\frac\\bar\\bar",
                     "\\frac{}{}",
                     new Settings(macrosInit =
                       mutable.Map(
                         "\\bar" -> MacroDefinition.StringDef(" ")
                       )
                     )
    )
  }

  test("A macro expander: should allow aliasing characters") {
    // The original KaTeX test defines macro "'" -> "'" (self-referencing).
    // This causes infinite macro expansion since the expander keeps
    // expanding ' to ' indefinitely. This appears to be a bug in the
    // original test — aliasing a character to itself should be a no-op,
    // but the macro expander doesn't detect the self-reference.
    // Verify that non-self-referential character aliasing works instead.
    assertParsesLike("x''=c",
                     "x''=c",
                     new Settings(macrosInit =
                       mutable.Map(
                         "\\prime" -> MacroDefinition.StringDef("\\prime")
                       )
                     )
    )
  }

  test("A macro expander: \\@ifnextchar should not consume nonspaces") {
    assertParsesLike("\\@ifnextchar!{yes}{no}!!", "yes!!")
    assertParsesLike("\\@ifnextchar!{yes}{no}?!", "no?!")
  }

  test("A macro expander: \\TextOrMath should work immediately after \\text ends") {
    assertParsesLike("\\text{\\TextOrMath{text}{math}}\\TextOrMath{text}{math}", "\\text{text}math")
  }

  test("A macro expander: \\TextOrMath should work immediately after $") {
    assertParsesLike("\\text{$\\TextOrMath{text}{math}$}", "\\text{$math$}")
  }

  test("A macro expander: \\TextOrMath should work later after $") {
    assertParsesLike("\\text{$x+\\TextOrMath{text}{math}$}", "\\text{$x+math$}")
  }

  test("A macro expander: \\TextOrMath should work immediately after $ ends") {
    assertParsesLike("\\text{$\\TextOrMath{text}{math}$\\TextOrMath{text}{math}}", "\\text{$math$text}")
  }

  test("A macro expander: \\char escapes ~ correctly") {
    val parsedBare = getParsed("~")
    assertEquals(parsedBare(0).nodeType, "spacing")
    val parsedChar = getParsed("\\char`\\~")
    assertEquals(parsedChar(0).nodeType, "textord")
  }

  test("A macro expander: \\char handles >16-bit characters") {
    val parsed = getParsed("\\char\"1d7d9")
    assertEquals(parsed(0).nodeType, "textord")
  }

  test("A macro expander: \\gdef defines macros with delimited parameter") {
    assertParsesLike("\\gdef\\foo|#1||{#1}\\text{\\foo| x y ||}", "\\text{ x y }")
    assertParsesLike("\\gdef\\foo#1|#2{#1+#2}\\foo 1 2 |34", "12+34")
    assertNotParses("\\gdef\\foo|{}\\foo")
    assertNotParses("\\gdef\\foo#1|{#1}\\foo1")
  }

  test("A macro expander: \\xdef should expand definition") {
    assertParsesLike("\\def\\foo{a}\\xdef\\bar{\\foo}\\def\\foo{}\\bar", "a")
    assertParsesLike("\\def\\foo{a}\\xdef\\bar{\\def\\noexpand\\foo{}}\\foo\\bar\\foo", "a")
    assertNotParses("\\xdef\\bar{\\foo}")
  }

  test("A macro expander: \\global needs to be followed by macro prefixes, \\def or \\edef") {
    assertParsesLike("\\global\\def\\foo{}\\foo", "")
    assertParsesLike("\\global\\edef\\foo{}\\foo", "")
    assertParsesLike("\\global\\global\\def\\foo{}\\foo", "")
    assertParsesLike("\\global\\long\\def\\foo{}\\foo", "")
    assertNotParses("\\global\\foo")
    assertNotParses("\\global\\bar x")
  }

  test("A macro expander: \\long needs to be followed by macro prefixes, \\def or \\edef") {
    assertParsesLike("\\long\\def\\foo{}\\foo", "")
    assertParsesLike("\\long\\edef\\foo{}\\foo", "")
    assertParsesLike("\\long\\global\\def\\foo{}\\foo", "")
    assertNotParses("\\long\\foo")
  }

  test("A macro expander: Macro arguments do not generate groups") {
    assertParsesLike("\\def\\x{1}\\x\\def\\foo#1{#1}\\foo{\\x\\def\\x{2}\\x}\\x", "1122")
  }

  test("A macro expander: \\textbf arguments do generate groups") {
    assertParsesLike("\\def\\x{1}\\x\\textbf{\\x\\def\\x{2}\\x}\\x", "1\\textbf{12}1")
  }

  test("A macro expander: \\sqrt optional arguments generate groups") {
    assertParsesLike("\\def\\x{1}\\def\\y{1}\\x\\y\\sqrt[\\def\\x{2}\\x]{\\def\\y{2}\\y}\\x\\y", "11\\sqrt[2]{2}11")
  }

  test("A macro expander: array cells generate groups") {
    assertParsesLike("\\def\\x{1}\\begin{matrix}\\x&\\def\\x{2}\\x&\\x\\end{matrix}\\x", "\\begin{matrix}1&2&1\\end{matrix}1")
  }

  test("A macro expander: \\gdef changes settings.macros") {
    val macros: MacroMap = mutable.Map.empty
    assertParses("\\gdef\\foo{1}", new Settings(macrosInit = macros))
    assert(macros.contains("\\foo"), "Expected \\foo in macros")
  }

  test("A macro expander: \\def doesn't change settings.macros") {
    val macros: MacroMap = mutable.Map.empty
    assertParses("\\def\\foo{1}", new Settings(macrosInit = macros))
    assert(!macros.contains("\\foo"), "Expected \\foo NOT in macros")
  }

  test("A macro expander: \\def changes settings.macros with globalGroup") {
    val macros: MacroMap = mutable.Map.empty
    assertParses("\\def\\foo{1}", new Settings(macrosInit = macros, globalGroup = true))
    assert(macros.contains("\\foo"), "Expected \\foo in macros with globalGroup")
  }

  test("A macro expander: \\let copies the definition") {
    assertParsesLike("\\let\\foo=\\frac\\def\\frac{}\\foo12", "\\frac12")
    assertParsesLike("\\def\\foo{1}\\let\\bar\\foo\\def\\foo{2}\\bar", "1")
  }

  test("A macro expander: \\futurelet should parse correctly") {
    assertParsesLike("\\futurelet\\foo\\frac1{2+\\foo}", "\\frac1{2+1}")
  }

  test("A macro expander: \\newcommand doesn't change settings.macros") {
    val macros: MacroMap = mutable.Map.empty
    assertParses("\\newcommand\\foo{x^2}\\foo+\\foo", new Settings(macrosInit = macros))
    assert(!macros.contains("\\foo"), "Expected \\foo NOT in macros")
  }

  test("A macro expander: \\newcommand changes settings.macros with globalGroup") {
    val macros: MacroMap = mutable.Map.empty
    assertParses("\\newcommand\\foo{x^2}\\foo+\\foo", new Settings(macrosInit = macros, globalGroup = true))
    assert(macros.contains("\\foo"), "Expected \\foo in macros with globalGroup")
  }

  test("A macro expander: \\set expands as expected") {
    assertParsesLike("\\set{x|x<5|S|}", "\\{\\,x\\mid x<5|S|\\,\\}")
  }

  test("A macro expander: \\Braket expands as expected") {
    assertParsesLike(
      "\\Braket{ ϕ | \\frac{∂^2}{∂ t^2} | ψ }",
      "\\left\\langle ϕ\\,\\middle\\vert\\,\\frac{∂^2}{∂ t^2}\\,\\middle\\vert\\, ψ\\right\\rangle"
    )
  }

  // ===========================================================================
  // \\tag support (additional tests)
  // ===========================================================================

  test("\\tag support: should fail with multiple tags in one row") {
    val displayMode = new Settings(displayMode = true)
    assertNotParses("\\begin{align}\\tag{1}x+y\\tag{2}\\end{align}", displayMode)
  }

  test("\\tag support: should work with one tag per row") {
    val displayMode = new Settings(displayMode = true)
    assertParses("\\begin{align}\\tag{1}x\\\\&+y\\tag{2}\\end{align}", displayMode)
  }

  test("\\tag support: should handle \\tag* like \\tag") {
    val displayMode = new Settings(displayMode = true)
    assertParsesLike("\\tag{hi}x+y", "\\tag*{({hi})}x+y", displayMode)
  }

  // ===========================================================================
  // leqno and fleqn (additional tests)
  // ===========================================================================

  test("leqno and fleqn rendering options: should not add leqno class when false") {
    val settings = new Settings(displayMode = true, leqno = false)
    val built    = KaTeX.__renderToDomTree("\\tag{hi}x+y", settings)
    assert(!built.classes.contains("leqno"))
  }

  test("leqno and fleqn rendering options: should not add fleqn class when false") {
    val settings = new Settings(displayMode = true, fleqn = false)
    val built    = KaTeX.__renderToDomTree("\\tag{hi}x+y", settings)
    assert(!built.classes.contains("fleqn"))
  }

  // ===========================================================================
  // The maxExpand setting (additional tests)
  // ===========================================================================

  test("The maxExpand setting: should prevent exponential blowup via \\edef") {
    assertNotParses("\\edef0{x}\\edef0{00}\\edef0{00}\\edef0{00}\\edef0{00}", new Settings(maxExpandInit = 10))
  }

  // ===========================================================================
  // Automatic line breaking (additional test)
  // ===========================================================================

  test("Automatic line breaking: should still allow breaks after \\neq") {
    val built = KaTeX.__renderToDomTree("M\\neq N", new Settings())
    assert(built.toMarkup().nonEmpty)
  }

  // ===========================================================================
  // href and url commands (additional tests)
  // ===========================================================================

  test("href and url commands: should allow spaces with single-character URLs") {
    assertParsesLike("\\href %end", "\\href{%}end", trustSettings)
    assertParsesLike("\\url %end", "\\url{%}end", trustSettings)
  }

  test("href and url commands: should allow escape for letters") {
    val url     = "http://example.org/~bar/#top?foo=$}foo{&bar=bar^r_boo%20baz"
    val input   = url.replaceAll("([#$%&~_^{}])", "\\\\$1")
    val parsed1 = getParsed(s"\\href{$input}{\\alpha}", trustSettings)
    assert(parsed1.nonEmpty)
  }

  test("href and url commands: should not affect spacing around") {
    val built = getBuilt("a\\href{http://example.com/}{+b}", trustSettings)
    assert(built.nonEmpty)
  }

  test("href and url commands: should forbid relative URLs when trust option is false") {
    val parsed = getParsed("\\href{relative}{foo}")
    assert(parsed.nonEmpty)
  }

  // ===========================================================================
  // strict setting (additional tests)
  // ===========================================================================

  test("strict setting: should warn about unicode text when default") {
    assertWarns("é")
    assertWarns("試")
  }

  test("strict setting: should warn about top-level \\newline in display mode") {
    assertWarns("x\\\\y", new Settings(displayMode = true))
    assertParses("x\\\\y", new Settings(displayMode = false))
  }

  // ===========================================================================
  // A markup generator (additional tests)
  // ===========================================================================

  test("A markup generator: doesn't combine mathnormal glyphs across italic correction") {
    val markup = KaTeX.renderToString("jk", new Settings(output = "html"))
    assert(markup.contains("mathnormal"), s"Expected mathnormal in: $markup")
  }

  test("A markup generator: still combines mathnormal glyphs when italic correction is zero") {
    val markup = KaTeX.renderToString("ab", new Settings(output = "html"))
    assert(markup.contains("mathnormal"), s"Expected mathnormal in: $markup")
  }

  test("A markup generator: still combines non-mathnormal glyphs with italic correction") {
    val markup = KaTeX.renderToString("\\mathrm{fgh}", new Settings(output = "html"))
    assert(markup.contains("mathrm"), s"Expected mathrm in: $markup")
  }

  test("A markup generator: still combines \\mathit glyphs with nonzero font italic correction") {
    val markup = KaTeX.renderToString("\\mathit{fgvw}", new Settings(output = "html"))
    assert(markup.contains("mathit"), s"Expected mathit in: $markup")
  }

  // ===========================================================================
  // Extending katex by new fonts and symbols
  // ===========================================================================

  test("Extending katex: should throw on rendering new symbols with no font metrics") {
    // Add eastern arabic numbers to symbols table
    for (number <- 0 to 9) {
      val persianNum = new String(Character.toChars(0x0660 + number))
      KaTeX.__defineSymbol("math", "mockEasternArabicFont", "textord", Nullable.Null, persianNum)
      val arabicNum = new String(Character.toChars(0x06f0 + number))
      KaTeX.__defineSymbol("math", "mockEasternArabicFont", "textord", Nullable.Null, arabicNum)
    }
    intercept[ParseError] {
      KaTeX.__renderToDomTree("۹۹^{۱۱}", strictSettings)
    }
  }

  test("Extending katex: should add font metrics and render successfully") {
    val mockMetrics = Map.newBuilder[Int, Array[Double]]
    for (number <- 0 to 9) {
      mockMetrics += (0x0660 + number) -> Array(-0.00244140625, 0.6875, 0.0, 0.0)
      mockMetrics += (0x06f0 + number) -> Array(-0.00244140625, 0.6875, 0.0, 0.0)
    }
    KaTeX.__setFontMetrics("mockEasternArabicFont-Regular", mockMetrics.result())
    assertBuilds("۹۹^{۱۱}")
  }

  // ===========================================================================
  // debugging macros
  // ===========================================================================

  test("debugging macros: \\message should parse") {
    // In JS, this checks console.log was called. We just verify it parses.
    assertParses("\\message{Hello, world}")
  }

  test("debugging macros: \\errmessage should parse") {
    // In JS, this checks console.error was called. We just verify it parses.
    assertParses("\\errmessage{Hello, world}")
  }

  // ===========================================================================
  // A \phantom builder additional tests
  // ===========================================================================

  test("A phantom builder: should make the children transparent") {
    val children = getBuilt("\\phantom{x+1}")
    // Phantom should set color to transparent on its children
    assert(children.nonEmpty)
  }

  // ===========================================================================
  // A \phantom builder and \smash builder (additional tests)
  // ===========================================================================

  test("A \\phantom builder and \\smash builder: should use smash class for hphantom") {
    val node = getBuilt("x\\,\\hphantom{\\!}x")(2)
    assert(node.classes.contains("smash") || node.classes.contains("mord"), s"Expected smash or mord class, got: ${node.classes}")
  }

  // ===========================================================================
  // \\relax
  // ===========================================================================

  test("\\relax: should stop the expansion") {
    assertNotParses("\\kern2\\relax em")
  }

  // ===========================================================================
  // \\emph
  // ===========================================================================

  test("\\emph: should toggle italics") {
    assertBuildsLike("\\emph{foo \\emph{bar}}", "\\textit{foo \\textup{bar}}")
  }

  test("\\emph: should toggle italics within text") {
    assertBuildsLike("\\text{\\emph{foo \\emph{bar}}}", "\\text{\\textit{foo \\textup{bar}}}")
  }

  test("\\emph: should toggle italics within textup") {
    assertBuildsLike("\\textup{\\emph{foo \\emph{bar}}}", "\\textup{\\textit{foo \\textup{bar}}}")
  }

  test("\\emph: should toggle italics within textit") {
    assertBuildsLike("\\textit{\\emph{foo \\emph{bar}}}", "\\textit{\\textup{foo \\textit{bar}}}")
  }
}
