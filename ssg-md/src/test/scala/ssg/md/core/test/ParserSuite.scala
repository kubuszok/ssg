/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../parser/ParserTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package core
package test

import ssg.md.ast._
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.parser.block._
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.ast.{ Block, Node }
import ssg.md.util.data.{ DataHolder, MutableDataSet, SharedDataKeys }
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.mappers.SpecialLeadInStartsWithCharsHandler

import java.io.{ InputStreamReader, StringReader }
import java.nio.charset.StandardCharsets

import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

final class ParserSuite extends munit.FunSuite {

  test("emptyReaderTest") {
    val parser    = Parser.builder().build()
    val document1 = parser.parseReader(new StringReader(""))
    assert(!document1.hasChildren())
  }

  test("ioReaderTest") {
    val parser = Parser.builder().build()

    val specResource = ResourceLocation.of(classOf[ComboCoreSpecTest], ComboCoreSpecTest.SPEC_RESOURCE)
    val input1       = specResource.resourceInputStream
    val reader       = new InputStreamReader(input1, StandardCharsets.UTF_8)
    val document1    = parser.parseReader(reader)

    val spec      = specResource.resourceText
    val document2 = parser.parse(spec)

    val renderer = HtmlRenderer.builder().escapeHtml(true).build()
    assertEquals(renderer.render(document2), renderer.render(document1))
  }

  test("customBlockParserFactory") {
    val parser = Parser.builder().customBlockParserFactory(new DashBlockParserFactory()).build()

    // The dashes would normally be a ThematicBreak
    val document = parser.parse("hey\n\n---\n")

    assert(document.getFirstChild().isInstanceOf[Paragraph])
    assertEquals(
      document.getFirstChild().getFirstChild().asInstanceOf[Text].chars.toString,
      "hey"
    )
    assert(document.getLastChild().isInstanceOf[DashBlock])
  }

  test("indentation") {
    val given_   = " - 1 space\n   - 3 spaces\n     - 5 spaces\n\t - tab + space"
    val parser   = Parser.builder().build()
    val document = parser.parse(given_)

    assert(document.getFirstChild().isInstanceOf[BulletList], "Document first child should be BulletList")
    assertEquals(document.getLineCount(), 4, "Document line count")

    var list = document.getFirstChild() // first level list
    assertEquals(list.getFirstChild(), list.getLastChild(), "expect one child")
    assertEquals(firstText(list.getFirstChild()), "1 space")
    assertEquals(list.getStartLineNumber(), 0, "node start line number")
    assertEquals(list.getEndLineNumber(), 3, "node end line number")

    list = list.getFirstChild().getLastChild() // second level list
    assertEquals(list.getFirstChild(), list.getLastChild(), "expect one child")
    assertEquals(firstText(list.getFirstChild()), "3 spaces")
    assertEquals(list.getStartLineNumber(), 1, "node start line number")
    assertEquals(list.getEndLineNumber(), 3, "node end line number")

    list = list.getFirstChild().getLastChild() // third level list
    assertEquals(firstText(list.getFirstChild()), "5 spaces")
    assertEquals(firstText(list.getFirstChild().getNext()), "tab + space")
    assertEquals(list.getStartLineNumber(), 2, "node start line number")
    assertEquals(list.getEndLineNumber(), 3, "node end line number")
  }

  test("indentationWithLines") {
    val given_   = " - 1 space\n   - 3 spaces\n     - 5 spaces\n\t - tab + space"
    val options  = new MutableDataSet().set(Parser.TRACK_DOCUMENT_LINES, java.lang.Boolean.valueOf(true))
    val parser   = Parser.builder(options).build()
    val document = parser.parse(given_)

    assert(document.getFirstChild().isInstanceOf[BulletList], "Document first child should be BulletList")
    assertEquals(document.getLineCount(), 4, "Document line count")

    var list = document.getFirstChild() // first level list
    assertEquals(list.getFirstChild(), list.getLastChild(), "expect one child")
    assertEquals(firstText(list.getFirstChild()), "1 space")
    assertEquals(list.getStartLineNumber(), 0, "node start line number")
    assertEquals(list.getEndLineNumber(), 3, "node end line number")

    list = list.getFirstChild().getLastChild() // second level list
    assertEquals(list.getFirstChild(), list.getLastChild(), "expect one child")
    assertEquals(firstText(list.getFirstChild()), "3 spaces")
    assertEquals(list.getStartLineNumber(), 1, "node start line number")
    assertEquals(list.getEndLineNumber(), 3, "node end line number")

    list = list.getFirstChild().getLastChild() // third level list
    assertEquals(firstText(list.getFirstChild()), "5 spaces")
    assertEquals(firstText(list.getFirstChild().getNext()), "tab + space")
    assertEquals(list.getStartLineNumber(), 2, "node start line number")
    assertEquals(list.getEndLineNumber(), 3, "node end line number")
  }

