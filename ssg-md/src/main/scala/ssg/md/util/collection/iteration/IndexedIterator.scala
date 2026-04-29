/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/iteration/IndexedIterator.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/iteration/IndexedIterator.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package collection
package iteration

import java.util.ConcurrentModificationException
import java.util.NoSuchElementException
import java.util.function.Consumer

class IndexedIterator[R, S, I <: ReversibleIterator[Int]](
  private val items: Indexed[S],
  private val iter:  I
) extends ReversibleIndexedIterator[R] {

  private var lastIndex: Int = -1
  private var modCount:  Int = items.modificationCount

  override def isReversed: Boolean = iter.isReversed

  override def hasNext: Boolean = iter.hasNext

  override def next(): R = {
    if (modCount != items.modificationCount) {
      throw new ConcurrentModificationException()
    }

    lastIndex = iter.next()
    // noinspection unchecked
    items.get(lastIndex).asInstanceOf[R]
  }

  override def remove(): Unit = {
    if (lastIndex == -1) {
      throw new NoSuchElementException()
    }

    if (modCount != items.modificationCount) {
      throw new ConcurrentModificationException()
    }

    items.removeAt(lastIndex)
    lastIndex = -1
    modCount = items.modificationCount
  }

  override def index: Int = {
    if (lastIndex < 0) {
      throw new NoSuchElementException()
    }
    lastIndex
  }

  override def forEachRemaining(consumer: Consumer[? >: R]): Unit =
    while (hasNext)
      consumer.accept(next())
}
