/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-abbreviation/.../MergeAbbreviationsTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package abbreviation
package test

import ssg.md.ext.abbreviation.AbbreviationExtension
import ssg.md.formatter.Formatter
import ssg.md.parser.Parser
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class MergeAbbreviationsSuite extends munit.FunSuite {

  private val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Collections.singletonList(AbbreviationExtension.create()))
    .set(Parser.BLANK_LINES_IN_AST, true)
    .set(Parser.PARSE_INNER_HTML_COMMENTS, true)
    .set(Parser.HEADING_NO_ATX_SPACE, true)
    .set(AbbreviationExtension.MAKE_MERGED_ABBREVIATIONS_UNIQUE, true)
    .set(Formatter.MAX_TRAILING_BLANK_LINES, 0)
    .toImmutable

  private val OPTIONS_AS_IS: DataHolder = new MutableDataSet(OPTIONS)
    .set(AbbreviationExtension.MAKE_MERGED_ABBREVIATIONS_UNIQUE, false)
    .toImmutable

  private val FORMATTER:       Formatter = Formatter.builder(Nullable(OPTIONS)).build()
  private val FORMATTER_AS_IS: Formatter = Formatter.builder(Nullable(OPTIONS_AS_IS)).build()
  private val PARSER:          Parser    = Parser.builder(OPTIONS).build()

  private def assertMergedAsIs(expected: String, markdownSources: String*): Unit = {
    val documents = markdownSources.map(src => PARSER.parse(src)).toArray
    val mergedOutput = FORMATTER_AS_IS.mergeRender(documents, 1)
    assertEquals(mergedOutput, expected, "Merged results differ")
  }

  private def assertMerged(expected: String, markdownSources: String*): Unit = {
    val documents = markdownSources.map(src => PARSER.parse(src)).toArray
    val mergedOutput = FORMATTER.mergeRender(documents, 1)
    assertEquals(mergedOutput, expected, "Merged results differ")
  }

  private def testIdAttributeAsIs(): Unit = {
    assertMergedAsIs(
      "*[Abbr]: Abbreviation\n" +
        "\n" +
        "This has an Abbr embedded in it.\n" +
        "\n" +
        "*[Abbr]: Abbreviation\n" +
        "\n" +
        "This has an Abbr embedded in it.\n" +
        "\n",
      "*[Abbr]: Abbreviation\n" +
        "\n" +
        "This has an Abbr embedded in it.\n" +
        "\n",
      "*[Abbr]:Abbreviation\n" +
        "\n" +
        "This has an Abbr embedded in it.\n" +
        "\n"
    )
  }

  test("IdAttributeAsIs1") {
    testIdAttributeAsIs()
  }

  test("IdAttributeAsIs2") {
    testIdAttributeAsIs()
    testIdAttributeAsIs()
  }

  private def testIdAttributeConflict(): Unit = {
    assertMerged(
      "*[Abbr]: Abbreviation\n" +
        "\n" +
        "This has an Abbr embedded in it.\n" +
        "\n" +
        "*[Abbr1]: Abbreviation\n" +
        "\n" +
        "This has an Abbr1 embedded in it.\n" +
        "\n",
      "*[Abbr]: Abbreviation\n" +
        "\n" +
        "This has an Abbr embedded in it.\n" +
        "\n",
      "*[Abbr]:Abbreviation\n" +
        "\n" +
        "This has an Abbr embedded in it.\n" +
        "\n"
    )
  }

  test("IdAttributeConflict1") {
    testIdAttributeConflict()
  }

  test("IdAttributeConflict2") {
    testIdAttributeConflict()
    testIdAttributeConflict()
  }

  private def testUndefinedIdConflict(): Unit = {
    assertMerged(
      "*[Abbr]: Abbreviation\n" +
        "\n" +
        "This has an Abbr embedded in it.\n" +
        "\n" +
        "This has an Abbr1 embedded in it.\n" +
        "\n" +
        "*[Abbr1]: Abbreviation\n" +
        "\n" +
        "This has an Abbr1 embedded in it.\n" +
        "\n",
      "*[Abbr]: Abbreviation\n" +
        "\n" +
        "This has an Abbr embedded in it.\n" +
        "\n" +
        "This has an Abbr1 embedded in it.\n" +
        "\n",
      "*[Abbr]:Abbreviation\n" +
        "\n" +
        "This has an Abbr embedded in it.\n" +
        "\n"
    )
  }

  test("UndefinedIdConflict1") {
    testUndefinedIdConflict()
  }

  test("UndefinedIdConflict2") {
    testUndefinedIdConflict()
    testUndefinedIdConflict()
  }
}
