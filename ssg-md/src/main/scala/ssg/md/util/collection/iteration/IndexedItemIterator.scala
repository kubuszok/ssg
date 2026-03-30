/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/iteration/IndexedItemIterator.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package collection
package iteration

import java.util.ConcurrentModificationException
import java.util.NoSuchElementException
import java.util.function.Consumer

class IndexedItemIterator[R](
  private val items:          Indexed[R],
  private val isReversedFlag: Boolean = false
) extends ReversibleIndexedIterator[R] {

  private var nextIndex: Int = {
    val n = if (isReversedFlag) items.size - 1 else 0
    // empty forward iterator has no next
    if (n >= items.size) -1 else n
  }

  private var lastIndex: Int = -1
  private var modCount:  Int = items.modificationCount

  override def isReversed: Boolean = isReversedFlag

  override def hasNext: Boolean = nextIndex != -1

  override def next(): R = {
    if (modCount != items.modificationCount) {
      throw new ConcurrentModificationException()
    }

    if (nextIndex == -1) {
      throw new NoSuchElementException()
    }

    lastIndex = nextIndex
    nextIndex =
      if (isReversedFlag) {
        if (nextIndex <= 0) -1 else nextIndex - 1
      } else {
        if (nextIndex == items.size - 1) -1 else nextIndex + 1
      }
    items.get(lastIndex)
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
