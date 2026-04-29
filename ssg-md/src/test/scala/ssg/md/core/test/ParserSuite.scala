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
    assert(!document1.hasChildren)
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

    assert(document.firstChild.get.isInstanceOf[Paragraph])
    assertEquals(
      document.firstChild.get.firstChild.get.asInstanceOf[Text].chars.toString,
      "hey"
    )
    assert(document.lastChild.get.isInstanceOf[DashBlock])
  }

  test("indentation") {
    val given_   = " - 1 space\n   - 3 spaces\n     - 5 spaces\n\t - tab + space"
    val parser   = Parser.builder().build()
    val document = parser.parse(given_)

    assert(document.firstChild.get.isInstanceOf[BulletList], "Document first child should be BulletList")
    assertEquals(document.lineCount, 4, "Document line count")

    var list = document.firstChild.get // first level list
    assertEquals(list.firstChild.get, list.lastChild.get, "expect one child")
    assertEquals(firstText(list.firstChild.get), "1 space")
    assertEquals(list.startLineNumber, 0, "node start line number")
    assertEquals(list.endLineNumber, 3, "node end line number")

    list = list.firstChild.get.lastChild.get // second level list
    assertEquals(list.firstChild.get, list.lastChild.get, "expect one child")
    assertEquals(firstText(list.firstChild.get), "3 spaces")
    assertEquals(list.startLineNumber, 1, "node start line number")
    assertEquals(list.endLineNumber, 3, "node end line number")

    list = list.firstChild.get.lastChild.get // third level list
    assertEquals(firstText(list.firstChild.get), "5 spaces")
    assertEquals(firstText(list.firstChild.get.next.get), "tab + space")
    assertEquals(list.startLineNumber, 2, "node start line number")
    assertEquals(list.endLineNumber, 3, "node end line number")
  }

  test("indentationWithLines") {
    val given_   = " - 1 space\n   - 3 spaces\n     - 5 spaces\n\t - tab + space"
    val options  = new MutableDataSet().set(Parser.TRACK_DOCUMENT_LINES, true)
    val parser   = Parser.builder(options).build()
    val document = parser.parse(given_)

    assert(document.firstChild.get.isInstanceOf[BulletList], "Document first child should be BulletList")
    assertEquals(document.lineCount, 4, "Document line count")

    var list = document.firstChild.get // first level list
    assertEquals(list.firstChild.get, list.lastChild.get, "expect one child")
    assertEquals(firstText(list.firstChild.get), "1 space")
    assertEquals(list.startLineNumber, 0, "node start line number")
    assertEquals(list.endLineNumber, 3, "node end line number")

    list = list.firstChild.get.lastChild.get // second level list
    assertEquals(list.firstChild.get, list.lastChild.get, "expect one child")
    assertEquals(firstText(list.firstChild.get), "3 spaces")
    assertEquals(list.startLineNumber, 1, "node start line number")
    assertEquals(list.endLineNumber, 3, "node end line number")

    list = list.firstChild.get.lastChild.get // third level list
    assertEquals(firstText(list.firstChild.get), "5 spaces")
    assertEquals(firstText(list.firstChild.get.next.get), "tab + space")
    assertEquals(list.startLineNumber, 2, "node start line number")
    assertEquals(list.endLineNumber, 3, "node end line number")
  }

  test("blockquotesWithLfLineBreaks") {
    // ---------------------- --1------ ---2------ ---3--------
    // --------------01234567 890123456 7890123456 789012345678
    val given_   = "> line1\n> line2 \n> line3  \n> line4    \n"
    val parser   = Parser.builder().build()
    val document = parser.parse(given_)

    assert(document.firstChild.get.isInstanceOf[BlockQuote])
    assert(document.firstChild.get.firstChild.get.isInstanceOf[Paragraph])
    val it = document.firstChild.get.firstChild.get.childIterator

    assert(it.hasNext)
    var node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line1")
    assertEquals(node.startOffset, 2)
    assertEquals(node.endOffset, 7)

    assert(it.hasNext)
    node = it.next()
    assert(node.isInstanceOf[SoftLineBreak])
    assertEquals(node.startOffset, 7)
    assertEquals(node.endOffset, 8)

    assert(it.hasNext)
    node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line2")
    assertEquals(node.startOffset, 10)
    assertEquals(node.endOffset, 15)

    assert(it.hasNext)
    node = it.next()
    assert(node.isInstanceOf[SoftLineBreak])
    assertEquals(node.startOffset, 16)
    assertEquals(node.endOffset, 17)

    assert(it.hasNext)
    node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line3")
    assertEquals(node.startOffset, 19)
    assertEquals(node.endOffset, 24)

    assert(it.hasNext)
    node = it.next()
    assert(node.isInstanceOf[HardLineBreak])
    assertEquals(node.startOffset, 24)
    assertEquals(node.endOffset, 27)

    assert(it.hasNext)
    node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line4")
    assertEquals(node.startOffset, 29)
    assertEquals(node.endOffset, 34)

    assert(!it.hasNext)
  }

  test("blockquotesWithCrLineBreaks") {
    // ---------------------- --1------ ---2------ ---3--------
    // --------------01234567 890123456 7890123456 789012345678
    val given_   = "> line1\r> line2 \r> line3  \r> line4    \r"
    val parser   = Parser.builder().build()
    val document = parser.parse(given_)

    assert(document.firstChild.get.isInstanceOf[BlockQuote])
    assert(document.firstChild.get.firstChild.get.isInstanceOf[Paragraph])
    val it = document.firstChild.get.firstChild.get.childIterator

    assert(it.hasNext)
    var node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line1")
    assertEquals(node.startOffset, 2)
    assertEquals(node.endOffset, 7)

    assert(it.hasNext)
    node = it.next()
    assert(node.isInstanceOf[SoftLineBreak])
    assertEquals(node.startOffset, 7)
    assertEquals(node.endOffset, 8)

    assert(it.hasNext)
    node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line2")
    assertEquals(node.startOffset, 10)
    assertEquals(node.endOffset, 15)

    assert(it.hasNext)
    node = it.next()
    assert(node.isInstanceOf[SoftLineBreak])
    assertEquals(node.startOffset, 16)
    assertEquals(node.endOffset, 17)

    assert(it.hasNext)
    node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line3")
    assertEquals(node.startOffset, 19)
    assertEquals(node.endOffset, 24)

    assert(it.hasNext)
    node = it.next()
    assert(node.isInstanceOf[HardLineBreak])
    assertEquals(node.startOffset, 24)
    assertEquals(node.endOffset, 27)

    assert(it.hasNext)
    node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line4")
    assertEquals(node.startOffset, 29)
    assertEquals(node.endOffset, 34)

    assert(!it.hasNext)
  }

  test("blockquotesWithCrLfLineBreaks") {
    // ---------------------- - -1------- - -2-------- - 3---------4- -
    // --------------01234567 8 901234567 8 9012345678 9 012345678901 2
    val given_   = "> line1\r\n> line2 \r\n> line3  \r\n> line4    \r\n"
    val parser   = Parser.builder().build()
    val document = parser.parse(given_)

    assert(document.firstChild.get.isInstanceOf[BlockQuote])
    assert(document.firstChild.get.firstChild.get.isInstanceOf[Paragraph])
    val it = document.firstChild.get.firstChild.get.childIterator

    assert(it.hasNext)
    var node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line1")
    assertEquals(node.startOffset, 2)
    assertEquals(node.endOffset, 7)

    assert(it.hasNext)
    node = it.next()
    assert(node.isInstanceOf[SoftLineBreak])
    assertEquals(node.startOffset, 7)
    assertEquals(node.endOffset, 9)

    assert(it.hasNext)
    node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line2")
    assertEquals(node.startOffset, 11)
    assertEquals(node.endOffset, 16)

    assert(it.hasNext)
    node = it.next()
    assert(node.isInstanceOf[SoftLineBreak])
    assertEquals(node.startOffset, 17)
    assertEquals(node.endOffset, 19)

    assert(it.hasNext)
    node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line3")
    assertEquals(node.startOffset, 21)
    assertEquals(node.endOffset, 26)

    assert(it.hasNext)
    node = it.next()
    assert(node.isInstanceOf[HardLineBreak])
    assertEquals(node.startOffset, 26)
    assertEquals(node.endOffset, 30)

    assert(it.hasNext)
    node = it.next()
    assert(node.isInstanceOf[Text])
    assertEquals(node.chars.toString, "line4")
    assertEquals(node.startOffset, 32)
    assertEquals(node.endOffset, 37)

    assert(!it.hasNext)
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
    val parser = Parser.builder(new MutableDataSet().set(Parser.HEADING_NO_ATX_SPACE, true)).build()

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
    val parser = Parser.builder(new MutableDataSet().set(SharedDataKeys.ESCAPE_NUMBERED_LEAD_IN, false)).build()

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
    val parser = Parser.builder(new MutableDataSet().set(SharedDataKeys.ESCAPE_NUMBERED_LEAD_IN, false)).build()

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
    val parser = Parser.builder(new MutableDataSet().set(Parser.LISTS_ORDERED_ITEM_DOT_ONLY, true)).build()

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
        new MutableDataSet().set(Parser.LISTS_ORDERED_ITEM_DOT_ONLY, true).set(SharedDataKeys.ESCAPE_NUMBERED_LEAD_IN, false)
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
      current = current.firstChild.get
    }
    current.chars.toString
  }

  // Custom block types for testing

  private class DashBlock extends Block {
    override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS
  }

  private class DashBlockParser(line: BasedSequence) extends AbstractBlockParser {
    private val dash: DashBlock = {
      val d = new DashBlock()
      d.chars = line
      d
    }

    override def getBlock: Block = dash

    override def closeBlock(state: ParserState): Unit =
      dash.setCharsFromContent()

    override def tryContinue(state: ParserState): Nullable[BlockContinue] =
      BlockContinue.none()
  }

  class DashBlockParserFactory extends CustomBlockParserFactory {
    override def afterDependents:    Nullable[Set[Class[?]]] = Nullable.empty
    override def beforeDependents:   Nullable[Set[Class[?]]] = Nullable.empty
    override def affectsGlobalScope: Boolean                 = false

    override def apply(options: DataHolder): BlockParserFactory =
      new BlockFactory(options)
  }

  private class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {
    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] =
      if (state.line.equals("---")) {
        Nullable(BlockStart.of(new DashBlockParser(state.line)))
      } else {
        BlockStart.none()
      }
  }
}
