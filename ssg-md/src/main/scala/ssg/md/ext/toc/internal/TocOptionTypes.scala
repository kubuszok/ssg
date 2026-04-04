/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/internal/TocOptionTypes.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package toc
package internal

import ssg.md.util.options.{ BooleanOptionParser, OptionParser }

/** Option parsers for TOC blocks. In Java these are enum members; here they are array entries. */
object TocOptionTypes {

  private val OPTION_BULLET:          String = "bullet"
  private val OPTION_NUMBERED:        String = "numbered"
  private val OPTION_TEXT:            String = "text"
  private val OPTION_FORMATTED:       String = "formatted"
  private val OPTION_HIERARCHY:       String = "hierarchy"
  private val OPTION_FLAT:            String = "flat"
  private val OPTION_FLAT_REVERSED:   String = "reversed"
  private val OPTION_SORTED:          String = "increasing"
  private val OPTION_SORTED_REVERSED: String = "decreasing"
  private val OPTION_LEVELS:          String = "levels"

  val OPTIONS: Array[OptionParser[TocOptions]] = Array(
    new TocLevelsOptionParser(OPTION_LEVELS),
    new BooleanOptionParser[TocOptions](OPTION_BULLET) {
      override protected def isOptionSet(options: TocOptions): Boolean    = !options.isNumbered
      override def setOptions(options:            TocOptions): TocOptions = options.withIsNumbered(false)
    },
    new BooleanOptionParser[TocOptions](OPTION_NUMBERED) {
      override protected def isOptionSet(options: TocOptions): Boolean    = options.isNumbered
      override def setOptions(options:            TocOptions): TocOptions = options.withIsNumbered(true)
    },
    new BooleanOptionParser[TocOptions](OPTION_TEXT) {
      override protected def isOptionSet(options: TocOptions): Boolean    = options.isTextOnly
      override def setOptions(options:            TocOptions): TocOptions = options.withIsTextOnly(true)
    },
    new BooleanOptionParser[TocOptions](OPTION_FORMATTED) {
      override protected def isOptionSet(options: TocOptions): Boolean    = !options.isTextOnly
      override def setOptions(options:            TocOptions): TocOptions = options.withIsTextOnly(false)
    },
    new BooleanOptionParser[TocOptions](OPTION_HIERARCHY) {
      override protected def isOptionSet(options: TocOptions): Boolean    = options.listType == TocOptions.ListType.HIERARCHY
      override def setOptions(options:            TocOptions): TocOptions = options.withListType(TocOptions.ListType.HIERARCHY)
    },
    new BooleanOptionParser[TocOptions](OPTION_FLAT) {
      override protected def isOptionSet(options: TocOptions): Boolean    = options.listType == TocOptions.ListType.FLAT
      override def setOptions(options:            TocOptions): TocOptions = options.withListType(TocOptions.ListType.FLAT)
    },
    new BooleanOptionParser[TocOptions](OPTION_FLAT_REVERSED) {
      override protected def isOptionSet(options: TocOptions): Boolean    = options.listType == TocOptions.ListType.FLAT_REVERSED
      override def setOptions(options:            TocOptions): TocOptions = options.withListType(TocOptions.ListType.FLAT_REVERSED)
    },
    new BooleanOptionParser[TocOptions](OPTION_SORTED) {
      override protected def isOptionSet(options: TocOptions): Boolean    = options.listType == TocOptions.ListType.SORTED
      override def setOptions(options:            TocOptions): TocOptions = options.withListType(TocOptions.ListType.SORTED)
    },
    new BooleanOptionParser[TocOptions](OPTION_SORTED_REVERSED) {
      override protected def isOptionSet(options: TocOptions): Boolean    = options.listType == TocOptions.ListType.SORTED_REVERSED
      override def setOptions(options:            TocOptions): TocOptions = options.withListType(TocOptions.ListType.SORTED_REVERSED)
    }
  )
}
