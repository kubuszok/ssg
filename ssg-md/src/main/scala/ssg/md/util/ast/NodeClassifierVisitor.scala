/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/NodeClassifierVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/NodeClassifierVisitor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package ast

import ssg.md.Nullable
import ssg.md.util.collection.CopyOnWriteRef
import ssg.md.util.collection.OrderedMap
import ssg.md.util.collection.OrderedSet

import java.util.{ BitSet, HashMap, Map, Set }
import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

class NodeClassifierVisitor(initExclusionMap: Map[Class[? <: Node], Set[Class[?]]]) extends NodeVisitorBase, NodeTracker {

  private val classifyingNodeTracker:  ClassifyingNodeTracker              = new ClassifyingNodeTracker(Nullable(this.asInstanceOf[NodeTracker]), initExclusionMap)
  private val _exclusionMap:           OrderedMap[Class[?], Set[Class[?]]] = classifyingNodeTracker.exclusionMap
  private val nodeAncestryMap:         HashMap[Integer, BitSet]            = classifyingNodeTracker.nodeAncestryMap
  private val exclusionSet:            OrderedSet[Class[?]]                = classifyingNodeTracker.exclusionSet
  private val nodeAncestryBitSetStack: mutable.Stack[BitSet]               = new mutable.Stack[BitSet]()
  private val nodeAncestryBitSet:      CopyOnWriteRef[BitSet]              = new CopyOnWriteRef[BitSet](new BitSet(), value => if (value != null) value.clone().asInstanceOf[BitSet] else new BitSet())

  @scala.annotation.nowarn("msg=unused") // preserved from original
  private val EMPTY_SET:            BitSet  = new BitSet()
  private var isClassificationDone: Boolean = false

  def classify(node: Node): ClassifyingNodeTracker = {
    // no double dipping
    assert(!isClassificationDone)
    visit(node)
    isClassificationDone = true
    classifyingNodeTracker
  }

  override def visit(node: Node): Unit =
    visitChildren(node)

  override def nodeRemoved(node:                Node): Unit = {}
  override def nodeRemovedWithChildren(node:    Node): Unit = {}
  override def nodeRemovedWithDescendants(node: Node): Unit = {}
  override def nodeAddedWithChildren(node:      Node): Unit = nodeAdded(node)
  override def nodeAddedWithDescendants(node:   Node): Unit = nodeAdded(node)

  override def nodeAdded(node: Node): Unit =
    if (isClassificationDone) {
      if (node.parent.isEmpty) {
        throw new IllegalStateException("Node must be inserted into the document before calling node tracker nodeAdded functions")
      }

      if (!node.parent.get.isInstanceOf[Document]) {
        val parentIndex = classifyingNodeTracker.items.indexOf(node.parent.get)
        if (parentIndex == -1) {
          throw new IllegalStateException(
            "Parent node: " + node.parent.get + " of " + node + " is not tracked, some post processor forgot to call tracker.nodeAdded()."
          )
        }

        val ancestorBitSet = nodeAncestryMap.get(parentIndex)
        nodeAncestryBitSet.value = Nullable(ancestorBitSet)
      }

      // let'er rip to update the descendants
      nodeAncestryBitSetStack.clear()
      visit(node)
    }

  private[ast] def pushNodeAncestry(): Unit =
    if (!_exclusionMap.isEmpty) {
      nodeAncestryBitSetStack.push(nodeAncestryBitSet.immutable.get)
    }

  private[ast] def popNodeAncestry(): Unit =
    nodeAncestryBitSet.value = Nullable(nodeAncestryBitSetStack.pop())

  private[ast] def updateNodeAncestry(node: Node, ancestryBitSet: CopyOnWriteRef[BitSet]): Boolean = boundary {
    if (!_exclusionMap.isEmpty && !node.isInstanceOf[Document]) {
      // add flags if needed
      var bitSet = ancestryBitSet.peek.get
      assert(bitSet != null)

      val index = classifyingNodeTracker.items.indexOf(node)
      if (index == -1) {
        throw new IllegalStateException("Node: " + node + " is not tracked, some post processor forgot to call tracker.nodeAdded().")
      }

      if (exclusionSet != null && !exclusionSet.isEmpty) {
        val iterator = exclusionSet.asInstanceOf[java.util.Set[Class[?]]].iterator()

        while (iterator.hasNext) {
          val nodeType = iterator.next()
          if (nodeType.isInstance(node)) {
            // get the index of this exclusion
            val i = exclusionSet.indexOf(nodeType)
            assert(i != -1)
            if (!bitSet.get(i)) {
              bitSet = ancestryBitSet.mutable.get
              assert(bitSet != null)
              bitSet.set(i)
            }
          }
        }
      }

      if (isClassificationDone && nodeAncestryBitSetStack.size > 1) {
        // see if we can stop
        // now store the stuff for the node index
        val oldBitSet = nodeAncestryMap.get(index)
        if (oldBitSet != null && oldBitSet.equals(bitSet)) {
          // no need to process descendants of this node
          break(false)
        }
      }

      if (!bitSet.isEmpty) {
        nodeAncestryMap.put(index, ancestryBitSet.immutable.get)
      }
    }

    true
  }

  /** Visit the child nodes.
    *
    * @param parent
    *   the parent node whose children should be visited
    */
  override def visitChildren(parent: Node): Unit = {
    if (!isClassificationDone) {
      // initial collection phase
      if (!parent.isInstanceOf[Document]) {
        classifyingNodeTracker.nodeAdded(parent)
      }
    }

    if (parent.firstChild.isDefined) {
      pushNodeAncestry()
      if (updateNodeAncestry(parent, nodeAncestryBitSet)) {
        super.visitChildren(parent)
      }
      popNodeAncestry()
    } else {
      updateNodeAncestry(parent, nodeAncestryBitSet)
    }
  }
}
