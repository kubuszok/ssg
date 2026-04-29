/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

final class FilterMathSuite extends munit.FunSuite {

  /** Helper for cross-platform numeric assertions — Native may format 11.0 as 11 */
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

  // abs

  test("abs negative integer") {
    assertNumEquals("{{ -17 | abs }}", "17")
  }

  test("abs positive float") {
    assertNumEquals("{{ 17.42 | abs }}", "17.42")
  }

  test("abs non-numeric string") {
    assertNumEquals("{{ 'not' | abs }}", "0")
  }

  // plus

  test("plus integers") {
    assertNumEquals("{{ 8 | plus: 2 }}", "10")
  }

  test("plus integer and float") {
    assertNumEquals("{{ 8 | plus: 3.0 }}", "11.0")
  }

  // minus

  test("minus integers") {
    assertNumEquals("{{ 8 | minus: 2 }}", "6")
  }

  test("minus integer and float") {
    assertNumEquals("{{ 8 | minus: 3.0 }}", "5.0")
  }

  // times

  test("times integers") {
    assertNumEquals("{{ 8 | times: 2 }}", "16")
  }

  test("times integer and float") {
    assertNumEquals("{{ 8 | times: 3.0 }}", "24.0")
  }

  // divided_by

  test("divided_by integers even") {
    assertNumEquals("{{ 8 | divided_by: 2 }}", "4")
  }

  test("divided_by integers with truncation") {
    assertNumEquals("{{ 8 | divided_by: 3 }}", "2")
  }

  // modulo

  test("modulo 8 mod 3") {
    assertNumEquals("{{ 8 | modulo: 3 }}", "2")
  }

  test("modulo 3 mod 2") {
    assertNumEquals("{{ 3 | modulo: 2 }}", "1")
  }

  // ceil

  test("ceil float") {
    assertNumEquals("{{ 4.6 | ceil }}", "5")
  }

  test("ceil string") {
    assertNumEquals("{{ '4.3' | ceil }}", "5")
  }

  // floor

  test("floor float") {
    assertNumEquals("{{ 4.6 | floor }}", "4")
  }

  test("floor string") {
    assertNumEquals("{{ '4.3' | floor }}", "4")
  }

  // round

  test("round to nearest integer") {
    assertNumEquals("{{ 4.6 | round }}", "5")
  }

  test("round to 2 decimal places") {
    assertNumEquals("{{ 4.5612 | round: 2 }}", "4.56")
  }

  // at_least

  test("at_least when value is greater") {
    assertNumEquals("{{ 5 | at_least: 4 }}", "5")
  }

  test("at_least when value is less") {
    assertNumEquals("{{ 5 | at_least: 6 }}", "6")
  }

  // at_most

  test("at_most when value is greater") {
    assertNumEquals("{{ 5 | at_most: 4 }}", "4")
  }

  test("at_most when value is less") {
    assertNumEquals("{{ 5 | at_most: 6 }}", "5")
  }
}
