/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/HeadingCollectingVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/util/HeadingCollectingVisitor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ast
package util

import ssg.md.util.ast.BlockNodeVisitor
import ssg.md.util.ast.Node
import ssg.md.util.ast.NodeVisitor
import ssg.md.util.ast.VisitHandler

import java.{ util => ju }

class HeadingCollectingVisitor {

  private val headings: ju.ArrayList[Heading] = new ju.ArrayList[Heading]()

  private val myVisitor: NodeVisitor = new BlockNodeVisitor(
    new VisitHandler[Heading](classOf[Heading], (h: Heading) => headings.add(h))
  )

  def collect(node: Node): Unit =
    myVisitor.visit(node)

  def collectAndGetHeadings(node: Node): ju.ArrayList[Heading] = {
    myVisitor.visit(node)
    headings
  }

  def getHeadings: ju.ArrayList[Heading] = headings
}
