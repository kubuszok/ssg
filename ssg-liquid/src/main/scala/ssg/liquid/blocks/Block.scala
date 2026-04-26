/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/blocks/Block.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.blocks → ssg.liquid.blocks
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/blocks/Block.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package blocks

/** Blocks are tags that wrap a section of template content. */
abstract class Block(_name: String) extends Insertion(_name) {

  def this() =
    this(null)
}
