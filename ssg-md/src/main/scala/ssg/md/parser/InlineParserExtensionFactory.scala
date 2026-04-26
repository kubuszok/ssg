/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/InlineParserExtensionFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/InlineParserExtensionFactory.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser

import ssg.md.util.dependency.Dependent

trait InlineParserExtensionFactory extends (LightInlineParser => InlineParserExtension), Dependent {

  /** Starting characters for this inline processor.
    *
    * @return
    *   set of characters for which this processor should be invoked
    */
  def getCharacters: CharSequence

  /** Create an inline parser extension for the given inline parser.
    *
    * @param inlineParser
    *   inline parser instance
    * @return
    *   inline parser extension
    */
  override def apply(inlineParser: LightInlineParser): InlineParserExtension
}
