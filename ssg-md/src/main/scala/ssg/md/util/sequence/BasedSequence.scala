/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/BasedSequence.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/BasedSequence.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package sequence

import ssg.md.Nullable
import ssg.md.util.data.{ DataHolder, DataKeyBase }
import ssg.md.util.misc.{ CharPredicate, Pair }
import ssg.md.util.sequence.builder.IBasedSegmentBuilder
import ssg.md.util.sequence.builder.tree.SegmentTree

import java.util as ju

/** A CharSequence that references original char sequence with offsets into original preserved.
  *
  * NOTE: '\0' changed to '\uFFFD' use [[ssg.md.util.sequence.mappers.NullEncoder.decodeNull]] mapper to get original null chars.
  *
  * Since equals is used for comparison of sequences and strings by base sequence manager, a base sequence with NUL may not compare equal to an equivalent unwrapped sequence because NUL chars are not
  * converted. For Strings this is handled by using String.equals() for comparison. For other CharacterSequence types the match will fail if original has NUL in it.
  *
  * a subSequence() returns a sub-sequence from the original base sequence with corresponding offsets
  */
@SuppressWarnings(Array("unchecked"))
trait BasedSequence extends IRichSequence[BasedSequence], BasedOptionsHolder {

  override def getBuilder[B <: builder.ISequenceBuilder[B, BasedSequence]]: B

  /** Get the underlying object on which this sequence contents are based
    *
    * @return
    *   underlying object containing original text
    */
  def getBase: AnyRef

  /** Get the base sequence for the text
    *
    * @return
    *   base sequence
    */
  def getBaseSequence: BasedSequence

  /** Get the start offset of this sequence into [[getBaseSequence]] and [[getBase]] original text source.
    *
    * @return
    *   start offset in original text
    */
  def startOffset: Int

  /** Get the end offset of this sequence into [[getBaseSequence]] and [[getBase]] original text source.
    *
    * @return
    *   end offset in original text
    */
  def endOffset: Int

  /** Get the offset of index in this sequence mapped to offset into [[getBaseSequence]] and [[getBase]] original text source.
    *
    * @param index
    *   index for which to get the offset in original source
    * @return
    *   offset of index of this sequence in original text
    */
  def getIndexOffset(index: Int): Int

  /** Add segments for this sequence, replacing out of base characters with strings
    *
    * @param builder
    *   builder
    */
  def addSegments(builder: IBasedSegmentBuilder[?]): Unit

  /** Get the segment tree for this sequence
    *
    * @return
    *   segment tree
    */
  def getSegmentTree: SegmentTree

  /** Get the range of this sequence in original [[getBaseSequence]] and [[getBase]] original text source.
    *
    * @return
    *   Range of start offset and end offset
    */
  def getSourceRange: Range

  /** Get a portion of this sequence
    *
    * @param startIndex
    *   offset from startIndex of this sequence
    * @param endIndex
    *   offset from startIndex of this sequence
    * @return
    *   based sequence which represents the requested range of this sequence.
    */
  override def subSequence(startIndex: Int, endIndex: Int): BasedSequence

  /** Get a portion of this sequence's base sequence
    *
    * NOTE: this means that if this sequence applies modifications to the original sequence then these modifications are NOT applied to the returned sequence.
    *
    * @param startIndex
    *   offset from 0 of original sequence
    * @param endIndex
    *   offset from 0 of original sequence
    * @return
    *   based sequence whose contents reflect the selected portion
    */
  def baseSubSequence(startIndex: Int, endIndex: Int): BasedSequence

  /** Get a portion of the original sequence that this sequence is based on
    *
    * @param startIndex
    *   offset from 0 of original sequence
    * @return
    *   based sequence from startIndex to the endIndex
    */
  def baseSubSequence(startIndex: Int): BasedSequence

  /** Safe, if index out of range but based sequence has characters will return those, else returns '\0'
    *
    * @param index
    *   index in string
    * @return
    *   character or '\0' if index out of base sequence
    */
  def safeBaseCharAt(index: Int): Char

  /** Safe, if index out of range but based sequence has characters will return those, else returns '\0'
    *
    * @param index
    *   index in string
    * @param predicate
    *   character set predicate
    * @return
    *   true if character at index tests true
    */
  def isBaseCharAt(index: Int, predicate: CharPredicate): Boolean

