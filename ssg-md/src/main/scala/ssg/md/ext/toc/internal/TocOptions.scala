/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/internal/TocOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package toc
package internal

import ssg.md.Nullable
import ssg.md.util.data.{DataHolder, MutableDataHolder}
import ssg.md.util.misc.CharPredicate
import ssg.md.util.sequence.{BasedSequence, SequenceUtils}

import scala.language.implicitConversions

/** TOC options - immutable with `with*` copy methods.
  *
  * The primary constructor is private; all construction goes through the companion `create` which
  * normalizes `title` / `titleLevel` exactly as the original Java constructor does.
  */
final class TocOptions private (
  val levels: Int,
  val isHtml: Boolean,
  val isTextOnly: Boolean,
  val isNumbered: Boolean,
  val titleLevel: Int,
  val title: String,
  val listType: TocOptions.ListType,
  val isAstAddOptions: Boolean,
  val isBlankLineSpacer: Boolean,
  val divClass: String,
  val listClass: String,
  val isCaseSensitiveTocTag: Boolean
) {

  def isLevelIncluded(level: Int): Boolean =
    level >= 1 && level <= 6 && (levels & (1 << level)) != 0

  def getTitleHeading: String = {
    if (title.trim.nonEmpty) {
      val out = new StringBuilder()
      var lv = titleLevel
      while (lv > 0) {
        out.append('#')
        lv -= 1
      }
      out.append(' ')
      out.append(title)
      out.toString()
    } else ""
  }

  def setIn(dataHolder: MutableDataHolder): MutableDataHolder = {
    dataHolder.set(TocExtension.LEVELS, levels)
    dataHolder.set(TocExtension.IS_TEXT_ONLY, isTextOnly)
    dataHolder.set(TocExtension.IS_NUMBERED, isNumbered)
    dataHolder.set(TocExtension.LIST_TYPE, listType)
    dataHolder.set(TocExtension.IS_HTML, isHtml)
    dataHolder.set(TocExtension.TITLE_LEVEL, titleLevel)
    dataHolder.set(TocExtension.TITLE, title)
    dataHolder.set(TocExtension.AST_INCLUDE_OPTIONS, isAstAddOptions)
    dataHolder.set(TocExtension.BLANK_LINE_SPACER, isBlankLineSpacer)
    dataHolder.set(TocExtension.DIV_CLASS, divClass)
    dataHolder.set(TocExtension.LIST_CLASS, listClass)
    dataHolder.set(TocExtension.CASE_SENSITIVE_TOC_TAG, isCaseSensitiveTocTag)
    dataHolder
  }

  // with* methods - they go through `create` to normalize title/titleLevel
  def withLevels(newLevels: Int): TocOptions = TocOptions.create(newLevels, isHtml, isTextOnly, isNumbered, titleLevel, title, listType, isAstAddOptions, isBlankLineSpacer, divClass, listClass, isCaseSensitiveTocTag)
  def withIsHtml(v: Boolean): TocOptions = TocOptions.create(levels, v, isTextOnly, isNumbered, titleLevel, title, listType, isAstAddOptions, isBlankLineSpacer, divClass, listClass, isCaseSensitiveTocTag)
  def withIsTextOnly(v: Boolean): TocOptions = TocOptions.create(levels, isHtml, v, isNumbered, titleLevel, title, listType, isAstAddOptions, isBlankLineSpacer, divClass, listClass, isCaseSensitiveTocTag)
  def withIsNumbered(v: Boolean): TocOptions = TocOptions.create(levels, isHtml, isTextOnly, v, titleLevel, title, listType, isAstAddOptions, isBlankLineSpacer, divClass, listClass, isCaseSensitiveTocTag)
  def withTitleLevel(v: Int): TocOptions = TocOptions.create(levels, isHtml, isTextOnly, isNumbered, v, title, listType, isAstAddOptions, isBlankLineSpacer, divClass, listClass, isCaseSensitiveTocTag)
  def withTitle(v: String): TocOptions = TocOptions.create(levels, isHtml, isTextOnly, isNumbered, titleLevel, v, listType, isAstAddOptions, isBlankLineSpacer, divClass, listClass, isCaseSensitiveTocTag)
  def withListType(v: TocOptions.ListType): TocOptions = TocOptions.create(levels, isHtml, isTextOnly, isNumbered, titleLevel, title, v, isAstAddOptions, isBlankLineSpacer, divClass, listClass, isCaseSensitiveTocTag)
  def withIsAstAddOptions(v: Boolean): TocOptions = TocOptions.create(levels, isHtml, isTextOnly, isNumbered, titleLevel, title, listType, v, isBlankLineSpacer, divClass, listClass, isCaseSensitiveTocTag)
  def withIsBlankLineSpacer(v: Boolean): TocOptions = TocOptions.create(levels, isHtml, isTextOnly, isNumbered, titleLevel, title, listType, isAstAddOptions, v, divClass, listClass, isCaseSensitiveTocTag)
  def withDivClass(v: String): TocOptions = TocOptions.create(levels, isHtml, isTextOnly, isNumbered, titleLevel, title, listType, isAstAddOptions, isBlankLineSpacer, v, listClass, isCaseSensitiveTocTag)
  def withListClass(v: String): TocOptions = TocOptions.create(levels, isHtml, isTextOnly, isNumbered, titleLevel, title, listType, isAstAddOptions, isBlankLineSpacer, divClass, v, isCaseSensitiveTocTag)

  override def toString: String =
    s"TocOptions { levels=$levels, isHtml=$isHtml, isTextOnly=$isTextOnly, isNumbered=$isNumbered, titleLevel=$titleLevel, title='$title', listType=$listType, divClass='$divClass', listClass='$listClass' }"
}

