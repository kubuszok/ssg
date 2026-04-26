/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/HtmlRendererOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/HtmlRendererOptions.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package html

import ssg.md.util.data.DataHolder
import ssg.md.util.misc.{ CharPredicate, Utils }

import java.util.regex.Pattern
import scala.collection.mutable
import scala.language.implicitConversions

class HtmlRendererOptions(options: DataHolder) {
  val softBreak:                    String                          = HtmlRenderer.SOFT_BREAK.get(options)
  val isSoftBreakAllSpaces:         Boolean                         = Utils.isWhiteSpaceNoEOL(softBreak)
  val hardBreak:                    String                          = HtmlRenderer.HARD_BREAK.get(options)
  val strongEmphasisStyleHtmlOpen:  Nullable[String]                = Nullable(HtmlRenderer.STRONG_EMPHASIS_STYLE_HTML_OPEN.get(options))
  val strongEmphasisStyleHtmlClose: Nullable[String]                = Nullable(HtmlRenderer.STRONG_EMPHASIS_STYLE_HTML_CLOSE.get(options))
  val emphasisStyleHtmlOpen:        Nullable[String]                = Nullable(HtmlRenderer.EMPHASIS_STYLE_HTML_OPEN.get(options))
  val emphasisStyleHtmlClose:       Nullable[String]                = Nullable(HtmlRenderer.EMPHASIS_STYLE_HTML_CLOSE.get(options))
  val codeStyleHtmlOpen:            Nullable[String]                = Nullable(HtmlRenderer.CODE_STYLE_HTML_OPEN.get(options))
  val codeStyleHtmlClose:           Nullable[String]                = Nullable(HtmlRenderer.CODE_STYLE_HTML_CLOSE.get(options))
  val escapeHtmlBlocks:             Boolean                         = HtmlRenderer.ESCAPE_HTML_BLOCKS.get(options)
  val escapeHtmlCommentBlocks:      Boolean                         = HtmlRenderer.ESCAPE_HTML_COMMENT_BLOCKS.get(options)
  val escapeInlineHtml:             Boolean                         = HtmlRenderer.ESCAPE_INLINE_HTML.get(options)
  val escapeInlineHtmlComments:     Boolean                         = HtmlRenderer.ESCAPE_INLINE_HTML_COMMENTS.get(options)
  val percentEncodeUrls:            Boolean                         = HtmlRenderer.PERCENT_ENCODE_URLS.get(options)
  val indentSize:                   Int                             = HtmlRenderer.INDENT_SIZE.get(options)
  val suppressHtmlBlocks:           Boolean                         = HtmlRenderer.SUPPRESS_HTML_BLOCKS.get(options)
  val suppressHtmlCommentBlocks:    Boolean                         = HtmlRenderer.SUPPRESS_HTML_COMMENT_BLOCKS.get(options)
  val suppressInlineHtml:           Boolean                         = HtmlRenderer.SUPPRESS_INLINE_HTML.get(options)
  val suppressInlineHtmlComments:   Boolean                         = HtmlRenderer.SUPPRESS_INLINE_HTML_COMMENTS.get(options)
  val doNotRenderLinksInDocument:   Boolean                         = HtmlRenderer.DO_NOT_RENDER_LINKS.get(options)
  val renderHeaderId:               Boolean                         = HtmlRenderer.RENDER_HEADER_ID.get(options)
  val generateHeaderIds:            Boolean                         = HtmlRenderer.GENERATE_HEADER_ID.get(options)
  val languageClassPrefix:          String                          = HtmlRenderer.FENCED_CODE_LANGUAGE_CLASS_PREFIX.get(options)
  val languageClassMap:             mutable.HashMap[String, String] = HtmlRenderer.FENCED_CODE_LANGUAGE_CLASS_MAP.get(options)
  val languageDelimiters:           String                          = HtmlRenderer.FENCED_CODE_LANGUAGE_DELIMITERS.get(options)
  val languageDelimiterSet:         CharPredicate                   = CharPredicate.anyOf(languageDelimiters)
  val noLanguageClass:              String                          = HtmlRenderer.FENCED_CODE_NO_LANGUAGE_CLASS.get(options)
  val sourcePositionAttribute:      String                          = HtmlRenderer.SOURCE_POSITION_ATTRIBUTE.get(options)
  val inlineCodeSpliceClass:        Nullable[String]                = Nullable(HtmlRenderer.INLINE_CODE_SPLICE_CLASS.get(options))
  val sourcePositionParagraphLines: Boolean                         = sourcePositionAttribute.nonEmpty && HtmlRenderer.SOURCE_POSITION_PARAGRAPH_LINES.get(options)
  val sourceWrapHtmlBlocks:         Boolean                         = sourcePositionAttribute.nonEmpty && HtmlRenderer.SOURCE_WRAP_HTML_BLOCKS.get(options)
  val formatFlags:                  Int                             = HtmlRenderer.FORMAT_FLAGS.get(options)
  val maxTrailingBlankLines:        Int                             = HtmlRenderer.MAX_TRAILING_BLANK_LINES.get(options)
  val maxBlankLines:                Int                             = HtmlRenderer.MAX_BLANK_LINES.get(options)
  val htmlBlockOpenTagEol:          Boolean                         = HtmlRenderer.HTML_BLOCK_OPEN_TAG_EOL.get(options)
  val htmlBlockCloseTagEol:         Boolean                         = HtmlRenderer.HTML_BLOCK_CLOSE_TAG_EOL.get(options)
  val unescapeHtmlEntities:         Boolean                         = HtmlRenderer.UNESCAPE_HTML_ENTITIES.get(options)
  val noPTagsUseBr:                 Boolean                         = HtmlRenderer.NO_P_TAGS_USE_BR.get(options)
  val autolinkWwwPrefix:            String                          = HtmlRenderer.AUTOLINK_WWW_PREFIX.get(options)

  val suppressedLinks: Nullable[Pattern] = {
    val ignoreLinks = HtmlRenderer.SUPPRESSED_LINKS.get(options)
    if (ignoreLinks.isEmpty) Nullable.empty else Nullable(Pattern.compile(ignoreLinks))
  }
  // wrapTightItemParagraphInSpan = HtmlRenderer.WRAP_TIGHT_ITEM_PARAGRAPH_IN_SPAN.getFrom(options);
}
