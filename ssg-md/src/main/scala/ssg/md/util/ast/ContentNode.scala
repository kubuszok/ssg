/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/ContentNode.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/ContentNode.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package ast

import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.SegmentedSequence

import java.{ util => ju }

abstract class ContentNode extends Node, Content {

  protected var lineSegments: ju.List[BasedSequence] = BasedSequence.EMPTY_LIST

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(chars: BasedSequence, lineSegments: ju.List[BasedSequence]) = {
    this()
    this.chars = chars
    this.lineSegments = lineSegments
  }

  def this(lineSegments: ju.List[BasedSequence]) = {
    this()
    val sc = ContentNode.getSpanningChars(lineSegments)
    this.chars = sc
    this.lineSegments = lineSegments
  }

  def this(blockContent: BlockContent) = {
    this()
    val sc = blockContent.spanningChars
    this.chars = sc
    this.lineSegments = blockContent.lines
  }

  def setContent(chars: BasedSequence, lineSegments: ju.List[BasedSequence]): Unit = {
    this.chars = chars
    this.lineSegments = lineSegments
  }

  def setContent(lineSegments: ju.List[BasedSequence]): Unit = {
    this.lineSegments = lineSegments
    this.chars = spanningChars
  }

  def setContent(blockContent: BlockContent): Unit = {
    this.chars = blockContent.spanningChars
    this.lineSegments = blockContent.lines
  }

  override def spanningChars: BasedSequence = ContentNode.getSpanningChars(lineSegments)

  override def lineCount: Int = lineSegments.size

  override def lineChars(index: Int): BasedSequence = lineSegments.get(index)

  override def contentLines: ju.List[BasedSequence] = lineSegments

  override def contentLines(startLine: Int, endLine: Int): ju.List[BasedSequence] = lineSegments.subList(startLine, endLine)

  override def contentChars: BasedSequence =
    if (lineSegments.isEmpty) BasedSequence.NULL
    else SegmentedSequence.create(lineSegments.get(0), lineSegments)

  override def contentChars(startLine: Int, endLine: Int): BasedSequence =
    if (lineSegments.isEmpty) BasedSequence.NULL
    else SegmentedSequence.create(lineSegments.get(0), contentLines(startLine, endLine))

  def contentLines_=(contentLines: ju.List[BasedSequence]): Unit =
    lineSegments = contentLines

  def setContentLine(lineIndex: Int, contentLine: BasedSequence): Unit = {
    val lines = new ju.ArrayList[BasedSequence](lineSegments)
    lines.set(lineIndex, contentLine)
    lineSegments = lines
    setCharsFromContent()
  }
}

object ContentNode {
  private def getSpanningChars(lineSegments: ju.List[BasedSequence]): BasedSequence =
    if (lineSegments.isEmpty) BasedSequence.NULL
    else lineSegments.get(0).baseSubSequence(lineSegments.get(0).startOffset, lineSegments.get(lineSegments.size - 1).endOffset)
}
