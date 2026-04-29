/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/SequenceUtils.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/SequenceUtils.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package sequence

import ssg.md.Nullable
import ssg.md.util.misc.{ CharPredicate, Pair, Utils }

import java.text.{ NumberFormat, ParsePosition }
import java.util as ju
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

/** Static utility methods and constants for CharSequence operations.
  *
  * In the original Java, this was an interface with static methods and constants. In Scala 3, it is converted to an object.
  */
@SuppressWarnings(Array("unchecked"))
object SequenceUtils {
  val EOL:     String = "\n"
  val SPACE:   String = " "
  val ANY_EOL: String = "\r\n"

  val EOL_CHAR:  Char = ANY_EOL.charAt(1)
  val EOL_CHAR1: Char = ANY_EOL.charAt(0)
  val EOL_CHAR2: Char = ANY_EOL.charAt(1)
  val SPC:       Char = ' '
  val NUL:       Char = '\u0000'
  val ENC_NUL:   Char = '\uFFFD'
  val NBSP:      Char = '\u00A0'
  val LS:        Char = '\u2028' // line separator
  val US:        Char = '\u001f' // US or USEP - Unit Separator, also used as IntelliJDummyIdentifier in Parsings, used as a tracked offset marker in the sequence

  val LINE_SEP:        String = Character.toString(LS)
  val SPACE_TAB:       String = " \t"
  val SPACE_EOL:       String = " \n"
  val US_CHARS:        String = Character.toString(US)
  val WHITESPACE:      String = " \t\r\n"
  val NBSP_CHARS:      String = Character.toString(NBSP)
  val WHITESPACE_NBSP: String = " \t\r\n\u00A0"

  /** @deprecated
    *   use CharPredicate fields directly
    */
  @deprecated("use CharPredicate fields directly", "0.1.0")
  val SPACE_SET: CharPredicate = CharPredicate.SPACE
  @deprecated("use CharPredicate fields directly", "0.1.0")
  val TAB_SET: CharPredicate = CharPredicate.TAB
  @deprecated("use CharPredicate fields directly", "0.1.0")
  val EOL_SET: CharPredicate = CharPredicate.EOL
  @deprecated("use CharPredicate fields directly", "0.1.0")
  val SPACE_TAB_SET: CharPredicate = CharPredicate.SPACE_TAB
  @deprecated("use CharPredicate fields directly", "0.1.0")
  val SPACE_TAB_NBSP_SET: CharPredicate = CharPredicate.SPACE_TAB_NBSP
  @deprecated("use CharPredicate fields directly", "0.1.0")
  val SPACE_TAB_EOL_SET: CharPredicate = CharPredicate.SPACE_TAB_EOL
  @deprecated("use CharPredicate fields directly", "0.1.0")
  val SPACE_EOL_SET: CharPredicate = CharPredicate.WHITESPACE
  @deprecated("use CharPredicate fields directly", "0.1.0")
  val ANY_EOL_SET: CharPredicate = CharPredicate.ANY_EOL
  @deprecated("use CharPredicate fields directly", "0.1.0")
  val WHITESPACE_SET: CharPredicate = CharPredicate.WHITESPACE
  @deprecated("use CharPredicate fields directly", "0.1.0")
  val WHITESPACE_NBSP_SET: CharPredicate = CharPredicate.WHITESPACE_NBSP
  @deprecated("use CharPredicate fields directly", "0.1.0")
  val BACKSLASH_SET: CharPredicate = CharPredicate.BACKSLASH
  @deprecated("use CharPredicate fields directly", "0.1.0")
  val US_SET: CharPredicate = (value: Int) => value == US
  @deprecated("use CharPredicate fields directly", "0.1.0")
  val HASH_SET: CharPredicate = CharPredicate.HASH
  @deprecated("use CharPredicate fields directly", "0.1.0")
  val DECIMAL_DIGITS: CharPredicate = CharPredicate.HASH
  @deprecated("use CharPredicate fields directly", "0.1.0")
  val HEXADECIMAL_DIGITS: CharPredicate = CharPredicate.HASH
  @deprecated("use CharPredicate fields directly", "0.1.0")
  val OCTAL_DIGITS: CharPredicate = CharPredicate.HASH

  /** @deprecated
    *   use new names instead
    */
  @deprecated("use new names instead", "0.1.0")
  val LSEP: Char = LS
  @deprecated("use new names instead", "0.1.0")
  val EOL_CHARS: String = ANY_EOL
  @deprecated("use new names instead", "0.1.0")
  val WHITESPACE_NO_EOL_CHARS: String = SPACE_TAB
  @deprecated("use new names instead", "0.1.0")
  val WHITESPACE_CHARS: String = WHITESPACE
  @deprecated("use new names instead", "0.1.0")
  val WHITESPACE_NBSP_CHARS: String = WHITESPACE_NBSP

  val SPLIT_INCLUDE_DELIMS:      Int = 1 // include delimiters as part of the split out part
  val SPLIT_TRIM_PARTS:          Int = 2 // trim split parts
  val SPLIT_SKIP_EMPTY:          Int = 4 // skip empty trimmed parts
  val SPLIT_INCLUDE_DELIM_PARTS: Int = 8 // include split out delimiters as parts themselves
  val SPLIT_TRIM_SKIP_EMPTY:     Int = SPLIT_TRIM_PARTS | SPLIT_SKIP_EMPTY

  private def getVisibleSpacesMap(): ju.HashMap[Character, String] = {
    val charMap = new ju.HashMap[Character, String]()
    charMap.put('\n', "\\n")
    charMap.put('\r', "\\r")
    charMap.put('\f', "\\f")
    charMap.put('\t', "\\u2192")
    charMap.put(LS, "\u27a5")
    charMap
  }

  val visibleSpacesMap: ju.Map[Character, String] = getVisibleSpacesMap()

  val EMPTY_INDICES: Array[Int] = Array.empty[Int]

  def subSequence[T <: CharSequence](thizz: T, startIndex: Int): CharSequence =
    thizz.subSequence(startIndex, thizz.length())

  /** Get a portion of this sequence selected by range
    *
    * @param thizz
    *   char sequence
    * @param range
    *   range to get, coordinates offset form start of this sequence
    * @return
    *   sequence whose contents reflect the selected portion, if range.isNull() then this is returned
    */
  def subSequence[T <: CharSequence](thizz: T, range: Range): CharSequence =
    if (range.isNull) thizz else thizz.subSequence(range.start, range.end)

  /** Get a portion of this sequence before one selected by range
    *
    * @param thizz
    *   char sequence
    * @param range
    *   range to get, coordinates offset form start of this sequence
    * @return
    *   sequence whose contents come before the selected range, if range.isNull() then null
    */
  def subSequenceBefore[T <: CharSequence](thizz: T, range: Range): Nullable[CharSequence] =
    if (range.isNull) Nullable.empty else Nullable(thizz.subSequence(0, range.start))

  /** Get a portion of this sequence after one selected by range
    *
    * @param thizz
    *   char sequence
    * @param range
    *   range to get, coordinates offset form start of this sequence
    * @return
    *   sequence whose contents come after the selected range, if range.isNull() then null
    */
  def subSequenceAfter[T <: CharSequence](thizz: T, range: Range): Nullable[CharSequence] =
    if (range.isNull) Nullable.empty else Nullable(thizz.subSequence(range.end, thizz.length()))

  /** Get portions of this sequence before and after one selected by range
    *
    * @param thizz
    *   char sequence
    * @param range
    *   range to get, coordinates offset form start of this sequence
    * @return
    *   sequence whose contents come before and after the selected range, if range.isNull() then pair of nulls
    */
  def subSequenceBeforeAfter[T <: CharSequence](thizz: T, range: Range): Pair[CharSequence, CharSequence] =
    Pair.of(subSequenceBefore(thizz, range), subSequenceAfter(thizz, range))

  // containsAny / containsAnyNot
  def containsAny(thizz:    CharSequence, s: CharPredicate):                                Boolean = indexOfAny(thizz, s, 0, Integer.MAX_VALUE) != -1
  def containsAny(thizz:    CharSequence, s: CharPredicate, index:     Int):                Boolean = indexOfAny(thizz, s, index, Integer.MAX_VALUE) != -1
  def containsAnyNot(thizz: CharSequence, s: CharPredicate):                                Boolean = indexOfAny(thizz, s.negate(), 0, Integer.MAX_VALUE) != -1
  def containsAnyNot(thizz: CharSequence, s: CharPredicate, fromIndex: Int):                Boolean = indexOfAny(thizz, s.negate(), fromIndex, Integer.MAX_VALUE) != -1
  def containsAnyNot(thizz: CharSequence, s: CharPredicate, fromIndex: Int, endIndex: Int): Boolean = indexOfAny(thizz, s.negate(), fromIndex, endIndex) != -1

