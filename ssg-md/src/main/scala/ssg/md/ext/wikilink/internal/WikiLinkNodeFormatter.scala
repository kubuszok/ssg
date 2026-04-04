/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-wikilink/src/main/java/com/vladsch/flexmark/ext/wikilink/internal/WikiLinkNodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package wikilink
package internal

import ssg.md.Nullable
import ssg.md.formatter.*
import ssg.md.util.ast.Document
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.mappers.SpaceMapper

import java.{ util => ju }
import scala.language.implicitConversions

class WikiLinkNodeFormatter(options: DataHolder) extends PhasedNodeFormatter {

  private var attributeUniquificationIdMap: Nullable[ju.Map[String, String]] = Nullable.empty
  private var wikiOptions:                  Nullable[WikiLinkOptions]        = Nullable.empty

  override def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]] =
    Nullable(
      Set[NodeFormattingHandler[?]](
        new NodeFormattingHandler[WikiLink](classOf[WikiLink], (node, ctx, md) => renderLink(node, ctx, md)),
        new NodeFormattingHandler[WikiImage](classOf[WikiImage], (node, ctx, md) => renderImage(node, ctx, md))
      )
    )

  override def getNodeClasses: Nullable[Set[Class[?]]] =
    Nullable(Set[Class[?]](classOf[WikiLink], classOf[WikiImage]))

  override def getFormattingPhases: Nullable[Set[FormattingPhase]] =
    Nullable(Set(FormattingPhase.COLLECT, FormattingPhase.DOCUMENT_TOP))

  override def renderDocument(context: NodeFormatterContext, markdown: MarkdownWriter, document: Document, phase: FormattingPhase): Unit = {
    attributeUniquificationIdMap = Nullable(Formatter.ATTRIBUTE_UNIQUIFICATION_ID_MAP.get(context.getTranslationStore))
    wikiOptions = Nullable(new WikiLinkOptions(document))
  }

  private def renderLink(node: WikiLink, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.append(node.openingMarker)
    if (node.linkIsFirst) {
      renderLinkPart(node, context, markdown)
      renderTextPart(node, context, markdown)
    } else {
      renderTextPart(node, context, markdown)
      renderLinkPart(node, context, markdown)
    }
    markdown.append(node.closingMarker)
  }

  private def renderImage(node: WikiImage, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.append(node.openingMarker)
    if (node.linkIsFirst) {
      renderLinkPart(node, context, markdown)
      renderTextPart(node, context, markdown)
    } else {
      renderTextPart(node, context, markdown)
      renderLinkPart(node, context, markdown)
    }
    markdown.append(node.closingMarker)
  }

  private def renderTextPart(node: WikiNode, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    if (!context.isTransformingText) {
      if (node.text.isNotNull) {
        if (node.linkIsFirst) {
          markdown.append(node.textSeparatorMarker)
        }

        if (context.getFormatterOptions.rightMargin > 0) {
          // no wrapping of link text
          markdown.append(node.text.toMapped(SpaceMapper.toNonBreakSpace))
        } else {
          context.renderChildren(node)
        }

        if (!node.linkIsFirst) {
          markdown.append(node.textSeparatorMarker)
        }
      }
    } else {
      val opts = wikiOptions.get
      context.getRenderPurpose match {
        case RenderPurpose.TRANSLATION_SPANS | RenderPurpose.TRANSLATED_SPANS =>
          if (node.linkIsFirst) {
            markdown.append(WikiNode.SEPARATOR_CHAR)
          }

          val text = if (node.text.isNull) node.pageRef else node.text
          if (opts.allowInlines) {
            context.renderChildren(node)
          } else {
            markdown.append(text.unescape())
          }

          if (!node.linkIsFirst) {
            markdown.append(WikiNode.SEPARATOR_CHAR)
          }

        case RenderPurpose.TRANSLATED =>
          if (node.linkIsFirst) {
            markdown.append(node.textSeparatorMarker)
          }

          if (opts.allowInlines) {
            context.renderChildren(node)
          } else {
            val translated = context.transformTranslating(Nullable.empty, node.text, Nullable.empty, Nullable.empty)
            markdown.append(escapePipeAnchors(translated, opts))
          }

          if (!node.linkIsFirst) {
            markdown.append(node.textSeparatorMarker)
          }

        case _ => throw new IllegalStateException("Unexpected renderer purpose")
      }
    }

  private def escapePipeAnchors(chars: CharSequence, opts: WikiLinkOptions): CharSequence = {
    val iMax = chars.length()
    val text = new StringBuilder()

    var i = 0
    while (i < iMax) {
      val c = chars.charAt(i)
      c match {
        case '\\' => text.append('\\')
        case '|'  => if (opts.allowPipeEscape) text.append('\\')
        case '#'  => if (opts.allowAnchors && opts.allowAnchorEscape) text.append('\\')
        case _    => ()
      }
      text.append(c)
      i += 1
    }
    text
  }

  private def renderLinkPart(node: WikiNode, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    if (!context.isTransformingText) {
      if (context.getFormatterOptions.rightMargin > 0) {
        // no wrapping of link text
        markdown.append(node.link.toMapped(SpaceMapper.toNonBreakSpace))
      } else {
        markdown.append(node.link)
      }
    } else {
      if (context.getRenderPurpose == RenderPurpose.TRANSLATION_SPANS) {
        markdown.append(node.pageRef)
        markdown.append(node.anchorMarker)
        if (node.anchorRef.isNotNull) {
          val anchorRef = context.transformAnchorRef(node.pageRef, node.anchorRef)
          markdown.append(anchorRef)
        }
      } else {
        val pageRef = context.transformNonTranslating(Nullable.empty, node.pageRef, Nullable.empty, Nullable.empty)
        val opts    = wikiOptions.get
        // NOTE: need to escape pipes and hashes in page refs
        markdown.append(escapeUnescapedPipeAnchors(pageRef, opts))
        markdown.append(node.anchorMarker)
        if (node.anchorRef.isNotNull) {
          val anchorRef = context.transformAnchorRef(node.pageRef, node.anchorRef)
          attributeUniquificationIdMap.foreach { idMap =>
            if (context.getRenderPurpose == RenderPurpose.TRANSLATED && context.getMergeContext.isDefined) {
              var uniquifiedAnchorRef = String.valueOf(anchorRef)
              if (pageRef.length() == 0) {
                uniquifiedAnchorRef = idMap.getOrDefault(uniquifiedAnchorRef, uniquifiedAnchorRef)
              }
              markdown.append(uniquifiedAnchorRef)
            } else {
              markdown.append(anchorRef)
            }
          }
          if (attributeUniquificationIdMap.isEmpty) {
            markdown.append(anchorRef)
          }
        }
      }
    }

  // Need to escape un-escaped \, |, and #
  private def escapeUnescapedPipeAnchors(chars: CharSequence, opts: WikiLinkOptions): CharSequence = {
    var isEscaped = false
    val iMax      = chars.length()
    val text      = new StringBuilder()

    var i = 0
    while (i < iMax) {
      val c = chars.charAt(i)
      c match {
        case '\\' => isEscaped = !isEscaped
        case '|'  =>
          if (!isEscaped && opts.allowPipeEscape) text.append('\\')
          isEscaped = false
        case '#' =>
          if (!isEscaped && opts.allowAnchors && opts.allowAnchorEscape) text.append('\\')
          isEscaped = false
        case _ => isEscaped = false
      }
      text.append(c)
      i += 1
    }

    if (isEscaped) {
      // dangling \
      text.append('\\')
    }
    text
  }
}

object WikiLinkNodeFormatter {

  class Factory extends NodeFormatterFactory {
    override def create(options: DataHolder): NodeFormatter = new WikiLinkNodeFormatter(options)
  }
}
