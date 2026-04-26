/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/IRichSequence.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/IRichSequence.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package sequence

import ssg.md.Nullable
import ssg.md.util.misc.{ CharPredicate, Pair }
import ssg.md.util.sequence.builder.ISequenceBuilder
import ssg.md.util.sequence.mappers.CharMapper

import java.util as ju

/** A CharSequence that provides a rich set of manipulation methods.
  *
  * NOTE: '\0' changed to '\uFFFD' use [[ssg.md.util.sequence.mappers.NullEncoder.decodeNull]] mapper to get original null chars.
  *
  * safe access methods return '\0' for no char response.
  */
@SuppressWarnings(Array("unchecked"))
trait IRichSequence[T <: IRichSequence[T]] extends CharSequence with Comparable[CharSequence] {

  /** Comparison to another CharSequence should result in a match if their contents are equal regardless of the implementation. Should not resort to content comparison unless
    *
    * @param other
    *   another char sequence
    * @return
    *   true if character sequences are equal
    */
  override def equals(other: Any): Boolean

  /** Should return hashCode of the underlying character sequence which is equal to the String value of that sequence
    *
    * @return
    *   hash code as if it was a string of the sequence content
    */
  override def hashCode(): Int

  def emptyArray():   Array[T]
  def nullSequence(): T

  /** @return the last character of the sequence or '\0' if empty */
  def lastChar(): Char

  /** @return the first character of the sequence or '\0' if empty */
  def firstChar(): Char

  /** return char at index or '\0' if index < 0 or >= length()
    *
    * @param index
    *   index
    * @return
    *   char or '\0'
    */
  def safeCharAt(index: Int): Char

  /** Get a portion of this sequence
    *
    * NOTE: the returned value should only depend on start/end indices.
    *
    * @param startIndex
    *   offset from startIndex of this sequence
    * @param endIndex
    *   offset from startIndex of this sequence
    * @return
    *   based sequence whose contents reflect the selected portion
    */
  override def subSequence(startIndex: Int, endIndex: Int): T

  /** Get a portion of this sequence, if index < 0 use 0, if > length() use length
    *
    * @param startIndex
    *   offset from startIndex of this sequence
    * @param endIndex
    *   offset from startIndex of this sequence
    * @return
    *   based sequence whose contents reflect the selected portion
    */
  def safeSubSequence(startIndex: Int, endIndex: Int): T

  /** Get a portion of this sequence, if index < 0 use 0, if > length() use length
    *
    * @param startIndex
    *   offset from startIndex of this sequence
    * @return
    *   based sequence whose contents reflect the selected portion
    */
  def safeSubSequence(startIndex: Int): T

  /** Get a portion of this sequence selected by range
    *
    * @param range
    *   range to get, coordinates offset from start of this sequence
    * @return
    *   based sequence whose contents reflect the selected portion, if range.isNull then [[nullSequence]]
    */
  def subSequence(range: Range): T

  /** Get a portion of this sequence before one selected by range
    *
    * @param range
    *   range to get, coordinates offset from start of this sequence
    * @return
    *   based sequence whose contents reflect the selected portion, if range.isNull then [[nullSequence]]
    */
  def subSequenceBefore(range: Range): T

  /** Get a portion of this sequence after one selected by range
    *
    * @param range
    *   range to get, coordinates offset from start of this sequence
    * @return
    *   based sequence whose contents reflect the selected portion, if range.isNull then [[nullSequence]]
    */
  def subSequenceAfter(range: Range): T

  /** Get a portion of this sequence starting from a given offset to endIndex of the sequence
    *
    * @param startIndex
    *   offset from startIndex of this sequence
    * @return
    *   based sequence whose contents reflect the selected portion
    */
  def subSequence(startIndex: Int): T

  /** Convenience method to get characters offset from endIndex of sequence.
    *
    * @param startIndex
    *   offset from endIndex of sequence [ 0..length() )
    * @param endIndex
    *   offset from endIndex of sequence [ 0..length() )
    * @return
    *   selected portion spanning length() - startIndex to length() - endIndex of this sequence
    */
  def endSequence(startIndex: Int, endIndex: Int): T

