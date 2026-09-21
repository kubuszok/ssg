/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.data.DataView

import ssg.liquid.parser.Flavor

import java.util.{ HashMap => JHashMap }

/** The one where-filter test that depends on how a whole-number Double prints.
  *
  * JVM and Scala Native only: on Scala.js a whole-number Double cannot be told from an integer, so `42.0` prints as `42` and DOES match the string target. The other tests are in the shared
  * WhereFilterSuite.
  */
final class WhereFilterJvmNativeSuite extends munit.FunSuite {

  private val jekyllParser: TemplateParser =
    new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).build()

  private def makeItem(pairs: (String, Any)*): JHashMap[String, DataView] =
    TestHelper.mapOf(pairs*)

  private def makeList(items: Any*): DataView =
    TestHelper.listOf(items*)

  test("jekyll where: numeric coercion via parseSortInput") {
    // JVM-only: JavaScript's number.toString() behavior differs from Java's Double.toString()
    // On JVM: Double(42.0).toString = "42.0", which != "42" (string target), so no match
    // On JS:  Number(42).toString() = "42", which == "42", so it matches
    // Jekyll's where uses parseSortInput, which parses numeric strings to Double
    val items = makeList(
      makeItem("score" -> "42", "name" -> "high"),
      makeItem("score" -> "10", "name" -> "low"),
      makeItem("score" -> "42", "name" -> "also-high")
    )
    val vars = new JHashMap[String, DataView]()
    vars.put("items", TestHelper.dv(items))
    // The string "42" is coerced to Double 42.0, so comparing with string "42" won't match
    // because parseSortInput turns "42" into 42.0, and comparePropertyVsTarget calls asString
    // on the target. So "42" matches the string representation of 42.0 which is "42.0"
    // Actually: the target value "42" stays as string, but the property is coerced to Double 42.0.
    // comparePropertyVsTarget does: strTarget.equals(itemProperty) for String, and for non-string
    // it converts each array element to string. Since 42.0.toString = "42.0" != "42", this will
    // NOT match. Let's test with the actual numeric value:
    val result = jekyllParser.parse("{% assign found = items | where: 'score', '42' %}{{ found | size }}").render(vars)
    // parseSortInput coerces "42" -> 42.0, then comparePropertyVsTarget:
    //   target = "42" (string), property = 42.0 (Double)
    //   isString(42.0) = false, so goes to array branch: asString(42.0) = "42.0", which != "42"
    // So the match fails. This tests that numeric coercion is actually happening.
    assertEquals(result, "0")
  }
}
