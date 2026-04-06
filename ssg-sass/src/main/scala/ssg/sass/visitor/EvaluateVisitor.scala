/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/evaluate.dart (~4939 lines)
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: evaluate.dart -> EvaluateVisitor.scala
 *   Convention: Phase 10 partial port — core infrastructure plus expression
 *               evaluation. Statement-level evaluation, callables, modules,
 *               control flow, @use/@forward/@import, selector expansion and
 *               source maps are deferred to subsequent phases. Stub methods
 *               throw UnsupportedOperationException so accidental use is
 *               loud rather than silently wrong.
 *   Idiom: Implements StatementVisitor[Value], ExpressionVisitor[Value],
 *          IfConditionExpressionVisitor[Any] and CssVisitor[Value]. The first
 *          and last yield SassNull from stubs (no Nullable[Value] wrapper —
 *          Sass null serves the role).
 */
package ssg
package sass
package visitor

import scala.collection.immutable.ListMap

import ssg.sass.ast.css.{
  CssAtRule,
  CssComment,
  CssDeclaration,
  CssImport,
  CssKeyframeBlock,
  CssMediaRule,
  CssStyleRule,
  CssStylesheet,
  CssSupportsRule
}
import ssg.sass.ast.sass.{
  AtRootRule,
  AtRule,
  BinaryOperationExpression,
  BinaryOperator,
  BooleanExpression,
  BooleanOperator,
  ColorExpression,
  ContentBlock,
  ContentRule,
  DebugRule,
  Declaration,
  EachRule,
  ErrorRule,
  Expression,
  ExpressionVisitor,
  ExtendRule,
  ForRule,
  ForwardRule,
  FunctionExpression,
  FunctionRule,
  IfConditionExpressionVisitor,
  IfConditionFunction,
  IfConditionNegation,
  IfConditionOperation,
  IfConditionParenthesized,
  IfConditionRaw,
  IfConditionSass,
  IfExpression,
  IfRule,
  ImportRule,
  IncludeRule,
  Interpolation,
  InterpolatedFunctionExpression,
  LegacyIfExpression,
  ListExpression,
  LoudComment,
  MapExpression,
  MediaRule,
  MixinRule,
  NullExpression,
  NumberExpression,
  ParenthesizedExpression,
  ReturnRule,
  SelectorExpression,
  SilentComment,
  StatementVisitor,
  StringExpression,
  StyleRule,
  Stylesheet,
  SupportsExpression,
  SupportsRule,
  UnaryOperationExpression,
  UnaryOperator,
  UseRule,
  ValueExpression,
  VariableDeclaration,
  VariableExpression,
  WarnRule,
  WhileRule
}
import ssg.sass.value.{
  SassBoolean,
  SassList,
  SassMap,
  SassNull,
  SassNumber,
  SassString,
  Value
}
import ssg.sass.{Callable, Environment, ImportCache, Logger, Nullable, SassScriptException}

/** Result of evaluating a Sass stylesheet — a CSS AST plus the set of URLs
  * that were loaded during evaluation.
  */
final case class EvaluateResult(stylesheet: CssStylesheet, loadedUrls: Set[String])

/** A visitor that executes Sass code to produce a CSS AST.
  *
  * This is a partial port: expression evaluation is implemented; statements,
  * callables, modules and CSS-tree construction are stubbed.
  */
