/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Bitfield flags for marking AST node state during compression.
 *
 * Original source: terser lib/compress/compressor-flags.js
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: has_flag -> hasFlag, set_flag -> setFlag, clear_flag -> clearFlag
 *   Convention: Object with val constants instead of exported const
 *   Idiom: Bitwise operations on AstNode.flags field
 */
package ssg
package js
package compress

import ssg.js.ast.AstNode

/** Bitfield flags stored in `AstNode.flags`.
  *
  * These are set and unset during compression, and store information in the node without requiring multiple fields.
  */
object CompressorFlags {

  // -- Persistent flags (survive between passes) --

  /** The node's value is unused. */
  val UNUSED: Int = 0x01 // 0b00000001

  /** The node is known to be truthy. */
  val TRUTHY: Int = 0x02 // 0b00000010

  /** The node is known to be falsy. */
  val FALSY: Int = 0x04 // 0b00000100

  /** The node evaluates to undefined. */
  val UNDEFINED: Int = 0x08 // 0b00001000

  /** The node has been inlined. */
  val INLINED: Int = 0x10 // 0b00010000

  /** Nodes to which values are ever written. Used when keep_assign is part of the unused option string.
    */
  val WRITE_ONLY: Int = 0x20 // 0b00100000

  // -- Per-pass flags (cleared between passes) --

  /** The node has been squeezed (simplified) in this pass. */
  val SQUEEZED: Int = 0x0100 // 0b0000000100000000

  /** The node has been optimized in this pass. */
  val OPTIMIZED: Int = 0x0200 // 0b0000001000000000

  /** The node is a top-level definition (for top_retain). */
  val TOP: Int = 0x0400 // 0b0000010000000000

  /** Mask of flags that should be cleared between compression passes. */
  val CLEAR_BETWEEN_PASSES: Int = SQUEEZED | OPTIMIZED | TOP

  /** Check whether `flag` is set on `node`. */
  def hasFlag(node: AstNode, flag: Int): Boolean =
    (node.flags & flag) != 0

  /** Set `flag` on `node`. */
  def setFlag(node: AstNode, flag: Int): Unit =
    node.flags |= flag

  /** Clear `flag` on `node`. */
  def clearFlag(node: AstNode, flag: Int): Unit =
    node.flags &= ~flag
}
