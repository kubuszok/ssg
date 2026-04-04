/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/internal/EnumRefTextCollectingVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package enumerated
package reference
package internal

import ssg.md.Nullable
import ssg.md.ast.{ HardLineBreak, HtmlEntity, SoftLineBreak, Text, TextBase }
import ssg.md.util.ast.{ DoNotCollectText, NodeVisitor, VisitHandler }
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.builder.SequenceBuilder

import scala.language.implicitConversions

class EnumRefTextCollectingVisitor(ordinal: Int) {

  private var out:             SequenceBuilder    = scala.compiletime.uninitialized
  private var ordinalRunnable: Nullable[Runnable] =
    if (ordinal < 0) Nullable.empty
    else
      Nullable(new Runnable {
        override def run(): Unit = out.add(String.valueOf(ordinal))
      })

  private val visitor: NodeVisitor = new NodeVisitor(
    new VisitHandler[Text](classOf[Text], (node: Text) => visitText(node)),
    new VisitHandler[TextBase](classOf[TextBase], (node: TextBase) => visitTextBase(node)),
    new VisitHandler[HtmlEntity](classOf[HtmlEntity], (node: HtmlEntity) => visitHtmlEntity(node)),
    new VisitHandler[SoftLineBreak](classOf[SoftLineBreak], (node: SoftLineBreak) => visitSoftLineBreak(node)),
    new VisitHandler[HardLineBreak](classOf[HardLineBreak], (node: HardLineBreak) => visitHardLineBreak(node)),
    new VisitHandler[EnumeratedReferenceText](classOf[EnumeratedReferenceText], (node: EnumeratedReferenceText) => visitEnumRefText(node)),
    new VisitHandler[EnumeratedReferenceLink](classOf[EnumeratedReferenceLink], (node: EnumeratedReferenceLink) => visitEnumRefLink(node))
  )

  def this() = this(-1)

  def getText: String = out.toString

  def collect(basedSequence: BasedSequence, renderings: Array[EnumeratedReferenceRendering], defaultFormat: String): Unit = {
    out = SequenceBuilder.emptyBuilder(basedSequence)
    EnumeratedReferences.renderReferenceOrdinals(renderings, new OrdinalRenderer(this))
  }

  def collectAndGetText(basedSequence: BasedSequence, renderings: Array[EnumeratedReferenceRendering], defaultFormat: String): String = {
    collect(basedSequence, renderings, defaultFormat)
    out.toString
  }

  private class OrdinalRenderer(val renderer: EnumRefTextCollectingVisitor) extends EnumeratedOrdinalRenderer {

    override def startRendering(renderings: Array[EnumeratedReferenceRendering]): Unit = {}

    override def setEnumOrdinalRunnable(runnable: Nullable[Runnable]): Unit =
      renderer.ordinalRunnable = runnable

    override def getEnumOrdinalRunnable: Nullable[Runnable] =
      renderer.ordinalRunnable

    override def render(referenceOrdinal: Int, referenceFormat: EnumeratedReferenceBlock, defaultText: String, needSeparator: Boolean): Unit = {
      val compoundRunnable = renderer.ordinalRunnable

      if (referenceFormat != null) { // @nowarn - referenceFormat may be null from repository.get
        renderer.ordinalRunnable = Nullable(
          new Runnable {
            override def run(): Unit = {
              if (compoundRunnable.isDefined) compoundRunnable.get.run()
              renderer.out.add(String.valueOf(referenceOrdinal))
              if (needSeparator) renderer.out.add(".")
            }
          }
        )

        renderer.visitor.visitChildren(referenceFormat)
      } else {
        renderer.out.add(defaultText + " ")
        if (compoundRunnable.isDefined) compoundRunnable.get.run()
        renderer.out.add(String.valueOf(referenceOrdinal))
        if (needSeparator) renderer.out.add(".")
      }
    }

    override def endRendering(): Unit = {}
  }

  private def visitEnumRefText(node: EnumeratedReferenceText): Unit = {
    val text = node.text.toString
    if (text.isEmpty) {
      // placeholder for ordinal
      if (ordinalRunnable.isDefined) ordinalRunnable.get.run()
    }
  }

  private def visitEnumRefLink(node: EnumeratedReferenceLink): Unit = {
    val text = node.text.toString
    if (text.isEmpty) {
      // placeholder for ordinal
      if (ordinalRunnable.isDefined) ordinalRunnable.get.run()
    }
  }

  private def visitSoftLineBreak(node: SoftLineBreak): Unit =
    out.add(node.chars)

  private def visitHardLineBreak(node: HardLineBreak): Unit = {
    val chars = node.chars
    out.add(chars.subSequence(chars.length() - 1, chars.length()))
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
