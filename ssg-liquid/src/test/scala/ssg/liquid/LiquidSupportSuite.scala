/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Tests for structured data access in Liquid templates via DataView.
 * Originally tested reflection-based Inspectable/LiquidSupport;
 * now tests DataView maps directly (cross-platform, no reflection). */
package ssg
package liquid

import ssg.data.DataView

import java.util.{ HashMap => JHashMap }

import scala.collection.immutable.VectorMap

final class LiquidSupportSuite extends munit.FunSuite {

  private val EAGER_PARSER: TemplateParser =
    new TemplateParser.Builder().withEvaluateMode(TemplateParser.EvaluateMode.EAGER).build()

  private def suppPojoData(): JHashMap[String, DataView] = {
    val child = DataView.from(VectorMap("val" -> DataView.from("SuppChild")))
    TestHelper.mapOf("foo" -> DataView.from(VectorMap("child" -> child)))
  }

  // --- LookupNode: nested property access ---

  test("lookupNode: DataView map nested access returns SuppChild") {
    val data = suppPojoData()
    assertEquals(TemplateParser.DEFAULT.parse("{{foo.child.val}}").render(data), "SuppChild")
  }

  test("lookupNode: DataView map nested access with EAGER returns SuppChild") {
    val data = suppPojoData()
    assertEquals(EAGER_PARSER.parse("{{foo.child.val}}").render(data), "SuppChild")
  }

  // --- Map filter ---

  test("mapFilter: DataView map filter returns SuppChild") {
    val data = suppPojoData()
    assertEquals(
      TemplateParser.DEFAULT.parse("{{ foo | map: 'child' | map: 'val' }}").render(data),
      "SuppChild"
    )
  }

  test("mapFilter: DataView map filter with EAGER returns SuppChild") {
    val data = suppPojoData()
    assertEquals(
      EAGER_PARSER.parse("{{ foo | map: 'child' | map: 'val' }}").render(data),
      "SuppChild"
    )
  }

  // --- Size property ---

  test("lookupNodeSize: DataView map child size returns 1") {
    val data = suppPojoData()
    assertEquals(TemplateParser.DEFAULT.parse("{{foo.child.size}}").render(data), "1")
  }

  test("lookupNodeSize: DataView map child size with EAGER returns 1") {
    val data = suppPojoData()
    assertEquals(EAGER_PARSER.parse("{{foo.child.size}}").render(data), "1")
  }

  // --- Foo/property access via DataView ---

  test("DataView map property access returns A") {
    val data = TestHelper.mapOf("foo" -> TestHelper.mapOf("a" -> "A"))
    assertEquals(TemplateParser.DEFAULT.parse("{{foo.a}}").render(data), "A")
  }

  test("DataView map property access with EAGER returns A") {
    val data = TestHelper.mapOf("foo" -> TestHelper.mapOf("a" -> "A"))
    assertEquals(EAGER_PARSER.parse("{{foo.a}}").render(data), "A")
  }

  // --- Target (explicit DataView map) ---

  test("DataView map renders toLiquid value") {
    val vars = TestHelper.mapOf("a" -> TestHelper.mapOf("val" -> "OK"))
    val res  = TemplateParser.DEFAULT.parse("{{a.val}}").render(vars)
    assertEquals(res, "OK")
  }

  test("DataView map with EAGER renders toLiquid value") {
    val vars = TestHelper.mapOf("a" -> TestHelper.mapOf("val" -> "OK"))
    val res  = EAGER_PARSER.parse("{{a.val}}").render(vars)
    assertEquals(res, "OK")
  }
}
