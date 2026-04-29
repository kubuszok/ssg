/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/CoreNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/CoreNodeRenderer.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package html
package renderer

import ssg.md.ast.*
import ssg.md.ast.util.{ LineCollectingVisitor, ReferenceRepository }
import ssg.md.html.{ HtmlRenderer, HtmlWriter }
import ssg.md.parser.{ ListOptions, Parser }
import ssg.md.util.ast.{ Document, Node, NonRenderingInline, TextCollectingVisitor }
import ssg.md.util.data.DataHolder
import ssg.md.util.html.{ Attribute, Attributes }
import ssg.md.util.misc.CharPredicate
import ssg.md.util.sequence.{ BasedSequence, Escaping, Range }

import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

/** The node renderer that renders all the core nodes (comes last in the order of node renderers).
  */
class CoreNodeRenderer(options: DataHolder) extends NodeRenderer {
  private val referenceRepository:        ReferenceRepository = Parser.REFERENCES.get(options)
  private val recheckUndefinedReferences: Boolean             = HtmlRenderer.RECHECK_UNDEFINED_REFERENCES.get(Nullable(options))
  private val listOptions:                ListOptions         = ListOptions.get(options)
  private val obfuscateEmail:             Boolean             = HtmlRenderer.OBFUSCATE_EMAIL.get(Nullable(options))
  private val obfuscateEmailRandom:       Boolean             = HtmlRenderer.OBFUSCATE_EMAIL_RANDOM.get(Nullable(options))
  private val codeContentBlock:           Boolean             = Parser.FENCED_CODE_CONTENT_BLOCK.get(options)
  private val codeSoftLineBreaks:         Boolean             = Parser.CODE_SOFT_LINE_BREAKS.get(options)

  private var myLines:             Nullable[List[Range]] = Nullable.empty
  private var myEOLs:              Nullable[List[Int]]   = Nullable.empty
  private var myNextLine:          Int                   = 0
  private var nextLineStartOffset: Int                   = 0

  override def getNodeRenderingHandlers: Nullable[Set[NodeRenderingHandler[?]]] =
    Nullable(
      Set[NodeRenderingHandler[?]](
        new NodeRenderingHandler[AutoLink](classOf[AutoLink], (node, context, html) => renderAutoLink(node, context, html)),
        new NodeRenderingHandler[BlockQuote](classOf[BlockQuote], (node, context, html) => renderBlockQuote(node, context, html)),
        new NodeRenderingHandler[BulletList](classOf[BulletList], (node, context, html) => renderBulletList(node, context, html)),
        new NodeRenderingHandler[Code](classOf[Code], (node, context, html) => renderCode(node, context, html)),
        new NodeRenderingHandler[CodeBlock](classOf[CodeBlock], (node, context, html) => renderCodeBlock(node, context, html)),
        new NodeRenderingHandler[Document](classOf[Document], (node, context, html) => renderDocument(node, context, html)),
        new NodeRenderingHandler[Emphasis](classOf[Emphasis], (node, context, html) => renderEmphasis(node, context, html)),
        new NodeRenderingHandler[FencedCodeBlock](classOf[FencedCodeBlock], (node, context, html) => renderFencedCodeBlock(node, context, html)),
        new NodeRenderingHandler[HardLineBreak](classOf[HardLineBreak], (node, context, html) => renderHardLineBreak(node, context, html)),
        new NodeRenderingHandler[Heading](classOf[Heading], (node, context, html) => renderHeading(node, context, html)),
        new NodeRenderingHandler[HtmlBlock](classOf[HtmlBlock], (node, context, html) => renderHtmlBlock(node, context, html)),
        new NodeRenderingHandler[HtmlCommentBlock](classOf[HtmlCommentBlock], (node, context, html) => renderHtmlCommentBlock(node, context, html)),
        new NodeRenderingHandler[HtmlInnerBlock](classOf[HtmlInnerBlock], (node, context, html) => renderHtmlInnerBlock(node, context, html)),
        new NodeRenderingHandler[HtmlInnerBlockComment](classOf[HtmlInnerBlockComment], (node, context, html) => renderHtmlInnerBlockComment(node, context, html)),
        new NodeRenderingHandler[HtmlEntity](classOf[HtmlEntity], (node, context, html) => renderHtmlEntity(node, context, html)),
        new NodeRenderingHandler[HtmlInline](classOf[HtmlInline], (node, context, html) => renderHtmlInline(node, context, html)),
        new NodeRenderingHandler[HtmlInlineComment](classOf[HtmlInlineComment], (node, context, html) => renderHtmlInlineComment(node, context, html)),
        new NodeRenderingHandler[Image](classOf[Image], (node, context, html) => renderImage(node, context, html)),
        new NodeRenderingHandler[ImageRef](classOf[ImageRef], (node, context, html) => renderImageRef(node, context, html)),
        new NodeRenderingHandler[IndentedCodeBlock](classOf[IndentedCodeBlock], (node, context, html) => renderIndentedCodeBlock(node, context, html)),
        new NodeRenderingHandler[Link](classOf[Link], (node, context, html) => renderLink(node, context, html)),
        new NodeRenderingHandler[LinkRef](classOf[LinkRef], (node, context, html) => renderLinkRef(node, context, html)),
        new NodeRenderingHandler[BulletListItem](classOf[BulletListItem], (node, context, html) => renderBulletListItem(node, context, html)),
        new NodeRenderingHandler[OrderedListItem](classOf[OrderedListItem], (node, context, html) => renderOrderedListItem(node, context, html)),
        new NodeRenderingHandler[MailLink](classOf[MailLink], (node, context, html) => renderMailLink(node, context, html)),
        new NodeRenderingHandler[OrderedList](classOf[OrderedList], (node, context, html) => renderOrderedList(node, context, html)),
        new NodeRenderingHandler[Paragraph](classOf[Paragraph], (node, context, html) => renderParagraph(node, context, html)),
        new NodeRenderingHandler[Reference](classOf[Reference], (node, context, html) => renderReference(node, context, html)),
        new NodeRenderingHandler[SoftLineBreak](classOf[SoftLineBreak], (node, context, html) => renderSoftLineBreak(node, context, html)),
        new NodeRenderingHandler[StrongEmphasis](classOf[StrongEmphasis], (node, context, html) => renderStrongEmphasis(node, context, html)),
        new NodeRenderingHandler[Text](classOf[Text], (node, context, html) => renderText(node, context, html)),
        new NodeRenderingHandler[TextBase](classOf[TextBase], (node, context, html) => renderTextBase(node, context, html)),
        new NodeRenderingHandler[ThematicBreak](classOf[ThematicBreak], (node, context, html) => renderThematicBreak(node, context, html))
      )
    )

