/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../parser/HtmlDeepParserTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package core
package test

import ssg.md.parser.internal.HtmlDeepParser

import scala.language.implicitConversions

final class HtmlDeepParserSuite extends munit.FunSuite {

  private def parseHtml(html: String, blockTagsOnly: Boolean, parseNonBlock: Boolean, openOnOneLine: Boolean): HtmlDeepParser = {
    val deepParser = new HtmlDeepParser()
    val htmlLines  = html.split("\n")
    for (htmlLine <- htmlLines)
      deepParser.parseHtmlChunk(htmlLine, blockTagsOnly, parseNonBlock, openOnOneLine)
    deepParser
  }

  test("test_openBlock") {
    val deepParser = parseHtml("<div>", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, false)
    assertEquals(deepParser.isBlankLineInterruptible, false)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_closedBlock") {
    val deepParser = parseHtml("<div>\n</div>", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_openPartialBlock") {
    val deepParser = parseHtml("<div", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, false)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_closedPartialBlock") {
    val deepParser = parseHtml("<div\n>\n</div>", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_openBlock1") {
    val deepParser = parseHtml("<div><p>asdfasdfsadf\nasdfsadfdsaf</p>", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, false)
    assertEquals(deepParser.isBlankLineInterruptible, false)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_closedBlock1") {
    val deepParser = parseHtml("<div><p>asdfasdfsadf\nasdfsadfdsaf\n</p>\n</div>", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_openBlock2") {
    val deepParser = parseHtml("<div><div attr\n", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, false)
    assertEquals(deepParser.isBlankLineInterruptible, false)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_closedBlock2") {
    val deepParser = parseHtml("<div attr\n>\n<p>asdfasdfsadf\nasdfsadfdsaf\n</p>\n</div>", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_openBlockOneLine") {
    val deepParser = parseHtml("<div", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = true)
    assertEquals(deepParser.hadHtml, false)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_openBlock2OneLine") {
    val deepParser = parseHtml("<div attr\n", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = true)
    assertEquals(deepParser.hadHtml, false)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_void") {
    val deepParser = parseHtml("<hr>", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_void1") {
    val deepParser = parseHtml("<br>", blockTagsOnly = false, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_selfClosed") {
    val deepParser = parseHtml("<div />", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_selfClosed0") {
    val deepParser = parseHtml("<div/>", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_selfClosed1") {
    val deepParser = parseHtml("<img />", blockTagsOnly = false, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_openComment") {
    val deepParser = parseHtml("<div><!-- comment with blank line", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, false)
    assertEquals(deepParser.isBlankLineInterruptible, false)
    assertEquals(deepParser.haveOpenRawTag, true)
  }

  test("test_closedComment") {
    val deepParser = parseHtml("<div><!-- comment with blank line\n\n-->", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, false)
    assertEquals(deepParser.isBlankLineInterruptible, false)
  }

  test("test_closedCommentBlock") {
    val deepParser = parseHtml("<div><!-- comment with blank line\n\n-->\n</div>", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_openCDATA") {
    val deepParser = parseHtml("<div><![CDATA[", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, false)
    assertEquals(deepParser.isBlankLineInterruptible, false)
    assertEquals(deepParser.haveOpenRawTag, true)
  }

  test("test_closedCDATA") {
    val deepParser = parseHtml("<div><![CDATA[\n]]>", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, false)
    assertEquals(deepParser.isBlankLineInterruptible, false)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_closedCDATABlock") {
    val deepParser = parseHtml("<div><![CDATA[\n]]>\n</div>", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_ignoreNonBlock") {
    val deepParser = parseHtml("<strong>", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, false)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_ignoreNonBlock1") {
    val deepParser = parseHtml("<strong>", blockTagsOnly = true, parseNonBlock = false, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, false)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_ignoreNonBlock2") {
    val deepParser = parseHtml("<strong>", blockTagsOnly = false, parseNonBlock = false, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, false)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_ignoreNonBlock3") {
    val deepParser = parseHtml("<strong><div>", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, false)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_ignoreNonBlock4") {
    val deepParser = parseHtml("<strong><div>", blockTagsOnly = true, parseNonBlock = false, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, false)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_ignoreNonBlock5") {
    val deepParser = parseHtml("<strong><!--", blockTagsOnly = false, parseNonBlock = false, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, false)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_ignoreNonBlock6") {
    val deepParser = parseHtml("<strong><!--", blockTagsOnly = false, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, false)
    assertEquals(deepParser.isBlankLineInterruptible, false)
    assertEquals(deepParser.haveOpenRawTag, true)
  }

  test("test_openNonBlock") {
    val deepParser = parseHtml("<strong>", blockTagsOnly = false, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, false)
    assertEquals(deepParser.isBlankLineInterruptible, false)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_closedNonBlock") {
    val deepParser = parseHtml("<strong></strong>", blockTagsOnly = false, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_openNonBlock2") {
    val deepParser = parseHtml("<div><strong>\n</strong>", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, false)
    assertEquals(deepParser.isBlankLineInterruptible, false)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_closedNonBlock2") {
    val deepParser = parseHtml("<div><strong>\n</strong>\n</div>", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_closedNonBlock3") {
    val deepParser = parseHtml("<div><strong>\n</div>", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_openPartial") {
    val deepParser = parseHtml("<p class=\"test\"\n", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, false)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_optionalOpen") {
    val deepParser = parseHtml("<p>par 1\n<p>par 2\n<p>par 3", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, false)
    assertEquals(deepParser.isBlankLineInterruptible, false)
    assertEquals(deepParser.haveOpenRawTag, false)
  }

  test("test_optionalClosed") {
    val deepParser = parseHtml("<p>par 1\n<p>par 2\n<p>par 3</p>\n", blockTagsOnly = true, parseNonBlock = true, openOnOneLine = false)
    assertEquals(deepParser.hadHtml, true)
    assertEquals(deepParser.isHtmlClosed, true)
    assertEquals(deepParser.isBlankLineInterruptible, true)
    assertEquals(deepParser.haveOpenRawTag, false)
  }
}
