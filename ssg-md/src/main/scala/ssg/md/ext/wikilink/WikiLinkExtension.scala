/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-wikilink/src/main/java/com/vladsch/flexmark/ext/wikilink/WikiLinkExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package wikilink

import ssg.md.ext.wikilink.internal.{WikiLinkLinkRefProcessor, WikiLinkLinkResolver, WikiLinkNodeFormatter, WikiLinkNodeRenderer}
import ssg.md.formatter.Formatter
import ssg.md.html.HtmlRenderer
import ssg.md.html.renderer.LinkType
import ssg.md.parser.Parser
import ssg.md.util.data.{DataKey, MutableDataHolder}

import scala.language.implicitConversions

/**
 * Extension for wikilinks
 *
 * Create it with [[WikiLinkExtension.create]] and then configure it on the builders
 *
 * The parsed wiki link text regions are turned into [[WikiLink]] nodes.
 */
class WikiLinkExtension private ()
    extends Parser.ParserExtension
    with HtmlRenderer.HtmlRendererExtension
    with Formatter.FormatterExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(formatterBuilder: Formatter.Builder, rendererType: String): Unit = {
    formatterBuilder.nodeFormatterFactory(new WikiLinkNodeFormatter.Factory())
  }

  override def extend(parserBuilder: Parser.Builder): Unit = {
    parserBuilder.linkRefProcessorFactory(new WikiLinkLinkRefProcessor.Factory())
  }

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new WikiLinkNodeRenderer.Factory())
      htmlRendererBuilder.linkResolverFactory(new WikiLinkLinkResolver.Factory())
    }
    // Skipping JIRA renderer per conversion rules
  }
}

object WikiLinkExtension {

  val ALLOW_INLINES: DataKey[Boolean] = new DataKey[Boolean]("ALLOW_INLINES", false)
  val ALLOW_ANCHORS: DataKey[Boolean] = new DataKey[Boolean]("ALLOW_ANCHORS", false)
  val ALLOW_ANCHOR_ESCAPE: DataKey[Boolean] = new DataKey[Boolean]("ALLOW_ANCHOR_ESCAPE", false)
  val ALLOW_PIPE_ESCAPE: DataKey[Boolean] = new DataKey[Boolean]("ALLOW_PIPE_ESCAPE", false)
  val DISABLE_RENDERING: DataKey[Boolean] = new DataKey[Boolean]("DISABLE_RENDERING", false)
  val LINK_FIRST_SYNTAX: DataKey[Boolean] = new DataKey[Boolean]("LINK_FIRST_SYNTAX", false)
  val LINK_PREFIX: DataKey[String] = new DataKey[String]("LINK_PREFIX", "")

  /** Link prefix to use for absolute wiki links starting with the '/' character. */
  val LINK_PREFIX_ABSOLUTE: DataKey[String] = new DataKey[String]("LINK_PREFIX_ABSOLUTE", LINK_PREFIX)

  val IMAGE_PREFIX: DataKey[String] = new DataKey[String]("IMAGE_PREFIX", "")

  /** Image prefix to use for absolute wiki image sources starting with the '/' character. */
  val IMAGE_PREFIX_ABSOLUTE: DataKey[String] = new DataKey[String]("IMAGE_PREFIX_ABSOLUTE", IMAGE_PREFIX)

  val IMAGE_LINKS: DataKey[Boolean] = new DataKey[Boolean]("IMAGE_LINKS", false)
  val LINK_FILE_EXTENSION: DataKey[String] = new DataKey[String]("LINK_FILE_EXTENSION", "")
  val IMAGE_FILE_EXTENSION: DataKey[String] = new DataKey[String]("IMAGE_FILE_EXTENSION", "")

  /** Characters to escape in wiki links. */
  val LINK_ESCAPE_CHARS: DataKey[String] = new DataKey[String]("LINK_ESCAPE_CHARS", " +/<>")

  /** Characters to replace [[LINK_ESCAPE_CHARS]] with. */
  val LINK_REPLACE_CHARS: DataKey[String] = new DataKey[String]("LINK_REPLACE_CHARS", "-----")

  val WIKI_LINK: LinkType = new LinkType("WIKI")

  def create(): WikiLinkExtension = new WikiLinkExtension()
}
