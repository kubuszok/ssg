/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-ins/src/main/java/com/vladsch/flexmark/ext/ins/internal/InsDelimiterProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-ins/src/main/java/com/vladsch/flexmark/ext/ins/internal/InsDelimiterProcessor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package ins
package internal

import ssg.md.Nullable
import ssg.md.parser.InlineParser
import ssg.md.parser.core.delimiter.Delimiter
import ssg.md.parser.delimiter.{ DelimiterProcessor, DelimiterRun }
import ssg.md.util.ast.Node
import ssg.md.util.sequence.BasedSequence

class InsDelimiterProcessor extends DelimiterProcessor {

  override def openingCharacter: Char = '+'

  override def closingCharacter: Char = '+'

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

  override def skipNonOpenerCloser: Boolean = false

  override def getDelimiterUse(opener: DelimiterRun, closer: DelimiterRun): Int =
    if (opener.length >= 2 && closer.length >= 2) 2 else 0

  override def unmatchedDelimiterNode(inlineParser: InlineParser, delimiter: DelimiterRun): Nullable[Node] = Nullable.empty

  override def process(opener: Delimiter, closer: Delimiter, delimitersUsed: Int): Unit = {
    // Normal case, wrap nodes between delimiters in ins.
    val ins = new Ins(opener.tailChars(delimitersUsed), BasedSequence.NULL, closer.leadChars(delimitersUsed))
    opener.moveNodesBetweenDelimitersTo(ins, closer)
  }
}
