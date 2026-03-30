/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/CharRecoveryOptimizer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence
package builder

import ssg.md.util.misc.Utils
import ssg.md.util.sequence.{ PositionAnchor, Range, SequenceUtils }
import ssg.md.util.sequence.SequenceUtils.*

import scala.util.boundary
import scala.util.boundary.break

class CharRecoveryOptimizer(private val anchor: PositionAnchor) extends SegmentOptimizer {

  private var prevEolPos: Int = 0

  private def prevMatchPos(base: CharSequence, chars: CharSequence, startIndex: Int, endIndex: Int): Int = {
    val length     = chars.length()
    val clampedEnd = Math.min(base.length(), endIndex)
    val iMax       = Math.min(clampedEnd - startIndex, length)

    var i = 0
    boundary {
      while (i < iMax) {
        val c  = base.charAt(i + startIndex)
        val c1 = chars.charAt(i)

        if (c == SequenceUtils.EOL_CHAR) prevEolPos = i + 1
        if (c1 != c) break(i)
        i += 1
      }
      iMax
    }
  }

  private def nextMatchPos(base: CharSequence, chars: CharSequence, startIndex: Int, fromIndex: Int): Int = {
    val clampedStart = Math.max(0, startIndex)
    val clampedFrom  = Math.min(base.length(), fromIndex)

    val length = chars.length()
    val iMax   = Math.min(clampedFrom - clampedStart, length)

    val baseOffset  = clampedFrom - iMax
    val charsOffset = length - iMax

    var i = iMax
    boundary {
      while (i > 0) {
        i -= 1
        val c  = base.charAt(baseOffset + i)
        val c1 = chars.charAt(charsOffset + i)

        if (c1 != c) break(charsOffset + i + 1)
      }
      charsOffset
    }
  }

  override def apply(chars: CharSequence, parts: Array[Object]): Array[Object] = boundary {
    // optimizer already applied
    if (parts.length != 3 || !(parts(0).isInstanceOf[Range] && parts(1).isInstanceOf[CharSequence] && parts(2).isInstanceOf[Range])) {
      break(parts)
    }

    val originalPrev: Range        = parts(0).asInstanceOf[Range]
    val originalText: CharSequence = parts(1).asInstanceOf[CharSequence]
    val originalNext: Range        = parts(2).asInstanceOf[Range]

    // optimizer already applied
    val textLength = originalText.length()
    if ((originalPrev.isNull && originalNext.isNull) || textLength == 0) {
      break(parts)
    }

    val charsLength = chars.length()

    val prevRange: Range         = originalPrev
    var text:      CharSequence  = originalText
    var nextRange: Range         = originalNext
    var result:    Array[Object] = parts

    prevEolPos = -1

    val prevPos = if (prevRange.isNull) 0 else prevMatchPos(chars, text, prevRange.end, if (nextRange.isNotNull) nextRange.start else charsLength)
    var nextPos = if (nextRange.isNull) textLength else nextMatchPos(chars, text, if (prevRange.isNotNull) prevRange.end else 0, nextRange.start)

    // FIX: factor out EOL recovery to separate optimizer and recover EOL in middle of text
    if (prevPos == 0 && nextPos == textLength) {
      // check for EOL recovery
      if (prevRange.isNotNull && !endsWithEOL(chars.subSequence(prevRange.start, prevRange.end)) && startsWith(text, "\n")) {
        // see if there is an EOL between prevRange end and nextRange start with only spaces between them
        val eol = endOfLine(chars, prevRange.end)
        if (eol < charsLength && isBlank(chars.subSequence(prevRange.end, eol))) {
          // we have an EOL
          val eolRange = Range.ofLength(eol, 1)
          text = text.subSequence(1, textLength)

          if (nextRange.isEmpty && nextRange.start < eolRange.end) {
            // serves no purpose and causes issues
            nextRange = Range.NULL
          }

          // need to insert EOL range between prevRange and text
          if (text.length() == 0) {
            // no text left, can replace that with EOL range
            result(1) = eolRange
            result(2) = nextRange
          } else {
            // EOL range between prevRange and text and replace text with left-over text
            if (prevRange.isNull) {
              // replace prev range
              result(0) = eolRange
              result(1) = text
              result(2) = nextRange
            } else if (nextRange.isNull) {
              // replace next range
              result(1) = eolRange
              result(2) = text
            } else {
              // create new parts array and insert eol range
              result = new Array[Object](parts.length + 1)
              result(0) = prevRange
              result(1) = eolRange
              result(2) = text
              result(3) = nextRange
            }
          }
        }
      }
      break(result)
    }

    assert(
      nextPos <= textLength,
      s"prevRange: $originalPrev, '${Utils.escapeJavaString(ssg.md.Nullable(text))}', nextRange: $originalNext"
    )

    // these pos are in text coordinates we find the breakdown for the text if there is overlap
    // if there is prevEol it goes to prev, and all after goes to next
    if (prevEolPos != -1 && prevEolPos < prevPos) {
      val adjustedPrevPos = prevEolPos

      // had eol, split is determined by EOL so if nextPos overlaps with prevPos, it can only take after EOL chars.
      if (nextPos < adjustedPrevPos) {
        nextPos = adjustedPrevPos
      }

      var matchedPrev = adjustedPrevPos
      var matchedNext = textLength - nextPos
      val maxSpan     = Math.min(textLength, (if (nextRange.isNotNull) nextRange.start else charsLength) - (if (prevRange.isNotNull) prevRange.end else 0))
      val excess      = matchedPrev + matchedNext - maxSpan

      if (excess > 0) {
        computeExcessAdjustment(matchedPrev, matchedNext, excess) match {
          case (mp, mn) =>
            matchedPrev = mp
            matchedNext = mn
        }
      }

      finalizeParts(chars, prevRange, text, nextRange, matchedPrev, matchedNext, textLength, charsLength, result)
    } else {
      var matchedPrev = prevPos
      var matchedNext = textLength - nextPos
      val maxSpan     = Math.min(textLength, (if (nextRange.isNotNull) nextRange.start else charsLength) - (if (prevRange.isNotNull) prevRange.end else 0))
      val excess      = matchedPrev + matchedNext - maxSpan

      if (excess > 0) {
        assert(
          matchedNext > 0 && matchedPrev > 0,
          s"prevRange: $originalPrev, '${Utils.escapeJavaString(ssg.md.Nullable(text))}', nextRange: $originalNext"
        )

        computeExcessAdjustment(matchedPrev, matchedNext, excess) match {
          case (mp, mn) =>
            matchedPrev = mp
            matchedNext = mn
        }
      }

      finalizeParts(chars, prevRange, text, nextRange, matchedPrev, matchedNext, textLength, charsLength, result)
    }
  }

