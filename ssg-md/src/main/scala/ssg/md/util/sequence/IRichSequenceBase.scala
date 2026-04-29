/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/IRichSequenceBase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/IRichSequenceBase.java
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
import ssg.md.util.sequence.builder.ISequenceBuilder
import ssg.md.util.sequence.mappers.{ ChangeCase, CharMapper, SpaceMapper }

import scala.language.implicitConversions

import java.util as ju
import scala.util.boundary
import scala.util.boundary.break

/** An abstract base for RichSequence which implements most of the methods allowing subclasses to implement RichSequence with minimal support methods
  */
@SuppressWarnings(Array("unchecked"))
abstract class IRichSequenceBase[T <: IRichSequence[T]](private var hash: Int) extends IRichSequence[T] {

  /** Helper to obtain a typed sequence builder without F-bounded type inference issues. In Scala 3 the F-bounded type parameter on [[IRichSequence.getBuilder]] infers as Nothing, making chained calls
    * impossible. This helper casts to an existential builder type.
    */
  @SuppressWarnings(Array("unchecked"))
  final protected def seqBuilder: ISequenceBuilder[?, T] = {
    val b: Any = getBuilder
    b.asInstanceOf[ISequenceBuilder[?, T]]
  }

  // ---- equals / hashCode / compareTo ----

  /** Equality comparison based on character content of this sequence, with quick fail resorting to content comparison only if length and hashCodes are equal
    */
  @SuppressWarnings(Array("unchecked"))
  final override def equals(o: Any): Boolean =
    SequenceUtils.equals(this, o)

  /** String hash code computation */
  final override def hashCode(): Int = {
    var h = hash
    if (h == 0 && length() > 0) {
      h = SequenceUtils.hashCode(this)
      hash = h
    }
    h
  }

  final def equalsIgnoreCase(other: Any): Boolean =
    (this eq other.asInstanceOf[AnyRef]) || (other.isInstanceOf[CharSequence] && other.asInstanceOf[CharSequence].length() == length() && matchChars(other.asInstanceOf[CharSequence], 0, true))

  final def equals(other: Any, ignoreCase: Boolean): Boolean =
    (this eq other.asInstanceOf[AnyRef]) || (other.isInstanceOf[CharSequence] && other.asInstanceOf[CharSequence].length() == length() && matchChars(other.asInstanceOf[CharSequence], 0, ignoreCase))

  override def compareTo(o: CharSequence): Int =
    SequenceUtils.compare(this, o)

  // ---- factory methods ----

  final override def sequenceOf(charSequence: Nullable[CharSequence]): T =
    if (!charSequence.isDefined) nullSequence() else sequenceOf(charSequence.get, 0, charSequence.get.length())

  final override def sequenceOf(charSequence: Nullable[CharSequence], startIndex: Int): T =
    if (!charSequence.isDefined) nullSequence() else sequenceOf(charSequence.get, startIndex, charSequence.get.length())

  // ---- subSequence helpers ----

  final override def subSequence(startIndex: Int): T =
    subSequence(startIndex, length())

  final def subSequence(range: Range): T =
    if (range.isNull) this.asInstanceOf[T] else subSequence(range.start, range.end)

  final def subSequenceBefore(range: Range): T =
    if (range.isNull) nullSequence() else subSequence(0, range.start)

  final def subSequenceAfter(range: Range): T =
    if (range.isNull) nullSequence() else subSequence(range.end)

  final def subSequenceBeforeAfter(range: Range): Pair[T, T] =
    Pair.of(subSequenceBefore(range), subSequenceAfter(range))

  final override def endSequence(startIndex: Int, endIndex: Int): T = {
    val length   = this.length()
    var useStart = length - startIndex
    var useEnd   = length - endIndex

    useEnd = Utils.rangeLimit(useEnd, 0, length)
    useStart = Utils.rangeLimit(useStart, 0, useEnd)
    subSequence(useStart, useEnd)
  }

  final override def endSequence(startIndex: Int): T =
    endSequence(startIndex, 0)

  final override def endCharAt(index: Int): Char = {
    val length = this.length()
    if (index < 0 || index >= length) SequenceUtils.NUL
    else charAt(length - index)
  }

  final override def midSequence(startIndex: Int, endIndex: Int): T = {
    val length   = this.length()
    var useStart = if (startIndex < 0) length + startIndex else startIndex
    var useEnd   = if (endIndex < 0) length + endIndex else endIndex

    useEnd = Utils.rangeLimit(useEnd, 0, length)
    useStart = Utils.rangeLimit(useStart, 0, useEnd)
    subSequence(useStart, useEnd)
  }

  final override def midSequence(startIndex: Int): T =
    midSequence(startIndex, length())

  final override def midCharAt(index: Int): Char = {
    val length = this.length()
    if (index < -length || index >= length) SequenceUtils.NUL
    else charAt(if (index < 0) length + index else index)
  }

  final override def lastChar(): Char =
    if (isEmpty()) SequenceUtils.NUL else charAt(length() - 1)

  final override def firstChar(): Char =
    if (isEmpty()) SequenceUtils.NUL else charAt(0)

  final def validateIndex(index: Int): Unit =
    SequenceUtils.validateIndex(index, length())

  final def validateIndexInclusiveEnd(index: Int): Unit =
    SequenceUtils.validateIndexInclusiveEnd(index, length())

  final def validateStartEnd(startIndex: Int, endIndex: Int): Unit =
    SequenceUtils.validateStartEnd(startIndex, endIndex, length())

  override def safeCharAt(index: Int): Char =
    if (index < 0 || index >= length()) SequenceUtils.NUL else charAt(index)

  override def safeSubSequence(startIndex: Int, endIndex: Int): T = {
    val length   = this.length()
    val useStart = Math.max(0, Math.min(length, startIndex))
    val useEnd   = Math.max(useStart, Math.min(length, endIndex))
    subSequence(useStart, useEnd)
  }

  override def safeSubSequence(startIndex: Int): T = {
    val length   = this.length()
    val useStart = Math.max(0, Math.min(length, startIndex))
    subSequence(useStart, length)
  }

  override def isCharAt(index: Int, predicate: CharPredicate): Boolean =
    predicate.test(safeCharAt(index))

  def toStringOrNull(): Nullable[String] =
    if (isNull) Nullable.empty[String] else Nullable(toString)

  // ---- indexOf ----

