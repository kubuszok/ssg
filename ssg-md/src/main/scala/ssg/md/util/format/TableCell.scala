/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TableCell.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TableCell.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package format

// Forward reference: Node will be available when util-ast is ported
import ssg.md.util.ast.Node
import ssg.md.util.html.CellAlignment
import ssg.md.util.misc.Utils.{ maxLimit, min, minLimit }
import ssg.md.util.sequence.{ BasedSequence, PrefixedSubSequence }

import scala.language.implicitConversions

class TableCell(
  val tableCellNode:     Nullable[Node],
  val openMarker:        BasedSequence,
  val text:              BasedSequence,
  val closeMarker:       BasedSequence,
  val rowSpan:           Int,
  val columnSpan:        Int,
  val alignment:         CellAlignment,
  val trackedTextOffset: Int,
  val spanTrackedOffset: Int,
  val trackedTextAdjust: Int,
  val afterSpace:        Boolean,
  val afterDelete:       Boolean
) {

  def this(text: CharSequence, rowSpan: Int, columnSpan: Int) =
    this(
      Nullable(null),
      BasedSequence.NULL,
      BasedSequence.of(text),
      BasedSequence.NULL,
      rowSpan,
      columnSpan,
      CellAlignment.NONE,
      TableCell.NOT_TRACKED,
      TableCell.NOT_TRACKED,
      0,
      false,
      false
    )

  def this(
    tableCellNode: Nullable[Node],
    text:          CharSequence,
    rowSpan:       Int,
    columnSpan:    Int,
    alignment:     CellAlignment
  ) =
    this(
      tableCellNode,
      BasedSequence.NULL,
      BasedSequence.of(text),
      BasedSequence.NULL,
      rowSpan,
      columnSpan,
      alignment,
      TableCell.NOT_TRACKED,
      TableCell.NOT_TRACKED,
      0,
      false,
      false
    )

  def this(
    tableCellNode: Nullable[Node],
    openMarker:    CharSequence,
    text:          CharSequence,
    closeMarker:   CharSequence,
    rowSpan:       Int,
    columnSpan:    Int
  ) =
    this(
      tableCellNode,
      BasedSequence.of(openMarker),
      BasedSequence.of(text),
      BasedSequence.of(closeMarker),
      rowSpan,
      columnSpan,
      CellAlignment.NONE,
      TableCell.NOT_TRACKED,
      TableCell.NOT_TRACKED,
      0,
      false,
      false
    )

  def this(
    tableCellNode: Nullable[Node],
    openMarker:    CharSequence,
    text:          CharSequence,
    closeMarker:   CharSequence,
    rowSpan:       Int,
    columnSpan:    Int,
    alignment:     CellAlignment
  ) =
    this(
      tableCellNode,
      BasedSequence.of(openMarker), {
        val chars     = BasedSequence.of(text)
        val om        = BasedSequence.of(openMarker)
        val cm        = BasedSequence.of(closeMarker)
        val useMarker = if (om.isEmpty()) cm.subSequence(0, 0) else om.subSequence(om.length())
        if (chars.isEmpty() && (chars ne BasedSequence.NULL)) PrefixedSubSequence.prefixOf(" ", useMarker) else chars
      },
      BasedSequence.of(closeMarker),
      rowSpan,
      columnSpan,
      if (alignment != null) alignment else CellAlignment.NONE,
      TableCell.NOT_TRACKED,
      TableCell.NOT_TRACKED,
      0,
      false,
      false
    )

  def this(other: TableCell, copyNode: Boolean, rowSpan: Int, columnSpan: Int, alignment: CellAlignment) =
    this(
      if (copyNode) other.tableCellNode else Nullable(null),
      other.openMarker, {
        val useMarker = if (other.openMarker.isEmpty()) other.closeMarker.subSequence(0, 0) else other.openMarker.subSequence(other.openMarker.length())
        if (other.text eq BasedSequence.NULL) PrefixedSubSequence.prefixOf(" ", useMarker) else other.text
      },
      other.closeMarker,
      rowSpan,
      columnSpan,
      if (alignment != null) alignment else CellAlignment.NONE,
      other.trackedTextOffset,
      other.spanTrackedOffset,
      other.trackedTextAdjust,
      other.afterSpace,
      other.afterDelete
    )

  def withColumnSpan(columnSpan: Int): TableCell =
    new TableCell(
      tableCellNode,
      openMarker,
      text,
      closeMarker,
      rowSpan,
      columnSpan,
      alignment,
      trackedTextOffset,
      if (spanTrackedOffset == TableCell.NOT_TRACKED) TableCell.NOT_TRACKED else min(spanTrackedOffset, columnSpan),
      trackedTextAdjust,
      afterSpace,
      afterDelete
    )

  def withText(text: CharSequence): TableCell =
    new TableCell(
      tableCellNode,
      openMarker,
      BasedSequence.of(text),
      closeMarker,
      rowSpan,
      columnSpan,
      alignment,
      TableCell.NOT_TRACKED,
      spanTrackedOffset,
      trackedTextAdjust,
      afterSpace,
      afterDelete
    )

  def withText(
    openMarker:  CharSequence,
    text:        CharSequence,
    closeMarker: CharSequence
  ): TableCell =
    new TableCell(
      tableCellNode,
      BasedSequence.of(openMarker),
      BasedSequence.of(text),
      BasedSequence.of(closeMarker),
      rowSpan,
      columnSpan,
      alignment,
      TableCell.NOT_TRACKED,
      spanTrackedOffset,
      trackedTextAdjust,
      afterSpace,
      afterDelete
    )

  def withRowSpan(rowSpan: Int): TableCell =
    new TableCell(
      tableCellNode,
      openMarker,
      text,
      closeMarker,
      rowSpan,
      columnSpan,
      alignment,
      trackedTextOffset,
      spanTrackedOffset,
      trackedTextAdjust,
      afterSpace,
      afterDelete
    )

  def withAlignment(alignment: CellAlignment): TableCell =
    new TableCell(
      tableCellNode,
      openMarker,
      text,
      closeMarker,
      rowSpan,
      columnSpan,
      alignment,
      trackedTextOffset,
      spanTrackedOffset,
      trackedTextAdjust,
      afterSpace,
      afterDelete
    )

  def withTrackedOffset(trackedTextOffset: Int): TableCell =
    new TableCell(
      tableCellNode,
      openMarker,
      text,
      closeMarker,
      rowSpan,
      columnSpan,
      alignment,
      trackedTextOffset,
      spanTrackedOffset,
      trackedTextAdjust,
      afterSpace,
      afterDelete
    )

  def withTrackedOffset(
    trackedTextOffset: Int,
    afterSpace:        Boolean,
    afterDelete:       Boolean
  ): TableCell =
    new TableCell(
      tableCellNode,
      openMarker,
      text,
      closeMarker,
      rowSpan,
      columnSpan,
      alignment,
      trackedTextOffset,
      spanTrackedOffset,
      trackedTextAdjust,
      afterSpace,
      afterDelete
    )

  def withSpanTrackedOffset(spanTrackedOffset: Int): TableCell =
    new TableCell(
      tableCellNode,
      openMarker,
      text,
      closeMarker,
      rowSpan,
      columnSpan,
      alignment,
      trackedTextOffset,
      spanTrackedOffset,
      trackedTextAdjust,
      afterSpace,
      afterDelete
    )

  def withTrackedTextAdjust(trackedTextAdjust: Int): TableCell =
    new TableCell(
      tableCellNode,
      openMarker,
      text,
      closeMarker,
      rowSpan,
      columnSpan,
      alignment,
      trackedTextOffset,
      spanTrackedOffset,
      trackedTextAdjust,
      afterSpace,
      afterDelete
    )

  def withAfterSpace(afterSpace: Boolean): TableCell =
    new TableCell(
      tableCellNode,
      openMarker,
      text,
      closeMarker,
      rowSpan,
      columnSpan,
      alignment,
      trackedTextOffset,
      spanTrackedOffset,
      trackedTextAdjust,
      afterSpace,
      afterDelete
    )

  private[format] def getLastSegment: BasedSequence =
    if (!closeMarker.isEmpty()) closeMarker else text

  def getEndOffset: Int =
    if (!closeMarker.isEmpty()) closeMarker.endOffset else text.endOffset

  def getStartOffset(previousCell: Nullable[TableCell]): Int =
    if (previousCell.isDefined) previousCell.get.getEndOffset
    else if (!openMarker.isEmpty()) openMarker.startOffset
    else text.startOffset

  def getInsideStartOffset(previousCell: Nullable[TableCell]): Int =
    if (previousCell.isDefined) previousCell.get.getEndOffset
    else if (!openMarker.isEmpty()) openMarker.endOffset
    else text.startOffset

  def getTextStartOffset(previousCell: Nullable[TableCell]): Int =
    if (!text.isEmpty()) text.startOffset
    else if (!openMarker.isEmpty()) openMarker.endOffset + 1
    else if (previousCell.isDefined) previousCell.get.getEndOffset + 1
    else closeMarker.startOffset - 1

  def getTextEndOffset(previousCell: Nullable[TableCell]): Int =
    if (!text.isEmpty()) text.endOffset
    else if (!openMarker.isEmpty()) openMarker.endOffset + 1
    else if (previousCell.isDefined) previousCell.get.getEndOffset + 1
    else closeMarker.startOffset - 1

  def getInsideEndOffset: Int =
    if (!closeMarker.isEmpty()) closeMarker.startOffset else text.endOffset

  def getCellSize(previousCell: Nullable[TableCell]): Int =
    getEndOffset - getStartOffset(previousCell)

  def insideToTextOffset(insideOffset: Int, previousCell: Nullable[TableCell]): Int =
    maxLimit(text.length(), minLimit(insideOffset - getInsideStartOffset(previousCell) + getTextStartOffset(previousCell), 0))

  def textToInsideOffset(insideOffset: Int, previousCell: Nullable[TableCell]): Int =
    maxLimit(
      getCellSize(previousCell),
      minLimit(insideOffset - getTextStartOffset(previousCell) + getInsideStartOffset(previousCell), 0)
    )

  def isInsideCell(offset: Int, previousCell: Nullable[TableCell]): Boolean =
    offset >= getInsideStartOffset(previousCell) && offset <= getInsideEndOffset

  def isAtCell(offset: Int, previousCell: Nullable[TableCell]): Boolean =
    offset >= getInsideStartOffset(previousCell) && offset <= getInsideEndOffset

  /** Returns the cell length occupied in the table
    *
    * @param previousCell
    *   previous cell or null for first cell
    * @return
    *   length of the cell as occupied in the original file
    */
  def getCellLength(previousCell: Nullable[TableCell]): Int =
    getEndOffset - getStartOffset(previousCell)

  /** Returns the cell prefix length occupied in the table
    *
    * @param previousCell
    *   previous cell or null for first cell
    * @return
    *   length of cell's prefix before actual text as occupied in the file
    */
  def getCellPrefixLength(previousCell: Nullable[TableCell]): Int =
    getInsideStartOffset(previousCell) - getStartOffset(previousCell)

  private def dumpSequence(sequence: BasedSequence): CharSequence = {
    val sb = new StringBuilder()
    sb.append("{ \"")
      .append(sequence.replace("\"", "\\\""))
      .append("\"")
      .append(" [")
      .append(sequence.startOffset)
      .append(", ")
      .append(sequence.endOffset)
      .append("), length=")
      .append(sequence.length())
      .append("}")
    sb
  }

  override def toString: String =
    // NOTE: show not simple name but name of container class if any
    this.getClass.getSimpleName + "{" +
      "openMarker=" + dumpSequence(openMarker) +
      ", text=" + dumpSequence(text) +
      ", closeMarker=" + dumpSequence(closeMarker) +
      ", columnSpan=" + columnSpan +
      ", rowSpan=" + rowSpan +
      ", alignment=" + alignment +
      ", trackedTextOffset=" + trackedTextOffset +
      ", spanTrackedOffset=" + spanTrackedOffset +
      ", trackedTextAdjust=" + trackedTextAdjust +
      ", afterSpace=" + afterSpace +
      ", afterDelete=" + afterDelete +
      '}'
}

object TableCell {
  val NOT_TRACKED: Int = Integer.MAX_VALUE

  val NULL: TableCell = new TableCell(
    Nullable(null),
    BasedSequence.NULL,
    BasedSequence.of(" "),
    BasedSequence.NULL,
    1,
    0,
    CellAlignment.NONE,
    NOT_TRACKED,
    NOT_TRACKED,
    0,
    false,
    false
  )
  val DEFAULT_CELL: TableCell = new TableCell(
    Nullable(null),
    BasedSequence.NULL,
    BasedSequence.of(" "),
    BasedSequence.NULL,
    1,
    1,
    CellAlignment.NONE,
    NOT_TRACKED,
    NOT_TRACKED,
    0,
    false,
    false
  )
}
