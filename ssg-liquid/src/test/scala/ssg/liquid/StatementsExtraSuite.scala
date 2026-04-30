/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

/** Gap-fill tests ported from liqp's StatementsTest.java — 11 missing tests. */
final class StatementsExtraSuite extends munit.FunSuite {

  test("statement: true eql true") {
    assertEquals(
      Template.parse(" {% if true == true %} true {% else %} false {% endif %} ").render(),
      "  true  "
    )
  }

  test("statement: true not eql true") {
    assertEquals(
      Template.parse(" {% if true != true %} true {% else %} false {% endif %} ").render(),
      "  false  "
    )
  }

  test("statement: 0 > 0 is false") {
    assertEquals(
      Template.parse(" {% if 0 > 0 %} true {% else %} false {% endif %} ").render(),
      "  false  "
    )
  }

  test("statement: 1 > 0 is true") {
    assertEquals(
      Template.parse(" {% if 1 > 0 %} true {% else %} false {% endif %} ").render(),
      "  true  "
    )
  }

  test("statement: 0 < 1 is true") {
    assertEquals(
      Template.parse(" {% if 0 < 1 %} true {% else %} false {% endif %} ").render(),
      "  true  "
    )
  }

  test("statement: 0 <= 0 is true") {
    assertEquals(
      Template.parse(" {% if 0 <= 0 %} true {% else %} false {% endif %} ").render(),
      "  true  "
    )
  }

  test("statement: null <= 0 involving nil") {
    assertEquals(
      TemplateParser.DEFAULT_JEKYLL.parse(" {% if null <= 0 %} true {% else %} false {% endif %} ").render(),
      "  false  "
    )
    assertEquals(
      TemplateParser.DEFAULT_JEKYLL.parse(" {% if 0 <= null %} true {% else %} false {% endif %} ").render(),
      "  false  "
    )
  }

  test("statement: 0 >= 0 is true") {
    assertEquals(
      Template.parse(" {% if 0 >= 0 %} true {% else %} false {% endif %} ").render(),
      "  true  "
    )
  }

  // SSG: `== empty` comparison for collections not yet supported
  test("statement: is collection empty".fail) {
    val vars = TestHelper.mapOf("array" -> TestHelper.listOf())
    assertEquals(
      Template.parse(" {% if array == empty %} true {% else %} false {% endif %} ").render(vars),
      "  true  "
    )
  }

  test("statement: is not collection empty") {
    val vars = TestHelper.mapOf(
      "array" -> TestHelper.listOf(
        java.lang.Integer.valueOf(1),
        java.lang.Integer.valueOf(2),
        java.lang.Integer.valueOf(3)
      )
    )
    assertEquals(
      Template.parse(" {% if array == empty %} true {% else %} false {% endif %} ").render(vars),
      "  false  "
    )
  }

  test("statement: nil/null equality") {
    val vars = TestHelper.mapOf("var" -> null)
    assertEquals(
      Template.parse(" {% if var == nil %} true {% else %} false {% endif %} ").render(vars),
      "  true  "
    )
    assertEquals(
      Template.parse(" {% if var == null %} true {% else %} false {% endif %} ").render(vars),
      "  true  "
    )
    // not nil
    val vars2 = TestHelper.mapOf("var" -> java.lang.Integer.valueOf(1))
    assertEquals(
      Template.parse(" {% if var != nil %} true {% else %} false {% endif %} ").render(vars2),
      "  true  "
    )
    assertEquals(
      Template.parse(" {% if var != null %} true {% else %} false {% endif %} ").render(vars2),
      "  true  "
    )
  }
}
