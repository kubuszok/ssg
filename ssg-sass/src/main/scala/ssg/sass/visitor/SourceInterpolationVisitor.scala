/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/source_interpolation.dart
 * Original: Copyright (c) 2024 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: source_interpolation.dart -> SourceInterpolationVisitor.scala
 *   Convention: Dart ExpressionVisitor<void> -> Scala ExpressionVisitor[Unit]
 *   Idiom: nullable buffer is Nullable[InterpolationBuffer]; visitor methods
 *          return Unit and mutate buffer. Caller reads buffer after accept().
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/visitor/source_interpolation.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass
package visitor

import ssg.sass.{ InterpolationBuffer, Nullable }
import ssg.sass.Nullable.*
import ssg.sass.ast.AstNode
import ssg.sass.ast.sass.*
import ssg.sass.util.FileSpan

import scala.util.boundary
import scala.util.boundary.break

/** A visitor that builds an [[Interpolation]] that evaluates to the same text as the given expression.
  *
  * This should be used through [Expression.sourceInterpolation].
  */
final class SourceInterpolationVisitor extends ExpressionVisitor[Unit] with IfConditionExpressionVisitor[Unit] {

  /** The buffer to which content is added each time this visitor visits an expression.
    *
    * This is set to empty if the visitor encounters a node that's not valid CSS with interpolations.
    */
  var buffer: Nullable[InterpolationBuffer] = Nullable(new InterpolationBuffer())

  def visitBinaryOperationExpression(node: BinaryOperationExpression): Unit =
    buffer = Nullable.empty

  def visitBooleanExpression(node: BooleanExpression): Unit =
    buffer = Nullable.empty

  def visitColorExpression(node: ColorExpression): Unit =
    buffer.foreach(_.write(node.span.text))

  def visitFunctionExpression(node: FunctionExpression): Unit =
    buffer = Nullable.empty

  def visitInterpolatedFunctionExpression(node: InterpolatedFunctionExpression): Unit = {
    buffer.foreach(_.addInterpolation(node.name))
    _visitArguments(node.arguments)
  }

  /** Visits the positional arguments in [arguments], if it's valid interpolated plain CSS.
    */
  private def _visitArguments(arguments: ArgumentList): Unit = {
    if (arguments.named.nonEmpty || arguments.rest.isDefined) return

    if (arguments.positional.isEmpty) {
      buffer.foreach(_.write(arguments.span.text))
      return
    }

    buffer.foreach(_.write(arguments.span.before(arguments.positional.head.span).text))
    _writeListAndBetween(arguments.positional, (node: Expression) => node.accept(this))
    buffer.foreach(_.write(arguments.span.after(arguments.positional.last.span).text))
  }

  def visitIfExpression(node: IfExpression): Unit = {
    var lastSpan: Nullable[FileSpan] = Nullable.empty
    for ((condition, expression) <- node.branches) {
      val firstSpan   = if (condition.isDefined) condition.get.span else expression.span
      val betweenText =
        if (lastSpan.isDefined) lastSpan.get.between(firstSpan).text
        else node.span.before(firstSpan).text
      buffer.foreach(_.write(betweenText))

      if (condition.isDefined) {
        condition.get.accept(this)
        buffer.foreach(_.write(condition.get.span.between(expression.span).text))
      }

      expression.accept(this)
      lastSpan = Nullable(expression.span)
    }
  }

  def visitIfConditionParenthesized(node: IfConditionParenthesized): Unit = {
    buffer.foreach(_.write(node.span.before(node.expression.span).text))
    node.expression.accept(this)
    buffer.foreach(_.write(node.span.after(node.expression.span).text))
  }

  def visitIfConditionNegation(node: IfConditionNegation): Unit = {
    buffer.foreach(_.write(node.span.before(node.expression.span).text))
    node.expression.accept(this)
  }

  def visitIfConditionOperation(node: IfConditionOperation): Unit =
    _writeListAndBetween(node.expressions, (n: IfConditionExpression) => n.accept(this))

  def visitIfConditionFunction(node: IfConditionFunction): Unit = {
    buffer.foreach(_.addInterpolation(node.name))
    buffer.foreach(_.write(node.name.span.between(node.arguments.span).text))
    buffer.foreach(_.addInterpolation(node.arguments))
    buffer.foreach(_.write(node.span.after(node.arguments.span).text))
  }

  def visitIfConditionSass(node: IfConditionSass): Unit =
    buffer = Nullable.empty

  def visitIfConditionRaw(node: IfConditionRaw): Unit =
    buffer.foreach(_.addInterpolation(node.text))

  def visitLegacyIfExpression(node: LegacyIfExpression): Unit =
    buffer = Nullable.empty

  def visitListExpression(node: ListExpression): Unit = {
    if (node.contents.length <= 1 && !node.hasBrackets) {
      buffer = Nullable.empty
      return
    }

    if (node.hasBrackets && node.contents.isEmpty) {
      buffer.foreach(_.write(node.span.text))
      return
    }

    if (node.hasBrackets) {
      buffer.foreach(_.write(node.span.before(node.contents.head.span).text))
    }
    _writeListAndBetween(node.contents, (n: Expression) => n.accept(this))

    if (node.hasBrackets) {
      buffer.foreach(_.write(node.span.after(node.contents.last.span).text))
    }
  }

  def visitMapExpression(node: MapExpression): Unit =
    buffer = Nullable.empty

  def visitNullExpression(node: NullExpression): Unit =
    buffer = Nullable.empty

  def visitNumberExpression(node: NumberExpression): Unit =
    buffer.foreach(_.write(node.span.text))

  def visitParenthesizedExpression(node: ParenthesizedExpression): Unit =
    buffer = Nullable.empty

  def visitSelectorExpression(node: SelectorExpression): Unit =
    buffer = Nullable.empty

  def visitStringExpression(node: StringExpression): Unit = {
    if (node.text.isPlain) {
      buffer.foreach(_.write(node.span.text))
      return
    }

    var i = 0
    while (i < node.text.contents.length) {
      val elemSpan = node.text.spanForElement(i)
      node.text.contents(i) match {
        case _: Expression =>
          val expression = node.text.contents(i).asInstanceOf[Expression]
          if (i == 0) buffer.foreach(_.write(node.span.before(elemSpan).text))
          buffer.foreach(_.add(expression, elemSpan))
          if (i == node.text.contents.length - 1) {
            buffer.foreach(_.write(node.span.after(elemSpan).text))
          }
        case _ =>
          buffer.foreach(_.write(elemSpan.text))
      }
      i += 1
    }
  }

  def visitSupportsExpression(node: SupportsExpression): Unit =
    buffer = Nullable.empty

  def visitUnaryOperationExpression(node: UnaryOperationExpression): Unit =
    buffer = Nullable.empty

  def visitValueExpression(node: ValueExpression): Unit =
    buffer = Nullable.empty

  def visitVariableExpression(node: VariableExpression): Unit =
    buffer = Nullable.empty

  /** Visits each expression in [nodes] with [visit], and writes whatever text is between them to [buffer].
    */
  private def _writeListAndBetween[T <: AstNode](
    nodes: List[T],
    visit: T => Unit
  ): Unit = boundary {
    var lastSpan: Nullable[FileSpan] = Nullable.empty
    for (node <- nodes) {
      if (lastSpan.isDefined) {
        buffer.foreach(_.write(lastSpan.get.between(node.span).text))
      }
      visit(node)
      if (buffer.isEmpty) break(())
      lastSpan = Nullable(node.span)
    }
  }
}