  /** Convenience method to get characters offset from endIndex of sequence.
    *
    * @param startIndex
    *   offset from endIndex of sequence [ 0..length() )
    * @return
    *   selected portion spanning length() - startIndex to length() of this sequence
    */
  def endSequence(startIndex: Int): T

  /** Convenience method to get characters offset from end of sequence.
    *
    * @param index
    *   offset from end of sequence
    * @return
    *   character located at length() - index in this sequence
    */
  def endCharAt(index: Int): Char

  /** Convenience method to get characters offset from start or end of sequence.
    *
    * @param startIndex
    *   offset into this sequence
    * @param endIndex
    *   offset into this sequence
    * @return
    *   selected portion spanning startIndex to endIndex of this sequence. If offset < 0 then taken as relative to length()
    */
  def midSequence(startIndex: Int, endIndex: Int): T

  /** Convenience method to get characters offset from start or end of sequence.
    *
    * @param startIndex
    *   offset into this sequence
    * @return
    *   selected portion spanning startIndex to length() of this sequence. If offset < 0 then taken as relative to length()
    */
  def midSequence(startIndex: Int): T

  /** Convenience method to get characters offset from start or end of sequence.
    *
    * @param index
    *   of character to get
    * @return
    *   character at index or \0 if index is outside valid range for this sequence
    */
  def midCharAt(index: Int): Char

  /** Factory function
    *
    * @param charSequence
    *   char sequence from which to construct a rich char sequence
    * @return
    *   rich char sequence from given inputs
    */
  def sequenceOf(charSequence: Nullable[CharSequence]): T

  /** Factory function
    *
    * @param charSequence
    *   char sequence from which to construct a rich char sequence
    * @param startIndex
    *   start index of the sequence to use
    * @return
    *   rich char sequence from given inputs
    */
  def sequenceOf(charSequence: Nullable[CharSequence], startIndex: Int): T

  /** Factory function
    *
    * @param charSequence
    *   char sequence from which to construct a rich char sequence
    * @param startIndex
    *   start index of the sequence to use
    * @param endIndex
    *   end index of the sequence to use
    * @return
    *   rich char sequence from given inputs
    */
  def sequenceOf(charSequence: Nullable[CharSequence], startIndex: Int, endIndex: Int): T

  /** Get a sequence builder for this sequence type
    *
    * @tparam B
    *   type of builder
    * @return
    *   builder which can build this type of sequence
    */
  def getBuilder[B <: ISequenceBuilder[B, T]]: B

  // ---- indexOf methods ----

  def indexOf(s: CharSequence):                                Int
  def indexOf(s: CharSequence, fromIndex: Int):                Int
  def indexOf(s: CharSequence, fromIndex: Int, endIndex: Int): Int

  def indexOf(c:    Char, fromIndex:          Int, endIndex: Int): Int
  def indexOf(c:    Char, fromIndex:          Int):                Int
  def indexOf(c:    Char):                                         Int
  def indexOfAny(s: CharPredicate, fromIndex: Int, endIndex: Int): Int
  def indexOfAny(s: CharPredicate, fromIndex: Int):                Int
  def indexOfAny(s: CharPredicate):                                Int

  def indexOfNot(c: Char, fromIndex: Int, endIndex: Int): Int
  def indexOfNot(c: Char, fromIndex: Int):                Int
  def indexOfNot(c: Char):                                Int

  def indexOfAnyNot(s: CharPredicate, fromIndex: Int, endIndex: Int): Int
  def indexOfAnyNot(s: CharPredicate, fromIndex: Int):                Int
  def indexOfAnyNot(s: CharPredicate):                                Int

  def lastIndexOf(s: CharSequence):                                  Int
  def lastIndexOf(s: CharSequence, fromIndex:  Int):                 Int
  def lastIndexOf(s: CharSequence, startIndex: Int, fromIndex: Int): Int

  def lastIndexOf(c:    Char, startIndex:          Int, fromIndex: Int): Int
  def lastIndexOf(c:    Char, fromIndex:           Int):                 Int
  def lastIndexOf(c:    Char):                                           Int
  def lastIndexOfAny(s: CharPredicate, startIndex: Int, fromIndex: Int): Int
  def lastIndexOfAny(s: CharPredicate, fromIndex:  Int):                 Int
  def lastIndexOfAny(s: CharPredicate):                                  Int

