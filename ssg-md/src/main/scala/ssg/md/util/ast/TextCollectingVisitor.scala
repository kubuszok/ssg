/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/TextCollectingVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/TextCollectingVisitor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package ast

import ssg.md.util.sequence.BasedSequence

class TextCollectingVisitor {
  private var out:    SpaceInsertingSequenceBuilder = scala.compiletime.uninitialized
  private var _flags: Int                           = 0

  private val myVisitor: NodeVisitor = new NodeVisitor() {
    override def processNode(node: Node, withChildren: Boolean, processor: (Node, Visitor[Node]) => Unit): Unit =
      if (!node.isOrDescendantOfType(classOf[DoNotCollectText])) {
        out.lastNode = node
        if (node.isInstanceOf[Block] && out.isNotEmpty) {
          out.appendEol()
        }
        node match {
          case textContainer: TextContainer =>
            if (textContainer.collectText(out, _flags, myVisitor)) {
              if (node.isInstanceOf[BlankLineBreakNode] && out.isNotEmpty) {
                out.appendEol()
              }
              processChildren(node, processor)
            }
            textContainer.collectEndText(out, _flags, myVisitor)
          case _ =>
            processChildren(node, processor)
        }
        if (node.isInstanceOf[LineBreakNode] && out.needEol()) {
          out.appendEol()
        }
      }
  }

  def getText: String = out.toString

  def getSequence: BasedSequence = out.toSequence

  def collect(node: Node): Unit =
    collect(node, 0)

  def collectAndGetText(node: Node): String =
    collectAndGetText(node, 0)

  def collectAndGetSequence(node: Node): BasedSequence =
    collectAndGetSequence(node, 0)

  def collect(node: Node, flags: Int): Unit = {
    out = SpaceInsertingSequenceBuilder.emptyBuilder(node.chars, flags)
    _flags = flags
    myVisitor.visit(node)
  }

  def collectAndGetText(node: Node, flags: Int): String = {
    collect(node, flags)
    out.toString
  }

  def collectAndGetSequence(node: Node, flags: Int): BasedSequence = {
    collect(node, flags)
    out.toSequence
  }
}
