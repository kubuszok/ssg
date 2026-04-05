/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/sass/expression.dart,
 *              lib/src/ast/sass/expression/binary_operation.dart,
 *              lib/src/ast/sass/expression/boolean.dart,
 *              lib/src/ast/sass/expression/color.dart,
 *              lib/src/ast/sass/expression/function.dart,
 *              lib/src/ast/sass/expression/if.dart,
 *              lib/src/ast/sass/expression/interpolated_function.dart,
 *              lib/src/ast/sass/expression/legacy_if.dart,
 *              lib/src/ast/sass/expression/list.dart,
 *              lib/src/ast/sass/expression/map.dart,
 *              lib/src/ast/sass/expression/null.dart,
 *              lib/src/ast/sass/expression/number.dart,
 *              lib/src/ast/sass/expression/parenthesized.dart,
 *              lib/src/ast/sass/expression/selector.dart,
 *              lib/src/ast/sass/expression/string.dart,
 *              lib/src/ast/sass/expression/supports.dart,
 *              lib/src/ast/sass/expression/unary_operation.dart,
 *              lib/src/ast/sass/expression/value.dart,
 *              lib/src/ast/sass/expression/variable.dart
 * Original: Copyright (c) 2016, 2018, 2021, 2022, 2025 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: expression.dart + 18 subtype files -> Expression.scala
 *   Convention: Dart abstract class + final subclasses -> Scala abstract class + final case classes
 *   Idiom: ExpressionVisitor/IfConditionExpressionVisitor as forward-reference traits;
 *          BinaryOperator/UnaryOperator enums included here;
 *          IfConditionExpression sealed hierarchy included here
 */
package ssg
package sass
package ast
package sass

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.util.{FileSpan, initialIdentifier, withoutNamespace}
import ssg.sass.value.{ListSeparator, SassColor, SassNumber, Value}

import scala.util.boundary
import scala.util.boundary.break

// ===========================================================================
// ExpressionVisitor — forward reference trait
// ===========================================================================

/** Visitor interface for [Expression] nodes. */
trait ExpressionVisitor[T] {
  def visitBinaryOperationExpression(node: BinaryOperationExpression): T
  def visitBooleanExpression(node: BooleanExpression): T
  def visitColorExpression(node: ColorExpression): T
  def visitFunctionExpression(node: FunctionExpression): T
  def visitIfExpression(node: IfExpression): T
  def visitInterpolatedFunctionExpression(node: InterpolatedFunctionExpression): T
  def visitLegacyIfExpression(node: LegacyIfExpression): T
  def visitListExpression(node: ListExpression): T
  def visitMapExpression(node: MapExpression): T
  def visitNullExpression(node: NullExpression): T
  def visitNumberExpression(node: NumberExpression): T
  def visitParenthesizedExpression(node: ParenthesizedExpression): T
  def visitSelectorExpression(node: SelectorExpression): T
  def visitStringExpression(node: StringExpression): T
  def visitSupportsExpression(node: SupportsExpression): T
  def visitUnaryOperationExpression(node: UnaryOperationExpression): T
  def visitValueExpression(node: ValueExpression): T
  def visitVariableExpression(node: VariableExpression): T
}

// ===========================================================================
// IfConditionExpressionVisitor — forward reference trait
// ===========================================================================

/** Visitor interface for [IfConditionExpression] nodes. */
trait IfConditionExpressionVisitor[T] {
  def visitIfConditionParenthesized(node: IfConditionParenthesized): T
  def visitIfConditionNegation(node: IfConditionNegation): T
  def visitIfConditionOperation(node: IfConditionOperation): T
  def visitIfConditionFunction(node: IfConditionFunction): T
  def visitIfConditionSass(node: IfConditionSass): T
  def visitIfConditionRaw(node: IfConditionRaw): T
}

// ===========================================================================
// Expression — base class
// ===========================================================================

/** A SassScript expression in a Sass syntax tree. */
abstract class Expression extends SassNode {

  /** Calls the appropriate visit method on [visitor]. */
  def accept[T](visitor: ExpressionVisitor[T]): T
}

// ===========================================================================
// BinaryOperator enum
// ===========================================================================

