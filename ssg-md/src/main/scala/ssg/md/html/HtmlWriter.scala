/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/HtmlWriter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package html

import ssg.md.html.renderer.{ AttributablePart, LinkStatus, NodeRendererContext, ResolvedLink }
import ssg.md.util.html.{ Attribute, Attributes, HtmlAppendableBase, MutableAttributes }
import ssg.md.util.sequence.{ BasedSequence, LineAppendable, LineAppendableImpl, RepeatedSequence, TagRange }

import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

class HtmlWriter private (
  lineAppendable:     LineAppendable,
  currentAttributes:  Nullable[MutableAttributes],
  indentOnFirstEol:   Boolean,
  lineOnChildText:    Boolean,
  withAttributesFlag: Boolean,
  suppressOpen:       Boolean,
  suppressClose:      Boolean,
  tagStack:           mutable.Stack[String]
) extends HtmlAppendableBase[HtmlWriter](
      lineAppendable,
      currentAttributes,
      indentOnFirstEol,
      lineOnChildText,
      withAttributesFlag,
      suppressOpen,
      suppressClose,
      tagStack
    ) {

  private var context:       Nullable[NodeRendererContext] = Nullable.empty
  private var useAttributes: Nullable[AttributablePart]    = Nullable.empty

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
    setIndentPrefix(RepeatedSequence.repeatOf(" ", indentSize).toString)
  }

  def this(other: HtmlWriter, inheritIndent: Boolean) = {
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
    setIndentPrefix(
      RepeatedSequence.repeatOf(" ", if (inheritIndent) other.getIndentPrefix.length() else 0).toString
    )
    context = other.context
  }

  def this(indentSize: Int, formatOptions: Int, suppressOpenTagLine: Boolean, suppressCloseTagLine: Boolean) = {
    this(indentSize, formatOptions)
    this.suppressOpenTagLine = suppressOpenTagLine
    this.suppressCloseTagLine = suppressCloseTagLine
  }

  def this(appendable: Nullable[Appendable], indentSize: Int, formatOptions: Int, suppressOpenTagLine: Boolean, suppressCloseTagLine: Boolean) = {
    this(
      new LineAppendableImpl(appendable, formatOptions),
      Nullable.empty,
      false,
      false,
      false,
      suppressOpenTagLine,
      suppressCloseTagLine,
      mutable.Stack.empty
    )
    setIndentPrefix(RepeatedSequence.repeatOf(" ", indentSize).toString)
  }

  private[html] def setContext(context: NodeRendererContext): Unit =
    this.context = Nullable(context)

  def getContext: NodeRendererContext = {
    assert(context.isDefined)
    context.get
  }

  def srcPos(): HtmlWriter =
    if (context.isEmpty) this
    else srcPos(context.get.getCurrentNode.chars)

  def srcPosWithEOL(): HtmlWriter =
    if (context.isEmpty) this
    else srcPosWithEOL(context.get.getCurrentNode.chars)

  def srcPosWithTrailingEOL(): HtmlWriter =
    if (context.isEmpty) this
    else srcPosWithTrailingEOL(context.get.getCurrentNode.chars)

  def srcPos(sourceText: BasedSequence): HtmlWriter =
    if (sourceText.isNotNull) {
      val trimmed = sourceText.trimEOL()
      srcPos(trimmed.startOffset, trimmed.endOffset)
    } else {
      this
    }

  def srcPosWithEOL(sourceText: BasedSequence): HtmlWriter =
    if (sourceText.isNotNull) {
      srcPos(sourceText.startOffset, sourceText.endOffset)
    } else {
      this
    }

  def srcPosWithTrailingEOL(sourceText: BasedSequence): HtmlWriter =
    if (sourceText.isNotNull) {
      var endOffset = sourceText.endOffset
      val base      = sourceText.getBaseSequence

      boundary {
        while (endOffset < base.length()) {
          val c = base.charAt(endOffset)
          if (c != ' ' && c != '\t') break(())
          endOffset += 1
        }
      }

      if (endOffset < base.length() && base.charAt(endOffset) == '\r') {
        endOffset += 1
      }

      if (endOffset < base.length() && base.charAt(endOffset) == '\n') {
        endOffset += 1
      }

      srcPos(sourceText.startOffset, endOffset)
    } else {
      this
    }

  def srcPos(startOffset: Int, endOffset: Int): HtmlWriter = {
    if (startOffset <= endOffset && context.isDefined && context.get.getHtmlOptions.sourcePositionAttribute.nonEmpty) {
      super.attr(context.get.getHtmlOptions.sourcePositionAttribute, startOffset + "-" + endOffset)
    }
    this
  }

  override def withAttr(): HtmlWriter =
    withAttr(AttributablePart.NODE)

  def withAttr(part: AttributablePart): HtmlWriter = {
    super.withAttr()
    useAttributes = Nullable(part)
    this
  }

  def withAttr(status: LinkStatus): HtmlWriter = {
    attr(Attribute.LINK_STATUS_ATTR, status.name)
    withAttr(AttributablePart.LINK)
  }

  def withAttr(resolvedLink: ResolvedLink): HtmlWriter =
    withAttr(resolvedLink.status)

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  override def tag(tagName: CharSequence, voidElement: Boolean): HtmlWriter = {
    if (useAttributes.isDefined) {
      val attributeValue: String =
        if (context.isDefined) {
          val attributes              = context.get.extendRenderingNodeAttributes(useAttributes.get, getAttributes)
          val sourcePositionAttribute = context.get.getHtmlOptions.sourcePositionAttribute
          val attrVal                 = attributes.getValue(sourcePositionAttribute) // @nowarn - getValue may return Java-null
          setAttributes(attributes)
          if (attrVal == null) "" else attrVal
        } else {
          val attrs = new Attributes()
          setAttributes(attrs)
          ""
        }

      if (attributeValue.nonEmpty) {
        // add to tag ranges
        val pos         = attributeValue.indexOf('-')
        var startOffset = -1
        var endOffset   = -1

        if (pos != -1) {
          try
            startOffset = Integer.parseInt(attributeValue.substring(0, pos))
          catch {
            case _: Throwable => ()
          }
          try
            endOffset = Integer.parseInt(attributeValue.substring(pos + 1))
          catch {
            case _: Throwable => ()
          }
        }

        if (startOffset >= 0 && startOffset < endOffset && context.isDefined) {
          val tagRanges = HtmlRenderer.TAG_RANGES.get(context.get.getDocument)
          tagRanges.add(new TagRange(tagName.toString, startOffset, endOffset))
        }
      }

      useAttributes = Nullable.empty
    }

    super.tag(tagName, voidElement)
    this
  }
}
