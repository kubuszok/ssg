/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../parser/ast/TextCollectingVisitorTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package core
package test

import ssg.md.parser.Parser
import ssg.md.util.ast.TextCollectingVisitor

import scala.language.implicitConversions

final class TextCollectingVisitorSuite extends munit.FunSuite {

  test("test_basic") {
    val parser            = Parser.builder().build()
    val document          = parser.parse("Test text")
    val collectingVisitor = new TextCollectingVisitor()
    val text              = collectingVisitor.collectAndGetText(document)
    assertEquals(text, "Test text")
  }

  test("test_emphasis") {
    val parser            = Parser.builder().build()
    val document          = parser.parse("Test text *emphasis*")
    val collectingVisitor = new TextCollectingVisitor()
    val text              = collectingVisitor.collectAndGetText(document)
    assertEquals(text, "Test text emphasis")
  }

  test("test_strong_emphasis") {
    val parser            = Parser.builder().build()
    val document          = parser.parse("Test text **emphasis**")
    val collectingVisitor = new TextCollectingVisitor()
    val text              = collectingVisitor.collectAndGetText(document)
    assertEquals(text, "Test text emphasis")
  }

  test("test_inline_code") {
    val parser            = Parser.builder().build()
    val document          = parser.parse("Test text `inline code`")
    val collectingVisitor = new TextCollectingVisitor()
    val text              = collectingVisitor.collectAndGetText(document)
    assertEquals(text, "Test text inline code")
  }

  test("test_fenced_code") {
    val parser            = Parser.builder().build()
    val document          = parser.parse("```info\nfenced code\nlines\n```")
    val collectingVisitor = new TextCollectingVisitor()
    val text              = collectingVisitor.collectAndGetText(document)
    assertEquals(text, "fenced code\nlines\n")
  }

  test("test_paragraphs") {
    val parser            = Parser.builder().build()
    val document          = parser.parse("paragraph1\nwith some lazy continuation\n\nparagraph2\nwith more text")
    val collectingVisitor = new TextCollectingVisitor()
    val text              = collectingVisitor.collectAndGetText(document)
    assertEquals(
      text,
      "paragraph1\n" +
        "with some lazy continuation\n" +
        "\n" +
        "paragraph2\n" +
        "with more text"
    )
  }

  test("test_paragraph_and_fenced_code_block") {
    val parser   = Parser.builder().build()
    val document = parser.parse(
      "before\n" +
        "\n" +
        "```\n" +
        "fenced code\n" +
        "block\n" +
        "```\n" +
        "\n" +
        "after"
    )
    val collectingVisitor = new TextCollectingVisitor()
    val text              = collectingVisitor.collectAndGetText(document)
    assertEquals(
      text,
      "before\n" +
        "\n" +
        "fenced code\n" +
        "block\n" +
        "\n" +
        "after"
    )
  }

  test("test_paragraph_and_indented_code_block") {
    val parser   = Parser.builder().build()
    val document = parser.parse(
      "before\n" +
        "\n" +
        "    indented code block\n" +
        "\n" +
        "after"
    )
    val collectingVisitor = new TextCollectingVisitor()
    val text              = collectingVisitor.collectAndGetText(document)
    assertEquals(
      text,
      "before\n" +
        "\n" +
        "indented code block\n" +
        "\n" +
        "after"
    )
  }
}
