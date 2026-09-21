/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.data.DataView

import java.util.{ HashMap => JHashMap }

/** Tests ported from liqp's ReadmeSamplesTest.java — 11 tests.
  *
  * These tests verify the README sample code works correctly.
  */
final class ReadmeSamplesSuite extends munit.FunSuite {

  // SSG: Unknown filters throw at parse time (price, prettyprint, paragraph)
  test("readme: render tree (parse produces a template)") { // ISS-1260 — generated code handles unknown filters at render time
    val input =
      """<ul id="products">
        |  {% for product in products %}
        |    <li>
        |      <h2>{{ product.name }}</h2>
        |      Only {{ product.price | price }}
        |
        |      {{ product.description | prettyprint | paragraph }}
        |    </li>
        |  {% endfor %}
        |</ul>
        |""".stripMargin
    val template = Template.parse(input)
    // Just verify it parses without error
    assert(template != null)
  }

  test("readme: intro example") {
    val parser   = new TemplateParser.Builder().build()
    val template = parser.parse("hi {{name}}")
    val vars     = TestHelper.mapOf("name" -> "tobi")
    val rendered = template.render(vars)
    assertEquals(rendered, "hi tobi")
  }

  test("readme: map example") {
    val template = new TemplateParser.Builder().build().parse("hi {{name}}")
    val map      = new JHashMap[String, DataView]()
    map.put("name", TestHelper.dv("tobi"))
    val rendered = template.render(map)
    assertEquals(rendered, "hi tobi")
  }

  // SSG: Inspectable public field access may differ
  // Converted from render(Inspectable) to render(Map) since DataView rewrite removed reflection path
  test("readme: inspectable (converted to DataView)") {
    val template = Template.parse("hi {{name}}")
    val rendered = template.render(TestHelper.mapOf("name" -> "tobi"))
    assertEquals(rendered, "hi tobi")
  }

  test("readme: liquid support") {
    val template = Template.parse("hi {{name}}")
    val rendered = template.render(TestHelper.mapOf("name" -> "tobi"))
    assertEquals(rendered, "hi tobi")
  }

  // "readme: eager mode" reads an object's properties by reflection, which only the JVM can do:
  // it is in src/test/scalajvm, ReadmeSamplesJvmSuite.

  test("readme: filter registration") {
    val parser = new TemplateParser.Builder()
      .withFilter(
        new filters.Filter("b") {
          override def apply(value: java.lang.Object, context: TemplateContext, params: Array[java.lang.Object]): java.lang.Object = {
            val text = super.asString(value, context)
            DataView.from(text.replaceAll("\\*(\\w(.*?\\w)?)\\*", "<strong>$1</strong>"))
          }
        }
      )
      .build()

    val template = parser.parse("{{ wiki | b }}")
    val vars     = TestHelper.mapOf("wiki" -> "Some *bold* text *in here*.")
    val rendered = template.render(vars)
    assertEquals(rendered, "Some <strong>bold</strong> text <strong>in here</strong>.")
  }

  test("readme: filter with optional params") {
    val parser = new TemplateParser.Builder()
      .withFilter(
        new filters.Filter("repeat") {
          override def apply(value: java.lang.Object, context: TemplateContext, params: Array[java.lang.Object]): java.lang.Object = {
            val text    = super.asString(value, context)
            var times   = if (params.length == 0) 1 else super.asNumber(params(0)).intValue()
            val builder = new StringBuilder()
            while (times > 0) {
              builder.append(text)
              times -= 1
            }
            DataView.from(builder.toString())
          }
        }
      )
      .build()

    val template = parser.parse("{{ 'a' | repeat }}\n{{ 'b' | repeat:5 }}")
    assertEquals(template.render(), "a\nbbbbb")
  }

  // "readme: filter can be anything (sum)" is in src/test/scala-jvm-native,
  // ReadmeSamplesJvmNativeSuite: on Scala.js a whole-number Double cannot be told from an integer.

  test("readme: block sample (loop)") {
    val parser = new TemplateParser.Builder()
      .withBlock(
        new blocks.Block("loop") {
          override def render(context: TemplateContext, ns: Array[nodes.LNode]): DataView = {
            var n       = super.asNumber(ns(0).render(context)).intValue()
            val block   = ns(1)
            val builder = new StringBuilder()
            while (n > 0) {
              builder.append(super.asString(block.render(context), context))
              n -= 1
            }
            DataView.from(builder.toString())
          }
        }
      )
      .build()

    val template = parser.parse("{% loop 5 %}looping!\n{% endloop %}")
    val rendered = template.render()
    assertEquals(rendered, "looping!\nlooping!\nlooping!\nlooping!\nlooping!\n")
  }

  test("readme: JSON rendering (via map)") {
    // Original uses render(String json), SSG uses render(Map)
    val template = new TemplateParser.Builder().build().parse("hi {{name}}")
    val vars     = TestHelper.mapOf("name" -> "tobi")
    val rendered = template.render(vars)
    assertEquals(rendered, "hi tobi")
  }
}

object ReadmeSamplesSuite {

  // MyParams was an Inspectable — converted to produce DataView maps
  // since the DataView rewrite removed reflection-based introspection.
  class MyParams {
    val name:          String                     = "tobi"
    def toDataViewMap: JHashMap[String, DataView] = TestHelper.mapOf("name" -> name)
  }

  // MyLazy was a LiquidSupport — converted to produce DataView maps directly.
  class MyLazy {
    def toDataViewMap: JHashMap[String, DataView] = TestHelper.mapOf("name" -> "tobi")
  }
}
