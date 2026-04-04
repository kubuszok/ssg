/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/FormatterUtils.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package formatter

import ssg.md.ast.*
import ssg.md.parser.{ ListOptions, Parser }
import ssg.md.util.ast.{ BlankLine, BlockQuoteLike, Document, Node }
import ssg.md.util.data.{ DataKey, NullableDataKey }
import ssg.md.util.format.MarkdownParagraph
import ssg.md.util.format.options.{ BlockQuoteMarker, ListBulletMarker, ListNumberedMarker, ListSpacing }
import ssg.md.util.misc.{ CharPredicate, Pair, Utils }
import ssg.md.util.sequence.{ BasedSequence, LineAppendable, RepeatedSequence }
import ssg.md.util.sequence.builder.SequenceBuilder
import ssg.md.util.sequence.mappers.SpaceMapper

import java.util.regex.{ Matcher, Pattern }
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

/** Utility methods for formatting markdown nodes.
  */
object FormatterUtils {

  val LIST_ITEM_NUMBER:      DataKey[Int]                            = new DataKey[Int]("LIST_ITEM_NUMBER", 0)
  val FIRST_LIST_ITEM_CHILD: DataKey[Boolean]                        = new DataKey[Boolean]("FIRST_LIST_ITEM_CHILD", false) // Set to true for first block list item child of an empty list item
  val NULL_PADDING:          CharSequence => Pair[Int, Int]          = (_: CharSequence) => Pair.of(0, 0)
  val LIST_ALIGN_NUMERIC:    DataKey[CharSequence => Pair[Int, Int]] =
    new DataKey[CharSequence => Pair[Int, Int]]("LIST_ITEM_NUMBER", NULL_PADDING) // function takes ordered marker and returns Pair LeftPad,RightPad
  val LIST_ITEM_SPACING: NullableDataKey[ListSpacing] = new NullableDataKey[ListSpacing]("LIST_ITEM_SPACING")

  def getBlockLikePrefix(node: BlockQuoteLike, context: NodeFormatterContext, blockQuoteMarkers: BlockQuoteMarker, prefix: BasedSequence): String = {
    val prefixChars = node.openingMarker.toString
    var usePrefix: String = ""
    var compactPrefix = false

    blockQuoteMarkers match {
      case BlockQuoteMarker.AS_IS =>
        if (node.firstChild.isDefined) {
          usePrefix = node.chars.baseSubSequence(node.openingMarker.startOffset, node.firstChild.get.startOffset).toString
        } else {
          usePrefix = prefixChars
        }

      case BlockQuoteMarker.ADD_COMPACT =>
        usePrefix = prefixChars.trim

      case BlockQuoteMarker.ADD_COMPACT_WITH_SPACE =>
        compactPrefix = true
        usePrefix = prefixChars.trim + " "

      case BlockQuoteMarker.ADD_SPACED =>
        usePrefix = prefixChars.trim + " "
    }

    // create a combined prefix, compact if needed
    val quoteLikePrefixPredicate = context.getBlockQuoteLikePrefixPredicate

    var combinedPrefix = prefix.toString
    if (compactPrefix && combinedPrefix.endsWith(" ") && combinedPrefix.length >= 2 && quoteLikePrefixPredicate.test(combinedPrefix.charAt(combinedPrefix.length - 2))) {
      combinedPrefix = combinedPrefix.substring(0, combinedPrefix.length - 1) + usePrefix
    } else {
      combinedPrefix += usePrefix
    }

    combinedPrefix
  }

  def stripSoftLineBreak(chars: CharSequence, spaceChar: CharSequence): CharSequence = {
    var sb: StringBuilder = null // @nowarn - Java regex appendReplacement needs StringBuffer-like usage
    val matcher = Pattern.compile("\\s*(?:\r\n|\r|\n)\\s*").matcher(chars)
    while (matcher.find()) {
      if (sb == null) sb = new StringBuilder()
      // manual replacement since Scala StringBuilder doesn't have appendReplacement
    }
    if (sb != null) {
      // Use Java StringBuffer for matcher compatibility
      val javaSb = new StringBuffer()
      matcher.reset()
      while (matcher.find())
        matcher.appendReplacement(javaSb, Matcher.quoteReplacement(spaceChar.toString))
      matcher.appendTail(javaSb)
      javaSb
    } else {
      chars
    }
  }

