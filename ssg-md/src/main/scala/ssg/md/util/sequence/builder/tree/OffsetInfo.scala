/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/tree/OffsetInfo.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence
package builder
package tree

final class OffsetInfo(
  val pos:         Int,
  val offset:      Int,
  val isEndOffset: Boolean,
  val startIndex:  Int,
  val endIndex:    Int
) {

  def this(pos: Int, offset: Int, isEndOffset: Boolean, startIndex: Int) = {
    this(pos, offset, isEndOffset, startIndex, startIndex)
  }

  override def toString: String =
    "OffsetInfo{ " +
      "p=" + pos +
      ", o=" + (if (isEndOffset) "[" + offset + ")" else "[" + offset + ", " + (offset + 1) + ")") +
      ", i=[" + startIndex + ", " + endIndex + ") }"
}
