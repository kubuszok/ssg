/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/FormatterOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package formatter

import ssg.md.parser.{ Parser, ParserEmulationProfile }
import ssg.md.util.data.DataHolder
import ssg.md.util.format.CharWidthProvider
import ssg.md.util.format.options.*

import java.util.regex.Pattern
import scala.language.implicitConversions

/** Options for the Formatter, read from DataHolder keys.
  */
class FormatterOptions(options: DataHolder) {
  val emulationProfile:  ParserEmulationProfile = Formatter.FORMATTER_EMULATION_PROFILE.get(Nullable(options))
  val itemContentIndent: Boolean                = emulationProfile.family != ParserEmulationProfile.FIXED_INDENT

  val setextHeadingEqualizeMarker:     Boolean                = Formatter.SETEXT_HEADING_EQUALIZE_MARKER.get(Nullable(options))
  val formatFlags:                     Int                    = Formatter.FORMAT_FLAGS.get(Nullable(options))
  val maxBlankLines:                   Int                    = Formatter.MAX_BLANK_LINES.get(Nullable(options))
  val maxTrailingBlankLines:           Int                    = Formatter.MAX_TRAILING_BLANK_LINES.get(Nullable(options))
  val rightMargin:                     Int                    = Formatter.RIGHT_MARGIN.get(Nullable(options))
  val minSetextMarkerLength:           Int                    = Parser.HEADING_SETEXT_MARKER_LENGTH.get(Nullable(options))
  val spaceAfterAtxMarker:             DiscretionaryText      = Formatter.SPACE_AFTER_ATX_MARKER.get(Nullable(options))
  val atxHeadingTrailingMarker:        EqualizeTrailingMarker = Formatter.ATX_HEADING_TRAILING_MARKER.get(Nullable(options))
  val headingStyle:                    HeadingStyle           = Formatter.HEADING_STYLE.get(Nullable(options))
  val thematicBreak:                   Nullable[String]       = Nullable(Formatter.THEMATIC_BREAK.get(Nullable(options)))
  val translationIdFormat:             String                 = Formatter.TRANSLATION_ID_FORMAT.get(Nullable(options))
  val translationHtmlBlockPrefix:      String                 = Formatter.TRANSLATION_HTML_BLOCK_PREFIX.get(Nullable(options))
  val translationHtmlInlinePrefix:     String                 = Formatter.TRANSLATION_HTML_INLINE_PREFIX.get(Nullable(options))
  val translationAutolinkPrefix:       String                 = Formatter.TRANSLATION_AUTOLINK_PREFIX.get(Nullable(options))
  val translationExcludePattern:       String                 = Formatter.TRANSLATION_EXCLUDE_PATTERN.get(Nullable(options))
  val translationHtmlBlockTagPattern:  String                 = Formatter.TRANSLATION_HTML_BLOCK_TAG_PATTERN.get(Nullable(options))
  val translationHtmlInlineTagPattern: String                 = Formatter.TRANSLATION_HTML_INLINE_TAG_PATTERN.get(Nullable(options))
  val blockQuoteBlankLines:            Boolean                = Formatter.BLOCK_QUOTE_BLANK_LINES.get(Nullable(options))
  val blockQuoteMarkers:               BlockQuoteMarker       = Formatter.BLOCK_QUOTE_MARKERS.get(Nullable(options))
  val indentedCodeMinimizeIndent:      Boolean                = Formatter.INDENTED_CODE_MINIMIZE_INDENT.get(Nullable(options))
  val fencedCodeMinimizeIndent:        Boolean                = Formatter.FENCED_CODE_MINIMIZE_INDENT.get(Nullable(options))
  val fencedCodeMatchClosingMarker:    Boolean                = Formatter.FENCED_CODE_MATCH_CLOSING_MARKER.get(Nullable(options))
  val fencedCodeSpaceBeforeInfo:       Boolean                = Formatter.FENCED_CODE_SPACE_BEFORE_INFO.get(Nullable(options))
  val fencedCodeMarkerLength:          Int                    = Formatter.FENCED_CODE_MARKER_LENGTH.get(Nullable(options))
  val fencedCodeMarkerType:            CodeFenceMarker        = Formatter.FENCED_CODE_MARKER_TYPE.get(Nullable(options))
  val listAddBlankLineBefore:          Boolean                = Formatter.LIST_ADD_BLANK_LINE_BEFORE.get(Nullable(options))
  val listAlignNumeric:                ElementAlignment       = Formatter.LIST_ALIGN_NUMERIC.get(Nullable(options))
  val listResetFirstItemNumber:        Boolean                = Formatter.LIST_RESET_FIRST_ITEM_NUMBER.get(Nullable(options))
  val listRenumberItems:               Boolean                = Formatter.LIST_RENUMBER_ITEMS.get(Nullable(options))
  val listRemoveEmptyItems:            Boolean                = Formatter.LIST_REMOVE_EMPTY_ITEMS.get(Nullable(options))
  val listBulletMarker:                ListBulletMarker       = Formatter.LIST_BULLET_MARKER.get(Nullable(options))
  val listNumberedMarker:              ListNumberedMarker     = Formatter.LIST_NUMBERED_MARKER.get(Nullable(options))
  val listSpacing:                     ListSpacing            = Formatter.LIST_SPACING.get(Nullable(options))
  val listsItemContentAfterSuffix:     Boolean                = Formatter.LISTS_ITEM_CONTENT_AFTER_SUFFIX.get(Nullable(options))
  val referencePlacement:              ElementPlacement       = Formatter.REFERENCE_PLACEMENT.get(Nullable(options))
  val referenceSort:                   ElementPlacementSort   = Formatter.REFERENCE_SORT.get(Nullable(options))
  val keepImageLinksAtStart:           Boolean                = Formatter.KEEP_IMAGE_LINKS_AT_START.get(Nullable(options))
  val keepExplicitLinksAtStart:        Boolean                = Formatter.KEEP_EXPLICIT_LINKS_AT_START.get(Nullable(options))
  val charWidthProvider:               CharWidthProvider      = Formatter.FORMAT_CHAR_WIDTH_PROVIDER.get(Nullable(options))
  val keepHardLineBreaks:              Boolean                = Formatter.KEEP_HARD_LINE_BREAKS.get(Nullable(options))
  val keepSoftLineBreaks:              Boolean                = Formatter.KEEP_SOFT_LINE_BREAKS.get(Nullable(options))
  val formatterOnTag:                  String                 = Formatter.FORMATTER_ON_TAG.get(Nullable(options))
  val formatterOffTag:                 String                 = Formatter.FORMATTER_OFF_TAG.get(Nullable(options))
  val formatterTagsEnabled:            Boolean                = Formatter.FORMATTER_TAGS_ENABLED.get(Nullable(options))
  val formatterTagsAcceptRegexp:       Boolean                = Formatter.FORMATTER_TAGS_ACCEPT_REGEXP.get(Nullable(options))
  val linkMarkerCommentPattern:        Nullable[Pattern]      = Nullable(Formatter.LINK_MARKER_COMMENT_PATTERN.get(Nullable(options)))
  val appendTransferredReferences:     Boolean                = Formatter.APPEND_TRANSFERRED_REFERENCES.get(Nullable(options))
  val optimizedInlineRendering:        Boolean                = Formatter.OPTIMIZED_INLINE_RENDERING.get(Nullable(options))
  val applySpecialLeadInHandlers:      Boolean                = Formatter.APPLY_SPECIAL_LEAD_IN_HANDLERS.get(Nullable(options))
  val escapeSpecialCharsOnWrap:        Boolean                = Formatter.ESCAPE_SPECIAL_CHARS.get(Nullable(options))
  val escapeNumberedLeadInOnWrap:      Boolean                = Formatter.ESCAPE_NUMBERED_LEAD_IN.get(Nullable(options))
  val unescapeSpecialCharsOnWrap:      Boolean                = Formatter.UNESCAPE_SPECIAL_CHARS.get(Nullable(options))
  val blankLinesInAst:                 Boolean                = Parser.BLANK_LINES_IN_AST.get(Nullable(options))
}
