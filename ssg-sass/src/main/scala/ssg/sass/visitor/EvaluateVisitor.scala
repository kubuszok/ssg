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
  DynamicImport,
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
  InterpolatedFunctionExpression,
  Interpolation,
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
  StaticImport,
  StringExpression,
  StyleRule,
  Stylesheet,
  SupportsAnything,
  SupportsCondition,
  SupportsDeclaration,
  SupportsExpression,
  SupportsFunction,
  SupportsInterpolation,
  SupportsNegation,
  SupportsOperation,
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
import ssg.sass.value.{ SassBoolean, SassList, SassMap, SassNull, SassNumber, SassString, Value }
import ssg.sass.{ BuiltInCallable, Callable, Environment, ImportCache, Logger, Nullable, SassException, SassScriptException, UserDefinedCallable }
import ssg.sass.extend.{ ExtendMode, ExtendUtils, MutableExtensionStore }
import ssg.sass.importer.Importer
import ssg.sass.parse.SelectorParser
import ssg.sass.ast.selector.SelectorList

/** Result of evaluating a Sass stylesheet — a CSS AST plus the set of URLs that were loaded during evaluation.
  */
final case class EvaluateResult(
  stylesheet: CssStylesheet,
  loadedUrls: Set[String],
  warnings:   List[String] = Nil
)

/** A visitor that executes Sass code to produce a CSS AST.
  *
  * This is a partial port: expression evaluation is implemented; statements, callables, modules and CSS-tree construction are stubbed.
  */
