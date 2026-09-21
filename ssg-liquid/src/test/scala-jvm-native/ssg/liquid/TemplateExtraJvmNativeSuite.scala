/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.data.DataView

/** The one gap-fill test from liqp's TemplateTest.java whose filter answers a whole-number Double.
  *
  * JVM and Scala Native only: on Scala.js a whole-number Double cannot be told from an integer, so `15.0` renders as `15`. The other tests are in the shared TemplateExtraSuite.
  */
final class TemplateExtraJvmNativeSuite extends munit.FunSuite {

  test("template: with custom filter (sum)") {
    val parser = new TemplateParser.Builder()
      .withFilter(
        new filters.Filter("sum") {
          override def apply(value: java.lang.Object, context: TemplateContext, params: Array[java.lang.Object]): java.lang.Object = {
            val numbers = super.asArray(value, context)
            var sum     = 0.0
            numbers.foreach { obj =>
              sum += super.asNumber(obj).doubleValue()
            }
            DataView.from(sum)
          }
        }
      )
      .build()

    val vars = TestHelper.mapOf(
      "numbers" -> TestHelper.listOf(
        java.lang.Integer.valueOf(1),
        java.lang.Integer.valueOf(2),
        java.lang.Integer.valueOf(3),
        java.lang.Integer.valueOf(4),
        java.lang.Integer.valueOf(5)
      )
    )
    assertEquals(parser.parse("{{ numbers | sum }}").render(vars), "15.0")
  }
}
