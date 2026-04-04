/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/internal/EnumeratedReferenceNodeRenderer.java
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
import ssg.md.ast.Heading
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.*
import ssg.md.util.ast.Document
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class EnumeratedReferenceNodeRenderer(options: DataHolder) extends PhasedNodeRenderer {

  @annotation.nowarn("msg=unused private member") // stub: will be used when rendering is completed
  private val options_ = new EnumeratedReferenceOptions(options)
  private var enumeratedOrdinals: EnumeratedReferences = scala.compiletime.uninitialized
  private var ordinalRunnable:    Nullable[Runnable]   = Nullable.empty
  private val headerIdGenerator:  HtmlIdGenerator      = new HeaderIdGenerator.Factory().create()

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] =
    Nullable(
      Set(
        new NodeRenderingHandler[EnumeratedReferenceText](classOf[EnumeratedReferenceText], (node, ctx, html) => renderText(node, ctx, html)),
        new NodeRenderingHandler[EnumeratedReferenceLink](classOf[EnumeratedReferenceLink], (node, ctx, html) => renderLink(node, ctx, html)),
        new NodeRenderingHandler[EnumeratedReferenceBlock](classOf[EnumeratedReferenceBlock], (node, ctx, html) => renderBlock(node, ctx, html))
      )
    )

  override def getRenderingPhases: Nullable[Set[RenderingPhase]] =
    Nullable(Set(RenderingPhase.HEAD_TOP, RenderingPhase.BODY_TOP))

  override def renderDocument(context: NodeRendererContext, html: HtmlWriter, document: Document, phase: RenderingPhase): Unit =
    if (phase == RenderingPhase.HEAD_TOP) {
      headerIdGenerator.generateIds(document)
    } else if (phase == RenderingPhase.BODY_TOP) {
      enumeratedOrdinals = EnumeratedReferenceExtension.ENUMERATED_REFERENCE_ORDINALS.get(document)
    }

  private def renderLink(node: EnumeratedReferenceLink, context: NodeRendererContext, html: HtmlWriter): Unit = {
    val text = node.text.toString

    if (text.isEmpty) {
      // placeholder for ordinal
      if (ordinalRunnable.isDefined) ordinalRunnable.get.run()
    } else {
      val htmlWriter = html
      enumeratedOrdinals.renderReferenceOrdinals(
        text,
        new OrdinalRenderer(this, context, htmlWriter) {
          override def startRendering(renderings: Array[EnumeratedReferenceRendering]): Unit = {
            val title = new EnumRefTextCollectingVisitor().collectAndGetText(node.chars.getBaseSequence, renderings, null)
            htmlWriter.withAttr().attr("href", "#" + text).attr("title", title).tag("a")
          }

          override def endRendering(): Unit =
            htmlWriter.tag("/a")
        }
      )
    }
  }

  private def renderText(node: EnumeratedReferenceText, context: NodeRendererContext, html: HtmlWriter): Unit = {
    var text = node.text.toString

    if (text.isEmpty) {
      // placeholder for ordinal
      if (ordinalRunnable.isDefined) ordinalRunnable.get.run()
    } else {
      val typeStr = EnumeratedReferenceRepository.getType(text)

      if (typeStr.isEmpty || text.equals(typeStr + ":")) {
        val parent = node.ancestorOfType(classOf[Heading])

        if (parent.isDefined) {
          parent.get match {
            case heading: Heading =>
              text = (if (typeStr.isEmpty) text else typeStr) + ":" + headerIdGenerator.getId(heading)
            case _ =>
          }
        }
      }

      enumeratedOrdinals.renderReferenceOrdinals(text, new OrdinalRenderer(this, context, html))
    }
  }

  private class OrdinalRenderer(val renderer: EnumeratedReferenceNodeRenderer, val context: NodeRendererContext, val html: HtmlWriter) extends EnumeratedOrdinalRenderer {

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
              html.text(String.valueOf(referenceOrdinal))
              if (needSeparator) html.text(".")
            }
          }
        )

        context.renderChildren(referenceFormat)
      } else {
        html.text(defaultText + " ")
        if (compoundRunnable.isDefined) compoundRunnable.get.run()
        html.text(String.valueOf(referenceOrdinal))
        if (needSeparator) html.text(".")
      }
    }

    override def endRendering(): Unit = {}
  }

  private def renderBlock(node: EnumeratedReferenceBlock, context: NodeRendererContext, html: HtmlWriter): Unit = {
    // nothing to render
  }
}

object EnumeratedReferenceNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new EnumeratedReferenceNodeRenderer(options)
  }
}
