/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/MarkdownWriterBase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/MarkdownWriterBase.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package format

import ssg.md.Nullable
import ssg.md.util.misc.BitFieldSet
import ssg.md.util.sequence.{ BasedSequence, LineAppendable, LineAppendableImpl, LineInfo }
import ssg.md.util.sequence.builder.ISequenceBuilder

import scala.language.implicitConversions

@SuppressWarnings(Array("unchecked"))
abstract class MarkdownWriterBase[T <: MarkdownWriterBase[T, N, C], N, C <: NodeContext[N, C]](
  builder:       Nullable[Appendable],
  formatOptions: Int
) extends LineAppendable {

  protected val appendable: LineAppendableImpl = new LineAppendableImpl(builder.getOrElse(null), formatOptions)
  appendable.setOptions(appendable.getOptions | LineAppendable.F_PREFIX_PRE_FORMATTED)

  protected var context: C = scala.compiletime.uninitialized

  def this() =
    this(Nullable(null), 0)

  def this(formatOptions: Int) =
    this(Nullable(null), formatOptions)

  override def toString: String =
    appendable.toString

  def setContext(context: C): Unit =
    this.context = context

  def getContext: C = context

  def tailBlankLine(): T =
    tailBlankLine(1)

  def lastBlockQuoteChildPrefix(prefix: BasedSequence): BasedSequence

  def tailBlankLine(count: Int): T = {
    val prefix         = appendable.getPrefix
    val replacedPrefix = lastBlockQuoteChildPrefix(prefix)
    if (!replacedPrefix.equals(prefix)) {
      // Needed to not add block quote prefix to trailing blank lines
      appendable.setPrefix(replacedPrefix, false)
      appendable.blankLine(count)
      appendable.setPrefix(prefix, false)
    } else {
      appendable.blankLine(count)
    }

    this.asInstanceOf[T]
  }

  // @formatter:off
  override def iterator(): java.util.Iterator[LineInfo] = { appendable.iterator() }
  override def getLines(maxTrailingBlankLines: Int, startLine: Int, endLine: Int, withPrefixes: Boolean): java.lang.Iterable[BasedSequence] = { appendable.getLines(maxTrailingBlankLines, startLine, endLine, true) }
  override def getLinesInfo(maxTrailingBlankLines: Int, startLine: Int, endLine: Int): java.lang.Iterable[LineInfo] = { appendable.getLinesInfo(maxTrailingBlankLines, startLine, endLine) }
  override def setPrefixLength(lineIndex: Int, prefixEndIndex: Int): Unit = { appendable.setPrefixLength(lineIndex, prefixEndIndex) }
  override def insertLine(lineIndex: Int, prefix: CharSequence, text: CharSequence): Unit = { appendable.insertLine(lineIndex, prefix, text) }
  override def setLine(lineIndex: Int, prefix: CharSequence, text: CharSequence): Unit = { appendable.setLine(lineIndex, prefix, text) }
  override def appendTo[A <: Appendable](out: A, withPrefixes: Boolean, maxBlankLines: Int, maxTrailingBlankLines: Int, startLine: Int, endLine: Int): A = { appendable.appendTo(out, withPrefixes, maxBlankLines, maxTrailingBlankLines, startLine, endLine) }
  override def endsWithEOL: Boolean = { appendable.endsWithEOL }
  override def isPendingSpace: Boolean = { appendable.isPendingSpace }
  override def isPreFormatted: Boolean = { appendable.isPreFormatted }
  override def getTrailingBlankLines(endLine: Int): Int = { appendable.getTrailingBlankLines(endLine) }
  override def column(): Int = { appendable.column() }
  override def getLineCount: Int = { appendable.getLineCount }
  override def getLineCountWithPending: Int = { appendable.getLineCountWithPending }
  override def getOptions: Int = { appendable.getOptions }
  override def getPendingSpace: Int = { appendable.getPendingSpace }
  override def getPendingEOL: Int = { appendable.getPendingEOL }
  override def offset(): Int = { appendable.offset() }
  override def offsetWithPending(): Int = { appendable.offsetWithPending() }
  override def getAfterEolPrefixDelta: Int = { appendable.getAfterEolPrefixDelta }
  override def getBuilder: ISequenceBuilder[?, ?] = { appendable.getBuilder }
  override def getPrefix: BasedSequence = { appendable.getPrefix }
  override def getBeforeEolPrefix: BasedSequence = { appendable.getBeforeEolPrefix }
  override def getLineInfo(lineIndex: Int): LineInfo = { appendable.getLineInfo(lineIndex) }
  override def getLine(lineIndex: Int): BasedSequence = { appendable.getLine(lineIndex) }
  override def getIndentPrefix: BasedSequence = { appendable.getIndentPrefix }
  override def toSequence(maxBlankLines: Int, maxTrailingBlankLines: Int, withPrefixes: Boolean): CharSequence = { appendable.toSequence(maxBlankLines, maxTrailingBlankLines, withPrefixes) }
  override def toString(maxBlankLines: Int, maxTrailingBlankLines: Int, withPrefixes: Boolean): String = { appendable.toString(maxBlankLines, maxTrailingBlankLines, withPrefixes) }
  override def getOptionSet: BitFieldSet[LineAppendable.Options] = { appendable.getOptionSet }
  override def removeExtraBlankLines(maxBlankLines: Int, maxTrailingBlankLines: Int, startLine: Int, endLine: Int): LineAppendable = { appendable.removeExtraBlankLines(maxBlankLines, maxTrailingBlankLines, startLine, endLine); this }
  override def removeLines(startLine: Int, endLine: Int): LineAppendable = { appendable.removeLines(startLine, endLine); this }
  override def pushOptions(): LineAppendable = { appendable.pushOptions(); this }
  override def popOptions(): LineAppendable = { appendable.popOptions(); this }
  override def changeOptions(addFlags: Int, removeFlags: Int): LineAppendable = { appendable.changeOptions(addFlags, removeFlags); this }
  override def addIndentOnFirstEOL(listener: Runnable): LineAppendable = { appendable.addIndentOnFirstEOL(listener); this }
  override def addPrefix(prefix: CharSequence): LineAppendable = { appendable.addPrefix(prefix); this }
  override def addPrefix(prefix: CharSequence, afterEol: Boolean): LineAppendable = { appendable.addPrefix(prefix, afterEol); this }
  override def append(c: Char): LineAppendable = { appendable.append(c); this }
  override def append(csq: CharSequence): LineAppendable = { appendable.append(csq); this }
  override def append(csq: CharSequence, start: Int, end: Int): LineAppendable = { appendable.append(csq, start, end); this }
  override def append(lines: LineAppendable, startLine: Int, endLine: Int, withPrefixes: Boolean): LineAppendable = { appendable.append(lines, startLine, endLine, withPrefixes); this }
  override def blankLine(): LineAppendable = { appendable.blankLine(); this }
  override def blankLine(count: Int): LineAppendable = { appendable.blankLine(count); this }
  override def blankLineIf(predicate: Boolean): LineAppendable = { appendable.blankLineIf(predicate); this }
  override def closePreFormatted(): LineAppendable = { appendable.closePreFormatted(); this }
  override def indent(): LineAppendable = { appendable.indent(); this }
  override def line(): LineAppendable = { appendable.line(); this }
  override def lineIf(predicate: Boolean): LineAppendable = { appendable.lineIf(predicate); this }
  override def lineOnFirstText(value: Boolean): LineAppendable = { appendable.lineOnFirstText(value); this }
  override def lineWithTrailingSpaces(count: Int): LineAppendable = { appendable.lineWithTrailingSpaces(count); this }
  override def openPreFormatted(keepIndent: Boolean): LineAppendable = { appendable.openPreFormatted(keepIndent); this }
  override def popPrefix(): LineAppendable = { appendable.popPrefix(); this }
  override def popPrefix(afterEol: Boolean): LineAppendable = { appendable.popPrefix(afterEol); this }
  override def pushPrefix(): LineAppendable = { appendable.pushPrefix(); this }
  override def removeIndentOnFirstEOL(listener: Runnable): LineAppendable = { appendable.removeIndentOnFirstEOL(listener); this }
  override def append(c: Char, count: Int): LineAppendable = { appendable.append(c, count); this }
  override def setIndentPrefix(prefix: Nullable[CharSequence]): LineAppendable = { appendable.setIndentPrefix(prefix); this }
  override def setOptions(flags: Int): LineAppendable = { appendable.setOptions(flags); this }
  override def setPrefix(prefix: CharSequence): LineAppendable = { appendable.setPrefix(prefix); this }
  override def setPrefix(prefix: Nullable[CharSequence], afterEol: Boolean): LineAppendable = { appendable.setPrefix(prefix, afterEol); this }
  override def unIndent(): LineAppendable = { appendable.unIndent(); this }
  override def unIndentNoEol(): LineAppendable = { appendable.unIndentNoEol(); this }
  // @formatter:on
}