  /** Get empty prefix to this sequence, same as subSequence(0, 0) */
  def getEmptyPrefix: BasedSequence

  /** Get empty suffix to this sequence, same as subSequence(length()) */
  def getEmptySuffix: BasedSequence

  /** Get the unescaped string of this sequence content */
  def unescape(): String

  /** Get the unescaped string of this sequence content without unescaping entities */
  def unescapeNoEntities(): String

  /** Get the unescaped string of this sequence content */
  def unescape(textMapper: ReplacedTextMapper): BasedSequence

  /** replace any \r\n and \r by \n */
  def normalizeEOL(textMapper: ReplacedTextMapper): BasedSequence

  /** replace any \r\n and \r by \n, append terminating EOL if one is not present */
  def normalizeEndWithEOL(textMapper: ReplacedTextMapper): BasedSequence

  /** Test if the given sequence is a continuation of this sequence in original source text */
  def isContinuedBy(other: BasedSequence): Boolean

  /** Test if this sequence is a continuation of the given sequence in original source text */
  def isContinuationOf(other: BasedSequence): Boolean

  /** Splice the given sequence to the end of this one and return a BasedSequence of the result. Does not copy anything, creates a new based sequence of the original text but one that spans characters
    * of this sequence and other
    */
  def spliceAtEnd(other: BasedSequence): BasedSequence

  /** start/end offset based containment, not textual */
  def containsAllOf(other: BasedSequence): Boolean

  /** start/end offset based containment, not textual */
  def containsSomeOf(other: BasedSequence): Boolean

  /** Get the prefix part of this from other, start/end offset based containment, not textual */
  def prefixOf(other: BasedSequence): BasedSequence

  /** Get the suffix part of this from other, start/end offset based containment, not textual */
  def suffixOf(other: BasedSequence): BasedSequence

  /** start/end offset based intersection, not textual */
  def intersect(other: BasedSequence): BasedSequence

  /** Extend this based sequence to include characters from underlying based sequence */
  def extendByAny(charSet:      CharPredicate, maxCount: Int): BasedSequence
  def extendByAny(charSet:      CharPredicate):                BasedSequence
  def extendByOneOfAny(charSet: CharPredicate):                BasedSequence

  /** Test for line containing some of the characters in the set */
  def containsSomeIn(charSet: CharPredicate): Boolean

  /** Test for line containing some characters not in the set */
  def containsSomeNotIn(charSet: CharPredicate): Boolean

  /** Test for line contains only characters from the set */
  def containsOnlyIn(charSet: CharPredicate): Boolean

  /** Test for line containing only characters not in the set */
  def containsOnlyNotIn(charSet: CharPredicate): Boolean

  /** Extend this based sequence to include characters from underlying based sequence not in character set */
  def extendByAnyNot(charSet:      CharPredicate, maxCount: Int): BasedSequence
  def extendByAnyNot(charSet:      CharPredicate):                BasedSequence
  def extendByOneOfAnyNot(charSet: CharPredicate):                BasedSequence

  @deprecated("Use extendByAnyNot", "")
  def extendToAny(charSet: CharPredicate, maxCount: Int): BasedSequence = extendByAnyNot(charSet, maxCount)

  @deprecated("Use extendByAnyNot", "")
  def extendToAny(charSet: CharPredicate): BasedSequence = extendByAnyNot(charSet)

  /** Extend in contained based sequence */
  def extendToEndOfLine(eolChars:   CharPredicate, includeEol: Boolean): BasedSequence
  def extendToEndOfLine(eolChars:   CharPredicate):                      BasedSequence
  def extendToEndOfLine(includeEol: Boolean):                            BasedSequence
  def extendToEndOfLine():                                               BasedSequence

  def extendToStartOfLine(eolChars:   CharPredicate, includeEol: Boolean): BasedSequence
  def extendToStartOfLine(eolChars:   CharPredicate):                      BasedSequence
  def extendToStartOfLine(includeEol: Boolean):                            BasedSequence
  def extendToStartOfLine():                                               BasedSequence

