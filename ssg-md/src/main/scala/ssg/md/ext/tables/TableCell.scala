/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/TableCell.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package tables

import ssg.md.Nullable
import ssg.md.ast.{Text, WhiteSpace}
import ssg.md.ast.util.TextNodeConverter
import ssg.md.util.ast.{DelimitedNode, Node}
import ssg.md.util.html.CellAlignment
import ssg.md.util.sequence.BasedSequence

/** Table cell of a [[TableRow]] containing inline nodes. */
class TableCell() extends Node, DelimitedNode {

  var openingMarker: BasedSequence = BasedSequence.NULL
  var text: BasedSequence = BasedSequence.NULL
  var closingMarker: BasedSequence = BasedSequence.NULL
  private var _header: Boolean = false
  private var _alignment: Nullable[TableCell.Alignment] = Nullable.empty
  private var _span: Int = 1

  def this(chars: BasedSequence) = {
    this()
    this.chars = (chars)
  }

  def trimWhiteSpace(): Unit = {
    val savedFirstChild = firstChild
    var child = firstChild

    while (child.isDefined && child.get.isInstanceOf[WhiteSpace]) {
      val nextChild = child.get.next
      child.get.unlink()
      child = nextChild
    }

    child = lastChild
    while (child.isDefined && child.get.isInstanceOf[WhiteSpace]) {
      val prevChild = child.get.previous
      child.get.unlink()
      child = prevChild
    }

    if (firstChild.isEmpty && savedFirstChild.isDefined) {
      // we keep a single space from the child
      val textNode = new Text(savedFirstChild.get.chars.subSequence(0, 1))
      appendChild(textNode)
    }
  }

  def mergeWhiteSpace(): Unit = {
    var hadWhitespace = false
    var child = firstChild

    while (child.isDefined && child.get.isInstanceOf[WhiteSpace]) {
      val nextChild = child.get.next
      val textNode = new Text(child.get.chars)
      child.get.insertBefore(textNode)
      child.get.unlink()
      child = nextChild
      hadWhitespace = true
    }

    child = lastChild
    while (child.isDefined && child.get.isInstanceOf[WhiteSpace]) {
      val prevChild = child.get.previous
      val textNode = new Text(child.get.chars)
      child.get.insertBefore(textNode)
      child.get.unlink()
      child = prevChild
      hadWhitespace = true
    }

    if (hadWhitespace) {
      TextNodeConverter.mergeTextNodes(this)
    }
  }

  def span: Int = _span

  def span_=(span: Int): Unit = { _span = span }

  def header: Boolean = _header

  def header_=(header: Boolean): Unit = { _header = header }

  def alignment: Nullable[TableCell.Alignment] = _alignment

  def alignment_=(alignment: Nullable[TableCell.Alignment]): Unit = { _alignment = alignment }

  override def segments: Array[BasedSequence] = Array(openingMarker, text, closingMarker)

  override def astExtra(out: StringBuilder): Unit = {
    _alignment.foreach(a => out.append(" ").append(a))
    if (_header) out.append(" header")
    if (_span > 1) out.append(" span=" + _span)
    Node.delimitedSegmentSpanChars(out, openingMarker, text, closingMarker, "text")
  }

  /** @return whether the cell is a header or not */
  def isHeader: Boolean = _header

  /** @return the cell alignment */
  def getAlignment: Nullable[TableCell.Alignment] = _alignment
}

object TableCell {

  /** How the cell is aligned horizontally. */
  enum Alignment {
    case LEFT, CENTER, RIGHT

    def cellAlignment: CellAlignment = this match {
      case LEFT => CellAlignment.LEFT
      case CENTER => CellAlignment.CENTER
      case RIGHT => CellAlignment.RIGHT
    }
  }
}
