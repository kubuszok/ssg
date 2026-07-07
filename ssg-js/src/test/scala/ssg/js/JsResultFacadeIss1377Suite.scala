/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package js

import ssg.commons.Severity
import ssg.js.parse.JsParseError

/** Differential tests for the ISS-1377 error-contract facade `Terser.minifyResult` (docs/architecture/error-contracts.md §2.5).
  *
  * `minifyResult` is the additive `DiagResult` envelope over the throwing `Terser.minify`: it catches ONLY the module-native `JsParseError` (§1.2 rule 3) and maps it to a `Severity.Error` failure
  * diagnostic (component `"ssg-js"`, code `"parse-error"`, the native exception preserved as cause) whose position follows the §1.3 ssg-js row: `source = e.filename`, `line = e.line` (1-based
  * passthrough), `column = e.col + 1` (the off-by-one trap), `offset = e.pos`. A clean minify is a `DiagResult.success` carrying the same code bytes as `Terser.minify`.
  *
  * Ground truth for the two invalid inputs is taken from the native parser itself (intercepting `Terser.minify`) and pinned with LITERAL expected values per §3:
  *   - `"var ok = 1;\n)"` — a bare `)` at the start of line 2 → `JsParseError(filename = "0", line = 2, col = 0, pos = 12)`, so `column == 1`.
  *   - `"var x = (1 + 2));"` — an extra `)` on line 1 → `JsParseError(filename = "0", line = 1, col = 15, pos = 15)`, so `column == 16` (pins `col + 1`).
  */
final class JsResultFacadeIss1377Suite extends munit.FunSuite {

  // A bare `)` at the start of line 2: `Unexpected token: punc ())`.
  private val badMultiLine = "var ok = 1;\n)"
  // An extra closing paren on line 1: the off-by-one column pin (native col 15 -> column 16).
  private val badSingleLine = "var x = (1 + 2));"

  test("ISS-1377: minifyResult maps a JsParseError to an Error failure with the §1.3 mapped position") {
    // Ground truth from the unchanged native parser.
    val native = intercept[JsParseError](Terser.minify(badMultiLine))
    assertEquals(native.filename, "0")
    assertEquals(native.line, 2)
    assertEquals(native.col, 0)
    assertEquals(native.pos, 12)

    val result = Terser.minifyResult(badMultiLine)
    assert(result.isFailure, s"a parse error must produce a failure, got $result")
    assertEquals(result.value, None)
    assertEquals(result.diagnostics.size, 1, s"diagnostics: ${result.diagnostics}")
    val d = result.diagnostics.head
    assertEquals(d.severity, Severity.Error)
    assertEquals(d.component, "ssg-js")
    assertEquals(d.code, Some("parse-error"))
    assert(d.cause.exists(_.isInstanceOf[JsParseError]), s"cause must be the native JsParseError, got ${d.cause}")

    val pos = d.position.getOrElse(fail("expected a position"))
    // §1.3 ssg-js row: source = filename, line = line (1-based), column = col + 1, offset = pos.
    assertEquals(pos.source, Some("0"))
    assertEquals(pos.line, Some(2))
    assertEquals(pos.column, Some(1)) // native col 0 + 1
    assertEquals(pos.offset, Some(12))
    // And the mapping is exactly the native fields with the documented +1.
    assertEquals(pos.line, Some(native.line))
    assertEquals(pos.column, Some(native.col + 1))
    assertEquals(pos.offset, Some(native.pos))
    assertEquals(pos.source, Some(native.filename))
  }

  test("ISS-1377: minifyResult pins the col + 1 off-by-one on a single-line parse error") {
    val native = intercept[JsParseError](Terser.minify(badSingleLine))
    assertEquals(native.line, 1)
    assertEquals(native.col, 15)
    assertEquals(native.pos, 15)

    val result = Terser.minifyResult(badSingleLine)
    assert(result.isFailure, s"expected a failure, got $result")
    val pos = result.diagnostics.head.position.getOrElse(fail("expected a position"))
    assertEquals(pos.line, Some(1))
    assertEquals(pos.column, Some(16)) // native col 15 + 1 — the off-by-one trap
    assertEquals(pos.offset, Some(15))
  }

  test("ISS-1377: minifyResult is a clean success carrying the same code bytes as Terser.minify") {
    val code   = "var x = 1 + 2;"
    val legacy = Terser.minify(code)
    val result = Terser.minifyResult(code)

    assert(result.isSuccess, s"a clean minify must be a success, got $result")
    assert(!result.isDegraded)
    assert(!result.hasErrors)
    assertEquals(result.diagnostics, Vector.empty)
    assertEquals(result.value.map(_.code), Some(legacy.code))
  }
}
