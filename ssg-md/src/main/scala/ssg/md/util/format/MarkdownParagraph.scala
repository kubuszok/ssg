/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/MarkdownParagraph.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package format

import ssg.md.Nullable
import ssg.md.util.data.{ DataHolder, SharedDataKeys }
import ssg.md.util.misc.CharPredicate
import ssg.md.util.misc.CharPredicate.*
import ssg.md.util.sequence.{ BasedSequence, Range, RepeatedSequence, SequenceUtils }
import ssg.md.util.sequence.builder.SequenceBuilder
import ssg.md.util.sequence.builder.tree.BasedOffsetTracker
import ssg.md.util.sequence.mappers.{ SpaceMapper, SpecialLeadInHandler }

import java.util.{ ArrayList, Collections, Comparator, List as JList }
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

class MarkdownParagraph(
  val baseSeq:           BasedSequence,
  val altSeq:            BasedSequence,
  val charWidthProvider: CharWidthProvider
) {

  private var _firstIndent:       BasedSequence        = BasedSequence.NULL
  private var _indent:            BasedSequence        = BasedSequence.NULL
  private var _firstWidthOffset:  Int                  = 0
  var width:                      Int                  = 0
  var keepHardLineBreaks:         Boolean              = true
  var keepSoftLineBreaks:         Boolean              = false
  var unEscapeSpecialLeadInChars: Boolean              = true
  var escapeSpecialLeadInChars:   Boolean              = true
  var restoreTrackedSpaces:       Boolean              = false
  var options:                    Nullable[DataHolder] = Nullable(null)

  var leadInHandlers:                JList[? <: SpecialLeadInHandler] = MarkdownParagraph.EMPTY_LEAD_IN_HANDLERS
  private var _trackedOffsets:       JList[TrackedOffset]             = MarkdownParagraph.EMPTY_OFFSET_LIST
  private var _trackedOffsetsSorted: Boolean                          = true

  def this(chars: CharSequence) =
    this(BasedSequence.of(chars), BasedSequence.of(chars), CharWidthProvider.NULL)

  def this(chars: BasedSequence) =
    this(chars, chars, CharWidthProvider.NULL)

  def this(chars: BasedSequence, charWidthProvider: CharWidthProvider) =
    this(chars, chars, charWidthProvider)

  def wrapTextNotTracked(): BasedSequence =
    if (getFirstWidth <= 0) {
      baseSeq
    } else {
      val wrapping = new LeftAlignedWrapping(baseSeq)
      wrapping.wrapText()
    }

  def getContinuationStartSplice(offset: Int, afterSpace: Boolean, afterDelete: Boolean): Range = boundary {
    val baseSequence = altSeq.getBaseSequence
    assert(offset >= 0 && offset <= baseSequence.length())
    if (afterSpace && afterDelete) {
      val preFormatTracker = BasedOffsetTracker.create(altSeq)
      val startOfLine      = baseSequence.startOfLine(offset)
      if (startOfLine > altSeq.startOffset && !baseSequence.isCharAt(offset, SPACE_TAB_NBSP_LINE_SEP)) {
        val previousNonBlank = baseSequence.lastIndexOfAnyNot(SPACE_TAB_NBSP_EOL, offset - 1)
        if (previousNonBlank < startOfLine) {
          // delete range between last non-blank and offset index
          val offsetInfo            = preFormatTracker.getOffsetInfo(offset, true)
          val offsetIndex           = offsetInfo.endIndex
          val previousNonBlankIndex = altSeq.lastIndexOfAnyNot(SPACE_TAB_NBSP_EOL, offsetIndex - 1)
          break(Range.of(previousNonBlankIndex + 1, offsetIndex)) // NOTE: early exit with result
        }
      }
    }
    Range.NULL
  }

  private[format] def resolveTrackedOffsets(unwrapped: BasedSequence, wrapped: BasedSequence): BasedSequence = {
    // Now we map the tracked offsets to indexes in the resulting text
    val tracker = BasedOffsetTracker.create(wrapped)
    val iMax    = _trackedOffsets.size()
    var i       = iMax
    while (i > 0) {
      i -= 1
      val trackedOffset            = _trackedOffsets.get(i)
      val offset                   = trackedOffset.offset
      val baseIsWhiteSpaceAtOffset = unwrapped.isBaseCharAt(offset, WHITESPACE_NBSP)

      if (baseIsWhiteSpaceAtOffset && !unwrapped.isBaseCharAt(offset - 1, WHITESPACE_NBSP)) {
        // we need to use previous non-blank and use that offset
        val info = tracker.getOffsetInfo(offset - 1, false)
        trackedOffset.setIndex(info.endIndex)
      } else if (!baseIsWhiteSpaceAtOffset && unwrapped.isBaseCharAt(offset + 1, WHITESPACE_NBSP)) {
        // we need to use this non-blank and use that offset
        val info = tracker.getOffsetInfo(offset, false)
        trackedOffset.setIndex(info.startIndex)
      } else {
        val info = tracker.getOffsetInfo(offset, true)
        trackedOffset.setIndex(info.endIndex)
      }
    }
    wrapped
  }

  def wrapText(): BasedSequence = boundary {
    if (getFirstWidth <= 0) {
      break(baseSeq)
    }
    if (_trackedOffsets.isEmpty) {
      break(wrapTextNotTracked())
    }

    // Adjust input text for wrapping by removing any continuation splice regions
    sortedTrackedOffsets()

    // delete any space ranges that need to be spliced
    var baseSpliced = baseSeq
    var altSpliced  = altSeq
    var lastRange   = Range.NULL

    {
      val iMax = _trackedOffsets.size()
      var i    = iMax
      while (i > 0) {
        i -= 1
        val trackedOffset = _trackedOffsets.get(i)
        if (lastRange.isEmpty || !lastRange.contains(trackedOffset.offset)) {
          lastRange = getContinuationStartSplice(trackedOffset.offset, trackedOffset.isAfterSpaceEdit, trackedOffset.isAfterDelete)
          if (lastRange.isNotEmpty) {
            trackedOffset.isSpliced = true
            baseSpliced = baseSpliced.delete(lastRange.start, lastRange.end)
            altSpliced = altSpliced.delete(lastRange.start, lastRange.end)
          }
        }
      }
    }

    assert(baseSpliced.equals(altSpliced))

    val textWrapper = new LeftAlignedWrapping(baseSpliced)
    var wrapped     = textWrapper.wrapText()

    if (restoreTrackedSpaces) {
      if (_indent.isNotEmpty() || _firstIndent.isNotEmpty()) throw new IllegalStateException("restoreTrackedSpaces is not supported with indentation applied by MarkdownParagraph")

      wrapped = resolveTrackedOffsetsEdit(baseSpliced, altSpliced, wrapped)
    } else {
      wrapped = resolveTrackedOffsets(baseSeq, wrapped)
    }

    wrapped
  }

  private[format] def resolveTrackedOffsetsEdit(baseSpliced: BasedSequence, altSpliced: BasedSequence, wrappedIn: BasedSequence): BasedSequence = {
    val inTest: Boolean = SharedDataKeys.RUNNING_TESTS.get(options.getOrElse(null))
    val spliced        = BasedSequence.of(baseSpliced.toString)
    val altTextWrapper = new LeftAlignedWrapping(spliced)
    val altWrapped     = {
      val builder = SequenceBuilder.emptyBuilder(spliced)
      builder.append(Nullable(altTextWrapper.wrapText().asInstanceOf[CharSequence]))
      builder.toSequence(altSpliced, Nullable(CharPredicate.LINE_SEP), Nullable(CharPredicate.SPACE_TAB_EOL))
    }

    val tracker    = BasedOffsetTracker.create(altSeq)
    val altTracker = BasedOffsetTracker.create(altWrapped)

    val iMax         = _trackedOffsets.size()
    val baseSequence = altSeq.getBaseSequence
    val altUnwrapped = altSeq
    var wrapped      = wrappedIn

    // NOTE: Restore trailing spaces at end of line if it has tracked offset on it
    var restoredAppendSpaces = 0

    // determine in reverse offset order
    var i = iMax
    while (i > 0) {
      i -= 1
      val trackedOffset = _trackedOffsets.get(i)

      val offset                  = trackedOffset.offset
      val countedSpacesBefore     = baseSequence.countTrailing(SPACE_TAB_NBSP, offset)
      val countedSpacesAfter      = baseSequence.countLeading(SPACE_TAB_NBSP, offset)
      val countedWhitespaceBefore = baseSequence.countTrailing(SPACE_TAB_NBSP_EOL, offset)
      val countedWhiteSpaceAfter  = baseSequence.countLeading(SPACE_TAB_NBSP_EOL, offset)

      if (inTest) {
        assert(trackedOffset.spacesBefore == countedSpacesBefore)
        assert(trackedOffset.spacesAfter == countedSpacesAfter)
      }

      val baseCharAt     = baseSequence.safeCharAt(offset)
      val prevBaseCharAt = baseSequence.safeCharAt(offset - countedSpacesBefore - 1)
      val nextBaseCharAt = baseSequence.safeCharAt(offset + countedSpacesAfter)

      var anchorOffset = 0
      var anchorDelta  = 0
      var anchorIndex  = 0
      var isLineSep    = false

      if (inTest) {
        System.out.println(trackedOffset)
      }

      if (!SPACE_TAB_NBSP.test(baseCharAt)) {
        anchorOffset = offset
        anchorIndex = tracker.getOffsetInfo(anchorOffset, false).startIndex

        if (altUnwrapped.safeCharAt(anchorIndex - 1) == SequenceUtils.LS) {
          // have line sep at anchor
          isLineSep = true
        }
      } else {
        if (!SPACE_TAB_NBSP_EOL.test(prevBaseCharAt)) {
          anchorOffset = offset - countedWhitespaceBefore
          anchorIndex = tracker.getOffsetInfo(anchorOffset - 1, false).endIndex
        } else if (!SPACE_TAB_NBSP_EOL.test(nextBaseCharAt)) {
          anchorOffset = offset + countedWhiteSpaceAfter
          anchorIndex = tracker.getOffsetInfo(anchorOffset, false).startIndex
        } else {
          throw new IllegalStateException(String.format("Should not be here. altSeq: '%s'", altUnwrapped))
        }
      }

      // now see where it is in the wrapped sequence
      var wrappedIndex = altTracker.getOffsetInfo(anchorOffset, false).startIndex

      // NOTE: at this point space == &nbsp; since altUnwrapped can have &nbsp; instead of spaces
      assert(
        SpaceMapper.areEquivalent(baseSequence.safeCharAt(anchorOffset), altUnwrapped.safeCharAt(anchorIndex + anchorDelta))
          // NOTE: alt sequence could have spaces removed which would result in space in base sequence and EOL in alt sequence, also unwrapped can have spaces replaced with NBSP
          || baseSequence.isCharAt(anchorOffset, WHITESPACE_NBSP_OR_NUL) && altUnwrapped.isCharAt(anchorIndex + anchorDelta, WHITESPACE_NBSP_OR_NUL)
      )

      var wrappedAdjusted    = 0
      var unwrappedAdjusted  = 0
      var addSpacesBeforeEol = 0
      // take char start but if at whitespace, use previous for validation
      if (WHITESPACE_NBSP.test(altUnwrapped.safeCharAt(anchorIndex + anchorDelta))) {
        // NOTE: if after wrapping caret is still on whitespace or end of string, then we adjust unwrapped and wrapped index backwards
        if (WHITESPACE_NBSP_OR_NUL.test(altWrapped.safeCharAt(wrappedIndex))) {
          unwrappedAdjusted = -1
          wrappedAdjusted = -1
        } else {
          addSpacesBeforeEol = 1
          unwrappedAdjusted = altUnwrapped.countLeading(WHITESPACE_NBSP, anchorIndex + anchorDelta)
        }
      } else if (altUnwrapped.safeCharAt(anchorIndex + anchorDelta) == SequenceUtils.LS) {
        // have line sep at anchor, if prev is not whitespace, use it for validation
        if (!WHITESPACE_NBSP.test(altUnwrapped.safeCharAt(anchorIndex + anchorDelta - 1))) {
          wrappedAdjusted -= 1
          unwrappedAdjusted -= 1
        } else {
          // use next char, it should not be whitespace
          anchorDelta += 1
          assert(!WHITESPACE_NBSP.test(altUnwrapped.safeCharAt(anchorIndex + anchorDelta)))
          isLineSep = true
        }
      }

      // NOTE: at this point space == &nbsp; since altUnwrapped can have &nbsp; instead of spaces
      val altUnwrappedCharAt = altUnwrapped.safeCharAt(anchorIndex + anchorDelta + unwrappedAdjusted)
      val wrappedCharAt      = wrapped.safeCharAt(wrappedIndex + wrappedAdjusted)

      assert(
        SpaceMapper.areEquivalent(altUnwrappedCharAt, wrappedCharAt)
          || WHITESPACE_NBSP.test(altUnwrappedCharAt) && WHITESPACE_NBSP.test(wrappedCharAt)
      )

      // Adjust index position and restore spaces if needed
      if (isLineSep) {
        wrappedIndex = Math.max(0, wrappedIndex - 1)
      }

      if (wrapped.isCharAt(wrappedIndex - 1, CharPredicate.ANY_EOL) && countedSpacesAfter > 0) {
        // at start of line with spaces to be inserted after, move to before prev EOL
        wrappedIndex -= wrapped.eolEndLength(wrappedIndex)
      }

      // treat NBSP as spaces
      val wrappedSpacesBefore = wrapped.countTrailing(SPACE_TAB_NBSP, wrappedIndex)
      val wrappedSpacesAfter  = wrapped.countLeading(SPACE_TAB_NBSP, wrappedIndex)

      var adjCountedSpacesBefore = countedSpacesBefore
      if (trackedOffset.isAfterSpaceEdit) {
        if (trackedOffset.isAfterInsert) {
          // need at least one space before
          adjCountedSpacesBefore = Math.max(1, adjCountedSpacesBefore)
        } else if (trackedOffset.isAfterDelete) {
          adjCountedSpacesBefore = 0
        }
      }

      var addSpacesBefore = if (trackedOffset.isSpliced) 0 else Math.max(0, adjCountedSpacesBefore - wrappedSpacesBefore)
      // add an implicit space if was followed by EOL
      var addSpacesAfter = Math.max(addSpacesBeforeEol, countedSpacesAfter - wrappedSpacesAfter)

      if (wrapped.isCharAt(wrappedIndex, CharPredicate.ANY_EOL_NUL)) {
        // at end of line add only before, nothing after
        addSpacesAfter = 0
        if (trackedOffset.isAfterDelete) addSpacesBefore = Math.min(1, addSpacesBefore)
      } else if (!wrapped.isCharAt(wrappedIndex - 1, CharPredicate.ANY_EOL_NUL)) {
        // not at start of line
        // spaces before caret, see if need to add max 1, and all spaces after
        addSpacesBefore = Math.min(1, addSpacesBefore)
      } else if (trackedOffset.isAfterDelete && !trackedOffset.isAfterSpaceEdit) {
        // at start of line, add max 1 space after
        // spaces before caret, see if need to add max 1
        addSpacesBefore = 0
        addSpacesAfter = Math.min(1, addSpacesAfter)
      } else {
        // at start of line, not after delete or after space edit
        if (!trackedOffset.isAfterInsert && !trackedOffset.isAfterDelete) addSpacesAfter = 0
        addSpacesBefore = 0
      }

      if (addSpacesBefore + addSpacesAfter > 0) {
        val lastNonBlank = wrapped.lastIndexOfAnyNot(WHITESPACE_NBSP)
        if (wrappedIndex <= lastNonBlank) {
          // insert in middle
          wrapped = wrapped.insert(wrappedIndex, RepeatedSequence.ofSpaces(addSpacesBefore + addSpacesAfter))

          // need to adjust all following indices by the amount inserted
          var j = i + 1
          while (j < iMax) {
            val trackedOffset1 = _trackedOffsets.get(j)
            val indexJ         = trackedOffset1.getIndex
            trackedOffset1.setIndex(indexJ + addSpacesBefore + addSpacesAfter)
            j += 1
          }
        } else {
          restoredAppendSpaces = Math.max(restoredAppendSpaces, addSpacesBefore)
        }

        wrappedIndex += addSpacesBefore
      }

      trackedOffset.setIndex(wrappedIndex)
    }

    // append any trailing spaces
    if (restoredAppendSpaces > 0) {
      wrapped = wrapped.appendSpaces(restoredAppendSpaces)
    }

    wrapped
  }

  def addTrackedOffset(trackedOffset: TrackedOffset): Unit = {
    if (_trackedOffsets eq MarkdownParagraph.EMPTY_OFFSET_LIST) _trackedOffsets = new ArrayList[TrackedOffset]()
    assert(trackedOffset.offset >= 0 && trackedOffset.offset <= altSeq.getBaseSequence.length())
    _trackedOffsets.removeIf(it => it.offset == trackedOffset.offset)
    _trackedOffsets.add(trackedOffset)
    _trackedOffsetsSorted = false
  }

  def getTrackedOffsets: JList[TrackedOffset] =
    sortedTrackedOffsets()

  private def sortedTrackedOffsets(): JList[TrackedOffset] = {
    if (!_trackedOffsetsSorted) {
      _trackedOffsets.sort(Comparator.comparing[TrackedOffset, Integer]((t: TrackedOffset) => t.offset))
      _trackedOffsetsSorted = true
    }
    _trackedOffsets
  }

  def getTrackedOffset(offset: Int): Nullable[TrackedOffset] = {
    sortedTrackedOffsets()

    var result: Nullable[TrackedOffset] = Nullable(null)
    var done = false
    val iter = _trackedOffsets.iterator()
    while (iter.hasNext && !done) {
      val trackedOffset = iter.next()
      if (trackedOffset.offset == offset) {
        result = Nullable(trackedOffset)
        done = true
      } else if (trackedOffset.offset > offset) {
        done = true
      }
    }
    result
  }

  def getChars: BasedSequence = baseSeq

  def getFirstIndent: CharSequence = _firstIndent

  def setFirstIndent(firstIndent: CharSequence): Unit =
    this._firstIndent = BasedSequence.of(firstIndent)

  def getIndent: CharSequence = _indent

  def setIndent(indent: CharSequence): Unit = {
    this._indent = BasedSequence.of(indent)
    if (this._firstIndent.isNull) this._firstIndent = this._indent
  }

  def getFirstWidth: Int =
    if (width == 0) 0 else Math.max(0, width + _firstWidthOffset)

  def firstWidthOffset: Int = _firstWidthOffset

  def firstWidthOffset_=(firstWidthOffset: Int): Unit =
    _firstWidthOffset = firstWidthOffset

  // Inner class: LeftAlignedWrapping
  private class LeftAlignedWrapping(val wrappingBaseSeq: BasedSequence) {
    val result:                             SequenceBuilder                  = SequenceBuilder.emptyBuilder(wrappingBaseSeq)
    val tokenizer:                          MarkdownParagraph.TextTokenizer  = new MarkdownParagraph.TextTokenizer(wrappingBaseSeq)
    var col:                                Int                              = 0
    var lineCount:                          Int                              = 0
    val spaceWidthVal:                      Int                              = charWidthProvider.spaceWidth
    var lineIndent:                         CharSequence                     = getFirstIndent
    val nextIndent:                         CharSequence                     = getIndent
    var lineWidth:                          Int                              = spaceWidthVal * getFirstWidth
    val nextWidth:                          Int                              = if (width <= 0) Integer.MAX_VALUE else spaceWidthVal * width
    var wordsOnLine:                        Int                              = 0
    var lastSpace:                          Nullable[BasedSequence]          = Nullable(null)
    val wrappingLeadInHandlers:             JList[? <: SpecialLeadInHandler] = MarkdownParagraph.this.leadInHandlers
    val wrappingUnEscapeSpecialLeadInChars: Boolean                          = MarkdownParagraph.this.unEscapeSpecialLeadInChars
    val wrappingEscapeSpecialLeadInChars:   Boolean                          = MarkdownParagraph.this.escapeSpecialLeadInChars

    private def advance(): Unit =
      tokenizer.next()

    private def addToken(token: MarkdownParagraph.Token): Unit =
      addChars(wrappingBaseSeq.subSequence(token.range.start, token.range.end))

    private def addChars(charSequence: CharSequence): Unit = {
      result.append(charSequence)
      col += charWidthProvider.getStringWidth(charSequence)
    }

    private def addSpaces(count: Int): Unit = {
      result.append(' ', count)
      col += charWidthProvider.spaceWidth * count
    }

    private def addSpaces(sequence: Nullable[BasedSequence], count: Int): Nullable[BasedSequence] = boundary {
      if (count <= 0) {
        break(sequence)
      }

      var remainder: Nullable[BasedSequence] = Nullable(null)
      var remaining = count

      // NOTE: can do splitting add from sequence before and after padding spaces to have start/end range if needed
      if (sequence.isDefined) {
        addChars(sequence.get.subSequence(0, Math.min(sequence.get.length(), remaining)))

        if (sequence.get.length() > remaining) {
          remainder = Nullable(sequence.get.subSequence(remaining))
        }

        remaining = Math.max(0, remaining - sequence.get.length())
      }

      // add more spaces if needed
      if (remaining > 0) {
        addSpaces(remaining)
      }

      remainder
    }

    private def afterLineBreak(): Unit = {
      col = 0
      wordsOnLine = 0
      lineCount += 1
      lineIndent = nextIndent
      lineWidth = nextWidth
      lastSpace = Nullable(null)
    }

    private def processLeadInEscape(handlers: JList[? <: SpecialLeadInHandler], sequence: BasedSequence): Unit = boundary {
      if (sequence.isNotEmpty() && wrappingEscapeSpecialLeadInChars) {
        val iter = handlers.iterator()
        while (iter.hasNext)
          if (iter.next().escape(sequence, options, addChars)) {
            break(())
          }
      }
      addChars(sequence)
    }

    private def processLeadInUnEscape(handlers: JList[? <: SpecialLeadInHandler], sequence: BasedSequence): Unit = boundary {
      if (sequence.isNotEmpty() && wrappingUnEscapeSpecialLeadInChars) {
        val iter = handlers.iterator()
        while (iter.hasNext)
          if (iter.next().unEscape(sequence, options, addChars)) {
            break(())
          }
      }
      addChars(sequence)
    }

    def wrapText(): BasedSequence =
      boundary {
        while (true) {
          val token = tokenizer.getToken
          if (token.isEmpty) break(result.toSequence)

          val tok = token.get
          tok.`type` match {
            case MarkdownParagraph.TextType.SPACE =>
              if (col != 0) lastSpace = Nullable(wrappingBaseSeq.subSequence(tok.range.start, tok.range.end))
              advance()

            case MarkdownParagraph.TextType.WORD =>
              if (col == 0 || col + charWidthProvider.getStringWidth(tok.subSequence(wrappingBaseSeq)) + spaceWidthVal <= lineWidth) {
                // fits, add it
                val firstNonBlank = col == 0

                if (col > 0) {
                  lastSpace = addSpaces(lastSpace, 1)
                } else {
                  if (!SequenceUtils.isEmpty(lineIndent)) {
                    addChars(lineIndent)
                  }
                }

                if (firstNonBlank && !tok.isFirstWord) {
                  processLeadInEscape(wrappingLeadInHandlers, wrappingBaseSeq.subSequence(tok.range.start, tok.range.end))
                } else if (!firstNonBlank && tok.isFirstWord) {
                  processLeadInUnEscape(wrappingLeadInHandlers, wrappingBaseSeq.subSequence(tok.range.start, tok.range.end))
                } else {
                  addToken(tok)
                }

                advance()
                wordsOnLine += 1
              } else {
                // need to insert a line break and repeat
                addChars(SequenceUtils.EOL)
                afterLineBreak()
              }

            case MarkdownParagraph.TextType.MARKDOWN_START_LINE =>
              // start a new line if not already new
              if (col > 0) {
                addChars(SequenceUtils.EOL)
                afterLineBreak()
              }
              advance()

            case MarkdownParagraph.TextType.MARKDOWN_BREAK =>
              // start a new line if not already new
              if (keepHardLineBreaks) {
                if (col > 0) {
                  addToken(tok)
                  afterLineBreak()
                }
              } else {
                // treat as a space
                lastSpace = Nullable(wrappingBaseSeq.subSequence(tok.range.start, tok.range.end))
              }
              advance()

            case MarkdownParagraph.TextType.BREAK =>
              if (col > 0 && keepSoftLineBreaks) {
                // only use the EOL
                addToken(tok)
                afterLineBreak()
              }
              advance()
          }
        }
        result.toSequence // unreachable but needed for type
      }
  }
}

