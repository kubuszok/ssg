/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/SegmentOptimizer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/SegmentOptimizer.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package sequence
package builder

/** Optimize segment BASE parts surrounding TEXT contained in Object[] array.
  *
  * @see
  *   [[CharRecoveryOptimizer]] for the default implementation
  */
trait SegmentOptimizer {

  /** Optimize segment BASE parts surrounding TEXT contained in Object[] array.
    *
    * @param chars
    *   base character sequence
    * @param objects
    *   parts to optimize Object(0) - previous BASE Range, will be Range.NULL if no previous range Object(1) - char sequence of TEXT to optimize Object(2) - next BASE Range, will be Range.NULL if no
    *   next range
    * @return
    *   Array containing optimized segments, non-null Range(s) are BASE segments, CharSequence(s) are TEXT segments null entry ignored, an optimal filler for unused entries Range with -ve start/end or
    *   -ve span are skipped CharSequence with 0 length skipped
    */
  def apply(chars: CharSequence, objects: Array[Object]): Array[Object]
}

object SegmentOptimizer {

  /** Insert a null at index in given parts array
    *
    * @param parts
    *   input array
    * @param index
    *   index where to insert
    * @return
    *   copy of input array with extra element inserted at index
    */
  def insert(parts: Array[Object], index: Int): Array[Object] =
    if (index < parts.length) {
      val newParts = new Array[Object](parts.length + 1)
      if (index == 0) {
        System.arraycopy(parts, 0, newParts, 1, parts.length)
      } else {
        System.arraycopy(parts, 0, newParts, 0, index)
        System.arraycopy(parts, index, newParts, index + 1, parts.length - index)
      }
      newParts
    } else {
      java.util.Arrays.copyOf(parts, parts.length + 1)
    }
}
