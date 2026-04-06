/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass

import ssg.sass.util.FileSpan

import scala.language.implicitConversions

final class Phase2Suite extends munit.FunSuite {

  // --- Syntax ---

  test("Syntax.forPath detects SCSS") {
    assertEquals(Syntax.forPath("style.scss"), Syntax.Scss)
  }

  test("Syntax.forPath detects Sass") {
    assertEquals(Syntax.forPath("style.sass"), Syntax.Sass)
  }

  test("Syntax.forPath detects CSS") {
    assertEquals(Syntax.forPath("style.css"), Syntax.Css)
  }

  test("Syntax.forPath defaults to SCSS") {
    assertEquals(Syntax.forPath("style.less"), Syntax.Scss)
  }

  test("Syntax toString returns display name") {
    assertEquals(Syntax.Scss.toString, "SCSS")
    assertEquals(Syntax.Sass.toString, "Sass")
    assertEquals(Syntax.Css.toString, "CSS")
  }

  // --- Deprecation ---

  test("Deprecation.fromId finds known deprecation") {
    assertEquals(Deprecation.fromId("slash-div"), Some(Deprecation.SlashDiv))
    assertEquals(Deprecation.fromId("import"), Some(Deprecation.Import))
  }

  test("Deprecation.fromId returns None for unknown") {
    assertEquals(Deprecation.fromId("nonexistent"), None)
  }

  test("Deprecation has correct id") {
    assertEquals(Deprecation.CallString.id, "call-string")
    assertEquals(Deprecation.UserAuthored.id, "user-authored")
  }

  test("Deprecation.UserAuthored has no deprecatedIn") {
    assert(Deprecation.UserAuthored.deprecatedIn.isEmpty)
  }

  test("Deprecation toString returns id") {
    assertEquals(Deprecation.SlashDiv.toString, "slash-div")
  }

  // --- SassException ---

  test("SassException stores message and span") {
    val span = FileSpan.synthetic("test")
    val ex   = SassException("bad input", span)
    assertEquals(ex.sassMessage, "bad input")
    assert(ex.toString.contains("bad input"))
  }

  test("SassException.withTrace creates SassRuntimeException") {
    val span  = FileSpan.synthetic("test")
    val trace = ssg.sass.util.Trace.empty
    val ex    = SassException("msg", span).withTrace(trace)
    assert(ex.isInstanceOf[SassRuntimeException])
  }

  test("SassFormatException has source and offset") {
    val file = ssg.sass.util.SourceFile("test.scss", "abc\ndef")
    val span = file.span(4, 7)
    val ex   = SassFormatException("parse error", span)
    assertEquals(ex.source, "abc\ndef")
    assertEquals(ex.offset, 4)
  }

  test("SassScriptException includes argument name") {
    val ex = SassScriptException("must be positive", Some("width"))
    assert(ex.fullMessage.contains("$width"))
    assert(ex.fullMessage.contains("must be positive"))
  }

  test("SassScriptException.withSpan creates SassException") {
    val ex     = SassScriptException("error")
    val span   = FileSpan.synthetic("x")
    val sassEx = ex.withSpan(span)
    assert(sassEx.isInstanceOf[SassException])
  }

  test("SassException.toCssString produces valid CSS") {
    val span = FileSpan.synthetic("x")
    val ex   = SassException("test error", span)
    val css  = ex.toCssString
    assert(css.contains("body::before"))
    assert(css.contains("content:"))
  }

  // --- Logger ---

  test("Logger.quiet emits no output") {
    // Should not throw
    Logger.quiet.warn("test")
    Logger.quiet.debug("test", FileSpan.synthetic("x"))
  }

  test("TrackingLogger tracks emissions") {
    val tracker = TrackingLogger(Logger.quiet)
    assert(!tracker.emittedWarning)
    assert(!tracker.emittedDebug)
    tracker.warn("test")
    assert(tracker.emittedWarning)
    tracker.debug("test", FileSpan.synthetic("x"))
    assert(tracker.emittedDebug)
  }

  // --- Utils ---

  test("Utils.toSentence joins with conjunction") {
    assertEquals(Utils.toSentence(List("a")), "a")
    assertEquals(Utils.toSentence(List("a", "b")), "a and b")
    assertEquals(Utils.toSentence(List("a", "b", "c")), "a, b and c")
    assertEquals(Utils.toSentence(List("a", "b"), "or"), "a or b")
  }

  test("Utils.indent adds spaces") {
    assertEquals(Utils.indent("a\nb", 2), "  a\n  b")
  }

  test("Utils.pluralize handles singular and plural") {
    assertEquals(Utils.pluralize("item", 1), "item")
    assertEquals(Utils.pluralize("item", 2), "items")
    assertEquals(Utils.pluralize("index", 2, "indices"), "indices")
  }

  test("Utils.isPublic identifies public members") {
    assert(Utils.isPublic("color"))
    assert(!Utils.isPublic("-private"))
    assert(!Utils.isPublic("_private"))
  }

  test("Utils.unvendor strips vendor prefix") {
    assertEquals(Utils.unvendor("-webkit-transform"), "transform")
    assertEquals(Utils.unvendor("-moz-transition"), "transition")
    assertEquals(Utils.unvendor("color"), "color")
    assertEquals(Utils.unvendor("--custom"), "--custom")
  }

  test("Utils.equalsIgnoreCase compares case-insensitively") {
    assert(Utils.equalsIgnoreCase("Hello", "hello"))
    assert(Utils.equalsIgnoreCase("ABC", "abc"))
    assert(!Utils.equalsIgnoreCase("abc", "abd"))
  }

  test("Utils.startsWithIgnoreCase checks prefix") {
    assert(Utils.startsWithIgnoreCase("HelloWorld", "hello"))
    assert(!Utils.startsWithIgnoreCase("Hi", "hello"))
  }

  test("Utils.trimAscii trims ASCII whitespace") {
    assertEquals(Utils.trimAscii("  hello  "), "hello")
    assertEquals(Utils.trimAscii("   "), "")
  }

  test("Utils.longestCommonSubsequence finds LCS") {
    val result = Utils.longestCommonSubsequence(
      List(1, 2, 3, 4, 5),
      List(2, 4, 6)
    )
    assertEquals(result, List(2, 4))
  }

  test("Utils.flattenVertically interleaves") {
    val result = Utils.flattenVertically(
      List(
        List("1a", "1b"),
        List("2a", "2b")
      )
    )
    assertEquals(result, List("1a", "2a", "1b", "2b"))
  }

  test("Utils.countOccurrences counts correctly") {
    assertEquals(Utils.countOccurrences("hello", 'l'.toInt), 2)
    assertEquals(Utils.countOccurrences("hello", 'z'.toInt), 0)
  }

  test("Utils.a returns correct article") {
    assertEquals(Utils.a("element"), "an element")
    assertEquals(Utils.a("div"), "a div")
  }
}
