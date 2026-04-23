/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: value.dart → Value.scala
 *   Convention: Dart abstract class → Scala abstract class
 *   Idiom: assert* methods throw SassScriptException; selector methods deferred
 */
package ssg
package sass
package value

import ssg.sass.{ Deprecation, EvaluationContext, Nullable, SassFormatException, SassScriptException }
import ssg.sass.Nullable.*
import ssg.sass.ast.selector.{ ComplexSelector, CompoundSelector, SelectorList, SimpleSelector }
import ssg.sass.parse.SelectorParser
import ssg.sass.visitor.ValueVisitor

import scala.language.implicitConversions

/** Base class for all SassScript values. */
abstract class Value {

  /** Whether this value is truthy (everything except false and null). */
  def isTruthy: Boolean = true

  /** The separator for this value when treated as a list. */
  def separator: ListSeparator = ListSeparator.Undecided

  /** Whether this value has brackets when treated as a list. */
  def hasBrackets: Boolean = false

  /** This value as a Sass list. */
  def asList: List[Value] = List(this)

  /** The number of elements in this value when treated as a list. */
  def lengthAsList: Int = 1

  /** Whether this value is blank (empty unquoted string or empty list). */
  def isBlank: Boolean = false

  /** Whether this is a special CSS number function like calc(). */
  def isSpecialNumber: Boolean = false

  /** Whether this is a special CSS variable function. */
  def isSpecialVariable: Boolean = false

  /** Returns Nullable.Null for SassNull, this for everything else. */
  def realNull: Nullable[Value] = this

  /** Accepts a ValueVisitor. */
  def accept[T](visitor: ValueVisitor[T]): T

  /** Converts a 1-based Sass index to a 0-based list index. */
  def sassIndexToListIndex(sassIndex: Value, name: Nullable[String] = Nullable.Null): Int = {
    val indexValue = sassIndex.assertNumber(name)
    if (indexValue.hasUnits) {
      EvaluationContext.warnForDeprecation(
        Deprecation.FunctionUnits,
        s"$$${name.getOrElse("index")}: Passing a number with unit ${indexValue.unitString} is " +
          "deprecated.\n" +
          "\n" +
          s"To preserve current behavior: ${indexValue.unitSuggestion(name.getOrElse("index"))}\n" +
          "\n" +
          "More info: https://sass-lang.com/d/function-units"
      )
    }
    val index = indexValue.assertInt(name)
    if (index == 0) throw SassScriptException("List index may not be 0.", name.toOption)
    val len = lengthAsList
    if (index.abs > len) {
      throw SassScriptException(
        s"Invalid index $sassIndex for a list with $len elements.",
        name.toOption
      )
    }
    if (index < 0) len + index else index - 1
  }

  /** Asserts this is a SassBoolean. */
  def assertBoolean(name: Nullable[String] = Nullable.Null): SassBoolean =
    throw SassScriptException(s"$this is not a boolean.", name.toOption)

  /** Asserts this is a SassColor. */
  def assertColor(name: Nullable[String] = Nullable.Null): SassColor =
    throw SassScriptException(s"$this is not a color.", name.toOption)

  /** Asserts this is a SassFunction. */
  def assertFunction(name: Nullable[String] = Nullable.Null): SassFunction =
    throw SassScriptException(s"$this is not a function reference.", name.toOption)

  /** Asserts this is a SassMixin. */
  def assertMixin(name: Nullable[String] = Nullable.Null): SassMixin =
    throw SassScriptException(s"$this is not a mixin reference.", name.toOption)

  /** Asserts this is a SassMap. */
  def assertMap(name: Nullable[String] = Nullable.Null): SassMap =
    throw SassScriptException(s"$this is not a map.", name.toOption)

  /** Tries to interpret this as a map. Returns None by default. */
  def tryMap(): Option[SassMap] = None

  /** Asserts this is a SassNumber. */
  def assertNumber(name: Nullable[String] = Nullable.Null): SassNumber =
    throw SassScriptException(s"$this is not a number.", name.toOption)

  /** Asserts this is a SassString. */
  def assertString(name: Nullable[String] = Nullable.Null): SassString =
    throw SassScriptException(s"$this is not a string.", name.toOption)

  /** Asserts this is a SassCalculation. */
  def assertCalculation(name: Nullable[String] = Nullable.Null): SassCalculation =
    throw SassScriptException(s"$this is not a calculation.", name.toOption)

  /** Asserts this value is an integer. */
  def assertInt(name: Nullable[String] = Nullable.Null): Int =
    throw SassScriptException(s"$this is not an int.", name.toOption)

