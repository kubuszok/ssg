/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../html/PathologicalTest.java
 * and flexmark-core-test/.../html/PathologicalRenderingTestCase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package core
package test

import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.test.util.Strings
import ssg.md.util.data.MutableDataSet

import scala.language.implicitConversions

/** Pathological test cases for the parser (spcInLinkUrls = false).
  *
  * These tests verify that the parser handles extreme/pathological inputs without hanging or running out of resources.
  */
final class PathologicalSuite extends munit.FunSuite {

  private val x = 100000

  private val options = new MutableDataSet().set(Parser.SPACE_IN_LINK_URLS, false).toImmutable

  private def assertRendering(source: String, expectedHtml: String): Unit = {
    val parser   = Parser.builder(options).build()
    val renderer = HtmlRenderer.builder(options).build()
    val document = parser.parse(source)
    val html     = renderer.render(document)
    assertEquals(html, expectedHtml)
  }

  test("nestedStrongEmphasis") {
    // this is limited by the stack size because visitor is recursive
    val n = 500
    assertRendering(
      Strings.repeat("*a **a ", n) + "b" + Strings.repeat(" a** a*", n),
      "<p>" + Strings.repeat("<em>a <strong>a ", n) + "b" +
        Strings.repeat(" a</strong> a</em>", n) + "</p>\n"
    )
  }

  test("emphasisClosersWithNoOpeners") {
    assertRendering(
      Strings.repeat("a_ ", x),
      "<p>" + Strings.repeat("a_ ", x - 1) + "a_</p>\n"
    )
  }

  test("emphasisOpenersWithNoClosers") {
    assertRendering(
      Strings.repeat("_a ", x),
      "<p>" + Strings.repeat("_a ", x - 1) + "_a</p>\n"
    )
  }

  test("linkClosersWithNoOpeners") {
    assertRendering(
      Strings.repeat("a] ", x),
      "<p>" + Strings.repeat("a] ", x - 1) + "a]</p>\n"
    )
  }

  test("linkOpenersWithNoClosers") {
    assertRendering(
      Strings.repeat("[a ", x),
      "<p>" + Strings.repeat("[a ", x - 1) + "[a</p>\n"
    )
  }

  test("linkOpenersAndEmphasisClosers") {
    assertRendering(
      Strings.repeat("[ a_ ", x),
      "<p>" + Strings.repeat("[ a_ ", x - 1) + "[ a_</p>\n"
    )
  }

  test("mismatchedOpenersAndClosers") {
    assertRendering(
      Strings.repeat("*a_ ", x),
      "<p>" + Strings.repeat("*a_ ", x - 1) + "*a_</p>\n"
    )
  }

  test("nestedBrackets") {
    assertRendering(
      Strings.repeat("[", x) + "a" + Strings.repeat("]", x),
      "<p>" + Strings.repeat("[", x) + "a" + Strings.repeat("]", x) + "</p>\n"
    )
  }

  test("longImageLinkTest".tag(munit.Slow)) {
    // This test can be slow because of the large x value
    try
      assertRendering(
        "![" + Strings.repeat("a", x) + "](" + Strings.repeat("a", x) + ")",
        "<p><img src=\"" + Strings.repeat("a", x) + "\" alt=\"" + Strings.repeat("a", x) + "\" /></p>\n"
      )
    catch {
      case _: StackOverflowError =>
        System.err.print("StackOverflow ")
    }
  }

  test("longLinkTest".tag(munit.Slow)) {
    // This test can be slow because of the large x value
    try
      assertRendering(
        "[" + Strings.repeat("a", x) + "](" + Strings.repeat("a", x) + ")",
        "<p><a href=\"" + Strings.repeat("a", x) + "\">" + Strings.repeat("a", x) + "</a></p>\n"
      )
    catch {
      case _: StackOverflowError =>
        System.err.print("StackOverflow ")
    }
  }
}
