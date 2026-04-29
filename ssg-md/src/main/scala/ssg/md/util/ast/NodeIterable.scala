/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/NodeIterable.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/NodeIterable.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package ast

import ssg.md.Nullable
import ssg.md.util.collection.iteration.ReversiblePeekingIterable
import ssg.md.util.collection.iteration.ReversiblePeekingIterator

import java.util.function.Consumer

class NodeIterable(val firstNode: Node, val lastNode: Node, val _reversed: Boolean) extends ReversiblePeekingIterable[Node] {

  override def iterator(): ReversiblePeekingIterator[Node] =
    new NodeIterator(firstNode, Nullable(lastNode), _reversed)

  override def forEach(consumer: Consumer[? >: Node]): Unit = {
    val it = iterator()
    while (it.hasNext)
      consumer.accept(it.next())
  }

  override def reversed(): ReversiblePeekingIterable[Node] =
    new NodeIterable(firstNode, lastNode, !_reversed)

  override def isReversed: Boolean = _reversed

  override def reversedIterator(): ReversiblePeekingIterator[Node] =
    new NodeIterator(firstNode, Nullable(lastNode), !_reversed)
}

object NodeIterable {
  val EMPTY: ReversiblePeekingIterable[Node] = new ReversiblePeekingIterable[Node] {
    override def iterator():                             ReversiblePeekingIterator[Node] = NodeIterator.EMPTY
    override def reversed():                             ReversiblePeekingIterable[Node] = this
    override def forEach(consumer: Consumer[? >: Node]): Unit                            = {}
    override def isReversed:                             Boolean                         = false
    override def reversedIterator():                     ReversiblePeekingIterator[Node] = NodeIterator.EMPTY
  }
}
