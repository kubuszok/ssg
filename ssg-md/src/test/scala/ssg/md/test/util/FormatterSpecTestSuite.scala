/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * munit adapter for flexmark formatter spec tests.
 * Replaces FormatterSpecTest/FormatterTranslationSpecTestBase (JUnit4 ComboSpecTestCase subclass). */
package ssg
package md
package test
package util

import ssg.md.Nullable
import ssg.md.formatter.Formatter
import ssg.md.parser.{ Parser, ParserEmulationProfile }
import ssg.md.test.util.spec.SpecExample
import ssg.md.util.ast.KeepType
import ssg.md.util.data.{ DataHolder, DataKey, DataSet, MutableDataSet, SharedDataKeys }
import ssg.md.util.format.TrackedOffset
import ssg.md.util.format.options.*
import ssg.md.util.sequence.{ BasedSequence, SequenceUtils }
import ssg.md.util.sequence.builder.SequenceBuilder

import java.{ util => ju }
import java.util.regex.Pattern
import scala.language.implicitConversions

/** munit suite for Formatter (markdown→markdown) spec tests.
  *
  * Parses markdown with Parser, renders with Formatter, compares against expected formatted markdown from the spec file.
  *
  * Mirrors the original FormatterSpecTest / FormatterTranslationSpecTestBase hierarchy.
  */
abstract class FormatterSpecTestSuite extends SpecTestSuite {

  /** Aggregate base formatter options with subclass defaults. */
  override protected def optionsFor(example: SpecExample): DataHolder = {
    val subclassBase = defaultOptions.getOrElse(new MutableDataSet())
    // Merge formatter base options (BLANK_LINES_IN_AST=true, HEADING_NO_ATX_SPACE=true) with subclass defaults
    val base      = DataSet.aggregate(Nullable(FormatterSpecTestSuite.BASE_OPTIONS), Nullable(subclassBase)).toImmutable
    val optionSet = example.optionsSet
    if (optionSet.isDefined && optionSet.get.nonEmpty) {
      val mergedMap = new ju.HashMap[String, DataHolder](FormatterSpecTestSuite.BASE_OPTIONS_MAP)
      mergedMap.putAll(optionsMap)
      val optionsProvider: String => Nullable[DataHolder] = { name =>
        TestUtils.processOption(mergedMap.asInstanceOf[ju.Map[String, DataHolder]], name)
      }
      val opts = TestUtils.getOptions(example, optionSet, optionsProvider)
      if (opts.isDefined) {
        DataSet.aggregate(Nullable(base), opts).toImmutable
      } else {
        base
      }
    } else {
      base
    }
  }

