/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/core/delimiter/EmphasisDelimiterProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/core/delimiter/EmphasisDelimiterProcessor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package parser
package core
package delimiter

import ssg.md.ast.{ Emphasis, StrongEmphasis }
import ssg.md.parser.InlineParser
import ssg.md.parser.delimiter.{ DelimiterProcessor, DelimiterRun }
import ssg.md.util.ast.{ DelimitedNode, Node }
import ssg.md.util.misc.Utils
import ssg.md.util.sequence.BasedSequence

abstract class EmphasisDelimiterProcessor(
  private val _delimiterChar: Char,
  strongWrapsEmphasis:        Boolean
) extends DelimiterProcessor {

  private val multipleUse: Int = if (strongWrapsEmphasis) 1 else 2

  override def openingCharacter: Char = _delimiterChar

  override def closingCharacter: Char = _delimiterChar

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

  override def unmatchedDelimiterNode(inlineParser: InlineParser, delimiter: DelimiterRun): Nullable[Node] =
    Nullable.empty

  override def getDelimiterUse(opener: DelimiterRun, closer: DelimiterRun): Int =
    // "multiple of 3" rule for internal delimiter runs
    if ((opener.canClose || closer.canOpen) && (opener.length + closer.length) % 3 == 0) {
      0
    } else if (opener.length < 3 || closer.length < 3) {
      // calculate actual number of delimiters used from this closer
      Utils.min(closer.length, opener.length)
    } else {
      // default to latest spec
      if (closer.length % 2 == 0) 2 else multipleUse
    }

  override def process(opener: Delimiter, closer: Delimiter, delimitersUsed: Int): Unit = {
    val emphasis: DelimitedNode =
      if (delimitersUsed == 1)
        Emphasis(opener.tailChars(delimitersUsed), BasedSequence.NULL, closer.leadChars(delimitersUsed))
      else
        StrongEmphasis(opener.tailChars(delimitersUsed), BasedSequence.NULL, closer.leadChars(delimitersUsed))

    opener.moveNodesBetweenDelimitersTo(emphasis, closer)
  }
}
