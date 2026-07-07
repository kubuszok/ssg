/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package sass

import ssg.commons.Severity
import ssg.sass.visitor.OutputStyle

/** Differential tests for the ISS-1375 error-contract facade `Compile.compileStringResult` (docs/architecture/error-contracts.md §2.3).
  *
  * The facade wraps the existing throwing `Compile.compileString` in the shared `DiagResult` envelope: a caught `SassException` becomes a failure carrying one `Severity.Error` diagnostic (component
  * `"ssg-sass"`, position mapped from the exception's `FileSpan` per the §1.3 sass row, `code` from the subclass); a successful compile becomes a success whose diagnostics are the
  * `CompileResult.warnings` turned into `Severity.Warning` diagnostics (still `isSuccess` — §1.1 severity policy). Additive only: `compileString`'s throwing contract is unchanged.
  *
  * The position assertions pin the §1.3 off-by-one traps with LITERAL expected values: the sass `FileLocation` is 0-based (SourceSpan.scala:54,70), so `line`/`column`/`endLine`/`endColumn` are the
  * raw span fields `+ 1`, while `offset`/`endOffset` ride along unchanged. Each mapping test also intercepts the legacy `compileString` for the SAME input and asserts the raw 0-based span, so the
  * `+ 1` is demonstrated against the source-of-truth rather than a hand-copied constant.
  */
final class SassResultFacadeIss1375Suite extends munit.FunSuite {

  // A multi-line runtime error: `1px + 1em` has incompatible units. The leading newline pushes the
  // error onto 0-based line 2 so the `line + 1` mapping is exercised with a value > 1.
  private val runtimeSrc = "\n.x {\n  width: 1px + 1em;\n}\n"

  // A parse-phase (format) error: unbalanced parentheses. 0-based span at line 0, column 9.
  private val formatSrc = ".x { y: (}"

  test("ISS-1375: compileStringResult maps a SassRuntimeException to an Error failure with the +1 position mapping") {
    // Source of truth: the legacy entry point throws with the raw 0-based span.
    val legacy = intercept[ssg.sass.SassRuntimeException](Compile.compileString(runtimeSrc))
    assertEquals(legacy.span.start.line, 2, "raw span start.line is 0-based")
    assertEquals(legacy.span.start.column, 9, "raw span start.column is 0-based")
    assertEquals(legacy.span.start.offset, 15)
    assertEquals(legacy.span.end.line, 2)
    assertEquals(legacy.span.end.column, 18)
    assertEquals(legacy.span.end.offset, 24)

    val result = Compile.compileStringResult(runtimeSrc)
    assert(result.isFailure, s"a compile error must produce a failure (value absent), got $result")
    assertEquals(result.value, None)
    assertEquals(result.diagnostics.size, 1)
    val d = result.diagnostics.head
    assertEquals(d.severity, Severity.Error)
    assertEquals(d.component, "ssg-sass")
    assertEquals(d.code, Some("runtime-error"))
    assert(d.message.contains("incompatible units"), s"message: ${d.message}")
    // The native exception rides along as the cause (§1.2 rule 5).
    assert(d.cause.exists(_.isInstanceOf[ssg.sass.SassRuntimeException]), s"cause: ${d.cause}")

    // The §1.3 sass row: line/column are span fields + 1; offsets ride along; source from sourceUrl.
    val pos = d.position.getOrElse(fail("expected a position"))
    assertEquals(pos.source, None)
    assertEquals(pos.line, Some(3)) // 0-based 2 + 1
    assertEquals(pos.column, Some(10)) // 0-based 9 + 1
    assertEquals(pos.endLine, Some(3)) // 0-based 2 + 1
    assertEquals(pos.endColumn, Some(19)) // 0-based 18 + 1
    assertEquals(pos.offset, Some(15)) // raw offset, unchanged
    assertEquals(pos.endOffset, Some(24)) // raw offset, unchanged
  }

  test("ISS-1375: compileStringResult maps a SassFormatException to an Error failure coded format-error") {
    val legacy = intercept[ssg.sass.SassFormatException](Compile.compileString(formatSrc))
    assertEquals(legacy.span.start.line, 0)
    assertEquals(legacy.span.start.column, 9)
    assertEquals(legacy.span.start.offset, 9)

    val result = Compile.compileStringResult(formatSrc)
    assert(result.isFailure)
    assertEquals(result.value, None)
    val d = result.diagnostics.head
    assertEquals(d.severity, Severity.Error)
    assertEquals(d.component, "ssg-sass")
    assertEquals(d.code, Some("format-error"))
    assert(d.cause.exists(_.isInstanceOf[ssg.sass.SassFormatException]), s"cause: ${d.cause}")
    val pos = d.position.getOrElse(fail("expected a position"))
    assertEquals(pos.source, None)
    assertEquals(pos.line, Some(1)) // 0-based 0 + 1
    assertEquals(pos.column, Some(10)) // 0-based 9 + 1
    assertEquals(pos.offset, Some(9)) // raw offset, unchanged
  }

  test("ISS-1375: compileStringResult is a clean success carrying the same CSS bytes as compileString") {
    val src    = ".x { color: red; }"
    val legacy = Compile.compileString(src)
    val result = Compile.compileStringResult(src)

    assert(result.isSuccess, s"a clean compile must be a success, got $result")
    assert(!result.isDegraded)
    assert(!result.hasErrors)
    assertEquals(result.diagnostics, Vector.empty)
    // Legacy parity: the facade produces the identical CompileResult on the happy path.
    assertEquals(result.value.map(_.css), Some(legacy.css))
  }

  test("ISS-1375: compileStringResult surfaces @warn output as Warning diagnostics but stays a success") {
    // @warn adds "WARNING: hello" to CompileResult.warnings (EvaluateVisitor.scala:201); the compile
    // still produces correct CSS, so per §1.1 this is Warning + success, never degraded/failure.
    val src    = "@warn \"hello\";\n.x { color: red; }"
    val legacy = Compile.compileString(src)
    assertEquals(legacy.warnings, List("WARNING: hello"))

    val result = Compile.compileStringResult(src)
    assert(result.isSuccess, s"warnings must not degrade a successful compile, got $result")
    assert(!result.isDegraded)
    assert(!result.isFailure)
    assert(!result.hasErrors)
    assertEquals(result.value.map(_.css), Some(legacy.css))
    assertEquals(result.diagnostics.size, 1)
    assertEquals(result.warnings.size, 1)
    val d = result.warnings.head
    assertEquals(d.severity, Severity.Warning)
    assertEquals(d.component, "ssg-sass")
    assertEquals(d.message, "WARNING: hello")
  }

  test("ISS-1375: compileStringResult threads the style parameter through to compileString") {
    val src    = ".x { color: red; }"
    val result = Compile.compileStringResult(src, OutputStyle.Compressed)
    assertEquals(result.value.map(_.css), Some(Compile.compileString(src, OutputStyle.Compressed).css))
    assert(result.value.exists(_.css == ".x{color:red}"), s"expected compressed CSS, got ${result.value.map(_.css)}")
  }
}
