/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/BlockContent.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/BlockContent.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package ast

import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.SegmentedSequence

import java.{ util => ju }

class BlockContent {
  // list of line text
  private val _lines:       ju.ArrayList[BasedSequence] = new ju.ArrayList[BasedSequence]()
  private val _lineIndents: ju.ArrayList[Integer]       = new ju.ArrayList[Integer]()

  def this(other: BlockContent, startLine: Int, lineIndent: Int) = {
    this()
    // copy content from other
    assert(other._lines.size == other._lineIndents.size, "lines and eols should be of the same size")

    if (other._lines.size > 0 && startLine < lineIndent) {
      _lines.addAll(other._lines.subList(startLine, lineIndent))
      _lineIndents.addAll(other._lineIndents.subList(startLine, lineIndent))
    }
  }

  def getLine(line: Int): BasedSequence = _lines.get(line)

  def spanningChars: BasedSequence =
    if (_lines.size > 0) _lines.get(0).baseSubSequence(_lines.get(0).startOffset, _lines.get(_lines.size - 1).endOffset)
    else BasedSequence.NULL

  def lines: ju.List[BasedSequence] = _lines

  def lineIndents: ju.List[Integer] = _lineIndents

  def lineCount: Int = _lines.size

  def startOffset: Int = if (_lines.size > 0) _lines.get(0).startOffset else -1

  def endOffset: Int = if (_lines.size > 0) _lines.get(_lines.size - 1).endOffset else -1

  def lineIndent: Int = if (_lines.size > 0) _lineIndents.get(0) else 0

  def sourceLength: Int =
    if (_lines.size > 0) _lines.get(_lines.size - 1).endOffset - _lines.get(0).startOffset
    else -1

  def add(lineWithEOL: BasedSequence, lineIndent: Int): Unit = {
    _lines.add(lineWithEOL)
    _lineIndents.add(lineIndent)
  }

  def addAll(lines: ju.List[BasedSequence], lineIndents: ju.List[Integer]): Unit = {
    assert(lines.size == lineIndents.size, "lines and lineIndents should be of the same size")
    _lines.addAll(lines)
    _lineIndents.addAll(lineIndents)
  }

  def hasSingleLine: Boolean = _lines.size == 1

  def contents: BasedSequence =
    if (_lines.size == 0) BasedSequence.NULL
    else contents(0, _lines.size)

  def subContents(startLine: Int, endLine: Int): BlockContent =
    new BlockContent(this, startLine, endLine)

  def contents(startLine: Int, endLine: Int): BasedSequence =
    if (_lines.size == 0) {
      BasedSequence.NULL
    } else {
      if (startLine < 0) {
        throw new IndexOutOfBoundsException("startLine must be at least 0")
      }
      if (endLine < 0) {
        throw new IndexOutOfBoundsException("endLine must be at least 0")
      }
      if (endLine < startLine) {
        throw new IndexOutOfBoundsException("endLine must not be less than startLine")
      }
      if (endLine > _lines.size) {
        throw new IndexOutOfBoundsException("endLine must not be greater than line cardinality")
      }

      SegmentedSequence.create(_lines.get(0), _lines.subList(startLine, endLine))
    }

  def getString: String =
    if (_lines.size == 0) {
      ""
    } else {
      val sb = new StringBuilder

      val it = _lines.iterator()
      while (it.hasNext) {
        val line = it.next()
        sb.append(line.trimEOL())
        sb.append('\n')
      }

      sb.toString()
    }
}