  // indexOf overloads
  def indexOf(thizz:       CharSequence, s: CharSequence):                                 Int = indexOf(thizz, s, 0, Integer.MAX_VALUE)
  def indexOf(thizz:       CharSequence, s: CharSequence, fromIndex:  Int):                Int = indexOf(thizz, s, fromIndex, Integer.MAX_VALUE)
  def indexOf(thizz:       CharSequence, c: Char):                                         Int = indexOf(thizz, c, 0, Integer.MAX_VALUE)
  def indexOf(thizz:       CharSequence, c: Char, fromIndex:          Int):                Int = indexOf(thizz, c, fromIndex, Integer.MAX_VALUE)
  def indexOfAny(thizz:    CharSequence, s: CharPredicate):                                Int = indexOfAny(thizz, s, 0, Integer.MAX_VALUE)
  def indexOfAny(thizz:    CharSequence, s: CharPredicate, index:     Int):                Int = indexOfAny(thizz, s, index, Integer.MAX_VALUE)
  def indexOfAnyNot(thizz: CharSequence, s: CharPredicate):                                Int = indexOfAny(thizz, s.negate(), 0, Integer.MAX_VALUE)
  def indexOfAnyNot(thizz: CharSequence, s: CharPredicate, fromIndex: Int):                Int = indexOfAny(thizz, s.negate(), fromIndex, Integer.MAX_VALUE)
  def indexOfAnyNot(thizz: CharSequence, s: CharPredicate, fromIndex: Int, endIndex: Int): Int = indexOfAny(thizz, s.negate(), fromIndex, endIndex)
  def indexOfNot(thizz:    CharSequence, c: Char):                                         Int = indexOfNot(thizz, c, 0, Integer.MAX_VALUE)
  def indexOfNot(thizz:    CharSequence, c: Char, fromIndex:          Int):                Int = indexOfNot(thizz, c, fromIndex, Integer.MAX_VALUE)

  // lastIndexOf overloads
  def lastIndexOf(thizz:       CharSequence, s: CharSequence):                                   Int = lastIndexOf(thizz, s, 0, Integer.MAX_VALUE)
  def lastIndexOf(thizz:       CharSequence, s: CharSequence, fromIndex:   Int):                 Int = lastIndexOf(thizz, s, 0, fromIndex)
  def lastIndexOf(thizz:       CharSequence, c: Char):                                           Int = lastIndexOf(thizz, c, 0, Integer.MAX_VALUE)
  def lastIndexOf(thizz:       CharSequence, c: Char, fromIndex:           Int):                 Int = lastIndexOf(thizz, c, 0, fromIndex)
  def lastIndexOfAny(thizz:    CharSequence, s: CharPredicate):                                  Int = lastIndexOfAny(thizz, s, 0, Integer.MAX_VALUE)
  def lastIndexOfAny(thizz:    CharSequence, s: CharPredicate, fromIndex:  Int):                 Int = lastIndexOfAny(thizz, s, 0, fromIndex)
  def lastIndexOfAnyNot(thizz: CharSequence, s: CharPredicate):                                  Int = lastIndexOfAny(thizz, s.negate(), 0, Integer.MAX_VALUE)
  def lastIndexOfAnyNot(thizz: CharSequence, s: CharPredicate, fromIndex:  Int):                 Int = lastIndexOfAny(thizz, s.negate(), 0, fromIndex)
  def lastIndexOfAnyNot(thizz: CharSequence, s: CharPredicate, startIndex: Int, fromIndex: Int): Int = lastIndexOfAny(thizz, s.negate(), startIndex, fromIndex)
  def lastIndexOfNot(thizz:    CharSequence, c: Char):                                           Int = lastIndexOfNot(thizz, c, 0, Integer.MAX_VALUE)
  def lastIndexOfNot(thizz:    CharSequence, c: Char, fromIndex:           Int):                 Int = lastIndexOfNot(thizz, c, 0, fromIndex)

  def indexOf(thizz: CharSequence, c: Char, fromIndex: Int, endIndex: Int): Int = {
    val start = Math.max(fromIndex, 0)
    val end   = Math.min(thizz.length(), endIndex)
    boundary {
      var i = start
      while (i < end) {
        if (c == thizz.charAt(i)) break(i)
        i += 1
      }
      -1
    }
  }

  // TEST:
  def indexOf(thizz: CharSequence, s: CharSequence, fromIndex: Int, endIndex: Int): Int = {
    val start = Math.max(fromIndex, 0)
    val sMax  = s.length()
    if (sMax == 0) start
    else {
      val end = Math.min(thizz.length(), endIndex)
      boundary {
        if (start < end) {
          val firstChar = s.charAt(0)
          var pos       = start
          while (pos + sMax <= end) {
            pos = indexOf(thizz, firstChar, pos)
            if (pos < 0 || pos + sMax > end) break(-1)
            if (matchChars(thizz, s, pos)) break(pos)
            pos += 1
          }
        }
        -1
      }
    }
  }

  def lastIndexOf(thizz: CharSequence, c: Char, startIndex: Int, fromIndex: Int): Int = {
    var fi = Math.min(fromIndex, thizz.length() - 1)
    fi += 1
    val si = Math.max(startIndex, 0)
    boundary {
      var i = fi
      while ({ i -= 1; i >= si })
        if (c == thizz.charAt(i)) break(i)
      -1
    }
  }

  def indexOfNot(thizz: CharSequence, c: Char, fromIndex: Int, endIndex: Int): Int = {
    val start = Math.max(fromIndex, 0)
    val end   = Math.min(endIndex, thizz.length())
    boundary {
      var i = start
      while (i < end) {
        if (thizz.charAt(i) != c) break(i)
        i += 1
      }
      -1
    }
  }

  def indexOfAny(thizz: CharSequence, s: CharPredicate, fromIndex: Int, endIndex: Int): Int = {
    val start = Math.max(fromIndex, 0)
    val end   = Math.min(endIndex, thizz.length())
    boundary {
      var i = start
      while (i < end) {
        val c = thizz.charAt(i)
        if (s.test(c)) break(i)
        i += 1
      }
      -1
    }
  }

  // TEST:
  def lastIndexOf(thizz: CharSequence, s: CharSequence, startIndex: Int, fromIndex: Int): Int = {
    val si   = Math.max(startIndex, 0)
    val sMax = s.length()
    if (sMax == 0) si
    else {
      val fi = Math.min(fromIndex, thizz.length())
      boundary {
        if (si < fi) {
          val lastChar = s.charAt(sMax - 1)
          var pos      = fi
          while (pos + 1 >= si + sMax) {
            pos = lastIndexOf(thizz, lastChar, pos)
            if (pos + 1 < si + sMax) break(-1)
            if (matchCharsReversed(thizz, s, pos)) break(pos + 1 - sMax)
            pos -= 1
          }
        }
        -1
      }
    }
  }

  // TEST:
  def lastIndexOfNot(thizz: CharSequence, c: Char, startIndex: Int, fromIndex: Int): Int = {
    var fi = Math.min(fromIndex, thizz.length() - 1)
    fi += 1
    val si = Math.max(startIndex, 0)
    boundary {
      var i = fi
      while ({ i -= 1; i >= si })
        if (thizz.charAt(i) != c) break(i)
      -1
    }
  }

  // TEST:
  def lastIndexOfAny(thizz: CharSequence, s: CharPredicate, startIndex: Int, fromIndex: Int): Int = {
    var fi = Math.min(fromIndex, thizz.length() - 1)
    fi += 1
    val si = Math.max(startIndex, 0)
    boundary {
      var i = fi
      while ({ i -= 1; i >= si }) {
        val c = thizz.charAt(i)
        if (s.test(c)) break(i)
      }
      -1
    }
  }

  /** Equality comparison based on character content of this sequence, with quick fail resorting to content comparison only if length and hashCodes are equal
    *
    * @param thizz
    *   char sequence to test for equality
    * @param o
    *   any character sequence
    * @return
    *   true if character contents are equal
    */
  def equals(thizz: CharSequence, o: Any): Boolean =
    // do quick failure of equality
    if (o.asInstanceOf[AnyRef] eq thizz.asInstanceOf[AnyRef]) true
    else
      o match {
        case chars: CharSequence =>
          if (chars.length() != thizz.length()) false
          else {
            o match {
              case other: String =>
                if (other.hashCode() != thizz.hashCode()) false
                else matchChars(thizz, chars, 0, false)
              case _ =>
                matchChars(thizz, chars, 0, false)
            }
          }
        case _ => false
      }

  def hashCode(thizz: CharSequence): Int = {
    var h      = 0
    val length = thizz.length()
    if (length > 0) {
      var i = 0
      while (i < length) {
        h = 31 * h + thizz.charAt(i)
        i += 1
      }
    }
    h
  }

  def compareReversed(o1: Nullable[CharSequence], o2: Nullable[CharSequence]): Int = compare(o2, o1)

  def compare(o1: Nullable[CharSequence], o2: Nullable[CharSequence]): Int = compare(o1, o2, ignoreCase = false)

  def compare(o1: Nullable[CharSequence], o2: Nullable[CharSequence], ignoreCase: Boolean): Int =
    compare(o1, o2, ignoreCase, Nullable.empty)

