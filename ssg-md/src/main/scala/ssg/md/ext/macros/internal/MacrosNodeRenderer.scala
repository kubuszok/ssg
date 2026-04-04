/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-macros/src/main/java/com/vladsch/flexmark/ext/macros/internal/MacrosNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package macros
package internal

import ssg.md.Nullable
import ssg.md.ast.Paragraph
import ssg.md.html.{ HtmlRenderer, HtmlWriter }
import ssg.md.html.renderer.*
import ssg.md.util.ast.{ Document, NodeVisitor, VisitHandler }
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class MacrosNodeRenderer(options: DataHolder) extends PhasedNodeRenderer {

  private val macrosOptions = new MacrosOptions(options)
  private val repository:                 MacroDefinitionRepository = MacrosExtension.MACRO_DEFINITIONS.get(options)
  private val recheckUndefinedReferences: Boolean                   = HtmlRenderer.RECHECK_UNDEFINED_REFERENCES.get(options)

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] =
    Nullable(
      Set(
        new NodeRenderingHandler[MacroReference](classOf[MacroReference], (node, ctx, html) => renderMacroReference(node, ctx, html)),
        new NodeRenderingHandler[MacroDefinitionBlock](classOf[MacroDefinitionBlock], (node, ctx, html) => renderMacroDefinitionBlock(node, ctx, html))
      )
    )

  override def getRenderingPhases: Nullable[Set[RenderingPhase]] =
    Nullable(Set(RenderingPhase.BODY_TOP))

  override def renderDocument(context: NodeRendererContext, html: HtmlWriter, document: Document, phase: RenderingPhase): Unit =
    if (phase == RenderingPhase.BODY_TOP) {
      if (recheckUndefinedReferences) {
        // need to see if have undefined footnotes that were defined after parsing
        var hadNewFootnotes = false
        val visitor         = new NodeVisitor(
          new VisitHandler[MacroReference](
            classOf[MacroReference],
            (node: MacroReference) =>
              if (!node.isDefined) {
                val macroDefinitionBlock = node.getMacroDefinitionBlock(repository)

                if (macroDefinitionBlock != null) {
                  repository.addMacrosReference(macroDefinitionBlock, node)
                  node.macroDefinitionBlock = macroDefinitionBlock
                  hadNewFootnotes = true
                }
              }
          )
        )

        visitor.visit(document)
        if (hadNewFootnotes) {
          this.repository.resolveMacrosOrdinals()
        }
      }
    }

  private def renderMacroReference(node: MacroReference, context: NodeRendererContext, html: HtmlWriter): Unit = {
    // render contents of macro definition
    val macroDefinitionBlock = repository.get(repository.normalizeKey(node.text))
    if (macroDefinitionBlock != null) {
      if (macroDefinitionBlock.hasChildren && !macroDefinitionBlock.inExpansion) {
        try {
          macroDefinitionBlock.inExpansion = true
          val child = macroDefinitionBlock.firstChild
          if (child.isDefined && child.get.isInstanceOf[Paragraph] && child == macroDefinitionBlock.lastChild) {
            // if a single paragraph then we unwrap it and output only its children as inline text
            if (macrosOptions.sourceWrapMacroReferences) {
              html.srcPos(node.chars).withAttr(AttributablePart.NODE_POSITION).tag("span")
              context.renderChildren(child.get)
              html.tag("/span")
            } else {
              context.renderChildren(child.get)
            }
          } else if (child.isDefined) {
            if (macrosOptions.sourceWrapMacroReferences) {
              html.srcPos(node.chars).withAttr(AttributablePart.NODE_POSITION).tag("div").indent().line()
              context.renderChildren(macroDefinitionBlock)
              html.unIndent()
              html.tag("/div")
            } else {
              context.renderChildren(macroDefinitionBlock)
            }
          }
        } finally
          macroDefinitionBlock.inExpansion = false
      }
    } else {
      html.text(node.chars)
    }
  }

  private def renderMacroDefinitionBlock(node: MacroDefinitionBlock, context: NodeRendererContext, html: HtmlWriter): Unit = {
    // nothing to render
  }
}

object MacrosNodeRenderer {

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new MacrosNodeRenderer(options)
  }
}
