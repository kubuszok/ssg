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
 *   Convention: faithful port of dart-sass sass:meta module. Unlike
 *               dart-sass, which splits meta functions between
 *               meta.dart (static) and the evaluator (runtime), ssg-sass
 *               concentrates the runtime-context functions here and
 *               threads the active environment through
 *               CurrentEnvironment / CurrentCallableInvoker /
 *               CurrentMixinInvoker thread-locals.
 *
 * Covenant: full-port
 * Covenant-baseline-spec-pass: 283
 * Covenant-baseline-loc: 440
 * Covenant-baseline-methods: ifFn,typeOfFn,inspectFn,featureExistsFn,variableExistsFn,functionExistsFn,keywordsFn,mixinExistsFn,globalVariableExistsFn,contentExistsFn,calcNameFn,calcArgsFn,acceptsContentFn,moduleVariablesFn,moduleFunctionsFn,moduleMixinsFn,getFunctionFn,getMixinFn,callFn,applyFn,typeName,argName,lookupFunction,lookupMixin,knownFeatures,moduleMixins,global,moduleOnly,module,MetaFunctions
 * Covenant-dart-reference: lib/src/functions/meta.dart
 * Covenant-verified: 2026-04-08
 *
 * T005 — Phase 4 task. Faithful port of meta.dart + the runtime-context
 * meta functions that dart-sass keeps in its evaluator. Covers
 * if, type-of, inspect, feature-exists (with the frozen _features set),
 * variable-exists, function-exists, mixin-exists, global-variable-exists,
 * content-exists, keywords, calc-name, calc-args, accepts-content,
 * module-variables, module-functions, module-mixins, get-function,
 * get-mixin, call, apply.
 *
 * Spec progress — core_functions/meta sass-spec subdir 249→283/489
 * (50.9%→57.9%, +34 cases). Global +57 cases (4512→4569).
 * Remaining 206 failures are dominated by:
 *   - Parser bugs around `meta.get-function(lighten)` and
 *     `load-css` mixin references
 *   - SassColor literal-vs-generated tracking (dart-sass preserves the
 *     original literal text for inspect mode; ssg-sass normalises)
 *   - Environment.functionValues returning built-ins inherited from
 *     the surrounding scope for @use'd empty modules
 *   - Binder not producing SassArgumentList for splat args without
 *     named keywords (affects type-of(arglist))
 *   - B004 argument-arity validation (error/too_many_args etc.)
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package functions

import scala.language.implicitConversions

import ssg.sass.{ BuiltInCallable, Callable, CurrentCallableInvoker, CurrentEnvironment, CurrentMixinInvoker, Nullable, PlainCssCallable, SassScriptException, UserDefinedCallable }
import ssg.sass.ast.sass.{ ArgumentList => AstArgumentList, MixinRule, ValueExpression }
import ssg.sass.value.{ SassArgumentList, SassBoolean, SassCalculation, SassColor, SassFunction, SassList, SassMap, SassMixin, SassNull, SassNumber, SassString, Value }
import ssg.sass.value.ListSeparator
import ssg.sass.visitor.SerializeVisitor

import scala.collection.immutable.ListMap

/** Built-in meta functions. */
object MetaFunctions {

  /** Feature names that dart-sass claims to support from `feature-exists()`. This is frozen — the function is deprecated and dart-sass no longer adds to the set. Matches `_features` in
    * lib/src/functions/meta.dart.
    */
  private val knownFeatures: Set[String] = Set(
    "global-variable-shadowing",
    "extend-selector-pseudoclass",
    "units-level-3",
    "at-error",
    "custom-property"
  )

  /** Dart-sass `type-of` dispatch. SassArgumentList MUST come before SassList because SassArgumentList extends SassList; otherwise an arglist would misreport as "list".
    */
  private def typeName(value: Value): String = value match {
    case _: SassArgumentList => "arglist"
    case _: SassNumber       => "number"
    case _: SassString       => "string"
    case _: SassColor        => "color"
    case _: SassMap          => "map"
    case _: SassCalculation  => "calculation"
    case _: SassList         => "list"
    case _: SassBoolean      => "bool"
    case SassNull => "null"
    case _: SassFunction => "function"
    case _: SassMixin    => "mixin"
    case _ => "unknown"
  }

