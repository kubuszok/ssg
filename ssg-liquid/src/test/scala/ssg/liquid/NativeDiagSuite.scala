/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package liquid

/** Diagnostic suite — verifies cross-platform correctness with lenient assertions. */
final class NativeDiagSuite extends munit.FunSuite {

  private def assertRendered(label: String, template: String, expected: String, vars: java.util.HashMap[String, Any] = new java.util.HashMap[String, Any]()): Unit =
    try {
      val result = Template.parse(template).render(vars)
      assertEquals(result, expected, s"$label failed")
    } catch {
      case e: Throwable =>
        fail(s"$label threw ${e.getClass.getName}: ${e.getMessage}")
    }

  test("diag: escape_once with double ampersand") {
    assertRendered("escape_once double", "{{ '&&amp;' | escape_once }}", "&amp;&amp;")
  }

  test("diag: strip_html script tag") {
    val m = new java.util.HashMap[String, Any]()
    m.put("html", "<script>x</script>text")
    assertRendered("strip_html script", "{{ html | strip_html }}", "text", m)
  }

  test("diag: strip_html style tag") {
    val m = new java.util.HashMap[String, Any]()
    m.put("html", "<style>x</style>text")
    assertRendered("strip_html style", "{{ html | strip_html }}", "text", m)
  }

  test("diag: abs float") {
    assertRendered("abs float", "{{ 17.42 | abs }}", "17.42")
  }

  test("diag: plus float") {
    val result = Template.parse("{{ 8 | plus: 3.0 }}").render()
    try {
      val num = java.lang.Double.parseDouble(result)
      assert(Math.abs(num - 11.0) < 0.001, s"plus float: got $result")
    } catch {
      case _: NumberFormatException => fail(s"plus float: got non-numeric '$result'")
    }
  }

  test("diag: round 2dp") {
    assertRendered("round 2dp", "{{ 4.5612 | round: 2 }}", "4.56")
  }
}
