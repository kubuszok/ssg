/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/calculation.dart
 * Original: Copyright (c) 2021 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: calculation.dart → SassCalculation.scala
 *   Convention: Dart final class → Scala final class; Dart enum → Scala 3 enum
 *   Idiom: Dart null → Nullable; Dart Object → Any; Dart switch → Scala match;
 *          number_lib functions inlined into companion object (Phase 9 not yet available);
 *          EvaluationContext.warnForDeprecation deferred (abs% deprecation is a no-op for now);
 *          CalculationInterpolation ported as deprecated class;
 *          Dart List.unmodifiable → List (Scala Lists are immutable)
 *   Audited: 2026-04-06
 */
package ssg
package sass
package value

import ssg.sass.{ Nullable, SassScriptException }
import ssg.sass.Nullable.*
import ssg.sass.util.CharCode
import ssg.sass.util.CharCode.*
import ssg.sass.util.NumberUtil
import ssg.sass.util.NumberUtil.*
import ssg.sass.visitor.ValueVisitor

import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

/** A SassScript calculation.
  *
  * Although calculations can in principle have any name or any number of arguments, this class only exposes the specific calculations that are supported by the Sass spec. This ensures that all
  * calculations that the user works with are always fully reduced.
  */
final class SassCalculation private (
  val name:      String,
  val arguments: List[Any]
) extends Value {

  override def isSpecialNumber: Boolean = true

  override def accept[T](visitor: ValueVisitor[T]): T = visitor.visitCalculation(this)

  override def assertCalculation(name: Nullable[String]): SassCalculation = this

  override def plus(other: Value): Value = other match {
    case _: SassString => super.plus(other)
    case _ => throw SassScriptException(s"""Undefined operation "$this + $other".""")
  }

  override def minus(other: Value): Value =
    throw SassScriptException(s"""Undefined operation "$this - $other".""")

  override def unaryPlus(): Value =
    throw SassScriptException(s"""Undefined operation "+$this".""")

  override def unaryMinus(): Value =
    throw SassScriptException(s"""Undefined operation "-$this".""")

  override def equals(other: Any): Boolean = other match {
    case o: SassCalculation =>
      name == o.name && arguments == o.arguments
    case _ => false
  }

  override def hashCode(): Int = name.hashCode ^ arguments.hashCode()

  override def toCssString(quote: Boolean = true): String = {
    val sb = new StringBuilder()
    sb.append(name)
    sb.append('(')
    var first = true
    for (a <- arguments) {
      if (!first) sb.append(", ")
      first = false
      sb.append(SassCalculation.argumentToCss(a))
    }
    sb.append(')')
    sb.toString()
  }

  override def toString: String = toCssString()
}

object SassCalculation {

  /** Creates a new calculation with the given name and arguments, without reducing them.
    */
  def unsimplified(name: String, arguments: Iterable[Any]): SassCalculation =
    new SassCalculation(name, arguments.toList)

  /** Renders a calculation argument (SassNumber, CalculationOperation, SassString, or SassCalculation) as its CSS source form.
    *
    * Non-finite SassNumbers (Infinity, -Infinity, NaN) are written as CSS-spec lowercase `infinity` / `-infinity` / `NaN` with unit factors appended as ` * 1<unit>` / ` / 1<unit>`, matching dart-sass
    * `_writeCalculationValue` + `_writeCalculationUnits`.
    */
  def argumentToCss(arg: Any): String = arg match {
    case n: SassNumber if !n.value.isFinite =>
      val sb = new StringBuilder()
      if (n.value == Double.PositiveInfinity) sb.append("infinity")
      else if (n.value == Double.NegativeInfinity) sb.append("-infinity")
      else sb.append("NaN")
      appendCalculationUnits(sb, n.numeratorUnits, n.denominatorUnits)
      sb.toString()
    case n:  SassNumber           => n.toCssString()
    case c:  SassCalculation      => c.toCssString()
    case s:  SassString           => s.toCssString(quote = false)
    case op: CalculationOperation =>
      val l = argumentToCssParenthesized(op.left, op.operator, isLeft = true)
      val r = argumentToCssParenthesized(op.right, op.operator, isLeft = false)
      s"$l ${op.operator.operator} $r"
    case v: Value => v.toCssString(quote = false)
    case other => other.toString
  }

  /** Appends numerator / denominator units as ` * 1<unit>` / ` / 1<unit>` factors, matching dart-sass `_writeCalculationUnits`.
    */
  private def appendCalculationUnits(
    sb:               StringBuilder,
    numeratorUnits:   List[String],
    denominatorUnits: List[String]
  ): Unit = {
    for (unit <- numeratorUnits)
      sb.append(" * 1").append(unit)
    for (unit <- denominatorUnits)
      sb.append(" / 1").append(unit)
  }

  /** Returns whether the right-hand operation of a calculation should be
    * parenthesized. Ported from dart-sass `_parenthesizeCalculationRhs`.
    */
  private def parenthesizeCalculationRhs(
    outer: CalculationOperator,
    right: CalculationOperator
  ): Boolean = outer match {
    case CalculationOperator.DividedBy => true
    case CalculationOperator.Plus      => false
    case _ => right == CalculationOperator.Plus || right == CalculationOperator.Minus
  }