  /** Extend this based sequence to include characters from underlying based sequence taking tab expansion to 4th spaces into account */
  def prefixWithIndent(maxColumns: Int): BasedSequence
  def prefixWithIndent():                BasedSequence

  /*
   These are convenience methods returning coordinates in Base Sequence of this sequence
   */
  def baseLineColumnAtIndex(index: Int): Pair[Integer, Integer]
  def baseLineRangeAtIndex(index:  Int): Range
  def baseEndOfLine(index:         Int): Int
  def baseEndOfLineAnyEOL(index:   Int): Int
  def baseStartOfLine(index:       Int): Int
  def baseStartOfLineAnyEOL(index: Int): Int
  def baseColumnAtIndex(index:     Int): Int

  def baseLineColumnAtStart(): Pair[Integer, Integer]
  def baseLineColumnAtEnd():   Pair[Integer, Integer]
  def baseEndOfLine():         Int
  def baseEndOfLineAnyEOL():   Int
  def baseStartOfLine():       Int
  def baseStartOfLineAnyEOL(): Int
  def baseLineRangeAtStart():  Range
  def baseLineRangeAtEnd():    Range
  def baseColumnAtEnd():       Int
  def baseColumnAtStart():     Int
}

object BasedSequence {

  val NULL:           BasedSequence          = new EmptyBasedSequence()
  val EMPTY:          BasedSequence          = new EmptyBasedSequence()
  val EOL:            BasedSequence          = CharSubSequence.of(SequenceUtils.EOL)
  val SPACE:          BasedSequence          = CharSubSequence.of(SequenceUtils.SPACE)
  val EMPTY_LIST:     ju.List[BasedSequence] = new ju.ArrayList[BasedSequence]()
  val EMPTY_ARRAY:    Array[BasedSequence]   = new Array[BasedSequence](0)
  val EMPTY_SEGMENTS: Array[BasedSequence]   = new Array[BasedSequence](0)
  val LINE_SEP:       BasedSequence          = CharSubSequence.of(SequenceUtils.LINE_SEP)

  def of(charSequence: Nullable[CharSequence]): BasedSequence =
    BasedSequenceImpl.create(charSequence)

  def ofSpaces(count: Int): BasedSequence =
    of(Nullable(RepeatedSequence.ofSpaces(count)))

  def repeatOf(c: Char, count: Int): BasedSequence =
    of(Nullable(RepeatedSequence.repeatOf(String.valueOf(c), 0, count)))

  def repeatOf(chars: CharSequence, count: Int): BasedSequence =
    of(Nullable(RepeatedSequence.repeatOf(chars, 0, chars.length() * count)))

  def repeatOf(chars: CharSequence, startIndex: Int, endIndex: Int): BasedSequence =
    of(Nullable(RepeatedSequence.repeatOf(chars, startIndex, endIndex)))

  /** Inner class for NULL/EMPTY based sequences
    */
  private[sequence] class EmptyBasedSequence extends BasedSequenceImpl(0) {

    override def optionFlags: Int = 0

    override def allOptions(options: Int): Boolean = false

    override def anyOptions(options: Int): Boolean = false

    @SuppressWarnings(Array("unchecked"))
    override def getOption[T](dataKey: DataKeyBase[T]): Nullable[T] =
      Nullable(dataKey.get(Nullable.empty[DataHolder]))

    override def options: Nullable[DataHolder] = Nullable.empty[DataHolder]

    override def length(): Int = 0

    override def charAt(index: Int): Char =
      throw new StringIndexOutOfBoundsException("EMPTY sequence has no characters")

    override def getIndexOffset(index: Int): Int = {
      SequenceUtils.validateIndexInclusiveEnd(index, length())
      0
    }

    override def subSequence(i: Int, i1: Int): BasedSequence = {
      SequenceUtils.validateStartEnd(i, i1, length())
      this
    }

    override def baseSubSequence(startIndex: Int, endIndex: Int): BasedSequence =
      subSequence(startIndex, endIndex)

    override def getBaseSequence: BasedSequence = this

    override def getBase: AnyRef = this

    override def startOffset: Int = 0

    override def endOffset: Int = 0

    override def getSourceRange: Range = Range.NULL
  }
}
