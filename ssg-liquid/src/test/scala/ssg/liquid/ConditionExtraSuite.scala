/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

/** Gap-fill tests ported from liqp's ConditionTest.java — 4 missing tests.
  *
  * Tests that exercise condition expressions via template rendering (since SSG doesn't expose the internal node API directly).
  */
final class ConditionExtraSuite extends munit.FunSuite {

  test("condition: contains works on strings") {
    // assert_evalutes_true "'bob'", 'contains', "'o'"
    assertEquals(Template.parse("{% if 'bob' contains 'o' %}true{% else %}false{% endif %}").render(), "true")
    assertEquals(Template.parse("{% if 'bob' contains 'b' %}true{% else %}false{% endif %}").render(), "true")
    assertEquals(Template.parse("{% if 'bob' contains 'bo' %}true{% else %}false{% endif %}").render(), "true")
    assertEquals(Template.parse("{% if 'bob' contains 'ob' %}true{% else %}false{% endif %}").render(), "true")
    assertEquals(Template.parse("{% if 'bob' contains 'bob' %}true{% else %}false{% endif %}").render(), "true")

    assertEquals(Template.parse("{% if 'bob' contains 'bob2' %}true{% else %}false{% endif %}").render(), "false")
    assertEquals(Template.parse("{% if 'bob' contains 'a' %}true{% else %}false{% endif %}").render(), "false")
    assertEquals(Template.parse("{% if 'bob' contains '---' %}true{% else %}false{% endif %}").render(), "false")
  }

  test("condition: contains works on arrays") {
    val vars = TestHelper.mapOf(
      "array" -> TestHelper.listOf(
        java.lang.Long.valueOf(1L),
        java.lang.Long.valueOf(2L),
        java.lang.Long.valueOf(3L),
        java.lang.Long.valueOf(4L),
        java.lang.Long.valueOf(5L)
      )
    )
    assertEquals(Template.parse("{% if array contains 0 %}true{% else %}false{% endif %}").render(vars), "false")
    assertEquals(Template.parse("{% if array contains 1 %}true{% else %}false{% endif %}").render(vars), "true")
    assertEquals(Template.parse("{% if array contains 2 %}true{% else %}false{% endif %}").render(vars), "true")
    assertEquals(Template.parse("{% if array contains 3 %}true{% else %}false{% endif %}").render(vars), "true")
    assertEquals(Template.parse("{% if array contains 4 %}true{% else %}false{% endif %}").render(vars), "true")
    assertEquals(Template.parse("{% if array contains 5 %}true{% else %}false{% endif %}").render(vars), "true")
    assertEquals(Template.parse("{% if array contains 6 %}true{% else %}false{% endif %}").render(vars), "false")
    assertEquals(Template.parse("{% if array contains '1' %}true{% else %}false{% endif %}").render(vars), "false")
  }

  test("condition: contains returns false for nil operands") {
    assertEquals(Template.parse("{% if not_assigned contains 0 %}true{% else %}false{% endif %}").render(), "false")
    assertEquals(Template.parse("{% if 0 contains not_assigned %}true{% else %}false{% endif %}").render(), "false")
  }

  test("condition: left or right may contain operators") {
    val vars = TestHelper.mapOf(
      "one" -> "gnomeslab-and-or-liquid",
      "another" -> "gnomeslab-and-or-liquid"
    )
    assertEquals(Template.parse("{% if one == another %}true{% else %}false{% endif %}").render(vars), "true")
  }
}
