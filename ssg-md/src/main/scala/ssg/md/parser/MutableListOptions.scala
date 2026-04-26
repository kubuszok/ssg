/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/MutableListOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/MutableListOptions.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser

import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class MutableListOptions private[parser] (other: ListOptions) extends ListOptions(other) {

  itemInterrupt = ListOptions.MutableItemInterrupt(super.getItemInterrupt)

  def this() =
    this(ListOptions())

  def this(options: Nullable[DataHolder]) =
    this(ListOptions.get(options.getOrElse(null.asInstanceOf[DataHolder]))) // @nowarn: DataHolder.get handles null

  override def getMutable: MutableListOptions = MutableListOptions(this)

  def setParserEmulationFamily(profile:               ParserEmulationProfile):           MutableListOptions = { myParserEmulationProfile = profile; this }
  def setItemInterrupt(interrupt:                     ListOptions.MutableItemInterrupt): MutableListOptions = { itemInterrupt = interrupt; this }
  def setAutoLoose(v:                                 Boolean):                          MutableListOptions = { autoLoose = v; this }
  def setAutoLooseOneLevelLists(v:                    Boolean):                          MutableListOptions = { autoLooseOneLevelLists = v; this }
  def setDelimiterMismatchToNewList(v:                Boolean):                          MutableListOptions = { delimiterMismatchToNewList = v; this }
  def setEndOnDoubleBlank(v:                          Boolean):                          MutableListOptions = { endOnDoubleBlank = v; this }
  def setItemMarkerSpace(v:                           Boolean):                          MutableListOptions = { itemMarkerSpace = v; this }
  def setItemTypeMismatchToNewList(v:                 Boolean):                          MutableListOptions = { itemTypeMismatchToNewList = v; this }
  def setItemTypeMismatchToSubList(v:                 Boolean):                          MutableListOptions = { itemTypeMismatchToSubList = v; this }
  def setLooseWhenPrevHasTrailingBlankLine(v:         Boolean):                          MutableListOptions = { looseWhenPrevHasTrailingBlankLine = v; this }
  def setLooseWhenLastItemPrevHasTrailingBlankLine(v: Boolean):                          MutableListOptions = { looseWhenLastItemPrevHasTrailingBlankLine = v; this }
  def setLooseWhenHasNonListChildren(v:               Boolean):                          MutableListOptions = { looseWhenHasNonListChildren = v; this }
  def setLooseWhenBlankLineFollowsItemParagraph(v:    Boolean):                          MutableListOptions = { looseWhenBlankLineFollowsItemParagraph = v; this }
  def setLooseWhenHasLooseSubItem(v:                  Boolean):                          MutableListOptions = { looseWhenHasLooseSubItem = v; this }
  def setLooseWhenHasTrailingBlankLine(v:             Boolean):                          MutableListOptions = { looseWhenHasTrailingBlankLine = v; this }
  def setLooseWhenContainsBlankLine(v:                Boolean):                          MutableListOptions = { looseWhenContainsBlankLine = v; this }
  def setNumberedItemMarkerSuffixed(v:                Boolean):                          MutableListOptions = { numberedItemMarkerSuffixed = v; this }
  def setOrderedItemDotOnly(v:                        Boolean):                          MutableListOptions = { orderedItemDotOnly = v; this }
  def setOrderedListManualStart(v:                    Boolean):                          MutableListOptions = { orderedListManualStart = v; this }
  def setCodeIndent(v:                                Int):                              MutableListOptions = { codeIndent = v; this }
  def setItemIndent(v:                                Int):                              MutableListOptions = { itemIndent = v; this }
  def setNewItemCodeIndent(v:                         Int):                              MutableListOptions = { newItemCodeIndent = v; this }
  def setItemMarkerSuffixes(v:                        Array[String]):                    MutableListOptions = { itemMarkerSuffixes = v; this }
}
