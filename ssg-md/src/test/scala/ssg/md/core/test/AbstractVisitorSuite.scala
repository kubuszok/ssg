/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../parser/ast/AbstractVisitorTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package core
package test

import ssg.md.ast.{ Code, Paragraph, Text }
import ssg.md.util.ast.{ Node, NodeVisitor, VisitHandler }

import scala.language.implicitConversions

/** Tests that replacing a node during visitor traversal does not destroy the visit order. */
final class AbstractVisitorSuite extends munit.FunSuite {

  test("replacingNodeInVisitorShouldNotDestroyVisitOrder") {
    val visitor = new NodeVisitor(
      new VisitHandler[Text](classOf[Text],
                             (node: Text) => {
                               node.insertAfter(new Code(node.chars))
                               node.unlink()
                             }
      )
    )

    val paragraph = new Paragraph()
    paragraph.appendChild(new Text("foo"))
    paragraph.appendChild(new Text("bar"))

    visitor.visit(paragraph)

    assertCode("foo", paragraph.firstChild.get)
    assertCode("bar", paragraph.firstChild.get.next.get)
    assert(paragraph.firstChild.get.next.get.next.isEmpty)
    assertCode("bar", paragraph.lastChild.get)
  }

  private def assertCode(expectedLiteral: String, node: Node): Unit = {
    assertEquals(node.getClass, classOf[Code], s"Expected node to be a Code node: $node")
    val code = node.asInstanceOf[Code]
    assertEquals(code.chars.toString, expectedLiteral)
  }
}