  private def renderDocument(node: Document, context: NodeRendererContext, html: HtmlWriter): Unit =
    // No rendering itself
    context.renderChildren(node)

  private def renderHeading(node: Heading, context: NodeRendererContext, html: HtmlWriter): Unit = {
    if (context.getHtmlOptions.renderHeaderId) {
      val id = context.getNodeId(node)
      id.foreach { idVal =>
        if (idVal.nonEmpty) html.attr("id", idVal)
      }
    }

    if (context.getHtmlOptions.sourcePositionParagraphLines) {
      html
        .srcPos(node.chars)
        .withAttr()
        .tagLine(
          "h" + node.level,
          (() => {
            html.srcPos(node.text).withAttr().tag("span")
            context.renderChildren(node)
            html.tag("/span")
            ()
          }): Runnable
        )
    } else {
      html.srcPos(node.text).withAttr().tagLine("h" + node.level, (() => context.renderChildren(node)): Runnable)
    }
  }

  private def renderBlockQuote(node: BlockQuote, context: NodeRendererContext, html: HtmlWriter): Unit =
    html.withAttr().tagLineIndent("blockquote", () => context.renderChildren(node))

  private def renderFencedCodeBlock(node: FencedCodeBlock, context: NodeRendererContext, html: HtmlWriter): Unit = {
    html.line()
    html.srcPosWithTrailingEOL(node.chars).withAttr().tag("pre").openPre()

    val info        = node.info
    val htmlOptions = context.getHtmlOptions
    if (info.isNotNull && !info.isBlank()) {
      val language      = node.infoDelimitedByAny(htmlOptions.languageDelimiterSet).unescape()
      val languageClass = htmlOptions.languageClassMap.getOrElse(language, htmlOptions.languageClassPrefix + language)
      html.attr("class", languageClass)
    } else {
      val noLanguageClass = htmlOptions.noLanguageClass.trim
      if (noLanguageClass.nonEmpty) {
        html.attr("class", noLanguageClass)
      }
    }

    html.srcPosWithEOL(node.contentChars).withAttr(CoreNodeRenderer.CODE_CONTENT).tag("code")
    if (codeContentBlock) {
      context.renderChildren(node)
    } else {
      html.text(node.contentChars.normalizeEOL())
    }
    html.tag("/code")
    html.tag("/pre").closePre()
    html.lineIf(htmlOptions.htmlBlockCloseTagEol)
  }

