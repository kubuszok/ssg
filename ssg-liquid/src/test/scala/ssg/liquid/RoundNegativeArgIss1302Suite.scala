/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

/** Pinning tests for ISS-1302: the Liquid `round` filter must replicate liqp's DecimalFormat semantics for a NEGATIVE (or zero) round argument.
  *
  * Ground truth: liqp Round.java:18-32 builds the DecimalFormat pattern as `"0"` and appends `"." + N zeros` ONLY when `round > 0`. A negative or zero round argument therefore formats to a scale-0
  * integer (nearest whole number, HALF_UP). It never rounds to the tens/hundreds place.
  *
  * SSG's Round.scala instead does `setScale(scale, HALF_UP)` with a negative scale, which rounds to the hundreds/tens place -- e.g. round(12345.678, -2) => 12300 instead of liqp's 12346.
  *
  * Verified against liqp Round.java:18-37.
  */
final class RoundNegativeArgIss1302Suite extends munit.FunSuite {

  /** Helper for cross-platform numeric assertions — mirrors FilterMathSuite.assertNumEquals. */
  private def assertNumEquals(template: String, expected: String): Unit =
    try {
      val result = Template.parse(template).render()
      if (result != expected) {
        // Try comparing as numbers
        try {
          val expectedNum = java.lang.Double.parseDouble(expected)
          val resultNum   = java.lang.Double.parseDouble(result)
          assert(Math.abs(expectedNum - resultNum) < 0.0001, s"Expected $expected but got: $result")
        } catch {
          case _: NumberFormatException =>
            assertEquals(result, expected)
        }
      }
    } catch {
      case e: Throwable =>
        fail(s"Template '$template' threw ${e.getClass.getName}: ${e.getMessage}")
    }

  // ---- PRIMARY red: negative round argument (the ISS-1302 bug) ----

  test("round with -2 argument rounds to nearest integer, not hundreds place") {
    // liqp Round.java:18-32: round <= 0 keeps pattern "0" => scale-0 integer, HALF_UP.
    // Expected 12346 (liqp), SSG currently yields 12300 (setScale(-2)).
    assertNumEquals("{{ 12345.678 | round: -2 }}", "12346")
  }

  test("round with -1 argument rounds to nearest integer, not tens place") {
    // liqp Round.java:18-32: same scale-0 integer path for negative arg.
    // Expected 12346 (liqp), SSG currently yields 12350 (setScale(-1)).
    assertNumEquals("{{ 12345.678 | round: -1 }}", "12346")
  }

  // ---- Controls (positive path + no arg; should already pass) ----

  test("round with 2 argument rounds to 2 decimal places") {
    // liqp positive path: pattern "0.00" => 12345.68.
    assertNumEquals("{{ 12345.678 | round: 2 }}", "12345.68")
  }

  test("round with no argument rounds to nearest integer") {
    // liqp default: scale-0 integer => 12346.
    assertNumEquals("{{ 12345.678 | round }}", "12346")
  }
}
