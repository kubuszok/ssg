/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gitlab/src/main/java/com/vladsch/flexmark/ext/gitlab/internal/GitLabNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gitlab
package internal

import ssg.md.Nullable
import ssg.md.ast.{ FencedCodeBlock, Image, ImageRef }
import ssg.md.ast.util.ReferenceRepository
import ssg.md.html.{ HtmlRenderer, HtmlWriter }
import ssg.md.html.renderer.*
import ssg.md.parser.Parser
import ssg.md.util.ast.{ Node, TextCollectingVisitor }
import ssg.md.util.data.DataHolder
import ssg.md.util.html.{ Attribute, Attributes }

import scala.language.implicitConversions

class GitLabNodeRenderer(options: DataHolder) extends NodeRenderer {

  private val gitLabOptions:              GitLabOptions       = new GitLabOptions(options)
  private val codeContentBlock:           Boolean             = Parser.FENCED_CODE_CONTENT_BLOCK.get(options)
  private val referenceRepository:        ReferenceRepository = Parser.REFERENCES.get(options)
  private val recheckUndefinedReferences: Boolean             = HtmlRenderer.RECHECK_UNDEFINED_REFERENCES.get(options)

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] = {
    val set = scala.collection.mutable.HashSet[NodeRenderingHandler[?]]()
    set += new NodeRenderingHandler[GitLabIns](classOf[GitLabIns], (node, ctx, html) => renderIns(node, ctx, html))
    set += new NodeRenderingHandler[GitLabDel](classOf[GitLabDel], (node, ctx, html) => renderDel(node, ctx, html))
    set += new NodeRenderingHandler[GitLabInlineMath](classOf[GitLabInlineMath], (node, ctx, html) => renderInlineMath(node, ctx, html))
    set += new NodeRenderingHandler[GitLabBlockQuote](classOf[GitLabBlockQuote], (node, ctx, html) => renderBlockQuote(node, ctx, html))
    if (gitLabOptions.renderBlockMath || gitLabOptions.renderBlockMermaid) {
      set += new NodeRenderingHandler[FencedCodeBlock](classOf[FencedCodeBlock], (node, ctx, html) => renderFencedCode(node, ctx, html))
    }
    if (gitLabOptions.renderVideoImages) {
      set += new NodeRenderingHandler[Image](classOf[Image], (node, ctx, html) => renderImage(node, ctx, html))
      set += new NodeRenderingHandler[ImageRef](classOf[ImageRef], (node, ctx, html) => renderImageRef(node, ctx, html))
    }
    Nullable(set.toSet)
  }

  private def renderIns(node: GitLabIns, context: NodeRendererContext, html: HtmlWriter): Unit =
    html.withAttr().tag("ins", false, false, () => context.renderChildren(node))

  private def renderDel(node: GitLabDel, context: NodeRendererContext, html: HtmlWriter): Unit =
    html.withAttr().tag("del", false, false, () => context.renderChildren(node))

  private def renderInlineMath(node: GitLabInlineMath, context: NodeRendererContext, html: HtmlWriter): Unit = {
    html.withAttr().attr(Attribute.CLASS_ATTR, gitLabOptions.inlineMathClass).withAttr().tag("span")
    html.text(node.text)
    html.tag("/span")
  }

  private def renderBlockQuote(node: GitLabBlockQuote, context: NodeRendererContext, html: HtmlWriter): Unit =
    html.withAttr().tagLineIndent("blockquote", () => context.renderChildren(node))

  private def renderFencedCode(node: FencedCodeBlock, context: NodeRendererContext, html: HtmlWriter): Unit = {
    val htmlOptions = context.getHtmlOptions
    val language    = node.infoDelimitedByAny(htmlOptions.languageDelimiterSet)

    if (gitLabOptions.renderBlockMath && language.isIn(gitLabOptions.mathLanguages)) {
      html.line()
      html.srcPosWithTrailingEOL(node.chars).attr(Attribute.CLASS_ATTR, gitLabOptions.blockMathClass).withAttr().tag("div")
      html.line()
      html.openPre()
      if (codeContentBlock) {
        context.renderChildren(node)
      } else {
        html.text(node.contentChars.normalizeEOL())
      }
      html.closePre().tag("/div")
      html.lineIf(htmlOptions.htmlBlockCloseTagEol)
    } else if (gitLabOptions.renderBlockMermaid && language.isIn(gitLabOptions.mermaidLanguages)) {
      html.line()
      html.srcPosWithTrailingEOL(node.chars).attr(Attribute.CLASS_ATTR, gitLabOptions.blockMermaidClass).withAttr().tag("div")
      html.line()
      html.openPre()
      if (codeContentBlock) {
        context.renderChildren(node)
      } else {
        html.text(node.contentChars.normalizeEOL())
      }
      html.closePre().tag("/div")
      html.lineIf(htmlOptions.htmlBlockCloseTagEol)
    } else {
      context.delegateRender()
    }
  }

  private def renderVideoImage(srcNode: Node, url: String, altText: String, attributes: Attributes, html: HtmlWriter): Boolean = {
    var bareUrl = url
    var pos     = url.indexOf('?')
    if (pos != -1) {
      bareUrl = url.substring(0, pos)
    }

    pos = bareUrl.lastIndexOf('.')
    if (pos != -1) {
      val extension = bareUrl.substring(pos + 1)
      if (gitLabOptions.videoImageExtensionSet.contains(extension)) {
        html
          .attr(Attribute.CLASS_ATTR, gitLabOptions.videoImageClass)
          .attr(attributes)
          .withAttr()
          .tagLineIndent(
            "div",
            () => {
              html.srcPos(srcNode.chars).attr("src", url).attr("width", "400").attr("controls", "true").withAttr(GitLabNodeRenderer.VIDEO).tag("video").tag("/video").line()

              if (gitLabOptions.renderVideoLink) {
                html
                  .tag("p")
                  .attr("href", url)
                  .attr("target", "_blank")
                  .attr("rel", "noopener noreferrer")
                  .attr("title", String.format(gitLabOptions.videoImageLinkTextFormat, altText))
                  .withAttr(GitLabNodeRenderer.VIDEO_LINK)
                  .tag("a")
                  .text(altText)
                  .tag("/a")
                  .tag("/p")
                  .line()
              }
            }
          )
        true
      } else false
    } else false
  }

  private def renderImage(node: Image, context: NodeRendererContext, html: HtmlWriter): Unit =
    if (!(context.isDoNotRenderLinks || CoreNodeRenderer.isSuppressedLinkPrefix(node.url, context))) {
      val altText      = new TextCollectingVisitor().collectAndGetText(node)
      val resolvedLink = context.resolveLink(LinkType.IMAGE, node.url.unescape(), Nullable.empty, Nullable.empty)
      val url          = resolvedLink.url

      if (node.urlContent.isEmpty) {
        val attributes         = resolvedLink.getNonNullAttributes
        val extendedAttributes = context.extendRenderingNodeAttributes(node, AttributablePart.NODE, attributes)

        if (renderVideoImage(node, url, altText, extendedAttributes, html)) {
          // handled
        } else {
          context.delegateRender()
        }
      } else {
        context.delegateRender()
      }
    }

  private def renderImageRef(node: ImageRef, context: NodeRendererContext, html: HtmlWriter): Unit = {
    var resolvedLink: Nullable[ResolvedLink] = Nullable.empty
    var isSuppressed = false

    if (!node.isDefined && recheckUndefinedReferences) {
      if (node.getReferenceNode(referenceRepository) != null) { // @nowarn - may be null
        node.isDefined = true
      }
    }

    if (node.isDefined) {
      val reference = node.getReferenceNode(referenceRepository)
      val url       = reference.url.unescape()
      isSuppressed = CoreNodeRenderer.isSuppressedLinkPrefix(url, context)

      var rl = context.resolveLink(LinkType.IMAGE, url, Nullable.empty, Nullable.empty)
      if (reference.title.isNotNull) {
        rl = rl.withTitle(reference.title.unescape())
      }
      resolvedLink = Nullable(rl)
    } else {
      // see if have reference resolver and this is resolved
      val normalizeRef = referenceRepository.normalizeKey(node.reference)
      val rl           = context.resolveLink(LinkType.IMAGE_REF, normalizeRef, Nullable.empty, Nullable.empty)
      if (rl.status != LinkStatus.UNKNOWN) {
        resolvedLink = Nullable(rl)
      }
    }

    resolvedLink.foreach { rl =>
      if (!(context.isDoNotRenderLinks || isSuppressed)) {
        val altText    = new TextCollectingVisitor().collectAndGetText(node)
        val url        = rl.url
        val attributes = rl.getNonNullAttributes
        if (renderVideoImage(node, url, altText, attributes, html)) {
          // handled - return
        } else {
          context.delegateRender()
        }
      } else {
        context.delegateRender()
      }
    }

    if (resolvedLink.isEmpty) {
      context.delegateRender()
    }
  }
}

object GitLabNodeRenderer {

  val VIDEO:      AttributablePart = new AttributablePart("VIDEO")
  val VIDEO_LINK: AttributablePart = new AttributablePart("VIDEO_LINK")

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new GitLabNodeRenderer(options)
  }
}
