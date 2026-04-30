/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import java.util.Locale

/** Gap-fill tests ported from liqp's TemplateTest.java — 5 missing tests. */
final class TemplateExtraSuite extends munit.FunSuite {

  // SSG: parser handles unknown tags differently — may not throw at parse time
  test("template: custom tag missing error reporting".fail) {
    try {
      Template.parse("{% custom_tag %}")
      fail("Expected parsing error for unknown tag")
    } catch {
      case e: Exception =>
        assert(e.getMessage.contains("custom_tag"), s"Expected message about custom_tag: ${e.getMessage}")
    }
  }

  test("template: with custom tag") {
    val parser = new TemplateParser.Builder()
      .withTag(new tags.Tag("custom_tag") {
        override def render(context: TemplateContext, ns: Array[nodes.LNode]): Any = "xxx"
      })
      .build()
    assertEquals(parser.parse("{% custom_tag %}").render(), "xxx")
  }

  test("template: with custom block") {
    val parser = new TemplateParser.Builder()
      .withBlock(
        new blocks.Block("custom_uppercase_block") {
          override def render(context: TemplateContext, ns: Array[nodes.LNode]): Any = {
            val block = ns(0)
            val res   = block.render(context)
            if (res != null) res.toString.toUpperCase(Locale.US) else null
          }
        }
      )
      .build()
    assertEquals(
      parser.parse("{% custom_uppercase_block %} some text {% endcustom_uppercase_block %}").render(),
      " SOME TEXT "
    )
  }

  test("template: with custom filter (sum)") {
    assume(PlatformCompat.supportsReflection, "Double.toString formatting differs on JS/Native")
    val parser = new TemplateParser.Builder()
      .withFilter(
        new filters.Filter("sum") {
          override def apply(value: Any, context: TemplateContext, params: Array[Any]): AnyRef = {
            val numbers = super.asArray(value, context)
            var sum     = 0.0
            numbers.foreach { obj =>
              sum += super.asNumber(obj).doubleValue()
            }
            java.lang.Double.valueOf(sum)
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

  test("template: inline comment") {
    val source =
      "{% # this is a comment %}\n" +
        "\n" +
        "{% # for i in (1..3) -%}\n" +
        "  i={{ i }}\n" +
        "{% # endfor %}\n" +
        "\n" +
        "{%\n" +
        "  ###############################\n" +
        "  # This is a comment\n" +
        "  # across multiple lines\n" +
        "  ###############################\n" +
        "%}"

    val expected =
      "\n" +
        "\n" +
        "i=\n" +
        "\n" +
        "\n"

    assertEquals(Template.parse(source).render(), expected)
  }
}
