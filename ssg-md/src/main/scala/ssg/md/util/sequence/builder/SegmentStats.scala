/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/SegmentStats.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/SegmentStats.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package sequence
package builder

import ssg.md.util.misc.DelimitedBuilder

class SegmentStats(val trackFirst256: Boolean) {

  protected var textLength:        Int = 0 // length of all text in stats
  protected var textSegments:      Int = 0 // total disjoint text segments
  protected var textSegmentLength: Int = 0 // length at start of last segment

  protected var textSpaceLength:        Int = 0 // length of all space text
  protected var textSpaceSegments:      Int = 0 // total disjoint spaces only segments
  protected var textSpaceSegmentLength: Int = 0 // length at start of spaces in last segment

  protected var textFirst256Length:        Int = 0 // length of all text chars < 256
  protected var textFirst256Segments:      Int = 0 // total disjoint chars < 256 only segments
  protected var textFirst256SegmentLength: Int = 0 // length at start of chars < 256 in last segment

  protected var repeatedChar: Int = -1 // repeated char if all same, -1 if no char, -2 if different chars, must be checked before commit which clears this

  def getTextLength: Int = textLength

  def getTextSpaceLength: Int = textSpaceLength

  def getTextFirst256Length: Int = textFirst256Length

  def isTrackTextFirst256: Boolean = trackFirst256

  def getTextSegments: Int = textSegments

  def getTextSpaceSegments: Int = textSpaceSegments

  def getTextFirst256Segments: Int = textFirst256Segments

  def isEmpty: Boolean =
    textLength == 0 && textSegments == 0 && textSegmentLength == 0 &&
      (!trackFirst256 ||
        (textSpaceLength == 0
          && textSpaceSegments == 0
          && textSpaceSegmentLength == 0
          && textFirst256Length == 0
          && textFirst256Segments == 0
          && textFirst256SegmentLength == 0))

  def isValid: Boolean =
    textLength >= textSegments &&
      (!trackFirst256 ||
        (textLength >= textFirst256Length && textSegments >= textFirst256Segments && textFirst256Length >= textFirst256Segments
          && textFirst256Length >= textSpaceLength && textFirst256Segments >= textSpaceSegments && textSpaceLength >= textSpaceSegments))

  def committedCopy(): SegmentStats = {
    val other = new SegmentStats(trackFirst256)
    other.textLength = this.textLength
    other.textSegments = this.textSegments
    other.textSegmentLength = this.textSegmentLength

    if (trackFirst256) {
      other.textSpaceLength = this.textSpaceLength
      other.textSpaceSegments = this.textSpaceSegments
      other.textSpaceSegmentLength = this.textSpaceSegmentLength
      other.textFirst256Length = this.textFirst256Length
      other.textFirst256Segments = this.textFirst256Segments
      other.textFirst256SegmentLength = this.textFirst256SegmentLength
    }

    other.commitText()
    other
  }

  def clear(): Unit = {
    textLength = 0
    textSegments = 0
    textSegmentLength = 0
    repeatedChar = SegmentStats.NULL_REPEATED_CHAR

    if (trackFirst256) {
      textSpaceLength = 0
      textSpaceSegments = 0
      textSpaceSegmentLength = 0
      textFirst256Length = 0
      textFirst256Segments = 0
      textFirst256SegmentLength = 0
    }
  }

  def add(other: SegmentStats): Unit = {
    textLength += other.textLength
    textSegments += other.textSegments

    if (trackFirst256 && other.trackFirst256) {
      textSpaceLength += other.textSpaceLength
      textSpaceSegments += other.textSpaceSegments
      textFirst256Length += other.textFirst256Length
      textFirst256Segments += other.textFirst256Segments
    }
  }

  def remove(other: SegmentStats): Unit = {
    assert(textLength >= other.textLength)
    assert(textSegments >= other.textSegments)
    textLength -= other.textLength
    textSegments -= other.textSegments

    // reset segment starts
    textSegmentLength = textLength

    if (trackFirst256 && other.trackFirst256) {
      assert(textSpaceLength >= other.textSpaceLength)
      assert(textSpaceSegments >= other.textSpaceSegments)
      assert(textFirst256Length >= other.textFirst256Length)
      assert(textFirst256Segments >= other.textFirst256Segments)

      textSpaceLength -= other.textSpaceLength
      textSpaceSegments -= other.textSpaceSegments
      textFirst256Length -= other.textFirst256Length
      textFirst256Segments -= other.textFirst256Segments

      // reset segment starts
      textSpaceSegmentLength = textSpaceLength
      textFirst256SegmentLength = textFirst256Length
    }
  }