  def lastIndexOfNot(c: Char):                                  Int
  def lastIndexOfNot(c: Char, fromIndex:  Int):                 Int
  def lastIndexOfNot(c: Char, startIndex: Int, fromIndex: Int): Int

  def lastIndexOfAnyNot(s: CharPredicate, startIndex: Int, fromIndex: Int): Int
  def lastIndexOfAnyNot(s: CharPredicate, fromIndex:  Int):                 Int
  def lastIndexOfAnyNot(s: CharPredicate):                                  Int

  // ---- count leading/trailing ----

  def countLeading(chars:    CharPredicate):                                 Int
  def countLeadingNot(chars: CharPredicate):                                 Int
  def countLeading(chars:    CharPredicate, startIndex: Int):                Int
  def countLeadingNot(chars: CharPredicate, startIndex: Int):                Int
  def countLeading(chars:    CharPredicate, startIndex: Int, endIndex: Int): Int
  def countLeadingNot(chars: CharPredicate, startIndex: Int, endIndex: Int): Int

  @deprecated("Use CharPredicate.anyOf(...)", "")
  def countLeading(c: Char): Int = countLeading(CharPredicate.anyOf(c))

  def countTrailing(chars:    CharPredicate):                                 Int
  def countTrailingNot(chars: CharPredicate):                                 Int
  def countTrailing(chars:    CharPredicate, startIndex: Int):                Int
  def countTrailingNot(chars: CharPredicate, startIndex: Int):                Int
  def countTrailing(chars:    CharPredicate, startIndex: Int, endIndex: Int): Int
  def countTrailingNot(chars: CharPredicate, startIndex: Int, endIndex: Int): Int

  def countLeadingSpace():                                  Int
  def countLeadingNotSpace():                               Int
  def countLeadingSpace(startIndex:    Int):                Int
  def countLeadingNotSpace(startIndex: Int):                Int
  def countLeadingSpace(startIndex:    Int, endIndex: Int): Int
  def countLeadingNotSpace(startIndex: Int, endIndex: Int): Int

  def countTrailingSpace():                                   Int
  def countTrailingNotSpace():                                Int
  def countTrailingSpace(fromIndex:     Int):                 Int
  def countTrailingNotSpace(fromIndex:  Int):                 Int
  def countTrailingSpace(startIndex:    Int, fromIndex: Int): Int
  def countTrailingNotSpace(startIndex: Int, fromIndex: Int): Int

  def countLeadingSpaceTab():                                  Int
  def countLeadingNotSpaceTab():                               Int
  def countLeadingSpaceTab(startIndex:    Int):                Int
  def countLeadingNotSpaceTab(startIndex: Int):                Int
  def countLeadingSpaceTab(startIndex:    Int, endIndex: Int): Int
  def countLeadingNotSpaceTab(startIndex: Int, endIndex: Int): Int

  def countTrailingSpaceTab():                                   Int
  def countTrailingNotSpaceTab():                                Int
  def countTrailingSpaceTab(fromIndex:     Int):                 Int
  def countTrailingNotSpaceTab(fromIndex:  Int):                 Int
  def countTrailingSpaceTab(startIndex:    Int, fromIndex: Int): Int
  def countTrailingNotSpaceTab(startIndex: Int, fromIndex: Int): Int

  def countLeadingWhitespace():                                  Int
  def countLeadingNotWhitespace():                               Int
  def countLeadingWhitespace(startIndex:    Int):                Int
  def countLeadingNotWhitespace(startIndex: Int):                Int
  def countLeadingWhitespace(startIndex:    Int, endIndex: Int): Int
  def countLeadingNotWhitespace(startIndex: Int, endIndex: Int): Int

  def countTrailingWhitespace():                                   Int
  def countTrailingNotWhitespace():                                Int
  def countTrailingWhitespace(fromIndex:     Int):                 Int
  def countTrailingNotWhitespace(fromIndex:  Int):                 Int
  def countTrailingWhitespace(startIndex:    Int, fromIndex: Int): Int
  def countTrailingNotWhitespace(startIndex: Int, fromIndex: Int): Int

