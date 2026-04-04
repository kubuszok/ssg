/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/LineAppendable.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence

import ssg.md.Nullable
import ssg.md.util.misc.{ BitFieldSet, EnumBitField }
import ssg.md.util.sequence.builder.ISequenceBuilder

import java.io.IOException

/** Used to collect line text for further processing
  *
  * control output of new lines limiting them to terminate text but not create blank lines, and control number of blank lines output, eliminate spaces before and after an \n, except in prefixes and
  * indents controlled by this class.
  *
  * allows appending unmodified text in preformatted regions created by [[openPreFormatted]] and [[closePreFormatted]]
  *
  * consecutive \n in the data are going go be collapsed to a single \n. To get blank lines use [[blankLine()]] or [[blankLine(int)]]
  *
  * tab is converted to spaces if [[LineAppendable.F_CONVERT_TABS]] or [[LineAppendable.F_COLLAPSE_WHITESPACE]] option is selected
  *
  * spaces before and after \n are removed controlled by [[LineAppendable.F_TRIM_TRAILING_WHITESPACE]] and [[LineAppendable.F_TRIM_LEADING_WHITESPACE]]
  *
  * use [[line()]], [[lineIf]], [[blankLine()]] as an alternative to appending \n. use [[blankLineIf]] and [[blankLine(int)]] for appending blank lines.
  */
@SuppressWarnings(Array("UnusedReturnValue", "SameParameterValue"))
trait LineAppendable extends Appendable with java.lang.Iterable[LineInfo] {

  /** Get current options as bit mask flags
    *
    * @return
    *   option flags
    */
  def getOptions: Int = getOptionSet.toInt

  def getEmptyAppendable: LineAppendable

  /** Make a copy of this appendable with the given line range
    */
  def copyAppendable(startLine: Int, endLine: Int, withPrefixes: Boolean): LineAppendable =
    getEmptyAppendable.append(this, startLine, endLine, withPrefixes)

  def copyAppendable(startLine: Int, endLine: Int): LineAppendable =
    getEmptyAppendable.append(this, startLine, endLine, false)

  def copyAppendable(startLine: Int): LineAppendable =
    getEmptyAppendable.append(this, startLine, Integer.MAX_VALUE, false)

  def copyAppendable(): LineAppendable =
    getEmptyAppendable.append(this, 0, Integer.MAX_VALUE, false)

  def copyAppendable(withPrefixes: Boolean): LineAppendable =
    getEmptyAppendable.append(this, 0, Integer.MAX_VALUE, withPrefixes)

  /** Get current options as set which can be used to modify options
    */
  def getOptionSet: BitFieldSet[LineAppendable.Options]

  def pushOptions(): LineAppendable

  def popOptions(): LineAppendable

  def noTrimLeading(): LineAppendable = changeOptions(0, LineAppendable.F_TRIM_LEADING_WHITESPACE)

  def trimLeading(): LineAppendable = changeOptions(LineAppendable.F_TRIM_LEADING_WHITESPACE, 0)

  def preserveSpaces(): LineAppendable = changeOptions(0, LineAppendable.F_TRIM_LEADING_WHITESPACE | LineAppendable.F_COLLAPSE_WHITESPACE)

  def noPreserveSpaces(): LineAppendable = changeOptions(LineAppendable.F_TRIM_LEADING_WHITESPACE | LineAppendable.F_COLLAPSE_WHITESPACE, 0)

  def removeOptions(flags: Int): LineAppendable = changeOptions(0, flags)

  def addOptions(flags: Int): LineAppendable = changeOptions(flags, 0)

  def changeOptions(addFlags: Int, removeFlags: Int): LineAppendable

  def setOptions(flags: Int): LineAppendable = setOptions(LineAppendable.toOptionSet(flags))

  def setOptions(options: LineAppendable.Options*): LineAppendable = setOptions(LineAppendable.toOptionSet(options*).toInt)

  def setOptions(options: BitFieldSet[LineAppendable.Options]): LineAppendable = setOptions(options.toInt)

  /** Get builder used for accumulation
    */
  def getBuilder: ISequenceBuilder[?, ?]

  /** Get trailing blank line count ending on given line
    */
  def getTrailingBlankLines(endLine: Int): Int

  def getTrailingBlankLines: Int = getTrailingBlankLines(getLineCountWithPending)