object MarkdownParagraph {
  private val MARKDOWN_START_LINE_CHAR: Char =
    SequenceUtils.LS // https://www.fileformat.info/info/unicode/char/2028/index.htm LINE_SEPARATOR this one is not preserved but will cause a line break if not already at beginning of line
  val EMPTY_LEAD_IN_HANDLERS: JList[SpecialLeadInHandler] = Collections.emptyList()
  val EMPTY_OFFSET_LIST:      JList[TrackedOffset]        = Collections.emptyList()

  enum TextType extends java.lang.Enum[TextType] {
    case WORD
    case SPACE
    case BREAK
    case MARKDOWN_BREAK
    case MARKDOWN_START_LINE
  }

  final class Token private (
    val `type`:      TextType,
    val range:       Range,
    val isFirstWord: Boolean
  ) {

    override def toString: String =
      "token: " + `type` + " " + range + (if (isFirstWord) " isFirst" else "")

    def subSequence(charSequence: BasedSequence): BasedSequence =
      charSequence.subSequence(range.start, range.end)

    def subSequence(charSequence: CharSequence): CharSequence =
      range.charSubSequence(charSequence)
  }

  object Token {

    def of(`type`: TextType, range: Range): Token =
      new Token(`type`, range, false)

    def of(`type`: TextType, start: Int, end: Int): Token =
      new Token(`type`, Range.of(start, end), false)

