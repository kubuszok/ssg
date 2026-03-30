/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/ReplacedTextRegion.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence

/** Holds Range triplets for text replacement.
  */
final class ReplacedTextRegion(
  val baseRange:     Range,
  val originalRange: Range,
  val replacedRange: Range
) {

  def containsReplacedIndex(replacedIndex: Int): Boolean =
    replacedRange.contains(replacedIndex)

  def containsBaseIndex(originalIndex: Int): Boolean =
    baseRange.contains(originalIndex)

  def containsOriginalIndex(originalIndex: Int): Boolean =
    originalRange.contains(originalIndex)
}
