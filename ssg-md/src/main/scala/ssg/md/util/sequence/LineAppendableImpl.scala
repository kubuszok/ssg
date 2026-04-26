/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/LineAppendableImpl.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/LineAppendableImpl.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package sequence

import ssg.md.Nullable
import ssg.md.util.collection.iteration.{ Indexed, IndexedItemIterable, IndexedItemIterator }
import ssg.md.util.misc.{ BitFieldSet, CharPredicate, Pair }
import ssg.md.util.sequence.builder.{ ISequenceBuilder, StringSequenceBuilder }

import java.io.IOException
import java.util as ju
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

class LineAppendableImpl(
  appendableIn:  Nullable[Appendable],
  formatOptions: BitFieldSet[LineAppendable.Options]
) extends LineAppendable {

  import LineAppendable.*

  private val EOL_CHAR: Char = '\n'

  private val passThrough: Boolean              = formatOptions.any(F_PASS_THROUGH)
  private val options:     BitFieldSet[Options] = formatOptions

  // pre-formatted nesting level, while >0 all text is passed through as is and not monitored
  private var preFormattedNesting:         Int = 0
  private var preFormattedFirstLine:       Int = -1
  private var preFormattedFirstLineOffset: Int = 0
  private var preFormattedLastLine:        Int = -1
  private var preFormattedLastLineOffset:  Int = 0

  // accumulated text and line information
  private var appendable: ISequenceBuilder[?, ?] =
    if (appendableIn.isDefined) {
      appendableIn.get match {
        case isb: ISequenceBuilder[?, ?] => isb.getBuilder
        case la:  LineAppendable         => la.getBuilder
        case _ => StringSequenceBuilder.emptyBuilder()
      }
    } else {
      StringSequenceBuilder.emptyBuilder()
    }

  private[sequence] val lines: ju.ArrayList[LineInfo] = new ju.ArrayList[LineInfo]()

  // indent level to use after the next \n and before text is appended
  private var prefix:            CharSequence                                 = BasedSequence.EMPTY
  private var prefixAfterEol:    CharSequence                                 = BasedSequence.EMPTY
  private var indentPrefix:      CharSequence                                 = BasedSequence.EMPTY
  private val prefixStack:       scala.collection.mutable.Stack[CharSequence] = scala.collection.mutable.Stack()
  private val indentPrefixStack: scala.collection.mutable.Stack[Boolean]      = scala.collection.mutable.Stack()

  // current line being accumulated
  private var allWhitespace:               Boolean                             = true
  private var lastWasWhitespace:           Boolean                             = false
  private var eolOnFirstText:              Int                                 = 0
  private val indentsOnFirstEol:           ju.ArrayList[Runnable]              = new ju.ArrayList[Runnable]()
  private val optionStack:                 scala.collection.mutable.Stack[Int] = scala.collection.mutable.Stack()
  private[sequence] var modificationCount: Int                                 = 0

  def this(formatOptions: Int) =
    this(Nullable.empty[Appendable], LineAppendable.toOptionSet(formatOptions))

  def this(builder: Nullable[Appendable], formatOptions: Int) =
    this(builder, LineAppendable.toOptionSet(formatOptions))

  override def getEmptyAppendable: LineAppendable =
    new LineAppendableImpl(Nullable(this: Appendable), getOptions)

  override def getOptionSet: BitFieldSet[Options] = options

  override def setOptions(flags: Int): LineAppendable = {
    options.setAll(flags)
    this
  }

  override def pushOptions(): LineAppendable = {
    optionStack.push(options.toInt)
    this
  }

  override def popOptions(): LineAppendable = {
    if (optionStack.isEmpty) throw new IllegalStateException("Option stack is empty")
    val mask = optionStack.pop()
    options.setAll(mask.toLong)
    this
  }

  override def changeOptions(addFlags: Int, removeFlags: Int): LineAppendable = {
    if ((addFlags & removeFlags) != 0) {
      throw new IllegalStateException(s"Add flags:$addFlags and remove flags:$removeFlags overlap:${addFlags & removeFlags}")
    }
    options.orMask(addFlags)
    options.andNotMask(removeFlags)
    this
  }

  private def any(flags: Int): Boolean = options.any(flags)

  @annotation.nowarn("msg=unused private member") // used by line processing logic not yet fully ported
  private def isConvertingTabs: Boolean = any(F_CONVERT_TABS | F_COLLAPSE_WHITESPACE)

  private def isTrimTrailingWhitespace: Boolean = any(F_TRIM_TRAILING_WHITESPACE)

  @annotation.nowarn("msg=unused private member") // used by line processing logic not yet fully ported
  private def isTrimLeadingWhitespace: Boolean = any(F_TRIM_LEADING_WHITESPACE)

  @annotation.nowarn("msg=unused private member") // used by line processing logic not yet fully ported
  private def isCollapseWhitespace: Boolean = any(F_COLLAPSE_WHITESPACE)

  override def getIndentPrefix: BasedSequence = BasedSequence.of(Nullable(indentPrefix))

  override def setIndentPrefix(prefix: Nullable[CharSequence]): LineAppendable = {
    indentPrefix = if (prefix.isDefined) prefix.get else BasedSequence.NULL
    this
  }

  override def getPrefix: BasedSequence = BasedSequence.of(Nullable(prefixAfterEol))

  override def getBeforeEolPrefix: BasedSequence = BasedSequence.of(Nullable(prefix))

  override def addPrefix(prefix: CharSequence, afterEol: Boolean): LineAppendable = {
    if (!passThrough && prefix.length() != 0) {
      if (afterEol) {
        prefixAfterEol = LineAppendable.combinedPrefix(Nullable(prefixAfterEol), Nullable(prefix))
      } else {
        this.prefix = LineAppendable.combinedPrefix(Nullable(prefixAfterEol), Nullable(prefix))
        prefixAfterEol = this.prefix
      }
    }
    this
  }

  override def getAfterEolPrefixDelta: Int = prefixAfterEol.length() - prefix.length()

  override def setPrefix(prefix: Nullable[CharSequence], afterEol: Boolean): LineAppendable = {
    if (!passThrough) {
      if (afterEol) {
        prefixAfterEol = if (prefix.isDefined) prefix.get else BasedSequence.NULL
      } else {
        this.prefix = if (prefix.isDefined) prefix.get else BasedSequence.NULL
        prefixAfterEol = this.prefix
      }
    }
    this
  }

  override def indent(): LineAppendable = {
    if (!passThrough) {
      line()
      rawIndent()
    }
    this
  }

  private def rawIndent(): Unit = {
    prefixStack.push(prefixAfterEol)
    prefix = LineAppendable.combinedPrefix(Nullable(prefixAfterEol), Nullable(indentPrefix))
    prefixAfterEol = prefix
    indentPrefixStack.push(true)
  }

  private def rawUnIndent(): Unit = {
    if (prefixStack.isEmpty) throw new IllegalStateException("unIndent with an empty stack")
    if (!indentPrefixStack.top) throw new IllegalStateException("unIndent for an element added by pushPrefix(), use popPrefix() instead")

    prefix = prefixStack.pop()
    prefixAfterEol = prefix
    indentPrefixStack.pop()
  }

  override def unIndent(): LineAppendable = {
    if (!passThrough) {
      line()
      rawUnIndent()
    }
    this
  }

  override def unIndentNoEol(): LineAppendable = {
    if (!passThrough) {
      if (endsWithEOL) {
        rawUnIndent()
      } else {
        val savedPrefix = this.prefix
        rawUnIndent()
        prefixAfterEol = this.prefix
        this.prefix = savedPrefix
      }
    }
    this
  }

  override def pushPrefix(): LineAppendable = {
    if (!passThrough) {
      prefixStack.push(prefixAfterEol)
      indentPrefixStack.push(false)
    }
    this
  }

  override def popPrefix(afterEol: Boolean): LineAppendable = {
    if (!passThrough) {
      if (prefixStack.isEmpty) throw new IllegalStateException("popPrefix with an empty stack")
      if (indentPrefixStack.top) throw new IllegalStateException("popPrefix for element added by indent(), use unIndent() instead")

      prefixAfterEol = prefixStack.pop()
      if (!afterEol) {
        prefix = prefixAfterEol
      }
      indentPrefixStack.pop()
    }
    this
  }

  private[sequence] def getLastLineInfo: LineInfo =
    if (lines.isEmpty) LineInfo.NULL else lines.get(lines.size() - 1)

  private def isTrailingBlankLine: Boolean =
    appendable.length == 0 && getLastLineInfo.isBlankText

  private[sequence] def lastNonBlankLine(endLine: Int): Int =
    if (endLine > lines.size() && appendable.length > 0 && !allWhitespace) {
      lines.size()
    } else {
      var i = Math.min(lines.size(), endLine)
      boundary[Int] {
        while (i > 0) {
          i -= 1
          val lineInfo = lines.get(i)
          if (!lineInfo.isBlankText) break(i)
        }
        i
      }
    }

  override def getTrailingBlankLines(endLine: Int): Int = {
    val useEndLine = Math.min(lines.size(), endLine)
    useEndLine - lastNonBlankLine(useEndLine) - 1
  }

  override def endsWithEOL: Boolean =
    appendable.length == 0 && getLastLineInfo.isNotNull

  private def getLineRange(start: Int, end: Int, prefix: CharSequence): LineInfo = {
    assert(start <= end)
    val sequence    = appendable.toSequence
    val eolNullable = SequenceUtils.trimmedEOL(sequence)
    var eol: CharSequence = if (eolNullable.isDefined) eolNullable.get else null // @nowarn — null needed for length check below
    if (eol == null || eol.length() == 0) { // @nowarn — null check at interop boundary
      eol = SequenceUtils.EOL
    }

    // KLUDGE: end always has 1 EOL character removed, however, if there is a \r before \n then one more char needs to be removed from end of text
    val text: CharSequence =
      if (start == Range.NULL.start && end == Range.NULL.end) BasedSequence.NULL
      else sequence.subSequence(start, Math.max(start, end - Math.max(0, eol.length() - 1)))

    val usePrefix = if (start >= end) SequenceUtils.trimEnd(prefix) else prefix

    val line = appendable.getBuilder.append(Nullable(usePrefix)).append(Nullable(text)).append(Nullable(eol)).toSequence

    val preformatted: LineInfo.Preformatted =
      if (preFormattedNesting > 0) {
        if (preFormattedFirstLine == lines.size()) LineInfo.Preformatted.FIRST else LineInfo.Preformatted.BODY
      } else {
        if (preFormattedFirstLine == lines.size()) LineInfo.Preformatted.LAST else LineInfo.Preformatted.NONE
      }

    LineInfo.create(
      line,
      getLastLineInfo,
      usePrefix.length(),
      text.length(),
      line.length(),
      SequenceUtils.isBlank(usePrefix),
      allWhitespace || text.length() == 0,
      preformatted
    )
  }

  private def resetBuilder(): Unit = {
    appendable = appendable.getBuilder
    allWhitespace = true
    lastWasWhitespace = true
  }

  private def addLineRange(start: Int, end: Int, prefix: CharSequence): Unit = {
    lines.add(getLineRange(start, end, prefix))
    resetBuilder()
  }

  private def appendEol(eol: CharSequence): Unit = {
    appendable.append(Nullable(eol))
    val endOffset = appendable.length
    addLineRange(0, endOffset - eol.length(), prefix)
    eolOnFirstText = 0
    rawIndentsOnFirstEol()
  }

  private def rawIndentsOnFirstEol(): Unit = {
    prefix = prefixAfterEol
    while (!indentsOnFirstEol.isEmpty) {
      val runnable = indentsOnFirstEol.remove(indentsOnFirstEol.size() - 1)
      rawIndent()
      runnable.run()
    }
  }

  private def appendEol(count: Int): Unit = {
    var remaining = count
    while (remaining > 0) {
      appendEol(BasedSequence.EOL)
      remaining -= 1
    }
  }

  private def isPrefixed(currentLine: Int): Boolean =
    any(F_PREFIX_PRE_FORMATTED) || (preFormattedFirstLine == currentLine || preFormattedNesting == 0 && preFormattedLastLine != currentLine)

  /** Returns text range if EOL was appended
    *
    * NOTE: if range == Range.NULL then no line would be added
    *
    * @return
    *   pair of line text range if EOL was added and prefix
    */
  private def getRangePrefixAfterEol: Pair[Range, CharSequence] = {
    var startOffset = 0
    var endOffset   = appendable.length + 1
    val currentLine = lines.size()
    val needPrefix  = isPrefixed(currentLine)

    if (passThrough) {
      new Pair(Range.of(startOffset, endOffset - 1), if (needPrefix) prefix else BasedSequence.NULL)
    } else {
      if (allWhitespace && (preFormattedNesting == 0 && !(preFormattedFirstLine == currentLine || preFormattedLastLine == currentLine))) {
        if (!any(F_TRIM_LEADING_EOL) || !lines.isEmpty) {
          new Pair(Range.of(startOffset, endOffset - 1), prefix)
        } else {
          new Pair(Range.NULL, BasedSequence.NULL)
        }
      } else {
        // apply options other than convert tabs which is done at time of appending
        if (isTrimTrailingWhitespace && preFormattedNesting == 0) {
          if (allWhitespace) {
            startOffset = endOffset - 1
          } else {
            endOffset -= SequenceUtils.countTrailingSpaceTab(appendable.toSequence, endOffset - 1)
          }
        }

        if (preFormattedFirstLine == currentLine) {
          if (startOffset > preFormattedFirstLineOffset) startOffset = preFormattedFirstLineOffset
        }

        if (preFormattedLastLine == currentLine) {
          if (endOffset < preFormattedLastLineOffset + 1) endOffset = preFormattedLastLineOffset + 1
        }

        new Pair(Range.of(startOffset, endOffset - 1), if (needPrefix) prefix else BasedSequence.NULL)
      }
    }
  }

  /** Returns text offset before EOL if EOL was issued
    */
  private def offsetAfterEol: Int = {
    val rangePrefixAfterEol = getRangePrefixAfterEol
    val lastLineInfo        = getLastLineInfo

    if (!rangePrefixAfterEol.first.isDefined || rangePrefixAfterEol.first.get.isNull) {
      lastLineInfo.sumLength
    } else {
      val range     = rangePrefixAfterEol.first.get
      var usePrefix = rangePrefixAfterEol.second.get
      if (range.isEmpty && usePrefix.length() != 0) {
        usePrefix = SequenceUtils.trimEnd(usePrefix)
      }
      lastLineInfo.sumLength + rangePrefixAfterEol.first.get.span + usePrefix.length()
    }
  }

  private def doEolOnFirstTest(): Unit =
    if (eolOnFirstText > 0) {
      eolOnFirstText = 0
      appendEol(BasedSequence.EOL)
    }

  private def appendImpl(s: CharSequence, index: Int): Unit = {
    val c = s.charAt(index)

    if (passThrough) {
      if (c == EOL_CHAR) {
        appendEol(BasedSequence.EOL)
      } else {
        if (eolOnFirstText > 0) {
          eolOnFirstText = 0
          appendEol(BasedSequence.EOL)
        }
        if (c != '\t' && c != ' ') allWhitespace = false
        appendable.append(c)
      }
    } else {
      if (c == EOL_CHAR) {
        val rangePrefixAfterEol = getRangePrefixAfterEol
        val textRange           = rangePrefixAfterEol.first.get
        if (textRange.isNull) {
          // nothing to add, just add EOL
          resetBuilder()
        } else {
          // add EOL and line
          appendable.append(c)
          addLineRange(textRange.start, textRange.end, rangePrefixAfterEol.second.get)
        }
        rawIndentsOnFirstEol()
      } else {
        doEolOnFirstTest()

        if (c == '\t') {
          if (preFormattedNesting == 0 && any(F_COLLAPSE_WHITESPACE)) {
            if (!lastWasWhitespace) {
              appendable.append(' ')
              lastWasWhitespace = true
            }
          } else {
            if (any(F_CONVERT_TABS)) {
              val column = appendable.length
              val spaces = 4 - (column % 4)
              appendable.append(' ', spaces)
            } else {
              appendable.append(Nullable(s), index, index + 1)
            }
          }
        } else if (c == ' ') {
          if (preFormattedNesting == 0) {
            if (!any(F_TRIM_LEADING_WHITESPACE) || (appendable.length != 0 && !allWhitespace)) {
              if (any(F_COLLAPSE_WHITESPACE)) {
                if (!lastWasWhitespace) {
                  appendable.append(' ')
                }
              } else {
                appendable.append(' ')
              }
            }
          } else {
            appendable.append(Nullable(s.subSequence(index, index + 1)))
          }
          lastWasWhitespace = true
        } else {
          allWhitespace = false
          lastWasWhitespace = false
          appendable.append(Nullable(s), index, index + 1)
        }
      }
    }
  }

  private def appendImpl(csq: CharSequence, start: Int, end: Int): Unit = {
    var i = start
    while (i < end) {
      appendImpl(csq, i)
      i += 1
    }
  }

  override def append(csq: CharSequence): LineAppendable = {
    if (csq.length() > 0) {
      appendImpl(csq, 0, csq.length())
    } else {
      appendable.append(Nullable(csq))
    }
    this
  }

  override def getBuilder: ISequenceBuilder[?, ?] = appendable.getBuilder

  override def append(csq: CharSequence, start: Int, end: Int): LineAppendable = {
    if (start < end) {
      appendImpl(csq, start, end)
    }
    this
  }

  override def append(c: Char): LineAppendable = {
    appendImpl(Character.toString(c), 0)
    this
  }

  override def append(c: Char, count: Int): LineAppendable = {
    append(RepeatedSequence.repeatOf(c, count))
    this
  }

  def repeat(csq: CharSequence, count: Int): LineAppendable = {
    append(RepeatedSequence.repeatOf(csq, count))
    this
  }

  def repeat(csq: CharSequence, start: Int, end: Int, count: Int): LineAppendable = {
    append(RepeatedSequence.repeatOf(csq.subSequence(start, end), count))
    this
  }

  override def line(): LineAppendable = {
    if (preFormattedNesting > 0 || appendable.length != 0) {
      appendImpl(SequenceUtils.EOL, 0)
    } else {
      val savedPrefix   = this.prefix
      val hadRawIndents = !indentsOnFirstEol.isEmpty

      rawIndentsOnFirstEol()

      if (hadRawIndents || savedPrefix.length() > 0 && this.prefix.length() == 0) {
        // IMPORTANT: add an option for behaviour of empty EOL and prefix reset
        // HACK: html converter expects prefix reset on empty EOL for indentation
        //   formatter wants to preserve first indent, until real text is output
        this.prefix = savedPrefix
      }
    }
    this
  }

  override def lineWithTrailingSpaces(count: Int): LineAppendable = {
    if (preFormattedNesting > 0 || appendable.length != 0) {
      val savedOptions = this.options.toInt
      this.options.andNotMask(F_TRIM_TRAILING_WHITESPACE | F_COLLAPSE_WHITESPACE)
      if (count > 0) append(' ', count)
      appendImpl(SequenceUtils.EOL, 0)
      this.options.setAll(savedOptions)
    }
    this
  }

  override def lineIf(predicate: Boolean): LineAppendable = {
    if (predicate) line()
    this
  }

  override def blankLine(): LineAppendable = {
    line()
    if (!lines.isEmpty && !isTrailingBlankLine || lines.isEmpty && !any(F_TRIM_LEADING_EOL)) {
      appendEol(BasedSequence.EOL)
    }
    this
  }

  override def blankLineIf(predicate: Boolean): LineAppendable = {
    if (predicate) blankLine()
    this
  }

  override def blankLine(count: Int): LineAppendable = {
    line()
    if (!any(F_TRIM_LEADING_EOL) || !lines.isEmpty) {
      val addBlankLines = count - getTrailingBlankLines(lines.size())
      appendEol(addBlankLines)
    }
    this
  }

  override def lineOnFirstText(value: Boolean): LineAppendable = {
    if (value) eolOnFirstText += 1
    else if (eolOnFirstText > 0) eolOnFirstText -= 1
    this
  }

  override def removeIndentOnFirstEOL(listener: Runnable): LineAppendable = {
    indentsOnFirstEol.remove(listener)
    this
  }

  override def addIndentOnFirstEOL(listener: Runnable): LineAppendable = {
    indentsOnFirstEol.add(listener)
    this
  }

  override def getLineCount: Int = lines.size()

  override def getLineCountWithPending: Int =
    if (appendable.length == 0) lines.size() else lines.size() + 1

  override def column(): Int = appendable.length

  override def getLineInfo(lineIndex: Int): LineInfo =
    if (lineIndex == lines.size()) {
      if (appendable.length == 0) {
        LineInfo.NULL
      } else {
        // create a dummy line info
        val rangePrefixAfterEol = getRangePrefixAfterEol
        val textRange           = rangePrefixAfterEol.first.get
        if (textRange.isNull) LineInfo.NULL
        else getLineRange(textRange.start, textRange.end, rangePrefixAfterEol.second.get)
      }
    } else {
      lines.get(lineIndex)
    }

  override def getLine(lineIndex: Int): BasedSequence = getLineInfo(lineIndex).getLine

  override def offset(): Int = getLastLineInfo.sumLength

  override def offsetWithPending(): Int = offsetAfterEol

  override def isPendingSpace: Boolean = appendable.length > 0 && lastWasWhitespace

  override def getPendingSpace: Int =
    if (lastWasWhitespace && appendable.length != 0) {
      SequenceUtils.countTrailingSpaceTab(appendable.toSequence)
    } else {
      0
    }

  override def getPendingEOL: Int =
    if (appendable.length == 0) {
      getTrailingBlankLines(lines.size()) + 1
    } else {
      0
    }

  override def isPreFormatted: Boolean = preFormattedNesting > 0

  override def openPreFormatted(addPrefixToFirstLine: Boolean): LineAppendable = {
    if (preFormattedNesting == 0) {
      if (preFormattedFirstLine != lines.size()) {
        preFormattedFirstLine = lines.size()
        preFormattedFirstLineOffset = appendable.length
      }
    }
    preFormattedNesting += 1
    this
  }

  override def closePreFormatted(): LineAppendable = {
    if (preFormattedNesting <= 0) throw new IllegalStateException("closePreFormatted called with nesting == 0")
    preFormattedNesting -= 1

    if (preFormattedNesting == 0 && !endsWithEOL) {
      // this will be the last line of preformatted text
      preFormattedLastLine = lines.size()
      preFormattedLastLineOffset = appendable.length
    }
    this
  }

  override def toString: String = {
    val out = new java.lang.StringBuilder()
    try
      appendToNoLine(out, true, Integer.MAX_VALUE, Integer.MAX_VALUE, 0, Integer.MAX_VALUE)
    catch {
      case _: IOException => // ignored
    }
    out.toString
  }

  override def toString(maxBlankLines: Int, maxTrailingBlankLines: Int, withPrefixes: Boolean): String = {
    val out = new java.lang.StringBuilder()
    try
      appendTo(out, withPrefixes, maxBlankLines, maxTrailingBlankLines, 0, Integer.MAX_VALUE)
    catch {
      case _: IOException => // ignored
    }
    out.toString
  }

  override def toSequence(maxBlankLines: Int, maxTrailingBlankLines: Int, withPrefixes: Boolean): CharSequence = {
    val out = getBuilder
    try
      appendTo(out, withPrefixes, maxBlankLines, maxTrailingBlankLines, 0, Integer.MAX_VALUE)
    catch {
      case _: IOException => // ignored
    }
    out.toSequence
  }

  override def appendTo[T <: Appendable](out: T, withPrefixes: Boolean, maxBlankLines: Int, maxTrailingBlankLines: Int, startLine: Int, endLine: Int): T = {
    line()
    appendToNoLine(out, withPrefixes, maxBlankLines, maxTrailingBlankLines, startLine, endLine)
  }

  def appendToNoLine[T <: Appendable](out: T, withPrefixes: Boolean, maxBlankLines: Int, maxTrailingBlankLines: Int, startLine: Int, endLine: Int): T = {
    val tailEOL                  = maxTrailingBlankLines >= 0
    val useMaxBlankLines         = Math.max(0, maxBlankLines)
    val useMaxTrailingBlankLines = Math.max(0, maxTrailingBlankLines)

    val endLinePending        = lines.size()
    val iMax                  = Math.min(getLineCountWithPending, endLine)
    val lastNonBlank          = lastNonBlankLine(iMax)
    var consecutiveBlankLines = 0

    var i = startLine
    while (i < iMax) {
      val info            = getLineInfo(i)
      val notDanglingLine = i < endLinePending

      if (info.textLength == 0 && !info.isPreformatted) {
        if (i > lastNonBlank) {
          // NOTE: these are tail blank lines
          if (consecutiveBlankLines < useMaxTrailingBlankLines) {
            consecutiveBlankLines += 1
            if (withPrefixes) out.append(if (isTrimTrailingWhitespace) SequenceUtils.trimEnd(info.getPrefix) else info.getPrefix)
            if (notDanglingLine && (tailEOL || consecutiveBlankLines != useMaxTrailingBlankLines)) {
              out.append(EOL_CHAR)
            }
          }
        } else {
          if (consecutiveBlankLines < useMaxBlankLines) {
            consecutiveBlankLines += 1
            if (withPrefixes) out.append(if (isTrimTrailingWhitespace) SequenceUtils.trimEnd(info.getPrefix) else info.getPrefix)
            if (notDanglingLine) out.append(EOL_CHAR)
          }
        }
      } else {
        consecutiveBlankLines = 0
        if (notDanglingLine && (tailEOL || i < lastNonBlank || info.isPreformatted && info.getPreformatted != LineInfo.Preformatted.LAST)) {
          if (withPrefixes) out.append(info.lineSeq)
          else out.append(info.getText)
        } else {
          if (withPrefixes) out.append(info.getLineNoEOL)
          else out.append(info.getText)
        }
      }
      i += 1
    }
    out
  }

  override def append(lineAppendable: LineAppendable, startLine: Int, endLine: Int, withPrefixes: Boolean): LineAppendable = {
    val iMax         = Math.min(endLine, lineAppendable.getLineCountWithPending)
    val useStartLine = Math.max(0, startLine)

    var i = useStartLine
    while (i < iMax) {
      val info       = lineAppendable.getLineInfo(i)
      val text       = info.getTextNoEOL
      val linePrefix = if (withPrefixes) info.getPrefix else BasedSequence.NULL
      val combinedPfx: CharSequence = if (any(F_PREFIX_PRE_FORMATTED) || !info.isPreformatted || info.getPreformatted == LineInfo.Preformatted.FIRST) {
        LineAppendable.combinedPrefix(Nullable(this.prefix), Nullable(linePrefix))
      } else {
        linePrefix
      }

      appendable.append(Nullable(text: CharSequence))
      allWhitespace = info.isBlankText
      lastWasWhitespace = info.textLength == 0 || CharPredicate.SPACE_TAB.test(text.safeCharAt(info.textLength - 1))

      if (i < lineAppendable.getLineCount) {
        // full line
        appendable.append(EOL_CHAR)
        allWhitespace = info.isBlankText
        val eo = appendable.length
        addLineRange(0, eo - 1, combinedPfx)
      } else {
        this.prefix = combinedPfx
      }
      i += 1
    }
    this
  }

  /** Remove lines and return index from which line info must be recomputed
    */
  private def removeLinesRaw(startLine: Int, endLine: Int): Int = {
    val useStartLine = Math.max(startLine, 0)
    val useEndLine   = Math.min(endLine, getLineCountWithPending)

    if (useStartLine < useEndLine) {
      lines.subList(useStartLine, useEndLine).clear()
      modificationCount += 1
      // recompute lineInfo for lines at or after the deleted lines
      useStartLine
    } else {
      if (endLine >= getLineCountWithPending && appendable.length > 0) {
        // reset pending text
        resetBuilder()
      }
      lines.size()
    }
  }

  private[sequence] def recomputeLineInfo(startLine: Int): Unit = {
    val iMax         = lines.size()
    val useStartLine = Math.max(0, startLine)

    if (useStartLine < iMax) {
      var lastInfo = if (useStartLine - 1 >= 0) lines.get(useStartLine - 1) else LineInfo.NULL
      var i        = useStartLine
      boundary {
        while (i < iMax) {
          val info = lines.get(i)
          lastInfo = LineInfo.create(lastInfo, info)
          lines.set(i, lastInfo)
          if (!lastInfo.needAggregateUpdate(info)) break(())
          i += 1
        }
      }
    }
  }

  override def removeLines(startLine: Int, endLine: Int): LineAppendable = {
    val useStartLine = removeLinesRaw(startLine, endLine)
    recomputeLineInfo(useStartLine)
    this
  }

  override def removeExtraBlankLines(maxBlankLines: Int, maxTrailingBlankLines: Int, startLine: Int, endLine: Int): LineAppendable = {
    val useMaxBlankLines         = Math.max(0, maxBlankLines)
    val useMaxTrailingBlankLines = Math.max(0, maxTrailingBlankLines)
    val iMax                     = Math.min(endLine, getLineCountWithPending)

    var consecutiveBlankLines    = 0
    var maxConsecutiveBlankLines = useMaxTrailingBlankLines
    var minRemovedLine           = getLineCountWithPending

    var i = iMax
    while (i > 0) {
      i -= 1
      val info = getLineInfo(i)
      if (info.isBlankText && !info.isPreformatted) {
        if (consecutiveBlankLines >= maxConsecutiveBlankLines) {
          minRemovedLine = removeLinesRaw(i + consecutiveBlankLines, i + consecutiveBlankLines + 1)
        } else {
          consecutiveBlankLines += 1
        }
      } else {
        consecutiveBlankLines = 0
        maxConsecutiveBlankLines = useMaxBlankLines
      }
    }

    recomputeLineInfo(minRemovedLine)
    this
  }

  override def setPrefixLength(lineIndex: Int, prefixLength: Int): Unit = {
    if (lineIndex == lines.size() && appendable.length > 0) {
      line()
    }

    val info    = lines.get(lineIndex)
    val lineSeq = info.lineSeq

    if (prefixLength < 0 || prefixLength > info.getTextEnd) {
      throw new IllegalArgumentException(s"prefixLength $prefixLength is out of valid range [0, ${info.getTextEnd + 1}) for the line")
    }

    if (prefixLength != info.prefixLength) {
      val pfx     = lineSeq.subSequence(0, prefixLength)
      val newInfo = LineInfo.create(
        info.lineSeq,
        if (lineIndex == 0) LineInfo.NULL else lines.get(lineIndex - 1),
        pfx.length(),
        info.prefixLength + info.textLength - prefixLength,
        info.length,
        SequenceUtils.isBlank(pfx),
        SequenceUtils.isBlank(lineSeq.subSequence(prefixLength, info.getTextEnd)),
        info.getPreformatted
      )

      lines.set(lineIndex, newInfo)
      this.recomputeLineInfo(lineIndex + 1)
    }
  }

  private def createLineInfo(lineIndex: Int, prefix: CharSequence, content: CharSequence): LineInfo = {
    val prevInfo     = if (lineIndex == 0) LineInfo.NULL else lines.get(lineIndex - 1)
    val info         = if (lineIndex == lines.size()) LineInfo.NULL else lines.get(lineIndex)
    var text         = content
    val eolNullable2 = SequenceUtils.trimmedEOL(content)
    var eol: CharSequence = if (eolNullable2.isDefined) eolNullable2.get else null // @nowarn — null needed for null check below

    if (eol == null) eol = SequenceUtils.EOL // @nowarn — null check at interop boundary
    else text = text.subSequence(0, text.length() - eol.length())

    val usePrefix = if (text.length() == 0) SequenceUtils.trimEnd(prefix) else prefix

    assert(
      !SequenceUtils.containsAny(text, CharPredicate.ANY_EOL),
      s"Line text should not contain any EOL, text: ${SequenceUtils.toVisibleWhitespaceString(text)}"
    )

    val line = appendable.getBuilder.append(Nullable(usePrefix)).append(Nullable(text)).append(Nullable(eol)).toSequence

    val preformatted: LineInfo.Preformatted =
      if (info.isNotNull) info.getPreformatted
      else if (prevInfo.isPreformatted && prevInfo.getPreformatted != LineInfo.Preformatted.LAST) LineInfo.Preformatted.BODY
      else LineInfo.Preformatted.NONE

    LineInfo.create(
      line,
      prevInfo,
      usePrefix.length(),
      text.length(),
      line.length(),
      SequenceUtils.isBlank(usePrefix),
      SequenceUtils.isBlank(text),
      preformatted
    )
  }

  override def setLine(lineIndex: Int, prefix: CharSequence, content: CharSequence): Unit = {
    if (lineIndex == lines.size() && appendable.length > 0) {
      line()
    }
    lines.set(lineIndex, createLineInfo(lineIndex, prefix, content))
    this.recomputeLineInfo(lineIndex + 1)
  }

  override def insertLine(lineIndex: Int, prefix: CharSequence, content: CharSequence): Unit = {
    lines.add(lineIndex, createLineInfo(lineIndex, prefix, content))
    this.recomputeLineInfo(lineIndex + 1)
  }

  private[sequence] def tailBlankLinesToRemove(endLine: Int, maxTrailingBlankLines: Int): Int =
    Math.max(0, getTrailingBlankLines(endLine) - Math.max(0, maxTrailingBlankLines))

  private[sequence] def getIndexedLineInfoProxy(maxTrailingBlankLines: Int, startLine: Int, endLine: Int): LineAppendableImpl.IndexedLineInfoProxy =
    new LineAppendableImpl.IndexedLineInfoProxy(this, maxTrailingBlankLines, startLine, endLine)

  private[sequence] def getIndexedLineProxy(maxTrailingBlankLines: Int, startLine: Int, endLine: Int, withPrefixes: Boolean): LineAppendableImpl.IndexedLineProxy =
    new LineAppendableImpl.IndexedLineProxy(getIndexedLineInfoProxy(maxTrailingBlankLines, startLine, endLine), withPrefixes)

  override def iterator(): ju.Iterator[LineInfo] =
    new IndexedItemIterator[LineInfo](getIndexedLineInfoProxy(Integer.MAX_VALUE, 0, getLineCount))

  override def getLines(maxTrailingBlankLines: Int, startLine: Int, endLine: Int, withPrefixes: Boolean): java.lang.Iterable[BasedSequence] =
    new IndexedItemIterable[BasedSequence](getIndexedLineProxy(maxTrailingBlankLines, startLine, endLine, withPrefixes))

  override def getLinesInfo(maxTrailingBlankLines: Int, startLine: Int, endLine: Int): java.lang.Iterable[LineInfo] =
    new IndexedItemIterable[LineInfo](getIndexedLineInfoProxy(maxTrailingBlankLines, startLine, endLine))
}

