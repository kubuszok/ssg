/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/core/ListBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser
package core

import ssg.md.ast._
import ssg.md.ast.util.Parsing
import ssg.md.parser.ListOptions
import ssg.md.parser.Parser
import ssg.md.parser.ParserEmulationProfile
import ssg.md.parser.block._
import ssg.md.util.ast.{ BlankLine, BlankLineContainer, Block, Node }

import ssg.md.util.data.DataHolder
import ssg.md.util.misc.CharPredicate
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.SequenceUtils
import ssg.md.util.sequence.mappers.{ SpecialLeadInCharsHandler, SpecialLeadInHandler }

import java.util.regex.Matcher

import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

class ListBlockParser(
  val options:  ListOptions,
  val listData: ListBlockParser.ListData,
  listItemParser: ListItemParser
) extends AbstractBlockParser,
    BlankLineContainer {

  private val myBlock: ListBlock = listData.listBlock
  myBlock.tight_=(true)

  var lastChild: ListItemParser                 = listItemParser
  var itemHandledLine: Nullable[BasedSequence]  = Nullable.empty
  var itemHandledNewListLine:    Boolean         = false
  var itemHandledNewItemLine:    Boolean         = false
  var itemHandledSkipActiveLine: Boolean         = false

  def setItemHandledLine(line: BasedSequence): Unit = {
    itemHandledLine = Nullable(line)
    itemHandledNewListLine = false
    itemHandledNewItemLine = false
    itemHandledSkipActiveLine = false
  }

  def setItemHandledNewListLine(line: BasedSequence): Unit = {
    itemHandledLine = Nullable(line)
    itemHandledNewListLine = true
    itemHandledNewItemLine = false
    itemHandledSkipActiveLine = false
  }

  def setItemHandledNewItemLine(line: BasedSequence): Unit = {
    itemHandledLine = Nullable(line)
    itemHandledNewListLine = false
    itemHandledNewItemLine = true
    itemHandledSkipActiveLine = false
  }

  def setItemHandledLineSkipActive(line: BasedSequence): Unit = {
    itemHandledLine = Nullable(line)
    itemHandledNewListLine = false
    itemHandledNewItemLine = false
    itemHandledSkipActiveLine = true
  }

  def contentIndent: Int =
    listData.markerIndent + listData.listMarker.length() + listData.contentOffset

  def lastContentIndent: Int =
    lastChild.contentIndent

  override def isContainer: Boolean = true

  override def canContain(state: ParserState, blockParser: BlockParser, block: Block): Boolean =
    block.isInstanceOf[ListItem]

  override def getBlock: ListBlock = myBlock

  private def setTight(tight: Boolean): Unit =
    myBlock.tight_=(tight)

  override def closeBlock(state: ParserState): Unit = {
    finalizeListTight(state)

    if (Parser.BLANK_LINES_IN_AST.get(state.properties)) {
      // need to transfer trailing blank line nodes from last item to parent list
      val block = getBlock

      var child = block.firstChildAnyNot(classOf[BlankLine])

      while (child.isDefined && child.get.isInstanceOf[ListItem]) {
        // transfer its trailing blank lines to us
        child.get.moveTrailingBlankLines()
        child = child.get.nextAnyNot(classOf[BlankLine])
      }
    }

    myBlock.setCharsFromContentOnly()
  }

  override def breakOutOnDoubleBlankLine: Boolean =
    options.isEndOnDoubleBlank

  override def tryContinue(state: ParserState): Nullable[BlockContinue] =
    // List blocks themselves don't have any markers, only list items. So try to stay in the list.
    // If there is a block start other than list item, canContain makes sure that this list is closed.
    Nullable(BlockContinue.atIndex(state.getIndex))

  private def hasNonItemChildren(item: ListItem): Boolean = boundary {
    if (item.hasChildren) {
      var count = 0
      val iter = item.childIterator
      while (iter.hasNext) {
        val child = iter.next()
        if (!child.isInstanceOf[ListBlock]) {
          count += 1
          if (count >= 2) {
            break(true)
          }
        }
      }
    }
    false
  }

  private def finalizeListTight(parserState: ParserState): Unit = {
    var item: Nullable[Node] = getBlock.firstChild
    var isTight = true
    var prevItemHadTrueTrailingBlankLine = false
    var haveNestedList = false

    while (item.isDefined) {
      val currentItem = item.get
      // check for non-final list item ending with blank line:
      var thisItemHadBlankAfterItemPara = false
      var thisItemContainsBlankLine = false
      @annotation.nowarn("msg=was mutated but not read") // faithful port: tracks state used to set thisItemHadTrueTrailingBlankLine
      var thisItemHadTrailingBlankLine = false
      var thisItemHadTrueTrailingBlankLine = false
      var thisItemHadChildren = false
      var thisItemLoose = false

      currentItem match {
        case listItem: ListItem =>
          if (listItem.isHadBlankAfterItemParagraph) {
            //noinspection StatementWithEmptyBody
            if (currentItem.next.isEmpty && (currentItem.firstChild.isEmpty || currentItem.firstChild.get.next.isEmpty)) {
              // not for last block
            } else {
              thisItemHadBlankAfterItemPara = true
            }
          }

          if (listItem.isContainsBlankLine) {
            thisItemContainsBlankLine = true
          }

          if (parserState.endsWithBlankLine(currentItem) && currentItem.next.isDefined) {
            thisItemHadTrueTrailingBlankLine = true
          }

          if (hasNonItemChildren(listItem)) {
            thisItemHadChildren = true
          }

          //noinspection PointlessBooleanExpression
          thisItemLoose = false ||
            (thisItemHadTrueTrailingBlankLine && options.isLooseWhenHasTrailingBlankLine) ||
            (thisItemHadBlankAfterItemPara && options.isLooseWhenBlankLineFollowsItemParagraph) ||
            (thisItemContainsBlankLine && options.isLooseWhenContainsBlankLine) ||
            (thisItemHadChildren && options.isLooseWhenHasNonListChildren) ||
            ((thisItemHadTrueTrailingBlankLine && currentItem.previous.isEmpty || prevItemHadTrueTrailingBlankLine) &&
              (options.isLooseWhenPrevHasTrailingBlankLine || (options.isLooseWhenLastItemPrevHasTrailingBlankLine && currentItem.next.isEmpty)))

          if (thisItemLoose) {
            listItem.loose_=(true)
            isTight = false
          }
        case _ => // not a list item, skip
      }

      // recurse into children of list item, to see if there are
      // spaces between any of them:
      var subItem: Nullable[Node] = currentItem.firstChild
      boundary {
        while (subItem.isDefined) {
          val currentSubItem = subItem.get
          if (parserState.endsWithBlankLine(currentSubItem) && (currentItem.next.isDefined || currentSubItem.next.isDefined)) {
            thisItemHadTrailingBlankLine = true
            if (subItem == currentItem.lastChild) thisItemHadTrueTrailingBlankLine = true

            if (!thisItemLoose) {
              if (options.isLooseWhenHasTrailingBlankLine) {
                isTight = false
              }

              if (thisItemHadTrueTrailingBlankLine && currentItem.previous.isEmpty && options.isLooseWhenPrevHasTrailingBlankLine) {
                isTight = false
                thisItemLoose = true
                currentItem match {
                  case li: ListItem => li.loose_=(true)
                  case _            =>
                }
              }
            }
          }

          if (currentSubItem.isInstanceOf[ListBlock]) {
            haveNestedList = true
            if (!thisItemLoose && options.isLooseWhenHasLooseSubItem) {
              val iterator = currentSubItem.childIterator
              boundary {
                while (iterator.hasNext) {
                  val item1 = iterator.next().asInstanceOf[ListItem]
                  if (!item1.isTight) {
                    thisItemLoose = true
                    isTight = false
                    currentItem match {
                      case li: ListItem => li.loose_=(true)
                      case _            =>
                    }
                    break(())
                  }
                }
              }
            }
          }

          if (options.isLooseWhenHasLooseSubItem) {
            if (thisItemLoose && (haveNestedList || !options.isAutoLooseOneLevelLists)) break(())
          } else {
            if (!isTight && (haveNestedList || !options.isAutoLooseOneLevelLists)) break(())
          }
          subItem = currentSubItem.next
        }
      }

      currentItem match {
        case _: ListItem =>
          prevItemHadTrueTrailingBlankLine = thisItemHadTrueTrailingBlankLine
        case _ =>
      }

      item = currentItem.next
    }

    if (options.isAutoLoose && options.isAutoLooseOneLevelLists) {
      if (!haveNestedList && getBlock.ancestorOfType(classOf[ListBlock]).isEmpty && !isTight) {
        setTight(false)
      }
    } else {
      if (options.isAutoLoose && !isTight) {
        setTight(false)
      }
    }
  }
}

