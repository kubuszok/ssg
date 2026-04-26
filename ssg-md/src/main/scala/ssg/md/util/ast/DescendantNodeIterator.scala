/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/DescendantNodeIterator.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/DescendantNodeIterator.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package ast

import ssg.md.Nullable
import ssg.md.util.collection.iteration.ReversiblePeekingIterator

import scala.collection.mutable
import java.util.function.Consumer

class DescendantNodeIterator(iterator: ReversiblePeekingIterator[Node]) extends ReversiblePeekingIterator[Node] {

  private val _isReversed: Boolean                         = iterator.isReversed
  private var _iterator:   ReversiblePeekingIterator[Node] =
    iterator match {
      case d: DescendantNodeIterator => d._iterator
      case _ => iterator
    }
  private var iteratorStack: Nullable[mutable.Stack[ReversiblePeekingIterator[Node]]] = Nullable.empty
  private var result:        Nullable[Node]                                           = Nullable.empty

  override def isReversed: Boolean = _isReversed

  override def hasNext: Boolean = _iterator.hasNext

  override def next(): Node = {
    result = Nullable(_iterator.next())
    val r = result.get

    if (r.firstChild.isDefined) {
      // push the current iterator on to the stack and make the node's children the iterator
      if (_iterator.hasNext) {
        if (iteratorStack.isEmpty) {
          iteratorStack = Nullable(new mutable.Stack[ReversiblePeekingIterator[Node]]())
        }
        iteratorStack.get.push(_iterator)
      }

      _iterator = if (_isReversed) r.reversedChildIterator else r.childIterator
    } else {
      // see if need to pop an iterator
      if (iteratorStack.isDefined && iteratorStack.get.nonEmpty && !_iterator.hasNext) {
        // pop a new iterator off the stack
        _iterator = iteratorStack.get.pop()
      }
    }

    r
  }

  override def peek: Nullable[Node] = _iterator.peek

  override def remove(): Unit = {
    if (result.isEmpty) {
      throw new IllegalStateException("Either next() was not called yet or the node was removed")
    }
    result.get.unlink()
    result = Nullable.empty
  }

  override def forEachRemaining(consumer: Consumer[? >: Node]): Unit =
    while (hasNext)
      consumer.accept(next())
}