  def compare(o1: Nullable[CharSequence], o2: Nullable[CharSequence], ignoreCase: Boolean, ignoreChars: Nullable[CharPredicate]): Int =
    if (o1.isEmpty || o2.isEmpty) {
      if (o1.isEmpty && o2.isEmpty) 0 else if (o1.isEmpty) -1 else 1
    } else {
      val s1   = o1.get
      val s2   = o2.get
      val len1 = s1.length()
      val len2 = s2.length()
      val iMax = Math.min(len1, len2)
      boundary {
        if (ignoreCase) {
          var i = 0
          while (i < iMax) {
            val c1 = s1.charAt(i)
            val c2 = s2.charAt(i)
            if (c1 != c2) {
              val u1 = Character.toUpperCase(c1)
              val u2 = Character.toUpperCase(c2)
              if (u1 != u2) {
                // Unfortunately, conversion to uppercase does not work properly
                // for the Georgian alphabet, which has strange rules about case
                // conversion. So we need to make one last check before exiting.
                if (Character.toLowerCase(u1) != Character.toLowerCase(u2)) {
                  // NOTE: if both chars are in the ignore set, then it is a match
                  if (ignoreChars.isEmpty || !(ignoreChars.get.test(c1) && ignoreChars.get.test(c2))) {
                    break(c1 - c2)
                  }
                }
              }
            }
            i += 1
          }
        } else {
          var i = 0
          while (i < iMax) {
            val c1 = s1.charAt(i)
            val c2 = s2.charAt(i)
            if (c1 != c2) {
              // NOTE: if both chars are in the ignore set, then it is a match
              if (ignoreChars.isEmpty || !(ignoreChars.get.test(c1) && ignoreChars.get.test(c2))) {
                break(c1 - c2)
              }
            }
            i += 1
          }
        }
        len1 - len2
      }
    }

  def toStringArray(sequences: CharSequence*): Array[String] = {
    val result = new Array[String](sequences.length)
    var i      = 0
    for (sequence <- sequences) {
      result(i) = if (sequence == null) null else sequence.toString // Java interop: sequences may be null
      i += 1
    }
    result
  }

  def isVisibleWhitespace(c: Char): Boolean = visibleSpacesMap.containsKey(c)

  def columnsToNextTabStop(column: Int): Int =
    // Tab stop is 4
    4 - (column % 4)

  def expandTo(indices: Array[Int], length: Int, step: Int): Array[Int] = {
    val remainder = length & step
    val next      = length + (if (remainder != 0) step else 0)
    if (indices.length < next) {
      val replace = new Array[Int](next)
      System.arraycopy(indices, 0, replace, 0, indices.length)
      replace
    } else {
      indices
    }
  }

  def truncateTo(indices: Array[Int], length: Int): Array[Int] =
    if (indices.length > length) {
      val replace = new Array[Int](length)
      System.arraycopy(indices, 0, replace, 0, length)
      replace
    } else {
      indices
    }

  def indexOfAll(thizz: CharSequence, s: CharSequence): Array[Int] = {
    val length = s.length()
    if (length == 0) EMPTY_INDICES
    else {
      var pos = indexOf(thizz, s)
      if (pos == -1) EMPTY_INDICES
      else {
        var iMax    = 0
        var indices = new Array[Int](32)
        indices(iMax) = pos
        iMax += 1

        var done = false
        while (!done) {
          pos = indexOf(thizz, s, pos + length)
          if (pos == -1) done = true
          else {
            if (indices.length <= iMax) indices = expandTo(indices, iMax + 1, 32)
            indices(iMax) = pos
            iMax += 1
          }
        }
        truncateTo(indices, iMax)
      }
    }
  }

  // TEST:
  def matches(thizz:           CharSequence, chars: CharSequence, ignoreCase: Boolean): Boolean = chars.length() == thizz.length() && matchChars(thizz, chars, 0, ignoreCase)
  def matches(thizz:           CharSequence, chars: CharSequence):                      Boolean = chars.length() == thizz.length() && matchChars(thizz, chars, 0, false)
  def matchesIgnoreCase(thizz: CharSequence, chars: CharSequence):                      Boolean = chars.length() == thizz.length() && matchChars(thizz, chars, 0, true)

  def matchChars(thizz: CharSequence, chars: CharSequence, startIndex: Int, ignoreCase: Boolean): Boolean =
    matchedCharCount(thizz, chars, startIndex, Integer.MAX_VALUE, fullMatchOnly = true, ignoreCase) == chars.length()
  def matchChars(thizz:           CharSequence, chars: CharSequence, startIndex: Int): Boolean = matchChars(thizz, chars, startIndex, false)
  def matchCharsIgnoreCase(thizz: CharSequence, chars: CharSequence, startIndex: Int): Boolean = matchChars(thizz, chars, startIndex, true)

  def matchChars(thizz:           CharSequence, chars: CharSequence, ignoreCase: Boolean): Boolean = matchChars(thizz, chars, 0, ignoreCase)
  def matchChars(thizz:           CharSequence, chars: CharSequence):                      Boolean = matchChars(thizz, chars, 0, false)
  def matchCharsIgnoreCase(thizz: CharSequence, chars: CharSequence):                      Boolean = matchChars(thizz, chars, 0, true)

  def matchCharsReversed(thizz: CharSequence, chars: CharSequence, endIndex: Int, ignoreCase: Boolean): Boolean =
    endIndex + 1 >= chars.length() && matchChars(thizz, chars, endIndex + 1 - chars.length(), ignoreCase)
  def matchCharsReversed(thizz:           CharSequence, chars: CharSequence, endIndex: Int): Boolean = endIndex + 1 >= chars.length() && matchChars(thizz, chars, endIndex + 1 - chars.length(), false)
  def matchCharsReversedIgnoreCase(thizz: CharSequence, chars: CharSequence, endIndex: Int): Boolean = endIndex + 1 >= chars.length() && matchChars(thizz, chars, endIndex + 1 - chars.length(), true)

  def matchedCharCount(thizz: CharSequence, chars: CharSequence, startIndex: Int, endIndex: Int, ignoreCase: Boolean): Int =
    matchedCharCount(thizz, chars, startIndex, Integer.MAX_VALUE, fullMatchOnly = false, ignoreCase)
  def matchedCharCount(thizz: CharSequence, chars: CharSequence, startIndex: Int, ignoreCase: Boolean): Int =
    matchedCharCount(thizz, chars, startIndex, Integer.MAX_VALUE, fullMatchOnly = false, ignoreCase)
  def matchedCharCount(thizz: CharSequence, chars: CharSequence, startIndex: Int, endIndex: Int): Int =
    matchedCharCount(thizz, chars, startIndex, Integer.MAX_VALUE, fullMatchOnly = false, ignoreCase = false)
  def matchedCharCount(thizz: CharSequence, chars: CharSequence, startIndex: Int): Int = matchedCharCount(thizz, chars, startIndex, Integer.MAX_VALUE, fullMatchOnly = false, ignoreCase = false)
  def matchedCharCountIgnoreCase(thizz: CharSequence, chars: CharSequence, startIndex: Int, endIndex: Int): Int =
    matchedCharCount(thizz, chars, startIndex, Integer.MAX_VALUE, fullMatchOnly = false, ignoreCase = true)
  def matchedCharCountIgnoreCase(thizz: CharSequence, chars: CharSequence, startIndex: Int): Int =
    matchedCharCount(thizz, chars, startIndex, Integer.MAX_VALUE, fullMatchOnly = false, ignoreCase = true)

  def matchedCharCountReversed(thizz: CharSequence, chars: CharSequence, startIndex: Int, fromIndex: Int): Int = matchedCharCountReversed(thizz, chars, startIndex, fromIndex, ignoreCase = false)
  def matchedCharCountReversedIgnoreCase(thizz: CharSequence, chars: CharSequence, startIndex: Int, fromIndex: Int): Int =
    matchedCharCountReversed(thizz, chars, startIndex, fromIndex, ignoreCase = true)

  def matchedCharCountReversed(thizz:           CharSequence, chars: CharSequence, fromIndex: Int, ignoreCase: Boolean): Int = matchedCharCountReversed(thizz, chars, 0, fromIndex, ignoreCase)
  def matchedCharCountReversed(thizz:           CharSequence, chars: CharSequence, fromIndex: Int):                      Int = matchedCharCountReversed(thizz, chars, 0, fromIndex, ignoreCase = false)
  def matchedCharCountReversedIgnoreCase(thizz: CharSequence, chars: CharSequence, fromIndex: Int):                      Int = matchedCharCountReversed(thizz, chars, 0, fromIndex, ignoreCase = true)

  def matchedCharCount(thizz: CharSequence, chars: CharSequence, startIndex: Int, endIndex: Int, fullMatchOnly: Boolean, ignoreCase: Boolean): Int = {
    val length = chars.length()
    val ei     = Math.min(thizz.length(), endIndex)
    val iMax   = Math.min(ei - startIndex, length)
    if (fullMatchOnly && iMax < length) 0
    else {
      boundary {
        if (ignoreCase) {
          var i = 0
          while (i < iMax) {
            val c1 = chars.charAt(i)
            val c2 = thizz.charAt(i + startIndex)
            if (c1 != c2) {
              val u1 = Character.toUpperCase(c1)
              val u2 = Character.toUpperCase(c2)
              if (u1 != u2) {
                // Unfortunately, conversion to uppercase does not work properly
                // for the Georgian alphabet, which has strange rules about case
                // conversion. So we need to make one last check before exiting.
                if (Character.toLowerCase(u1) != Character.toLowerCase(u2)) {
                  break(i)
                }
              }
            }
            i += 1
          }
        } else {
          var i = 0
          while (i < iMax) {
            if (chars.charAt(i) != thizz.charAt(i + startIndex)) break(i)
            i += 1
          }
        }
        iMax
      }
    }
  }

