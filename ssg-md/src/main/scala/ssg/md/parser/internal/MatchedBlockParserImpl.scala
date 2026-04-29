/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/internal/MatchedBlockParserImpl.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/internal/MatchedBlockParserImpl.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package parser
package internal

import ssg.md.parser.block.{ BlockParser, MatchedBlockParser }
import ssg.md.util.data.MutableDataHolder
import ssg.md.util.sequence.BasedSequence

import scala.jdk.CollectionConverters.*

class MatchedBlockParserImpl(val matchedBlockParser: BlockParser) extends MatchedBlockParser {

  override def blockParser: BlockParser = matchedBlockParser

  override def paragraphContent: Nullable[BasedSequence] =
    if (matchedBlockParser.isParagraphParser) {
      Nullable(matchedBlockParser.getBlockContent.get.contents)
    } else {
      Nullable.empty
    }

  override def paragraphLines: Nullable[List[BasedSequence]] =
    if (matchedBlockParser.isParagraphParser) {
      Nullable(matchedBlockParser.getBlockContent.get.lines.asScala.toList)
    } else {
      Nullable.empty
    }

  override def paragraphEolLengths: Nullable[List[Int]] =
    if (matchedBlockParser.isParagraphParser) {
      Nullable(matchedBlockParser.getBlockContent.get.lineIndents.asScala.map(_.intValue()).toList)
    } else {
      Nullable.empty
    }

  override def paragraphDataHolder: Nullable[MutableDataHolder] =
    if (matchedBlockParser.isParagraphParser) {
      Nullable(matchedBlockParser.getDataHolder)
    } else {
      Nullable.empty
    }
}