final class EvaluateVisitor(
  val importCache: Nullable[ImportCache] = Nullable.Null,
  val logger: Nullable[Logger] = Nullable.Null
) extends StatementVisitor[Value]
    with ExpressionVisitor[Value]
    with IfConditionExpressionVisitor[Any]
    with CssVisitor[Value] {

  // ---------------------------------------------------------------------------
  // State
  // ---------------------------------------------------------------------------

  /** The current lexical environment. Variables and (eventually) functions
    * and mixins live here.
    */
  private var _environment: Environment = Environment()

  /** Whether we're currently evaluating a `@supports` declaration. When true,
    * calculations are not simplified.
    */
  private var _inSupportsDeclaration: Boolean = false

  /** Set of URLs loaded during evaluation. Currently always empty until
    * `@use`/`@import` are wired up.
    */
  private val _loadedUrls = scala.collection.mutable.LinkedHashSet.empty[String]

  // ---------------------------------------------------------------------------
  // Public entry points
  // ---------------------------------------------------------------------------

  /** Evaluate a parsed [[Stylesheet]] to a CSS AST. Statements are not yet
    * implemented; this returns an empty stylesheet.
    */
  def run(stylesheet: Stylesheet): EvaluateResult = {
    // TODO: walk children, build CSS tree.
    EvaluateResult(CssStylesheet.empty(), _loadedUrls.toSet)
  }

  /** Evaluate an expression in isolation against this visitor's environment.
    * Used for tests, REPL and command-line --watch.
    */
  def runExpression(stylesheet: Stylesheet, expression: Expression): Value =
    expression.accept(this)

  /** Evaluate an expression in isolation against an explicit environment. */
  def runExpression(expression: Expression, environment: Environment): Value = {
    val saved = _environment
    _environment = environment
    try expression.accept(this)
    finally _environment = saved
  }

  // ===========================================================================
  // ExpressionVisitor
  // ===========================================================================

  override def visitBinaryOperationExpression(node: BinaryOperationExpression): Value = {
    val left = node.left.accept(this)
    node.operator match {
      case BinaryOperator.SingleEquals       => left.singleEquals(node.right.accept(this))
      case BinaryOperator.Or                 => if (left.isTruthy) left else node.right.accept(this)
      case BinaryOperator.And                => if (left.isTruthy) node.right.accept(this) else left
      case BinaryOperator.Equals             => SassBoolean(left == node.right.accept(this))
      case BinaryOperator.NotEquals          => SassBoolean(left != node.right.accept(this))
      case BinaryOperator.GreaterThan        => left.greaterThan(node.right.accept(this))
      case BinaryOperator.GreaterThanOrEquals => left.greaterThanOrEquals(node.right.accept(this))
      case BinaryOperator.LessThan           => left.lessThan(node.right.accept(this))
      case BinaryOperator.LessThanOrEquals   => left.lessThanOrEquals(node.right.accept(this))
      case BinaryOperator.Plus               => left.plus(node.right.accept(this))
      case BinaryOperator.Minus              => left.minus(node.right.accept(this))
      case BinaryOperator.Times              => left.times(node.right.accept(this))
      case BinaryOperator.DividedBy          => left.dividedBy(node.right.accept(this))
      case BinaryOperator.Modulo             => left.modulo(node.right.accept(this))
    }
  }

  override def visitBooleanExpression(node: BooleanExpression): Value =
    SassBoolean(node.value)

  override def visitColorExpression(node: ColorExpression): Value = node.value

  override def visitFunctionExpression(node: FunctionExpression): Value = {
    // Look up the callable in the current environment. If not found, fall
    // through to a "plain CSS function" rendering. Full dispatch (built-in
    // calculations, namespaces, plain CSS calls) is deferred to Phase 20.
    val callable: Nullable[Callable] = _environment.getFunction(node.name)
    callable.fold[Value] {
      // Render unknown function as plain CSS: `name(arg1, arg2, ...)`.
      val args = node.arguments.positional.map(a => _evaluateToCss(a))
      new SassString(s"${node.originalName}(${args.mkString(", ")})", hasQuotes = false)
    } { c =>
      // Built-in callable dispatch — minimal: evaluate positional args and call.
      c match {
        case bic: ssg.sass.BuiltInCallable =>
          val argValues = node.arguments.positional.map(_.accept(this))
          bic.callback(argValues)
        case _ =>
          throw SassScriptException(s"Callable type not yet supported: $c")
      }
    }
  }

  override def visitIfExpression(node: IfExpression): Value = {
    // Walk branches; the first whose condition is truthy wins. Conditions
    // are full Sass expressions wrapped in IfConditionSass for the simple
    // case. The Object/String|bool variant of conditions used by CSS `if()`
    // is preserved here but coerced to truthiness for plain Sass usage.
    val branches = node.branches
    var result: Nullable[Value] = Nullable.empty
    var i = 0
    while (i < branches.length && result.isEmpty) {
      val (condition, expression) = branches(i)
      val matched: Boolean = condition.fold(true) { c =>
        c.accept(this) match {
          case b: Boolean => b
          case _          => true
        }
      }
      if (matched) {
        result = Nullable(expression.accept(this))
      }
      i += 1
    }
    result.getOrElse(SassNull)
  }

  override def visitInterpolatedFunctionExpression(node: InterpolatedFunctionExpression): Value = {
    // Plain CSS function whose name is computed via interpolation.
    val name = _performInterpolation(node.name)
    val args = node.arguments.positional.map(a => _evaluateToCss(a))
    new SassString(s"$name(${args.mkString(", ")})", hasQuotes = false)
  }

  override def visitLegacyIfExpression(node: LegacyIfExpression): Value = {
    // Three-argument macro form of `if($condition, $if-true, $if-false)`.
    val positional = node.arguments.positional
    if (positional.length < 3) {
      throw SassScriptException("Missing arguments to if().")
    }
    val condition = positional(0).accept(this)
    if (condition.isTruthy) positional(1).accept(this)
    else positional(2).accept(this)
  }

  override def visitListExpression(node: ListExpression): Value =
    SassList(
      node.contents.map(_.accept(this)),
      node.separator,
      brackets = node.hasBrackets
    )

  override def visitMapExpression(node: MapExpression): Value = {
    var map: ListMap[Value, Value] = ListMap.empty
    for ((key, value) <- node.pairs) {
      val keyValue = key.accept(this)
      val valueValue = value.accept(this)
      if (map.contains(keyValue)) {
        throw SassScriptException("Duplicate key.")
      }
      map = map.updated(keyValue, valueValue)
    }
    SassMap(map)
  }

  override def visitNullExpression(node: NullExpression): Value = SassNull

  override def visitNumberExpression(node: NumberExpression): Value =
    node.unit.fold[SassNumber](SassNumber(node.value))(u => SassNumber(node.value, u))

  override def visitParenthesizedExpression(node: ParenthesizedExpression): Value =
    node.expression.accept(this)

  override def visitSelectorExpression(node: SelectorExpression): Value =
    // No active style rule yet — full implementation needs the modifiable CSS
    // tree from Phase 19.
    SassNull

  override def visitStringExpression(node: StringExpression): Value = {
    // Don't use _performInterpolation here because we need the raw text from
    // SassString values rather than their CSS representation.
    val oldInSupports = _inSupportsDeclaration
    _inSupportsDeclaration = false
    try {
      val sb = new StringBuilder()
      var i = 0
      while (i < node.text.contents.length) {
        node.text.contents(i) match {
          case s: String => sb.append(s)
          case e: Expression =>
            e.accept(this) match {
              case s: SassString => sb.append(s.text)
              case other         => sb.append(other.toCssString(quote = false))
            }
          case other =>
            throw new IllegalStateException(s"Unknown interpolation value $other")
        }
        i += 1
      }
      new SassString(sb.toString(), hasQuotes = node.hasQuotes)
    } finally {
      _inSupportsDeclaration = oldInSupports
    }
  }

  override def visitSupportsExpression(node: SupportsExpression): Value =
    // Until SupportsCondition handling is fully ported, render the condition
    // as an unquoted string from its toString form.
    new SassString(node.condition.toString, hasQuotes = false)

  override def visitUnaryOperationExpression(node: UnaryOperationExpression): Value = {
    val operand = node.operand.accept(this)
    node.operator match {
      case UnaryOperator.Plus   => operand.unaryPlus()
      case UnaryOperator.Minus  => operand.unaryMinus()
      case UnaryOperator.Divide => operand.unaryDivide()
      case UnaryOperator.Not    => operand.unaryNot()
    }
  }

  override def visitValueExpression(node: ValueExpression): Value = node.value

  override def visitVariableExpression(node: VariableExpression): Value = {
    val result: Nullable[Value] = _environment.getVariable(node.name)
    result.getOrElse(throw SassScriptException(s"Undefined variable: $$${node.name}."))
  }

  // ===========================================================================
  // IfConditionExpressionVisitor — used by CSS `if()` expressions
  // ===========================================================================

  override def visitIfConditionParenthesized(node: IfConditionParenthesized): Any =
    node.expression.accept(this) match {
      case s: String => s"($s)"
      case other     => other
    }

  override def visitIfConditionNegation(node: IfConditionNegation): Any =
    node.expression.accept(this) match {
      case s: String  => s"not $s"
      case b: Boolean => !b
      case _          => throw new IllegalStateException("unreachable")
    }

  override def visitIfConditionOperation(node: IfConditionOperation): Any = {
    // Short-circuit evaluation: false on `and` returns false, true on `or`
    // returns true. Otherwise, accumulate as Sass-side strings.
    var values: List[String] = Nil
    val it = node.expressions.iterator
    var shortCircuit: Nullable[Any] = Nullable.empty
    while (it.hasNext && shortCircuit.isEmpty) {
      val expression = it.next()
      expression.accept(this) match {
        case s: String => values = s :: values
        case false if node.op == BooleanOperator.And => shortCircuit = Nullable(false)
        case true if node.op == BooleanOperator.Or   => shortCircuit = Nullable(true)
        case _ => ()
      }
    }
    shortCircuit.fold[Any] {
      if (values.isEmpty) node.op == BooleanOperator.And
      else values.reverse.mkString(s" ${node.op} ")
    }(identity)
  }

  override def visitIfConditionFunction(node: IfConditionFunction): Any =
    s"${_performInterpolation(node.name)}(${_performInterpolation(node.arguments)})"

  override def visitIfConditionSass(node: IfConditionSass): Any =
    node.expression.accept(this).isTruthy

  override def visitIfConditionRaw(node: IfConditionRaw): Any =
    _performInterpolation(node.text)

  // ===========================================================================
  // Helpers
  // ===========================================================================

  /** Evaluate an [[Interpolation]] to its plain string form, evaluating any
    * embedded expressions and serializing them to CSS.
    */
  private def _performInterpolation(interpolation: Interpolation): String = {
    val sb = new StringBuilder()
    var i = 0
    while (i < interpolation.contents.length) {
      interpolation.contents(i) match {
        case s: String     => sb.append(s)
        case e: Expression => sb.append(_evaluateToCss(e, quote = false))
        case other =>
          throw new IllegalStateException(s"Unknown interpolation value $other")
      }
      i += 1
    }
    sb.toString()
  }

  /** Evaluate [[expression]] and return its CSS string representation. */
  private def _evaluateToCss(expression: Expression, quote: Boolean = true): String = {
    val value = expression.accept(this)
    value match {
      case s: SassString if !quote => s.text
      case _                       => value.toCssString(quote)
    }
  }

  // ===========================================================================
  // StatementVisitor — stubs (Phase 19+)
  // ===========================================================================

  private def statementStub(name: String): Value =
    throw new UnsupportedOperationException(s"EvaluateVisitor.$name not yet implemented")

  override def visitAtRootRule(node: AtRootRule): Value = statementStub("visitAtRootRule")
  override def visitAtRule(node: AtRule): Value = statementStub("visitAtRule")
  override def visitContentBlock(node: ContentBlock): Value = statementStub("visitContentBlock")
  override def visitContentRule(node: ContentRule): Value = statementStub("visitContentRule")
  override def visitDebugRule(node: DebugRule): Value = SassNull
  override def visitDeclaration(node: Declaration): Value = statementStub("visitDeclaration")
  override def visitEachRule(node: EachRule): Value = statementStub("visitEachRule")
  override def visitErrorRule(node: ErrorRule): Value = {
    val value = node.expression.accept(this)
    throw SassScriptException(value.toCssString(quote = false))
  }
  override def visitExtendRule(node: ExtendRule): Value = statementStub("visitExtendRule")
  override def visitForRule(node: ForRule): Value = statementStub("visitForRule")
  override def visitForwardRule(node: ForwardRule): Value = statementStub("visitForwardRule")
  override def visitFunctionRule(node: FunctionRule): Value = statementStub("visitFunctionRule")
  override def visitIfRule(node: IfRule): Value = statementStub("visitIfRule")
  override def visitImportRule(node: ImportRule): Value = statementStub("visitImportRule")
  override def visitIncludeRule(node: IncludeRule): Value = statementStub("visitIncludeRule")
  override def visitLoudComment(node: LoudComment): Value = SassNull
  override def visitMediaRule(node: MediaRule): Value = statementStub("visitMediaRule")
  override def visitMixinRule(node: MixinRule): Value = statementStub("visitMixinRule")
  override def visitReturnRule(node: ReturnRule): Value = node.expression.accept(this)
  override def visitSilentComment(node: SilentComment): Value = SassNull
  override def visitStyleRule(node: StyleRule): Value = statementStub("visitStyleRule")
  override def visitStylesheet(node: Stylesheet): Value = SassNull
  override def visitSupportsRule(node: SupportsRule): Value = statementStub("visitSupportsRule")
  override def visitUseRule(node: UseRule): Value = statementStub("visitUseRule")
  override def visitVariableDeclaration(node: VariableDeclaration): Value = {
    // Minimal variable declaration: evaluate expression, store in environment.
    val value = node.expression.accept(this)
    _environment.setVariable(node.name, value)
    SassNull
  }
  override def visitWarnRule(node: WarnRule): Value = SassNull
  override def visitWhileRule(node: WhileRule): Value = statementStub("visitWhileRule")

  // ===========================================================================
  // CssVisitor — stubs (Phase 19+)
  // ===========================================================================

  private def cssStub(name: String): Value =
    throw new UnsupportedOperationException(s"EvaluateVisitor.$name not yet implemented")

  override def visitCssAtRule(node: CssAtRule): Value = cssStub("visitCssAtRule")
  override def visitCssComment(node: CssComment): Value = cssStub("visitCssComment")
  override def visitCssDeclaration(node: CssDeclaration): Value = cssStub("visitCssDeclaration")
  override def visitCssImport(node: CssImport): Value = cssStub("visitCssImport")
  override def visitCssKeyframeBlock(node: CssKeyframeBlock): Value = cssStub("visitCssKeyframeBlock")
  override def visitCssMediaRule(node: CssMediaRule): Value = cssStub("visitCssMediaRule")
  override def visitCssStyleRule(node: CssStyleRule): Value = cssStub("visitCssStyleRule")
  override def visitCssStylesheet(node: CssStylesheet): Value = cssStub("visitCssStylesheet")
  override def visitCssSupportsRule(node: CssSupportsRule): Value = cssStub("visitCssSupportsRule")
}
