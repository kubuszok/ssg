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

  // --- Version ---

  test("Version.parse parses semver strings") {
    assertEquals(Version.parse("1.23.0"), Version(1, 23, 0))
    assertEquals(Version.parse("0.0.0"), Version(0, 0, 0))
    assertEquals(Version.parse("1.2.3"), Version(1, 2, 3))
  }

  test("Version comparison is numeric, not lexical") {
    // This is the key test: 1.10.0 > 1.9.0, not 1.10.0 < 1.9.0
    assert(Version(1, 10, 0) > Version(1, 9, 0), "1.10.0 should be greater than 1.9.0")
    assert(Version(1, 9, 0) < Version(1, 10, 0), "1.9.0 should be less than 1.10.0")
    assert(Version(2, 0, 0) > Version(1, 99, 99), "2.0.0 should be greater than 1.99.99")
  }

  test("Version.tryParse returns None for invalid strings") {
    assertEquals(Version.tryParse("invalid"), None)
    assertEquals(Version.tryParse("1.2.x"), None)
  }

  test("Deprecation.forVersion returns deprecations up to the given version") {
    // CallString was deprecated in 0.0.0, should always be included
    val v1    = Version(1, 0, 0)
    val deps1 = Deprecation.forVersion(v1)
    assert(deps1.contains(Deprecation.CallString), "CallString should be in deprecations for v1.0.0")

    // SlashDiv was deprecated in 1.33.0, so it should not be in v1.0.0
    assert(!deps1.contains(Deprecation.SlashDiv), "SlashDiv should NOT be in deprecations for v1.0.0")

    // SlashDiv should be in 1.33.0 and later
    val v133    = Version(1, 33, 0)
    val deps133 = Deprecation.forVersion(v133)
    assert(deps133.contains(Deprecation.SlashDiv), "SlashDiv should be in deprecations for v1.33.0")

    // MixedDecls is obsolete (has obsoleteIn set), so should NOT be included
    val v200    = Version(2, 0, 0)
    val deps200 = Deprecation.forVersion(v200)
    assert(!deps200.contains(Deprecation.MixedDecls), "MixedDecls is obsolete, should not be included")

    // UserAuthored has no deprecatedIn, should not be included
    assert(!deps200.contains(Deprecation.UserAuthored), "UserAuthored has no deprecatedIn, should not be included")
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