  /** Wraps a calculation child in parens when necessary, matching dart-sass
    * `_writeCalculationValue` parenthesization logic.
    */
  private def argumentToCssParenthesized(arg: Any, parentOp: CalculationOperator, isLeft: Boolean): String = {
    val needsParens = if (isLeft) {
      arg match {
        case op: CalculationOperation => op.operator.precedence < parentOp.precedence
        case _                        => false
      }
    } else {
      arg match {
        case op: CalculationOperation => parenthesizeCalculationRhs(parentOp, op.operator)
        case n: SassNumber if parentOp == CalculationOperator.DividedBy =>
          if (n.value.isFinite) n.hasComplexUnits else n.hasUnits
        case _ => false
      }
    }
    if (needsParens) s"(${argumentToCss(arg)})" else argumentToCss(arg)
  }

  /** Creates a `calc()` calculation with the given argument.
    *
    * The argument must be either a SassNumber, a SassCalculation, an unquoted SassString, or a CalculationOperation.
    *
    * This automatically simplifies the calculation, so it may return a SassNumber rather than a SassCalculation. It throws an exception if it can determine that the calculation will definitely
    * produce invalid CSS.
    */
  def calc(argument: Any): Value =
    _simplify(argument) match {
      case value: SassNumber      => value
      case value: SassCalculation => value
      case simplified => new SassCalculation("calc", List(simplified))
    }

  /** Creates a `min()` calculation with the given arguments.
    *
    * Each argument must be either a SassNumber, a SassCalculation, an unquoted SassString, or a CalculationOperation. It must be passed at least one argument.
    *
    * This automatically simplifies the calculation, so it may return a SassNumber rather than a SassCalculation.
    */
  def min(arguments: Iterable[Any]): Value = {
    val args = _simplifyArguments(arguments)
    if (args.isEmpty) {
      throw new IllegalArgumentException("min() must have at least one argument.")
    }

    var minimum: Nullable[SassNumber] = Nullable.Null
    boundary[Unit] {
      for (arg <- args)
        arg match {
          case num: SassNumber =>
            if (minimum.isDefined && !minimum.get.isComparableTo(num)) {
              minimum = Nullable.Null
              break(())
            } else if (minimum.isEmpty || minimum.get.greaterThan(num).isTruthy) {
              minimum = num
            }
          case _ =>
            minimum = Nullable.Null
            break(())
        }
    }
    if (minimum.isDefined) {
      minimum.get
    } else {
      _verifyCompatibleNumbers(args)
      new SassCalculation("min", args)
    }
  }

  /** Creates a `max()` calculation with the given arguments.
    *
    * Each argument must be either a SassNumber, a SassCalculation, an unquoted SassString, or a CalculationOperation. It must be passed at least one argument.
    *
    * This automatically simplifies the calculation, so it may return a SassNumber rather than a SassCalculation.
    */
  def max(arguments: Iterable[Any]): Value = {
    val args = _simplifyArguments(arguments)
    if (args.isEmpty) {
      throw new IllegalArgumentException("max() must have at least one argument.")
    }

    var maximum: Nullable[SassNumber] = Nullable.Null
    boundary[Unit] {
      for (arg <- args)
        arg match {
          case num: SassNumber =>
            if (maximum.isDefined && !maximum.get.isComparableTo(num)) {
              maximum = Nullable.Null
              break(())
            } else if (maximum.isEmpty || maximum.get.lessThan(num).isTruthy) {
              maximum = num
            }
          case _ =>
            maximum = Nullable.Null
            break(())
        }
    }
    if (maximum.isDefined) {
      maximum.get
    } else {
      _verifyCompatibleNumbers(args)
      new SassCalculation("max", args)
    }
  }

  /** Creates a `hypot()` calculation with the given arguments. */
  def hypot(arguments: Iterable[Any]): Value = {
    val args = _simplifyArguments(arguments)
    if (args.isEmpty) {
      throw new IllegalArgumentException("hypot() must have at least one argument.")
    }
    _verifyCompatibleNumbers(args)

    val first = args.head
    first match {
      case firstNum: SassNumber if !firstNum.hasUnit("%") =>
        var subtotal   = 0.0
        val allNumbers = boundary[Boolean] {
          for (i <- args.indices)
            args(i) match {
              case number: SassNumber if number.hasCompatibleUnits(firstNum) =>
                val v = number.convertValueToMatch(firstNum, s"numbers[${i + 1}]", "numbers[1]")
                subtotal += v * v
              case _ =>
                break(false)
            }
          true
        }
        if (allNumbers) {
          SassNumber.withUnits(
            math.sqrt(subtotal),
            numeratorUnits = firstNum.numeratorUnits,
            denominatorUnits = firstNum.denominatorUnits
          )
        } else {
          new SassCalculation("hypot", args)
        }
      case _ =>
        new SassCalculation("hypot", args)
    }
  }

  /** Creates a `sqrt()` calculation with the given argument. */
  def sqrt(argument: Any): Value =
    _singleArgument("sqrt", argument, _numberSqrt, forbidUnits = true)

