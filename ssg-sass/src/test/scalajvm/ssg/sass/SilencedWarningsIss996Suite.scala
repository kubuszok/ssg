/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM-scoped RED test for the red phase; the implementer confirms
 * cross-platform once the source fix lands. */
package ssg
package sass

import scala.language.implicitConversions

/** Differential RED test for ISS-996 ([R0610-P1] bug: silenced deprecations still leak into `CompileResult.warnings`).
  *
  * `EvaluateVisitor.warn` (EvaluateVisitor.scala:189) and `_warnWithSpan` (EvaluateVisitor.scala:2401) append the deprecation message to the `_warnings` buffer UNCONDITIONALLY, then separately call
  * `_logger.warnForDeprecation(...)`. The `DeprecationProcessingLogger` (Logger.scala:265) correctly returns early — suppressing logging — for deprecations in its `silenceDeprecations` set, but
  * `_warnings` is appended regardless. So a silenced deprecation still surfaces on `EvaluateResult.warnings` and thence on `CompileResult.warnings` (Compile.scala:201-205).
  *
  * Oracle — dart-sass (vendored at original-src/dart-sass):
  *   - A silenced deprecation is not emitted at all. `Deprecation`s passed to `silenceDeprecations` are dropped before they ever reach a warnings sink (lib/src/logger/deprecation_processing.dart —
  *     `warn` returns without recording when the deprecation is silenced). dart-sass keeps a single emission path: if logging is suppressed, the warning does not exist, so it cannot appear in any
  *     collected `warnings`.
  *
  * Reference oracle: compiling a source that triggers exactly one deprecation, WITH that deprecation in `silenceDeprecations`, MUST yield `CompileResult.warnings` that does NOT contain it (ideally
  * empty) — mirroring dart-sass's single-path emission.
  *
  * The source `a { b: (1px / 2px); }` parenthesizes the slash so the evaluator treats it as division, firing `Deprecation.SlashDiv` (id `slash-div`) — verified against the current evaluator (see the
  * baseline assertion below, which passes today).
  */
final class SilencedWarningsIss996Suite extends munit.FunSuite {

  // Parentheses force division (not a slash-separated literal), so the current
  // evaluator emits exactly one slash-div deprecation. See DeprecationSuite,
  // which exercises the same trigger.
  private val source = "a { b: (1px / 2px); }"

  // The warning string the evaluator pushes onto _warnings for this deprecation
  // (EvaluateVisitor.scala:189 / 2401): "DEPRECATION WARNING [slash-div]: ...".
  private def mentionsSlashDiv(warnings: List[String]): Boolean =
    warnings.exists(_.contains(s"[${Deprecation.SlashDiv.id}]"))

  test("Iss996 baseline: slash-div surfaces in warnings when NOT silenced (passes today)") {
    val result = Compile.compileString(source)
    assert(
      result.warnings.nonEmpty,
      s"expected the source to trigger a deprecation, got empty warnings"
    )
    assert(
      mentionsSlashDiv(result.warnings),
      s"expected a [${Deprecation.SlashDiv.id}] deprecation in warnings, got:\n${result.warnings.mkString("\n")}"
    )
  }

  test("Iss996 RED: a silenced slash-div deprecation must NOT surface in CompileResult.warnings") {
    val result = Compile.compileString(
      source,
      silenceDeprecations = Nullable(List(Deprecation.SlashDiv))
    )
    // dart-sass oracle: a silenced deprecation is never emitted, so it cannot
    // appear in CompileResult.warnings. On the current code this FAILS because
    // EvaluateVisitor appends to _warnings (EvaluateVisitor.scala:189/2401)
    // unconditionally, while DeprecationProcessingLogger only suppresses the
    // logging side (Logger.scala:265).
    assert(
      !mentionsSlashDiv(result.warnings),
      s"silenced [${Deprecation.SlashDiv.id}] must be ABSENT from warnings; expected: silenced -> absent, actual: leaked -> present in:\n${result.warnings.mkString("\n")}"
    )
  }
}
