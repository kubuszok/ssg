/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/.../TableTextCollectingVisitorTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package ext
package tables
package test

import ssg.md.ext.tables.TablesExtension
import ssg.md.parser.Parser
import ssg.md.util.ast.{ TextCollectingVisitor, TextContainer }
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Arrays
import scala.language.implicitConversions

final class TableTextCollectingVisitorTest extends munit.FunSuite {

  test("test_basic") {
    val options: DataHolder = new MutableDataSet()
      .set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create()))
      .toImmutable

    val parser   = Parser.builder(options).build()
    val markdown =
      "| First Header  | Second Header |\n" +
        "| ------------- | ------------- |\n" +
        "| Content Cell  | Content Cell  |\n" +
        "\n" +
        "| Left-aligned | Center-aligned | Right-aligned |\n" +
        "| :---         |     :---:      |          ---: |\n" +
        "| git status   | git status     | git status    |\n" +
        "| git diff     | git diff       | git diff      |\n"

    val document          = parser.parse(markdown)
    val collectingVisitor = new TextCollectingVisitor()
    val text              = collectingVisitor.collectAndGetText(document, TextContainer.F_ADD_SPACES_BETWEEN_NODES)

    assertEquals(
      text,
      "First Header Second Header\n" +
        "Content Cell Content Cell\n" +
        "\n" +
        "Left-aligned Center-aligned Right-aligned\n" +
        "git status git status git status\n" +
        "git diff git diff git diff\n"
    )
  }

  test("test_linkURL") {
    val options: DataHolder = new MutableDataSet()
      .set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create()))
      .toImmutable

    val parser   = Parser.builder(options).build()
    val markdown =
      "| First Header  | Second Header |\n" +
        "| ------------- | ------------- |\n" +
        "| Content Cell  | ![](image%20spaces.png)  |\n"

    val document          = parser.parse(markdown)
    val collectingVisitor = new TextCollectingVisitor()
    val text              = collectingVisitor.collectAndGetText(document, TextContainer.F_LINK_URL | TextContainer.F_ADD_SPACES_BETWEEN_NODES)

    assertEquals(
      text,
      "First Header Second Header\n" +
        "Content Cell image spaces.png\n"
    )
  }

  test("test_linkNodeText") {
    val options: DataHolder = new MutableDataSet()
      .set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create()))
      .toImmutable

    val parser   = Parser.builder(options).build()
    val markdown =
      "| First Header  | Second Header |\n" +
        "| ------------- | ------------- |\n" +
        "| **Content Cell**  | ![](image%20spaces.png)  |\n"

    val document          = parser.parse(markdown)
    val collectingVisitor = new TextCollectingVisitor()
    val text              = collectingVisitor.collectAndGetText(document, TextContainer.F_LINK_NODE_TEXT | TextContainer.F_ADD_SPACES_BETWEEN_NODES)

    assertEquals(
      text,
      "First Header Second Header\n" +
        "Content Cell ![](image%20spaces.png)\n"
    )
  }

  test("test_linkUrlNodeText") {
    val options: DataHolder = new MutableDataSet()
      .set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create()))
      .toImmutable

    val parser   = Parser.builder(options).build()
    val markdown =
      "| First Header  | Second Header |\n" +
        "| ------------- | ------------- |\n" +
        "| **Content Cell**  | ![](image%20spaces.png)  |\n"

    val document          = parser.parse(markdown)
    val collectingVisitor = new TextCollectingVisitor()
    val text              = collectingVisitor.collectAndGetText(document, TextContainer.F_LINK_URL | TextContainer.F_ADD_SPACES_BETWEEN_NODES)

    assertEquals(
      text,
      "First Header Second Header\n" +
        "Content Cell image spaces.png\n"
    )
  }
}