  /** Creates a `sin()` calculation with the given argument. */
  def sin(argument: Any): Value =
    _singleArgument("sin", argument, _numberSin)

  /** Creates a `cos()` calculation with the given argument. */
  def cos(argument: Any): Value =
    _singleArgument("cos", argument, _numberCos)

  /** Creates a `tan()` calculation with the given argument. */
  def tan(argument: Any): Value =
    _singleArgument("tan", argument, _numberTan)

  /** Creates an `atan()` calculation with the given argument. */
  def atan(argument: Any): Value =
    _singleArgument("atan", argument, _numberAtan, forbidUnits = true)

  /** Creates an `asin()` calculation with the given argument. */
  def asin(argument: Any): Value =
    _singleArgument("asin", argument, _numberAsin, forbidUnits = true)

  /** Creates an `acos()` calculation with the given argument. */
  def acos(argument: Any): Value =
    _singleArgument("acos", argument, _numberAcos, forbidUnits = true)

  /** Creates an `abs()` calculation with the given argument.
    *
    * This automatically simplifies the calculation, so it may return a SassNumber rather than a SassCalculation. It throws an exception if it can determine that the calculation will definitely
    * produce invalid CSS.
    */
  def abs(argument: Any): Value = {
    val simplified = _simplify(argument)
    simplified match {
      case number: SassNumber =>
        if (number.hasUnit("%")) {
          ssg.sass.EvaluationContext.warnForDeprecation(
            ssg.sass.Deprecation.AbsPercent,
            "Passing percentage units to the global abs() function is deprecated.\n" +
              "In the future, this will emit a CSS abs() function to be resolved by the browser.\n" +
              "To preserve current behavior: math.abs($argument)" +
              "\n" +
              "To emit a CSS abs() now: abs(#{$argument})\n" +
              "More info: https://sass-lang.com/d/abs-percent"
          )
        }
        _numberAbs(number)
      case _ =>
        new SassCalculation("abs", List(simplified))
    }
  }

  /** Creates an `exp()` calculation with the given argument. */
  def exp(argument: Any): Value = {
    val simplified = _simplify(argument)
    simplified match {
      case number: SassNumber =>
        number.assertNoUnits()
        _numberPow(SassNumber(math.E), number)
      case _ =>
        new SassCalculation("exp", List(simplified))
    }
  }

  /** Creates a `sign()` calculation with the given argument. */
  def sign(argument: Any): Value = {
    val simplified = _simplify(argument)
    simplified match {
      case number: SassNumber if number.value.isNaN || number.value == 0 =>
        number
      case number: SassNumber if !number.hasUnit("%") =>
        SassNumber(number.value.sign).coerceToMatch(number)
      case _ =>
        new SassCalculation("sign", List(simplified))
    }
  }

  /** Creates a `clamp()` calculation with the given min, value, and max.
    *
    * Each argument must be either a SassNumber, a SassCalculation, an unquoted SassString, or a CalculationOperation.
    *
    * This may be passed fewer than three arguments, but only if one of the arguments is an unquoted `var()` string.
    */
  def clamp(minArg: Any, valueArg: Nullable[Any] = Nullable.Null, maxArg: Nullable[Any] = Nullable.Null): Value = {
    if (valueArg.isEmpty && maxArg.isDefined) {
      throw new IllegalArgumentException("If value is null, max must also be null.")
    }

    val simplifiedMin = _simplify(minArg)
    val simplifiedValue: Nullable[Any] = valueArg.map(_simplify)
    val simplifiedMax:   Nullable[Any] = maxArg.map(_simplify)

    (simplifiedMin, simplifiedValue, simplifiedMax) match {
      case (sMin: SassNumber, sv, sm) =>
        (sv, sm) match {
          case (sValue: SassNumber, sMax: SassNumber) if sMin.hasCompatibleUnits(sValue) && sMin.hasCompatibleUnits(sMax) =>
            if (sValue.lessThanOrEquals(sMin).isTruthy) sMin
            else if (sValue.greaterThanOrEquals(sMax).isTruthy) sMax
            else sValue
          case _ =>
            val args = List(simplifiedMin) ++
              simplifiedValue.fold(Nil: List[Any])(v => List(v)) ++
              simplifiedMax.fold(Nil: List[Any])(v => List(v))
            _verifyCompatibleNumbers(args)
            _verifyLength(args, 3)
            new SassCalculation("clamp", args)
        }
      case _ =>
        val args = List(simplifiedMin) ++
          simplifiedValue.fold(Nil: List[Any])(v => List(v)) ++
          simplifiedMax.fold(Nil: List[Any])(v => List(v))
        _verifyCompatibleNumbers(args)
        _verifyLength(args, 3)
        new SassCalculation("clamp", args)
    }
  }