  // TEST:
  def matchedCharCountReversed(thizz: CharSequence, chars: CharSequence, startIndex: Int, fromIndex: Int, ignoreCase: Boolean): Int = {
    val si = Math.max(0, startIndex)
    val fi = Math.max(0, Math.min(thizz.length(), fromIndex))

    val length = chars.length()
    val iMax   = Math.min(fi - si, length)

    val offset = fi - iMax
    boundary {
      if (ignoreCase) {
        var i = iMax
        while ({ i -= 1; i >= 0 }) {
          val c1 = chars.charAt(i)
          val c2 = thizz.charAt(offset + i)
          if (c1 != c2) {
            val u1 = Character.toUpperCase(c1)
            val u2 = Character.toUpperCase(c2)
            if (u1 != u2) {
              // Unfortunately, conversion to uppercase does not work properly
              // for the Georgian alphabet, which has strange rules about case
              // conversion.  So we need to make one last check before exiting.
              if (Character.toLowerCase(u1) != Character.toLowerCase(u2)) {
                break(iMax - i - 1)
              }
            }
          }
        }
      } else {
        var i = iMax
        while ({ i -= 1; i >= 0 })
          if (chars.charAt(i) != thizz.charAt(offset + i)) break(iMax - i - 1)
      }
      iMax
    }
  }

  // countOfSpaceTab, countOfNotSpaceTab, etc.
  def countOfSpaceTab(thizz:    CharSequence): Int = countOfAny(thizz, CharPredicate.SPACE_TAB, 0, Integer.MAX_VALUE)
  def countOfNotSpaceTab(thizz: CharSequence): Int = countOfAny(thizz, CharPredicate.SPACE_TAB.negate(), 0, Integer.MAX_VALUE)

  def countOfWhitespace(thizz:    CharSequence): Int = countOfAny(thizz, CharPredicate.WHITESPACE, Integer.MAX_VALUE)
  def countOfNotWhitespace(thizz: CharSequence): Int = countOfAny(thizz, CharPredicate.WHITESPACE.negate(), 0, Integer.MAX_VALUE)

  def countOfAny(thizz: CharSequence, chars: CharPredicate, fromIndex: Int): Int = countOfAny(thizz, chars, fromIndex, Integer.MAX_VALUE)
  def countOfAny(thizz: CharSequence, chars: CharPredicate):                 Int = countOfAny(thizz, chars, 0, Integer.MAX_VALUE)

  def countOfAnyNot(thizz: CharSequence, chars: CharPredicate, fromIndex: Int, endIndex: Int): Int = countOfAny(thizz, chars.negate(), fromIndex, endIndex)
  def countOfAnyNot(thizz: CharSequence, chars: CharPredicate, fromIndex: Int):                Int = countOfAny(thizz, chars.negate(), fromIndex, Integer.MAX_VALUE)
  def countOfAnyNot(thizz: CharSequence, chars: CharPredicate):                                Int = countOfAny(thizz, chars.negate(), 0, Integer.MAX_VALUE)

  def countOfAny(thizz: CharSequence, s: CharPredicate, fromIndex: Int, endIndex: Int): Int = {
    val start = Math.max(fromIndex, 0)
    val end   = Math.min(endIndex, thizz.length())
    var count = 0
    var i     = start
    while (i < end) {
      val c = thizz.charAt(i)
      if (s.test(c)) count += 1
      i += 1
    }
    count
  }

  // countLeadingSpace and variants
  def countLeadingSpace(thizz:    CharSequence):                                 Int = countLeading(thizz, CharPredicate.SPACE, 0, Integer.MAX_VALUE)
  def countLeadingSpace(thizz:    CharSequence, startIndex: Int):                Int = countLeading(thizz, CharPredicate.SPACE, startIndex, Integer.MAX_VALUE)
  def countLeadingSpace(thizz:    CharSequence, startIndex: Int, endIndex: Int): Int = countLeading(thizz, CharPredicate.SPACE, startIndex, endIndex)
  def countLeadingNotSpace(thizz: CharSequence):                                 Int = countLeading(thizz, CharPredicate.SPACE.negate(), 0, Integer.MAX_VALUE)
  def countLeadingNotSpace(thizz: CharSequence, startIndex: Int):                Int = countLeading(thizz, CharPredicate.SPACE.negate(), startIndex, Integer.MAX_VALUE)
  def countLeadingNotSpace(thizz: CharSequence, startIndex: Int, endIndex: Int): Int = countLeading(thizz, CharPredicate.SPACE.negate(), startIndex, endIndex)

  def countTrailingSpace(thizz:    CharSequence):                                  Int = countTrailing(thizz, CharPredicate.SPACE, 0, Integer.MAX_VALUE)
  def countTrailingSpace(thizz:    CharSequence, fromIndex:  Int):                 Int = countTrailing(thizz, CharPredicate.SPACE, 0, fromIndex)
  def countTrailingSpace(thizz:    CharSequence, startIndex: Int, fromIndex: Int): Int = countTrailing(thizz, CharPredicate.SPACE, startIndex, fromIndex)
  def countTrailingNotSpace(thizz: CharSequence):                                  Int = countTrailing(thizz, CharPredicate.SPACE.negate(), 0, Integer.MAX_VALUE)
  def countTrailingNotSpace(thizz: CharSequence, fromIndex:  Int):                 Int = countTrailing(thizz, CharPredicate.SPACE.negate(), 0, fromIndex)
  def countTrailingNotSpace(thizz: CharSequence, startIndex: Int, fromIndex: Int): Int = countTrailing(thizz, CharPredicate.SPACE.negate(), startIndex, fromIndex)

  def countLeadingSpaceTab(thizz:    CharSequence):                                 Int = countLeading(thizz, CharPredicate.SPACE_TAB, 0, Integer.MAX_VALUE)
  def countLeadingSpaceTab(thizz:    CharSequence, startIndex: Int):                Int = countLeading(thizz, CharPredicate.SPACE_TAB, startIndex, Integer.MAX_VALUE)
  def countLeadingSpaceTab(thizz:    CharSequence, startIndex: Int, endIndex: Int): Int = countLeading(thizz, CharPredicate.SPACE_TAB, startIndex, endIndex)
  def countLeadingNotSpaceTab(thizz: CharSequence):                                 Int = countLeading(thizz, CharPredicate.SPACE_TAB.negate(), 0, Integer.MAX_VALUE)
  def countLeadingNotSpaceTab(thizz: CharSequence, startIndex: Int):                Int = countLeading(thizz, CharPredicate.SPACE_TAB.negate(), startIndex, Integer.MAX_VALUE)
  def countLeadingNotSpaceTab(thizz: CharSequence, startIndex: Int, endIndex: Int): Int = countLeading(thizz, CharPredicate.SPACE_TAB.negate(), startIndex, endIndex)

  def countTrailingSpaceTab(thizz:    CharSequence):                                  Int = countTrailing(thizz, CharPredicate.SPACE_TAB, 0, Integer.MAX_VALUE)
  def countTrailingSpaceTab(thizz:    CharSequence, fromIndex:  Int):                 Int = countTrailing(thizz, CharPredicate.SPACE_TAB, 0, fromIndex)
  def countTrailingSpaceTab(thizz:    CharSequence, startIndex: Int, fromIndex: Int): Int = countTrailing(thizz, CharPredicate.SPACE_TAB, startIndex, fromIndex)
  def countTrailingNotSpaceTab(thizz: CharSequence):                                  Int = countTrailing(thizz, CharPredicate.SPACE_TAB.negate(), 0, Integer.MAX_VALUE)
  def countTrailingNotSpaceTab(thizz: CharSequence, fromIndex:  Int):                 Int = countTrailing(thizz, CharPredicate.SPACE_TAB.negate(), 0, fromIndex)
  def countTrailingNotSpaceTab(thizz: CharSequence, startIndex: Int, fromIndex: Int): Int = countTrailing(thizz, CharPredicate.SPACE_TAB.negate(), startIndex, fromIndex)

  def countLeadingWhitespace(thizz:    CharSequence):                                 Int = countLeading(thizz, CharPredicate.WHITESPACE, 0, Integer.MAX_VALUE)
  def countLeadingWhitespace(thizz:    CharSequence, startIndex: Int):                Int = countLeading(thizz, CharPredicate.WHITESPACE, startIndex, Integer.MAX_VALUE)
  def countLeadingWhitespace(thizz:    CharSequence, startIndex: Int, endIndex: Int): Int = countLeading(thizz, CharPredicate.WHITESPACE, startIndex, endIndex)
  def countLeadingNotWhitespace(thizz: CharSequence):                                 Int = countLeading(thizz, CharPredicate.WHITESPACE.negate(), 0, Integer.MAX_VALUE)
  def countLeadingNotWhitespace(thizz: CharSequence, startIndex: Int):                Int = countLeading(thizz, CharPredicate.WHITESPACE.negate(), startIndex, Integer.MAX_VALUE)
  def countLeadingNotWhitespace(thizz: CharSequence, startIndex: Int, endIndex: Int): Int = countLeading(thizz, CharPredicate.WHITESPACE.negate(), startIndex, endIndex)