  test("blockquotesWithLfLineBreaks") {
    // ---------------------- --1------ ---2------ ---3--------
    // --------------01234567 890123456 7890123456 789012345678
    val given_   = "> line1\n> line2 \n> line3  \n> line4    \n"
    val parser   = Parser.builder().build()
    val document = parser.parse(given_)

    assert(document.getFirstChild().isInstanceOf[BlockQuote])
    assert(document.getFirstChild().getFirstChild().isInstanceOf[Paragraph])
    val it = document.getFirstChild().getFirstChild().getChildIterator()

    assert(it.hasNext())
    var node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line1")
    assertEquals(node.getStartOffset(), 2)
    assertEquals(node.getEndOffset(), 7)

    assert(it.hasNext())
    node = it.next()
    assert(node.isInstanceOf[SoftLineBreak])
    assertEquals(node.getStartOffset(), 7)
    assertEquals(node.getEndOffset(), 8)

    assert(it.hasNext())
    node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line2")
    assertEquals(node.getStartOffset(), 10)
    assertEquals(node.getEndOffset(), 15)

    assert(it.hasNext())
    node = it.next()
    assert(node.isInstanceOf[SoftLineBreak])
    assertEquals(node.getStartOffset(), 16)
    assertEquals(node.getEndOffset(), 17)

    assert(it.hasNext())
    node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line3")
    assertEquals(node.getStartOffset(), 19)
    assertEquals(node.getEndOffset(), 24)

    assert(it.hasNext())
    node = it.next()
    assert(node.isInstanceOf[HardLineBreak])
    assertEquals(node.getStartOffset(), 24)
    assertEquals(node.getEndOffset(), 27)

    assert(it.hasNext())
    node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line4")
    assertEquals(node.getStartOffset(), 29)
    assertEquals(node.getEndOffset(), 34)

    assert(!it.hasNext())
  }

  test("blockquotesWithCrLineBreaks") {
    // ---------------------- --1------ ---2------ ---3--------
    // --------------01234567 890123456 7890123456 789012345678
    val given_   = "> line1\r> line2 \r> line3  \r> line4    \r"
    val parser   = Parser.builder().build()
    val document = parser.parse(given_)

    assert(document.getFirstChild().isInstanceOf[BlockQuote])
    assert(document.getFirstChild().getFirstChild().isInstanceOf[Paragraph])
    val it = document.getFirstChild().getFirstChild().getChildIterator()

    assert(it.hasNext())
    var node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line1")
    assertEquals(node.getStartOffset(), 2)
    assertEquals(node.getEndOffset(), 7)

    assert(it.hasNext())
    node = it.next()
    assert(node.isInstanceOf[SoftLineBreak])
    assertEquals(node.getStartOffset(), 7)
    assertEquals(node.getEndOffset(), 8)

    assert(it.hasNext())
    node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line2")
    assertEquals(node.getStartOffset(), 10)
    assertEquals(node.getEndOffset(), 15)

    assert(it.hasNext())
    node = it.next()
    assert(node.isInstanceOf[SoftLineBreak])
    assertEquals(node.getStartOffset(), 16)
    assertEquals(node.getEndOffset(), 17)

    assert(it.hasNext())
    node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line3")
    assertEquals(node.getStartOffset(), 19)
    assertEquals(node.getEndOffset(), 24)

    assert(it.hasNext())
    node = it.next()
    assert(node.isInstanceOf[HardLineBreak])
    assertEquals(node.getStartOffset(), 24)
    assertEquals(node.getEndOffset(), 27)

    assert(it.hasNext())
    node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line4")
    assertEquals(node.getStartOffset(), 29)
    assertEquals(node.getEndOffset(), 34)

    assert(!it.hasNext())
  }

