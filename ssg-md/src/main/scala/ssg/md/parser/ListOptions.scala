/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/ListOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/ListOptions.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser

import ssg.md.ast._
import ssg.md.util.data.{ DataHolder, MutableDataHolder, MutableDataSetter }

import scala.language.implicitConversions

class ListOptions protected (options: Nullable[DataHolder]) extends MutableDataSetter {

  protected var myParserEmulationProfile: ParserEmulationProfile    = Parser.PARSER_EMULATION_PROFILE.get(options)
  protected var itemInterrupt:            ListOptions.ItemInterrupt = ListOptions.ItemInterrupt(options)

  protected var autoLoose:                                 Boolean       = Parser.LISTS_AUTO_LOOSE.get(options)
  protected var autoLooseOneLevelLists:                    Boolean       = Parser.LISTS_AUTO_LOOSE_ONE_LEVEL_LISTS.get(options)
  protected var delimiterMismatchToNewList:                Boolean       = Parser.LISTS_DELIMITER_MISMATCH_TO_NEW_LIST.get(options)
  protected var endOnDoubleBlank:                          Boolean       = Parser.LISTS_END_ON_DOUBLE_BLANK.get(options)
  protected var itemMarkerSpace:                           Boolean       = Parser.LISTS_ITEM_MARKER_SPACE.get(options)
  protected var itemTypeMismatchToNewList:                 Boolean       = Parser.LISTS_ITEM_TYPE_MISMATCH_TO_NEW_LIST.get(options)
  protected var itemTypeMismatchToSubList:                 Boolean       = Parser.LISTS_ITEM_TYPE_MISMATCH_TO_SUB_LIST.get(options)
  protected var looseWhenPrevHasTrailingBlankLine:         Boolean       = Parser.LISTS_LOOSE_WHEN_PREV_HAS_TRAILING_BLANK_LINE.get(options)
  protected var looseWhenLastItemPrevHasTrailingBlankLine: Boolean       = Parser.LISTS_LOOSE_WHEN_LAST_ITEM_PREV_HAS_TRAILING_BLANK_LINE.get(options)
  protected var looseWhenHasNonListChildren:               Boolean       = Parser.LISTS_LOOSE_WHEN_HAS_NON_LIST_CHILDREN.get(options)
  protected var looseWhenBlankLineFollowsItemParagraph:    Boolean       = Parser.LISTS_LOOSE_WHEN_BLANK_LINE_FOLLOWS_ITEM_PARAGRAPH.get(options)
  protected var looseWhenHasLooseSubItem:                  Boolean       = Parser.LISTS_LOOSE_WHEN_HAS_LOOSE_SUB_ITEM.get(options)
  protected var looseWhenHasTrailingBlankLine:             Boolean       = Parser.LISTS_LOOSE_WHEN_HAS_TRAILING_BLANK_LINE.get(options)
  protected var looseWhenContainsBlankLine:                Boolean       = Parser.LISTS_LOOSE_WHEN_CONTAINS_BLANK_LINE.get(options)
  protected var numberedItemMarkerSuffixed:                Boolean       = Parser.LISTS_NUMBERED_ITEM_MARKER_SUFFIXED.get(options)
  protected var orderedItemDotOnly:                        Boolean       = Parser.LISTS_ORDERED_ITEM_DOT_ONLY.get(options)
  protected var orderedListManualStart:                    Boolean       = Parser.LISTS_ORDERED_LIST_MANUAL_START.get(options)
  protected var itemContentAfterSuffix:                    Boolean       = Parser.LISTS_ITEM_CONTENT_AFTER_SUFFIX.get(options)
  protected var itemPrefixChars:                           String        = Parser.LISTS_ITEM_PREFIX_CHARS.get(options)
  protected var codeIndent:                                Int           = Parser.LISTS_CODE_INDENT.get(options)
  protected var itemIndent:                                Int           = Parser.LISTS_ITEM_INDENT.get(options)
  protected var newItemCodeIndent:                         Int           = Parser.LISTS_NEW_ITEM_CODE_INDENT.get(options)
  protected var itemMarkerSuffixes:                        Array[String] = Parser.LISTS_ITEM_MARKER_SUFFIXES.get(options)

