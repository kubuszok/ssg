/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/internal/CommonmarkInlineParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/internal/CommonmarkInlineParser.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package parser
package internal

import ssg.md.parser.InlineParserExtensionFactory
import ssg.md.parser.delimiter.DelimiterProcessor
import ssg.md.util.ast.Document
import ssg.md.util.data.DataHolder

import java.util.BitSet

class CommonmarkInlineParser(
  options:                 DataHolder,
  specialCharacters:       BitSet,
  delimiterCharacters:     BitSet,
  delimiterProcessors:     Map[Char, DelimiterProcessor],
  referenceLinkProcessors: LinkRefProcessorData,
  inlineParserExtensions:  List[InlineParserExtensionFactory]
) extends InlineParserImpl(
      options,
      specialCharacters,
      delimiterCharacters,
      delimiterProcessors,
      referenceLinkProcessors,
      inlineParserExtensions
    ) {

  override def initializeDocument(document: Document): Unit =
    super.initializeDocument(document)
}
