/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/TocUtils.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package toc

import ssg.md.ast.Heading
import ssg.md.ast.util.TextCollectingVisitor
import ssg.md.ext.toc.internal.TocOptions
import ssg.md.html.HtmlWriter
import ssg.md.html.renderer.{AttributablePart, NodeRendererContext}
import ssg.md.util.html.Attribute
import ssg.md.util.misc.Pair
import ssg.md.util.sequence.{BasedSequence, Escaping, LineAppendable}

import java.{util => ju}
import scala.language.implicitConversions

object TocUtils {

  val TOC_CONTENT: AttributablePart = new AttributablePart("TOC_CONTENT")
  val TOC_LIST: AttributablePart = new AttributablePart("TOC_LIST")

  def filteredHeadings(headings: ju.List[Heading], tocOptions: TocOptions): ju.List[Heading] = {
    val filtered = new ju.ArrayList[Heading](headings.size())
    val it = headings.iterator()
    while (it.hasNext) {
      val header = it.next()
      if (tocOptions.isLevelIncluded(header.level) && !(header.parent.isDefined && header.parent.get.isInstanceOf[SimTocContent])) {
        filtered.add(header)
      }
    }
    filtered
  }

  @annotation.nowarn("msg=null")
  def htmlHeadingTexts(context: NodeRendererContext, headings: ju.List[Heading], tocOptions: TocOptions): Pair[ju.List[Heading], ju.List[String]] = {
    val headingContents = new ju.ArrayList[String](headings.size())
    val isReversed = tocOptions.listType == TocOptions.ListType.SORTED_REVERSED || tocOptions.listType == TocOptions.ListType.FLAT_REVERSED
    val isSorted = tocOptions.listType == TocOptions.ListType.SORTED || tocOptions.listType == TocOptions.ListType.SORTED_REVERSED
    val needText = isReversed || isSorted
    val headingNodes: ju.HashMap[String, Heading] = if (!needText) null else new ju.HashMap[String, Heading](headings.size()) // @nowarn - Java interop: conditionally null
    val headingTexts: ju.HashMap[String, String] = if (!needText || tocOptions.isTextOnly) null else new ju.HashMap[String, String](headings.size()) // @nowarn - Java interop: conditionally null

    val it = headings.iterator()
    while (it.hasNext) {
      val heading = it.next()
      val headingContent: String =
        if (tocOptions.isTextOnly) {
          getHeadingText(heading)
        } else {
          val content = getHeadingContent(context, heading)
          if (needText) {
            headingTexts.put(content, getHeadingText(heading))
          }
          content
        }

      if (needText) {
        headingNodes.put(headingContent, heading)
      }
      headingContents.add(headingContent)
    }

    var resultHeadings: ju.List[Heading] = headings

    if (isSorted || isReversed) {
      if (tocOptions.isTextOnly) {
        if (isSorted) {
          headingContents.sort((h1, h2) => if (isReversed) h2.compareTo(h1) else h1.compareTo(h2))
        } else {
          ju.Collections.reverse(headingContents)
        }
      } else {
        if (isSorted) {
          headingContents.sort((h1, h2) => {
            val t1 = headingTexts.get(h1)
            val t2 = headingTexts.get(h2)
            if (isReversed) t2.compareTo(t1) else t1.compareTo(t2)
          })
        } else {
          ju.Collections.reverse(headingContents)
        }
      }

      resultHeadings = new ju.ArrayList[Heading]()
      val cit = headingContents.iterator()
      while (cit.hasNext) {
        resultHeadings.add(headingNodes.get(cit.next()))
      }
    }

    new Pair[ju.List[Heading], ju.List[String]](Nullable(resultHeadings), Nullable(headingContents.asInstanceOf[ju.List[String]]))
  }

