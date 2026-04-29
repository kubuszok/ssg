/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../parser/DelimiterProcessorTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package core
package test

import ssg.md.ast.Text
import ssg.md.html.HtmlRenderer
import ssg.md.html.renderer.{ NodeRenderer, NodeRendererContext, NodeRendererFactory, NodeRenderingHandler }
import ssg.md.parser.Parser
import ssg.md.parser.InlineParser
import ssg.md.parser.core.delimiter.Delimiter
import ssg.md.parser.delimiter.{ DelimiterProcessor, DelimiterRun }
import ssg.md.test.util.TestUtils
import ssg.md.util.ast.{ DelimitedNode, Node }
import ssg.md.util.data.{ DataHolder, MutableDataSet }
import ssg.md.util.sequence.BasedSequence

import scala.collection.mutable
import scala.language.implicitConversions

final class DelimiterProcessorSuite extends munit.FunSuite {

  private val OPTIONS: DataHolder = new MutableDataSet().set(TestUtils.NO_FILE_EOL, false).toImmutable

  private val PARSER:   Parser       = Parser.builder(OPTIONS).customDelimiterProcessor(new AsymmetricDelimiterProcessor()).build()
  private val RENDERER: HtmlRenderer = HtmlRenderer.builder(OPTIONS).nodeRendererFactory(new UpperCaseNodeRendererFactory()).build()

  private def assertRendering(source: String, expectedHtml: String): Unit = {
    val document = PARSER.parse(source)
    val html     = RENDERER.render(document)
    assertEquals(html, expectedHtml)
  }

  test("delimiterProcessorWithInvalidDelimiterUse") {
    val parser = Parser.builder(OPTIONS).customDelimiterProcessor(new CustomDelimiterProcessor(':', 0)).customDelimiterProcessor(new CustomDelimiterProcessor(';', -1)).build()
    assertEquals(RENDERER.render(parser.parse(":test:")), "<p>:test:</p>\n")
    assertEquals(RENDERER.render(parser.parse(";test;")), "<p>;test;</p>\n")
  }

  test("asymmetricDelimiter") {
    assertRendering("{foo} bar", "<p>FOO bar</p>\n")
    assertRendering("f{oo ba}r", "<p>fOO BAr</p>\n")
    assertRendering("{{foo} bar", "<p>{FOO bar</p>\n")
    assertRendering("{foo}} bar", "<p>FOO} bar</p>\n")
    assertRendering("{{foo} bar}", "<p>FOO BAR</p>\n")
    assertRendering("{foo bar", "<p>{foo bar</p>\n")
    assertRendering("foo} bar", "<p>foo} bar</p>\n")
    assertRendering("}foo} bar", "<p>}foo} bar</p>\n")
    assertRendering("{foo{ bar", "<p>{foo{ bar</p>\n")
    assertRendering("}foo{ bar", "<p>}foo{ bar</p>\n")
  }

  // Inner classes

  private class CustomDelimiterProcessor(delimiterChar: Char, delimiterUse: Int) extends DelimiterProcessor {
    override def openingCharacter: Char = delimiterChar
    override def closingCharacter: Char = delimiterChar
    override def minLength:        Int  = 1

    override def getDelimiterUse(opener: DelimiterRun, closer: DelimiterRun): Int = delimiterUse

    override def canBeOpener(
      before:              String,
      after:               String,
      leftFlanking:        Boolean,
      rightFlanking:       Boolean,
      beforeIsPunctuation: Boolean,
      afterIsPunctuation:  Boolean,
      beforeIsWhitespace:  Boolean,
      afterIsWhiteSpace:   Boolean
    ): Boolean = leftFlanking

    override def canBeCloser(
      before:              String,
      after:               String,
      leftFlanking:        Boolean,
      rightFlanking:       Boolean,
      beforeIsPunctuation: Boolean,
      afterIsPunctuation:  Boolean,
      beforeIsWhitespace:  Boolean,
      afterIsWhiteSpace:   Boolean
    ): Boolean = rightFlanking

    override def skipNonOpenerCloser: Boolean = false

    override def unmatchedDelimiterNode(inlineParser: InlineParser, delimiter: DelimiterRun): Nullable[Node] =
      Nullable.empty

    override def process(opener: Delimiter, closer: Delimiter, delimitersUsed: Int): Unit = {}
  }

  private class AsymmetricDelimiterProcessor extends DelimiterProcessor {
    override def openingCharacter: Char = '{'
    override def closingCharacter: Char = '}'
    override def minLength:        Int  = 1

    override def getDelimiterUse(opener: DelimiterRun, closer: DelimiterRun): Int = 1

    override def unmatchedDelimiterNode(inlineParser: InlineParser, delimiter: DelimiterRun): Nullable[Node] =
      Nullable.empty

    override def canBeOpener(
      before:              String,
      after:               String,
      leftFlanking:        Boolean,
      rightFlanking:       Boolean,
      beforeIsPunctuation: Boolean,
      afterIsPunctuation:  Boolean,
      beforeIsWhitespace:  Boolean,
      afterIsWhiteSpace:   Boolean
    ): Boolean = leftFlanking

    override def canBeCloser(
      before:              String,
      after:               String,
      leftFlanking:        Boolean,
      rightFlanking:       Boolean,
      beforeIsPunctuation: Boolean,
      afterIsPunctuation:  Boolean,
      beforeIsWhitespace:  Boolean,
      afterIsWhiteSpace:   Boolean
    ): Boolean = rightFlanking

    override def skipNonOpenerCloser: Boolean = false

    override def process(opener: Delimiter, closer: Delimiter, delimitersUsed: Int): Unit = {
      val content = new UpperCaseNode(opener.tailChars(delimitersUsed), BasedSequence.NULL, closer.leadChars(delimitersUsed))
      opener.moveNodesBetweenDelimitersTo(content, closer)
    }
  }

  private class UpperCaseNode extends Node with DelimitedNode {
    var openingMarker: BasedSequence = BasedSequence.NULL
    var text:          BasedSequence = BasedSequence.NULL
    var closingMarker: BasedSequence = BasedSequence.NULL

    override def segments: Array[BasedSequence] = Array(openingMarker, text, closingMarker)

    def this(chars: BasedSequence) = {
      this()
      this.chars = chars
    }

    def this(openingMarker: BasedSequence, text: BasedSequence, closingMarker: BasedSequence) = {
      this()
      this.chars = openingMarker.baseSubSequence(openingMarker.startOffset, closingMarker.endOffset)
      this.openingMarker = openingMarker
      this.text = text
      this.closingMarker = closingMarker
    }
  }

  private class UpperCaseNodeRendererFactory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer =
      new UpperCaseNodeRenderer(options)
  }

  private class UpperCaseNodeRenderer(options: DataHolder) extends NodeRenderer {
    override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
      val set = mutable.HashSet.empty[NodeRenderingHandler[?]]
      set.add(
        new NodeRenderingHandler[UpperCaseNode](
          classOf[UpperCaseNode],
          new NodeRenderingHandler.CustomNodeRenderer[UpperCaseNode] {
            override def render(node: UpperCaseNode, context: NodeRendererContext, html: ssg.md.html.HtmlWriter): Unit = {
              var child = node.firstChild
              while (child.isDefined) {
                val c = child.get
                c match {
                  case textNode: Text =>
                    textNode.chars = textNode.chars.toUpperCase()
                  case _ =>
                }
                context.render(c)
                child = c.next
              }
            }
          }
        )
      )
      Nullable(set.toSet)
    }
  }
}
