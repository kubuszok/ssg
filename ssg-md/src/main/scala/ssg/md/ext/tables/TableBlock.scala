/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/TableBlock.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package tables

import ssg.md.Nullable
import ssg.md.util.sequence.BasedSequence

import java.util.{List as JList}
import scala.language.implicitConversions
import ssg.md.util.ast.{BlankLineBreakNode, Block, BlockContent}

/** Table block containing a [[TableHead]] and optionally a [[TableBody]]. */
class TableBlock() extends Block with BlankLineBreakNode {

  def this(chars: BasedSequence) = {
    this()
    this.chars = (chars)
  }

  def this(chars: BasedSequence, lineSegments: JList[BasedSequence]) = {
    this()
    this.chars = (chars)
    this.contentLines = (lineSegments)
  }

  def this(lineSegments: JList[BasedSequence]) = {
    this()
    setContent(lineSegments)
  }

  def this(blockContent: BlockContent) = {
    this()
    setContent(blockContent)
  }

  override def segments: Array[BasedSequence] = new Array[BasedSequence](0)

  private[tables] def caption: Nullable[TableCaption] = {
    val child = lastChild
    if (child.isDefined && child.get.isInstanceOf[TableCaption]) Nullable(child.get.asInstanceOf[TableCaption])
    else Nullable.empty
  }
}
