/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/BasedUtils.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/BasedUtils.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package sequence

import ssg.md.Nullable
import ssg.md.util.sequence.builder.IBasedSegmentBuilder

/** Utility methods for BasedSequence operations.
  */
object BasedUtils {

  /** Generate segments for given sequence
    *
    * @param segments
    *   segment builder
    * @param chars
    *   based sequence for which to generate segments
    */
  def generateSegments(segments: IBasedSegmentBuilder[?], chars: BasedSequence): Unit = {
    // find contiguous ranges of base chars and replaced chars, slower but only when optimizers are available
    var baseStart   = -1
    var baseEnd     = -1
    var hadSequence = false

    var stringBuilder: StringBuilder = null // @nowarn — null used as sentinel for lazy init at perf-critical path

    val iMax = chars.length()
    var i    = 0
    while (i < iMax) {
      val offset = chars.getIndexOffset(i)

      if (offset >= 0) {
        if (baseStart == -1) {
          if (stringBuilder != null) { // @nowarn — null check for lazy init sentinel
            if (!hadSequence) {
              segments.appendAnchor(chars.startOffset)
              hadSequence = true
            }
            segments.append(stringBuilder.toString)
            stringBuilder = null // @nowarn — resetting sentinel
          }
          baseStart = offset
        } else {
          if (offset > baseEnd + 1) {
            // not contiguous base, append accumulated so far and start a new range
            segments.append(baseStart, baseEnd + 1)
            baseStart = offset
          }
        }
        baseEnd = offset
      } else {
        if (baseStart != -1) {
          segments.append(baseStart, baseEnd + 1)
          baseEnd = -1
          baseStart = -1
          hadSequence = true
        }

        if (stringBuilder == null) stringBuilder = new StringBuilder() // @nowarn — lazy init
        stringBuilder.append(chars.charAt(i))
      }
      i += 1
    }

    if (baseStart != -1) {
      segments.append(baseStart, baseEnd + 1)
      hadSequence = true
    }

    if (stringBuilder != null) { // @nowarn — null check for lazy init sentinel
      if (!hadSequence) {
        segments.appendAnchor(chars.startOffset)
        hadSequence = true
      }
      segments.append(stringBuilder.toString)
      segments.appendAnchor(chars.endOffset)
    }

    if (!hadSequence) {
      assert(chars.length() == 0)
      segments.appendAnchor(chars.startOffset)
    }
  }

  def asBased(sequence: CharSequence): BasedSequence =
    BasedSequence.of(Nullable(sequence))
}