  @deprecated("Use countLeadingSpaceTab()", "")
  def countLeading(): Int = countLeadingSpaceTab()

  @deprecated("Use countLeadingSpaceTab()", "")
  def countTrailing(): Int = countLeadingSpaceTab()

  def countOfSpaceTab():    Int
  def countOfNotSpaceTab(): Int

  def countOfWhitespace():    Int
  def countOfNotWhitespace(): Int

  @deprecated("Use countOfAny(CharPredicate.anyOf(c))", "")
  def countOf(c: Char): Int = countOfAny(CharPredicate.anyOf(c))

  def countOfAny(chars:    CharPredicate):                                 Int
  def countOfAnyNot(chars: CharPredicate):                                 Int
  def countOfAny(chars:    CharPredicate, startIndex: Int):                Int
  def countOfAnyNot(chars: CharPredicate, startIndex: Int):                Int
  def countOfAny(chars:    CharPredicate, startIndex: Int, endIndex: Int): Int
  def countOfAnyNot(chars: CharPredicate, startIndex: Int, endIndex: Int): Int

  /** Count column of indent given by chars in the set in this sequence, expanding tabs to 4th column
    *
    * @param startColumn
    *   column of where this sequence starts
    * @param chars
    *   whitespace characters
    * @return
    *   column of first non-whitespace as given by chars
    */
  def countLeadingColumns(startColumn: Int, chars: CharPredicate): Int

  // ---- trim range ----

  def trimStartRange(keep: Int, chars: CharPredicate): Range
  def trimEndRange(keep:   Int, chars: CharPredicate): Range
  def trimRange(keep:      Int, chars: CharPredicate): Range

  def trimStartRange(chars: CharPredicate): Range
  def trimEndRange(chars:   CharPredicate): Range
  def trimRange(chars:      CharPredicate): Range

  def trimStartRange(keep: Int): Range
  def trimEndRange(keep:   Int): Range
  def trimRange(keep:      Int): Range

  def trimStartRange(): Range
  def trimEndRange():   Range
  def trimRange():      Range

  // ---- trim ----

  def trimStart(keep: Int, chars: CharPredicate): T
  def trimEnd(keep:   Int, chars: CharPredicate): T
  def trim(keep:      Int, chars: CharPredicate): T

  def trimStart(keep: Int): T
  def trimEnd(keep:   Int): T
  def trim(keep:      Int): T

  def trimStart(chars: CharPredicate): T
  def trimEnd(chars:   CharPredicate): T
  def trim(chars:      CharPredicate): T

  def trimStart(): T
  def trimEnd():   T
  def trim():      T

  // ---- trimmed (returns the trimmed part) ----

  def trimmedStart(keep: Int, chars: CharPredicate): T
  def trimmedEnd(keep:   Int, chars: CharPredicate): T
  def trimmed(keep:      Int, chars: CharPredicate): Pair[T, T]

  def trimmedStart(keep: Int): T
  def trimmedEnd(keep:   Int): T
  def trimmed(keep:      Int): Pair[T, T]

  def trimmedStart(chars: CharPredicate): T
  def trimmedEnd(chars:   CharPredicate): T
  def trimmed(chars:      CharPredicate): Pair[T, T]

  def trimmedStart(): T
  def trimmedEnd():   T
  def trimmed():      Pair[T, T]

  // ---- padding ----

  def padding(length: Int, pad: Char): T
  def padding(length: Int):            T

  def padStart(length: Int, pad: Char): T
  def padEnd(length:   Int, pad: Char): T
  def padStart(length: Int):            T
  def padEnd(length:   Int):            T

  // ---- state checks ----

  def isEmpty():    Boolean
  def isBlank():    Boolean
  def isNotEmpty(): Boolean
  def isNotBlank(): Boolean
  def isNull:       Boolean
  def isNotNull:    Boolean

  // ---- null handling ----

