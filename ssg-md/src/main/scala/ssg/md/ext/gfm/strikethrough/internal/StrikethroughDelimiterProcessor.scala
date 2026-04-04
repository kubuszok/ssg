/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-strikethrough/src/main/java/com/vladsch/flexmark/ext/gfm/strikethrough/internal/StrikethroughDelimiterProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gfm
package strikethrough
package internal

import ssg.md.Nullable
import ssg.md.parser.InlineParser
import ssg.md.parser.core.delimiter.Delimiter
import ssg.md.parser.delimiter.{ DelimiterProcessor, DelimiterRun }
import ssg.md.util.ast.Node
import ssg.md.util.sequence.BasedSequence

class StrikethroughDelimiterProcessor extends DelimiterProcessor {

  override def openingCharacter: Char = '~'

  override def closingCharacter: Char = '~'

  override def minLength: Int = 2

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

  override def unmatchedDelimiterNode(inlineParser: InlineParser, delimiter: DelimiterRun): Nullable[Node] = Nullable.empty

  override def skipNonOpenerCloser: Boolean = false

  override def getDelimiterUse(opener: DelimiterRun, closer: DelimiterRun): Int =
    if (opener.length >= 2 && closer.length >= 2) {
      // Use exactly two delimiters even if we have more, and don't care about internal openers/closers.
      2
    } else {
      0
    }

  override def process(opener: Delimiter, closer: Delimiter, delimitersUsed: Int): Unit = {
    // wrap nodes between delimiters in strikethrough.
    val strikethrough = new Strikethrough(opener.tailChars(delimitersUsed), BasedSequence.NULL, closer.leadChars(delimitersUsed))
    opener.moveNodesBetweenDelimitersTo(strikethrough, closer)
  }
}