  override protected def renderHtml(example: SpecExample, options: DataHolder): String = {
    import scala.jdk.CollectionConverters.*
    val parser           = Parser.builder(options).build()
    val formatter        = Formatter.builder(Nullable(options)).build()
    val noFileEol        = TestUtils.NO_FILE_EOL.get(options)
    val trimmedSource    = if (noFileEol) TestUtils.trimTrailingEOL(example.source) else example.source
    val originalSequence = BasedSequence.of(trimmedSource)
    val extractMarkup    = TestUtils.extractMarkup(originalSequence)
    val trackedSequence: BasedSequence = extractMarkup.first.get
    val document = parser.parse(trackedSequence.toString)

    val offsets: Array[Int] = extractMarkup.second.get
    if (offsets.length > 0) {
      val trackedOffsets = new java.util.ArrayList[TrackedOffset](offsets.length)
      val c              = FormatterSpecTestSuite.EDIT_OP_CHAR.get(options)
      val editOp         = FormatterSpecTestSuite.EDIT_OP.get(options)

      for (offset <- offsets) {
        val trackedOffset = TrackedOffset.track(offset, editOp != 0 && c == ' ', editOp > 0, editOp < 0)
        trackedOffset.spacesBefore = trackedSequence.getBaseSequence.countTrailingSpaceTab(offset)
        trackedOffset.spacesAfter = trackedSequence.getBaseSequence.countLeadingSpaceTab(offset)
        trackedOffsets.add(trackedOffset)
      }

      Formatter.TRACKED_SEQUENCE.set(document.document, trackedSequence)
      Formatter.TRACKED_OFFSETS.set(document.document, trackedOffsets.asScala.toList)
      Formatter.RESTORE_TRACKED_SPACES.set(document.document, Formatter.RESTORE_TRACKED_SPACES.get(options))

      Formatter.DOCUMENT_FIRST_PREFIX.set(document.document, Formatter.DOCUMENT_FIRST_PREFIX.get(options))
      Formatter.DOCUMENT_PREFIX.set(document.document, Formatter.DOCUMENT_PREFIX.get(options))

      if (trackedOffsets.isEmpty && !FormatterSpecTestSuite.SHOW_LINE_RANGES.get(options)) {
        formatter.render(document)
      } else {
        val builder: SequenceBuilder = document.chars.getBuilder
        formatter.render(document, builder)
        val html = builder.toString

        val resolvedOffsets = new Array[Int](trackedOffsets.size())
        var i               = 0
        for (trackedOffset <- trackedOffsets.asScala)
          if (trackedOffset.isResolved) {
            resolvedOffsets(i) = trackedOffset.getIndex
            i += 1
          }

        val finalOffsets = if (i < resolvedOffsets.length) java.util.Arrays.copyOf(resolvedOffsets, i) else resolvedOffsets

        var result = TestUtils.insertCaretMarkup(BasedSequence.of(html), finalOffsets).toSequence.toString

        if (FormatterSpecTestSuite.SHOW_LINE_RANGES.get(options)) {
          val out = new StringBuilder()
          out.append(result)
          if (trackedSequence eq document.document.chars) {
            TestUtils.appendBanner(out, TestUtils.bannerText("Ranges"), false)
            out.append(builder.toStringWithRanges(false))
          } else {
            if (!trackedOffsets.isEmpty) {
              TestUtils.appendBanner(out, TestUtils.bannerText("Tracked Offsets"), false)
              var i1 = 0
              for (trackedOffset1 <- trackedOffsets.asScala) {
                out.append("[").append(i1).append("]: ").append(trackedOffset1.toString).append("\n")
                i1 += 1
              }
            }
            TestUtils.appendBanner(out, TestUtils.bannerText("Ranges"), !trackedOffsets.isEmpty)
            val sequence = builder.toSequence(trackedSequence)
            val sb1: SequenceBuilder = sequence.getBuilder
            out.append(sb1.append(sequence).toStringWithRanges(false)).append("\n")
            TestUtils.appendBanner(out, TestUtils.bannerText("Segments"), false)
            val sb2: SequenceBuilder = sequence.getBuilder
            out.append(sb2.append(sequence).segmentBuilder.toString)
          }
          result = out.toString
        }
        result
      }
    } else {
      // No tracked offsets
      Formatter.DOCUMENT_FIRST_PREFIX.set(document.document, Formatter.DOCUMENT_FIRST_PREFIX.get(options))
      Formatter.DOCUMENT_PREFIX.set(document.document, Formatter.DOCUMENT_PREFIX.get(options))

      if (!FormatterSpecTestSuite.SHOW_LINE_RANGES.get(options)) {
        formatter.render(document)
      } else {
        val builder: SequenceBuilder = document.chars.getBuilder
        formatter.render(document, builder)
        val html = builder.toString
        val out  = new StringBuilder()
        out.append(html)
        if (trackedSequence eq document.document.chars) {
          TestUtils.appendBanner(out, TestUtils.bannerText("Ranges"), false)
          out.append(builder.toStringWithRanges(false))
        }
        out.toString
      }
    }
  }

  override protected def renderAst(example: SpecExample, options: DataHolder): Nullable[String] = Nullable.empty
}

object FormatterSpecTestSuite {