  test("blockquotesWithCrLfLineBreaks") {
    // ---------------------- - -1------- - -2-------- - 3---------4- -
    // --------------01234567 8 901234567 8 9012345678 9 012345678901 2
    val given_   = "> line1\r\n> line2 \r\n> line3  \r\n> line4    \r\n"
    val parser   = Parser.builder().build()
    val document = parser.parse(given_)

    assert(document.getFirstChild().isInstanceOf[BlockQuote])
    assert(document.getFirstChild().getFirstChild().isInstanceOf[Paragraph])
    val it = document.getFirstChild().getFirstChild().getChildIterator()

    assert(it.hasNext())
    var node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line1")
    assertEquals(node.getStartOffset(), 2)
    assertEquals(node.getEndOffset(), 7)

    assert(it.hasNext())
    node = it.next()
    assert(node.isInstanceOf[SoftLineBreak])
    assertEquals(node.getStartOffset(), 7)
    assertEquals(node.getEndOffset(), 9)

    assert(it.hasNext())
    node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line2")
    assertEquals(node.getStartOffset(), 11)
    assertEquals(node.getEndOffset(), 16)

    assert(it.hasNext())
    node = it.next()
    assert(node.isInstanceOf[SoftLineBreak])
    assertEquals(node.getStartOffset(), 17)
    assertEquals(node.getEndOffset(), 19)

    assert(it.hasNext())
    node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line3")
    assertEquals(node.getStartOffset(), 21)
    assertEquals(node.getEndOffset(), 26)

    assert(it.hasNext())
    node = it.next()
    assert(node.isInstanceOf[HardLineBreak])
    assertEquals(node.getStartOffset(), 26)
    assertEquals(node.getEndOffset(), 30)

    assert(it.hasNext())
    node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line4")
    assertEquals(node.getStartOffset(), 32)
    assertEquals(node.getEndOffset(), 37)

    assert(!it.hasNext())
  }

  test("test_escapeCustom") {
    val parser = Parser.builder().specialLeadInHandler(SpecialLeadInStartsWithCharsHandler.create('$')).build()

    assertEquals(doEscape("abc", parser), "abc")
    assertEquals(doEscape("$", parser), "\\$")
    assertEquals(doEscape("$abc", parser), "\\$abc")

    assertEquals(doUnEscape("abc", parser), "abc")
    assertEquals(doUnEscape("\\$", parser), "$")
    assertEquals(doUnEscape("\\$abc", parser), "$abc")
  }

  test("test_escapeBlockQuote") {
    val parser = Parser.builder().build()

    assertEquals(doEscape("abc", parser), "abc")
    assertEquals(doEscape(">", parser), "\\>")
    assertEquals(doEscape(">abc", parser), "\\>abc")

    assertEquals(doUnEscape("abc", parser), "abc")
    assertEquals(doUnEscape("\\>", parser), ">")
    assertEquals(doUnEscape("\\>abc", parser), ">abc")
  }

  test("test_escapeHeading") {
    val parser = Parser.builder().build()

    assertEquals(doEscape("abc", parser), "abc")
    assertEquals(doEscape("#", parser), "\\#")
    assertEquals(doEscape("#abc", parser), "#abc")

    assertEquals(doUnEscape("abc", parser), "abc")
    assertEquals(doUnEscape("\\#", parser), "#")
    assertEquals(doUnEscape("\\#abc", parser), "\\#abc")
  }

  test("test_escapeHeadingNoAtxSpace") {
    val parser = Parser.builder(new MutableDataSet().set(Parser.HEADING_NO_ATX_SPACE, java.lang.Boolean.valueOf(true))).build()

    assertEquals(doEscape("abc", parser), "abc")
    assertEquals(doEscape("#", parser), "\\#")
    assertEquals(doEscape("#abc", parser), "\\#abc")

    assertEquals(doUnEscape("abc", parser), "abc")
    assertEquals(doUnEscape("\\#", parser), "#")
    assertEquals(doUnEscape("\\#abc", parser), "#abc")
  }

