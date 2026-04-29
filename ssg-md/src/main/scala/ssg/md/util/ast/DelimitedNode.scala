/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/DelimitedNode.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/DelimitedNode.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package ast

import ssg.md.util.misc.BitFieldSet
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.builder.ISequenceBuilder

trait DelimitedNode extends TextContainer {
  def openingMarker:                                 BasedSequence
  def chars:                                         BasedSequence
  def openingMarker_=(openingMarker: BasedSequence): Unit

  def text:                        BasedSequence
  def text_=(text: BasedSequence): Unit

  def closingMarker:                                 BasedSequence
  def closingMarker_=(closingMarker: BasedSequence): Unit

  override def collectText(out: ISequenceBuilder[? <: ISequenceBuilder[?, BasedSequence], BasedSequence], flags: Int, nodeVisitor: NodeVisitor): Boolean =
    if (BitFieldSet.any(flags, TextContainer.F_NODE_TEXT)) {
      out.append(text)
      false
    } else {
      true
    }
}
