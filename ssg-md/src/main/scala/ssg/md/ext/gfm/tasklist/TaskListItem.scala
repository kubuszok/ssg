/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-tasklist/src/main/java/com/vladsch/flexmark/ext/gfm/tasklist/TaskListItem.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-gfm-tasklist/src/main/java/com/vladsch/flexmark/ext/gfm/tasklist/TaskListItem.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package gfm
package tasklist

import ssg.md.ast.{ ListItem, OrderedListItem, Paragraph }
import ssg.md.parser.ListOptions
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

import java.util.List as JList
import ssg.md.util.ast.BlockContent

/** A Task list item */
class TaskListItem() extends ListItem {

  private var _isOrderedItem:   Boolean = false
  private var _canChangeMarker: Boolean = true

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(chars: BasedSequence, segments: JList[BasedSequence]) = {
    this()
    this.chars = chars
    this.contentLines = segments
  }

  def this(blockContent: BlockContent) = {
    this()
    setContent(blockContent)
  }

  def this(block: ListItem) = {
    this()
    // Replicate what ListItem(ListItem) does: copy fields, take children, set chars
    this._openingMarker = block.openingMarker
    this.markerSuffix = block.markerSuffix
    this.tight_=(block.isOwnTight)
    this.hadBlankAfterItemParagraph_=(block.isHadBlankAfterItemParagraph)
    this.containsBlankLine_=(block.isContainsBlankLine)
    this.priority = block.priority
    takeChildren(block)
    setCharsFromContent()
    _isOrderedItem = block.isInstanceOf[OrderedListItem]
  }

  override def astExtra(out: StringBuilder): Unit = {
    super.astExtra(out)
    if (_isOrderedItem) out.append(" isOrderedItem")
    out.append(if (isItemDoneMarker) " isDone" else " isNotDone")
  }

  override def isParagraphWrappingDisabled(node: Paragraph, listOptions: ListOptions, options: DataHolder): Boolean = {
    assert(node.parent.isDefined && node.parent.contains(this))

    // see if this is the first paragraph child item we handle our own paragraph wrapping for that one
    var child = firstChild
    while (child.isDefined && !child.get.isInstanceOf[Paragraph]) child = child.get.next
    child.contains(node)
  }

  override def openingMarker_=(v: BasedSequence): Unit =
    throw new IllegalStateException("openingMarker is immutable on TaskListItem — set during construction only")

  def isItemDoneMarker: Boolean = !markerSuffix.matches("[ ]")

  override def isOrderedItem: Boolean = _isOrderedItem

  def isOrderedItem_=(orderedItem: Boolean): Unit = _isOrderedItem = orderedItem

  override def canChangeMarker: Boolean = _canChangeMarker

  def canChangeMarker_=(canChangeMarker: Boolean): Unit = _canChangeMarker = canChangeMarker
}