  @annotation.nowarn("msg=null")
  def renderHtmlToc(out: HtmlWriter, sourceText: BasedSequence, headings: ju.List[Integer], headingTexts: ju.List[String], headingRefIds: ju.List[String], tocOptions: TocOptions): Unit = {
    if (headings.size() > 0 && ((sourceText ne BasedSequence.NULL) || tocOptions.title.trim.nonEmpty)) {
      if (sourceText ne BasedSequence.NULL) out.srcPos(sourceText)
      out.attr(Attribute.CLASS_ATTR, tocOptions.divClass).withAttr(TOC_CONTENT).tag("div")
      out.line()
      out.indent()
      if (tocOptions.title.trim.nonEmpty) {
        out.tag("h" + tocOptions.titleLevel).text(tocOptions.title).tag("/h" + tocOptions.titleLevel)
        out.line()
      }
    }

    var initLevel = -1
    var lastLevel = -1
    val listOpen = if (tocOptions.isNumbered) "ol" else "ul"
    val listClose = "/" + listOpen
    val openedItems = new Array[Boolean](7)
    val openedList = new Array[Boolean](7)
    val openedItemAppendCount = new Array[Int](7)

    var i = 0
    while (i < headings.size()) {
      val headerText = headingTexts.get(i)
      val headerLevel = if (tocOptions.listType != TocOptions.ListType.HIERARCHY) 1 else headings.get(i).intValue()

      if (initLevel == -1) {
        initLevel = headerLevel
        lastLevel = headerLevel
        out.attr(Attribute.CLASS_ATTR, tocOptions.listClass).withAttr(TOC_LIST)
        out.line()
        out.tag(listOpen)
        out.indent()
        out.line()
        openedList(0) = true
      }

      if (lastLevel < headerLevel) {
        var lv = lastLevel
        while (lv < headerLevel) {
          openedItems(lv + 1) = false
          openedList(lv + 1) = false
          lv += 1
        }

        if (!openedList(lastLevel)) {
          out.withAttr()
          out.indent()
          out.line()
          out.tag(listOpen)
          out.indent()
          openedList(lastLevel) = true
        }
      } else if (lastLevel == headerLevel) {
        if (openedItems(lastLevel)) {
          if (openedList(lastLevel)) {
            out.unIndent()
            out.tag(listClose)
            out.line()
          }
          out.lineIf(openedItemAppendCount(lastLevel) != out.offsetWithPending())
          out.tag("/li")
          out.line()
        }
        openedItems(lastLevel) = false
        openedList(lastLevel) = false
      } else {
        // lastLevel > headerLevel
        var lv = lastLevel
        while (lv >= headerLevel) {
          if (openedItems(lv)) {
            if (openedList(lv)) {
              out.unIndent()
              out.tag(listClose)
              out.unIndent()
              out.line()
            }
            out.lineIf(openedItemAppendCount(lastLevel) != out.offsetWithPending())
            out.tag("/li")
            out.line()
          }
          openedItems(lv) = false
          openedList(lv) = false
          lv -= 1
        }
      }

      out.line()
      out.tag("li")
      openedItems(headerLevel) = true
      val headerId = headingRefIds.get(i)
      if (headerId == null || headerId.isEmpty) { // @nowarn - Java interop: may be null
        out.raw(headerText)
      } else {
        out.attr("href", "#" + headerId).withAttr().tag("a")
        out.raw(headerText)
        out.tag("/a")
      }

      lastLevel = headerLevel
      openedItemAppendCount(headerLevel) = out.offsetWithPending()
      i += 1
    }

    var j = lastLevel
    while (j >= 1) {
      if (openedItems(j)) {
        if (openedList(j)) {
          out.unIndent()
          out.tag(listClose)
          out.unIndent()
          out.line()
        }
        out.lineIf(openedItemAppendCount(lastLevel) != out.offsetWithPending())
        out.tag("/li")
        out.line()
      }
      j -= 1
    }

    // close original list
    if (openedList(0)) {
      out.unIndent()
      out.tag(listClose)
      out.line()
    }

    if (headings.size() > 0 && ((sourceText ne BasedSequence.NULL) || tocOptions.title.trim.nonEmpty)) {
      out.line()
      out.unIndent()
      out.tag("/div")
    }

    out.line()
  }

  private def getHeadingText(header: Heading): String = {
    @annotation.nowarn("msg=deprecated")
    val text = new TextCollectingVisitor().collectAndGetText(header)
    Escaping.escapeHtml(text, false)
  }

  private def getHeadingContent(context: NodeRendererContext, header: Heading): String = {
    val subContext = context.getSubContext(false)
    subContext.doNotRenderLinks()
    subContext.renderChildren(header)
    subContext.getHtmlWriter.asInstanceOf[LineAppendable].toString(-1, -1)
  }
}
