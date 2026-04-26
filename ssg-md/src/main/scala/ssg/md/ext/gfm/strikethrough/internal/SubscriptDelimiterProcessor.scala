/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-strikethrough/src/main/java/com/vladsch/flexmark/ext/gfm/strikethrough/internal/SubscriptDelimiterProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-gfm-strikethrough/src/main/java/com/vladsch/flexmark/ext/gfm/strikethrough/internal/SubscriptDelimiterProcessor.java
 * Covenant-verified: 2026-04-26
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

class SubscriptDelimiterProcessor extends DelimiterProcessor {

  override def openingCharacter: Char = '~'

  override def closingCharacter: Char = '~'

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

  override def unmatchedDelimiterNode(inlineParser: InlineParser, delimiter: DelimiterRun): Nullable[Node] = Nullable.empty

  override def getDelimiterUse(opener: DelimiterRun, closer: DelimiterRun): Int =
    if (opener.length >= 1 && closer.length >= 1) {
      // Use exactly one delimiter even if we have more, and don't care about internal openers/closers.
      1
    } else {
      0
    }

  override def process(opener: Delimiter, closer: Delimiter, delimitersUsed: Int): Unit = {
    // wrap nodes between delimiters in subscript.
    val node = new Subscript(opener.tailChars(delimitersUsed), BasedSequence.NULL, closer.leadChars(delimitersUsed))
    opener.moveNodesBetweenDelimitersTo(node, closer)
  }
}
