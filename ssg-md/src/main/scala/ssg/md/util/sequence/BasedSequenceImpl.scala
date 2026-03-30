/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/BasedSequenceImpl.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence

import ssg.md.Nullable
import ssg.md.util.misc.{ CharPredicate, Pair, Utils }
import ssg.md.util.sequence.builder.{ BasedSegmentBuilder, IBasedSegmentBuilder, SequenceBuilder }
import ssg.md.util.sequence.builder.tree.SegmentTree
import ssg.md.util.sequence.mappers.CharMapper

import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

/** Implementation of BasedSequence
  */
abstract class BasedSequenceImpl(hash: Int) extends IRichSequenceBase[BasedSequence](hash) with BasedSequence {

  override def emptyArray(): Array[BasedSequence] = BasedSequence.EMPTY_ARRAY

  override def nullSequence(): BasedSequence = BasedSequence.NULL

  override def sequenceOf(charSequence: Nullable[CharSequence], startIndex: Int, endIndex: Int): BasedSequence =
    BasedSequence.of(charSequence).subSequence(startIndex, endIndex)

  @SuppressWarnings(Array("unchecked"))
  override def getBuilder[B <: builder.ISequenceBuilder[B, BasedSequence]]: B =
    SequenceBuilder.emptyBuilder(this).asInstanceOf[B]

  override def addSegments(builder: IBasedSegmentBuilder[?]): Unit =
    builder.append(startOffset, endOffset)

  /** Get the segment tree for this sequence or null if sequence is contiguous from startOffset to endOffset
    *
    * @return
    *   null for contiguous sequences, else segment tree for this sequence
    */
  override def getSegmentTree: SegmentTree = {
    // default implementation
    val segmentBuilder = BasedSegmentBuilder.emptyBuilder(getBaseSequence)
    addSegments(segmentBuilder)
    SegmentTree.build(segmentBuilder.getSegments, segmentBuilder.getText)
  }

  override def toMapped(mapper: CharMapper): BasedSequence =
    MappedBasedSequence.mappedOf(this, mapper)

  final override def baseSubSequence(startIndex: Int): BasedSequence =
    baseSubSequence(startIndex, getBaseSequence.endOffset)

  override def baseSubSequence(startIndex: Int, endIndex: Int): BasedSequence =
    getBaseSequence.subSequence(startIndex, endIndex)

  override def safeCharAt(index: Int): Char =
    if (index < 0 || index >= length()) SequenceUtils.NUL else charAt(index)

  override def safeBaseCharAt(index: Int): Char = {
    val so = startOffset
    if (index >= so && index < so + length()) charAt(index - so)
    else getBaseSequence.safeCharAt(index)
  }

  override def isBaseCharAt(index: Int, predicate: CharPredicate): Boolean =
    predicate.test(safeBaseCharAt(index))

  override def getEmptyPrefix: BasedSequence = subSequence(0, 0)

  override def getEmptySuffix: BasedSequence = subSequence(length())

  override def toStringOrNull(): Nullable[String] =
    if (isNull) Nullable.empty[String] else Nullable(toString)

  override def unescape(): String = Escaping.unescapeString(this)

  override def unescapeNoEntities(): String = Escaping.unescapeString(this, false)

  override def unescape(textMapper: ReplacedTextMapper): BasedSequence =
    Escaping.unescape(this, textMapper)

  override def normalizeEOL(textMapper: ReplacedTextMapper): BasedSequence =
    Escaping.normalizeEOL(this, textMapper)

  override def normalizeEndWithEOL(textMapper: ReplacedTextMapper): BasedSequence =
    Escaping.normalizeEndWithEOL(this, textMapper)

  override def isContinuedBy(other: BasedSequence): Boolean =
    other.length() > 0 && length() > 0 && (other.getBase eq getBase) && other.startOffset == endOffset

  override def isContinuationOf(other: BasedSequence): Boolean =
    other.length() > 0 && length() > 0 && (other.getBase eq getBase) && other.endOffset == startOffset

  override def spliceAtEnd(other: BasedSequence): BasedSequence =
    if (other.isEmpty()) {
      this
    } else if (isEmpty()) {
      other
    } else {
      assert(
        isContinuedBy(other),
        s"sequence[$startOffset, $endOffset] is not continued by other[${other.startOffset}, ${other.endOffset}]"
      )
      baseSubSequence(startOffset, other.endOffset)
    }

  override def containsAllOf(other: BasedSequence): Boolean =
    (getBase eq other.getBase) && other.startOffset >= startOffset && other.endOffset <= endOffset

  override def containsSomeOf(other: BasedSequence): Boolean =
    (getBase eq other.getBase) && !(startOffset >= other.endOffset || endOffset <= other.startOffset)

