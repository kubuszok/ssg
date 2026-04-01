/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/block/ParserState.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser
package block

import ssg.md.ast.util.Parsing
import ssg.md.parser.InlineParser
import ssg.md.util.ast.{ Block, BlockTracker, Node }
import ssg.md.util.data.MutableDataHolder
import ssg.md.util.sequence.BasedSequence

/** State of the parser that is used in block parsers.
  *
  * ''This interface is not intended to be implemented by clients.''
  */
trait ParserState extends BlockTracker, BlockParserTracker {

  /** @return the current line */
  def line: BasedSequence

  /** @return the current line with EOL */
  def lineWithEOL: BasedSequence

  /** @return the current index within the line (0-based) */
  def getIndex: Int

  /** @return the index of the next non-space character starting from `getIndex` (may be the same) (0-based) */
  def nextNonSpaceIndex: Int

  /** The column is the position within the line after tab characters have been processed as 4-space tab stops. If the line doesn't contain any tabs, it's the same as the `getIndex`. If the line
    * starts with a tab, followed by text, then the column for the first character of the text is 4 (the index is 1).
    *
    * @return
    *   the current column within the line (0-based)
    */
  def column: Int

  /** @return the indentation in columns (either by spaces or tab stop of 4), starting from `column` */
  def indent: Int

  /** @return true if the current line is blank starting from the index */
  def isBlank: Boolean

  /** @return true if the current line is blank starting from the index */
  def isBlankLine: Boolean

  /** @return the deepest open block parser */
  def activeBlockParser: BlockParser

  /** @return the current list of active block parsers, deepest is last */
  def activeBlockParsers: List[BlockParser]

  /** @param node
    *   block node for which to get the active block parser
    * @return
    *   an active block parser for the node or null if not found or the block is already closed.
    */
  def getActiveBlockParser(node: Block): Nullable[BlockParser]

  /** @return inline parser instance for the parser state */
  def inlineParser: InlineParser

  /** @return The 0 based current line number within the input */
  def lineNumber: Int

  /** @return the start of line offset into the input stream corresponding to current index into the line */
  def lineStart: Int

  /** @return the EOL offset into the input stream corresponding to current index into the line */
  def lineEolLength: Int

  /** @return the end of line offset into the input stream corresponding to current index into the line, including the EOL */
  def lineEndIndex: Int

  /** Test the block to see if it ends in a blank line. The blank line can be in the block or its last child.
    *
    * @param block
    *   block to be tested
    * @return
    *   true if the block ends in a blank line
    */
  def endsWithBlankLine(block: Node): Boolean

  /** Test a block to see if the last line of the block is blank. Children not tested.
    *
    * @param node
    *   block instance to test
    * @return
    *   true if the block's last line is blank
    */
  def isLastLineBlank(node: Node): Boolean

  /** @return document properties of the document being parsed */
  def properties: MutableDataHolder

  /** Get the current parser phase.
    *
    * @return
    *   the current parser phase
    */
  def parserPhase: ParserPhase

  /** @return strings and patterns class adjusted for options */
  def parsing: Parsing

  /** Returns a list of document lines encountered this far in the parsing process.
    *
    * @return
    *   list of line sequences (including EOLs)
    */
  def lineSegments: List[BasedSequence]
}