object TocOptions {

  val DEFAULT_LEVELS: Int = 4 | 8 // bits for H2 & H3
  val DEFAULT_TITLE: String = "Table of Contents"
  val DEFAULT_TITLE_LEVEL: Int = 1
  val VALID_LEVELS: Int = 0x7e

  val DEFAULT: TocOptions = new TocOptions(
    DEFAULT_LEVELS, false, false, false, DEFAULT_TITLE_LEVEL, DEFAULT_TITLE,
    ListType.HIERARCHY, false, true, "", "", true
  )

  enum ListType extends java.lang.Enum[ListType] {
    case HIERARCHY, FLAT, FLAT_REVERSED, SORTED, SORTED_REVERSED
  }

  /** Normalizing factory matching the original Java main constructor. */
  @annotation.nowarn("msg=null")
  def create(
    levels: Int,
    isHtml: Boolean,
    isTextOnly: Boolean,
    isNumbered: Boolean,
    titleLevelIn: Int,
    titleIn: String,
    listType: ListType,
    isAstAddOptions: Boolean,
    isBlankLineSpacer: Boolean,
    divClass: String,
    listClass: String,
    isCaseSensitiveTocTag: Boolean
  ): TocOptions = {
    val maskedLevels = VALID_LEVELS & levels
    var titleLevel = titleLevelIn
    val resolvedTitle: String =
      if (titleIn != null) { // @nowarn - Java interop: may be null from callers
        val trimmed = SequenceUtils.trim(titleIn)
        var markers = BasedSequence.of(trimmed).countLeading(CharPredicate.HASH)
        if (markers >= 1) {
          titleLevel = Math.min(markers, 6)
          markers = titleLevel
        }
        val useTitle = SequenceUtils.trim(titleIn.subSequence(markers, titleIn.length())).toString
        if (useTitle.isEmpty) " " else useTitle
      } else ""
    titleLevel = Math.max(1, Math.min(titleLevel, 6))
    new TocOptions(maskedLevels, isHtml, isTextOnly, isNumbered, titleLevel, resolvedTitle, listType, isAstAddOptions, isBlankLineSpacer, divClass, listClass, isCaseSensitiveTocTag)
  }

  /** Constructor from DataHolder, matching original `TocOptions(DataHolder, boolean)`. */
  @annotation.nowarn("msg=null")
  def fromOptions(options: DataHolder, isSimToc: Boolean): TocOptions = {
    val t = TocExtension.TITLE.get(options)
    val titleStr = if (t == null) { if (isSimToc) DEFAULT_TITLE else "" } else t // @nowarn - Java interop: NullableDataKey may return null
    create(
      TocExtension.LEVELS.get(options),
      TocExtension.IS_HTML.get(options),
      TocExtension.IS_TEXT_ONLY.get(options),
      TocExtension.IS_NUMBERED.get(options),
      TocExtension.TITLE_LEVEL.get(options),
      titleStr,
      TocExtension.LIST_TYPE.get(options),
      TocExtension.AST_INCLUDE_OPTIONS.get(options),
      TocExtension.BLANK_LINE_SPACER.get(options),
      TocExtension.DIV_CLASS.get(options),
      TocExtension.LIST_CLASS.get(options),
      TocExtension.CASE_SENSITIVE_TOC_TAG.get(options)
    )
  }

  def getLevels(levelList: Int*): Int = {
    var levels = 0
    for (level <- levelList) {
      if (level < 1 || level > 6) throw new IllegalArgumentException("TocOption level out of range [1, 6]")
      levels |= 1 << level
    }
    levels
  }
}
