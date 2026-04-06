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

/** Holds a reference to the [[Environment]] currently active inside an [[ssg.sass.visitor.EvaluateVisitor]] invocation. Built-in callables (e.g. `mixin-exists`, `variable-exists`, `module-functions`)
  * consult this so they can introspect lexical state without an explicit env parameter. The visitor sets it on entry and restores the previous value on exit.
  *
  * NOTE: this is a simple shared `var` rather than a real `ThreadLocal`/scala-native zone — Sass evaluation is single-threaded and ssg-js/ssg-native runtimes don't share the holder. If multi-threaded
  * evaluation is ever needed, swap this for `DynamicVariable`.
  */
object CurrentEnvironment {

  private var _env: Nullable[Environment] = Nullable.empty

  def get: Nullable[Environment] = _env

  def set(env: Nullable[Environment]): Nullable[Environment] = {
    val prev = _env
    _env = env
    prev
  }
}