  def ifNull(other:            T):       T
  def ifNullEmptyAfter(other:  T):       T
  def ifNullEmptyBefore(other: T):       T
  def nullIfEmpty():                     T
  def nullIfBlank():                     T
  def nullIf(condition:        Boolean): T

  def nullIf(predicate:    (? >: T, ? >: CharSequence) => Boolean, matches: CharSequence*): T
  def nullIfNot(predicate: (? >: T, ? >: CharSequence) => Boolean, matches: CharSequence*): T

  def nullIf(predicate:    (? >: CharSequence) => Boolean, matches: CharSequence*): T
  def nullIfNot(predicate: (? >: CharSequence) => Boolean, matches: CharSequence*): T

  def nullIf(matches:    CharSequence*): T
  def nullIfNot(matches: CharSequence*): T

  def nullIfStartsWith(matches:    CharSequence*): T
  def nullIfNotStartsWith(matches: CharSequence*): T

  @deprecated("Use nullIfNotStartsWith", "")
  def nullIfStartsWithNot(matches: CharSequence*): T = nullIfNotStartsWith(matches*)

  def nullIfEndsWith(matches:                CharSequence*):                   T
  def nullIfNotEndsWith(matches:             CharSequence*):                   T
  def nullIfStartsWithIgnoreCase(matches:    CharSequence*):                   T
  def nullIfNotStartsWithIgnoreCase(matches: CharSequence*):                   T
  def nullIfEndsWithIgnoreCase(matches:      CharSequence*):                   T
  def nullIfNotEndsWithIgnoreCase(matches:   CharSequence*):                   T
  def nullIfStartsWith(ignoreCase:           Boolean, matches: CharSequence*): T
  def nullIfNotStartsWith(ignoreCase:        Boolean, matches: CharSequence*): T
  def nullIfEndsWith(ignoreCase:             Boolean, matches: CharSequence*): T
  def nullIfNotEndsWith(ignoreCase:          Boolean, matches: CharSequence*): T

  @deprecated("Use nullIfNotEndsWith", "")
  def nullIfEndsWithNot(matches: CharSequence*): T = nullIfNotEndsWith(matches*)

  // ---- EOL helpers ----

  def eolEndLength(): Int

  @deprecated("Use eolEndLength()", "")
  def eolStartLength(): Int = eolEndLength()

  def eolEndLength(eolEnd:     Int): Int
  def eolStartLength(eolStart: Int): Int

  @deprecated("Use eolStartLength(eolStart)", "")
  def eolLength(eolStart: Int): Int = eolStartLength(eolStart)

  def eolEndRange(eolEnd:     Int): Range
  def eolStartRange(eolStart: Int): Range

  def trimEOL():    T
  def trimmedEOL(): T

  // ---- delimited by ----

  def endOfDelimitedBy(s:       CharSequence, index:  Int): Int
  def endOfDelimitedByAny(s:    CharPredicate, index: Int): Int
  def endOfDelimitedByAnyNot(s: CharPredicate, index: Int): Int

  def startOfDelimitedBy(s:       CharSequence, index:  Int): Int
  def startOfDelimitedByAny(s:    CharPredicate, index: Int): Int
  def startOfDelimitedByAnyNot(s: CharPredicate, index: Int): Int

  // ---- line methods ----

  def endOfLine(index:         Int): Int
  def endOfLineAnyEOL(index:   Int): Int
  def startOfLine(index:       Int): Int
  def startOfLineAnyEOL(index: Int): Int

  def lineRangeAt(index:       Int): Range
  def lineRangeAtAnyEOL(index: Int): Range

  def lineAt(index:       Int): T
  def lineAtAnyEOL(index: Int): T

  // ---- blank line trimming ----

  def trimTailBlankLines(): T
  def trimLeadBlankLines(): T

  def leadingBlankLinesRange(eolChars:  CharPredicate, fromIndex:  Int, endIndex:  Int): Range
  def trailingBlankLinesRange(eolChars: CharPredicate, startIndex: Int, fromIndex: Int): Range

  def leadingBlankLinesRange():                                 Range
  def leadingBlankLinesRange(startIndex:  Int):                 Range
  def leadingBlankLinesRange(fromIndex:   Int, endIndex:  Int): Range
  def trailingBlankLinesRange():                                Range
  def trailingBlankLinesRange(fromIndex:  Int):                 Range
  def trailingBlankLinesRange(startIndex: Int, fromIndex: Int): Range