  def this() = this(Nullable.empty)

  protected def this(other: ListOptions) = {
    this(Nullable.empty)
    myParserEmulationProfile = other.getParserEmulationProfile
    itemInterrupt = ListOptions.ItemInterrupt(other.getItemInterrupt)
    autoLoose = other.isAutoLoose
    autoLooseOneLevelLists = other.isAutoLooseOneLevelLists
    delimiterMismatchToNewList = other.isDelimiterMismatchToNewList
    endOnDoubleBlank = other.isEndOnDoubleBlank
    itemMarkerSpace = other.isItemMarkerSpace
    itemTypeMismatchToNewList = other.isItemTypeMismatchToNewList
    itemTypeMismatchToSubList = other.isItemTypeMismatchToSubList
    looseWhenPrevHasTrailingBlankLine = other.isLooseWhenPrevHasTrailingBlankLine
    looseWhenLastItemPrevHasTrailingBlankLine = other.isLooseWhenLastItemPrevHasTrailingBlankLine
    looseWhenHasNonListChildren = other.isLooseWhenHasNonListChildren
    looseWhenBlankLineFollowsItemParagraph = other.isLooseWhenBlankLineFollowsItemParagraph
    looseWhenHasLooseSubItem = other.isLooseWhenHasLooseSubItem
    looseWhenHasTrailingBlankLine = other.isLooseWhenHasTrailingBlankLine
    looseWhenContainsBlankLine = other.isLooseWhenContainsBlankLine
    numberedItemMarkerSuffixed = other.isNumberedItemMarkerSuffixed
    orderedItemDotOnly = other.isOrderedItemDotOnly
    orderedListManualStart = other.isOrderedListManualStart
    itemContentAfterSuffix = other.isItemContentAfterSuffix
    itemPrefixChars = other.getItemPrefixChars
    codeIndent = other.getCodeIndent
    itemIndent = other.getItemIndent
    newItemCodeIndent = other.getNewItemCodeIndent
    itemMarkerSuffixes = other.getItemMarkerSuffixes
  }

  def isTightListItem(node: ListItem): Boolean =
    if (node.isLoose) false
    else {
      val al = isAutoLoose
      if (al && isAutoLooseOneLevelLists) {
        val singleLevel = node.ancestorOfType(classOf[ListItem]).isEmpty && node.childOfType(classOf[ListBlock]).isEmpty
        node.firstChild.isEmpty || (!singleLevel && node.isTight) || (singleLevel && node.isInTightList)
      } else {
        node.firstChild.isEmpty || (!al && node.isTight) || (al && node.isInTightList)
      }
    }

  def isInTightListItem(node: Paragraph): Boolean = {
    val parent = node.parent
    parent.fold(false) {
      case listItem: ListItem =>
        if (!listItem.isItemParagraph(node)) false
        else {
          val al = isAutoLoose
          if (al && isAutoLooseOneLevelLists) {
            isTightListItem(listItem)
          } else {
            (!al && listItem.isParagraphInTightListItem(node)) || (al && listItem.isInTightList)
          }
        }
      case _ => false
    }
  }

  def canInterrupt(a: ListBlock, isEmptyItem: Boolean, isItemParagraph: Boolean): Boolean = {
    val isNumberedItem = a.isInstanceOf[OrderedList]
    val isOneItem      = isNumberedItem && (!isOrderedListManualStart || a.asInstanceOf[OrderedList].startNumber == 1)
    getItemInterrupt.canInterrupt(isNumberedItem, isOneItem, isEmptyItem, isItemParagraph)
  }

  def canStartSubList(a: ListBlock, isEmptyItem: Boolean): Boolean = {
    val isNumberedItem = a.isInstanceOf[OrderedList]
    val isOneItem      = isNumberedItem && (!isOrderedListManualStart || a.asInstanceOf[OrderedList].startNumber == 1)
    getItemInterrupt.canStartSubList(isNumberedItem, isOneItem, isEmptyItem)
  }

