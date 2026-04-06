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

  /** Stack of active contexts. The top of the stack is the most recently entered.
    *
    * NOTE: this is a single shared `var` rather than a `ThreadLocal`/`DynamicVariable` because Sass evaluation is single-threaded and ssg-js / ssg-native runtimes don't have working ThreadLocals
    * across all platforms. If multi-threaded evaluation is ever needed, swap this for a `DynamicVariable[List[EvaluationContext]]`.
    */
  private var _stack: List[EvaluationContext] = Nil

  /** Returns the current [[EvaluationContext]], or empty if none is active. */
  def current: Nullable[EvaluationContext] = _stack match {
    case head :: _ => Nullable(head)
    case Nil       => Nullable.empty
  }

  /** Pushes [[ctx]] as the current context, runs `body`, and restores the previous context — even on exception.
    */
  def withContext[A](ctx: EvaluationContext)(body: => A): A = {
    _stack = ctx :: _stack
    try body
    finally _stack = _stack.tail
  }

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

/** Holds a callback for invoking a [[Callable]] from outside the [[ssg.sass.visitor.EvaluateVisitor]]. Set by the visitor on entry so built-in `meta.call` / `meta.apply` functions can dispatch
  * arbitrary callables (built-in or user-defined) without an explicit visitor reference. Same single-`var` rationale as [[CurrentEnvironment]].
  */
object CurrentCallableInvoker {

  /** A function that invokes a [[Callable]] with positional + named args. */
  type Invoker = (Callable, List[ssg.sass.value.Value], scala.collection.immutable.ListMap[String, ssg.sass.value.Value]) => ssg.sass.value.Value

  private var _invoker: Nullable[Invoker] = Nullable.empty

  def get: Nullable[Invoker] = _invoker

  def set(inv: Nullable[Invoker]): Nullable[Invoker] = {
    val prev = _invoker
    _invoker = inv
    prev
  }
}