final class EvaluateVisitor(
  val importCache: Nullable[ImportCache] = Nullable.Null,
  val logger:      Nullable[Logger] = Nullable.Null,
  val importer:    Nullable[Importer] = Nullable.Null
) extends StatementVisitor[Value]
    with ExpressionVisitor[Value]
    with IfConditionExpressionVisitor[Any]
    with CssVisitor[Value]
    with ssg.sass.EvaluationContext {

  // ---------------------------------------------------------------------------
  // EvaluationContext — deprecation / warning emission
  // ---------------------------------------------------------------------------

  override def currentCallableNode: ssg.sass.ast.AstNode = null.asInstanceOf[ssg.sass.ast.AstNode]

  override def warn(message: String, deprecation: Boolean = false): Unit =
    if (deprecation) {
      _warnings += s"DEPRECATION WARNING: $message"
      _logger.warn(message, deprecation = Nullable(Deprecation.UserAuthored))
    } else {
      _warnings += s"WARNING: $message"
      _logger.warn(message)
    }

  override def warnForDeprecation(deprecation: Deprecation, message: String): Unit = {
    _warnings += s"DEPRECATION WARNING [${deprecation.id}]: $message"
    _logger.warnForDeprecation(deprecation, message)
  }

  // ---------------------------------------------------------------------------
  // State
  // ---------------------------------------------------------------------------

  /** The current lexical environment. Variables and (eventually) functions and mixins live here. Pre-populated with all global built-in functions.
    */
  private var _environment: Environment = Environment.withBuiltins()

  /** Whether we're currently evaluating a `@supports` declaration. When true, calculations are not simplified.
    */
  private var _inSupportsDeclaration: Boolean = false

  /** The pending configuration map pushed in by an enclosing `@use "mid" with (...)`. `visitForwardRule` — and the recursive `@use` of the target inside a module that is itself being loaded — consult
    * this map so that configured variables flow through `@forward` chains. Keys are unprefixed variable names. Entries are evaluated expressions (not the raw AST) because the outer `with` clause must
    * be evaluated in the *caller's* environment, not the forwarded module's.
    */
  private var _pendingConfig: Map[String, ssg.sass.value.Value] = Map.empty

  /** Set of URLs loaded during evaluation. Currently always empty until `@use`/`@import` are wired up.
    */
  private val _loadedUrls = scala.collection.mutable.LinkedHashSet.empty[String]

  /** Effective ImportCache used for parse-deduping across `@use`/`@forward`/`@import`. If the caller didn't supply one, we build a fresh cache whose only importer is the evaluator's `importer` (if
    * any), so repeated loads of the same canonical URL still hit the parse cache within a single compilation.
    */
  private val _effectiveImportCache: ImportCache = {
    val supplied = importCache.fold[Nullable[ImportCache]](Nullable.empty)(c => Nullable(c))
    supplied.getOrElse {
      val imps = importer.fold[List[Importer]](Nil)(i => List(i))
      new ImportCache(importers = imps)
    }
  }

  /** Parses (via the import cache) and returns the stylesheet at [canonicalUrl], or empty if [imp] can't load it. Dedupes parses across repeated `@use` of the same URL.
    */
  private def _loadAndParseCached(
    imp:          Importer,
    canonicalUrl: String
  ): Nullable[ssg.sass.ast.sass.Stylesheet] =
    _effectiveImportCache.importCanonical(imp, canonicalUrl)

  /** Stack of canonical URLs currently being loaded, used to detect `@import`/`@use`/`@forward` cycles within a single compilation.
    */
  private val _activeImports: scala.collection.mutable.LinkedHashSet[String] =
    scala.collection.mutable.LinkedHashSet.empty

  /** The root modifiable CSS stylesheet currently being built. Set at the start of [[run]] and used as the initial value of [[_parent]].
    */
  private var _root: Nullable[ModifiableCssStylesheet] = Nullable.empty

  /** The current parent node in the CSS tree. New children produced by statement visitors are added here via [[_addChild]].
    */
  private var _parent: Nullable[ModifiableCssParentNode] = Nullable.empty

  /** The current enclosing style rule, or empty if none. */
  private var _styleRule: Nullable[ModifiableCssStyleRule] = Nullable.empty

  /** Index of the end of the leading `@import`/`@use`/`@forward` block in `_root.children`. Not yet used for ordering but kept for parity with the Dart evaluator.
    */
  private var _endOfImports: Int = 0

  /** AST-level extension store keyed by media context. The `null` key holds extensions declared outside any `@media` block. Each media rule gets its own store so extensions declared inside `@media`
    * only apply to rules in the same media block.
    */
  private val _mediaExtensionStores: scala.collection.mutable.LinkedHashMap[
    ModifiableCssMediaRule | Null,
    MutableExtensionStore
  ] = {
    val m = scala.collection.mutable.LinkedHashMap.empty[ModifiableCssMediaRule | Null, MutableExtensionStore]
    m.put(null, new MutableExtensionStore(ExtendMode.Normal))
    m
  }

  /** Legacy textual extend map, keyed by the same media-scope identity used by `_mediaExtensionStores`.
    */
  private val _mediaLegacyExtends: scala.collection.mutable.LinkedHashMap[
    ModifiableCssMediaRule | Null,
    scala.collection.mutable.LinkedHashMap[String, scala.collection.mutable.ListBuffer[String]]
  ] = scala.collection.mutable.LinkedHashMap.empty

  /** A pending `@extend` whose target must be matched somewhere in the same media scope, unless it is marked `!optional`. Populated by [[visitExtendRule]] and validated at the end of
    * [[_applyExtends]].
    */
  final private case class PendingExtend(
    targetText: String,
    target:     Nullable[ssg.sass.ast.selector.SimpleSelector],
    isOptional: Boolean,
    span:       ssg.sass.util.FileSpan,
    mediaKey:   ModifiableCssMediaRule | Null,
    var found:  Boolean
  )

  private val _pendingExtends: scala.collection.mutable.ListBuffer[PendingExtend] =
    scala.collection.mutable.ListBuffer.empty

  /** Warnings produced during evaluation. Currently populated by the extend subsystem and surfaced through [[EvaluateResult.warnings]].
    */
  private val _warnings: scala.collection.mutable.ListBuffer[String] =
    scala.collection.mutable.ListBuffer.empty

  /** Side map from a style rule to the underlying ModifiableBox that holds its selector. Used by `_applyExtends` to mutate selectors in place, since Box itself is unmodifiable.
    */
  private val _selectorBoxes: scala.collection.mutable.LinkedHashMap[
    ModifiableCssStyleRule,
    ModifiableBox[Any]
  ] = scala.collection.mutable.LinkedHashMap.empty

  // ---------------------------------------------------------------------------
  // Public entry points
  // ---------------------------------------------------------------------------

  /** Evaluate a parsed [[Stylesheet]] to a CSS AST. Walks children, builds a modifiable CSS tree, then wraps it in an unmodifiable stylesheet.
    */
  def run(stylesheet: Stylesheet): EvaluateResult = {
    val root = new ModifiableCssStylesheet(stylesheet.span)
    _root = Nullable(root)
    _parent = Nullable(root: ModifiableCssParentNode)
    _endOfImports = 0
    val savedCur = ssg.sass.CurrentEnvironment.set(Nullable(_environment))
    val savedInv = ssg.sass.CurrentCallableInvoker.set(
      Nullable((c: Callable, pos: List[Value], named: ListMap[String, Value]) => _invokeCallable(c, pos, named))
    )
    val savedMixinInv = ssg.sass.CurrentMixinInvoker.set(
      Nullable((c: Callable, pos: List[Value], named: ListMap[String, Value]) => _invokeMixinCallable(c, pos, named, Nullable.empty))
    )
    try
      ssg.sass.EvaluationContext.withContext(this) {
        // Forward any warnings discovered during parsing into the evaluator's
        // warning buffer so they surface on CompileResult.warnings.
        for (ptw <- stylesheet.parseTimeWarnings)
          ptw.deprecation.fold(_warnings += s"WARNING: ${ptw.message}") { d =>
            _warnings += s"DEPRECATION WARNING [${d.id}]: ${ptw.message}"
          }
        visitStylesheet(stylesheet)
        // Apply basic `@extend` rewrites before serialising.
        _applyExtends(root)
        // Read back the current root (usually the same instance) to build the
        // unmodifiable wrapper; also read `_endOfImports` for future ordering.
        val finalRoot = _root.getOrElse(root)
        val _         = _endOfImports
        val out       = CssStylesheet(finalRoot.children, stylesheet.span)
        EvaluateResult(out, _loadedUrls.toSet, _warnings.toList)
      }
    finally {
      val _ = ssg.sass.CurrentEnvironment.set(savedCur)
      val _ = ssg.sass.CurrentCallableInvoker.set(savedInv)
      val _ = ssg.sass.CurrentMixinInvoker.set(savedMixinInv)
    }
  }

  /** Invokes a [[Callable]] (built-in or user-defined function) with the given arguments. Used by `meta.call` to dispatch a `SassFunction`'s underlying callable. Mixin invocation is not supported
    * here.
    */
  private def _invokeCallable(callable: Callable, positional: List[Value], named: ListMap[String, Value]): Value =
    callable match {
      case bic: BuiltInCallable =>
        val merged =
          if (named.isEmpty) positional
          else _mergeBuiltInNamedArgs(bic, positional, named)
        bic.callback(_padBuiltInPositional(bic, merged))
      case ud: UserDefinedCallable[?] =>
        ud.declaration match {
          case fr: ssg.sass.ast.sass.FunctionRule =>
            _runUserDefinedFunction(fr, positional, named)
          case _ =>
            throw SassScriptException(s"Callable ${callable.name} is not a function.")
        }
      case _ =>
        throw SassScriptException(s"Callable type not supported by meta.call: $callable")
    }

  /** Switches the active environment, keeping [[CurrentEnvironment]] in sync so built-in callables (e.g. `mixin-exists`) introspect the right scope.
    */
  private def _withEnvironment[T](env: Environment)(body: => T): T = {
    val savedEnv = _environment
    val savedCur = ssg.sass.CurrentEnvironment.set(Nullable(env))
    _environment = env
    try body
    finally {
      _environment = savedEnv
      val _ = ssg.sass.CurrentEnvironment.set(savedCur)
    }
  }

  /** Evaluate an expression in isolation against this visitor's environment. Used for tests, REPL and command-line --watch.
    */
  def runExpression(stylesheet: Stylesheet, expression: Expression): Value =
    expression.accept(this)

  /** Evaluate an expression in isolation against an explicit environment. */
  def runExpression(expression: Expression, environment: Environment): Value =
    _withEnvironment(environment)(expression.accept(this))

  // ===========================================================================
  // ExpressionVisitor
  // ===========================================================================

  override def visitBinaryOperationExpression(node: BinaryOperationExpression): Value = try {
    val left = node.left.accept(this)
    node.operator match {
      case BinaryOperator.SingleEquals        => left.singleEquals(node.right.accept(this))
      case BinaryOperator.Or                  => if (left.isTruthy) left else node.right.accept(this)
      case BinaryOperator.And                 => if (left.isTruthy) node.right.accept(this) else left
      case BinaryOperator.Equals              => SassBoolean(left == node.right.accept(this))
      case BinaryOperator.NotEquals           => SassBoolean(left != node.right.accept(this))
      case BinaryOperator.GreaterThan         => left.greaterThan(node.right.accept(this))
      case BinaryOperator.GreaterThanOrEquals => left.greaterThanOrEquals(node.right.accept(this))
      case BinaryOperator.LessThan            => left.lessThan(node.right.accept(this))
      case BinaryOperator.LessThanOrEquals    => left.lessThanOrEquals(node.right.accept(this))
      case BinaryOperator.Plus                => left.plus(node.right.accept(this))
      case BinaryOperator.Minus               => left.minus(node.right.accept(this))
      case BinaryOperator.Times               => left.times(node.right.accept(this))
      case BinaryOperator.DividedBy           =>
        val rightVal = node.right.accept(this)
        // ISS-028: slash-div deprecation. Only warn when both sides are numbers
        // and the parser did NOT mark this as a CSS-style slash separator
        // (allowsSlash=true is e.g. the result of `list.slash()` or parsed
        // list slash context like `font: 16px/1.4 sans-serif`).
        if (!node.allowsSlash && left.isInstanceOf[SassNumber] && rightVal.isInstanceOf[SassNumber])
          warnForDeprecation(
            Deprecation.SlashDiv,
            "Using / for division is deprecated and will be removed in Dart Sass 2.0.0. Recommendation: math.div($left, $right) or calc($left / $right)."
          )
        left.dividedBy(rightVal)
      case BinaryOperator.Modulo => left.modulo(node.right.accept(this))
    }
  } catch {
    case e: SassScriptException => throw e.withSpan(node.span)
  }

  override def visitBooleanExpression(node: BooleanExpression): Value =
    SassBoolean(node.value)

  override def visitColorExpression(node: ColorExpression): Value = node.value

  override def visitFunctionExpression(node: FunctionExpression): Value = try
    scala.util.boundary[Value] {
      // First-class CSS calc()/min()/max()/clamp() — produce a SassCalculation
      // (or a simplified SassNumber) instead of falling through to plain text.
      if (node.namespace.isEmpty) {
        node.name match {
          case "calc" | "min" | "max" | "clamp" | "round" | "mod" | "rem" | "abs" | "sign" | "sin" | "cos" | "tan" | "asin" | "acos" | "atan" | "atan2" | "sqrt" | "exp" | "pow" | "log" | "hypot" =>
            val calcResult = _evaluateCalculation(node)
            if (calcResult.isDefined) scala.util.boundary.break(calcResult.get)
          case _ => ()
        }
      }
      // Look up the callable in the current environment. If not found, fall
      // through to a "plain CSS function" rendering. Full dispatch (built-in
      // calculations, namespaces, plain CSS calls) is deferred to Phase 20.
      val callable: Nullable[Callable] =
        if (node.namespace.isDefined) {
          node.namespace.fold(Nullable.empty[Callable]) { ns =>
            _environment.getNamespacedFunction(ns, node.name)
          }
        } else {
          _environment.getFunction(node.name)
        }
      callable.fold[Value] {
        // Render unknown function as plain CSS: `name(arg1, arg2, ...)`.
        val args = node.arguments.positional.map(a => _evaluateToCss(a))
        new SassString(s"${node.originalName}(${args.mkString(", ")})", hasQuotes = false)
      } { c =>
        // Built-in callable dispatch — minimal: evaluate positional args and call.
        c match {
          case bic: ssg.sass.BuiltInCallable =>
            val (positional, named) = _evaluateArguments(node.arguments)
            val merged              =
              if (named.isEmpty) positional
              else _mergeBuiltInNamedArgs(bic, positional, named)
            bic.callback(_padBuiltInPositional(bic, merged))
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
  catch {
    case e: SassScriptException => throw e.withSpan(node.span)
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
          case _ => true
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
      val keyValue   = key.accept(this)
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

  override def visitSelectorExpression(node: SelectorExpression): Value = {
    // Returns the current enclosing style rule's selector text as an
    // unquoted SassString, or SassNull when not inside any style rule.
    // Full SelectorList value type is deferred — text suffices for `&`
    // SassScript references in the current text-based selector model.
    val _ = node
    _styleRule.fold[Value](SassNull) { rule =>
      new SassString(rule.selector.toString, hasQuotes = false)
    }
  }

  override def visitStringExpression(node: StringExpression): Value = {
    // Don't use _performInterpolation here because we need the raw text from
    // SassString values rather than their CSS representation.
    val oldInSupports = _inSupportsDeclaration
    _inSupportsDeclaration = false
    try {
      val sb = new StringBuilder()
      var i  = 0
      while (i < node.text.contents.length) {
        node.text.contents(i) match {
          case s: String     => sb.append(s)
          case e: Expression =>
            e.accept(this) match {
              case s: SassString => sb.append(s.text)
              case other => sb.append(other.toCssString(quote = false))
            }
          case other =>
            throw new IllegalStateException(s"Unknown interpolation value $other")
        }
        i += 1
      }
      new SassString(sb.toString(), hasQuotes = node.hasQuotes)
    } finally
      _inSupportsDeclaration = oldInSupports
  }

  override def visitSupportsExpression(node: SupportsExpression): Value =
    // Until SupportsCondition handling is fully ported, render the condition
    // as an unquoted string from its toString form.
    new SassString(node.condition.toString, hasQuotes = false)

  override def visitUnaryOperationExpression(node: UnaryOperationExpression): Value = try {
    val operand = node.operand.accept(this)
    node.operator match {
      case UnaryOperator.Plus   => operand.unaryPlus()
      case UnaryOperator.Minus  => operand.unaryMinus()
      case UnaryOperator.Divide => operand.unaryDivide()
      case UnaryOperator.Not    => operand.unaryNot()
    }
  } catch {
    case e: SassScriptException => throw e.withSpan(node.span)
  }

  override def visitValueExpression(node: ValueExpression): Value = node.value

  override def visitVariableExpression(node: VariableExpression): Value = {
    val result: Nullable[Value] =
      if (node.namespace.isDefined) {
        node.namespace.fold(Nullable.empty[Value]) { ns =>
          _environment.getNamespacedVariable(ns, node.name)
        }
      } else {
        _environment.getVariable(node.name)
      }
    result.getOrElse {
      val qualified = node.namespace.fold(s"$$${node.name}")(ns => s"$ns.$$${node.name}")
      throw SassException(s"Undefined variable: $qualified.", node.span)
    }
  }

  // ===========================================================================
  // IfConditionExpressionVisitor — used by CSS `if()` expressions
  // ===========================================================================

  override def visitIfConditionParenthesized(node: IfConditionParenthesized): Any =
    node.expression.accept(this) match {
      case s: String => s"($s)"
      case other => other
    }

  override def visitIfConditionNegation(node: IfConditionNegation): Any =
    node.expression.accept(this) match {
      case s: String  => s"not $s"
      case b: Boolean => !b
      case _ => throw new IllegalStateException("unreachable")
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
        case _                                       => ()
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

  /** First-class evaluation of `calc()`, `min()`, `max()`, `clamp()`. Walks the argument expressions, treating arithmetic operators as [[ssg.sass.value.CalculationOperation]]s and leaf expressions as
    * normally-evaluated values. Returns a [[ssg.sass.value.SassCalculation]] (or a simplified [[ssg.sass.value.SassNumber]]) on success, or Nullable.empty if the calculation can't be built — in which
    * case the caller falls through to the existing plain-CSS rendering path.
    */
  private def _evaluateCalculation(node: FunctionExpression): Nullable[Value] = {
    import ssg.sass.value.{ CalculationOperator, SassCalculation }
    val args = node.arguments.positional
    if (args.isEmpty || node.arguments.named.nonEmpty) return Nullable.empty
    try {
      def toArg(expr: Expression): Any = expr match {
        case ParenthesizedExpression(inner, _)      => toArg(inner)
        case BinaryOperationExpression(op, l, r, _) =>
          val co: Nullable[CalculationOperator] = op match {
            case BinaryOperator.Plus      => Nullable(CalculationOperator.Plus)
            case BinaryOperator.Minus     => Nullable(CalculationOperator.Minus)
            case BinaryOperator.Times     => Nullable(CalculationOperator.Times)
            case BinaryOperator.DividedBy => Nullable(CalculationOperator.DividedBy)
            case _                        => Nullable.empty[CalculationOperator]
          }
          if (co.isEmpty) expr.accept(this)
          else SassCalculation.operate(co.get, toArg(l), toArg(r))
        case _ =>
          val v = expr.accept(this)
          // Special CSS calc keywords: pi, e, infinity, -infinity, NaN.
          v match {
            case s: SassString if !s.hasQuotes =>
              s.text match {
                case "pi"        => SassNumber(math.Pi)
                case "e"         => SassNumber(math.E)
                case "infinity"  => SassNumber(Double.PositiveInfinity)
                case "-infinity" => SassNumber(Double.NegativeInfinity)
                case "NaN"       => SassNumber(Double.NaN)
                case _           => v
              }
            case _ => v
          }
      }
      val converted = args.map(toArg)
      def nOpt(i: Int): Nullable[Any] =
        if (converted.length > i) Nullable(converted(i)) else Nullable.empty[Any]
      val result: Value = node.name match {
        case "calc" if converted.length == 1 =>
          SassCalculation.calc(converted.head)
        case "min"                            => SassCalculation.min(converted)
        case "max"                            => SassCalculation.max(converted)
        case "clamp" if converted.length == 3 =>
          SassCalculation.clamp(converted(0), Nullable(converted(1)), Nullable(converted(2)))
        case "clamp" if converted.length >= 1 && converted.length <= 3 =>
          SassCalculation.clamp(converted(0), nOpt(1), nOpt(2))
        case "hypot"                         => SassCalculation.hypot(converted)
        case "sqrt" if converted.length == 1 => SassCalculation.sqrt(converted.head)
        case "sin" if converted.length == 1  => SassCalculation.sin(converted.head)
        case "cos" if converted.length == 1  => SassCalculation.cos(converted.head)
        case "tan" if converted.length == 1  => SassCalculation.tan(converted.head)
        case "asin" if converted.length == 1 => SassCalculation.asin(converted.head)
        case "acos" if converted.length == 1 => SassCalculation.acos(converted.head)
        case "atan" if converted.length == 1 => SassCalculation.atan(converted.head)
        case "abs" if converted.length == 1  =>
          converted.head match {
            case n: SassNumber if n.hasUnit("%") =>
              warnForDeprecation(
                Deprecation.AbsPercent,
                "Passing percentages to the global abs() function is deprecated. Recommendation: math.abs($number) (with the sass:math module)."
              )
            case _ => ()
          }
          SassCalculation.abs(converted.head)
        case "sign" if converted.length == 1  => SassCalculation.sign(converted.head)
        case "exp" if converted.length == 1   => SassCalculation.exp(converted.head)
        case "atan2" if converted.length == 2 => SassCalculation.atan2(converted(0), Nullable(converted(1)))
        case "pow" if converted.length == 2   => SassCalculation.pow(converted(0), Nullable(converted(1)))
        case "log" if converted.length == 1   => SassCalculation.log(converted.head, Nullable.empty[Any])
        case "log" if converted.length == 2   => SassCalculation.log(converted(0), Nullable(converted(1)))
        case "mod" if converted.length == 2   => SassCalculation.mod(converted(0), Nullable(converted(1)))
        case "rem" if converted.length == 2   => SassCalculation.rem(converted(0), Nullable(converted(1)))
        case "round" if converted.length == 1 => SassCalculation.round(converted.head)
        case "round" if converted.length == 2 => SassCalculation.round(converted(0), Nullable(converted(1)))
        case "round" if converted.length == 3 => SassCalculation.round(converted(0), Nullable(converted(1)), Nullable(converted(2)))
        case _                                => return Nullable.empty
      }
      Nullable(result)
    } catch {
      case _: SassScriptException      => Nullable.empty
      case _: IllegalArgumentException => Nullable.empty
    }
  }

  /** Evaluate an [[Interpolation]] to its plain string form, evaluating any embedded expressions and serializing them to CSS.
    */
  private def _performInterpolation(interpolation: Interpolation): String = {
    val sb = new StringBuilder()
    var i  = 0
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
      case _ => value.toCssString(quote)
    }
  }

  /** ISS-033: rejects configuring a module member whose variable name begins with `-` or `_`, matching dart-sass `_validateConfiguration` in `lib/src/visitor/evaluate.dart`. Private members are
    * considered internal to the defining module and may not be overridden via `@use ... with (...)` or `@forward ... with (...)`.
    */
  private def _checkPrivateConfig(cv: ssg.sass.ast.sass.ConfiguredVariable): Unit = {
    val n = cv.name
    if (n.nonEmpty && (n.charAt(0) == '-' || n.charAt(0) == '_')) {
      throw SassException(
        "Private members can't be configured by their importers.",
        cv.span
      )
    }
  }

  /** Adds [[child]] as a child of the current parent node. Throws if no parent is currently set (which should never happen during normal statement evaluation).
    */
  private def _addChild(child: ModifiableCssNode): Unit = {
    val parent = _parent.getOrElse {
      throw new IllegalStateException("EvaluateVisitor has no active parent node.")
    }
    parent.addChild(child)
  }

  /** Runs [[body]] with [[parent]] as the active parent node, restoring the previous parent when complete. Mirrors Dart's `_withParent`.
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

  /** Runs [[body]] inside a semi-global scope (used by `@if`, `@for`, `@each`, `@while` so that assignments propagate to the enclosing scope instead of shadowing).
    */
  private def _withSemiGlobalScope[T](body: => T): T =
    _environment.withinSemiGlobalScope(body)

  /** Resolves the active logger, falling back to [[Logger.quiet]] when no explicit logger is provided.
    */
  private def _logger: Logger = logger.getOrElse(Logger.quiet)

  // ===========================================================================
  // StatementVisitor
  // ===========================================================================

  /** Walks the top-level statements of [[node]], letting each one attach itself to the current parent (the root modifiable stylesheet set by [[run]]). Returns [[SassNull]] — the CSS tree lives in
    * [[_root]].
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
    val childSelectorText: String = node.selector.fold(
      node.parsedSelector.fold("")(ps => ps.toString)
    )(interpolation => _performInterpolation(interpolation))

    // Text-based parent (`&`) expansion. When nested inside another style
    // rule, combine the child selector with the parent's selector text:
    // for each comma-separated child piece, replace `&` with each parent
    // piece, or prepend the parent piece + space if `&` is absent. Cross
    // multiple parent and child commas to flatten the result.
    val parentSelector: Nullable[String] = _styleRule.fold[Nullable[String]](Nullable.empty) { p =>
      Nullable(p.selector.toString)
    }
    val expandedSelector: String = _expandSelector(childSelectorText, parentSelector)

    // Prefer an AST-based expansion: parse both child and parent, then use
    // `SelectorList.nestWithin` so we store a real `SelectorList` in the
    // rule's box. Fall back to the textual expansion when either side fails
    // to parse, matching the previous behaviour.
    val parsedExpanded: Nullable[SelectorList] = {
      val childParsed  = SelectorParser.tryParse(childSelectorText)
      val parentParsed = _styleRule.flatMap { p =>
        p.selector match {
          case sl: SelectorList => Nullable(sl)
          case other => SelectorParser.tryParse(other.toString)
        }
      }
      if (childParsed.isEmpty) SelectorParser.tryParse(expandedSelector)
      else if (parentParsed.isEmpty) Nullable(childParsed.get)
      else
        try Nullable(childParsed.get.nestWithin(parentParsed))
        catch { case _: Throwable => SelectorParser.tryParse(expandedSelector) }
    }

    val boxValue: Any = parsedExpanded.fold[Any](expandedSelector)(sl => sl: Any)
    val modifiableSelectorBox = new ModifiableBox[Any](boxValue)
    val selectorBox           = modifiableSelectorBox.seal()
    val rule                  = new ModifiableCssStyleRule(selectorBox, node.span)
    _selectorBoxes(rule) = modifiableSelectorBox

    // Nested style rules in CSS output must be FLAT — they should be
    // emitted as siblings of the outer style rule rather than children.
    // Walk up `_parent` to the nearest non-CssStyleRule ancestor and add
    // the new rule there, then evaluate children with that as the parent.
    val savedParent = _parent
    val nearestNonStyle: ModifiableCssParentNode = _nearestNonStyleRuleParent()
    _parent = Nullable(nearestNonStyle)
    try
      _withParent(rule) {
        _withStyleRule(rule) {
          _withScope {
            // ISS-026: track whether a nested style rule has been seen in
            // the current source block so visitDeclaration can emit the
            // `mixed-decls` deprecation for declarations written after it.
            _sawNestedRuleStack.push(false)
            try
              node.children.foreach { kids =>
                for (statement <- kids) {
                  val _ = statement.accept(this)
                  statement match {
                    case _: StyleRule =>
                      if (_sawNestedRuleStack.nonEmpty) {
                        val _ = _sawNestedRuleStack.pop()
                        _sawNestedRuleStack.push(true)
                      }
                    case _ => ()
                  }
                }
              }
            finally {
              val _ = _sawNestedRuleStack.pop()
            }
          }
        }
      }
    finally _parent = savedParent
    SassNull
  }

  /** ISS-026: per-source-block "did we already visit a nested style rule?" flag stack. Pushed on entering a style rule body and popped on exit. `visitDeclaration` consults the top-of-stack to decide
    * whether the current declaration is a mixed-decl case.
    */
  private val _sawNestedRuleStack: scala.collection.mutable.Stack[Boolean] =
    scala.collection.mutable.Stack.empty[Boolean]

  /** Walks `_parent` up until it finds a parent node that is not a [[ModifiableCssStyleRule]]. Falls back to `_root` (or the current parent if `_root` is unset). Used to keep nested style rules flat.
    */
  private def _nearestNonStyleRuleParent(): ModifiableCssParentNode = {
    var cur:   Nullable[ModifiableCssParentNode] = _parent
    var found: Nullable[ModifiableCssParentNode] = Nullable.empty
    import scala.util.boundary, boundary.break
    boundary {
      while (cur.isDefined) {
        val node = cur.get
        node match {
          case _: ModifiableCssStyleRule =>
            // Climb to that node's parent in the CSS tree.
            val nextParent = node.parent
            cur = nextParent.fold[Nullable[ModifiableCssParentNode]](Nullable.empty) { pn =>
              pn match {
                case mp: ModifiableCssParentNode => Nullable(mp)
                case _ => Nullable.empty
              }
            }
          case other =>
            found = Nullable(other)
            break(())
        }
      }
    }
    found.getOrElse {
      _root.fold[ModifiableCssParentNode](
        _parent.getOrElse(
          throw new IllegalStateException("EvaluateVisitor has no active parent node.")
        )
      )(r => r: ModifiableCssParentNode)
    }
  }

  /** Text-based parent selector (`&`) expansion. For each comma-separated child piece, substitute `&` with each comma-separated parent piece, or prepend the parent piece + space when `&` is absent.
    * With no parent, the child selector is returned unchanged.
    */
  private def _expandSelector(childSel: String, parentSel: Nullable[String]): String =
    parentSel.fold(childSel) { parent =>
      val parentParts = parent.split(",").map((s: String) => s.trim)
      val childParts  = childSel.split(",").map((s: String) => s.trim)
      val expanded    = for {
        p <- parentParts
        c <- childParts
      } yield
        if (c.contains("&")) c.replace("&", p)
        else s"$p $c"
      expanded.mkString(", ")
    }

  override def visitDeclaration(node: Declaration): Value = {
    // ISS-026: mixed-decls deprecation. If a nested style rule has already
    // been visited in the current enclosing style-rule body, emit the
    // `mixed-decls` warning. Mirrors dart-sass `_warnForRule` in
    // `lib/src/visitor/evaluate.dart`.
    if (_sawNestedRuleStack.nonEmpty && _sawNestedRuleStack.top) {
      warnForDeprecation(
        Deprecation.MixedDecls,
        "Sass's behavior for declarations that appear after nested rules will be changing to match the behavior specified by CSS in an upcoming version. To keep the existing behavior, move the declaration above the nested rule. To opt into the new behavior, wrap the declaration in `& { ... }`."
      )
    }

    val nameText  = _performInterpolation(node.name)
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
            case other => new SassString(other.toCssString(quote = false), hasQuotes = false)
          }
        }
      val valueWrapper = new CssValue[Value](cssVal, expression.span)
      val decl         = new ModifiableCssDeclaration(
        nameValue,
        valueWrapper,
        node.span,
        parsedAsSassScript = node.parsedAsSassScript,
        isImportant = node.isImportant
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
      if (node.isGlobal) {
        _environment.setGlobalVariable(node.name, value)
      } else {
        _environment.setVariable(node.name, value)
      }
    }
    SassNull
  }

  override def visitIfRule(node: IfRule): Value = {
    import scala.util.boundary, boundary.break
    boundary {
      for (clause <- node.clauses)
        if (clause.expression.accept(this).isTruthy) {
          _withSemiGlobalScope {
            for (statement <- clause.children) {
              val _ = statement.accept(this)
            }
          }
          break(SassNull)
        }
      node.lastClause.foreach { elseClause =>
        _withSemiGlobalScope {
          for (statement <- elseClause.children) {
            val _ = statement.accept(this)
          }
        }
      }
      SassNull
    }
  }

  override def visitForRule(node: ForRule): Value = {
    // Evaluate and validate bounds. Mirrors dart-sass `visitForRule` in
    // `pkg/sass/lib/src/visitor/evaluate.dart`: both bounds must be numbers,
    // must be integers, and must have compatible units. Unit mismatches are
    // detected by coercing `to` into `from`'s units and catching the
    // SassScriptException raised by `coerceValueToMatch`.
    val fromName: Nullable[String] = Nullable("from")
    val toName:   Nullable[String] = Nullable("to")
    val fromValue =
      try node.from.accept(this).assertNumber(fromName)
      catch { case e: SassScriptException => throw e.withSpan(node.from.span) }
    val toValue =
      try node.to.accept(this).assertNumber(toName)
      catch { case e: SassScriptException => throw e.withSpan(node.to.span) }

    val fromInt =
      try fromValue.assertInt(fromName)
      catch { case e: SassScriptException => throw e.withSpan(node.from.span) }
    val toInt =
      try toValue.assertInt(toName)
      catch { case e: SassScriptException => throw e.withSpan(node.to.span) }

    // Check unit compatibility by coercing `to` to `from`'s units. This
    // throws a "have incompatible units" SassScriptException when they
    // don't match (unless one side is unitless, which is allowed).
    try {
      val _ = toValue.coerceValueToMatch(fromValue, toName, fromName)
    } catch { case e: SassScriptException => throw e.withSpan(node.to.span) }

    val direction = if (fromInt > toInt) -1 else 1
    val end       =
      if (node.isExclusive) toInt
      else toInt + direction

    _withSemiGlobalScope {
      var i = fromInt
      while (i != end) {
        _environment.setVariable(
          node.variable,
          SassNumber.withUnits(
            i.toDouble,
            numeratorUnits = fromValue.numeratorUnits,
            denominatorUnits = fromValue.denominatorUnits
          )
        )
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
    _withSemiGlobalScope {
      for (element <- listValue.asList) {
        if (node.variables.length == 1) {
          _environment.setVariable(node.variables.head, element)
        } else {
          // Destructure sub-list values; pad with null for missing slots.
          val sub = element.asList
          var i   = 0
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
    _withSemiGlobalScope {
      while (node.condition.accept(this).isTruthy)
        for (statement <- node.children.get) {
          val _ = statement.accept(this)
        }
    }
    SassNull
  }

  override def visitDebugRule(node: DebugRule): Value = {
    val value   = node.expression.accept(this)
    val message = value match {
      case s: SassString => s.text
      case other => other.toCssString(quote = false)
    }
    _logger.debug(message, node.span)
    _warnings += s"DEBUG: $message"
    SassNull
  }

  override def visitWarnRule(node: WarnRule): Value = {
    val value   = node.expression.accept(this)
    val message = value match {
      case s: SassString => s.text
      case other => other.toCssString(quote = false)
    }
    _logger.warn(message)
    _warnings += s"WARNING: $message"
    SassNull
  }

  override def visitErrorRule(node: ErrorRule): Value = {
    val value   = node.expression.accept(this)
    val message = value match {
      case s: SassString => s.text
      case other => other.toCssString(quote = false)
    }
    throw SassException(message, node.span)
  }

  override def visitSilentComment(node: SilentComment): Value = SassNull

  override def visitLoudComment(node: LoudComment): Value = {
    val text    = _performInterpolation(node.text)
    val comment = new ModifiableCssComment(text, node.text.span)
    _addChild(comment)
    SassNull
  }

  override def visitAtRule(node: AtRule): Value = {
    val nameText  = _performInterpolation(node.name)
    val nameValue = new CssValue[String](nameText, node.name.span)
    val valueWrapper: Nullable[CssValue[String]] = node.value.map { interp =>
      new CssValue[String](_performInterpolation(interp), interp.span)
    }

    val childless = node.children.isEmpty
    val rule      = new ModifiableCssAtRule(
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

  /** Stack of active `@media` queries, used for merging nested media contexts. Currently unused — nested media rules simply re-emit their own queries.
    */
  private var _mediaQueries: List[CssMediaQuery] = Nil

  override def visitMediaRule(node: MediaRule): Value = {
    val queryText = _performInterpolation(node.query)
    // Preflight: reject obviously invalid @media query shapes that dart-sass
    // rejects at parse time but that our stage-1 StylesheetParser slurps as
    // raw text. We do this to bring expected-error-not-raised cases in line
    // with dart-sass for css/media/logic/error.hrx. This is intentionally
    // conservative: we only reject patterns that are unambiguously invalid
    // in Level 3/4 media query syntax.
    _validateMediaQueryText(queryText, node.query.span)
    // Try the structured parser first; fall back to wrapping the raw text
    // as a condition-only query when the text doesn't conform to the
    // Level-3 syntax our parser supports (e.g. interpolated fragments).
    val parsed: List[CssMediaQuery] =
      ssg.sass.parse.MediaQueryParser.tryParseList(queryText).getOrElse(List(CssMediaQuery.condition(List(queryText))))
    val rule         = new ModifiableCssMediaRule(parsed, node.span)
    val savedQueries = _mediaQueries
    _mediaQueries = parsed

    // Sass media bubbling: when a `@media` rule appears inside a style
    // rule, the media rule itself attaches to the nearest non-style
    // parent (typically the stylesheet root or an enclosing media rule),
    // and a clone of the enclosing style rule is placed inside the media
    // rule to hold the nested children. This produces output like
    // `.a { @media (q) { color: red; } }` => `@media (q) { .a { color: red; } }`.
    val enclosingStyleRule = _styleRule
    try
      if (enclosingStyleRule.isDefined) {
        val savedParent = _parent
        val nearestNonStyle: ModifiableCssParentNode = _nearestNonStyleRuleParent()
        _parent = Nullable(nearestNonStyle)
        try
          _withParent(rule) {
            // Build a fresh style rule inside the media rule with the
            // same selector box as the enclosing style rule, then run
            // the media's children as if they were direct children of
            // that style rule.
            val outer     = enclosingStyleRule.get
            val innerRule = outer.copyWithoutChildren()
            _withParent(innerRule) {
              _withStyleRule(innerRule) {
                _withScope {
                  for (statement <- node.children.get) {
                    val _ = statement.accept(this)
                  }
                }
              }
            }
          }
        finally _parent = savedParent
      } else {
        _withParent(rule) {
          _withScope {
            for (statement <- node.children.get) {
              val _ = statement.accept(this)
            }
          }
        }
      }
    finally
      _mediaQueries = savedQueries
    SassNull
  }

  /** Rejects a small set of unambiguously invalid `@media` query shapes. Mirrors dart-sass StylesheetParser diagnostics for cases that our stage-1 parser would otherwise accept as raw text. Only runs
    * on interpolation-free text to avoid false positives from user-inserted fragments.
    */
  private def _validateMediaQueryText(text: String, span: ssg.sass.util.FileSpan): Unit = {
    val trimmed = text.trim
    if (trimmed.isEmpty) return
    // Skip if text contains characters that suggest interpolation leftovers
    // or Level-4 range syntax we don't fully parse (e.g. `<`, `>`).
    if (trimmed.contains('<') || trimmed.contains('>')) return
    // (1) Missing whitespace before an opening paren after a keyword:
    //     `not(`, `and(`, `or(`. These are always errors in dart-sass.
    //     Use \b-style lookbehind: the keyword must be preceded by start
    //     or whitespace / `(`.
    val missingWs = "(?:(?:^|[\\s()])(?:not|and|or))\\(".r
    if (missingWs.findFirstIn(trimmed).isDefined) {
      throw new SassException("Expected whitespace.", span)
    }
    // (2) Trailing keyword with no following condition: `a and`,
    //     `(a) or`, `not`, `a and not`. These mean the query ends
    //     immediately before the `{` with an unfinished operator.
    val trailingKw = "(?:^|\\s)(?:not|and|or)\\s*$".r
    if (trailingKw.findFirstIn(trimmed).isDefined) {
      throw new SassException("expected media condition in parentheses.", span)
    }
    // (3) `not ` as a modifier must be followed by a type identifier
    //     (e.g. `not screen`) or a parenthesised condition, not the end
    //     of the query. Already covered by (2) when the query is just
    //     `not`. Covered.

    // (4) Mixing `and` and `or` at the top level, or using `or` after a
    //     type identifier. dart-sass requires either all `and`s or all
    //     `or`s after the first operator, and does not allow `or` after
    //     a bare media type. We approximate by scanning top-level tokens
    //     outside parentheses and checking for illegal sequences.
    val topLevel = _topLevelTokens(trimmed)
    val hasAnd   = topLevel.exists(_.equalsIgnoreCase("and"))
    val hasOr    = topLevel.exists(_.equalsIgnoreCase("or"))
    val startsWithIdent: Boolean = topLevel.headOption.exists { t =>
      val lc = t.toLowerCase
      lc != "not" && lc != "only" && !t.startsWith("(") && !t.startsWith("#{")
    }
    if (hasAnd && hasOr) {
      throw new SassException("""expected "{".""", span)
    }
    if (startsWithIdent && hasOr) {
      // `a or ...` - type queries can only combine with `and`.
      throw new SassException("""expected "{".""", span)
    }
  }

  /** Splits `text` into whitespace-separated tokens at the top level, treating anything inside balanced parentheses (and `#{...}`) as a single token.
    */
  private def _topLevelTokens(text: String): List[String] = {
    val out   = scala.collection.mutable.ListBuffer.empty[String]
    val buf   = new StringBuilder()
    var i     = 0
    var depth = 0
    def flush(): Unit =
      if (buf.nonEmpty) {
        out += buf.toString
        buf.clear()
      }
    while (i < text.length) {
      val c = text.charAt(i)
      if (depth == 0 && (c == ' ' || c == '\t' || c == '\n' || c == '\r')) {
        flush()
        i += 1
      } else if (c == '(') {
        depth += 1
        buf.append(c)
        i += 1
      } else if (c == ')') {
        if (depth > 0) depth -= 1
        buf.append(c)
        i += 1
      } else if (c == '#' && i + 1 < text.length && text.charAt(i + 1) == '{') {
        buf.append(c); buf.append('{')
        i += 2
        var d = 1
        while (i < text.length && d > 0) {
          val cc = text.charAt(i)
          if (cc == '{') d += 1
          else if (cc == '}') d -= 1
          buf.append(cc)
          i += 1
        }
      } else {
        buf.append(c)
        i += 1
      }
    }
    flush()
    out.toList
  }

  override def visitSupportsRule(node: SupportsRule): Value = {
    val conditionText = _visitSupportsCondition(node.condition)
    val cssCondition  = new CssValue[String](conditionText, node.condition.span)
    val rule          = new ModifiableCssSupportsRule(cssCondition, node.span)

    // Sass supports-bubbling (mirrors @media): when a `@supports` rule
    // appears inside a style rule, the supports rule itself attaches to
    // the nearest non-style parent and a clone of the enclosing style
    // rule is placed inside the supports rule to hold the nested
    // children. `.a { @supports (q) { color: red; } }` serializes as
    // `@supports (q) { .a { color: red; } }`.
    val enclosingStyleRule = _styleRule
    if (enclosingStyleRule.isDefined) {
      val savedParent = _parent
      val nearestNonStyle: ModifiableCssParentNode = _nearestNonStyleRuleParent()
      _parent = Nullable(nearestNonStyle)
      try
        _withParent(rule) {
          val outer     = enclosingStyleRule.get
          val innerRule = outer.copyWithoutChildren()
          _withParent(innerRule) {
            _withStyleRule(innerRule) {
              _withScope {
                for (statement <- node.children.get) {
                  val _ = statement.accept(this)
                }
              }
            }
          }
        }
      finally _parent = savedParent
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

  override def visitAtRootRule(node: AtRootRule): Value = {
    val root = _root.getOrElse {
      throw new IllegalStateException("@at-root used before a root stylesheet is set.")
    }

    // Resolve the query: parse the interpolated text if present, otherwise
    // use the default query (which excludes only style rules).
    val query: ssg.sass.ast.sass.AtRootQuery = node.query.fold(
      ssg.sass.ast.sass.AtRootQuery.defaultQuery
    ) { interp =>
      val queryText = _performInterpolation(interp)
      ssg.sass.parse.AtRootQueryParser.tryParseQuery(queryText).getOrElse(ssg.sass.ast.sass.AtRootQuery.defaultQuery)
    }

    // Walk up the current parent chain and find the topmost non-excluded
    // ancestor. The new attachment point is that ancestor (or `root` if
    // every ancestor is excluded).
    def excludes(node: ModifiableCssParentNode): Boolean = node match {
      case _:  ModifiableCssStyleRule    => query.excludesStyleRules
      case _:  ModifiableCssMediaRule    => query.excludesName("media")
      case _:  ModifiableCssSupportsRule => query.excludesName("supports")
      case ar: ModifiableCssAtRule       => query.excludesName(ar.name.value.toLowerCase)
      case _ => false
    }

    // Collect ancestors innermost-first, stopping at the root stylesheet.
    val ancestors = scala.collection.mutable.ListBuffer.empty[ModifiableCssParentNode]
    var cur: Nullable[ModifiableCssParentNode] = _parent
    var atRoot = false
    while (cur.isDefined && !atRoot) {
      val n = cur.get
      n match {
        case _: ModifiableCssStylesheet => atRoot = true
        case _ =>
          ancestors += n
          cur = n.parent.fold[Nullable[ModifiableCssParentNode]](Nullable.empty) {
            case mp: ModifiableCssParentNode => Nullable(mp)
            case _ => Nullable.empty
          }
      }
    }

    // ISS-027: find the OUTERMOST excluded ancestor; the at-root target
    // is the ancestor just above it. If no ancestor is excluded, stay at
    // the current parent. This correctly handles
    // `@media { .a { @at-root (without: media) { ... } } }` where the
    // style rule `.a` is innermost but the media wrapper must be stripped.
    var lastExcludedIdx = -1
    var i               = 0
    while (i < ancestors.length) {
      if (excludes(ancestors(i))) lastExcludedIdx = i
      i += 1
    }
    val newParent: ModifiableCssParentNode =
      if (lastExcludedIdx < 0) {
        _parent.getOrElse(root: ModifiableCssParentNode)
      } else if (lastExcludedIdx + 1 < ancestors.length) {
        ancestors(lastExcludedIdx + 1)
      } else {
        root: ModifiableCssParentNode
      }

    val savedParent    = _parent
    val savedStyleRule = _styleRule
    _parent = Nullable(newParent)
    // Clear the active style rule if it's no longer in the kept chain.
    if (query.excludesStyleRules) _styleRule = Nullable.empty
    try {
      // ISS-027: if an enclosing style rule survived the query (e.g.
      // `@media { .a { @at-root (without: media) { ... } } }`), re-wrap
      // the body in a fresh copy of that style rule at the new parent
      // so bare declarations retain their selector context.
      val needStyleRuleWrapper =
        !query.excludesStyleRules &&
          savedStyleRule.isDefined &&
          (newParent match {
            case _: ModifiableCssStyleRule => false
            case _ => true
          })
      if (needStyleRuleWrapper) {
        val wrapper = savedStyleRule.get.copyWithoutChildren().asInstanceOf[ModifiableCssStyleRule]
        _withParent(wrapper) {
          _withStyleRule(wrapper) {
            _withScope {
              for (statement <- node.children.get) {
                val _ = statement.accept(this)
              }
            }
          }
        }
      } else {
        _withScope {
          for (statement <- node.children.get) {
            val _ = statement.accept(this)
          }
        }
      }
    } finally {
      _parent = savedParent
      _styleRule = savedStyleRule
    }
    SassNull
  }

  override def visitUseRule(node: UseRule): Value = {
    val urlStr0 = node.url.toString
    if (urlStr0.startsWith("sass:")) {
      // Core (`sass:*`) modules are not configurable — reject any `with` clause.
      if (node.configuration.nonEmpty)
        throw new SassException(
          "Built-in modules can't be configured.",
          node.span
        )
      val moduleName = urlStr0.substring("sass:".length)
      ssg.sass.functions.Functions.modules.get(moduleName).foreach { callables =>
        val moduleEnv = Environment.withBuiltins()
        for (c <- callables) c match {
          case bic: BuiltInCallable => moduleEnv.setFunction(bic)
          case _ => ()
        }
        // `sass:meta` additionally exposes `apply` as a mixin so that
        // `@include meta.apply(...)` resolves.
        if (moduleName == "meta") {
          for (m <- ssg.sass.functions.MetaFunctions.moduleMixins)
            moduleEnv.setMixin(m)
        }
        // Use the explicit namespace only when it differs from the raw URL
        // (e.g. `@use "sass:color" as c`); otherwise default to the bare
        // module name so `color.red(...)` resolves regardless of how the
        // parser derived the default namespace from the `sass:` URL.
        val ns =
          if (node.namespace.isDefined && node.namespace.get != urlStr0) node.namespace.get
          else moduleName
        _environment.addNamespace(ns, moduleEnv)
      }
      SassNull
    } else {
      _visitFileUseRule(node)
      SassNull
    }
  }

  private def _visitFileUseRule(node: UseRule): Unit =
    importer.foreach { imp =>
      val urlStr    = node.url.toString
      val canonical = imp.canonicalize(urlStr)
      canonical.foreach { canonicalUrl =>
        if (_activeImports.contains(canonicalUrl)) {
          // Cycle — skip silently to mirror existing `_loadedUrls` behaviour.
        } else if (!_loadedUrls.contains(canonicalUrl)) {
          _loadedUrls += canonicalUrl
          _activeImports += canonicalUrl
          try
            _loadAndParseCached(imp, canonicalUrl).foreach { importedSheet =>
              // Evaluate the module in a fresh environment, then register its
              // members either as a namespace or by merging them flat (`as *`).
              val moduleEnv = Environment.withBuiltins()
              // Apply `with (...)` configuration before evaluating the module
              // so that `!default` declarations honor the override.
              // ISS-033: reject configuring private (`_foo`/`-foo`) vars.
              // Explicit `with` is evaluated in the *caller's* environment,
              // then seeded into moduleEnv. Both the local config and any
              // inherited `_pendingConfig` from an enclosing `@use`-with
              // flow through `@forward` chains (local wins on overlap).
              val localConfig = scala.collection.mutable.LinkedHashMap.empty[String, ssg.sass.value.Value]
              for (cv <- node.configuration) {
                _checkPrivateConfig(cv)
                val cvValue = cv.expression.accept(this)
                localConfig(cv.name) = cvValue
                moduleEnv.setVariable(cv.name, cvValue)
              }
              for ((cn, cv) <- _pendingConfig)
                if (!localConfig.contains(cn)) moduleEnv.setVariable(cn, cv)
              val savedPending1 = _pendingConfig
              _pendingConfig = _pendingConfig ++ localConfig.toMap
              try
                _withEnvironment(moduleEnv) {
                  importedSheet.children.get.foreach { stmt =>
                    val _ = stmt.accept(this)
                  }
                }
              finally _pendingConfig = savedPending1
              // Build a public-facing view that hides private (`-foo`/`_foo`)
              // members before exposing the module to the caller. This
              // applies uniformly to both namespaced `@use` and `as *` flat
              // merge — dart-sass never exposes private members across a
              // module boundary.
              val publicEnv = moduleEnv.publicView()
              if (node.namespace.isDefined) {
                node.namespace.foreach { ns =>
                  _environment.addNamespace(ns, publicEnv)
                }
              } else {
                // Flat (`as *`) — merge public members into the current
                // environment. Private members stay inside `moduleEnv`.
                for ((name, value) <- publicEnv.variableEntries)
                  if (!_environment.variableExists(name)) {
                    _environment.setVariable(name, value)
                  }
                for (fn <- publicEnv.functionValues)
                  _environment.setFunction(fn)
                for (mx <- publicEnv.mixinValues)
                  _environment.setMixin(mx)
              }
            }
          finally {
            val _ = _activeImports.remove(canonicalUrl)
          }
        }
      }
    }

  /** Returns a [[Callable]] equivalent to [orig] but reporting [newName] as its name. Used for `@forward ... as prefix-*`. If [newName] equals the original name, returns [orig] unchanged.
    */
  private def _aliasCallable(newName: String, orig: ssg.sass.Callable): ssg.sass.Callable =
    if (newName == orig.name) orig
    else
      orig match {
        case bic: BuiltInCallable =>
          new BuiltInCallable(newName, bic.parameters, bic.callback, bic.acceptsContent)
        case _ =>
          // Generic fallback — wrap in a thin Callable that delegates name only.
          // The original callable is kept reachable via the wrapper for later
          // evaluator dispatch via reference equality or name lookup.
          new ssg.sass.Callable {
            def name: String = newName
          }
      }

  override def visitForwardRule(node: ForwardRule): Value = {
    // Minimal @forward: load the target module and merge its members into
    // the current environment so callers doing `@use "this-file"` see them
    // (via the namespace). Honors `show`/`hide` filtering and `as prefix-*`.
    importer.foreach { imp =>
      val urlStr    = node.url.toString
      val canonical = imp.canonicalize(urlStr)
      canonical.foreach { canonicalUrl =>
        if (_activeImports.contains(canonicalUrl)) {
          // Cycle — skip silently.
        } else if (!_loadedUrls.contains(canonicalUrl)) {
          _loadedUrls += canonicalUrl
          _activeImports += canonicalUrl
          try
            _loadAndParseCached(imp, canonicalUrl).foreach { importedSheet =>
              val moduleEnv = Environment.withBuiltins()
              // Apply `with (...)` configuration before evaluating the module
              // so that `!default` declarations honor the override (mirrors @use).
              // ISS-033: reject configuring private (`_foo`/`-foo`) vars.
              // Config flows in from both the local `@forward ... with (...)`
              // and from an enclosing `@use`-with via `_pendingConfig`; the
              // local clause wins on overlap, and the combined map is pushed
              // so a further `@forward` inside the target propagates again.
              val localConfig = scala.collection.mutable.LinkedHashMap.empty[String, ssg.sass.value.Value]
              for (cv <- node.configuration) {
                _checkPrivateConfig(cv)
                val cvValue = cv.expression.accept(this)
                localConfig(cv.name) = cvValue
                moduleEnv.setVariable(cv.name, cvValue)
              }
              for ((cn, cv) <- _pendingConfig)
                if (!localConfig.contains(cn)) moduleEnv.setVariable(cn, cv)
              val savedPending2 = _pendingConfig
              _pendingConfig = _pendingConfig ++ localConfig.toMap
              try
                _withEnvironment(moduleEnv) {
                  importedSheet.children.get.foreach { stmt =>
                    val _ = stmt.accept(this)
                  }
                }
              finally _pendingConfig = savedPending2
              val prefix:                   String  = if (node.prefix.isDefined) node.prefix.get else ""
              def varAllowed(name: String): Boolean =
                if (node.shownVariables.isDefined) node.shownVariables.get.contains(name)
                else if (node.hiddenVariables.isDefined) !node.hiddenVariables.get.contains(name)
                else true
              def memberAllowed(name: String): Boolean =
                if (node.shownMixinsAndFunctions.isDefined) node.shownMixinsAndFunctions.get.contains(name)
                else if (node.hiddenMixinsAndFunctions.isDefined) !node.hiddenMixinsAndFunctions.get.contains(name)
                else true
              // Names of global built-in callables — not forwarded.
              val builtinNames: Set[String] =
                ssg.sass.functions.Functions.global.iterator.map(_.name).toSet
              // `@forward` only re-exports public members (a leading `-`
              // or `_` marks a member private to its defining module).
              // show/hide filters are applied per-kind so e.g. `show $bar`
              // only scopes the variable namespace, not a mixin named
              // `bar`.
              for ((name, value) <- moduleEnv.variableEntries)
                if (!Environment.isPrivate(name) && varAllowed(name)) {
                  val newName = prefix + name
                  if (!_environment.variableExists(newName)) {
                    _environment.setVariable(newName, value)
                  }
                }
              for (fn <- moduleEnv.functionValues)
                if (
                  !Environment.isPrivate(fn.name)
                  && !builtinNames.contains(fn.name)
                  && memberAllowed(fn.name)
                ) {
                  _environment.setFunction(_aliasCallable(prefix + fn.name, fn))
                }
              for (mx <- moduleEnv.mixinValues)
                if (
                  !Environment.isPrivate(mx.name)
                  && !builtinNames.contains(mx.name)
                  && memberAllowed(mx.name)
                ) {
                  _environment.setMixin(_aliasCallable(prefix + mx.name, mx))
                }
            }
          finally {
            val _ = _activeImports.remove(canonicalUrl)
          }
        }
      }
    }
    SassNull
  }

  override def visitImportRule(node: ImportRule): Value = {
    for (imp <- node.imports)
      imp match {
        case si: StaticImport =>
          val urlText  = _performInterpolation(si.url)
          val urlValue = new CssValue[String](urlText, si.url.span)
          val modifiersValue: Nullable[CssValue[String]] = si.modifiers.map { m =>
            new CssValue[String](_performInterpolation(m), m.span)
          }
          val cssImport = new ModifiableCssImport(urlValue, si.span, modifiersValue)
          _addChild(cssImport)
        case di: DynamicImport =>
          _loadDynamicImport(di.urlString)
        case _ => ()
      }
    SassNull
  }

  /** Loads a dynamic `@import` via the configured importer, parses the contents, and evaluates the resulting stylesheet in the current scope. Silently skips if no importer is configured or the URL
    * can't be resolved.
    */
  private def _loadDynamicImport(url: String): Unit =
    importer.foreach { imp =>
      val canonical = imp.canonicalize(url)
      canonical.foreach { canonicalUrl =>
        if (_activeImports.contains(canonicalUrl)) {
          // Cycle — skip silently.
        } else if (!_loadedUrls.contains(canonicalUrl)) {
          _loadedUrls += canonicalUrl
          _activeImports += canonicalUrl
          try
            _loadAndParseCached(imp, canonicalUrl).foreach { importedSheet =>
              // Evaluate the imported stylesheet's children as if they were
              // written inline at the @import point. No new scope — imports
              // share the enclosing environment.
              importedSheet.children.get.foreach { stmt =>
                val _ = stmt.accept(this)
              }
            }
          finally {
            val _ = _activeImports.remove(canonicalUrl)
          }
        }
      }
    }

  /** Returns the nearest enclosing `@media` rule in the current CSS parent chain, or `null` if this `@extend` is declared outside any media block.
    */
  private def _enclosingMediaRule(): ModifiableCssMediaRule | Null = {
    var cur: Nullable[ModifiableCssParentNode] = _parent
    var out: ModifiableCssMediaRule | Null     = null
    import scala.util.boundary, boundary.break
    boundary {
      while (cur.isDefined) {
        val node = cur.get
        node match {
          case mr: ModifiableCssMediaRule =>
            out = mr
            break(())
          case _ =>
            cur = node.parent.fold[Nullable[ModifiableCssParentNode]](Nullable.empty) { pn =>
              pn match {
                case mp: ModifiableCssParentNode => Nullable(mp)
                case _ => Nullable.empty
              }
            }
        }
      }
    }
    out
  }

  override def visitExtendRule(node: ExtendRule): Value = {
    // AST-based `@extend` support: record each (extender, target) pair into
    // a media-scoped extension store, falling back to a textual mapping if
    // either side fails to parse. Selector rewriting happens in
    // `_applyExtends` once the stylesheet has been fully evaluated.
    //
    // This method also enforces two dart-sass constraints:
    //   1. Extend targets must be a single simple selector (e.g. `.foo`,
    //      `%bar`, `h1`). Compound (`.a.b`) or complex (`.a .b`) targets
    //      raise a SassException.
    //   2. Each extend call site is recorded as a `PendingExtend` so that,
    //      after the tree walk, unmatched non-optional targets raise
    //      `"The target selector was not found"`.
    _styleRule.foreach { rule =>
      val extenderText = rule.selector.toString
      val targetText   = _performInterpolation(node.selector).trim
      val extenderList = SelectorParser.tryParse(extenderText)
      val targetList   = SelectorParser.tryParse(targetText)

      // Reject compound/complex extend targets up-front (dart-sass parity).
      if (targetList.isDefined) {
        for (targetComplex <- targetList.get.components) {
          val singleCompound = targetComplex.singleCompound
          if (singleCompound.isEmpty || singleCompound.get.components.length != 1)
            throw new SassException(
              "compound selectors may no longer be extended.",
              node.span
            )
        }
      }

      val mediaKey: ModifiableCssMediaRule | Null = _enclosingMediaRule()
      val store = _mediaExtensionStores.getOrElseUpdate(
        mediaKey,
        new MutableExtensionStore(ExtendMode.Normal)
      )

      val ok = extenderList.isDefined && targetList.isDefined && {
        for (targetComplex <- targetList.get.components) {
          val target = targetComplex.singleCompound.get.components.head
          for (extender <- extenderList.get.components)
            store.addExtensionAst(extender, target, node.isOptional)
          _pendingExtends += PendingExtend(
            targetText = targetComplex.toString,
            target = Nullable(target),
            isOptional = node.isOptional,
            span = node.span,
            mediaKey = mediaKey,
            found = false
          )
        }
        true
      }
      if (!ok) {
        // Legacy textual fallback — `tryParse` failed for one side.
        val legacy = _mediaLegacyExtends.getOrElseUpdate(
          mediaKey,
          scala.collection.mutable.LinkedHashMap.empty
        )
        for (target <- targetText.split(',').map((s: String) => s.trim))
          if (target.nonEmpty) {
            legacy.getOrElseUpdate(target, scala.collection.mutable.ListBuffer.empty) += extenderText
            _pendingExtends += PendingExtend(
              targetText = target,
              target = Nullable.empty,
              isOptional = node.isOptional,
              span = node.span,
              mediaKey = mediaKey,
              found = false
            )
          }
      }
    }
    SassNull
  }

  /** Entry point: walk the root with no active media scope, then validate any non-optional `@extend`s whose targets were never matched.
    */
  private def _applyExtends(node: ModifiableCssParentNode): Unit = {
    _applyExtendsIn(node, null)
    // Detect media-scoped extends whose target exists at another scope
    // (most commonly at top-level): dart-sass emits a warning that the
    // extend has no effect in the media context. We check the set of
    // targets encountered during the walk.
    for (pending <- _pendingExtends)
      if (!pending.found && pending.mediaKey != null) {
        val tgt = pending.targetText
        if (_allExtendableTargets.contains(tgt)) {
          _warnings +=
            s"Extending $tgt in @media context has no effect since $tgt is not in the same @media context"
          // Mark as found so the hard error below doesn't also fire.
          pending.found = true
        }
      }
    // Enforce the non-optional target-not-found check.
    for (pending <- _pendingExtends)
      if (!pending.found && !pending.isOptional) {
        throw new SassException(
          "The target selector was not found.\n" +
            "Use \"@extend " + pending.targetText + " !optional\" to avoid this error.",
          pending.span
        )
      }
  }

  /** Every simple-selector textual form encountered under any style rule during extend application. Used to diagnose cross-media extends whose target exists in a different scope.
    */
  private val _allExtendableTargets: scala.collection.mutable.Set[String] =
    scala.collection.mutable.Set.empty

  /** Walks the modifiable CSS tree under `node`, rewriting every style rule's selectors to include any extensions declared in the same media scope (`mediaKey`). A new `ModifiableCssMediaRule`
    * encountered as a child switches the active `mediaKey`, so that extensions inside `@media` blocks only apply to rules in the same block.
    *
    * This is still a textual rewrite over the parsed `SelectorList` AST: no unification, no "second law of extend" beyond what `ExtensionStore.extendList` provides. The per-media scope and
    * pending-check tracking are the parts that make `!optional` work.
    */
  private def _applyExtendsIn(
    node:     ModifiableCssParentNode,
    mediaKey: ModifiableCssMediaRule | Null
  ): Unit = {
    val astStore    = _mediaExtensionStores.get(mediaKey)
    val legacyStore = _mediaLegacyExtends.get(mediaKey)
    val hasAst      = astStore.exists(_.hasAstExtensions)
    val hasLegacy   = legacyStore.exists(_.nonEmpty)

    // Snapshot children because rule removal mutates the live list.
    val toVisit = node.modifiableChildren
    for (child <- toVisit) child match {
      case rule: ModifiableCssStyleRule =>
        var removed = false
        _selectorBoxes.get(rule).foreach { box =>
          val currentSelector = box.value.toString
          // Record each comma-separated piece as a potential extend target.
          for (part <- currentSelector.split(',')) {
            val t = part.trim
            if (t.nonEmpty) _allExtendableTargets += t
          }
          val parsed: Nullable[SelectorList] = box.value match {
            case sl: SelectorList => Nullable(sl)
            case _ => SelectorParser.tryParse(currentSelector)
          }
          if (hasAst && parsed.isDefined) {
            // Mark any pending extends whose target simple selector appears
            // in this rule's selector list as "found" in the current scope.
            for (pending <- _pendingExtends)
              if (!pending.found && pending.mediaKey == mediaKey && pending.target.isDefined) {
                val tgt = pending.target.get
                val hit = parsed.get.components.exists { complex =>
                  complex.components.exists(_.selector.components.contains(tgt))
                }
                if (hit) pending.found = true
              }
            val extended = astStore.get.extendList(parsed.get)
            val filtered = extended.components.filterNot(ExtendUtils.isPlaceholderOnly)
            if (filtered.isEmpty) {
              rule.remove()
              removed = true
            } else if (filtered.length != extended.components.length) {
              val newList = new ssg.sass.ast.selector.SelectorList(filtered, extended.span)
              box.value = newList: Any
            } else {
              box.value = extended: Any
            }
          } else if (hasLegacy) {
            val parts     = currentSelector.split(',').map((s: String) => s.trim).toList
            val augmented = scala.collection.mutable.ListBuffer[String]()
            augmented ++= parts
            for (part <- parts)
              for ((target, extenders) <- legacyStore.get)
                if (part == target || part.contains(target)) {
                  for (pending <- _pendingExtends)
                    if (!pending.found && pending.mediaKey == mediaKey && pending.targetText == target)
                      pending.found = true
                  for (extender <- extenders)
                    augmented += part.replace(target, extender)
                }
            box.value = augmented.distinct.mkString(", ")
          } else if (parsed.isDefined) {
            // No extensions, but still strip any placeholder-only rules so
            // bare `%foo { ... }` never leaks into CSS output.
            val filtered = parsed.get.components.filterNot(ExtendUtils.isPlaceholderOnly)
            if (filtered.isEmpty) {
              rule.remove()
              removed = true
            }
          }
        }
        if (!removed) _applyExtendsIn(rule, mediaKey)
      case mr:     ModifiableCssMediaRule  => _applyExtendsIn(mr, mr)
      case parent: ModifiableCssParentNode => _applyExtendsIn(parent, mediaKey)
      case _ => ()
    }
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

  /** Walks a [[SupportsCondition]] producing its plain CSS text form. Evaluates any embedded expressions/interpolations against the current environment rather than relying on the raw `toString` of
    * unevaluated expressions.
    */
  private def _visitSupportsCondition(condition: SupportsCondition): String = condition match {
    case SupportsAnything(contents, _) =>
      s"(${_performInterpolation(contents)})"

    case sd: SupportsDeclaration =>
      val oldInSupports = _inSupportsDeclaration
      _inSupportsDeclaration = true
      try {
        val nameStr  = _evaluateToCss(sd.name, quote = false)
        val valueStr = _evaluateToCss(sd.value, quote = false)
        s"($nameStr: $valueStr)"
      } finally
        _inSupportsDeclaration = oldInSupports

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

  /** Wraps a supports sub-condition in parentheses when required by a surrounding negation.
    */
  private def _parenthesizeSupports(inner: SupportsCondition): String = inner match {
    case _: SupportsNegation | _: SupportsOperation =>
      s"(${_visitSupportsCondition(inner)})"
    case _ =>
      _visitSupportsCondition(inner)
  }

  /** Wraps a supports sub-condition in parentheses when required by a surrounding operation of the given operator.
    */
  private def _parenthesizeSupportsWithOp(
    inner: SupportsCondition,
    op:    BooleanOperator
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

  /** Sentinel exception used to unwind a function body when a `@return` rule is encountered. Caught exclusively inside [[_runUserDefinedCallableFunction]]; never escapes into user code.
    */
  final private class ReturnSignal(val value: Value) extends RuntimeException {
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

  override def visitReturnRule(node: ReturnRule): Value =
    throw new ReturnSignal(node.expression.accept(this))

  override def visitContentRule(node: ContentRule): Value = {
    val block: Nullable[ContentBlock] = _environment.content
    block.foreach { cb =>
      // Evaluate `@content(arg1, arg2, ...)` arguments in the current
      // (mixin) environment, then bind them to the content block's
      // declared parameters (`@include foo using ($p1, $p2)`) before
      // running the block body in a fresh scope.
      val (positional, named) = _evaluateArguments(node.arguments)
      _withScope {
        _bindParameters(cb.parameters, positional, named)
        for (statement <- cb.childrenList) {
          val _ = statement.accept(this)
        }
      }
    }
    SassNull
  }

  override def visitIncludeRule(node: IncludeRule): Value = {
    val lookup: Nullable[Callable] =
      if (node.namespace.isDefined) {
        node.namespace.flatMap(ns => _environment.getNamespace(ns)).flatMap(_.getMixin(node.name))
      } else {
        _environment.getMixin(node.name)
      }
    val mixin = lookup.getOrElse {
      throw SassException(s"Undefined mixin: ${node.name}.", node.span)
    }
    val (positional, named) = _evaluateArguments(node.arguments)
    _invokeMixinCallable(
      mixin,
      positional,
      named,
      node.content.asInstanceOf[Nullable[ContentBlock]]
    )
    SassNull
  }

  /** Runs a mixin [[Callable]] against the current parent node, emitting its statements in place. Shared between `@include` (via [[visitIncludeRule]]) and `meta.apply` (via [[CurrentMixinInvoker]]).
    *
    * For a [[UserDefinedCallable]] backed by a [[MixinRule]], binds parameters in a fresh environment snapshot and runs the body. For a content-accepting [[BuiltInCallable]], invokes its callback
    * with the positional args. Any other callable shape raises a [[SassScriptException]].
    */
  private def _invokeMixinCallable(
    callable:   Callable,
    positional: List[Value],
    named:      ListMap[String, Value],
    content:    Nullable[ContentBlock]
  ): Unit =
    callable match {
      case ud: UserDefinedCallable[?] =>
        ud.declaration match {
          case mr: MixinRule =>
            _environment.withSnapshot {
              _bindParameters(mr.parameters, positional, named)
              val savedContent = _environment.content
              _environment.content = content
              try
                for (statement <- mr.childrenList) {
                  val _ = statement.accept(this)
                }
              finally
                _environment.content = savedContent
            }
          case other =>
            throw SassScriptException(
              s"Mixin ${callable.name} is not backed by a MixinRule (got $other)."
            )
        }
      case bic: BuiltInCallable =>
        val _ = bic.callback(positional)
      case other =>
        throw SassScriptException(s"Unsupported mixin callable: $other")
    }

  /** Evaluates a function call by invoking a UserDefinedCallable whose declaration is a [[FunctionRule]]. Runs the function body in a fresh scope with parameters bound, catching [[ReturnSignal]] to
    * capture the result.
    */
  private def _runUserDefinedFunction(
    fr:         FunctionRule,
    positional: List[Value],
    named:      ListMap[String, Value]
  ): Value =
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

  /** Binds the supplied positional and named argument values to the declared parameters, applying defaults for any missing trailing parameters. Validates argument counts and names against the
    * declaration, mirroring dart-sass `ParameterList.verify` in `lib/src/ast/sass/parameter_list.dart`.
    */
  private def _bindParameters(
    declared:   ssg.sass.ast.sass.ParameterList,
    positional: List[Value],
    named:      ListMap[String, Value]
  ): Unit = {
    val params = declared.parameters
    // --- Validation (port of dart-sass ParameterList.verify) ------------
    // 1. For each parameter consumed positionally, reject an overlap with
    //    a named argument of the same name.
    // 2. For each parameter not consumed positionally, either it's named,
    //    or it has a default value — otherwise "Missing argument $foo".
    // 3. If there's no rest parameter and positional > params.length,
    //    "Only N [positional] arguments allowed, but M [was|were] passed."
    // 4. If there's no kwargs-rest parameter and any named arg doesn't
    //    correspond to a declared parameter, "No parameter named $foo."
    var namedUsed = 0
    var i0        = 0
    while (i0 < params.length) {
      val parameter = params(i0)
      if (i0 < positional.length) {
        if (named.contains(parameter.name))
          throw SassScriptException(
            s"Argument $$${parameter.name} was passed both by position and by name."
          )
      } else if (named.contains(parameter.name)) {
        namedUsed += 1
      } else if (parameter.defaultValue.isEmpty) {
        throw SassScriptException(s"Missing argument $$${parameter.name}.")
      }
      i0 += 1
    }
    if (declared.restParameter.isEmpty) {
      if (positional.length > params.length) {
        val nParams = params.length
        val nPass   = positional.length
        val argWord = if (nParams == 1) "argument" else "arguments"
        val posWord = if (named.isEmpty) "" else "positional "
        val wasWord = if (nPass == 1) "was" else "were"
        throw SassScriptException(
          s"Only $nParams $posWord${argWord} allowed, but $nPass $wasWord passed."
        )
      }
      if (declared.keywordRestParameter.isEmpty && namedUsed < named.size) {
        val declaredNames = params.iterator.map(_.name).toSet
        val unknown       = named.keysIterator.filter(k => !declaredNames.contains(k)).toList
        val word          = if (unknown.length == 1) "parameter" else "parameters"
        val list          = unknown.map(n => s"$$$n").mkString(" or ")
        throw SassScriptException(s"No $word named $list.")
      }
    }
    var i = 0
    while (i < params.length) {
      val param = params(i)
      val value: Value =
        if (i < positional.length) positional(i)
        else
          named.get(param.name) match {
            case Some(v)    => v
            case scala.None =>
              param.defaultValue.fold[Value] {
                // Validation above guarantees a default exists here.
                SassNull
              }(_.accept(this))
          }
      _environment.setVariable(param.name, value)
      i += 1
    }
    // Bind any remaining positional arguments to the rest parameter as
    // a comma-separated SassList (or SassArgumentList carrying any extra
    // keyword arguments). Parameters declared with `$name...` always
    // bind, even when no extras were supplied (empty list).
    declared.restParameter.foreach { restName =>
      val extras =
        if (positional.length > params.length)
          positional.drop(params.length)
        else Nil
      // Leftover named args = anything not consumed by a declared param.
      val declaredNames = params.iterator.map(_.name).toSet
      val leftover      = named.filter { case (k, _) => !declaredNames.contains(k) }
      val restValue: ssg.sass.value.Value =
        if (declared.keywordRestParameter.isDefined || leftover.nonEmpty) {
          new ssg.sass.value.SassArgumentList(
            extras,
            leftover,
            ssg.sass.value.ListSeparator.Comma
          )
        } else {
          ssg.sass.value.SassList(
            extras,
            ssg.sass.value.ListSeparator.Comma
          )
        }
      _environment.setVariable(restName, restValue)
    }
    // Bind the keyword-rest parameter to a map of leftover keyword args.
    declared.keywordRestParameter.foreach { kwName =>
      val declaredNames = params.iterator.map(_.name).toSet
      val leftover      = named.filter { case (k, _) => !declaredNames.contains(k) }
      val entries       = leftover.iterator.map { case (k, v) =>
        (ssg.sass.value.SassString(k, hasQuotes = false): ssg.sass.value.Value) -> v
      }.toList
      _environment.setVariable(kwName, ssg.sass.value.SassMap(ListMap.from(entries)))
    }
  }

  /** Pads the positional argument list for a [[BuiltInCallable]] with defaults parsed from its declared signature. For each declared parameter beyond `positional.size`, looks up the raw default
    * expression text, parses it via [[ssg.sass.parse.ScssParser]] and evaluates it in the current environment. Stops at the first parameter without a default (which becomes a required arg whose
    * absence is surfaced by the callback itself). Eliminates a large class of index-out-of-bounds bugs in hand-written built-ins that declare defaults like `"$start-at, $end-at: -1"`.
    */
  private def _padBuiltInPositional(bic: BuiltInCallable, positional: List[Value]): List[Value] = {
    val defaults = bic.parameterDefaults
    if (defaults.isEmpty || positional.length >= defaults.length) positional
    else {
      val buf = scala.collection.mutable.ListBuffer.from(positional)
      var i   = positional.length
      scala.util.boundary[Unit] {
        while (i < defaults.length) {
          defaults(i) match {
            case Some(text) =>
              try {
                val (expr, _) = new ssg.sass.parse.ScssParser(text).parseExpression()
                buf += expr.accept(this)
              } catch {
                case _: Throwable => scala.util.boundary.break(())
              }
            case scala.None => scala.util.boundary.break(())
          }
          i += 1
        }
      }
      buf.toList
    }
  }

  /** Merges named arguments into the positional list for a built-in function call. Resolves each name against the callable's declared parameter signature (see [[BuiltInCallable.parameterNames]]),
    * filling any gaps with [[SassNull]]. Names that don't correspond to a declared parameter raise a [[SassScriptException]]. When the callable declares no parameter names (rest-only signatures like
    * `"$args..."`), named arguments are ignored and the positional list is returned unchanged.
    */
  private def _mergeBuiltInNamedArgs(
    bic:        BuiltInCallable,
    positional: List[Value],
    named:      ListMap[String, Value]
  ): List[Value] = {
    val names = bic.parameterNames
    if (names.isEmpty) positional
    else {
      // Validate: every named key must be a declared parameter.
      for ((k, _) <- named)
        if (!names.contains(k))
          throw SassScriptException(s"No parameter named $$$k in ${bic.name}().")
      // Determine the highest index that is explicitly supplied (positional
      // or named) so we don't append trailing nulls for unsupplied tail
      // parameters with defaults.
      val namedIndices = named.keys.map(names.indexOf).filter(_ >= 0)
      val maxIdx       =
        (if (namedIndices.isEmpty) -1 else namedIndices.max).max(positional.length - 1)
      val buf = scala.collection.mutable.ListBuffer.empty[Value]
      var i   = 0
      while (i <= maxIdx && i < names.length) {
        val pname = names(i)
        if (i < positional.length) buf += positional(i)
        else
          named.get(pname) match {
            case Some(v) => buf += v
            case _       => buf += SassNull
          }
        i += 1
      }
      buf.toList
    }
  }

  /** Evaluates the positional and named expressions in [[args]] against the current environment, returning a `(positional, named)` pair. Rest and keyword-rest arguments are not yet expanded.
    */
  private def _evaluateArguments(
    args: ssg.sass.ast.sass.ArgumentList
  ): (List[Value], ListMap[String, Value]) = {
    val positionalBuf = scala.collection.mutable.ListBuffer.empty[Value]
    for (expr <- args.positional)
      positionalBuf += expr.accept(this)
    var named: ListMap[String, Value] = ListMap.empty
    for ((k, v) <- args.named)
      named = named.updated(k, v.accept(this))
    // Splat the trailing rest argument (`$list...`).
    //   • SassArgumentList → splat its positional contents AND merge its
    //     captured keywords into the named-arg map (dart-sass parity).
    //   • SassMap          → splat into keyword arguments only; every key
    //     must be a SassString. This is the `list.join((...)...)` form.
    //   • SassList         → splat as positional args.
    //   • anything else    → single positional arg.
    args.rest.foreach { restExpr =>
      restExpr.accept(this) match {
        case al: ssg.sass.value.SassArgumentList =>
          for (v <- al.asList) positionalBuf += v
          for ((k, v) <- al.keywords)
            named = named.updated(k, v)
        case map: ssg.sass.value.SassMap =>
          for ((k, v) <- map.contents) k match {
            case s: ssg.sass.value.SassString =>
              named = named.updated(s.text, v)
            case other =>
              throw SassScriptException(
                s"Variable keyword argument map must have string keys. $other is not a string in ${map.toString}."
              )
          }
        case list: ssg.sass.value.SassList =>
          for (v <- list.asList) positionalBuf += v
        case other =>
          positionalBuf += other
      }
    }
    // Splat an optional keyword-rest argument (`..., $kwargs...`). Must
    // be a SassMap keyed by SassStrings; each entry becomes a named arg.
    args.keywordRest.foreach { kwExpr =>
      kwExpr.accept(this) match {
        case map: ssg.sass.value.SassMap =>
          for ((k, v) <- map.contents) k match {
            case s: ssg.sass.value.SassString =>
              named = named.updated(s.text, v)
            case other =>
              throw SassScriptException(
                s"Variable keyword argument map must have string keys. $other is not a string in ${map.toString}."
              )
          }
        case other =>
          throw SassScriptException(
            s"Variable keyword arguments must be a map (was $other)."
          )
      }
    }
    (positionalBuf.toList, named)
  }

  // ===========================================================================
  // CssVisitor — stubs (Phase 19+)
  // ===========================================================================

  private def cssStub(name: String): Value =
    throw new UnsupportedOperationException(s"EvaluateVisitor.$name not yet implemented")

  override def visitCssAtRule(node:        CssAtRule):        Value = cssStub("visitCssAtRule")
  override def visitCssComment(node:       CssComment):       Value = cssStub("visitCssComment")
  override def visitCssDeclaration(node:   CssDeclaration):   Value = cssStub("visitCssDeclaration")
  override def visitCssImport(node:        CssImport):        Value = cssStub("visitCssImport")
  override def visitCssKeyframeBlock(node: CssKeyframeBlock): Value = cssStub("visitCssKeyframeBlock")
  override def visitCssMediaRule(node:     CssMediaRule):     Value = cssStub("visitCssMediaRule")
  override def visitCssStyleRule(node:     CssStyleRule):     Value = cssStub("visitCssStyleRule")
  override def visitCssStylesheet(node:    CssStylesheet):    Value = cssStub("visitCssStylesheet")
  override def visitCssSupportsRule(node:  CssSupportsRule):  Value = cssStub("visitCssSupportsRule")
}
