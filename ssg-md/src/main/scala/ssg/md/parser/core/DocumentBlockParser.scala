/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/core/DocumentBlockParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser
package core

import ssg.md.parser.Parser
import ssg.md.parser.block._
import ssg.md.util.ast.{ BlankLineContainer, Block, Document }
import ssg.md.util.data.DataHolder
import ssg.md.util.sequence.BasedSequence

import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

class DocumentBlockParser extends AbstractBlockParser, BlankLineContainer {

  private var _document: Document = scala.compiletime.uninitialized

  def initializeDocument(options: DataHolder, charSequence: BasedSequence): Unit =
    _document = Document(options, charSequence)

  override def isContainer: Boolean = true

  override def canContain(state: ParserState, blockParser: BlockParser, block: Block): Boolean = true

  override def getBlock: Document = _document

  override def tryContinue(state: ParserState): Nullable[BlockContinue] =
    Nullable(BlockContinue.atIndex(state.getIndex))

  override def addLine(state: ParserState, line: BasedSequence): Unit = {}

  override def closeBlock(state: ParserState): Unit =
    if (Parser.TRACK_DOCUMENT_LINES.get(state.properties)) {
      _document.setContent(state.lineSegments.asJava)
    }
}
