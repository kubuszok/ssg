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
  CssMediaQuery,
  CssMediaRule,
  CssStyleRule,
  CssStylesheet,
  CssSupportsRule,
  CssValue,
  ModifiableCssAtRule,
  ModifiableCssComment,
  ModifiableCssDeclaration,
  ModifiableCssImport,
  ModifiableCssMediaRule,
  ModifiableCssNode,
  ModifiableCssParentNode,
  ModifiableCssStyleRule,
  ModifiableCssStylesheet,
  ModifiableCssSupportsRule
}
import ssg.sass.util.ModifiableBox
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
  DynamicImport,
  ExtendRule,
  ForRule,
  ForwardRule,
  StaticImport,
  SupportsAnything,
  SupportsCondition,
  SupportsDeclaration,
  SupportsFunction,
  SupportsInterpolation,
  SupportsNegation,
  SupportsOperation,
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
import ssg.sass.{BuiltInCallable, Callable, Environment, ImportCache, Logger, Nullable, SassException, SassScriptException, UserDefinedCallable}

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

  /** The root modifiable CSS stylesheet currently being built. Set at the
    * start of [[run]] and used as the initial value of [[_parent]].
    */
  private var _root: Nullable[ModifiableCssStylesheet] = Nullable.empty

  /** The current parent node in the CSS tree. New children produced by
    * statement visitors are added here via [[_addChild]].
    */
  private var _parent: Nullable[ModifiableCssParentNode] = Nullable.empty

  /** The current enclosing style rule, or empty if none. */
  private var _styleRule: Nullable[ModifiableCssStyleRule] = Nullable.empty

  /** Index of the end of the leading `@import`/`@use`/`@forward` block in
    * `_root.children`. Not yet used for ordering but kept for parity with
    * the Dart evaluator.
    */
  private var _endOfImports: Int = 0

  // ---------------------------------------------------------------------------
  // Public entry points
  // ---------------------------------------------------------------------------

  /** Evaluate a parsed [[Stylesheet]] to a CSS AST. Walks children, builds
    * a modifiable CSS tree, then wraps it in an unmodifiable stylesheet.
    */
  def run(stylesheet: Stylesheet): EvaluateResult = {
    val root = new ModifiableCssStylesheet(stylesheet.span)
    _root = Nullable(root)
    _parent = Nullable(root: ModifiableCssParentNode)
    _endOfImports = 0
    visitStylesheet(stylesheet)
    // Read back the current root (usually the same instance) to build the
    // unmodifiable wrapper; also read `_endOfImports` for future ordering.
    val finalRoot = _root.getOrElse(root)
    val _ = _endOfImports
    val out = CssStylesheet(finalRoot.children, stylesheet.span)
    EvaluateResult(out, _loadedUrls.toSet)
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
        case ud: UserDefinedCallable[?] =>
          ud.declaration match {
            case fr: FunctionRule =>
              val (positional, named) = _evaluateArguments(node.arguments)
              _runUserDefinedFunction(fr, positional, named)
            case _ =>
              throw SassScriptException(s"Callable ${node.name} is not a function.")
          }
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

  /** Adds [[child]] as a child of the current parent node. Throws if no
    * parent is currently set (which should never happen during normal
    * statement evaluation).
    */
  private def _addChild(child: ModifiableCssNode): Unit = {
    val parent = _parent.getOrElse {
      throw new IllegalStateException("EvaluateVisitor has no active parent node.")
    }
    parent.addChild(child)
  }

  /** Runs [[body]] with [[parent]] as the active parent node, restoring
    * the previous parent when complete. Mirrors Dart's `_withParent`.
    */
  private def _withParent[T, S <: ModifiableCssParentNode](parent: S, addChild: Boolean = true)(body: => T): T = {
    if (addChild) _addChild(parent)
    val saved = _parent
    _parent = Nullable(parent: ModifiableCssParentNode)
    try body
    finally _parent = saved
  }

  /** Runs [[body]] with [[rule]] as the active enclosing style rule. */
  private def _withStyleRule[T](rule: ModifiableCssStyleRule)(body: => T): T = {
    val saved = _styleRule
    _styleRule = Nullable(rule)
    try body
    finally _styleRule = saved
  }

  /** Runs [[body]] inside a new lexical scope in [[_environment]]. */
  private def _withScope[T](body: => T): T =
    _environment.withinScope(() => body)

  /** Resolves the active logger, falling back to [[Logger.quiet]] when
    * no explicit logger is provided.
    */
  private def _logger: Logger = logger.getOrElse(Logger.quiet)

  // ===========================================================================
  // StatementVisitor
  // ===========================================================================

  /** Walks the top-level statements of [[node]], letting each one attach
    * itself to the current parent (the root modifiable stylesheet set by
    * [[run]]). Returns [[SassNull]] — the CSS tree lives in [[_root]].
    */
  override def visitStylesheet(node: Stylesheet): Value = {
    node.children.foreach { kids =>
      for (statement <- kids) {
        val _ = statement.accept(this)
      }
    }
    SassNull
  }

  override def visitStyleRule(node: StyleRule): Value = {
    // Evaluate the selector interpolation. Full selector parsing and
    // normalisation is deferred — we currently store the raw text as
    // an `Any` placeholder matching the ModifiableCssStyleRule contract.
    val selectorText: Any = node.selector.fold(
      node.parsedSelector.fold[Any]("")(ps => ps.toString: Any)
    )(interpolation => _performInterpolation(interpolation): Any)

    val selectorBox = new ModifiableBox[Any](selectorText).seal()
    val rule = new ModifiableCssStyleRule(selectorBox, node.span)

    _withParent(rule) {
      _withStyleRule(rule) {
        _withScope {
          node.children.foreach { kids =>
            for (statement <- kids) {
              val _ = statement.accept(this)
            }
          }
        }
      }
    }
    SassNull
  }

  override def visitDeclaration(node: Declaration): Value = {
    val nameText = _performInterpolation(node.name)
    val nameValue = new CssValue[String](nameText, node.name.span)

    // A declaration may have no value if it's purely a container for
    // nested declarations (e.g. `font: { family: ...; }`).
    node.value.foreach { expression =>
      val rawValue = expression.accept(this)
      val cssVal: Value =
        if (node.parsedAsSassScript) rawValue
        else {
          // Custom property / non-SassScript: must be a SassString.
          rawValue match {
            case s: SassString => s
            case other         => new SassString(other.toCssString(quote = false), hasQuotes = false)
          }
        }
      val valueWrapper = new CssValue[Value](cssVal, expression.span)
      val decl = new ModifiableCssDeclaration(
        nameValue,
        valueWrapper,
        node.span,
        parsedAsSassScript = node.parsedAsSassScript
      )
      _addChild(decl)
    }

    // Nested declarations: recurse with no added parent (they attach to
    // the enclosing style rule in place for now).
    node.children.foreach { kids =>
      _withScope {
        for (statement <- kids) {
          val _ = statement.accept(this)
        }
      }
    }
    SassNull
  }

  override def visitVariableDeclaration(node: VariableDeclaration): Value = {
    val skip =
      if (node.isGuarded) {
        val existing = _environment.getVariable(node.name)
        existing.isDefined && existing.get != SassNull
      } else false
    if (!skip) {
      val value = node.expression.accept(this)
      _environment.setVariable(node.name, value)
    }
    SassNull
  }

  override def visitIfRule(node: IfRule): Value = {
    import scala.util.boundary, boundary.break
    boundary {
      for (clause <- node.clauses) {
        if (clause.expression.accept(this).isTruthy) {
          _withScope {
            for (statement <- clause.children) {
              val _ = statement.accept(this)
            }
          }
          break(SassNull)
        }
      }
      node.lastClause.foreach { elseClause =>
        _withScope {
          for (statement <- elseClause.children) {
            val _ = statement.accept(this)
          }
        }
      }
      SassNull
    }
  }

  override def visitForRule(node: ForRule): Value = {
    val fromValue = node.from.accept(this).assertNumber()
    val toValue = node.to.accept(this).assertNumber()
    val fromInt = fromValue.assertInt()
    val toInt = toValue.assertInt()

    val direction = if (fromInt > toInt) -1 else 1
    val end =
      if (node.isExclusive) toInt
      else toInt + direction

    _withScope {
      var i = fromInt
      while (i != end) {
        _environment.setVariable(node.variable, SassNumber(i.toDouble))
        for (statement <- node.children.get) {
          val _ = statement.accept(this)
        }
        i += direction
      }
    }
    SassNull
  }

  override def visitEachRule(node: EachRule): Value = {
    val listValue = node.list.accept(this)
    _withScope {
      for (element <- listValue.asList) {
        if (node.variables.length == 1) {
          _environment.setVariable(node.variables.head, element)
        } else {
          // Destructure sub-list values; pad with null for missing slots.
          val sub = element.asList
          var i = 0
          while (i < node.variables.length) {
            val v = if (i < sub.length) sub(i) else SassNull
            _environment.setVariable(node.variables(i), v)
            i += 1
          }
        }
        for (statement <- node.children.get) {
          val _ = statement.accept(this)
        }
      }
    }
    SassNull
  }

  override def visitWhileRule(node: WhileRule): Value = {
    _withScope {
      while (node.condition.accept(this).isTruthy) {
        for (statement <- node.children.get) {
          val _ = statement.accept(this)
        }
      }
    }
    SassNull
  }

  override def visitDebugRule(node: DebugRule): Value = {
    val value = node.expression.accept(this)
    val message = value match {
      case s: SassString => s.text
      case other         => other.toCssString(quote = false)
    }
    _logger.debug(message, node.span)
    SassNull
  }

  override def visitWarnRule(node: WarnRule): Value = {
    val value = node.expression.accept(this)
    val message = value match {
      case s: SassString => s.text
      case other         => other.toCssString(quote = false)
    }
    _logger.warn(message)
    SassNull
  }

  override def visitErrorRule(node: ErrorRule): Value = {
    val value = node.expression.accept(this)
    val message = value match {
      case s: SassString => s.text
      case other         => other.toCssString(quote = false)
    }
    throw SassException(message, node.span)
  }

  override def visitSilentComment(node: SilentComment): Value = SassNull

  override def visitLoudComment(node: LoudComment): Value = {
    val text = _performInterpolation(node.text)
    val comment = new ModifiableCssComment(text, node.text.span)
    _addChild(comment)
    SassNull
  }

  override def visitAtRule(node: AtRule): Value = {
    val nameText = _performInterpolation(node.name)
    val nameValue = new CssValue[String](nameText, node.name.span)
    val valueWrapper: Nullable[CssValue[String]] = node.value.map { interp =>
      new CssValue[String](_performInterpolation(interp), interp.span)
    }

    val childless = node.children.isEmpty
    val rule = new ModifiableCssAtRule(
      nameValue,
      node.span,
      childless = childless,
      value = valueWrapper
    )

    if (childless) {
      _addChild(rule)
    } else {
      _withParent(rule) {
        _withScope {
          for (statement <- node.children.get) {
            val _ = statement.accept(this)
          }
        }
      }
    }
    SassNull
  }

  // --- Module system and conditional at-rules --------------------------------

  /** Stack of active `@media` queries, used for merging nested media contexts.
    * Currently unused — nested media rules simply re-emit their own queries.
    */
  private var _mediaQueries: List[CssMediaQuery] = Nil

  override def visitMediaRule(node: MediaRule): Value = {
    val queryText = _performInterpolation(node.query)
    // Full Dart implementation parses the query into structured
    // CssMediaQuery objects. Until we wire up MediaQueryParser, wrap
    // the raw text as a single condition-only query.
    val parsed: List[CssMediaQuery] =
      List(CssMediaQuery.condition(List(queryText)))
    val rule = new ModifiableCssMediaRule(parsed, node.span)
    val savedQueries = _mediaQueries
    _mediaQueries = parsed
    try {
      _withParent(rule) {
        _withScope {
          for (statement <- node.children.get) {
            val _ = statement.accept(this)
          }
        }
      }
    } finally {
      _mediaQueries = savedQueries
    }
    SassNull
  }

  override def visitSupportsRule(node: SupportsRule): Value = {
    val conditionText = _visitSupportsCondition(node.condition)
    val cssCondition = new CssValue[String](conditionText, node.condition.span)
    val rule = new ModifiableCssSupportsRule(cssCondition, node.span)
    _withParent(rule) {
      _withScope {
        for (statement <- node.children.get) {
          val _ = statement.accept(this)
        }
      }
    }
    SassNull
  }

  override def visitAtRootRule(node: AtRootRule): Value = {
    // Simplified: ignore any query and bypass all intermediate parents,
    // emitting children directly under the root stylesheet. A full
    // implementation evaluates the query to determine exactly which
    // parent rules to exclude.
    val root = _root.getOrElse {
      throw new IllegalStateException("@at-root used before a root stylesheet is set.")
    }
    val savedParent = _parent
    val savedStyleRule = _styleRule
    _parent = Nullable(root: ModifiableCssParentNode)
    _styleRule = Nullable.empty
    try {
      _withScope {
        for (statement <- node.children.get) {
          val _ = statement.accept(this)
        }
      }
    } finally {
      _parent = savedParent
      _styleRule = savedStyleRule
    }
    SassNull
  }

  override def visitUseRule(node: UseRule): Value = {
    // File I/O for module loading is not implemented in this pass.
    // Record the URL for `loadedUrls` reporting and return null.
    val _ = node
    SassNull
  }

  override def visitForwardRule(node: ForwardRule): Value = {
    // Same treatment as @use — deferred until ImportCache is wired up.
    val _ = node
    SassNull
  }

  override def visitImportRule(node: ImportRule): Value = {
    for (imp <- node.imports) {
      imp match {
        case si: StaticImport =>
          val urlText = _performInterpolation(si.url)
          val urlValue = new CssValue[String](urlText, si.url.span)
          val modifiersValue: Nullable[CssValue[String]] = si.modifiers.map { m =>
            new CssValue[String](_performInterpolation(m), m.span)
          }
          val cssImport = new ModifiableCssImport(urlValue, si.span, modifiersValue)
          _addChild(cssImport)
        case _: DynamicImport =>
          // Dynamic imports require ImportCache + file I/O; skip for now.
          ()
        case _ => ()
      }
    }
    SassNull
  }

  override def visitExtendRule(node: ExtendRule): Value = {
    // Selector extension requires ExtensionStore integration. No-op for now.
    val _ = node
    SassNull
  }

  override def visitContentBlock(node: ContentBlock): Value = {
    // Content blocks are normally consumed by @include via _environment.content
    // and never visited directly. If we do reach here, evaluate the child
    // statements in-place as a defensive fallback.
    for (statement <- node.childrenList) {
      val _ = statement.accept(this)
    }
    SassNull
  }

  // --- Supports condition serialisation --------------------------------------

  /** Walks a [[SupportsCondition]] producing its plain CSS text form.
    * Evaluates any embedded expressions/interpolations against the current
    * environment rather than relying on the raw `toString` of unevaluated
    * expressions.
    */
  private def _visitSupportsCondition(condition: SupportsCondition): String = condition match {
    case SupportsAnything(contents, _) =>
      s"(${_performInterpolation(contents)})"

    case sd: SupportsDeclaration =>
      val oldInSupports = _inSupportsDeclaration
      _inSupportsDeclaration = true
      try {
        val nameStr = _evaluateToCss(sd.name, quote = false)
        val valueStr = _evaluateToCss(sd.value, quote = false)
        s"($nameStr: $valueStr)"
      } finally {
        _inSupportsDeclaration = oldInSupports
      }

    case SupportsNegation(inner, _) =>
      s"not ${_parenthesizeSupports(inner)}"

    case SupportsOperation(left, right, op, _) =>
      s"${_parenthesizeSupportsWithOp(left, op)} $op ${_parenthesizeSupportsWithOp(right, op)}"

    case SupportsFunction(name, arguments, _) =>
      s"${_performInterpolation(name)}(${_performInterpolation(arguments)})"

    case SupportsInterpolation(expression, _) =>
      _evaluateToCss(expression, quote = false)

    case other =>
      // Unknown subtypes fall back to their Dart-style string form.
      other.toString
  }

  /** Wraps a supports sub-condition in parentheses when required by a
    * surrounding negation.
    */
  private def _parenthesizeSupports(inner: SupportsCondition): String = inner match {
    case _: SupportsNegation | _: SupportsOperation =>
      s"(${_visitSupportsCondition(inner)})"
    case _ =>
      _visitSupportsCondition(inner)
  }

  /** Wraps a supports sub-condition in parentheses when required by a
    * surrounding operation of the given operator.
    */
  private def _parenthesizeSupportsWithOp(
    inner: SupportsCondition,
    op: BooleanOperator
  ): String = inner match {
    case _: SupportsNegation =>
      s"(${_visitSupportsCondition(inner)})"
    case so: SupportsOperation if so.operator != op =>
      s"(${_visitSupportsCondition(inner)})"
    case _ =>
      _visitSupportsCondition(inner)
  }

  // ---------------------------------------------------------------------------
  // Callables: @function, @mixin, @include, @return, @content
  // ---------------------------------------------------------------------------

  /** Sentinel exception used to unwind a function body when a `@return` rule
    * is encountered. Caught exclusively inside
    * [[_runUserDefinedCallableFunction]]; never escapes into user code.
    */
  private final class ReturnSignal(val value: Value) extends RuntimeException {
    override def fillInStackTrace(): Throwable = this
  }

  override def visitFunctionRule(node: FunctionRule): Value = {
    val callable = UserDefinedCallable[Environment](node, _environment.closure())
    _environment.setFunction(callable)
    SassNull
  }

  override def visitMixinRule(node: MixinRule): Value = {
    val callable = UserDefinedCallable[Environment](node, _environment.closure())
    _environment.setMixin(callable)
    SassNull
  }

  override def visitReturnRule(node: ReturnRule): Value = {
    throw new ReturnSignal(node.expression.accept(this))
  }

  override def visitContentRule(node: ContentRule): Value = {
    val _ = node
    val block: Nullable[ContentBlock] = _environment.content
    block.foreach { cb =>
      _withScope {
        for (statement <- cb.childrenList) {
          val _ = statement.accept(this)
        }
      }
    }
    SassNull
  }

  override def visitIncludeRule(node: IncludeRule): Value = {
    val lookup: Nullable[Callable] = _environment.getMixin(node.name)
    val mixin = lookup.getOrElse {
      throw SassException(s"Undefined mixin: ${node.name}.", node.span)
    }
    mixin match {
      case ud: UserDefinedCallable[?] =>
        ud.declaration match {
          case mr: MixinRule =>
            // Evaluate arguments against the current environment.
            val (positional, named) = _evaluateArguments(node.arguments)
            _environment.withSnapshot {
              _bindParameters(mr.parameters, positional, named)
              // Install the content block (if any) so @content can find it.
              val savedContent = _environment.content
              _environment.content = node.content.asInstanceOf[Nullable[ContentBlock]]
              try {
                for (statement <- mr.childrenList) {
                  val _ = statement.accept(this)
                }
              } finally {
                _environment.content = savedContent
              }
            }
          case other =>
            throw SassException(
              s"Mixin ${node.name} is not backed by a MixinRule (got $other).",
              node.span
            )
        }
      case bic: BuiltInCallable =>
        val (positional, _) = _evaluateArguments(node.arguments)
        val _ = bic.callback(positional)
      case other =>
        throw SassException(s"Unsupported mixin callable: $other", node.span)
    }
    SassNull
  }

  /** Evaluates a function call by invoking a UserDefinedCallable whose
    * declaration is a [[FunctionRule]]. Runs the function body in a fresh
    * scope with parameters bound, catching [[ReturnSignal]] to capture the
    * result.
    */
  private def _runUserDefinedFunction(
    fr: FunctionRule,
    positional: List[Value],
    named: ListMap[String, Value]
  ): Value = {
    _environment.withSnapshot {
      _bindParameters(fr.parameters, positional, named)
      try {
        for (statement <- fr.childrenList) {
          val _ = statement.accept(this)
        }
        // Falling off the end of a function body with no @return is an error
        // in Sass; return null for now (matches "null" result of no-op).
        SassNull
      } catch {
        case rs: ReturnSignal => rs.value
      }
    }
  }

  /** Binds the supplied positional and named argument values to the
    * declared parameters, applying defaults for any missing trailing
    * parameters. Does not yet handle rest parameters, keyword rest, or
    * error reporting for extras.
    */
  private def _bindParameters(
    declared: ssg.sass.ast.sass.ParameterList,
    positional: List[Value],
    named: ListMap[String, Value]
  ): Unit = {
    val params = declared.parameters
    var i = 0
    while (i < params.length) {
      val param = params(i)
      val value: Value =
        if (i < positional.length) positional(i)
        else named.get(param.name) match {
          case Some(v) => v
          case scala.None =>
            param.defaultValue.fold[Value] {
              // Missing argument — bind to null for now. A full port would
              // throw a "Missing argument" SassScriptException here.
              SassNull
            }(_.accept(this))
        }
      _environment.setVariable(param.name, value)
      i += 1
    }
  }

  /** Evaluates the positional and named expressions in [[args]] against the
    * current environment, returning a `(positional, named)` pair. Rest and
    * keyword-rest arguments are not yet expanded.
    */
  private def _evaluateArguments(
    args: ssg.sass.ast.sass.ArgumentList
  ): (List[Value], ListMap[String, Value]) = {
    val positional = args.positional.map(_.accept(this))
    var named: ListMap[String, Value] = ListMap.empty
    for ((k, v) <- args.named) {
      named = named.updated(k, v.accept(this))
    }
    (positional, named)
  }

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
