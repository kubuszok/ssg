/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/blocks/Cycle.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/blocks/Cycle.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package blocks

import ssg.data.DataView
import ssg.liquid.nodes.LNode

import java.util.{ ArrayList, List => JList, Map => JMap }

class Cycle extends Block {

  override def render(context: TemplateContext, nodes: Array[LNode]): DataView = {
    val elements = new ArrayList[DataView]()
    var i        = 1
    while (i < nodes.length) {
      elements.add(nodes(i).render(context))
      i += 1
    }

    val groupName =
      if (nodes(0) == null) {
        val sb = new StringBuilder()
        var j  = 0
        while (j < elements.size()) {
          sb.append(asString(elements.get(j), context))
          j += 1
        }
        sb.toString()
      } else asString(nodes(0).render(context), context)

    val cycleRegistry: JMap[String, Any] = context.getRegistry(TemplateContext.REGISTRY_CYCLE)

    val obj = cycleRegistry.remove(groupName)

    val group =
      if (obj == null) new Cycle.CycleGroup(elements.size())
      else obj.asInstanceOf[Cycle.CycleGroup]

    cycleRegistry.put(groupName, group)

    group.next(elements)
  }
}

object Cycle {

  final private class CycleGroup(private val sizeFirstCycle: Int) {
    private var currentIndex: Int = 0

    def next(elements: JList[DataView]): DataView = {
      val obj =
        if (currentIndex >= elements.size()) DataView.from("")
        else elements.get(currentIndex)

      currentIndex += 1

      if (currentIndex == sizeFirstCycle) {
        currentIndex = 0
      }

      obj
    }
  }
}
