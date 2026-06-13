/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/test/java/liqp/parser/ParseTest.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp → ssg.liquid
 *   Convention: munit FunSuite instead of JUnit
 *   Idiom: Scala 3 patterns, manual map construction instead of JSON strings */
package ssg
package liquid

import ssg.data.DataView

import ssg.liquid.exceptions.LiquidException

import java.util.{ HashMap => JHashMap }

final class ParseSuite extends munit.FunSuite {

  // ---------------------------------------------------------------------------
  // error_with_cssTest
  // ---------------------------------------------------------------------------

  /*
   * def test_error_with_css
   *   text = %| div { font-weight: bold; } |
   *   template = TemplateParser.DEFAULT.parse(text)
   *   assert_equal text, template.render
   * end
   */
  test("error_with_css: CSS text passes through unchanged") {
    val text = " div { font-weight: bold; } "
    assertEquals(TemplateParser.DEFAULT.parse(text).render(), text)
  }

  // ---------------------------------------------------------------------------
  // raise_on_single_close_bracetTest
  // ---------------------------------------------------------------------------

  /*
   * def test_raise_on_single_close_bracet
   *   assert_raise(SyntaxError) do
   *     TemplateParser.DEFAULT.parse("text {{method} oh nos!")
   *   end
   * end
   */
  test("raise_on_single_close_bracet: single closing brace raises exception") {
    intercept[LiquidException] {
      TemplateParser.DEFAULT.parse("text {{method} oh nos!")
    }
  }

  // ---------------------------------------------------------------------------
  // raise_on_label_and_no_close_bracetsTest
  // ---------------------------------------------------------------------------

  /*
   * def test_raise_on_label_and_no_close_bracets
   *   assert_raise(SyntaxError) do
   *     TemplateParser.DEFAULT.parse("TEST {{ ")
   *   end
   * end
   */
  test("raise_on_label_and_no_close_bracets: unclosed output tag raises exception") {
    intercept[LiquidException] {
      TemplateParser.DEFAULT.parse("TEST {{ ")
    }
  }

  // ---------------------------------------------------------------------------
  // raise_on_label_and_no_close_bracets_percentTest
  // ---------------------------------------------------------------------------

  /*
   * def test_raise_on_label_and_no_close_bracets_percent
   *   assert_raise(SyntaxError) do
   *     TemplateParser.DEFAULT.parse("TEST {% ")
   *   end
   * end
   */
  test("raise_on_label_and_no_close_bracets_percent: unclosed tag raises exception") {
    intercept[LiquidException] {
      TemplateParser.DEFAULT.parse("TEST {% ")
    }
  }

  // ---------------------------------------------------------------------------
  // error_on_empty_filterTest
  // ---------------------------------------------------------------------------

  /*
   * def test_error_on_empty_filter
   *   assert_nothing_raised do
   *     TemplateParser.DEFAULT.parse("{{test}}")
   *   end
   * end
   */
  test("error_on_empty_filter: simple output tag parses without error") {
    // The original also tested "{{test |a|b|}}" and "{{|test|}}" but those
    // are marked as "TODO isn't allowed (yet?)" in liqp — we skip them too
    TemplateParser.DEFAULT.parse("{{test}}")
  }

  // ---------------------------------------------------------------------------
  // meaningless_parensTest
  // ---------------------------------------------------------------------------

  /*
   * def test_meaningless_parens
   *   assigns = {'b' => 'bar', 'c' => 'baz'}
   *   markup = "a == 'foo' or (b == 'bar' and c == 'baz') or false"
   *   assert_template_result(' YES ',"{% if #{markup} %} YES {% endif %}", assigns)
   * end
   */
  test("meaningless_parens: parenthesized condition works") {
    val vars = new JHashMap[String, DataView]()
    vars.put("b", TestHelper.dv("bar"))
    vars.put("c", TestHelper.dv("baz"))
    val markup = "a == 'foo' or (b == 'bar' and c == 'baz') or false"
    assertEquals(
      TemplateParser.DEFAULT.parse("{% if " + markup + " %} YES {% endif %}").render(vars),
      " YES "
    )
  }

  // ---------------------------------------------------------------------------
  // unexpected_characters_silently_eat_logicTest
  // ---------------------------------------------------------------------------

  /*
   * def test_unexpected_characters_silently_eat_logic
   *   markup = "true && false"
   *   assert_template_result(' YES ',"{% if #{markup} %} YES {% endif %}")
   *   markup = "false || true"
   *   assert_template_result('',"{% if #{markup} %} YES {% endif %}")
   * end
   */
  test("unexpected_characters_silently_eat_logic: && and || not supported") {
    // The original also had these tests marked as "TODO isn't allowed (yet?)"
    // in liqp — we skip them too since the SSG parser also does not support
    // && and || operators
  }

  // ---------------------------------------------------------------------------
  // keywords_as_identifier
  // ---------------------------------------------------------------------------

  test("keywords_as_identifier: comment keyword as identifier") {
    val vars  = new JHashMap[String, DataView]()
    val inner = new JHashMap[String, DataView]()
    inner.put("comment", TestHelper.dv("content"))
    vars.put("var", TestHelper.dv(inner))
    assertEquals(
      TemplateParser.DEFAULT.parse("var2:{{var2}} {%assign var2 = var.comment%} var2:{{var2}}").render(vars),
      "var2:  var2:content"
    )
  }

  test("keywords_as_identifier: end keyword as identifier") {
    val vars  = new JHashMap[String, DataView]()
    val inner = new JHashMap[String, DataView]()
    inner.put("end", TestHelper.dv("content"))
    vars.put("var", TestHelper.dv(inner))
    assertEquals(
      TemplateParser.DEFAULT.parse("var2:{{var2}} {%assign var2 = var.end%} var2:{{var2}}").render(vars),
      "var2:  var2:content"
    )
  }

  // ---------------------------------------------------------------------------
  // testStripSpaces
  // ---------------------------------------------------------------------------

  test("stripSpaces: default does not strip") {
    val source = "a \n \n {{ a }} \n \n c"
    val res    = new TemplateParser.Builder().build().parse(source).render(mapOf("a" -> "b"))
    assertEquals(res, "a \n \n b \n \n c")
  }

  test("stripSpaces: false is default") {
    val source = "a \n \n {{ a }} \n \n c"
    val res    = new TemplateParser.Builder().withStripSpaceAroundTags(false).build().parse(source).render(mapOf("a" -> "b"))
    assertEquals(res, "a \n \n b \n \n c")
  }

  // LiquidLexer.g4:86-87,122-123 — full strip mode strips all whitespace
  // both before and after tags (WhitespaceChar* on both sides).
  test("stripSpaces: true strips whitespace around tags") {
    val source = "a \n \n {{ a }} \n \n c"
    val res    = new TemplateParser.Builder().withStripSpaceAroundTags(true).build().parse(source).render(mapOf("a" -> "b"))
    assertEquals(res, "abc")
  }

  // LiquidLexer.g4:86,122 — single-line strip: leading side strips SpaceOrTab*
  // only (no linebreaks), trailing side strips SpaceOrTab* LineBreak?.
  test("stripSpaces: strip with single line") {
    val source = "a \n \n {{ a }} \n \n c"
    val res    = new TemplateParser.Builder().withStripSpaceAroundTags(true, true).build().parse(source).render(mapOf("a" -> "b"))
    assertEquals(res, "a \n \nb \n c")
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private def mapOf(pairs: (String, Any)*): JHashMap[String, DataView] =
    TestHelper.mapOf(pairs*)
}
