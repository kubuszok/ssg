/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/ParserEmulationProfile.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/ParserEmulationProfile.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser

import ssg.md.html.HtmlRenderer
import ssg.md.util.ast.KeepType
import ssg.md.util.data._

import scala.language.implicitConversions

enum ParserEmulationProfile(familyInit: Nullable[ParserEmulationProfile]) extends java.lang.Enum[ParserEmulationProfile] with MutableDataSetter {

  lazy val family: ParserEmulationProfile = familyInit.getOrElse(this)

  case COMMONMARK extends ParserEmulationProfile(Nullable.empty)
  case COMMONMARK_0_26 extends ParserEmulationProfile(Nullable(COMMONMARK))
  case COMMONMARK_0_27 extends ParserEmulationProfile(Nullable(COMMONMARK))
  case COMMONMARK_0_28 extends ParserEmulationProfile(Nullable(COMMONMARK))
  case COMMONMARK_0_29 extends ParserEmulationProfile(Nullable(COMMONMARK))
  case FIXED_INDENT extends ParserEmulationProfile(Nullable.empty)
  case KRAMDOWN extends ParserEmulationProfile(Nullable.empty)
  case MARKDOWN extends ParserEmulationProfile(Nullable.empty)
  case GITHUB_DOC extends ParserEmulationProfile(Nullable(MARKDOWN))
  case GITHUB extends ParserEmulationProfile(Nullable(COMMONMARK))
  case MULTI_MARKDOWN extends ParserEmulationProfile(Nullable(FIXED_INDENT))
  case PEGDOWN extends ParserEmulationProfile(Nullable(FIXED_INDENT))
  case PEGDOWN_STRICT extends ParserEmulationProfile(Nullable(FIXED_INDENT))

  def getProfileOptions: MutableDataHolder = {
    val options = MutableDataSet()
    setIn(options)
    options
  }

  def getOptions: MutableListOptions = getOptions(Nullable.empty)

