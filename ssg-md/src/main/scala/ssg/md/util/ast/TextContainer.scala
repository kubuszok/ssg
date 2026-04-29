/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/TextContainer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/TextContainer.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package ast

import ssg.md.util.misc.{ BitField, BitFieldSet, EnumBitField }
import ssg.md.util.sequence.BasedSequence
import ssg.md.util.sequence.builder.ISequenceBuilder

trait TextContainer {

  /** Append node's text
    *
    * @param out
    *   sequence build to which to append text
    * @param flags
    *   collection flags
    * @param nodeVisitor
    *   node visitor to use to visit children
    * @return
    *   true if child nodes should be visited
    */
  def collectText(out: ISequenceBuilder[? <: ISequenceBuilder[?, BasedSequence], BasedSequence], flags: Int, nodeVisitor: NodeVisitor): Boolean

  /** Append node's text ending, after any child nodes have been visited. The default implementation does nothing.
    *
    * @param out
    *   sequence build to which to append text
    * @param flags
    *   collection flags
    * @param nodeVisitor
    *   node visitor to use to visit children
    */
  def collectEndText(out: ISequenceBuilder[? <: ISequenceBuilder[?, BasedSequence], BasedSequence], flags: Int, nodeVisitor: NodeVisitor): Unit = {}
}

object TextContainer {

  enum Flags(val bitsValue: Int) extends java.lang.Enum[Flags] with BitField {
    case LINK_TEXT_TYPE extends Flags(3)
    case NODE_TEXT extends Flags(1) // not unescaped and not percent decoded
    case FOR_HEADING_ID extends Flags(1) // text for heading ID
    case NO_TRIM_REF_TEXT_START extends Flags(1) // don't trim ref text start
    case NO_TRIM_REF_TEXT_END extends Flags(1) // don't trim ref text end
    case ADD_SPACES_BETWEEN_NODES extends Flags(1) // when appending text from different nodes, ensure there is at least one space

    override def bits: Int = bitsValue
  }

  given EnumBitField[Flags] with {
    def elementType: Class[Flags] = classOf[Flags]
    def typeName:    String       = "Flags"
    val values:      Array[Flags] = Flags.values
    val bitMasks:    Array[Long]  = EnumBitField.computeBitMasks(values, "Flags")
  }

  val F_LINK_TEXT_TYPE: Int = BitFieldSet.intMask(Flags.LINK_TEXT_TYPE)
  val F_LINK_TEXT:      Int = 0 // use link text
  val F_LINK_PAGE_REF:  Int = 1 // use page ref
  val F_LINK_ANCHOR:    Int = 2 // use link anchor
  val F_LINK_URL:       Int = 3 // use link URL
  val F_LINK_NODE_TEXT: Int = 4 // use node text

  val F_NODE_TEXT:                Int = BitFieldSet.intMask(Flags.NODE_TEXT)
  val F_FOR_HEADING_ID:           Int = BitFieldSet.intMask(Flags.FOR_HEADING_ID)
  val F_NO_TRIM_REF_TEXT_START:   Int = BitFieldSet.intMask(Flags.NO_TRIM_REF_TEXT_START)
  val F_NO_TRIM_REF_TEXT_END:     Int = BitFieldSet.intMask(Flags.NO_TRIM_REF_TEXT_END)
  val F_ADD_SPACES_BETWEEN_NODES: Int = BitFieldSet.intMask(Flags.ADD_SPACES_BETWEEN_NODES)
}
