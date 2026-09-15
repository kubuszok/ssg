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
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/evaluation_context.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass

import scala.util.DynamicVariable

import ssg.sass.util.FileSpan

/// An interface that exposes information about the current Sass evaluation.
///
/// This allows us to expose zone-scoped information without having to create a
/// new zone variable for each piece of information.
trait EvaluationContext {

  /// Returns the span for the currently executing callable.
  ///
  /// For normal exception reporting, this should be avoided in favor of
  /// throwing [SassScriptException]s. It should only be used when calling APIs
  /// that require spans.
  ///
  /// Throws a [StateError] if there isn't a callable being invoked.
  def currentCallableSpan: FileSpan

  /// Prints a warning message associated with the current `@import` or function
  /// call.
  ///
  /// If [deprecation] is non-null, the warning is emitted as a deprecation
  /// warning of that type.
  def warn(message: String, deprecation: Nullable[Deprecation] = Nullable.Null): Unit

  /** Emits a deprecation warning tagged with the given [[Deprecation]]. Default forwards to `warn` with the deprecation value.
    */
  def warnForDeprecation(deprecation: Deprecation, message: String): Unit =
    warn(message, Nullable(deprecation))
}

object EvaluationContext {

  /** Stack of active contexts. The top of the stack is the most recently entered.
    *
    * Thread-isolated via `DynamicVariable` (backed by `InheritableThreadLocal` on the JVM; a plain holder on JS/Native where evaluation is single-threaded). This mirrors dart-sass's per-Zone
    * `#_evaluationContext` scoping and allows concurrent `compileString` calls on the JVM without cross-contamination (ISS-997).
    */
  private val _stack: DynamicVariable[List[EvaluationContext]] = new DynamicVariable(Nil)

  /** Returns the current [[EvaluationContext]], or empty if none is active. */
  def current: Nullable[EvaluationContext] = _stack.value match {
    case head :: _ => Nullable(head)
    case Nil       => Nullable.empty
  }

  /** Pushes [[ctx]] as the current context, runs `body`, and restores the previous context — even on exception.
    *
    * Uses `DynamicVariable.withValue` for scoped push/pop, which is semantically identical to the previous manual `_stack = ctx :: _stack` / `try-finally _stack = _stack.tail`, but thread-isolated
    * and exception-safe by construction. Mirrors dart-sass `runZoned(callback, zoneValues: {#_evaluationContext: context})` (evaluation_context.dart:100-101).
    */
  def withContext[A](ctx: EvaluationContext)(body: => A): A =
    _stack.withValue(ctx :: _stack.value)(body)

  /// Prints a warning message associated with the current `@import` or function
  /// call.
  ///
  /// If [deprecation] is `true`, the warning is emitted as a deprecation warning.
  ///
  /// This may only be called within a custom function or importer callback.
  def warn(message: String, deprecation: Boolean = false): Unit =
    current match {
      case ctx if ctx.isDefined =>
        ctx.get.warn(
          message,
          if (deprecation) Nullable(Deprecation.UserAuthored) else Nullable.Null
        )
      case _ if deprecation =>
        Logger.default.warnForDeprecation(Deprecation.UserAuthored, message)
      case _ =>
        Logger.default.warn(message)
    }

  /// Prints a deprecation warning with [message] of type [deprecation].
  def warnForDeprecation(deprecation: Deprecation, message: String): Unit =
    current match {
      case ctx if ctx.isDefined => ctx.get.warn(message, Nullable(deprecation))
      case _                    => Logger.default.warnForDeprecation(deprecation, message)
    }

  /// Prints a deprecation warning with [message] of type [deprecation],
  /// using stderr if there is no [EvaluationContext.current].
  def warnForDeprecationFromApi(message: String, deprecation: Deprecation): Unit =
    current match {
      case ctx if ctx.isDefined => ctx.get.warn(message, Nullable(deprecation))
      case _                    => Logger.default.warnForDeprecation(deprecation, message)
    }
}

/** Holds a reference to the [[Environment]] currently active inside an [[ssg.sass.visitor.EvaluateVisitor]] invocation. Built-in callables (e.g. `mixin-exists`, `variable-exists`, `module-functions`)
  * consult this so they can introspect lexical state without an explicit env parameter. The visitor sets it on entry and restores the previous value on exit.
  *
  * Thread-isolated via `DynamicVariable` (backed by `InheritableThreadLocal` on the JVM; a plain holder on JS/Native where evaluation is single-threaded). Allows concurrent `compileString` calls on
  * the JVM without cross-contamination (ISS-997).
  */
object CurrentEnvironment {

  private val _env: DynamicVariable[Nullable[Environment]] = new DynamicVariable(Nullable.empty)

  def get: Nullable[Environment] = _env.value

  def set(env: Nullable[Environment]): Nullable[Environment] = {
    val prev = _env.value
    _env.value = env
    prev
  }
}

/** Holds a callback for invoking a [[Callable]] from outside the [[ssg.sass.visitor.EvaluateVisitor]]. Set by the visitor on entry so built-in `meta.call` / `meta.apply` functions can dispatch
  * arbitrary callables (built-in or user-defined) without an explicit visitor reference. Thread-isolated via `DynamicVariable`, same rationale as [[CurrentEnvironment]].
  *
  * dart-sass: `call()` constructs a synthetic `ArgumentList` AST node containing `ValueExpression` wrappers around the already-evaluated values, then dispatches through `_runFunctionCallable` which
  * calls `_evaluateArguments` + the full binding pipeline (`ParameterList.verify`, default values, rest assembly, keyword rest handling). The invoker type reflects this: it accepts an AST
  * `ArgumentList` rather than pre-extracted positional/named lists, so the evaluator's full argument binding pipeline is exercised.
  */
object CurrentCallableInvoker {

  /** A function that invokes a [[Callable]] with a synthetic [[ssg.sass.ast.sass.ArgumentList]] AST node. The evaluator's implementation runs `_evaluateArguments` on the AST node, then dispatches
    * through the standard built-in / user-defined / plain-CSS callable pipeline.
    */
  type Invoker = (Callable, ssg.sass.ast.sass.ArgumentList) => ssg.sass.value.Value

  private val _invoker: DynamicVariable[Nullable[Invoker]] = new DynamicVariable(Nullable.empty)

  def get: Nullable[Invoker] = _invoker.value

  def set(inv: Nullable[Invoker]): Nullable[Invoker] = {
    val prev = _invoker.value
    _invoker.value = inv
    prev
  }
}

/** Holds a callback for invoking a mixin [[Callable]] from outside the [[ssg.sass.visitor.EvaluateVisitor]]. Set by the visitor on entry so built-in `meta.apply` can run a mixin body against the
  * current statement-visitor parent node. Thread-isolated via `DynamicVariable`, same rationale as [[CurrentEnvironment]].
  *
  * dart-sass: `apply()` constructs a synthetic `ArgumentList` AST node and dispatches through `_applyMixin`, analogous to how `call()` dispatches through `_runFunctionCallable`.
  */
object CurrentMixinInvoker {

  /** A function that invokes a mixin [[Callable]] with a synthetic [[ssg.sass.ast.sass.ArgumentList]] AST node, plus the `@content` block from the enclosing `@include` site (if any).
    */
  type Invoker = (Callable, ssg.sass.ast.sass.ArgumentList) => Unit

  private val _invoker: DynamicVariable[Nullable[Invoker]] = new DynamicVariable(Nullable.empty)

  def get: Nullable[Invoker] = _invoker.value

  def set(inv: Nullable[Invoker]): Nullable[Invoker] = {
    val prev = _invoker.value
    _invoker.value = inv
    prev
  }
}
