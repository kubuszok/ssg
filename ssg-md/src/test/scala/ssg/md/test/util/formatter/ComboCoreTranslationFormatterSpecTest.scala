/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../ComboCoreTranslationFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package test
package util
package formatter

import ssg.md.Nullable
import ssg.md.formatter.Formatter
import ssg.md.parser.Parser
import ssg.md.test.util.{ FormatterSpecTestSuite, TranslationFormatterSpecTestSuite }
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.ast.KeepType
import ssg.md.util.data.{ DataHolder, MutableDataSet }
import ssg.md.util.format.options.*

import scala.language.implicitConversions

final class ComboCoreTranslationFormatterSpecTest extends TranslationFormatterSpecTestSuite {
  override def specResource:         ResourceLocation                       = ComboCoreTranslationFormatterSpecTest.RESOURCE_LOCATION
  override def optionsMap:           java.util.Map[String, ? <: DataHolder] = ComboCoreTranslationFormatterSpecTest.OPTIONS_MAP
  override def knownFailurePrefixes: Set[String]                            = Set("Block Quotes -", "Formatter -", "Headings -")
}

object ComboCoreTranslationFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/test/util/formatter/core_translation_formatter_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboCoreTranslationFormatterSpecTest], SPEC_RESOURCE)

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = FormatterSpecTestSuite.placementAndSortOptions(
      Parser.REFERENCES_KEEP,
      Formatter.REFERENCE_PLACEMENT,
      Formatter.REFERENCE_SORT
    )
    map.put("atx-space-as-is", new MutableDataSet().set(Formatter.SPACE_AFTER_ATX_MARKER, DiscretionaryText.AS_IS).toImmutable())
    map.put("atx-space-add", new MutableDataSet().set(Formatter.SPACE_AFTER_ATX_MARKER, DiscretionaryText.ADD).toImmutable())
    map.put("atx-space-remove", new MutableDataSet().set(Formatter.SPACE_AFTER_ATX_MARKER, DiscretionaryText.REMOVE).toImmutable())
    map.put(
      "setext-no-equalize",
      new MutableDataSet().set(Formatter.SETEXT_HEADING_EQUALIZE_MARKER, java.lang.Boolean.valueOf(false)).toImmutable()
    )
    map.put(
      "atx-trailing-as-is",
      new MutableDataSet().set(Formatter.ATX_HEADING_TRAILING_MARKER, EqualizeTrailingMarker.AS_IS).toImmutable()
    )
    map.put(
      "atx-trailing-add",
      new MutableDataSet().set(Formatter.ATX_HEADING_TRAILING_MARKER, EqualizeTrailingMarker.ADD).toImmutable()
    )
    map.put(
      "atx-trailing-equalize",
      new MutableDataSet().set(Formatter.ATX_HEADING_TRAILING_MARKER, EqualizeTrailingMarker.EQUALIZE).toImmutable()
    )
    map.put(
      "atx-trailing-remove",
      new MutableDataSet().set(Formatter.ATX_HEADING_TRAILING_MARKER, EqualizeTrailingMarker.REMOVE).toImmutable()
    )
    map.put("thematic-break", new MutableDataSet().set(Formatter.THEMATIC_BREAK, "*** ** * ** ***").toImmutable())
    map.put(
      "no-block-quote-blank-lines",
      new MutableDataSet().set(Formatter.BLOCK_QUOTE_BLANK_LINES, java.lang.Boolean.valueOf(false)).toImmutable()
    )
    map.put(
      "block-quote-compact",
      new MutableDataSet().set(Formatter.BLOCK_QUOTE_MARKERS, BlockQuoteMarker.ADD_COMPACT).toImmutable()
    )
    map.put(
      "block-quote-compact-with-space",
      new MutableDataSet().set(Formatter.BLOCK_QUOTE_MARKERS, BlockQuoteMarker.ADD_COMPACT_WITH_SPACE).toImmutable()
    )
    map.put("block-quote-spaced", new MutableDataSet().set(Formatter.BLOCK_QUOTE_MARKERS, BlockQuoteMarker.ADD_SPACED).toImmutable())
    map.put(
      "indented-code-minimize",
      new MutableDataSet().set(Formatter.INDENTED_CODE_MINIMIZE_INDENT, java.lang.Boolean.valueOf(true)).toImmutable()
    )
    map.put(
      "fenced-code-minimize",
      new MutableDataSet().set(Formatter.FENCED_CODE_MINIMIZE_INDENT, java.lang.Boolean.valueOf(true)).toImmutable()
    )
    map.put(
      "fenced-code-match-closing",
      new MutableDataSet().set(Formatter.FENCED_CODE_MATCH_CLOSING_MARKER, java.lang.Boolean.valueOf(true)).toImmutable()
    )
    map.put(
      "fenced-code-spaced-info",
      new MutableDataSet().set(Formatter.FENCED_CODE_SPACE_BEFORE_INFO, java.lang.Boolean.valueOf(true)).toImmutable()
    )
    map.put(
      "fenced-code-marker-length",
      new MutableDataSet().set(Formatter.FENCED_CODE_MARKER_LENGTH, java.lang.Integer.valueOf(6)).toImmutable()
    )
    map.put(
      "fenced-code-marker-backtick",
      new MutableDataSet().set(Formatter.FENCED_CODE_MARKER_TYPE, CodeFenceMarker.BACK_TICK).toImmutable()
    )
    map.put(
      "fenced-code-marker-tilde",
      new MutableDataSet().set(Formatter.FENCED_CODE_MARKER_TYPE, CodeFenceMarker.TILDE).toImmutable()
    )
    map.put(
      "list-add-blank-line-before",
      new MutableDataSet().set(Formatter.LIST_ADD_BLANK_LINE_BEFORE, java.lang.Boolean.valueOf(true)).toImmutable()
    )
    map.put(
      "list-no-renumber-items",
      new MutableDataSet().set(Formatter.LIST_RENUMBER_ITEMS, java.lang.Boolean.valueOf(false)).toImmutable()
    )
    map.put("list-bullet-dash", new MutableDataSet().set(Formatter.LIST_BULLET_MARKER, ListBulletMarker.DASH).toImmutable())
    map.put("list-bullet-asterisk", new MutableDataSet().set(Formatter.LIST_BULLET_MARKER, ListBulletMarker.ASTERISK).toImmutable())
    map.put("list-bullet-plus", new MutableDataSet().set(Formatter.LIST_BULLET_MARKER, ListBulletMarker.PLUS).toImmutable())
    map.put("list-numbered-dot", new MutableDataSet().set(Formatter.LIST_NUMBERED_MARKER, ListNumberedMarker.DOT).toImmutable())
    map.put("list-numbered-paren", new MutableDataSet().set(Formatter.LIST_NUMBERED_MARKER, ListNumberedMarker.PAREN).toImmutable())
    map.put("list-spacing-loosen", new MutableDataSet().set(Formatter.LIST_SPACING, ListSpacing.LOOSEN).toImmutable())
    map.put("list-spacing-tighten", new MutableDataSet().set(Formatter.LIST_SPACING, ListSpacing.TIGHTEN).toImmutable())
    map.put("list-spacing-loose", new MutableDataSet().set(Formatter.LIST_SPACING, ListSpacing.LOOSE).toImmutable())
    map.put("list-spacing-tight", new MutableDataSet().set(Formatter.LIST_SPACING, ListSpacing.TIGHT).toImmutable())
    map.put("references-keep-last", new MutableDataSet().set(Parser.REFERENCES_KEEP, KeepType.LAST).toImmutable())
    map.put(
      "image-links-at-start",
      new MutableDataSet().set(Formatter.KEEP_IMAGE_LINKS_AT_START, java.lang.Boolean.valueOf(true)).toImmutable()
    )
    map.put(
      "explicit-links-at-start",
      new MutableDataSet().set(Formatter.KEEP_EXPLICIT_LINKS_AT_START, java.lang.Boolean.valueOf(true)).toImmutable()
    )
    map.put(
      "remove-empty-items",
      new MutableDataSet().set(Formatter.LIST_REMOVE_EMPTY_ITEMS, java.lang.Boolean.valueOf(true)).toImmutable()
    )
    map
  }
}