  final override def indexOf(s:           CharSequence):                                   Int = SequenceUtils.indexOf(this, s)
  final override def indexOf(s:           CharSequence, fromIndex:   Int):                 Int = SequenceUtils.indexOf(this, s, fromIndex)
  final override def indexOf(s:           CharSequence, fromIndex:   Int, endIndex:  Int): Int = SequenceUtils.indexOf(this, s, fromIndex, endIndex)
  final override def indexOf(c:           Char):                                           Int = SequenceUtils.indexOf(this, c)
  final override def indexOf(c:           Char, fromIndex:           Int):                 Int = SequenceUtils.indexOf(this, c, fromIndex)
  final override def indexOfAny(s:        CharPredicate):                                  Int = SequenceUtils.indexOfAny(this, s)
  final override def indexOfAny(s:        CharPredicate, index:      Int):                 Int = SequenceUtils.indexOfAny(this, s, index)
  final override def indexOfAnyNot(s:     CharPredicate):                                  Int = SequenceUtils.indexOfAnyNot(this, s)
  final override def indexOfAnyNot(s:     CharPredicate, fromIndex:  Int):                 Int = SequenceUtils.indexOfAnyNot(this, s, fromIndex)
  final override def indexOfAnyNot(s:     CharPredicate, fromIndex:  Int, endIndex:  Int): Int = SequenceUtils.indexOfAnyNot(this, s, fromIndex, endIndex)
  final override def indexOfNot(c:        Char):                                           Int = SequenceUtils.indexOfNot(this, c)
  final override def indexOfNot(c:        Char, fromIndex:           Int):                 Int = SequenceUtils.indexOfNot(this, c, fromIndex)
  final override def lastIndexOf(c:       Char):                                           Int = SequenceUtils.lastIndexOf(this, c)
  final override def lastIndexOf(c:       Char, fromIndex:           Int):                 Int = SequenceUtils.lastIndexOf(this, c, fromIndex)
  final override def lastIndexOfNot(c:    Char):                                           Int = SequenceUtils.lastIndexOfNot(this, c)
  final override def lastIndexOfNot(c:    Char, fromIndex:           Int):                 Int = SequenceUtils.lastIndexOfNot(this, c, fromIndex)
  final override def lastIndexOf(s:       CharSequence):                                   Int = SequenceUtils.lastIndexOf(this, s)
  final override def lastIndexOf(s:       CharSequence, fromIndex:   Int):                 Int = SequenceUtils.lastIndexOf(this, s, fromIndex)
  final override def lastIndexOfAny(s:    CharPredicate, fromIndex:  Int):                 Int = SequenceUtils.lastIndexOfAny(this, s, fromIndex)
  final override def lastIndexOfAny(s:    CharPredicate):                                  Int = SequenceUtils.lastIndexOfAny(this, s)
  final override def lastIndexOfAnyNot(s: CharPredicate):                                  Int = SequenceUtils.lastIndexOfAnyNot(this, s)
  final override def lastIndexOfAnyNot(s: CharPredicate, fromIndex:  Int):                 Int = SequenceUtils.lastIndexOfAnyNot(this, s, fromIndex)
  final override def lastIndexOfAnyNot(s: CharPredicate, startIndex: Int, fromIndex: Int): Int = SequenceUtils.lastIndexOfAnyNot(this, s, startIndex, fromIndex)
  final override def indexOf(c:           Char, fromIndex:           Int, endIndex:  Int): Int = SequenceUtils.indexOf(this, c, fromIndex, endIndex)
  final override def indexOfNot(c:        Char, fromIndex:           Int, endIndex:  Int): Int = SequenceUtils.indexOfNot(this, c, fromIndex, endIndex)
  final override def indexOfAny(s:        CharPredicate, fromIndex:  Int, endIndex:  Int): Int = SequenceUtils.indexOfAny(this, s, fromIndex, endIndex)
  final override def lastIndexOf(s:       CharSequence, startIndex:  Int, fromIndex: Int): Int = SequenceUtils.lastIndexOf(this, s, startIndex, fromIndex)
  final override def lastIndexOf(c:       Char, startIndex:          Int, fromIndex: Int): Int = SequenceUtils.lastIndexOf(this, c, startIndex, fromIndex)
  final override def lastIndexOfNot(c:    Char, startIndex:          Int, fromIndex: Int): Int = SequenceUtils.lastIndexOfNot(this, c, startIndex, fromIndex)
  final override def lastIndexOfAny(s:    CharPredicate, startIndex: Int, fromIndex: Int): Int = SequenceUtils.lastIndexOfAny(this, s, startIndex, fromIndex)

  // ---- countOf ----

  final override def countOfSpaceTab():      Int = SequenceUtils.countOfSpaceTab(this)
  final override def countOfNotSpaceTab():   Int = SequenceUtils.countOfNotSpaceTab(this)
  final override def countOfWhitespace():    Int = SequenceUtils.countOfWhitespace(this)
  final override def countOfNotWhitespace(): Int = SequenceUtils.countOfNotWhitespace(this)

  final override def countOfAny(chars:    CharPredicate, fromIndex:  Int):                Int = SequenceUtils.countOfAny(this, chars, fromIndex)
  final override def countOfAny(chars:    CharPredicate):                                 Int = SequenceUtils.countOfAny(this, chars)
  final override def countOfAnyNot(chars: CharPredicate, fromIndex:  Int):                Int = SequenceUtils.countOfAnyNot(this, chars, fromIndex)
  final override def countOfAnyNot(chars: CharPredicate):                                 Int = SequenceUtils.countOfAnyNot(this, chars)
  final override def countOfAny(s:        CharPredicate, fromIndex:  Int, endIndex: Int): Int = SequenceUtils.countOfAny(this, s, fromIndex, endIndex)
  final override def countOfAnyNot(chars: CharPredicate, startIndex: Int, endIndex: Int): Int = SequenceUtils.countOfAnyNot(this, chars, startIndex, endIndex)

  // ---- countLeading / countTrailing ----

  final override def countLeading(chars:    CharPredicate):                 Int = SequenceUtils.countLeading(this, chars)
  final override def countLeading(chars:    CharPredicate, fromIndex: Int): Int = SequenceUtils.countLeading(this, chars, fromIndex)
  final override def countLeadingNot(chars: CharPredicate):                 Int = SequenceUtils.countLeadingNot(this, chars)
  final override def countLeadingNot(chars: CharPredicate, fromIndex: Int): Int = SequenceUtils.countLeadingNot(this, chars, fromIndex)

  final override def countTrailing(chars:    CharPredicate):                 Int = SequenceUtils.countTrailing(this, chars)
  final override def countTrailing(chars:    CharPredicate, fromIndex: Int): Int = SequenceUtils.countTrailing(this, chars, fromIndex)
  final override def countTrailingNot(chars: CharPredicate):                 Int = SequenceUtils.countTrailingNot(this, chars)
  final override def countTrailingNot(chars: CharPredicate, fromIndex: Int): Int = SequenceUtils.countTrailingNot(this, chars, fromIndex)

  final override def countLeadingNot(chars:  CharPredicate, startIndex: Int, endIndex: Int): Int = SequenceUtils.countLeadingNot(this, chars, startIndex, endIndex)
  final override def countTrailingNot(chars: CharPredicate, startIndex: Int, endIndex: Int): Int = SequenceUtils.countTrailingNot(this, chars, startIndex, endIndex)
  final override def countLeading(chars:     CharPredicate, fromIndex:  Int, endIndex: Int): Int = SequenceUtils.countLeading(this, chars, fromIndex, endIndex)

  final override def countLeadingColumns(startColumn: Int, chars:                CharPredicate):       Int = SequenceUtils.countLeadingColumns(this, startColumn, chars)
  final override def countTrailing(chars:             CharPredicate, startIndex: Int, fromIndex: Int): Int = SequenceUtils.countTrailing(this, chars, startIndex, fromIndex)

  final override def countLeadingSpace():                                  Int = SequenceUtils.countLeadingSpace(this)
  final override def countLeadingNotSpace():                               Int = SequenceUtils.countLeadingNotSpace(this)
  final override def countLeadingSpace(startIndex:    Int):                Int = SequenceUtils.countLeadingSpace(this, startIndex)
  final override def countLeadingNotSpace(startIndex: Int):                Int = SequenceUtils.countLeadingNotSpace(this, startIndex)
  final override def countLeadingSpace(startIndex:    Int, endIndex: Int): Int = SequenceUtils.countLeadingSpace(this, startIndex, endIndex)
  final override def countLeadingNotSpace(startIndex: Int, endIndex: Int): Int = SequenceUtils.countLeadingNotSpace(this, startIndex, endIndex)

