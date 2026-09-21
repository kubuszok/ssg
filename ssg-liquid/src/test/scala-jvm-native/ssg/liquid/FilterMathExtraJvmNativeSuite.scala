/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

/** The one gap-fill test from liqp's Divided_ByTest.java whose divisor is a whole-number Double.
  *
  * JVM and Scala Native only: on Scala.js a whole-number Double cannot be told from an integer, so `3.` prints as `3`, liqp's `canBeInteger` (a test of the printed form) answers true and
  * `8 | divided_by: 3.` divides as integers. The other tests are in the shared FilterMathExtraSuite.
  */
final class FilterMathExtraJvmNativeSuite extends munit.FunSuite {

  /** Helper for cross-platform numeric assertions */
  private def assertNumEquals(template: String, expected: String, clue: String = ""): Unit = {
    val result = Template.parse(template).render()
    if (result != expected) {
      try {
        val expectedNum = java.lang.Double.parseDouble(expected)
        val resultNum   = java.lang.Double.parseDouble(result)
        assert(Math.abs(expectedNum - resultNum) < 0.0001, s"$clue Expected $expected but got: $result")
      } catch {
        case _: NumberFormatException =>
          assertEquals(result, expected, clue)
      }
    }
  }

  test("divided_by: float division") {
    assertNumEquals("{{ 8 | divided_by: 3. }}", String.valueOf(8 / 3.0))
    assertNumEquals("{{ 8 | divided_by: 3.0 }}", String.valueOf(8 / 3.0))
    assertNumEquals("{{ 8 | divided_by: 2.0 }}", "4.0")
    assertNumEquals("{{ 0 | divided_by: 2.0 }}", "0.0")
  }
}
