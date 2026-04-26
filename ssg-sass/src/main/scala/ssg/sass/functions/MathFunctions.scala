/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions/math.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: math.dart -> MathFunctions.scala
 *   Convention: faithful port of dart-sass sass:math module. Module
 *               functions use bare dart-sass names (compatible,
 *               is-unitless). The global namespace exposes the legacy
 *               aliases comparable/unitless via withName, matching
 *               dart-sass's `.withDeprecationWarning('math').withName(...)`
 *               pattern. Deprecation-warning wiring lives under the
 *               sass:meta module introspection work.
 *
 * Covenant: full-port
 * Covenant-baseline-spec-pass: 397
 * Covenant-baseline-loc: 490
 * Covenant-baseline-methods: moduleAbsFn,ceilFn,clampFn,cosFn,acosFn,asinFn,atanFn,atan2Fn,sinFn,tanFn,hypotFn,logFn,powFn,sqrtFn,maxFn,minFn,percentageFn,randomFn,roundFn,unitFn,isUnitlessFn,compatibleFn,divFn,floorFn,globalAbsFn,numberFn,singleArgMathFn,numberSqrt,numberSin,numberCos,numberTan,numberAtan,numberAsin,numberAcos,numberAbs,numberLog,numberPow,numberAtan2,radiansToDegrees,global,module,moduleVariables,rng,MathFunctions
 * Covenant-dart-reference: lib/src/functions/math.dart
 * Covenant-verified: 2026-04-08
 *
 * T004 — Phase 4 task. Faithful port of math.dart covering bounding
 * (ceil/floor/round/clamp/max/min/abs), distance (hypot), exponential
 * (log/pow/sqrt), trigonometric (sin/cos/tan/asin/acos/atan/atan2),
 * unit predicates (compatible/is-unitless/unit), percentage/random/div.
 *
 * Module variables (e, pi, epsilon, max-safe-integer, min-safe-integer,
 * max-number, min-number) are declared via `moduleVariables` and wired
 * through the EvaluateVisitor's use-rule handler so `@use "sass:math"`
 * exposes them under the math namespace.
 *
 * Status: core_functions/math sass-spec subdir 248→397/486
 * (51.0%→81.7%, +149 cases). Global +167 cases (4345→4512).
 * Remaining 89 failures are dominated by:
 *   - B009 Infinity/NaN -> calc() wrapping (~60 cases): dart-sass
 *     serializes non-finite SassNumbers as `calc(infinity)`, `calc(NaN)`
 *     or `calc(NaN * 1deg)`; ssg-sass emits the raw Double name.
 *   - B004 argument-arity validation (~20 cases): error/too_many_args,
 *     error/wrong_name tests across every math built-in.
 *   - Precision edge cases on log/tan near asymptotes (~5 cases).
 *   - HRX multi-file resolution for random.hrx helpers (~4 cases).
 */
package ssg
package sass
package functions

import scala.language.implicitConversions
import scala.util.Random

import ssg.sass.{ BuiltInCallable, Callable, Deprecation, EvaluationContext, Nullable, SassScriptException }
import ssg.sass.value.{ SassBoolean, SassNull, SassNumber, SassString, Value }

/** Built-in `sass:math` functions. Faithful port of `lib/src/functions/math.dart`. */
object MathFunctions {

  // ---------------------------------------------------------------------------
  // Inline number-library helpers (port of lib/src/util/number.dart top-level
  // math helpers). These wrap the low-level java.lang.Math operations in
  // dart-sass's unit-aware semantics:
  //
  //   - sqrt/pow: require unitless inputs, return unitless
  //   - log: requires unitless inputs, returns unitless; base is optional
  //   - sin/cos/tan: coerce input to radians, return unitless
  //   - asin/acos/atan/atan2: require unitless inputs, return degrees
  //   - abs: preserve units via coerceToMatch
  //
  // They match dart-sass's util/number.dart helpers one-for-one.
  // ---------------------------------------------------------------------------

  private def numberSqrt(number: SassNumber): SassNumber = {
    number.assertNoUnits("number")
    SassNumber(math.sqrt(number.value))
  }

