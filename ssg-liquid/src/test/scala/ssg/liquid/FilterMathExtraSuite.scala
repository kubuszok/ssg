/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import java.time.{ LocalDateTime, ZoneId, ZoneOffset, ZonedDateTime }
import java.util.Collections

/** Gap-fill tests for math filter suites ported from liqp's:
  *   - AppendTest.java (3 missing: date append, eager, timezone)
  *   - Divided_ByTest.java (3 missing: float division, direct apply)
  *   - MinusTest.java (5 missing: type coercion, bug110, bug115, date)
  *   - ModuloTest.java (3 missing: float, original, floated)
  *   - PlusTest.java (2 missing: string-to-number coercion)
  *   - TimesTest.java (2 missing: coercion, original)
  */
final class FilterMathExtraSuite extends munit.FunSuite {

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

  // ---------------------------------------------------------------------------
  // AppendTest.java — 3 missing methods
  // ---------------------------------------------------------------------------

  // 2007-11-01 15:25:00 +0900
  private val testTime: ZonedDateTime = ZonedDateTime.of(
    LocalDateTime.of(2007, 11, 1, 15, 25, 0),
    ZoneId.of("+09:00")
  )

  test("append: append to date type") {
    val data = Collections.singletonMap[String, Any]("a", testTime)
    val res  = Template.parse("{{ a | append: '!' }}").render(data)
    assertEquals(res, "2007-11-01 15:25:00 +0900!")

    val res2 = Template.parse("{{ '!' | append: a }}").render(data)
    assertEquals(res2, "!2007-11-01 15:25:00 +0900")
  }

  // SSG: EAGER evaluate mode date handling differs
  test("append: append to date type eager".fail) {
    val data   = Collections.singletonMap[String, Any]("a", testTime)
    val parser = new TemplateParser.Builder()
      .withEvaluateMode(TemplateParser.EvaluateMode.EAGER)
      .build()
    val res = parser.parse("{{ '!' | append: a }}").render(data)
    assertEquals(res, "!2007-11-01 15:25:00 +0900")

    val res2 = parser.parse("{{ a | append: '!' }}").render(data)
    assertEquals(res2, "2007-11-01 15:25:00 +0900!")
  }

  // SSG: LocalDateTime formatting with timezone offset differs
  test("append: append to date type with default timezone set".fail) {
    val time   = LocalDateTime.of(2020, 1, 1, 12, 59, 59, 999)
    val data   = Collections.singletonMap[String, Any]("a", time)
    val tz     = ZoneId.ofOffset("UTC", ZoneOffset.ofHours(-5))
    val parser = new TemplateParser.Builder()
      .withDefaultTimeZone(tz)
      .build()
    val res = parser.parse("{{ '!' | append: a }}").render(data)
    assertEquals(res, "!2020-01-01 12:59:59 -0500")

    val res2 = parser.parse("{{ a | append: '!' }}").render(data)
    assertEquals(res2, "2020-01-01 12:59:59 -0500!")
  }

  // ---------------------------------------------------------------------------
  // Divided_ByTest.java — 3 missing
  // ---------------------------------------------------------------------------

  test("divided_by: float division") {
    assume(PlatformCompat.supportsReflection, "Float division formatting differs on JS/Native")
    assertNumEquals("{{ 8 | divided_by: 3. }}", String.valueOf(8 / 3.0))
    assertNumEquals("{{ 8 | divided_by: 3.0 }}", String.valueOf(8 / 3.0))
    assertNumEquals("{{ 8 | divided_by: 2.0 }}", "4.0")
    assertNumEquals("{{ 0 | divided_by: 2.0 }}", "0.0")
  }

  test("divided_by: original test 12/3, 14/3, 15/3") {
    assertNumEquals("{{ 12 | divided_by:3 }}", "4")
    assertNumEquals("{{ 14 | divided_by:3 }}", "4")
    assertNumEquals("{{ 15 | divided_by:3 }}", "5")
  }

  test("divided_by: division by zero throws") {
    intercept[RuntimeException] {
      Template.parse("{{ 5 | divided_by: 0 }}").render()
    }
  }

