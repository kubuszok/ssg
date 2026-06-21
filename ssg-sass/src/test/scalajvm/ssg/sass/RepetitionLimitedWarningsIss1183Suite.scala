/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM-scoped RED test for the red phase; the implementer confirms
 * cross-platform once the source fix lands. */
package ssg
package sass

import scala.language.implicitConversions

/** Differential RED test for ISS-1183 ([R0610] bug: repetition-limited deprecations still leak into `CompileResult.warnings`).
  *
  * `EvaluateVisitor.warn` (EvaluateVisitor.scala:194) and `_warnWithSpan` (EvaluateVisitor.scala:2425) append the deprecation message to `_warnings` (-> `CompileResult.warnings`) gated ONLY on
  * `_isDeprecationSilenced` (the silence set). But `DeprecationProcessingLogger.handleDeprecation` (Logger.scala:246-278) drops a deprecation for THREE reasons, not one: (a) future-not-opted-in:
  * `deprecation.isFuture && !futureDeprecations.contains(deprecation)` (Logger.scala:248-250), (b) silenced: `silenceDeprecations.contains(deprecation)` (Logger.scala:265-267), (c)
  * repetition-limited: with `limitRepetition` defaulting to TRUE (Logger.scala:158) and `maxRepetitions == 5` (Logger.scala:161), the 6th+ occurrence of the SAME deprecation is dropped
  * (Logger.scala:269-275).
  *
  * The visitor's gate only mirrors (b). Deprecations dropped for (a) or (c) still leak into `_warnings`.
  *
  * Oracle — dart-sass (vendored at original-src/dart-sass): a single emission path. `_warn` (evaluate.dart:4681-4696) routes EXCLUSIVELY through the logger; there is no parallel warnings list. When
  * `DeprecationProcessingLogger.warn` (lib/src/logger/deprecation_processing.dart) drops a deprecation — for any of its reasons, including the repetition limit — that warning never reaches any sink,
  * so it cannot appear in any collected `warnings`. The port must record on `_warnings` only when the logger would actually emit.
  *
  * This suite exercises reason (c), the repetition limit, which is active by default (`limitRepetition = true`, `maxRepetitions = 5`). Triggering the SAME deprecation (slash-div) more than 5 times at
  * DISTINCT source locations makes the logger emit only the first 5 and drop the 6th+, while the visitor leaks ALL of them into `CompileResult.warnings`.
  *
  * The construct `aN { bN: (1px / 2px); }` parenthesizes the slash so the evaluator treats it as division, firing `Deprecation.SlashDiv` (id `slash-div`). DISTINCT selectors and property names give
  * each warning a distinct `(message, span)` so `_warningsEmitted` (EvaluateVisitor.scala:2418) does NOT deduplicate them.
  */
final class RepetitionLimitedWarningsIss1183Suite extends munit.FunSuite {

  // Each rule parenthesizes the slash to force division (a slash-div
  // deprecation). Distinct selectors AND property names give every warning a
  // distinct (message, span) pair so _warningsEmitted does not collapse them.
  private def sourceWith(n: Int): String =
    (1 to n).map(i => s"a$i { b$i: (1px / 2px); }").mkString("\n")

  private def slashDivCount(warnings: List[String]): Int =
    warnings.count(_.contains(s"[${Deprecation.SlashDiv.id}]"))

  test(
    "Iss1183 GUARD: 3 slash-div triggers (<= maxRepetitions) all surface — none repetition-dropped (holds today and after the fix)"
  ) {
    val result = Compile.compileString(sourceWith(3))
    assertEquals(
      slashDivCount(result.warnings),
      3,
      s"expected all 3 slash-div warnings to surface (3 <= 5, no repetition drop), got:\n${result.warnings.mkString("\n")}"
    )
  }

  test("Iss1183 RED: with 7 distinct slash-div triggers, warnings must respect the repetition limit (<= maxRepetitions = 5)") {
    val result = Compile.compileString(sourceWith(7))
    // dart-sass oracle: the logger drops the 6th+ occurrence of the same
    // deprecation (Logger.scala:269-275, maxRepetitions = 5), and there is no
    // parallel sink, so at most 5 slash-div warnings can ever surface. On the
    // current code this FAILS because the visitor's gate (EvaluateVisitor.scala
    // :194 / :2425) checks only _isDeprecationSilenced and leaks all 7.
    assert(
      slashDivCount(result.warnings) <= 5,
      s"repetition-limited [${Deprecation.SlashDiv.id}] must be capped at maxRepetitions=5; expected: <= 5, actual: ${slashDivCount(result.warnings)} leaked in:\n${result.warnings.mkString("\n")}"
    )
  }
}