  test("test_escapeUnorderedList") {
    val parser = Parser.builder().build()

    assertEquals(doEscape("abc", parser), "abc")

    assertEquals(doEscape("+", parser), "\\+")
    assertEquals(doEscape("+abc", parser), "+abc")

    assertEquals(doEscape("-", parser), "\\-")
    assertEquals(doEscape("-abc", parser), "-abc")

    assertEquals(doEscape("*", parser), "\\*")
    assertEquals(doEscape("*abc", parser), "*abc")

    assertEquals(doUnEscape("\\+", parser), "+")
    assertEquals(doUnEscape("\\+abc", parser), "\\+abc")

    assertEquals(doUnEscape("\\-", parser), "-")
    assertEquals(doUnEscape("\\-abc", parser), "\\-abc")

    assertEquals(doUnEscape("\\*", parser), "*")
    assertEquals(doUnEscape("\\*abc", parser), "\\*abc")
  }

  test("test_escapeUnorderedListNoNumbered") {
    val parser = Parser.builder(new MutableDataSet().set(SharedDataKeys.ESCAPE_NUMBERED_LEAD_IN, java.lang.Boolean.valueOf(false))).build()

    assertEquals(doEscape("abc", parser), "abc")

    assertEquals(doEscape("+", parser), "\\+")
    assertEquals(doEscape("+abc", parser), "+abc")

    assertEquals(doEscape("-", parser), "\\-")
    assertEquals(doEscape("-abc", parser), "-abc")

    assertEquals(doEscape("*", parser), "\\*")
    assertEquals(doEscape("*abc", parser), "*abc")

    assertEquals(doUnEscape("\\+", parser), "+")
    assertEquals(doUnEscape("\\+abc", parser), "\\+abc")

    assertEquals(doUnEscape("\\-", parser), "-")
    assertEquals(doUnEscape("\\-abc", parser), "\\-abc")

    assertEquals(doUnEscape("\\*", parser), "*")
    assertEquals(doUnEscape("\\*abc", parser), "\\*abc")
  }

  test("test_escapeUnorderedListCustom") {
    val parser = Parser.builder(new MutableDataSet().set(Parser.LISTS_ITEM_PREFIX_CHARS, "$")).build()

    assertEquals(doEscape("abc", parser), "abc")

    assertEquals(doEscape("$", parser), "\\$")
    assertEquals(doEscape("$abc", parser), "$abc")

    assertEquals(doUnEscape("\\$", parser), "$")
    assertEquals(doUnEscape("\\$abc", parser), "\\$abc")

    assertEquals(doEscape("+", parser), "+")
    assertEquals(doEscape("+abc", parser), "+abc")

    assertEquals(doEscape("-", parser), "-")
    assertEquals(doEscape("-abc", parser), "-abc")

    assertEquals(doEscape("*", parser), "*")
    assertEquals(doEscape("*abc", parser), "*abc")

    assertEquals(doUnEscape("\\+", parser), "\\+")
    assertEquals(doUnEscape("\\+abc", parser), "\\+abc")

    assertEquals(doUnEscape("\\-", parser), "\\-")
    assertEquals(doUnEscape("\\-abc", parser), "\\-abc")

    assertEquals(doUnEscape("\\*", parser), "\\*")
    assertEquals(doUnEscape("\\*abc", parser), "\\*abc")
  }

  test("test_escapeOrderedList") {
    val parser = Parser.builder().build()

    assertEquals(doEscape("1", parser), "1")
    assertEquals(doEscape("2", parser), "2")
    assertEquals(doEscape("3", parser), "3")

    assertEquals(doEscape("", parser), "")
    assertEquals(doEscape(".", parser), ".")

    assertEquals(doEscape("1 ", parser), "1 ")
    assertEquals(doEscape("2 ", parser), "2 ")
    assertEquals(doEscape("3 ", parser), "3 ")

    assertEquals(doEscape("1.", parser), "1\\.")
    assertEquals(doEscape("1.abc", parser), "1.abc")

    assertEquals(doEscape("2.", parser), "2\\.")
    assertEquals(doEscape("2.abc", parser), "2.abc")

    assertEquals(doEscape("1)", parser), "1\\)")
    assertEquals(doEscape("1)abc", parser), "1)abc")

    assertEquals(doEscape("2)", parser), "2\\)")
    assertEquals(doEscape("2)abc", parser), "2)abc")

    assertEquals(doUnEscape("1\\", parser), "1\\")
    assertEquals(doUnEscape("2\\", parser), "2\\")
    assertEquals(doUnEscape("3\\", parser), "3\\")

    assertEquals(doUnEscape("\\", parser), "\\")
    assertEquals(doUnEscape("\\.", parser), "\\.")

    assertEquals(doUnEscape("1\\.", parser), "1.")
    assertEquals(doUnEscape("1\\.abc", parser), "1\\.abc")

    assertEquals(doUnEscape("2\\.", parser), "2.")
    assertEquals(doUnEscape("2\\.abc", parser), "2\\.abc")

    assertEquals(doUnEscape("1\\)", parser), "1)")
    assertEquals(doUnEscape("1\\)abc", parser), "1\\)abc")

    assertEquals(doUnEscape("2\\)", parser), "2)")
    assertEquals(doUnEscape("2\\)abc", parser), "2\\)abc")
  }