  def getActualAdditionalPrefix(contentChars: BasedSequence, markdown: MarkdownWriter): String = {
    val parentPrefix = markdown.getPrefix.length
    val column       = contentChars.baseColumnAtStart()
    RepeatedSequence.repeatOf(" ", Utils.minLimit(0, column - parentPrefix)).toString
  }

  def getAdditionalPrefix(fromChars: BasedSequence, toChars: BasedSequence): String = {
    val parentPrefix = fromChars.startOffset
    val column       = toChars.startOffset
    RepeatedSequence.repeatOf(" ", Utils.minLimit(0, column - parentPrefix)).toString
  }

  def getSoftLineBreakSpan(node: Nullable[Node]): BasedSequence =
    if (node.isEmpty) BasedSequence.NULL
    else {
      var lastNode = node.get
      var nextNode = node.get.next

      while (nextNode.isDefined && !nextNode.get.isInstanceOf[SoftLineBreak]) {
        lastNode = nextNode.get
        nextNode = nextNode.get.next
      }

      Node.spanningChars(lastNode.chars, lastNode.chars)
    }

  def appendWhiteSpaceBetween(
    markdown:      MarkdownWriter,
    prev:          Node,
    next:          Node,
    preserve:      Boolean,
    collapse:      Boolean,
    collapseToEOL: Boolean
  ): Unit =
    if (next != null && prev != null && (preserve || collapse)) { // @nowarn - Java interop: nodes can be null
      appendWhiteSpaceBetween(markdown, prev.chars, next.chars, preserve, collapse, collapseToEOL)
    }

  def appendWhiteSpaceBetween(
    markdown:      MarkdownWriter,
    prev:          BasedSequence,
    next:          BasedSequence,
    preserve:      Boolean,
    collapse:      Boolean,
    collapseToEOL: Boolean
  ): Unit =
    if ((next ne null) && (prev ne null) && (preserve || collapse)) { // @nowarn - Java interop: sequences can be null
      if (prev.endOffset <= next.startOffset) {
        val sequence = prev.baseSubSequence(prev.endOffset, next.startOffset)
        if (!sequence.isEmpty && sequence.isBlank()) {
          if (!preserve) {
            if (collapseToEOL && sequence.indexOfAny(CharPredicate.ANY_EOL) != -1) {
              markdown.append('\n')
            } else {
              markdown.append(' ')
            }
          } else {
            // need to set pre-formatted or spaces after eol are ignored assuming prefixes are used
            val saved = markdown.getOptions
            markdown.setOptions(saved & ~LineAppendable.F_TRIM_LEADING_WHITESPACE)
            markdown.append(sequence)
            markdown.setOptions(saved)
          }
        }
      } else {
        // nodes reversed due to children being rendered before the parent
      }
    }