/** A binary operator constant. */
enum BinaryOperator(
  val displayName: String,
  val operator: String,
  val precedence: Int,
  val isAssociative: Boolean = false
) extends java.lang.Enum[BinaryOperator] {

  /** The Microsoft equals operator, `=`. */
  case SingleEquals extends BinaryOperator("single equals", "=", 0)

  /** The disjunction operator, `or`. */
  case Or extends BinaryOperator("or", "or", 1, isAssociative = true)

  /** The conjunction operator, `and`. */
  case And extends BinaryOperator("and", "and", 2, isAssociative = true)

  /** The equality operator, `==`. */
  case Equals extends BinaryOperator("equals", "==", 3)

  /** The inequality operator, `!=`. */
  case NotEquals extends BinaryOperator("not equals", "!=", 3)

  /** The greater-than operator, `>`. */
  case GreaterThan extends BinaryOperator("greater than", ">", 4)

  /** The greater-than-or-equal-to operator, `>=`. */
  case GreaterThanOrEquals extends BinaryOperator("greater than or equals", ">=", 4)

  /** The less-than operator, `<`. */
  case LessThan extends BinaryOperator("less than", "<", 4)

  /** The less-than-or-equal-to operator, `<=`. */
  case LessThanOrEquals extends BinaryOperator("less than or equals", "<=", 4)

  /** The addition operator, `+`. */
  case Plus extends BinaryOperator("plus", "+", 5, isAssociative = true)

  /** The subtraction operator, `-`. */
  case Minus extends BinaryOperator("minus", "-", 5)

  /** The multiplication operator, `*`. */
  case Times extends BinaryOperator("times", "*", 6, isAssociative = true)

  /** The division operator, `/`. */
  case DividedBy extends BinaryOperator("divided by", "/", 6)

  /** The modulo operator, `%`. */
  case Modulo extends BinaryOperator("modulo", "%", 6)

  override def toString: String = displayName
}

// ===========================================================================
// UnaryOperator enum
// ===========================================================================

/** A unary operator constant. */
enum UnaryOperator(
  val displayName: String,
  val operator: String
) extends java.lang.Enum[UnaryOperator] {

  /** The numeric identity operator, `+`. */
  case Plus extends UnaryOperator("plus", "+")

  /** The numeric negation operator, `-`. */
  case Minus extends UnaryOperator("minus", "-")

  /** The leading slash operator, `/`. */
  case Divide extends UnaryOperator("divide", "/")

  /** The boolean negation operator, `not`. */
  case Not extends UnaryOperator("not", "not")

  override def toString: String = displayName
}

// ===========================================================================
// BinaryOperationExpression
// ===========================================================================

/** A binary operator, as in `1 + 2` or `$this and $other`.
  *
  * @param operator   the operator being invoked
  * @param left       the left-hand operand
  * @param right      the right-hand operand
  * @param allowsSlash whether this is a [BinaryOperator.DividedBy] operation that
  *                    may be interpreted as slash-separated numbers
  */
final case class BinaryOperationExpression(
  operator: BinaryOperator,
  left: Expression,
  right: Expression,
  allowsSlash: Boolean = false
) extends Expression {

  def span: FileSpan = {
    // Move to the left- and right-most non-binary expressions
    var l = left
    while (l.isInstanceOf[BinaryOperationExpression]) {
      l = l.asInstanceOf[BinaryOperationExpression].left
    }
    var r = right
    while (r.isInstanceOf[BinaryOperationExpression]) {
      r = r.asInstanceOf[BinaryOperationExpression].right
    }
    l.span.expand(r.span)
  }

  /** Returns the span that covers only the operator. */
  def operatorSpan: FileSpan = {
    if (left.span.file == right.span.file &&
        left.span.end.offset < right.span.start.offset) {
      left.span.file
        .span(left.span.end.offset, right.span.start.offset)
        .trim()
    } else {
      span
    }
  }

  def accept[T](visitor: ExpressionVisitor[T]): T =
    visitor.visitBinaryOperationExpression(this)

  override def toString: String = {
    val buffer = new StringBuilder()

    val leftNeedsParens = left match {
      case b: BinaryOperationExpression =>
        b.operator.precedence < operator.precedence
      case l: ListExpression if !l.hasBrackets && l.contents.length >= 2 =>
        true
      case _ => false
    }
    if (leftNeedsParens) buffer.append('(')
    buffer.append(left)
    if (leftNeedsParens) buffer.append(')')

    buffer.append(' ')
    buffer.append(operator.operator)
    buffer.append(' ')

    val r = right
    val rightNeedsParens = r match {
      case b: BinaryOperationExpression =>
        b.operator.precedence <= operator.precedence &&
          !(b.operator == operator && operator.isAssociative)
      case l: ListExpression if !l.hasBrackets && l.contents.length >= 2 =>
        true
      case _ => false
    }
    if (rightNeedsParens) buffer.append('(')
    buffer.append(r)
    if (rightNeedsParens) buffer.append(')')

    buffer.toString()
  }
}

