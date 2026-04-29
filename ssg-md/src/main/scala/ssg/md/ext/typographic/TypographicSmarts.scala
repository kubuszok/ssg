/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-typographic/src/main/java/com/vladsch/flexmark/ext/typographic/TypographicSmarts.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-typographic/src/main/java/com/vladsch/flexmark/ext/typographic/TypographicSmarts.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package typographic

import ssg.md.Nullable
import ssg.md.util.ast.{ DoNotAttributeDecorate, Node, NodeVisitor, TextContainer, TypographicText }
import ssg.md.util.misc.BitFieldSet
import ssg.md.util.sequence.{ BasedSequence, Escaping, ReplacedTextMapper }
import ssg.md.util.sequence.builder.ISequenceBuilder

/** A TypographicSmarts node */
class TypographicSmarts() extends Node, DoNotAttributeDecorate, TypographicText {

  private var _typographicText: Nullable[String] = Nullable.empty

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(typographicText: String) = {
    this()
    _typographicText = Nullable(typographicText)
  }

  def this(chars: BasedSequence, typographicText: String) = {
    this()
    this.chars = chars
    _typographicText = Nullable(typographicText)
  }

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

  override def astExtra(out: StringBuilder): Unit =
    out.append(" typographic: ").append(_typographicText.getOrElse("null")).append(" ")

  def typographicText: Nullable[String] = _typographicText

  def typographicText_=(text: String): Unit = _typographicText = Nullable(text)

  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS

  override protected def toStringAttributes: String = "text=" + chars
}
