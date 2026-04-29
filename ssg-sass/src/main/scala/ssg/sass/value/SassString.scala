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
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/value/string.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
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
      lower.startsWith("attr(") || lower.startsWith("if(")
    }

  override def isSpecialVariable: Boolean =
    if (hasQuotes) false
    else {
      val lower = text.toLowerCase
      lower.startsWith("attr(") || lower.startsWith("if(") ||
      lower.startsWith("var(")
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

  /** Converts a 1-based Sass index to a 0-based code unit index.
    */
  def sassIndexToStringIndex(sassIndex: Value, name: Nullable[String] = Nullable.Null): Int = {
    val codepointIndex = sassIndexToRuneIndex(sassIndex, name)
    ssg.sass.Utils.codepointIndexToCodeUnitIndex(text, codepointIndex)
  }

  /** Converts a 1-based Sass index to a 0-based rune (codepoint) index.
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
      SassString(text + s.text, hasQuotes)
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

  /** CSS representation of this string. For unquoted strings, newlines are folded to spaces and post-newline whitespace is collapsed (matching dart-sass `_visitUnquotedString`). For quoted strings
    * dart-sass prefers double quotes, falling back to single quotes when the text contains a double quote and no single quote (see `_visitQuotedString` in `serialize.dart`). Control characters are
    * escaped with `\hh ` hex form; `\`, and the active quote char are backslash-escaped.
    */
  override def toCssString(quote: Boolean = true): String =
    if (!hasQuotes || !quote) foldNewlines(text)
    else {
      var hasDouble = false
      var hasSingle = false
      var i         = 0
      while (i < text.length) {
        val c = text.charAt(i)
        if (c == '"') hasDouble = true
        else if (c == '\'') hasSingle = true
        i += 1
      }
      val q  = if (hasDouble && !hasSingle) '\'' else '"'
      val sb = new StringBuilder()
      sb.append(q)
      i = 0
      while (i < text.length) {
        val c = text.charAt(i)
        c match {
          case '\\'                       => sb.append("\\\\")
          case _ if c == q                => sb.append('\\'); sb.append(c)
          case _ if c < 0x20 || c == 0x7f =>
            // Hex escape with trailing space terminator (dart-sass uses
            // lowercase hex and a single trailing space to disambiguate
            // from following hex-looking characters).
            sb.append('\\')
            sb.append(Integer.toHexString(c.toInt))
            sb.append(' ')
          case _ => sb.append(c)
        }
        i += 1
      }
      sb.append(q)
      sb.toString()
    }

  /** Fold newlines to a single space and collapse post-newline whitespace, matching dart-sass `_visitUnquotedString` (serialize.dart:1452-1473). Returns [s] unchanged if it contains no newlines.
    */
  private def foldNewlines(s: String): String = {
    if (s.indexOf('\n') < 0) return s
    val sb           = new StringBuilder(s.length)
    var afterNewline = false
    var i            = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (c == '\n') {
        sb.append(' ')
        afterNewline = true
      } else if (c == ' ' && afterNewline) {
        // skip: collapse post-newline spaces
      } else {
        afterNewline = false
        sb.append(c)
      }
      i += 1
    }
    sb.toString()
  }

  override def toString: String = toCssString()
}

object SassString {
  private val emptyQuoted   = new SassString("", hasQuotes = true)
  private val emptyUnquoted = new SassString("", hasQuotes = false)

  def empty(quotes: Boolean = true): SassString =
    if (quotes) emptyQuoted else emptyUnquoted
}
