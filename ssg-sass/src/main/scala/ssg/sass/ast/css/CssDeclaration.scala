/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/css/declaration.dart, lib/src/ast/css/modifiable/declaration.dart
 * Original: Copyright (c) 2016, 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: declaration.dart + modifiable/declaration.dart -> CssDeclaration.scala
 *   Convention: Dart abstract interface class -> Scala trait
 *   Idiom: Value type from ssg.sass.value.Value; SassString check in constructor
 */
package ssg
package sass
package ast
package css

import ssg.sass.util.FileSpan
import ssg.sass.value.{ SassString, Value }
import ssg.sass.visitor.CssVisitor

/** A plain CSS declaration (that is, a `name: value` pair). */
trait CssDeclaration extends CssNode {

  /** The name of this declaration. */
  def name: CssValue[String]

  /** The value of this declaration. */
  def value: CssValue[Value]

  /** The span for value that should be emitted to the source map.
    *
    * When the declaration's expression is just a variable, this is the span where that variable was declared whereas value.span is the span where the variable was used. Otherwise, this is identical
    * to value.span.
    */
  def valueSpanForMap: FileSpan

  /** Returns whether this is a CSS Custom Property declaration. */
  def isCustomProperty: Boolean

  /** Whether this property's value was originally parsed as SassScript, as opposed to a custom property which is parsed as an interpolated sequence of tokens.
    *
    * If this is false, value will contain an unquoted SassString. isCustomProperty will usually be true, but there are other properties that may not be parsed as SassScript, like `return` in a plain
    * CSS `@function`.
    */
  def parsedAsSassScript: Boolean
}

/** A modifiable version of CssDeclaration for use in the evaluation step. */
final class ModifiableCssDeclaration(
  val name:               CssValue[String],
  val value:              CssValue[Value],
  val span:               FileSpan,
  val parsedAsSassScript: Boolean,
  valueSpanForMapOpt:     Option[FileSpan] = None
) extends ModifiableCssNode
    with CssDeclaration {

  val valueSpanForMap: FileSpan = valueSpanForMapOpt.getOrElse(value.span)

  def isCustomProperty: Boolean = name.value.startsWith("--")

  // Validate: if not parsed as SassScript, value must be a SassString
  if (!parsedAsSassScript) {
    require(
      value.value.isInstanceOf[SassString],
      s"If parsedAsSassScript is false, value must contain a SassString " +
        s"(was `${value}` of type ${value.value.getClass.getName})."
    )
  }

  def accept[T](visitor: CssVisitor[T]): T =
    visitor.visitCssDeclaration(this)

  override def toString: String = s"$name: $value;"
}
