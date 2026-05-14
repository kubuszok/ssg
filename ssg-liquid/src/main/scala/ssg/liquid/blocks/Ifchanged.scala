/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/blocks/Ifchanged.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/blocks/Ifchanged.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package blocks

import ssg.data.DataView
import ssg.liquid.nodes.LNode

import java.util.{ Map => JMap, Objects }

class Ifchanged extends Block {

  override def render(context: TemplateContext, nodes: Array[LNode]): DataView =
    if (nodes == null || nodes.length == 0) {
      DataView.nil
    } else {
      val rendered = nodes(0).render(context).toString
      val registryMap: JMap[String, Any] = context.getRegistry(TemplateContext.REGISTRY_IFCHANGED)
      if (!Objects.equals(rendered, String.valueOf(registryMap.get(TemplateContext.REGISTRY_IFCHANGED)))) {
        registryMap.put(TemplateContext.REGISTRY_IFCHANGED, rendered)
        DataView.from(rendered)
      } else {
        DataView.nil
      }
    }
}