  /** DataKey for SHOW_LINE_RANGES from FormatterTranslationSpecTestBase */
  val SHOW_LINE_RANGES: DataKey[Boolean] = new DataKey[Boolean]("SHOW_LINE_RANGES", false)
  val EDIT_OP_CHAR:     DataKey[Char]    = new DataKey[Char]("EDIT_OP_CHAR", SequenceUtils.NUL)
  val EDIT_OP:          DataKey[Int]     = new DataKey[Int]("EDIT_OP", 0)

  private val FIXED_INDENT_OPTIONS: DataHolder = new MutableDataSet().setFrom(ParserEmulationProfile.FIXED_INDENT).toImmutable

  /** Base formatter options: BLANK_LINES_IN_AST=true, HEADING_NO_ATX_SPACE=true (matches original FormatterTranslationSpecTestBase). */
  val BASE_OPTIONS: DataHolder = new MutableDataSet().set(Parser.BLANK_LINES_IN_AST, true).set(Parser.HEADING_NO_ATX_SPACE, true).toImmutable

  /** Base options map (matches original FormatterTranslationSpecTestBase). */
  val BASE_OPTIONS_MAP: ju.Map[String, DataHolder] = {
    val map = new ju.HashMap[String, DataHolder]()
    map.put("IGNORED", new MutableDataSet().set(TestUtils.IGNORE, true).toImmutable)
    map.put("show-ranges", new MutableDataSet().set(SHOW_LINE_RANGES, true).toImmutable)
    map.put("running-tests", new MutableDataSet().set(SharedDataKeys.RUNNING_TESTS, true).toImmutable)

    map.put("insert-char", new MutableDataSet().set(EDIT_OP, 1).set(EDIT_OP_CHAR, '\u0000').toImmutable)
    map.put("insert-space", new MutableDataSet().set(EDIT_OP, 1).set(EDIT_OP_CHAR, ' ').toImmutable)
    map.put("delete-char", new MutableDataSet().set(EDIT_OP, -1).set(EDIT_OP_CHAR, '\u0000').toImmutable)
    map.put("delete-space", new MutableDataSet().set(EDIT_OP, -1).set(EDIT_OP_CHAR, ' ').toImmutable)
    map.put("restore-tracked-spaces", new MutableDataSet().set(Formatter.RESTORE_TRACKED_SPACES, true).toImmutable)
    map.put("multi-line-image-url", new MutableDataSet().set(Parser.PARSE_MULTI_LINE_IMAGE_URLS, true).toImmutable)

    map.put(
      "format-fixed-indent",
      new MutableDataSet().set(Formatter.FORMATTER_EMULATION_PROFILE, ParserEmulationProfile.FIXED_INDENT).toImmutable
    )
    map.put("format-content-after-prefix", new MutableDataSet().set(Formatter.LISTS_ITEM_CONTENT_AFTER_SUFFIX, true).toImmutable)
    map.put("no-list-auto-loose", new MutableDataSet().set(Parser.LISTS_AUTO_LOOSE, false).toImmutable)
    map.put("parse-fixed-indent", FIXED_INDENT_OPTIONS)
    map.put(
      "format-github",
      new MutableDataSet().set(Formatter.FORMATTER_EMULATION_PROFILE, ParserEmulationProfile.GITHUB_DOC).toImmutable
    )
    map.put("parse-github", new MutableDataSet().set(Parser.PARSER_EMULATION_PROFILE, ParserEmulationProfile.GITHUB_DOC).toImmutable)
    map.put("max-blank-lines-1", new MutableDataSet().set(Formatter.MAX_BLANK_LINES, 1).toImmutable)
    map.put("max-blank-lines-2", new MutableDataSet().set(Formatter.MAX_BLANK_LINES, 2).toImmutable)
    map.put("max-blank-lines-3", new MutableDataSet().set(Formatter.MAX_BLANK_LINES, 3).toImmutable)
    map.put("no-tailing-blanks", new MutableDataSet().set(Formatter.MAX_TRAILING_BLANK_LINES, 0).toImmutable)
    map.put("list-content-after-suffix", new MutableDataSet().set(Formatter.LISTS_ITEM_CONTENT_AFTER_SUFFIX, true).toImmutable)
    map.put("atx-space-as-is", new MutableDataSet().set(Formatter.SPACE_AFTER_ATX_MARKER, DiscretionaryText.AS_IS).toImmutable)
    map.put("atx-space-add", new MutableDataSet().set(Formatter.SPACE_AFTER_ATX_MARKER, DiscretionaryText.ADD).toImmutable)
    map.put("atx-space-remove", new MutableDataSet().set(Formatter.SPACE_AFTER_ATX_MARKER, DiscretionaryText.REMOVE).toImmutable)
    map.put("heading-any", new MutableDataSet().set(Formatter.HEADING_STYLE, HeadingStyle.AS_IS).toImmutable)
    map.put("heading-atx", new MutableDataSet().set(Formatter.HEADING_STYLE, HeadingStyle.ATX_PREFERRED).toImmutable)
    map.put("heading-setext", new MutableDataSet().set(Formatter.HEADING_STYLE, HeadingStyle.SETEXT_PREFERRED).toImmutable)
    map.put("setext-no-equalize", new MutableDataSet().set(Formatter.SETEXT_HEADING_EQUALIZE_MARKER, false).toImmutable)
    map.put(
      "atx-trailing-as-is",
      new MutableDataSet().set(Formatter.ATX_HEADING_TRAILING_MARKER, EqualizeTrailingMarker.AS_IS).toImmutable
    )
    map.put(
      "atx-trailing-add",
      new MutableDataSet().set(Formatter.ATX_HEADING_TRAILING_MARKER, EqualizeTrailingMarker.ADD).toImmutable
    )
    map.put(
      "atx-trailing-equalize",
      new MutableDataSet().set(Formatter.ATX_HEADING_TRAILING_MARKER, EqualizeTrailingMarker.EQUALIZE).toImmutable
    )
    map.put(
      "atx-trailing-remove",
      new MutableDataSet().set(Formatter.ATX_HEADING_TRAILING_MARKER, EqualizeTrailingMarker.REMOVE).toImmutable
    )
    map.put("thematic-break", new MutableDataSet().set(Formatter.THEMATIC_BREAK, "*** ** * ** ***").toImmutable)
    map.put("no-block-quote-blank-lines", new MutableDataSet().set(Formatter.BLOCK_QUOTE_BLANK_LINES, false).toImmutable)
    map.put("block-quote-compact", new MutableDataSet().set(Formatter.BLOCK_QUOTE_MARKERS, BlockQuoteMarker.ADD_COMPACT).toImmutable)
    map.put(
      "block-quote-compact-with-space",
      new MutableDataSet().set(Formatter.BLOCK_QUOTE_MARKERS, BlockQuoteMarker.ADD_COMPACT_WITH_SPACE).toImmutable
    )
    map.put("block-quote-spaced", new MutableDataSet().set(Formatter.BLOCK_QUOTE_MARKERS, BlockQuoteMarker.ADD_SPACED).toImmutable)
    map.put("indented-code-minimize", new MutableDataSet().set(Formatter.INDENTED_CODE_MINIMIZE_INDENT, true).toImmutable)
    map.put("fenced-code-minimize", new MutableDataSet().set(Formatter.FENCED_CODE_MINIMIZE_INDENT, true).toImmutable)
    map.put("fenced-code-match-closing", new MutableDataSet().set(Formatter.FENCED_CODE_MATCH_CLOSING_MARKER, true).toImmutable)
    map.put("fenced-code-spaced-info", new MutableDataSet().set(Formatter.FENCED_CODE_SPACE_BEFORE_INFO, true).toImmutable)
    map.put("fenced-code-marker-length", new MutableDataSet().set(Formatter.FENCED_CODE_MARKER_LENGTH, 6).toImmutable)
    map.put(
      "fenced-code-marker-backtick",
      new MutableDataSet().set(Formatter.FENCED_CODE_MARKER_TYPE, CodeFenceMarker.BACK_TICK).toImmutable
    )
    map.put(
      "fenced-code-marker-tilde",
      new MutableDataSet().set(Formatter.FENCED_CODE_MARKER_TYPE, CodeFenceMarker.TILDE).toImmutable
    )
    map.put("list-add-blank-line-before", new MutableDataSet().set(Formatter.LIST_ADD_BLANK_LINE_BEFORE, true).toImmutable)
    map.put("list-no-renumber-items", new MutableDataSet().set(Formatter.LIST_RENUMBER_ITEMS, false).toImmutable)
    map.put("list-reset-first-item", new MutableDataSet().set(Formatter.LIST_RESET_FIRST_ITEM_NUMBER, true).toImmutable)
    map.put(
      "list-no-delimiter-mismatch-to-new-list",
      new MutableDataSet().set(Parser.LISTS_DELIMITER_MISMATCH_TO_NEW_LIST, false).toImmutable
    )
    map.put(
      "list-no-item-mismatch-to-new-list",
      new MutableDataSet().set(Parser.LISTS_ITEM_TYPE_MISMATCH_TO_NEW_LIST, false).toImmutable
    )
    map.put("list-bullet-dash", new MutableDataSet().set(Formatter.LIST_BULLET_MARKER, ListBulletMarker.DASH).toImmutable)
    map.put("list-bullet-asterisk", new MutableDataSet().set(Formatter.LIST_BULLET_MARKER, ListBulletMarker.ASTERISK).toImmutable)
    map.put("list-bullet-plus", new MutableDataSet().set(Formatter.LIST_BULLET_MARKER, ListBulletMarker.PLUS).toImmutable)
    map.put("list-numbered-dot", new MutableDataSet().set(Formatter.LIST_NUMBERED_MARKER, ListNumberedMarker.DOT).toImmutable)
    map.put("list-numbered-paren", new MutableDataSet().set(Formatter.LIST_NUMBERED_MARKER, ListNumberedMarker.PAREN).toImmutable)
    map.put("list-spacing-as-is", new MutableDataSet().set(Formatter.LIST_SPACING, ListSpacing.AS_IS).toImmutable)
    map.put("list-spacing-loosen", new MutableDataSet().set(Formatter.LIST_SPACING, ListSpacing.LOOSEN).toImmutable)
    map.put("list-spacing-tighten", new MutableDataSet().set(Formatter.LIST_SPACING, ListSpacing.TIGHTEN).toImmutable)
    map.put("list-spacing-loose", new MutableDataSet().set(Formatter.LIST_SPACING, ListSpacing.LOOSE).toImmutable)
    map.put("list-spacing-tight", new MutableDataSet().set(Formatter.LIST_SPACING, ListSpacing.TIGHT).toImmutable)
    map.put("references-as-is", new MutableDataSet().set(Formatter.REFERENCE_PLACEMENT, ElementPlacement.AS_IS).toImmutable)
    map.put(
      "references-document-top",
      new MutableDataSet().set(Formatter.REFERENCE_PLACEMENT, ElementPlacement.DOCUMENT_TOP).toImmutable
    )
    map.put(
      "references-group-with-first",
      new MutableDataSet().set(Formatter.REFERENCE_PLACEMENT, ElementPlacement.GROUP_WITH_FIRST).toImmutable
    )
    map.put(
      "references-group-with-last",
      new MutableDataSet().set(Formatter.REFERENCE_PLACEMENT, ElementPlacement.GROUP_WITH_LAST).toImmutable
    )
    map.put(
      "references-document-bottom",
      new MutableDataSet().set(Formatter.REFERENCE_PLACEMENT, ElementPlacement.DOCUMENT_BOTTOM).toImmutable
    )
    map.put("references-sort", new MutableDataSet().set(Formatter.REFERENCE_SORT, ElementPlacementSort.SORT).toImmutable)
    map.put(
      "references-sort-unused-last",
      new MutableDataSet().set(Formatter.REFERENCE_SORT, ElementPlacementSort.SORT_UNUSED_LAST).toImmutable
    )
    map.put(
      "references-sort-delete-unused",
      new MutableDataSet().set(Formatter.REFERENCE_SORT, ElementPlacementSort.SORT_DELETE_UNUSED).toImmutable
    )
    map.put(
      "references-delete-unused",
      new MutableDataSet().set(Formatter.REFERENCE_SORT, ElementPlacementSort.DELETE_UNUSED).toImmutable
    )
    map.put("references-keep-last", new MutableDataSet().set(Parser.REFERENCES_KEEP, KeepType.LAST).toImmutable)
    map.put("image-links-at-start", new MutableDataSet().set(Formatter.KEEP_IMAGE_LINKS_AT_START, true).toImmutable)
    map.put("explicit-links-at-start", new MutableDataSet().set(Formatter.KEEP_EXPLICIT_LINKS_AT_START, true).toImmutable)
    map.put("remove-empty-items", new MutableDataSet().set(Formatter.LIST_REMOVE_EMPTY_ITEMS, true).toImmutable)
    map.put("no-hard-breaks", new MutableDataSet().set(Formatter.KEEP_HARD_LINE_BREAKS, false).toImmutable)
    map.put("no-soft-breaks", new MutableDataSet().set(Formatter.KEEP_SOFT_LINE_BREAKS, false).toImmutable)
    map.put("apply-escapers", new MutableDataSet().set(Formatter.APPLY_SPECIAL_LEAD_IN_HANDLERS, true).toImmutable)
    map.put("no-apply-escapers", new MutableDataSet().set(Formatter.APPLY_SPECIAL_LEAD_IN_HANDLERS, false).toImmutable)

    map.put("no-list-reset-first-item-number", new MutableDataSet().set(Formatter.LIST_RESET_FIRST_ITEM_NUMBER, false).toImmutable)
    map.put("list-reset-first-item-number", new MutableDataSet().set(Formatter.LIST_RESET_FIRST_ITEM_NUMBER, true).toImmutable)

    map.put("formatter-tags-enabled", new MutableDataSet().set(Formatter.FORMATTER_TAGS_ENABLED, true).toImmutable)
    map.put("formatter-tags-accept-regexp", new MutableDataSet().set(Formatter.FORMATTER_TAGS_ACCEPT_REGEXP, true).toImmutable)
    map.put("formatter-on-tag-alt", new MutableDataSet().set(Formatter.FORMATTER_ON_TAG, "@format:on").toImmutable)
    map.put("formatter-off-tag-alt", new MutableDataSet().set(Formatter.FORMATTER_OFF_TAG, "@format:off").toImmutable)
    map.put(
      "formatter-on-tag-regex",
      new MutableDataSet().set(Formatter.FORMATTER_ON_TAG, "^@format:(?:yes|on|true)$").set(Formatter.FORMATTER_TAGS_ACCEPT_REGEXP, true).toImmutable
    )
    map.put(
      "formatter-off-tag-regex",
      new MutableDataSet().set(Formatter.FORMATTER_OFF_TAG, "^@format:(?:no|off|false)$").set(Formatter.FORMATTER_TAGS_ACCEPT_REGEXP, true).toImmutable
    )

    map.put("list-align-numeric-none", new MutableDataSet().set(Formatter.LIST_ALIGN_NUMERIC, ElementAlignment.NONE).toImmutable)
    map.put(
      "list-align-numeric-left",
      new MutableDataSet().set(Formatter.LIST_ALIGN_NUMERIC, ElementAlignment.LEFT_ALIGN).toImmutable
    )
    map.put(
      "list-align-numeric-right",
      new MutableDataSet().set(Formatter.LIST_ALIGN_NUMERIC, ElementAlignment.RIGHT_ALIGN).toImmutable
    )
    map.put(
      "link-address-pattern",
      new MutableDataSet().set(Formatter.LINK_MARKER_COMMENT_PATTERN, Pattern.compile("^\\s*@IGNORE PREVIOUS:.*$")).toImmutable
    )

    map.put(
      "margin",
      new MutableDataSet()
        .set(
          TestUtils.CUSTOM_OPTION,
          ((option: String, params: String) => TestUtils.customIntOption(option, Nullable(params), (v: Int) => marginOption(v))): java.util.function.BiFunction[String, String, DataHolder]
        )
        .toImmutable
    )
    map.put(
      "first-prefix",
      new MutableDataSet()
        .set(
          TestUtils.CUSTOM_OPTION,
          ((option: String, params: String) => TestUtils.customStringOption(option, Nullable(params), (v: String) => firstIndentOption(v))): java.util.function.BiFunction[String, String, DataHolder]
        )
        .toImmutable
    )
    map.put(
      "prefix",
      new MutableDataSet()
        .set(
          TestUtils.CUSTOM_OPTION,
          ((option: String, params: String) => TestUtils.customStringOption(option, Nullable(params), (v: String) => indentOption(v))): java.util.function.BiFunction[String, String, DataHolder]
        )
        .toImmutable
    )
    map
  }

