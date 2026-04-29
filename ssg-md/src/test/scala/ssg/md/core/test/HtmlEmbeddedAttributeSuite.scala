/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../html/HtmlEmbeddedAttributeTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package core
package test

import ssg.md.ast.Paragraph
import ssg.md.html.{ EmbeddedAttributeProvider, HtmlRenderer }
import ssg.md.parser.Parser
import ssg.md.parser.block.{ NodePostProcessor, NodePostProcessorFactory }
import ssg.md.util.ast.{ Document, Node, NodeTracker }
import ssg.md.util.data.{ DataHolder, MutableDataHolder, MutableDataSet }
import ssg.md.util.html.MutableAttributes
import ssg.md.util.sequence.BasedSequence

import java.util.Collections

import scala.language.implicitConversions

final class HtmlEmbeddedAttributeSuite extends munit.FunSuite {

  private var OPTIONS:  MutableDataSet = scala.compiletime.uninitialized
  private var PARSER:   Parser         = scala.compiletime.uninitialized
  private var RENDERER: HtmlRenderer   = scala.compiletime.uninitialized

  override def beforeEach(context: BeforeEach): Unit = {
    OPTIONS = new MutableDataSet()
    OPTIONS.set(Parser.EXTENSIONS, Collections.singletonList(TestNodePostProcessorExtension.create()))
    PARSER = Parser.builder(OPTIONS).build()
    RENDERER = HtmlRenderer.builder(OPTIONS).build()
  }

  test("embeddedAttributeProvider1") {
    assertEquals(
      RENDERER.render(PARSER.parse("![Figure 1. Some description here.](http://example.com/image.png)\n")),
      "<p class=\"caption\"><img src=\"http://example.com/image.png\" alt=\"Figure 1. Some description here.\" /></p>\n"
    )
  }

  test("embeddedAttributeProvider2") {
    assertEquals(
      RENDERER.render(
        PARSER.parse(
          "![bar]\n" +
            "\n[bar]: http://example.com/image.png 'Image Title'"
        )
      ),
      "<p class=\"caption\"><img src=\"http://example.com/image.png\" alt=\"bar\" title=\"Image Title\" /></p>\n"
    )
  }

  test("embeddedAttributeProvider3") {
    assertEquals(
      RENDERER.render(
        PARSER.parse(
          "![Figure 1. Some description here.][bar]\n" +
            "\n[bar]: http://example.com/image.png 'Image Title'"
        )
      ),
      "<p class=\"caption\"><img src=\"http://example.com/image.png\" alt=\"Figure 1. Some description here.\" title=\"Image Title\" /></p>\n"
    )
  }

  // Inner classes

  private class TestNodePostProcessor extends NodePostProcessor {
    override def process(state: NodeTracker, node: Node): Unit = {
      @annotation.nowarn("msg=unused") // paragraphText mirrors original local var
      val paragraphText = BasedSequence.NULL
      if (node.isInstanceOf[Paragraph]) {
        val attributes = new MutableAttributes()
        attributes.addValue("class", "caption")
        node.appendChild(new EmbeddedAttributeProvider.EmbeddedNodeAttributes(node, attributes))
      }
    }
  }

  private class TestNodeFactory(options: DataHolder) extends NodePostProcessorFactory(false) {
    addNodes(classOf[Paragraph])

    override def apply(document: Document): NodePostProcessor =
      new TestNodePostProcessor()
  }

  private object TestNodePostProcessorHelper {
    def factory(options: DataHolder): NodePostProcessorFactory =
      new TestNodeFactory(options)
  }

  /** An extension that registers a post processor which intentionally strips (replaces) specific link and image-link tokens after parsing.
    */
  private class TestNodePostProcessorExtension extends Parser.ParserExtension with HtmlRenderer.HtmlRendererExtension {
    override def rendererOptions(options: MutableDataHolder): Unit = {
      // add any configuration settings to options you want to apply to everything, here
    }

    override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit =
      htmlRendererBuilder.attributeProviderFactory(EmbeddedAttributeProvider.Factory)

    override def parserOptions(options: MutableDataHolder): Unit = {}

    override def extend(parserBuilder: Parser.Builder): Unit =
      parserBuilder.postProcessorFactory(TestNodePostProcessorHelper.factory(parserBuilder))
  }

  private object TestNodePostProcessorExtension {
    def create(): TestNodePostProcessorExtension = new TestNodePostProcessorExtension()
  }
}