  /** Creates a `pow()` calculation with the given base and exponent. */
  def pow(base: Any, exponent: Nullable[Any]): Value = {
    val args: List[Any] = List(base) ++ exponent.fold(Nil: List[Any])(e => List(e))
    _verifyLength(args, 2)
    val simplifiedBase = _simplify(base)
    val simplifiedExponent: Nullable[Any] = exponent.map(_simplify)
    (simplifiedBase, simplifiedExponent) match {
      case (b: SassNumber, e: SassNumber) =>
        b.assertNoUnits()
        e.assertNoUnits()
        _numberPow(b, e)
      case _ =>
        new SassCalculation("pow", args)
    }
  }

  /** Creates a `log()` calculation with the given number and base. */
  def log(number: Any, base: Nullable[Any]): Value = {
    val simplifiedNumber = _simplify(number)
    val simplifiedBase: Nullable[Any] = base.map(_simplify)
    val args:           List[Any]     = List(simplifiedNumber) ++ simplifiedBase.fold(Nil: List[Any])(b => List(b))
    (simplifiedNumber, simplifiedBase) match {
      case (num: SassNumber, _) =>
        simplifiedBase match {
          case b: SassNumber =>
            num.assertNoUnits()
            b.assertNoUnits()
            _numberLog(num, Nullable(b))
          case _ if simplifiedBase.isEmpty =>
            num.assertNoUnits()
            _numberLog(num, Nullable.Null)
          case _ =>
            new SassCalculation("log", args)
        }
      case _ =>
        new SassCalculation("log", args)
    }
  }

  /** Creates a `atan2()` calculation for y and x. */
  def atan2(y: Any, x: Nullable[Any]): Value = {
    val simplifiedY = _simplify(y)
    val simplifiedX: Nullable[Any] = x.map(_simplify)
    val args:        List[Any]     = List(simplifiedY) ++ simplifiedX.fold(Nil: List[Any])(v => List(v))
    _verifyLength(args, 2)
    _verifyCompatibleNumbers(args)
    (simplifiedY, simplifiedX) match {
      case (yNum: SassNumber, xNum: SassNumber) if !yNum.hasUnit("%") && !xNum.hasUnit("%") && yNum.hasCompatibleUnits(xNum) =>
        _numberAtan2(yNum, xNum)
      case _ =>
        new SassCalculation("atan2", args)
    }
  }

  /** Creates a `rem()` calculation with the given dividend and modulus. */
  def rem(dividend: Any, modulus: Nullable[Any]): Value = {
    val simplifiedDividend = _simplify(dividend)
    val simplifiedModulus: Nullable[Any] = modulus.map(_simplify)
    val args:              List[Any]     = List(simplifiedDividend) ++ simplifiedModulus.fold(Nil: List[Any])(v => List(v))
    _verifyLength(args, 2)
    _verifyCompatibleNumbers(args)
    (simplifiedDividend, simplifiedModulus) match {
      case (dNum: SassNumber, mNum: SassNumber) if dNum.hasCompatibleUnits(mNum) =>
        val result = dNum.modulo(mNum).asInstanceOf[SassNumber]
        if (NumberUtil.signIncludingZero(mNum.value) != NumberUtil.signIncludingZero(dNum.value)) {
          if (mNum.value.isInfinite) dNum
          else if (result.value == 0) result.unaryMinus().asInstanceOf[SassNumber]
          else result.minus(mNum).asInstanceOf[SassNumber]
        } else {
          result
        }
      case _ =>
        new SassCalculation("rem", args)
    }
  }

  /** Creates a `mod()` calculation with the given dividend and modulus. */
  def mod(dividend: Any, modulus: Nullable[Any]): Value = {
    val simplifiedDividend = _simplify(dividend)
    val simplifiedModulus: Nullable[Any] = modulus.map(_simplify)
    val args:              List[Any]     = List(simplifiedDividend) ++ simplifiedModulus.fold(Nil: List[Any])(v => List(v))
    _verifyLength(args, 2)
    _verifyCompatibleNumbers(args)
    (simplifiedDividend, simplifiedModulus) match {
      case (dNum: SassNumber, mNum: SassNumber) if dNum.hasCompatibleUnits(mNum) =>
        dNum.modulo(mNum).asInstanceOf[SassNumber]
      case _ =>
        new SassCalculation("mod", args)
    }
  }

  /** Creates a `round()` calculation with the given strategyOrNumber, numberOrStep, and step. Strategy must be either nearest, up, down or to-zero.
    */
  def round(
    strategyOrNumber: Any,
    numberOrStep:     Nullable[Any] = Nullable.Null,
    step:             Nullable[Any] = Nullable.Null
  ): Value =
    roundInternal(
      strategyOrNumber,
      numberOrStep,
      step,
      inLegacySassFunction = Nullable.Null,
      warn = Nullable.Null
    )