object ListBlockParser {

  /** Parse a list marker and return data on the marker or null.
    */
  def parseListMarker(options: ListOptions, newItemCodeIndent: Int, state: ParserState): Nullable[ListData] = boundary {
    val parsing = state.parsing
    val line = state.line
    val markerIndex = state.nextNonSpaceIndex
    val markerColumn = state.column + state.indent
    val markerIndent = state.indent

    val rest = line.subSequence(markerIndex, line.length())
    val matcher = parsing.LIST_ITEM_MARKER.matcher(rest)
    if (!matcher.find()) {
      break(Nullable.empty)
    }

    // Cross-platform: LIST_ITEM_MARKER no longer uses lookaheads for the
    // trailing space/tab/EOL check. Perform it here instead.
    val matchEnd = matcher.end()
    if (parsing.listsItemMarkerSpaceFlag) {
      // listsItemMarkerSpace=true: require space or tab after marker
      if (matchEnd >= rest.length()) break(Nullable.empty)
      val nextCh = rest.charAt(matchEnd)
      if (nextCh != ' ' && nextCh != '\t') break(Nullable.empty)
    } else {
      // listsItemMarkerSpace=false: require space, tab, or end-of-string
      if (matchEnd < rest.length()) {
        val nextCh = rest.charAt(matchEnd)
        if (nextCh != ' ' && nextCh != '\t') break(Nullable.empty)
      }
    }

    val listBlock = createListBlock(matcher)

    val markerLength = matcher.end() - matcher.start()
    val isNumberedList = !"+-*".contains(matcher.group())
    val indexAfterMarker = markerIndex + markerLength

    // marker doesn't include tabs, so counting them as columns directly is ok
    var columnAfterMarker = markerColumn + markerLength

    // the column within the line where the content starts
    var contentOffset = 0

    // See at which column the content starts if there is content
    var hasContent = false
    var contentIndex = indexAfterMarker
    boundary {
      var i = indexAfterMarker
      while (i < line.length()) {
        val c = line.charAt(i)
        if (c == '\t') {
          contentOffset += Parsing.columnsToNextTabStop(columnAfterMarker + contentOffset)
          contentIndex += 1
        } else if (c == ' ') {
          contentOffset += 1
          contentIndex += 1
        } else {
          hasContent = true
          break(())
        }
        i += 1
      }
    }

    var markerSuffix: BasedSequence = BasedSequence.NULL
    var markerSuffixOffset = contentOffset

    if (!hasContent || contentOffset > newItemCodeIndent) {
      // If this line is blank or has a code block, default to 1 space after marker
      contentOffset = 1
      markerSuffixOffset = 1
    } else if (!isNumberedList || options.isNumberedItemMarkerSuffixed) {
      // see if we have optional suffix strings on the marker
      val markerSuffixes = options.getItemMarkerSuffixes
      boundary {
        for (suffix <- markerSuffixes) {
          val suffixLength = suffix.length
          if (suffixLength > 0 && line.matchChars(suffix, contentIndex)) {
            if (options.isItemMarkerSpace) {
              val c = line.midCharAt(contentIndex + suffixLength)
              if (c != ' ' && c != '\t') {
                // no space after, no match -- continue to next suffix
              } else {
                markerSuffix = line.subSequence(contentIndex, contentIndex + suffixLength)
                contentOffset += suffixLength
                contentIndex += suffixLength
                columnAfterMarker += suffixLength

                hasContent = false
                val suffixContentOffset = contentOffset

                var i = contentIndex
                while (i < line.length()) {
                  val ch = line.charAt(i)
                  if (ch == '\t') {
                    contentOffset += Parsing.columnsToNextTabStop(columnAfterMarker + contentOffset)
                  } else if (ch == ' ') {
                    contentOffset += 1
                  } else {
                    hasContent = true
                    i = line.length() // break inner while
                  }
                  i += 1
                }

                if (!hasContent || contentOffset - suffixContentOffset > newItemCodeIndent) {
                  // If this line is blank or has a code block, default to 1 space after marker suffix
                  contentOffset = suffixContentOffset + 1
                }
                break(()) // break for loop
              }
            } else {
              markerSuffix = line.subSequence(contentIndex, contentIndex + suffixLength)
              contentOffset += suffixLength
              contentIndex += suffixLength
              columnAfterMarker += suffixLength

              hasContent = false
              val suffixContentOffset = contentOffset

              var i = contentIndex
              while (i < line.length()) {
                val ch = line.charAt(i)
                if (ch == '\t') {
                  contentOffset += Parsing.columnsToNextTabStop(columnAfterMarker + contentOffset)
                } else if (ch == ' ') {
                  contentOffset += 1
                } else {
                  hasContent = true
                  i = line.length() // break inner while
                }
                i += 1
              }

              if (!hasContent || contentOffset - suffixContentOffset > newItemCodeIndent) {
                contentOffset = suffixContentOffset + 1
              }
              break(()) // break for loop
            }
          }
        }
      }
    }

    Nullable(ListData(
      listBlock,
      !hasContent,
      markerIndex,
      markerColumn,
      markerIndent,
      contentOffset,
      rest.subSequence(matcher.start(), matcher.end()),
      isNumberedList,
      markerSuffix,
      markerSuffixOffset
    ))
  }