  def startNewList(a: ListBlock, b: ListBlock): Boolean = {
    val isNumberedList = a.isInstanceOf[OrderedList]
    val isNumberedItem = b.isInstanceOf[OrderedList]

    if (isNumberedList == isNumberedItem) {
      if (isNumberedList) {
        isDelimiterMismatchToNewList && a.asInstanceOf[OrderedList].delimiter != b.asInstanceOf[OrderedList].delimiter
      } else {
        isDelimiterMismatchToNewList && a.asInstanceOf[BulletList].openingMarker != b.asInstanceOf[BulletList].openingMarker
      }
    } else {
      isItemTypeMismatchToNewList
    }
  }

  def startSubList(a: ListBlock, b: ListBlock): Boolean = {
    val isNumberedList = a.isInstanceOf[OrderedList]
    val isNumberedItem = b.isInstanceOf[OrderedList]
    isNumberedList != isNumberedItem && isItemTypeMismatchToSubList
  }

  def getMutable: MutableListOptions = MutableListOptions(this)

  override def setIn(options: MutableDataHolder): MutableDataHolder = {
    options.set(Parser.PARSER_EMULATION_PROFILE, getParserEmulationProfile)
    getItemInterrupt.setIn(options)

    options.set(Parser.LISTS_AUTO_LOOSE, autoLoose)
    options.set(Parser.LISTS_AUTO_LOOSE_ONE_LEVEL_LISTS, autoLooseOneLevelLists)
    options.set(Parser.LISTS_DELIMITER_MISMATCH_TO_NEW_LIST, delimiterMismatchToNewList)
    options.set(Parser.LISTS_END_ON_DOUBLE_BLANK, endOnDoubleBlank)
    options.set(Parser.LISTS_ITEM_MARKER_SPACE, itemMarkerSpace)
    options.set(Parser.LISTS_ITEM_TYPE_MISMATCH_TO_NEW_LIST, itemTypeMismatchToNewList)
    options.set(Parser.LISTS_ITEM_TYPE_MISMATCH_TO_SUB_LIST, itemTypeMismatchToSubList)
    options.set(Parser.LISTS_LOOSE_WHEN_PREV_HAS_TRAILING_BLANK_LINE, looseWhenPrevHasTrailingBlankLine)
    options.set(Parser.LISTS_LOOSE_WHEN_LAST_ITEM_PREV_HAS_TRAILING_BLANK_LINE, looseWhenLastItemPrevHasTrailingBlankLine)
    options.set(Parser.LISTS_LOOSE_WHEN_HAS_NON_LIST_CHILDREN, looseWhenHasNonListChildren)
    options.set(Parser.LISTS_LOOSE_WHEN_BLANK_LINE_FOLLOWS_ITEM_PARAGRAPH, looseWhenBlankLineFollowsItemParagraph)
    options.set(Parser.LISTS_LOOSE_WHEN_HAS_LOOSE_SUB_ITEM, looseWhenHasLooseSubItem)
    options.set(Parser.LISTS_LOOSE_WHEN_HAS_TRAILING_BLANK_LINE, looseWhenHasTrailingBlankLine)
    options.set(Parser.LISTS_LOOSE_WHEN_CONTAINS_BLANK_LINE, looseWhenContainsBlankLine)
    options.set(Parser.LISTS_NUMBERED_ITEM_MARKER_SUFFIXED, numberedItemMarkerSuffixed)
    options.set(Parser.LISTS_ORDERED_ITEM_DOT_ONLY, orderedItemDotOnly)
    options.set(Parser.LISTS_ORDERED_LIST_MANUAL_START, orderedListManualStart)
    options.set(Parser.LISTS_CODE_INDENT, codeIndent)
    options.set(Parser.LISTS_ITEM_INDENT, itemIndent)
    options.set(Parser.LISTS_NEW_ITEM_CODE_INDENT, newItemCodeIndent)
    options.set(Parser.LISTS_ITEM_MARKER_SUFFIXES, itemMarkerSuffixes)
    options.set(Parser.LISTS_ITEM_CONTENT_AFTER_SUFFIX, itemContentAfterSuffix)
    options.set(Parser.LISTS_ITEM_PREFIX_CHARS, itemPrefixChars)

    options
  }

