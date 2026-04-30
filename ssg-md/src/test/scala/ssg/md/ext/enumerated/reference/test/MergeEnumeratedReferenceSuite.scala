/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/.../MergeEnumeratedReferenceTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package enumerated
package reference
package test

import ssg.md.ext.attributes.AttributesExtension
import ssg.md.ext.enumerated.reference.EnumeratedReferenceExtension
import ssg.md.formatter.Formatter
import ssg.md.parser.Parser
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Arrays
import scala.language.implicitConversions

final class MergeEnumeratedReferenceSuite extends munit.FunSuite {

  private val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Arrays.asList(EnumeratedReferenceExtension.create(), AttributesExtension.create()))
    .set(Parser.BLANK_LINES_IN_AST, true)
    .set(Parser.PARSE_INNER_HTML_COMMENTS, true)
    .set(Parser.HEADING_NO_ATX_SPACE, true)
    .set(Formatter.MAX_TRAILING_BLANK_LINES, 0)
    .toImmutable

  private val FORMATTER: Formatter = Formatter.builder(Nullable(OPTIONS)).build()
  private val PARSER:    Parser    = Parser.builder(OPTIONS).build()

  private def assertMerged(expected: String, markdownSources: String*): Unit = {
    val documents    = markdownSources.map(src => PARSER.parse(src)).toArray
    val mergedOutput = FORMATTER.mergeRender(documents, 1)
    assertEquals(mergedOutput, expected, "Merged results differ")
  }

  test("IdAttributeConflict1") {
    assertMerged(
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "[#fig:test]\n" +
        "\n" +
        "[@fig]: Figure [#].\n" +
        "\n" +
        "![Fig](http://example.com/test.png){#fig1:test}  \n" +
        "[#fig1:test]\n" +
        "\n" +
        "[@fig1]: Figure [#].\n" +
        "\n",
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "[#fig:test]\n" +
        "\n" +
        "[@fig]: Figure [#].\n" +
        "\n",
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "[#fig:test]\n" +
        "\n" +
        "[@fig]: Figure [#].\n" +
        "\n"
    )
  }

  test("IdAttributeConflict2") {
    assertMerged(
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "[#fig:test]\n" +
        "\n" +
        "[@fig]: Figure [#].\n" +
        "\n" +
        "![Fig](http://example.com/test.png){#fig1:test}  \n" +
        "[#fig1:test]\n" +
        "\n" +
        "[@fig1]: Figure [#].\n" +
        "\n",
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "[#fig:test]\n" +
        "\n" +
        "[@fig]: Figure [#].\n" +
        "\n",
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "[#fig:test]\n" +
        "\n" +
        "[@fig]: Figure [#].\n" +
        "\n"
    )
    assertMerged(
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "[#fig:test]\n" +
        "\n" +
        "[@fig]: Figure [#].\n" +
        "\n" +
        "![Fig](http://example.com/test.png){#fig1:test}  \n" +
        "[#fig1:test]\n" +
        "\n" +
        "[@fig1]: Figure [#].\n" +
        "\n",
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "[#fig:test]\n" +
        "\n" +
        "[@fig]: Figure [#].\n" +
        "\n",
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "[#fig:test]\n" +
        "\n" +
        "[@fig]: Figure [#].\n" +
        "\n"
    )
  }

  test("UndefinedIdConflict1") {
    assertMerged(
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "[#fig:test]\n" +
        "\n" +
        "![Fig](http://example.com/test.png){#fig2:test}  \n" +
        "[#fig2:test]\n" +
        "\n" +
        "[@fig]: Figure [#].\n" +
        "\n" +
        "![Fig](http://example.com/test.png){#fig1:test}  \n" +
        "[#fig1:test]\n" +
        "\n" +
        "[@fig1]: Figure [#].\n" +
        "\n",
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "[#fig:test]\n" +
        "\n" +
        "![Fig](http://example.com/test.png){#fig2:test}  \n" +
        "[#fig2:test]\n" +
        "\n" +
        "[@fig]: Figure [#].\n" +
        "\n",
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "[#fig:test]\n" +
        "\n" +
        "[@fig]: Figure [#].\n" +
        "\n"
    )
  }

  test("UndefinedIdConflict2") {
    assertMerged(
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "[#fig:test]\n" +
        "\n" +
        "![Fig](http://example.com/test.png){#fig2:test}  \n" +
        "[#fig2:test]\n" +
        "\n" +
        "[@fig]: Figure [#].\n" +
        "\n" +
        "![Fig](http://example.com/test.png){#fig1:test}  \n" +
        "[#fig1:test]\n" +
        "\n" +
        "[@fig1]: Figure [#].\n" +
        "\n",
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "[#fig:test]\n" +
        "\n" +
        "![Fig](http://example.com/test.png){#fig2:test}  \n" +
        "[#fig2:test]\n" +
        "\n" +
        "[@fig]: Figure [#].\n" +
        "\n",
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "[#fig:test]\n" +
        "\n" +
        "[@fig]: Figure [#].\n" +
        "\n"
    )
    assertMerged(
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "[#fig:test]\n" +
        "\n" +
        "![Fig](http://example.com/test.png){#fig2:test}  \n" +
        "[#fig2:test]\n" +
        "\n" +
        "[@fig]: Figure [#].\n" +
        "\n" +
        "![Fig](http://example.com/test.png){#fig1:test}  \n" +
        "[#fig1:test]\n" +
        "\n" +
        "[@fig1]: Figure [#].\n" +
        "\n",
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "[#fig:test]\n" +
        "\n" +
        "![Fig](http://example.com/test.png){#fig2:test}  \n" +
        "[#fig2:test]\n" +
        "\n" +
        "[@fig]: Figure [#].\n" +
        "\n",
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "[#fig:test]\n" +
        "\n" +
        "[@fig]: Figure [#].\n" +
        "\n"
    )
  }

  private def testHtmlPreservation(): Unit =
    assertMerged(
      "# [#hd1] Heading {style=\"font-size: 26pt\"}\n" +
        "\n" +
        "\\<CUSTOMER_ADDRESS\\> {.addresse}\n" +
        "\n" +
        "<br />\n" +
        "\n" +
        "[@hd1]: [#].\n" +
        "\n" +
        "<br />\n" +
        "\n" +
        "[](http://example.com)\n" +
        "\n",
      "# [#hd1] Heading {style=\"font-size: 26pt\"}\n" +
        "\n" +
        "\\<CUSTOMER_ADDRESS\\>\n" +
        "{.addresse}\n" +
        "\n" +
        "<br />\n" +
        "\n" +
        "[@hd1]: [#]. \n" +
        "\n",
      "<br />\n" +
        "\n" +
        "[](http://example.com)\n" +
        "\n"
    )

  test("HtmlPreservation1") {
    testHtmlPreservation()
  }

  test("HtmlPreservation2") {
    testHtmlPreservation()
    testHtmlPreservation()
  }

  private def testHtmlPreservationLink(): Unit =
    assertMerged(
      "[](http://example.com)\n" +
        "\n" +
        "<br />\n" +
        "\n" +
        "<br />\n" +
        "\n" +
        "[](http://example.com)\n" +
        "\n",
      "[](http://example.com)\n" +
        "\n" +
        "<br />\n" +
        "\n",
      "<br />\n" +
        "\n" +
        "[](http://example.com)\n" +
        "\n"
    )

  test("HtmlPreservationLink1") {
    testHtmlPreservationLink()
  }

  test("HtmlPreservationLink2") {
    testHtmlPreservationLink()
    testHtmlPreservationLink()
  }
}