  def countTrailingWhitespace(thizz:    CharSequence):                                  Int = countTrailing(thizz, CharPredicate.WHITESPACE, 0, Integer.MAX_VALUE)
  def countTrailingWhitespace(thizz:    CharSequence, fromIndex:  Int):                 Int = countTrailing(thizz, CharPredicate.WHITESPACE, 0, fromIndex)
  def countTrailingWhitespace(thizz:    CharSequence, startIndex: Int, fromIndex: Int): Int = countTrailing(thizz, CharPredicate.WHITESPACE, startIndex, fromIndex)
  def countTrailingNotWhitespace(thizz: CharSequence):                                  Int = countTrailing(thizz, CharPredicate.WHITESPACE.negate(), 0, Integer.MAX_VALUE)
  def countTrailingNotWhitespace(thizz: CharSequence, fromIndex:  Int):                 Int = countTrailing(thizz, CharPredicate.WHITESPACE.negate(), 0, fromIndex)
  def countTrailingNotWhitespace(thizz: CharSequence, startIndex: Int, fromIndex: Int): Int = countTrailing(thizz, CharPredicate.WHITESPACE.negate(), startIndex, fromIndex)

  def countLeading(thizz:    CharSequence, chars: CharPredicate):                 Int = countLeading(thizz, chars, 0, Integer.MAX_VALUE)
  def countLeading(thizz:    CharSequence, chars: CharPredicate, fromIndex: Int): Int = countLeading(thizz, chars, fromIndex, Integer.MAX_VALUE)
  def countLeadingNot(thizz: CharSequence, chars: CharPredicate):                 Int = countLeading(thizz, chars.negate(), 0, Integer.MAX_VALUE)
  def countLeadingNot(thizz: CharSequence, chars: CharPredicate, fromIndex: Int): Int = countLeading(thizz, chars.negate(), fromIndex, Integer.MAX_VALUE)

  def countTrailing(thizz:    CharSequence, chars: CharPredicate):                 Int = countTrailing(thizz, chars, 0, Integer.MAX_VALUE)
  def countTrailing(thizz:    CharSequence, chars: CharPredicate, fromIndex: Int): Int = countTrailing(thizz, chars, 0, fromIndex)
  def countTrailingNot(thizz: CharSequence, chars: CharPredicate):                 Int = countTrailing(thizz, chars.negate(), 0, Integer.MAX_VALUE)
  def countTrailingNot(thizz: CharSequence, chars: CharPredicate, fromIndex: Int): Int = countTrailing(thizz, chars.negate(), 0, fromIndex)

  def countLeadingNot(thizz:  CharSequence, chars: CharPredicate, startIndex: Int, endIndex: Int): Int = countLeading(thizz, chars.negate(), startIndex, endIndex)
  def countTrailingNot(thizz: CharSequence, chars: CharPredicate, startIndex: Int, endIndex: Int): Int = countTrailing(thizz, chars.negate(), startIndex, endIndex)

  def countLeading(thizz: CharSequence, chars: CharPredicate, fromIndex: Int, endIndex: Int): Int = {
    val ei    = Math.min(endIndex, thizz.length())
    val fi    = Utils.rangeLimit(fromIndex, 0, ei)
    val index = indexOfAnyNot(thizz, chars, fi, ei)
    if (index == -1) ei - fi else index - fi
  }

  def countLeadingColumns(thizz: CharSequence, startColumn: Int, chars: CharPredicate): Int = {
    val fromIndex = 0
    val endIndex  = thizz.length()
    val index     = indexOfAnyNot(thizz, chars, fromIndex, endIndex)

    // expand tabs
    val end     = if (index == -1) endIndex else index
    var columns = if (index == -1) endIndex - fromIndex else index - fromIndex
    var tab     = indexOf(thizz, '\t', fromIndex, end)
    if (tab != -1) {
      var delta = startColumn
      while (tab >= 0 && tab < endIndex) {
        delta += tab + columnsToNextTabStop(tab + delta)
        tab = indexOf(thizz, '\t', tab + 1)
      }
      columns += delta
    }
    columns
  }

  // TEST: this
  def countTrailing(thizz: CharSequence, chars: CharPredicate, startIndex: Int, fromIndex: Int): Int = {
    val fi    = Math.min(fromIndex, thizz.length())
    val si    = Utils.rangeLimit(startIndex, 0, fi)
    val index = lastIndexOfAnyNot(thizz, chars, si, fi - 1)
    if (index == -1) fi - si else if (fi <= index) 0 else fi - index - 1
  }

  // trim methods
  def trimStart[T <: CharSequence](thizz:    T, chars: CharPredicate):             CharSequence                     = subSequence(thizz, trimStartRange(thizz, 0, chars))
  def trimmedStart[T <: CharSequence](thizz: T, chars: CharPredicate):             Nullable[CharSequence]           = trimmedStart(thizz, 0, chars)
  def trimEnd[T <: CharSequence](thizz:      T, chars: CharPredicate):             CharSequence                     = trimEnd(thizz, 0, chars)
  def trimmedEnd[T <: CharSequence](thizz:   T, chars: CharPredicate):             Nullable[CharSequence]           = trimmedEnd(thizz, 0, chars)
  def trim[T <: CharSequence](thizz:         T, chars: CharPredicate):             CharSequence                     = trim(thizz, 0, chars)
  def trimmed[T <: CharSequence](thizz:      T, chars: CharPredicate):             Pair[CharSequence, CharSequence] = trimmed(thizz, 0, chars)
  def trimStart[T <: CharSequence](thizz:    T, keep:  Int):                       CharSequence                     = trimStart(thizz, keep, CharPredicate.WHITESPACE)
  def trimmedStart[T <: CharSequence](thizz: T, keep:  Int):                       Nullable[CharSequence]           = trimmedStart(thizz, keep, CharPredicate.WHITESPACE)
  def trimEnd[T <: CharSequence](thizz:      T, keep:  Int):                       CharSequence                     = trimEnd(thizz, keep, CharPredicate.WHITESPACE)
  def trimmedEnd[T <: CharSequence](thizz:   T, keep:  Int):                       Nullable[CharSequence]           = trimmedEnd(thizz, keep, CharPredicate.WHITESPACE)
  def trim[T <: CharSequence](thizz:         T, keep:  Int):                       CharSequence                     = trim(thizz, keep, CharPredicate.WHITESPACE)
  def trimmed[T <: CharSequence](thizz:      T, keep:  Int):                       Pair[CharSequence, CharSequence] = trimmed(thizz, keep, CharPredicate.WHITESPACE)
  def trimStart[T <: CharSequence](thizz:    T):                                   CharSequence                     = trimStart(thizz, 0, CharPredicate.WHITESPACE)
  def trimmedStart[T <: CharSequence](thizz: T):                                   Nullable[CharSequence]           = trimmedStart(thizz, 0, CharPredicate.WHITESPACE)
  def trimEnd[T <: CharSequence](thizz:      T):                                   CharSequence                     = trimEnd(thizz, 0, CharPredicate.WHITESPACE)
  def trimmedEnd[T <: CharSequence](thizz:   T):                                   Nullable[CharSequence]           = trimmedEnd(thizz, 0, CharPredicate.WHITESPACE)
  def trim[T <: CharSequence](thizz:         T):                                   CharSequence                     = trim(thizz, 0, CharPredicate.WHITESPACE)
  def trimmed[T <: CharSequence](thizz:      T):                                   Pair[CharSequence, CharSequence] = trimmed(thizz, 0, CharPredicate.WHITESPACE)
  def trimStart[T <: CharSequence](thizz:    T, keep:  Int, chars: CharPredicate): CharSequence                     = subSequence(thizz, trimStartRange(thizz, keep, chars))
  def trimmedStart[T <: CharSequence](thizz: T, keep:  Int, chars: CharPredicate): Nullable[CharSequence]           = subSequenceBefore(thizz, trimStartRange(thizz, keep, chars))
  def trimEnd[T <: CharSequence](thizz:      T, keep:  Int, chars: CharPredicate): CharSequence                     = subSequence(thizz, trimEndRange(thizz, keep, chars))
  def trimmedEnd[T <: CharSequence](thizz:   T, keep:  Int, chars: CharPredicate): Nullable[CharSequence]           = subSequenceAfter(thizz, trimEndRange(thizz, keep, chars))
  def trim[T <: CharSequence](thizz:         T, keep:  Int, chars: CharPredicate): CharSequence                     = subSequence(thizz, trimRange(thizz, keep, chars))
  def trimmed[T <: CharSequence](thizz:      T, keep:  Int, chars: CharPredicate): Pair[CharSequence, CharSequence] = subSequenceBeforeAfter(thizz, trimRange(thizz, keep, chars))

  // trimRange methods
  def trimStartRange(thizz: CharSequence, chars: CharPredicate): Range = trimStartRange(thizz, 0, chars)
  def trimEndRange(thizz:   CharSequence, chars: CharPredicate): Range = trimEndRange(thizz, 0, chars)
  def trimRange(thizz:      CharSequence, chars: CharPredicate): Range = trimRange(thizz, 0, chars)
  def trimStartRange(thizz: CharSequence, keep:  Int):           Range = trimStartRange(thizz, keep, CharPredicate.WHITESPACE)
  def trimEndRange(thizz:   CharSequence, keep:  Int):           Range = trimEndRange(thizz, keep, CharPredicate.WHITESPACE)
  def trimRange(thizz:      CharSequence, keep:  Int):           Range = trimRange(thizz, keep, CharPredicate.WHITESPACE)
  def trimStartRange(thizz: CharSequence):                       Range = trimStartRange(thizz, 0, CharPredicate.WHITESPACE)
  def trimEndRange(thizz:   CharSequence):                       Range = trimEndRange(thizz, 0, CharPredicate.WHITESPACE)
  def trimRange(thizz:      CharSequence):                       Range = trimRange(thizz, 0, CharPredicate.WHITESPACE)