  /** Extracts a string-typed argument's text, validating that it is a string. dart-sass: `sassIndexToListIndex`, `_environment.getVariable`, etc. all require string arguments for `$name` parameters.
    */
  private def argName(v: Value): String = v match {
    case s: SassString => s.text
    case other => other.toString
  }

  /** Validates that [v] is a SassString for a parameter named [paramName], and returns its text. Throws SassScriptException if not.
    */
  private def assertString(v: Value, paramName: String): String = v match {
    case s: SassString => s.text
    case other => throw SassScriptException(s"$$$paramName: $other is not a string.")
  }

  /** Validates that [v] is a SassString or SassNull for the `$module` parameter. Returns the string text or null.
    */
  private def assertOptionalString(v: Value, paramName: String): Nullable[String] = v match {
    case SassNull => Nullable.empty
    case s: SassString => Nullable(s.text)
    case other => throw SassScriptException(s"$$$paramName: $other is not a string.")
  }

  /** Validates and resolves an optional `$module` argument to a namespace string. Returns `Nullable.empty` when moduleArg is SassNull (no module specified). Throws SassScriptException if the module
    * arg is not a string, or if the namespace doesn't exist in the current environment.
    *
    * dart-sass: `_environment.getModule(module)` / `_getModule(module)`
    */
  private def resolveModuleNamespace(moduleArg: Value): Nullable[String] =
    moduleArg match {
      case SassNull => Nullable.empty
      case other    =>
        val moduleName = assertOptionalString(other, "module").getOrElse(other.toString)
        CurrentEnvironment.get.foreach { env =>
          val moduleExists = env.findNamespacedModule(moduleName).isDefined
          if (!moduleExists) {
            throw SassScriptException(s"There is no module with the namespace \"$moduleName\".")
          }
        }
        Nullable(moduleName)
    }

  private val ifFn: BuiltInCallable =
    BuiltInCallable.function(
      "if",
      "$condition, $if-true, $if-false",
      args => {
        // Guard against malformed calls (e.g. the Sass-4 `if(cond: ...; else: ...)`
        // edge-case syntax that our parser currently produces as a 1-arg call).
        // Without this guard, `args(1)` / `args(2)` leaks as IndexOutOfBoundsException
        // into the sass-spec runner as a raw Java error.
        if (args.length < 3)
          throw SassScriptException("Missing element $if-true or $if-false in if().")
        if (args.head.isTruthy) args(1) else args(2)
      }
    )

  private val typeOfFn: BuiltInCallable =
    BuiltInCallable.function("type-of", "$value", args => SassString(typeName(args.head), hasQuotes = false))

  private val inspectFn: BuiltInCallable =
    BuiltInCallable.function(
      "inspect",
      "$value",
      args => SassString(SerializeVisitor.serializeValue(args(0), inspect = true), hasQuotes = false)
    )