  def getOptions(dataHolder: Nullable[DataHolder]): MutableListOptions = {
    if (family == FIXED_INDENT) {
      if (this == MULTI_MARKDOWN) {
        MutableListOptions()
          .setParserEmulationFamily(this)
          .setAutoLoose(true)
          .setAutoLooseOneLevelLists(true)
          .setDelimiterMismatchToNewList(false)
          .setCodeIndent(8)
          .setEndOnDoubleBlank(false)
          .setItemIndent(4)
          .setItemInterrupt(
            ListOptions
              .MutableItemInterrupt()
              .setBulletItemInterruptsParagraph(false)
              .setOrderedItemInterruptsParagraph(false)
              .setOrderedNonOneItemInterruptsParagraph(false)
              .setEmptyBulletItemInterruptsParagraph(false)
              .setEmptyOrderedItemInterruptsParagraph(false)
              .setEmptyOrderedNonOneItemInterruptsParagraph(false)
              .setBulletItemInterruptsItemParagraph(true)
              .setOrderedItemInterruptsItemParagraph(true)
              .setOrderedNonOneItemInterruptsItemParagraph(true)
              .setEmptyBulletItemInterruptsItemParagraph(true)
              .setEmptyOrderedItemInterruptsItemParagraph(true)
              .setEmptyOrderedNonOneItemInterruptsItemParagraph(true)
              .setEmptyBulletSubItemInterruptsItemParagraph(true)
              .setEmptyOrderedSubItemInterruptsItemParagraph(true)
              .setEmptyOrderedNonOneSubItemInterruptsItemParagraph(true)
          )
          .setItemMarkerSpace(false)
          .setItemTypeMismatchToNewList(false)
          .setItemTypeMismatchToSubList(false)
          .setLooseWhenBlankLineFollowsItemParagraph(true)
          .setLooseWhenHasTrailingBlankLine(false)
          .setLooseWhenHasNonListChildren(true)
          .setNewItemCodeIndent(Int.MaxValue)
          .setOrderedItemDotOnly(true)
          .setOrderedListManualStart(false)
      } else if (this == PEGDOWN || this == PEGDOWN_STRICT) {
        MutableListOptions()
          .setParserEmulationFamily(this)
          .setAutoLoose(false)
          .setAutoLooseOneLevelLists(false)
          .setLooseWhenBlankLineFollowsItemParagraph(false)
          .setLooseWhenHasLooseSubItem(false)
          .setLooseWhenHasTrailingBlankLine(false)
          .setLooseWhenPrevHasTrailingBlankLine(true)
          .setOrderedListManualStart(false)
          .setDelimiterMismatchToNewList(false)
          .setItemTypeMismatchToNewList(true)
          .setItemTypeMismatchToSubList(false)
          .setEndOnDoubleBlank(false)
          .setOrderedItemDotOnly(true)
          .setItemMarkerSpace(true)
          .setItemIndent(4)
          .setCodeIndent(8)
          .setNewItemCodeIndent(Int.MaxValue)
          .setItemInterrupt(
            ListOptions
              .MutableItemInterrupt()
              .setBulletItemInterruptsParagraph(false)
              .setOrderedItemInterruptsParagraph(false)
              .setOrderedNonOneItemInterruptsParagraph(false)
              .setEmptyBulletItemInterruptsParagraph(false)
              .setEmptyOrderedItemInterruptsParagraph(false)
              .setEmptyOrderedNonOneItemInterruptsParagraph(false)
              .setBulletItemInterruptsItemParagraph(true)
              .setOrderedItemInterruptsItemParagraph(true)
              .setOrderedNonOneItemInterruptsItemParagraph(true)
              .setEmptyBulletItemInterruptsItemParagraph(true)
              .setEmptyOrderedItemInterruptsItemParagraph(true)
              .setEmptyOrderedNonOneItemInterruptsItemParagraph(true)
              .setEmptyBulletSubItemInterruptsItemParagraph(true)
              .setEmptyOrderedSubItemInterruptsItemParagraph(true)
              .setEmptyOrderedNonOneSubItemInterruptsItemParagraph(true)
          )
      } else {
        // default FIXED_INDENT
        MutableListOptions()
          .setParserEmulationFamily(this)
          .setAutoLoose(false)
          .setAutoLooseOneLevelLists(false)
          .setLooseWhenBlankLineFollowsItemParagraph(false)
          .setLooseWhenHasLooseSubItem(false)
          .setLooseWhenHasTrailingBlankLine(true)
          .setLooseWhenPrevHasTrailingBlankLine(false)
          .setLooseWhenLastItemPrevHasTrailingBlankLine(true)
          .setOrderedListManualStart(false)
          .setDelimiterMismatchToNewList(false)
          .setItemTypeMismatchToNewList(false)
          .setItemTypeMismatchToSubList(false)
          .setEndOnDoubleBlank(false)
          .setOrderedItemDotOnly(true)
          .setItemMarkerSpace(true)
          .setItemIndent(4)
          .setCodeIndent(8)
          .setNewItemCodeIndent(Int.MaxValue)
          .setItemInterrupt(
            ListOptions
              .MutableItemInterrupt()
              .setBulletItemInterruptsParagraph(false)
              .setOrderedItemInterruptsParagraph(false)
              .setOrderedNonOneItemInterruptsParagraph(false)
              .setEmptyBulletItemInterruptsParagraph(false)
              .setEmptyOrderedItemInterruptsParagraph(false)
              .setEmptyOrderedNonOneItemInterruptsParagraph(false)
              .setBulletItemInterruptsItemParagraph(true)
              .setOrderedItemInterruptsItemParagraph(true)
              .setOrderedNonOneItemInterruptsItemParagraph(true)
              .setEmptyBulletItemInterruptsItemParagraph(true)
              .setEmptyOrderedItemInterruptsItemParagraph(true)
              .setEmptyOrderedNonOneItemInterruptsItemParagraph(true)
              .setEmptyBulletSubItemInterruptsItemParagraph(true)
              .setEmptyOrderedSubItemInterruptsItemParagraph(true)
              .setEmptyOrderedNonOneSubItemInterruptsItemParagraph(true)
          )
      }
    } else if (family == KRAMDOWN) {
      MutableListOptions()
        .setParserEmulationFamily(this)
        .setAutoLoose(false)
        .setLooseWhenBlankLineFollowsItemParagraph(true)
        .setLooseWhenHasLooseSubItem(false)
        .setLooseWhenHasTrailingBlankLine(false)
        .setLooseWhenPrevHasTrailingBlankLine(false)
        .setOrderedListManualStart(false)
        .setDelimiterMismatchToNewList(false)
        .setItemTypeMismatchToNewList(true)
        .setItemTypeMismatchToSubList(true)
        .setOrderedItemDotOnly(true)
        .setItemMarkerSpace(true)
        .setEndOnDoubleBlank(false)
        .setItemIndent(4)
        .setCodeIndent(8)
        .setNewItemCodeIndent(Int.MaxValue)
        .setItemInterrupt(
          ListOptions
            .MutableItemInterrupt()
            .setBulletItemInterruptsParagraph(false)
            .setOrderedItemInterruptsParagraph(false)
            .setOrderedNonOneItemInterruptsParagraph(false)
            .setEmptyBulletItemInterruptsParagraph(false)
            .setEmptyOrderedItemInterruptsParagraph(false)
            .setEmptyOrderedNonOneItemInterruptsParagraph(false)
            .setBulletItemInterruptsItemParagraph(true)
            .setOrderedItemInterruptsItemParagraph(true)
            .setOrderedNonOneItemInterruptsItemParagraph(true)
            .setEmptyBulletItemInterruptsItemParagraph(true)
            .setEmptyOrderedItemInterruptsItemParagraph(true)
            .setEmptyOrderedNonOneItemInterruptsItemParagraph(true)
            .setEmptyBulletSubItemInterruptsItemParagraph(false)
            .setEmptyOrderedSubItemInterruptsItemParagraph(false)
            .setEmptyOrderedNonOneSubItemInterruptsItemParagraph(false)
        )
    } else if (family == MARKDOWN) {
      if (this == GITHUB_DOC) {
        MutableListOptions()
          .setParserEmulationFamily(this)
          .setAutoLoose(false)
          .setLooseWhenBlankLineFollowsItemParagraph(true)
          .setLooseWhenHasLooseSubItem(true)
          .setLooseWhenHasTrailingBlankLine(true)
          .setLooseWhenPrevHasTrailingBlankLine(true)
          .setLooseWhenContainsBlankLine(false)
          .setLooseWhenHasNonListChildren(true)
          .setOrderedListManualStart(false)
          .setDelimiterMismatchToNewList(false)
          .setItemTypeMismatchToNewList(false)
          .setItemTypeMismatchToSubList(false)
          .setEndOnDoubleBlank(false)
          .setOrderedItemDotOnly(true)
          .setItemMarkerSpace(true)
          .setItemIndent(4)
          .setCodeIndent(8)
          .setNewItemCodeIndent(Int.MaxValue)
          .setItemInterrupt(
            ListOptions
              .MutableItemInterrupt()
              .setBulletItemInterruptsParagraph(true)
              .setOrderedItemInterruptsParagraph(false)
              .setOrderedNonOneItemInterruptsParagraph(false)
              .setEmptyBulletItemInterruptsParagraph(true)
              .setEmptyOrderedItemInterruptsParagraph(false)
              .setEmptyOrderedNonOneItemInterruptsParagraph(false)
              .setBulletItemInterruptsItemParagraph(true)
              .setOrderedItemInterruptsItemParagraph(true)
              .setOrderedNonOneItemInterruptsItemParagraph(true)
              .setEmptyBulletItemInterruptsItemParagraph(true)
              .setEmptyOrderedItemInterruptsItemParagraph(true)
              .setEmptyOrderedNonOneItemInterruptsItemParagraph(true)
              .setEmptyBulletSubItemInterruptsItemParagraph(true)
              .setEmptyOrderedSubItemInterruptsItemParagraph(true)
              .setEmptyOrderedNonOneSubItemInterruptsItemParagraph(true)
          )
      } else {
        // default MARKDOWN
        MutableListOptions()
          .setParserEmulationFamily(this)
          .setAutoLoose(false)
          .setLooseWhenBlankLineFollowsItemParagraph(true)
          .setLooseWhenHasLooseSubItem(true)
          .setLooseWhenHasTrailingBlankLine(true)
          .setLooseWhenPrevHasTrailingBlankLine(true)
          .setLooseWhenContainsBlankLine(true)
          .setOrderedListManualStart(false)
          .setDelimiterMismatchToNewList(false)
          .setItemTypeMismatchToNewList(false)
          .setItemTypeMismatchToSubList(false)
          .setEndOnDoubleBlank(false)
          .setOrderedItemDotOnly(true)
          .setItemMarkerSpace(true)
          .setItemIndent(4)
          .setCodeIndent(8)
          .setNewItemCodeIndent(Int.MaxValue)
          .setItemInterrupt(
            ListOptions
              .MutableItemInterrupt()
              .setBulletItemInterruptsParagraph(false)
              .setOrderedItemInterruptsParagraph(false)
              .setOrderedNonOneItemInterruptsParagraph(false)
              .setEmptyBulletItemInterruptsParagraph(false)
              .setEmptyOrderedItemInterruptsParagraph(false)
              .setEmptyOrderedNonOneItemInterruptsParagraph(false)
              .setBulletItemInterruptsItemParagraph(true)
              .setOrderedItemInterruptsItemParagraph(true)
              .setOrderedNonOneItemInterruptsItemParagraph(true)
              .setEmptyBulletItemInterruptsItemParagraph(false)
              .setEmptyOrderedItemInterruptsItemParagraph(false)
              .setEmptyOrderedNonOneItemInterruptsItemParagraph(false)
              .setEmptyBulletSubItemInterruptsItemParagraph(true)
              .setEmptyOrderedSubItemInterruptsItemParagraph(true)
              .setEmptyOrderedNonOneSubItemInterruptsItemParagraph(true)
          )
      }
    } else if (family == COMMONMARK) {
      if (this == COMMONMARK_0_26) {
        MutableListOptions(Nullable.empty[DataHolder]).setEndOnDoubleBlank(true)
      } else {
        // default CommonMark
        MutableListOptions(Nullable.empty[DataHolder])
      }
    } else {
      // default CommonMark
      MutableListOptions(Nullable.empty[DataHolder])
    }
  }