  final override def countTrailingSpace():                                   Int = SequenceUtils.countTrailingSpace(this)
  final override def countTrailingNotSpace():                                Int = SequenceUtils.countTrailingNotSpace(this)
  final override def countTrailingSpace(fromIndex:     Int):                 Int = SequenceUtils.countTrailingSpace(this, fromIndex)
  final override def countTrailingNotSpace(fromIndex:  Int):                 Int = SequenceUtils.countTrailingNotSpace(this, fromIndex)
  final override def countTrailingSpace(startIndex:    Int, fromIndex: Int): Int = SequenceUtils.countTrailingSpace(this, startIndex, fromIndex)
  final override def countTrailingNotSpace(startIndex: Int, fromIndex: Int): Int = SequenceUtils.countTrailingNotSpace(this, startIndex, fromIndex)

  final override def countLeadingSpaceTab():                                  Int = SequenceUtils.countLeadingSpaceTab(this)
  final override def countLeadingNotSpaceTab():                               Int = SequenceUtils.countLeadingNotSpaceTab(this)
  final override def countLeadingSpaceTab(startIndex:    Int):                Int = SequenceUtils.countLeadingSpaceTab(this, startIndex)
  final override def countLeadingNotSpaceTab(startIndex: Int):                Int = SequenceUtils.countLeadingNotSpaceTab(this, startIndex)
  final override def countLeadingSpaceTab(startIndex:    Int, endIndex: Int): Int = SequenceUtils.countLeadingSpaceTab(this, startIndex, endIndex)
  final override def countLeadingNotSpaceTab(startIndex: Int, endIndex: Int): Int = SequenceUtils.countLeadingNotSpaceTab(this, startIndex, endIndex)

  final override def countTrailingSpaceTab():                                   Int = SequenceUtils.countTrailingSpaceTab(this)
  final override def countTrailingNotSpaceTab():                                Int = SequenceUtils.countTrailingNotSpaceTab(this)
  final override def countTrailingSpaceTab(fromIndex:     Int):                 Int = SequenceUtils.countTrailingSpaceTab(this, fromIndex)
  final override def countTrailingNotSpaceTab(fromIndex:  Int):                 Int = SequenceUtils.countTrailingNotSpaceTab(this, fromIndex)
  final override def countTrailingSpaceTab(startIndex:    Int, fromIndex: Int): Int = SequenceUtils.countTrailingSpaceTab(this, startIndex, fromIndex)
  final override def countTrailingNotSpaceTab(startIndex: Int, fromIndex: Int): Int = SequenceUtils.countTrailingNotSpaceTab(this, startIndex, fromIndex)

  final override def countLeadingWhitespace():                                  Int = SequenceUtils.countLeadingWhitespace(this)
  final override def countLeadingNotWhitespace():                               Int = SequenceUtils.countLeadingNotWhitespace(this)
  final override def countLeadingWhitespace(startIndex:    Int):                Int = SequenceUtils.countLeadingWhitespace(this, startIndex)
  final override def countLeadingNotWhitespace(startIndex: Int):                Int = SequenceUtils.countLeadingNotWhitespace(this, startIndex)
  final override def countLeadingWhitespace(startIndex:    Int, endIndex: Int): Int = SequenceUtils.countLeadingWhitespace(this, startIndex, endIndex)
  final override def countLeadingNotWhitespace(startIndex: Int, endIndex: Int): Int = SequenceUtils.countLeadingNotWhitespace(this, startIndex, endIndex)

  final override def countTrailingWhitespace():                                   Int = SequenceUtils.countTrailingWhitespace(this)
  final override def countTrailingNotWhitespace():                                Int = SequenceUtils.countTrailingNotWhitespace(this)
  final override def countTrailingWhitespace(fromIndex:     Int):                 Int = SequenceUtils.countTrailingWhitespace(this, fromIndex)
  final override def countTrailingNotWhitespace(fromIndex:  Int):                 Int = SequenceUtils.countTrailingNotWhitespace(this, fromIndex)
  final override def countTrailingWhitespace(startIndex:    Int, fromIndex: Int): Int = SequenceUtils.countTrailingWhitespace(this, startIndex, fromIndex)
  final override def countTrailingNotWhitespace(startIndex: Int, fromIndex: Int): Int = SequenceUtils.countTrailingNotWhitespace(this, startIndex, fromIndex)

  // ---- trim ----

  final override def trimStart(chars:    CharPredicate):             T          = subSequence(trimStartRange(0, chars))
  final override def trimmedStart(chars: CharPredicate):             T          = trimmedStart(0, chars)
  final override def trimEnd(chars:      CharPredicate):             T          = trimEnd(0, chars)
  final override def trimmedEnd(chars:   CharPredicate):             T          = trimmedEnd(0, chars)
  final override def trim(chars:         CharPredicate):             T          = trim(0, chars)
  final override def trimmed(chars:      CharPredicate):             Pair[T, T] = trimmed(0, chars)
  final override def trimStart(keep:     Int):                       T          = trimStart(keep, CharPredicate.WHITESPACE)
  final override def trimmedStart(keep:  Int):                       T          = trimmedStart(keep, CharPredicate.WHITESPACE)
  final override def trimEnd(keep:       Int):                       T          = trimEnd(keep, CharPredicate.WHITESPACE)
  final override def trimmedEnd(keep:    Int):                       T          = trimmedEnd(keep, CharPredicate.WHITESPACE)
  final override def trim(keep:          Int):                       T          = trim(keep, CharPredicate.WHITESPACE)
  final override def trimmed(keep:       Int):                       Pair[T, T] = trimmed(keep, CharPredicate.WHITESPACE)
  final override def trimStart():                                    T          = trimStart(0, CharPredicate.WHITESPACE)
  final override def trimmedStart():                                 T          = trimmedStart(0, CharPredicate.WHITESPACE)
  final override def trimEnd():                                      T          = trimEnd(0, CharPredicate.WHITESPACE)
  final override def trimmedEnd():                                   T          = trimmedEnd(0, CharPredicate.WHITESPACE)
  final override def trim():                                         T          = trim(0, CharPredicate.WHITESPACE)
  final override def trimmed():                                      Pair[T, T] = trimmed(0, CharPredicate.WHITESPACE)
  final override def trimStart(keep:     Int, chars: CharPredicate): T          = subSequence(trimStartRange(keep, chars))
  final override def trimmedStart(keep:  Int, chars: CharPredicate): T          = subSequenceBefore(trimStartRange(keep, chars))
  final override def trimEnd(keep:       Int, chars: CharPredicate): T          = subSequence(trimEndRange(keep, chars))
  final override def trimmedEnd(keep:    Int, chars: CharPredicate): T          = subSequenceAfter(trimEndRange(keep, chars))
  final override def trim(keep:          Int, chars: CharPredicate): T          = subSequence(trimRange(keep, chars))
  final override def trimmed(keep:       Int, chars: CharPredicate): Pair[T, T] = subSequenceBeforeAfter(trimRange(keep, chars))

  // ---- trim ranges ----

