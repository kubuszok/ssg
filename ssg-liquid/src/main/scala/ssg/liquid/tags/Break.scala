/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/tags/Break.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.tags → ssg.liquid.tags
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/tags/Break.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package tags

import ssg.liquid.nodes.LNode

class Break extends Tag {

  override def render(context: TemplateContext, nodes: Array[LNode]): Any = LValue.BREAK
}
