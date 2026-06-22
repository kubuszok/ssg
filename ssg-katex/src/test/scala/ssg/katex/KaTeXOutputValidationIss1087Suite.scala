/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Test for ISS-1087: KaTeXOptions.output / Settings.output is an unvalidated
 * String. A typo like "mathML" or "HTML" silently falls through to the
 * htmlAndMathml branch in BuildTree with no error.
 *
 * Upstream KaTeX declares output as an enum in its settings schema
 * (original-src/katex/src/Settings.ts:117-121):
 *   output: { type: {enum: ["htmlAndMathml", "html", "mathml"]} }
 *
 * The fix validates output at the render consumption point and throws a
 * clear error for invalid values.
 *
 * Expected values:
 *   - Invalid output ("mathML", "HTML", "") must throw IllegalArgumentException
 *   - output="html" produces HTML-only markup (no <math MathML element)
 *   - output="mathml" produces MathML-only markup (<math element, no katex HTML span content)
 *   - output="htmlAndMathml" (default) produces both HTML and MathML
 */
package ssg
package katex

class KaTeXOutputValidationIss1087Suite extends munit.FunSuite {

  // --- Invalid output values must throw ---

  test("invalid output 'mathML' (case typo) throws IllegalArgumentException via KaTeXOptions") {
    val ex = intercept[IllegalArgumentException] {
      KaTeX.renderToString("x^2", KaTeXOptions(output = "mathML"))
    }
    assert(
      ex.getMessage.contains("mathML"),
      s"Error message should mention the bad value 'mathML' but got: ${ex.getMessage}"
    )
    assert(
      ex.getMessage.contains("htmlAndMathml") || ex.getMessage.contains("html"),
      s"Error message should mention valid values but got: ${ex.getMessage}"
    )
  }

  test("invalid output 'HTML' (case typo) throws IllegalArgumentException via KaTeXOptions") {
    val ex = intercept[IllegalArgumentException] {
      KaTeX.renderToString("x^2", KaTeXOptions(output = "HTML"))
    }
    assert(
      ex.getMessage.contains("HTML"),
      s"Error message should mention the bad value 'HTML' but got: ${ex.getMessage}"
    )
  }

  test("invalid output '' (empty string) throws IllegalArgumentException via Settings") {
    val ex = intercept[IllegalArgumentException] {
      KaTeX.renderToString("x^2", new Settings(output = ""))
    }
    assert(
      ex.getMessage.contains("\"\"") || ex.getMessage.contains("''"),
      s"Error message should mention the empty bad value but got: ${ex.getMessage}"
    )
  }

  test("invalid output 'foo' throws IllegalArgumentException via Settings") {
    val ex = intercept[IllegalArgumentException] {
      KaTeX.renderToString("x^2", new Settings(output = "foo"))
    }
    assert(
      ex.getMessage.contains("foo"),
      s"Error message should mention the bad value 'foo' but got: ${ex.getMessage}"
    )
  }

  test("invalid output via renderToHTMLTree also throws") {
    val ex = intercept[IllegalArgumentException] {
      KaTeX.renderToHTMLTree("x^2", new Settings(output = "invalid"))
    }
    assert(
      ex.getMessage.contains("invalid"),
      s"Error message should mention the bad value 'invalid' but got: ${ex.getMessage}"
    )
  }

  // --- Valid output values must still render correctly ---

  test("output='html' produces HTML-only markup (no MathML <math element)") {
    val markup = KaTeX.renderToString("x^2", KaTeXOptions(output = "html"))
    assert(
      markup.contains("katex"),
      s"html output should contain 'katex' class but got: $markup"
    )
    assert(
      !markup.contains("<math"),
      s"html output should NOT contain MathML <math element but got: $markup"
    )
  }

  test("output='mathml' produces MathML-only markup (contains <math element)") {
    val markup = KaTeX.renderToString("x^2", KaTeXOptions(output = "mathml"))
    assert(
      markup.contains("<math"),
      s"mathml output should contain <math element but got: $markup"
    )
  }

  test("output='htmlAndMathml' (default) produces both HTML and MathML") {
    val markup = KaTeX.renderToString("x^2", KaTeXOptions(output = "htmlAndMathml"))
    assert(
      markup.contains("katex"),
      s"htmlAndMathml output should contain 'katex' class but got: $markup"
    )
    assert(
      markup.contains("<math"),
      s"htmlAndMathml output should contain MathML <math element but got: $markup"
    )
  }

  test("default output (no explicit value) produces both HTML and MathML") {
    val markup = KaTeX.renderToString("x^2")
    assert(
      markup.contains("katex"),
      s"default output should contain 'katex' class but got: $markup"
    )
    assert(
      markup.contains("<math"),
      s"default output should contain MathML <math element but got: $markup"
    )
  }
}
