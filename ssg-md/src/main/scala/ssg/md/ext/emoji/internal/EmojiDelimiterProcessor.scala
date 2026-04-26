/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-emoji/src/main/java/com/vladsch/flexmark/ext/emoji/internal/EmojiDelimiterProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-emoji/src/main/java/com/vladsch/flexmark/ext/emoji/internal/EmojiDelimiterProcessor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package emoji
package internal

import ssg.md.Nullable
import ssg.md.parser.InlineParser
import ssg.md.parser.core.delimiter.Delimiter
import ssg.md.parser.delimiter.{ DelimiterProcessor, DelimiterRun }
import ssg.md.util.ast.Node
import ssg.md.util.misc.CharPredicate
import ssg.md.util.sequence.BasedSequence

class EmojiDelimiterProcessor extends DelimiterProcessor {

  override def openingCharacter: Char = ':'

  override def closingCharacter: Char = ':'

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
  ): Boolean =
    leftFlanking && !"0123456789".contains(before)

  override def canBeCloser(
    before:              String,
    after:               String,
    leftFlanking:        Boolean,
    rightFlanking:       Boolean,
    beforeIsPunctuation: Boolean,
    afterIsPunctuation:  Boolean,
    beforeIsWhitespace:  Boolean,
    afterIsWhiteSpace:   Boolean
  ): Boolean =
    rightFlanking && !"0123456789".contains(after)

  override def skipNonOpenerCloser: Boolean = true

  override def getDelimiterUse(opener: DelimiterRun, closer: DelimiterRun): Int =
    if (opener.length >= 1 && closer.length >= 1) 1 else 1

  override def unmatchedDelimiterNode(inlineParser: InlineParser, delimiter: DelimiterRun): Nullable[Node] = Nullable.empty

  override def process(opener: Delimiter, closer: Delimiter, delimitersUsed: Int): Unit =
    // Normal case, wrap nodes between delimiters in emoji node.
    // don't allow any spaces between delimiters
    if (opener.input.subSequence(opener.endIndex, closer.startIndex).indexOfAny(CharPredicate.WHITESPACE) == -1) {
      val emoji = new Emoji(opener.tailChars(delimitersUsed), BasedSequence.NULL, closer.leadChars(delimitersUsed))
      opener.moveNodesBetweenDelimitersTo(emoji, closer)
    } else {
      opener.convertDelimitersToText(delimitersUsed, closer)
    }
}