object LineAppendableImpl {

  private[sequence] class IndexedLineInfoProxy(
    val appendable:            LineAppendableImpl,
    val maxTrailingBlankLines: Int,
    val startLine:             Int,
    val endLine:               Int
  ) extends Indexed[LineInfo] {

    private val resolvedEndLine: Int = Math.min(endLine, appendable.getLineCountWithPending)

    override def get(index: Int): LineInfo = {
      if (index + startLine >= resolvedEndLine) {
        throw new IndexOutOfBoundsException(s"index $index is out of valid range [$startLine, $resolvedEndLine)")
      }
      appendable.getLineInfo(index + startLine)
    }

    override def set(index: Int, item: LineInfo): Unit = {
      if (index + startLine >= resolvedEndLine) {
        throw new IndexOutOfBoundsException(s"index $index is out of valid range [$startLine, $resolvedEndLine)")
      }
      appendable.lines.set(startLine + index, item)
      appendable.recomputeLineInfo(startLine + index + 1)
    }

    override def removeAt(index: Int): Unit = {
      if (index + startLine >= resolvedEndLine) {
        throw new IndexOutOfBoundsException(s"index $index is out of valid range [$startLine, $resolvedEndLine)")
      }
      appendable.removeLines(index + startLine, index + 1)
    }

    override def size: Int = {
      val removeBlankLines = appendable.tailBlankLinesToRemove(resolvedEndLine, maxTrailingBlankLines)
      Math.max(0, resolvedEndLine - startLine - removeBlankLines)
    }

    override def modificationCount: Int = appendable.modificationCount
  }

  private[sequence] class IndexedLineProxy(
    val proxy:        IndexedLineInfoProxy,
    val withPrefixes: Boolean
  ) extends Indexed[BasedSequence] {

    override def get(index: Int): BasedSequence =
      if (proxy.maxTrailingBlankLines == -1 && index + 1 == proxy.size) {
        if (withPrefixes) proxy.get(index).getLineNoEOL else proxy.get(index).getTextNoEOL
      } else {
        if (withPrefixes) proxy.get(index).getLine else proxy.get(index).getText
      }

    override def set(index: Int, item: BasedSequence): Unit =
      if (withPrefixes) {
        proxy.appendable.setLine(index + proxy.startLine, BasedSequence.NULL, item)
      } else {
        proxy.appendable.setLine(index + proxy.startLine, proxy.appendable.getLineInfo(index + proxy.startLine).getPrefix, item)
      }

    override def removeAt(index: Int): Unit =
      proxy.removeAt(index)

    override def size: Int = proxy.size

    override def modificationCount: Int = proxy.modificationCount
  }
}
