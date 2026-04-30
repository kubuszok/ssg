/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/.../MergeAttributesTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package attributes
package test

import ssg.md.ext.attributes.AttributesExtension
import ssg.md.formatter.Formatter
import ssg.md.parser.Parser
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class MergeAttributesSuite extends munit.FunSuite {

  private val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Collections.singletonList(AttributesExtension.create()))
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

  private def testIdAttributeConflict(): Unit =
    assertMerged(
      "![Fig](http://example.com/test.png){#fig:test}\n" +
        "\n" +
        "[Figure](#fig:test).\n" +
        "\n" +
        "![Fig](http://example.com/test.png){#fig:test1}\n" +
        "\n" +
        "[Figure](#fig:test1).\n" +
        "\n",
      "![Fig](http://example.com/test.png){#fig:test}\n" +
        "\n" +
        "[Figure](#fig:test).\n" +
        "\n",
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "\n" +
        "[Figure](#fig:test).\n" +
        "\n"
    )

  test("IdAttributeConflict1") {
    testIdAttributeConflict()
  }

  test("IdAttributeConflict2") {
    testIdAttributeConflict()
    testIdAttributeConflict()
  }

  private def testUndefinedIdConflict(): Unit =
    assertMerged(
      "![Fig](http://example.com/test.png){#fig:test}\n" +
        "\n" +
        "[Figure](#fig:test).\n" +
        "\n" +
        "[Figure](#fig:test1).\n" +
        "\n" +
        "![Fig](http://example.com/test.png){#fig:test1}\n" +
        "\n" +
        "[Figure](#fig:test1).\n" +
        "\n",
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "\n" +
        "[Figure](#fig:test).\n" +
        "\n" +
        "[Figure](#fig:test1).\n" +
        "\n",
      "![Fig](http://example.com/test.png){#fig:test}  \n" +
        "\n" +
        "[Figure](#fig:test).\n" +
        "\n"
    )

  test("UndefinedIdConflict1") {
    testUndefinedIdConflict()
  }

  test("UndefinedIdConflict2") {
    testUndefinedIdConflict()
    testUndefinedIdConflict()
  }

  // Header attribute id adjustment
  private def testAtxHeadingConflict(): Unit =
    assertMerged(
      "# Atx Heading\n" +
        "\n" +
        "[link](#atx-heading)\n" +
        "\n" +
        "# Atx Heading {.atx-heading1}\n" +
        "\n" +
        "[link](#atx-heading1)\n" +
        "\n",
      "# Atx Heading\n" +
        "[link](#atx-heading)\n" +
        "\n",
      "# Atx Heading\n" +
        "[link](#atx-heading)\n" +
        "\n"
    )

  test("AtxHeadingConflict1") {
    testAtxHeadingConflict()
  }

  test("AtxHeadingConflict2") {
    testAtxHeadingConflict()
    testAtxHeadingConflict()
  }

  private def testSetextHeadingConflict(): Unit =
    assertMerged(
      "Setext Heading\n" +
        "==============\n" +
        "\n" +
        "[link](#setext-heading)\n" +
        "\n" +
        "Setext Heading {.setext-heading1}\n" +
        "=================================\n" +
        "\n" +
        "[link](#setext-heading1)\n" +
        "\n",
      "Setext Heading\n" +
        "=======\n" +
        "[link](#setext-heading)\n" +
        "\n",
      "Setext Heading\n" +
        "=======\n" +
        "[link](#setext-heading)\n" +
        "\n"
    )

  test("SetextHeadingConflict1") {
    testSetextHeadingConflict()
  }

  test("SetextHeadingConflict2") {
    testSetextHeadingConflict()
    testSetextHeadingConflict()
  }

  // Header attribute id adjustment
  private def testAtxHeadingExplicitConflict(): Unit =
    assertMerged(
      "# Atx Heading {#atx-explicit}\n" +
        "\n" +
        "[link](#atx-explicit)\n" +
        "\n" +
        "# Atx Heading {#atx-explicit1}\n" +
        "\n" +
        "[link](#atx-explicit1)\n" +
        "\n",
      "# Atx Heading {#atx-explicit}\n" +
        "[link](#atx-explicit)\n" +
        "\n",
      "# Atx Heading {#atx-explicit}\n" +
        "[link](#atx-explicit)\n" +
        "\n"
    )

  test("AtxHeadingExplicitConflict1") {
    testAtxHeadingExplicitConflict()
  }

  test("AtxHeadingExplicitConflict2") {
    testAtxHeadingExplicitConflict()
    testAtxHeadingExplicitConflict()
  }

  private def testSetextHeadingExplicitConflict(): Unit =
    assertMerged(
      "Setext Heading\n" +
        "==============\n" +
        "\n" +
        "[link](#setext-heading)\n" +
        "\n" +
        "Setext Heading {.setext-heading1}\n" +
        "=================================\n" +
        "\n" +
        "[link](#setext-heading1)\n" +
        "\n",
      "Setext Heading\n" +
        "=======\n" +
        "[link](#setext-heading)\n" +
        "\n",
      "Setext Heading\n" +
        "=======\n" +
        "[link](#setext-heading)\n" +
        "\n"
    )

  test("SetextHeadingExplicitConflict1") {
    testSetextHeadingExplicitConflict()
  }

  test("SetextHeadingExplicitConflict2") {
    testSetextHeadingExplicitConflict()
    testSetextHeadingExplicitConflict()
  }

  private def testHtmlPreservation(): Unit =
    assertMerged(
      "# Heading {style=\"font-size: 26pt\"}\n" +
        "\n" +
        "\\<CUSTOMER_ADDRESS\\> {.addresse}\n" +
        "\n" +
        "<br />\n" +
        "\n" +
        "<br />\n" +
        "\n" +
        "[](http://example.com)\n" +
        "\n",
      "# Heading {style=\"font-size: 26pt\"}\n" +
        "\n" +
        "\\<CUSTOMER_ADDRESS\\>\n" +
        "{.addresse}\n" +
        "\n" +
        "<br />\n" +
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
