/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/iteration/ArrayIterable.java
 * Original: Copyright (c) 2015-2019 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package collection
package iteration

class ArrayIterable[T](
  private val array:          Array[T],
  private val startIndex:     Int,
  private val endIndex:       Int,
  private val isReversedFlag: Boolean
) extends ReversibleIterable[T] {

  def this(array: Array[T]) =
    this(array, 0, array.length, false)

  def this(array: Array[T], startIndex: Int) =
    this(array, startIndex, array.length, false)

  def this(array: Array[T], startIndex: Int, endIndex: Int) =
    this(array, startIndex, endIndex, false)

  override def reversed(): ReversibleIterable[T] =
    new ArrayIterable[T](array, startIndex, endIndex, !isReversedFlag)

  override def isReversed: Boolean = isReversedFlag

  override def reversedIterator(): ReversibleIterator[T] =
    new ArrayIterable.MyIterator[T](array, startIndex, endIndex, !isReversedFlag)

  override def iterator(): ReversibleIterator[T] =
    new ArrayIterable.MyIterator[T](array, startIndex, endIndex, isReversedFlag)
}

object ArrayIterable {

  def of[T](items: Array[T]): ArrayIterable[T] =
    new ArrayIterable[T](items)

  private class MyIterator[E](
    private val array:          Array[E],
    private val startIndex:     Int,
    private val endIndex:       Int,
    private val isReversedFlag: Boolean
  ) extends ReversibleIterator[E] {

    private var idx: Int = if (isReversedFlag) endIndex else startIndex

    override def hasNext: Boolean =
      if (isReversedFlag) idx >= startIndex else idx < endIndex

    override def next(): E =
      if (isReversedFlag) {
        idx -= 1
        array(idx)
      } else {
        val result = array(idx)
        idx += 1
        result
      }

    override def isReversed: Boolean = isReversedFlag
  }
}