  def blankLinesRemovedRanges():                                                        ju.List[Range]
  def blankLinesRemovedRanges(fromIndex: Int):                                          ju.List[Range]
  def blankLinesRemovedRanges(fromIndex: Int, endIndex:            Int):                ju.List[Range]
  def blankLinesRemovedRanges(eolChars:  CharPredicate, fromIndex: Int, endIndex: Int): ju.List[Range]

  // ---- trim to line ----

  def trimToEndOfLine(eolChars:   CharPredicate, includeEol: Boolean, index: Int): T
  def trimToEndOfLine(includeEol: Boolean, index:            Int):                 T
  def trimToEndOfLine(includeEol: Boolean):                                        T
  def trimToEndOfLine(index:      Int):                                            T
  def trimToEndOfLine():                                                           T

  def trimToStartOfLine(eolChars:   CharPredicate, includeEol: Boolean, index: Int): T
  def trimToStartOfLine(includeEol: Boolean, index:            Int):                 T
  def trimToStartOfLine(includeEol: Boolean):                                        T
  def trimToStartOfLine(index:      Int):                                            T
  def trimToStartOfLine():                                                           T

  // ---- normalize EOL ----

  def normalizeEOL():        String
  def normalizeEndWithEOL(): String

  // ---- comparison helpers ----

  def matches(chars:           CharSequence):                      Boolean
  def matchesIgnoreCase(chars: CharSequence):                      Boolean
  def matches(chars:           CharSequence, ignoreCase: Boolean): Boolean

  def equalsIgnoreCase(other: Any):                      Boolean
  def equals(other:           Any, ignoreCase: Boolean): Boolean

  def matchChars(chars:           CharSequence):                      Boolean
  def matchCharsIgnoreCase(chars: CharSequence):                      Boolean
  def matchChars(chars:           CharSequence, ignoreCase: Boolean): Boolean

  def matchChars(chars:           CharSequence, startIndex: Int, ignoreCase: Boolean): Boolean
  def matchChars(chars:           CharSequence, startIndex: Int):                      Boolean
  def matchCharsIgnoreCase(chars: CharSequence, startIndex: Int):                      Boolean

  def matchedCharCount(chars:           CharSequence, startIndex: Int, endIndex:   Int, fullMatchOnly: Boolean, ignoreCase: Boolean): Int
  def matchedCharCount(chars:           CharSequence, startIndex: Int, endIndex:   Int, ignoreCase:    Boolean):                      Int
  def matchedCharCount(chars:           CharSequence, startIndex: Int, ignoreCase: Boolean):                                          Int
  def matchedCharCount(chars:           CharSequence, startIndex: Int, endIndex:   Int):                                              Int
  def matchedCharCount(chars:           CharSequence, startIndex: Int):                                                               Int
  def matchedCharCountIgnoreCase(chars: CharSequence, startIndex: Int, endIndex:   Int):                                              Int
  def matchedCharCountIgnoreCase(chars: CharSequence, startIndex: Int):                                                               Int

  def matchCharsReversed(chars:           CharSequence, endIndex: Int, ignoreCase: Boolean): Boolean
  def matchCharsReversed(chars:           CharSequence, endIndex: Int):                      Boolean
  def matchCharsReversedIgnoreCase(chars: CharSequence, endIndex: Int):                      Boolean

  def matchedCharCountReversed(chars:           CharSequence, startIndex: Int, fromIndex:  Int, ignoreCase: Boolean): Int
  def matchedCharCountReversed(chars:           CharSequence, startIndex: Int, fromIndex:  Int):                      Int
  def matchedCharCountReversedIgnoreCase(chars: CharSequence, startIndex: Int, fromIndex:  Int):                      Int
  def matchedCharCountReversed(chars:           CharSequence, fromIndex:  Int, ignoreCase: Boolean):                  Int
  def matchedCharCountReversed(chars:           CharSequence, fromIndex:  Int):                                       Int
  def matchedCharCountReversedIgnoreCase(chars: CharSequence, fromIndex:  Int):                                       Int

