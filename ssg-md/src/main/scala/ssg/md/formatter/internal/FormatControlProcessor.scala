/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/internal/FormatControlProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package formatter
package internal

import ssg.md.ast.{ HtmlCommentBlock, HtmlInnerBlockComment, Paragraph }
import ssg.md.formatter.FormatterOptions
import ssg.md.util.ast.{ Block, Document, Node }
import ssg.md.util.data.DataHolder

import java.util.regex.{ Pattern, PatternSyntaxException }
import scala.language.implicitConversions

class FormatControlProcessor(document: Document, options: Nullable[DataHolder]) {

  private val formatterOptions:     FormatterOptions = new FormatterOptions(options.getOrElse(document))
  private val formatterOnTag:       String           = formatterOptions.formatterOnTag
  private val formatterOffTag:      String           = formatterOptions.formatterOffTag
  private val formatterTagsEnabled: Boolean          = formatterOptions.formatterTagsEnabled

  private var myFormatterOff: Boolean = false
  @annotation.nowarn("msg=private variable was mutated but not read") // faithful port: will be used when full formatting pipeline is complete
  private var justTurnedOffFormatting: Boolean = false
  @annotation.nowarn("msg=private variable was mutated but not read") // faithful port: will be used when full formatting pipeline is complete
  private var justTurnedOnFormatting:        Boolean           = false
  private var formatterTagsAcceptRegexp:     Boolean           = formatterOptions.formatterTagsAcceptRegexp
  @volatile private var formatterOffPattern: Nullable[Pattern] = Nullable.empty
  @volatile private var formatterOnPattern:  Nullable[Pattern] = Nullable.empty

  def isFormattingOff: Boolean = myFormatterOff

  def getFormatterOffPattern: Nullable[Pattern] = {
    if (formatterOffPattern.isEmpty && formatterTagsEnabled && formatterTagsAcceptRegexp) {
      formatterOffPattern = getPatternOrDisableRegexp(formatterOffTag)
    }
    formatterOffPattern
  }

  def getFormatterOnPattern: Nullable[Pattern] = {
    if (formatterOnPattern.isEmpty && formatterTagsEnabled && formatterTagsAcceptRegexp) {
      formatterOnPattern = getPatternOrDisableRegexp(formatterOnTag)
    }
    formatterOnPattern
  }

  private def getPatternOrDisableRegexp(tag: String): Nullable[Pattern] =
    try
      Nullable(Pattern.compile(tag))
    catch {
      case _: PatternSyntaxException =>
        formatterTagsAcceptRegexp = false
        Nullable.empty
    }

  def isFormattingRegion(node: Node): Boolean =
    if (!formatterTagsEnabled) false
    else {
      node match {
        case comment: HtmlCommentBlock =>
          val text = comment.chars.toString.trim
          isFormattingTag(text)
        case comment: HtmlInnerBlockComment =>
          val text = comment.chars.toString.trim
          isFormattingTag(text)
        case _ => false
      }
    }

  private def isFormattingTag(text: String): Boolean = {
    val content = text.stripPrefix(FormatControlProcessor.OPEN_COMMENT).stripSuffix(FormatControlProcessor.CLOSE_COMMENT).trim

    if (formatterTagsAcceptRegexp) {
      getFormatterOffPattern.exists(_.matcher(content).matches()) ||
      getFormatterOnPattern.exists(_.matcher(content).matches())
    } else {
      content == formatterOffTag || content == formatterOnTag
    }
  }

  def processNode(node: Node, isFormatRegion: Boolean): Unit =
    if (formatterTagsEnabled && isFormatRegion) {
      val text    = node.chars.toString.trim
      val content = text.stripPrefix(FormatControlProcessor.OPEN_COMMENT).stripSuffix(FormatControlProcessor.CLOSE_COMMENT).trim

      if (formatterTagsAcceptRegexp) {
        if (getFormatterOffPattern.exists(_.matcher(content).matches())) {
          justTurnedOffFormatting = !myFormatterOff
          myFormatterOff = true
        } else if (getFormatterOnPattern.exists(_.matcher(content).matches())) {
          justTurnedOnFormatting = myFormatterOff
          myFormatterOff = false
        }
      } else {
        if (content == formatterOffTag) {
          justTurnedOffFormatting = !myFormatterOff
          myFormatterOff = true
        } else if (content == formatterOnTag) {
          justTurnedOnFormatting = myFormatterOff
          myFormatterOff = false
        }
      }
    }