object BinaryOperationExpression {

  /** Creates a [BinaryOperator.DividedBy] operation that may be interpreted as
    * slash-separated numbers.
    */
  def slash(left: Expression, right: Expression): BinaryOperationExpression =
    BinaryOperationExpression(BinaryOperator.DividedBy, left, right, allowsSlash = true)
}

// ===========================================================================
// BooleanExpression
// ===========================================================================

/** A boolean literal, `true` or `false`.
  *
  * @param value the value of this expression
  * @param span  the source span
  */
final case class BooleanExpression(
  value: Boolean,
  span: FileSpan
) extends Expression {

  def accept[T](visitor: ExpressionVisitor[T]): T =
    visitor.visitBooleanExpression(this)

  override def toString: String = value.toString
}

// ===========================================================================
// ColorExpression
// ===========================================================================

/** A color literal.
  *
  * @param value the value of this color
  * @param span  the source span
  */
final case class ColorExpression(
  value: SassColor,
  span: FileSpan
) extends Expression {

  def accept[T](visitor: ExpressionVisitor[T]): T =
    visitor.visitColorExpression(this)

  override def toString: String = value.toString
}

// ===========================================================================
// FunctionExpression
// ===========================================================================

/** A function invocation. This may be a plain CSS function or a Sass function,
  * but may not include interpolation.
  *
  * @param originalName the name of the function being invoked (original underscores)
  * @param arguments    the arguments to pass to the function
  * @param span         the source span
  * @param namespace    the namespace of the function, or empty
  */
final case class FunctionExpression(
  originalName: String,
  arguments: ArgumentList,
  span: FileSpan,
  namespace: Nullable[String] = Nullable.empty
) extends Expression with CallableInvocation with SassReference {

  /** The name with underscores converted to hyphens. */
  val name: String = originalName.replace('_', '-')

  def nameSpan: FileSpan =
    if (namespace.isEmpty) span.initialIdentifier()
    else span.withoutNamespace().initialIdentifier()

  def namespaceSpan: Nullable[FileSpan] =
    if (namespace.isEmpty) Nullable.empty
    else Nullable(span.initialIdentifier())

  def accept[T](visitor: ExpressionVisitor[T]): T =
    visitor.visitFunctionExpression(this)

  override def toString: String = {
    val buffer = new StringBuilder()
    namespace.foreach(ns => buffer.append(s"$ns."))
    buffer.append(s"$originalName$arguments")
    buffer.toString()
  }
}

// ===========================================================================
// IfExpression + IfConditionExpression hierarchy
// ===========================================================================

/** The parent class of conditions in an [IfExpression]. */
sealed trait IfConditionExpression extends SassNode {

  /** Whether this is an arbitrary substitution expression. */
  def isArbitrarySubstitution: Boolean = false

  /** Calls the appropriate visit method on [visitor]. */
  def accept[T](visitor: IfConditionExpressionVisitor[T]): T
}

/** A parenthesized condition. */
final case class IfConditionParenthesized(
  expression: IfConditionExpression,
  span: FileSpan
) extends IfConditionExpression {

  def accept[T](visitor: IfConditionExpressionVisitor[T]): T =
    visitor.visitIfConditionParenthesized(this)

  override def toString: String = s"($expression)"
}

/** A negated condition. */
final case class IfConditionNegation(
  expression: IfConditionExpression,
  span: FileSpan
) extends IfConditionExpression {

  def accept[T](visitor: IfConditionExpressionVisitor[T]): T =
    visitor.visitIfConditionNegation(this)

  override def toString: String = s"not $expression"
}