  /** Like round, but with the internal-only inLegacySassFunction and warn parameters. */
  def roundInternal(
    strategyOrNumber:     Any,
    numberOrStep:         Nullable[Any],
    step:                 Nullable[Any],
    inLegacySassFunction: Nullable[String],
    warn:                 Nullable[(String, Nullable[Deprecation]) => Unit]
  ): Value = {
    val simplifiedStrategy = _simplify(strategyOrNumber)
    val simplifiedNumberOrStep: Nullable[Any] = numberOrStep.map(_simplify)
    val simplifiedStep:         Nullable[Any] = step.map(_simplify)

    (simplifiedStrategy, simplifiedNumberOrStep, simplifiedStep) match {
      // round(number) where number is unitless
      case (number: SassNumber, _, _) if !number.hasUnits && simplifiedNumberOrStep.isEmpty && simplifiedStep.isEmpty =>
        SassNumber(number.value.round.toDouble)

      // round(number) where number has units and in legacy function
      case (number: SassNumber, _, _) if simplifiedNumberOrStep.isEmpty && simplifiedStep.isEmpty && inLegacySassFunction.isDefined =>
        warn.foreach(w =>
          w(
            "In future versions of Sass, round() will be interpreted as a CSS " +
              "round() calculation. This requires an explicit modulus when " +
              "rounding numbers with units. If you want to use the Sass " +
              "function, call math.round() instead.\n" +
              "\n" +
              "See https://sass-lang.com/d/import",
            Nullable(Deprecation.GlobalBuiltin)
          )
        )
        _matchUnits(number.value.round.toDouble, number)

      // round(number, step) where incompatible
      case (number: SassNumber, stepVal: SassNumber, _) if simplifiedStep.isEmpty && !number.hasCompatibleUnits(stepVal) =>
        _verifyCompatibleNumbers(List(number, stepVal))
        new SassCalculation("round", List(number, stepVal))

      // round(number, step) where compatible
      case (number: SassNumber, stepVal: SassNumber, _) if simplifiedStep.isEmpty =>
        _verifyCompatibleNumbers(List(number, stepVal))
        _roundWithStep("nearest", number, stepVal)

      // round(strategy, number, step) where strategy is valid and incompatible
      case (strategy: SassString, number: SassNumber, stepOpt)
          if _isRoundingStrategy(strategy.text) && stepOpt.exists(_.isInstanceOf[SassNumber])
            && !number.hasCompatibleUnits(stepOpt.get.asInstanceOf[SassNumber]) =>
        val stepNum = stepOpt.get.asInstanceOf[SassNumber]
        _verifyCompatibleNumbers(List(number, stepNum))
        new SassCalculation("round", List(strategy, number, stepNum))

      // round(strategy, number, step) where strategy is valid and compatible
      case (strategy: SassString, number: SassNumber, stepOpt) if _isRoundingStrategy(strategy.text) && stepOpt.exists(_.isInstanceOf[SassNumber]) =>
        val stepNum = stepOpt.get.asInstanceOf[SassNumber]
        _verifyCompatibleNumbers(List(number, stepNum))
        _roundWithStep(strategy.text, number, stepNum)

      // round(strategy, rest-string, null)
      case (strategy: SassString, rest: SassString, _) if _isRoundingStrategy(strategy.text) && simplifiedStep.isEmpty =>
        new SassCalculation("round", List(strategy, rest))

      // round(strategy, non-null-non-number, null)
      case (strategy: SassString, _, _) if _isRoundingStrategy(strategy.text) && simplifiedNumberOrStep.isDefined && simplifiedStep.isEmpty =>
        throw SassScriptException("If strategy is not null, step is required.")

      // round(strategy, null, null)
      case (strategy: SassString, _, _) if _isRoundingStrategy(strategy.text) && simplifiedNumberOrStep.isEmpty && simplifiedStep.isEmpty =>
        throw SassScriptException("Number to round and step arguments are required.")

      // round(non-strategy-non-number, null, null) => unsimplified
      case (number, _, _) if simplifiedNumberOrStep.isEmpty && simplifiedStep.isEmpty =>
        new SassCalculation("round", List(number))

      // round(number, step, null) where step is not a number
      case (number, _, _) if simplifiedStep.isEmpty && simplifiedNumberOrStep.isDefined =>
        new SassCalculation("round", List(number, simplifiedNumberOrStep.get))

      // round(strategy-or-special-var, number, step)
      case (strategy: SassString, _, _)
          if (_isRoundingStrategy(strategy.text) || strategy.isSpecialVariable)
            && simplifiedNumberOrStep.isDefined && simplifiedStep.isDefined =>
        new SassCalculation("round", List(strategy, simplifiedNumberOrStep.get, simplifiedStep.get))

      // round(non-strategy, number?, step?)
      case (_, _, _) if simplifiedNumberOrStep.isDefined && simplifiedStep.isDefined =>
        throw SassScriptException(
          s"$strategyOrNumber must be either nearest, up, down or to-zero."
        )

      case _ =>
        throw SassScriptException("Invalid parameters.")
    }
  }

  /** Creates a `calc-size()` calculation with the given basis and value. */
  def calcSize(basis: Any, value: Nullable[Any]): SassCalculation = {
    val args: List[Any] = List(basis) ++ value.fold(Nil: List[Any])(v => List(v))
    _verifyLength(args, 2)
    val simplifiedBasis = _simplify(basis)
    val simplifiedValue: Nullable[Any] = value.map(_simplify)
    new SassCalculation(
      "calc-size",
      List(simplifiedBasis) ++ simplifiedValue.fold(Nil: List[Any])(v => List(v))
    )
  }

