/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/evaluate.dart (~4939 lines)
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: evaluate.dart -> EvaluateVisitor.scala
 *   Convention: Phase 10 skeleton — public API surface only. The full
 *               evaluation engine is the largest single file in dart-sass and
 *               will be ported in a dedicated future phase.
 *   Idiom: Skeleton entry points throw UnsupportedOperationException.
 */
package ssg
package sass
package visitor

import ssg.sass.ast.sass.Stylesheet
import ssg.sass.ast.css.CssStylesheet
import ssg.sass.{Logger, ImportCache}

/** Result of evaluating a Sass stylesheet — a CSS AST plus the set of URLs
  * that were loaded during evaluation.
  */
final case class EvaluateResult(stylesheet: CssStylesheet, loadedUrls: Set[String])

/** A visitor that executes Sass code to produce a CSS AST.
  *
  * Skeleton — exposes the public entry points only. The visit* methods are
  * intentionally omitted; they will be added in the full Phase 10 port.
  */
final class EvaluateVisitor(
  val importCache: Nullable[ImportCache] = Nullable.Null,
  val logger: Nullable[Logger] = Nullable.Null
) {

  /** Evaluate a parsed [[Stylesheet]] to a CSS AST. */
  def run(stylesheet: Stylesheet): EvaluateResult =
    throw new UnsupportedOperationException("Phase 10 stub: EvaluateVisitor.run")

  /** Evaluate an expression in isolation (used for command-line --watch). */
  def runExpression(stylesheet: Stylesheet, expression: ssg.sass.ast.sass.Expression): ssg.sass.value.Value =
    throw new UnsupportedOperationException("Phase 10 stub: EvaluateVisitor.runExpression")
}
