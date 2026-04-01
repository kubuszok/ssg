/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/internal/CoreNodeFormatter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package formatter
package internal

import ssg.md.ast.*
import ssg.md.ast.util.ReferenceRepository
import ssg.md.html.renderer.LinkType
import ssg.md.parser.{ ListOptions, Parser, ParserEmulationProfile }
import ssg.md.util.ast.{ BlankLine, Block, Document, Node, NodeRepository }
import ssg.md.util.data.{ DataHolder, DataKey, DataKeyBase, MutableDataHolder }
import ssg.md.util.format.options.{ CodeFenceMarker, DiscretionaryText, ElementPlacement, ElementPlacementSort, EqualizeTrailingMarker, ListSpacing }
import ssg.md.util.misc.{ CharPredicate, Utils }
import ssg.md.util.sequence.{ BasedSequence, RepeatedSequence, SequenceUtils }
import ssg.md.util.sequence.mappers.SpaceMapper


import scala.language.implicitConversions

/** The node formatter that formats all the core nodes (comes last in the order of node formatters).
  */
class CoreNodeFormatter(options: DataHolder)
    extends NodeRepositoryFormatter[ReferenceRepository, Reference, RefNode](options, null.asInstanceOf[DataKey[java.util.Map[String, String]]], Formatter.UNIQUIFICATION_MAP) { // @nowarn - Java interop: parent class checks for null

  private val formatterOptions: FormatterOptions = new FormatterOptions(options)
  private val listOptions:      ListOptions      = ListOptions.get(options)
  private val myHtmlBlockPrefix:            String = "<" + formatterOptions.translationHtmlBlockPrefix
  private val myHtmlInlinePrefix:           String = formatterOptions.translationHtmlInlinePrefix
  private val myTranslationAutolinkPrefix:  String = formatterOptions.translationAutolinkPrefix
  private var blankLines: Int = 0
  private var myTranslationStore: Nullable[MutableDataHolder] = Nullable.empty
  private var attributeUniquificationIdMap: Nullable[java.util.Map[String, String]] = Nullable.empty

  override def getBlockQuoteLikePrefixChar: Char = '>'

  override def getNodeFormattingHandlers: Nullable[Set[NodeFormattingHandler[?]]] = {
    Nullable(Set(
      // Generic unknown node formatter
      new NodeFormattingHandler[Node](classOf[Node], (node, context, markdown) => renderGenericNode(node, context, markdown)),

      // specific nodes
      new NodeFormattingHandler[AutoLink](classOf[AutoLink], (node, context, markdown) => renderAutoLink(node, context, markdown)),
      new NodeFormattingHandler[BlankLine](classOf[BlankLine], (node, context, markdown) => renderBlankLine(node, context, markdown)),
      new NodeFormattingHandler[BlockQuote](classOf[BlockQuote], (node, context, markdown) => renderBlockQuote(node, context, markdown)),
      new NodeFormattingHandler[Code](classOf[Code], (node, context, markdown) => renderCode(node, context, markdown)),
      new NodeFormattingHandler[Document](classOf[Document], (node, context, markdown) => renderDocument(node, context, markdown)),
      new NodeFormattingHandler[Emphasis](classOf[Emphasis], (node, context, markdown) => renderEmphasis(node, context, markdown)),
      new NodeFormattingHandler[FencedCodeBlock](classOf[FencedCodeBlock], (node, context, markdown) => renderFencedCodeBlock(node, context, markdown)),
      new NodeFormattingHandler[HardLineBreak](classOf[HardLineBreak], (node, context, markdown) => renderHardLineBreak(node, context, markdown)),
      new NodeFormattingHandler[Heading](classOf[Heading], (node, context, markdown) => renderHeading(node, context, markdown)),
      new NodeFormattingHandler[HtmlBlock](classOf[HtmlBlock], (node, context, markdown) => renderHtmlBlock(node, context, markdown)),
      new NodeFormattingHandler[HtmlCommentBlock](classOf[HtmlCommentBlock], (node, context, markdown) => renderHtmlCommentBlock(node, context, markdown)),
      new NodeFormattingHandler[HtmlInnerBlock](classOf[HtmlInnerBlock], (node, context, markdown) => renderHtmlInnerBlock(node, context, markdown)),
      new NodeFormattingHandler[HtmlInnerBlockComment](classOf[HtmlInnerBlockComment], (node, context, markdown) => renderHtmlInnerBlockComment(node, context, markdown)),
      new NodeFormattingHandler[HtmlEntity](classOf[HtmlEntity], (node, context, markdown) => renderHtmlEntity(node, context, markdown)),
      new NodeFormattingHandler[HtmlInline](classOf[HtmlInline], (node, context, markdown) => renderHtmlInline(node, context, markdown)),
      new NodeFormattingHandler[HtmlInlineComment](classOf[HtmlInlineComment], (node, context, markdown) => renderHtmlInlineComment(node, context, markdown)),
      new NodeFormattingHandler[Image](classOf[Image], (node, context, markdown) => renderImage(node, context, markdown)),
      new NodeFormattingHandler[ImageRef](classOf[ImageRef], (node, context, markdown) => renderImageRef(node, context, markdown)),
      new NodeFormattingHandler[IndentedCodeBlock](classOf[IndentedCodeBlock], (node, context, markdown) => renderIndentedCodeBlock(node, context, markdown)),
      new NodeFormattingHandler[Link](classOf[Link], (node, context, markdown) => renderLink(node, context, markdown)),
      new NodeFormattingHandler[LinkRef](classOf[LinkRef], (node, context, markdown) => renderLinkRef(node, context, markdown)),
      new NodeFormattingHandler[BulletList](classOf[BulletList], (node, context, markdown) => FormatterUtils.renderList(node, context, markdown)),
      new NodeFormattingHandler[OrderedList](classOf[OrderedList], (node, context, markdown) => FormatterUtils.renderList(node, context, markdown)),
      new NodeFormattingHandler[BulletListItem](classOf[BulletListItem], (node, context, markdown) => FormatterUtils.renderListItem(node, context, markdown, listOptions, node.markerSuffix, false)),
      new NodeFormattingHandler[OrderedListItem](classOf[OrderedListItem], (node, context, markdown) => FormatterUtils.renderListItem(node, context, markdown, listOptions, node.markerSuffix, false)),
      new NodeFormattingHandler[MailLink](classOf[MailLink], (node, context, markdown) => renderMailLink(node, context, markdown)),
      new NodeFormattingHandler[Paragraph](classOf[Paragraph], (node, context, markdown) => renderParagraph(node, context, markdown)),
      new NodeFormattingHandler[Reference](classOf[Reference], (node, context, markdown) => renderReference(node, context, markdown)),
      new NodeFormattingHandler[SoftLineBreak](classOf[SoftLineBreak], (node, context, markdown) => renderSoftLineBreak(node, context, markdown)),
      new NodeFormattingHandler[StrongEmphasis](classOf[StrongEmphasis], (node, context, markdown) => renderStrongEmphasis(node, context, markdown)),
      new NodeFormattingHandler[Text](classOf[Text], (node, context, markdown) => renderText(node, context, markdown)),
      new NodeFormattingHandler[TextBase](classOf[TextBase], (node, context, markdown) => context.renderChildren(node)),
      new NodeFormattingHandler[ThematicBreak](classOf[ThematicBreak], (node, context, markdown) => renderThematicBreak(node, context, markdown))
    ))
  }

  override def getNodeClasses: Nullable[Set[Class[?]]] = {
    if (formatterOptions.referencePlacement.isNoChange || !formatterOptions.referenceSort.isUnused) Nullable.empty
    else Nullable(Set(classOf[RefNode]))
  }

  override def getRepository(options: DataHolder): ReferenceRepository =
    Parser.REFERENCES.get(Nullable(options))

  override def getReferencePlacement: ElementPlacement =
    formatterOptions.referencePlacement

  override def getReferenceSort: ElementPlacementSort =
    formatterOptions.referenceSort

  private def appendReference(id: CharSequence, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    if (context.isTransformingText && context.getRenderPurpose == RenderPurpose.TRANSLATED && context.getMergeContext.isDefined) {
      // may need to map references
      val reference = String.valueOf(context.transformTranslating(Nullable.empty, id, Nullable.empty, Nullable.empty))
      val uniquifiedReference = referenceUniqificationMap.fold(reference)(_.getOrDefault(reference, reference))
      markdown.append(uniquifiedReference)
    } else {
      markdown.appendTranslating(id)
    }
  }

  override def renderReferenceBlock(node: Reference, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    if (context.isTransformingText) {
      markdown.append(node.openingMarker)
      appendReference(node.reference, context, markdown)
      markdown.append(node.closingMarker)

      markdown.append(' ')

      markdown.append(node.urlOpeningMarker)

      if (context.getRenderPurpose == RenderPurpose.TRANSLATION_SPANS) {
        val resolvedLink = context.resolveLink(LinkType.LINK, node.url, false)
        markdown.appendNonTranslating(resolvedLink.getPageRef)

        if (resolvedLink.getAnchorRef.isDefined) { // @nowarn - Java interop: getAnchorRef may return null
          markdown.append("#")
          val anchorRef = context.transformAnchorRef(resolvedLink.getPageRef, resolvedLink.getAnchorRef.get)
          if (attributeUniquificationIdMap.isDefined && resolvedLink.getPageRef.isEmpty && context.isTransformingText && context.getMergeContext.isDefined) {
            val stringAnchorRef = String.valueOf(anchorRef)
            val uniquifiedAnchorRef = attributeUniquificationIdMap.get.getOrDefault(stringAnchorRef, stringAnchorRef)
            markdown.append(uniquifiedAnchorRef)
          } else {
            markdown.append(anchorRef)
          }
          markdown.append(anchorRef)
        }
      } else {
        markdown.appendNonTranslating(node.pageRef)

        markdown.append(node.anchorMarker)
        if (node.anchorRef.isNotNull) {
          val anchorRef = context.transformAnchorRef(node.pageRef, node.anchorRef)
          markdown.append(anchorRef)
        }
      }

      if (node.titleOpeningMarker.isNotNull) {
        markdown.append(' ')
        markdown.append(node.titleOpeningMarker)
        if (node.title.isNotNull) markdown.appendTranslating(node.title)
        markdown.append(node.titleClosingMarker)
      }

      markdown.append(node.urlClosingMarker).line()
    } else {
      markdown.append(node.chars).line()
      val next = node.next
      if (next.isDefined && (next.get.isInstanceOf[HtmlCommentBlock] || next.get.isInstanceOf[HtmlInnerBlockComment])) {
        val text = next.get.chars.trim().midSequence(4, -3)
        if (formatterOptions.linkMarkerCommentPattern.isDefined && formatterOptions.linkMarkerCommentPattern.get.matcher(text).matches()) {
          // if after ref then output nothing, the ref takes care of this
          markdown.append("<!--").append(String.valueOf(text)).append("-->")
        }
      }
      markdown.line()
    }
  }

  override def renderDocument(context: NodeFormatterContext, markdown: MarkdownWriter, document: Document, phase: FormattingPhase): Unit = {
    super.renderDocument(context, markdown, document, phase)

    attributeUniquificationIdMap = Nullable(Formatter.ATTRIBUTE_UNIQUIFICATION_ID_MAP.get(Nullable(context.getTranslationStore)))

    if (phase == FormattingPhase.DOCUMENT_BOTTOM) {
      if (formatterOptions.appendTransferredReferences) {
        // we will transfer all references which were not part of our document
        val keys = new java.util.ArrayList[DataKeyBase[?]]()

        val allKeys = document.getAll.keySet().iterator()
        while (allKeys.hasNext) {
          val key = allKeys.next()
          if (key.get(Nullable(document)).isInstanceOf[NodeRepository[?]]) {
            keys.add(key)
          }
        }

        keys.sort(java.util.Comparator.comparing[DataKeyBase[?], String](_.name))

        var firstAppend = true

        val keyIter = keys.iterator()
        while (keyIter.hasNext) {
          val key = keyIter.next()
          if (key.get(Nullable(document)).isInstanceOf[NodeRepository[?]]) {
            val repository = key.get(Nullable(document: DataHolder)).asInstanceOf[NodeRepository[?]]
            val nodes = repository.getReferencedElements(document)

            val nodeIter = nodes.iterator()
            while (nodeIter.hasNext) {
              val value = nodeIter.next()
              value match {
                case valueNode: Node =>
                  // NOTE: here the node.getDocument() is necessary to test if this was appended reference
                  if (valueNode.document ne document) {
                    // need to add this one
                    if (firstAppend) {
                      firstAppend = false
                      markdown.blankLine()
                    }
                    context.render(valueNode)
                  }
                case _ => ()
              }
            }
          }
        }
      }
    }
  }

  private def renderGenericNode(node: Node, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    val chars = node.chars
    if (node.isInstanceOf[Block]) {
      val contentChars = node.asInstanceOf[Block].contentChars
      if (chars.isNotNull) {
        val prefix = chars.prefixOf(contentChars)
        if (!prefix.isEmpty) {
          markdown.append(prefix)
        }
      }
      context.renderChildren(node)
      if (chars.isNotNull) {
        val suffix = chars.suffixOf(contentChars)
        if (!suffix.isEmpty) {
          markdown.append(suffix)
        }
      }
    } else {
      if (formatterOptions.keepSoftLineBreaks) {
        markdown.append(chars)
      } else {
        markdown.append(FormatterUtils.stripSoftLineBreak(chars, " "))
      }
    }
  }

  private def renderBlankLine(node: BlankLine, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    if (FormatterUtils.LIST_ITEM_SPACING.get(Nullable(context.getDocument)) == null && markdown.offsetWithPending() > 0) { // @nowarn - NullableDataKey may return null
      if (!(node.previous.isEmpty || node.previous.exists(_.isInstanceOf[BlankLine]))) {
        blankLines = 0
      }
      blankLines += 1
      if (blankLines <= formatterOptions.maxBlankLines) markdown.blankLine(blankLines)
    }
  }

  private def renderDocument(node: Document, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    // No rendering itself
    myTranslationStore = Nullable(context.getTranslationStore)
    context.renderChildren(node)
  }

  private def renderHeading(node: Heading, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.blankLine()
    val headingPreference = formatterOptions.headingStyle
    if (context.isTransformingText || headingPreference.isNoChange(node.isSetextHeading, node.level)) {
      if (node.isAtxHeading) {
        markdown.append(node.openingMarker)
        val spaceAfterAtx = formatterOptions.spaceAfterAtxMarker == DiscretionaryText.ADD ||
          (formatterOptions.spaceAfterAtxMarker == DiscretionaryText.AS_IS && node.openingMarker.endOffset < node.text.startOffset)

        if (spaceAfterAtx) markdown.append(' ')

        context.translatingRefTargetSpan(Nullable(node), (context12, writer) => context12.renderChildren(node))

        formatterOptions.atxHeadingTrailingMarker match {
          case EqualizeTrailingMarker.EQUALIZE =>
            if (node.closingMarker.isNotNull) {
              // fall through to ADD
              if (spaceAfterAtx) markdown.append(' ')
              markdown.append(node.openingMarker)
            }
          case EqualizeTrailingMarker.ADD =>
            if (spaceAfterAtx) markdown.append(' ')
            markdown.append(node.openingMarker)

          case EqualizeTrailingMarker.REMOVE => ()

          case EqualizeTrailingMarker.AS_IS | _ =>
            if (node.closingMarker.isNotNull) {
              if (spaceAfterAtx) markdown.append(' ')
              markdown.append(node.closingMarker)
            }
        }

        // add uniquification id attribute if needed
        val generator = context.getIdGenerator
        generator.foreach { gen =>
          context.addExplicitId(node, gen.getId(node), context, markdown)
        }
      } else {
        context.translatingRefTargetSpan(Nullable(node), (context1, writer) => context1.renderChildren(node))

        // add uniquification id attribute if needed
        val generator = context.getIdGenerator
        generator.foreach { gen =>
          context.addExplicitId(node, gen.getId(node), context, markdown)
        }

        markdown.line()

        if (formatterOptions.setextHeadingEqualizeMarker) {
          markdown.append(node.closingMarker.charAt(0), Utils.minLimit(markdown.getLineInfo(markdown.getLineCountWithPending - 1).textLength, formatterOptions.minSetextMarkerLength))
        } else {
          markdown.append(node.closingMarker)
        }
      }
    } else if (headingPreference.isSetext) {
      // change to setext
      context.renderChildren(node)
      markdown.line()
      val closingMarker = if (node.level == 1) '=' else '-'

      if (formatterOptions.setextHeadingEqualizeMarker) {
        markdown.append(closingMarker, Utils.minLimit(markdown.getLineInfo(markdown.getLineCountWithPending - 1).textLength, formatterOptions.minSetextMarkerLength))
      } else {
        markdown.append(RepeatedSequence.repeatOf(closingMarker, formatterOptions.minSetextMarkerLength))
      }
    } else {
      // change to atx
      val openingMarker = RepeatedSequence.repeatOf('#', node.level)
      markdown.append(openingMarker)

      val spaceAfterAtx = formatterOptions.spaceAfterAtxMarker == DiscretionaryText.ADD ||
        (formatterOptions.spaceAfterAtxMarker == DiscretionaryText.AS_IS && !Parser.HEADING_NO_ATX_SPACE.get(Nullable(context.getOptions)))

      if (spaceAfterAtx) markdown.append(' ')

      context.renderChildren(node)

      formatterOptions.atxHeadingTrailingMarker match {
        case EqualizeTrailingMarker.EQUALIZE | EqualizeTrailingMarker.ADD =>
          if (spaceAfterAtx) markdown.append(' ')
          markdown.append(openingMarker)

        case EqualizeTrailingMarker.AS_IS | EqualizeTrailingMarker.REMOVE | _ => ()
      }
    }

    markdown.tailBlankLine()
  }

  private def renderBlockQuote(node: BlockQuote, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    FormatterUtils.renderBlockQuoteLike(node, context, markdown)

  private def renderThematicBreak(node: ThematicBreak, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.blankLine()
    if (formatterOptions.thematicBreak.isDefined) {
      markdown.append(formatterOptions.thematicBreak.get)
    } else {
      markdown.append(node.chars)
    }
    markdown.tailBlankLine()
  }

  private def renderFencedCodeBlock(node: FencedCodeBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.blankLine()

    var openingMarker: CharSequence = node.openingMarker
    var closingMarker: CharSequence = node.closingMarker
    var openingMarkerChar = openingMarker.charAt(0)
    var closingMarkerChar = if (closingMarker.length > 0) closingMarker.charAt(0) else SequenceUtils.NUL
    var openingMarkerLen = openingMarker.length
    var closingMarkerLen = closingMarker.length

    formatterOptions.fencedCodeMarkerType match {
      case CodeFenceMarker.ANY => ()
      case CodeFenceMarker.BACK_TICK =>
        openingMarkerChar = '`'
        closingMarkerChar = openingMarkerChar
      case CodeFenceMarker.TILDE =>
        openingMarkerChar = '~'
        closingMarkerChar = openingMarkerChar
    }

    if (openingMarkerLen < formatterOptions.fencedCodeMarkerLength) openingMarkerLen = formatterOptions.fencedCodeMarkerLength
    if (closingMarkerLen < formatterOptions.fencedCodeMarkerLength) closingMarkerLen = formatterOptions.fencedCodeMarkerLength

    openingMarker = RepeatedSequence.repeatOf(String.valueOf(openingMarkerChar), openingMarkerLen)
    if (formatterOptions.fencedCodeMatchClosingMarker || closingMarkerChar == SequenceUtils.NUL) {
      closingMarker = openingMarker
    } else {
      closingMarker = RepeatedSequence.repeatOf(String.valueOf(closingMarkerChar), closingMarkerLen)
    }

    markdown.append(openingMarker)
    if (formatterOptions.fencedCodeSpaceBeforeInfo) markdown.append(' ')
    markdown.appendNonTranslating(node.info)
    markdown.line()

    markdown.openPreFormatted(true)
    if (context.isTransformingText) {
      markdown.appendNonTranslating(node.contentChars)
    } else {
      if (formatterOptions.fencedCodeMinimizeIndent) {
        val lines = node.contentLines
        val leadColumns = new Array[Int](lines.size)
        var minSpaces = Int.MaxValue
        var i = 0
        val lineIter = lines.iterator()
        while (lineIter.hasNext) {
          val line = lineIter.next()
          leadColumns(i) = line.countLeadingColumns(0, CharPredicate.SPACE_TAB)
          minSpaces = Utils.min(minSpaces, leadColumns(i))
          i += 1
        }
        if (minSpaces > 0) {
          i = 0
          val lineIter2 = lines.iterator()
          while (lineIter2.hasNext) {
            val line = lineIter2.next()
            if (leadColumns(i) > minSpaces) markdown.append(' ', leadColumns(i) - minSpaces)
            markdown.append(line.trimStart())
            i += 1
          }
        } else {
          markdown.append(node.contentChars)
        }
      } else {
        markdown.append(node.contentChars)
      }
    }
    markdown.closePreFormatted()
    markdown.line().append(closingMarker).line()

    if (!(node.parent.get.isInstanceOf[ListItem]) || !FormatterUtils.isLastOfItem(Nullable(node)) || FormatterUtils.LIST_ITEM_SPACING.get(Nullable(context.getDocument)) == ListSpacing.LOOSE) {
      markdown.tailBlankLine()
    }
  }

  private def renderIndentedCodeBlock(node: IndentedCodeBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.blankLine()

    if (context.isTransformingText) {
      // here we need actual prefix in the code or the generated partial doc will not be accurate and cause translated AST to be wrong
      var contentChars = node.contentChars
      val prefix = FormatterUtils.getActualAdditionalPrefix(contentChars, markdown)

      // need to always have EOL at the end and make sure no leading spaces on id at translated collection
      if (context.getRenderPurpose == RenderPurpose.TRANSLATED) {
        contentChars = contentChars.trimStart()
      }

      markdown.pushPrefix().addPrefix(prefix)
      markdown.openPreFormatted(true)
      markdown.appendNonTranslating(Utils.suffixWith(contentChars.toString, '\n'))
    } else {
      var prefix = RepeatedSequence.repeatOf(" ", listOptions.getCodeIndent).toString

      if (formatterOptions.emulationProfile == ParserEmulationProfile.GITHUB_DOC) {
        if (node.parent.get.isInstanceOf[ListItem]) {
          val marker = node.parent.get.asInstanceOf[ListItem].openingMarker
          prefix = RepeatedSequence.repeatOf(" ", Utils.minLimit(8 - marker.length - 1, 4)).toString
        }
      }

      markdown.pushPrefix().addPrefix(prefix)
      markdown.openPreFormatted(true)

      if (formatterOptions.indentedCodeMinimizeIndent) {
        val lines = node.contentLines
        val leadColumns = new Array[Int](lines.size)
        var minSpaces = Int.MaxValue
        var i = 0
        val lineIter = lines.iterator()
        while (lineIter.hasNext) {
          val line = lineIter.next()
          leadColumns(i) = line.countLeadingColumns(0, CharPredicate.SPACE_TAB)
          minSpaces = Utils.min(minSpaces, leadColumns(i))
          i += 1
        }
        if (minSpaces > 0) {
          i = 0
          val lineIter2 = lines.iterator()
          while (lineIter2.hasNext) {
            val line = lineIter2.next()
            if (leadColumns(i) > minSpaces) markdown.append(' ', leadColumns(i) - minSpaces)
            markdown.append(line.trimStart())
            i += 1
          }
        } else {
          markdown.append(node.contentChars)
        }
      } else {
        markdown.append(node.contentChars)
      }
    }

    markdown.closePreFormatted()
    markdown.popPrefix(true)
    markdown.tailBlankLine()
  }

  private def renderEmphasis(node: Emphasis, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.append(node.openingMarker)
    context.renderChildren(node)
    markdown.append(node.openingMarker)
  }

  private def renderStrongEmphasis(node: StrongEmphasis, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.append(node.openingMarker)
    context.renderChildren(node)
    markdown.append(node.openingMarker)
  }

  private def renderParagraph(node: Paragraph, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    if (context.isTransformingText) {
      // leave all as is
      FormatterUtils.renderTextBlockParagraphLines(node, context, markdown)
      if (node.trailingBlankLine) {
        markdown.tailBlankLine()
      }
    } else {
      if (!node.parent.get.isInstanceOf[ParagraphItemContainer]) {
        if (node.parent.get.isInstanceOf[ParagraphContainer]) {
          val startWrappingDisabled = node.parent.get.asInstanceOf[ParagraphContainer].isParagraphStartWrappingDisabled(node)
          val endWrappingDisabled = node.parent.get.asInstanceOf[ParagraphContainer].isParagraphEndWrappingDisabled(node)
          if (startWrappingDisabled || endWrappingDisabled) {
            if (!startWrappingDisabled) markdown.blankLine()
            FormatterUtils.renderTextBlockParagraphLines(node, context, markdown)
            if (!endWrappingDisabled) markdown.tailBlankLine()
          } else {
            FormatterUtils.renderLooseParagraph(node, context, markdown)
          }
        } else {
          if (!node.trailingBlankLine && (node.next.isEmpty || node.next.isInstanceOf[ListBlock])) { // @nowarn - Java interop
            FormatterUtils.renderTextBlockParagraphLines(node, context, markdown)
          } else {
            FormatterUtils.renderLooseParagraph(node, context, markdown)
          }
        }
      } else {
        val isItemParagraph = node.parent.get.asInstanceOf[ParagraphItemContainer].isItemParagraph(node)
        if (isItemParagraph) {
          if (formatterOptions.blankLinesInAst) {
            FormatterUtils.renderLooseItemParagraph(node, context, markdown)
          } else {
            val itemSpacing = FormatterUtils.LIST_ITEM_SPACING.get(Nullable(context.getDocument))
            if (itemSpacing == ListSpacing.TIGHT) {
              FormatterUtils.renderTextBlockParagraphLines(node, context, markdown)
            } else if (itemSpacing == ListSpacing.LOOSE) {
              if (node.parent.get.nextAnyNot(classOf[BlankLine]).isEmpty) { // @nowarn - Java interop
                FormatterUtils.renderTextBlockParagraphLines(node, context, markdown)
              } else {
                FormatterUtils.renderLooseItemParagraph(node, context, markdown)
              }
            } else {
              if (!node.parent.get.asInstanceOf[ParagraphItemContainer].isParagraphWrappingDisabled(node, listOptions, context.getOptions)) {
                FormatterUtils.renderLooseItemParagraph(node, context, markdown)
              } else {
                FormatterUtils.renderTextBlockParagraphLines(node, context, markdown)
              }
            }
          }
        } else {
          FormatterUtils.renderLooseParagraph(node, context, markdown)
        }
      }
    }
  }

  private def renderSoftLineBreak(node: SoftLineBreak, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    if (formatterOptions.keepSoftLineBreaks || formatterOptions.rightMargin > 0) {
      markdown.append(node.chars)
    } else if (!markdown.isPendingSpace) {
      // need to add a space
      markdown.append(' ')
    }
  }

  private def renderHardLineBreak(node: HardLineBreak, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    if (formatterOptions.keepHardLineBreaks) {
      markdown.append(node.chars)
    } else if (!markdown.isPendingSpace) {
      // need to add a space
      markdown.append(' ')
    }
  }

  private val htmlEntityPlaceholderGenerator: TranslationPlaceholderGenerator =
    (index: Int) => s"&#$index;"

  private def renderHtmlEntity(node: HtmlEntity, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    if (context.getRenderPurpose == RenderPurpose.FORMAT) {
      markdown.append(node.chars)
    } else {
      context.customPlaceholderFormat(htmlEntityPlaceholderGenerator, (context1, markdown1) => markdown1.appendNonTranslating(node.chars))
    }
  }

  private def renderText(node: Text, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    if (formatterOptions.keepSoftLineBreaks) {
      markdown.append(node.chars)
    } else {
      markdown.append(FormatterUtils.stripSoftLineBreak(node.chars, " "))
    }
  }

  private def renderCode(node: Code, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.append(node.openingMarker)
    if (context.isTransformingText || formatterOptions.rightMargin == 0) {
      if (formatterOptions.keepSoftLineBreaks) {
        markdown.appendNonTranslating(node.text)
      } else {
        markdown.appendNonTranslating(FormatterUtils.stripSoftLineBreak(node.text, " "))
      }
    } else {
      // wrapping text
      if (formatterOptions.keepSoftLineBreaks) {
        markdown.append(node.text)
      } else {
        markdown.append(FormatterUtils.stripSoftLineBreak(node.text, " "))
      }
    }
    markdown.append(node.closingMarker)
  }

  private def renderHtmlBlock(node: HtmlBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    if (node.hasChildren) {
      // inner blocks handle rendering
      context.renderChildren(node)
    } else {
      markdown.blankLine()
      renderHtmlBlockBase(node, context, markdown)
      markdown.tailBlankLine()
    }
  }

  private def renderHtmlCommentBlock(node: HtmlCommentBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    // here we need to make it translating, it is a comment
    val text = node.chars.trim().midSequence(4, -3)
    val trimmedEOL = BasedSequence.EOL

    if (!context.isTransformingText && formatterOptions.linkMarkerCommentPattern.isDefined && formatterOptions.linkMarkerCommentPattern.get.matcher(text).matches()) {
      // if after ref then output nothing, the ref takes care of this
      if (!node.previous.isInstanceOf[Reference]) {
        markdown.append("<!--").append(String.valueOf(text.toMapped(SpaceMapper.toNonBreakSpace))).append("-->")
      }
    } else {
      markdown.appendTranslating("<!--", text, "-->", trimmedEOL)
    }
  }

  private def renderHtmlBlockBase(node: HtmlBlockBase, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    context.getRenderPurpose match {
      case RenderPurpose.TRANSLATION_SPANS | RenderPurpose.TRANSLATED_SPANS =>
        markdown.appendNonTranslating(myHtmlBlockPrefix, node.chars.trimEOL(), ">", node.chars.trimmedEOL())

      case RenderPurpose.TRANSLATED =>
        markdown.openPreFormatted(true)
        markdown.appendNonTranslating(node.chars)
        markdown.closePreFormatted()

      case RenderPurpose.FORMAT | _ =>
        // NOTE: to allow changing node chars before formatting, need to make sure the node's chars were not changed before using content lines
        markdown.openPreFormatted(true)
        val spanningChars = node.spanningChars
        if (spanningChars.equals(node.chars)) {
          val lineIter = node.contentLines.iterator()
          while (lineIter.hasNext) {
            markdown.append(lineIter.next())
          }
        } else {
          markdown.append(node.chars)
        }
        markdown.line().closePreFormatted()
    }
  }

  private def renderHtmlInnerBlock(node: HtmlInnerBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    renderHtmlBlockBase(node, context, markdown)

  private def renderHtmlInnerBlockComment(node: HtmlInnerBlockComment, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    // here we need to make it translating, it is a comment
    val text = node.chars.trim().midSequence(4, -3)
    if (!context.isTransformingText && formatterOptions.linkMarkerCommentPattern.isDefined && formatterOptions.linkMarkerCommentPattern.get.matcher(text).matches()) {
      // if after ref then output nothing, the ref takes care of this
      if (!node.previous.isInstanceOf[Reference]) {
        markdown.append("<!--").append(String.valueOf(text.toMapped(SpaceMapper.toNonBreakSpace))).append("-->")
      }
    } else {
      markdown.appendTranslating("<!--", text, "-->")
    }
  }

  private def renderHtmlInline(node: HtmlInline, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    context.getRenderPurpose match {
      case RenderPurpose.TRANSLATED_SPANS | RenderPurpose.TRANSLATION_SPANS =>
        val prefix = if (node.chars.startsWith("</")) "</" else "<"
        markdown.appendNonTranslating(prefix + myHtmlInlinePrefix, node.chars, ">")

      case RenderPurpose.TRANSLATED =>
        markdown.appendNonTranslating(node.chars)

      case RenderPurpose.FORMAT | _ =>
        markdown.append(node.chars)
    }
  }

  private def renderHtmlInlineComment(node: HtmlInlineComment, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    val text = node.chars.trim().midSequence(4, -3)
    if (!context.isTransformingText && formatterOptions.linkMarkerCommentPattern.isDefined && formatterOptions.linkMarkerCommentPattern.get.matcher(text).matches()) {
      markdown.append("<!--").append(String.valueOf(text.toMapped(SpaceMapper.toNonBreakSpace))).append("-->")
    } else {
      markdown.appendTranslating("<!--", text, "-->")
    }
  }

  private def renderAutoLink(node: AutoLink, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    renderAutoLinkNode(node, context, markdown, myTranslationAutolinkPrefix, Nullable.empty)

  private def renderMailLink(node: MailLink, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    renderAutoLinkNode(node, context, markdown, myTranslationAutolinkPrefix, Nullable.empty)

  private def renderAutoLinkNode(node: DelimitedLinkNode, context: NodeFormatterContext, markdown: MarkdownWriter, prefix: String, suffix: Nullable[String]): Unit = {
    if (context.isTransformingText) {
      context.getRenderPurpose match {
        case RenderPurpose.TRANSLATION_SPANS =>
          if (node.openingMarker.isNull) {
            // unwrapped, need to store that fact
            myTranslationStore.foreach(_.set(CoreNodeFormatter.UNWRAPPED_AUTO_LINKS, true))

            context.postProcessNonTranslating(
              (s: String) => {
                CoreNodeFormatter.UNWRAPPED_AUTO_LINKS_MAP.get(Nullable(myTranslationStore.get)).add(s)
                s: CharSequence
              },
              new Runnable {
                def run(): Unit = {
                  markdown.append("<")
                  markdown.appendNonTranslating(prefix, node.text, suffix.getOrElse(null): String) // @nowarn - Java interop
                  markdown.append(">")
                }
              }
            )
          } else {
            markdown.append("<")
            markdown.appendNonTranslating(prefix, node.text, suffix.getOrElse(null): String) // @nowarn - Java interop
            markdown.append(">")
          }

        case RenderPurpose.TRANSLATED_SPANS =>
          markdown.append("<")
          markdown.appendNonTranslating(prefix, node.text, suffix.getOrElse(null): String) // @nowarn - Java interop
          markdown.append(">")

        case RenderPurpose.TRANSLATED =>
          // NOTE: on entry the node text is a placeholder, so we can check if it is wrapped or unwrapped
          if (CoreNodeFormatter.UNWRAPPED_AUTO_LINKS.get(Nullable(myTranslationStore.get)) && CoreNodeFormatter.UNWRAPPED_AUTO_LINKS_MAP.get(Nullable(myTranslationStore.get)).contains(node.text.toString)) {
            markdown.appendNonTranslating(prefix, node.text, suffix.getOrElse(null): String) // @nowarn - Java interop
          } else {
            markdown.append("<")
            markdown.appendNonTranslating(prefix, node.text, suffix.getOrElse(null): String) // @nowarn - Java interop
            markdown.append(">")
          }

        case RenderPurpose.FORMAT | _ =>
          markdown.append(node.chars)
      }
    } else {
      markdown.append(node.chars)
    }
  }

  private def renderImage(node: Image, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    if (!context.isTransformingText && formatterOptions.rightMargin > 0 && formatterOptions.keepImageLinksAtStart) {
      markdown.append(SequenceUtils.LS)
    } else {
      markdown.lineIf(formatterOptions.keepImageLinksAtStart)
    }

    if (!formatterOptions.optimizedInlineRendering || context.isTransformingText) {
      markdown.append(node.textOpeningMarker)
      if (!context.isTransformingText || node.firstChildAny(classOf[HtmlInline]).isDefined) { // @nowarn - Java interop
        if (formatterOptions.rightMargin > 0) {
          // no wrapping of link text
          markdown.append(node.text.toMapped(SpaceMapper.toNonBreakSpace))
        } else {
          context.renderChildren(node)
        }
      } else {
        markdown.appendTranslating(node.text)
      }
      markdown.append(node.textClosingMarker)

      markdown.append(node.linkOpeningMarker)

      markdown.append(node.urlOpeningMarker)

      if (context.getRenderPurpose == RenderPurpose.TRANSLATION_SPANS) {
        val resolvedLink = context.resolveLink(LinkType.LINK, node.url, false)
        markdown.appendNonTranslating(resolvedLink.getPageRef)
      } else {
        markdown.append(node.urlOpeningMarker)
        markdown.appendNonTranslating(node.pageRef)
      }

      markdown.append(node.urlClosingMarker)

      if (!node.urlContent.isEmpty) {
        markdown.openPreFormatted(true)
        markdown.pushOptions().preserveSpaces()

        if (!context.isTransformingText && formatterOptions.rightMargin > 0) {
          val chars = node.urlContent
          val iMax = chars.length
          var hadEOL = true
          markdown.append('\n')

          for (i <- 0 until iMax) {
            val c = chars.charAt(i)
            c match {
              case '\n' | '\r' =>
                hadEOL = true
                markdown.append(chars.subSequence(i, i + 1))
              case ' ' =>
                if (hadEOL) {
                  markdown.append(SequenceUtils.LS)
                  hadEOL = false
                }
                markdown.append(SequenceUtils.NBSP)
              case _ =>
                if (hadEOL) {
                  markdown.append(SequenceUtils.LS)
                  hadEOL = false
                }
                markdown.append(chars.subSequence(i, i + 1))
            }
          }
        } else {
          markdown.append(node.urlContent)
        }
        markdown.popOptions()
        markdown.closePreFormatted()
        // title and closing must be first on the line
        markdown.append(SequenceUtils.LS)
      }

      if (node.titleOpeningMarker.isNotNull) {
        markdown.append(' ')
        markdown.append(node.titleOpeningMarker)
        if (node.title.isNotNull) markdown.appendTranslating(node.title)
        markdown.append(node.titleClosingMarker)
      }

      markdown.append(node.linkClosingMarker)
    } else {
      markdown.append(node.chars)
    }
  }

  private def renderLink(node: Link, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    if (!context.isTransformingText && formatterOptions.rightMargin > 0 && formatterOptions.keepExplicitLinksAtStart) {
      markdown.append(SequenceUtils.LS)
    } else {
      markdown.lineIf(formatterOptions.keepExplicitLinksAtStart)
    }

    if (!formatterOptions.optimizedInlineRendering || context.isTransformingText) {
      markdown.append(node.textOpeningMarker)
      if (!context.isTransformingText || node.firstChildAny(classOf[HtmlInline]).isDefined) { // @nowarn - Java interop
        if (formatterOptions.rightMargin > 0) {
          // no wrapping of link text
          markdown.append(node.text.toMapped(SpaceMapper.toNonBreakSpace))
        } else {
          context.renderChildren(node)
        }
      } else {
        markdown.appendTranslating(node.text)
      }
      markdown.append(node.textClosingMarker)

      markdown.append(node.linkOpeningMarker)
      markdown.append(node.urlOpeningMarker)

      if (context.getRenderPurpose == RenderPurpose.TRANSLATION_SPANS) {
        val resolvedLink = context.resolveLink(LinkType.LINK, node.url, false)
        markdown.appendNonTranslating(resolvedLink.getPageRef)

        if (resolvedLink.getAnchorRef.isDefined) { // @nowarn - Java interop
          markdown.append("#")
          val anchorRef = context.transformAnchorRef(resolvedLink.getPageRef, resolvedLink.getAnchorRef.get)
          markdown.append(anchorRef)
        }
      } else {
        val pageRef = context.transformNonTranslating(Nullable.empty, node.pageRef, Nullable.empty, Nullable.empty)
        markdown.append(pageRef)

        markdown.append(node.anchorMarker)

        if (node.anchorRef.isNotNull) {
          val anchorRef = context.transformAnchorRef(node.pageRef, node.anchorRef)
          if (attributeUniquificationIdMap.isDefined && context.getRenderPurpose == RenderPurpose.TRANSLATED && context.getMergeContext.isDefined) {
            var uniquifiedAnchorRef = String.valueOf(anchorRef)
            if (pageRef.length == 0) {
              uniquifiedAnchorRef = attributeUniquificationIdMap.get.getOrDefault(uniquifiedAnchorRef, uniquifiedAnchorRef)
            }
            markdown.append(uniquifiedAnchorRef)
          } else {
            markdown.append(anchorRef)
          }
        }
      }

      markdown.append(node.urlClosingMarker)

      if (node.titleOpeningMarker.isNotNull) {
        markdown.append(' ')
        markdown.append(node.titleOpeningMarker)
        if (node.title.isNotNull) markdown.appendTranslating(node.title)
        markdown.append(node.titleClosingMarker)
      }

      markdown.append(node.linkClosingMarker)
    } else {
      markdown.append(node.chars)
    }
  }

  private def renderImageRef(node: ImageRef, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    if (!formatterOptions.optimizedInlineRendering || context.isTransformingText) {
      if (context.isTransformingText || formatterOptions.rightMargin == 0) {
        if (node.isReferenceTextCombined) {
          markdown.append(node.referenceOpeningMarker)
          markdown.appendTranslating(node.reference)
          markdown.append(node.referenceClosingMarker)

          markdown.append(node.textOpeningMarker)
          markdown.append(node.textClosingMarker)
        } else {
          markdown.append(node.textOpeningMarker)
          appendReference(node.text, context, markdown)
          markdown.append(node.textClosingMarker)

          markdown.append(node.referenceOpeningMarker)
          markdown.appendTranslating(node.reference)
          markdown.append(node.referenceClosingMarker)
        }
      } else {
        if (node.isReferenceTextCombined) {
          markdown.append(node.referenceOpeningMarker)
          if (node.isOrDescendantOfType(classOf[Paragraph])) {
            markdown.append(node.reference.toMapped(SpaceMapper.toNonBreakSpace))
          } else {
            markdown.append(node.reference)
          }
          markdown.append(node.referenceClosingMarker)

          markdown.append(node.textOpeningMarker)
          markdown.append(node.textClosingMarker)
        } else {
          markdown.append(node.textOpeningMarker)
          context.renderChildren(node)
          markdown.append(node.textClosingMarker)

          markdown.append(node.referenceOpeningMarker)
          markdown.append(node.reference)
          markdown.append(node.referenceClosingMarker)
        }
      }
    } else {
      markdown.append(node.chars)
    }
  }

  private def renderLinkRef(node: LinkRef, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    if (!formatterOptions.optimizedInlineRendering || context.isTransformingText) {
      if (context.isTransformingText || formatterOptions.rightMargin == 0) {
        if (node.isReferenceTextCombined) {
          markdown.append(node.referenceOpeningMarker)
          FormatterUtils.appendWhiteSpaceBetween(markdown, node.referenceOpeningMarker, node.reference, true, false, false)
          appendReference(node.reference, context, markdown)
          markdown.append(node.referenceClosingMarker)

          markdown.append(node.textOpeningMarker)
          markdown.append(node.textClosingMarker)
        } else {
          markdown.append(node.textOpeningMarker)
          if (!context.isTransformingText || node.firstChildAny(classOf[HtmlInline]).isDefined) { // @nowarn - Java interop
            context.renderChildren(node)
          } else {
            appendReference(node.text, context, markdown)
          }
          markdown.append(node.textClosingMarker)

          markdown.append(node.referenceOpeningMarker)
          FormatterUtils.appendWhiteSpaceBetween(markdown, node.referenceOpeningMarker, node.reference, true, false, false)
          markdown.appendTranslating(node.reference)
          markdown.append(node.referenceClosingMarker)
        }
      } else {
        if (node.isReferenceTextCombined) {
          markdown.append(node.referenceOpeningMarker)
          if (node.isOrDescendantOfType(classOf[Paragraph])) {
            markdown.append(node.reference.toMapped(SpaceMapper.toNonBreakSpace))
          } else {
            markdown.append(node.reference)
          }
          markdown.append(node.referenceClosingMarker)

          markdown.append(node.textOpeningMarker)
          markdown.append(node.textClosingMarker)
        } else {
          markdown.append(node.textOpeningMarker)
          context.renderChildren(node)
          markdown.append(node.textClosingMarker)

          markdown.append(node.referenceOpeningMarker)
          markdown.append(node.reference)
          markdown.append(node.referenceClosingMarker)
        }
      }
    } else {
      markdown.append(node.chars)
    }
  }
}

object CoreNodeFormatter {

  val UNWRAPPED_AUTO_LINKS: DataKey[Boolean] = new DataKey[Boolean]("UNWRAPPED_AUTO_LINKS", false)
  val UNWRAPPED_AUTO_LINKS_MAP: DataKey[java.util.HashSet[String]] = new DataKey[java.util.HashSet[String]]("UNWRAPPED_AUTO_LINKS_MAP", new ssg.md.util.data.NotNullValueSupplier[java.util.HashSet[String]] { def get: java.util.HashSet[String] = new java.util.HashSet[String]() })

  class Factory extends NodeFormatterFactory {
    override def create(options: DataHolder): NodeFormatter =
      new CoreNodeFormatter(options)
  }
}
