/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-anchorlink/src/main/java/com/vladsch/flexmark/ext/anchorlink/AnchorLinkExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package anchorlink

import ssg.md.ext.anchorlink.internal.{AnchorLinkNodePostProcessor, AnchorLinkNodeRenderer}
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.{DataKey, MutableDataHolder}

/**
 * Extension for anchor links.
 *
 * Create it with [[AnchorLinkExtension.create]] and then configure it on the builders.
 *
 * The parsed anchorlink text is turned into [[AnchorLink]] nodes.
 */
class AnchorLinkExtension private () extends Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(parserBuilder: Parser.Builder): Unit = {
    parserBuilder.postProcessorFactory(new AnchorLinkNodePostProcessor.Factory(parserBuilder))
  }

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new AnchorLinkNodeRenderer.Factory())
    }
    // Skipping JIRA renderer per conversion rules
  }
}

object AnchorLinkExtension {
  val ANCHORLINKS_WRAP_TEXT: DataKey[Boolean] = new DataKey[Boolean]("ANCHORLINKS_WRAP_TEXT", true)
  val ANCHORLINKS_TEXT_PREFIX: DataKey[String] = new DataKey[String]("ANCHORLINKS_TEXT_PREFIX", "")
  val ANCHORLINKS_TEXT_SUFFIX: DataKey[String] = new DataKey[String]("ANCHORLINKS_TEXT_SUFFIX", "")
  val ANCHORLINKS_ANCHOR_CLASS: DataKey[String] = new DataKey[String]("ANCHORLINKS_ANCHOR_CLASS", "")
  val ANCHORLINKS_SET_NAME: DataKey[Boolean] = new DataKey[Boolean]("ANCHORLINKS_SET_NAME", false)
  val ANCHORLINKS_SET_ID: DataKey[Boolean] = new DataKey[Boolean]("ANCHORLINKS_SET_ID", true)
  val ANCHORLINKS_NO_BLOCK_QUOTE: DataKey[Boolean] = new DataKey[Boolean]("ANCHORLINKS_NO_BLOCK_QUOTE", false)

  def create(): AnchorLinkExtension = new AnchorLinkExtension()
}
