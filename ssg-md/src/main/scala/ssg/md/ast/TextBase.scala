/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/TextBase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast

import ssg.md.util.ast.Node
import ssg.md.util.ast.NodeVisitor
import ssg.md.util.ast.TextContainer
import ssg.md.util.misc.BitFieldSet
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.Escaping
import ssg.md.util.sequence.ReplacedTextMapper
import ssg.md.util.sequence.builder.ISequenceBuilder

import scala.language.implicitConversions

class TextBase extends Node with TextContainer {

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(chars: String) = {
    this()
    this.chars = BasedSequence.of(chars)
  }

  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS

  override def astExtra(out: StringBuilder): Unit =
    astExtraChars(out)

  override def collectText(out: ISequenceBuilder[? <: ISequenceBuilder[?, BasedSequence], BasedSequence], flags: Int, nodeVisitor: NodeVisitor): Boolean = {
    if (BitFieldSet.any(flags, TextContainer.F_NODE_TEXT)) {
      out.append(chars)
    } else {
      val textMapper = new ReplacedTextMapper(chars)
      val unescaped  = Escaping.unescape(chars, textMapper)
      out.append(unescaped)
    }
    false
  }

  override protected def toStringAttributes: String =
    "text=" + chars
}
