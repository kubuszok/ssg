/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Differential tests for the ISS-1378 error-contract facades over the
 * ssg-katex render entry points (docs/architecture/error-contracts.md §2.6):
 *
 *   - KaTeX.renderToStringResult(expression, options: Settings)
 *   - KaTeX.renderToStringResult(expression, options: KaTeXOptions)
 *   - KaTeXOptions.renderToStringResult(expression)
 *   - KaTeXOptions.renderToStringResult(expression, options: KaTeXOptions)
 *
 * Each facade wraps the corresponding throwing renderToString entry point in
 * the shared ssg.commons.DiagResult envelope, catching ONLY the module-native
 * ParseError (§1.2 rule 3). With throwOnError = true a caught ParseError is a
 * failure; with throwOnError = false it is a DEGRADED result carrying the SAME
 * katex-error span markup the legacy path emits in-band. Position maps per the
 * §1.3 katex row: offset/endOffset from the ParseError's 0-based char offset
 * and length when both are present, else None.
 *
 * The invalid inputs are genuine LaTeX the parser rejects (also pinned by
 * ErrorsSpecSuite):
 *   - "\\xyz"       -> "Undefined control sequence: \\xyz" at position 1,
 *                      i.e. 0-based offset 0, underlined length 4 -> offsetRange(0, 4)
 *   - "\\verb|hello" -> "\\verb ended by end of line..." thrown with NO token
 *                      (VerbFunc.scala:48) -> position stays None (§1.3 else branch)
 */
package ssg
package katex

import ssg.commons.{ Severity, SourcePosition }

final class KatexResultFacadeIss1378Suite extends munit.FunSuite {

  private val validExpr   = "x^2"
  private val invalidExpr = "\\xyz"
  private val hardExpr    = "1^2^3"
  private val verbExpr    = "\\verb|hello"

  test("ISS-1378: KaTeX.renderToStringResult(Settings) success carries the same bytes as renderToString") {
    val result = KaTeX.renderToStringResult(validExpr)
    assert(result.isSuccess, s"valid input must be a clean success, got: $result")
    assert(!result.hasErrors, "clean success carries no error diagnostics")
    assertEquals(result.value, Some(KaTeX.renderToString(validExpr)))
  }

  test("ISS-1378: KaTeX.renderToStringResult(Settings) with throwOnError=true fails with a parse-error diagnostic") {
    val result = KaTeX.renderToStringResult(invalidExpr, new Settings(throwOnError = true))
    assert(result.isFailure, s"throwOnError=true must fail (no value), got: $result")
    assertEquals(result.value, None)
    assertEquals(result.errors.size, 1)
    val diag = result.errors.head
    assertEquals(diag.severity, Severity.Error)
    assertEquals(diag.component, "ssg-katex")
    assertEquals(diag.code, Some("parse-error"))
    assert(diag.cause.exists(_.isInstanceOf[ParseError]), s"cause must be the native ParseError, got: ${diag.cause}")
  }

  test("ISS-1378: KaTeX.renderToStringResult(Settings) with throwOnError=false degrades to the legacy katex-error span") {
    val settings = new Settings(throwOnError = false)
    val result   = KaTeX.renderToStringResult(hardExpr, settings)
    assert(result.isDegraded, s"throwOnError=false must degrade (value present WITH errors), got: $result")
    assertEquals(result.value, Some(KaTeX.renderToString(hardExpr, new Settings(throwOnError = false))))
    assert(result.value.exists(_.contains("katex-error")), "degraded output is the in-band katex-error span")
    assertEquals(result.errors.size, 1)
    assertEquals(result.errors.head.code, Some("parse-error"))
  }

  test("ISS-1378: KaTeX.renderToStringResult position maps the §1.3 katex row to offsetRange(0, 4)") {
    val result = KaTeX.renderToStringResult(invalidExpr, new Settings(throwOnError = true))
    val diag   = result.errors.head
    assertEquals(diag.position, Some(SourcePosition.offsetRange(0, 4)))
  }

  test("ISS-1378: KaTeX.renderToStringResult leaves position None when the ParseError carries no token") {
    val result = KaTeX.renderToStringResult(verbExpr, new Settings(throwOnError = true))
    assert(result.isFailure, s"verb-delimiter error must fail, got: $result")
    assertEquals(result.errors.head.position, None)
  }

  test("ISS-1378: KaTeX.renderToStringResult(KaTeXOptions) success carries the same bytes as renderToString") {
    val opts   = KaTeXOptions(displayMode = true)
    val result = KaTeX.renderToStringResult(validExpr, opts)
    assert(result.isSuccess, s"valid input must be a clean success, got: $result")
    assertEquals(result.value, Some(KaTeX.renderToString(validExpr, opts)))
  }

  test("ISS-1378: KaTeX.renderToStringResult(KaTeXOptions) with throwOnError=false degrades") {
    val opts   = KaTeXOptions(throwOnError = false)
    val result = KaTeX.renderToStringResult(hardExpr, opts)
    assert(result.isDegraded, s"throwOnError=false must degrade, got: $result")
    assertEquals(result.value, Some(KaTeX.renderToString(hardExpr, opts)))
    assertEquals(result.errors.head.code, Some("parse-error"))
  }

  test("ISS-1378: KaTeXOptions.renderToStringResult(expr) default succeeds and matches the static renderToString") {
    val result = KaTeXOptions.renderToStringResult(validExpr)
    assert(result.isSuccess, s"valid input must be a clean success, got: $result")
    assertEquals(result.value, Some(KaTeXOptions.renderToString(validExpr)))
  }

  test("ISS-1378: KaTeXOptions.renderToStringResult(expr, options) with throwOnError=true fails") {
    val opts   = KaTeXOptions(throwOnError = true)
    val result = KaTeXOptions.renderToStringResult(invalidExpr, opts)
    assert(result.isFailure, s"throwOnError=true must fail, got: $result")
    assertEquals(result.errors.head.component, "ssg-katex")
    assertEquals(result.errors.head.code, Some("parse-error"))
  }

  test("ISS-1378: KaTeXOptions.renderToStringResult(expr, options) with throwOnError=false degrades to legacy bytes") {
    val opts   = KaTeXOptions(throwOnError = false)
    val result = KaTeXOptions.renderToStringResult(hardExpr, opts)
    assert(result.isDegraded, s"throwOnError=false must degrade, got: $result")
    assertEquals(result.value, Some(KaTeXOptions.renderToString(hardExpr, opts)))
    assert(result.value.exists(_.contains("katex-error")), "degraded output is the in-band katex-error span")
  }
}
