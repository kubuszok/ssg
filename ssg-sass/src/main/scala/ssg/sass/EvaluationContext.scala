/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/evaluation_context.dart
 * Original: Copyright (c) 2020 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: evaluation_context.dart -> EvaluationContext.scala
 *   Convention: Skeleton — public API surface only
 */
package ssg
package sass

import ssg.sass.ast.AstNode

/** An interface accessed by SassScript functions to get the context of the current evaluation.
  */
trait EvaluationContext {

  /** Returns the AST node being evaluated, for use in error messages. */
  def currentCallableNode: AstNode

  /** Emits a warning with the given [message]. */
  def warn(message: String, deprecation: Boolean = false): Unit
}

object EvaluationContext {

  /** Returns the current [[EvaluationContext]], or throws if none is active. TODO: implement zone-style context propagation.
    */
  def current: Nullable[EvaluationContext] = Nullable.empty

  /** Emits a warning via the current context, if any. */
  def warn(message: String, deprecation: Boolean = false): Unit =
    current.foreach(_.warn(message, deprecation))
}
