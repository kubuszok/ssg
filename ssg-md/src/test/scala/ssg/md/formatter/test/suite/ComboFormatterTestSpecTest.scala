/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-formatter-test-suite/.../ComboFormatterTestSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * NOTE: SpecExampleExtension is not yet ported, so it is omitted from the extensions list.
 * This means any examples in the spec that rely on it will need to be in knownFailures. */
package ssg
package md
package formatter
package test
package suite

import ssg.md.Nullable
import ssg.md.ext.abbreviation.AbbreviationExtension
import ssg.md.ext.anchorlink.AnchorLinkExtension
import ssg.md.ext.aside.AsideExtension
import ssg.md.ext.autolink.AutolinkExtension
import ssg.md.ext.definition.DefinitionExtension
import ssg.md.ext.emoji.EmojiExtension
import ssg.md.ext.escaped.character.EscapedCharacterExtension
import ssg.md.ext.footnotes.FootnoteExtension
import ssg.md.ext.gfm.strikethrough.StrikethroughSubscriptExtension
import ssg.md.ext.gfm.tasklist.{ TaskListExtension, TaskListItemCase, TaskListItemPlacement }
import ssg.md.ext.ins.InsExtension
import ssg.md.ext.jekyll.front.matter.JekyllFrontMatterExtension
import ssg.md.ext.jekyll.tag.JekyllTagExtension
import ssg.md.ext.macros.MacrosExtension
import ssg.md.ext.superscript.SuperscriptExtension
import ssg.md.ext.tables.TablesExtension
import ssg.md.ext.toc.{ SimTocExtension, TocExtension }
import ssg.md.ext.typographic.TypographicExtension
import ssg.md.ext.wikilink.WikiLinkExtension
import ssg.md.ext.yaml.front.matter.YamlFrontMatterExtension
import ssg.md.formatter.Formatter
import ssg.md.parser.Parser
import ssg.md.test.util.FormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }
import ssg.md.util.format.options.*

import java.util.{ Arrays, HashMap }
import scala.language.implicitConversions

final class ComboFormatterTestSpecTest extends FormatterSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboFormatterTestSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboFormatterTestSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboFormatterTestSpecTest.OPTIONS_MAP
  override def knownFailurePrefixes: Set[String] = Set("Attribute Formatting -", "Block Quotes -", "Formatter -", "Jekyll Include -", "Lists -", "Table of Contents -", "Task List Items -")
}

object ComboFormatterTestSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/formatter/test/suite/formatter_test_suite_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboFormatterTestSpecTest], SPEC_RESOURCE)
  val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Arrays.asList(
      AbbreviationExtension.create(),
      AnchorLinkExtension.create(),
      AsideExtension.create(),
      AutolinkExtension.create(),
      DefinitionExtension.create(),
      EmojiExtension.create(),
      EscapedCharacterExtension.create(),
      FootnoteExtension.create(),
      StrikethroughSubscriptExtension.create(),
      JekyllFrontMatterExtension.create(),
      JekyllTagExtension.create(),
      InsExtension.create(),
      SuperscriptExtension.create(),
      // SpecExampleExtension.create(), // not yet ported
      TablesExtension.create(),
      TaskListExtension.create(),
      TocExtension.create(),
      SimTocExtension.create(),
      TypographicExtension.create(),
      WikiLinkExtension.create(),
      MacrosExtension.create(),
      YamlFrontMatterExtension.create()
    ))
    .set(Parser.BLANK_LINES_IN_AST, true)
    .set(SimTocExtension.BLANK_LINE_SPACER, true)
    .set(Parser.HEADING_NO_ATX_SPACE, true)
    .set(JekyllTagExtension.EMBED_INCLUDED_CONTENT, false)
    .toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("no-tailing-blanks", new MutableDataSet().set(Formatter.MAX_TRAILING_BLANK_LINES, 0).toImmutable)
    map.put("atx-space-as-is", new MutableDataSet().set(Formatter.SPACE_AFTER_ATX_MARKER, DiscretionaryText.AS_IS).toImmutable)
    map.put("atx-space-add", new MutableDataSet().set(Formatter.SPACE_AFTER_ATX_MARKER, DiscretionaryText.ADD).toImmutable)
    map.put("atx-space-remove", new MutableDataSet().set(Formatter.SPACE_AFTER_ATX_MARKER, DiscretionaryText.REMOVE).toImmutable)
    map.put("setext-no-equalize", new MutableDataSet().set(Formatter.SETEXT_HEADING_EQUALIZE_MARKER, false).toImmutable)
    map.put("atx-trailing-add", new MutableDataSet().set(Formatter.ATX_HEADING_TRAILING_MARKER, EqualizeTrailingMarker.ADD).toImmutable)
    map.put("atx-trailing-equalize", new MutableDataSet().set(Formatter.ATX_HEADING_TRAILING_MARKER, EqualizeTrailingMarker.EQUALIZE).toImmutable)
    map.put("atx-trailing-remove", new MutableDataSet().set(Formatter.ATX_HEADING_TRAILING_MARKER, EqualizeTrailingMarker.REMOVE).toImmutable)
    map.put("thematic-break", new MutableDataSet().set(Formatter.THEMATIC_BREAK, "*** ** * ** ***").toImmutable)
    map.put("block-quote-compact", new MutableDataSet().set(Formatter.BLOCK_QUOTE_MARKERS, BlockQuoteMarker.ADD_COMPACT).toImmutable)
    map.put("block-quote-compact-with-space", new MutableDataSet().set(Formatter.BLOCK_QUOTE_MARKERS, BlockQuoteMarker.ADD_COMPACT_WITH_SPACE).toImmutable)
    map.put("block-quote-spaced", new MutableDataSet().set(Formatter.BLOCK_QUOTE_MARKERS, BlockQuoteMarker.ADD_SPACED).toImmutable)
    map.put("indented-code-minimize", new MutableDataSet().set(Formatter.INDENTED_CODE_MINIMIZE_INDENT, true).toImmutable)
    map.put("fenced-code-minimize", new MutableDataSet().set(Formatter.FENCED_CODE_MINIMIZE_INDENT, true).toImmutable)
    map.put("fenced-code-match-closing", new MutableDataSet().set(Formatter.FENCED_CODE_MATCH_CLOSING_MARKER, true).toImmutable)
    map.put("fenced-code-spaced-info", new MutableDataSet().set(Formatter.FENCED_CODE_SPACE_BEFORE_INFO, true).toImmutable)
    map.put("fenced-code-marker-length", new MutableDataSet().set(Formatter.FENCED_CODE_MARKER_LENGTH, 6).toImmutable)
    map.put("fenced-code-marker-backtick", new MutableDataSet().set(Formatter.FENCED_CODE_MARKER_TYPE, CodeFenceMarker.BACK_TICK).toImmutable)
    map.put("fenced-code-marker-tilde", new MutableDataSet().set(Formatter.FENCED_CODE_MARKER_TYPE, CodeFenceMarker.TILDE).toImmutable)
    map.put("list-add-blank-line-before", new MutableDataSet().set(Formatter.LIST_ADD_BLANK_LINE_BEFORE, true).toImmutable)
    map.put("list-no-renumber-items", new MutableDataSet().set(Formatter.LIST_RENUMBER_ITEMS, false).toImmutable)
    map.put("list-bullet-any", new MutableDataSet().set(Formatter.LIST_BULLET_MARKER, ListBulletMarker.ANY).toImmutable)
    map.put("list-bullet-dash", new MutableDataSet().set(Formatter.LIST_BULLET_MARKER, ListBulletMarker.DASH).toImmutable)
    map.put("list-bullet-asterisk", new MutableDataSet().set(Formatter.LIST_BULLET_MARKER, ListBulletMarker.ASTERISK).toImmutable)
    map.put("list-bullet-plus", new MutableDataSet().set(Formatter.LIST_BULLET_MARKER, ListBulletMarker.PLUS).toImmutable)
    map.put("list-numbered-any", new MutableDataSet().set(Formatter.LIST_NUMBERED_MARKER, ListNumberedMarker.ANY).toImmutable)
    map.put("list-numbered-dot", new MutableDataSet().set(Formatter.LIST_NUMBERED_MARKER, ListNumberedMarker.DOT).toImmutable)
    map.put("list-numbered-paren", new MutableDataSet().set(Formatter.LIST_NUMBERED_MARKER, ListNumberedMarker.PAREN).toImmutable)
    map.put("list-spacing", new MutableDataSet().set(Formatter.LIST_SPACING, ListSpacing.AS_IS).toImmutable)
    map.put("task-case-as-is", new MutableDataSet().set(TaskListExtension.FORMAT_LIST_ITEM_CASE, TaskListItemCase.AS_IS).toImmutable)
    map.put("task-case-lowercase", new MutableDataSet().set(TaskListExtension.FORMAT_LIST_ITEM_CASE, TaskListItemCase.LOWERCASE).toImmutable)
    map.put("task-case-uppercase", new MutableDataSet().set(TaskListExtension.FORMAT_LIST_ITEM_CASE, TaskListItemCase.UPPERCASE).toImmutable)
    map.put("task-placement-as-is", new MutableDataSet().set(TaskListExtension.FORMAT_LIST_ITEM_PLACEMENT, TaskListItemPlacement.AS_IS).toImmutable)
    map.put("task-placement-incomplete-first", new MutableDataSet().set(TaskListExtension.FORMAT_LIST_ITEM_PLACEMENT, TaskListItemPlacement.INCOMPLETE_FIRST).toImmutable)
    map.put("task-placement-incomplete-nested-first", new MutableDataSet().set(TaskListExtension.FORMAT_LIST_ITEM_PLACEMENT, TaskListItemPlacement.INCOMPLETE_NESTED_FIRST).toImmutable)
    map.put("task-placement-complete-to-non-task", new MutableDataSet().set(TaskListExtension.FORMAT_LIST_ITEM_PLACEMENT, TaskListItemPlacement.COMPLETE_TO_NON_TASK).toImmutable)
    map.put("task-placement-complete-nested-to-non-task", new MutableDataSet().set(TaskListExtension.FORMAT_LIST_ITEM_PLACEMENT, TaskListItemPlacement.COMPLETE_NESTED_TO_NON_TASK).toImmutable)
    map
  }
}