  def trimStartRange(thizz: CharSequence, keep: Int, chars: CharPredicate): Range = {
    val length    = thizz.length()
    val trimCount = countLeading(thizz, chars, 0, length)
    if (trimCount > keep) Range.of(trimCount - keep, length) else Range.NULL
  }

  def trimEndRange(thizz: CharSequence, keep: Int, chars: CharPredicate): Range = {
    val length    = thizz.length()
    val trimCount = countTrailing(thizz, chars, 0, length)
    if (trimCount > keep) Range.of(0, length - trimCount + keep) else Range.NULL
  }

  def trimRange(thizz: CharSequence, keep: Int, chars: CharPredicate): Range = {
    val length = thizz.length()
    if (keep >= length) Range.NULL
    else {
      val trimStart = countLeading(thizz, chars, 0, length)
      if (trimStart > keep) {
        val trimEnd = countTrailing(thizz, chars, trimStart - keep, length)
        if (trimEnd > keep) Range.of(trimStart - keep, length - trimEnd + keep) else Range.of(trimStart - keep, length)
      } else {
        val trimEnd = countTrailing(thizz, chars, trimStart, length)
        if (trimEnd > keep) Range.of(0, length - trimEnd + keep) else Range.NULL
      }
    }
  }

  def padStart(thizz: CharSequence, length: Int, pad: Char): String =
    if (length <= thizz.length()) "" else RepeatedSequence.repeatOf(pad, length - thizz.length()).toString

  def padEnd(thizz: CharSequence, length: Int, pad: Char): String =
    if (length <= thizz.length()) "" else RepeatedSequence.repeatOf(pad, length - thizz.length()).toString

  def padStart(thizz: CharSequence, length: Int): String = padStart(thizz, length, ' ')

  def padEnd(thizz: CharSequence, length: Int): String = padEnd(thizz, length, ' ')

  def toVisibleWhitespaceString(thizz: CharSequence): String = {
    val sb   = new StringBuilder()
    val iMax = thizz.length()
    var i    = 0
    while (i < iMax) {
      val c = thizz.charAt(i)
      val s = SequenceUtils.visibleSpacesMap.get(c)
      if (s != null) sb.append(s) // Java Map.get returns null
      else sb.append(c)
      i += 1
    }
    sb.toString()
  }

  // *****************************************************************
  // EOL Helpers
  // *****************************************************************

  def lastChar(thizz: CharSequence): Char =
    if (thizz.length() == 0) SequenceUtils.NUL else thizz.charAt(thizz.length() - 1)

  def firstChar(thizz: CharSequence): Char =
    if (thizz.length() == 0) SequenceUtils.NUL else thizz.charAt(0)

  def safeCharAt(thizz: CharSequence, index: Int): Char =
    if (index < 0 || index >= thizz.length()) SequenceUtils.NUL else thizz.charAt(index)

  def eolEndLength(thizz: CharSequence): Int = eolEndLength(thizz, thizz.length())

  def eolEndLength(thizz: CharSequence, eolEnd: Int): Int = {
    val pos = Math.min(eolEnd - 1, thizz.length() - 1)
    if (pos < 0) 0
    else {
      val c = thizz.charAt(pos)
      if (c == '\r') {
        if (safeCharAt(thizz, pos + 1) != '\n') 1 else 0
      } else if (c == '\n') {
        if (safeCharAt(thizz, pos - 1) == '\r') 2 else 1
      } else {
        0
      }
    }
  }

  def eolStartLength(thizz: CharSequence, eolStart: Int): Int = {
    val length = thizz.length()
    val pos    = Math.min(eolStart, length)

    if (pos >= 0 && pos < length) {
      val c = thizz.charAt(pos)
      if (c == '\r') {
        if (safeCharAt(thizz, pos + 1) == '\n') 2 else 1
      } else if (c == '\n') {
        if (safeCharAt(thizz, pos - 1) != '\r') 1 else 0
      } else {
        0
      }
    } else {
      0
    }
  }

  def endOfLine(thizz:         CharSequence, index: Int): Int = endOfDelimitedBy(thizz, SequenceUtils.EOL, index)
  def endOfLineAnyEOL(thizz:   CharSequence, index: Int): Int = endOfDelimitedByAny(thizz, CharPredicate.ANY_EOL, index)
  def startOfLine(thizz:       CharSequence, index: Int): Int = startOfDelimitedBy(thizz, SequenceUtils.EOL, index)
  def startOfLineAnyEOL(thizz: CharSequence, index: Int): Int = startOfDelimitedByAny(thizz, CharPredicate.ANY_EOL, index)

  def startOfDelimitedByAnyNot(thizz: CharSequence, s: CharPredicate, index: Int): Int = startOfDelimitedByAny(thizz, s.negate(), index)
  def endOfDelimitedByAnyNot(thizz:   CharSequence, s: CharPredicate, index: Int): Int = endOfDelimitedByAny(thizz, s.negate(), index)

  def startOfDelimitedBy(thizz: CharSequence, s: CharSequence, index: Int): Int = {
    val idx    = Utils.rangeLimit(index, 0, thizz.length())
    val offset = lastIndexOf(thizz, s, idx - 1)
    if (offset == -1) 0 else offset + 1
  }

  def startOfDelimitedByAny(thizz: CharSequence, s: CharPredicate, index: Int): Int = {
    val idx    = Utils.rangeLimit(index, 0, thizz.length())
    val offset = lastIndexOfAny(thizz, s, idx - 1)
    if (offset == -1) 0 else offset + 1
  }

  def endOfDelimitedBy(thizz: CharSequence, s: CharSequence, index: Int): Int = {
    val length = thizz.length()
    val idx    = Utils.rangeLimit(index, 0, length)
    val offset = indexOf(thizz, s, idx)
    if (offset == -1) length else offset
  }

  def endOfDelimitedByAny(thizz: CharSequence, s: CharPredicate, index: Int): Int = {
    val length = thizz.length()
    val idx    = Utils.rangeLimit(index, 0, length)
    val offset = indexOfAny(thizz, s, idx)
    if (offset == -1) length else offset
  }

  def lineRangeAt(thizz: CharSequence, index: Int): Range =
    Range.of(startOfLine(thizz, index), endOfLine(thizz, index))

  def lineRangeAtAnyEOL(thizz: CharSequence, index: Int): Range =
    Range.of(startOfLineAnyEOL(thizz, index), endOfLineAnyEOL(thizz, index))

  def eolEndRange(thizz: CharSequence, eolEnd: Int): Range = {
    val eolLength = eolEndLength(thizz, eolEnd)
    if (eolLength == 0) Range.NULL else Range.of(eolEnd - eolLength, eolEnd)
  }

  def eolStartRange(thizz: CharSequence, eolStart: Int): Range = {
    val eolLength = eolStartLength(thizz, eolStart)
    if (eolLength == 0) Range.NULL else Range.of(eolStart, eolStart + eolLength)
  }

  def trimEOL[T <: CharSequence](thizz: T): CharSequence = {
    val eolLength = eolEndLength(thizz)
    if (eolLength > 0) thizz.subSequence(0, thizz.length() - eolLength) else thizz
  }

  def trimmedEOL[T <: CharSequence](thizz: T): Nullable[CharSequence] = {
    val eolLength = eolEndLength(thizz)
    if (eolLength > 0) Nullable(thizz.subSequence(thizz.length() - eolLength, thizz.length()))
    else Nullable.empty
  }

  def trimTailBlankLines[T <: CharSequence](thizz: T): Nullable[CharSequence] = {
    val range = trailingBlankLinesRange(thizz)
    if (range.isNull) Nullable(thizz) else subSequenceBefore(thizz, range)
  }

  def trimLeadBlankLines[T <: CharSequence](thizz: T): Nullable[CharSequence] = {
    val range = leadingBlankLinesRange(thizz)
    if (range.isNull) Nullable(thizz) else subSequenceAfter(thizz, range)
  }

  def leadingBlankLinesRange(thizz:  CharSequence):                                  Range = leadingBlankLinesRange(thizz, CharPredicate.EOL, 0, Integer.MAX_VALUE)
  def leadingBlankLinesRange(thizz:  CharSequence, startIndex: Int):                 Range = leadingBlankLinesRange(thizz, CharPredicate.EOL, startIndex, Integer.MAX_VALUE)
  def leadingBlankLinesRange(thizz:  CharSequence, fromIndex:  Int, endIndex:  Int): Range = leadingBlankLinesRange(thizz, CharPredicate.EOL, fromIndex, endIndex)
  def trailingBlankLinesRange(thizz: CharSequence):                                  Range = trailingBlankLinesRange(thizz, CharPredicate.EOL, 0, Integer.MAX_VALUE)
  def trailingBlankLinesRange(thizz: CharSequence, fromIndex:  Int):                 Range = trailingBlankLinesRange(thizz, CharPredicate.EOL, fromIndex, Integer.MAX_VALUE)
  def trailingBlankLinesRange(thizz: CharSequence, startIndex: Int, fromIndex: Int): Range = trailingBlankLinesRange(thizz, CharPredicate.EOL, startIndex, fromIndex)

