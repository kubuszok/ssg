/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/iteration/BitSetIterator.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/iteration/BitSetIterator.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package collection
package iteration

import java.util.BitSet
import java.util.NoSuchElementException
import java.util.function.Consumer

class BitSetIterator(
  private val bitSet:         BitSet,
  private val isReversedFlag: Boolean = false
) extends ReversibleIterator[Int] {

  private var nextIndex: Int =
    if (isReversedFlag) bitSet.previousSetBit(bitSet.length())
    else bitSet.nextSetBit(0)

  private var lastIndex: Int = -1

  override def isReversed: Boolean = isReversedFlag

  override def hasNext: Boolean = nextIndex != -1

  override def next(): Int = {
    if (nextIndex == -1) {
      throw new NoSuchElementException()
    }

    lastIndex = nextIndex
    nextIndex =
      if (isReversedFlag) {
        if (nextIndex == 0) -1 else bitSet.previousSetBit(nextIndex - 1)
      } else {
        bitSet.nextSetBit(nextIndex + 1)
      }
    lastIndex
  }

  override def remove(): Unit = {
    if (lastIndex == -1) {
      throw new NoSuchElementException()
    }

    bitSet.clear(lastIndex)
  }

  override def forEachRemaining(consumer: Consumer[? >: Int]): Unit =
    while (hasNext)
      consumer.accept(next())
}
