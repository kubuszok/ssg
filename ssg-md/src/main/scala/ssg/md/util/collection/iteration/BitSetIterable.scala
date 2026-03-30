/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/iteration/BitSetIterable.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package collection
package iteration

import java.util.BitSet

class BitSetIterable(
  private val bitSet:         BitSet,
  private val isReversedFlag: Boolean = false
) extends ReversibleIterable[Int] {

  override def isReversed: Boolean = isReversedFlag

  override def iterator(): ReversibleIterator[Int] =
    new BitSetIterator(bitSet, isReversedFlag)

  override def reversed(): ReversibleIterable[Int] =
    new BitSetIterable(bitSet, !isReversedFlag)

  override def reversedIterator(): ReversibleIterator[Int] =
    new BitSetIterator(bitSet, !isReversedFlag)
}