  def initializeFrom(element: Node): Unit = {
    myFormatterOff = !isFormattingRegionAt(element.startOffset, element, checkParent = true)
  }

  def processFormatControl(node: Node): Unit = {
    justTurnedOffFormatting = false
    justTurnedOnFormatting = false

    if ((node.isInstanceOf[HtmlCommentBlock] || node.isInstanceOf[HtmlInnerBlockComment]) && formatterTagsEnabled) {
      // could be formatter control
      val formatterOff = myFormatterOff
      val isFormatterOff = isFormatterOffTag(Nullable(node.chars))
      if (isFormatterOff.isEmpty) {
        return // @nowarn - early return: faithful port of guard clause
      }
      myFormatterOff = isFormatterOff.get

      if (!formatterOff && myFormatterOff) justTurnedOffFormatting = true
      if (formatterOff && !myFormatterOff) justTurnedOnFormatting = true
    }
  }

  private def isFormatterOffTag(commentText: Nullable[CharSequence]): Nullable[Boolean] = {
    if (commentText.isEmpty) Nullable.empty
    else {
      var text = commentText.get.toString.trim
      text = text.substring(FormatControlProcessor.OPEN_COMMENT.length, text.length - FormatControlProcessor.CLOSE_COMMENT.length).trim

      if (formatterTagsAcceptRegexp && formatterOffPattern.isDefined && formatterOnPattern.isDefined) {
        if (formatterOnPattern.get.matcher(text).matches()) {
          Nullable(false)
        } else if (formatterOffPattern.get.matcher(text).matches()) {
          Nullable(true)
        } else {
          Nullable.empty
        }
      } else if (formatterTagsEnabled) {
        if (text == formatterOnTag) {
          Nullable(false)
        } else if (text == formatterOffTag) {
          Nullable(true)
        } else {
          Nullable.empty
        }
      } else {
        Nullable.empty
      }
    }
  }

  private def isFormattingRegionAt(offset: Int, startNode: Node, checkParent: Boolean): Boolean = {
    var node: Node = startNode
    val checkingParent = checkParent
    var continue = true

    while (node != null && continue) { // @nowarn - Java interop: node traversal via getPrevious/getParent may return null
      if (node.startOffset <= offset) {
        if (node.isInstanceOf[Block] && !node.isInstanceOf[Paragraph] && node.hasChildren) {
          val lastChild = node.lastChild.getOrElse(null)
          if (lastChild != null) { // @nowarn - Java interop
            return isFormattingRegionAt(offset, lastChild, checkParent = false)
          } else {
            return true // no lastChild means formatting region
          }
        } else if (node.isInstanceOf[HtmlCommentBlock] || node.isInstanceOf[HtmlInnerBlockComment]) {
          val formatterOff = isFormatterOffTag(Nullable(node.chars))
          if (formatterOff.isDefined) return !formatterOff.get
        }
      }

      if (node.previous.isEmpty && checkingParent) {
        node = node.parent.getOrElse(null) // @nowarn - Java interop: null for loop termination
        if (node.isInstanceOf[Document]) {
          continue = false
        } else if (node != null) { // @nowarn - Java interop
          node = node.previous.getOrElse(null) // @nowarn - Java interop: null for loop termination
        }
      } else {
        node = node.previous.getOrElse(null) // @nowarn - Java interop: null for loop termination
      }
    }
    true
  }
}

object FormatControlProcessor {
  val OPEN_COMMENT:  String = "<!--"
  val CLOSE_COMMENT: String = "-->"
}
