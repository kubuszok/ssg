/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/TextCollectingVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast
package util

import ssg.md.util.ast.DoNotCollectText
import ssg.md.util.ast.Node
import ssg.md.util.ast.NodeVisitor
import ssg.md.util.ast.Visitor
import ssg.md.util.ast.VisitHandler
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.builder.SequenceBuilder

import java.{ util => ju }

import scala.language.implicitConversions

/** @deprecated
  *   use [[ssg.md.util.ast.TextCollectingVisitor]] from the utils library
  */
@deprecated("use ssg.md.util.ast.TextCollectingVisitor", "")
class TextCollectingVisitor(lineBreakNodes: Class[?]*) {

  private var out: SequenceBuilder = scala.compiletime.uninitialized

  private val myLineBreakNodes: ju.HashSet[Class[?]] =
    if (lineBreakNodes.isEmpty) null
    else new ju.HashSet[Class[?]](ju.Arrays.asList(lineBreakNodes*))

  private val myVisitor: NodeVisitor = new NodeVisitor(
    new VisitHandler[Text](classOf[Text], (node: Text) => visitText(node)),
    new VisitHandler[TextBase](classOf[TextBase], (node: TextBase) => visitTextBase(node)),
    new VisitHandler[HtmlEntity](classOf[HtmlEntity], (node: HtmlEntity) => visitHtmlEntity(node)),
    new VisitHandler[SoftLineBreak](classOf[SoftLineBreak], (node: SoftLineBreak) => visitSoftLineBreak(node)),
    new VisitHandler[Paragraph](classOf[Paragraph], (node: Paragraph) => visitParagraph(node)),
    new VisitHandler[HardLineBreak](classOf[HardLineBreak], (node: HardLineBreak) => visitHardLineBreak(node))
  ) {
    override def processNode(node: Node, withChildren: Boolean, processor: (Node, Visitor[Node]) => Unit): Unit = {
      val visitor = getAction(node)
      if (visitor.isDefined) {
        processor(node, visitor.get)
      } else {
        processChildren(node, processor)
        if (myLineBreakNodes != null && myLineBreakNodes.contains(node.getClass) && !node.isOrDescendantOfType(classOf[DoNotCollectText])) {
          out.add("\n")
        }
      }
    }
  }

  def getText: String = out.toString

  def collect(node: Node): Unit = {
    out = SequenceBuilder.emptyBuilder(node.chars)
    myVisitor.visit(node)
  }

  def collectAndGetText(node: Node): String = {
    collect(node)
    out.toString
  }

  def collectAndGetSequence(node: Node): BasedSequence = {
    collect(node)
    out.toSequence
  }

  private def visitParagraph(node: Paragraph): Unit =
    if (!node.isOrDescendantOfType(classOf[DoNotCollectText])) {
      if (!out.isEmpty) {
        out.add("\n\n")
      }
      myVisitor.visitChildren(node)
    }

  private def visitSoftLineBreak(node: SoftLineBreak): Unit =
    out.add(node.chars)

  private def visitHardLineBreak(node: HardLineBreak): Unit = {
    val chars = node.chars
    out.add(chars.subSequence(chars.length - 1, chars.length))
  }

  private def visitHtmlEntity(node: HtmlEntity): Unit =
    out.add(node.chars.unescape())

  private def visitText(node: Text): Unit =
    if (!node.isOrDescendantOfType(classOf[DoNotCollectText])) {
      out.add(node.chars)
    }

  private def visitTextBase(node: TextBase): Unit =
    out.add(node.chars)
}

object TextCollectingVisitor {

  protected def concatArrays(classes: Array[Class[?]]*): Array[Class[?]] = {
    var total = 0
    for (classList <- classes)
      total += classList.length
    val result = new Array[Class[?]](total)
    var index  = 0
    for (classList <- classes) {
      System.arraycopy(classList, 0, result, index, classList.length)
      index += classList.length
    }
    result
  }
}