  // ---- starts/ends with ----

  def endsWith(suffix: CharSequence):  Boolean
  def endsWith(chars:  CharPredicate): Boolean
  def endsWithEOL():                   Boolean
  def endsWithAnyEOL():                Boolean
  def endsWithSpace():                 Boolean
  def endsWithSpaceTab():              Boolean
  def endsWithWhitespace():            Boolean

  def endsWithIgnoreCase(suffix: CharSequence):                      Boolean
  def endsWith(suffix:           CharSequence, ignoreCase: Boolean): Boolean

  def startsWith(prefix: CharSequence):  Boolean
  def startsWith(chars:  CharPredicate): Boolean
  def startsWithEOL():                   Boolean
  def startsWithAnyEOL():                Boolean
  def startsWithSpace():                 Boolean
  def startsWithSpaceTab():              Boolean
  def startsWithWhitespace():            Boolean

  def startsWithIgnoreCase(prefix: CharSequence):                      Boolean
  def startsWith(prefix:           CharSequence, ignoreCase: Boolean): Boolean

  // ---- remove prefix/suffix ----

  def removeSuffix(suffix:           CharSequence):                      T
  def removeSuffixIgnoreCase(suffix: CharSequence):                      T
  def removeSuffix(suffix:           CharSequence, ignoreCase: Boolean): T

  def removePrefix(prefix:           CharSequence):                      T
  def removePrefixIgnoreCase(prefix: CharSequence):                      T
  def removePrefix(prefix:           CharSequence, ignoreCase: Boolean): T

  def removeProperSuffix(suffix:           CharSequence):                      T
  def removeProperSuffixIgnoreCase(suffix: CharSequence):                      T
  def removeProperSuffix(suffix:           CharSequence, ignoreCase: Boolean): T

  def removeProperPrefix(prefix:           CharSequence):                      T
  def removeProperPrefixIgnoreCase(prefix: CharSequence):                      T
  def removeProperPrefix(prefix:           CharSequence, ignoreCase: Boolean): T

  // ---- insert / delete / replace ----

  def insert(index: Int, chars: CharSequence): T

  @deprecated("Use insert(index, chars) instead", "")
  def insert(chars: CharSequence, index: Int): T = insert(index, chars)

  def delete(startIndex:  Int, endIndex:         Int):                            T
  def replace(startIndex: Int, endIndex:         Int, replacement: CharSequence): T
  def replace(find:       CharSequence, replace: CharSequence):                   T

  // ---- case mapping ----

  def toLowerCase():                T
  def toUpperCase():                T
  def toMapped(mapper: CharMapper): T

  def toNbSp(): T
  def toSpc():  T

  def toVisibleWhitespaceString(): String

  // ---- split ----

  def splitList(delimiter: CharSequence, limit: Int, flags: Int, trimChars: Nullable[CharPredicate]): ju.List[T]
  def splitList(delimiter: CharSequence, limit: Int, flags: Int):                                     ju.List[T]
  def splitList(delimiter: CharSequence):                                                             ju.List[T]
  def split(delimiter:     CharSequence, limit: Int, flags: Int, trimChars: Nullable[CharPredicate]): Array[T]
  def split(delimiter:     CharSequence, limit: Int, flags: Int):                                     Array[T]
  def split(delimiter:     CharSequence):                                                             Array[T]

  @deprecated("Use split(delimiter.toString, limit, flags, null)", "")
  def split(delimiter: Char, limit: Int, flags: Int): Array[T] = split(Character.toString(delimiter), limit, flags, Nullable.empty[CharPredicate])

  @deprecated("Use split(delimiter.toString, limit, 0, null)", "")
  def split(delimiter: Char, limit: Int): Array[T] = split(Character.toString(delimiter), limit, 0, Nullable.empty[CharPredicate])

  @deprecated("Use split(delimiter.toString, 0, 0, null)", "")
  def split(delimiter: Char): Array[T] = split(Character.toString(delimiter), 0, 0, Nullable.empty[CharPredicate])

