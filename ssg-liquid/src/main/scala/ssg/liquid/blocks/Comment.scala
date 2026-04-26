/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/blocks/Comment.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.blocks → ssg.liquid.blocks
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/blocks/Comment.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package blocks

import ssg.liquid.nodes.LNode

/** Block tag, comments out the text in the block. */
class Comment extends Block {

  override def render(context: TemplateContext, nodes: Array[LNode]): Any = ""
}