  final override def trimStartRange(keep:  Int, chars: CharPredicate): Range = SequenceUtils.trimStartRange(this, keep, chars)
  final override def trimEndRange(keep:    Int, chars: CharPredicate): Range = SequenceUtils.trimEndRange(this, keep, chars)
  final override def trimRange(keep:       Int, chars: CharPredicate): Range = SequenceUtils.trimRange(this, keep, chars)
  final override def trimStartRange(chars: CharPredicate):             Range = SequenceUtils.trimStartRange(this, chars)
  final override def trimEndRange(chars:   CharPredicate):             Range = SequenceUtils.trimEndRange(this, chars)
  final override def trimRange(chars:      CharPredicate):             Range = SequenceUtils.trimRange(this, chars)
  final override def trimStartRange(keep:  Int):                       Range = SequenceUtils.trimStartRange(this, keep)
  final override def trimEndRange(keep:    Int):                       Range = SequenceUtils.trimEndRange(this, keep)
  final override def trimRange(keep:       Int):                       Range = SequenceUtils.trimRange(this, keep)
  final override def trimStartRange():                                 Range = SequenceUtils.trimStartRange(this)
  final override def trimEndRange():                                   Range = SequenceUtils.trimEndRange(this)
  final override def trimRange():                                      Range = SequenceUtils.trimRange(this)

  // ---- padding ----

  final override def padding(length: Int, pad: Char): T =
    if (length <= this.length()) nullSequence() else sequenceOf(Nullable(RepeatedSequence.repeatOf(pad, length - this.length())))

  final override def padding(length: Int): T =
    padStart(length, ' ')

  override def padStart(length: Int, pad: Char): T = {
    val p = padding(length, pad)
    if (p.isEmpty()) this.asInstanceOf[T]
    else {
      val b = seqBuilder
      b.append(p)
      b.append(this)
      b.toSequence
    }
  }

  override def padEnd(length: Int, pad: Char): T = {
    val p = padding(length, pad)
    if (p.isEmpty()) this.asInstanceOf[T]
    else {
      val b = seqBuilder
      b.append(this)
      b.append(p)
      b.toSequence
    }
  }

  override def padStart(length: Int): T = padStart(length, ' ')
  override def padEnd(length:   Int): T = padEnd(length, ' ')

  // ---- EOL Helpers ----

  final override def eolEndLength():                Int = SequenceUtils.eolEndLength(this)
  final override def eolEndLength(eolEnd:     Int): Int = SequenceUtils.eolEndLength(this, eolEnd)
  final override def eolStartLength(eolStart: Int): Int = SequenceUtils.eolStartLength(this, eolStart)

  final override def endOfLine(index:         Int): Int = SequenceUtils.endOfLine(this, index)
  final override def endOfLineAnyEOL(index:   Int): Int = SequenceUtils.endOfLineAnyEOL(this, index)
  final override def startOfLine(index:       Int): Int = SequenceUtils.startOfLine(this, index)
  final override def startOfLineAnyEOL(index: Int): Int = SequenceUtils.startOfLineAnyEOL(this, index)

  final override def startOfDelimitedByAnyNot(s: CharPredicate, index: Int): Int = startOfDelimitedByAny(s.negate(), index)
  final override def endOfDelimitedByAnyNot(s:   CharPredicate, index: Int): Int = endOfDelimitedByAny(s.negate(), index)

  final override def startOfDelimitedBy(s:    CharSequence, index:  Int): Int = SequenceUtils.startOfDelimitedBy(this, s, index)
  final override def startOfDelimitedByAny(s: CharPredicate, index: Int): Int = SequenceUtils.startOfDelimitedByAny(this, s, index)
  final override def endOfDelimitedBy(s:      CharSequence, index:  Int): Int = SequenceUtils.endOfDelimitedBy(this, s, index)
  final override def endOfDelimitedByAny(s:   CharPredicate, index: Int): Int = SequenceUtils.endOfDelimitedByAny(this, s, index)

  final override def lineRangeAt(index:       Int): Range = SequenceUtils.lineRangeAt(this, index)
  final override def lineRangeAtAnyEOL(index: Int): Range = SequenceUtils.lineRangeAtAnyEOL(this, index)

  final override def lineAt(index:       Int): T = subSequence(lineRangeAt(index))
  final override def lineAtAnyEOL(index: Int): T = subSequence(lineRangeAtAnyEOL(index))

  final override def eolEndRange(eolEnd: Int): Range = SequenceUtils.eolEndRange(this, eolEnd)
  override def eolStartRange(eolStart:   Int): Range = SequenceUtils.eolStartRange(this, eolStart)

  final override def trimEOL(): T = {
    val eolLength = eolEndLength()
    if (eolLength > 0) subSequence(0, length() - eolLength) else this.asInstanceOf[T]
  }

  final override def trimmedEOL(): T = {
    val eolLength = eolEndLength()
    if (eolLength > 0) subSequence(length() - eolLength) else nullSequence()
  }

  final override def trimTailBlankLines(): T = {
    val range = trailingBlankLinesRange()
    if (range.isNull) this.asInstanceOf[T] else subSequenceBefore(range)
  }

  final override def trimLeadBlankLines(): T = {
    val range = leadingBlankLinesRange()
    if (range.isNull) this.asInstanceOf[T] else subSequenceAfter(range)
  }

  // ---- blank lines ranges ----

  final override def leadingBlankLinesRange():                                 Range = SequenceUtils.leadingBlankLinesRange(this)
  final override def leadingBlankLinesRange(startIndex:  Int):                 Range = SequenceUtils.leadingBlankLinesRange(this, startIndex)
  final override def leadingBlankLinesRange(fromIndex:   Int, endIndex:  Int): Range = SequenceUtils.leadingBlankLinesRange(this, fromIndex, endIndex)
  final override def trailingBlankLinesRange():                                Range = SequenceUtils.trailingBlankLinesRange(this)
  final override def trailingBlankLinesRange(fromIndex:  Int):                 Range = SequenceUtils.trailingBlankLinesRange(this, fromIndex)
  final override def trailingBlankLinesRange(startIndex: Int, fromIndex: Int): Range = SequenceUtils.trailingBlankLinesRange(this, startIndex, fromIndex)

  final override def trailingBlankLinesRange(eolChars: CharPredicate, startIndex: Int, fromIndex: Int): Range = SequenceUtils.trailingBlankLinesRange(this, eolChars, startIndex, fromIndex)
  final override def leadingBlankLinesRange(eolChars:  CharPredicate, fromIndex:  Int, endIndex:  Int): Range = SequenceUtils.leadingBlankLinesRange(this, eolChars, fromIndex, endIndex)

  // ---- blank lines removed ranges ----

  final override def blankLinesRemovedRanges():                                                        ju.List[Range] = SequenceUtils.blankLinesRemovedRanges(this)
  final override def blankLinesRemovedRanges(fromIndex: Int):                                          ju.List[Range] = SequenceUtils.blankLinesRemovedRanges(this, fromIndex)
  final override def blankLinesRemovedRanges(fromIndex: Int, endIndex:            Int):                ju.List[Range] = SequenceUtils.blankLinesRemovedRanges(this, fromIndex, endIndex)
  final override def blankLinesRemovedRanges(eolChars:  CharPredicate, fromIndex: Int, endIndex: Int): ju.List[Range] = SequenceUtils.blankLinesRemovedRanges(this, eolChars, fromIndex, endIndex)

  // ---- trim to line ----

  override def trimToEndOfLine(includeEol: Boolean):             T = trimToEndOfLine(CharPredicate.EOL, includeEol, 0)
  override def trimToEndOfLine(index:      Int):                 T = trimToEndOfLine(CharPredicate.EOL, false, index)
  override def trimToEndOfLine():                                T = trimToEndOfLine(CharPredicate.EOL, false, 0)
  override def trimToEndOfLine(includeEol: Boolean, index: Int): T = trimToEndOfLine(CharPredicate.EOL, includeEol, index)