    def of(`type`: TextType, range: Range, isFirstWord: Boolean): Token =
      new Token(`type`, range, isFirstWord)

    def of(`type`: TextType, start: Int, end: Int, isFirstWord: Boolean): Token =
      new Token(`type`, Range.of(start, end), isFirstWord)
  }

  class TextTokenizer(val chars: CharSequence) {

    private val maxIndex:              Int             = chars.length()
    private var index:                 Int             = 0
    private var lastPos:               Int             = 0
    private var isInWord:              Boolean         = false
    private var isFirstNonBlank:       Boolean         = true
    private var lastConsecutiveSpaces: Int             = 0
    private var _token:                Nullable[Token] = Nullable(null)

    reset()

    def reset(): Unit = {
      index = 0
      lastPos = 0
      isInWord = false
      _token = Nullable(null)
      lastConsecutiveSpaces = 0
      isFirstNonBlank = true
      next()
    }

    def getToken: Nullable[Token] = _token

    def asList(): JList[Token] = {
      val tokens = new ArrayList[Token]()
      reset()

      while (_token.isDefined) {
        tokens.add(_token.get)
        next()
      }

      tokens
    }

    def next(): Unit = {
      _token = Nullable(null)

      boundary {
        while (index < maxIndex) {
          val c = chars.charAt(index)
          if (isInWord) {
            if (c == ' ' || c == '\t' || c == '\n' || c == MARKDOWN_START_LINE_CHAR) {
              isInWord = false
              val isFirstWordFlag = isFirstNonBlank
              isFirstNonBlank = false

              if (lastPos < index) {
                // have a word
                _token = Nullable(Token.of(TextType.WORD, lastPos, index, isFirstWordFlag))
                lastPos = index
                break()
              }
            } else {
              index += 1
            }
          } else {
            // in white space
            if (c != ' ' && c != '\t' && c != '\n' && c != MARKDOWN_START_LINE_CHAR) {
              if (lastPos < index) {
                _token = Nullable(Token.of(TextType.SPACE, lastPos, index))
                lastPos = index
                isInWord = true
                lastConsecutiveSpaces = 0
                break()
              } else {
                isInWord = true
                lastConsecutiveSpaces = 0
              }
            } else {
              if (c == '\n') {
                if (lastConsecutiveSpaces >= 2) {
                  _token = Nullable(Token.of(TextType.MARKDOWN_BREAK, index - lastConsecutiveSpaces, index + 1))
                } else {
                  _token = Nullable(Token.of(TextType.BREAK, index, index + 1))
                }

                lastPos = index + 1
                lastConsecutiveSpaces = 0
                isFirstNonBlank = true
                index += 1
                break()
              } else if (c == MARKDOWN_START_LINE_CHAR) {
                _token = Nullable(Token.of(TextType.MARKDOWN_START_LINE, index, index + 1))
                lastPos = index + 1
                lastConsecutiveSpaces = 0
                index += 1
                break()
              } else {
                if (c == ' ') lastConsecutiveSpaces += 1
                else lastConsecutiveSpaces = 0
                index += 1
              }
            }
          }
        }
      }

      if (lastPos < index) {
        if (isInWord) {
          _token = Nullable(Token.of(TextType.WORD, lastPos, index, isFirstNonBlank))
          isFirstNonBlank = false
        } else {
          _token = Nullable(Token.of(TextType.SPACE, lastPos, index))
        }
        lastPos = index
      }
    }
  }
}
