/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/NodeIterator.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package ast

import ssg.md.Nullable
import ssg.md.util.collection.iteration.ReversiblePeekingIterator

import java.util.NoSuchElementException
import java.util.function.Consumer

class NodeIterator(val firstNode: Node, val lastNode: Nullable[Node], val reversed: Boolean) extends ReversiblePeekingIterator[Node] {

  if (firstNode == null) throw new NullPointerException()

  private var node:   Nullable[Node] = if (reversed) lastNode else Nullable(firstNode)
  private var result: Nullable[Node] = Nullable.empty

  /** @param firstNode
    *   node from which to start the iteration and continue until all sibling nodes have been traversed
    */
  def this(firstNode: Node) =
    this(firstNode, Nullable.empty, false)

  /** @param firstNode
    *   node from which to start the iteration and continue until all sibling nodes have been traversed
    * @param reversed
    *   true/false if the nodes are to be traversed in reverse order
    */
  def this(firstNode: Node, reversed: Boolean) =
    this(firstNode, Nullable.empty, reversed)

  /** @param firstNode
    *   node from which to start the iteration and continue until all sibling nodes have been traversed or lastNode has been traversed
    * @param lastNode
    *   the last node to be traversed
    */
  def this(firstNode: Node, lastNode: Node) =
    this(firstNode, Nullable(lastNode), false)

  /** @return
    *   true if the iterator is a reversed iterator
    */
  override def isReversed: Boolean = reversed

  /** @return
    *   true if there is a next node
    */
  override def hasNext: Boolean = node.isDefined

  /** @return
    *   the next node for the iterator
    */
  override def next(): Node = {
    result = Nullable.empty

    if (node.isEmpty) {
      throw new NoSuchElementException()
    }

    result = node
    val r = result.get
    node = if (reversed) r.previous else r.next
    if (node.isEmpty || (r eq (if (reversed) firstNode else lastNode.getOrElse(null.asInstanceOf[Node])))) {
      node = Nullable.empty
    }
    r
  }

  /** @return
    *   the node which would be returned by a call to next() or null if there is no next node.
    */
  override def peek: Nullable[Node] = node

  /** Remove the last node returned by next()
    */
  override def remove(): Unit = {
    if (result.isEmpty) {
      throw new IllegalStateException("Either next() was not called yet or the node was removed")
    }
    result.get.unlink()
    result = Nullable.empty
  }

  override def forEachRemaining(consumer: Consumer[? >: Node]): Unit = {
    if (consumer == null) throw new NullPointerException()
    while (hasNext)
      consumer.accept(next())
  }
}

object NodeIterator {
  val EMPTY: ReversiblePeekingIterator[Node] = new ReversiblePeekingIterator[Node] {
    override def remove():   Unit           = {}
    override def isReversed: Boolean        = false
    override def hasNext:    Boolean        = false
    override def next():     Node           = throw new NoSuchElementException()
    override def peek:       Nullable[Node] = Nullable.empty
  }
}
