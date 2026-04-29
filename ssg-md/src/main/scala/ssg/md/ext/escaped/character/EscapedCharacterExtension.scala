/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-escaped-character/src/main/java/com/vladsch/flexmark/ext/escaped/character/EscapedCharacterExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-escaped-character/src/main/java/com/vladsch/flexmark/ext/escaped/character/EscapedCharacterExtension.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package escaped
package character

import ssg.md.ext.escaped.character.internal.{ EscapedCharacterNodePostProcessor, EscapedCharacterNodeRenderer }
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.MutableDataHolder

/** Extension for escaped_characters.
  *
  * Create it with [[EscapedCharacterExtension.create]] and then configure it on the builders.
  *
  * The parsed escaped_character text is turned into [[EscapedCharacter]] nodes.
  */
class EscapedCharacterExtension private () extends Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(parserBuilder: Parser.Builder): Unit =
    parserBuilder.postProcessorFactory(new EscapedCharacterNodePostProcessor.Factory())

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new EscapedCharacterNodeRenderer.Factory())
    }
    // Skipping JIRA renderer per conversion rules
  }
}

object EscapedCharacterExtension {
  def create(): EscapedCharacterExtension = new EscapedCharacterExtension()
}
