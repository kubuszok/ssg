/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../parser/UsageExampleTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package core
package test

import ssg.md.ast.Text
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.ast.{ Node, NodeVisitor, VisitHandler }

import scala.language.implicitConversions

final class UsageExampleSuite extends munit.FunSuite {

  test("parseAndRender") {
    val parser   = Parser.builder().build()
    val document = parser.parse("This is *Sparta*")
    val renderer = HtmlRenderer.builder().escapeHtml(true).build()
    assertEquals(renderer.render(document), "<p>This is <em>Sparta</em></p>\n")
  }

  test("visitor") {
    val parser  = Parser.builder().build()
    val node    = parser.parse("Example\n=======\n\nSome more text")
    val visitor = new WordCountVisitor()
    visitor.countWords(node)
    assertEquals(visitor.wordCount, 4)
  }

  private class WordCountVisitor {
    var wordCount: Int = 0

    private val myVisitor: NodeVisitor = new NodeVisitor(
      new VisitHandler[Text](classOf[Text], (text: Text) => visit(text))
    )

    def countWords(node: Node): Unit =
      myVisitor.visit(node)

    private def visit(text: Text): Unit = {
      // This is called for all Text nodes. Override other visit methods for other node types.

      // Count words (this is just an example, don't actually do it this way for various reasons).
      wordCount += text.chars.toString.split("\\W+").length

      // Descend into children (could be omitted in this case because Text nodes don't have children).
      myVisitor.visitChildren(text)
    }
  }
}