  private val featureExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "feature-exists",
      "$feature",
      { args =>
        EvaluationContext.warnForDeprecation(
          Deprecation.FeatureExists,
          "The feature-exists() function is deprecated.\n\n" +
            "More info: https://sass-lang.com/d/feature-exists"
        )
        val feature = args(0).assertString("feature")
        SassBoolean(knownFeatures.contains(feature.text))
      }
    )

  /** Sass treats `_` and `-` as interchangeable in identifiers. Normalize to `-` for consistent lookup.
    */
  private def normalizeName(name: String): String = name.replace('_', '-')

  private val variableExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "variable-exists",
      "$name",
      args =>
        SassBoolean(
          CurrentEnvironment.get.fold(false)(e =>
            e.variableExists(assertString(args.head, "name")) ||
              e.variableExists(normalizeName(assertString(args.head, "name")))
          )
        )
    )

  private val functionExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "function-exists",
      "$name, $module: null",
      { args =>
        val rawName   = assertString(args.head, "name")
        val name      = rawName
        val moduleArg = if (args.length > 1) args(1) else SassNull
        val ns        = resolveModuleNamespace(moduleArg)
        val found     = CurrentEnvironment.get.fold(false)(e => e.functionExists(name, ns) || e.functionExists(normalizeName(name), ns)) || (moduleArg == SassNull && (Functions
          .lookupGlobal(name)
          .isDefined || Functions.lookupGlobal(normalizeName(name)).isDefined))
        SassBoolean(found)
      }
    )

  private val keywordsFn: BuiltInCallable =
    BuiltInCallable.function(
      "keywords",
      "$args",
      args =>
        args(0) match {
          case al: SassArgumentList =>
            val entries = al.keywords.iterator.map { case (k, v) =>
              (SassString(k, hasQuotes = false): Value) -> v
            }.toList
            SassMap(ListMap.from(entries))
          case other =>
            throw SassScriptException(s"$$args: $other is not an argument list.")
        }
    )

  private val mixinExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "mixin-exists",
      "$name, $module: null",
      { args =>
        val name      = assertString(args.head, "name")
        val moduleArg = if (args.length > 1) args(1) else SassNull
        val ns        = resolveModuleNamespace(moduleArg)
        SassBoolean(CurrentEnvironment.get.fold(false)(e => e.mixinExists(name, ns) || e.mixinExists(normalizeName(name), ns)))
      }
    )

  private val globalVariableExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "global-variable-exists",
      "$name, $module: null",
      { args =>
        val name      = assertString(args.head, "name")
        val moduleArg = if (args.length > 1) args(1) else SassNull
        // dart-sass: `_environment.globalVariableExists(variable.text.replaceAll("_", "-"), namespace: module?.text)`
        // Uses globalVariableExists which checks only the global scope (index 0),
        // not all scopes. A local variable declared inside a rule block is NOT global.
        val moduleNs: Nullable[String] = moduleArg match {
          case SassNull => Nullable.empty
          case other    => Nullable(assertString(other, "module"))
        }
        val normalized = normalizeName(name)
        val found      = CurrentEnvironment.get.fold(false) { env =>
          env.globalVariableExists(normalized, moduleNs)
        }
        SassBoolean(found)
      }
    )

  private val contentExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "content-exists",
      "",
      _ =>
        // dart-sass: evaluate.dart:439-446
        // content-exists() may only be called within a mixin.
        CurrentEnvironment.get match {
          case env if env.isDefined && !env.get.inMixin =>
            throw SassScriptException("content-exists() may only be called within a mixin.")
          case env =>
            SassBoolean(env.fold(false)(_.content.isDefined))
        }
    )

  private val calcNameFn: BuiltInCallable =
    BuiltInCallable.function(
      "calc-name",
      "$calc",
      args =>
        args.head match {
          // dart-sass returns the calculation name as a quoted string,
          // matching `SassString(calculation.name)` which defaults to
          // `quotes: true`. ssg-sass previously used `hasQuotes = false`
          // which emitted the name bare and broke the calc_name tests.
          case c: SassCalculation => SassString(c.name, hasQuotes = true)
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
        val name = assertString(args.head, "module")
        // Walk the module's PUBLIC variable surface (`Module.variables`)
        // which includes any members `@forward`ed from upstream modules
        // — not the inner Environment's local scope chain.
        val moduleOpt = CurrentEnvironment.get.flatMap(_.findNamespacedModule(name))
        if (moduleOpt.isEmpty) {
          throw SassScriptException(s"There is no module with namespace \"$name\".")
        }
        moduleOpt.fold(SassMap.empty) { m =>
          val entries = m.variables.iterator.map { case (k, v) =>
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
        val name = assertString(args.head, "module")
        // Surface the namespace's function members as a name->name map.
        // Use the module's PUBLIC functions surface, which includes
        // forwarded members. Falls back to the static built-in module
        // table for `sass:` modules when no `@use` is in scope.
        val moduleOpt = CurrentEnvironment.get.flatMap(_.findNamespacedModule(name))
        if (moduleOpt.isEmpty) {
          throw SassScriptException(s"There is no module with namespace \"$name\".")
        }
        val nsEntries = moduleOpt.fold(List.empty[(Value, Value)]) { m =>
          m.functions.iterator.map { case (fnName, fn) =>
            (SassString(fnName, hasQuotes = true): Value) ->
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

  // dart-sass: evaluate.dart:475-487
  private val moduleMixinsFn: BuiltInCallable =
    BuiltInCallable.function(
      "module-mixins",
      "$module",
      { args =>
        val namespace = args.head.assertString("module")
        val name      = namespace.text
        // Return a SassMap of all mixins in the namespaced module.
        // Similar pattern to module-functions above.
        CurrentEnvironment.get.flatMap(_.findNamespacedModule(name)) match {
          case mod if mod.isDefined =>
            val m       = mod.get
            val entries = m.mixins.iterator.map { case (mixinName, mixin) =>
              (SassString(mixinName, hasQuotes = true): Value) ->
                (new SassMixin(mixin): Value)
            }.toList
            SassMap(ListMap.from(entries))
          case _ =>
            throw SassScriptException(s"There is no module with namespace \"$name\".")
        }
      }
    )

  /** Helper: looks up a function callable by name, optionally in a namespaced module. Falls back to the global built-in registry when no module is specified. Returns `Nullable.empty` if not found.
    */
  private def lookupFunction(name: String, moduleArg: Value): Nullable[Callable] = {
    // Try both the original name and the _/- normalized form.
    val normalized = normalizeName(name)
    moduleArg match {
      case SassNull =>
        CurrentEnvironment.get.flatMap(e => e.getFunction(name).orElse(e.getFunction(normalized))) match {
          case n if n.isDefined => n
          case _                =>
            Functions.lookupGlobal(name).orElse(Functions.lookupGlobal(normalized)) match {
              case Some(b) => Nullable(b: Callable)
              case None    => Nullable.empty
            }
        }
      case other =>
        val ns = argName(other)
        CurrentEnvironment.get.flatMap(e => e.getNamespacedFunction(ns, name).orElse(e.getNamespacedFunction(ns, normalized))) match {
          case n if n.isDefined => n
          case _                =>
            // Fall back to the static built-in module table for `sass:` modules
            // even when no `@use` is in scope.
            Functions.modules.get(ns).flatMap { fns =>
              fns.collectFirst { case b: BuiltInCallable if b.name == name || b.name == normalized => b: Callable }
            } match {
              case Some(c) => Nullable(c)
              case None    => Nullable.empty
            }
        }
    }
  }

  /** Helper: looks up a mixin callable by name, optionally in a namespaced module. */
  private def lookupMixin(name: String, moduleArg: Value): Nullable[Callable] = {
    val normalized = normalizeName(name)
    moduleArg match {
      case SassNull => CurrentEnvironment.get.flatMap(e => e.getMixin(name).orElse(e.getMixin(normalized)))
      case other    =>
        val ns = argName(other)
        // Use getNamespacedMixin which goes through the Module's mixins map
        // directly (works for both BuiltInModule and EnvironmentModule).
        // getNamespace only works for EnvironmentModule and misses built-in
        // modules like sass:meta which register load-css and apply as mixins.
        CurrentEnvironment.get.flatMap(env => env.getNamespacedMixin(ns, name).orElse(env.getNamespacedMixin(ns, normalized)))
    }
  }

  // dart-sass: evaluate.dart:489-520
  private val getFunctionFn: BuiltInCallable =
    BuiltInCallable.function(
      "get-function",
      "$name, $css: false, $module: null",
      { args =>
        val name      = argName(args.head)
        val css       = if (args.length > 1) args(1).isTruthy else false
        val moduleArg = if (args.length > 2) args(2) else SassNull

        if (css) {
          // $css and $module may not both be passed at once.
          if (moduleArg != SassNull) {
            throw SassScriptException("$css and $module may not both be passed at once.")
          }
          // Return a plain CSS callable that emits a plain CSS function call.
          new SassFunction(new PlainCssCallable(name))
        } else {
          lookupFunction(name, moduleArg).fold[Value] {
            throw SassScriptException(s"Function not found: $name")
          }(c => new SassFunction(c))
        }
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

  /** Constructs a synthetic [[AstArgumentList]] AST node from a [[SassArgumentList]], matching dart-sass evaluate.dart:547-562. The entire SassArgumentList is wrapped as a `rest` ValueExpression, and
    * any keyword arguments are additionally wrapped as a `keywordRest` SassMap ValueExpression.
    *
    * This synthetic node goes through `_evaluateArguments` which properly unpacks the rest (SassArgumentList -> positional + keywords) and the keywordRest (SassMap -> named args), then through the
    * full binding pipeline (ParameterList.verify, default values, rest assembly).
    */
  private def buildSyntheticArgumentList(
    sassArgs:     SassArgumentList,
    callableSpan: ssg.sass.util.FileSpan
  ): AstArgumentList =
    new AstArgumentList(
      positional = Nil,
      named = Map.empty,
      namedSpans = Map.empty,
      span = callableSpan,
      rest = Nullable(ValueExpression(sassArgs, callableSpan)),
      keywordRest =
        if (sassArgs.keywords.isEmpty) Nullable.empty
        else
          Nullable(
            ValueExpression(
              SassMap(
                ListMap.from(
                  sassArgs.keywords.iterator.map { case (name, value) =>
                    (SassString(name, hasQuotes = false): Value) -> value
                  }
                )
              ),
              callableSpan
            )
          )
    )

  // dart-sass: evaluate.dart:540-599
  // The `call` function is defined in the evaluator (not in meta.dart) because
  // it needs access to `_runFunctionCallable` and the evaluator's environment.
  // SSG concentrates it in MetaFunctions and threads the active evaluator
  // through `CurrentCallableInvoker`.
  private val callFn: BuiltInCallable =
    BuiltInCallable.function(
      "call",
      "$function, $args...",
      { args =>
        val function = args(0)
        val sassArgs = args(1).asInstanceOf[SassArgumentList]

        // dart-sass: `var callableNode = _callableNode!;`
        // In SSG, the evaluator sets _callableNode before invoking any
        // built-in callback, so currentCallableSpan reflects the `call()`
        // invocation site.
        val callableSpan = EvaluationContext.current.fold(
          ssg.sass.util.FileSpan.synthetic("call()")
        )(_.currentCallableSpan)

        // dart-sass (evaluate.dart:547-562): construct a synthetic
        // ArgumentList AST node. The entire SassArgumentList is passed
        // as the `rest` expression, and any keyword arguments are
        // additionally wrapped as a keywordRest SassMap expression.
        // This goes through _evaluateArguments which properly unpacks
        // the rest (SassArgumentList -> positional + keywords) and the
        // keywordRest (SassMap -> named args), then through the full
        // binding pipeline (ParameterList.verify, default values, rest
        // assembly).
        val invocation = buildSyntheticArgumentList(sassArgs, callableSpan)

        if (function.isInstanceOf[SassString]) {
          // dart-sass (evaluate.dart:564-580): passing a string to call()
          // is deprecated. Construct a FunctionExpression and visit it.
          // In SSG, we resolve the function name and dispatch through
          // the invoker, which handles plain CSS fallback for unknown names.
          val s = function.asInstanceOf[SassString]
          EvaluationContext.warnForDeprecation(
            Deprecation.CallString,
            "Passing a string to call() is deprecated and will be illegal in " +
              "Dart Sass 2.0.0.\n\nRecommendation: call(get-function(" + s.text + "))"
          )
          val callable: Callable = lookupFunction(s.text, SassNull).getOrElse {
            new PlainCssCallable(s.text)
          }
          CurrentCallableInvoker.get.fold[Value] {
            throw SassScriptException("meta.call requires an active evaluation context.")
          }(invoker => invoker(callable, invocation))
        } else {
          // dart-sass (evaluate.dart:582-598): extract the Callable from
          // the SassFunction value and dispatch through _runFunctionCallable.
          val callable = function.assertFunction("function").callable
          CurrentCallableInvoker.get.fold[Value] {
            throw SassScriptException("meta.call requires an active evaluation context.")
          }(invoker => invoker(callable, invocation))
        }
      }
    )

  // dart-sass: evaluate.dart:646-683
  // The `apply` mixin is defined in the evaluator (not in meta.dart) because
  // it needs access to `_applyMixin` and the evaluator's environment.
  // SSG concentrates it in MetaFunctions and threads the active evaluator
  // through `CurrentMixinInvoker`.
  private val applyFn: BuiltInCallable =
    BuiltInCallable.mixin(
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
        // dart-sass (evaluate.dart:654-660): construct a synthetic
        // ArgumentList AST node, same pattern as call() but without
        // keywordRest (dart-sass apply uses rest only).
        val sassArgs     = args(1).asInstanceOf[SassArgumentList]
        val callableSpan = EvaluationContext.current.fold(
          ssg.sass.util.FileSpan.synthetic("apply()")
        )(_.currentCallableSpan)
        // dart-sass: apply does not include keywordRest — the keywords
        // from the SassArgumentList are unpacked by _evaluateArguments
        // through the rest arg's SassArgumentList.keywords path.
        val invocation = new AstArgumentList(
          positional = Nil,
          named = Map.empty,
          namedSpans = Map.empty,
          span = callableSpan,
          rest = Nullable(ValueExpression(sassArgs, callableSpan))
        )
        CurrentMixinInvoker.get.fold[Value] {
          throw SassScriptException("meta.apply requires an active evaluation context.")
        } { invoker =>
          invoker(callable, invocation)
          // Mixins emit statements; meta.apply itself returns null.
          SassNull
        }
      },
      acceptsContent = true
    )

  /** Built-in mixins exposed by `sass:meta`. These are registered in the mixin slot of the namespace env rather than the function slot so that `@include meta.apply(...)` resolves.
    */
  val moduleMixins: List[Callable] = List(applyFn)

  /** Globally available meta built-ins. Matches dart-sass `_shared` (meta.dart) wrapped with `.withDeprecationWarning('meta')`, plus `ifFn` which lives in the barrel `globalFunctions` list without a
    * deprecation wrapper.
    */
  val global: List[Callable] = List(
    ifFn,
    featureExistsFn.withDeprecationWarning("meta"),
    inspectFn.withDeprecationWarning("meta"),
    typeOfFn.withDeprecationWarning("meta"),
    keywordsFn.withDeprecationWarning("meta")
  )

  /** Runtime-context functions that dart-sass defines in the evaluator (evaluate.dart lines 386-600) rather than in meta.dart. These require access to the current environment (via
    * `CurrentEnvironment`) and are registered as globals by the evaluator with deprecation wrappers, plus added to the `sass:meta` module without wrappers.
    */
  val runtimeFunctions: List[BuiltInCallable] = List(
    variableExistsFn,
    globalVariableExistsFn,
    functionExistsFn,
    mixinExistsFn,
    contentExistsFn,
    moduleVariablesFn,
    moduleFunctionsFn,
    moduleMixinsFn,
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

  /** All functions for the `sass:meta` module. Includes shared functions (without deprecation wrappers since they're accessed via the module), runtime-context functions, and module-only functions.
    */
  def module: List[Callable] =
    List(featureExistsFn, inspectFn, typeOfFn, keywordsFn) :::
      runtimeFunctions :::
      moduleOnly
}