  // ---------------------------------------------------------------------------
  // MinusTest.java — 5 missing
  // ---------------------------------------------------------------------------

  test("minus: float minus") {
    assertNumEquals("{{ 8 | minus: 3. }}", "5.0")
    assertNumEquals("{{ 8 | minus: 3.0 }}", "5.0")
    assertNumEquals("{{ 8 | minus: 2.0 }}", "6.0")
    assertNumEquals("{{ '0.3' | minus: 0.1}}", "0.2")
  }

  test("minus: original test with variables") {
    val vars = TestHelper.mapOf("input" -> java.lang.Integer.valueOf(5), "operand" -> java.lang.Integer.valueOf(1))
    assertEquals(
      Template.parse("{{ input | minus:operand }}").render(vars),
      "4"
    )
    assertNumEquals("{{ '4.3' | minus:'2' }}", "2.3")
  }

  test("minus: bug110 type coercion") {
    assertNumEquals("{{ 5 | minus: 2 }}", "3")
    assertNumEquals("{{ 5.0 | minus: 2 }}", "3.0")
    assertNumEquals("{{ \"5\" | minus: 2 }}", "3")
    assertNumEquals("{{ \"5\" | minus: 2.0 }}", "3.0")
    assertNumEquals("{{ \"5\" | minus: \"2\" }}", "3")
    assertNumEquals("{{ \"5\" | minus: \"2.0\" }}", "3.0")
  }

  test("minus: bug115 whitespace in string numbers") {
    assertNumEquals("{{ \" 5 \" | minus: 2 }}", "3")
    assertNumEquals("{{ \"5\" | minus: \"  2     \" }}", "3")
    assertNumEquals("{{ \"  5\" | minus: \"   2.0\" }}", "3.0")
  }

  test("minus: minus with date returns number") {
    val data = Collections.singletonMap[String, Any]("a", LocalDateTime.now())
    val res  = Template.parse("{{ a | minus: 1 }}").render(data)
    assertEquals(res, "-1")
  }

  // ---------------------------------------------------------------------------
  // ModuloTest.java — 3 missing
  // ---------------------------------------------------------------------------

  test("modulo: float modulo") {
    assertNumEquals("{{ \"8\" | modulo: 3. }}", "2.0")
    assertNumEquals("{{ 8 | modulo: 3.0 }}", "2.0")
    assertNumEquals("{{ 8 | modulo: '2.0' }}", "0.0")
  }

  test("modulo: original test 3 mod 2") {
    assertNumEquals("{{ 3 | modulo:2 }}", "1")
  }

  test("modulo: with floated input") {
    assertNumEquals("{{ 183.357 | modulo: 12 }}", "3.357")
  }

  // ---------------------------------------------------------------------------
  // PlusTest.java — 2 missing
  // ---------------------------------------------------------------------------

  test("plus: float plus") {
    assertNumEquals("{{ 8 | plus: '3.' }}", "11.0")
    assertNumEquals("{{ 8 | plus: 3.0 }}", "11.0")
    assertNumEquals("{{ 8 | plus: \"2.0\" }}", "10.0")
  }

  test("plus: original test") {
    assertNumEquals("{{ 1 | plus:1 }}", "2")
    assertNumEquals("{{ '1' | plus:'1.0' }}", "2.0")
  }

  // ---------------------------------------------------------------------------
  // TimesTest.java — 2 missing
  // ---------------------------------------------------------------------------

  test("times: float times with coercion") {
    assertNumEquals("{{ 8 | times: 3. }}", "24.0")
    assertNumEquals("{{ 8 | times: '3.0' }}", "24.0")
    assertNumEquals("{{ 8 | times: 2.0 }}", "16.0")
    assertNumEquals("{{ foo | times: 4 }}", "0")
    assertNumEquals("{{ '0.1' | times: 3 }}", "0.3")
  }

  test("times: original test values") {
    assertNumEquals("{{ 3 | times:4 }}", "12")
    assertNumEquals("{{ 0.0725 | times:100 }}", "7.25")
    assertNumEquals("{{ \"-0.0725\" | times:100 }}", "-7.25")
    assertNumEquals("{{ \"-0.0725\" | times: -100 }}", "7.25")
  }
}