  private def numberSin(number: SassNumber): SassNumber =
    SassNumber(math.sin(number.coerceValueToUnit("rad", "number")))

  private def numberCos(number: SassNumber): SassNumber =
    SassNumber(math.cos(number.coerceValueToUnit("rad", "number")))

  private def numberTan(number: SassNumber): SassNumber =
    SassNumber(math.tan(number.coerceValueToUnit("rad", "number")))

  private def numberAtan(number: SassNumber): SassNumber = {
    number.assertNoUnits("number")
    radiansToDegrees(math.atan(number.value))
  }

  private def numberAsin(number: SassNumber): SassNumber = {
    number.assertNoUnits("number")
    radiansToDegrees(math.asin(number.value))
  }

  private def numberAcos(number: SassNumber): SassNumber = {
    number.assertNoUnits("number")
    radiansToDegrees(math.acos(number.value))
  }

  private def numberAbs(number: SassNumber): SassNumber =
    SassNumber(math.abs(number.value)).coerceToMatch(number)

  private def numberLog(number: SassNumber, base: Nullable[SassNumber]): SassNumber =
    if (base.isDefined) {
      base.get.assertNoUnits()
      SassNumber(math.log(number.value) / math.log(base.get.value))
    } else {
      SassNumber(math.log(number.value))
    }

  private def numberPow(base: SassNumber, exponent: SassNumber): SassNumber = {
    base.assertNoUnits("base")
    exponent.assertNoUnits("exponent")
    // Handle the IEEE 754 edge cases dart-sass expects but Java's
    // Math.pow does not: `pow(1, ±infinity)` → 1 (Java returns NaN),
    // and `pow(±1, ±infinity)` likewise. Falls through to Math.pow
    // for every other input.
    val b      = base.value
    val e      = exponent.value
    val result =
      if (math.abs(b) == 1.0 && e.isInfinite) 1.0
      else math.pow(b, e)
    SassNumber(result)
  }

  private def numberAtan2(y: SassNumber, x: SassNumber): SassNumber =
    radiansToDegrees(math.atan2(y.value, x.convertValueToMatch(y, "x", "y")))

  /** Returns radians as a SassNumber with unit `deg`. */
  private def radiansToDegrees(radians: Double): SassNumber =
    SassNumber.withUnits(radians * (180.0 / math.Pi), numeratorUnits = List("deg"))

  // ---------------------------------------------------------------------------
  // Factory helpers (port of _function / _numberFunction / _singleArgumentMathFunc).
  // ---------------------------------------------------------------------------

  /** Creates a callable that transforms a number's value with [transform] and preserves its units — matches dart-sass's `_numberFunction`.
    */
  private def numberFn(name: String, transform: Double => Double): BuiltInCallable =
    BuiltInCallable.function(
      name,
      "$number",
      { args =>
        val n = args(0).assertNumber("number")
        SassNumber.withUnits(transform(n.value), n.numeratorUnits, n.denominatorUnits)
      }
    )

  /** Creates a single-argument math callable — matches dart-sass's `_singleArgumentMathFunc`. Delegates unit handling to the passed SassNumber -> SassNumber function.
    */
  private def singleArgMathFn(name: String, fn: SassNumber => SassNumber): BuiltInCallable =
    BuiltInCallable.function(
      name,
      "$number",
      { args =>
        val n = args(0).assertNumber("number")
        fn(n)
      }
    )

  // ---------------------------------------------------------------------------
  // Bounding functions
  // ---------------------------------------------------------------------------

  private val ceilFn:  BuiltInCallable = numberFn("ceil", math.ceil)
  private val floorFn: BuiltInCallable = numberFn("floor", math.floor)
  private val roundFn: BuiltInCallable = numberFn("round", v => math.round(v).toDouble)

