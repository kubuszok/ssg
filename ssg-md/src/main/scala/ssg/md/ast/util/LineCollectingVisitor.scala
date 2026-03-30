/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/LineCollectingVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast
package util

import ssg.md.util.ast.Node
import ssg.md.util.ast.NodeVisitor
import ssg.md.util.ast.VisitHandler
import ssg.md.util.sequence.Range

import java.{ util => ju }

class LineCollectingVisitor {

  private val myVisitor: NodeVisitor = new NodeVisitor(
    new VisitHandler[Text](classOf[Text], (node: Text) => visitText(node)),
    new VisitHandler[TextBase](classOf[TextBase], (node: TextBase) => visitTextBase(node)),
    new VisitHandler[HtmlEntity](classOf[HtmlEntity], (node: HtmlEntity) => visitHtmlEntity(node)),
    new VisitHandler[HtmlInline](classOf[HtmlInline], (node: HtmlInline) => visitHtmlInline(node)),
    new VisitHandler[SoftLineBreak](classOf[SoftLineBreak], (node: SoftLineBreak) => visitSoftLineBreak(node)),
    new VisitHandler[HardLineBreak](classOf[HardLineBreak], (node: HardLineBreak) => visitHardLineBreak(node))
  )

  private var myLines:       ju.List[Range]   = ju.Collections.emptyList()
  private var myEOLs:        ju.List[Integer] = ju.Collections.emptyList()
  private var myStartOffset: Int              = 0
  private var myEndOffset:   Int              = 0

  private def finalizeLines(): Unit =
    if (myStartOffset < myEndOffset) {
      val range = Range.of(myStartOffset, myEndOffset)
      myLines.add(range)
      myEOLs.add(0)
      myStartOffset = myEndOffset
    }

  def getLines: ju.List[Range] = {
    finalizeLines()
    myLines
  }

  def getEOLs: ju.List[Integer] = {
    finalizeLines()
    myEOLs
  }

  def collect(node: Node): Unit = {
    myLines = new ju.ArrayList[Range]()
    myEOLs = new ju.ArrayList[Integer]()
    myStartOffset = node.startOffset
    myEndOffset = node.endOffset
    myVisitor.visit(node)
  }

  def collectAndGetRanges(node: Node): ju.List[Range] = {
    collect(node)
    getLines
  }

  private def visitSoftLineBreak(node: SoftLineBreak): Unit = {
    val range = Range.of(myStartOffset, node.endOffset)
    myLines.add(range)
    myEOLs.add(node.textLength)
    myStartOffset = node.endOffset
  }

  private def visitHardLineBreak(node: HardLineBreak): Unit = {
    val range = Range.of(myStartOffset, node.endOffset)
    myLines.add(range)
    myEOLs.add(node.textLength)
    myStartOffset = node.endOffset
  }

  private def visitHtmlEntity(node: HtmlEntity): Unit =
    myEndOffset = node.endOffset

  private def visitHtmlInline(node: HtmlInline): Unit =
    myEndOffset = node.endOffset

  private def visitText(node: Text): Unit =
    myEndOffset = node.endOffset

  private def visitTextBase(node: TextBase): Unit =
    myEndOffset = node.endOffset
}