/** A sequence of `and`s or `or`s. */
final case class IfConditionOperation(
  expressions: List[IfConditionExpression],
  op: BooleanOperator
) extends IfConditionExpression {
  require(expressions.length >= 2, "expressions must have length >= 2")

  def span: FileSpan =
    expressions.head.span.expand(expressions.last.span)

  def accept[T](visitor: IfConditionExpressionVisitor[T]): T =
    visitor.visitIfConditionOperation(this)

  override def toString: String = expressions.mkString(s" $op ")
}

/** A plain-CSS function-style condition. */
final case class IfConditionFunction(
  name: Interpolation,
  arguments: Interpolation,
  span: FileSpan
) extends IfConditionExpression {

  override def isArbitrarySubstitution: Boolean = name.asPlain match {
    case n if n.isDefined =>
      val plain = n.get.toLowerCase
      plain == "if" || plain == "var" || plain == "attr" || plain.startsWith("--")
    case _ => false
  }

  def accept[T](visitor: IfConditionExpressionVisitor[T]): T =
    visitor.visitIfConditionFunction(this)

  override def toString: String = s"$name($arguments)"
}

/** A Sass condition that will evaluate to true or false at compile time. */
final case class IfConditionSass(
  expression: Expression,
  span: FileSpan
) extends IfConditionExpression {

  def accept[T](visitor: IfConditionExpressionVisitor[T]): T =
    visitor.visitIfConditionSass(this)

  override def toString: String = s"sass($expression)"
}

/** A chunk of raw text, possibly with interpolations. */
final case class IfConditionRaw(
  text: Interpolation
) extends IfConditionExpression {

  def span: FileSpan = text.span

  override def isArbitrarySubstitution: Boolean = true

  def accept[T](visitor: IfConditionExpressionVisitor[T]): T =
    visitor.visitIfConditionRaw(this)

  override def toString: String = text.toString
}

/** A CSS `if()` expression.
  *
  * @param branches the conditional branches; a None condition indicates an
  *                 `else` branch
  * @param span     the source span
  */
final case class IfExpression(
  branches: List[(Nullable[IfConditionExpression], Expression)],
  span: FileSpan
) extends Expression {
  require(branches.nonEmpty, "branches may not be empty")

  def accept[T](visitor: ExpressionVisitor[T]): T =
    visitor.visitIfExpression(this)

  override def toString: String = {
    val buffer = new StringBuilder("if(")
    var first = true
    for ((condition, expression) <- branches) {
      if (first) { first = false }
      else { buffer.append("; ") }
      condition.fold(buffer.append("else"))(c => buffer.append(c))
      buffer.append(": ")
      buffer.append(expression)
    }
    buffer.append(')')
    buffer.toString()
  }
}

// ===========================================================================
// InterpolatedFunctionExpression
// ===========================================================================

/** An interpolated function invocation. This is always a plain CSS function.
  *
  * @param name      the name of the function being invoked
  * @param arguments the arguments to pass to the function
  * @param span      the source span
  */
final case class InterpolatedFunctionExpression(
  name: Interpolation,
  arguments: ArgumentList,
  span: FileSpan
) extends Expression with CallableInvocation {

  def accept[T](visitor: ExpressionVisitor[T]): T =
    visitor.visitInterpolatedFunctionExpression(this)

  override def toString: String = s"$name$arguments"
}

// ===========================================================================
// LegacyIfExpression
// ===========================================================================

/** A ternary expression. Defined as a separate syntactic construct rather than
  * a normal function because only one of the `$if-true` and `$if-false`
  * arguments are evaluated.
  *
  * @param arguments the arguments passed to `if()`
  * @param span      the source span
  */
final case class LegacyIfExpression(
  arguments: ArgumentList,
  span: FileSpan
) extends Expression with CallableInvocation {

  def accept[T](visitor: ExpressionVisitor[T]): T =
    visitor.visitLegacyIfExpression(this)

  override def toString: String = s"if$arguments"
}

// ===========================================================================
// ListExpression
// ===========================================================================

/** A list literal.
  *
  * @param contents   the elements of this list
  * @param separator  which separator this list uses
  * @param span       the source span
  * @param hasBrackets whether the list has square brackets
  */