  def getParserEmulationProfile:                   ParserEmulationProfile    = myParserEmulationProfile
  def getItemInterrupt:                            ListOptions.ItemInterrupt = itemInterrupt
  def isAutoLoose:                                 Boolean                   = autoLoose
  def isAutoLooseOneLevelLists:                    Boolean                   = autoLooseOneLevelLists
  def isDelimiterMismatchToNewList:                Boolean                   = delimiterMismatchToNewList
  def isEndOnDoubleBlank:                          Boolean                   = endOnDoubleBlank
  def isItemMarkerSpace:                           Boolean                   = itemMarkerSpace
  def isItemTypeMismatchToNewList:                 Boolean                   = itemTypeMismatchToNewList
  def isItemContentAfterSuffix:                    Boolean                   = itemContentAfterSuffix
  def getItemPrefixChars:                          String                    = itemPrefixChars
  def isItemTypeMismatchToSubList:                 Boolean                   = itemTypeMismatchToSubList
  def isLooseWhenPrevHasTrailingBlankLine:         Boolean                   = looseWhenPrevHasTrailingBlankLine
  def isLooseWhenLastItemPrevHasTrailingBlankLine: Boolean                   = looseWhenLastItemPrevHasTrailingBlankLine
  def isLooseWhenHasNonListChildren:               Boolean                   = looseWhenHasNonListChildren
  def isLooseWhenHasLooseSubItem:                  Boolean                   = looseWhenHasLooseSubItem
  def isLooseWhenHasTrailingBlankLine:             Boolean                   = looseWhenHasTrailingBlankLine
  def isLooseWhenContainsBlankLine:                Boolean                   = looseWhenContainsBlankLine
  def isLooseWhenBlankLineFollowsItemParagraph:    Boolean                   = looseWhenBlankLineFollowsItemParagraph
  def isOrderedItemDotOnly:                        Boolean                   = orderedItemDotOnly
  def isOrderedListManualStart:                    Boolean                   = orderedListManualStart
  def isNumberedItemMarkerSuffixed:                Boolean                   = numberedItemMarkerSuffixed
  def getCodeIndent:                               Int                       = codeIndent
  def getItemIndent:                               Int                       = itemIndent
  def getNewItemCodeIndent:                        Int                       = newItemCodeIndent
  def getItemMarkerSuffixes:                       Array[String]             = itemMarkerSuffixes
}

object ListOptions {

  def get(options: DataHolder): ListOptions = ListOptions(Nullable(options))

  def addItemMarkerSuffixes(options: MutableDataHolder, addSuffixes: String*): Unit = {
    val suffixes    = Parser.LISTS_ITEM_MARKER_SUFFIXES.get(options)
    val newSuffixes = addSuffixes.filterNot(suffixes.contains)
    if (newSuffixes.nonEmpty) {
      options.set(Parser.LISTS_ITEM_MARKER_SUFFIXES, suffixes ++ newSuffixes)
    }
  }

  class ItemInterrupt(options: Nullable[DataHolder]) {

    var bulletItemInterruptsParagraph:        Boolean = Parser.LISTS_BULLET_ITEM_INTERRUPTS_PARAGRAPH.get(options)
    var orderedItemInterruptsParagraph:       Boolean = Parser.LISTS_ORDERED_ITEM_INTERRUPTS_PARAGRAPH.get(options)
    var orderedNonOneItemInterruptsParagraph: Boolean = Parser.LISTS_ORDERED_NON_ONE_ITEM_INTERRUPTS_PARAGRAPH.get(options)

    var emptyBulletItemInterruptsParagraph:        Boolean = Parser.LISTS_EMPTY_BULLET_ITEM_INTERRUPTS_PARAGRAPH.get(options)
    var emptyOrderedItemInterruptsParagraph:       Boolean = Parser.LISTS_EMPTY_ORDERED_ITEM_INTERRUPTS_PARAGRAPH.get(options)
    var emptyOrderedNonOneItemInterruptsParagraph: Boolean = Parser.LISTS_EMPTY_ORDERED_NON_ONE_ITEM_INTERRUPTS_PARAGRAPH.get(options)

    var bulletItemInterruptsItemParagraph:        Boolean = Parser.LISTS_BULLET_ITEM_INTERRUPTS_ITEM_PARAGRAPH.get(options)
    var orderedItemInterruptsItemParagraph:       Boolean = Parser.LISTS_ORDERED_ITEM_INTERRUPTS_ITEM_PARAGRAPH.get(options)
    var orderedNonOneItemInterruptsItemParagraph: Boolean = Parser.LISTS_ORDERED_NON_ONE_ITEM_INTERRUPTS_ITEM_PARAGRAPH.get(options)