  private def renderThematicBreak(node: ThematicBreak, context: NodeRendererContext, html: HtmlWriter): Unit =
    html.srcPos(node.chars).withAttr().tagVoidLine("hr")

  private def renderIndentedCodeBlock(node: IndentedCodeBlock, context: NodeRendererContext, html: HtmlWriter): Unit = {
    html.line()
    html.srcPosWithEOL(node.chars).withAttr().tag("pre").openPre()

    val noLanguageClass = context.getHtmlOptions.noLanguageClass.trim
    if (noLanguageClass.nonEmpty) {
      html.attr("class", noLanguageClass)
    }

    html.srcPosWithEOL(node.contentChars).withAttr(CoreNodeRenderer.CODE_CONTENT).tag("code")
    if (codeContentBlock) {
      context.renderChildren(node)
    } else {
      html.text(node.contentChars.trimTailBlankLines().normalizeEndWithEOL())
    }
    html.tag("/code")
    html.tag("/pre").closePre()
    html.lineIf(context.getHtmlOptions.htmlBlockCloseTagEol)
  }

  private def renderCodeBlock(node: CodeBlock, context: NodeRendererContext, html: HtmlWriter): Unit =
    if (node.parent.exists(_.isInstanceOf[IndentedCodeBlock])) {
      html.text(node.contentChars.trimTailBlankLines().normalizeEndWithEOL())
    } else {
      html.text(node.contentChars.normalizeEOL())
    }

  private def renderBulletList(node: BulletList, context: NodeRendererContext, html: HtmlWriter): Unit =
    html.withAttr().tagIndent("ul", () => context.renderChildren(node))

  private def renderOrderedList(node: OrderedList, context: NodeRendererContext, html: HtmlWriter): Unit = {
    val start = node.startNumber
    if (listOptions.isOrderedListManualStart && start != 1) html.attr("start", String.valueOf(start))
    html.withAttr().tagIndent("ol", () => context.renderChildren(node))
  }

  private def renderBulletListItem(node: BulletListItem, context: NodeRendererContext, html: HtmlWriter): Unit =
    renderListItem(node, context, html)

  private def renderOrderedListItem(node: OrderedListItem, context: NodeRendererContext, html: HtmlWriter): Unit =
    renderListItem(node, context, html)

  private def renderListItem(node: ListItem, context: NodeRendererContext, html: HtmlWriter): Unit =
    if (listOptions.isTightListItem(node)) {
      html
        .srcPosWithEOL(node.chars)
        .withAttr(CoreNodeRenderer.TIGHT_LIST_ITEM)
        .withCondIndent()
        .tagLine("li",
                 () => {
                   html.text(node.markerSuffix.unescape())
                   context.renderChildren(node)
                 }
        )
    } else {
      html
        .srcPosWithEOL(node.chars)
        .withAttr(CoreNodeRenderer.LOOSE_LIST_ITEM)
        .withCondIndent()
        .tagLine("li",
                 () => {
                   html.text(node.markerSuffix.unescape())
                   context.renderChildren(node)
                 }
        )
    }

  def renderTextBlockParagraphLines(node: Paragraph, context: NodeRendererContext, html: HtmlWriter, wrapTextInSpan: Boolean): Unit =
    if (context.getHtmlOptions.sourcePositionParagraphLines && node.hasChildren) {
      val breakCollectingVisitor = new LineCollectingVisitor()
      myLines = Nullable(breakCollectingVisitor.collectAndGetRanges(node).asScala.toList)
      myEOLs = Nullable(breakCollectingVisitor.getEOLs.asScala.toList.map(_.intValue()))
      myNextLine = 0

      node.firstChild.foreach { firstChild =>
        outputSourceLineSpan(node, firstChild, node, html)
      }
      context.renderChildren(node)
      html.tag("/span")
    } else if (wrapTextInSpan) {
      html.withAttr().tag("span", false, false, () => context.renderChildren(node))
    } else {
      context.renderChildren(node)
    }

  private def outputSourceLineSpan(parentNode: Node, startNode: Node, endNode: Node, html: HtmlWriter): Unit = {
    var startOffset = startNode.startOffset
    val range       = myLines.get(myNextLine)
    val eolLength   = myEOLs.get(myNextLine)

    // remove trailing spaces from text
    var endOffset = endNode.endOffset
    if (range.end <= endOffset) {
      endOffset = range.end
      endOffset -= eolLength
      endOffset -= parentNode.baseSubSequence(startOffset, endOffset).countTrailing(CharPredicate.SPACE_TAB)
      myNextLine += 1
      nextLineStartOffset = range.end
      nextLineStartOffset += parentNode.baseSubSequence(nextLineStartOffset, parentNode.endOffset).countLeading(CharPredicate.SPACE_TAB)
    }

    if (range.start > startOffset) {
      startOffset = range.start
    }

    html.srcPos(startOffset, endOffset).withAttr(CoreNodeRenderer.PARAGRAPH_LINE).tag("span")
  }

