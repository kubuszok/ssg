/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/iteration/Reverse.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-collection/src/main/java/com/vladsch/flexmark/util/collection/iteration/Reverse.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package collection
package iteration

import java.util.List as JList

class Reverse[T](
  private val list:           JList[T],
  private val isReversedFlag: Boolean = true
) extends ReversibleIterable[T] {

  override def iterator(): ReversibleIterator[T] =
    new Reverse.ReversedListIterator[T](list, isReversedFlag)

  override def reversed(): ReversibleIterable[T] =
    new Reverse[T](list, !isReversedFlag)

  override def isReversed: Boolean = isReversedFlag

  override def reversedIterator(): ReversibleIterator[T] =
    new Reverse.ReversedListIterator[T](list, !isReversedFlag)
}

object Reverse {

  private class ReversedListIterator[T](
    private val list:           JList[T],
    private val isReversedFlag: Boolean
  ) extends ReversibleIterator[T] {

    private var idx: Int =
      if (isReversedFlag) {
        if (list.size() == 0) -1 else list.size() - 1
      } else {
        if (list.size() == 0) -1 else 0
      }

    override def isReversed: Boolean = isReversedFlag

    override def remove(): Unit = {
      // no-op in original
    }

    override def hasNext: Boolean = idx != -1

    override def next(): T = {
      val t = list.get(idx)
      if (idx != -1) {
        if (isReversedFlag) {
          idx -= 1
        } else {
          if (idx == list.size() - 1) {
            idx = -1
          } else {
            idx += 1
          }
        }
      }

      t
    }
  }
}