  override def intersect(other: BasedSequence): BasedSequence =
    if (getBase ne other.getBase) BasedSequence.NULL
    else if (other.endOffset <= startOffset) subSequence(0, 0)
    else if (other.startOffset >= endOffset) subSequence(length(), length())
    else baseSubSequence(Utils.max(startOffset, other.startOffset), Utils.min(endOffset, other.endOffset))

  override def containsSomeIn(charSet: CharPredicate): Boolean =
    boundary[Boolean] {
      val iMax = length()
      var i    = 0
      while (i < iMax) {
        if (charSet.test(charAt(i))) break(true)
        i += 1
      }
      false
    }

  override def containsSomeNotIn(charSet: CharPredicate): Boolean =
    boundary[Boolean] {
      val iMax = length()
      var i    = 0
      while (i < iMax) {
        if (!charSet.test(charAt(i))) break(true)
        i += 1
      }
      false
    }

  override def containsOnlyIn(charSet: CharPredicate): Boolean = !containsSomeNotIn(charSet)

  override def containsOnlyNotIn(charSet: CharPredicate): Boolean = !containsSomeIn(charSet)

  override def extendByAny(charSet:      CharPredicate): BasedSequence = extendByAny(charSet, Integer.MAX_VALUE - endOffset)
  override def extendByOneOfAny(charSet: CharPredicate): BasedSequence = extendByAny(charSet, 1)

  override def extendByAny(charSet: CharPredicate, maxCount: Int): BasedSequence = {
    val count = getBaseSequence.countLeading(charSet, endOffset, endOffset + maxCount)
    if (count == 0) this else baseSubSequence(startOffset, endOffset + count)
  }

  override def extendByAnyNot(charSet:      CharPredicate): BasedSequence = extendByAnyNot(charSet, Integer.MAX_VALUE - endOffset)
  override def extendByOneOfAnyNot(charSet: CharPredicate): BasedSequence = extendByAnyNot(charSet, 1)

  override def extendByAnyNot(charSet: CharPredicate, maxCount: Int): BasedSequence = {
    val count = getBaseSequence.countLeadingNot(charSet, endOffset, endOffset + maxCount)
    if (count == getBaseSequence.length() - endOffset) this
    else baseSubSequence(startOffset, endOffset + count + 1)
  }

  final override def extendToEndOfLine(eolChars:     CharPredicate): BasedSequence = extendToEndOfLine(eolChars, false)
  final override def extendToEndOfLine(includeEol:   Boolean):       BasedSequence = extendToEndOfLine(CharPredicate.EOL, includeEol)
  final override def extendToEndOfLine():                            BasedSequence = extendToEndOfLine(CharPredicate.EOL, false)
  final override def extendToStartOfLine(eolChars:   CharPredicate): BasedSequence = extendToStartOfLine(eolChars, false)
  final override def extendToStartOfLine(includeEol: Boolean):       BasedSequence = extendToStartOfLine(CharPredicate.EOL, includeEol)
  final override def extendToStartOfLine():                          BasedSequence = extendToStartOfLine(CharPredicate.EOL, false)

  final override def extendToEndOfLine(eolChars: CharPredicate, includeEol: Boolean): BasedSequence =
    boundary[BasedSequence] {
      val eo = endOffset

      // if already have eol then no need to check
      if (eolChars.test(lastChar())) break(this)

      val baseSequence = getBaseSequence
      var eol          = baseSequence.endOfLine(eo)

      if (includeEol) {
        eol = Math.min(baseSequence.length(), eol + Math.min(baseSequence.eolStartLength(eol), 1))
      }

      if (eol != eo) baseSequence.subSequence(startOffset, eol)
      else this
    }

  override def extendToStartOfLine(eolChars: CharPredicate, includeEol: Boolean): BasedSequence =
    boundary[BasedSequence] {
      val so = startOffset

      // if already have eol then no need to check
      if (eolChars.test(firstChar())) break(this)

      val baseSequence = getBaseSequence
      var sol          = baseSequence.startOfLine(so)

      if (includeEol) {
        sol = Math.max(0, sol - Math.min(baseSequence.eolEndLength(sol), 1))
      }

      if (sol != so) baseSequence.subSequence(sol, endOffset)
      else this
    }

  override def prefixWith(prefix: Nullable[CharSequence]): BasedSequence =
    if (!prefix.isDefined || prefix.get.length() == 0) this
    else PrefixedSubSequence.prefixOf(prefix.get.toString, this)

  final def prefixWithIndent(): BasedSequence = prefixWithIndent(Integer.MAX_VALUE)