  /** Creates a new SassList with the same separator and brackets but different contents. */
  def withListContents(
    contents:  Iterable[Value],
    separator: Nullable[ListSeparator] = Nullable.Null,
    brackets:  Nullable[Boolean] = Nullable.Null
  ): SassList =
    SassList(
      contents.toList,
      separator.getOrElse(this.separator),
      brackets = brackets.getOrElse(this.hasBrackets)
    )

  /** Asserts that this value is a space- (or slash-) separated list with no brackets, throwing a SassScriptException if it isn't.
    *
    * Returns the list contents. Ported from dart-sass `Value.assertCommonListStyle`.
    */
  def assertCommonListStyle(name: Nullable[String], allowSlash: Boolean): List[Value] = {
    val invalidSeparator = separator == ListSeparator.Comma ||
      (!allowSlash && separator == ListSeparator.Slash)
    if (!invalidSeparator && !hasBrackets) asList
    else {
      val buffer = new StringBuilder("Expected")
      if (hasBrackets) buffer.append(" an unbracketed")
      if (invalidSeparator) {
        if (hasBrackets) buffer.append(",") else buffer.append(" a")
        buffer.append(" space-")
        if (allowSlash) buffer.append(" or slash-")
        buffer.append("separated")
      }
      buffer.append(" list, was ")
      buffer.append(this.toString)
      throw SassScriptException(buffer.toString(), name.toOption)
    }
  }

  // ---------------------------------------------------------------------------
  // Selector assertion methods
  // ---------------------------------------------------------------------------

  /** Converts a `selector-parse()`-style input into a string that can be parsed.
    *
    * Throws a [[SassScriptException]] if `this` isn't a type or a structure that can be parsed as a selector.
    */
  private def selectorString(name: Nullable[String]): String = {
    val s = selectorStringOrNull()
    if (s.isEmpty) {
      throw SassScriptException(
        s"$this is not a valid selector: it must be a string,\n" +
          "a list of strings, or a list of lists of strings.",
        name.toOption
      )
    }
    s.get
  }

  /** Converts a `selector-parse()`-style input into a string that can be parsed.
    *
    * Returns `Nullable.Null` if `this` isn't a type or a structure that can be parsed as a selector.
    */
  private def selectorStringOrNull(): Nullable[String] =
    this match {
      case str:  SassString => Nullable(str.text)
      case list: SassList   =>
        if (list.asList.isEmpty) Nullable.Null
        else {
          val result = scala.collection.mutable.ListBuffer.empty[String]
          list.separator match {
            case ListSeparator.Comma =>
              val it     = list.asList.iterator
              var failed = false
              while (it.hasNext && !failed)
                it.next() match {
                  case s: SassString =>
                    result += s.text
                  case inner: SassList if inner.separator == ListSeparator.Space =>
                    val s = (inner: Value).selectorStringOrNull()
                    if (s.isEmpty) failed = true
                    else result += s.get
                  case _ =>
                    failed = true
                }
              if (failed) Nullable.Null
              else Nullable(result.mkString(", "))
            case ListSeparator.Slash =>
              Nullable.Null
            case _ =>
              // Space or undecided separator
              val it     = list.asList.iterator
              var failed = false
              while (it.hasNext && !failed)
                it.next() match {
                  case s: SassString =>
                    result += s.text
                  case _ =>
                    failed = true
                }
              if (failed) Nullable.Null
              else Nullable(result.mkString(" "))
          }
        }
      case _ => Nullable.Null
    }

  /** Parses `this` as a selector list, in the same manner as the `selector-parse()` function.
    *
    * Throws a [[SassScriptException]] if this isn't a type that can be parsed as a selector, or if parsing fails. If [allowParent] is `true`, this allows [[ssg.sass.ast.selector.ParentSelector]]s.
    * Otherwise, they're considered parse errors.
    *
    * If this came from a function argument, [name] is the argument name (without the `$`). It's used for error reporting.
    */
  def assertSelector(
    name:        Nullable[String] = Nullable.Null,
    allowParent: Boolean = false
  ): SelectorList = {
    val string = selectorString(name)
    try
      new SelectorParser(string, allowParent = allowParent).parse()
    catch {
      case error: SassFormatException =>
        // Note(nweiz): error messages are not colorized yet (no terminal
        // capability detection).
        throw SassScriptException(
          error.toString.replaceFirst("Error: ", ""),
          name.toOption
        )
    }
  }

  /** Parses `this` as a simple selector, in the same manner as the `selector-parse()` function.
    *
    * Throws a [[SassScriptException]] if this isn't a type that can be parsed as a selector, or if parsing fails. If [allowParent] is `true`, this allows [[ssg.sass.ast.selector.ParentSelector]]s.
    * Otherwise, they're considered parse errors.
    *
    * If this came from a function argument, [name] is the argument name (without the `$`). It's used for error reporting.
    */
  def assertSimpleSelector(
    name:        Nullable[String] = Nullable.Null,
    allowParent: Boolean = false
  ): SimpleSelector = {
    val string = selectorString(name)
    try
      new SelectorParser(string, allowParent = allowParent).parseSimpleSelector()
    catch {
      case error: SassFormatException =>
        // Note(nweiz): error messages are not colorized yet (no terminal
        // capability detection).
        throw SassScriptException(
          error.toString.replaceFirst("Error: ", ""),
          name.toOption
        )
    }
  }