    var emptyBulletItemInterruptsItemParagraph:        Boolean = Parser.LISTS_EMPTY_BULLET_ITEM_INTERRUPTS_ITEM_PARAGRAPH.get(options)
    var emptyOrderedItemInterruptsItemParagraph:       Boolean = Parser.LISTS_EMPTY_ORDERED_ITEM_INTERRUPTS_ITEM_PARAGRAPH.get(options)
    var emptyOrderedNonOneItemInterruptsItemParagraph: Boolean = Parser.LISTS_EMPTY_ORDERED_NON_ONE_ITEM_INTERRUPTS_ITEM_PARAGRAPH.get(options)

    var emptyBulletSubItemInterruptsItemParagraph:        Boolean = Parser.LISTS_EMPTY_BULLET_SUB_ITEM_INTERRUPTS_ITEM_PARAGRAPH.get(options)
    var emptyOrderedSubItemInterruptsItemParagraph:       Boolean = Parser.LISTS_EMPTY_ORDERED_SUB_ITEM_INTERRUPTS_ITEM_PARAGRAPH.get(options)
    var emptyOrderedNonOneSubItemInterruptsItemParagraph: Boolean = Parser.LISTS_EMPTY_ORDERED_NON_ONE_SUB_ITEM_INTERRUPTS_ITEM_PARAGRAPH.get(options)

    def this() = this(Nullable.empty)

    def this(other: ItemInterrupt) = {
      this(Nullable.empty)
      bulletItemInterruptsParagraph = other.bulletItemInterruptsParagraph
      orderedItemInterruptsParagraph = other.orderedItemInterruptsParagraph
      orderedNonOneItemInterruptsParagraph = other.orderedNonOneItemInterruptsParagraph
      emptyBulletItemInterruptsParagraph = other.emptyBulletItemInterruptsParagraph
      emptyOrderedItemInterruptsParagraph = other.emptyOrderedItemInterruptsParagraph
      emptyOrderedNonOneItemInterruptsParagraph = other.emptyOrderedNonOneItemInterruptsParagraph
      bulletItemInterruptsItemParagraph = other.bulletItemInterruptsItemParagraph
      orderedItemInterruptsItemParagraph = other.orderedItemInterruptsItemParagraph
      orderedNonOneItemInterruptsItemParagraph = other.orderedNonOneItemInterruptsItemParagraph
      emptyBulletItemInterruptsItemParagraph = other.emptyBulletItemInterruptsItemParagraph
      emptyOrderedItemInterruptsItemParagraph = other.emptyOrderedItemInterruptsItemParagraph
      emptyOrderedNonOneItemInterruptsItemParagraph = other.emptyOrderedNonOneItemInterruptsItemParagraph
      emptyBulletSubItemInterruptsItemParagraph = other.emptyBulletSubItemInterruptsItemParagraph
      emptyOrderedSubItemInterruptsItemParagraph = other.emptyOrderedSubItemInterruptsItemParagraph
      emptyOrderedNonOneSubItemInterruptsItemParagraph = other.emptyOrderedNonOneSubItemInterruptsItemParagraph
    }

