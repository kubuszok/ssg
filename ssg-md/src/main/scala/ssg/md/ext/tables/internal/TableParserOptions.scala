/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/internal/TableParserOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package tables
package internal

import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

private[tables] class TableParserOptions(options: DataHolder) {

  val maxHeaderRows:              Int     = TablesExtension.MAX_HEADER_ROWS.get(options)
  val minHeaderRows:              Int     = TablesExtension.MIN_HEADER_ROWS.get(options)
  val minSeparatorDashes:         Int     = TablesExtension.MIN_SEPARATOR_DASHES.get(options)
  val appendMissingColumns:       Boolean = TablesExtension.APPEND_MISSING_COLUMNS.get(options)
  val discardExtraColumns:        Boolean = TablesExtension.DISCARD_EXTRA_COLUMNS.get(options)
  val columnSpans:                Boolean = TablesExtension.COLUMN_SPANS.get(options)
  val trimCellWhitespace:         Boolean = TablesExtension.TRIM_CELL_WHITESPACE.get(options)
  val headerSeparatorColumnMatch: Boolean = TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH.get(options)
  val className:                  String  = TablesExtension.CLASS_NAME.get(options)
  val withCaption:                Boolean = TablesExtension.WITH_CAPTION.get(options)
}