  /** Creates and simplifies a CalculationOperation with the given operator, left, and right.
    */
  def operate(
    operator: CalculationOperator,
    left:     Any,
    right:    Any
  ): Any =
    operateInternal(
      operator,
      left,
      right,
      inLegacySassFunction = Nullable.Null,
      simplify = true,
      warn = Nullable.Null
    )

  /** Like operate, but with the internal-only inLegacySassFunction and warn parameters.
    */
  def operateInternal(
    operator:             CalculationOperator,
    left:                 Any,
    right:                Any,
    inLegacySassFunction: Nullable[String],
    simplify:             Boolean,
    warn:                 Nullable[(String, Nullable[Deprecation]) => Unit]
  ): Any =
    if (!simplify) {
      CalculationOperation(operator, left, right)
    } else {
      val simplifiedLeft  = _simplify(left)
      var simplifiedRight = _simplify(right)

      operator match {
        case CalculationOperator.Plus | CalculationOperator.Minus =>
          (simplifiedLeft, simplifiedRight) match {
            case (l: SassNumber, r: SassNumber) =>
              var compatible = l.hasCompatibleUnits(r)
              if (!compatible && inLegacySassFunction.isDefined && l.isComparableTo(r)) {
                warn.foreach(w =>
                  w(
                    s"In future versions of Sass, ${inLegacySassFunction.get}() will be " +
                      s"interpreted as the CSS ${inLegacySassFunction.get}() calculation. " +
                      "This doesn't allow unitless numbers to be mixed with numbers " +
                      "with units. If you want to use the Sass function, call " +
                      s"math.${inLegacySassFunction.get}() instead.\n" +
                      "\n" +
                      "See https://sass-lang.com/d/import",
                    Nullable(Deprecation.GlobalBuiltin)
                  )
                )
                compatible = true
              }
              if (compatible) {
                if (operator == CalculationOperator.Plus) l.plus(r)
                else l.minus(r)
              } else {
                _verifyCompatibleNumbers(List(simplifiedLeft, simplifiedRight))

                if (fuzzyLessThan(r.value, 0)) {
                  simplifiedRight = r.times(SassNumber(-1)).asInstanceOf[SassNumber]
                  val flippedOp =
                    if (operator == CalculationOperator.Plus) CalculationOperator.Minus
                    else CalculationOperator.Plus
                  CalculationOperation(flippedOp, simplifiedLeft, simplifiedRight)
                } else {
                  CalculationOperation(operator, simplifiedLeft, simplifiedRight)
                }
              }
            case _ =>
              _verifyCompatibleNumbers(List(simplifiedLeft, simplifiedRight))

              simplifiedRight match {
                case r: SassNumber if fuzzyLessThan(r.value, 0) =>
                  simplifiedRight = r.times(SassNumber(-1)).asInstanceOf[SassNumber]
                  val flippedOp =
                    if (operator == CalculationOperator.Plus) CalculationOperator.Minus
                    else CalculationOperator.Plus
                  CalculationOperation(flippedOp, simplifiedLeft, simplifiedRight)
                case _ =>
                  CalculationOperation(operator, simplifiedLeft, simplifiedRight)
              }
          }
        case _ =>
          // times or dividedBy
          (simplifiedLeft, simplifiedRight) match {
            case (l: SassNumber, r: SassNumber) =>
              if (operator == CalculationOperator.Times) l.times(r)
              else l.dividedBy(r)
            case _ =>
              CalculationOperation(operator, simplifiedLeft, simplifiedRight)
          }
      }
    }

  // --- Private helpers ---

  /** Returns whether text is a valid rounding strategy name. */
  private def _isRoundingStrategy(text: String): Boolean =
    text == "nearest" || text == "up" || text == "down" || text == "to-zero"

  /** Rounds half away from zero, matching Dart's `double.round()` semantics.
    *
    * Dart: `(-1.5).round() == -2`, `(1.5).round() == 2`
    * Java: `Math.round(-1.5) == -1`, `Math.round(1.5) == 2`
    */
  private def _roundHalfAwayFromZero(x: Double): Double =
    if (x < 0) -math.round(-x).toDouble
    else math.round(x).toDouble

  /** Returns value coerced to number's units. */
  private def _matchUnits(value: Double, number: SassNumber): SassNumber =
    SassNumber.withUnits(
      value,
      numeratorUnits = number.numeratorUnits,
      denominatorUnits = number.denominatorUnits
    )