  def trailingBlankLinesRange(thizz: CharSequence, eolChars: CharPredicate, startIndex: Int, fromIndex: Int): Range = {
    val fi = Math.min(fromIndex, thizz.length())
    val si = Utils.rangeLimit(startIndex, 0, fi)

    val iMax    = fi
    var lastEOL = iMax

    boundary {
      var i = iMax
      while ({ i -= 1; i >= si }) {
        val c = thizz.charAt(i)
        if (eolChars.test(c)) lastEOL = Math.min(i + Math.min(eolStartLength(thizz, i), 1), fi)
        else if (c != ' ' && c != '\t') {
          if (lastEOL != iMax) break(Range.of(lastEOL, fi))
          else break(Range.NULL)
        }
      }
      Range.of(si, fi)
    }
  }

  def leadingBlankLinesRange(thizz: CharSequence, eolChars: CharPredicate, fromIndex: Int, endIndex: Int): Range = {
    val ei = Math.min(endIndex, thizz.length())
    val fi = Utils.rangeLimit(fromIndex, 0, ei)

    val iMax    = ei
    var lastEOL = -1

    boundary {
      var i = fi
      while (i < iMax) {
        val c = thizz.charAt(i)
        if (eolChars.test(c)) lastEOL = i
        else if (c != ' ' && c != '\t') {
          if (lastEOL >= 0) break(Range.of(fi, Math.min(lastEOL + Math.min(eolStartLength(thizz, lastEOL), 1), ei)))
          else break(Range.NULL)
        }
        i += 1
      }
      Range.of(fi, ei)
    }
  }

  def blankLinesRemovedRanges(thizz: CharSequence):                                ju.List[Range] = blankLinesRemovedRanges(thizz, CharPredicate.EOL, 0, Integer.MAX_VALUE)
  def blankLinesRemovedRanges(thizz: CharSequence, fromIndex: Int):                ju.List[Range] = blankLinesRemovedRanges(thizz, CharPredicate.EOL, fromIndex, Integer.MAX_VALUE)
  def blankLinesRemovedRanges(thizz: CharSequence, fromIndex: Int, endIndex: Int): ju.List[Range] = blankLinesRemovedRanges(thizz, CharPredicate.EOL, fromIndex, endIndex)

  def blankLinesRemovedRanges(thizz: CharSequence, eolChars: CharPredicate, fromIndex: Int, endIndex: Int): ju.List[Range] = {
    val ei      = Math.min(endIndex, thizz.length())
    val fi      = Utils.rangeLimit(fromIndex, 0, ei)
    var lastPos = fi
    val ranges  = new ju.ArrayList[Range]()

    while (lastPos < ei) {
      val blankLines = leadingBlankLinesRange(thizz, eolChars, lastPos, ei)
      if (blankLines.isNull) {
        val endOfL = Math.min(endOfLine(thizz, lastPos) + 1, ei)
        if (lastPos < endOfL) ranges.add(Range.of(lastPos, endOfL))
        lastPos = endOfL
      } else {
        if (lastPos < blankLines.start) ranges.add(Range.of(lastPos, blankLines.start))
        lastPos = blankLines.end
      }
    }
    ranges
  }

  def isEmpty(thizz:    CharSequence): Boolean = thizz.length() == 0
  def isBlank(thizz:    CharSequence): Boolean = isEmpty(thizz) || countLeading(thizz, CharPredicate.WHITESPACE, 0, Integer.MAX_VALUE) == thizz.length()
  def isNotEmpty(thizz: CharSequence): Boolean = thizz.length() != 0
  def isNotBlank(thizz: CharSequence): Boolean = !isBlank(thizz)

  def endsWith(thizz:   CharSequence, suffix: CharSequence):                      Boolean = thizz.length() > 0 && matchCharsReversed(thizz, suffix, thizz.length() - 1, false)
  def endsWith(thizz:   CharSequence, suffix: CharSequence, ignoreCase: Boolean): Boolean = thizz.length() > 0 && matchCharsReversed(thizz, suffix, thizz.length() - 1, ignoreCase)
  def startsWith(thizz: CharSequence, prefix: CharSequence):                      Boolean = thizz.length() > 0 && matchChars(thizz, prefix, 0, false)
  def startsWith(thizz: CharSequence, prefix: CharSequence, ignoreCase: Boolean): Boolean = thizz.length() > 0 && matchChars(thizz, prefix, 0, ignoreCase)

  def endsWith(thizz:   CharSequence, chars: CharPredicate): Boolean = countTrailing(thizz, chars) > 0
  def startsWith(thizz: CharSequence, chars: CharPredicate): Boolean = countLeading(thizz, chars) > 0

  def endsWithEOL(thizz:        CharSequence): Boolean = endsWith(thizz, CharPredicate.EOL)
  def endsWithAnyEOL(thizz:     CharSequence): Boolean = endsWith(thizz, CharPredicate.ANY_EOL)
  def endsWithSpace(thizz:      CharSequence): Boolean = endsWith(thizz, CharPredicate.SPACE)
  def endsWithSpaceTab(thizz:   CharSequence): Boolean = endsWith(thizz, CharPredicate.SPACE_TAB)
  def endsWithWhitespace(thizz: CharSequence): Boolean = endsWith(thizz, CharPredicate.WHITESPACE)

  def startsWithEOL(thizz:        CharSequence): Boolean = startsWith(thizz, CharPredicate.EOL)
  def startsWithAnyEOL(thizz:     CharSequence): Boolean = startsWith(thizz, CharPredicate.ANY_EOL)
  def startsWithSpace(thizz:      CharSequence): Boolean = startsWith(thizz, CharPredicate.SPACE)
  def startsWithSpaceTab(thizz:   CharSequence): Boolean = startsWith(thizz, CharPredicate.SPACE_TAB)
  def startsWithWhitespace(thizz: CharSequence): Boolean = startsWith(thizz, CharPredicate.WHITESPACE)

  // splitList overloads
  def splitList[T <: CharSequence](thizz: T, delimiter: CharSequence): ju.List[CharSequence] = splitList(thizz, delimiter, 0, 0, Nullable.empty)
  def splitList[T <: CharSequence](thizz: T, delimiter: CharSequence, limit: Int, includeDelims: Boolean, trimChars: Nullable[CharPredicate]): ju.List[CharSequence] =
    splitList(thizz, delimiter, limit, if (includeDelims) SequenceUtils.SPLIT_INCLUDE_DELIMS else 0, trimChars)
  def splitList[T <: CharSequence](thizz: T, delimiter: CharSequence, limit: Int, flags: Int): ju.List[CharSequence] = splitList(thizz, delimiter, limit, flags, Nullable.empty)
  def splitList[T <: CharSequence](thizz: T, delimiter: CharSequence, includeDelims: Boolean, trimChars: Nullable[CharPredicate]): ju.List[CharSequence] =
    splitList(thizz, delimiter, 0, if (includeDelims) SequenceUtils.SPLIT_INCLUDE_DELIMS else 0, trimChars)

  // NOTE: these default to including delimiters as part of split item
  def splitListEOL[T <: CharSequence](thizz: T):                         ju.List[CharSequence] = splitList(thizz, SequenceUtils.EOL, 0, SequenceUtils.SPLIT_INCLUDE_DELIMS, Nullable.empty)
  def splitListEOL[T <: CharSequence](thizz: T, includeDelims: Boolean): ju.List[CharSequence] =
    splitList(thizz, SequenceUtils.EOL, 0, if (includeDelims) SequenceUtils.SPLIT_INCLUDE_DELIMS else 0, Nullable.empty)
  def splitListEOL[T <: CharSequence](thizz: T, includeDelims: Boolean, trimChars: Nullable[CharPredicate]): ju.List[CharSequence] =
    splitList(thizz, SequenceUtils.EOL, 0, if (includeDelims) SequenceUtils.SPLIT_INCLUDE_DELIMS else 0, trimChars)

  def splitList[T <: CharSequence](thizz: T, delimiter: CharSequence, limit: Int, flags: Int, trimChars: Nullable[CharPredicate]): ju.List[CharSequence] = {
    val tc: CharPredicate = if (trimChars.isEmpty) CharPredicate.WHITESPACE else trimChars.get
    var fl = flags
    if (trimChars.isDefined) fl |= SPLIT_TRIM_PARTS

    val lim = if (limit < 1) Integer.MAX_VALUE else limit

    val includeDelimiterParts = (fl & SPLIT_INCLUDE_DELIM_PARTS) != 0
    val includeDelimiter      = if (!includeDelimiterParts && (fl & SPLIT_INCLUDE_DELIMS) != 0) delimiter.length() else 0
    val trimParts             = (fl & SPLIT_TRIM_PARTS) != 0
    val skipEmpty             = (fl & SPLIT_SKIP_EMPTY) != 0
    val items                 = new ju.ArrayList[CharSequence]()

    var lastPos = 0
    val length  = thizz.length()
    if (lim > 1) {
      var done = false
      while (lastPos < length && !done) {
        val pos = indexOf(thizz, delimiter, lastPos)
        if (pos < 0) done = true
        else {
          if (lastPos < pos || !skipEmpty) {
            var item: CharSequence = thizz.subSequence(lastPos, pos + includeDelimiter)
            if (trimParts) item = trim(item, tc)
            if (!isEmpty(item) || !skipEmpty) {
              items.add(item)
              if (includeDelimiterParts) {
                items.add(thizz.subSequence(pos, pos + delimiter.length()))
              }
              if (items.size() >= lim - 1) {
                lastPos = pos + 1
                done = true
              }
            }
          }
          if (!done) lastPos = pos + 1
        }
      }
    }

    if (lastPos < length) {
      var item: CharSequence = thizz.subSequence(lastPos, length)
      if (trimParts) item = trim(item, tc)
      if (!isEmpty(item) || !skipEmpty) {
        items.add(item)
      }
    }
    items
  }

