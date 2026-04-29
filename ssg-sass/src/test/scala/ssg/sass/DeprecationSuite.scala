/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package sass

/** Covers the deprecation warnings emitted by the parser and evaluator. Each case compiles a tiny SCSS snippet and asserts the corresponding deprecation id appears in `CompileResult.warnings`.
  */
final class DeprecationSuite extends munit.FunSuite {

  private def compile(src: String): CompileResult =
    Compile.compileString(src)

  private def assertHasDeprecation(result: CompileResult, id: String, clue: String = ""): Unit = {
    val found = result.warnings.exists(_.contains(s"[$id]"))
    assert(found, s"Expected deprecation [$id] in warnings, got:\n${result.warnings.mkString("\n")}\n$clue")
  }

  test("@elseif emits elseif deprecation") {
    // Dropped into an unknown at-rule in the dispatch, since @if/@else aren't
    // parsed by ssg-sass yet. The parser still records the deprecation.
    val r = compile("@elseif (true) { color: red; }")
    assertHasDeprecation(r, "elseif")
  }

  test("@-moz-document emits moz-document deprecation") {
    val r = compile("@-moz-document url(http://example.com) { a { color: red; } }")
    assertHasDeprecation(r, "moz-document")
  }

  test("slash-div emits slash-div deprecation for number / number in parentheses") {
    // Parentheses force division (rather than slash-separated literal), triggering the deprecation.
    // Without parentheses, `10 / 2` is a slash-separated number (outputs `10/2`, no warning).
    val r = compile("a { x: (10 / 2); }")
    assertHasDeprecation(r, "slash-div")
  }

  test("slash-div: unparenthesized slash is a slash-separated literal (no warning)") {
    // dart-sass preserves `10 / 2` as `10/2` in output — no deprecation warning.
    val r     = compile("a { x: 10 / 2; }")
    val found = r.warnings.exists(_.contains("[slash-div]"))
    assert(!found, s"Expected NO slash-div warning for slash-separated literal, got:\n${r.warnings.mkString("\n")}")
  }

  test("lighten() emits color-functions deprecation") {
    val r = compile("a { x: lighten(#000, 10%); }")
    assertHasDeprecation(r, "color-functions")
  }

  test("darken() emits color-functions deprecation") {
    val r = compile("a { x: darken(#fff, 10%); }")
    assertHasDeprecation(r, "color-functions")
  }

  test("saturate() emits color-functions deprecation") {
    val r = compile("a { x: saturate(#808080, 10%); }")
    assertHasDeprecation(r, "color-functions")
  }

  test("desaturate() emits color-functions deprecation") {
    val r = compile("a { x: desaturate(#808080, 10%); }")
    assertHasDeprecation(r, "color-functions")
  }

  test("opacify() emits color-functions deprecation") {
    val r = compile("a { x: opacify(rgba(0, 0, 0, 0.5), 0.2); }")
    assertHasDeprecation(r, "color-functions")
  }

  test("transparentize() emits color-functions deprecation") {
    val r = compile("a { x: transparentize(rgba(0, 0, 0, 0.5), 0.2); }")
    assertHasDeprecation(r, "color-functions")
  }

  test("rgb(color, alpha) compiles without error") {
    // In dart-sass, the 2-arg rgb(color, alpha) form does not emit a
    // color-module-compat deprecation -- it either throws for non-legacy
    // colors or succeeds silently for legacy colors.
    val r = compile("a { x: rgb(#808080, 0.5); }")
    assert(r.css.nonEmpty, "Expected successful compilation")
  }

  test("feature-exists() emits feature-exists deprecation") {
    val r = compile("a { x: feature-exists(global-variable-shadowing); }")
    assertHasDeprecation(r, "feature-exists")
  }

  test("abs(10%) emits abs-percent deprecation") {
    val r = compile("a { x: abs(10%); }")
    assertHasDeprecation(r, "abs-percent")
  }

  test("DeprecationProcessingLogger forwards warnings through inner logger") {
    val inner = new TrackingLogger(Logger.quiet)
    val dp    = new DeprecationProcessingLogger(inner)
    dp.warnForDeprecation(Deprecation.SlashDiv, "test message")
    assert(inner.emittedWarning, "inner logger should have received a warning")
  }

  test("DeprecationProcessingLogger silences configured deprecations") {
    val inner = new TrackingLogger(Logger.quiet)
    val dp    = new DeprecationProcessingLogger(inner, silenceDeprecations = Set(Deprecation.SlashDiv))
    dp.warnForDeprecation(Deprecation.SlashDiv, "test message")
    assert(!inner.emittedWarning, "silenced deprecation should not reach inner logger")
  }

  test("DeprecationProcessingLogger.validate warns about obsolete deprecations marked fatal") {
    val warnings = scala.collection.mutable.ArrayBuffer.empty[String]
    val inner    = new Logger {
      def warn(
        message:     String,
        span:        Nullable[ssg.sass.util.FileSpan],
        trace:       Nullable[ssg.sass.util.Trace],
        deprecation: Nullable[Deprecation]
      ):                                                        Unit = warnings += message
      def debug(message: String, span: ssg.sass.util.FileSpan): Unit = ()
    }
    // MixedDecls has obsoleteIn set
    val dp = new DeprecationProcessingLogger(inner, fatalDeprecations = Set(Deprecation.MixedDecls))
    dp.validate()
    assert(warnings.exists(_.contains("obsolete")), s"Expected obsolete warning, got: $warnings")
  }

  test("DeprecationProcessingLogger.validate warns about silencing user-authored") {
    val warnings = scala.collection.mutable.ArrayBuffer.empty[String]
    val inner    = new Logger {
      def warn(
        message:     String,
        span:        Nullable[ssg.sass.util.FileSpan],
        trace:       Nullable[ssg.sass.util.Trace],
        deprecation: Nullable[Deprecation]
      ):                                                        Unit = warnings += message
      def debug(message: String, span: ssg.sass.util.FileSpan): Unit = ()
    }
    val dp = new DeprecationProcessingLogger(inner, silenceDeprecations = Set(Deprecation.UserAuthored))
    dp.validate()
    assert(warnings.exists(_.contains("User-authored")), s"Expected user-authored warning, got: $warnings")
  }

  test("DeprecationProcessingLogger.summarize reports omitted warnings") {
    val warnings = scala.collection.mutable.ArrayBuffer.empty[String]
    val inner    = new Logger {
      def warn(
        message:     String,
        span:        Nullable[ssg.sass.util.FileSpan],
        trace:       Nullable[ssg.sass.util.Trace],
        deprecation: Nullable[Deprecation]
      ):                                                        Unit = warnings += message
      def debug(message: String, span: ssg.sass.util.FileSpan): Unit = ()
    }
    val dp = new DeprecationProcessingLogger(inner)
    // Emit more than maxRepetitions (5) warnings for the same deprecation
    for (_ <- 1 to 10)
      dp.warnForDeprecation(Deprecation.SlashDiv, "test")
    dp.summarize()
    // Should report 5 omitted warnings (10 - 5 = 5)
    assert(
      warnings.exists(_.contains("5 repetitive deprecation warnings omitted")),
      s"Expected summary of 5 omitted warnings, got: $warnings"
    )
  }
}
