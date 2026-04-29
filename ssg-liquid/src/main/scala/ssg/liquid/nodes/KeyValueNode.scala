/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/nodes/KeyValueNode.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.nodes → ssg.liquid.nodes
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/nodes/KeyValueNode.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package nodes

import java.util.{ HashMap, Map => JMap }

class KeyValueNode(val key: String, val value: LNode) extends LNode {

  override def render(context: TemplateContext): Any = {
    val map: JMap[String, Any] = new HashMap[String, Any]()
    map.put(key, value.render(context))
    map
  }
}
