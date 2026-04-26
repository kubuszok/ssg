/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-superscript/src/main/java/com/vladsch/flexmark/ext/superscript/internal/SuperscriptDelimiterProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-superscript/src/main/java/com/vladsch/flexmark/ext/superscript/internal/SuperscriptDelimiterProcessor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package superscript
package internal

import ssg.md.Nullable
import ssg.md.parser.InlineParser
import ssg.md.parser.core.delimiter.Delimiter
import ssg.md.parser.delimiter.{ DelimiterProcessor, DelimiterRun }
import ssg.md.util.ast.Node
import ssg.md.util.sequence.BasedSequence

class SuperscriptDelimiterProcessor extends DelimiterProcessor {

  override def openingCharacter: Char = '^'

  override def closingCharacter: Char = '^'

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
  ): Boolean = true // leftFlanking

  override def canBeCloser(
    before:              String,
    after:               String,
    leftFlanking:        Boolean,
    rightFlanking:       Boolean,
    beforeIsPunctuation: Boolean,
    afterIsPunctuation:  Boolean,
    beforeIsWhitespace:  Boolean,
    afterIsWhiteSpace:   Boolean
  ): Boolean = true // rightFlanking

  override def skipNonOpenerCloser: Boolean = false

  override def getDelimiterUse(opener: DelimiterRun, closer: DelimiterRun): Int =
    if (opener.length >= 1 && closer.length >= 1) 1 else 0

  override def unmatchedDelimiterNode(inlineParser: InlineParser, delimiter: DelimiterRun): Nullable[Node] = Nullable.empty

  override def process(opener: Delimiter, closer: Delimiter, delimitersUsed: Int): Unit = {
    // Normal case, wrap nodes between delimiters in superscript.
    val superscript = new Superscript(opener.tailChars(delimitersUsed), BasedSequence.NULL, closer.leadChars(delimitersUsed))
    opener.moveNodesBetweenDelimitersTo(superscript, closer)
  }
}
