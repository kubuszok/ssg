/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/blocks/Ifchanged.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.blocks → ssg.liquid.blocks
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/blocks/Ifchanged.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package blocks

import ssg.liquid.nodes.LNode

import java.util.{ Map => JMap, Objects }

/** Renders content only if the value has changed since the last call. */
class Ifchanged extends Block {

  override def render(context: TemplateContext, nodes: Array[LNode]): Any =
    if (nodes == null || nodes.length == 0) {
      null
    } else {
      // Compare strings, so we can match (int)1 == "1"
      val rendered = String.valueOf(nodes(0).render(context))
      val registryMap: JMap[String, Any] = context.getRegistry(TemplateContext.REGISTRY_IFCHANGED)
      if (!Objects.equals(rendered, String.valueOf(registryMap.get(TemplateContext.REGISTRY_IFCHANGED)))) {
        registryMap.put(TemplateContext.REGISTRY_IFCHANGED, rendered)
        rendered
      } else {
        null
      }
    }
}
