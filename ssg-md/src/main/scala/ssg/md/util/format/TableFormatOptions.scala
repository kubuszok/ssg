/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TableFormatOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/TableFormatOptions.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package format

import ssg.md.Nullable
import ssg.md.util.data.{ DataHolder, DataKey, MutableDataHolder, MutableDataSetter, NullableDataKey }
import ssg.md.util.format.options.{ DiscretionaryText, TableCaptionHandling }
import ssg.md.util.misc.CharPredicate
import ssg.md.util.sequence.SequenceUtils

import scala.language.implicitConversions

class TableFormatOptions(options: Nullable[DataHolder]) extends MutableDataSetter {

  val leadTrailPipes:                  Boolean           = TableFormatOptions.FORMAT_TABLE_LEAD_TRAIL_PIPES.get(options.getOrElse(null))
  val spaceAroundPipes:                Boolean           = TableFormatOptions.FORMAT_TABLE_SPACE_AROUND_PIPES.get(options.getOrElse(null))
  val adjustColumnWidth:               Boolean           = TableFormatOptions.FORMAT_TABLE_ADJUST_COLUMN_WIDTH.get(options.getOrElse(null))
  val applyColumnAlignment:            Boolean           = TableFormatOptions.FORMAT_TABLE_APPLY_COLUMN_ALIGNMENT.get(options.getOrElse(null))
  val fillMissingColumns:              Boolean           = TableFormatOptions.FORMAT_TABLE_FILL_MISSING_COLUMNS.get(options.getOrElse(null))
  val formatTableFillMissingMinColumn: Nullable[Integer] = Nullable(TableFormatOptions.FORMAT_TABLE_FILL_MISSING_MIN_COLUMN.get(options.getOrElse(null)))

  val trimCellWhitespace:       Boolean              = TableFormatOptions.FORMAT_TABLE_TRIM_CELL_WHITESPACE.get(options.getOrElse(null))
  val dumpIntellijOffsets:      Boolean              = TableFormatOptions.FORMAT_TABLE_DUMP_TRACKING_OFFSETS.get(options.getOrElse(null))
  val leftAlignMarker:          DiscretionaryText    = TableFormatOptions.FORMAT_TABLE_LEFT_ALIGN_MARKER.get(options.getOrElse(null))
  val formatTableCaption:       TableCaptionHandling = TableFormatOptions.FORMAT_TABLE_CAPTION.get(options.getOrElse(null))
  val formatTableCaptionSpaces: DiscretionaryText    = TableFormatOptions.FORMAT_TABLE_CAPTION_SPACES.get(options.getOrElse(null))
  val minSeparatorColumnWidth:  Int                  = TableFormatOptions.FORMAT_TABLE_MIN_SEPARATOR_COLUMN_WIDTH.get(options.getOrElse(null))
  val minSeparatorDashes:       Int                  = TableFormatOptions.FORMAT_TABLE_MIN_SEPARATOR_DASHES.get(options.getOrElse(null))
  val charWidthProvider:        CharWidthProvider    = TableFormatOptions.FORMAT_CHAR_WIDTH_PROVIDER.get(options.getOrElse(null))
  val formatTableIndentPrefix:  String               = TableFormatOptions.FORMAT_TABLE_INDENT_PREFIX.get(options.getOrElse(null))
  val tableManipulator:         TableManipulator     = TableFormatOptions.FORMAT_TABLE_MANIPULATOR.get(options.getOrElse(null))

  val spaceWidth: Int = charWidthProvider.spaceWidth
  val spacePad:   Int = if (spaceAroundPipes) 2 * spaceWidth else 0
  val pipeWidth:  Int = charWidthProvider.getCharWidth('|')
  val colonWidth: Int = charWidthProvider.getCharWidth(':')
  val dashWidth:  Int = charWidthProvider.getCharWidth('-')

  def this() =
    this(Nullable(null))

  override def setIn(dataHolder: MutableDataHolder): MutableDataHolder = {
    dataHolder.set(TableFormatOptions.FORMAT_TABLE_LEAD_TRAIL_PIPES, leadTrailPipes)
    dataHolder.set(TableFormatOptions.FORMAT_TABLE_SPACE_AROUND_PIPES, spaceAroundPipes)
    dataHolder.set(TableFormatOptions.FORMAT_TABLE_ADJUST_COLUMN_WIDTH, adjustColumnWidth)
    dataHolder.set(TableFormatOptions.FORMAT_TABLE_APPLY_COLUMN_ALIGNMENT, applyColumnAlignment)
    dataHolder.set(TableFormatOptions.FORMAT_TABLE_FILL_MISSING_COLUMNS, fillMissingColumns)
    dataHolder.set(TableFormatOptions.FORMAT_TABLE_FILL_MISSING_MIN_COLUMN, formatTableFillMissingMinColumn.getOrElse(null))
    dataHolder.set(TableFormatOptions.FORMAT_TABLE_LEFT_ALIGN_MARKER, leftAlignMarker)
    dataHolder.set(TableFormatOptions.FORMAT_TABLE_MIN_SEPARATOR_COLUMN_WIDTH, minSeparatorColumnWidth)
    dataHolder.set(TableFormatOptions.FORMAT_TABLE_MIN_SEPARATOR_DASHES, minSeparatorDashes)
    dataHolder.set(TableFormatOptions.FORMAT_CHAR_WIDTH_PROVIDER, charWidthProvider)
    dataHolder.set(TableFormatOptions.FORMAT_TABLE_CAPTION, formatTableCaption)
    dataHolder.set(TableFormatOptions.FORMAT_TABLE_CAPTION_SPACES, formatTableCaptionSpaces)
    dataHolder.set(TableFormatOptions.FORMAT_TABLE_INDENT_PREFIX, formatTableIndentPrefix)
    dataHolder.set(TableFormatOptions.FORMAT_TABLE_TRIM_CELL_WHITESPACE, trimCellWhitespace)
    dataHolder.set(TableFormatOptions.FORMAT_TABLE_MANIPULATOR, tableManipulator)
    dataHolder.set(TableFormatOptions.FORMAT_TABLE_DUMP_TRACKING_OFFSETS, dumpIntellijOffsets)
    dataHolder
  }
}

