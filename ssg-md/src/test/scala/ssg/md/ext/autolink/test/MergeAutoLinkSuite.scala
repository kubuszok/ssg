/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-autolink/.../MergeAutoLinkTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package autolink
package test

import ssg.md.ext.autolink.AutolinkExtension
import ssg.md.formatter.Formatter
import ssg.md.parser.Parser
import ssg.md.util.ast.Document
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class MergeAutoLinkSuite extends munit.FunSuite {

  private val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.BLANK_LINES_IN_AST, true)
    .set(Parser.PARSE_INNER_HTML_COMMENTS, true)
    .set(Parser.HEADING_NO_ATX_SPACE, true)
    .set(Parser.EXTENSIONS, Collections.singletonList(AutolinkExtension.create()))
    .set(Formatter.DEFAULT_LINK_RESOLVER, true)
    .set(Formatter.MAX_TRAILING_BLANK_LINES, 0)
    .toImmutable

  private val FORMATTER: Formatter = Formatter.builder(Nullable(OPTIONS)).build()
  private val PARSER:    Parser    = Parser.builder(OPTIONS).build()

  private def assertMerged(expected: String, markdownSources: String*): Unit =
    assertMergedWithUrls(expected, null, null, markdownSources*) // @nowarn - null for Java interop compatibility

  private def assertMergedWithUrls(
    expected:         String,
    documentUrls:     Array[String],
    documentRootUrls: Array[String],
    markdownSources:  String*
  ): Unit = {
    val documents = new Array[Document](markdownSources.length)
    var i         = 0
    while (i < markdownSources.length) {
      documents(i) = PARSER.parse(markdownSources(i))

      if (documentUrls != null && i < documentUrls.length && documentUrls(i) != null) {
        documents(i).set(Formatter.DOC_RELATIVE_URL, documentUrls(i))
      }

      if (documentRootUrls != null && i < documentRootUrls.length && documentRootUrls(i) != null) {
        documents(i).set(Formatter.DOC_ROOT_URL, documentRootUrls(i))
      }

      i += 1
    }

    val mergedOutput = FORMATTER.mergeRender(documents, 1)
    assertEquals(mergedOutput, expected, "Merged results differ")
  }

  private def testMailLink(): Unit =
    assertMerged(
      "test@example.com\n" +
        "\n" +
        "test@example.com\n" +
        "\n",
      "test@example.com\n" +
        "\n",
      "test@example.com\n" +
        "\n"
    )

  test("Mail_Link1") {
    testMailLink()
  }

  test("Mail_Link2") {
    testMailLink()
    testMailLink()
  }

  private def testMailLink1(): Unit =
    assertMerged(
      "test@example.com\n" +
        "\n" +
        "<test@example.com>\n" +
        "\n",
      "test@example.com\n" +
        "\n",
      "<test@example.com>\n" +
        "\n"
    )

  test("Mail_Link11") {
    testMailLink1()
  }

  test("Mail_Link12") {
    testMailLink1()
    testMailLink1()
  }

  private def testMailLink2(): Unit =
    assertMerged(
      "<test@example.com>\n" +
        "\n" +
        "test@example.com\n" +
        "\n",
      "<test@example.com>\n" +
        "\n",
      "test@example.com\n" +
        "\n"
    )

  test("Mail_Link21") {
    testMailLink2()
  }

  test("Mail_Link22") {
    testMailLink2()
    testMailLink2()
  }

  private def testMailLink3(): Unit =
    assertMerged(
      "<test@example.com>\n" +
        "\n" +
        "<test@example.com>\n" +
        "\n",
      "<test@example.com>\n" +
        "\n",
      "<test@example.com>\n" +
        "\n"
    )

  test("Mail_Link31") {
    testMailLink3()
  }

  test("Mail_Link32") {
    testMailLink3()
    testMailLink3()
  }

  private def testHtmlPreservationAutoLink0(): Unit =
    assertMerged(
      "http://example.com\n" +
        "\n" +
        "http://example.com\n" +
        "\n",
      "http://example.com\n" +
        "\n",
      "http://example.com\n" +
        "\n"
    )

  test("HtmlPreservationAutoLink01") {
    testHtmlPreservationAutoLink0()
  }

  test("HtmlPreservationAutoLink02") {
    testHtmlPreservationAutoLink0()
    testHtmlPreservationAutoLink0()
  }

  private def testHtmlPreservationAutoLink1(): Unit =
    assertMerged(
      "<http://example.com>\n" +
        "\n" +
        "http://example.com\n" +
        "\n",
      "<http://example.com>\n" +
        "\n",
      "http://example.com\n" +
        "\n"
    )

  test("HtmlPreservationAutoLink11") {
    testHtmlPreservationAutoLink1()
  }

  test("HtmlPreservationAutoLink12") {
    testHtmlPreservationAutoLink1()
    testHtmlPreservationAutoLink1()
  }

  private def testHtmlPreservationAutoLink2(): Unit =
    assertMerged(
      "http://example.com\n" +
        "\n" +
        "<http://example.com>\n" +
        "\n",
      "http://example.com\n" +
        "\n",
      "<http://example.com>\n" +
        "\n"
    )

  test("HtmlPreservationAutoLink21") {
    testHtmlPreservationAutoLink2()
  }

  test("HtmlPreservationAutoLink22") {
    testHtmlPreservationAutoLink2()
    testHtmlPreservationAutoLink2()
  }

  private def testHtmlPreservationAutoLink3(): Unit =
    assertMerged(
      "<http://example.com>\n" +
        "\n" +
        "<http://example.com>\n" +
        "\n",
      "<http://example.com>\n" +
        "\n",
      "<http://example.com>\n" +
        "\n"
    )

  test("HtmlPreservationAutoLink31") {
    testHtmlPreservationAutoLink3()
  }

  test("HtmlPreservationAutoLink32") {
    testHtmlPreservationAutoLink3()
    testHtmlPreservationAutoLink3()
  }
}
