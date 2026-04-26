/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-html/src/main/java/com/vladsch/flexmark/util/html/HtmlAppendableBase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-html/src/main/java/com/vladsch/flexmark/util/html/HtmlAppendableBase.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package html

import ssg.md.Nullable
import ssg.md.util.misc.{ BitFieldSet, Utils }
import ssg.md.util.sequence.*
import ssg.md.util.sequence.builder.ISequenceBuilder

import scala.collection.mutable
import scala.language.implicitConversions

@SuppressWarnings(Array("unchecked"))
abstract class HtmlAppendableBase[T <: HtmlAppendableBase[T]](
  private val appendable:        LineAppendable,
  private var currentAttributes: Nullable[MutableAttributes],
  private var indentOnFirstEol:  Boolean,
  private var lineOnChildText:   Boolean,
  private var withAttributes:    Boolean,
  var suppressOpenTagLine:       Boolean,
  var suppressCloseTagLine:      Boolean,
  private val openTagStack:      mutable.Stack[String]
) extends HtmlAppendable {

  def this(other: LineAppendable, inheritIndent: Boolean) = {
    this(
      new LineAppendableImpl(Nullable.empty[Appendable], other.getOptions),
      Nullable.empty,
      false,
      false,
      false,
      false,
      false,
      mutable.Stack.empty
    )
    appendable.setIndentPrefix(
      RepeatedSequence.repeatOf(" ", if (inheritIndent) other.getIndentPrefix.length() else 0).toString
    )
  }

  def this(indentSize: Int, formatOptions: Int) = {
    this(
      new LineAppendableImpl(Nullable.empty[Appendable], formatOptions),
      Nullable.empty,
      false,
      false,
      false,
      false,
      false,
      mutable.Stack.empty
    )
    appendable.setIndentPrefix(RepeatedSequence.repeatOf(" ", indentSize).toString)
  }

  def this(other: Nullable[Appendable], indentSize: Int, formatOptions: Int) = {
    this(
      new LineAppendableImpl(other, formatOptions),
      Nullable.empty,
      false,
      false,
      false,
      false,
      false,
      mutable.Stack.empty
    )
    appendable.setIndentPrefix(RepeatedSequence.repeatOf(" ", indentSize).toString)
  }

  override def getEmptyAppendable: HtmlAppendable =
    new HtmlAppendableBase[T](Nullable(appendable.asInstanceOf[Appendable]), appendable.getIndentPrefix.length(), appendable.getOptions) {
      override def setIndentPrefix(prefix: Nullable[CharSequence]):                    LineAppendable = { appendable.setIndentPrefix(prefix); this }
      override def setPrefix(prefix:       Nullable[CharSequence], afterEol: Boolean): LineAppendable = { appendable.setPrefix(prefix, afterEol); this }
    }

  override def toString: String = appendable.toString

  override def openPre(): T = {
    appendable.openPreFormatted(true)
    this.asInstanceOf[T]
  }

  override def closePre(): T = {
    appendable.closePreFormatted()
    this.asInstanceOf[T]
  }

  override def inPre: Boolean = appendable.isPreFormatted

  override def raw(s: CharSequence): T = {
    appendable.append(s)
    this.asInstanceOf[T]
  }

  override def raw(s: CharSequence, count: Int): T = {
    var i = count
    while (i > 0) {
      appendable.append(s)
      i -= 1
    }
    this.asInstanceOf[T]
  }

  override def rawPre(s: CharSequence): T = {
    var text = s
    // if previous pre-formatted did not have an EOL and this one does, need to transfer the EOL
    // to previous pre-formatted to have proper handling of first/last line, otherwise this opening
    // pre-formatted, blows away previous last line pre-formatted information
    if (appendable.getPendingEOL == 0 && text.length() > 0 && text.charAt(0) == '\n') {
      appendable.line()
      text = text.subSequence(1, text.length())
    }

    appendable.openPreFormatted(true).append(text).closePreFormatted()
    this.asInstanceOf[T]
  }

  override def rawIndentedPre(s: CharSequence): T = {
    appendable.openPreFormatted(true).append(s).closePreFormatted()
    this.asInstanceOf[T]
  }

  override def text(s: CharSequence): T = {
    appendable.append(Escaping.escapeHtml(s, false))
    this.asInstanceOf[T]
  }

  override def attr(attrName: CharSequence, value: CharSequence): T = {
    if (currentAttributes.isEmpty) {
      currentAttributes = Nullable(new MutableAttributes())
    }
    currentAttributes.get.addValue(attrName, value)
    this.asInstanceOf[T]
  }

  override def attr(attribute: Attribute*): T = {
    if (currentAttributes.isEmpty) {
      currentAttributes = Nullable(new MutableAttributes())
    }
    for (a <- attribute)
      currentAttributes.get.addValue(a.name, a.value)
    this.asInstanceOf[T]
  }

  override def attr(attributes: Attributes): T = {
    if (!attributes.isEmpty) {
      if (currentAttributes.isEmpty) {
        currentAttributes = Nullable(new MutableAttributes(attributes))
      } else {
        currentAttributes.get.addValues(attributes)
      }
    }
    this.asInstanceOf[T]
  }

  override def withAttr(): T = {
    withAttributes = true
    this.asInstanceOf[T]
  }

  override def getAttributes: Nullable[Attributes] =
    if (currentAttributes.isDefined) Nullable(currentAttributes.get.asInstanceOf[Attributes])
    else Nullable.empty

  override def setAttributes(attributes: Attributes): T = {
    currentAttributes = Nullable(attributes.toMutable)
    this.asInstanceOf[T]
  }

  override def withCondLineOnChildText(): T = {
    lineOnChildText = true
    this.asInstanceOf[T]
  }

  override def withCondIndent(): T = {
    indentOnFirstEol = true
    this.asInstanceOf[T]
  }

  override def tag(tagName: CharSequence): T =
    tag(tagName, false)

  override def tag(tagName: CharSequence, runnable: Runnable): T =
    tag(tagName, false, false, runnable)

  override def tagVoid(tagName: CharSequence): T =
    tag(tagName, true)

  protected def getOpenTagText: String =
    Utils.splice(openTagStack, ", ", true)

  protected def pushTag(tagName: CharSequence): Unit =
    openTagStack.push(String.valueOf(tagName))

  protected def popTag(tagName: CharSequence): Unit = {
    if (openTagStack.isEmpty) throw new IllegalStateException(s"Close tag '$tagName' with no tags open")
    val openTag = openTagStack.top
    if (openTag != String.valueOf(tagName)) {
      throw new IllegalStateException(s"Close tag '$tagName' does not match '$openTag' in $getOpenTagText")
    }
    openTagStack.pop()
  }

  protected def tagOpened(tagName: CharSequence): Unit =
    pushTag(tagName)

  protected def tagClosed(tagName: CharSequence): Unit =
    popTag(tagName)

  override def getOpenTags: mutable.Stack[String] = openTagStack

  override def getOpenTagsAfterLast(latestTag: CharSequence): List[String] =
    if (openTagStack.isEmpty) Nil
    else {
      val tagList = openTagStack.toList.reverse // stack is LIFO, reverse to get push order
      val iMax    = tagList.size
      val lastTag = String.valueOf(latestTag)
      var lastPos = iMax
      var i       = iMax - 1
      while (i >= 0)
        if (tagList(i) == lastTag) {
          lastPos = i + 1
          i = -1 // break
        } else {
          i -= 1
        }
      tagList.slice(lastPos, iMax)
    }

  override def tag(tagName: CharSequence, voidElement: Boolean): T =
    if (tagName.length() == 0 || tagName.charAt(0) == '/') {
      closeTag(tagName)
    } else {
      var attributes: Nullable[Attributes] = Nullable.empty

      if (withAttributes) {
        attributes = if (currentAttributes.isDefined) Nullable(currentAttributes.get.asInstanceOf[Attributes]) else Nullable.empty
        currentAttributes = Nullable.empty
        withAttributes = false
      }

      appendable.append("<")
      appendable.append(tagName)

      if (attributes.isDefined && !attributes.get.isEmpty) {
        for (attribute <- attributes.get.values) {
          val attributeValue = attribute.value
          if (attribute.isNonRendering) {
            // skip non-rendering attributes
          } else {
            appendable.append(" ")
            appendable.append(Escaping.escapeHtml(attribute.name, true))
            appendable.append("=\"")
            appendable.append(Escaping.escapeHtml(attributeValue, true))
            appendable.append("\"")
          }
        }
      }

      if (voidElement) {
        appendable.append(" />")
      } else {
        appendable.append(">")
        tagOpened(tagName)
      }

      this.asInstanceOf[T]
    }

  override def closeTag(tagName: CharSequence): T = {
    if (tagName.length() == 0) throw new IllegalStateException(s"closeTag called with tag:'$tagName'")

    if (tagName.charAt(0) == '/') {
      appendable.append("<").append(tagName).append(">")
      tagClosed(tagName.subSequence(1, tagName.length()))
    } else {
      appendable.append("</").append(tagName).append(">")
      tagClosed(tagName)
    }
    this.asInstanceOf[T]
  }

  override def tag(tagName: CharSequence, withIndent: Boolean, withLine: Boolean, runnable: Runnable): T = {
    val isLineOnChildText  = lineOnChildText
    val isIndentOnFirstEol = indentOnFirstEol
    lineOnChildText = false
    indentOnFirstEol = false

    if (withIndent && !suppressOpenTagLine) {
      appendable.line()
    }

    tag(tagName, false)

    if (withIndent && !isIndentOnFirstEol) appendable.indent()

    if ((appendable.getOptions & LineAppendable.F_PASS_THROUGH) != 0) {
      runnable.run()
    } else {
      var hadConditionalIndent = false
      val indentOnFirstEolRunnable: Runnable = () => hadConditionalIndent = true

      if (isLineOnChildText) appendable.setLineOnFirstText()

      if (isIndentOnFirstEol) {
        appendable.addIndentOnFirstEOL(indentOnFirstEolRunnable)
      }

      runnable.run()

      if (isLineOnChildText) appendable.clearLineOnFirstText()

      if (hadConditionalIndent) {
        appendable.unIndentNoEol()
      } else {
        appendable.removeIndentOnFirstEOL(indentOnFirstEolRunnable)
      }
    }

    if (withIndent && !isIndentOnFirstEol) appendable.unIndent()

    // don't rely on unIndent() doing a line, it will only do so if there was text since indent()
    if (withLine && !suppressCloseTagLine) appendable.line()

    closeTag(tagName)

    if (withIndent && !suppressCloseTagLine) {
      appendable.line()
    }

    this.asInstanceOf[T]
  }

  override def tagVoidLine(tagName: CharSequence): T = {
    lineIf(!suppressOpenTagLine)
    tagVoid(tagName)
    lineIf(!suppressCloseTagLine)
    this.asInstanceOf[T]
  }

  override def tagLine(tagName: CharSequence): T = {
    lineIf(!suppressOpenTagLine)
    tag(tagName)
    lineIf(!suppressCloseTagLine)
    this.asInstanceOf[T]
  }

  override def tagLine(tagName: CharSequence, voidElement: Boolean): T = {
    lineIf(!suppressOpenTagLine)
    tag(tagName, voidElement)
    lineIf(!suppressCloseTagLine)
    this.asInstanceOf[T]
  }

  override def tagLine(tagName: CharSequence, runnable: Runnable): T = {
    lineIf(!suppressOpenTagLine)
    tag(tagName, false, false, runnable)
    lineIf(!suppressCloseTagLine)
    this.asInstanceOf[T]
  }

  override def tagIndent(tagName: CharSequence, runnable: Runnable): T = {
    tag(tagName, true, false, runnable)
    this.asInstanceOf[T]
  }

  override def tagLineIndent(tagName: CharSequence, runnable: Runnable): T = {
    tag(tagName, true, true, runnable)
    this.asInstanceOf[T]
  }

  // delegated to LineAppendable
  override def iterator():                                                                                java.util.Iterator[LineInfo]      = appendable.iterator()
  override def getLines(maxTrailingBlankLines: Int, startLine: Int, endLine: Int, withPrefixes: Boolean): java.lang.Iterable[BasedSequence] =
    appendable.getLines(maxTrailingBlankLines, startLine, endLine, true)
  override def getLinesInfo(maxTrailingBlankLines: Int, startLine:      Int, endLine: Int): java.lang.Iterable[LineInfo] = appendable.getLinesInfo(maxTrailingBlankLines, startLine, endLine)
  override def setPrefixLength(lineIndex:          Int, prefixEndIndex: Int):               Unit                         = appendable.setPrefixLength(lineIndex, prefixEndIndex)
  override def insertLine(lineIndex: Int, prefix: CharSequence, text: CharSequence): Unit = appendable.insertLine(lineIndex, prefix, text)
  override def setLine(lineIndex:    Int, prefix: CharSequence, text: CharSequence): Unit = appendable.setLine(lineIndex, prefix, text)
  override def appendTo[U <: Appendable](out: U, withPrefixes: Boolean, maxBlankLines: Int, maxTrailingBlankLines: Int, startLine: Int, endLine: Int): U =
    appendable.appendTo(out, withPrefixes, maxBlankLines, maxTrailingBlankLines, startLine, endLine)
  override def endsWithEOL:                         Boolean                = appendable.endsWithEOL
  override def isPendingSpace:                      Boolean                = appendable.isPendingSpace
  override def isPreFormatted:                      Boolean                = appendable.isPreFormatted
  override def getTrailingBlankLines(endLine: Int): Int                    = appendable.getTrailingBlankLines(endLine)
  override def column():                            Int                    = appendable.column()
  override def getLineCount:                        Int                    = appendable.getLineCount
  override def getLineCountWithPending:             Int                    = appendable.getLineCountWithPending
  override def getOptions:                          Int                    = appendable.getOptions
  override def getPendingSpace:                     Int                    = appendable.getPendingSpace
  override def getPendingEOL:                       Int                    = appendable.getPendingEOL
  override def offset():                            Int                    = appendable.offset()
  override def offsetWithPending():                 Int                    = appendable.offsetWithPending()
  override def getAfterEolPrefixDelta:              Int                    = appendable.getAfterEolPrefixDelta
  override def getBuilder:                          ISequenceBuilder[?, ?] = appendable.getBuilder
  override def getPrefix:                           BasedSequence          = appendable.getPrefix
  override def getBeforeEolPrefix:                  BasedSequence          = appendable.getBeforeEolPrefix
  override def getLineInfo(lineIndex:         Int): LineInfo               = appendable.getLineInfo(lineIndex)
  override def getLine(lineIndex:             Int): BasedSequence          = appendable.getLine(lineIndex)
  override def getIndentPrefix:                     BasedSequence          = appendable.getIndentPrefix
  override def toSequence(maxBlankLines: Int, maxTrailingBlankLines: Int, withPrefixes: Boolean): CharSequence = appendable.toSequence(maxBlankLines, maxTrailingBlankLines, withPrefixes)
  override def toString(maxBlankLines:   Int, maxTrailingBlankLines: Int, withPrefixes: Boolean): String       = appendable.toString(maxBlankLines, maxTrailingBlankLines, withPrefixes)
  override def getOptionSet:                                                                                        BitFieldSet[LineAppendable.Options] = appendable.getOptionSet
  override def removeExtraBlankLines(maxBlankLines: Int, maxTrailingBlankLines: Int, startLine: Int, endLine: Int): LineAppendable                      = {
    appendable.removeExtraBlankLines(maxBlankLines, maxTrailingBlankLines, startLine, endLine); this
  }
  override def removeLines(startLine:        Int, endLine:              Int):                                      LineAppendable = { appendable.removeLines(startLine, endLine); this }
  override def pushOptions():                                                                                      LineAppendable = { appendable.pushOptions(); this }
  override def popOptions():                                                                                       LineAppendable = { appendable.popOptions(); this }
  override def changeOptions(addFlags:       Int, removeFlags:          Int):                                      LineAppendable = { appendable.changeOptions(addFlags, removeFlags); this }
  override def addIndentOnFirstEOL(listener: Runnable):                                                            LineAppendable = { appendable.addIndentOnFirstEOL(listener); this }
  override def addPrefix(prefix:             CharSequence):                                                        LineAppendable = { appendable.addPrefix(prefix); this }
  override def addPrefix(prefix:             CharSequence, afterEol:    Boolean):                                  LineAppendable = { appendable.addPrefix(prefix, afterEol); this }
  override def append(c:                     Char):                                                                LineAppendable = { appendable.append(c); this }
  override def append(csq:                   CharSequence):                                                        LineAppendable = { appendable.append(csq); this }
  override def append(csq:                   CharSequence, start:       Int, end:     Int):                        LineAppendable = { appendable.append(csq, start, end); this }
  override def append(lines:                 LineAppendable, startLine: Int, endLine: Int, withPrefixes: Boolean): LineAppendable = { appendable.append(lines, startLine, endLine, withPrefixes); this }
  override def blankLine():                                                                                        LineAppendable = { appendable.blankLine(); this }
  override def blankLine(count:              Int):                                                                 LineAppendable = { appendable.blankLine(count); this }
  override def blankLineIf(predicate:        Boolean):                                                             LineAppendable = { appendable.blankLineIf(predicate); this }
  override def closePreFormatted():                                                                                LineAppendable = { appendable.closePreFormatted(); this }
  override def indent():                                                                                           LineAppendable = { appendable.indent(); this }
  override def line():                                                                                             LineAppendable = { appendable.line(); this }
  override def lineIf(predicate:             Boolean):                                                             LineAppendable = { appendable.lineIf(predicate); this }
  override def lineOnFirstText(value:        Boolean):                                                             LineAppendable = { appendable.lineOnFirstText(value); this }
  override def lineWithTrailingSpaces(count: Int):                                                                 LineAppendable = { appendable.lineWithTrailingSpaces(count); this }
  override def openPreFormatted(keepIndent:  Boolean):                                                             LineAppendable = { appendable.openPreFormatted(keepIndent); this }
  override def popPrefix():                                                                                        LineAppendable = { appendable.popPrefix(); this }
  override def popPrefix(afterEol:           Boolean):                                                             LineAppendable = { appendable.popPrefix(afterEol); this }
  override def pushPrefix():                                                                                       LineAppendable = { appendable.pushPrefix(); this }
  override def removeIndentOnFirstEOL(listener: Runnable):                                    LineAppendable = { appendable.removeIndentOnFirstEOL(listener); this }
  override def append(c:                        Char, count:                      Int):       LineAppendable = { appendable.append(c, count); this }
  override def setIndentPrefix(prefix:          Nullable[CharSequence]):                      LineAppendable = { appendable.setIndentPrefix(prefix); this }
  override def setOptions(flags:                Int):                                         LineAppendable = { appendable.setOptions(flags); this }
  override def setPrefix(prefix:                Nullable[CharSequence], afterEol: Boolean):   LineAppendable = { appendable.setPrefix(prefix, afterEol); this }
  override def unIndent():                                                                    LineAppendable = { appendable.unIndent(); this }
  override def unIndentNoEol():                                                               LineAppendable = { appendable.unIndentNoEol(); this }
}
