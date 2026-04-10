/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/ListItem.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast

import ssg.md.Nullable
import ssg.md.parser.ListOptions
import ssg.md.util.ast.BlankLineContainer
import ssg.md.util.ast.Block
import ssg.md.util.ast.BlockContent
import ssg.md.util.ast.Node
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

import java.{ util => ju }

abstract class ListItem extends Block, ParagraphItemContainer, BlankLineContainer, ParagraphContainer {
  var openingMarker:                       BasedSequence = BasedSequence.NULL
  private var _markerSuffix:               BasedSequence = BasedSequence.NULL
  private var _tight:                      Boolean       = true
  private var _hadBlankAfterItemParagraph: Boolean       = false
  private var _containsBlankLine:          Boolean       = false
  private var _priority:                   Int           = Int.MinValue

  def this(other: ListItem) = {
    this()
    this.openingMarker = other.openingMarker
    this._markerSuffix = other._markerSuffix
    this._tight = other._tight
    this._hadBlankAfterItemParagraph = other._hadBlankAfterItemParagraph
    this._containsBlankLine = other._containsBlankLine
    this._priority = other._priority

    takeChildren(other)
    setCharsFromContent()
  }

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(chars: BasedSequence, segments: ju.List[BasedSequence]) = {
    this()
    this.chars = chars
    this.lineSegments = segments
  }

  def this(blockContent: BlockContent) = {
    this()
    setContent(blockContent)
  }

  def isOrderedItem: Boolean = false

  override def astExtra(out: StringBuilder): Unit = {
    Node.segmentSpanChars(out, openingMarker, "open")
    Node.segmentSpanChars(out, markerSuffix, "openSuffix")
    if (isTight) out.append(" isTight")
    else out.append(" isLoose")
    if (isHadBlankAfterItemParagraph) out.append(" hadBlankLineAfter")
    else if (isContainsBlankLine) out.append(" hadBlankLine")
  }

  override def segments: Array[BasedSequence] =
    Array(openingMarker, markerSuffix)

  def canChangeMarker: Boolean = true

  def priority:                  Int  = _priority
  def priority_=(priority: Int): Unit = _priority = priority

  def markerSuffix: BasedSequence = _markerSuffix

  def markerSuffix_=(markerSuffix: BasedSequence): Unit = {
    assert(markerSuffix.isNull || openingMarker.getBase == markerSuffix.getBase)
    _markerSuffix = markerSuffix
  }

  def tight_=(tight: Boolean): Unit = _tight = tight

  def loose_=(loose: Boolean): Unit = _tight = !loose

  def isTight: Boolean = _tight && isInTightList

  def isOwnTight: Boolean = _tight

  def isLoose: Boolean = !isTight

  override def isParagraphEndWrappingDisabled(node: Paragraph): Boolean =
    (!firstChild.contains(node) && lastChild.contains(node)) || (firstChild.contains(node) && (parent.isEmpty || parent.get.lastChildAny(classOf[ListItem]).contains(this)))

  override def isParagraphStartWrappingDisabled(node: Paragraph): Boolean =
    isItemParagraph(node)

  override def isParagraphInTightListItem(node: Paragraph): Boolean =
    if (!isTight) false
    else isItemParagraph(node)

  override def isItemParagraph(node: Paragraph): Boolean = {
    // see if this is the first paragraph child item
    var child = firstChild
    while (child.isDefined && !child.get.isInstanceOf[Paragraph]) child = child.get.next
    child.contains(node)
  }

  override def isParagraphWrappingDisabled(node: Paragraph, listOptions: ListOptions, options: DataHolder): Boolean = {
    assert(node.parent.contains(this))
    listOptions.isInTightListItem(node)
  }

  def isInTightList: Boolean =
    parent match {
      case lb: ListBlock => lb.isTight
      case _ => true
    }

  def isHadBlankAfterItemParagraph: Boolean = _hadBlankAfterItemParagraph

  def isContainsBlankLine: Boolean = _containsBlankLine

  def containsBlankLine_=(containsBlankLine: Boolean): Unit =
    _containsBlankLine = containsBlankLine

  def hadBlankAfterItemParagraph_=(hadBlankAfterItemParagraph: Boolean): Unit =
    _hadBlankAfterItemParagraph = hadBlankAfterItemParagraph

  override def lastBlankLineChild: Nullable[Node] = lastChild
}
