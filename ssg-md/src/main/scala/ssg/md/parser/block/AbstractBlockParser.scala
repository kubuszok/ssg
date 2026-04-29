/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/block/AbstractBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/block/AbstractBlockParser.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package parser
package block

import ssg.md.parser.InlineParser
import ssg.md.util.ast.{ BlankLine, Block, BlockContent, Node }
import ssg.md.util.data.{ MutableDataHolder, MutableDataSet }
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

abstract class AbstractBlockParser extends BlockParser {

  private var mutableData: Nullable[MutableDataSet] = Nullable.empty
  private var _isClosed:   Boolean                  = false

  override def isClosed: Boolean = _isClosed

  override def isContainer: Boolean = false

  override def isInterruptible: Boolean = false

  override def isRawText: Boolean = false

  override def canContain(state: ParserState, blockParser: BlockParser, block: Block): Boolean = false

  override def isParagraphParser: Boolean = false

  /** Should be overridden in BlockQuote, FencedCode and ListItem.
    *
    * @param lastMatchedBlockParser
    *   the last matched block parser instance
    * @return
    *   true if the blank line should be propagated to parent
    */
  override def isPropagatingLastBlankLine(lastMatchedBlockParser: BlockParser): Boolean = true

  override def getBlockContent: Nullable[BlockContent] = Nullable.empty

  override def addLine(state: ParserState, line: BasedSequence): Unit = {}

  override def parseInlines(inlineParser: InlineParser): Unit = {}

  override def breakOutOnDoubleBlankLine: Boolean = false

  final override def finalizeClosedBlock(): Unit = {
    mutableData = Nullable.empty
    _isClosed = true
  }

  override def canInterruptBy(blockParserFactory: BlockParserFactory): Boolean = true

  override def getDataHolder: MutableDataHolder = {
    if (mutableData.isEmpty) {
      mutableData = MutableDataSet()
    }
    mutableData.get
  }

  def removeBlankLines(): Unit = {
    // need to remove blank lines, these were used to extend block quote chars to include blank lines
    var child: Nullable[Node] = getBlock.firstChild

    while (child.isDefined) {
      val next: Nullable[Node] = child.get.next
      child.get match {
        case _: BlankLine => child.get.unlink()
        case _ =>
      }
      child = next
    }
  }
}
