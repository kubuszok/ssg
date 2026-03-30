/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/InlineParserFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser

import ssg.md.parser.delimiter.DelimiterProcessor
import ssg.md.parser.internal.LinkRefProcessorData
import ssg.md.util.data.DataHolder

import java.util.BitSet

trait InlineParserFactory {

  def inlineParser(
    options:                DataHolder,
    specialCharacters:      BitSet,
    delimiterCharacters:    BitSet,
    delimiterProcessors:    Map[Char, DelimiterProcessor],
    linkRefProcessors:      LinkRefProcessorData,
    inlineParserExtensions: List[InlineParserExtensionFactory]
  ): InlineParser
}