  def endsWithEOL: Boolean

  // these methods are monitored for content and formatting applied
  override def append(csq: CharSequence): LineAppendable

  override def append(csq: CharSequence, start: Int, end: Int): LineAppendable

  override def append(c: Char): LineAppendable

  def append(c: Char, count: Int): LineAppendable

  def appendAll(sequences: java.lang.Iterable[CharSequence]): LineAppendable = {
    val iter = sequences.iterator()
    while (iter.hasNext)
      append(iter.next())
    this
  }

  /** Append lines from another line formatting appendable.
    *
    * NOTE: does not apply formatting options. Instead, appends already formatted lines as is
    *
    * If there is an accumulating line, it will be terminated by an EOL before appending lines
    */
  def append(lineAppendable: LineAppendable, startLine: Int, endLine: Int, withPrefixes: Boolean): LineAppendable

  def append(lineAppendable: LineAppendable): LineAppendable =
    append(lineAppendable, 0, Integer.MAX_VALUE, true)

  def append(lineAppendable: LineAppendable, withPrefixes: Boolean): LineAppendable =
    append(lineAppendable, 0, Integer.MAX_VALUE, withPrefixes)

  /** Add a new line if there was any unterminated text appended or if this is a preformatted region */
  def line(): LineAppendable

  /** Add a new line, keep trailing spaces if there was any unterminated text appended */
  def lineWithTrailingSpaces(count: Int): LineAppendable

  /** Add a new line, if predicate is true and line() would add an EOL. */
  def lineIf(predicate: Boolean): LineAppendable

  /** Add a blank line, if there is not one already appended. */
  def blankLine(): LineAppendable

  /** Add a blank line, if predicate is true and there isn't already blank lines appended. */
  def blankLineIf(predicate: Boolean): LineAppendable

  /** Add blank lines, if there isn't already given number of blank lines appended. */
  def blankLine(count: Int): LineAppendable

  def isPreFormatted: Boolean

  /** Open preformatted section and suspend content modification */
  def openPreFormatted(addPrefixToFirstLine: Boolean): LineAppendable

  /** Close preformatted section and suspend content modification */
  def closePreFormatted(): LineAppendable

  /** Increase the indent level, will terminate the current line if there is unterminated text */
  def indent(): LineAppendable

  /** Decrease the indent level, min level is 0, will terminate the current line if there is unterminated text */
  def unIndent(): LineAppendable

  /** Decrease the indent level, if there is unterminated text then unindented prefix is to be applied after the next EOL. */
  def unIndentNoEol(): LineAppendable

  def getIndentPrefix: BasedSequence

  def setIndentPrefix(prefix: Nullable[CharSequence]): LineAppendable

  /** Get prefix being applied to all lines, even in pre-formatted sections. This is the prefix that will be set after EOL */
  def getPrefix: BasedSequence

  /** Get prefix used before EOL */
  def getBeforeEolPrefix: BasedSequence

  def addPrefix(prefix: CharSequence, afterEol: Boolean): LineAppendable

  def setPrefix(prefix: Nullable[CharSequence], afterEol: Boolean): LineAppendable

  def addPrefix(prefix: CharSequence): LineAppendable = addPrefix(prefix, getPendingEOL == 0)

  def setPrefix(prefix: CharSequence): LineAppendable = setPrefix(Nullable(prefix), getPendingEOL == 0)

  def pushPrefix(): LineAppendable

  def popPrefix(afterEol: Boolean): LineAppendable

  def popPrefix(): LineAppendable = popPrefix(false)

  /** Get pending prefix after EOL */
  def getAfterEolPrefixDelta: Int

  /** Get column offset after last append */
  def column(): Int

  /** Get text offset of all output lines, excluding any text for the last line being accumulated */
  def offset(): Int

  /** Get offset after last append as if EOL was added but without the EOL itself */
  def offsetWithPending(): Int

  /** Test if trailing text ends in space or tab */
  def isPendingSpace: Boolean

  /** Get number of spaces at end of pending text */
  def getPendingSpace: Int

  /** Get number of EOLs at end of appendable, this is actually number of tail blank lines */
  def getPendingEOL: Int

  def lineOnFirstText(value: Boolean): LineAppendable

  def setLineOnFirstText(): LineAppendable = lineOnFirstText(true)

