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

import ssg.sass.{SassScriptException, Nullable}
import ssg.sass.Nullable.*
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
    val index = sassIndex.assertNumber(name).assertInt(name)
    if (index == 0) throw SassScriptException("List index may not be 0.", name.toOption)
    val len = lengthAsList
    if (index.abs > len) {
      throw SassScriptException(
        s"Invalid index $index for a list with $len elements.",
        name.toOption
      )
    }
    if (index < 0) len + index else index - 1
  }

  /** Asserts this is a SassBoolean. */
  def assertBoolean(name: Nullable[String] = Nullable.Null): SassBoolean =
    throw SassScriptException(s"$this is not a boolean.", name.toOption)

  /** Asserts this is a SassColor. */
  def assertColor(name: Nullable[String] = Nullable.Null): Value =
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

  /** Asserts this is a SassNumber. Returns Value until SassNumber is ported in Phase 3b. */
  def assertNumber(name: Nullable[String] = Nullable.Null): Value =
    throw SassScriptException(s"$this is not a number.", name.toOption)

  /** Asserts this is a SassString. */
  def assertString(name: Nullable[String] = Nullable.Null): SassString =
    throw SassScriptException(s"$this is not a string.", name.toOption)

  /** Asserts this is a SassCalculation. */
  def assertCalculation(name: Nullable[String] = Nullable.Null): Value =
    throw SassScriptException(s"$this is not a calculation.", name.toOption)

  /** Asserts this value is an integer. Placeholder until SassNumber Phase 3b. */
  def assertInt(name: Nullable[String] = Nullable.Null): Int =
    throw SassScriptException(s"$this is not an int.", name.toOption)

  /** Creates a new SassList with the same separator and brackets but different contents. */
  def withListContents(
    contents: Iterable[Value],
    separator: Nullable[ListSeparator] = Nullable.Null,
    brackets: Nullable[Boolean] = Nullable.Null
  ): SassList = {
    SassList(
      contents.toList,
      separator.getOrElse(this.separator),
      brackets = brackets.getOrElse(this.hasBrackets)
    )
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
    case _ =>
      SassString(s"${toCssString()}${other.toCssString()}", hasQuotes = false)
  }

  def minus(other: Value): Value =
    SassString(s"${toCssString()}-${other.toCssString()}", hasQuotes = false)

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
  def toCssString(quote: Boolean = true): String = {
    // Default: use the serializer. For now, use toString as fallback.
    toString
  }
}
