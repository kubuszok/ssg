/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import java.util.HashMap

/** Regression tests for Native-specific issues (possessive quantifiers). */
final class FilterStringBisectSuite extends munit.FunSuite {

  test("strip_newlines with \\r\\n on Native") {
    val vars = new HashMap[String, Any]()
    vars.put("text", "a\r\nb\nc")
    assertEquals(Template.parse("{{ text | strip_newlines }}").render(vars), "abc")
  }

  test("truncatewords on Native") {
    assertEquals(Template.parse("{{ 'a b c d e f' | truncatewords: 3 }}").render(), "a b c...")
  }
}