  def clearLineOnFirstText(): LineAppendable = lineOnFirstText(false)

  /** Add an indent on first EOL appended and run runnable */
  def addIndentOnFirstEOL(listener: Runnable): LineAppendable

  /** Remove runnable, has no effect if EOL was already appended and runnable was run */
  def removeIndentOnFirstEOL(listener: Runnable): LineAppendable

  /** Get the number of lines appended, not including any unterminated ones */
  def getLineCount: Int

  def isEmpty: Boolean = getLineCountWithPending == 0

  def isNotEmpty: Boolean = getLineCountWithPending != 0

  /** Get the number of lines appended, including any unterminated ones */
  def getLineCountWithPending: Int

  def getLineInfo(lineIndex: Int): LineInfo

  def get(lineIndex: Int): LineInfo = getLineInfo(lineIndex)

  def getLine(lineIndex: Int): BasedSequence

  override def iterator(): java.util.Iterator[LineInfo]

  def getLines(maxTrailingBlankLines: Int, startLine: Int, endLine: Int, withPrefixes: Boolean): java.lang.Iterable[BasedSequence]

  def getLines(maxTrailingBlankLines: Int): java.lang.Iterable[BasedSequence] =
    getLines(maxTrailingBlankLines, 0, Integer.MAX_VALUE, true)

  def getLines(): java.lang.Iterable[BasedSequence] =
    getLines(Integer.MAX_VALUE, 0, Integer.MAX_VALUE, true)

  def getLines(maxTrailingBlankLines: Int, withPrefixes: Boolean): java.lang.Iterable[BasedSequence] =
    getLines(maxTrailingBlankLines, 0, Integer.MAX_VALUE, withPrefixes)

  def getLines(withPrefixes: Boolean): java.lang.Iterable[BasedSequence] =
    getLines(Integer.MAX_VALUE, 0, Integer.MAX_VALUE, withPrefixes)

  def getLinesInfo(maxTrailingBlankLines: Int, startLine: Int, endLine: Int): java.lang.Iterable[LineInfo]

  def getLinesInfo(maxTrailingBlankLines: Int): java.lang.Iterable[LineInfo] =
    getLinesInfo(maxTrailingBlankLines, 0, Integer.MAX_VALUE)

  def getLinesInfo(): java.lang.Iterable[LineInfo] =
    getLinesInfo(Integer.MAX_VALUE, 0, Integer.MAX_VALUE)

  def getLineContent(lineIndex: Int): BasedSequence = {
    val lineInfo = getLineInfo(lineIndex)
    val line     = getLine(lineIndex)
    line.subSequence(lineInfo.prefixLength, lineInfo.prefixLength + lineInfo.textLength)
  }

  def getLinePrefix(lineIndex: Int): BasedSequence = {
    val lineInfo = getLineInfo(lineIndex)
    val line     = getLine(lineIndex)
    line.subSequence(0, lineInfo.prefixLength)
  }

  def setPrefixLength(lineIndex: Int, prefixLength: Int): Unit

  def setLine(lineIndex: Int, prefix: CharSequence, text: CharSequence): Unit

  def insertLine(lineIndex: Int, prefix: CharSequence, text: CharSequence): Unit

  def removeLines(startLine: Int, endLine: Int): LineAppendable

  def toString(maxBlankLines: Int, maxTrailingBlankLines: Int, withPrefixes: Boolean): String

  def toString(maxBlankLines: Int, maxTrailingBlankLines: Int): String =
    toString(maxBlankLines, maxTrailingBlankLines, true)

  def toString(maxBlankLines: Int, withPrefixes: Boolean): String =
    toString(maxBlankLines, maxBlankLines, withPrefixes)

  def toString(withPrefixes: Boolean): String =
    toString(Integer.MAX_VALUE, Integer.MAX_VALUE, withPrefixes)

  def toString(maxBlankLines: Int): String =
    toString(maxBlankLines, maxBlankLines, true)

  def toSequence(maxBlankLines: Int, maxTrailingBlankLines: Int, withPrefixes: Boolean): CharSequence

  def toSequence(maxBlankLines: Int, maxTrailingBlankLines: Int): CharSequence =
    toSequence(maxBlankLines, maxTrailingBlankLines, true)

