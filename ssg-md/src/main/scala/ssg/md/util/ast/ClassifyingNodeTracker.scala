/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/ClassifyingNodeTracker.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/ClassifyingNodeTracker.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package ast

import ssg.md.Nullable
import ssg.md.util.collection.ClassificationBag
import ssg.md.util.collection.OrderedMap
import ssg.md.util.collection.OrderedSet
import ssg.md.util.collection.iteration.ReversibleIterable
import ssg.md.util.collection.iteration.ReversiblePeekingIterable

import java.util.{ BitSet, HashMap, Map, Set }

import scala.language.implicitConversions

class ClassifyingNodeTracker(
  private val host: Nullable[NodeTracker],
  initExclusionMap: Map[Class[? <: Node], Set[Class[?]]]
) extends NodeTracker {

  protected val nodeClassifier: ClassificationBag[Class[?], Node]   = new ClassificationBag[Class[?], Node]((node: Node) => node.getClass)
  private val _exclusionMap:    OrderedMap[Class[?], Set[Class[?]]] = new OrderedMap[Class[?], Set[Class[?]]](initExclusionMap.size)
  private val _exclusionSet:    OrderedSet[Class[?]]                = new OrderedSet[Class[?]]()
  private val _nodeAncestryMap: HashMap[Integer, BitSet]            = new HashMap[Integer, BitSet]()

  // initialize
  _exclusionMap.putAll(initExclusionMap)

  // this maps the exclusion class to bits in the bit set
  {
    val iterator = _exclusionMap.valueIterable().iterator()
    while (iterator.hasNext)
      _exclusionSet.addAll(iterator.next())
  }

  def exclusionMap: OrderedMap[Class[?], Set[Class[?]]] = _exclusionMap

  def nodeAncestryMap: HashMap[Integer, BitSet] = _nodeAncestryMap

  def exclusionSet: OrderedSet[Class[?]] = _exclusionSet

  def getNodeClassifier: ClassificationBag[Class[?], Node] = nodeClassifier

  private def validateLinked(node: Node): Unit =
    if (node.next.isEmpty && node.parent.isEmpty) {
      throw new IllegalStateException("Added block " + node + " is not linked into the AST")
    }

  override def nodeAdded(node: Node): Unit = {
    validateLinked(node)
    nodeClassifier.add(node)
    if (host.isDefined) host.get.nodeAdded(node)
  }

  override def nodeAddedWithChildren(node: Node): Unit = {
    validateLinked(node)
    nodeClassifier.add(node)
    addNodes(node.children)
    if (host.isDefined) host.get.nodeAddedWithChildren(node)
  }

  override def nodeAddedWithDescendants(node: Node): Unit = {
    validateLinked(node)
    nodeClassifier.add(node)
    addNodes(node.descendants)
    if (host.isDefined) host.get.nodeAddedWithDescendants(node)
  }

  private def addNodes(nodes: ReversiblePeekingIterable[Node]): Unit = {
    val it = nodes.iterator()
    while (it.hasNext)
      nodeClassifier.add(it.next())
  }

  private def validateUnlinked(node: Node): Unit =
    if (!(node.next.isEmpty && node.parent.isEmpty)) {
      throw new IllegalStateException("Removed block " + node + " is still linked in the AST")
    }

  override def nodeRemoved(node: Node): Unit =
    nodeRemovedWithDescendants(node)

  override def nodeRemovedWithChildren(node: Node): Unit =
    nodeRemovedWithDescendants(node)

  override def nodeRemovedWithDescendants(node: Node): Unit = {
    validateUnlinked(node)
    nodeClassifier.add(node)
    removeNodes(node.descendants)
    if (host.isDefined) host.get.nodeRemovedWithDescendants(node)
  }

  private def removeNodes(nodes: ReversiblePeekingIterable[Node]): Unit = {
    val it = nodes.iterator()
    while (it.hasNext)
      nodeClassifier.add(it.next())
  }

  def items: OrderedSet[Node] = nodeClassifier.getItems

  def getCategoryItems[X](nodeClass: Class[? <: X], classes: Set[Class[?]]): ReversibleIterable[X] =
    nodeClassifier.getCategoryItems(nodeClass, classes)
}
