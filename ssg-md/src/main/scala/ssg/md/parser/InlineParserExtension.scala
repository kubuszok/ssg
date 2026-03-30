/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/InlineParserExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser

trait InlineParserExtension {

  def finalizeDocument(inlineParser: InlineParser): Unit

  def finalizeBlock(inlineParser: InlineParser): Unit

  /** Parse input.
    *
    * @param inlineParser
    *   the light inline parser
    * @return
    *   true if character input was processed
    */
  def parse(inlineParser: LightInlineParser): Boolean
}
