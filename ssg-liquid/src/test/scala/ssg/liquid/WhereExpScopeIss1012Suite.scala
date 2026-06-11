/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.data.DataView

import ssg.liquid.parser.Flavor

import java.util.{ HashMap => JHashMap }

/** Red tests for ISS-1012: `where_exp` must evaluate its expression in the enclosing template context with the loop variable overlaid on top.
  *
  * liqp mechanism: Where_Exp.java:77 renders the expression with a singleton map of the loop variable but passes the full `context` as parent; Template.java:361 then builds
  * `parent.newChildContext(variables)`, and TemplateContext.java:104-120 resolves variables local-first (loop var wins) with fall-through to the parent (outer scope visible).
  *
  * The two "red" cases below assert the FIXED behavior and are expected to fail until ISS-1012 is resolved; the two "control" cases pass today and must keep passing after the fix.
  */
final class WhereExpScopeIss1012Suite extends munit.FunSuite {

  private def parse(template: String): Template =
    Flavor.JEKYLL.defaultParser().parse(template)

  private def L(v: Long): java.lang.Long = java.lang.Long.valueOf(v)

  private def itemsData(): JHashMap[String, DataView] =
    TestHelper.mapOf(
      "items" -> TestHelper.listOf(
        TestHelper.mapOf("name" -> "alpha", "n" -> L(1)),
        TestHelper.mapOf("name" -> "beta", "n" -> L(2)),
        TestHelper.mapOf("name" -> "gamma", "n" -> L(3))
      )
    )

  /* Control: expression referencing only the loop variable — works today. */
  test("ISS-1012 control: where_exp with loop variable only") {
    val res = parse("{{ items | where_exp: 'i', 'i.n > 2' | map: 'name' | join: ',' }}").render(itemsData())
    assertEquals(res, "gamma")
  }

  /* Red: expression comparing against a variable assigned outside the filter. */
  test("ISS-1012 red: where_exp sees an outer assign") {
    val res = parse(
      "{% assign threshold = 2 %}{{ items | where_exp: 'i', 'i.n > threshold' | map: 'name' | join: ',' }}"
    ).render(itemsData())
    assertEquals(res, "gamma")
  }

  /* Red: expression referencing a nested top-level context variable, the
   * Jekyll site/page-style usage that motivated ISS-1012. */
  test("ISS-1012 red: where_exp sees a top-level nested context variable") {
    val data = itemsData()
    data.putAll(TestHelper.mapOf("site" -> TestHelper.mapOf("min_n" -> L(2))))
    val res = parse(
      "{{ items | where_exp: 'i', 'i.n >= site.min_n' | map: 'name' | join: ',' }}"
    ).render(data)
    assertEquals(res, "beta,gamma")
  }

  /* Control: shadowing — an outer variable with the SAME name as the loop
   * variable; the loop variable must win (liqp overlay semantics: the child
   * context map is consulted first, TemplateContext.java:107-111, the parent
   * only on a local miss, TemplateContext.java:113-116). Passes today (only
   * the loop var is visible at all) and must keep passing after the fix. */
  test("ISS-1012 control: loop variable shadows an outer variable of the same name") {
    val data = itemsData()
    data.putAll(TestHelper.mapOf("i" -> TestHelper.mapOf("n" -> L(99))))
    val res = parse("{{ items | where_exp: 'i', 'i.n == 2' | map: 'name' | join: ',' }}").render(data)
    assertEquals(res, "beta")
  }
}
