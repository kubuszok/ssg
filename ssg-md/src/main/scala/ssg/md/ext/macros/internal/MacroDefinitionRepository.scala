/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-macros/src/main/java/com/vladsch/flexmark/ext/macros/internal/MacroDefinitionRepository.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package macros
package internal

import ssg.md.Nullable
import ssg.md.util.ast.{ KeepType, Node, NodeRepository }
import ssg.md.util.data.{ DataHolder, DataKey }

import scala.language.implicitConversions
import java.util.{ ArrayList, Comparator }

class MacroDefinitionRepository(options: DataHolder)
    extends NodeRepository[MacroDefinitionBlock](
      if (options != null) Nullable(MacrosExtension.MACRO_DEFINITIONS_KEEP.get(options)) else Nullable.empty
    ) {

  private val myReferencedMacroDefinitionBlocks = new ArrayList[MacroDefinitionBlock]()

  def addMacrosReference(macroDefinitionBlock: MacroDefinitionBlock, macros: MacroReference): Unit = {
    if (!macroDefinitionBlock.isReferenced) {
      myReferencedMacroDefinitionBlocks.add(macroDefinitionBlock)
    }

    macroDefinitionBlock.firstReferenceOffset = macros.startOffset
  }

  def resolveMacrosOrdinals(): Unit = {
    // need to sort by first referenced offset then set each to its ordinal position in the array+1
    myReferencedMacroDefinitionBlocks.sort(Comparator.comparingInt[MacroDefinitionBlock](_.firstReferenceOffset))
    var ordinal = 0
    val iter    = myReferencedMacroDefinitionBlocks.iterator()
    while (iter.hasNext) {
      val block = iter.next()
      ordinal += 1
      block.ordinal = ordinal
    }
  }

  def referencedMacroDefinitionBlocks: java.util.List[MacroDefinitionBlock] = myReferencedMacroDefinitionBlocks

  override def dataKey: DataKey[MacroDefinitionRepository] = MacrosExtension.MACRO_DEFINITIONS

  override def keepDataKey: DataKey[KeepType] = MacrosExtension.MACRO_DEFINITIONS_KEEP

  override def getReferencedElements(parent: Node): java.util.Set[MacroDefinitionBlock] = {
    val references = new java.util.HashSet[MacroDefinitionBlock]()
    visitNodes(
      parent,
      value =>
        value match {
          case ref: MacroReference =>
            val reference = ref.getReferenceNode(MacroDefinitionRepository.this)
            if (reference != null) { // @nowarn - getReferenceNode may return null
              references.add(reference)
            }
          case _ =>
        },
      classOf[MacroReference]
    )
    references
  }
}
