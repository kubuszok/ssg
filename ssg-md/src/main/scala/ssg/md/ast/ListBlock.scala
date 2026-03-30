/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/ListBlock.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast

import ssg.md.Nullable
import ssg.md.util.ast.BlankLineContainer
import ssg.md.util.ast.Block
import ssg.md.util.ast.BlockContent
import ssg.md.util.ast.Node
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

import java.{ util => ju }

abstract class ListBlock extends Block with BlankLineContainer {

  private var _tight: Boolean = false

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

  def isTight: Boolean = _tight

  def isLoose: Boolean = !_tight

  def tight_=(tight: Boolean): Unit =
    _tight = tight

  def loose_=(loose: Boolean): Unit =
    _tight = !loose

  override def lastBlankLineChild: Nullable[Node] = lastChild

  override def astExtra(out: StringBuilder): Unit = {
    super.astExtra(out)
    if (isTight) out.append(" isTight")
    else out.append(" isLoose")
  }
}