  test("test_escapeOrderedListNoNumbered") {
    val parser = Parser.builder(new MutableDataSet().set(SharedDataKeys.ESCAPE_NUMBERED_LEAD_IN, java.lang.Boolean.valueOf(false))).build()

    assertEquals(doEscape("1", parser), "1")
    assertEquals(doEscape("2", parser), "2")
    assertEquals(doEscape("3", parser), "3")

    assertEquals(doEscape("", parser), "")
    assertEquals(doEscape(".", parser), ".")

    assertEquals(doEscape("1 ", parser), "1 ")
    assertEquals(doEscape("2 ", parser), "2 ")
    assertEquals(doEscape("3 ", parser), "3 ")

    assertEquals(doEscape("1.", parser), "1.")
    assertEquals(doEscape("1.abc", parser), "1.abc")

    assertEquals(doEscape("2.", parser), "2.")
    assertEquals(doEscape("2.abc", parser), "2.abc")

    assertEquals(doEscape("1)", parser), "1)")
    assertEquals(doEscape("1)abc", parser), "1)abc")

    assertEquals(doEscape("2)", parser), "2)")
    assertEquals(doEscape("2)abc", parser), "2)abc")

    assertEquals(doUnEscape("1\\", parser), "1\\")
    assertEquals(doUnEscape("2\\", parser), "2\\")
    assertEquals(doUnEscape("3\\", parser), "3\\")

    assertEquals(doUnEscape("\\", parser), "\\")
    assertEquals(doUnEscape("\\.", parser), "\\.")

    assertEquals(doUnEscape("1\\.", parser), "1.")
    assertEquals(doUnEscape("1\\.abc", parser), "1\\.abc")

    assertEquals(doUnEscape("2\\.", parser), "2.")
    assertEquals(doUnEscape("2\\.abc", parser), "2\\.abc")

    assertEquals(doUnEscape("1\\)", parser), "1)")
    assertEquals(doUnEscape("1\\)abc", parser), "1\\)abc")

    assertEquals(doUnEscape("2\\)", parser), "2)")
    assertEquals(doUnEscape("2\\)abc", parser), "2\\)abc")
  }

  test("test_escapeOrderedListDotOnly") {
    val parser = Parser.builder(new MutableDataSet().set(Parser.LISTS_ORDERED_ITEM_DOT_ONLY, java.lang.Boolean.valueOf(true))).build()

    assertEquals(doEscape("1", parser), "1")
    assertEquals(doEscape("2", parser), "2")
    assertEquals(doEscape("3", parser), "3")

    assertEquals(doEscape("1.", parser), "1\\.")
    assertEquals(doEscape("1.abc", parser), "1.abc")

    assertEquals(doEscape("2.", parser), "2\\.")
    assertEquals(doEscape("2.abc", parser), "2.abc")

    assertEquals(doEscape("1)", parser), "1)")
    assertEquals(doEscape("1)abc", parser), "1)abc")

    assertEquals(doEscape("2)", parser), "2)")
    assertEquals(doEscape("2)abc", parser), "2)abc")

    assertEquals(doUnEscape("1\\", parser), "1\\")
    assertEquals(doUnEscape("2\\", parser), "2\\")
    assertEquals(doUnEscape("3\\", parser), "3\\")

    assertEquals(doUnEscape("1\\.", parser), "1.")
    assertEquals(doUnEscape("1\\.abc", parser), "1\\.abc")

    assertEquals(doUnEscape("2\\.", parser), "2.")
    assertEquals(doUnEscape("2\\.abc", parser), "2\\.abc")

    assertEquals(doUnEscape("1\\)", parser), "1\\)")
    assertEquals(doUnEscape("1\\)abc", parser), "1\\)abc")

    assertEquals(doUnEscape("2\\)", parser), "2\\)")
    assertEquals(doUnEscape("2\\)abc", parser), "2\\)abc")
  }