  private def createListBlock(matcher: Matcher): ListBlock = {
    val bullet = matcher.group(1)
    if (bullet != null) { // @nowarn -- Java regex group returns null when not matched
      val bulletList = BulletList()
      bulletList.openingMarker = bullet.charAt(0)
      bulletList
    } else {
      val digit = matcher.group(2)
      val delimiter = matcher.group(3)
      val orderedList = OrderedList()
      orderedList.startNumber = Integer.parseInt(digit)
      orderedList.delimiter = delimiter.charAt(0)
      orderedList
    }
  }

  final case class ListData(
    listBlock:          ListBlock,
    isEmpty:            Boolean,
    markerIndex:        Int,
    markerColumn:       Int,
    markerIndent:       Int,
    contentOffset:      Int,
    listMarker:         BasedSequence,
    isNumberedList:     Boolean,
    markerSuffix:       BasedSequence,
    markerSuffixOffset: Int
  )

  class Factory extends CustomBlockParserFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable(
      Set(
        classOf[BlockQuoteParser.Factory],
        classOf[HeadingParser.Factory],
        classOf[FencedCodeBlockParser.Factory],
        classOf[HtmlBlockParser.Factory],
        classOf[ThematicBreakParser.Factory]
      )
    )

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable(
      Set(
        classOf[IndentedCodeBlockParser.Factory]
      )
    )