  def splitList(delimiter: CharSequence, limit:         Int, includeDelims: Boolean, trimChars: Nullable[CharPredicate]): ju.List[T]
  def splitList(delimiter: CharSequence, includeDelims: Boolean, trimChars: Nullable[CharPredicate]):                     ju.List[T]
  def split(delimiter:     CharSequence, limit:         Int, includeDelims: Boolean, trimChars: Nullable[CharPredicate]): Array[T]
  def split(delimiter:     CharSequence, includeDelims: Boolean, trimChars: Nullable[CharPredicate]):                     Array[T]

  def splitEOL():                                                               Array[T]
  def splitEOL(includeDelims:     Boolean):                                     Array[T]
  def splitListEOL():                                                           ju.List[T]
  def splitListEOL(includeDelims: Boolean):                                     ju.List[T]
  def splitListEOL(includeDelims: Boolean, trimChars: Nullable[CharPredicate]): ju.List[T]

  // ---- indexOfAll ----

  def indexOfAll(s: CharSequence): Array[Int]

  // ---- prefix/suffix with ----

  def prefixWith(prefix:     Nullable[CharSequence]): T
  def suffixWith(suffix:     Nullable[CharSequence]): T
  def prefixOnceWith(prefix: Nullable[CharSequence]): T
  def suffixOnceWith(suffix: Nullable[CharSequence]): T

  def appendEOL():         T
  def suffixWithEOL():     T
  def prefixWithEOL():     T
  def prefixOnceWithEOL(): T
  def suffixOnceWithEOL(): T

  def appendSpace():                T
  def suffixWithSpace():            T
  def prefixWithSpace():            T
  def appendSpaces(count:     Int): T
  def suffixWithSpaces(count: Int): T
  def prefixWithSpaces(count: Int): T
  def prefixOnceWithSpace():        T
  def suffixOnceWithSpace():        T

  // ---- appendTo ----

  def appendTo(out: StringBuilder, charMapper: Nullable[CharMapper], startIndex: Int, endIndex: Int): T
  def appendTo(out: StringBuilder, charMapper: Nullable[CharMapper]):                                 T
  def appendTo(out: StringBuilder, charMapper: Nullable[CharMapper], startIndex: Int):                T
  def appendTo(out: StringBuilder, startIndex: Int, endIndex:                    Int):                T
  def appendTo(out: StringBuilder):                                                                   T
  def appendTo(out: StringBuilder, startIndex: Int):                                                  T

  // ---- appendRangesTo ----

  def appendRangesTo(out: StringBuilder, charMapper: Nullable[CharMapper], ranges: Range*):               T
  def appendRangesTo(out: StringBuilder, ranges:     Range*):                                             T
  def appendRangesTo(out: StringBuilder, charMapper: Nullable[CharMapper], ranges: Iterable[? <: Range]): T
  def appendRangesTo(out: StringBuilder, ranges:     Iterable[? <: Range]):                               T

  // ---- extractRanges ----

  def extractRanges(ranges: Range*):          T
  def extractRanges(ranges: Iterable[Range]): T

  // ---- append ----

  def append(sequences: CharSequence*):               T
  def append(sequences: Iterable[? <: CharSequence]): T

  // ---- line/column info ----

  def lineColumnAtIndex(index: Int): Pair[Integer, Integer]

  @deprecated("Use lineColumnAtIndex(index)", "")
  def getLineColumnAtIndex(index: Int): Pair[Integer, Integer] = lineColumnAtIndex(index)

  def columnAtIndex(index: Int): Int

  @deprecated("Use columnAtIndex(index)", "")
  def getColumnAtIndex(index: Int): Int = columnAtIndex(index)

  /** Safe, if index out of range returns '\0'
    *
    * @param index
    *   index in string
    * @param predicate
    *   character set predicate
    * @return
    *   true if character at index tests true
    */
  def isCharAt(index: Int, predicate: CharPredicate): Boolean

  /** Return string or null if BaseSequence.NULL
    *
    * @return
    *   string or null if BaseSequence.NULL
    */
  def toStringOrNull(): Nullable[String]

  def isIn(texts: Array[String]):                    Boolean
  def isIn(texts: ju.Collection[? <: CharSequence]): Boolean
}
