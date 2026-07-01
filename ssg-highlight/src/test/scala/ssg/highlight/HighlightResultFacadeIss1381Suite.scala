/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

import ssg.commons.Severity

/** Differential tests for the ISS-1381 error-contract facade `SyntaxHighlighter.highlightResult` (docs/architecture/error-contracts.md §2.9).
  *
  * The facade is `DiagResult.fromEither` over the existing `Either[HighlightError, String]` contract, so its behavior is exercised directly against a hand-built `SyntaxHighlighter` returning a chosen
  * `Either` (each of the three `HighlightError` cases + a `Right`), plus one end-to-end check through `SyntaxHighlighter.default` for the only failure mode reachable via the public API on every
  * platform (an unregistered language -> `UnknownLanguage`, which never touches grammar loading).
  *
  * Reaching `MissingQuery` / `QueryLoadFailed` through the real engine is deliberately NOT attempted here -- that requires a grammar-registration seam and is ISS-1372's territory. These tests pin the
  * facade's mapping (severity, component, code, message, `position = None`) rather than the engine's Left-case reachability.
  */
final class HighlightResultFacadeIss1381Suite extends munit.FunSuite {

  /** A `SyntaxHighlighter` whose `highlight` always returns `fixed`, so the facade's transformation can be tested independently of tree-sitter grammar availability (which differs per platform).
    */
  private def fixedHighlighter(fixed: Either[HighlightError, String]): SyntaxHighlighter =
    new SyntaxHighlighter {
      override def highlight(source:          String, language: String): Either[HighlightError, String] = fixed
      override def supportsLanguage(language: String):                   Boolean                        = fixed.isRight
      override def supportedLanguages:                                   Seq[String]                    = Seq.empty
    }

  test("ISS-1381: highlightResult maps Left(UnknownLanguage) to an Error failure diagnostic") {
    val result = fixedHighlighter(Left(HighlightError.UnknownLanguage)).highlightResult("class X {}", "nope")
    assert(result.isFailure, "Left must produce a failure (value absent)")
    assertEquals(result.value, None)
    assertEquals(result.diagnostics.size, 1)
    val diagnostic = result.diagnostics.head
    assertEquals(diagnostic.severity, Severity.Error)
    assertEquals(diagnostic.component, "ssg-highlight")
    assertEquals(diagnostic.code, Some("UnknownLanguage"))
    assertEquals(diagnostic.position, None)
    assertEquals(diagnostic.message, "No registered grammar matched the requested language.")
  }

  test("ISS-1381: highlightResult maps Left(MissingQuery) to an Error failure diagnostic") {
    val result = fixedHighlighter(Left(HighlightError.MissingQuery)).highlightResult("x", "lang")
    assert(result.isFailure)
    val diagnostic = result.diagnostics.head
    assertEquals(diagnostic.severity, Severity.Error)
    assertEquals(diagnostic.component, "ssg-highlight")
    assertEquals(diagnostic.code, Some("MissingQuery"))
    assertEquals(diagnostic.position, None)
    assertEquals(diagnostic.message, "The grammar is registered but has no highlight query directory mapped.")
  }

  test("ISS-1381: highlightResult maps Left(QueryLoadFailed) to an Error failure diagnostic") {
    val result = fixedHighlighter(Left(HighlightError.QueryLoadFailed)).highlightResult("x", "lang")
    assert(result.isFailure)
    val diagnostic = result.diagnostics.head
    assertEquals(diagnostic.severity, Severity.Error)
    assertEquals(diagnostic.component, "ssg-highlight")
    assertEquals(diagnostic.code, Some("QueryLoadFailed"))
    assertEquals(diagnostic.position, None)
    assertEquals(diagnostic.message, "Loading the highlight query for the grammar failed.")
  }

  test("ISS-1381: highlightResult maps Right(html) to a clean success carrying the same bytes") {
    val html   = "<span class=\"hl-keyword\">class</span>"
    val result = fixedHighlighter(Right(html)).highlightResult("class X {}", "scala")
    assert(result.isSuccess, "Right must produce a clean success")
    assert(!result.isDegraded)
    assert(!result.hasErrors)
    assertEquals(result.value, Some(html))
    assertEquals(result.diagnostics, Vector.empty)
  }

  test("ISS-1381: the three HighlightError cases render three distinct messages") {
    val messages =
      Seq(HighlightError.UnknownLanguage, HighlightError.MissingQuery, HighlightError.QueryLoadFailed).map { err =>
        fixedHighlighter(Left(err)).highlightResult("x", "lang").diagnostics.head.message
      }
    assertEquals(messages.distinct.size, 3, s"expected 3 distinct messages, got: $messages")
  }

  test("ISS-1381: highlightResult on the default engine fails for an unregistered language") {
    // UnknownLanguage is the one failure mode reachable through the real public API on every platform:
    // resolveGrammar returns None before any grammar/query loading, so this needs no tree-sitter runtime.
    val result = SyntaxHighlighter.default.highlightResult("class X {}", "definitely-not-a-language-xyz")
    assert(result.isFailure, "an unregistered language must fail")
    assertEquals(result.value, None)
    val diagnostic = result.diagnostics.head
    assertEquals(diagnostic.severity, Severity.Error)
    assertEquals(diagnostic.component, "ssg-highlight")
    assertEquals(diagnostic.code, Some("UnknownLanguage"))
    assertEquals(diagnostic.position, None)
  }
}
