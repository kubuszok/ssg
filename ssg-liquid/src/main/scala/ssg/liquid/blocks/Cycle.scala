/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/blocks/Cycle.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.blocks → ssg.liquid.blocks
 *   Idiom: Inner class → companion object class
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/blocks/Cycle.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package blocks

import ssg.liquid.nodes.LNode

import java.util.{ ArrayList, List => JList, Map => JMap }

/** Cycle is usually used within a loop to alternate between values, like colors or DOM classes. */
class Cycle extends Block {

  override def render(context: TemplateContext, nodes: Array[LNode]): Any = {
    // collect all the variants to the list first
    val elements = new ArrayList[Any]()
    var i        = 1
    while (i < nodes.length) {
      elements.add(nodes(i).render(context))
      i += 1
    }

    // The group-name is either the first token-expression, or if that is
    // null (indicating there is no name), give it the name as stringified parameters
    val groupName =
      if (nodes(0) == null) asString(elements, context)
      else asString(nodes(0).render(context), context)

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

    def next(elements: JList[Any]): Any = {
      val obj =
        if (currentIndex >= elements.size()) ""
        else elements.get(currentIndex)

      currentIndex += 1

      if (currentIndex == sizeFirstCycle) {
        currentIndex = 0
      }

      obj
    }
  }
}