  override def prefixWithIndent(maxColumns: Int): BasedSequence = {
    var offset       = startOffset
    val so           = startOffset
    var columns      = 0
    var columnOffset = 0
    var hadTab       = false

    // find '\n' - search backwards from startOffset
    var startOff = so
    boundary {
      while (startOff >= 0) {
        val c = getBaseSequence.charAt(startOff)
        if (c == '\t') hadTab = true
        else if (c == '\n') {
          startOff += 1
          break(())
        }
        startOff -= 1
      }
    }

    if (startOff < 0) startOff = 0

    if (startOff < offset) {
      if (hadTab) {
        // see what is the column at offset
        val offsetColumns = new Array[Int](offset - startOff)
        var currOffset    = startOff
        while (currOffset < offset) {
          if (getBaseSequence.charAt(currOffset) == '\t') {
            val cols = 4 - (columnOffset % 4)
            offsetColumns(currOffset - startOff) = cols
            columnOffset += cols
          } else {
            offsetColumns(currOffset - startOff) = 1
            columnOffset += 1
          }
          currOffset += 1
        }

        boundary {
          while (columns < maxColumns && offset > 0 && (getBaseSequence.charAt(offset - 1) == ' ' || getBaseSequence.charAt(offset - 1) == '\t')) {
            columns += offsetColumns(offset - 1 - startOff)
            if (columns > maxColumns) break(())
            offset -= 1
          }
        }
      } else {
        while (columns < maxColumns && offset > 0 && (getBaseSequence.charAt(offset - 1) == ' ' || getBaseSequence.charAt(offset - 1) == '\t')) {
          columns += 1
          offset -= 1
        }
      }
    }

    if (offset == so) this else baseSubSequence(offset, endOffset)
  }

  override def prefixOf(other: BasedSequence): BasedSequence =
    if (getBase ne other.getBase) BasedSequence.NULL
    else if (other.startOffset <= startOffset) subSequence(0, 0)
    else if (other.startOffset >= endOffset) this
    else baseSubSequence(startOffset, other.startOffset)

  override def suffixOf(other: BasedSequence): BasedSequence =
    if (getBase ne other.getBase) BasedSequence.NULL
    else if (other.endOffset >= endOffset) subSequence(length(), length())
    else if (other.endOffset <= startOffset) this
    else baseSubSequence(other.endOffset, endOffset)

  // ---- base line/column helpers ----

  override def baseLineRangeAtIndex(index:  Int): Range                  = getBaseSequence.lineRangeAt(index)
  override def baseLineColumnAtIndex(index: Int): Pair[Integer, Integer] = getBaseSequence.lineColumnAtIndex(index)
  override def baseEndOfLine(index:         Int): Int                    = getBaseSequence.endOfLine(index)
  override def baseEndOfLineAnyEOL(index:   Int): Int                    = getBaseSequence.endOfLineAnyEOL(index)
  override def baseStartOfLine(index:       Int): Int                    = getBaseSequence.startOfLine(index)
  override def baseStartOfLineAnyEOL(index: Int): Int                    = getBaseSequence.startOfLineAnyEOL(index)
  override def baseColumnAtIndex(index:     Int): Int                    = getBaseSequence.columnAtIndex(index)

  override def baseEndOfLine():       Int                    = baseEndOfLine(endOffset)
  override def baseEndOfLineAnyEOL(): Int                    = baseEndOfLineAnyEOL(endOffset)
  override def baseColumnAtEnd():     Int                    = baseColumnAtIndex(endOffset)
  override def baseLineRangeAtEnd():  Range                  = baseLineRangeAtIndex(endOffset)
  override def baseLineColumnAtEnd(): Pair[Integer, Integer] = baseLineColumnAtIndex(endOffset)

  override def baseStartOfLine():       Int                    = baseStartOfLine(startOffset)
  override def baseStartOfLineAnyEOL(): Int                    = baseStartOfLineAnyEOL(startOffset)
  override def baseColumnAtStart():     Int                    = baseColumnAtIndex(startOffset)
  override def baseLineRangeAtStart():  Range                  = baseLineRangeAtIndex(startOffset)
  override def baseLineColumnAtStart(): Pair[Integer, Integer] = baseLineColumnAtIndex(startOffset)
}

object BasedSequenceImpl {

  def firstNonNull(sequences: BasedSequence*): BasedSequence =
    boundary[BasedSequence] {
      for (sequence <- sequences)
        if (sequence != null && (sequence ne BasedSequence.NULL)) {
          break(sequence)
        }
      BasedSequence.NULL
    }

  private[sequence] def create(charSequence: Nullable[CharSequence]): BasedSequence =
    if (!charSequence.isDefined) BasedSequence.NULL
    else
      charSequence.get match {
        case bs: BasedSequence => bs
        case cs => SubSequence.create(cs)
      }
}