  def isTextFirst256: Boolean = {
    val segmentLength = textLength - textSegmentLength
    textFirst256Length - textFirst256SegmentLength == segmentLength
  }

  def isTextRepeatedSpace: Boolean = {
    val segmentLength = textLength - textSegmentLength
    textSpaceLength - textSpaceSegmentLength == segmentLength
  }

  def isRepeatedText: Boolean = repeatedChar >= 0

  def commitText(): Unit =
    if (textLength > textSegmentLength) {
      textSegments += 1
      repeatedChar = SegmentStats.NULL_REPEATED_CHAR

      if (trackFirst256) {
        val segmentLength = textLength - textSegmentLength

        if (textSpaceLength - textSpaceSegmentLength == segmentLength) textSpaceSegments += 1
        if (textFirst256Length - textFirst256SegmentLength == segmentLength) textFirst256Segments += 1
      }

      textSegmentLength = textLength
      if (trackFirst256) {
        textSpaceSegmentLength = textSpaceLength
        textFirst256SegmentLength = textFirst256Length
      }
    }

  def addText(text: CharSequence): Unit = {
    // need to count spaces in it
    textLength += text.length()

    if (trackFirst256) {
      val iMax = text.length()
      var i    = 0
      while (i < iMax) {
        val c = text.charAt(i)

        if (repeatedChar == SegmentStats.NULL_REPEATED_CHAR) {
          repeatedChar = c.toInt
        } else if (repeatedChar != c.toInt) {
          repeatedChar = SegmentStats.NOT_REPEATED_CHAR
        }

        if (c < 256) {
          if (c == ' ') textSpaceLength += 1
          textFirst256Length += 1
        }
        i += 1
      }
    }
  }

  def addText(c: Char): Unit = {
    // need to count spaces in it
    textLength += 1

    if (trackFirst256) {
      if (repeatedChar == SegmentStats.NULL_REPEATED_CHAR) {
        repeatedChar = c.toInt
      } else if (repeatedChar != c.toInt) {
        repeatedChar = SegmentStats.NOT_REPEATED_CHAR
      }

      if (c < 256) {
        if (c == ' ') textSpaceLength += 1
        textFirst256Length += 1
      }
    }
  }

  def addText(c: Char, repeat: Int): Unit = {
    assert(repeat > 0)

    // need to count spaces in it
    textLength += repeat

    if (trackFirst256) {
      if (repeatedChar == SegmentStats.NULL_REPEATED_CHAR) {
        repeatedChar = c.toInt
      } else if (repeatedChar != c.toInt) {
        repeatedChar = SegmentStats.NOT_REPEATED_CHAR
      }

      if (c < 256) {
        if (c == ' ') textSpaceLength += repeat
        textFirst256Length += repeat
      }
    }
  }

  def removeText(text: CharSequence): Unit = {
    textLength -= text.length()

    if (trackFirst256) {
      val iMax = text.length()
      var i    = 0
      while (i < iMax) {
        val c = text.charAt(i)
        if (repeatedChar == SegmentStats.NULL_REPEATED_CHAR) {
          repeatedChar = c.toInt
        } else if (repeatedChar != c.toInt) {
          repeatedChar = SegmentStats.NOT_REPEATED_CHAR
        }

        if (c < 256) {
          if (c == ' ') {
            assert(textSpaceLength > 0)
            textSpaceLength -= 1
          }

          assert(textFirst256Length > 0)
          textFirst256Length -= 1
        }
        i += 1
      }
    }

    // if whole segment was removed, reset repeated char
    if (textLength == textSegmentLength) repeatedChar = SegmentStats.NULL_REPEATED_CHAR
  }

  override def toString: String = {
    val sb = new DelimitedBuilder(", ")
    sb.append("s=")
      .append(textSpaceSegments)
      .append(":")
      .append(textSpaceLength)
      .mark()
      .append("u=")
      .append(textFirst256Segments)
      .append(":")
      .append(textFirst256Length)
      .mark()
      .append("t=")
      .append(textSegments)
      .append(":")
      .append(textLength)

    sb.toString
  }
}

object SegmentStats {

  val NULL_REPEATED_CHAR: Int = -1
  val NOT_REPEATED_CHAR:  Int = -2
}