  private def computeExcessAdjustment(matchedPrev: Int, matchedNext: Int, excess: Int): (Int, Int) =
    anchor match {
      case PositionAnchor.PREVIOUS =>
        // give it all to next
        val prevDelta = Math.min(matchedPrev, excess)
        (matchedPrev - prevDelta, matchedNext - (excess - prevDelta))

      case PositionAnchor.NEXT =>
        // give it all to prev
        val nextDelta = Math.min(matchedNext, excess)
        (matchedPrev - (excess - nextDelta), matchedNext - nextDelta)

      case PositionAnchor.CURRENT =>
        // divide between the two with remainder to right
        val prevHalf = Math.min(matchedPrev, excess >> 1)
        (matchedPrev - prevHalf, matchedNext - (excess - prevHalf))
    }

  private def finalizeParts(
    chars:         CharSequence,
    origPrevRange: Range,
    origText:      CharSequence,
    origNextRange: Range,
    matchedPrev:   Int,
    matchedNext:   Int,
    textLength:    Int,
    charsLength:   Int,
    partsIn:       Array[Object]
  ): Array[Object] = {
    var prevRange = origPrevRange
    var nextRange = origNextRange
    var result    = partsIn

    // now we can compute match pos and ranges
    if (matchedPrev > 0) {
      prevRange = prevRange.endPlus(matchedPrev)
    }

    if (matchedNext > 0) {
      nextRange = nextRange.startMinus(matchedNext)
    }

    var text: CharSequence = origText.subSequence(matchedPrev, textLength - matchedNext)

    var eolRange = Range.NULL

    if (prevRange.isNotNull && !endsWithEOL(chars.subSequence(prevRange.start, prevRange.end)) && startsWith(text, "\n")) {
      // see if there is an EOL between prevRange end and nextRange start with only spaces between them
      val eol = endOfLine(chars, prevRange.end)
      if (eol < charsLength && (nextRange.isNull || eol < nextRange.start) && isBlank(chars.subSequence(prevRange.end, eol))) {
        // we have an EOL
        eolRange = Range.ofLength(eol, 1)
        text = text.subSequence(1, text.length())
      }
    }

    if (prevRange.isNotNull && nextRange.isNotNull && text.length() == 0 && prevRange.isAdjacentBefore(nextRange)) {
      // remove the string and next range
      result(0) = prevRange.expandToInclude(nextRange)
      result(1) = null // null used per optimizer protocol: "null entry ignored"
      result(2) = null // null used per optimizer protocol: "null entry ignored"
    } else {
      if (eolRange.isNotNull) {
        // need to insert eolRange, can replace prevRange if it is NULL or text if it is empty, or move text to nextRange if it is NULL
        if (nextRange.isEmpty && nextRange.start < eolRange.end) {
          // serves no purpose and causes issues
          nextRange = Range.NULL
        }

        if (text.length() == 0) {
          result(0) = prevRange
          result(1) = eolRange
          result(2) = nextRange
        } else if (prevRange.isNull) {
          result(0) = eolRange
          result(1) = text
          result(2) = nextRange
        } else if (nextRange.isNull) {
          result(0) = prevRange
          result(1) = eolRange
          result(2) = text
        } else {
          // insert a new position
          result = new Array[Object](partsIn.length + 1)
          result(0) = prevRange
          result(1) = eolRange
          result(2) = text
          result(3) = nextRange
        }
      } else {
        result(0) = prevRange
        result(1) = text
        result(2) = nextRange
      }
    }

    result
  }
}
