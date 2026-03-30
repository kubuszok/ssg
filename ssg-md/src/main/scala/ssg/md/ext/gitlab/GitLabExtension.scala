/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gitlab/src/main/java/com/vladsch/flexmark/ext/gitlab/GitLabExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gitlab

import ssg.md.ext.gitlab.internal.*
import ssg.md.formatter.Formatter
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.{DataKey, MutableDataHolder}

import scala.language.implicitConversions

/**
 * Extension for GitLab Flavoured Markdown
 *
 * Create it with [[GitLabExtension.create]] and then configure it on the builders
 */
class GitLabExtension private ()
    extends Parser.ParserExtension
    with HtmlRenderer.HtmlRendererExtension
    with Formatter.FormatterExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(formatterBuilder: Formatter.Builder, rendererType: String): Unit = {
    formatterBuilder.nodeFormatterFactory(new GitLabNodeFormatter.Factory())
  }

  override def extend(parserBuilder: Parser.Builder): Unit = {
    val options = new GitLabOptions(parserBuilder)
    if (options.blockQuoteParser) {
      parserBuilder.customBlockParserFactory(new GitLabBlockQuoteParser.Factory())
    }

    if (options.delParser || options.insParser) {
      parserBuilder.customInlineParserExtensionFactory(new GitLabInlineParser.Factory())
    }

    if (options.inlineMathParser) {
      parserBuilder.customInlineParserExtensionFactory(new GitLabInlineMathParser.Factory())
    }
  }

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new GitLabNodeRenderer.Factory())
    }
    // Skipping JIRA renderer per conversion rules
  }
}

object GitLabExtension {

  private val DEFAULT_MATH_LANGUAGES: Array[String] = Array("math")
  private val DEFAULT_MERMAID_LANGUAGES: Array[String] = Array("mermaid")

  val INS_PARSER: DataKey[Boolean] = new DataKey[Boolean]("INS_PARSER", true)
  val DEL_PARSER: DataKey[Boolean] = new DataKey[Boolean]("DEL_PARSER", true)
  val BLOCK_QUOTE_PARSER: DataKey[Boolean] = new DataKey[Boolean]("BLOCK_QUOTE_PARSER", true)
  val NESTED_BLOCK_QUOTES: DataKey[Boolean] = new DataKey[Boolean]("NESTED_BLOCK_QUOTES", true)
  val INLINE_MATH_PARSER: DataKey[Boolean] = new DataKey[Boolean]("INLINE_MATH_PARSER", true)
  val RENDER_BLOCK_MATH: DataKey[Boolean] = new DataKey[Boolean]("RENDER_BLOCK_MATH", true)
  val RENDER_BLOCK_MERMAID: DataKey[Boolean] = new DataKey[Boolean]("RENDER_BLOCK_MERMAID", true)
  val RENDER_VIDEO_IMAGES: DataKey[Boolean] = new DataKey[Boolean]("RENDER_VIDEO_IMAGES", true)
  val RENDER_VIDEO_LINK: DataKey[Boolean] = new DataKey[Boolean]("RENDER_VIDEO_LINK", true)

  val MATH_LANGUAGES: DataKey[Array[String]] = new DataKey[Array[String]]("MATH_LANGUAGES", DEFAULT_MATH_LANGUAGES)
  val MERMAID_LANGUAGES: DataKey[Array[String]] = new DataKey[Array[String]]("MERMAID_LANGUAGES", DEFAULT_MERMAID_LANGUAGES)
  val INLINE_MATH_CLASS: DataKey[String] = new DataKey[String]("INLINE_MATH_CLASS", "katex")
  val BLOCK_MATH_CLASS: DataKey[String] = new DataKey[String]("BLOCK_MATH_CLASS", "katex")
  val BLOCK_MERMAID_CLASS: DataKey[String] = new DataKey[String]("BLOCK_MERMAID_CLASS", "mermaid")
  val VIDEO_IMAGE_CLASS: DataKey[String] = new DataKey[String]("VIDEO_IMAGE_CLASS", "video-container")
  val VIDEO_IMAGE_LINK_TEXT_FORMAT: DataKey[String] = new DataKey[String]("VIDEO_IMAGE_LINK_TEXT_FORMAT", "Download '%s'")

  @deprecated("use HtmlRenderer.FENCED_CODE_LANGUAGE_DELIMITERS instead", "")
  val BLOCK_INFO_DELIMITERS: DataKey[String] = HtmlRenderer.FENCED_CODE_LANGUAGE_DELIMITERS
  val VIDEO_IMAGE_EXTENSIONS: DataKey[String] = new DataKey[String]("VIDEO_IMAGE_EXTENSIONS", "mp4,m4v,mov,webm,ogv")

  def create(): GitLabExtension = new GitLabExtension()
}