final case class ListExpression(
  contents: List[Expression],
  separator: ListSeparator,
  span: FileSpan,
  hasBrackets: Boolean = false
) extends Expression {

  def accept[T](visitor: ExpressionVisitor[T]): T =
    visitor.visitListExpression(this)

  override def toString: String = {
    val buffer = new StringBuilder()
    if (hasBrackets) {
      buffer.append('[')
    } else if (contents.isEmpty ||
               (contents.length == 1 && separator == ListSeparator.Comma)) {
      buffer.append('(')
    }

    val sep = if (separator == ListSeparator.Comma) ", " else " "
    buffer.append(
      contents.map { element =>
        if (_elementNeedsParens(element)) s"($element)"
        else element.toString
      }.mkString(sep)
    )

    if (hasBrackets) {
      buffer.append(']')
    } else if (contents.isEmpty) {
      buffer.append(')')
    } else if (contents.length == 1 && separator == ListSeparator.Comma) {
      buffer.append(",)")
    }

    buffer.toString()
  }

  /** Returns whether [expression] needs parentheses when printed. */
  private def _elementNeedsParens(expression: Expression): Boolean =
    expression match {
      case l: ListExpression
        if l.contents.length >= 2 && !l.hasBrackets =>
        if (separator == ListSeparator.Comma) l.separator == ListSeparator.Comma
        else l.separator != ListSeparator.Undecided
      case _: UnaryOperationExpression
        if separator == ListSeparator.Space =>
        val u = expression.asInstanceOf[UnaryOperationExpression]
        u.operator == UnaryOperator.Plus || u.operator == UnaryOperator.Minus
      case _ => false
    }
}

// ===========================================================================
// MapExpression
// ===========================================================================

/** A map literal.
  *
  * @param pairs the pairs in this map (list, not map, because keys may repeat)
  * @param span  the source span
  */
final case class MapExpression(
  pairs: List[(Expression, Expression)],
  span: FileSpan
) extends Expression {

  def accept[T](visitor: ExpressionVisitor[T]): T =
    visitor.visitMapExpression(this)

  override def toString: String =
    s"(${pairs.map { case (k, v) => s"$k: $v" }.mkString(", ")})"
}

// ===========================================================================
// NullExpression
// ===========================================================================

/** A null literal.
  *
  * @param span the source span
  */
final case class NullExpression(
  span: FileSpan
) extends Expression {

  def accept[T](visitor: ExpressionVisitor[T]): T =
    visitor.visitNullExpression(this)

  override def toString: String = "null"
}

// ===========================================================================
// NumberExpression
// ===========================================================================

/** A number literal.
  *
  * @param value the numeric value
  * @param span  the source span
  * @param unit  the number's unit, or empty
  */
final case class NumberExpression(
  value: Double,
  span: FileSpan,
  unit: Nullable[String] = Nullable.empty
) extends Expression {

  def accept[T](visitor: ExpressionVisitor[T]): T =
    visitor.visitNumberExpression(this)

  override def toString: String =
    unit.fold(SassNumber(value).toString)(u => SassNumber(value, u).toString)
}

// ===========================================================================
// ParenthesizedExpression
// ===========================================================================

/** An expression wrapped in parentheses.
  *
  * @param expression the internal expression
  * @param span       the source span
  */
final case class ParenthesizedExpression(
  expression: Expression,
  span: FileSpan
) extends Expression {

  def accept[T](visitor: ExpressionVisitor[T]): T =
    visitor.visitParenthesizedExpression(this)

  override def toString: String = s"($expression)"
}

// ===========================================================================
// SelectorExpression
// ===========================================================================

/** A parent selector reference, `&`.
  *
  * @param span the source span
  */
final case class SelectorExpression(
  span: FileSpan
) extends Expression {

  def accept[T](visitor: ExpressionVisitor[T]): T =
    visitor.visitSelectorExpression(this)

  override def toString: String = "&"
}

// ===========================================================================
// StringExpression
// ===========================================================================

/** A string literal.
  *
  * @param text      interpolation that, when evaluated, produces the contents
  * @param hasQuotes whether this has quotes
  */
final case class StringExpression(
  text: Interpolation,
  hasQuotes: Boolean = false
) extends Expression {

  def span: FileSpan = text.span

  def accept[T](visitor: ExpressionVisitor[T]): T =
    visitor.visitStringExpression(this)

  override def toString: String = {
    if (!hasQuotes) text.toString
    else s"\"$text\""
  }
}

object StringExpression {