  /** Returns a rounded number based on a selected rounding strategy, to the nearest integer multiple of step.
    */
  private def _roundWithStep(
    strategy: String,
    number:   SassNumber,
    step:     SassNumber
  ): SassNumber = {
    if (!Set("nearest", "up", "down", "to-zero").contains(strategy)) {
      throw new IllegalArgumentException(
        s"$strategy must be either nearest, up, down or to-zero."
      )
    }

    if (
      (number.value.isInfinite && step.value.isInfinite) ||
      step.value == 0 ||
      number.value.isNaN ||
      step.value.isNaN
    ) {
      _matchUnits(Double.NaN, number)
    } else if (number.value.isInfinite) {
      number
    } else if (step.value.isInfinite) {
      (strategy, number.value) match {
        case (_, 0.0)                            => number
        case ("nearest" | "to-zero", v) if v > 0 => _matchUnits(0.0, number)
        case ("nearest" | "to-zero", _)          => _matchUnits(-0.0, number)
        case ("up", v) if v > 0                  => _matchUnits(Double.PositiveInfinity, number)
        case ("up", _)                           => _matchUnits(-0.0, number)
        case ("down", v) if v < 0                => _matchUnits(Double.NegativeInfinity, number)
        case ("down", _)                         => _matchUnits(0.0, number)
        case _                                   => throw new IllegalArgumentException(s"Invalid argument: $strategy.")
      }
    } else {
      val stepWithNumberUnit = step.convertValueToMatch(number)
      strategy match {
        case "nearest" =>
          _matchUnits(
            _roundHalfAwayFromZero(number.value / stepWithNumberUnit) * stepWithNumberUnit,
            number
          )
        case "up" =>
          _matchUnits(
            (if (step.value < 0) math.floor(number.value / stepWithNumberUnit)
             else math.ceil(number.value / stepWithNumberUnit)) * stepWithNumberUnit,
            number
          )
        case "down" =>
          _matchUnits(
            (if (step.value < 0) math.ceil(number.value / stepWithNumberUnit)
             else math.floor(number.value / stepWithNumberUnit)) * stepWithNumberUnit,
            number
          )
        case "to-zero" =>
          if (number.value < 0) {
            _matchUnits(
              math.ceil(number.value / stepWithNumberUnit) * stepWithNumberUnit,
              number
            )
          } else {
            _matchUnits(
              math.floor(number.value / stepWithNumberUnit) * stepWithNumberUnit,
              number
            )
          }
        case _ => _matchUnits(Double.NaN, number)
      }
    }
  }

  /** Returns a list of args, with each argument reduced via [[_simplify]]. */
  private def _simplifyArguments(args: Iterable[Any]): List[Any] =
    args.map(_simplify).toList

  /** Simplifies a calculation argument. */
  private def _simplify(arg: Any): Any = arg match {
    case _: SassNumber | _: CalculationOperation => arg
    case interp: CalculationInterpolation =>
      SassString(s"(${interp.value})", hasQuotes = false)
    case s: SassString if !s.hasQuotes => s
    case s: SassString                 =>
      throw SassScriptException(s"Quoted string $s can't be used in a calculation.")
    case calc: SassCalculation if calc.name == "calc" && calc.arguments.length == 1 =>
      calc.arguments.head match {
        case s: SassString if !s.hasQuotes && _needsParentheses(s.text) =>
          SassString(s"(${s.text})", hasQuotes = false)
        case singleArg => singleArg
      }
    case _: SassCalculation => arg
    case v: Value           =>
      throw SassScriptException(s"Value $v can't be used in a calculation.")
    case _ =>
      throw new IllegalArgumentException(s"Unexpected calculation argument $arg.")
  }

