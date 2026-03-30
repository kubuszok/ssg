/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-resizable-image/src/main/java/com/vladsch/flexmark/ext/resizable/image/ResizableImageExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package resizable
package image

import ssg.md.ext.resizable.image.internal.{ResizableImageInlineParserExtension, ResizableImageNodeRenderer}
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.MutableDataHolder

class ResizableImageExtension private () extends Parser.ParserExtension with HtmlRenderer.HtmlRendererExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(parserBuilder: Parser.Builder): Unit = {
    parserBuilder.customInlineParserExtensionFactory(new ResizableImageInlineParserExtension.Factory())
  }

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new ResizableImageNodeRenderer.Factory())
    }
    // Skipping JIRA renderer per conversion rules
  }
}

object ResizableImageExtension {
  def create(): ResizableImageExtension = new ResizableImageExtension()
}