  def renderList(node: ListBlock, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    if (context.isTransformingText) {
      // CAUTION: during translation no formatting should be done
      context.renderChildren(node)
    } else {
      val itemList = new java.util.ArrayList[Node]()
      var item     = node.firstChild
      while (item.isDefined) {
        itemList.add(item.get)
        item = item.get.next
      }
      renderList(node, context, markdown, itemList)
    }

  def renderList(node: ListBlock, context: NodeFormatterContext, markdown: MarkdownWriter, itemList: java.util.List[Node]): Unit = {
    val formatterOptions = context.getFormatterOptions
    if (formatterOptions.listAddBlankLineBefore && !node.isOrDescendantOfType(classOf[ListItem])) {
      markdown.blankLine()
    }

    val document       = context.getDocument
    val listSpacing    = LIST_ITEM_SPACING.get(Nullable(document))
    val listItemNumber = LIST_ITEM_NUMBER.get(Nullable(document))
    val startingNumber = node match {
      case ol: OrderedList =>
        if (formatterOptions.listRenumberItems && formatterOptions.listResetFirstItemNumber) 1
        else ol.startNumber
      case _ => 1
    }
    val listAlignNumeric = LIST_ALIGN_NUMERIC.get(Nullable(document))
    document.set(LIST_ITEM_NUMBER, startingNumber)

    var itemSpacing: Nullable[ListSpacing] = Nullable.empty
    formatterOptions.listSpacing match {
      case ListSpacing.AS_IS   => ()
      case ListSpacing.LOOSE   => itemSpacing = Nullable(ListSpacing.LOOSE)
      case ListSpacing.TIGHT   => itemSpacing = Nullable(ListSpacing.TIGHT)
      case ListSpacing.LOOSEN  => itemSpacing = Nullable(if (hasLooseItems(itemList)) ListSpacing.LOOSE else ListSpacing.TIGHT)
      case ListSpacing.TIGHTEN => itemSpacing = Nullable(if (hasLooseItems(itemList)) ListSpacing.AS_IS else ListSpacing.TIGHT)
    }

    document.remove(LIST_ALIGN_NUMERIC)

    if (!formatterOptions.listAlignNumeric.isNoChange && node.isInstanceOf[OrderedList]) {
      var maxLen = Int.MinValue
      var minLen = Int.MaxValue
      var i      = startingNumber
      val iter   = itemList.iterator()
      while (iter.hasNext) {
        val item = iter.next()
        if (!formatterOptions.listRemoveEmptyItems || (item.hasChildren && item.firstChildAnyNot(classOf[BlankLine]).isDefined)) { // @nowarn - Java interop: may return null
          val length = if (formatterOptions.listRenumberItems) Integer.toString(i).length + 1 else item.asInstanceOf[ListItem].openingMarker.length
          maxLen = Math.max(maxLen, length)
          minLen = Math.min(minLen, length)
          i += 1
        }
      }

      if (maxLen != minLen) {
        val finalMaxLen = maxLen
        document.set(
          LIST_ALIGN_NUMERIC,
          if (formatterOptions.listAlignNumeric.isLeft)
            (sequence: CharSequence) => Pair.of(0, Math.min(4, Math.max(0, finalMaxLen - sequence.length)))
          else
            (sequence: CharSequence) => Pair.of(Math.min(4, Math.max(0, finalMaxLen - sequence.length)), 0)
        )
      }
    }

    document.set(
      LIST_ITEM_SPACING,
      if (itemSpacing.exists(_ == ListSpacing.LOOSE) && (listSpacing == null || listSpacing == ListSpacing.LOOSE)) ListSpacing.LOOSE else itemSpacing.getOrElse(null)
    ) // @nowarn - NullableDataKey expects nullable value
    val iter = itemList.iterator()
    while (iter.hasNext) {
      val item = iter.next()
      if (itemSpacing.exists(_ == ListSpacing.LOOSE) && (listSpacing == null || listSpacing == ListSpacing.LOOSE)) { // @nowarn - NullableDataKey may return null
        markdown.blankLine()
      }
      context.render(item)
    }
    document.set(LIST_ITEM_SPACING, listSpacing)
    document.set(LIST_ITEM_NUMBER, listItemNumber)
    document.set(LIST_ALIGN_NUMERIC, listAlignNumeric)

    if (!node.isOrDescendantOfType(classOf[ListItem])) {
      markdown.tailBlankLine()
    }
  }

  def renderLooseParagraph(node: Paragraph, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    markdown.blankLine()
    renderLooseItemParagraph(node, context, markdown)
  }

  def isFollowedByBlankLine(node: Nullable[Node]): Boolean = boundary {
    var current = node
    while (current.isDefined) {
      if (current.get.nextAnyNot(classOf[HtmlCommentBlock], classOf[HtmlInnerBlockComment], classOf[HtmlInlineComment]).isInstanceOf[BlankLine]) {
        break(true)
      }
      val nextNonBlank = current.get.nextAnyNot(classOf[BlankLine], classOf[HtmlCommentBlock], classOf[HtmlInnerBlockComment], classOf[HtmlInlineComment])
      if (nextNonBlank.isDefined) {
        break(false)
      }
      current = current.get.parent
    }
    false
  }

  def isNotLastItem(node: Nullable[Node]): Boolean = boundary {
    var current = node
    while (current.isDefined && !current.get.isInstanceOf[Document]) {
      if (current.get.nextAnyNot(classOf[BlankLine], classOf[HtmlCommentBlock], classOf[HtmlInnerBlockComment], classOf[HtmlInlineComment]).isDefined) {
        break(true)
      }
      current = current.get.parent
    }
    false
  }

  def isLastOfItem(node: Nullable[Node]): Boolean =
    node.isDefined && node.get.nextAnyNot(classOf[BlankLine], classOf[HtmlCommentBlock], classOf[HtmlInnerBlockComment], classOf[HtmlInlineComment]).isEmpty

  def renderLooseItemParagraph(node: Paragraph, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    renderTextBlockParagraphLines(node, context, markdown)
    val parent = node.parent.get
    if (parent.isInstanceOf[ListItem]) {
      if (context.getFormatterOptions.blankLinesInAst) {
        var addBlankLine        = false
        val canAddTailBlankLine = !parent.asInstanceOf[ParagraphContainer].isParagraphEndWrappingDisabled(node)

        val listItem = parent.asInstanceOf[ListItem]
        context.getFormatterOptions.listSpacing match {
          case ListSpacing.LOOSEN =>
            addBlankLine = parent.parent.get.isInstanceOf[ListBlock] && parent.parent.get.asInstanceOf[ListBlock].isLoose &&
              hasLooseItems(parent.parent.get.children) &&
              (isFollowedByBlankLine(Nullable(node)) && isNotLastItem(Nullable(parent)) ||
                !listItem.isOwnTight ||
                (listItem.isItemParagraph(node) && parent.firstChild.isDefined && parent.firstChild.get.next.isDefined)) // @nowarn - Java interop

          case ListSpacing.LOOSE =>
            addBlankLine = true

          case ListSpacing.TIGHTEN =>
            addBlankLine = canAddTailBlankLine && (if (listItem.isItemParagraph(node)) isFollowedByBlankLine(Nullable(node)) && isNotLastItem(Nullable(node)) else isNotLastItem(Nullable(node)))

          case ListSpacing.AS_IS =>
            addBlankLine = isFollowedByBlankLine(Nullable(node)) && isNotLastItem(Nullable(parent))

          case ListSpacing.TIGHT | _ =>
            addBlankLine = false
        }

        if (addBlankLine) {
          markdown.tailBlankLine()
        }
      } else {
        if (context.getFormatterOptions.listSpacing != ListSpacing.TIGHTEN || parent.next.isDefined) { // @nowarn - Java interop
          markdown.tailBlankLine()
        }
      }
    } else {
      markdown.tailBlankLine()
    }
  }

  private[formatter] def hasLooseItems(itemList: java.lang.Iterable[Node]): Boolean = boundary {
    val iter = itemList.iterator()
    while (iter.hasNext) {
      val item = iter.next()
      if (item.isInstanceOf[ListItem]) {
        if (!item.asInstanceOf[ListItem].isOwnTight && item.next.isDefined) { // @nowarn - Java interop
          break(true)
        }
      }
    }
    false
  }

  private def hasLooseItems(itemList: java.util.List[Node]): Boolean = boundary {
    val iter = itemList.iterator()
    while (iter.hasNext) {
      val item = iter.next()
      if (item.isInstanceOf[ListItem]) {
        if (!item.asInstanceOf[ListItem].isOwnTight && item.next.isDefined) { // @nowarn - Java interop
          break(true)
        }
      }
    }
    false
  }

  def renderListItem(
    node:                   ListItem,
    context:                NodeFormatterContext,
    markdown:               MarkdownWriter,
    listOptions:            ListOptions,
    markerSuffix:           BasedSequence,
    addBlankLineLooseItems: Boolean
  ): Unit = {
    val options                 = context.getFormatterOptions
    val savedFirstListItemChild = FIRST_LIST_ITEM_CHILD.get(Nullable(context.getDocument))

    if (context.isTransformingText) {
      val openingMarker    = node.openingMarker
      val additionalPrefix = getActualAdditionalPrefix(openingMarker, markdown)

      val (itemContentPrefix, itemContentSpacer) = if (node.firstChild.isEmpty) {
        // empty list item with no children
        val count = openingMarker.length + (if (listOptions.isItemContentAfterSuffix) markerSuffix.length else 0) + 1
        val icp   = RepeatedSequence.repeatOf(' ', count).toString
        (icp, " ")
      } else {
        val childContent = node.firstChild.get.chars
        val icp          = getAdditionalPrefix(if (markerSuffix.isEmpty) openingMarker else markerSuffix, childContent)
        val ics          = getAdditionalPrefix(if (markerSuffix.isEmpty) openingMarker.getEmptySuffix else markerSuffix.getEmptySuffix, childContent)
        (icp, ics)
      }

      val prefix = additionalPrefix + itemContentPrefix
      markdown.pushPrefix().addPrefix(prefix, true)
      markdown.append(additionalPrefix).append(openingMarker)

      if (!markerSuffix.isEmpty) {
        val markerSuffixIndent = getAdditionalPrefix(openingMarker.getEmptySuffix, markerSuffix)
        markdown.append(markerSuffixIndent).append(markerSuffix)
      }

      markdown.append(itemContentSpacer)

      // if have no item text and followed by eol then add EOL
      if (!node.firstChild.exists(_.isInstanceOf[Paragraph])) {
        if (node.firstChild.isEmpty) {
          if (!savedFirstListItemChild) {
            markdown.append("\n")
          }
        } else {
          val posEOL = node.endOfLine(openingMarker.endOffset)
          if (posEOL < node.firstChild.get.startOffset) {
            // output EOL
            markdown.append("\n")
          }
        }
      }

      context.renderChildren(node)
      markdown.popPrefix()
    } else if (options.listRemoveEmptyItems && !(node.hasChildren && node.firstChildAnyNot(classOf[BlankLine]).isDefined)) { // @nowarn - Java interop
      // empty items removed, skip rendering
    } else {
      var useOpeningMarker: CharSequence = node.openingMarker
      if (node.isOrderedItem) {
        var delimiter = useOpeningMarker.charAt(useOpeningMarker.length - 1)
        val number    = useOpeningMarker.subSequence(0, useOpeningMarker.length - 1)

        options.listNumberedMarker match {
          case ListNumberedMarker.ANY   => ()
          case ListNumberedMarker.DOT   => delimiter = '.'
          case ListNumberedMarker.PAREN => delimiter = ')'
          case null                     => throw new IllegalStateException("Missing case for ListNumberedMarker") // @nowarn - defensive null case
        }

        val document = context.getDocument

        if (options.listRenumberItems) {
          var itemNumber: Int = LIST_ITEM_NUMBER.get(Nullable(document))
          useOpeningMarker = s"$itemNumber$delimiter"
          itemNumber += 1
          document.set(LIST_ITEM_NUMBER, itemNumber)
        } else {
          useOpeningMarker = s"$number$delimiter"
        }

        val padding = LIST_ALIGN_NUMERIC.get(Nullable(document)).apply(useOpeningMarker)
        if (padding.first.get > 0) useOpeningMarker = RepeatedSequence.ofSpaces(padding.first.get).toString + useOpeningMarker.toString
        if (padding.second.get > 0) useOpeningMarker = useOpeningMarker.toString + RepeatedSequence.ofSpaces(padding.second.get).toString
      } else {
        if (node.canChangeMarker) {
          options.listBulletMarker match {
            case ListBulletMarker.ANY      => ()
            case ListBulletMarker.DASH     => useOpeningMarker = "-"
            case ListBulletMarker.ASTERISK => useOpeningMarker = "*"
            case ListBulletMarker.PLUS     => useOpeningMarker = "+"
            case null                      => throw new IllegalStateException("Missing case for ListBulletMarker") // @nowarn - defensive null case
          }
        }
      }

      // NOTE: if list item content after suffix is set in the parser, then sub-items are indented after suffix
      //    otherwise only the item's lazy continuation for the paragraph can be indented after suffix, child items are normally indented
      val itemContinuationCount = if (listOptions.isItemContentAfterSuffix || options.listsItemContentAfterSuffix) markerSuffix.length else 0
      val continuationCount     = useOpeningMarker.length + (if (listOptions.isItemContentAfterSuffix) markerSuffix.length else 0) + 1
      val additionalItemPrefix: CharSequence = if (options.itemContentIndent) RepeatedSequence.repeatOf(' ', itemContinuationCount) else ""
      val childPrefix:          CharSequence = if (options.itemContentIndent) RepeatedSequence.repeatOf(' ', continuationCount) else RepeatedSequence.repeatOf(" ", listOptions.getItemIndent).toString

      val openingMarker      = node.openingMarker
      val replacedOpenMarker = {
        val b = openingMarker.getBuilder.asInstanceOf[SequenceBuilder]
        b.append(openingMarker.getEmptyPrefix).append(useOpeningMarker).append(openingMarker.getEmptySuffix).toSequence
      }

      markdown.pushOptions().preserveSpaces().append(replacedOpenMarker).append(' ').append(markerSuffix).popOptions()

      markdown.pushPrefix().addPrefix(childPrefix, true)

      val childNode = node.firstChild
      if (childNode.isDefined && node.firstChildAnyNot(classOf[BlankLine]).isDefined) { // @nowarn - Java interop
        markdown.pushPrefix().addPrefix(additionalItemPrefix, true)
        // NOTE: depends on first child
        FIRST_LIST_ITEM_CHILD.set(context.getDocument, true)
        context.render(childNode.get)
        FIRST_LIST_ITEM_CHILD.set(context.getDocument, false)
        markdown.popPrefix()

        var next = childNode.get.next
        while (next.isDefined) {
          context.render(next.get)
          next = next.get.next
        }

        if (addBlankLineLooseItems && (node.isLoose && context.getFormatterOptions.listSpacing == ListSpacing.LOOSEN || context.getFormatterOptions.listSpacing == ListSpacing.LOOSE)) {
          markdown.tailBlankLine()
        }
      } else {
        // NOTE: empty list item
        if (node.isLoose) {
          markdown.tailBlankLine()
        } else {
          if (!savedFirstListItemChild) {
            markdown.line()
          }
        }
      }
      markdown.popPrefix()
    }

    FIRST_LIST_ITEM_CHILD.set(context.getDocument, savedFirstListItemChild)
  }

  def renderTextBlockParagraphLines(node: Node, context: NodeFormatterContext, markdown: MarkdownWriter): Unit =
    if (context.isTransformingText) {
      context.translatingSpan((context1, writer) => context1.renderChildren(node))
      markdown.line()
    } else {
      val formatterOptions = context.getFormatterOptions
      if (formatterOptions.rightMargin > 0) {
        val subContextOptions  = context.getOptions.toMutable.set(Formatter.KEEP_SOFT_LINE_BREAKS, true).set(Formatter.KEEP_HARD_LINE_BREAKS, true)
        val seqBuilder         = context.getDocument.chars.getBuilder.asInstanceOf[SequenceBuilder]
        val subContext         = context.getSubContext(Nullable(subContextOptions), seqBuilder.getBuilder)
        val subContextMarkdown = subContext.getMarkdown
        subContextMarkdown.removeOptions(LineAppendable.F_TRIM_TRAILING_WHITESPACE)
        subContext.renderChildren(node)

        val nodeLessEol    = node.chars.trimEOL()
        val trailingSpaces = node.chars.trimmedEnd()
        if (trailingSpaces.isNotEmpty() && !subContextMarkdown.endsWithEOL) {
          // add these so our tracked offsets at end of paragraph after whitespaces are not outside the sequence
          subContextMarkdown.append(trailingSpaces)
        }
        subContextMarkdown.line()
        subContextMarkdown.appendToSilently(seqBuilder, 0, -1)

        val paragraphChars    = seqBuilder.toSequence
        val altParagraphChars = seqBuilder.toSequence(context.getTrackedSequence)
        val haveAltSequence   = paragraphChars ne altParagraphChars
        var startOffset: Int = 0
        var endOffset:   Int = 0

        val trackedOffsets = context.getTrackedOffsets
        if (haveAltSequence) {
          // NOTE: this is only needed to find offset in trackedOffsets for the paragraph
          val charsLessEol = altParagraphChars.trimEnd()
          startOffset = charsLessEol.startOffset
          val endOffsetDelta = nodeLessEol.countTrailingWhitespace() - charsLessEol.countTrailingWhitespace()
          endOffset = charsLessEol.endOffset + endOffsetDelta + 1
        } else {
          startOffset = nodeLessEol.startOffset
          endOffset = nodeLessEol.endOffset
        }

        val paragraphTrackedOffsets = trackedOffsets.getTrackedOffsets(startOffset, endOffset)

        val formatter = new MarkdownParagraph(paragraphChars, altParagraphChars, formatterOptions.charWidthProvider)
        formatter.options = Nullable(context.getOptions)

        formatter.width = formatterOptions.rightMargin - markdown.getPrefix.length
        formatter.keepSoftLineBreaks = false
        formatter.keepHardLineBreaks = formatterOptions.keepHardLineBreaks
        formatter.restoreTrackedSpaces = context.isRestoreTrackedSpaces
        formatter.setFirstIndent(BasedSequence.NULL)
        formatter.setIndent(BasedSequence.NULL)

        // adjust first line width, based on change in prefix after the first line EOL
        formatter.firstWidthOffset = -markdown.column() + markdown.getAfterEolPrefixDelta

        if (formatterOptions.applySpecialLeadInHandlers) {
          import scala.jdk.CollectionConverters.*
          formatter.leadInHandlers = Parser.SPECIAL_LEAD_IN_HANDLERS.get(Nullable(context.getDocument)).asJava
        }

        val ptIter = paragraphTrackedOffsets.iterator()
        while (ptIter.hasNext)
          formatter.addTrackedOffset(ptIter.next())

        val wrappedText     = formatter.wrapText().toMapped(SpaceMapper.fromNonBreakSpace)
        val startLine       = markdown.getLineCount
        val firstLineOffset = markdown.column()
        markdown.pushOptions().preserveSpaces().append(wrappedText).line().popOptions()

        if (!paragraphTrackedOffsets.isEmpty) {
          val startLineInfo = markdown.getLineInfo(startLine)

          val ptIter2 = paragraphTrackedOffsets.iterator()
          while (ptIter2.hasNext) {
            val trackedOffset = ptIter2.next()
            if (trackedOffset.isResolved) {
              val offsetIndex = trackedOffset.getIndex
              val lineColumn  = wrappedText.lineColumnAtIndex(offsetIndex)
              val trackedLine = lineColumn.first.get
              val lineInfo    = markdown.getLineInfo(startLine + trackedLine)

              val lengthOffset = startLineInfo.sumLength - startLineInfo.length
              val prefixDelta  = lineInfo.sumPrefixLength - startLineInfo.sumPrefixLength + startLineInfo.prefixLength
              val delta        = firstLineOffset + lengthOffset + prefixDelta
              trackedOffset.setIndex(offsetIndex + delta)
            }
          }
        }
      } else {
        context.renderChildren(node)
        markdown.line()
      }
    }

  def renderBlockQuoteLike(node: BlockQuoteLike, context: NodeFormatterContext, markdown: MarkdownWriter): Unit = {
    val formatterOptions = context.getFormatterOptions

    val combinedPrefix = getBlockLikePrefix(node, context, formatterOptions.blockQuoteMarkers, markdown.getPrefix)

    markdown.pushPrefix()

    if (!FIRST_LIST_ITEM_CHILD.get(Nullable(context.getDocument))) {
      if (formatterOptions.blockQuoteBlankLines) {
        markdown.blankLine()
      }
      markdown.setPrefix(combinedPrefix, false)
    } else {
      val firstPrefix = getBlockLikePrefix(node, context, formatterOptions.blockQuoteMarkers, BasedSequence.NULL)
      markdown.pushOptions().removeOptions(LineAppendable.F_WHITESPACE_REMOVAL).append(firstPrefix).popOptions()
      markdown.setPrefix(combinedPrefix, true)
    }

    val lines = markdown.getLineCountWithPending
    context.renderChildren(node.asInstanceOf[Node])
    markdown.popPrefix()

    if (formatterOptions.blockQuoteBlankLines && (lines < markdown.getLineCountWithPending && !FIRST_LIST_ITEM_CHILD.get(Nullable(context.getDocument)))) {
      markdown.tailBlankLine()
    }
  }
}