  private def outputNextLineBreakSpan(node: Node, html: HtmlWriter, outputBreakText: Boolean): Unit = {
    val range     = myLines.get(myNextLine)
    val eolLength = myEOLs.get(myNextLine)
    myNextLine += 1

    // remove trailing spaces from text
    var countTrailing = node.baseSubSequence(nextLineStartOffset, range.end - eolLength).countTrailing(CharPredicate.SPACE_TAB)
    if (!outputBreakText && countTrailing > 0) {
      countTrailing -= 1
    }
    val totalEolLength = eolLength + countTrailing

    html.srcPos(nextLineStartOffset, range.end - totalEolLength).withAttr(CoreNodeRenderer.PARAGRAPH_LINE).tag("span")
    nextLineStartOffset = range.end

    // remove leading spaces
    nextLineStartOffset += node.baseSubSequence(nextLineStartOffset, node.chars.getBaseSequence.length()).countLeading(CharPredicate.SPACE_TAB)
  }

  private def renderLooseParagraph(node: Paragraph, context: NodeRendererContext, html: HtmlWriter): Unit =
    if (context.getHtmlOptions.noPTagsUseBr) {
      renderTextBlockParagraphLines(node, context, html, false)
      html.tagVoid("br").tagVoid("br").line()
    } else {
      html.srcPosWithEOL(node.chars).withAttr().tagLine("p", () => renderTextBlockParagraphLines(node, context, html, false))
    }

  private def renderParagraph(node: Paragraph, context: NodeRendererContext, html: HtmlWriter): Unit =
    if (node.firstChildAnyNot(classOf[NonRenderingInline]).isDefined) {
      node.parent match {
        case p if p.isDefined && p.get.isInstanceOf[ParagraphItemContainer] =>
          val pic = p.get.asInstanceOf[ParagraphItemContainer]
          if (!pic.isParagraphWrappingDisabled(node, listOptions, context.getOptions)) {
            renderLooseParagraph(node, context, html)
          } else {
            renderTextBlockParagraphLines(node, context, html, false)
          }
        case _ =>
          renderLooseParagraph(node, context, html)
      }
    }

  @SuppressWarnings(Array("org.wartremover.warts.Return"))
  private def renderLineBreak(breakText: String, suppressInTag: Nullable[String], node: Node, context: NodeRendererContext, html: HtmlWriter): Boolean =
    if (myLines.isDefined && myNextLine < myLines.get.size) {
      // here we may need to close tags opened since the span tag
      val openTags        = html.getOpenTagsAfterLast("span")
      val iMax            = openTags.size
      val outputBreakText = iMax == 0 || suppressInTag.isEmpty || !suppressInTag.get.equalsIgnoreCase(openTags(iMax - 1))

      if (!outputBreakText && !html.isPendingSpace) {
        // we add a space for EOL
        html.raw(" ")
      }

      var i = iMax - 1
      while (i >= 0) {
        html.closeTag(openTags(i))
        i -= 1
      }

      html.tag("/span")

      if (outputBreakText) {
        html.raw(breakText)
      }

      outputNextLineBreakSpan(node, html, outputBreakText)

      var j = 0
      while (j < openTags.size) {
        val tag = openTags(j)
        if (!outputBreakText && context.getHtmlOptions.inlineCodeSpliceClass.exists(_.nonEmpty)) {
          html.attr(Attribute.CLASS_ATTR, context.getHtmlOptions.inlineCodeSpliceClass.get).withAttr().tag(tag)
        } else {
          html.tag(tag)
        }
        j += 1
      }
      true
    } else {
      false
    }

  private def renderSoftLineBreak(node: SoftLineBreak, context: NodeRendererContext, html: HtmlWriter): Unit = {
    val softBreak = context.getHtmlOptions.softBreak
    if (context.getHtmlOptions.sourcePositionParagraphLines) {
      val suppressInCode = if (softBreak == "\n" || softBreak == "\r\n" || softBreak == "\r") Nullable("code") else Nullable.empty[String]
      if (renderLineBreak(softBreak, suppressInCode, node, context, html)) {
        // rendered via line break span
      } else {
        html.raw(softBreak)
      }
    } else {
      html.raw(softBreak)
    }
  }

