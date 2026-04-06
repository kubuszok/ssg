/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions/meta.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: meta.dart -> MetaFunctions.scala
 *   Convention: Phase 9 — implementations of basic meta built-ins.
 */
package ssg
package sass
package functions

import ssg.sass.{ BuiltInCallable, Callable, CurrentCallableInvoker, CurrentEnvironment, CurrentMixinInvoker, Environment, Nullable, SassScriptException, UserDefinedCallable }
import ssg.sass.ast.sass.MixinRule
import ssg.sass.value.{ SassArgumentList, SassBoolean, SassCalculation, SassColor, SassFunction, SassList, SassMap, SassMixin, SassNull, SassNumber, SassString, Value }
import ssg.sass.value.ListSeparator

import scala.collection.immutable.ListMap

/** Built-in meta functions. */
object MetaFunctions {

  private def typeName(value: Value): String = value match {
    case _: SassArgumentList => "arglist"
    case _: SassNumber       => "number"
    case _: SassString       => "string"
    case _: SassColor        => "color"
    case _: SassMap          => "map"
    case _: SassList         => "list"
    case _: SassBoolean      => "bool"
    case SassNull => "null"
    case _: SassFunction => "function"
    case _: SassMixin    => "mixin"
    case _ => "unknown"
  }

  /** Extracts a string-typed argument's text. */
  private def argName(v: Value): String = v match {
    case s: SassString => s.text
    case other => other.toString
  }

  /** Resolves the env to introspect for an optional `$module` argument: `null` -> the active environment, otherwise the namespaced module registered under that name (or empty if none).
    */
  private def envFor(moduleArg: Value): Nullable[Environment] =
    CurrentEnvironment.get.flatMap { env =>
      moduleArg match {
        case SassNull => Nullable(env)
        case other    => env.getNamespace(argName(other))
      }
    }

  private val ifFn: BuiltInCallable =
    BuiltInCallable.function(
      "if",
      "$condition, $if-true, $if-false",
      args => if (args.head.isTruthy) args(1) else args(2)
    )

  private val typeOfFn: BuiltInCallable =
    BuiltInCallable.function("type-of", "$value", args => SassString(typeName(args.head), hasQuotes = false))

  private val inspectFn: BuiltInCallable =
    BuiltInCallable.function("inspect", "$value", args => SassString(args.head.toString, hasQuotes = false))