  override def trimToStartOfLine(includeEol: Boolean):             T = trimToStartOfLine(CharPredicate.EOL, includeEol, 0)
  override def trimToStartOfLine(index:      Int):                 T = trimToStartOfLine(CharPredicate.EOL, false, index)
  override def trimToStartOfLine():                                T = trimToStartOfLine(CharPredicate.EOL, false, 0)
  override def trimToStartOfLine(includeEol: Boolean, index: Int): T = trimToStartOfLine(CharPredicate.EOL, includeEol, index)

  override def trimToEndOfLine(eolChars: CharPredicate, includeEol: Boolean, index: Int): T = {
    val eolPos = endOfDelimitedByAny(eolChars, index)
    if (eolPos < length()) {
      val endIndex = if (includeEol) eolPos + eolStartLength(eolPos) else eolPos
      subSequence(0, endIndex)
    } else {
      this.asInstanceOf[T]
    }
  }

  override def trimToStartOfLine(eolChars: CharPredicate, includeEol: Boolean, index: Int): T = {
    val eolPos = startOfDelimitedByAny(eolChars, index)
    if (eolPos > 0) {
      val startIndex = if (includeEol) eolPos - eolEndLength(eolPos - 1) else eolPos
      subSequence(startIndex)
    } else {
      this.asInstanceOf[T]
    }
  }

  // ---- null handling ----

  final override def ifNull(other:            T):       T = if (isNull) other else this.asInstanceOf[T]
  final override def ifNullEmptyAfter(other:  T):       T = if (isNull) other.subSequence(other.length(), other.length()) else this.asInstanceOf[T]
  final override def ifNullEmptyBefore(other: T):       T = if (isNull) other.subSequence(0, 0) else this.asInstanceOf[T]
  final override def nullIfEmpty():                     T = if (isEmpty()) nullSequence() else this.asInstanceOf[T]
  final override def nullIfBlank():                     T = if (isBlank()) nullSequence() else this.asInstanceOf[T]
  final override def nullIf(condition:        Boolean): T = if (condition) nullSequence() else this.asInstanceOf[T]
  final override def nullIfNot(predicate: (? >: T, ? >: CharSequence) => Boolean, matches: CharSequence*): T = nullIf((a: T, b: CharSequence) => !predicate(a, b), matches*)
  final override def nullIf(predicate:    (? >: CharSequence) => Boolean, matches:         CharSequence*): T = nullIf((o1: T, o2: CharSequence) => predicate(o2), matches*)
  final override def nullIfNot(predicate: (? >: CharSequence) => Boolean, matches:         CharSequence*): T = nullIfNot((o1: T, o2: CharSequence) => predicate(o2), matches*)
  final override def nullIf(matches:      CharSequence*):                                                  T = nullIf(((cs: CharSequence) => this.matches(cs)): (CharSequence => Boolean), matches*)
  final override def nullIfNot(matches:   CharSequence*):                                                  T = nullIfNot(((cs: CharSequence) => this.matches(cs)): (CharSequence => Boolean), matches*)
  final override def nullIfStartsWith(matches:              CharSequence*): T = nullIf(((cs: CharSequence) => this.startsWith(cs)): (CharSequence => Boolean), matches*)
  final override def nullIfNotStartsWith(matches:           CharSequence*): T = nullIfNot(((cs: CharSequence) => this.startsWith(cs)): (CharSequence => Boolean), matches*)
  final override def nullIfEndsWith(matches:                CharSequence*): T = nullIf(((cs: CharSequence) => this.endsWith(cs)): (CharSequence => Boolean), matches*)
  final override def nullIfNotEndsWith(matches:             CharSequence*): T = nullIfNot(((cs: CharSequence) => this.endsWith(cs)): (CharSequence => Boolean), matches*)
  final override def nullIfStartsWithIgnoreCase(matches:    CharSequence*): T = nullIf(((cs: CharSequence) => this.startsWithIgnoreCase(cs)): (CharSequence => Boolean), matches*)
  final override def nullIfNotStartsWithIgnoreCase(matches: CharSequence*): T = nullIfNot(((cs: CharSequence) => this.startsWithIgnoreCase(cs)): (CharSequence => Boolean), matches*)
  final override def nullIfEndsWithIgnoreCase(matches:      CharSequence*): T = nullIf(((cs: CharSequence) => this.endsWithIgnoreCase(cs)): (CharSequence => Boolean), matches*)
  final override def nullIfNotEndsWithIgnoreCase(matches:   CharSequence*): T = nullIfNot(((cs: CharSequence) => this.endsWithIgnoreCase(cs)): (CharSequence => Boolean), matches*)
  final override def nullIfStartsWith(ignoreCase:    Boolean, matches: CharSequence*): T = nullIf(((prefix: CharSequence) => startsWith(prefix, ignoreCase)): (CharSequence => Boolean), matches*)
  final override def nullIfNotStartsWith(ignoreCase: Boolean, matches: CharSequence*): T = nullIfNot(((prefix: CharSequence) => startsWith(prefix, ignoreCase)): (CharSequence => Boolean), matches*)
  final override def nullIfEndsWith(ignoreCase:      Boolean, matches: CharSequence*): T = nullIf(((suffix: CharSequence) => endsWith(suffix, ignoreCase)): (CharSequence => Boolean), matches*)
  final override def nullIfNotEndsWith(ignoreCase:   Boolean, matches: CharSequence*): T = nullIfNot(((suffix: CharSequence) => endsWith(suffix, ignoreCase)): (CharSequence => Boolean), matches*)

  final override def nullIf(predicate: (? >: T, ? >: CharSequence) => Boolean, matches: CharSequence*): T =
    boundary[T] {
      for (m <- matches)
        if (predicate.asInstanceOf[(T, CharSequence) => Boolean](this.asInstanceOf[T], m)) break(nullSequence())
      this.asInstanceOf[T]
    }

  // ---- isNull / isEmpty / isBlank ----

  final override def isNull:    Boolean = this eq nullSequence()
  final override def isNotNull: Boolean = !(this eq nullSequence())

  final override def isEmpty():    Boolean = SequenceUtils.isEmpty(this)
  final override def isBlank():    Boolean = SequenceUtils.isBlank(this)
  final override def isNotEmpty(): Boolean = SequenceUtils.isNotEmpty(this)
  final override def isNotBlank(): Boolean = SequenceUtils.isNotBlank(this)

  // ---- startsWith / endsWith ----

  final override def endsWith(suffix:   CharSequence):                      Boolean = SequenceUtils.endsWith(this, suffix)
  final override def endsWith(suffix:   CharSequence, ignoreCase: Boolean): Boolean = SequenceUtils.endsWith(this, suffix, ignoreCase)
  final override def startsWith(prefix: CharSequence):                      Boolean = SequenceUtils.startsWith(this, prefix)
  final override def startsWith(prefix: CharSequence, ignoreCase: Boolean): Boolean = SequenceUtils.startsWith(this, prefix, ignoreCase)

  final override def endsWith(chars:   CharPredicate): Boolean = SequenceUtils.endsWith(this, chars)
  final override def startsWith(chars: CharPredicate): Boolean = SequenceUtils.startsWith(this, chars)

  final override def endsWithEOL():        Boolean = SequenceUtils.endsWithEOL(this)
  final override def endsWithAnyEOL():     Boolean = SequenceUtils.endsWithAnyEOL(this)
  final override def endsWithSpace():      Boolean = SequenceUtils.endsWithSpace(this)
  final override def endsWithSpaceTab():   Boolean = SequenceUtils.endsWithSpaceTab(this)
  final override def endsWithWhitespace(): Boolean = SequenceUtils.endsWithWhitespace(this)

