/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/iteration/IndexedIterable.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/iteration/IndexedIterable.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package collection
package iteration

class IndexedIterable[R, S, I <: ReversibleIterable[Int]](
  private val items:    Indexed[S],
  private val iterable: ReversibleIterable[Int]
) extends ReversibleIndexedIterable[R] {

  override def isReversed: Boolean = iterable.isReversed

  override def iterator(): ReversibleIndexedIterator[R] =
    new IndexedIterator[R, S, ReversibleIterator[Int]](items, iterable.iterator())

  override def reversed(): ReversibleIndexedIterable[R] =
    new IndexedIterable[R, S, ReversibleIterable[Int]](items, iterable.reversed())

  override def reversedIterator(): ReversibleIndexedIterator[R] =
    new IndexedIterator[R, S, ReversibleIterator[Int]](items, iterable.reversedIterator())
}