  def toSequence(maxBlankLines: Int, withPrefixes: Boolean): CharSequence =
    toSequence(maxBlankLines, maxBlankLines, withPrefixes)

  def toSequence(withPrefixes: Boolean): CharSequence =
    toSequence(Integer.MAX_VALUE, Integer.MAX_VALUE, withPrefixes)

  def toSequence(): CharSequence =
    toSequence(Integer.MAX_VALUE, Integer.MAX_VALUE, true)

  @deprecated("Use appendTo with explicit parameters", "")
  def appendTo[T <: Appendable](out: T, maxTrailingBlankLines: Int): T =
    appendTo(out, Integer.MAX_VALUE, maxTrailingBlankLines)

  def appendTo[T <: Appendable](out: T, withPrefixes: Boolean, maxBlankLines: Int, maxTrailingBlankLines: Int, startLine: Int, endLine: Int): T

  def appendTo[T <: Appendable](out: T, maxBlankLines: Int, maxTrailingBlankLines: Int, startLine: Int, endLine: Int): T =
    appendTo(out, true, maxBlankLines, maxTrailingBlankLines, startLine, endLine)

  def appendTo[T <: Appendable](out: T, maxBlankLines: Int, maxTrailingBlankLines: Int): T =
    appendTo(out, maxBlankLines, maxTrailingBlankLines, 0, Integer.MAX_VALUE)

  def appendTo[T <: Appendable](out: T): T =
    appendTo(out, 0, 0, 0, Integer.MAX_VALUE)

  def appendToSilently[T <: Appendable](out: T, withPrefixes: Boolean, maxBlankLines: Int, maxTrailingBlankLines: Int, startLine: Int, endLine: Int): T = {
    try
      appendTo(out, withPrefixes, maxBlankLines, maxTrailingBlankLines, startLine, endLine)
    catch {
      case _: IOException => // ignored
    }
    out
  }

  def appendToSilently[T <: Appendable](out: T, maxBlankLines: Int, maxTrailingBlankLines: Int, startLine: Int, endLine: Int): T =
    appendToSilently(out, true, maxBlankLines, maxTrailingBlankLines, startLine, endLine)

  def appendToSilently[T <: Appendable](out: T, maxBlankLines: Int, maxTrailingBlankLines: Int): T =
    appendToSilently(out, maxBlankLines, maxTrailingBlankLines, 0, Integer.MAX_VALUE)

  def appendToSilently[T <: Appendable](out: T): T =
    appendToSilently(out, 0, 0, 0, Integer.MAX_VALUE)

  def removeExtraBlankLines(maxBlankLines: Int, maxTrailingBlankLines: Int, startLine: Int, endLine: Int): LineAppendable

  def removeExtraBlankLines(maxBlankLines: Int, maxTrailingBlankLines: Int): LineAppendable =
    removeExtraBlankLines(maxBlankLines, maxTrailingBlankLines, 0, Integer.MAX_VALUE)
}

object LineAppendable {

  enum Options extends java.lang.Enum[Options] {
    case CONVERT_TABS // expand tabs on column multiples of 4
    case COLLAPSE_WHITESPACE // collapse multiple tabs and spaces to single space, implies CONVERT_TABS
    case TRIM_TRAILING_WHITESPACE // don't output trailing whitespace
    case PASS_THROUGH // just pass everything through to appendable with no formatting
    case TRIM_LEADING_WHITESPACE // allow leading spaces on a line, else remove
    case TRIM_LEADING_EOL // allow EOL at offset 0
    case PREFIX_PRE_FORMATTED // when prefixing lines, prefix pre-formatted lines
  }

  given EnumBitField[Options] with {
    def elementType: Class[Options] = classOf[Options]
    def typeName:    String         = "Options"
    val values:      Array[Options] = Options.values
    val bitMasks:    Array[Long]    = EnumBitField.computeBitMasks(values, "Options")
  }

