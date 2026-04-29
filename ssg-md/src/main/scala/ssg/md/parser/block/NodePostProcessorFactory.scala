/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/block/NodePostProcessorFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/block/NodePostProcessorFactory.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package parser
package block

import ssg.md.parser.PostProcessorFactory
import ssg.md.util.ast.{ Document, Node }

import scala.collection.mutable

abstract class NodePostProcessorFactory(ignored: Boolean) extends PostProcessorFactory {

  private val nodeMap: mutable.HashMap[Class[?], Set[Class[?]]] = mutable.HashMap.empty

  override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

  override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

  final override def affectsGlobalScope: Boolean = false

  final protected def addNodeWithExclusions(nodeType: Class[? <: Node], excludeDescendantsOf: Class[?]*): Unit =
    if (excludeDescendantsOf.nonEmpty) {
      nodeMap.put(nodeType, excludeDescendantsOf.toSet)
    } else {
      addNodes(nodeType)
    }

  final protected def addNodes(nodeTypes: Class[?]*): Unit =
    for (nodeType <- nodeTypes)
      nodeMap.put(nodeType, Set.empty)

  final override def getNodeTypes: Nullable[Map[Class[?], Set[Class[?]]]] =
    Nullable(nodeMap.toMap)

  override def apply(document: Document): NodePostProcessor
}
