/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/iteration/IndexedItemIterable.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package collection
package iteration

class IndexedItemIterable[R](
  private val items:          Indexed[R],
  private val isReversedFlag: Boolean = false
) extends ReversibleIndexedIterable[R] {

  override def isReversed: Boolean = isReversedFlag

  override def iterator(): ReversibleIndexedIterator[R] =
    new IndexedItemIterator[R](items, isReversedFlag)

  override def reversed(): ReversibleIndexedIterable[R] =
    new IndexedItemIterable[R](items, !isReversedFlag)

  override def reversedIterator(): ReversibleIndexedIterator[R] =
    new IndexedItemIterator[R](items, !isReversedFlag)
}
