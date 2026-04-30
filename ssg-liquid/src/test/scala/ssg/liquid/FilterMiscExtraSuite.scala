/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.liquid.parser.Flavor

import java.util.{ HashMap => JHashMap }

/** Tests ported from liqp's filters/JsonTest.java (2), Normalize_WhitespaceTest.java (1), and FilterTest.java (2) — total 5 tests.
  */
final class FilterMiscExtraSuite extends munit.FunSuite {

  // ---------------------------------------------------------------------------
  // JsonTest.java — 2 tests
  // ---------------------------------------------------------------------------

  test("json: string input should be stringified") {
    val template = Template.parse("{{ 'Hello, World!' | json }}")
    val rendered = template.render()
    assertEquals(rendered, "\"Hello, World!\"")
  }

  test("json: object input should be stringified") {
    val template = Template.parse("{{ obj | json }}")
    val map      = new JHashMap[String, Any]()
    val nested   = new JHashMap[String, Any]()
    nested.put("key", "value")
    map.put("obj", nested)
    val rendered = template.render(map)
    assertEquals(rendered, "{\"key\":\"value\"}")
  }

  // ---------------------------------------------------------------------------
  // Normalize_WhitespaceTest.java — 1 test (multiple assertions)
  // ---------------------------------------------------------------------------

  test("normalize_whitespace: replaces newlines, tabs, and multiple spaces") {
    // normalize_whitespace is a Jekyll-only filter
    val parser = new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).build()

    val cases: Array[(String, String, String)] = Array(
      ("a b", "a\nb", "replace newlines with a space"),
      ("a b", "a\n\nb", "replace newlines with a space"),
      ("a b", "a\tb", "replace tabs with a space"),
      ("a b", "a\t\tb", "replace tabs with a space"),
      ("a b", "a  b", "replace multiple spaces with a single space"),
      ("a b", "a\t\nb", "replace multiple spaces with a single space"),
      ("a b", "a \t \n\nb", "replace multiple spaces with a single space"),
      ("a", "a ", "strip whitespace from beginning and end of string"),
      ("a", " a", "strip whitespace from beginning and end of string"),
      ("a", " a ", "strip whitespace from beginning and end of string")
    )

    cases.foreach { case (expected, input, description) =>
      val vars = new JHashMap[String, Any]()
      vars.put("v", input)
      val result = parser.parse("{{ v | normalize_whitespace }}").render(vars)
      assertEquals(result, expected, s"$description: input='$input'")
    }
  }

  // ---------------------------------------------------------------------------
  // FilterTest.java — 2 tests
  // ---------------------------------------------------------------------------

  test("custom filter: textilize filter") {
    val parser = new TemplateParser.Builder()
      .withFilter(
        new filters.Filter("textilize") {
          override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
            val s = super.asString(value, context).trim
            "<b>" + s.substring(1, s.length - 1) + "</b>"
          }
        }
      )
      .build()

    val template = parser.parse("{{ '*hi*' | textilize }}")
    val rendered = template.render()
    assertEquals(rendered, "<b>hi</b>")
  }

  // SSG: Unknown filter throws at parse time rather than render time
  test("flavored filters: normalize_whitespace available in Jekyll but not Liquid".fail) {
    val templateText = "{{ ' a  b   c' | normalize_whitespace }}"

    val template1 = new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).build().parse(templateText)
    val res       = template1.render()
    assertEquals(res, "a b c")

    val template2 = new TemplateParser.Builder().withFlavor(Flavor.LIQUID).build().parse(templateText)
    try {
      template2.render()
      fail("Expected an exception for normalize_whitespace in LIQUID flavor")
    } catch {
      case e: Exception =>
        assert(e.getMessage.contains("no filter available named: |normalize_whitespace"), s"Unexpected message: ${e.getMessage}")
    }
  }
}
