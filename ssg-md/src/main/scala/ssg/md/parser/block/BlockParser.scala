/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/block/BlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser
package block

import ssg.md.parser.InlineParser
import ssg.md.util.ast.{ Block, BlockContent }
import ssg.md.util.data.MutableDataHolder
import ssg.md.util.sequence.BasedSequence

/** Parser for a specific block node.
  *
  * Implementations should subclass [[AbstractBlockParser]] instead of implementing this directly.
  */
trait BlockParser {

  /** @return true if the block that is parsed is a container (contains other blocks), or false if it's a leaf. */
  def isContainer: Boolean

  /** @param state
    *   parser state
    * @param blockParser
    *   block parser
    * @param block
    *   new block being started
    * @return
    *   true if this block parser's block can contain the given block type, false if it cannot
    */
  def canContain(state: ParserState, blockParser: BlockParser, block: Block): Boolean

  /** @return the block parser's block node instance */
  def getBlock: Block

  /** See if the block parser can continue parsing the current block.
    *
    * @param state
    *   current parsing state
    * @return
    *   block continue instance
    */
  def tryContinue(state: ParserState): Nullable[BlockContinue]

  /** Add another line to the block.
    *
    * @param state
    *   parser state
    * @param line
    *   line sequence
    */
  def addLine(state: ParserState, line: BasedSequence): Unit

  def closeBlock(state: ParserState): Unit

  /** @return true if the block is already closed. */
  def isClosed: Boolean

  /** @param lastMatchedBlockParser
    *   last matched block parser instance
    * @return
    *   true if the last blank line status should be propagated to parent blocks
    */
  def isPropagatingLastBlankLine(lastMatchedBlockParser: BlockParser): Boolean

  /** @return true if Double blank line should finalize this block parser and its children and reset to parent */
  def breakOutOnDoubleBlankLine: Boolean

  /** @return true if this block parser is the paragraph block parser */
  def isParagraphParser: Boolean

  /** @return get the currently accumulated block content. */
  def getBlockContent: Nullable[BlockContent]

  /** Used to clean up and prepare for the next parsing run of the AbstractBlockParser. For internal parser house keeping, not for BlockParser implementors.
    */
  def finalizeClosedBlock(): Unit

  /** Do inline processing for the block content using the given inline parser interface.
    *
    * @param inlineParser
    *   instance of inline parser
    */
  def parseInlines(inlineParser: InlineParser): Unit

  /** Allows block parsers to be interrupted by other block parsers.
    *
    * @return
    *   true if block starts should be tried when this block parser is active
    */
  def isInterruptible: Boolean

  /** Allows block parsers to keep indenting spaces for those blocks that are interruptible but don't want indenting spaces removed.
    *
    * @return
    *   true if block wants to keep indenting spaces
    */
  def isRawText: Boolean

  /** Allows block parsers to determine if they can be interrupted by other block parsers.
    *
    * @param blockParserFactory
    *   interrupting block parser
    * @return
    *   true if can interrupt.
    */
  def canInterruptBy(blockParserFactory: BlockParserFactory): Boolean

  /** @return the data holder for a block parser instance. Implemented by [[AbstractBlockParser]] */
  def getDataHolder: MutableDataHolder
}
