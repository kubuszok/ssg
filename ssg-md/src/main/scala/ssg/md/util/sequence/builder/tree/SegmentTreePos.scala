/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/tree/SegmentTreePos.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/tree/SegmentTreePos.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package sequence
package builder
package tree

final case class SegmentTreePos(pos: Int, startIndex: Int, iterations: Int) {

  override def equals(o: Any): Boolean =
    o match {
      case that: SegmentTreePos =>
        pos == that.pos && startIndex == that.startIndex
      case _ => false
    }

  override def hashCode(): Int = {
    var result = pos
    result = 31 * result + startIndex
    result
  }

  override def toString: String =
    "{" + pos + ", s: " + startIndex + ", i: " + iterations + "}"
}
