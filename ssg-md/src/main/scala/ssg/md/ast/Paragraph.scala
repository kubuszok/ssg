/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/Paragraph.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast

import ssg.md.util.ast.Block
import ssg.md.util.ast.BlockContent
import ssg.md.util.ast.Node
import ssg.md.util.ast.NodeVisitor
import ssg.md.util.ast.TextContainer
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.builder.ISequenceBuilder

import java.{ util => ju }

import scala.language.implicitConversions

class Paragraph extends Block, TextContainer {

  private var _lineIndents: Array[Int] = Paragraph.EMPTY_INDENTS
  var trailingBlankLine:    Boolean    = false
  var hasTableSeparator:    Boolean    = false

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(chars: BasedSequence, lineSegments: ju.List[BasedSequence], lineIndents: ju.List[Integer]) = {
    this()
    this.chars = chars
    this.lineSegments = lineSegments
    if (lineSegments.size != lineIndents.size) {
      throw new IllegalArgumentException("line segments and line indents have to be of the same size")
    }
    setLineIndents(lineIndents)
  }

  def this(chars: BasedSequence, lineSegments: ju.List[BasedSequence], lineIndents: Array[Int]) = {
    this()
    this.chars = chars
    this.lineSegments = lineSegments
    if (lineSegments.size != lineIndents.length) {
      throw new IllegalArgumentException("line segments and line indents have to be of the same size")
    }
    _lineIndents = lineIndents
  }

  def this(blockContent: BlockContent) = {
    this()
    setContent(blockContent)
    setLineIndents(blockContent.lineIndents)
  }

  protected def setLineIndents(lineIndents: ju.List[Integer]): Unit = {
    _lineIndents = new Array[Int](lineIndents.size)
    var i    = 0
    val iter = lineIndents.iterator()
    while (iter.hasNext) {
      _lineIndents(i) = iter.next()
      i += 1
    }
  }

  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS

  override def astExtra(out: StringBuilder): Unit = {
    super.astExtra(out)
    if (trailingBlankLine) out.append(" isTrailingBlankLine")
  }

  // FIX: add indent tracking then deprecate. ContentNode does not have indents
  override def setContent(chars: BasedSequence, lineSegments: ju.List[BasedSequence]): Unit =
    super.setContent(chars, lineSegments)

  def setContent(chars: BasedSequence, lineSegments: ju.List[BasedSequence], lineIndents: ju.List[Integer]): Unit = {
    super.setContent(chars, lineSegments)
    if (lineSegments.size != lineIndents.size) {
      throw new IllegalArgumentException("line segments and line indents have to be of the same size")
    }
    setLineIndents(lineIndents)
  }

  // FIX: add indent tracking then deprecate. ContentNode does not have indents
  override def setContent(lineSegments: ju.List[BasedSequence]): Unit =
    super.setContent(lineSegments)

  override def setContent(blockContent: BlockContent): Unit = {
    super.setContent(blockContent)
    setLineIndents(blockContent.lineIndents)
  }

  def setContent(blockContent: BlockContent, startLine: Int, endLine: Int): Unit = {
    super.setContent(blockContent.lines.subList(startLine, endLine))
    setLineIndents(blockContent.lineIndents.subList(startLine, endLine))
  }

  def setContent(other: Paragraph, startLine: Int, endLine: Int): Unit = {
    super.setContent(other.contentLines(startLine, endLine))
    if (endLine > startLine) {
      val lineIndents = new Array[Int](endLine - startLine)
      System.arraycopy(other._lineIndents, startLine, lineIndents, 0, endLine - startLine)
      _lineIndents = lineIndents
    } else {
      _lineIndents = Paragraph.EMPTY_INDENTS
    }
  }

  def lineIndents_=(lineIndents: Array[Int]): Unit =
    _lineIndents = lineIndents

  def getLineIndent(line: Int): Int = _lineIndents(line)

  def lineIndents: Array[Int] = _lineIndents

  override def collectText(out: ISequenceBuilder[? <: ISequenceBuilder[?, BasedSequence], BasedSequence], flags: Int, nodeVisitor: NodeVisitor): Boolean =
    true

  override def collectEndText(out: ISequenceBuilder[? <: ISequenceBuilder[?, BasedSequence], BasedSequence], flags: Int, nodeVisitor: NodeVisitor): Unit =
    if (trailingBlankLine) {
      out.add("\n")
    }
}

object Paragraph {
  private val EMPTY_INDENTS: Array[Int] = Array.empty[Int]
}