  private val featureExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "feature-exists",
      "$feature",
      _ => {
        EvaluationContext.warnForDeprecation(
          Deprecation.FeatureExists,
          "The feature-exists() function is deprecated. Recommendation: remove all usage — no new Sass features require this check."
        )
        // Deprecated; we don't claim to support any features.
        SassBoolean.sassFalse
      }
    )

  private val variableExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "variable-exists",
      "$name",
      args =>
        SassBoolean(
          CurrentEnvironment.get.fold(false)(_.variableExists(argName(args.head)))
        )
    )

  private val functionExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "function-exists",
      "$name, $module: null",
      { args =>
        val name      = argName(args.head)
        val moduleArg = if (args.length > 1) args(1) else SassNull
        val found     = envFor(moduleArg).fold(false)(_.functionExists(name)) ||
          (moduleArg == SassNull && Functions.lookupGlobal(name).isDefined)
        SassBoolean(found)
      }
    )

  private val keywordsFn: BuiltInCallable =
    BuiltInCallable.function(
      "keywords",
      "$args",
      args =>
        // When the arglist carries a keyword-rest map (set by
        // `_bindParameters` for `$kwargs...`), surface it as a SassMap with
        // quoted-string keys. Otherwise return an empty map.
        args.head match {
          case al: SassArgumentList =>
            val entries = al.keywords.iterator.map { case (k, v) =>
              (SassString(k, hasQuotes = true): Value) -> v
            }.toList
            SassMap(ListMap.from(entries))
          case _ => SassMap.empty
        }
    )

  private val mixinExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "mixin-exists",
      "$name, $module: null",
      { args =>
        val name      = argName(args.head)
        val moduleArg = if (args.length > 1) args(1) else SassNull
        SassBoolean(envFor(moduleArg).fold(false)(_.mixinExists(name)))
      }
    )

  private val globalVariableExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "global-variable-exists",
      "$name, $module: null",
      { args =>
        val name      = argName(args.head)
        val moduleArg = if (args.length > 1) args(1) else SassNull
        SassBoolean(envFor(moduleArg).fold(false)(_.variableExists(name)))
      }
    )

  private val contentExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "content-exists",
      "",
      _ => SassBoolean(CurrentEnvironment.get.fold(false)(_.content.isDefined))
    )

  private val calcNameFn: BuiltInCallable =
    BuiltInCallable.function(
      "calc-name",
      "$calc",
      args =>
        args.head match {
          case c: SassCalculation => SassString(c.name, hasQuotes = false)
          case other =>
            throw SassScriptException(s"$$calc: $other is not a calculation.")
        }
    )

  private val calcArgsFn: BuiltInCallable =
    BuiltInCallable.function(
      "calc-args",
      "$calc",
      args =>
        args.head match {
          case c: SassCalculation =>
            val items: List[Value] = c.arguments.map {
              case n:  SassNumber      => n:  Value
              case sc: SassCalculation => sc: Value
              case other =>
                // CalculationOperation / SassString / other -> render as unquoted string.
                SassString(SassCalculation.argumentToCss(other), hasQuotes = false): Value
            }
            SassList(items, ListSeparator.Comma)
          case other =>
            throw SassScriptException(s"$$calc: $other is not a calculation.")
        }
    )

  private val acceptsContentFn: BuiltInCallable =
    BuiltInCallable.function(
      "accepts-content",
      "$mixin",
      args =>
        args.head match {
          case m: SassMixin =>
            val accepts = m.callable match {
              case bic: BuiltInCallable        => bic.acceptsContent
              case ud:  UserDefinedCallable[?] =>
                ud.declaration match {
                  case mr: MixinRule => mr.hasContent
                  case _ => false
                }
              case _ => false
            }
            SassBoolean(accepts)
          case other =>
            throw SassScriptException(s"$$mixin: $other is not a mixin.")
        }
    )

  private val moduleVariablesFn: BuiltInCallable =
    BuiltInCallable.function(
      "module-variables",
      "$module",
      { args =>
        val name = argName(args.head)
        // First try a built-in `sass:` module — none of those expose
        // variables today, so this is effectively the user-defined path.
        CurrentEnvironment.get.flatMap(_.getNamespace(name)).fold(SassMap.empty) { env =>
          val entries = env.variableEntries.map { case (k, v) =>
            (SassString(k, hasQuotes = true): Value) -> v
          }.toList
          SassMap(ListMap.from(entries))
        }
      }
    )

  private val moduleFunctionsFn: BuiltInCallable =
    BuiltInCallable.function(
      "module-functions",
      "$module",
      { args =>
        val name = argName(args.head)
        // Surface the namespace's function members as a name->name map.
        // First try the active env (covers `@use "vars" as v` and
        // `@use "sass:color"` since both register namespaces). Fall back
        // to the static built-in module table for `sass:` modules even
        // when no `@use` is in scope.
        val nsEntries = CurrentEnvironment.get.flatMap(_.getNamespace(name)).fold(List.empty[(Value, Value)]) { env =>
          env.functionValues.map { fn =>
            (SassString(fn.name, hasQuotes = true): Value) ->
              (new SassFunction(fn): Value)
          }.toList
        }
        val entries =
          if (nsEntries.nonEmpty) nsEntries
          else
            Functions.modules.get(name).fold(List.empty[(Value, Value)]) { fns =>
              fns.collect { case b: BuiltInCallable =>
                (SassString(b.name, hasQuotes = true): Value) ->
                  (new SassFunction(b): Value)
              }
            }
        SassMap(ListMap.from(entries))
      }
    )

  /** Helper: looks up a function callable by name, optionally in a namespaced module. Falls back to the global built-in registry when no module is specified. Returns `Nullable.empty` if not found.
    */
  private def lookupFunction(name: String, moduleArg: Value): Nullable[Callable] =
    moduleArg match {
      case SassNull =>
        CurrentEnvironment.get.flatMap(_.getFunction(name)) match {
          case n if n.isDefined => n
          case _                =>
            Functions.lookupGlobal(name) match {
              case Some(b) => Nullable(b: Callable)
              case None    => Nullable.empty
            }
        }
      case other =>
        val ns = argName(other)
        CurrentEnvironment.get.flatMap(_.getNamespacedFunction(ns, name)) match {
          case n if n.isDefined => n
          case _                =>
            // Fall back to the static built-in module table for `sass:` modules
            // even when no `@use` is in scope.
            Functions.modules.get(ns).flatMap { fns =>
              fns.collectFirst { case b: BuiltInCallable if b.name == name => b: Callable }
            } match {
              case Some(c) => Nullable(c)
              case None    => Nullable.empty
            }
        }
    }

  /** Helper: looks up a mixin callable by name, optionally in a namespaced module. */
  private def lookupMixin(name: String, moduleArg: Value): Nullable[Callable] =
    moduleArg match {
      case SassNull => CurrentEnvironment.get.flatMap(_.getMixin(name))
      case other    =>
        val ns = argName(other)
        CurrentEnvironment.get.flatMap(env => env.getNamespace(ns)).flatMap(_.getMixin(name))
    }

  private val getFunctionFn: BuiltInCallable =
    BuiltInCallable.function(
      "get-function",
      "$name, $css: false, $module: null",
      { args =>
        val name      = argName(args.head)
        val moduleArg = if (args.length > 2) args(2) else SassNull
        lookupFunction(name, moduleArg).fold[Value] {
          throw SassScriptException(s"Function not found: $name")
        }(c => new SassFunction(c))
      }
    )

  private val getMixinFn: BuiltInCallable =
    BuiltInCallable.function(
      "get-mixin",
      "$name, $module: null",
      { args =>
        val name      = argName(args.head)
        val moduleArg = if (args.length > 1) args(1) else SassNull
        lookupMixin(name, moduleArg).fold[Value] {
          throw SassScriptException(s"Mixin not found: $name")
        }(c => new SassMixin(c))
      }
    )

  private val callFn: BuiltInCallable =
    BuiltInCallable.function(
      "call",
      "$function, $args...",
      { args =>
        if (args.isEmpty)
          throw SassScriptException("call() requires a function argument.")
        // First argument is the function: a SassFunction or — for legacy
        // Sass — a plain string function name resolved against the active env.
        val callable: Callable = args.head match {
          case f: SassFunction => f.callable
          case s: SassString   =>
            lookupFunction(s.text, SassNull).getOrElse {
              throw SassScriptException(s"Function not found: ${s.text}")
            }
          case other =>
            throw SassScriptException(s"call() expected a function, got: $other")
        }
        // Remaining args: if a single SassArgumentList was passed (the
        // common `$args...` rest case), splat its positional + keyword
        // entries; otherwise treat as plain positional list.
        val rest                = args.tail
        val (positional, named) = rest match {
          case (al: SassArgumentList) :: Nil =>
            (al.asList, ListMap.from(al.keywords))
          case (sl: SassList) :: Nil =>
            (sl.asList, ListMap.empty[String, Value])
          case other => (other, ListMap.empty[String, Value])
        }
        CurrentCallableInvoker.get.fold[Value] {
          // No active visitor — only built-in callables can be invoked
          // through their callback directly.
          callable match {
            case bic: BuiltInCallable => bic.callback(positional)
            case _ => throw SassScriptException("meta.call requires an active evaluation context.")
          }
        }(invoker => invoker(callable, positional, named))
      }
    )

  private val applyFn: BuiltInCallable =
    BuiltInCallable.function(
      "apply",
      "$mixin, $args...",
      { args =>
        if (args.isEmpty)
          throw SassScriptException("apply() requires a mixin argument.")
        // First argument is the mixin: a SassMixin or — for legacy Sass — a
        // plain string mixin name resolved against the active env.
        val callable: Callable = args.head match {
          case m: SassMixin  => m.callable
          case s: SassString =>
            lookupMixin(s.text, SassNull).getOrElse {
              throw SassScriptException(s"Mixin not found: ${s.text}")
            }
          case other =>
            throw SassScriptException(s"apply() expected a mixin, got: $other")
        }
        // Remaining args: splat a single trailing SassArgumentList, same as
        // meta.call does for functions.
        val rest                = args.tail
        val (positional, named) = rest match {
          case (al: SassArgumentList) :: Nil =>
            (al.asList, ListMap.from(al.keywords))
          case (sl: SassList) :: Nil =>
            (sl.asList, ListMap.empty[String, Value])
          case other => (other, ListMap.empty[String, Value])
        }
        CurrentMixinInvoker.get.fold[Value] {
          throw SassScriptException("meta.apply requires an active evaluation context.")
        } { invoker =>
          invoker(callable, positional, named)
          // Mixins emit statements; meta.apply itself returns null.
          SassNull
        }
      }
    )

  /** Built-in mixins exposed by `sass:meta`. These are registered in the mixin slot of the namespace env rather than the function slot so that `@include meta.apply(...)` resolves.
    */
  val moduleMixins: List[Callable] = List(applyFn)

  val global: List[Callable] = List(
    ifFn,
    typeOfFn,
    inspectFn,
    featureExistsFn,
    variableExistsFn,
    functionExistsFn,
    keywordsFn,
    mixinExistsFn,
    globalVariableExistsFn,
    contentExistsFn,
    moduleVariablesFn,
    moduleFunctionsFn,
    getFunctionFn,
    getMixinFn,
    callFn
  )

  /** Functions exposed only under `sass:meta` (not as globals). */
  val moduleOnly: List[Callable] = List(
    calcNameFn,
    calcArgsFn,
    acceptsContentFn
  )

  def module: List[Callable] = global ::: moduleOnly
}
