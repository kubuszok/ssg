/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/HtmlEntity.java
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

/** Inline HTML element.
  *
  * @see
  *   <a href="http://spec.commonmark.org/0.24/#raw-html">CommonMark Spec</a>
  */
class HtmlEntity extends Node with TextContainer {

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  override def astExtra(out: StringBuilder): Unit =
    if (!chars.isEmpty) out.append(" \"").append(chars).append("\"")

  // TODO: add opening and closing marker with intermediate text so that completions can be easily done
  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS

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
}