    def setIn(options: MutableDataHolder): Unit = {
      options.set(Parser.LISTS_BULLET_ITEM_INTERRUPTS_PARAGRAPH, bulletItemInterruptsParagraph)
      options.set(Parser.LISTS_ORDERED_ITEM_INTERRUPTS_PARAGRAPH, orderedItemInterruptsParagraph)
      options.set(Parser.LISTS_ORDERED_NON_ONE_ITEM_INTERRUPTS_PARAGRAPH, orderedNonOneItemInterruptsParagraph)
      options.set(Parser.LISTS_EMPTY_BULLET_ITEM_INTERRUPTS_PARAGRAPH, emptyBulletItemInterruptsParagraph)
      options.set(Parser.LISTS_EMPTY_ORDERED_ITEM_INTERRUPTS_PARAGRAPH, emptyOrderedItemInterruptsParagraph)
      options.set(Parser.LISTS_EMPTY_ORDERED_NON_ONE_ITEM_INTERRUPTS_PARAGRAPH, emptyOrderedNonOneItemInterruptsParagraph)
      options.set(Parser.LISTS_BULLET_ITEM_INTERRUPTS_ITEM_PARAGRAPH, bulletItemInterruptsItemParagraph)
      options.set(Parser.LISTS_ORDERED_ITEM_INTERRUPTS_ITEM_PARAGRAPH, orderedItemInterruptsItemParagraph)
      options.set(Parser.LISTS_ORDERED_NON_ONE_ITEM_INTERRUPTS_ITEM_PARAGRAPH, orderedNonOneItemInterruptsItemParagraph)
      options.set(Parser.LISTS_EMPTY_BULLET_ITEM_INTERRUPTS_ITEM_PARAGRAPH, emptyBulletItemInterruptsItemParagraph)
      options.set(Parser.LISTS_EMPTY_ORDERED_ITEM_INTERRUPTS_ITEM_PARAGRAPH, emptyOrderedItemInterruptsItemParagraph)
      options.set(Parser.LISTS_EMPTY_ORDERED_NON_ONE_ITEM_INTERRUPTS_ITEM_PARAGRAPH, emptyOrderedNonOneItemInterruptsItemParagraph)
      options.set(Parser.LISTS_EMPTY_BULLET_SUB_ITEM_INTERRUPTS_ITEM_PARAGRAPH, emptyBulletSubItemInterruptsItemParagraph)
      options.set(Parser.LISTS_EMPTY_ORDERED_SUB_ITEM_INTERRUPTS_ITEM_PARAGRAPH, emptyOrderedSubItemInterruptsItemParagraph)
      options.set(
        Parser.LISTS_EMPTY_ORDERED_NON_ONE_SUB_ITEM_INTERRUPTS_ITEM_PARAGRAPH,
        emptyOrderedNonOneSubItemInterruptsItemParagraph
      )
    }

    def canInterrupt(isNumberedItem: Boolean, isOneItem: Boolean, isEmptyItem: Boolean, isItemParagraph: Boolean): Boolean =
      if (isNumberedItem) {
        if (isOneItem) {
          if (isItemParagraph) orderedItemInterruptsItemParagraph && (!isEmptyItem || emptyOrderedItemInterruptsItemParagraph)
          else orderedItemInterruptsParagraph && (!isEmptyItem || emptyOrderedItemInterruptsParagraph)
        } else {
          if (isItemParagraph) orderedNonOneItemInterruptsItemParagraph && (!isEmptyItem || emptyOrderedNonOneItemInterruptsItemParagraph)
          else orderedNonOneItemInterruptsParagraph && (!isEmptyItem || emptyOrderedNonOneItemInterruptsParagraph)
        }
      } else {
        if (isItemParagraph) bulletItemInterruptsItemParagraph && (!isEmptyItem || emptyBulletItemInterruptsItemParagraph)
        else bulletItemInterruptsParagraph && (!isEmptyItem || emptyBulletItemInterruptsParagraph)
      }

    def canStartSubList(isNumberedItem: Boolean, isOneItem: Boolean, isEmptyItem: Boolean): Boolean =
      if (isNumberedItem) {
        orderedItemInterruptsItemParagraph && (!isEmptyItem || emptyOrderedSubItemInterruptsItemParagraph && emptyOrderedItemInterruptsItemParagraph) &&
        (isOneItem || (orderedNonOneItemInterruptsItemParagraph && (!isEmptyItem || emptyOrderedNonOneSubItemInterruptsItemParagraph && emptyOrderedNonOneItemInterruptsItemParagraph)))
      } else {
        bulletItemInterruptsItemParagraph && (!isEmptyItem || emptyBulletSubItemInterruptsItemParagraph && emptyBulletItemInterruptsItemParagraph)
      }
  }

  class MutableItemInterrupt(options: Nullable[DataHolder]) extends ItemInterrupt(options) {

    def this() = this(Nullable.empty)