  final override def startsWithEOL():        Boolean = SequenceUtils.startsWithEOL(this)
  final override def startsWithAnyEOL():     Boolean = SequenceUtils.startsWithAnyEOL(this)
  final override def startsWithSpace():      Boolean = SequenceUtils.startsWithSpace(this)
  final override def startsWithSpaceTab():   Boolean = SequenceUtils.startsWithSpaceTab(this)
  final override def startsWithWhitespace(): Boolean = SequenceUtils.startsWithWhitespace(this)

  // ---- remove prefix/suffix ----

  final override def removeSuffix(suffix:           CharSequence): T       = if (!endsWith(suffix)) this.asInstanceOf[T] else subSequence(0, length() - suffix.length())
  final override def removePrefix(prefix:           CharSequence): T       = if (!startsWith(prefix)) this.asInstanceOf[T] else subSequence(prefix.length(), length())
  final override def removeProperSuffix(suffix:     CharSequence): T       = if (length() <= suffix.length() || !endsWith(suffix)) this.asInstanceOf[T] else subSequence(0, length() - suffix.length())
  final override def removeProperPrefix(prefix:     CharSequence): T       = if (length() <= prefix.length() || !startsWith(prefix)) this.asInstanceOf[T] else subSequence(prefix.length(), length())
  final override def endsWithIgnoreCase(suffix:     CharSequence): Boolean = length() > 0 && matchCharsReversed(suffix, length() - 1, true)
  final override def startsWithIgnoreCase(prefix:   CharSequence): Boolean = length() > 0 && matchChars(prefix, 0, true)
  final override def removeSuffixIgnoreCase(suffix: CharSequence): T       = if (!endsWithIgnoreCase(suffix)) this.asInstanceOf[T] else subSequence(0, length() - suffix.length())
  final override def removePrefixIgnoreCase(prefix: CharSequence): T       = if (!startsWithIgnoreCase(prefix)) this.asInstanceOf[T] else subSequence(prefix.length(), length())
  final override def removeProperSuffixIgnoreCase(suffix: CharSequence): T =
    if (length() <= suffix.length() || !endsWithIgnoreCase(suffix)) this.asInstanceOf[T] else subSequence(0, length() - suffix.length())
  final override def removeProperPrefixIgnoreCase(prefix: CharSequence): T =
    if (length() <= prefix.length() || !startsWithIgnoreCase(prefix)) this.asInstanceOf[T] else subSequence(prefix.length(), length())
  final override def removeSuffix(suffix: CharSequence, ignoreCase: Boolean):       T = if (!endsWith(suffix, ignoreCase)) this.asInstanceOf[T] else subSequence(0, length() - suffix.length())
  final override def removePrefix(prefix: CharSequence, ignoreCase: Boolean):       T = if (!startsWith(prefix, ignoreCase)) this.asInstanceOf[T] else subSequence(prefix.length(), length())
  final override def removeProperSuffix(suffix: CharSequence, ignoreCase: Boolean): T =
    if (length() <= suffix.length() || !endsWith(suffix, ignoreCase)) this.asInstanceOf[T] else subSequence(0, length() - suffix.length())
  final override def removeProperPrefix(prefix: CharSequence, ignoreCase: Boolean): T =
    if (length() <= prefix.length() || !startsWith(prefix, ignoreCase)) this.asInstanceOf[T] else subSequence(prefix.length(), length())

  // ---- insert / delete ----

  override def insert(index: Int, chars: CharSequence): T = {
    val idx = Math.max(0, Math.min(length(), index))

    if (chars.length() == 0) {
      this.asInstanceOf[T]
    } else if (idx == 0) {
      prefixWith(Nullable(chars))
    } else if (idx == length()) {
      suffixWith(Nullable(chars))
    } else {
      val b = seqBuilder
      b.add(Nullable(subSequence(0, idx)))
      b.add(Nullable(chars))
      b.add(Nullable(subSequence(idx)))
      b.toSequence
    }
  }

  override def delete(startIndex: Int, endIndex: Int): T = {
    val eIdx = Math.max(0, Math.min(length(), endIndex))
    val sIdx = Math.min(eIdx, Math.max(0, startIndex))

    if (sIdx == eIdx) {
      this.asInstanceOf[T]
    } else if (sIdx == 0) {
      subSequence(eIdx)
    } else if (eIdx == length()) {
      subSequence(0, sIdx)
    } else {
      val b = seqBuilder
      b.add(Nullable(subSequence(0, sIdx)))
      b.add(Nullable(subSequence(eIdx)))
      b.toSequence
    }
  }

  // ---- case mapping ----

  final override def toLowerCase(): T = toMapped(ChangeCase.toLowerCase)
  final override def toUpperCase(): T = toMapped(ChangeCase.toUpperCase)
  final override def toNbSp():      T = toMapped(SpaceMapper.toNonBreakSpace)
  final override def toSpc():       T = toMapped(SpaceMapper.fromNonBreakSpace)

  // ---- matches ----

  final override def matches(chars:           CharSequence, ignoreCase: Boolean): Boolean = SequenceUtils.matches(this, chars, ignoreCase)
  final override def matches(chars:           CharSequence):                      Boolean = SequenceUtils.matches(this, chars)
  final override def matchesIgnoreCase(chars: CharSequence):                      Boolean = SequenceUtils.matchesIgnoreCase(this, chars)

  final override def matchChars(chars:           CharSequence, startIndex: Int, ignoreCase: Boolean): Boolean = SequenceUtils.matchChars(this, chars, startIndex, ignoreCase)
  final override def matchChars(chars:           CharSequence, startIndex: Int):                      Boolean = SequenceUtils.matchChars(this, chars, startIndex)
  final override def matchCharsIgnoreCase(chars: CharSequence, startIndex: Int):                      Boolean = SequenceUtils.matchCharsIgnoreCase(this, chars, startIndex)

  final override def matchChars(chars:           CharSequence, ignoreCase: Boolean): Boolean = SequenceUtils.matchChars(this, chars, ignoreCase)
  final override def matchChars(chars:           CharSequence):                      Boolean = SequenceUtils.matchChars(this, chars)
  final override def matchCharsIgnoreCase(chars: CharSequence):                      Boolean = SequenceUtils.matchCharsIgnoreCase(this, chars)

  final override def matchCharsReversed(chars:           CharSequence, endIndex: Int, ignoreCase: Boolean): Boolean = SequenceUtils.matchCharsReversed(this, chars, endIndex, ignoreCase)
  final override def matchCharsReversed(chars:           CharSequence, endIndex: Int):                      Boolean = SequenceUtils.matchCharsReversed(this, chars, endIndex)
  final override def matchCharsReversedIgnoreCase(chars: CharSequence, endIndex: Int):                      Boolean = SequenceUtils.matchCharsReversedIgnoreCase(this, chars, endIndex)