  def columnAtIndex(thizz: CharSequence, index: Int): Int = {
    val lineStart = lastIndexOfAny(thizz, CharPredicate.ANY_EOL, index)
    index - (if (lineStart == -1) 0 else lineStart + eolStartLength(thizz, lineStart))
  }

  def lineColumnAtIndex(thizz: CharSequence, index: Int): Pair[Integer, Integer] = {
    val iMax = thizz.length()
    if (index < 0 || index > iMax) {
      throw new IllegalArgumentException("Index: " + index + " out of range [0, " + iMax + "]")
    }

    var hadCr = false
    var line  = 0
    var col   = 0
    var i     = 0
    while (i < index) {
      val c1 = thizz.charAt(i)
      if (c1 == '\r') {
        col = 0
        line += 1
        hadCr = true
      } else if (c1 == '\n') {
        if (!hadCr) line += 1
        col = 0
        hadCr = false
      } else {
        col += 1
      }
      i += 1
    }

    new Pair[Integer, Integer](Integer.valueOf(line), Integer.valueOf(col))
  }

  def validateIndex(index: Int, length: Int): Unit =
    if (index < 0 || index >= length) {
      throw new StringIndexOutOfBoundsException("String index: " + index + " out of range: [0, " + length + ")")
    }

  def validateIndexInclusiveEnd(index: Int, length: Int): Unit =
    if (index < 0 || index > length) {
      throw new StringIndexOutOfBoundsException("index: " + index + " out of range: [0, " + length + "]")
    }

  def validateStartEnd(startIndex: Int, endIndex: Int, length: Int): Unit = {
    if (startIndex < 0 || startIndex > length) {
      throw new StringIndexOutOfBoundsException("startIndex: " + startIndex + " out of range: [0, " + length + ")")
    }
    if (endIndex < startIndex || endIndex > length) {
      throw new StringIndexOutOfBoundsException("endIndex: " + endIndex + " out of range: [" + startIndex + ", " + length + "]")
    }
  }

  def parseUnsignedIntOrNull(text: String): Nullable[Integer] = parseUnsignedIntOrNull(text, 10)

  def parseUnsignedIntOrNull(text: String, radix: Int): Nullable[Integer] =
    try {
      val value = Integer.parseInt(text, radix)
      if (value >= 0) Nullable(Integer.valueOf(value)) else Nullable.empty
    } catch {
      case _: NumberFormatException => Nullable.empty
    }

  def parseIntOrNull(text: String): Nullable[Integer] = parseIntOrNull(text, 10)

  def parseIntOrNull(text: String, radix: Int): Nullable[Integer] =
    try
      Nullable(Integer.valueOf(Integer.parseInt(text, radix)))
    catch {
      case _: NumberFormatException => Nullable.empty
    }

  def parseLongOrNull(text: String): Nullable[java.lang.Long] = parseLongOrNull(text, 10)

  def parseLongOrNull(text: String, radix: Int): Nullable[java.lang.Long] =
    try
      Nullable(java.lang.Long.valueOf(java.lang.Long.parseLong(text, radix)))
    catch {
      case _: NumberFormatException => Nullable.empty
    }

  def parseUnsignedIntOrDefault(text: String, defaultValue: Int): Int = parseUnsignedIntOrDefault(text, defaultValue, 10)

  def parseUnsignedIntOrDefault(text: String, defaultValue: Int, radix: Int): Int =
    try {
      val value = Integer.parseInt(text, radix)
      if (value >= 0) value else defaultValue
    } catch {
      case _: NumberFormatException => defaultValue
    }

  def parseIntOrDefault(text: String, defaultValue: Int): Int = parseIntOrDefault(text, defaultValue, 10)

  def parseIntOrDefault(text: String, defaultValue: Int, radix: Int): Int =
    try
      Integer.parseInt(text, radix)
    catch {
      case _: NumberFormatException => defaultValue
    }

  /** Parse number from text
    *
    * Will parse 0x, 0b, octal if starts with 0, decimal
    *
    * @param text
    *   text containing the number to parse
    * @return
    *   null or parsed number
    */
  def parseNumberOrNull(text: Nullable[String]): Nullable[Number] =
    if (text.isEmpty) Nullable.empty
    else {
      val t = text.get
      if (t.startsWith("0x")) {
        parseLongOrNull(t.substring(2), 16).map(_.asInstanceOf[Number])
      } else if (t.startsWith("0b")) {
        parseLongOrNull(t.substring(2), 2).map(_.asInstanceOf[Number])
      } else {
        if (t.startsWith("0")) {
          val octal = parseLongOrNull(t.substring(1), 8)
          if (octal.isDefined) octal.map(_.asInstanceOf[Number])
          else parseNumberDecimal(t)
        } else {
          parseNumberDecimal(t)
        }
      }
    }

  private def parseNumberDecimal(t: String): Nullable[Number] = {
    val numberFormat = NumberFormat.getInstance()
    val pos          = new ParsePosition(0)
    val number       = numberFormat.parse(t, pos)
    if (pos.getIndex == t.length) Nullable(number) else Nullable.empty
  }

  /** Parse number from text
    *
    * Will parse 0x, 0b, octal if starts with 0, decimal
    *
    * @param text
    *   text containing the number to parse
    * @param suffixTester
    *   predicate to test number suffix, if null or predicate returns true then sequence will be accepted as valid
    * @return
    *   null or parsed number with unparsed suffix
    */
  def parseNumberPrefixOrNull(text: Nullable[String], suffixTester: Nullable[String => Boolean]): Nullable[Pair[Number, String]] =
    if (text.isEmpty) Nullable.empty
    else {
      val t = text.get
      if (t.startsWith("0x")) {
        val digits = countLeading(t.substring(2), CharPredicate.HEXADECIMAL_DIGITS)
        val suffix = t.substring(2 + digits)
        if (digits > 0 && (suffix.isEmpty || suffixTester.isEmpty || suffixTester.get(suffix))) {
          val num = parseLongOrNull(t.substring(2, 2 + digits), 16)
          if (num.isDefined) Nullable(Pair.of[Number, String](num.get, suffix)) else Nullable.empty
        } else Nullable.empty
      } else if (t.startsWith("0b")) {
        val digits = countLeading(t.substring(2), CharPredicate.BINARY_DIGITS)
        val suffix = t.substring(2 + digits)
        if (digits > 0 && (suffix.isEmpty || suffixTester.isEmpty || suffixTester.get(suffix))) {
          val num = parseLongOrNull(t.substring(2, 2 + digits), 2)
          if (num.isDefined) Nullable(Pair.of[Number, String](num.get, suffix)) else Nullable.empty
        } else Nullable.empty
      } else if (t.startsWith("0")) {
        val digits        = countLeading(t.substring(1), CharPredicate.OCTAL_DIGITS)
        val decimalDigits = countLeading(t.substring(1), CharPredicate.DECIMAL_DIGITS)
        if (digits == decimalDigits) {
          val suffix = t.substring(1 + digits)
          if (digits > 0 && (suffix.isEmpty || suffixTester.isEmpty || suffixTester.get(suffix))) {
            val num = parseLongOrNull(t.substring(1, 1 + digits), 8)
            if (num.isDefined) Nullable(Pair.of[Number, String](num.get, suffix)) else Nullable.empty
          } else parseNumberPrefixDecimal(t, suffixTester)
        } else parseNumberPrefixDecimal(t, suffixTester)
      } else {
        parseNumberPrefixDecimal(t, suffixTester)
      }
    }

  private def parseNumberPrefixDecimal(t: String, suffixTester: Nullable[String => Boolean]): Nullable[Pair[Number, String]] = {
    val numberFormat = NumberFormat.getInstance()
    val pos          = new ParsePosition(0)
    val number       = numberFormat.parse(t, pos)
    val suffix       = t.substring(pos.getIndex)
    if (pos.getIndex > 0 && (suffix.isEmpty || suffixTester.isEmpty || suffixTester.get(suffix))) {
      Nullable(Pair.of[Number, String](number, suffix))
    } else {
      Nullable.empty
    }
  }

  def containedBy[T <: CharSequence](items: Array[T], element: CharSequence): Boolean =
    boundary {
      for (item <- items)
        if (equals(element, item)) break(true)
      false
    }

  def containedBy(items: ju.Collection[? <: CharSequence], element: CharSequence): Boolean =
    boundary {
      val iter = items.iterator()
      while (iter.hasNext)
        if (equals(element, iter.next())) break(true)
      false
    }
}
