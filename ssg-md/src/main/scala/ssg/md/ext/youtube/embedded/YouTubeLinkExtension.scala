/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-youtube-embedded/src/main/java/com/vladsch/flexmark/ext/youtube/embedded/YouTubeLinkExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-youtube-embedded/src/main/java/com/vladsch/flexmark/ext/youtube/embedded/YouTubeLinkExtension.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package youtube
package embedded

import ssg.md.ext.youtube.embedded.internal.{ YouTubeLinkNodePostProcessor, YouTubeLinkNodeRenderer }
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.MutableDataHolder

class YouTubeLinkExtension private () extends Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(parserBuilder: Parser.Builder): Unit =
    parserBuilder.postProcessorFactory(new YouTubeLinkNodePostProcessor.Factory(parserBuilder))

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new YouTubeLinkNodeRenderer.Factory())
    }
    // Skipping JIRA renderer per conversion rules
  }
}

object YouTubeLinkExtension {
  def create(): YouTubeLinkExtension = new YouTubeLinkExtension()
}
