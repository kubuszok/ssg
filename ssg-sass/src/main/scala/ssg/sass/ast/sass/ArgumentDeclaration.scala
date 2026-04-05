/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/sass/argument_list.dart,
 *              lib/src/ast/sass/parameter.dart,
 *              lib/src/ast/sass/parameter_list.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: argument_list.dart + parameter.dart + parameter_list.dart
 *            -> ArgumentDeclaration.scala
 *   Convention: Dart final class -> Scala final class
 *   Idiom: Nullable for optional fields; boundary/break not needed (no early return)
 */
package ssg
package sass
package ast
package sass

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.util.{FileSpan, initialIdentifier}

// ---------------------------------------------------------------------------
// ArgumentList — the arguments passed to a function or mixin invocation
// ---------------------------------------------------------------------------

/** A set of arguments passed in to a function or mixin.
  *
  * @param positional the arguments passed by position
  * @param named      the arguments passed by name
  * @param namedSpans the spans for named arguments, including their names
  * @param span       the source span
  * @param rest       the first rest argument (as in `$args...`)
  * @param keywordRest the second rest argument (keyword map only)
  */
final class ArgumentList(
  val positional: List[Expression],
  val named: Map[String, Expression],
  val namedSpans: Map[String, FileSpan],
  val span: FileSpan,
  val rest: Nullable[Expression] = Nullable.empty,
  val keywordRest: Nullable[Expression] = Nullable.empty
) extends SassNode {

  assert(rest.isDefined || keywordRest.isEmpty)

  /** Returns whether this invocation passes no arguments. */
  def isEmpty: Boolean =
    positional.isEmpty && named.isEmpty && rest.isEmpty

  override def toString: String = {
    val components = scala.collection.mutable.ListBuffer[String]()
    for (arg <- positional) {
      components += _parenthesizeArgument(arg)
    }
    for ((name, value) <- named) {
      components += s"$$$name: ${_parenthesizeArgument(value)}"
    }
    rest.foreach { r =>
      components += s"${_parenthesizeArgument(r)}..."
    }
    keywordRest.foreach { kr =>
      components += s"${_parenthesizeArgument(kr)}..."
    }
    s"(${components.mkString(", ")})"
  }

  /** Wraps [argument] in parentheses if necessary. */
  private def _parenthesizeArgument(argument: Expression): String =
    argument match {
      case l: ListExpression
        if l.separator == ssg.sass.value.ListSeparator.Comma &&
           !l.hasBrackets && l.contents.length >= 2 =>
        s"($argument)"
      case _ =>
        argument.toString
    }
}

object ArgumentList {

  /** Creates an invocation that passes no arguments. */
  def empty(span: FileSpan): ArgumentList =
    new ArgumentList(Nil, Map.empty, Map.empty, span)
}

// ---------------------------------------------------------------------------
// Parameter — a single parameter in a ParameterList
// ---------------------------------------------------------------------------

/** A parameter declared as part of a [ParameterList].
  *
  * @param name         the parameter name
  * @param span         the source span
  * @param defaultValue the default value, or empty if none was declared
  */
final class Parameter(
  val name: String,
  val span: FileSpan,
  val defaultValue: Nullable[Expression] = Nullable.empty
) extends SassNode with SassDeclaration {

  /** The variable name as written in the document, without underscores
    * converted to hyphens and including the leading `$`.
    */
  def originalName: String =
    if (defaultValue.isEmpty) span.text
    else span.text // simplified; full version uses declarationName

  def nameSpan: FileSpan =
    if (defaultValue.isEmpty) span
    else span.initialIdentifier(includeLeading = 1)

  override def toString: String =
    defaultValue.fold(name)(dv => s"$name: $dv")
}

// ---------------------------------------------------------------------------
// ParameterList — parameter declaration for a function or mixin
// ---------------------------------------------------------------------------

/** A parameter declaration, as for a function or mixin definition.
  *
  * @param parameters    the parameters that are taken
  * @param span          the source span
  * @param restParameter the name of the rest parameter (as in `$args...`), or empty
  */
final class ParameterList(
  val parameters: List[Parameter],
  val span: FileSpan,
  val restParameter: Nullable[String] = Nullable.empty
) extends SassNode {

  /** Returns whether this declaration takes no parameters. */
  def isEmpty: Boolean = parameters.isEmpty && restParameter.isEmpty

  /** Returns [span] expanded to include an identifier immediately before the
    * declaration, if possible.
    */
  def spanWithName: FileSpan = {
    val text = span.file.getText(0)
    // Move backwards through any whitespace between the name and the parameters.
    var i = span.start.offset - 1
    while (i > 0 && Character.isWhitespace(text.charAt(i))) {
      i -= 1
    }
    // Then move backwards through the name itself.
    if (i < 0 || !isName(text.charAt(i))) {
      span
    } else {
      i -= 1
      while (i >= 0 && isName(text.charAt(i))) {
        i -= 1
      }
      // If the name didn't start with isNameStart, it's not a valid identifier.
      if (!isNameStart(text.charAt(i + 1))) span
      else span.file.span(i + 1, span.end.offset).trim()
    }
  }

  /** Returns whether [positional] and [names] are valid for this parameter
    * declaration.
    */
  def matches(positional: Int, names: Set[String]): Boolean = {
    var namedUsed = 0
    var i = 0
    while (i < parameters.length) {
      val parameter = parameters(i)
      if (i < positional) {
        if (names.contains(parameter.name)) {
          return false // scalastyle:ignore (only in matches())
        }
      } else if (names.contains(parameter.name)) {
        namedUsed += 1
      } else if (parameter.defaultValue.isEmpty) {
        return false // scalastyle:ignore
      }
      i += 1
    }
    if (restParameter.isDefined) true
    else if (positional > parameters.length) false
    else namedUsed >= names.size
  }

  override def toString: String = {
    val parts = scala.collection.mutable.ListBuffer[String]()
    for (p <- parameters) {
      parts += s"$$$p"
    }
    restParameter.foreach { rp =>
      parts += s"$$$rp..."
    }
    parts.mkString(", ")
  }

  // Character classification helpers (subset of Sass identifier rules)
  private def isName(c: Char): Boolean =
    c == '_' || c == '-' || c.isLetterOrDigit

  private def isNameStart(c: Char): Boolean =
    c == '_' || c.isLetter
}

object ParameterList {

  /** Creates a declaration that declares no parameters. */
  def empty(span: FileSpan): ParameterList =
    new ParameterList(Nil, span)
}