  final override def matchedCharCount(chars: CharSequence, startIndex: Int, endIndex:   Int, ignoreCase: Boolean): Int = SequenceUtils.matchedCharCount(this, chars, startIndex, endIndex, ignoreCase)
  final override def matchedCharCount(chars: CharSequence, startIndex: Int, ignoreCase: Boolean):                  Int = SequenceUtils.matchedCharCount(this, chars, startIndex, ignoreCase)
  final override def matchedCharCount(chars: CharSequence, startIndex: Int, endIndex:   Int):                      Int = SequenceUtils.matchedCharCount(this, chars, startIndex, endIndex)
  final override def matchedCharCount(chars: CharSequence, startIndex: Int):                                       Int = SequenceUtils.matchedCharCount(this, chars, startIndex)
  final override def matchedCharCountIgnoreCase(chars: CharSequence, startIndex: Int):                Int = SequenceUtils.matchedCharCountIgnoreCase(this, chars, startIndex)
  final override def matchedCharCountIgnoreCase(chars: CharSequence, startIndex: Int, endIndex: Int): Int = SequenceUtils.matchedCharCountIgnoreCase(this, chars, startIndex, endIndex)

  final override def matchedCharCountReversedIgnoreCase(chars: CharSequence, startIndex: Int, fromIndex: Int): Int =
    SequenceUtils.matchedCharCountReversedIgnoreCase(this, chars, startIndex, fromIndex)
  final override def matchedCharCountReversed(chars: CharSequence, startIndex: Int, fromIndex: Int): Int = SequenceUtils.matchedCharCountReversed(this, chars, startIndex, fromIndex)

  final override def matchedCharCountReversed(chars:           CharSequence, fromIndex: Int, ignoreCase: Boolean): Int = SequenceUtils.matchedCharCountReversed(this, chars, fromIndex, ignoreCase)
  final override def matchedCharCountReversed(chars:           CharSequence, fromIndex: Int):                      Int = SequenceUtils.matchedCharCountReversed(this, chars, fromIndex)
  final override def matchedCharCountReversedIgnoreCase(chars: CharSequence, fromIndex: Int):                      Int = SequenceUtils.matchedCharCountReversedIgnoreCase(this, chars, fromIndex)
  final override def matchedCharCount(chars: CharSequence, startIndex: Int, endIndex: Int, fullMatchOnly: Boolean, ignoreCase: Boolean): Int =
    SequenceUtils.matchedCharCount(this, chars, startIndex, endIndex, fullMatchOnly, ignoreCase)
  final override def matchedCharCountReversed(chars: CharSequence, startIndex: Int, fromIndex: Int, ignoreCase: Boolean): Int =
    SequenceUtils.matchedCharCountReversed(this, chars, startIndex, fromIndex, ignoreCase)

  // ---- toString ----

  override def toString: String = {
    val iMax = length()
    val sb   = new StringBuilder(iMax)
    var i    = 0
    while (i < iMax) {
      sb.append(charAt(i))
      i += 1
    }
    sb.toString()
  }

  // ---- normalizeEOL ----

  final override def normalizeEOL():              String = Escaping.normalizeEOL(toString)
  final override def normalizeEndWithEOL():       String = Escaping.normalizeEndWithEOL(toString)
  final override def toVisibleWhitespaceString(): String = SequenceUtils.toVisibleWhitespaceString(this)

  // ---- splitList / split ----

  @SuppressWarnings(Array("unchecked"))
  final override def splitList(delimiter: CharSequence): ju.List[T] = SequenceUtils.splitList(this.asInstanceOf[T], delimiter, 0, 0, Nullable.empty[CharPredicate]).asInstanceOf[ju.List[T]]
  @SuppressWarnings(Array("unchecked"))
  final override def splitList(delimiter: CharSequence, limit: Int, includeDelims: Boolean, trimChars: Nullable[CharPredicate]): ju.List[T] =
    SequenceUtils.splitList(this.asInstanceOf[T], delimiter, limit, if (includeDelims) SequenceUtils.SPLIT_INCLUDE_DELIMS else 0, trimChars).asInstanceOf[ju.List[T]]
  @SuppressWarnings(Array("unchecked"))
  final override def splitList(delimiter: CharSequence, limit: Int, flags: Int): ju.List[T] =
    SequenceUtils.splitList(this.asInstanceOf[T], delimiter, limit, flags, Nullable.empty[CharPredicate]).asInstanceOf[ju.List[T]]
  @SuppressWarnings(Array("unchecked"))
  final override def splitList(delimiter: CharSequence, includeDelims: Boolean, trimChars: Nullable[CharPredicate]): ju.List[T] =
    SequenceUtils.splitList(this.asInstanceOf[T], delimiter, 0, if (includeDelims) SequenceUtils.SPLIT_INCLUDE_DELIMS else 0, trimChars).asInstanceOf[ju.List[T]]

  // NOTE: these default to including delimiters as part of split item
  @SuppressWarnings(Array("unchecked"))
  final override def splitListEOL(): ju.List[T] =
    SequenceUtils.splitList(this.asInstanceOf[T], SequenceUtils.EOL, 0, SequenceUtils.SPLIT_INCLUDE_DELIMS, Nullable.empty[CharPredicate]).asInstanceOf[ju.List[T]]
  @SuppressWarnings(Array("unchecked"))
  final override def splitListEOL(includeDelims: Boolean): ju.List[T] = SequenceUtils
    .splitList(
      this.asInstanceOf[T],
      SequenceUtils.EOL,
      0,
      if (includeDelims) SequenceUtils.SPLIT_INCLUDE_DELIMS else 0,
      Nullable.empty[CharPredicate]
    )
    .asInstanceOf[ju.List[T]]
  @SuppressWarnings(Array("unchecked"))
  final override def splitListEOL(includeDelims: Boolean, trimChars: Nullable[CharPredicate]): ju.List[T] =
    SequenceUtils.splitList(this.asInstanceOf[T], SequenceUtils.EOL, 0, if (includeDelims) SequenceUtils.SPLIT_INCLUDE_DELIMS else 0, trimChars).asInstanceOf[ju.List[T]]
  @SuppressWarnings(Array("unchecked"))
  final override def splitList(delimiter: CharSequence, limit: Int, flags: Int, trimChars: Nullable[CharPredicate]): ju.List[T] =
    SequenceUtils.splitList(this.asInstanceOf[T], delimiter, limit, flags, trimChars).asInstanceOf[ju.List[T]]

  final override def splitEOL():                       Array[T] = split(SequenceUtils.EOL, 0, SequenceUtils.SPLIT_INCLUDE_DELIMS, Nullable.empty[CharPredicate])
  final override def splitEOL(includeDelims: Boolean): Array[T] = split(SequenceUtils.EOL, 0, if (includeDelims) SequenceUtils.SPLIT_INCLUDE_DELIMS else 0, Nullable.empty[CharPredicate])
  final override def split(delimiter: CharSequence, includeDelims: Boolean, trimChars: Nullable[CharPredicate]): Array[T] =
    split(delimiter, 0, if (includeDelims) SequenceUtils.SPLIT_INCLUDE_DELIMS else 0, trimChars)
  final override def split(delimiter: CharSequence):                                                                         Array[T] = split(delimiter, 0, 0, Nullable.empty[CharPredicate])
  final override def split(delimiter: CharSequence, limit: Int, includeDelims: Boolean, trimChars: Nullable[CharPredicate]): Array[T] =
    split(delimiter, limit, if (includeDelims) SequenceUtils.SPLIT_INCLUDE_DELIMS else 0, trimChars)
  final override def split(delimiter: CharSequence, limit: Int, flags: Int): Array[T] = split(delimiter, limit, flags, Nullable.empty[CharPredicate])
  @SuppressWarnings(Array("unchecked"))
  final override def split(delimiter: CharSequence, limit: Int, flags: Int, trimChars: Nullable[CharPredicate]): Array[T] = {
    val list = SequenceUtils.splitList(this.asInstanceOf[T], delimiter, limit, flags, trimChars).asInstanceOf[ju.List[T]]
    list.toArray(emptyArray())
  }