  test("test_escapeOrderedListDotOnlyNoNumbered") {
    val parser = Parser
      .builder(
        new MutableDataSet().set(Parser.LISTS_ORDERED_ITEM_DOT_ONLY, java.lang.Boolean.valueOf(true)).set(SharedDataKeys.ESCAPE_NUMBERED_LEAD_IN, java.lang.Boolean.valueOf(false))
      )
      .build()

    assertEquals(doEscape("1", parser), "1")
    assertEquals(doEscape("2", parser), "2")
    assertEquals(doEscape("3", parser), "3")

    assertEquals(doEscape("1.", parser), "1.")
    assertEquals(doEscape("1.abc", parser), "1.abc")

    assertEquals(doEscape("2.", parser), "2.")
    assertEquals(doEscape("2.abc", parser), "2.abc")

    assertEquals(doEscape("1)", parser), "1)")
    assertEquals(doEscape("1)abc", parser), "1)abc")

    assertEquals(doEscape("2)", parser), "2)")
    assertEquals(doEscape("2)abc", parser), "2)abc")

    assertEquals(doUnEscape("1\\", parser), "1\\")
    assertEquals(doUnEscape("2\\", parser), "2\\")
    assertEquals(doUnEscape("3\\", parser), "3\\")

    assertEquals(doUnEscape("1\\.", parser), "1.")
    assertEquals(doUnEscape("1\\.abc", parser), "1\\.abc")

    assertEquals(doUnEscape("2\\.", parser), "2.")
    assertEquals(doUnEscape("2\\.abc", parser), "2\\.abc")

    assertEquals(doUnEscape("1\\)", parser), "1\\)")
    assertEquals(doUnEscape("1\\)abc", parser), "1\\)abc")

    assertEquals(doUnEscape("2\\)", parser), "2\\)")
    assertEquals(doUnEscape("2\\)abc", parser), "2\\)abc")
  }

  // Helper methods

  private def doEscape(input: String, parser: Parser): String = {
    val baseSeq  = BasedSequence.of(input)
    val handlers = Parser.SPECIAL_LEAD_IN_HANDLERS.get(parser.options)
    val sb       = new StringBuilder()

    boundary[String] {
      for (handler <- handlers)
        if (handler.escape(baseSeq, parser.options, (cs: CharSequence) => sb.append(cs))) break(sb.toString())
      input
    }
  }

  private def doUnEscape(input: String, parser: Parser): String = {
    val baseSeq  = BasedSequence.of(input)
    val handlers = Parser.SPECIAL_LEAD_IN_HANDLERS.get(parser.options)
    val sb       = new StringBuilder()

    boundary[String] {
      for (handler <- handlers)
        if (handler.unEscape(baseSeq, parser.options, (cs: CharSequence) => sb.append(cs))) break(sb.toString())
      input
    }
  }

  private def firstText(n: Node): String = {
    var current = n
    while (!current.isInstanceOf[Text]) {
      assert(current != null, "Expected non-null node")
      current = current.getFirstChild()
    }
    current.chars.toString
  }

  // Custom block types for testing

  private class DashBlock extends Block {
    override def getSegments(): Array[BasedSequence] = Node.EMPTY_SEGMENTS
  }

  private class DashBlockParser(line: BasedSequence) extends AbstractBlockParser {
    private val dash: DashBlock = {
      val d = new DashBlock()
      d.chars = line
      d
    }

    override def getBlock(): Block = dash

    override def closeBlock(state: ParserState): Unit =
      dash.setCharsFromContent()

    override def tryContinue(state: ParserState): BlockContinue =
      BlockContinue.none()
  }

  class DashBlockParserFactory extends CustomBlockParserFactory {
    override def getAfterDependents():  scala.collection.mutable.Set[Class[?]] = scala.collection.mutable.HashSet.empty
    override def getBeforeDependents(): scala.collection.mutable.Set[Class[?]] = scala.collection.mutable.HashSet.empty
    override def affectsGlobalScope():  Boolean                                = false

    override def apply(options: DataHolder): BlockParserFactory =
      new BlockFactory(options)
  }

  private class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {
    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart =
      if (state.getLine().equals("---")) {
        BlockStart.of(Array(new DashBlockParser(state.getLine())))
      } else {
        BlockStart.none()
      }
  }
}
