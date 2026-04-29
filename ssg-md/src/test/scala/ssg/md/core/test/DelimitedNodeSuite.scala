/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../parser/ast/DelimitedNodeTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package core
package test

import ssg.md.ast.{ Emphasis, StrongEmphasis }
import ssg.md.parser.Parser
import ssg.md.util.ast.{ DelimitedNode, NodeVisitor, VisitHandler }

import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions

final class DelimitedNodeSuite extends munit.FunSuite {

  test("emphasisDelimiters") {
    val input = "* *emphasis* \n" +
      "* **strong** \n" +
      "* _important_ \n" +
      "* __CRITICAL__ \n"

    val parser   = Parser.builder().build()
    val document = parser.parse(input)

    val list    = ArrayBuffer.empty[DelimitedNode]
    val visitor = new NodeVisitor(
      new VisitHandler[Emphasis](classOf[Emphasis], (node: Emphasis) => list.addOne(node)),
      new VisitHandler[StrongEmphasis](classOf[StrongEmphasis], (node: StrongEmphasis) => list.addOne(node))
    )

    visitor.visit(document)

    assertEquals(list.size, 4)

    val emphasis  = list(0)
    val strong    = list(1)
    val important = list(2)
    val critical  = list(3)

    assertEquals(String.valueOf(emphasis.openingMarker), "*")
    assertEquals(String.valueOf(emphasis.closingMarker), "*")
    assertEquals(String.valueOf(strong.openingMarker), "**")
    assertEquals(String.valueOf(strong.closingMarker), "**")
    assertEquals(String.valueOf(important.openingMarker), "_")
    assertEquals(String.valueOf(important.closingMarker), "_")
    assertEquals(String.valueOf(critical.openingMarker), "__")
    assertEquals(String.valueOf(critical.closingMarker), "__")
  }
}