  private val clampFn: BuiltInCallable =
    BuiltInCallable.function(
      "clamp",
      "$min, $number, $max",
      { args =>
        val minN    = args(0).assertNumber("min")
        val numberN = args(1).assertNumber("number")
        val maxN    = args(2).assertNumber("max")

        // Even though we don't use the resulting values, `convertValueToMatch`
        // generates more user-friendly exceptions than [greaterThanOrEquals]
        // since it has more context about parameter names.
        val _ = numberN.convertValueToMatch(minN, "number", "min")
        val _ = maxN.convertValueToMatch(minN, "max", "min")

        if (minN.greaterThanOrEquals(maxN).isTruthy) minN
        else if (minN.greaterThanOrEquals(numberN).isTruthy) minN
        else if (numberN.greaterThanOrEquals(maxN).isTruthy) maxN
        else numberN
      }
    )

  private val maxFn: BuiltInCallable =
    BuiltInCallable.function(
      "max",
      "$numbers...",
      { args =>
        val raw:  List[Value]          = if (args.length == 1) args(0).asList else args
        var maxN: Nullable[SassNumber] = Nullable.empty
        for (value <- raw) {
          val number = value.assertNumber()
          if (maxN.isEmpty || maxN.get.lessThan(number).isTruthy)
            maxN = Nullable(number)
        }
        if (maxN.isDefined) maxN.get
        else throw SassScriptException("At least one argument must be passed.")
      }
    )

  private val minFn: BuiltInCallable =
    BuiltInCallable.function(
      "min",
      "$numbers...",
      { args =>
        val raw:  List[Value]          = if (args.length == 1) args(0).asList else args
        var minN: Nullable[SassNumber] = Nullable.empty
        for (value <- raw) {
          val number = value.assertNumber()
          if (minN.isEmpty || minN.get.greaterThan(number).isTruthy)
            minN = Nullable(number)
        }
        if (minN.isDefined) minN.get
        else throw SassScriptException("At least one argument must be passed.")
      }
    )

  private val moduleAbsFn: BuiltInCallable = singleArgMathFn("abs", numberAbs)

  // ---------------------------------------------------------------------------
  // Distance functions
  // ---------------------------------------------------------------------------

  private val hypotFn: BuiltInCallable =
    BuiltInCallable.function(
      "hypot",
      "$numbers...",
      { args =>
        val raw: List[Value] = if (args.length == 1) args(0).asList else args
        val numbers = raw.map(_.assertNumber())
        if (numbers.isEmpty)
          throw SassScriptException("At least one argument must be passed.")

        // Reject complex units the same way SassCalculation does for the
        // calc-shorthand path. The ssg-sass evaluator's calc-shorthand
        // path catches SassScriptException and falls through here, so
        // we have to re-raise the "isn't compatible with CSS calculations"
        // error ourselves to match dart-sass's user-facing behavior
        // (see values/calculation/hypot.hrx!error/unsimplifiable).
        for (n <- numbers)
          if (n.hasComplexUnits)
            throw SassScriptException(s"Number $n isn't compatible with CSS calculations.")

        var subtotal = 0.0
        var i        = 0
        while (i < numbers.length) {
          val number = numbers(i)
          val value  = number.convertValueToMatch(numbers.head, s"numbers[${i + 1}]", "numbers[1]")
          subtotal += math.pow(value, 2)
          i += 1
        }
        SassNumber.withUnits(
          math.sqrt(subtotal),
          numbers.head.numeratorUnits,
          numbers.head.denominatorUnits
        )
      }
    )

  // ---------------------------------------------------------------------------
  // Exponential functions
  // ---------------------------------------------------------------------------

  private val logFn: BuiltInCallable =
    BuiltInCallable.function(
      "log",
      "$number, $base: null",
      { args =>
        val number = args(0).assertNumber("number")
        if (number.hasUnits)
          throw SassScriptException(s"$$number: Expected $number to have no units.")

        val baseArg: Value = if (args.length >= 2) args(1) else SassNull
        baseArg match {
          case SassNull =>
            numberLog(number, Nullable.empty)
          case _ =>
            val base = baseArg.assertNumber("base")
            if (base.hasUnits)
              throw SassScriptException(s"$$base: Expected $base to have no units.")
            numberLog(number, Nullable(base))
        }
      }
    )

  private val powFn: BuiltInCallable =
    BuiltInCallable.function(
      "pow",
      "$base, $exponent",
      { args =>
        val base     = args(0).assertNumber("base")
        val exponent = args(1).assertNumber("exponent")
        numberPow(base, exponent)
      }
    )

