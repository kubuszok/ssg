/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Basic diagnostic test to isolate parser/renderer issues.
 */
package ssg
package md
package core
package test

import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser

import scala.language.implicitConversions

final class BasicParseTest extends munit.FunSuite {

  test("parse empty string") {
    val parser = Parser.builder().build()
    val doc = parser.parse("")
    assertNotEquals(doc, null)
  }

  test("block parser factories are registered") {
    // Check that core block parser factories are actually created
    val options = new ssg.md.util.data.MutableDataSet().toImmutable
    val factories = ssg.md.parser.internal.DocumentParser.calculateBlockParserFactories(options, Nil)
    println(s"Number of block parser factories: ${factories.size}")
    factories.foreach(f => println(s"  Factory: ${f.getClass.getSimpleName}"))
    assert(factories.nonEmpty, "Block parser factories should not be empty")
  }

  test("parse simple paragraph") {
    val parser = Parser.builder().build()
    val renderer = HtmlRenderer.builder().build()

    // Debug: test parsing chain
    val input = ssg.md.util.sequence.BasedSequence.of("hello")
    println(s"Input: '${input}', length: ${input.length()}")

    val doc = parser.parse("hello")

    // Debug: check AST
    val astVisitor = new ssg.md.test.util.AstCollectingVisitor()
    val ast = astVisitor.collectAndGetAstText(doc)
    println(s"AST:\n$ast")
    println(s"Doc children: ${doc.hasChildren}")
    println(s"Doc firstChild: ${doc.firstChild}")
    println(s"Doc chars: '${doc.chars}'")
    println(s"Doc chars length: ${doc.chars.length()}")

    // Check if firstChild is a Paragraph
    if (doc.firstChild.isDefined) {
      val child = doc.firstChild.get
      println(s"First child type: ${child.getClass.getSimpleName}")
      println(s"First child chars: '${child.chars}'")
    }

    val html = renderer.render(doc)
    println(s"HTML output: '$html'")
    println(s"HTML length: ${html.length}")
    assertEquals(html, "<p>hello</p>\n")
  }

  test("parse heading") {
    val parser = Parser.builder().build()
    val renderer = HtmlRenderer.builder().build()
    val doc = parser.parse("# Hello")

    // Debug: check Heading node
    val heading = doc.firstChild.get
    println(s"Heading type: ${heading.getClass.getSimpleName}")
    println(s"Heading chars: '${heading.chars}'")
    println(s"Heading chars class: ${heading.chars.getClass.getSimpleName}")

    // Debug: render to StringBuilder directly
    val sb = new java.lang.StringBuilder()
    renderer.render(doc, sb)
    println(s"StringBuilder output: '${sb.toString}'")
    println(s"StringBuilder length: ${sb.length()}")

    // Debug: check LineAppendableImpl internals
    val htmlWriter = new ssg.md.html.HtmlWriter(
      Nullable(new java.lang.StringBuilder(): Appendable),
      0, 0, false, false
    )
    val lineAppendable = htmlWriter.asInstanceOf[ssg.md.util.sequence.LineAppendable]
    println(s"LineAppendable builder class: ${lineAppendable.getBuilder.getClass.getSimpleName}")

    // Also try render(node) which returns String
    val html = renderer.render(doc)
    println(s"render() output: '$html'")
    println(s"render() output class: ${html.getClass.getSimpleName}")
    for (i <- 0 until math.min(html.length, 20)) {
      println(s"  char[$i] = '${html.charAt(i)}' (${html.charAt(i).toInt})")
    }

    assertEquals(html, "<h1>Hello</h1>\n")
  }

  test("BasedSequence.of toString") {
    val seq = ssg.md.util.sequence.BasedSequence.of("hello world")
    assertEquals(seq.toString, "hello world")
  }

  test("SubSequence toString") {
    val seq = ssg.md.util.sequence.BasedSequence.of("hello world")
    assertEquals(seq.toString, "hello world")
    assertEquals(seq.length(), 11)
    assertEquals(seq.charAt(0), 'h')
  }

  test("SubSequence subSequence toString") {
    val seq = ssg.md.util.sequence.BasedSequence.of("hello world")
    val sub = seq.subSequence(0, 5)
    assertEquals(sub.toString, "hello")
  }

  test("Empty URL link AST includes url and pageRef fields") {
    val parser = Parser.builder().build()
    val doc = parser.parse("[link]()")
    val link = doc.firstChild.get.firstChild.get.asInstanceOf[ssg.md.ast.Link]
    println(s"link.url = '${link.url}', isNull=${link.url.isNull}, isNotNull=${link.url.isNotNull}, length=${link.url.length()}, startOffset=${link.url.startOffset}, endOffset=${link.url.endOffset}")
    println(s"link.pageRef = '${link.pageRef}', isNull=${link.pageRef.isNull}, isNotNull=${link.pageRef.isNotNull}")
    println(s"link.url eq BasedSequence.NULL: ${link.url eq ssg.md.util.sequence.BasedSequence.NULL}")
    val ast = new ssg.md.test.util.AstCollectingVisitor().collectAndGetAstText(doc)
    println(s"Empty URL link AST:\n$ast")
    assert(ast.contains("url:"), s"AST should contain url field:\n$ast")
  }

  test("Nullable wrapping null NullableDataKey") {
    val key = ssg.md.html.HtmlRenderer.EMPHASIS_STYLE_HTML_OPEN
    val options = new ssg.md.util.data.MutableDataSet().toImmutable
    val rawValue: String = key.get(Nullable(options))
    println(s"rawValue: '$rawValue', isNull: ${rawValue == null}")
    val wrapped = Nullable(rawValue)
    println(s"wrapped.isEmpty: ${wrapped.isEmpty}, wrapped.isDefined: ${wrapped.isDefined}")
    assert(wrapped.isEmpty, s"Expected empty but got: $wrapped (class: ${wrapped.getClass})")
  }
}