    def this(other: ItemInterrupt) = {
      this(Nullable.empty)
      bulletItemInterruptsParagraph = other.bulletItemInterruptsParagraph
      orderedItemInterruptsParagraph = other.orderedItemInterruptsParagraph
      orderedNonOneItemInterruptsParagraph = other.orderedNonOneItemInterruptsParagraph
      emptyBulletItemInterruptsParagraph = other.emptyBulletItemInterruptsParagraph
      emptyOrderedItemInterruptsParagraph = other.emptyOrderedItemInterruptsParagraph
      emptyOrderedNonOneItemInterruptsParagraph = other.emptyOrderedNonOneItemInterruptsParagraph
      bulletItemInterruptsItemParagraph = other.bulletItemInterruptsItemParagraph
      orderedItemInterruptsItemParagraph = other.orderedItemInterruptsItemParagraph
      orderedNonOneItemInterruptsItemParagraph = other.orderedNonOneItemInterruptsItemParagraph
      emptyBulletItemInterruptsItemParagraph = other.emptyBulletItemInterruptsItemParagraph
      emptyOrderedItemInterruptsItemParagraph = other.emptyOrderedItemInterruptsItemParagraph
      emptyOrderedNonOneItemInterruptsItemParagraph = other.emptyOrderedNonOneItemInterruptsItemParagraph
      emptyBulletSubItemInterruptsItemParagraph = other.emptyBulletSubItemInterruptsItemParagraph
      emptyOrderedSubItemInterruptsItemParagraph = other.emptyOrderedSubItemInterruptsItemParagraph
      emptyOrderedNonOneSubItemInterruptsItemParagraph = other.emptyOrderedNonOneSubItemInterruptsItemParagraph
    }

    def setBulletItemInterruptsParagraph(v:                    Boolean): MutableItemInterrupt = { bulletItemInterruptsParagraph = v; this }
    def setOrderedItemInterruptsParagraph(v:                   Boolean): MutableItemInterrupt = { orderedItemInterruptsParagraph = v; this }
    def setOrderedNonOneItemInterruptsParagraph(v:             Boolean): MutableItemInterrupt = { orderedNonOneItemInterruptsParagraph = v; this }
    def setEmptyBulletItemInterruptsParagraph(v:               Boolean): MutableItemInterrupt = { emptyBulletItemInterruptsParagraph = v; this }
    def setEmptyOrderedItemInterruptsParagraph(v:              Boolean): MutableItemInterrupt = { emptyOrderedItemInterruptsParagraph = v; this }
    def setEmptyOrderedNonOneItemInterruptsParagraph(v:        Boolean): MutableItemInterrupt = { emptyOrderedNonOneItemInterruptsParagraph = v; this }
    def setBulletItemInterruptsItemParagraph(v:                Boolean): MutableItemInterrupt = { bulletItemInterruptsItemParagraph = v; this }
    def setOrderedItemInterruptsItemParagraph(v:               Boolean): MutableItemInterrupt = { orderedItemInterruptsItemParagraph = v; this }
    def setOrderedNonOneItemInterruptsItemParagraph(v:         Boolean): MutableItemInterrupt = { orderedNonOneItemInterruptsItemParagraph = v; this }
    def setEmptyBulletItemInterruptsItemParagraph(v:           Boolean): MutableItemInterrupt = { emptyBulletItemInterruptsItemParagraph = v; this }
    def setEmptyOrderedItemInterruptsItemParagraph(v:          Boolean): MutableItemInterrupt = { emptyOrderedItemInterruptsItemParagraph = v; this }
    def setEmptyOrderedNonOneItemInterruptsItemParagraph(v:    Boolean): MutableItemInterrupt = { emptyOrderedNonOneItemInterruptsItemParagraph = v; this }
    def setEmptyBulletSubItemInterruptsItemParagraph(v:        Boolean): MutableItemInterrupt = { emptyBulletSubItemInterruptsItemParagraph = v; this }
    def setEmptyOrderedSubItemInterruptsItemParagraph(v:       Boolean): MutableItemInterrupt = { emptyOrderedSubItemInterruptsItemParagraph = v; this }
    def setEmptyOrderedNonOneSubItemInterruptsItemParagraph(v: Boolean): MutableItemInterrupt = { emptyOrderedNonOneSubItemInterruptsItemParagraph = v; this }
  }
}