object TableFormatOptions {
  // NOTE: the value of \u001f is hardcoded in Parsing patterns
  val INTELLIJ_DUMMY_IDENTIFIER_CHAR: Char          = SequenceUtils.US
  val INTELLIJ_DUMMY_IDENTIFIER:      String        = SequenceUtils.US_CHARS
  val INTELLIJ_DUMMY_IDENTIFIER_SET:  CharPredicate = (value: Int) => value == SequenceUtils.US

  val FORMAT_TABLE_LEAD_TRAIL_PIPES:       DataKey[Boolean] = new DataKey[Boolean]("FORMAT_TABLE_LEAD_TRAIL_PIPES", true)
  val FORMAT_TABLE_SPACE_AROUND_PIPES:     DataKey[Boolean] = new DataKey[Boolean]("FORMAT_TABLE_SPACE_AROUND_PIPES", true)
  val FORMAT_TABLE_ADJUST_COLUMN_WIDTH:    DataKey[Boolean] = new DataKey[Boolean]("FORMAT_TABLE_ADJUST_COLUMN_WIDTH", true)
  val FORMAT_TABLE_APPLY_COLUMN_ALIGNMENT: DataKey[Boolean] = new DataKey[Boolean]("FORMAT_TABLE_APPLY_COLUMN_ALIGNMENT", true)
  val FORMAT_TABLE_FILL_MISSING_COLUMNS:   DataKey[Boolean] = new DataKey[Boolean]("FORMAT_TABLE_FILL_MISSING_COLUMNS", false)

  /** Used by table formatting to set min column from which to add missing columns, null to use default
    */
  val FORMAT_TABLE_FILL_MISSING_MIN_COLUMN: NullableDataKey[Integer] = new NullableDataKey[Integer]("FORMAT_TABLE_FILL_MISSING_MIN_COLUMN")

  val FORMAT_TABLE_LEFT_ALIGN_MARKER:          DataKey[DiscretionaryText]    = new DataKey[DiscretionaryText]("FORMAT_TABLE_LEFT_ALIGN_MARKER", DiscretionaryText.AS_IS)
  val FORMAT_TABLE_MIN_SEPARATOR_COLUMN_WIDTH: DataKey[Int]                  = new DataKey[Int]("FORMAT_TABLE_MIN_SEPARATOR_COLUMN_WIDTH", 3)
  val FORMAT_TABLE_MIN_SEPARATOR_DASHES:       DataKey[Int]                  = new DataKey[Int]("FORMAT_TABLE_MIN_SEPARATOR_DASHES", 1)
  val FORMAT_TABLE_TRIM_CELL_WHITESPACE:       DataKey[Boolean]              = new DataKey[Boolean]("FORMAT_TABLE_TRIM_CELL_WHITESPACE", true)
  val FORMAT_TABLE_CAPTION:                    DataKey[TableCaptionHandling] = new DataKey[TableCaptionHandling]("FORMAT_TABLE_CAPTION", TableCaptionHandling.AS_IS)
  val FORMAT_TABLE_CAPTION_SPACES:             DataKey[DiscretionaryText]    = new DataKey[DiscretionaryText]("FORMAT_TABLE_CAPTION_SPACES", DiscretionaryText.AS_IS)
  val FORMAT_TABLE_INDENT_PREFIX:              DataKey[String]               = new DataKey[String]("FORMAT_TABLE_INDENT_PREFIX", "")
  val FORMAT_TABLE_MANIPULATOR:                DataKey[TableManipulator]     = new DataKey[TableManipulator]("FORMAT_TABLE_MANIPULATOR", TableManipulator.NULL)

  val FORMAT_CHAR_WIDTH_PROVIDER:         DataKey[CharWidthProvider] = new DataKey[CharWidthProvider]("FORMAT_CHAR_WIDTH_PROVIDER", CharWidthProvider.NULL)
  val FORMAT_TABLE_DUMP_TRACKING_OFFSETS: DataKey[Boolean]           = new DataKey[Boolean]("FORMAT_TABLE_DUMP_TRACKING_OFFSETS", false)
}