  /** Parses `this` as a compound selector, in the same manner as the `selector-parse()` function.
    *
    * Throws a [[SassScriptException]] if this isn't a type that can be parsed as a selector, or if parsing fails. If [allowParent] is `true`, this allows [[ssg.sass.ast.selector.ParentSelector]]s.
    * Otherwise, they're considered parse errors.
    *
    * If this came from a function argument, [name] is the argument name (without the `$`). It's used for error reporting.
    */
  def assertCompoundSelector(
    name:        Nullable[String] = Nullable.Null,
    allowParent: Boolean = false
  ): CompoundSelector = {
    val string = selectorString(name)
    try
      new SelectorParser(string, allowParent = allowParent).parseCompoundSelector()
    catch {
      case error: SassFormatException =>
        // Note(nweiz): error messages are not colorized yet (no terminal
        // capability detection).
        throw SassScriptException(
          error.toString.replaceFirst("Error: ", ""),
          name.toOption
        )
    }
  }

  /** Parses `this` as a complex selector, in the same manner as the `selector-parse()` function.
    *
    * Throws a [[SassScriptException]] if this isn't a type that can be parsed as a selector, or if parsing fails. If [allowParent] is `true`, this allows [[ssg.sass.ast.selector.ParentSelector]]s.
    * Otherwise, they're considered parse errors.
    *
    * If this came from a function argument, [name] is the argument name (without the `$`). It's used for error reporting.
    */
  def assertComplexSelector(
    name:        Nullable[String] = Nullable.Null,
    allowParent: Boolean = false
  ): ComplexSelector = {
    val string = selectorString(name)
    try
      new SelectorParser(string, allowParent = allowParent).parseComplexSelector()
    catch {
      case error: SassFormatException =>
        // Note(nweiz): error messages are not colorized yet (no terminal
        // capability detection).
        throw SassScriptException(
          error.toString.replaceFirst("Error: ", ""),
          name.toOption
        )
    }
  }

  /** SassScript = operator (not ==). Returns unquoted string "left=right". */
  def singleEquals(other: Value): Value =
    SassString(s"${toCssString()}=${other.toCssString()}", hasQuotes = false)

  // --- Arithmetic operators (default: throw) ---

  def greaterThan(other: Value): SassBoolean =
    throw SassScriptException(s"Undefined operation \"$this > $other\".")

  def greaterThanOrEquals(other: Value): SassBoolean =
    throw SassScriptException(s"Undefined operation \"$this >= $other\".")

  def lessThan(other: Value): SassBoolean =
    throw SassScriptException(s"Undefined operation \"$this < $other\".")

  def lessThanOrEquals(other: Value): SassBoolean =
    throw SassScriptException(s"Undefined operation \"$this <= $other\".")

  def times(other: Value): Value =
    throw SassScriptException(s"Undefined operation \"$this * $other\".")

  def modulo(other: Value): Value =
    throw SassScriptException(s"Undefined operation \"$this % $other\".")

  def plus(other: Value): Value = other match {
    case s: SassString =>
      SassString(s"${toCssString()}${s.text}", hasQuotes = s.hasQuotes)
    case _: SassCalculation =>
      throw SassScriptException(s"""Undefined operation "$this + $other".""")
    case _ =>
      SassString(s"${toCssString()}${other.toCssString()}", hasQuotes = false)
  }

  def minus(other: Value): Value = other match {
    case _: SassCalculation =>
      throw SassScriptException(s"""Undefined operation "$this - $other".""")
    case _ =>
      SassString(s"${toCssString()}-${other.toCssString()}", hasQuotes = false)
  }

  def dividedBy(other: Value): Value =
    SassString(s"${toCssString()}/${other.toCssString()}", hasQuotes = false)

  def unaryPlus(): Value =
    SassString(s"+${toCssString()}", hasQuotes = false)

  def unaryMinus(): Value =
    SassString(s"-${toCssString()}", hasQuotes = false)

  def unaryDivide(): Value =
    SassString(s"/${toCssString()}", hasQuotes = false)

  def unaryNot(): Value = SassBoolean.sassFalse

  /** Returns this value without any slash. Default: returns this. */
  def withoutSlash: Value = this

  /** Returns the CSS string representation. */
  def toCssString(quote: Boolean = true): String =
    // Default: use the serializer. Currently, use toString as fallback.
    toString
}
