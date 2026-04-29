/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TrackedOffsetUtils.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TrackedOffsetUtils.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package format

import ssg.md.util.misc.CharPredicate.WHITESPACE
import ssg.md.util.sequence.{ BasedSequence, LineAppendable }
import ssg.md.util.sequence.builder.SequenceBuilder
import ssg.md.util.sequence.builder.tree.BasedOffsetTracker

import java.util.List as JList
import scala.util.boundary
import scala.util.boundary.break

object TrackedOffsetUtils {

  /** Resolve any unresolved tracked offsets
    *
    * @param sequence
    *   original sequence for tracked offsets
    * @param appendable
    *   line appendable containing resulting lines
    * @param offsets
    *   tracked offsets
    * @param maxTrailingBlankLines
    *   max trailing blank lines to use in resolving offsets
    * @param traceDetails
    *   true if running tests and want detail printout to stdout
    */
  def resolveTrackedOffsets(sequence: BasedSequence, appendable: LineAppendable, offsets: JList[TrackedOffset], maxTrailingBlankLines: Int, traceDetails: Boolean): Unit =
    if (!offsets.isEmpty) {
      val trackedOffsets = TrackedOffsetList.create(sequence, offsets)

      // need to resolve any unresolved offsets
      var unresolved        = trackedOffsets.size()
      var length            = 0
      val appendableBuilder = appendable.getBuilder
      val baseSeq           = appendableBuilder match {
        case sb: SequenceBuilder => sb.baseSequence
        case _ => sequence.getBaseSequence
      }

      boundary {
        val linesInfo = appendable.getLinesInfo(maxTrailingBlankLines, 0, appendable.getLineCount)
        val lineIter  = linesInfo.iterator()
        while (lineIter.hasNext) {
          val lineInfo           = lineIter.next()
          val line               = lineInfo.getLine
          val lineTrackedOffsets = trackedOffsets.getTrackedOffsets(line.startOffset, line.endOffset)

          if (!lineTrackedOffsets.isEmpty) {
            val trackedIter = lineTrackedOffsets.iterator()
            while (trackedIter.hasNext) {
              val trackedOffset = trackedIter.next()
              val tracker       = BasedOffsetTracker.create(line)

              if (!trackedOffset.isResolved) {
                val offset                   = trackedOffset.offset
                val baseIsWhiteSpaceAtOffset = baseSeq.isCharAt(offset, WHITESPACE)

                if (baseIsWhiteSpaceAtOffset && !baseSeq.isCharAt(offset - 1, WHITESPACE)) {
                  // we need to use previous non-blank and use that offset
                  val info = tracker.getOffsetInfo(offset - 1, false)
                  trackedOffset.setIndex(info.endIndex + length)
                } else if (!baseIsWhiteSpaceAtOffset && baseSeq.isCharAt(offset + 1, WHITESPACE)) {
                  // we need to use this non-blank and use that offset
                  val info = tracker.getOffsetInfo(offset, false)
                  trackedOffset.setIndex(info.startIndex + length)
                } else {
                  val info = tracker.getOffsetInfo(offset, true)
                  trackedOffset.setIndex(info.endIndex + length)
                }
                if (traceDetails) {
                  val lineBuilder = SequenceBuilder.emptyBuilder(line)
                  lineBuilder.append(Nullable(line.asInstanceOf[CharSequence]))
                  System.out.println(
                    String.format(
                      "Resolved %d to %d, start: %d, in line[%d]: '%s'",
                      offset.asInstanceOf[AnyRef],
                      trackedOffset.getIndex.asInstanceOf[AnyRef],
                      length.asInstanceOf[AnyRef],
                      lineInfo.index.asInstanceOf[AnyRef],
                      lineBuilder.toStringWithRanges(true)
                    )
                  )
                }
                unresolved -= 1
              }
            }
          }

          length += line.length()
          if (unresolved <= 0) break()
        }
      }
    }
}
