/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.data.DataView

import java.util.Locale

/** Gap-fill tests ported from liqp's TemplateTest.java — 5 missing tests. */
final class TemplateExtraSuite extends munit.FunSuite {

  // An unregistered tag is reported as "Invalid Tag" per errorMode
  // (LiquidParser.g4:106-107); the default parser is not LAX, so parsing throws
  // a LiquidException naming the offending tag.
  test("template: custom tag missing error reporting") {
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
      .withTag(
        new tags.Tag("custom_tag") {
          override def render(context: TemplateContext, ns: Array[nodes.LNode]): DataView = DataView.from("xxx")
        }
      )
      .build()
    assertEquals(parser.parse("{% custom_tag %}").render(), "xxx")
  }

  test("template: with custom block") {
    val parser = new TemplateParser.Builder()
      .withBlock(
        new blocks.Block("custom_uppercase_block") {
          override def render(context: TemplateContext, ns: Array[nodes.LNode]): DataView = {
            val block = ns(0)
            val res   = block.render(context)
            DataView.from(super.asString(res, context).toUpperCase(Locale.US))
          }
        }
      )
      .build()
    assertEquals(
      parser.parse("{% custom_uppercase_block %} some text {% endcustom_uppercase_block %}").render(),
      " SOME TEXT "
    )
  }

  // "template: with custom filter (sum)" is in src/test/scala-jvm-native,
  // TemplateExtraJvmNativeSuite: on Scala.js a whole-number Double cannot be told from an integer.

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