  val O_CONVERT_TABS:             Options              = Options.CONVERT_TABS
  val O_COLLAPSE_WHITESPACE:      Options              = Options.COLLAPSE_WHITESPACE
  val O_TRIM_TRAILING_WHITESPACE: Options              = Options.TRIM_TRAILING_WHITESPACE
  val O_PASS_THROUGH:             Options              = Options.PASS_THROUGH
  val O_TRIM_LEADING_WHITESPACE:  Options              = Options.TRIM_LEADING_WHITESPACE
  val O_TRIM_LEADING_EOL:         Options              = Options.TRIM_LEADING_EOL
  val O_PREFIX_PRE_FORMATTED:     Options              = Options.PREFIX_PRE_FORMATTED
  val O_FORMAT_ALL:               BitFieldSet[Options] = BitFieldSet.of(O_CONVERT_TABS, O_COLLAPSE_WHITESPACE, O_TRIM_TRAILING_WHITESPACE, O_TRIM_LEADING_WHITESPACE)

  val F_CONVERT_TABS:             Int = BitFieldSet.intMask(O_CONVERT_TABS)
  val F_COLLAPSE_WHITESPACE:      Int = BitFieldSet.intMask(O_COLLAPSE_WHITESPACE)
  val F_TRIM_TRAILING_WHITESPACE: Int = BitFieldSet.intMask(O_TRIM_TRAILING_WHITESPACE)
  val F_PASS_THROUGH:             Int = BitFieldSet.intMask(O_PASS_THROUGH)
  val F_TRIM_LEADING_WHITESPACE:  Int = BitFieldSet.intMask(O_TRIM_LEADING_WHITESPACE)
  val F_TRIM_LEADING_EOL:         Int = BitFieldSet.intMask(O_TRIM_LEADING_EOL)
  val F_PREFIX_PRE_FORMATTED:     Int = BitFieldSet.intMask(O_PREFIX_PRE_FORMATTED)
  val F_FORMAT_ALL:               Int = F_CONVERT_TABS | F_COLLAPSE_WHITESPACE | F_TRIM_TRAILING_WHITESPACE | F_TRIM_LEADING_WHITESPACE | F_TRIM_LEADING_EOL
  val F_WHITESPACE_REMOVAL:       Int = F_COLLAPSE_WHITESPACE | F_TRIM_TRAILING_WHITESPACE | F_TRIM_LEADING_WHITESPACE

  // Deprecated constants — use F_ prefixed constants
  @deprecated("Use F_CONVERT_TABS", "") val CONVERT_TABS:                                                                       Int = F_CONVERT_TABS
  @deprecated("Use F_COLLAPSE_WHITESPACE", "") val COLLAPSE_WHITESPACE:                                                         Int = F_COLLAPSE_WHITESPACE
  @deprecated("Use F_TRIM_TRAILING_WHITESPACE", "") val TRIM_TRAILING_WHITESPACE:                                               Int = F_TRIM_TRAILING_WHITESPACE
  @deprecated("Use F_PASS_THROUGH", "") val PASS_THROUGH:                                                                       Int = F_PASS_THROUGH
  @deprecated("ALLOW_LEADING_WHITESPACE is now inverted and named F_TRIM_LEADING_WHITESPACE", "") val ALLOW_LEADING_WHITESPACE: Int = 0
  @deprecated("Use F_TRIM_LEADING_WHITESPACE", "") val TRIM_LEADING_WHITESPACE:                                                 Int = F_TRIM_LEADING_WHITESPACE
  @deprecated("ALLOW_LEADING_EOL is now inverted and named F_TRIM_LEADING_EOL", "") val ALLOW_LEADING_EOL:                      Int = 0
  @deprecated("Use F_PREFIX_PRE_FORMATTED", "") val PREFIX_PRE_FORMATTED:                                                       Int = F_PREFIX_PRE_FORMATTED
  @deprecated("Use F_FORMAT_ALL", "") val FORMAT_ALL:                                                                           Int = F_FORMAT_ALL

  def toOptionSet(options: Int): BitFieldSet[Options] =
    BitFieldSet.of(classOf[Options], options)

  def toOptionSet(options: Options*): BitFieldSet[Options] =
    BitFieldSet.of(classOf[Options], options.toArray)

  def combinedPrefix(prefix: Nullable[CharSequence], suffix: Nullable[CharSequence]): CharSequence = {
    val hasPrefix = prefix.isDefined && prefix.get.length() > 0
    val hasSuffix = suffix.isDefined && suffix.get.length() > 0
    if (hasPrefix && hasSuffix) String.valueOf(prefix.get) + suffix.get
    else if (hasPrefix) prefix.get
    else if (hasSuffix) suffix.get
    else BasedSequence.NULL
  }
}