  private val sqrtFn: BuiltInCallable = singleArgMathFn("sqrt", numberSqrt)

  // ---------------------------------------------------------------------------
  // Trigonometric functions
  // ---------------------------------------------------------------------------

  private val acosFn: BuiltInCallable = singleArgMathFn("acos", numberAcos)
  private val asinFn: BuiltInCallable = singleArgMathFn("asin", numberAsin)
  private val atanFn: BuiltInCallable = singleArgMathFn("atan", numberAtan)

  private val atan2Fn: BuiltInCallable =
    BuiltInCallable.function(
      "atan2",
      "$y, $x",
      { args =>
        val y = args(0).assertNumber("y")
        val x = args(1).assertNumber("x")
        numberAtan2(y, x)
      }
    )

  private val cosFn: BuiltInCallable = singleArgMathFn("cos", numberCos)
  private val sinFn: BuiltInCallable = singleArgMathFn("sin", numberSin)
  private val tanFn: BuiltInCallable = singleArgMathFn("tan", numberTan)

  // ---------------------------------------------------------------------------
  // Unit functions
  // ---------------------------------------------------------------------------

  private val compatibleFn: BuiltInCallable =
    BuiltInCallable.function(
      "compatible",
      "$number1, $number2",
      { args =>
        val n1 = args(0).assertNumber("number1")
        val n2 = args(1).assertNumber("number2")
        SassBoolean(n1.isComparableTo(n2))
      }
    )

  private val isUnitlessFn: BuiltInCallable =
    BuiltInCallable.function(
      "is-unitless",
      "$number",
      { args =>
        val number = args(0).assertNumber("number")
        SassBoolean(!number.hasUnits)
      }
    )

  private val unitFn: BuiltInCallable =
    BuiltInCallable.function(
      "unit",
      "$number",
      { args =>
        val number = args(0).assertNumber("number")
        SassString(number.unitString, hasQuotes = true)
      }
    )

  // ---------------------------------------------------------------------------
  // Other functions
  // ---------------------------------------------------------------------------

  private val percentageFn: BuiltInCallable =
    BuiltInCallable.function(
      "percentage",
      "$number",
      { args =>
        val number = args(0).assertNumber("number")
        number.assertNoUnits("number")
        SassNumber(number.value * 100, "%")
      }
    )

  private val rng: Random = new Random()

  private val randomFn: BuiltInCallable =
    BuiltInCallable.function(
      "random",
      "$limit: null",
      { args =>
        val arg: Value = if (args.nonEmpty) args(0) else SassNull
        arg match {
          case SassNull =>
            SassNumber(rng.nextDouble())
          case _ =>
            val limit = arg.assertNumber("limit")
            if (limit.hasUnits) {
              EvaluationContext.warnForDeprecation(
                Deprecation.FunctionUnits,
                s"math.random() will no longer ignore $$limit units ($limit) in a future release.\n\n" +
                  s"Recommendation: math.random(math.div($$limit, 1${limit.unitString})) * 1${limit.unitString}\n\n" +
                  s"To preserve current behavior: math.random(math.div($$limit, 1${limit.unitString}))\n\n" +
                  "More info: https://sass-lang.com/d/function-units"
              )
            }
            val limitScalar = limit.assertInt("limit")
            if (limitScalar < 1)
              throw SassScriptException(s"$$limit: Must be greater than 0, was $limit.")
            SassNumber((rng.nextInt(limitScalar) + 1).toDouble)
        }
      }
    )

  private val divFn: BuiltInCallable =
    BuiltInCallable.function(
      "div",
      "$number1, $number2",
      { args =>
        val number1 = args(0)
        val number2 = args(1)
        if (!number1.isInstanceOf[SassNumber] || !number2.isInstanceOf[SassNumber]) {
          EvaluationContext.warnForDeprecation(
            Deprecation.SlashDiv,
            "math.div() will only support number arguments in a future release.\n" +
              "Use list.slash() instead for a slash separator."
          )
        }
        number1.dividedBy(number2)
      }
    )

