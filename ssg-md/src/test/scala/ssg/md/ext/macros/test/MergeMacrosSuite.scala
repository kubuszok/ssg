/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-macros/.../MergeMacrosTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package macros
package test

import ssg.md.ext.macros.MacrosExtension
import ssg.md.formatter.Formatter
import ssg.md.parser.Parser
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class MergeMacrosSuite extends munit.FunSuite {

  private val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Collections.singletonList(MacrosExtension.create()))
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
      ">>>macro\n" +
        "simple text\n" +
        "<<<\n" +
        "\n" +
        "Plain text <<<macro>>>\n" +
        "\n" +
        ">>>macro1\n" +
        "simple text\n" +
        "<<<\n" +
        "\n" +
        "Plain text <<<macro1>>>\n" +
        "\n",
      ">>>macro\n" +
        "simple text\n" +
        "<<<\n" +
        "\n" +
        "Plain text <<<macro>>>\n" +
        "\n",
      ">>>macro\n" +
        "simple text\n" +
        "<<<\n" +
        "\n" +
        "Plain text <<<macro>>>\n" +
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
      ">>>macro\n" +
        "simple text\n" +
        "<<<\n" +
        "\n" +
        "Plain text <<<macro>>>\n" +
        "\n" +
        "Plain text <<<macro1>>>\n" +
        "\n" +
        ">>>macro1\n" +
        "simple text\n" +
        "<<<\n" +
        "\n" +
        "Plain text <<<macro1>>>\n" +
        "\n",
      ">>>macro\n" +
        "simple text\n" +
        "<<<\n" +
        "\n" +
        "Plain text <<<macro>>>\n" +
        "\n" +
        "Plain text <<<macro1>>>\n" +
        "\n",
      ">>>macro\n" +
        "simple text\n" +
        "<<<\n" +
        "\n" +
        "Plain text <<<macro>>>\n" +
        "\n"
    )

  test("UndefinedIdConflict1") {
    testUndefinedIdConflict()
  }

  test("UndefinedIdConflict2") {
    testUndefinedIdConflict()
    testUndefinedIdConflict()
  }
}