    override def affectsGlobalScope: Boolean = false

    override def apply(options: DataHolder): BlockParserFactory = BlockFactory(options)

    override def getLeadInHandler(options: DataHolder): Nullable[SpecialLeadInHandler] =
      Nullable(ListItemLeadInHandler.create(
        Parser.LISTS_ITEM_PREFIX_CHARS.get(options),
        Parser.LISTS_ORDERED_ITEM_DOT_ONLY.get(options)
      ))
  }

  private class ListItemLeadInHandler(listItemDelims: CharSequence, dotOnly: Boolean) extends SpecialLeadInCharsHandler(CharPredicate.anyOf(listItemDelims)) {

    private val orderedDelims: CharPredicate =
      if (dotOnly) ListItemLeadInHandler.ORDERED_DELIM_DOT
      else ListItemLeadInHandler.ORDERED_DELIM_DOT_PARENS

    override def escape(sequence: BasedSequence, options: Nullable[DataHolder], consumer: CharSequence => Unit): Boolean = {
      if (super.escape(sequence, options, consumer)) {
        true
      } else if (ssg.md.util.data.SharedDataKeys.ESCAPE_NUMBERED_LEAD_IN.get(options)) {
        val nonDigit = sequence.indexOfAnyNot(CharPredicate.DECIMAL_DIGITS)
        if (nonDigit > 0 && nonDigit + 1 == sequence.length() && orderedDelims.test(sequence.charAt(nonDigit))) {
          consumer(sequence.subSequence(0, nonDigit))
          consumer("\\")
          consumer(sequence.subSequence(nonDigit))
          true
        } else {
          false
        }
      } else {
        false
      }
    }

    override def unEscape(sequence: BasedSequence, options: Nullable[DataHolder], consumer: CharSequence => Unit): Boolean = {
      if (super.unEscape(sequence, options, consumer)) {
        true
      } else {
        val nonDigit = sequence.indexOfAnyNot(CharPredicate.DECIMAL_DIGITS)
        if (nonDigit > 0 && nonDigit + 2 == sequence.length() && sequence.charAt(nonDigit) == '\\' && orderedDelims.test(sequence.charAt(nonDigit + 1))) {
          consumer(sequence.subSequence(0, nonDigit))
          consumer(sequence.subSequence(nonDigit + 1))
          true
        } else {
          false
        }
      }
    }
  }

  private object ListItemLeadInHandler {
    val ORDERED_DELIM_DOT:        CharPredicate       = CharPredicate.anyOf('.')
    val ORDERED_DELIM_DOT_PARENS: CharPredicate       = CharPredicate.anyOf(".)")
    val ORDERED_DELIM_DOT_HANDLER: SpecialLeadInHandler       = new ListItemLeadInHandler(Parser.LISTS_ITEM_PREFIX_CHARS.defaultValue, true)
    val ORDERED_DELIM_DOT_PARENS_HANDLER: SpecialLeadInHandler = new ListItemLeadInHandler(Parser.LISTS_ITEM_PREFIX_CHARS.defaultValue, false)

    def create(listItemDelims: CharSequence, dotOnly: Boolean): SpecialLeadInHandler = {
      if (SequenceUtils.equals(Parser.LISTS_ITEM_PREFIX_CHARS.defaultValue, listItemDelims)) {
        if (dotOnly) ORDERED_DELIM_DOT_HANDLER
        else ORDERED_DELIM_DOT_PARENS_HANDLER
      } else {
        new ListItemLeadInHandler(listItemDelims, dotOnly)
      }
    }
  }

  private class BlockFactory(options: DataHolder) extends AbstractBlockParserFactory(options) {

    private val myOptions: ListOptions = ListOptions.get(options)

    override def tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): Nullable[BlockStart] = boundary {
      val matched = matchedBlockParser.blockParser
      val emulationFamily = myOptions.getParserEmulationProfile.family
      val newItemCodeIndent = myOptions.getNewItemCodeIndent

      matched match {
        case listBlockParser: ListBlockParser =>
          // the list item should have handled this line, if it is part of new list item, or a new sub list
          if (listBlockParser.itemHandledLine.exists(_ eq state.line)) {
            if (listBlockParser.itemHandledNewListLine) {
              // it is a new list already determined by the item
              val listData = parseListMarker(myOptions, newItemCodeIndent, state)
              assert(listData.isDefined)
              val ld = listData.get
              val listItemParser = new ListItemParser(myOptions, state.parsing, ld)
              val newColumn = ld.markerColumn + ld.listMarker.length() + ld.contentOffset
              val newListBlockParser = new ListBlockParser(myOptions, ld, listItemParser)
              Nullable(BlockStart.of(newListBlockParser, listItemParser).atColumn(newColumn))
            } else if (listBlockParser.itemHandledNewItemLine) {
              // it is a new item for this list already determined by the previous item
              val listData = parseListMarker(myOptions, newItemCodeIndent, state)
              assert(listData.isDefined)
              val ld = listData.get
              val listItemParser = new ListItemParser(myOptions, state.parsing, ld)
              val newColumn = ld.markerColumn + ld.listMarker.length() + ld.contentOffset
              listBlockParser.lastChild = listItemParser
              Nullable(BlockStart.of(listItemParser).atColumn(newColumn))
            } else {
              // then it is not for us to handle, since we only handle new list creation here outside of an existing list
              listBlockParser.itemHandledLine = Nullable.empty
              BlockStart.none()
            }
          } else {
            // if there is a pre-existing list then it's last list item should have handled the line
            BlockStart.none()
          }

        case _ =>
          // see if the list item is still active and set line handled, need this to handle lazy continuations when they look like list items
          val block = matched.getBlock.ancestorOfType(classOf[ListBlock])
          if (block.isDefined) {
            val listBlockParser = state.getActiveBlockParser(block.get.asInstanceOf[Block]).asInstanceOf[ListBlockParser]
            if (listBlockParser.itemHandledLine.exists(_ eq state.line) && listBlockParser.itemHandledSkipActiveLine) {
              listBlockParser.itemHandledLine = Nullable.empty
              break(BlockStart.none())
            }
          }

          // at this point if the line should have been handled by the item
          // what we can have here is list items for the same list or list items that start a new list because of mismatched type
          // nothing else should get here because the list item should have handled it
          // so everything should have indent >= current list indent, the rest should not be here

          if (emulationFamily == ParserEmulationProfile.COMMONMARK) {
            val currentIndent = state.indent
            if (currentIndent >= myOptions.getCodeIndent) {
              break(BlockStart.none())
            }
          } else if (emulationFamily == ParserEmulationProfile.FIXED_INDENT) {
            val currentIndent = state.indent
            if (currentIndent >= myOptions.getItemIndent) {
              break(BlockStart.none())
            }
          } else if (emulationFamily == ParserEmulationProfile.KRAMDOWN) {
            val currentIndent = state.indent
            if (currentIndent >= myOptions.getItemIndent) {
              break(BlockStart.none())
            }
          } else if (emulationFamily == ParserEmulationProfile.MARKDOWN) {
            val currentIndent = state.indent
            if (currentIndent >= myOptions.getItemIndent) {
              break(BlockStart.none())
            }
          }

          val listData = parseListMarker(myOptions, newItemCodeIndent, state)

          if (listData.isDefined) {
            val ld = listData.get
            val newColumn = ld.markerColumn + ld.listMarker.length() + ld.contentOffset

            val inParagraph = matched.isParagraphParser
            val inParagraphListItem = inParagraph &&
              matched.getBlock.parent.exists(_.isInstanceOf[ListItem]) &&
              matched.getBlock.parent.flatMap(_.firstChild).contains(matched.getBlock)

            if (inParagraph && !myOptions.canInterrupt(ld.listBlock, ld.isEmpty, inParagraphListItem)) {
              BlockStart.none()
            } else {
              val listItemParser = new ListItemParser(myOptions, state.parsing, ld)
              // prepend new list block
              val listBlockParser = new ListBlockParser(myOptions, ld, listItemParser)
              Nullable(BlockStart.of(listBlockParser, listItemParser).atColumn(newColumn))
            }
          } else {
            BlockStart.none()
          }
      }
    }
  }
}