  // ---- appendTo ----

  final override def appendTo(out: StringBuilder, charMapper: Nullable[CharMapper]):                  T = appendTo(out, charMapper, 0, length())
  final override def appendTo(out: StringBuilder, charMapper: Nullable[CharMapper], startIndex: Int): T = appendTo(out, charMapper, startIndex, length())
  final override def appendTo(out: StringBuilder):                                                    T = appendTo(out, Nullable.empty[CharMapper], 0, length())
  final override def appendTo(out: StringBuilder, startIndex: Int):                                   T = appendTo(out, Nullable.empty[CharMapper], startIndex, length())
  final override def appendTo(out: StringBuilder, startIndex: Int, endIndex:                    Int): T = appendTo(out, Nullable.empty[CharMapper], startIndex, endIndex)

  final override def appendTo(out: StringBuilder, charMapper: Nullable[CharMapper], startIndex: Int, endIndex: Int): T = {
    val useSequence: CharSequence = if (!charMapper.isDefined) this else toMapped(charMapper.get)
    out.underlying.append(useSequence, startIndex, endIndex)
    this.asInstanceOf[T]
  }

  // ---- appendRangesTo ----

  final override def appendRangesTo(out: StringBuilder, charMapper: Nullable[CharMapper], ranges: Range*): T = appendRangesTo(out, charMapper, ranges.toSeq)
  final override def appendRangesTo(out: StringBuilder, ranges:     Range*):                               T = appendRangesTo(out, Nullable.empty[CharMapper], ranges.toSeq)
  final override def appendRangesTo(out: StringBuilder, ranges:     Iterable[? <: Range]):                 T = appendRangesTo(out, Nullable.empty[CharMapper], ranges)

  final override def appendRangesTo(out: StringBuilder, charMapper: Nullable[CharMapper], ranges: Iterable[? <: Range]): T = {
    val useSequence: CharSequence = if (!charMapper.isDefined) this else toMapped(charMapper.get)
    ranges.foreach { range =>
      if (range != null && range.isNotNull) out.underlying.append(useSequence, range.start, range.end)
    }
    this.asInstanceOf[T]
  }

  // ---- indexOfAll ----

  final override def indexOfAll(s: CharSequence): Array[Int] = SequenceUtils.indexOfAll(this, s)

  // ---- append EOL / Space ----

  final override def appendEOL():         T = suffixWith(Nullable(SequenceUtils.EOL))
  final override def suffixWithEOL():     T = suffixWith(Nullable(SequenceUtils.EOL))
  final override def prefixWithEOL():     T = prefixWith(Nullable(SequenceUtils.EOL))
  final override def prefixOnceWithEOL(): T = prefixOnceWith(Nullable(SequenceUtils.EOL))
  final override def suffixOnceWithEOL(): T = suffixOnceWith(Nullable(SequenceUtils.EOL))

  final override def appendSpace():                T = suffixWith(Nullable(SequenceUtils.SPACE))
  final override def suffixWithSpace():            T = suffixWith(Nullable(SequenceUtils.SPACE))
  final override def prefixWithSpace():            T = prefixWith(Nullable(SequenceUtils.SPACE))
  final override def appendSpaces(count:     Int): T = suffixWith(Nullable(RepeatedSequence.ofSpaces(count)))
  final override def suffixWithSpaces(count: Int): T = suffixWith(Nullable(RepeatedSequence.ofSpaces(count)))
  final override def prefixWithSpaces(count: Int): T = prefixWith(Nullable(RepeatedSequence.ofSpaces(count)))
  final override def prefixOnceWithSpace():        T = prefixOnceWith(Nullable(SequenceUtils.SPACE))
  final override def suffixOnceWithSpace():        T = suffixOnceWith(Nullable(SequenceUtils.SPACE))

  // ---- prefix/suffix with ----

  override def prefixWith(prefix: Nullable[CharSequence]): T =
    if (!prefix.isDefined || prefix.get.length() == 0) this.asInstanceOf[T]
    else {
      val b = seqBuilder
      b.add(prefix)
      b.add(Nullable(this: CharSequence))
      b.toSequence
    }

  override def suffixWith(suffix: Nullable[CharSequence]): T =
    // convoluted to allow BasedCharSequence to use PrefixedCharSequence so all fits into SegmentedCharSequence
    if (!suffix.isDefined || suffix.get.length() == 0) this.asInstanceOf[T]
    else {
      val b = seqBuilder
      b.add(Nullable(this: CharSequence))
      b.add(suffix)
      b.toSequence
    }

  final override def prefixOnceWith(prefix: Nullable[CharSequence]): T =
    if (!prefix.isDefined || prefix.get.length() == 0 || startsWith(prefix.get)) this.asInstanceOf[T] else prefixWith(prefix)

  final override def suffixOnceWith(suffix: Nullable[CharSequence]): T =
    if (!suffix.isDefined || suffix.get.length() == 0 || endsWith(suffix.get)) this.asInstanceOf[T] else suffixWith(suffix)

  // ---- replace ----

  final override def replace(startIndex: Int, endIndex: Int, replacement: CharSequence): T = {
    val length = this.length()
    val sIdx   = Math.max(startIndex, 0)
    val eIdx   = Math.min(endIndex, length)

    val segments = seqBuilder
    segments.add(Nullable(subSequence(0, sIdx)))
    segments.add(Nullable(replacement))
    segments.add(Nullable(subSequence(eIdx)))
    segments.toSequence
  }

  final override def replace(find: CharSequence, replace: CharSequence): T = {
    val indices = indexOfAll(find)
    if (indices.isEmpty) {
      this.asInstanceOf[T]
    } else {
      val segments = seqBuilder
      val iMax     = indices.length
      val length   = this.length()

      var i       = 0
      var lastPos = 0
      while (i < iMax) {
        val pos = indices(i)
        i += 1
        if (lastPos < pos) segments.add(Nullable(subSequence(lastPos, pos)))
        lastPos = pos + find.length()
        segments.add(Nullable(replace))
      }

      if (lastPos < length) {
        segments.add(Nullable(subSequence(lastPos, length)))
      }

      segments.toSequence
    }
  }

  // ---- append ----

  final override def append(sequences: CharSequence*): T =
    append(sequences.toSeq)

  final override def append(sequences: Iterable[? <: CharSequence]): T = {
    val segments = seqBuilder
    segments.add(Nullable(this: CharSequence))
    sequences.foreach { seq =>
      segments.add(Nullable(seq))
    }
    segments.toSequence
  }

  // ---- extractRanges ----

  final override def extractRanges(ranges: Range*): T =
    extractRanges(ranges.toSeq)

  final override def extractRanges(ranges: Iterable[Range]): T = {
    val segments = seqBuilder
    ranges.foreach { range =>
      if (!(range == null || range.isNull)) segments.add(Nullable(range.safeSubSequence(this)))
    }
    segments.toSequence
  }

  // ---- column at index ----

  final override def columnAtIndex(index:     Int): Int                    = SequenceUtils.columnAtIndex(this, index)
  final override def lineColumnAtIndex(index: Int): Pair[Integer, Integer] = SequenceUtils.lineColumnAtIndex(this, index)

  // ---- isIn ----

  override def isIn(texts: Array[String]): Boolean =
    SequenceUtils.containedBy(texts, this)

  override def isIn(texts: ju.Collection[? <: CharSequence]): Boolean =
    SequenceUtils.containedBy(texts, this)
}