  private def firstIndentOption(params: String): DataHolder = {
    val value = if (params != null) params else ""
    new MutableDataSet().set(Formatter.DOCUMENT_FIRST_PREFIX, value).toImmutable
  }

  private def indentOption(params: String): DataHolder = {
    val value = if (params != null) params else ""
    new MutableDataSet().set(Formatter.DOCUMENT_PREFIX, value).toImmutable
  }

  private def marginOption(params: Int): DataHolder =
    new MutableDataSet().set(Formatter.RIGHT_MARGIN, params).toImmutable

  /** Helper to create placement and sort options (mirrors ComboSpecTestCase.placementAndSortOptions). */
  def placementAndSortOptions(
    keepTypeDataKey:  Nullable[DataKey[KeepType]],
    placementDataKey: Nullable[DataKey[ElementPlacement]],
    sortDataKey:      Nullable[DataKey[ElementPlacementSort]]
  ): ju.HashMap[String, DataHolder] = {
    val map = new ju.HashMap[String, DataHolder]()
    keepTypeDataKey.foreach { key =>
      map.put("references-keep-last", new MutableDataSet().set(key, KeepType.LAST).toImmutable)
      map.put("references-keep-first", new MutableDataSet().set(key, KeepType.FIRST).toImmutable)
      map.put("references-keep-fail", new MutableDataSet().set(key, KeepType.FAIL).toImmutable)
      map.put("references-keep-locked", new MutableDataSet().set(key, KeepType.LOCKED).toImmutable)
    }
    placementDataKey.foreach { key =>
      map.put("references-as-is", new MutableDataSet().set(key, ElementPlacement.AS_IS).toImmutable)
      map.put("references-document-top", new MutableDataSet().set(key, ElementPlacement.DOCUMENT_TOP).toImmutable)
      map.put("references-group-with-first", new MutableDataSet().set(key, ElementPlacement.GROUP_WITH_FIRST).toImmutable)
      map.put("references-group-with-last", new MutableDataSet().set(key, ElementPlacement.GROUP_WITH_LAST).toImmutable)
      map.put("references-document-bottom", new MutableDataSet().set(key, ElementPlacement.DOCUMENT_BOTTOM).toImmutable)
    }
    sortDataKey.foreach { key =>
      map.put("references-sort", new MutableDataSet().set(key, ElementPlacementSort.SORT).toImmutable)
      map.put("references-sort-unused-last", new MutableDataSet().set(key, ElementPlacementSort.SORT_UNUSED_LAST).toImmutable)
      map.put("references-sort-delete-unused", new MutableDataSet().set(key, ElementPlacementSort.SORT_DELETE_UNUSED).toImmutable)
      map.put("references-delete-unused", new MutableDataSet().set(key, ElementPlacementSort.DELETE_UNUSED).toImmutable)
    }
    map
  }
}
