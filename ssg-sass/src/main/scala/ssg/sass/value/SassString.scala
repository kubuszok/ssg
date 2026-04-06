/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/string.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: string.dart → SassString.scala
 *   Convention: Quoted/unquoted distinction preserved
 *   Idiom: sassLength uses codePointCount for Unicode awareness
 */
package ssg
package sass
package value

import ssg.sass.{ Nullable, SassScriptException }
import ssg.sass.Nullable.*
import ssg.sass.visitor.ValueVisitor

import scala.language.implicitConversions

/** A SassScript string value. */
final class SassString(val text: String, val hasQuotes: Boolean = true) extends Value {

  /** Unicode code point length (not UTF-16 code unit length). */
  lazy val sassLength: Int = text.codePointCount(0, text.length)

  private var _hashCache:    Int     = 0
  private var _hashComputed: Boolean = false

  override def isBlank: Boolean = !hasQuotes && text.isEmpty

  override def isSpecialNumber: Boolean =
    if (hasQuotes) false
    else {
      val lower = text.toLowerCase
      lower.startsWith("calc(") || lower.startsWith("var(") ||
      lower.startsWith("env(") || lower.startsWith("min(") ||
      lower.startsWith("max(") || lower.startsWith("clamp(") ||
      lower.startsWith("attr(")
    }

  override def isSpecialVariable: Boolean =
    if (hasQuotes) false
    else {
      val lower = text.toLowerCase
      lower.startsWith("var(") || lower.startsWith("env(")
    }

  override def accept[T](visitor: ValueVisitor[T]): T = visitor.visitString(this)

  override def assertString(name: Nullable[String]): SassString = this

  /** Throws if this string is unquoted. */
  def assertQuoted(name: Nullable[String] = Nullable.Null): Unit =
    if (!hasQuotes) {
      throw SassScriptException(s"Expected $this to be quoted.", name.toOption)
    }

  /** Throws if this string is quoted. */
  def assertUnquoted(name: Nullable[String] = Nullable.Null): Unit =
    if (hasQuotes) {
      throw SassScriptException(s"Expected $this to be unquoted.", name.toOption)
    }

  /** Converts a 1-based Sass index to a 0-based code unit index. Placeholder until SassNumber is ported in Phase 3b.
    */
  def sassIndexToStringIndex(sassIndex: Value, name: Nullable[String] = Nullable.Null): Int = {
    val codepointIndex = sassIndexToRuneIndex(sassIndex, name)
    ssg.sass.Utils.codepointIndexToCodeUnitIndex(text, codepointIndex)
  }

  /** Converts a 1-based Sass index to a 0-based rune (codepoint) index. Placeholder until SassNumber is ported in Phase 3b.
    */
  def sassIndexToRuneIndex(sassIndex: Value, name: Nullable[String] = Nullable.Null): Int = {
    val index = sassIndex.assertNumber(name).assertInt(name)
    if (index == 0) throw SassScriptException("String index may not be 0.", name.toOption)
    if (index.abs > sassLength) {
      throw SassScriptException(
        s"Invalid index $index for a string with $sassLength characters.",
        name.toOption
      )
    }
    if (index < 0) sassLength + index else index - 1
  }

  override def plus(other: Value): Value = other match {
    case s: SassString =>
      SassString(text + s.text, hasQuotes || s.hasQuotes)
    case _ =>
      SassString(text + other.toCssString(), hasQuotes)
  }

  override def hashCode(): Int = {
    if (!_hashComputed) {
      _hashCache = text.hashCode
      _hashComputed = true
    }
    _hashCache
  }

  override def equals(other: Any): Boolean = other match {
    case that: SassString => this.text == that.text
    case _ => false
  }

  override def toString: String =
    if (hasQuotes) s"\"$text\""
    else text
}

object SassString {
  private val emptyQuoted   = new SassString("", hasQuotes = true)
  private val emptyUnquoted = new SassString("", hasQuotes = false)

  def empty(quotes: Boolean = true): SassString =
    if (quotes) emptyQuoted else emptyUnquoted
}
