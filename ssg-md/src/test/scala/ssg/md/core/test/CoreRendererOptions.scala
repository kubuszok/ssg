/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../renderer/CoreRendererSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Shared options and options map from CoreRendererSpecTest,
 * used by all core renderer spec test suites.
 */
package ssg
package md
package core
package test

import ssg.md.Nullable
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.ast.KeepType
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.{ util => ju }
import scala.collection.mutable
import scala.language.implicitConversions

/** Base options and named option entries from CoreRendererSpecTest.
  *
  * In the original Java code, CoreRendererSpecTest defines:
  *   - A base OPTIONS with OBFUSCATE_EMAIL_RANDOM=false
  *   - A large optionsMap with named option sets
  *
  * All Combo*SpecTest subclasses inherit these.
  */
object CoreRendererOptions {

  /** Base options: OBFUSCATE_EMAIL_RANDOM=false (matches original CoreRendererSpecTest). */
  val BASE_OPTIONS: DataHolder = new MutableDataSet().set(HtmlRenderer.OBFUSCATE_EMAIL_RANDOM, false).toImmutable

  /** Named option entries from CoreRendererSpecTest.optionsMap.
    *
    * These are merged with each test suite's own optionsMap. The RendererSpecTestSuite.optionsFor() method already merges BASE_OPTIONS_MAP (containing "src-pos") with the suite's optionsMap. This
    * object provides the full CoreRendererSpecTest optionsMap entries.
    */
  val OPTIONS_MAP: ju.Map[String, DataHolder] = {
    val map = new ju.HashMap[String, DataHolder]()

    val userTags = Parser.HTML_BLOCK_TAGS.get(Nullable.empty) :+ "tag"

    map.put("heading-ids", new MutableDataSet().set(HtmlRenderer.RENDER_HEADER_ID, true))
    map.put("heading-ids-no-dupe-dashes", new MutableDataSet().set(HtmlRenderer.HEADER_ID_GENERATOR_NO_DUPED_DASHES, true))
    map.put(
      "heading-ids-no-non-ascii-lowercase",
      new MutableDataSet().set(HtmlRenderer.HEADER_ID_GENERATOR_NON_ASCII_TO_LOWERCASE, false)
    )
    map.put("heading-ids-no-trim-start", new MutableDataSet().set(HtmlRenderer.HEADER_ID_REF_TEXT_TRIM_LEADING_SPACES, false))
    map.put("heading-ids-no-trim-end", new MutableDataSet().set(HtmlRenderer.HEADER_ID_REF_TEXT_TRIM_TRAILING_SPACES, false))
    map.put("heading-ids-emoji", new MutableDataSet().set(HtmlRenderer.HEADER_ID_ADD_EMOJI_SHORTCUT, true))
    map.put(
      "heading-ids-github",
      new MutableDataSet()
        .set(HtmlRenderer.HEADER_ID_GENERATOR_TO_DASH_CHARS, " -")
        .set(HtmlRenderer.HEADER_ID_GENERATOR_NON_DASH_CHARS, "_")
        .set(HtmlRenderer.HEADER_ID_GENERATOR_NON_ASCII_TO_LOWERCASE, false)
        // GitHub does not trim trailing spaces of Ref Links in Headings for ID generation
        .set(HtmlRenderer.HEADER_ID_REF_TEXT_TRIM_TRAILING_SPACES, false)
        // GitHub adds emoji shortcut text to heading id
        .set(HtmlRenderer.HEADER_ID_ADD_EMOJI_SHORTCUT, true)
    )

    map.put("user-block-tags", new MutableDataSet().set(Parser.HTML_BLOCK_TAGS, userTags))
    map.put("obfuscate-email", new MutableDataSet().set(HtmlRenderer.OBFUSCATE_EMAIL, true))
    map.put("keep-blank-lines", new MutableDataSet().set(Parser.BLANK_LINES_IN_AST, true))
    map.put("keep-last", new MutableDataSet().set(Parser.REFERENCES_KEEP, KeepType.LAST))
    map.put("jekyll-macros-in-urls", new MutableDataSet().set(Parser.PARSE_JEKYLL_MACROS_IN_URLS, true))
    map.put("hdr-no-atx-space", new MutableDataSet().set(Parser.HEADING_NO_ATX_SPACE, true))
    map.put("no-empty-heading-without-space", new MutableDataSet().set(Parser.HEADING_NO_EMPTY_HEADING_WITHOUT_SPACE, true))
    map.put("hdr-no-lead-space", new MutableDataSet().set(Parser.HEADING_NO_LEAD_SPACE, true))
    map.put("list-no-break", new MutableDataSet().set(Parser.LISTS_END_ON_DOUBLE_BLANK, false))
    map.put("list-break", new MutableDataSet().set(Parser.LISTS_END_ON_DOUBLE_BLANK, true))
    map.put("list-no-loose", new MutableDataSet().set(Parser.LISTS_AUTO_LOOSE, false))
    map.put("list-loose-if-prev", new MutableDataSet().set(Parser.LISTS_LOOSE_WHEN_PREV_HAS_TRAILING_BLANK_LINE, true))
    map.put("list-no-start", new MutableDataSet().set(Parser.LISTS_ORDERED_LIST_MANUAL_START, false))
    map.put("list-no-bullet-match", new MutableDataSet().set(Parser.LISTS_DELIMITER_MISMATCH_TO_NEW_LIST, false))
    map.put("list-no-type-match", new MutableDataSet().set(Parser.LISTS_ITEM_TYPE_MISMATCH_TO_NEW_LIST, false))
    map.put("bullet-no-para-break", new MutableDataSet().set(Parser.LISTS_BULLET_ITEM_INTERRUPTS_PARAGRAPH, false))
    map.put("bullet-no-item-break", new MutableDataSet().set(Parser.LISTS_BULLET_ITEM_INTERRUPTS_ITEM_PARAGRAPH, false))
    map.put("empty-bullet-item-break", new MutableDataSet().set(Parser.LISTS_EMPTY_BULLET_ITEM_INTERRUPTS_ITEM_PARAGRAPH, true))
    map.put(
      "empty-bullet-no-sub-item-break",
      new MutableDataSet().set(Parser.LISTS_EMPTY_BULLET_SUB_ITEM_INTERRUPTS_ITEM_PARAGRAPH, false)
    )
    map.put(
      "empty-bullet-sub-item-break",
      new MutableDataSet().set(Parser.LISTS_EMPTY_BULLET_SUB_ITEM_INTERRUPTS_ITEM_PARAGRAPH, true)
    )
    map.put("ordered-no-para-break", new MutableDataSet().set(Parser.LISTS_ORDERED_ITEM_INTERRUPTS_PARAGRAPH, false))
    map.put("ordered-non-1-para-break", new MutableDataSet().set(Parser.LISTS_ORDERED_NON_ONE_ITEM_INTERRUPTS_PARAGRAPH, true))
    map.put("ordered-no-non-1-para-break", new MutableDataSet().set(Parser.LISTS_ORDERED_NON_ONE_ITEM_INTERRUPTS_PARAGRAPH, false))
    map.put("ordered-no-item-break", new MutableDataSet().set(Parser.LISTS_ORDERED_ITEM_INTERRUPTS_ITEM_PARAGRAPH, false))
    map.put("ordered-non-1-item-break", new MutableDataSet().set(Parser.LISTS_ORDERED_NON_ONE_ITEM_INTERRUPTS_ITEM_PARAGRAPH, true))
    map.put(
      "ordered-no-non-1-item-break",
      new MutableDataSet().set(Parser.LISTS_ORDERED_NON_ONE_ITEM_INTERRUPTS_ITEM_PARAGRAPH, false)
    )
    map.put("list-item-mismatch-to-subitem", new MutableDataSet().set(Parser.LISTS_ITEM_TYPE_MISMATCH_TO_SUB_LIST, true))
    map.put("thematic-break-no-relaxed-start", new MutableDataSet().set(Parser.THEMATIC_BREAK_RELAXED_START, false))
    map.put(
      "test-completions",
      new MutableDataSet().set(Parser.THEMATIC_BREAK_RELAXED_START, false).set(HtmlRenderer.SUPPRESS_INLINE_HTML, true)
    )
    map.put("escape-html", new MutableDataSet().set(HtmlRenderer.ESCAPE_HTML, true))
    map.put("escape-html-blocks", new MutableDataSet().set(HtmlRenderer.ESCAPE_HTML_BLOCKS, true))
    map.put("escape-html-comment-blocks", new MutableDataSet().set(HtmlRenderer.ESCAPE_HTML_COMMENT_BLOCKS, true))
    map.put("escape-inline-html", new MutableDataSet().set(HtmlRenderer.ESCAPE_INLINE_HTML, true))
    map.put("escape-inline-html-comments", new MutableDataSet().set(HtmlRenderer.ESCAPE_INLINE_HTML_COMMENTS, true))
    map.put("suppress-html", new MutableDataSet().set(HtmlRenderer.SUPPRESS_HTML, true))
    map.put("suppress-html-blocks", new MutableDataSet().set(HtmlRenderer.SUPPRESS_HTML_BLOCKS, true))
    map.put("suppress-html-comment-blocks", new MutableDataSet().set(HtmlRenderer.SUPPRESS_HTML_COMMENT_BLOCKS, true))
    map.put("suppress-inline-html", new MutableDataSet().set(HtmlRenderer.SUPPRESS_INLINE_HTML, true))
    map.put("suppress-inline-html-comments", new MutableDataSet().set(HtmlRenderer.SUPPRESS_INLINE_HTML_COMMENTS, true))
    map.put("no-class-prefix", new MutableDataSet().set(HtmlRenderer.FENCED_CODE_LANGUAGE_CLASS_PREFIX, ""))

    val classMap = mutable.HashMap[String, String]("latex" -> "math")
    map.put("class-map-latex", new MutableDataSet().set(HtmlRenderer.FENCED_CODE_LANGUAGE_CLASS_MAP, classMap))

    map.put("no-language-class", new MutableDataSet().set(HtmlRenderer.FENCED_CODE_NO_LANGUAGE_CLASS, "nohighlight"))
    map.put("parse-inner-comments", new MutableDataSet().set(Parser.PARSE_INNER_HTML_COMMENTS, true))
    map.put("multi-line-image-url", new MutableDataSet().set(Parser.PARSE_MULTI_LINE_IMAGE_URLS, true))
    map.put("unmatched-fence", new MutableDataSet().set(Parser.MATCH_CLOSING_FENCE_CHARACTERS, false))
    map.put("dummy-identifier", new MutableDataSet().set(Parser.INTELLIJ_DUMMY_IDENTIFIER, true))
    map.put("code-no-trim-trailing", new MutableDataSet().set(Parser.INDENTED_CODE_NO_TRAILING_BLANK_LINES, false))
    map.put("ordered-dot-only", new MutableDataSet().set(Parser.LISTS_ORDERED_ITEM_DOT_ONLY, true))
    map.put("block-quote-extend", new MutableDataSet().set(Parser.BLOCK_QUOTE_EXTEND_TO_BLANK_LINE, true))
    map.put("block-ignore-blank", new MutableDataSet().set(Parser.BLOCK_QUOTE_IGNORE_BLANK_LINE, true))
    map.put("html-block-start-only-on-block-tags", new MutableDataSet().set(Parser.HTML_BLOCK_START_ONLY_ON_BLOCK_TAGS, true))
    map.put("no-html-block-start-only-on-block-tags", new MutableDataSet().set(Parser.HTML_BLOCK_START_ONLY_ON_BLOCK_TAGS, false))
    map.put("setext-marker-length", new MutableDataSet().set(Parser.HEADING_SETEXT_MARKER_LENGTH, 3))
    map.put(
      "src-pos-lines",
      new MutableDataSet().set(HtmlRenderer.SOURCE_POSITION_PARAGRAPH_LINES, true).set(Parser.CODE_SOFT_LINE_BREAKS, true)
    )
    map.put(
      "src-pos-lines-splice",
      new MutableDataSet().set(HtmlRenderer.SOURCE_POSITION_PARAGRAPH_LINES, true).set(Parser.CODE_SOFT_LINE_BREAKS, true).set(HtmlRenderer.INLINE_CODE_SPLICE_CLASS, "line-spliced")
    )
    map.put("src-wrap-html", new MutableDataSet().set(HtmlRenderer.SOURCE_WRAP_HTML, true))
    map.put("src-wrap-blocks", new MutableDataSet().set(HtmlRenderer.SOURCE_WRAP_HTML_BLOCKS, true))
    map.put("hard-line-break-limit", new MutableDataSet().set(Parser.HARD_LINE_BREAK_LIMIT, true))
    map.put("code-content-block", new MutableDataSet().set(Parser.FENCED_CODE_CONTENT_BLOCK, true))
    map.put(
      "style-strong-emphasis",
      new MutableDataSet().set(HtmlRenderer.STRONG_EMPHASIS_STYLE_HTML_OPEN, "<span class=\"text-bold\">").set(HtmlRenderer.STRONG_EMPHASIS_STYLE_HTML_CLOSE, "</span>")
    )
    map.put(
      "style-emphasis",
      new MutableDataSet().set(HtmlRenderer.EMPHASIS_STYLE_HTML_OPEN, "<span class=\"text-italic\">").set(HtmlRenderer.EMPHASIS_STYLE_HTML_CLOSE, "</span>")
    )
    map.put(
      "style-code",
      new MutableDataSet().set(HtmlRenderer.CODE_STYLE_HTML_OPEN, "<span class=\"text-code\">").set(HtmlRenderer.CODE_STYLE_HTML_CLOSE, "</span>")
    )
    map.put("url-spaces", new MutableDataSet().set(Parser.SPACE_IN_LINK_URLS, true))
    map.put("url-jekyll-macros", new MutableDataSet().set(Parser.PARSE_JEKYLL_MACROS_IN_URLS, true))
    map.put(
      "suppress-format-eol",
      new MutableDataSet().set(HtmlRenderer.HTML_BLOCK_OPEN_TAG_EOL, false).set(HtmlRenderer.HTML_BLOCK_CLOSE_TAG_EOL, false).set(HtmlRenderer.INDENT_SIZE, 0)
    )
    map.put("no-unescape-entities", new MutableDataSet().set(HtmlRenderer.UNESCAPE_HTML_ENTITIES, false))
    map.put("space-in-link-elements", new MutableDataSet().set(Parser.SPACE_IN_LINK_ELEMENTS, true))
    map.put("www-auto-link-element", new MutableDataSet().set(Parser.WWW_AUTO_LINK_ELEMENT, true))
    map.put("directional-punctuation", new MutableDataSet().set(Parser.INLINE_DELIMITER_DIRECTIONAL_PUNCTUATIONS, true))
    map.put("deep-html-parsing", new MutableDataSet().set(Parser.HTML_BLOCK_DEEP_PARSER, true))
    map.put(
      "code-soft-breaks",
      new MutableDataSet().set(Parser.CODE_SOFT_LINE_BREAKS, true).set(HtmlRenderer.SOFT_BREAK, "\n")
    )
    map.put(
      "code-soft-break-spaces",
      new MutableDataSet().set(Parser.CODE_SOFT_LINE_BREAKS, true).set(HtmlRenderer.SOFT_BREAK, " \t")
    )
    map.put("spec-027", new MutableDataSet().set(Parser.STRONG_WRAPS_EMPHASIS, true))
    map.put("custom-list-marker", new MutableDataSet().set(Parser.LISTS_ITEM_PREFIX_CHARS, "*/"))
    map.put("no-p-tags", new MutableDataSet().set(HtmlRenderer.NO_P_TAGS_USE_BR, true))
    map.put("allow-name-space", new MutableDataSet().set(Parser.HTML_ALLOW_NAME_SPACE, true))
    map.put(
      "list-markdown-navigator",
      new MutableDataSet()
        .set(Parser.LISTS_AUTO_LOOSE, false)
        .set(Parser.LISTS_AUTO_LOOSE, false)
        .set(Parser.LISTS_DELIMITER_MISMATCH_TO_NEW_LIST, false)
        .set(Parser.LISTS_ITEM_TYPE_MISMATCH_TO_NEW_LIST, false)
        .set(Parser.LISTS_ITEM_TYPE_MISMATCH_TO_SUB_LIST, false)
        .set(Parser.LISTS_END_ON_DOUBLE_BLANK, false)
        .set(Parser.LISTS_ORDERED_ITEM_DOT_ONLY, true)
        .set(Parser.LISTS_ORDERED_LIST_MANUAL_START, false)
        .set(Parser.LISTS_BULLET_ITEM_INTERRUPTS_PARAGRAPH, true)
        .set(Parser.LISTS_ORDERED_ITEM_INTERRUPTS_PARAGRAPH, true)
        .set(Parser.LISTS_ORDERED_NON_ONE_ITEM_INTERRUPTS_PARAGRAPH, true)
        .set(Parser.LISTS_EMPTY_BULLET_ITEM_INTERRUPTS_PARAGRAPH, true)
        .set(Parser.LISTS_EMPTY_ORDERED_ITEM_INTERRUPTS_PARAGRAPH, true)
        .set(Parser.LISTS_EMPTY_ORDERED_NON_ONE_ITEM_INTERRUPTS_PARAGRAPH, true)
        .set(Parser.LISTS_BULLET_ITEM_INTERRUPTS_ITEM_PARAGRAPH, true)
        .set(Parser.LISTS_ORDERED_ITEM_INTERRUPTS_ITEM_PARAGRAPH, true)
        .set(Parser.LISTS_ORDERED_NON_ONE_ITEM_INTERRUPTS_ITEM_PARAGRAPH, true)
        .set(Parser.LISTS_EMPTY_BULLET_ITEM_INTERRUPTS_ITEM_PARAGRAPH, true)
        .set(Parser.LISTS_EMPTY_ORDERED_ITEM_INTERRUPTS_ITEM_PARAGRAPH, true)
        .set(Parser.LISTS_EMPTY_ORDERED_NON_ONE_ITEM_INTERRUPTS_ITEM_PARAGRAPH, true)
        .set(Parser.LISTS_EMPTY_BULLET_SUB_ITEM_INTERRUPTS_ITEM_PARAGRAPH, true)
        .set(Parser.LISTS_EMPTY_ORDERED_SUB_ITEM_INTERRUPTS_ITEM_PARAGRAPH, true)
        .set(Parser.LISTS_EMPTY_ORDERED_NON_ONE_SUB_ITEM_INTERRUPTS_ITEM_PARAGRAPH, true)
    )
    map
  }
}
