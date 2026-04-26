/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/tree/SegmentTreeRange.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/tree/SegmentTreeRange.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package sequence
package builder
package tree

/** Class used to extract subSequence information from segment tree
  */
final class SegmentTreeRange(
  val startIndex:  Int,
  val endIndex:    Int,
  val startOffset: Int,
  val endOffset:   Int,
  val startPos:    Int,
  val endPos:      Int
) {

  val length: Int = endIndex - startIndex

  override def toString: String =
    "SegmentTreeRange{" +
      "startIndex=" + startIndex +
      ", endIndex=" + endIndex +
      ", startOffset=" + startOffset +
      ", endOffset=" + endOffset +
      ", startPos=" + startPos +
      ", endPos=" + endPos +
      ", length=" + length +
      "}"
}
