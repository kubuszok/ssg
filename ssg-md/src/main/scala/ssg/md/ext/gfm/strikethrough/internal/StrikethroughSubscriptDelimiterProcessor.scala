/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-strikethrough/src/main/java/com/vladsch/flexmark/ext/gfm/strikethrough/internal/StrikethroughSubscriptDelimiterProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-gfm-strikethrough/src/main/java/com/vladsch/flexmark/ext/gfm/strikethrough/internal/StrikethroughSubscriptDelimiterProcessor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
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
import ssg.md.util.ast.{ DelimitedNode, Node }
import ssg.md.util.sequence.BasedSequence

class StrikethroughSubscriptDelimiterProcessor extends DelimiterProcessor {

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

  override def getDelimiterUse(opener: DelimiterRun, closer: DelimiterRun): Int =
    // "multiple of 3" rule for internal delimiter runs
    if ((opener.canClose || closer.canOpen) && (opener.length + closer.length) % 3 == 0) {
      0
    } else {
      // calculate actual number of delimiters used from this closer
      if (opener.length < 3 || closer.length < 3) {
        if (closer.length <= opener.length) closer.length else opener.length
      } else {
        if (closer.length % 2 == 0) 2 else 1
      }
    }

  override def unmatchedDelimiterNode(inlineParser: InlineParser, delimiter: DelimiterRun): Nullable[Node] = Nullable.empty

  override def process(opener: Delimiter, closer: Delimiter, delimitersUsed: Int): Unit = {
    // wrap nodes between delimiters in strikethrough.
    val emphasis: DelimitedNode =
      if (delimitersUsed == 1) new Subscript(opener.tailChars(delimitersUsed), BasedSequence.NULL, closer.leadChars(delimitersUsed))
      else new Strikethrough(opener.tailChars(delimitersUsed), BasedSequence.NULL, closer.leadChars(delimitersUsed))

    opener.moveNodesBetweenDelimitersTo(emphasis, closer)
  }
}
