/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/delimiter/DelimiterProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/delimiter/DelimiterProcessor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package parser
package delimiter

import ssg.md.parser.core.delimiter.Delimiter
import ssg.md.util.ast.Node

/** Custom delimiter processor for additional delimiters besides `_` and `*`.
  *
  * Note that implementations of this need to be thread-safe, the same instance may be used by multiple parsers.
  */
trait DelimiterProcessor {

  /** @return the character that marks the beginning of a delimited node */
  def openingCharacter: Char

  /** @return the character that marks the ending of a delimited node */
  def closingCharacter: Char

  /** @return Minimum number of delimiter characters that are needed to activate this. Must be at least 1. */
  def minLength: Int

  /** Determine how many (if any) of the delimiter characters should be used.
    *
    * @param opener
    *   the opening delimiter run
    * @param closer
    *   the closing delimiter run
    * @return
    *   how many delimiters should be used; must not be greater than length of either opener or closer
    */
  def getDelimiterUse(opener: DelimiterRun, closer: DelimiterRun): Int

  /** Process the matched delimiters, e.g. by wrapping the nodes between opener and closer in a new node.
    *
    * @param opener
    *   the delimiter with text node that contained the opening delimiter
    * @param closer
    *   the delimiter with text node that contained the closing delimiter
    * @param delimitersUsed
    *   the number of delimiters that were used
    */
  def process(opener: Delimiter, closer: Delimiter, delimitersUsed: Int): Unit

  /** Allow delimiter processor to substitute unmatched delimiters by custom nodes.
    *
    * @param inlineParser
    *   inline parser instance
    * @param delimiter
    *   delimiter run that was not matched
    * @return
    *   node to replace unmatched delimiter, or Nullable.empty to replace with delimiter text
    */
  def unmatchedDelimiterNode(inlineParser: InlineParser, delimiter: DelimiterRun): Nullable[Node]

  /** Decide whether this delimiter can be an open delimiter.
    */
  def canBeOpener(
    before:              String,
    after:               String,
    leftFlanking:        Boolean,
    rightFlanking:       Boolean,
    beforeIsPunctuation: Boolean,
    afterIsPunctuation:  Boolean,
    beforeIsWhitespace:  Boolean,
    afterIsWhiteSpace:   Boolean
  ): Boolean

  /** Decide whether this delimiter can be a close delimiter.
    */
  def canBeCloser(
    before:              String,
    after:               String,
    leftFlanking:        Boolean,
    rightFlanking:       Boolean,
    beforeIsPunctuation: Boolean,
    afterIsPunctuation:  Boolean,
    beforeIsWhitespace:  Boolean,
    afterIsWhiteSpace:   Boolean
  ): Boolean

  /** Whether to skip delimiters that cannot be openers or closers. */
  def skipNonOpenerCloser: Boolean
}