  // ---------------------------------------------------------------------------
  // Global aliases via withDeprecationWarning / withName.
  // Matches dart-sass's `.withDeprecationWarning('math').withName(...)` pattern.
  // ---------------------------------------------------------------------------

  /** The global abs() — has additional `abs-percent` deprecation plus `global-builtin` deprecation warnings that the module-level abs does not emit. The body otherwise matches `numberAbs`.
    */
  private val globalAbsFn: BuiltInCallable =
    BuiltInCallable.function(
      "abs",
      "$number",
      { args =>
        val number = args(0).assertNumber("number")
        if (number.hasUnit("%")) {
          EvaluationContext.warnForDeprecation(
            Deprecation.AbsPercent,
            "Passing percentage units to the global abs() function is deprecated.\n" +
              "In the future, this will emit a CSS abs() function to be resolved by the browser.\n" +
              "To preserve current behavior: math.abs($number)\n\n" +
              "To emit a CSS abs() now: abs(#{$number})\n" +
              "More info: https://sass-lang.com/d/abs-percent"
          )
        } else {
          BuiltInCallable.warnForGlobalBuiltIn("math", "abs")
        }
        SassNumber.withUnits(
          math.abs(number.value),
          number.numeratorUnits,
          number.denominatorUnits
        )
      }
    )

  // ---------------------------------------------------------------------------
  // Public lists.
  // ---------------------------------------------------------------------------

  /** Globally available built-ins. Mirrors dart-sass `global` exactly. Each entry uses `.withDeprecationWarning("math")` to emit a `global-builtin` deprecation warning directing users to `math.X`.
    * The global `abs()` handles its own deprecation warnings inline (it has the additional `abs-percent` path), so it is not wrapped.
    */
  val global: List[Callable] = List(
    globalAbsFn,
    ceilFn.withDeprecationWarning("math"),
    floorFn.withDeprecationWarning("math"),
    maxFn.withDeprecationWarning("math"),
    minFn.withDeprecationWarning("math"),
    percentageFn.withDeprecationWarning("math"),
    randomFn.withDeprecationWarning("math"),
    roundFn.withDeprecationWarning("math"),
    unitFn.withDeprecationWarning("math"),
    compatibleFn.withDeprecationWarning("math").withName("comparable"),
    isUnitlessFn.withDeprecationWarning("math").withName("unitless"),
    // These math functions are registered globally as a workaround: in dart-sass
    // they are dispatched by the evaluator's special-case calc path, not as
    // global built-ins. Once EvaluateVisitor handles top-level sqrt()/sin()/
    // cos()/etc. through calc evaluation, these entries can be removed.
    sqrtFn,
    sinFn,
    cosFn,
    tanFn,
    asinFn,
    acosFn,
    atanFn,
    logFn,
    powFn,
    clampFn,
    hypotFn
  )

  /** Members of the `sass:math` module. Mirrors dart-sass `module`. */
  def module: List[Callable] = List(
    moduleAbsFn,
    acosFn,
    asinFn,
    atanFn,
    atan2Fn,
    ceilFn,
    clampFn,
    cosFn,
    compatibleFn,
    floorFn,
    hypotFn,
    isUnitlessFn,
    logFn,
    maxFn,
    minFn,
    percentageFn,
    powFn,
    randomFn,
    roundFn,
    sinFn,
    sqrtFn,
    tanFn,
    unitFn,
    divFn
  )

  /** Built-in constants exposed as module variables under `@use "sass:math"`. Mirrors dart-sass's `variables` map on the math BuiltInModule. The evaluator consumes this map through
    * `EvaluateVisitor.visitUseRule` when loading `sass:math`, which seeds these names into the math module's `Environment` via `Functions.moduleVariables(moduleName)`.
    */
  def moduleVariables: Map[String, Value] = Map(
    "e" -> SassNumber(math.E),
    "pi" -> SassNumber(math.Pi),
    "epsilon" -> SassNumber(2.220446049250313e-16),
    "max-safe-integer" -> SassNumber(9007199254740991.0),
    "min-safe-integer" -> SassNumber(-9007199254740991.0),
    "max-number" -> SassNumber(Double.MaxValue),
    "min-number" -> SassNumber(Double.MinPositiveValue)
  )
}
