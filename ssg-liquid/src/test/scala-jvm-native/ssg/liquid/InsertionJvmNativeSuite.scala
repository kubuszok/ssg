/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.data.DataView

/** The two tests of liqp's InsertionTest.java whose tag answers a whole-number Double.
  *
  * JVM and Scala Native only: on Scala.js a whole-number Double cannot be told from an integer, so `20.0` renders as `20`. The other tests are in the shared InsertionSuite.
  */
final class InsertionJvmNativeSuite extends munit.FunSuite {

  test("insertion: custom tag") {
    val parser = new TemplateParser.Builder()
      .withTag(
        new tags.Tag("twice") {
          override def render(context: TemplateContext, ns: Array[nodes.LNode]): DataView = {
            val number = super.asNumber(ns(0).render(context)).doubleValue()
            DataView.from(number * 2)
          }
        }
      )
      .build()

    assertEquals(parser.parse("{% twice 10 %}").render(), "20.0")
  }

  test("insertion: custom tag parameters") {
    val parser = new TemplateParser.Builder()
      .withTag(
        new tags.Tag("multiply") {
          override def render(context: TemplateContext, ns: Array[nodes.LNode]): DataView = {
            val n1 = super.asNumber(ns(0).render(context)).doubleValue()
            val n2 = super.asNumber(ns(1).render(context)).doubleValue()
            DataView.from(n1 * n2)
          }
        }
      )
      .build()

    assertEquals(parser.parse("{% multiply 2 4 %}").render(), "8.0")
  }
}