  override def setIn(dataHolder: MutableDataHolder): MutableDataHolder = {
    if (this == FIXED_INDENT) {
      getOptions(Nullable(dataHolder)).setIn(dataHolder).set(Parser.STRONG_WRAPS_EMPHASIS, true).set(Parser.LINKS_ALLOW_MATCHED_PARENTHESES, false)
    } else if (this == KRAMDOWN) {
      getOptions(Nullable(dataHolder)).setIn(dataHolder)
      dataHolder
        .set(Parser.HEADING_NO_LEAD_SPACE, true)
        .set(Parser.BLOCK_QUOTE_INTERRUPTS_PARAGRAPH, false)
        .set(HtmlRenderer.RENDER_HEADER_ID, true)
        .set(HtmlRenderer.SOFT_BREAK, " ")
        .set(Parser.HTML_BLOCK_DEEP_PARSER, true)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_NON_BLOCK, true)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_FIRST_OPEN_TAG_ON_ONE_LINE, false)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS, false)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_MARKDOWN_INTERRUPTS_CLOSED, true)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS_PARTIAL_TAG, false)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_INDENTED_CODE_INTERRUPTS, true)
        .set(Parser.STRONG_WRAPS_EMPHASIS, true)
        .set(Parser.LINKS_ALLOW_MATCHED_PARENTHESES, false)
    } else if (this == MARKDOWN) {
      getOptions(Nullable(dataHolder)).setIn(dataHolder)
      dataHolder
        .set(Parser.HEADING_NO_LEAD_SPACE, true)
        .set(Parser.BLOCK_QUOTE_IGNORE_BLANK_LINE, true)
        .set(HtmlRenderer.SOFT_BREAK, " ")
        .set(Parser.HTML_BLOCK_DEEP_PARSER, true)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_NON_BLOCK, true)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_FIRST_OPEN_TAG_ON_ONE_LINE, false)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS, false)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_MARKDOWN_INTERRUPTS_CLOSED, true)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS_PARTIAL_TAG, false)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_INDENTED_CODE_INTERRUPTS, true)
        .set(Parser.STRONG_WRAPS_EMPHASIS, true)
        .set(Parser.LINKS_ALLOW_MATCHED_PARENTHESES, false)
    } else if (this == GITHUB_DOC) {
      getOptions(Nullable(dataHolder)).setIn(dataHolder)
      dataHolder
        .set(Parser.BLOCK_QUOTE_IGNORE_BLANK_LINE, true)
        .set(Parser.BLOCK_QUOTE_INTERRUPTS_PARAGRAPH, true)
        .set(Parser.BLOCK_QUOTE_INTERRUPTS_ITEM_PARAGRAPH, false)
        .set(Parser.HEADING_NO_LEAD_SPACE, true)
        .set(Parser.HTML_BLOCK_DEEP_PARSER, true)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_NON_BLOCK, true)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_FIRST_OPEN_TAG_ON_ONE_LINE, false)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS, true)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_MARKDOWN_INTERRUPTS_CLOSED, true)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS_PARTIAL_TAG, false)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_INDENTED_CODE_INTERRUPTS, false)
        .set(Parser.STRONG_WRAPS_EMPHASIS, true)
        .set(Parser.LINKS_ALLOW_MATCHED_PARENTHESES, false)
        .set(HtmlRenderer.HEADER_ID_GENERATOR_TO_DASH_CHARS, " -")
        .set(HtmlRenderer.HEADER_ID_GENERATOR_NON_DASH_CHARS, "_")
        .set(HtmlRenderer.HEADER_ID_GENERATOR_NON_ASCII_TO_LOWERCASE, false)
    } else if (this == GITHUB) {
      getOptions(Nullable(dataHolder)).setIn(dataHolder)
      dataHolder
        .set(HtmlRenderer.HEADER_ID_GENERATOR_TO_DASH_CHARS, " -")
        .set(HtmlRenderer.HEADER_ID_GENERATOR_NON_DASH_CHARS, "_")
        .set(HtmlRenderer.HEADER_ID_GENERATOR_NON_ASCII_TO_LOWERCASE, false)
        .set(HtmlRenderer.HEADER_ID_REF_TEXT_TRIM_TRAILING_SPACES, false)
        .set(HtmlRenderer.HEADER_ID_ADD_EMOJI_SHORTCUT, true)
    } else if (this == MULTI_MARKDOWN) {
      getOptions(Nullable(dataHolder)).setIn(dataHolder)
      dataHolder
        .set(Parser.BLOCK_QUOTE_IGNORE_BLANK_LINE, true)
        .set(Parser.BLOCK_QUOTE_WITH_LEAD_SPACES_INTERRUPTS_ITEM_PARAGRAPH, false)
        .set(HtmlRenderer.RENDER_HEADER_ID, true)
        .set(HtmlRenderer.HEADER_ID_GENERATOR_RESOLVE_DUPES, false)
        .set(HtmlRenderer.HEADER_ID_GENERATOR_TO_DASH_CHARS, "")
        .set(HtmlRenderer.HEADER_ID_GENERATOR_NO_DUPED_DASHES, true)
        .set(HtmlRenderer.SOFT_BREAK, " ")
        .set(Parser.HTML_BLOCK_DEEP_PARSER, true)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_NON_BLOCK, true)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_FIRST_OPEN_TAG_ON_ONE_LINE, false)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS, false)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_MARKDOWN_INTERRUPTS_CLOSED, true)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS_PARTIAL_TAG, false)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_INDENTED_CODE_INTERRUPTS, true)
        .set(Parser.STRONG_WRAPS_EMPHASIS, true)
        .set(Parser.LINKS_ALLOW_MATCHED_PARENTHESES, false)
    } else if (this == PEGDOWN || this == PEGDOWN_STRICT) {
      val pegdownExtensions = ParserEmulationProfile.PEGDOWN_EXTENSIONS.get(dataHolder)
      getOptions(Nullable(dataHolder)).setIn(dataHolder)
      dataHolder
        .set(Parser.BLOCK_QUOTE_EXTEND_TO_BLANK_LINE, true)
        .set(Parser.BLOCK_QUOTE_IGNORE_BLANK_LINE, true)
        .set(Parser.BLOCK_QUOTE_ALLOW_LEADING_SPACE, false)
        .set(Parser.INDENTED_CODE_NO_TRAILING_BLANK_LINES, true)
        .set(Parser.HEADING_SETEXT_MARKER_LENGTH, 3)
        .set(Parser.HEADING_NO_LEAD_SPACE, true)
        .set(Parser.REFERENCES_KEEP, KeepType.LAST)
        .set(Parser.PARSE_INNER_HTML_COMMENTS, true)
        .set(Parser.SPACE_IN_LINK_ELEMENTS, true)
        .set(HtmlRenderer.OBFUSCATE_EMAIL, true)
        .set(HtmlRenderer.GENERATE_HEADER_ID, true)
        .set(HtmlRenderer.HEADER_ID_GENERATOR_NO_DUPED_DASHES, true)
        .set(HtmlRenderer.HEADER_ID_GENERATOR_RESOLVE_DUPES, false)
        .set(HtmlRenderer.SOFT_BREAK, " ")
        .set(Parser.STRONG_WRAPS_EMPHASIS, true)
        .set(Parser.LINKS_ALLOW_MATCHED_PARENTHESES, false)

      if (ParserEmulationProfile.haveAny(pegdownExtensions, PegdownExtensions.ANCHORLINKS)) {
        dataHolder.set(HtmlRenderer.RENDER_HEADER_ID, false)
      }

      if (this == PEGDOWN_STRICT) {
        dataHolder
          .set(Parser.HTML_BLOCK_DEEP_PARSER, true)
          .set(Parser.HTML_BLOCK_DEEP_PARSE_NON_BLOCK, true)
          .set(Parser.HTML_BLOCK_DEEP_PARSE_FIRST_OPEN_TAG_ON_ONE_LINE, false)
          .set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS, false)
          .set(Parser.HTML_BLOCK_DEEP_PARSE_MARKDOWN_INTERRUPTS_CLOSED, true)
          .set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS_PARTIAL_TAG, false)
          .set(Parser.HTML_BLOCK_DEEP_PARSE_INDENTED_CODE_INTERRUPTS, false)
      } else {
        dataHolder
          .set(Parser.HTML_BLOCK_DEEP_PARSER, true)
          .set(Parser.HTML_BLOCK_DEEP_PARSE_NON_BLOCK, true)
          .set(Parser.HTML_BLOCK_DEEP_PARSE_FIRST_OPEN_TAG_ON_ONE_LINE, false)
          .set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS, true)
          .set(Parser.HTML_BLOCK_DEEP_PARSE_MARKDOWN_INTERRUPTS_CLOSED, true)
          .set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS_PARTIAL_TAG, false)
          .set(Parser.HTML_BLOCK_DEEP_PARSE_INDENTED_CODE_INTERRUPTS, false)
      }
    } else if (this == COMMONMARK_0_26 || this == COMMONMARK_0_27) {
      dataHolder.set(Parser.STRONG_WRAPS_EMPHASIS, true)
      dataHolder.set(Parser.LINKS_ALLOW_MATCHED_PARENTHESES, false)
    } else {
      // COMMONMARK_0_28 and others - no special options
    }

    dataHolder
  }
}

object ParserEmulationProfile {

  /** Key used to hold user pegdown extension selection */
  val PEGDOWN_EXTENSIONS: DataKey[Int] = DataKey("PEGDOWN_EXTENSIONS", PegdownExtensions.ALL)

  def haveAny(extensions: Int, mask: Int): Boolean = (extensions & mask) != 0

  def haveAll(extensions: Int, mask: Int): Boolean = (extensions & mask) == mask
}