  private def renderHardLineBreak(node: HardLineBreak, context: NodeRendererContext, html: HtmlWriter): Unit =
    if (context.getHtmlOptions.sourcePositionParagraphLines) {
      if (!renderLineBreak(context.getHtmlOptions.hardBreak, Nullable.empty, node, context, html)) {
        html.raw(context.getHtmlOptions.hardBreak)
      }
    } else {
      html.raw(context.getHtmlOptions.hardBreak)
    }

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  private def renderEmphasis(node: Emphasis, context: NodeRendererContext, html: HtmlWriter): Unit = {
    val htmlOptions    = context.getHtmlOptions
    val useCustomStyle = htmlOptions.emphasisStyleHtmlOpen.isDefined && htmlOptions.emphasisStyleHtmlClose.isDefined
    if (!useCustomStyle) {
      if (context.getHtmlOptions.sourcePositionParagraphLines) {
        html.withAttr().tag("em")
      } else {
        html.srcPos(node.text).withAttr().tag("em")
      }
      context.renderChildren(node)
      html.tag("/em")
    } else {
      html.raw(htmlOptions.emphasisStyleHtmlOpen.get)
      context.renderChildren(node)
      html.raw(htmlOptions.emphasisStyleHtmlClose.get)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  private def renderStrongEmphasis(node: StrongEmphasis, context: NodeRendererContext, html: HtmlWriter): Unit = {
    val htmlOptions    = context.getHtmlOptions
    val useCustomStyle = htmlOptions.strongEmphasisStyleHtmlOpen.isDefined && htmlOptions.strongEmphasisStyleHtmlClose.isDefined
    if (!useCustomStyle) {
      if (context.getHtmlOptions.sourcePositionParagraphLines) {
        html.withAttr().tag("strong")
      } else {
        html.srcPos(node.text).withAttr().tag("strong")
      }
      context.renderChildren(node)
      html.tag("/strong")
    } else {
      html.raw(htmlOptions.strongEmphasisStyleHtmlOpen.get)
      context.renderChildren(node)
      html.raw(htmlOptions.strongEmphasisStyleHtmlClose.get)
    }
  }

  private def renderText(node: Text, context: NodeRendererContext, html: HtmlWriter): Unit =
    html.text(Escaping.normalizeEOL(node.chars.unescape()))

  private def renderTextBase(node: TextBase, context: NodeRendererContext, html: HtmlWriter): Unit =
    context.renderChildren(node)

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  private def renderCode(node: Code, context: NodeRendererContext, html: HtmlWriter): Unit = {
    val htmlOptions = context.getHtmlOptions
    if (htmlOptions.codeStyleHtmlOpen.isEmpty || htmlOptions.codeStyleHtmlClose.isEmpty) {
      if (context.getHtmlOptions.sourcePositionParagraphLines) {
        html.withAttr().tag("code")
      } else {
        html.srcPos(node.text).withAttr().tag("code")
      }
      if (codeSoftLineBreaks && !htmlOptions.isSoftBreakAllSpaces) {
        node.children.asScala.foreach {
          case text: Text =>
            html.text(Escaping.collapseWhitespace(text.chars, true))
          case child =>
            context.render(child)
        }
      } else {
        html.text(Escaping.collapseWhitespace(node.text, true))
      }
      html.tag("/code")
    } else {
      html.raw(htmlOptions.codeStyleHtmlOpen.get)
      if (codeSoftLineBreaks && !htmlOptions.isSoftBreakAllSpaces) {
        node.children.asScala.foreach {
          case text: Text =>
            html.text(Escaping.collapseWhitespace(text.chars, true))
          case child =>
            context.render(child)
        }
      } else {
        html.text(Escaping.collapseWhitespace(node.text, true))
      }
      html.raw(htmlOptions.codeStyleHtmlClose.get)
    }
  }

  private def renderHtmlBlock(node: HtmlBlock, context: NodeRendererContext, html: HtmlWriter): Unit = {
    html.line()
    val htmlOptions = context.getHtmlOptions

    if (htmlOptions.sourceWrapHtmlBlocks) {
      html.srcPos(node.chars).withAttr(AttributablePart.NODE_POSITION).tag("div").indent().line()
    }

    if (node.hasChildren) {
      // inner blocks handle rendering
      context.renderChildren(node)
    } else {
      CoreNodeRenderer.renderHtmlBlock(node, context, html, htmlOptions.suppressHtmlBlocks, htmlOptions.escapeHtmlBlocks, false)
    }

    if (htmlOptions.sourceWrapHtmlBlocks) {
      html.unIndent()
      html.tag("/div")
    }

    html.lineIf(htmlOptions.htmlBlockCloseTagEol)
  }

  private def renderHtmlCommentBlock(node: HtmlCommentBlock, context: NodeRendererContext, html: HtmlWriter): Unit =
    CoreNodeRenderer.renderHtmlBlock(
      node,
      context,
      html,
      context.getHtmlOptions.suppressHtmlCommentBlocks,
      context.getHtmlOptions.escapeHtmlCommentBlocks,
      false
    )

  private def renderHtmlInnerBlock(node: HtmlInnerBlock, context: NodeRendererContext, html: HtmlWriter): Unit =
    CoreNodeRenderer.renderHtmlBlock(node, context, html, context.getHtmlOptions.suppressHtmlBlocks, context.getHtmlOptions.escapeHtmlBlocks, false)

  private def renderHtmlInnerBlockComment(node: HtmlInnerBlockComment, context: NodeRendererContext, html: HtmlWriter): Unit =
    CoreNodeRenderer.renderHtmlBlock(
      node,
      context,
      html,
      context.getHtmlOptions.suppressHtmlCommentBlocks,
      context.getHtmlOptions.escapeHtmlCommentBlocks,
      false
    )

  private def renderHtmlInline(node: HtmlInline, context: NodeRendererContext, html: HtmlWriter): Unit =
    CoreNodeRenderer.renderInlineHtml(node, context, html, context.getHtmlOptions.suppressInlineHtml, context.getHtmlOptions.escapeInlineHtml)

  private def renderHtmlInlineComment(node: HtmlInlineComment, context: NodeRendererContext, html: HtmlWriter): Unit =
    CoreNodeRenderer.renderInlineHtml(node, context, html, context.getHtmlOptions.suppressInlineHtmlComments, context.getHtmlOptions.escapeInlineHtmlComments)

  private def renderReference(node: Reference, context: NodeRendererContext, html: HtmlWriter): Unit = {
    // intentionally empty
  }

  private def renderHtmlEntity(node: HtmlEntity, context: NodeRendererContext, html: HtmlWriter): Unit =
    if (context.getHtmlOptions.unescapeHtmlEntities) {
      html.text(node.chars.unescape())
    } else {
      html.raw(node.chars.unescapeNoEntities())
    }

  private def renderAutoLink(node: AutoLink, context: NodeRendererContext, html: HtmlWriter): Unit = {
    val text = node.text.toString
    if (context.isDoNotRenderLinks || CoreNodeRenderer.isSuppressedLinkPrefix(node.url, context)) {
      html.text(text)
    } else {
      val resolvedLink = context.resolveLink(LinkType.LINK, text, Nullable.empty)
      val url          = if (resolvedLink.url.startsWith("www.")) context.getHtmlOptions.autolinkWwwPrefix + resolvedLink.url else resolvedLink.url
      html.srcPos(node.text).attr("href", url).withAttr(resolvedLink).tag("a", false, false, (() => { html.text(text); () }): Runnable)
    }
  }

  private def renderMailLink(node: MailLink, context: NodeRendererContext, html: HtmlWriter): Unit = {
    var text = node.text.unescape()
    if (context.isDoNotRenderLinks || CoreNodeRenderer.isSuppressedLinkPrefix(node.url, context)) {
      html.text(text)
    } else {
      val resolvedLink = context.resolveLink(LinkType.LINK, text, Nullable.empty)
      if (obfuscateEmail) {
        val url = Escaping.obfuscate("mailto:" + resolvedLink.url, obfuscateEmailRandom)
        text = Escaping.obfuscate(text, true)
        html.srcPos(node.text).attr("href", url).withAttr(resolvedLink).tag("a").raw(text).tag("/a")
      } else {
        val url = resolvedLink.url
        html.srcPos(node.text).attr("href", "mailto:" + url).withAttr(resolvedLink).tag("a").text(text).tag("/a")
      }
    }
  }

  private def renderImage(node: Image, context: NodeRendererContext, html: HtmlWriter): Unit =
    if (!(context.isDoNotRenderLinks || CoreNodeRenderer.isSuppressedLinkPrefix(node.url, context))) {
      val altText      = new TextCollectingVisitor().collectAndGetText(node)
      var resolvedLink = context.resolveLink(LinkType.IMAGE, node.url.unescape(), Nullable.empty[Attributes], Nullable.empty)
      var url          = resolvedLink.url

      if (!node.urlContent.isEmpty) {
        // reverse URL encoding of =, &
        val content = Escaping.percentEncodeUrl(node.urlContent).replace("+", "%2B").replace("%3D", "=").replace("%26", "&amp;")
        url += content
      }

      html.attr("src", url)
      html.attr("alt", altText)

      // we have a title part, use that
      if (node.title.isNotNull) {
        resolvedLink = resolvedLink.withTitle(Nullable(node.title.unescape()))
      }

      html.attr(resolvedLink.getNonNullAttributes)
      html.srcPos(node.chars).withAttr(resolvedLink).tagVoid("img")
    }

  private def renderLink(node: Link, context: NodeRendererContext, html: HtmlWriter): Unit =
    if (context.isDoNotRenderLinks || CoreNodeRenderer.isSuppressedLinkPrefix(node.url, context)) {
      context.renderChildren(node)
    } else {
      var resolvedLink = context.resolveLink(LinkType.LINK, node.url.unescape(), Nullable.empty[Attributes], Nullable.empty)

      html.attr("href", resolvedLink.url)

      // we have a title part, use that
      if (node.title.isNotNull) {
        resolvedLink = resolvedLink.withTitle(Nullable(node.title.unescape()))
      }

      html.attr(resolvedLink.getNonNullAttributes)
      html.srcPos(node.chars).withAttr(resolvedLink).tag("a")
      renderChildrenSourceLineWrapped(node, node.text, context, html)
      html.tag("/a")
    }

  private def renderChildrenSourceLineWrapped(
    node:          Node,
    nodeChildText: BasedSequence,
    context:       NodeRendererContext,
    html:          HtmlWriter
  ): Unit =
    // if have SOFT BREAK or HARD BREAK as child then we open our own span
    if (context.getHtmlOptions.sourcePositionParagraphLines && nodeChildText.indexOfAny(CharPredicate.ANY_EOL) >= 0) {
      if (myNextLine > 0) {
        myNextLine -= 1
      }

      outputSourceLineSpan(node, node, node, html)
      context.renderChildren(node)
      html.tag("/span")
    } else {
      context.renderChildren(node)
    }

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  private def renderImageRef(node: ImageRef, context: NodeRendererContext, html: HtmlWriter): Unit = {
    var resolvedLink: Nullable[ResolvedLink] = Nullable.empty
    var isSuppressed = false

    if (!node.isDefined && recheckUndefinedReferences) {
      if (node.getReferenceNode(referenceRepository) != null) { // @nowarn - Java interop: getReferenceNode may return null
        node.isDefined = true
      }
    }

    var reference: Reference = null.asInstanceOf[Reference] // @nowarn - Java interop: may be null

    if (node.isDefined) {
      reference = node.getReferenceNode(referenceRepository)
      val url = reference.url.unescape()
      isSuppressed = CoreNodeRenderer.isSuppressedLinkPrefix(url, context)

      var rl = context.resolveLink(LinkType.IMAGE, url, Nullable.empty[Attributes], Nullable.empty)
      if (reference.title.isNotNull) {
        rl = rl.withTitle(Nullable(reference.title.unescape()))
      }
      resolvedLink = Nullable(rl)
    } else {
      // see if have reference resolver and this is resolved
      val normalizeRef = referenceRepository.normalizeKey(node.reference)
      val rl           = context.resolveLink(LinkType.IMAGE_REF, normalizeRef, Nullable.empty[Attributes], Nullable.empty)
      if (rl.status == LinkStatus.UNKNOWN || rl.url.isEmpty) {
        resolvedLink = Nullable.empty
      } else {
        resolvedLink = Nullable(rl)
      }
    }

    if (resolvedLink.isEmpty) {
      // empty ref, we treat it as text
      html.text(node.chars.unescape())
    } else {
      if (!(context.isDoNotRenderLinks || isSuppressed)) {
        val altText    = new TextCollectingVisitor().collectAndGetText(node)
        var attributes = resolvedLink.get.getNonNullAttributes

        html.attr("src", resolvedLink.get.url)
        html.attr("alt", altText)

        // need to take attributes for reference definition, then overlay them with ours
        if (reference != null) { // @nowarn - Java interop
          attributes = context.extendRenderingNodeAttributes(reference, AttributablePart.NODE, Nullable(attributes))
        }

        html.attr(attributes)
        html.srcPos(node.chars).withAttr(resolvedLink.get).tagVoid("img")
      }
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  private def renderLinkRef(node: LinkRef, context: NodeRendererContext, html: HtmlWriter): Unit = {
    var resolvedLink: Nullable[ResolvedLink] = Nullable.empty
    var isSuppressed = false

    if (!node.isDefined && recheckUndefinedReferences) {
      if (node.getReferenceNode(referenceRepository) != null) { // @nowarn - Java interop: getReferenceNode may return null
        node.isDefined = true
      }
    }

    var reference: Reference = null.asInstanceOf[Reference] // @nowarn - Java interop: may be null
    if (node.isDefined) {
      reference = node.getReferenceNode(referenceRepository)
      val url = reference.url.unescape()
      isSuppressed = CoreNodeRenderer.isSuppressedLinkPrefix(url, context)

      var rl = context.resolveLink(LinkType.LINK, url, Nullable.empty[Attributes], Nullable.empty)
      if (reference.title.isNotNull) {
        rl = rl.withTitle(Nullable(reference.title.unescape()))
      }
      resolvedLink = Nullable(rl)
    } else {
      // see if have reference resolver and this is resolved
      val normalizeRef = node.reference.unescape()
      val rl           = context.resolveLink(LinkType.LINK_REF, normalizeRef, Nullable.empty[Attributes], Nullable.empty)
      if (rl.status == LinkStatus.UNKNOWN || rl.url.isEmpty) {
        resolvedLink = Nullable.empty
      } else {
        resolvedLink = Nullable(rl)
      }
    }

    if (resolvedLink.isEmpty) {
      // empty ref, we treat it as text
      assert(!node.isDefined)
      if (!node.hasChildren) {
        html.text(node.chars.unescape())
      } else {
        html.text(node.chars.prefixOf(node.childChars).unescape())
        renderChildrenSourceLineWrapped(node, node.text, context, html)
        html.text(node.chars.suffixOf(node.childChars).unescape())
      }
    } else {
      if (context.isDoNotRenderLinks || isSuppressed) {
        context.renderChildren(node)
      } else {
        var attributes = resolvedLink.get.getNonNullAttributes

        html.attr("href", resolvedLink.get.url)

        if (reference != null) { // @nowarn - Java interop
          attributes = context.extendRenderingNodeAttributes(reference, AttributablePart.NODE, Nullable(attributes))
        }

        html.attr(attributes)
        html.srcPos(node.chars).withAttr(resolvedLink.get).tag("a")
        renderChildrenSourceLineWrapped(node, node.text, context, html)
        html.tag("/a")
      }
    }
  }
}

object CoreNodeRenderer {
  val LOOSE_LIST_ITEM: AttributablePart = new AttributablePart("LOOSE_LIST_ITEM")
  val TIGHT_LIST_ITEM: AttributablePart = new AttributablePart("TIGHT_LIST_ITEM")
  val PARAGRAPH_LINE:  AttributablePart = new AttributablePart("PARAGRAPH_LINE")
  val CODE_CONTENT:    AttributablePart = new AttributablePart("FENCED_CODE_CONTENT")

  def renderHtmlBlock(node: HtmlBlockBase, context: NodeRendererContext, html: HtmlWriter, suppress: Boolean, escape: Boolean, trimSpaces: Boolean): Unit =
    if (suppress) {
      // do nothing
    } else {
      if (node.isInstanceOf[HtmlBlock]) html.line()

      var normalizeEOL = if (node.isInstanceOf[HtmlBlock]) node.contentChars.normalizeEOL() else node.chars.normalizeEOL()

      if (trimSpaces) {
        normalizeEOL = normalizeEOL.trim
      }

      if (escape) {
        if (node.isInstanceOf[HtmlBlock]) {
          if (normalizeEOL.nonEmpty && normalizeEOL.charAt(normalizeEOL.length - 1) == '\n') {
            // leave off the trailing EOL
            normalizeEOL = normalizeEOL.substring(0, normalizeEOL.length - 1)
          }
          html.raw("<p>").text(normalizeEOL).raw("</p>")
        } else {
          html.text(normalizeEOL)
        }
      } else {
        html.rawPre(normalizeEOL)
      }

      if (node.isInstanceOf[HtmlBlock]) {
        html.lineIf(context.getHtmlOptions.htmlBlockCloseTagEol)
      }
    }

  def renderInlineHtml(node: HtmlInlineBase, context: NodeRendererContext, html: HtmlWriter, suppress: Boolean, escape: Boolean): Unit =
    if (!suppress) {
      if (escape) {
        html.text(node.chars.normalizeEOL())
      } else {
        html.rawPre(node.chars.normalizeEOL())
      }
    }

  def isSuppressedLinkPrefix(url: CharSequence, context: NodeRendererContext): Boolean =
    context.getHtmlOptions.suppressedLinks.exists { suppressedLinks =>
      val matcher = suppressedLinks.matcher(url)
      matcher.matches()
    }

  class Factory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer =
      new CoreNodeRenderer(options)
  }
}
