/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-admonition/src/main/java/com/vladsch/flexmark/ext/admonition/internal/AdmonitionNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package admonition
package internal

import ssg.md.Nullable
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.*
import ssg.md.util.ast.Document
import ssg.md.util.data.DataHolder
import ssg.md.util.html.Attribute

import scala.language.implicitConversions

class AdmonitionNodeRenderer(options: DataHolder) extends PhasedNodeRenderer {

  private val admonitionOptions: AdmonitionOptions = new AdmonitionOptions(options)

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] =
    Nullable(
      Set[NodeRenderingHandler[?]](
        new NodeRenderingHandler[AdmonitionBlock](classOf[AdmonitionBlock], (node, ctx, html) => render(node, ctx, html))
      )
    )

  override def getRenderingPhases: Nullable[Set[RenderingPhase]] =
    Nullable(Set(RenderingPhase.BODY_TOP))

  override def renderDocument(context: NodeRendererContext, html: HtmlWriter, document: Document, phase: RenderingPhase): Unit =
    if (phase == RenderingPhase.BODY_TOP) {
      // dump out the SVG used by the rest of the nodes
      val resolvedQualifiers = new java.util.HashSet[String]()

      val referencedQualifiers = new AdmonitionCollectingVisitor().collectAndGetQualifiers(document)
      val iter                 = referencedQualifiers.iterator()
      while (iter.hasNext) {
        val qualifier         = iter.next()
        var resolvedQualifier = admonitionOptions.qualifierTypeMap.get(qualifier)
        if (resolvedQualifier == null) resolvedQualifier = admonitionOptions.unresolvedQualifier // @nowarn - Java map get may return null
        resolvedQualifiers.add(resolvedQualifier)
      }

      if (!resolvedQualifiers.isEmpty) {
        html.line()
        html.attr("xmlns", "http://www.w3.org/2000/svg").attr(Attribute.CLASS_ATTR, "adm-hidden").withAttr(AdmonitionNodeRenderer.ADMONITION_SVG_OBJECT_PART).tag("svg")
        html.indent().line()
        val qIter = resolvedQualifiers.iterator()
        while (qIter.hasNext) {
          val info       = qIter.next()
          val svgContent = admonitionOptions.typeSvgMap.get(info)
          if (svgContent != null && !svgContent.isEmpty) { // @nowarn - Java map get may return null
            html.raw("<symbol id=\"adm-").raw(info).raw("\">")
            html.indent().line()
            html.raw(svgContent)
            html.line()
            html.unIndent()
            html.raw("</symbol>")
            html.line()
          }
        }
        html.unIndent()
        html.closeTag("svg")
        html.line()
      }
    }

  private def render(node: AdmonitionBlock, context: NodeRendererContext, html: HtmlWriter): Unit = {
    val info     = node.info.toString.toLowerCase
    var nodeType = admonitionOptions.qualifierTypeMap.get(info)
    if (nodeType == null) { // @nowarn - Java map get may return null
      nodeType = admonitionOptions.unresolvedQualifier
    }

    val title: String = if (node.title.isNull) {
      val titleFromMap = admonitionOptions.qualifierTitleMap.get(info)
      if (titleFromMap == null) { // @nowarn - Java map get may return null
        info.substring(0, 1).toUpperCase + info.substring(1)
      } else titleFromMap
    } else {
      node.title.toString
    }

    val openClose: Nullable[String] =
      if (node.openingMarker.equals("???")) Nullable(" adm-collapsed")
      else if (node.openingMarker.equals("???+")) Nullable("adm-open")
      else Nullable.empty

    if (title.isEmpty) {
      html.srcPos(node.chars).withAttr().attr(Attribute.CLASS_ATTR, "adm-block").attr(Attribute.CLASS_ATTR, "adm-" + nodeType).tag("div", false)
      html.line()

      html.attr(Attribute.CLASS_ATTR, "adm-body").withAttr(AdmonitionNodeRenderer.ADMONITION_BODY_PART).tag("div")
      html.indent().line()

      context.renderChildren(node)

      html.unIndent()
      html.closeTag("div").line()
      html.closeTag("div").line()
    } else {
      html.srcPos(node.chars).attr(Attribute.CLASS_ATTR, "adm-block").attr(Attribute.CLASS_ATTR, "adm-" + nodeType)

      openClose.foreach { oc =>
        html.attr(Attribute.CLASS_ATTR, oc).attr(Attribute.CLASS_ATTR, "adm-" + nodeType)
      }

      html.withAttr().tag("div", false)
      html.line()
      html.attr(Attribute.CLASS_ATTR, "adm-heading").withAttr(AdmonitionNodeRenderer.ADMONITION_HEADING_PART).tag("div")
      html.line()
      html.attr(Attribute.CLASS_ATTR, "adm-icon").withAttr(AdmonitionNodeRenderer.ADMONITION_ICON_PART).tag("svg").raw("<use xlink:href=\"#adm-").raw(nodeType).raw("\" />").closeTag("svg")
      html.withAttr(AdmonitionNodeRenderer.ADMONITION_TITLE_PART).tag("span").text(title).closeTag("span").line()
      html.closeTag("div").line()

      html.attr(Attribute.CLASS_ATTR, "adm-body").withAttr(AdmonitionNodeRenderer.ADMONITION_BODY_PART).withCondIndent().tagLine("div", () => context.renderChildren(node))

      html.closeTag("div").line()
    }
  }
}

object AdmonitionNodeRenderer {

  val ADMONITION_SVG_OBJECT_PART: AttributablePart = new AttributablePart("ADMONITION_SVG_OBJECT_PART")
  val ADMONITION_HEADING_PART:    AttributablePart = new AttributablePart("ADMONITION_HEADING_PART")
  val ADMONITION_ICON_PART:       AttributablePart = new AttributablePart("ADMONITION_ICON_PART")
  val ADMONITION_TITLE_PART:      AttributablePart = new AttributablePart("ADMONITION_TITLE_PART")
  val ADMONITION_BODY_PART:       AttributablePart = new AttributablePart("ADMONITION_BODY_PART")

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new AdmonitionNodeRenderer(options)
  }
}
