/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gitlab/src/main/java/com/vladsch/flexmark/ext/gitlab/internal/GitLabOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gitlab
package internal

import ssg.md.html.HtmlRenderer
import ssg.md.util.data.{DataHolder, MutableDataHolder, MutableDataSetter}
import ssg.md.util.misc.CharPredicate

import scala.language.implicitConversions

class GitLabOptions(options: DataHolder) extends MutableDataSetter {

  val insParser: Boolean = GitLabExtension.INS_PARSER.get(options)
  val delParser: Boolean = GitLabExtension.DEL_PARSER.get(options)
  val inlineMathParser: Boolean = GitLabExtension.INLINE_MATH_PARSER.get(options)
  val blockQuoteParser: Boolean = GitLabExtension.BLOCK_QUOTE_PARSER.get(options)
  val nestedBlockQuotes: Boolean = GitLabExtension.NESTED_BLOCK_QUOTES.get(options)
  val inlineMathClass: String = GitLabExtension.INLINE_MATH_CLASS.get(options)
  val renderBlockMath: Boolean = GitLabExtension.RENDER_BLOCK_MATH.get(options)
  val renderBlockMermaid: Boolean = GitLabExtension.RENDER_BLOCK_MERMAID.get(options)
  val renderVideoImages: Boolean = GitLabExtension.RENDER_VIDEO_IMAGES.get(options)
  val renderVideoLink: Boolean = GitLabExtension.RENDER_VIDEO_LINK.get(options)
  val blockMathClass: String = GitLabExtension.BLOCK_MATH_CLASS.get(options)
  val blockMermaidClass: String = GitLabExtension.BLOCK_MERMAID_CLASS.get(options)
  val blockInfoDelimiters: String = HtmlRenderer.FENCED_CODE_LANGUAGE_DELIMITERS.get(options)
  val blockInfoDelimiterSet: CharPredicate = CharPredicate.anyOf(blockInfoDelimiters)
  val mathLanguages: Array[String] = GitLabExtension.MATH_LANGUAGES.get(options)
  val mermaidLanguages: Array[String] = GitLabExtension.MERMAID_LANGUAGES.get(options)
  val videoImageClass: String = GitLabExtension.VIDEO_IMAGE_CLASS.get(options)
  val videoImageLinkTextFormat: String = GitLabExtension.VIDEO_IMAGE_LINK_TEXT_FORMAT.get(options)
  val videoImageExtensions: String = GitLabExtension.VIDEO_IMAGE_EXTENSIONS.get(options)
  val videoImageExtensionSet: java.util.HashSet[String] = {
    val set = new java.util.HashSet[String]()
    val extensions = videoImageExtensions.split(",")
    for (ext <- extensions) {
      val trimmed = ext.trim
      if (trimmed.nonEmpty) {
        set.add(trimmed)
      }
    }
    set
  }

  override def setIn(dataHolder: MutableDataHolder): MutableDataHolder = {
    dataHolder.set(GitLabExtension.INS_PARSER, insParser)
    dataHolder.set(GitLabExtension.DEL_PARSER, delParser)
    dataHolder.set(GitLabExtension.INLINE_MATH_PARSER, inlineMathParser)
    dataHolder.set(GitLabExtension.BLOCK_QUOTE_PARSER, blockQuoteParser)
    dataHolder.set(GitLabExtension.NESTED_BLOCK_QUOTES, nestedBlockQuotes)
    dataHolder.set(GitLabExtension.INLINE_MATH_CLASS, inlineMathClass)
    dataHolder.set(GitLabExtension.RENDER_BLOCK_MATH, renderBlockMath)
    dataHolder.set(GitLabExtension.RENDER_BLOCK_MERMAID, renderBlockMermaid)
    dataHolder.set(GitLabExtension.RENDER_VIDEO_IMAGES, renderVideoImages)
    dataHolder.set(GitLabExtension.RENDER_VIDEO_LINK, renderVideoLink)
    dataHolder.set(GitLabExtension.BLOCK_MATH_CLASS, blockMathClass)
    dataHolder.set(GitLabExtension.BLOCK_MERMAID_CLASS, blockMermaidClass)
    dataHolder.set(HtmlRenderer.FENCED_CODE_LANGUAGE_DELIMITERS, blockInfoDelimiters)
    dataHolder.set(GitLabExtension.VIDEO_IMAGE_CLASS, videoImageClass)
    dataHolder.set(GitLabExtension.VIDEO_IMAGE_LINK_TEXT_FORMAT, videoImageLinkTextFormat)
    dataHolder.set(GitLabExtension.VIDEO_IMAGE_EXTENSIONS, videoImageExtensions)
    dataHolder
  }
}
