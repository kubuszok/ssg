/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-typographic/src/main/java/com/vladsch/flexmark/ext/typographic/internal/QuoteDelimiterProcessorBase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package typographic
package internal

import ssg.md.Nullable
import ssg.md.parser.InlineParser
import ssg.md.parser.core.delimiter.Delimiter
import ssg.md.parser.delimiter.{ DelimiterProcessor, DelimiterRun }
import ssg.md.util.ast.Node
import ssg.md.util.sequence.BasedSequence
import scala.language.implicitConversions

class QuoteDelimiterProcessorBase(
  protected val myOptions:        TypographicOptions,
  protected val myOpenDelimiter:  Char,
  protected val myCloseDelimiter: Char,
  protected val myOpener:         String,
  protected val myCloser:         String,
  protected val myUnmatched:      Nullable[String]
) extends DelimiterProcessor {

  final override def openingCharacter: Char = myOpenDelimiter

  final override def closingCharacter: Char = myCloseDelimiter

  override def minLength: Int = 1

  override def canBeOpener(
    before:              String,
    after:               String,
    leftFlanking:        Boolean,
    rightFlanking:       Boolean,
    beforeIsPunctuation: Boolean,
    afterIsPunctuation:  Boolean,
    beforeIsWhitespace:  Boolean,
    afterIsWhiteSpace:   Boolean
  ): Boolean = leftFlanking

  override def canBeCloser(
    before:              String,
    after:               String,
    leftFlanking:        Boolean,
    rightFlanking:       Boolean,
    beforeIsPunctuation: Boolean,
    afterIsPunctuation:  Boolean,
    beforeIsWhitespace:  Boolean,
    afterIsWhiteSpace:   Boolean
  ): Boolean = rightFlanking

  override def skipNonOpenerCloser: Boolean = false

  protected def havePreviousOpener(opener: DelimiterRun): Boolean = {
    var previous = opener.previous
    val minLen   = this.minLength
    while (previous != null)
      if (previous.delimiterChar == myOpenDelimiter) {
        if (canOpen(previous, minLen)) return true // scalastyle:ignore
        else { previous = previous.previous; /* continue */ }
      } else {
        previous = previous.previous
      }
    false
  }

  protected def haveNextCloser(closer: DelimiterRun): Boolean = {
    var next   = closer.next
    val minLen = this.minLength
    while (next != null)
      if (next.delimiterChar == myCloseDelimiter) {
        if (canClose(next, minLen)) return true // scalastyle:ignore
        else { next = next.next; /* continue */ }
      } else {
        next = next.next
      }
    false
  }

  protected def canClose(closer: DelimiterRun, minLength: Int): Boolean =
    if (closer.canClose) {
      val closerChars = closer.node.chars
      if (
        closer.next != null && closerChars
          .isContinuationOf(closer.next.node.chars) || closerChars.endOffset >= closerChars.getBaseSequence.length() || isAllowed(closerChars.getBaseSequence, closerChars.endOffset + minLength - 1)
      ) {
        true
      } else {
        false
      }
    } else {
      false
    }

  protected def canOpen(opener: DelimiterRun, minLength: Int): Boolean =
    if (opener.canOpen) {
      val openerChars = opener.node.chars
      if (
        opener.previous != null && opener.previous.node.chars.isContinuationOf(openerChars) || openerChars.startOffset == 0 || isAllowed(openerChars.getBaseSequence,
                                                                                                                                         openerChars.startOffset - minLength
        )
      ) {
        true
      } else {
        false
      }
    } else {
      false
    }

  protected def isAllowed(c: Char): Boolean = !Character.isLetterOrDigit(c)

  protected def isAllowed(seq: CharSequence, index: Int): Boolean =
    index < 0 || index >= seq.length() || !Character.isLetterOrDigit(seq.charAt(index))

  override def getDelimiterUse(opener: DelimiterRun, closer: DelimiterRun): Int = {
    val minLen = this.minLength
    if (opener.length >= minLen && closer.length >= minLen) {
      if (canOpen(opener, minLen) && canClose(closer, minLen)) minLen else 0
    } else {
      0
    }
  }

  override def unmatchedDelimiterNode(inlineParser: InlineParser, delimiter: DelimiterRun): Nullable[Node] =
    if (myUnmatched.isDefined && myOptions.typographicSmarts) {
      val chars = delimiter.node.chars
      if (chars.length() == 1) {
        Nullable(new TypographicSmarts(chars, myUnmatched.get))
      } else {
        Nullable.empty
      }
    } else {
      Nullable.empty
    }

  override def process(opener: Delimiter, closer: Delimiter, delimitersUsed: Int): Unit = {
    // Normal case, wrap nodes between delimiters in typographic quotes.
    val node = new TypographicQuotes(opener.tailChars(delimitersUsed), BasedSequence.NULL, closer.leadChars(delimitersUsed))
    node.typographicOpening = Nullable(myOpener)
    node.typographicClosing = Nullable(myCloser)
    opener.moveNodesBetweenDelimitersTo(node, closer)
  }
}