  /** Returns Sass source for a quoted string that, when evaluated, will have
    * [text] as its contents.
    */
  def quoteText(text: String): String = {
    val quote = _bestQuote(List(text))
    val buffer = new StringBuilder()
    buffer.append(quote)
    _quoteInnerText(text, quote, buffer)
    buffer.append(quote)
    buffer.toString()
  }

  /** Creates a string expression with no interpolation. */
  def plain(text: String, span: FileSpan, quotes: Boolean = false): StringExpression =
    StringExpression(Interpolation.plain(text, span), hasQuotes = quotes)

  private def _quoteInnerText(text: String, quote: Char, buffer: StringBuilder): Unit = {
    var i = 0
    while (i < text.length) {
      val c = text.charAt(i)
      if (c == '\n' || c == '\r' || c == '\f') {
        buffer.append("\\a")
        if (i != text.length - 1) {
          val next = text.charAt(i + 1)
          if (Character.isWhitespace(next) || isHex(next)) {
            buffer.append(' ')
          }
        }
      } else if (c == '\\' || c == quote) {
        buffer.append('\\')
        buffer.append(c)
      } else {
        buffer.append(c)
      }
      i += 1
    }
  }

  private def _bestQuote(strings: List[String]): Char = boundary {
    var containsDoubleQuote = false
    for (value <- strings; i <- 0 until value.length) {
      val c = value.charAt(i)
      if (c == '\'') {
        break('"')
      }
      if (c == '"') containsDoubleQuote = true
    }
    if (containsDoubleQuote) '\'' else '"'
  }

  private def isHex(c: Char): Boolean =
    (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
}

// ===========================================================================
// SupportsExpression
// ===========================================================================

/** An expression-level `@supports` condition. This appears only in the
  * modifiers that come after a plain-CSS `@import`.
  *
  * @param condition the condition itself
  */
final case class SupportsExpression(
  condition: SupportsCondition
) extends Expression {

  def span: FileSpan = condition.span

  def accept[T](visitor: ExpressionVisitor[T]): T =
    visitor.visitSupportsExpression(this)

  override def toString: String = condition.toString
}

// ===========================================================================
// UnaryOperationExpression
// ===========================================================================

/** A unary operator, as in `+$var` or `not fn()`.
  *
  * @param operator the operator being invoked
  * @param operand  the operand
  * @param span     the source span
  */
final case class UnaryOperationExpression(
  operator: UnaryOperator,
  operand: Expression,
  span: FileSpan
) extends Expression {

  def accept[T](visitor: ExpressionVisitor[T]): T =
    visitor.visitUnaryOperationExpression(this)

  override def toString: String = {
    val buffer = new StringBuilder(operator.operator)
    if (operator == UnaryOperator.Not) buffer.append(' ')
    val needsParens = operand match {
      case _: BinaryOperationExpression => true
      case _: UnaryOperationExpression  => true
      case l: ListExpression if !l.hasBrackets && l.contents.length >= 2 => true
      case _ => false
    }
    if (needsParens) buffer.append('(')
    buffer.append(operand)
    if (needsParens) buffer.append(')')
    buffer.toString()
  }
}

// ===========================================================================
// ValueExpression
// ===========================================================================

/** An expression that directly embeds a [Value]. Never constructed by the
  * parser; only used when ASTs are constructed dynamically.
  *
  * @param value the embedded value
  * @param span  the source span
  */
final case class ValueExpression(
  value: Value,
  span: FileSpan
) extends Expression {

  def accept[T](visitor: ExpressionVisitor[T]): T =
    visitor.visitValueExpression(this)

  override def toString: String = value.toString
}

// ===========================================================================
// VariableExpression
// ===========================================================================

/** A Sass variable.
  *
  * @param name      the name of this variable, with underscores converted to hyphens
  * @param span      the source span
  * @param namespace the namespace of the variable, or empty
  */
final case class VariableExpression(
  name: String,
  span: FileSpan,
  namespace: Nullable[String] = Nullable.empty
) extends Expression with SassReference {

  def nameSpan: FileSpan =
    if (namespace.isEmpty) span
    else span.withoutNamespace()

  def namespaceSpan: Nullable[FileSpan] =
    if (namespace.isEmpty) Nullable.empty
    else Nullable(span.initialIdentifier())

  def accept[T](visitor: ExpressionVisitor[T]): T =
    visitor.visitVariableExpression(this)

  override def toString: String = span.text
}