  /** Returns whether text needs parentheses if it's the contents of a `calc()` being embedded in another calculation.
    */
  private def _needsParentheses(text: String): Boolean =
    if (text.isEmpty) false
    else {
      val first = text.charAt(0).toInt
      if (_charNeedsParentheses(first)) true
      else {
        var couldBeVar = text.length >= 4 && CharCode.characterEqualsIgnoreCase(first, $v)

        if (text.length < 2) false
        else {
          val second = text.charAt(1).toInt
          if (_charNeedsParentheses(second)) true
          else {
            couldBeVar = couldBeVar && CharCode.characterEqualsIgnoreCase(second, $a)

            if (text.length < 3) false
            else {
              val third = text.charAt(2).toInt
              if (_charNeedsParentheses(third)) true
              else {
                couldBeVar = couldBeVar && CharCode.characterEqualsIgnoreCase(third, $r)

                if (text.length < 4) false
                else {
                  val fourth = text.charAt(3).toInt
                  if (couldBeVar && fourth == $lparen) true
                  else if (_charNeedsParentheses(fourth)) true
                  else {
                    boundary[Boolean] {
                      var i = 4
                      while (i < text.length) {
                        if (_charNeedsParentheses(text.charAt(i).toInt)) break(true)
                        i += 1
                      }
                      false
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

  /** Returns whether character intrinsically needs parentheses if it appears in the unquoted string argument of a `calc()` being embedded in another calculation.
    */
  private def _charNeedsParentheses(character: Int): Boolean =
    CharCode.isWhitespace(character) || character == $slash || character == $asterisk

  /** Verifies that all the numbers in args aren't known to be incompatible with one another, and that they don't have units that are too complex for calculations.
    */
  private def _verifyCompatibleNumbers(args: List[Any]): Unit = {
    // Note: this logic is largely duplicated in
    // _EvaluateVisitor._verifyCompatibleNumbers and most changes here should
    // also be reflected there.
    for (arg <- args)
      arg match {
        case n: SassNumber if n.hasComplexUnits =>
          throw SassScriptException(s"Number $n isn't compatible with CSS calculations.")
        case _ => ()
      }

    for (i <- args.indices.dropRight(1))
      args(i) match {
        case number1: SassNumber =>
          for (j <- (i + 1) until args.length)
            args(j) match {
              case number2: SassNumber =>
                if (!number1.hasPossiblyCompatibleUnits(number2)) {
                  throw SassScriptException(s"$number1 and $number2 are incompatible.")
                }
              case _ => ()
            }
        case _ => ()
      }
  }

  /** Throws a SassScriptException if args isn't expectedLength *and* doesn't contain a SassString.
    */
  private def _verifyLength(args: List[Any], expectedLength: Int): Unit =
    if (args.length == expectedLength) ()
    else if (args.exists(_.isInstanceOf[SassString])) ()
    else {
      val verb = Utils.pluralize("was", args.length, Nullable("were"))
      throw SassScriptException(
        s"$expectedLength arguments required, but only ${args.length} $verb passed."
      )
    }

  /** Returns a Value from calling a single-argument math function.
    *
    * If forbidUnits is true it will throw an error if argument has units.
    */
  private def _singleArgument(
    name:        String,
    argument:    Any,
    mathFunc:    SassNumber => SassNumber,
    forbidUnits: Boolean = false
  ): Value = {
    val simplified = _simplify(argument)
    simplified match {
      case number: SassNumber =>
        if (forbidUnits) number.assertNoUnits()
        mathFunc(number)
      case _ =>
        new SassCalculation(name, List(simplified))
    }
  }

  // --- Inline number_lib math functions ---

  private def _numberSqrt(number: SassNumber): SassNumber = {
    number.assertNoUnits("number")
    SassNumber(math.sqrt(number.value))
  }

  private def _numberSin(number: SassNumber): SassNumber =
    SassNumber(math.sin(number.coerceValueToUnit("rad", "number")))

  private def _numberCos(number: SassNumber): SassNumber =
    SassNumber(math.cos(number.coerceValueToUnit("rad", "number")))

  private def _numberTan(number: SassNumber): SassNumber =
    SassNumber(math.tan(number.coerceValueToUnit("rad", "number")))

  private def _numberAtan(number: SassNumber): SassNumber = {
    number.assertNoUnits("number")
    _radiansToDegrees(math.atan(number.value))
  }

  private def _numberAsin(number: SassNumber): SassNumber = {
    number.assertNoUnits("number")
    _radiansToDegrees(math.asin(number.value))
  }

  private def _numberAcos(number: SassNumber): SassNumber = {
    number.assertNoUnits("number")
    _radiansToDegrees(math.acos(number.value))
  }

  private def _numberAbs(number: SassNumber): SassNumber =
    SassNumber(number.value.abs).coerceToMatch(number)

  private def _numberLog(number: SassNumber, base: Nullable[SassNumber]): SassNumber =
    if (base.isDefined) {
      base.get.assertNoUnits()
      SassNumber(math.log(number.value) / math.log(base.get.value))
    } else {
      SassNumber(math.log(number.value))
    }

  private def _numberPow(base: SassNumber, exponent: SassNumber): SassNumber = {
    base.assertNoUnits("base")
    exponent.assertNoUnits("exponent")
    SassNumber(math.pow(base.value, exponent.value))
  }

  private def _numberAtan2(y: SassNumber, x: SassNumber): SassNumber =
    _radiansToDegrees(math.atan2(y.value, x.convertValueToMatch(y, "x", "y")))

  /** Returns radians as a SassNumber with unit `deg`. */
  private def _radiansToDegrees(radians: Double): SassNumber =
    SassNumber.withUnits(radians * (180.0 / math.Pi), numeratorUnits = List("deg"))
}

/** A binary operation that can appear in a SassCalculation. */
final case class CalculationOperation(
  operator: CalculationOperator,
  left:     Any,
  right:    Any
) {

  override def toString: String =
    // Basic toString; full serialization postponed until serializer is ported
    s"$left ${operator.operator} $right"
}

/** An enumeration of possible operators for CalculationOperation. */
enum CalculationOperator(
  val displayName: String,
  val operator:    String,
  val precedence:  Int
) extends java.lang.Enum[CalculationOperator] {

  /** The addition operator. */
  case Plus extends CalculationOperator("plus", "+", 1)

  /** The subtraction operator. */
  case Minus extends CalculationOperator("minus", "-", 1)

  /** The multiplication operator. */
  case Times extends CalculationOperator("times", "*", 2)

  /** The division operator. */
  case DividedBy extends CalculationOperator("divided by", "/", 2)

  override def toString: String = displayName
}

/** A deprecated representation of a string injected into a SassCalculation using interpolation.
  *
  * This only exists for backwards-compatibility with an older version of Dart Sass. It's now equivalent to creating a SassString whose value is wrapped in parentheses.
  */
@deprecated("Use SassString instead.", "always")
final class CalculationInterpolation(private val _value: String) {

  def value: String = _value

  override def equals(other: Any): Boolean = other match {
    case o: CalculationInterpolation => value == o.value
    case _ => false
  }

  override def hashCode(): Int = value.hashCode

  override def toString: String = value
}
