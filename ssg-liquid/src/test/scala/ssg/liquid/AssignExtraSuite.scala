/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

/** Gap-fill tests ported from liqp's tags/AssignTest.java — 5 missing tests. */
final class AssignExtraSuite extends munit.FunSuite {

  test("assign: multiple filters") {
    // https://github.com/bkiers/Liqp/issues/84
    assertEquals(
      Template.parse("{% assign v = 1 | minus: 10 | plus: 5 %}{{v}}").render(),
      "-4"
    )
  }

  test("assign: hyphenated variable") {
    assertEquals(
      Template.parse("{% assign oh-my = 'godz' %}{{ oh-my }}").render(),
      "godz"
    )
  }

  test("assign: assign in template") {
    val vars = TestHelper.mapOf("var" -> "content")
    assertEquals(
      Template.parse("var2:{{var2}} {%assign var2 = var%} var2:{{var2}}").render(vars),
      "var2:  var2:content"
    )
  }

  test("assign: hyphenated assign") {
    val vars = TestHelper.mapOf("a-b" -> "1")
    assertEquals(
      Template.parse("a-b:{{a-b}} {%assign a-b = 2 %}a-b:{{a-b}}").render(vars),
      "a-b:1 a-b:2"
    )
  }

  test("assign: variable named offset") {
    assertEquals(
      Template.parse("{% assign offset = 3 %}{{offset}}").render(),
      "3"
    )
  }
}
