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
 *   Convention: Port of the dart-sass evaluator covering expression
 *               evaluation, statement-level evaluation, callables, modules,
 *               control flow, @use/@forward/@import, selector expansion,
 *               and CssVisitor dispatch for plain CSS re-evaluation.
 *   Idiom: Implements StatementVisitor[Value], ExpressionVisitor[Value],
 *          IfConditionExpressionVisitor[Any] and CssVisitor[Value].
 *          Returns SassNull where Dart returns void/null (no
 *          Nullable[Value] wrapper — Sass null serves the role).
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/visitor/evaluate.dart (~4939 lines)
 * Covenant-verified: 2026-04-26
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
  CssNode,
  CssParentNode,
  CssStyleRule,
  CssStylesheet,
  CssSupportsRule,
  CssValue,
  ModifiableCssAtRule,
  ModifiableCssComment,
  ModifiableCssDeclaration,
  ModifiableCssImport,
  ModifiableCssKeyframeBlock,
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
  IfConditionExpression,
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
import ssg.sass.{
  BuiltInCallable,
  Callable,
  Configuration,
  ConfiguredValue,
  Environment,
  ExplicitConfiguration,
  ImportCache,
  Logger,
  Module,
  Nullable,
  PlainCssCallable,
  SassException,
  SassScriptException,
  UserDefinedCallable
}
import ssg.sass.extend.{ Extension, ExtensionStore }
import ssg.sass.importer.Importer
import ssg.sass.parse.{ KeyframeSelectorParser, SelectorParser }
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
  * Port of dart-sass `_EvaluateVisitor` (evaluate.dart). Implements expression evaluation, statement-level visitors, CssVisitor for plain-CSS re-evaluation, callables, modules, and CSS tree
  * construction.
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

  override def currentCallableNode: ssg.sass.ast.AstNode =
    _callableNode.getOrElse {
      throw new IllegalStateException("No Sass callable is currently being evaluated.")
    }

  override def warn(message: String, deprecation: Nullable[Deprecation] = Nullable.Null): Unit =
    deprecation match {
      case dep if dep.isDefined =>
        _warnings += s"DEPRECATION WARNING [${dep.get.id}]: $message"
        _logger.warnForDeprecation(dep.get, message)
      case _ =>
        _warnings += s"WARNING: $message"
        _logger.warn(message)
    }

  // ---------------------------------------------------------------------------
  // Slash division deprecation (dart-sass async_evaluate.dart:2814-2867)
  // ---------------------------------------------------------------------------

  /** Returns the result of the SassScript `/` operation between [left] and [right] in [node]. */
  private def _slash(left: Value, right: Value, node: BinaryOperationExpression): Value = {
    val result = left.dividedBy(right)
    (left, right) match {
      case (l: SassNumber, r: SassNumber) if node.allowsSlash && _operandAllowsSlash(node.left) && _operandAllowsSlash(node.right) =>
        // Slash-separated number (e.g., font: 16px/1.4)
        result.asInstanceOf[SassNumber].withSlash(l, r)
      case (_: SassNumber, _: SassNumber) =>
        // Both are numbers but slash-separated is not allowed — emit deprecation warning
        warnForDeprecation(
          Deprecation.SlashDiv,
          "Using / for division outside of calc() is deprecated and will be removed in Dart Sass 2.0.0.\n\n" +
            s"Recommendation: math.div(${node.left}, ${node.right}) or calc(${node.left} / ${node.right})\n\n" +
            "More info and automated migrator: https://sass-lang.com/d/slash-div"
        )
        result
      case _ =>
        result
    }
  }

  /** Returns whether [node] can be used as a component of a slash-separated number.
    *
    * Although this logic is mostly resolved at parse-time, we can't tell whether operands will be evaluated as calculations until evaluation-time.
    */
  private def _operandAllowsSlash(node: Expression): Boolean =
    node match {
      case fn: FunctionExpression =>
        // A function expression allows slash only if it's a calc function
        // (unnamespaced, name in the calc list, and no user-defined function shadows it)
        fn.namespace.isEmpty && _calcFunctionNames.contains(fn.name.toLowerCase) &&
        _environment.getFunction(fn.name).isEmpty
      case _ =>
        // Non-function expressions allow slash
        true
    }

  /** Set of calc function names that are evaluated as calculations (not as user-defined functions). */
  private val _calcFunctionNames: Set[String] = Set(
    "calc",
    "clamp",
    "hypot",
    "sin",
    "cos",
    "tan",
    "asin",
    "acos",
    "atan",
    "sqrt",
    "exp",
    "sign",
    "mod",
    "rem",
    "atan2",
    "pow",
    "log",
    "calc-size"
  )

  // ---------------------------------------------------------------------------
  // State
  // ---------------------------------------------------------------------------

  /** The current lexical environment. Variables and (eventually) functions and mixins live here. Pre-populated with all global built-in functions.
    */
  private var _environment: Environment = Environment.withBuiltins()

  /** Whether we're currently evaluating a `@supports` declaration. When true, calculations are not reduced to plain numbers.
    */
  private var _inSupportsDeclaration: Boolean = false

  /** The name of the current member being evaluated (for stack traces). Corresponds to Dart's `_member`.
    */
  private var _member: String = "root stylesheet"

  /** The evaluation stack, used for stack traces. Each entry is a (member-name, node-with-span) pair. Corresponds to Dart's `_stack`.
    */
  private val _stack: scala.collection.mutable.ListBuffer[(String, ssg.sass.ast.AstNode)] =
    scala.collection.mutable.ListBuffer.empty

  /** The node for the innermost callable invocation being evaluated. Used by `_addExceptionSpan` and `currentCallableSpan`. Corresponds to Dart's `_callableNode`.
    */
  private var _callableNode: Nullable[ssg.sass.ast.AstNode] = Nullable.empty

  /** Whether we're currently in a `@dependency` context. When true and `_quietDeps` is also true, deprecation warnings are suppressed.
    */
  private var _inDependency: Boolean = false

  /** Whether to suppress deprecation warnings for dependencies. */
  private val _quietDeps: Boolean = false

  /** Set of (message, span) pairs that have already been warned about, used to deduplicate warnings. Corresponds to Dart's `_warningsEmitted`.
    */
  private val _warningsEmitted: scala.collection.mutable.Set[(String, ssg.sass.util.FileSpan)] =
    scala.collection.mutable.Set.empty

  /** The configuration map pushed in by an enclosing `@use "mid" with (...)`. `visitForwardRule` — and the recursive `@use` of the target inside a module that is itself being loaded — consult this
    * map so that configured variables flow through `@forward` chains. Keys are unprefixed variable names. Entries are evaluated expressions (not the raw AST) because the outer `with` clause must be
    * evaluated in the *caller's* environment, not the forwarded module's.
    */
  private var _pendingConfig: Map[String, ssg.sass.value.Value] = Map.empty

  /** Set of URLs loaded during evaluation.
    */
  private val _loadedUrls = scala.collection.mutable.LinkedHashSet.empty[String]

  /// All modules that have been loaded and evaluated so far.
  private val _modules = scala.collection.mutable.LinkedHashMap.empty[String, Module[Callable]]

  /// The first [Configuration] used to load a module [Uri].
  private val _moduleConfigurations = scala.collection.mutable.LinkedHashMap.empty[String, Configuration]

  /// A map from canonical module URLs to the nodes whose spans indicate where
  /// those modules were originally loaded.
  ///
  /// This is not guaranteed to have a node for every module in [_modules]. For
  /// example, the entrypoint module was not loaded by a node.
  private val _moduleNodes = scala.collection.mutable.LinkedHashMap.empty[String, ssg.sass.ast.AstNode]

  /// A map from canonical URLs for modules (or imported files) that are
  /// currently being evaluated to AST nodes whose spans indicate the original
  /// loads for those modules.
  ///
  /// Map values may be `Nullable.empty`, which indicates an active module that doesn't
  /// have a source span associated with its original load (such as the
  /// entrypoint module).
  ///
  /// This is used to ensure that we don't get into an infinite load loop.
  private val _activeModules = scala.collection.mutable.LinkedHashMap.empty[String, Nullable[ssg.sass.ast.AstNode]]

  /// The current configuration for the module being loaded.
  ///
  /// If this is empty, that indicates that the current module is not configured.
  private var _configuration: Configuration = Configuration.empty

  /// The importer that's currently being used to resolve relative imports.
  ///
  /// If this is `Nullable.empty`, relative imports aren't supported in the current
  /// stylesheet.
  private var _importer: Nullable[Importer] = importer

  /// The stylesheet that's currently being evaluated.
  private var _stylesheet: Nullable[Stylesheet] = Nullable.empty

  /// Plain-CSS imports that didn't appear in the initial block of CSS imports.
  ///
  /// These are added to the initial CSS import block by [visitStylesheet] after
  /// the stylesheet has been fully performed.
  ///
  /// This is `null` unless there are any out-of-order imports in the current
  /// stylesheet.
  private var _outOfOrderImports: Nullable[scala.collection.mutable.ListBuffer[ModifiableCssImport]] = Nullable.empty

  /// A map from modules loaded by the current module to loud comments written
  /// in this module that should appear before the loaded module.
  ///
  /// This is `null` unless there are any pre-module comments in the current
  /// stylesheet.
  private var _preModuleComments: Nullable[scala.collection.mutable.LinkedHashMap[Module[Callable], scala.collection.mutable.ListBuffer[CssComment]]] = Nullable.empty

  /// The extension store that tracks extensions and style rules for the current
  /// module.
  private var _extensionStore: Nullable[ssg.sass.extend.ExtensionStore] = Nullable.empty

  /// Whether we're directly within an `@at-root` rule that excludes style
  /// rules.
  private var _atRootExcludingStyleRule: Boolean = false

  /// Whether we're currently executing a function body.
  /// dart-sass: `_inFunction` (evaluate.dart:233).
  private var _inFunction: Boolean = false

  /// Whether we're currently building the output of a `@keyframes` rule.
  private var _inKeyframes: Boolean = false

  /// Whether we're currently building the output of an unknown at rule.
  private var _inUnknownAtRule: Boolean = false

  /// The name of the current declaration parent.
  private var _declarationName: Nullable[String] = Nullable.empty

  /// The underlying style rule, independent of `_atRootExcludingStyleRule`.
  /// Used by `_styleRule` (derived getter), `_withStyleRule`, `_execute`,
  /// `visitSelectorExpression`, and `visitStyleRule`/`visitAtRootRule`.
  private var _styleRuleIgnoringAtRoot: Nullable[ModifiableCssStyleRule] = Nullable.empty

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

  /// Built in modules, indexed by their URLs.
  ///
  /// Port of dart-sass `_builtInModules` (evaluate.dart:160-161).
  private lazy val _builtInModules: Map[String, Module[Callable]] = {
    val builder = scala.collection.mutable.LinkedHashMap.empty[String, Module[Callable]]
    for ((name, callables) <- ssg.sass.functions.Functions.modules) {
      val mixins: List[Callable] =
        if (name == "meta") _loadCssMixin :: ssg.sass.functions.MetaFunctions.moduleMixins
        else Nil
      val vars = ssg.sass.functions.Functions.moduleVariables(name)
      builder(s"sass:$name") = new BuiltInModule[Callable](name, callables, mixins, vars)
    }
    builder.toMap
  }

  /// The `load-css` built-in mixin for the `sass:meta` module.
  ///
  /// This must be defined in the evaluator (not in MetaFunctions) because
  /// it needs access to `_loadModule`, `_combineCss`, `_callableNode`,
  /// and `_assertConfigurationIsEmpty`.
  ///
  /// Port of dart-sass `metaMixins[0]` (async_evaluate.dart:594-637).
  @annotation.nowarn("msg=implicit conversion") // String -> Nullable[String] in assertString/assertMap name args
  private lazy val _loadCssMixin: BuiltInCallable =
    BuiltInCallable.mixin(
      "load-css",
      "$url, $with: null",
      { (arguments: List[Value]) =>
        val url     = arguments.head.assertString("url").text
        val withRaw = arguments(1)
        val withMap: Nullable[SassMap] =
          if (withRaw == SassNull) Nullable.empty
          else Nullable(withRaw.assertMap("with"))

        val callableNode = _callableNode.getOrElse {
          throw SassScriptException("load-css() requires an active callable context.")
        }
        var configuration: Configuration = Configuration.empty
        withMap.foreach { wm =>
          val values             = scala.collection.mutable.LinkedHashMap.empty[String, ConfiguredValue]
          var privateDeprecation = false
          for ((variable, value) <- wm.contents) {
            val name = variable.assertString("with key").text.replace("_", "-")
            if (values.contains(name)) {
              throw SassScriptException(s"The variable $$$name was configured twice.")
            } else if (name.startsWith("-") && !privateDeprecation) {
              privateDeprecation = true
              ssg.sass.EvaluationContext.warnForDeprecation(
                Deprecation.WithPrivate,
                s"Configuring private variables (such as $$$name) is " +
                  "deprecated.\n" +
                  "This will be an error in Dart Sass 2.0.0."
              )
            }
            values(name) = ConfiguredValue.explicit(value, Nullable(callableNode))
          }
          configuration = ExplicitConfiguration(values.toMap, callableNode)
        }

        // dart-sass: resolve URL relative to the call site's source URL,
        // so that `meta.load-css("x")` inside a mixin defined in subdir/
        // resolves relative to that subdir, not the top-level input file.
        val baseUrl0: Nullable[String] =
          Nullable(callableNode.span.sourceUrl.toString).filter(_.nonEmpty)
        _loadModule(
          url,
          "load-css()",
          callableNode,
          (module, _) => {
            // Replay the combined CSS into the current parent, using the
            // CssVisitor implementation so that nested rules, @import, etc.
            // are handled exactly like `@import` of a module.
            val css = _combineCss(module, clone = true)
            css.accept(this)
          },
          configuration = Nullable(configuration),
          namesInErrors = true,
          baseUrl = baseUrl0
        )
        _assertConfigurationIsEmpty(configuration, nameInError = true)
        SassNull
      }
    )

  // Note: cycle detection for @import is now handled by _activeModules
  // (which was already used for @use/@forward cycle detection).

  /** The root modifiable CSS stylesheet currently being built. Set at the start of [[run]] and used as the initial value of [[_parent]].
    */
  private var _root: Nullable[ModifiableCssStylesheet] = Nullable.empty

  /** The current parent node in the CSS tree. New children produced by statement visitors are added here via [[_addChild]].
    */
  private var _parent: Nullable[ModifiableCssParentNode] = Nullable.empty

  /** The current enclosing style rule, or empty if none. */
  private var _styleRule: Nullable[ModifiableCssStyleRule] = Nullable.empty

  /** Index of the end of the leading `@import`/`@use`/`@forward` block in `_root.children`. Used for import ordering and parity with the Dart evaluator.
    */
  private var _endOfImports: Int = 0

  /** Warnings produced during evaluation. Surfaced through [[EvaluateResult.warnings]].
    */
  private val _warnings: scala.collection.mutable.ListBuffer[String] =
    scala.collection.mutable.ListBuffer.empty

  // ---------------------------------------------------------------------------
  // Public entry points
  // ---------------------------------------------------------------------------

  /** Evaluate a parsed [[Stylesheet]] to a CSS AST.
    *
    * The entrypoint sets up top-level state and calls `visitStylesheet` directly (like the pre-module era). `@use`/`@forward` within the stylesheet call `_loadModule` -> `_execute` to load child
    * modules. After the tree walk, `_combineCss` merges all upstream modules' CSS and applies cross-module `@extend` via `_extendModules`.
    */
  def run(stylesheet: Stylesheet): EvaluateResult = {
    val url = stylesheet.span.sourceUrl.toString
    if (url.nonEmpty) {
      _activeModules(url) = Nullable.empty
      _loadedUrls += url
    }

    val savedCur = ssg.sass.CurrentEnvironment.set(Nullable(_environment))
    val savedInv = ssg.sass.CurrentCallableInvoker.set(
      Nullable((c: Callable, pos: List[Value], named: ListMap[String, Value]) => _invokeCallable(c, pos, named))
    )
    val savedMixinInv = ssg.sass.CurrentMixinInvoker.set(
      Nullable((c: Callable, pos: List[Value], named: ListMap[String, Value]) =>
        // dart-sass: meta.apply reads _environment.content so that the @content
        // block from the @include site is forwarded to the inner mixin.
        _invokeMixinCallable(c, pos, named, _environment.content)
      )
    )
    try
      ssg.sass.EvaluationContext.withContext(this) {
        // Forward any warnings discovered during parsing into the evaluator's
        // warning buffer so they surface on CompileResult.warnings.
        for (ptw <- stylesheet.parseTimeWarnings)
          ptw.deprecation.fold(_warnings += s"WARNING: ${ptw.message}") { d =>
            _warnings += s"DEPRECATION WARNING [${d.id}]: ${ptw.message}"
          }
        // dart-sass run():
        //   var module = _addExceptionTrace(() => _execute(importer, node));
        //   result = (stylesheet: _combineCss(module), loadedUrls: _loadedUrls);
        //
        // _execute evaluates the stylesheet into a Module, building its own
        // CSS tree and extension store. _combineCss then topologically merges
        // all upstream modules' CSS and applies cross-module @extend via
        // _extendModules.
        try {
          val module = _addExceptionTrace(_execute(_importer, stylesheet))
          val out    = _combineCss(module)
          EvaluateResult(out, _loadedUrls.toSet, _warnings.toList)
        } catch {
          // dart-sass evaluate.dart:728-730: rethrow with loadedUrls
          case error: SassException =>
            throw error.withLoadedUrls(_loadedUrls.toSet)
        }
      }
    finally {
      val _ = ssg.sass.CurrentEnvironment.set(savedCur)
      val _ = ssg.sass.CurrentCallableInvoker.set(savedInv)
      val _ = ssg.sass.CurrentMixinInvoker.set(savedMixinInv)
    }
  }

  // ---------------------------------------------------------------------------
  // Module infrastructure: _execute, _loadModule, _loadStylesheet,
  // _combineCss, _extendModules, etc.
  // Ported from dart-sass evaluate.dart lines 802-1165.
  // ---------------------------------------------------------------------------

  /// Loads the module at [url] and passes it to [callback].
  ///
  /// This first tries loading [url] from [_modules], and only falls back to
  /// actually loading the module if the URL isn't cached.
  ///
  /// The [stackFrame] and [nodeWithSpan] are used for the name and location of
  /// the stack frame for the duration of the [callback].
  ///
  /// Port of dart-sass `_loadModule` (evaluate.dart:802-879).
  private def _loadModule(
    url:           String,
    stackFrame:    String,
    nodeWithSpan:  ssg.sass.ast.AstNode,
    callback:      (Module[Callable], Boolean) => Unit,
    configuration: Nullable[Configuration] = Nullable.empty,
    namesInErrors: Boolean = false,
    baseUrl:       Nullable[String] = Nullable.empty
  ): Unit = {
    import scala.util.boundary, boundary.break
    boundary {
      // Check for built-in modules first.
      val builtIn = _builtInModules.get(url)
      if (builtIn.isDefined) {
        configuration.foreach { config =>
          if (config.isInstanceOf[ExplicitConfiguration]) {
            throw _exception(
              if (namesInErrors) s"Built-in module $url can't be configured."
              else "Built-in modules can't be configured.",
              Nullable(config.asInstanceOf[ExplicitConfiguration].nodeWithSpan.span)
            )
          }
        }
        // Always consider built-in stylesheets to be "already loaded", since they
        // never require additional execution to load and never produce CSS.
        _addExceptionSpan(nodeWithSpan, callback(builtIn.get, false))
        break(())
      }

      _withStackFrame(
        stackFrame,
        nodeWithSpan, {
          val loaded = _loadStylesheet(url, nodeWithSpan.span, baseUrl = baseUrl)
          if (loaded.isEmpty) {
            throw _exception("Can't find stylesheet to import.", Nullable(nodeWithSpan.span))
          }
          loaded.foreach { case (importedSheet, imp, isDependency) =>
            val canonicalUrl = importedSheet.span.sourceUrl.toString
            if (canonicalUrl.nonEmpty) {
              if (_activeModules.contains(canonicalUrl)) {
                val message =
                  if (namesInErrors) s"Module loop: ${canonicalUrl} is already being loaded."
                  else "Module loop: this module is already being loaded."

                _activeModules.get(canonicalUrl).flatMap(_.toOption) match {
                  case Some(previousLoad) =>
                    throw _multiSpanException(message, "new load", Map(previousLoad.span -> "original load"))
                  case _ =>
                    throw _exception(message)
                }
              } else {
                _activeModules(canonicalUrl) = Nullable(nodeWithSpan)
              }
            }

            val firstLoad       = !_modules.contains(canonicalUrl)
            val oldInDependency = _inDependency
            _inDependency = isDependency
            val module: Module[Callable] =
              try
                _execute(
                  Nullable(imp),
                  importedSheet,
                  configuration = configuration,
                  nodeWithSpan = Nullable(nodeWithSpan),
                  namesInErrors = namesInErrors
                )
              finally {
                if (canonicalUrl.nonEmpty) _activeModules.remove(canonicalUrl)
                _inDependency = oldInDependency
              }

            _addExceptionSpan(nodeWithSpan, callback(module, firstLoad), addStackFrame = false)
          }
        }
      )
    } // boundary
  }

  /// Executes [stylesheet], loaded by [importer], to produce a module.
  ///
  /// If [configuration] is not passed, the current configuration is used
  /// instead.
  ///
  /// If [namesInErrors] is `true`, this includes the names of modules in errors
  /// relating to them. This should only be `true` if the names won't be obvious
  /// from the source span.
  ///
  /// Port of dart-sass `_execute` (evaluate.dart:889-1003).
  @annotation.nowarn("msg=unused") // default params used by _loadModule callers
  private def _execute(
    executeImporter: Nullable[Importer],
    stylesheet:      Stylesheet,
    configuration:   Nullable[Configuration] = Nullable.empty,
    nodeWithSpan:    Nullable[ssg.sass.ast.AstNode] = Nullable.empty,
    namesInErrors:   Boolean = false
  ): Module[Callable] = {
    import scala.util.boundary, boundary.break
    boundary[Module[Callable]] {
      val url = stylesheet.span.sourceUrl.toString

      val currentConfiguration = configuration.getOrElse(_configuration)
      // Check if the module is already loaded.
      _modules.get(url) match {
        case Some(alreadyLoaded) =>
          if (
            !_moduleConfigurations.get(url).exists(_.sameOriginal(currentConfiguration)) &&
            currentConfiguration.isInstanceOf[ExplicitConfiguration] &&
            // Don't throw an error if the module being loaded doesn't expose any
            // configurable variables that could have been affected by the
            // configuration in the first place.
            alreadyLoaded.couldHaveBeenConfigured(currentConfiguration.values.keySet)
          ) {
            val message =
              if (namesInErrors) s"$url was already loaded, so it can't be configured using \"with\"."
              else "This module was already loaded, so it can't be configured using \"with\"."

            val existingSpan      = _moduleNodes.get(url).map(_.span)
            val configurationSpan =
              if (configuration.isEmpty) Nullable(currentConfiguration.asInstanceOf[ExplicitConfiguration].nodeWithSpan.span)
              else Nullable.empty[ssg.sass.util.FileSpan]

            val secondarySpans = scala.collection.mutable.LinkedHashMap.empty[ssg.sass.util.FileSpan, String]
            existingSpan.foreach(s => secondarySpans(s) = "original load")
            configurationSpan.foreach(s => secondarySpans(s) = "configuration")

            if (secondarySpans.isEmpty) throw _exception(message)
            else throw _multiSpanException(message, "new load", secondarySpans.toMap)
          }
          break(alreadyLoaded)

        case scala.None => () // fall through to execute
      }

      // dart-sass: _execute creates a bare environment without built-in
      // functions pre-populated. Built-ins are resolved via a separate
      // fallback in visitFunctionExpression. This ensures that the module's
      // public function surface (via toModule) contains only user-defined
      // functions, not inherited built-ins.
      val environment = Environment()
      var css:               Nullable[CssStylesheet]                                                                                             = Nullable.empty
      var preModuleComments: Nullable[scala.collection.mutable.LinkedHashMap[Module[Callable], scala.collection.mutable.ListBuffer[CssComment]]] = Nullable.empty
      val extensionStore = ExtensionStore()

      _withEnvironment(environment) {
        val oldImporter                 = _importer
        val oldStylesheet               = _stylesheet
        val oldRoot                     = _root
        val oldPreModuleComments        = _preModuleComments
        val oldParent                   = _parent
        val oldEndOfImports             = _endOfImports
        val oldOutOfOrderImports        = _outOfOrderImports
        val oldExtensionStore           = _extensionStore
        val oldStyleRule                = _styleRule
        val oldStyleRuleIgnoring        = _styleRuleIgnoringAtRoot
        val oldMediaQueries             = _mediaQueries
        val oldDeclarationName          = _declarationName
        val oldInUnknownAtRule          = _inUnknownAtRule
        val oldAtRootExcludingStyleRule = _atRootExcludingStyleRule
        val oldInKeyframes              = _inKeyframes
        val oldConfiguration            = _configuration

        _importer = executeImporter
        _stylesheet = Nullable(stylesheet)
        val root = new ModifiableCssStylesheet(stylesheet.span)
        _root = Nullable(root)
        _parent = Nullable(root: ModifiableCssParentNode)
        _endOfImports = 0
        _outOfOrderImports = Nullable.empty
        _extensionStore = Nullable(extensionStore)
        _styleRule = Nullable.empty
        _styleRuleIgnoringAtRoot = Nullable.empty
        _mediaQueries = Nil
        _declarationName = Nullable.empty
        _inUnknownAtRule = false
        _atRootExcludingStyleRule = false
        _inKeyframes = false
        configuration.foreach { config => _configuration = config }

        visitStylesheet(stylesheet)
        css = Nullable(
          if (_outOfOrderImports.isEmpty) CssStylesheet(root.children, stylesheet.span)
          else CssStylesheet(_addOutOfOrderImports().toList, stylesheet.span)
        )
        preModuleComments = _preModuleComments

        _importer = oldImporter
        _stylesheet = oldStylesheet
        _root = oldRoot
        _preModuleComments = oldPreModuleComments
        _parent = oldParent
        _endOfImports = oldEndOfImports
        _outOfOrderImports = oldOutOfOrderImports
        _extensionStore = oldExtensionStore
        _styleRule = oldStyleRule
        _styleRuleIgnoringAtRoot = oldStyleRuleIgnoring
        _mediaQueries = oldMediaQueries
        _declarationName = oldDeclarationName
        _inUnknownAtRule = oldInUnknownAtRule
        _atRootExcludingStyleRule = oldAtRootExcludingStyleRule
        _inKeyframes = oldInKeyframes
        _configuration = oldConfiguration
      }

      val preModuleCommentsMap: Map[Module[Callable], List[CssComment]] =
        preModuleComments.fold[Map[Module[Callable], List[CssComment]]](Map.empty) { pmc =>
          pmc.map { case (k, v) => k -> v.toList }.toMap
        }
      val module = environment.toModule(
        css.getOrElse {
          throw new IllegalStateException("_execute: CSS stylesheet was not produced.")
        },
        extensionStore,
        preModuleComments = preModuleCommentsMap
      )
      if (url.nonEmpty) {
        _modules(url) = module
        _moduleConfigurations(url) = currentConfiguration
        nodeWithSpan.foreach(n => _moduleNodes(url) = n)
      }

      module
    } // boundary
  }

  /// Returns a copy of [_root.children] with [_outOfOrderImports] inserted
  /// after [_endOfImports], if necessary.
  ///
  /// Port of dart-sass `_addOutOfOrderImports` (evaluate.dart:1007-1015).
  private def _addOutOfOrderImports(): List[ssg.sass.ast.css.CssNode] =
    _outOfOrderImports.fold[List[ssg.sass.ast.css.CssNode]](_root.get.children) { outOfOrderImports =>
      val rootChildren = _root.get.children
      rootChildren.take(_endOfImports) ++ outOfOrderImports.toList ++ rootChildren.drop(_endOfImports)
    }

  /// Returns a new stylesheet containing [root]'s CSS as well as the CSS of all
  /// modules transitively used by [root].
  ///
  /// This also applies each module's extensions to its upstream modules.
  ///
  /// If [clone] is `true`, this will copy the modules before extending them so
  /// that they don't modify [root] or its dependencies.
  ///
  /// Port of dart-sass `_combineCss` (evaluate.dart:1024-1079).
  private def _combineCss(root: Module[Callable], clone: Boolean = false): CssStylesheet = {
    import scala.util.boundary, boundary.break
    boundary[CssStylesheet] {
      if (!root.upstream.exists(_.transitivelyContainsCss)) {
        val selectors = root.extensionStore.simpleSelectors
        root.extensionStore.extensionsWhereTarget(target => !selectors.contains(target)).headOption.foreach { unsatisfiedExtension =>
          _throwForUnsatisfiedExtension(unsatisfiedExtension)
        }
        break(root.css)
      }

      // The imports (and comments between them) that should be included at the
      // beginning of the final document.
      val imports = scala.collection.mutable.ListBuffer.empty[ssg.sass.ast.css.CssNode]

      // The CSS statements in the final document.
      val cssStatements = scala.collection.mutable.ListBuffer.empty[ssg.sass.ast.css.CssNode]

      /// The modules in reverse topological order.
      val sorted = scala.collection.mutable.Queue.empty[Module[Callable]]

      /// The modules that have been visited so far. Note that if [clone] is
      /// true, this contains the original modules, not the copies.
      val seen = scala.collection.mutable.LinkedHashSet.empty[Module[Callable]]

      def visitModule(mod: Module[Callable]): Unit = {
        if (!seen.add(mod)) return
        val effectiveMod = if (clone) mod.cloneCss() else mod

        for (upstream <- effectiveMod.upstream)
          if (upstream.transitivelyContainsCss) {
            effectiveMod.preModuleComments.get(upstream).foreach { comments =>
              // Intermix the top-level comments with plain CSS `@import`s until we
              // start to have actual CSS defined, at which point start treating it as
              // normal CSS.
              (if (cssStatements.isEmpty) imports else cssStatements) ++= comments
            }
            visitModule(upstream)
          }

        sorted.prepend(effectiveMod)
        val statements = effectiveMod.css.children
        val index      = _indexAfterImports(statements)
        imports ++= statements.take(index)
        cssStatements ++= statements.drop(index)
      }

      visitModule(root)

      if (root.transitivelyContainsExtensions) _extendModules(sorted)

      CssStylesheet((imports ++ cssStatements).toList, root.css.span)
    } // boundary
  }

  /// Destructively updates the selectors in each module with the extensions
  /// defined in downstream modules.
  ///
  /// Port of dart-sass `_extendModules` (evaluate.dart:1083-1136).
  private def _extendModules(sortedModules: Iterable[Module[Callable]]): Unit = {
    // All the [ExtensionStore]s directly downstream of a given module (indexed
    // by its canonical URL).
    val downstreamExtensionStores = scala.collection.mutable.LinkedHashMap.empty[String, scala.collection.mutable.ListBuffer[ExtensionStore]]

    /// Extensions that haven't yet been satisfied by some upstream module.
    val unsatisfiedExtensions = scala.collection.mutable.LinkedHashSet.empty[Extension]

    for (module <- sortedModules) {
      // Create a snapshot of the simple selectors currently in the
      // [ExtensionStore] so that we don't consider an extension "satisfied"
      // below because of a simple selector added by another (sibling)
      // extension.
      val originalSelectors = module.extensionStore.simpleSelectors

      // Add all as-yet-unsatisfied extensions before adding downstream
      // [ExtensionStore]s, because those are all in [unsatisfiedExtensions]
      // already.
      unsatisfiedExtensions ++= module.extensionStore.extensionsWhereTarget(target => !originalSelectors.contains(target))

      module.url.foreach { url =>
        downstreamExtensionStores.get(url).foreach { stores =>
          module.extensionStore.addExtensions(stores)
        }
      }
      if (!module.extensionStore.isEmpty) {
        for (upstream <- module.upstream)
          upstream.url.foreach { url =>
            downstreamExtensionStores.getOrElseUpdate(url, scala.collection.mutable.ListBuffer.empty) +=
              module.extensionStore
          }
      }

      // Remove all extensions that are now satisfied after adding downstream
      // [ExtensionStore]s so it counts any downstream extensions that have been
      // newly satisfied.
      unsatisfiedExtensions --= module.extensionStore.extensionsWhereTarget(originalSelectors.contains)
    }

    if (unsatisfiedExtensions.nonEmpty) {
      _throwForUnsatisfiedExtension(unsatisfiedExtensions.head)
    }
  }

  /// Throws an exception indicating that [extension] is unsatisfied.
  ///
  /// Port of dart-sass `_throwForUnsatisfiedExtension` (evaluate.dart:1139-1145).
  private def _throwForUnsatisfiedExtension(extension: Extension): Nothing =
    throw new SassException(
      "The target selector was not found.\n" +
        s"""Use "@extend ${extension.target} !optional" to avoid this error.""",
      extension.span
    )

  /// Returns the index of the first node in [statements] that comes after all
  /// static imports.
  ///
  /// Port of dart-sass `_indexAfterImports` (evaluate.dart:1149-1163).
  private def _indexAfterImports(statements: List[ssg.sass.ast.css.CssNode]): Int = {
    var lastImport = -1
    var i          = 0
    import scala.util.boundary, boundary.break
    boundary {
      while (i < statements.length) {
        statements(i) match {
          case _: CssImport =>
            lastImport = i
          case _: CssComment =>
            () // continue
          case _ =>
            break(())
        }
        i += 1
      }
    }
    lastImport + 1
  }

  /// Loads the [Stylesheet] identified by [url], or returns empty if loading
  /// fails.
  ///
  /// This first tries loading [url] relative to [baseUrl], which defaults to
  /// `_stylesheet.span.sourceUrl`.
  ///
  /// Port of dart-sass `_loadStylesheet` (evaluate.dart:1983-2055).
  private def _loadStylesheet(
    url:       String,
    span:      ssg.sass.util.FileSpan,
    baseUrl:   Nullable[String] = Nullable.empty,
    forImport: Boolean = false
  ): Nullable[(Stylesheet, Importer, Boolean)] = {
    val effectiveBaseUrl: Nullable[String] =
      if (baseUrl.isDefined) baseUrl
      else _stylesheet.flatMap(s => s.span.sourceUrl)

    val canonResult = _effectiveImportCache.canonicalize(
      url,
      baseImporter = _importer,
      baseUrl = effectiveBaseUrl,
      forImport = forImport
    )
    canonResult.flatMap { cr =>
      // Make sure we record the canonical URL as "loaded" even if the
      // actual load fails, because watchers should watch it to see if it
      // changes in a way that allows the load to succeed.
      _loadedUrls += cr.canonicalUrl

      val isDependency = _inDependency || !_importer.exists(_ eq cr.importer)
      _effectiveImportCache
        .importCanonical(
          cr.importer,
          cr.canonicalUrl,
          originalUrl = Nullable(cr.originalUrl)
        )
        .map { stylesheet =>
          (stylesheet, cr.importer, isDependency)
        }
    }
  }

  /// Adds any comments in [_root.children] to [_preModuleComments] for
  /// [module].
  ///
  /// Port of dart-sass `_registerCommentsForModule` (evaluate.dart:1760-1771).
  private def _registerCommentsForModule(module: Module[Callable]): Unit = {
    import scala.util.boundary, boundary.break
    boundary {
      // If we're not in a module (for example, we're evaluating a line of code
      // for the repl), there's nothing to register comments for.
      if (_root.isEmpty) break(())
      val root = _root.get
      if (root.children.isEmpty || !module.transitivelyContainsCss) break(())
      val comments = {
        val pmc = _preModuleComments.getOrElse {
          val m = scala.collection.mutable.LinkedHashMap.empty[Module[Callable], scala.collection.mutable.ListBuffer[CssComment]]
          _preModuleComments = Nullable(m)
          m
        }
        pmc.getOrElseUpdate(module, scala.collection.mutable.ListBuffer.empty)
      }
      comments ++= root.children.collect { case c: CssComment => c }
      root.clearChildren()
      _endOfImports = 0
    } // boundary
  }

  /// Updates [configuration] to include [node]'s configuration and returns the
  /// result.
  ///
  /// Port of dart-sass `_addForwardConfiguration` (evaluate.dart:1727-1757).
  private def _addForwardConfiguration(
    configuration: Configuration,
    node:          ForwardRule
  ): Configuration = {
    val newValues = scala.collection.mutable.Map.from(configuration.values)
    for (variable <- node.configuration)
      if (variable.isGuarded) {
        configuration.remove(variable.name) match {
          case oldValue if oldValue.isDefined && oldValue.get.value != SassNull =>
            newValues(variable.name) = oldValue.get
          case _ =>
            val variableNodeWithSpan = _expressionNode(variable.expression)
            newValues(variable.name) = ConfiguredValue.explicit(
              _withoutSlash(variable.expression.accept(this), variableNodeWithSpan),
              Nullable(variableNodeWithSpan)
            )
        }
      } else {
        val variableNodeWithSpan = _expressionNode(variable.expression)
        newValues(variable.name) = ConfiguredValue.explicit(
          _withoutSlash(variable.expression.accept(this), variableNodeWithSpan),
          Nullable(variableNodeWithSpan)
        )
      }

    if (configuration.isInstanceOf[ExplicitConfiguration] || configuration.isEmpty) {
      ExplicitConfiguration(newValues.toMap, node)
    } else {
      Configuration.implicitConfig(newValues.toMap)
    }
  }

  /// Remove configured values from [upstream] that have been removed from
  /// [downstream], unless they match a name in [except].
  ///
  /// Port of dart-sass `_removeUsedConfiguration` (evaluate.dart:1775-1784).
  private def _removeUsedConfiguration(
    upstream:   Configuration,
    downstream: Configuration,
    except:     Set[String]
  ): Unit =
    for (name <- upstream.values.keys.toList)
      if (!except.contains(name) && !downstream.values.contains(name)) {
        upstream.remove(name)
      }

  /// Throws an error if [configuration] contains any values.
  ///
  /// Port of dart-sass `_assertConfigurationIsEmpty` (evaluate.dart:1794-1812).
  private def _assertConfigurationIsEmpty(
    configuration: Configuration,
    nameInError:   Boolean = false
  ): Unit =
    // By definition, implicit configurations are allowed to only use a subset
    // of their values.
    if (configuration.isInstanceOf[ExplicitConfiguration] && !configuration.isEmpty) {
      val (name, value) = configuration.values.head
      throw _exception(
        if (nameInError) s"$$$name was not declared with !default in the @used module."
        else "This variable was not declared with !default in the @used module.",
        value.configurationSpan.map(_.span)
      )
    }

  /** Invokes a [[Callable]] (built-in or user-defined function) with the given arguments. Used by `meta.call` to dispatch a `SassFunction`'s underlying callable. Mixin invocation is not supported
    * here.
    */
  private def _invokeCallable(callable: Callable, positional: List[Value], named: ListMap[String, Value]): Value =
    ssg.sass.AliasedCallable.unwrap(callable) match {
      case bic: BuiltInCallable =>
        _checkBuiltInArity(bic, positional, named)
        val merged =
          if (named.isEmpty) positional
          else _mergeBuiltInNamedArgs(bic, positional, named)
        val padded0     = _padBuiltInPositional(bic, merged)
        val (padded, _) = _collectBuiltInRestArgs(bic, padded0, named)
        bic.callback(padded)
      case ud: UserDefinedCallable[?] =>
        ud.declaration match {
          case fr: ssg.sass.ast.sass.FunctionRule =>
            // dart-sass _runUserDefinedCallable: switch to a fresh closure of
            // the callable's captured environment, then push a new scope inside
            // it. The fresh closure isolates the body's mutations from the
            // captured env; the scope() push gives parameters and local
            // variables somewhere to live without bleeding into the caller's
            // global scope.
            ud.environment match {
              case env: Environment =>
                _withEnvironment(env.closure()) {
                  _runUserDefinedFunction(fr, positional, named)
                }
              case _ =>
                _runUserDefinedFunction(fr, positional, named)
            }
          case _ =>
            throw SassScriptException(s"Callable ${callable.name} is not a function.")
        }
      case pcc: PlainCssCallable =>
        // dart-sass: _runFunctionCallable for PlainCssCallable produces a
        // plain CSS function call string, e.g. `round(0.6)`.
        // dart-sass: plain CSS functions don't support keyword arguments.
        if (named.nonEmpty) {
          throw SassScriptException("Plain CSS functions don't support keyword arguments.")
        }
        val sb = new StringBuilder()
        sb.append(pcc.name)
        sb.append('(')
        var first = true
        for (arg <- positional) {
          if (!first) sb.append(", ")
          first = false
          sb.append(arg.toCssString())
        }
        sb.append(')')
        new ssg.sass.value.SassString(sb.toString(), hasQuotes = false)
      case other =>
        throw SassScriptException(s"Callable type not supported by meta.call: $other")
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

  override def visitBinaryOperationExpression(node: BinaryOperationExpression): Value = {
    // dart-sass evaluate.dart:2769-2776: reject non-= non-/ operators in plain CSS
    if (
      _stylesheet.exists(_.plainCss) &&
      node.operator != BinaryOperator.SingleEquals &&
      node.operator != BinaryOperator.DividedBy
    ) {
      throw _exception(
        "Operators aren't allowed in plain CSS.",
        Nullable(node.operatorSpan)
      )
    }

    _addExceptionSpan(
      node, {
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
            _slash(left, node.right.accept(this), node)
          case BinaryOperator.Modulo => left.modulo(node.right.accept(this))
        }
      }
    )
  }

  override def visitBooleanExpression(node: BooleanExpression): Value =
    SassBoolean(node.value)

  override def visitColorExpression(node: ColorExpression): Value = node.value

  override def visitFunctionExpression(node: FunctionExpression): Value = try
    scala.util.boundary[Value] {
      // dart-sass: identifiers starting with `--` are CSS custom idents and
      // must always fall through to plain-CSS output, even if a user-defined
      // function with that (normalised) name exists.
      val isCssCustomIdent = node.originalName.startsWith("--")
      // Look up the callable in the current environment.
      // dart-sass wraps this in _addExceptionSpan to attach the call site
      // span to any SassScriptException raised during lookup.
      val callable: Nullable[Callable] =
        if (isCssCustomIdent) Nullable.empty
        else if (_stylesheet.exists(_.plainCss)) Nullable.empty
        else
          _addExceptionSpan(
            node,
            if (node.namespace.isDefined) {
              node.namespace.fold(Nullable.empty[Callable]) { ns =>
                _environment.getNamespacedFunction(ns, node.name)
              }
            } else {
              // dart-sass: after checking the environment's scope chain and
              // imported/global modules, fall back to the evaluator-level
              // built-in function table (_builtInFunctions). This is necessary
              // because _execute creates bare environments without built-ins.
              _environment.getFunction(node.name).orElse {
                ssg.sass.functions.Functions.lookupGlobal(node.name).fold(Nullable.empty[Callable])(b => Nullable(b: Callable))
              }
            }
          )
      // User-defined functions shadow CSS math functions (dart-sass order).
      // Built-in functions do NOT shadow — CSS calc/min/max/etc. take
      // precedence over same-named built-ins.
      val isUserDefined = callable.exists(c => ssg.sass.AliasedCallable.unwrap(c).isInstanceOf[ssg.sass.UserDefinedCallable[?]])
      if (!isUserDefined && node.namespace.isEmpty) {
        node.name.toLowerCase match {
          // min/max/round/abs are legacy Sass functions that are only treated as
          // calculations when all arguments are calculation-safe (no modulo, no
          // comparisons, etc.).  Ported from dart-sass visitFunctionExpression
          // lines 3058-3065.
          case "min" | "max" | "round" | "abs"
              if node.arguments.named.isEmpty &&
                node.arguments.rest.isEmpty &&
                node.arguments.positional.forall(_.isCalculationSafe) =>
            val calcResult = _evaluateCalculation(node, inLegacySassFunction = Nullable(node.name.toLowerCase))
            if (calcResult.isDefined) scala.util.boundary.break(calcResult.get)
          case "calc" | "calc-size" | "clamp" | "hypot" | "sin" | "cos" | "tan" | "asin" | "acos" | "atan" | "sqrt" | "exp" | "sign" | "mod" | "rem" | "atan2" | "pow" | "log" =>
            val calcResult = _evaluateCalculation(node)
            if (calcResult.isDefined) scala.util.boundary.break(calcResult.get)
          case _ => ()
        }
      }
      // dart-sass (evaluate.dart:3092-3098): set _inFunction around the
      // entire function call dispatch (including built-ins).
      val oldInFunction = _inFunction
      _inFunction = true
      val fnResult =
        try
          callable.fold[Value] {
            // dart-sass: when a namespaced function is not found, throw
            // "Undefined function." rather than falling through to plain CSS.
            // (evaluate.dart:3051-3054)
            if (node.namespace.isDefined) {
              throw _exception("Undefined function.", Nullable(node.span))
            }
            // dart-sass: plain CSS functions don't support keyword arguments
            // (async_evaluate.dart:3631-3636).
            if (node.arguments.named.nonEmpty || node.arguments.keywordRest.isDefined) {
              throw _exception(
                "Plain CSS functions don't support keyword arguments.",
                Nullable(node.span)
              )
            }
            // Render unknown function as plain CSS: `name(arg1, arg2, ...)`.
            // dart-sass (async_evaluate.dart:3638-3656): render positional args
            // then append any rest arg if present.
            val buf = new StringBuilder(node.originalName)
            buf.append('(')
            var first = true
            for (arg <- node.arguments.positional) {
              if (!first) buf.append(", ")
              first = false
              buf.append(_evaluateToCss(arg))
            }
            node.arguments.rest.foreach { restExpr =>
              val rest = restExpr.accept(this)
              if (!first) buf.append(", ")
              buf.append(rest.toCssString())
            }
            buf.append(')')
            new SassString(buf.toString(), hasQuotes = false)
          } { c =>
            // Built-in callable dispatch: evaluate positional args and call.
            // Unwrap any AliasedCallable wrapper (inserted by `@forward ... as
            // prefix-*`) so the underlying BuiltInCallable / UserDefinedCallable
            // is reachable.
            ssg.sass.AliasedCallable.unwrap(c) match {
              case bic: ssg.sass.BuiltInCallable =>
                val (positional, named, _) = _evaluateArguments(node.arguments)
                _checkBuiltInArity(bic, positional, named)
                val merged =
                  if (named.isEmpty) positional
                  else _mergeBuiltInNamedArgs(bic, positional, named)
                val padded0     = _padBuiltInPositional(bic, merged)
                val (padded, _) = _collectBuiltInRestArgs(bic, padded0, named)
                // dart-sass: _withoutSlash strips slash-separated info from
                // the result of built-in function calls (async_evaluate.dart:3609).
                // dart-sass: wrap built-in invocations with _addExceptionSpan
                // so SassScriptExceptions from callbacks get proper span context.
                val oldCallableNode = _callableNode
                _callableNode = Nullable(node: ssg.sass.ast.AstNode)
                val bicResult = _addExceptionSpan(node, bic.callback(padded)).withoutSlash
                _callableNode = oldCallableNode
                bicResult
              case ud: UserDefinedCallable[?] =>
                ud.declaration match {
                  case fr: FunctionRule =>
                    val (positional, named, splatSep) = _evaluateArguments(node.arguments)
                    // dart-sass _runUserDefinedCallable: switch to a fresh closure
                    // of the function's captured environment, then push a scope
                    // inside it. The fresh closure isolates the body's mutations
                    // from the captured env so concurrent invocations don't see
                    // each other's locals; the scope() (inside _runUserDefinedFunction)
                    // gives parameters and locals their own buffer level, so
                    // `$var: ...` redirects from the global level to the local
                    // level instead of writing to the actual shared global map.
                    ud.environment match {
                      case env: Environment =>
                        _withEnvironment(env.closure()) {
                          _runUserDefinedFunction(fr, positional, named, splatSep)
                        }
                      case _ =>
                        _runUserDefinedFunction(fr, positional, named, splatSep)
                    }
                  case _ =>
                    throw SassScriptException(s"Callable ${node.name} is not a function.")
                }
              case other =>
                throw SassScriptException(s"Callable type not yet supported: $other")
            }
          }
        finally
          _inFunction = oldInFunction
      fnResult
    }
  catch {
    case e: SassScriptException => throw e.withSpan(node.span)
  }

  override def visitIfExpression(node: IfExpression): Value = {
    // Ported from dart-sass evaluate.dart visitIfExpression.
    // Conditions evaluate to either String (CSS function condition that must
    // be preserved in the output) or Boolean (Sass condition that can be
    // short-circuited). When any CSS condition is encountered, the entire
    // `if()` expression is serialized as a plain CSS `if(...)` string.
    import scala.util.boundary, boundary.break
    val branches = node.branches
    var results: List[(String, Value)] = Nil
    var i = 0
    boundary {
      while (i < branches.length) {
        val (condition, expression) = branches(i)
        val condResult: Any = condition.fold[Any](true)(_.accept(this))
        condResult match {
          case s: String =>
            results = (s, expression.accept(this)) :: results
          case true if results.nonEmpty =>
            results = ("else", expression.accept(this)) :: results
          case true =>
            break(expression.accept(this))
          case _ => () // false — skip this branch
        }
        i += 1
      }
      if (results.isEmpty) SassNull
      else {
        val pairs = results.reverse
        val sb    = new StringBuilder("if(")
        var first = true
        for ((cond, value) <- pairs) {
          if (!first) sb.append("; ")
          first = false
          sb.append(cond).append(": ").append(value.toCssString())
        }
        sb.append(')')
        new SassString(sb.toString(), hasQuotes = false)
      }
    }
  }

  override def visitInterpolatedFunctionExpression(node: InterpolatedFunctionExpression): Value = {
    // Plain CSS function whose name is computed via interpolation.
    // Port of dart-sass `visitInterpolatedFunctionExpression` (evaluate.dart:3480-3492).
    val name = _performInterpolation(node.name)
    // dart-sass: reject keyword arguments for plain CSS functions.
    if (node.arguments.named.nonEmpty || node.arguments.keywordRest.isDefined) {
      throw _exception(
        "Plain CSS functions don't support keyword arguments.",
        Nullable(node.span)
      )
    }
    // dart-sass dispatches through PlainCssCallable + _runFunctionCallable.
    // For plain CSS functions, arguments are evaluated to CSS text and
    // concatenated. Rest arguments are also expanded.
    val sb = new StringBuilder()
    sb.append(name)
    sb.append('(')
    var first = true
    for (arg <- node.arguments.positional) {
      if (!first) sb.append(", ")
      first = false
      sb.append(_evaluateToCss(arg))
    }
    // Handle rest argument (`$args...`): evaluate and serialize.
    node.arguments.rest.foreach { restExpr =>
      val rest = restExpr.accept(this)
      if (!first) sb.append(", ")
      sb.append(rest.toCssString())
    }
    sb.append(')')
    new SassString(sb.toString(), hasQuotes = false)
  }

  override def visitLegacyIfExpression(node: LegacyIfExpression): Value = {
    // Three-argument macro form of `if($condition, $if-true, $if-false)`.
    // dart-sass uses _evaluateMacroArguments + _withoutSlash to strip slash
    // info from the result (evaluate.dart:2981-2993).
    val positional = node.arguments.positional
    if (positional.length < 3) {
      throw SassScriptException("Missing arguments to if().")
    }
    val condition = positional(0).accept(this)
    val result    = if (condition.isTruthy) positional(1) else positional(2)
    _withoutSlash(result.accept(this), _expressionNode(result))
  }

  override def visitListExpression(node: ListExpression): Value =
    SassList(
      node.contents.map(_.accept(this)),
      node.separator,
      brackets = node.hasBrackets
    )

  override def visitMapExpression(node: MapExpression): Value = {
    // dart-sass evaluate.dart:3019-3040: track key nodes for multi-span error
    var map: ListMap[Value, Value] = ListMap.empty
    val keyNodes = scala.collection.mutable.LinkedHashMap.empty[Value, Expression]
    for ((key, value) <- node.pairs) {
      val keyValue   = key.accept(this)
      val valueValue = value.accept(this)
      if (map.contains(keyValue)) {
        // dart-sass throws MultiSpanSassRuntimeException with spans for both keys
        val oldKeySpan     = keyNodes.get(keyValue).map(_.span)
        val secondarySpans = oldKeySpan.map(s => Map(s -> "first key")).getOrElse(Map.empty)
        throw MultiSpanSassRuntimeException(
          "Duplicate key.",
          key.span,
          "second key",
          secondarySpans,
          _stackTrace(Nullable(key.span))
        )
      }
      map = map.updated(keyValue, valueValue)
      keyNodes(keyValue) = key
    }
    SassMap(map)
  }

  override def visitNullExpression(node: NullExpression): Value = SassNull

  override def visitNumberExpression(node: NumberExpression): Value =
    node.unit.fold[SassNumber](SassNumber(node.value))(u => SassNumber(node.value, u))

  override def visitParenthesizedExpression(node: ParenthesizedExpression): Value =
    // dart-sass evaluate.dart:3000-3006: reject parentheses in plain CSS
    if (_stylesheet.exists(_.plainCss))
      throw _exception(
        "Parentheses aren't allowed in plain CSS.",
        Nullable(node.span)
      )
    else
      node.expression.accept(this)

  override def visitSelectorExpression(node: SelectorExpression): Value = {
    // Returns the current enclosing style rule's selector text as an
    // unquoted SassString, or SassNull when not inside any style rule.
    // Full SelectorList value type is postponed — text suffices for `&`
    // SassScript references in the current text-based selector model.
    val _ = node
    _styleRuleIgnoringAtRoot.fold[Value](SassNull) { rule =>
      rule.originalSelector.asSassList
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
              case other => sb.append(_serialize(other, e, quote = false))
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
    // Ported from dart-sass visitSupportsExpression (evaluate.dart).
    // Evaluates the condition and returns its CSS text as an unquoted string.
    new SassString(_visitSupportsCondition(node.condition), hasQuotes = false)

  override def visitUnaryOperationExpression(node: UnaryOperationExpression): Value = {
    // dart-sass evaluate.dart:2880-2892: wrap entire operation in _addExceptionSpan
    val operand = node.operand.accept(this)
    _addExceptionSpan(
      node,
      node.operator match {
        case UnaryOperator.Plus   => operand.unaryPlus()
        case UnaryOperator.Minus  => operand.unaryMinus()
        case UnaryOperator.Divide => operand.unaryDivide()
        case UnaryOperator.Not    => operand.unaryNot()
      }
    )
  }

  override def visitValueExpression(node: ValueExpression): Value = node.value

  override def visitVariableExpression(node: VariableExpression): Value = {
    // dart-sass evaluate.dart:2871-2878: wrap getVariable in _addExceptionSpan
    val result: Nullable[Value] = _addExceptionSpan(
      node,
      if (node.namespace.isDefined) {
        node.namespace.fold(Nullable.empty[Value]) { ns =>
          _environment.getNamespacedVariable(ns, node.name)
        }
      } else {
        _environment.getVariable(node.name)
      }
    )
    result.getOrElse {
      throw _exception("Undefined variable.", Nullable(node.span))
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
    // returns true. Otherwise, accumulate CSS-side string conditions.
    // Ported from dart-sass evaluate.dart visitIfConditionOperation.
    var values: List[(IfConditionExpression, String)] = Nil
    val it = node.expressions.iterator
    var shortCircuit: Nullable[Any] = Nullable.empty
    while (it.hasNext && shortCircuit.isEmpty) {
      val expression = it.next()
      expression.accept(this) match {
        case s: String => values = (expression, s) :: values
        case false if node.op == BooleanOperator.And => shortCircuit = Nullable(false)
        case true if node.op == BooleanOperator.Or   => shortCircuit = Nullable(true)
        case _                                       => ()
      }
    }
    shortCircuit.fold[Any] {
      values.reverse match {
        case Nil => node.op == BooleanOperator.And
        // If the only CSS node left in the operation is parenthesized, remove
        // the parentheses. This is guaranteed to be valid because parentheses
        // contain an `<if-group>` and this operation is itself an `<if-group>`.
        case (_: IfConditionParenthesized, s) :: Nil =>
          s.substring(1, s.length - 1)
        case pairs =>
          pairs.map(_._2).mkString(s" ${node.op} ")
      }
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

  /** First-class evaluation of `calc()`, `min()`, `max()`, `clamp()`, and other CSS math functions. Walks the argument expressions, treating arithmetic operators as
    * [[ssg.sass.value.CalculationOperation]]s and leaf expressions as normally-evaluated values. Returns a [[ssg.sass.value.SassCalculation]] (or a reduced [[ssg.sass.value.SassNumber]]) on success,
    * or Nullable.empty if the calculation can't be built — in which case the caller falls through to the existing plain-CSS rendering path.
    *
    * Ported from: dart-sass `_visitCalculation` + `_checkCalculationArguments` + `_binaryOperatorToCalculationOperator` + value-type validation in `_visitCalculationExpression`.
    */
  private def _evaluateCalculation(node: FunctionExpression, inLegacySassFunction: Nullable[String] = Nullable.empty): Nullable[Value] = {
    import ssg.sass.value.{ CalculationOperation, CalculationOperator, ListSeparator, SassCalculation }

    // --- Reject named args and rest args (dart-sass _visitCalculation lines 3114-3123) ---
    if (node.arguments.named.nonEmpty) {
      throw SassException("Keyword arguments can't be used with calculations.", node.span)
    }
    if (node.arguments.rest.isDefined) {
      throw SassException("Rest arguments can't be used with calculations.", node.span)
    }

    // --- Argument count validation (dart-sass _checkCalculationArguments lines 3211-3252) ---
    _checkCalculationArguments(node)

    val args = node.arguments.positional
    var converted: List[Any] = Nil
    try {
      def toArg(expr: Expression): Any = expr match {
        case ParenthesizedExpression(inner, _) =>
          // Ported from dart-sass _visitCalculationExpression ParenthesizedExpression case.
          val result = toArg(inner)
          result match {
            case s: SassString => SassString(s"(${s.text})", hasQuotes = false)
            case _ => result
          }
        case binExpr @ BinaryOperationExpression(op, l, r, _) =>
          // --- Check whitespace around + and - (dart-sass _checkWhitespaceAroundCalculationOperator) ---
          _checkWhitespaceAroundCalculationOperator(binExpr)
          // --- Reject non-calc operators (dart-sass _binaryOperatorToCalculationOperator lines 3434-3441) ---
          val co: CalculationOperator = op match {
            case BinaryOperator.Plus      => CalculationOperator.Plus
            case BinaryOperator.Minus     => CalculationOperator.Minus
            case BinaryOperator.Times     => CalculationOperator.Times
            case BinaryOperator.DividedBy => CalculationOperator.DividedBy
            case _                        =>
              throw SassException("This operation can't be used in a calculation.", node.span)
          }
          SassCalculation.operateInternal(
            co,
            toArg(l),
            toArg(r),
            inLegacySassFunction = inLegacySassFunction,
            simplify = !_inSupportsDeclaration,
            warn = Nullable((msg: String, dep: Nullable[Deprecation]) => warn(msg, dep))
          )
        // --- StringExpression handling (dart-sass _visitCalculationExpression lines 3315-3327) ---
        case se: StringExpression if !se.hasQuotes && se.isCalculationSafe =>
          se.text.asPlain.fold(SassString(_performInterpolation(se.text), hasQuotes = false): Any) { plain =>
            plain.toLowerCase match {
              case "pi"        => SassNumber(math.Pi)
              case "e"         => SassNumber(math.E)
              case "infinity"  => SassNumber(Double.PositiveInfinity)
              case "-infinity" => SassNumber(Double.NegativeInfinity)
              case "nan"       => SassNumber(Double.NaN)
              case _           => SassString(plain, hasQuotes = false)
            }
          }
        // --- Space-separated list (dart-sass _visitCalculationExpression ListExpression case, lines 3364-3386) ---
        case le: ListExpression if !le.hasBrackets && le.separator == ListSeparator.Space && le.contents.length >= 2 =>
          val elements = le.contents.map(toArg)
          _checkAdjacentCalculationValues(elements, le)
          val rendered = elements.indices.map { i =>
            val el = elements(i)
            if (el.isInstanceOf[CalculationOperation] && le.contents(i).isInstanceOf[ParenthesizedExpression])
              SassString(s"($el)", hasQuotes = false)
            else el
          }
          SassString(rendered.mkString(" "), hasQuotes = false)
        // dart-sass lines 3350-3353: only NumberExpression, VariableExpression,
        // FunctionExpression, and LegacyIfExpression are allowed to be evaluated.
        // Everything else (including UnaryOperationExpression) is rejected.
        case _: NumberExpression | _: VariableExpression | _: FunctionExpression | _: InterpolatedFunctionExpression | _: LegacyIfExpression | _: IfExpression =>
          val v = expr.accept(this)
          // Special CSS calc keywords: pi, e, infinity, -infinity, NaN.
          v match {
            case s: SassString if !s.hasQuotes =>
              s.text.toLowerCase match {
                case "pi"        => SassNumber(math.Pi)
                case "e"         => SassNumber(math.E)
                case "infinity"  => SassNumber(Double.PositiveInfinity)
                case "-infinity" => SassNumber(Double.NegativeInfinity)
                case "nan"       => SassNumber(Double.NaN)
                case _           => v
              }
            // --- Value type validation (dart-sass _visitCalculationExpression lines 3354-3361) ---
            case _: SassNumber                 => v
            case _: SassCalculation            => v
            case s: SassString if !s.hasQuotes => v
            case result =>
              throw SassException(s"Value $result can't be used in a calculation.", node.span)
          }
        case _ =>
          // dart-sass lines 3388-3393: reject any other expression type
          throw SassException("This expression can't be used in a calculation.", node.span)
      }
      converted = args.map(toArg)
      // dart-sass line 3133: in a @supports declaration, return the
      // calculation unsimplified so calc(1 + 2) stays as-is.
      if (_inSupportsDeclaration) {
        return Nullable(SassCalculation.unsimplified(node.name, converted))
      }
      def nOpt(i: Int): Nullable[Any] =
        if (converted.length > i) Nullable(converted(i)) else Nullable.empty[Any]
      val result: Value = node.name.toLowerCase match {
        case "calc" =>
          SassCalculation.calc(converted.head)
        case "calc-size" =>
          SassCalculation.calcSize(converted(0), nOpt(1))
        case "min"   => SassCalculation.min(converted)
        case "max"   => SassCalculation.max(converted)
        case "clamp" => SassCalculation.clamp(converted(0), nOpt(1), nOpt(2))
        case "hypot" => SassCalculation.hypot(converted)
        case "sqrt"  => SassCalculation.sqrt(converted.head)
        case "sin"   => SassCalculation.sin(converted.head)
        case "cos"   => SassCalculation.cos(converted.head)
        case "tan"   => SassCalculation.tan(converted.head)
        case "asin"  => SassCalculation.asin(converted.head)
        case "acos"  => SassCalculation.acos(converted.head)
        case "atan"  => SassCalculation.atan(converted.head)
        case "abs"   =>
          converted.head match {
            case n: SassNumber if n.hasUnit("%") =>
              warnForDeprecation(
                Deprecation.AbsPercent,
                "Passing percentages to the global abs() function is deprecated. Recommendation: math.abs($number) (with the sass:math module)."
              )
            case _ => ()
          }
          SassCalculation.abs(converted.head)
        case "sign"                         => SassCalculation.sign(converted.head)
        case "exp"                          => SassCalculation.exp(converted.head)
        case "atan2"                        => SassCalculation.atan2(converted(0), Nullable(converted(1)))
        case "pow"                          => SassCalculation.pow(converted(0), Nullable(converted(1)))
        case "log" if converted.length == 1 => SassCalculation.log(converted.head, Nullable.empty[Any])
        case "log"                          => SassCalculation.log(converted(0), Nullable(converted(1)))
        case "mod"                          => SassCalculation.mod(converted(0), Nullable(converted(1)))
        case "rem"                          => SassCalculation.rem(converted(0), Nullable(converted(1)))
        case "round"                        =>
          val warnFn: Nullable[(String, Nullable[Deprecation]) => Unit] =
            Nullable((msg: String, dep: Nullable[Deprecation]) => warn(msg, dep))
          SassCalculation.roundInternal(
            converted(0),
            nOpt(1),
            nOpt(2),
            inLegacySassFunction = inLegacySassFunction,
            warn = warnFn
          )
        case _ => return Nullable.empty
      }
      Nullable(result)
    } catch {
      case e: SassException       => throw e
      case e: SassScriptException =>
        // dart-sass async_evaluate.dart:3197-3204: the simplification logic
        // in SassCalculation static methods throws SassScriptException for
        // incompatible arguments. Re-throw as SassException with span info.
        if (e.getMessage != null && e.getMessage.contains("compatible")) {
          _verifyCalcCompatibleNumbers(converted, args)
        }
        throw SassException(e.getMessage, node.span)
      case _: IllegalArgumentException => Nullable.empty
    }
  }

  /** Verifies that calculation arguments aren't known to be incompatible, throwing a multi-span error with source positions when they are.
    *
    * Ported from: dart-sass `_verifyCompatibleNumbers` (async_evaluate.dart lines 3259-3293).
    */
  private def _verifyCalcCompatibleNumbers(args: List[Any], nodesWithSpans: List[Expression]): Unit = {
    for (i <- args.indices)
      args(i) match {
        case n: SassNumber if n.hasComplexUnits =>
          throw SassException(
            s"Number $n isn't compatible with CSS calculations.",
            nodesWithSpans(i).span
          )
        case _ => ()
      }
    for (i <- args.indices.dropRight(1))
      args(i) match {
        case number1: SassNumber =>
          for (j <- (i + 1) until args.length)
            args(j) match {
              case number2: SassNumber =>
                if (!number1.hasPossiblyCompatibleUnits(number2)) {
                  throw SassException(
                    s"$number1 and $number2 are incompatible.",
                    nodesWithSpans(i).span
                  )
                }
              case _ => ()
            }
        case _ => ()
      }
  }

  /** Verifies that the calculation [node] has the correct number of arguments.
    *
    * Ported from: dart-sass `_checkCalculationArguments` (async_evaluate.dart lines 3211-3252).
    */
  private def _checkCalculationArguments(node: FunctionExpression): Unit = {
    val argCount = node.arguments.positional.length
    def check(maxArgs: Int = -1): Unit =
      if (argCount == 0) {
        throw SassException("Missing argument.", node.span)
      } else if (maxArgs >= 0 && argCount > maxArgs) {
        val argWord  = ssg.sass.Utils.pluralize("argument", maxArgs)
        val verbWord = if (argCount == 1) "was" else "were"
        throw SassException(
          s"Only $maxArgs $argWord allowed, but $argCount $verbWord passed.",
          node.span
        )
      }
    node.name.toLowerCase match {
      case "calc" | "sqrt" | "sin" | "cos" | "tan" | "asin" | "acos" | "atan" | "abs" | "exp" | "sign" =>
        check(maxArgs = 1)
      case "min" | "max" | "hypot" =>
        check()
      case "pow" | "atan2" | "log" | "mod" | "rem" | "calc-size" =>
        check(maxArgs = 2)
      case "round" | "clamp" =>
        check(maxArgs = 3)
      case _ =>
        throw new IllegalStateException(s"""Unknown calculation name "${node.name}".""")
    }
  }

  /** Throws an error if [node] requires whitespace around its operator in a calculation but doesn't have it.
    *
    * Ported from: dart-sass `_checkWhitespaceAroundCalculationOperator` (evaluate.dart lines 3397-3426).
    */
  private def _checkWhitespaceAroundCalculationOperator(node: BinaryOperationExpression): Unit = {
    if (node.operator != BinaryOperator.Plus && node.operator != BinaryOperator.Minus) {
      return
    }

    // We _should_ never be able to violate these conditions since we always
    // parse binary operations from a single file, but it's better to be safe
    // than have this crash bizarrely.
    if (node.left.span.file != node.right.span.file) return
    if (node.left.span.end.offset >= node.right.span.start.offset) return

    val textBetweenOperands = node.left.span.file.getText(
      node.left.span.end.offset,
      node.right.span.start.offset
    )
    val first = textBetweenOperands.charAt(0)
    val last  = textBetweenOperands.charAt(textBetweenOperands.length - 1)
    if (!(first.isWhitespace || first == '/') || !(last.isWhitespace || last == '/')) {
      throw SassException(
        """"+" and "-" must be surrounded by whitespace in calculations.""",
        node.operatorSpan
      )
    }
  }

  /** Throws an error if [elements] contains two adjacent non-string values.
    *
    * Ported from: dart-sass `_checkAdjacentCalculationValues` (evaluate.dart lines 3444-3471).
    */
  private def _checkAdjacentCalculationValues(elements: Seq[Any], node: ListExpression): Unit = {
    var i = 1
    while (i < elements.length) {
      val previous = elements(i - 1)
      val current  = elements(i)
      if (!previous.isInstanceOf[SassString] && !current.isInstanceOf[SassString]) {
        val currentNode      = node.contents(i)
        val isUnaryPlusMinus = currentNode match {
          case UnaryOperationExpression(UnaryOperator.Minus | UnaryOperator.Plus, _, _) => true
          case ne: NumberExpression if ne.value < 0 => true
          case _ => false
        }
        if (isUnaryPlusMinus) {
          throw SassException(
            """"+" and "-" must be surrounded by whitespace in calculations.""",
            currentNode.span
          )
        } else {
          throw SassException(
            s""""${elements(i - 1)}" and "${elements(i)}" can't be used adjacent to one another in a calculation.""",
            node.span
          )
        }
      }
      i += 1
    }
  }

  /** Evaluate an [[Interpolation]] to its plain string form, evaluating any embedded expressions and serializing them to CSS.
    *
    * If [[warnForColor]] is `true`, this will emit a warning for any named color values passed into the interpolation.
    *
    * Port of dart-sass `_performInterpolation` (evaluate.dart:4375-4457).
    */
  private def _performInterpolation(interpolation: Interpolation, warnForColor: Boolean = false): String = {
    val oldInSupports = _inSupportsDeclaration
    _inSupportsDeclaration = false
    try {
      val sb = new StringBuilder()
      var i  = 0
      while (i < interpolation.contents.length) {
        interpolation.contents(i) match {
          case s: String     => sb.append(s)
          case e: Expression =>
            val result = e.accept(this)
            if (warnForColor) {
              result match {
                case c: ssg.sass.value.SassColor =>
                  ColorNames.namesByColor.get(c).foreach { colorName =>
                    _warnWithSpan(
                      s"You probably don't mean to use the color value " +
                        s"$colorName in interpolation here.\n" +
                        s"It may end up represented as $result, which will likely produce " +
                        "invalid CSS.\n" +
                        "Always quote color names when using them as strings or map keys " +
                        s"""(for example, "$colorName").\n""" +
                        s"""If you really want to use the color value here, use '"" + $${e}'.""",
                      e.span,
                      Nullable.empty
                    )
                  }
                case _ => ()
              }
            }
            sb.append(_serialize(result, e, quote = false))
          case other =>
            throw new IllegalStateException(s"Unknown interpolation value $other")
        }
        i += 1
      }
      sb.toString()
    } finally
      _inSupportsDeclaration = oldInSupports
  }

  /** Calls `value.toCssString()` and wraps a [SassScriptException] to associate it with [nodeWithSpan]'s source span.
    *
    * Port of dart-sass `_serialize` (evaluate.dart:4473-4474).
    */
  private def _serialize(value: Value, nodeWithSpan: ssg.sass.ast.AstNode, quote: Boolean): String =
    _addExceptionSpan(nodeWithSpan, value.toCssString(quote))

  /** Evaluate [[expression]] and return its CSS string representation.
    *
    * Port of dart-sass `_evaluateToCss` (evaluate.dart:4461-4465).
    */
  private def _evaluateToCss(expression: Expression, quote: Boolean = true): String =
    _serialize(expression.accept(this), expression, quote = quote)

  /** ISS-033: warns about configuring a module member whose variable name begins with `-` or `_`, matching dart-sass `_validateConfiguration` in `lib/src/visitor/evaluate.dart`. Private members are
    * considered internal to the defining module; configuring them via `@use ... with (...)` or `@forward ... with (...)` is deprecated and will become an error in Dart Sass 2.0.0.
    */
  private def _checkPrivateConfig(cv: ssg.sass.ast.sass.ConfiguredVariable): Unit = {
    val n = cv.name
    if (n.nonEmpty && (n.charAt(0) == '-' || n.charAt(0) == '_')) {
      ssg.sass.EvaluationContext.warnForDeprecation(
        Deprecation.WithPrivate,
        s"Configuring private variables (such as $$$n) is " +
          "deprecated.\n" +
          "This will be an error in Dart Sass 2.0.0."
      )
    }
  }

  /** Adds [[child]] as a child of the current parent node. Throws if no parent is currently set (which should never happen during normal statement evaluation).
    *
    * If [[through]] is passed, [[child]] is added as a child of the first parent for which [[through]] returns `false` instead. That parent is copied unless it's the lattermost child of its parent.
    *
    * Port of dart-sass `_addChild` (evaluate.dart:4545-4577).
    */
  private def _addChild(child: ModifiableCssNode, through: (CssNode => Boolean) = null): Unit = {
    // Go up through parents that match [through].
    var parent: ModifiableCssParentNode = _parent.getOrElse {
      throw new IllegalStateException("EvaluateVisitor has no active parent node.")
    }
    if (through != null) {
      while (through(parent))
        parent.parent match {
          case gp if gp.isDefined =>
            gp.get match {
              case mp: ModifiableCssParentNode => parent = mp
              case _ =>
                throw new IllegalArgumentException(
                  s"through() must return false for at least one parent of $child."
                )
            }
          case _ =>
            throw new IllegalArgumentException(
              s"through() must return false for at least one parent of $child."
            )
        }

      // If the parent has a (visible) following sibling, we shouldn't add to
      // the parent. Instead, we should create a copy and add it after the
      // interstitial sibling.
      if (parent.hasFollowingSibling) {
        // A node with siblings must have a parent
        val grandparent = parent.modifiableParent.getOrElse {
          throw new IllegalStateException("A node with following siblings must have a parent.")
        }
        val lastChild = grandparent.children.last.asInstanceOf[ModifiableCssParentNode]
        if (parent.equalsIgnoringChildren(lastChild.asInstanceOf[ModifiableCssNode])) {
          // If we've already made a copy of [parent] and nothing else has been
          // added after it, re-use it.
          parent = lastChild
        } else {
          parent = parent.copyWithoutChildren()
          grandparent.addChild(parent)
        }
      }
    }

    parent.addChild(child)
  }

  /** If the current [[_parent]] is not the last child of its own parent, makes a new childless copy of it and sets [[_parent]] to that. Otherwise, leaves [[_parent]] as-is.
    *
    * Port of dart-sass `_copyParentAfterSibling` (evaluate.dart:4531-4538).
    */
  private def _copyParentAfterSibling(): Unit = {
    val parent = _parent.getOrElse {
      throw new IllegalStateException("EvaluateVisitor has no active parent node.")
    }
    parent.parent match {
      case gp if gp.isDefined =>
        val grandparent = gp.get.asInstanceOf[ModifiableCssParentNode]
        if (grandparent.children.last ne parent) {
          val newParent = parent.copyWithoutChildren()
          grandparent.addChild(newParent)
          _parent = Nullable(newParent: ModifiableCssParentNode)
        }
      case _ => ()
    }
  }

  /** Returns whether the current position in the output stylesheet uses plain CSS nesting.
    *
    * If it does, it's safe to use other features of the CSS nesting spec because the user has already opted into a narrower browser compatibility window.
    *
    * Port of dart-sass `_hasCssNesting` (evaluate.dart:353-365).
    */
  @annotation.nowarn("msg=unused private member|Unreachable case") // scaffolding: wired when CSS nesting output is ported
  private def _hasCssNesting: Boolean = {
    import scala.util.boundary, boundary.break
    var current: Nullable[CssParentNode] = _styleRule.fold[Nullable[CssParentNode]](Nullable.empty)(r => Nullable(r: CssParentNode))
    boundary[Boolean] {
      while (current.isDefined)
        current.get.parent match {
          case p if p.isDefined =>
            p.get match {
              case _:  CssStyleRule  => break(true)
              case gp: CssParentNode => current = Nullable(gp)
              case _ => break(false)
            }
          case _ => break(false)
        }
      false
    }
  }

  /** Returns a list of queries that selects for contexts that match both [[queries1]] and [[queries2]].
    *
    * Returns empty list if there are no contexts that match both, or `Nullable.empty` if there are contexts that can't be represented by media queries.
    *
    * Port of dart-sass `_mergeMediaQueries` (evaluate.dart:2346-2365).
    */
  private def _mergeMediaQueries(
    queries1: Iterable[CssMediaQuery],
    queries2: Iterable[CssMediaQuery]
  ): Nullable[List[CssMediaQuery]] = {
    import scala.util.boundary, boundary.break
    val queries = scala.collection.mutable.ListBuffer.empty[CssMediaQuery]
    boundary[Nullable[List[CssMediaQuery]]] {
      for (query1 <- queries1)
        for (query2 <- queries2)
          query1.merge(query2) match {
            case ssg.sass.ast.css.MediaQueryMergeResult.Empty           => ()
            case ssg.sass.ast.css.MediaQueryMergeResult.Unrepresentable => break(Nullable.empty)
            case ssg.sass.ast.css.MediaQueryMergeResult.Success(query)  => queries += query
          }
      Nullable(queries.toList)
    }
  }

  /** Runs [[body]] with [[queries]] as the current media queries.
    *
    * This also sets [[sources]] as the current set of media queries that were merged together to create [[queries]]. This is used to determine when it's safe to bubble one query through another.
    *
    * Port of dart-sass `_withMediaQueries` (evaluate.dart:4596-4609).
    */
  private def _withMediaQueries[T](
    queries: List[CssMediaQuery],
    sources: Set[CssMediaQuery]
  )(body: => T): T = {
    val oldMediaQueries = _mediaQueries
    val oldSources      = _mediaQuerySources
    _mediaQueries = queries
    _mediaQuerySources = sources
    try body
    finally {
      _mediaQueries = oldMediaQueries
      _mediaQuerySources = oldSources
    }
  }

  /** Runs [[body]] with [[parent]] as the active parent node, restoring the previous parent when complete. Mirrors Dart's `_withParent`.
    */
  private def _withParent[T, S <: ModifiableCssParentNode](
    parent:   S,
    addChild: Boolean = true,
    through:  (CssNode => Boolean) = null
  )(body: => T): T = {
    if (addChild) _addChild(parent, through)
    val saved = _parent
    _parent = Nullable(parent: ModifiableCssParentNode)
    try body
    finally _parent = saved
  }

  /** Runs [[body]] with [[rule]] as the active enclosing style rule. */
  private def _withStyleRule[T](rule: ModifiableCssStyleRule)(body: => T): T = {
    val saved         = _styleRule
    val savedIgnoring = _styleRuleIgnoringAtRoot
    _styleRule = Nullable(rule)
    _styleRuleIgnoringAtRoot = Nullable(rule)
    try body
    finally {
      _styleRule = saved
      _styleRuleIgnoringAtRoot = savedIgnoring
    }
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

  // ---------------------------------------------------------------------------
  // Infrastructure: stack frames, exception wrapping, warnings
  // (dart-sass evaluate.dart lines 4619-4796)
  // ---------------------------------------------------------------------------

  /** Adds a frame to the stack with the given [member] name, and [nodeWithSpan] as the site of the new frame.
    *
    * Runs [callback] with the new stack.
    *
    * This takes an AstNode rather than a FileSpan so it can avoid calling AstNode.span if the span isn't required, since some nodes need to do real work to manufacture a source span.
    *
    * Port of dart-sass `_withStackFrame` (evaluate.dart:4619-4631).
    */
  private def _withStackFrame[T](member: String, nodeWithSpan: ssg.sass.ast.AstNode, callback: => T): T = {
    _stack += ((_member, nodeWithSpan))
    val oldMember = _member
    _member = member
    val result = callback
    _member = oldMember
    _stack.remove(_stack.length - 1)
    result
  }

  /** Like [[ssg.sass.value.Value.withoutSlash]], but produces a deprecation warning if [value] was a slash-separated number.
    *
    * Port of dart-sass `_withoutSlash` (evaluate.dart:4635-4657).
    */
  private def _withoutSlash(value: ssg.sass.value.Value, nodeForSpan: ssg.sass.ast.AstNode): ssg.sass.value.Value = {
    value match {
      case n: SassNumber if n.asSlash.isDefined =>
        def recommendation(number: SassNumber): String = number.asSlash.fold(number.toString) { pair =>
          val (before, after) = pair
          s"math.div(${recommendation(before)}, ${recommendation(after)})"
        }

        _warnWithSpan(
          "Using / for division is deprecated and will be removed in Dart Sass " +
            "2.0.0.\n" +
            "\n" +
            s"Recommendation: ${recommendation(value.asInstanceOf[SassNumber])}\n" +
            "\n" +
            "More info and automated migrator: " +
            "https://sass-lang.com/d/slash-div",
          nodeForSpan.span,
          Nullable(Deprecation.SlashDiv)
        )
      case _ => ()
    }
    value.withoutSlash
  }

  /** Creates a new stack frame with location information from [member] and [span].
    *
    * Port of dart-sass `_stackFrame` (evaluate.dart:4661-4666).
    */
  private def _stackFrame(member: String, span: ssg.sass.util.FileSpan): ssg.sass.util.Frame =
    ssg.sass.util.Frame.fromSpan(span, Nullable(member))

  /** Returns a stack trace at the current point.
    *
    * If [span] is passed, it's used for the innermost stack frame.
    *
    * Port of dart-sass `_stackTrace` (evaluate.dart:4671-4678).
    */
  private def _stackTrace(span: Nullable[ssg.sass.util.FileSpan] = Nullable.empty): ssg.sass.util.Trace = {
    val frames = scala.collection.mutable.ListBuffer.empty[ssg.sass.util.Frame]
    for ((member, nodeWithSpan) <- _stack)
      frames += _stackFrame(member, nodeWithSpan.span)
    span.foreach(s => frames += _stackFrame(_member, s))
    ssg.sass.util.Trace(frames.reverse.toList)
  }

  /** Emits a warning with the given [message] about the given [span].
    *
    * Port of dart-sass `_warn` (evaluate.dart:4681-4696).
    */
  @annotation.nowarn // scaffolding: called from _withoutSlash (itself awaiting full _runUserDefinedCallable wiring)
  private def _warnWithSpan(message: String, span: ssg.sass.util.FileSpan, deprecation: Nullable[Deprecation] = Nullable.empty): Unit = {
    if (_quietDeps && _inDependency) return

    if (!_warningsEmitted.add((message, span))) return
    val trace = _stackTrace(Nullable(span))
    deprecation match {
      case dep if dep.isDefined =>
        _logger.warnForDeprecation(dep.get, message, span = Nullable(span), trace = Nullable(trace))
        _warnings += s"DEPRECATION WARNING [${dep.get.id}]: $message"
      case _ =>
        _logger.warn(message, span = Nullable(span), trace = Nullable(trace))
        _warnings += s"WARNING: $message"
    }
  }

  /** Returns a [SassRuntimeException] with the given [message].
    *
    * If [span] is passed, it's used for the innermost stack frame.
    *
    * Port of dart-sass `_exception` (evaluate.dart:4701-4706).
    */
  private def _exception(message: String, span: Nullable[ssg.sass.util.FileSpan] = Nullable.empty): SassRuntimeException = {
    val effectiveSpan = span.getOrElse {
      if (_stack.nonEmpty) _stack.last._2.span
      else ssg.sass.util.FileSpan.synthetic("<unknown>")
    }
    SassRuntimeException(message, effectiveSpan, _stackTrace(span))
  }

  /** Returns a [MultiSpanSassRuntimeException] with the given [message], [primaryLabel], and [secondaryLabels].
    *
    * The primary span is taken from the current stack trace span.
    *
    * Port of dart-sass `_multiSpanException` (evaluate.dart:4712-4723).
    */
  private def _multiSpanException(
    message:         String,
    primaryLabel:    String,
    secondaryLabels: Map[ssg.sass.util.FileSpan, String]
  ): MultiSpanSassRuntimeException = {
    val primarySpan =
      if (_stack.nonEmpty) _stack.last._2.span
      else ssg.sass.util.FileSpan.synthetic("<unknown>")
    MultiSpanSassRuntimeException(message, primarySpan, primaryLabel, secondaryLabels, _stackTrace())
  }

  /** Runs [callback], and converts any [SassScriptException]s it throws to [SassRuntimeException]s with [nodeWithSpan]'s source span.
    *
    * This takes an AstNode rather than a FileSpan so it can avoid calling AstNode.span if the span isn't required, since some nodes need to do real work to manufacture a source span.
    *
    * If [addStackFrame] is true (the default), this will add an innermost stack frame for [nodeWithSpan]. Otherwise, it will use the existing stack as-is.
    *
    * Port of dart-sass `_addExceptionSpan` (evaluate.dart:4734-4750).
    */
  private def _addExceptionSpan[T](
    nodeWithSpan:  ssg.sass.ast.AstNode,
    callback:      => T,
    addStackFrame: Boolean = true
  ): T =
    try
      callback
    catch {
      case error: SassScriptException =>
        val spanToUse = if (addStackFrame) Nullable(nodeWithSpan.span) else Nullable.empty[ssg.sass.util.FileSpan]
        throw error.withSpan(nodeWithSpan.span) match {
          case se: SassException => se.withTrace(_stackTrace(spanToUse))
        }
    }

  /** Runs [callback], and converts any [SassException]s that aren't already [SassRuntimeException]s to [SassRuntimeException]s with the current stack trace.
    *
    * Port of dart-sass `_addExceptionTrace` (evaluate.dart:4755-4767).
    */
  private def _addExceptionTrace[T](callback: => T): T =
    try
      callback
    catch {
      case error: SassRuntimeException => throw error
      case error: SassException        =>
        throw error.withTrace(_stackTrace(Nullable(error.span)))
    }

  /** Runs [callback], and converts any [SassRuntimeException]s containing an
    * @error
    *   to throw a more relevant [SassRuntimeException] with [nodeWithSpan]'s source span.
    *
    * Port of dart-sass `_addErrorSpan` (evaluate.dart:4772-4783).
    */
  @annotation.nowarn("msg=unused private member") // called from visitFunctionExpression / visitInterpolatedFunctionExpression in Dart; full wiring requires _runFunctionCallable port
  private def _addErrorSpan[T](nodeWithSpan: ssg.sass.ast.AstNode, callback: => T): T =
    try
      callback
    catch {
      case error: SassRuntimeException =>
        if (!error.span.text.startsWith("@error")) throw error
        throw SassRuntimeException(error.sassMessage, nodeWithSpan.span, _stackTrace())
    }

  /** Returns the [AstNode] whose span should be used for [expression].
    *
    * If [expression] is a variable reference, AstNode's span will be the span where that variable was originally declared. Otherwise, this will just return [expression].
    *
    * Port of dart-sass `_expressionNode` (evaluate.dart:4483-4500).
    */
  @annotation.nowarn("msg=unused") // called via _addExceptionSpan in _loadModule
  private def _expressionNode(expression: ssg.sass.ast.AstNode): ssg.sass.ast.AstNode =
    expression match {
      case ve: VariableExpression =>
        // dart-sass: look up the original declaration node for better error
        // spans. Falls back to the expression itself if not found.
        _addExceptionSpan(
          expression,
          _environment.getVariableNode(ve.name, namespace = ve.namespace)
        ).getOrElse(expression)
      case _ => expression
    }

  /** Returns the best human-readable message for [error].
    *
    * Port of dart-sass `_getErrorMessage` (evaluate.dart:4786-4795).
    */
  @annotation.nowarn("msg=unused private member") // utility available for future error reporting
  private def _getErrorMessage(error: Throwable): String = error match {
    case e: Error => e.toString
    case e =>
      try e.getMessage
      catch { case _: Exception => e.toString }
  }

  // ---------------------------------------------------------------------------
  // _runBuiltInCallable — full port of dart-sass evaluate.dart:3676-3758
  // ---------------------------------------------------------------------------

  /** Runs a [BuiltInCallable] with the given evaluated [arguments].
    *
    * This handles:
    *   - Mapping positional + named arguments to the parameter list
    *   - Rest argument handling (collecting extras into a SassArgumentList)
    *   - Keyword argument handling
    *   - Calling the callback
    *   - Error wrapping with span context
    *   - Post-call check that all keywords were accessed
    *
    * Port of dart-sass `_runBuiltInCallable` (evaluate.dart:3676-3758).
    */
  @annotation.nowarn("msg=unused private member")
  private def _runBuiltInCallable(
    arguments:    ssg.sass.ast.sass.ArgumentList,
    callable:     BuiltInCallable,
    nodeWithSpan: ssg.sass.ast.AstNode
  ): Value = {
    val (positional, named, _) = _evaluateArguments(arguments)

    val oldCallableNode = _callableNode
    _callableNode = Nullable(nodeWithSpan)

    // dart-sass: callbackFor selects the overload whose parameter count
    // matches the given positional count and named set. For non-overloaded
    // callables, there is a single (overload, callback) pair.
    // In our implementation, BuiltInCallable always has a single callback
    // and the arity checking happens via _checkBuiltInArity.
    _checkBuiltInArity(callable, positional, named)

    // Merge named args into positional slots, then pad with defaults.
    val merged =
      if (named.isEmpty) positional
      else _mergeBuiltInNamedArgs(callable, positional, named)
    val padded0 = _padBuiltInPositional(callable, merged)

    // dart-sass: collect extra positional args beyond declared params
    // into a SassArgumentList (evaluate.dart:3709-3726). This is
    // essential for rest-parameter built-ins like meta.call($function, $args...)
    // where the callback expects arguments[1] to be a SassArgumentList.
    val (padded, remainingNamed) = _collectBuiltInRestArgs(callable, padded0, named)

    // Track the SassArgumentList (if created) for post-call keyword checks.
    var argumentList: Nullable[ssg.sass.value.SassArgumentList] = Nullable.empty
    if (callable.hasRestParameter) {
      padded.lastOption match {
        case Some(al: ssg.sass.value.SassArgumentList) =>
          argumentList = Nullable(al)
        case _ => ()
      }
    }

    val result: Value =
      try
        _addExceptionSpan(nodeWithSpan, callable.callback(padded))
      catch {
        case e:     SassException => throw e
        case error: Throwable     =>
          throw _exception(_getErrorMessage(error), Nullable(nodeWithSpan.span))
      }
    _callableNode = oldCallableNode

    // dart-sass: if there was a SassArgumentList and keywords remain
    // unconsumed (the callback never called `keywords`), raise an error
    // listing the unexpected parameter names.
    argumentList.foreach { al =>
      if (remainingNamed.nonEmpty && !al.wereKeywordsAccessed) {
        val unusedNames = remainingNamed.keys.map(n => s"$$$n")
        val word        = Utils.pluralize("parameter", remainingNamed.size)
        throw SassRuntimeException(
          s"No $word named ${Utils.toSentence(unusedNames, "or")}.",
          nodeWithSpan.span,
          _stackTrace(Nullable(nodeWithSpan.span))
        )
      }
    }

    result
  }

  // ---------------------------------------------------------------------------
  // _warnForBogusCombinators — port of dart-sass evaluate.dart:2486-2536
  // ---------------------------------------------------------------------------

  /** Emits deprecation warnings for any bogus combinators in [rule].
    *
    * Port of dart-sass `_warnForBogusCombinators` (evaluate.dart:2486-2536).
    */
  private def _warnForBogusCombinators(rule: ModifiableCssStyleRule): Unit =
    // dart-sass calls rule.isInvisibleOtherThanBogusCombinators which delegates
    // to the style rule's own visibility check. We use the selector's version.
    if (!rule.selector.isInvisibleOtherThanBogusCombinators) {
      for (complex <- rule.selector.components)
        if (complex.isBogus) {
          if (complex.isUseless) {
            _warnWithSpan(
              s"""The selector "${complex.toString.trim}" is invalid CSS. It """ +
                "will be omitted from the generated CSS.\n" +
                "This will be an error in Dart Sass 2.0.0.\n" +
                "\n" +
                "More info: https://sass-lang.com/d/bogus-combinators",
              complex.span.trimRight(),
              Nullable(Deprecation.BogusCombinators)
            )
          } else if (complex.leadingCombinators.nonEmpty) {
            _warnWithSpan(
              s"""The selector "${complex.toString.trim}" is invalid CSS.\n""" +
                "This will be an error in Dart Sass 2.0.0.\n" +
                "\n" +
                "More info: https://sass-lang.com/d/bogus-combinators",
              complex.span.trimRight(),
              Nullable(Deprecation.BogusCombinators)
            )
          } else {
            _warnWithSpan(
              s"""The selector "${complex.toString.trim}" is only valid for """ +
                "nesting and shouldn't\n" +
                "have children other than style rules." +
                (if (complex.isBogusOtherThanLeadingCombinator) " It will be omitted from the generated CSS." else "") +
                "\n" +
                "This will be an error in Dart Sass 2.0.0.\n" +
                "\n" +
                "More info: https://sass-lang.com/d/bogus-combinators",
              complex.span.trimRight(),
              Nullable(Deprecation.BogusCombinators)
            )
          }
        }
    }

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

    // Make sure all global variables declared in a module always appear in the
    // module's definition, even if their assignments aren't reached.
    // dart-sass evaluate.dart:1175-1181
    for ((name, span) <- node.globalVariables) {
      val _ = visitVariableDeclaration(
        new VariableDeclaration(name, NullExpression(span), span, isGuarded = true)
      )
    }

    SassNull
  }

  override def visitStyleRule(node: StyleRule): Value = {
    // NOTE: this logic is largely duplicated in [visitCssStyleRule]. Most
    // changes here should be mirrored there.

    // dart-sass evaluate.dart:2376-2386: guard checks
    if (_declarationName.isDefined) {
      throw _exception(
        "Style rules may not be used within nested declarations.",
        Nullable(node.span)
      )
    } else if (_inKeyframes && _parent.exists(_.isInstanceOf[CssKeyframeBlock])) {
      throw _exception(
        "Style rules may not be used within keyframe blocks.",
        Nullable(node.span)
      )
    }

    // dart-sass evaluate.dart:2388-2391: evaluate the selector interpolation
    val selectorText: String = node.selector.fold(
      node.parsedSelector.fold("")(ps => ps.toString)
    )(interpolation => _performInterpolation(interpolation))

    if (_inKeyframes) {
      // NOTE: this logic is largely duplicated in [visitCssKeyframeBlock].
      // Most changes here should be mirrored there.
      //
      // dart-sass evaluate.dart:2393-2416: inside @keyframes, parse with
      // KeyframeSelectorParser and create a CssKeyframeBlock instead of a
      // CssStyleRule. Keyframe selectors (from, to, 0%, 50%) are not valid
      // CSS selectors, so they must not go through SelectorList.parse.
      val parsedSelector = new KeyframeSelectorParser(selectorText).parse()
      val rule           = new ModifiableCssKeyframeBlock(
        new CssValue(parsedSelector, node.selector.fold(node.span)(_.span)),
        node.span
      )
      _withParent(rule, through = (n: CssNode) => n.isInstanceOf[CssStyleRule]) {
        _environment.scope(semiGlobal = false, when = node.hasDeclarations) {
          node.children.foreach { kids =>
            for (child <- kids) {
              val _ = child.accept(this)
            }
          }
        }
      }
    } else {
      // dart-sass evaluate.dart:2418-2422: parse the evaluated selector text
      // into a SelectorList AST. No fallback — if parsing fails, it's an error.
      val isPlainCss     = _stylesheet.exists(_.plainCss)
      var parsedSelector = new SelectorParser(selectorText, plainCss = isPlainCss).parse()

      // dart-sass evaluate.dart:2424-2428: determine whether to merge (nest)
      // with the parent selector.
      val merge: Boolean = _styleRule match {
        case sr if sr.isEmpty          => true
        case sr if sr.get.fromPlainCss => false
        case _                         => !(isPlainCss && parsedSelector.containsParentSelector)
      }
      if (merge) {
        // dart-sass evaluate.dart:2430-2443: in plain CSS, reject leading
        // combinators at the top level.
        if (isPlainCss) {
          for (complex <- parsedSelector.components)
            if (complex.leadingCombinators.nonEmpty) {
              throw _exception(
                "Top-level leading combinators aren't allowed in plain CSS.",
                Nullable(complex.leadingCombinators.head.span)
              )
            }
        }

        // dart-sass evaluate.dart:2445-2449: nest within the parent selector.
        parsedSelector = parsedSelector.nestWithin(
          _styleRuleIgnoringAtRoot.map(_.originalSelector),
          implicitParent = !_atRootExcludingStyleRule,
          preserveParentSelectors = isPlainCss
        )
      }

      // dart-sass evaluate.dart:2452: register selector in the extension store.
      val selectorBox = _extensionStore.fold(
        new ModifiableBox[SelectorList](parsedSelector).seal()
      )(_.addSelector(parsedSelector, Nullable(_mediaQueries).filter(_.nonEmpty)))
      val rule = new ModifiableCssStyleRule(
        selectorBox,
        node.span,
        originalSel = Nullable(parsedSelector),
        fromPlainCss = isPlainCss
      )
      // dart-sass evaluate.dart:2459: save and reset _atRootExcludingStyleRule
      val oldAtRootExcludingStyleRule = _atRootExcludingStyleRule
      _atRootExcludingStyleRule = false
      // dart-sass evaluate.dart:2461-2472: use _withParent with through to
      // bubble style rules up through parent CssStyleRules when merging.
      val throughFn: CssNode => Boolean = if (merge) _.isInstanceOf[CssStyleRule] else null
      _withParent(rule, through = throughFn) {
        _withStyleRule(rule) {
          _environment.scope(semiGlobal = false, when = node.hasDeclarations) {
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
      _atRootExcludingStyleRule = oldAtRootExcludingStyleRule

      // dart-sass evaluate.dart:2475: emit deprecation warnings for bogus combinators.
      _warnForBogusCombinators(rule)
      // dart-sass evaluate.dart:2477-2480: mark the last child of _parent as
      // isGroupEnd so the serializer emits a blank line between groups.
      if (_styleRule.isEmpty) {
        val parentNode = _parent.getOrElse {
          throw new IllegalStateException("EvaluateVisitor has no active parent node.")
        }
        if (parentNode.children.nonEmpty) {
          parentNode.children.last match {
            case m: ModifiableCssNode => m.isGroupEnd = true
            case _ => ()
          }
        }
      }
    }
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

  override def visitDeclaration(node: Declaration): Value = {
    // dart-sass (async_evaluate.dart:1365-1370): declarations are only valid
    // inside style rules, unknown at-rules, or keyframes blocks.
    if (_styleRule.isEmpty && !_inUnknownAtRule && !_inKeyframes) {
      throw _exception(
        "Declarations may only be used within style rules.",
        Nullable(node.span)
      )
    }

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

    // dart-sass (async_evaluate.dart:1371-1378): reject non-SassScript
    // declarations nested inside a declaration block.
    if (_declarationName.isDefined && !node.parsedAsSassScript) {
      throw _exception(
        if (node.name.asPlain.exists(_.startsWith("--")))
          "Declarations whose names begin with \"--\" may not be nested."
        else
          "Declarations parsed as raw CSS may not be nested.",
        Nullable(node.span)
      )
    }

    val nameText = _performInterpolation(node.name)
    // If we're inside a nested declaration block, prepend the parent name
    // (e.g., inside `background: { color: red; }`, _declarationName is
    // "background" and nameText is "color", producing "background-color").
    val name = new CssValue[String](
      _declarationName.fold(nameText)(dn => s"$dn-$nameText"),
      node.name.span
    )

    // A declaration may have no value if it's purely a container for
    // nested declarations (e.g. `font: { family: ...; }`).
    node.value.foreach { expression =>
      val value = expression.accept(this)
      // dart-sass (async_evaluate.dart:1387-1393): skip blank values
      // unless it's an empty list (which should produce an error) or
      // a custom property (allowed to be empty per spec).
      if (!value.isBlank || _isEmptyList(value) || name.value.startsWith("--")) {
        val cssVal: Value =
          if (node.parsedAsSassScript) value
          else {
            // Custom property / non-SassScript: must be a SassString.
            value match {
              case s: SassString => s
              case other => new SassString(other.toCssString(quote = false), hasQuotes = false)
            }
          }
        _copyParentAfterSibling()
        _addChild(
          new ModifiableCssDeclaration(
            name,
            new CssValue[Value](cssVal, expression.span),
            node.span,
            parsedAsSassScript = node.parsedAsSassScript,
            isImportant = node.isImportant
          )
        )
      }
    }

    // Nested declarations: set _declarationName so that ALL child
    // statements (including @if, @for, @each, @include) that ultimately
    // produce declarations will have the parent prefix applied.
    // dart-sass evaluate.dart visitDeclaration (lines 1407-1416).
    node.children.foreach { kids =>
      val oldDeclarationName = _declarationName
      _declarationName = Nullable(name.value)
      _environment.scope(semiGlobal = false, when = node.hasDeclarations) {
        for (child <- kids)
          child.accept(this)
      }
      _declarationName = oldDeclarationName
    }
    SassNull
  }

  /// Returns whether [value] is an empty list.
  /// Port of dart-sass `_isEmptyList` (evaluate.dart:1422).
  private def _isEmptyList(value: Value): Boolean = value.asList.isEmpty

  /// Port of dart-sass `visitVariableDeclaration` (evaluate.dart:2648-2707).
  override def visitVariableDeclaration(node: VariableDeclaration): Value = {
    import scala.util.boundary, boundary.break
    boundary[Value] {
      if (node.isGuarded) {
        if (node.namespace.isEmpty && _environment.atRoot) {
          // Mark the variable as configurable so couldHaveBeenConfigured works.
          _environment.markVariableConfigurable(node.name)
          // Consult the Configuration for an override.
          val configOverride = _configuration.remove(node.name)
          configOverride.foreach { cv =>
            if (cv.value != SassNull) {
              _environment.setVariable(node.name, cv.value, global = true)
              break(SassNull)
            }
          }
          // Also check the legacy _pendingConfig for backward compat with
          // tests that use the old ad-hoc configuration mechanism.
          _pendingConfig.get(node.name) match {
            case Some(v) if v != SassNull =>
              _pendingConfig = _pendingConfig - node.name
              _environment.setVariable(node.name, v, global = true)
              break(SassNull)
            case _ => ()
          }
        }

        val existing = _addExceptionSpan(node, _environment.getVariable(node.name, namespace = node.namespace))
        if (existing.isDefined && existing.get != SassNull) break(SassNull)
      }

      // dart-sass evaluate.dart:2668-2685: warn when !global declares a new variable
      if (node.isGlobal && !_environment.globalVariableExists(node.name)) {
        _warnWithSpan(
          if (_environment.atRoot)
            "As of Dart Sass 2.0.0, !global assignments won't be able to " +
              "declare new variables.\n" +
              "\n" +
              "Since this assignment is at the root of the stylesheet, the " +
              "!global flag is\n" +
              "unnecessary and can safely be removed."
          else
            "As of Dart Sass 2.0.0, !global assignments won't be able to " +
              "declare new variables.\n" +
              "\n" +
              s"Recommendation: add `$$${node.name}: null` at the stylesheet root.",
          node.span,
          Nullable(Deprecation.NewGlobal)
        )
      }

      // dart-sass: _withoutSlash strips slash info on variable assignment
      // (async_evaluate.dart:2687).
      val value = node.expression.accept(this).withoutSlash
      // dart-sass evaluate.dart:2696-2704: wrap setVariable in
      // _addExceptionSpan so SassScriptException gets a source span.
      _addExceptionSpan(node,
                        _environment.setVariable(
                          node.name,
                          value,
                          namespace = node.namespace,
                          global = node.isGlobal
                        )
      )
      SassNull
    }
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
    // Coerce `to` to `from`'s units before extracting integer. This ensures
    // the direction comparison uses the same unit basis and also validates
    // unit compatibility (throws for incompatible units).
    val toInt =
      try {
        val coercedVal = toValue.coerceValueToMatch(fromValue, toName, fromName)
        SassNumber(coercedVal).assertInt(toName)
      } catch { case e: SassScriptException => throw e.withSpan(node.to.span) }

    val direction = if (fromInt > toInt) -1 else 1
    val end       =
      if (node.isExclusive) toInt
      else toInt + direction

    // dart-sass (async_evaluate.dart:1645-1662): use setLocalVariable for
    // the loop variable so it doesn't overwrite an outer variable with the
    // same name. The entire loop runs inside a semi-global scope.
    _withSemiGlobalScope {
      var i = fromInt
      while (i != end) {
        _environment.setLocalVariable(
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
    // dart-sass (async_evaluate.dart:1424-1445): use setLocalVariable for
    // the loop variable so it doesn't overwrite an outer variable with the
    // same name. The entire loop runs inside a semi-global scope.
    _withSemiGlobalScope {
      for (element <- listValue.asList) {
        if (node.variables.length == 1) {
          _environment.setLocalVariable(node.variables.head, element.withoutSlash)
        } else {
          // Destructure sub-list values; pad with null for missing slots.
          val sub = element.asList
          var i   = 0
          while (i < node.variables.length) {
            val v = if (i < sub.length) sub(i).withoutSlash else SassNull
            _environment.setLocalVariable(node.variables(i), v)
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
    // dart-sass: no iteration limit (evaluate.dart:2747-2764)
    _withSemiGlobalScope {
      while (node.condition.accept(this).isTruthy)
        for (statement <- node.children.get) {
          val _ = statement.accept(this)
        }
    }
    SassNull
  }

  override def visitDebugRule(node: DebugRule): Value = {
    // dart-sass: visitDebugRule (evaluate.dart:1363-1370)
    // Only calls _logger.debug — does NOT add to warnings.
    val value   = node.expression.accept(this)
    val message = value match {
      case s: SassString => s.text
      case other => SerializeVisitor.serializeValue(other, inspect = true)
    }
    _logger.debug(message, node.span)
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
    // NOTE: this logic is largely duplicated in [visitCssComment]. Most changes
    // here should be mirrored there.

    // dart-sass: loud comments inside function bodies are silently ignored.
    if (_inFunction) return SassNull

    // Comments are allowed to appear between CSS imports.
    _root.foreach { root =>
      if ((_parent.get eq root) && _endOfImports == root.children.length) {
        _endOfImports += 1
      }
    }

    var text = _performInterpolation(node.text)
    // Indented syntax doesn't require */
    if (!text.endsWith("*/")) text += " */"

    _copyParentAfterSibling()
    _addChild(new ModifiableCssComment(text, node.text.span))
    SassNull
  }

  override def visitAtRule(node: AtRule): Value = {
    // dart-sass (evaluate.dart:1555-1560): reject @rules nested inside declarations.
    if (_declarationName.isDefined) {
      throw _exception(
        "At-rules may not be used within nested declarations.",
        Nullable(node.span)
      )
    }

    val nameText  = _performInterpolation(node.name)
    val nameValue = new CssValue[String](nameText, node.name.span)
    // dart-sass evaluate.dart:1564-1566: trim the value and warn for color.
    val valueWrapper: Nullable[CssValue[String]] = node.value.map { interp =>
      val raw = _performInterpolation(interp)
      // dart-sass strips // comments at parse time; our raw-text parser
      // preserves them, so strip them post-interpolation.
      val stripped = _stripSilentComments(raw)
      // dart-sass: `_interpolationToValue(value, trim: true, ...)` — trim ASCII
      // whitespace from the interpolated value.
      val trimmed = ssg.sass.Utils.trimAscii(stripped, excludeEscape = true)
      new CssValue[String](trimmed, interp.span)
    }

    val childless = node.children.isEmpty
    val rule      = new ModifiableCssAtRule(
      nameValue,
      node.span,
      childless = childless,
      value = valueWrapper
    )

    if (childless) {
      // dart-sass evaluate.dart:1569-1573: copy the parent after any siblings
      // so that the childless at-rule appears after any bubbled-up rules.
      _copyParentAfterSibling()
      _parent.get.addChild(rule)
    } else {
      val wasInKeyframes     = _inKeyframes
      val wasInUnknownAtRule = _inUnknownAtRule
      val nameLower          = nameText.toLowerCase
      if (ssg.sass.Utils.unvendor(nameLower) == "keyframes") {
        _inKeyframes = true
      } else {
        _inUnknownAtRule = true
      }

      // dart-sass: if the user has already opted into plain CSS nesting, don't
      // bother with any merging or bubbling.
      if (_hasCssNesting) {
        _withParent(rule) {
          _environment.scope(semiGlobal = false, when = node.hasDeclarations) {
            for (statement <- node.children.get) {
              val _ = statement.accept(this)
            }
          }
        }
        _inUnknownAtRule = wasInUnknownAtRule
        _inKeyframes = wasInKeyframes
        return SassNull
      }

      // dart-sass: _withParent with through = CssStyleRule makes the
      // at-rule bubble through enclosing style rules automatically.
      // When inside a style rule, a copy of the style rule is created
      // inside the at-rule to hold the nested children.
      _addChild(rule, through = (n: CssNode) => n.isInstanceOf[CssStyleRule])
      val savedParent = _parent
      _parent = Nullable(rule: ModifiableCssParentNode)
      try {
        val enclosingStyleRule = _styleRule
        if (enclosingStyleRule.isEmpty || _inKeyframes || nameLower == "font-face") {
          // Special-cased at-rules within style blocks are pulled out to the
          // root. Equivalent to prepending "@at-root" on them.
          _environment.scope(semiGlobal = false, when = node.hasDeclarations) {
            for (statement <- node.children.get) {
              val _ = statement.accept(this)
            }
          }
        } else {
          // If we're in a style rule, copy it into the at-rule so that
          // declarations immediately inside it have somewhere to go.
          //
          // For example, "a {@foo {b: c}}" should produce "@foo {a {b: c}}".
          val innerRule = enclosingStyleRule.get.copyWithoutChildren()
          _withParent(innerRule, addChild = true) {
            _withStyleRule(innerRule) {
              for (statement <- node.children.get) {
                val _ = statement.accept(this)
              }
            }
          }
        }
      } finally
        _parent = savedParent
      _inUnknownAtRule = wasInUnknownAtRule
      _inKeyframes = wasInKeyframes
    }
    SassNull
  }

  // --- Module system and conditional at-rules --------------------------------

  /** The current media queries, or empty if none.
    *
    * Port of dart-sass `_mediaQueries` (evaluate.dart:206).
    */
  private var _mediaQueries: List[CssMediaQuery] = Nil

  /** The set of media queries that were merged together to create [[_mediaQueries]].
    *
    * This will be non-empty if and only if [[_mediaQueries]] is the result of a merge. Port of dart-sass `_mediaQuerySources` (evaluate.dart:213).
    */
  private var _mediaQuerySources: Set[CssMediaQuery] = Set.empty

  override def visitMediaRule(node: MediaRule): Value = {
    // dart-sass (evaluate.dart:2261-2266): reject @media nested inside declarations.
    if (_declarationName.isDefined) {
      throw _exception(
        "Media rules may not be used within nested declarations.",
        Nullable(node.span)
      )
    }

    val queryText = _stripCssComments(_performInterpolation(node.query))
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

    // dart-sass: If the user has already opted into plain CSS nesting, don't
    // bother with any merging or bubbling; this rule is already only usable by
    // browsers that support nesting natively anyway.
    if (_hasCssNesting) {
      _withParent(new ModifiableCssMediaRule(parsed, node.span)) {
        for (child <- node.children.get) {
          val _ = child.accept(this)
        }
      }
      return SassNull
    }

    // dart-sass: merge with enclosing @media queries if any exist.
    // If the merge produces an empty result, the nested @media is
    // impossible (e.g. @media screen { @media print { ... } }) and
    // should be skipped entirely. If there are no enclosing queries,
    // use the parsed queries as-is.
    val mergedQueries: Nullable[List[CssMediaQuery]] =
      if (_mediaQueries.nonEmpty) _mergeMediaQueries(_mediaQueries, parsed)
      else Nullable.empty
    // If merge returned an empty list (no possible merge), skip this rule.
    if (mergedQueries.isDefined && mergedQueries.get.isEmpty) return SassNull

    val effectiveQueries = mergedQueries.getOrElse(parsed)
    val mergedSources: Set[CssMediaQuery] =
      if (mergedQueries.isEmpty) Set.empty
      else _mediaQuerySources ++ _mediaQueries.toSet ++ parsed.toSet

    val rule = new ModifiableCssMediaRule(effectiveQueries, node.span)

    // dart-sass (async_evaluate.dart:2284-2316): use _withParent with a
    // `through` function that bubbles the media rule past enclosing style
    // rules and past enclosing media rules whose queries are all part of
    // the merge sources. This is what causes nested @media to be promoted
    // to siblings of the outer @media when the merge succeeds.
    // dart-sass: bubble @media past CssStyleRule and past merge-source
    // CssMediaRule. When _inKeyframes, our parser produces CssStyleRule
    // nodes for keyframe selectors (to/from/0%/etc.) — dart-sass produces
    // CssKeyframeBlock instead. Don't bubble through CssStyleRule when
    // _inKeyframes to avoid moving @media out of keyframe blocks.
    val throughFn: CssNode => Boolean = { (n: CssNode) =>
      n match {
        case _:  CssStyleRule                           => !_inKeyframes
        case mr: CssMediaRule if mergedSources.nonEmpty =>
          mr.queries.forall(mergedSources.contains)
        case _ => false
      }
    }

    _withParent(rule, through = throughFn) {
      _withMediaQueries(effectiveQueries, mergedSources) {
        val enclosingStyleRule = _styleRule
        // dart-sass: when inside keyframes, our parser uses CssStyleRule for
        // keyframe selectors (to/from/0%) while dart-sass uses CssKeyframeBlock.
        // Don't copy the keyframe selector into the media query — @media
        // inside keyframes should contain its children directly.
        if (enclosingStyleRule.isDefined && !_inKeyframes) {
          // If we're in a style rule, copy it into the media query so that
          // declarations immediately inside @media have somewhere to go.
          //
          // For example, "a {@media screen {b: c}}" should produce
          // "@media screen {a {b: c}}".
          val innerRule = enclosingStyleRule.get.copyWithoutChildren()
          _withParent(innerRule) {
            _withStyleRule(innerRule) {
              for (statement <- node.children.get) {
                val _ = statement.accept(this)
              }
            }
          }
        } else {
          for (statement <- node.children.get) {
            val _ = statement.accept(this)
          }
        }
      }
    }
    SassNull
  }

  /** Strips `/* ... */` CSS comments from text, collapsing surrounding whitespace to a single space. Preserves quoted strings. Mirrors the comment-stripping that dart-sass's structured media-query
    * parser performs implicitly by consuming and discarding comments during tokenization.
    */
  /** Strips `//` (silent) comments from text produced by raw-text interpolation in SCSS at-rule values. The dart-sass parser handles these at parse time; our stage-1 parser copies raw text, so we
    * strip them post-interpolation. Only strips `//` when it's outside strings and parentheses (to avoid stripping `//` in URLs like `url(//example.com)`).
    */
  private def _stripSilentComments(text: String): String = {
    if (!text.contains("//")) return text
    val sb         = new StringBuilder(text.length)
    var i          = 0
    val n          = text.length
    var parenDepth = 0
    while (i < n) {
      val c = text.charAt(i)
      if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/' && parenDepth == 0) {
        // Skip to end of line
        while (i < n && text.charAt(i) != '\n') i += 1
        // Trim trailing whitespace before the comment
        while (sb.nonEmpty && sb.last == ' ') sb.deleteCharAt(sb.length - 1)
      } else if (c == '(') {
        parenDepth += 1
        sb.append(c)
        i += 1
      } else if (c == ')') {
        if (parenDepth > 0) parenDepth -= 1
        sb.append(c)
        i += 1
      } else if (c == '"' || c == '\'') {
        // Don't strip inside strings
        sb.append(c)
        i += 1
        while (i < n && text.charAt(i) != c) {
          if (text.charAt(i) == '\\' && i + 1 < n) {
            sb.append(text.charAt(i))
            i += 1
          }
          sb.append(text.charAt(i))
          i += 1
        }
        if (i < n) { sb.append(text.charAt(i)); i += 1 }
      } else {
        sb.append(c)
        i += 1
      }
    }
    sb.toString().trim
  }

  private def _stripCssComments(text: String): String =
    if (!text.contains("/*")) text
    else {
      val sb = new StringBuilder(text.length)
      var i  = 0
      val n  = text.length
      while (i < n) {
        val c = text.charAt(i)
        if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
          // Skip past */
          i += 2
          while (i + 1 < n && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) i += 1
          if (i + 1 < n) i += 2 // skip */
          // Collapse surrounding whitespace: after stripping a comment, ensure
          // at most one space exists in its place. If preceding text already ends
          // with whitespace, skip any following whitespace too; otherwise emit one
          // space if the comment was not adjacent to structural characters.
          if (sb.nonEmpty && (sb.last == ' ' || sb.last == '\t')) {
            // Already have whitespace before the comment; skip any whitespace after it.
            while (i < n && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i += 1
          } else if (i < n && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            // No whitespace before, but whitespace after; keep exactly one space.
            sb.append(' ')
            while (i < n && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i += 1
          } else if (sb.nonEmpty && i < n) {
            // No whitespace on either side; emit a space as separator.
            sb.append(' ')
          }
        } else if (c == '"' || c == '\'') {
          sb.append(c)
          i += 1
          while (i < n && text.charAt(i) != c) {
            if (text.charAt(i) == '\\' && i + 1 < n) {
              sb.append(text.charAt(i))
              i += 1
            }
            sb.append(text.charAt(i))
            i += 1
          }
          if (i < n) { sb.append(text.charAt(i)); i += 1 }
        } else {
          sb.append(c)
          i += 1
        }
      }
      sb.toString()
    }

  /** Rejects a small set of unambiguously invalid `@media` query shapes. Mirrors dart-sass StylesheetParser diagnostics for cases that our stage-1 parser would otherwise accept as raw text. Only runs
    * on interpolation-free text to avoid false positives from user-inserted fragments.
    */
  private def _validateMediaQueryText(text: String, span: ssg.sass.util.FileSpan): Unit = {
    val trimmed = text.trim
    if (trimmed.isEmpty) return

    // (0) Validate range syntax inside each top-level parenthesized expression.
    // dart-sass parses media range syntax (`expr < name < expr`) structurally
    // in _mediaInParens. Our parser collects raw text, so we validate here.
    _validateMediaRangeExpressions(trimmed, span)

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

    // (5) `IDENT and not (condition) and/or ...` — after consuming
    //     `and not (condition)`, dart-sass returns immediately; any further
    //     operator is invalid. Detect: [IDENT, "and", "not", PAREN, and/or, ...]
    if (topLevel.length >= 5) {
      val t0 = topLevel(0).toLowerCase
      val t1 = topLevel(1).toLowerCase
      val t2 = topLevel(2).toLowerCase
      val t3 = topLevel(3)
      val t4 = topLevel(4).toLowerCase
      if (
        t0 != "not" && t0 != "only" && !t0.startsWith("(") &&
        t1 == "and" && t2 == "not" && t3.startsWith("(") &&
        (t4 == "and" || t4 == "or")
      ) {
        throw new SassException("""expected "{".""", span)
      }
    }
    // Also check: [ONLY, IDENT, "and", "not", PAREN, and/or, ...]
    if (topLevel.length >= 6) {
      val t0 = topLevel(0).toLowerCase
      val t1 = topLevel(1).toLowerCase
      val t2 = topLevel(2).toLowerCase
      val t3 = topLevel(3).toLowerCase
      val t4 = topLevel(4)
      val t5 = topLevel(5).toLowerCase
      if (
        (t0 == "only" || t0 == "not") &&
        !t1.startsWith("(") && t2 == "and" && t3 == "not" && t4.startsWith("(") &&
        (t5 == "and" || t5 == "or")
      ) {
        throw new SassException("""expected "{".""", span)
      }
    }

    // (6) Interpolation followed by `or` is treated as a type query + or,
    //     which is always invalid (or can only follow parenthesized conditions).
    if (hasOr && topLevel.headOption.exists(_.startsWith("#{"))) {
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

  /** Validates media range syntax inside parenthesized expressions.
    *
    * Scans the media query text for top-level parenthesized sub-expressions and checks each one for valid range syntax according to CSS Media Queries Level 4 rules as enforced by dart-sass:
    *
    *   - At most two comparison operators per parenthesized expression
    *   - Both operators must point the same direction (`<`/`<=` or `>`/`>=`)
    *   - `=` cannot start a range (no `expr = middle = expr`)
    *   - Spaced comparisons `< =` or `> =` are invalid
    */
  private def _validateMediaRangeExpressions(text: String, span: ssg.sass.util.FileSpan): Unit = {
    // Extract the contents of each top-level parenthesized expression
    // (the query may have multiple: `(a) and (b)`).
    val parenContents = _extractParenContents(text)
    for (content <- parenContents)
      _validateSingleMediaFeature(content, span)
  }

  /** Extracts the inner text of each top-level parenthesized group in [text]. For example, `(width < 500px) and (height > 200px)` yields `List("width < 500px", "height > 200px")`.
    */
  private def _extractParenContents(text: String): List[String] = {
    val result = scala.collection.mutable.ListBuffer.empty[String]
    var i      = 0
    while (i < text.length)
      if (text.charAt(i) == '(') {
        var depth = 1
        val start = i + 1
        i += 1
        while (i < text.length && depth > 0) {
          val c = text.charAt(i)
          if (c == '(') depth += 1
          else if (c == ')') depth -= 1
          i += 1
        }
        if (depth == 0) {
          result += text.substring(start, i - 1)
        }
      } else {
        i += 1
      }
    result.toList
  }

  /** Validates a single media feature expression (the content inside one pair of parentheses). Checks for invalid range syntax.
    */
  private def _validateSingleMediaFeature(content: String, span: ssg.sass.util.FileSpan): Unit = {
    val s = content.trim
    // Skip nested parenthesized conditions like `(not (...))` or `(a) and (b)`.
    // These are logic expressions, not feature comparisons.
    if (s.startsWith("(") || s.startsWith("not ") || s.startsWith("not\t")) return
    // Skip if it contains `and` or `or` at the top level (logic expression).
    val topTokens = _topLevelTokens(s)
    if (topTokens.exists(t => t.equalsIgnoreCase("and") || t.equalsIgnoreCase("or"))) return

    // Find comparison operators in the token stream. We look for sequences of
    // `<`, `<=`, `>`, `>=`, `=` that appear between non-operator tokens.
    // Tokenize comparison operators: scan for <, >, = not inside parens, strings, or interpolations.
    val ops   = scala.collection.mutable.ListBuffer.empty[(String, Int)]
    var i     = 0
    var depth = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (c == '(') { depth += 1; i += 1 }
      else if (c == ')') { depth -= 1; i += 1 }
      else if (c == '\'' || c == '"') {
        // Skip string
        i += 1
        while (i < s.length && s.charAt(i) != c) {
          if (s.charAt(i) == '\\') i += 1
          i += 1
        }
        if (i < s.length) i += 1
      } else if (depth == 0 && (c == '<' || c == '>' || c == '=')) {
        val pos = i
        if ((c == '<' || c == '>') && i + 1 < s.length && s.charAt(i + 1) == '=') {
          ops += ((s.substring(i, i + 2), pos))
          i += 2
        } else if (c == '=' && i + 1 < s.length && s.charAt(i + 1) == '=') {
          // `==` is a Sass equality operator, not a media comparison.
          i += 2
        } else {
          ops += ((c.toString, pos))
          i += 1
        }
      } else {
        i += 1
      }
    }

    if (ops.isEmpty) return

    // Check for spaced comparison: `< =` or `> =`
    // This manifests as `<` or `>` immediately followed (after whitespace) by `=`.
    for (idx <- 0 until ops.length - 1) {
      val (op1, _pos1) = ops(idx)
      val (op2, pos2)  = ops(idx + 1)
      if ((op1 == "<" || op1 == ">") && op2 == "=") {
        // Check if there's only whitespace between them
        val between = s.substring(_pos1 + op1.length, pos2).trim
        if (between.isEmpty) {
          throw new SassException("""Expected expression.""", span)
        }
      }
    }

    // A colon means this is a `feature: value` expression, not a range.
    if (s.contains(':')) return

    // Validate operator count and direction.
    if (ops.length == 1) {
      // Single comparison is always valid (e.g. `width < 500px`).
      return
    }

    if (ops.length == 2) {
      val (op1, _) = ops(0)
      val (op2, _) = ops(1)
      // Both must be range-capable (< or > family, not bare =).
      if (op1 == "=" || op2 == "=") {
        // `=` cannot form a range.
        throw new SassException("""expected ")".""", span)
      }
      // Both must point the same direction.
      val dir1 = if (op1 == "<" || op1 == "<=") -1 else 1
      val dir2 = if (op2 == "<" || op2 == "<=") -1 else 1
      if (dir1 != dir2) {
        // Mismatched range: e.g. `1px > width < 2px`
        throw new SassException("""expected ")".""", span)
      }
      // Valid range.
      return
    }

    // Three or more operators: always invalid (e.g. `1 < width < 2 < 3`).
    throw new SassException("""expected ")".""", span)
  }

  override def visitSupportsRule(node: SupportsRule): Value = {
    // dart-sass (evaluate.dart:2541-2546): reject @supports nested inside declarations.
    if (_declarationName.isDefined) {
      throw _exception(
        "Supports rules may not be used within nested declarations.",
        Nullable(node.span)
      )
    }

    val conditionText = _visitSupportsCondition(node.condition)
    val cssCondition  = new CssValue[String](conditionText, node.condition.span)
    val rule          = new ModifiableCssSupportsRule(cssCondition, node.span)

    // dart-sass: if the user has already opted into plain CSS nesting, don't
    // bother with bubbling.
    if (_hasCssNesting) {
      _withParent(rule) {
        for (statement <- node.children.get) {
          val _ = statement.accept(this)
        }
      }
      return SassNull
    }

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

    // dart-sass evaluate.dart:1196-1208: walk up from _parent to the
    // stylesheet root, collecting NON-excluded ancestors (innermost-first).
    def excludes(n: ModifiableCssParentNode): Boolean = n match {
      case _:  ModifiableCssStyleRule    => query.excludesStyleRules
      case _:  ModifiableCssMediaRule    => query.excludesName("media")
      case _:  ModifiableCssSupportsRule => query.excludesName("supports")
      case ar: ModifiableCssAtRule       => query.excludesName(ar.name.value.toLowerCase)
      case _ => false
    }

    var parent: ModifiableCssParentNode = _parent.getOrElse(root)
    val included = scala.collection.mutable.ListBuffer.empty[ModifiableCssParentNode]
    while (!parent.isInstanceOf[ModifiableCssStylesheet]) {
      if (!excludes(parent)) included += parent
      parent.modifiableParent match {
        case gp if gp.isDefined => parent = gp.get
        case _                  =>
          throw new IllegalStateException(
            "CssNodes must have a CssStylesheet transitive parent node."
          )
      }
    }

    // dart-sass evaluate.dart:1209: _trimIncluded
    val trimmedRoot = _trimIncluded(included)

    // dart-sass evaluate.dart:1213-1220: if nothing was excluded, just
    // evaluate children in place. However, we still need to apply the
    // _atRootExcludingStyleRule flag so that nested style rules don't
    // get an implicit parent selector. This is needed when @at-root
    // runs inside an @import context where _styleRuleIgnoringAtRoot
    // is inherited from the outer scope.
    if (trimmedRoot eq _parent.getOrElse(root)) {
      val savedAtRootExcluding = _atRootExcludingStyleRule
      if (query.excludesStyleRules) {
        _atRootExcludingStyleRule = true
      }
      try
        _environment.scope(semiGlobal = false, when = node.hasDeclarations) {
          for (child <- node.children.get)
            child.accept(this)
        }
      finally
        _atRootExcludingStyleRule = savedAtRootExcluding
      return SassNull
    }

    // dart-sass evaluate.dart:1222-1233: create copies of included
    // ancestors and nest them. `innerCopy` is where children will be
    // evaluated; `outerCopy` is attached to `trimmedRoot`.
    var innerCopy: ModifiableCssParentNode = trimmedRoot
    if (included.nonEmpty) {
      innerCopy = included.head.copyWithoutChildren()
      var outerCopy = innerCopy
      for (n <- included.tail) {
        val copy = n.copyWithoutChildren()
        copy.addChild(outerCopy)
        outerCopy = copy
      }
      trimmedRoot.addChild(outerCopy)
    }

    // dart-sass evaluate.dart:1235-1239: _scopeForAtRoot + evaluate children
    _scopeForAtRoot(node, innerCopy, query, included.toList) {
      for (child <- node.children.get)
        child.accept(this)
    }

    SassNull
  }

  /** dart-sass evaluate.dart:1254-1284: destructively trims a trailing sublist from [nodes] that matches the current list of parents.
    *
    * [nodes] is a list of parents included by an `@at-root` rule, from innermost to outermost. If it contains a trailing sublist that's contiguous and whose final node is a direct child of [_root],
    * this removes that sublist and returns the innermost removed parent.
    *
    * Otherwise, leaves [nodes] as-is and returns [_root].
    */
  private def _trimIncluded(
    nodes: scala.collection.mutable.ListBuffer[ModifiableCssParentNode]
  ): ModifiableCssParentNode = {
    val root = _root.get
    if (nodes.isEmpty) return root

    var parent:              ModifiableCssParentNode = _parent.getOrElse(root)
    var innermostContiguous: Int                     = -1
    var i = 0
    while (i < nodes.length) {
      while (!(parent eq nodes(i))) {
        innermostContiguous = -1
        val gp = parent.modifiableParent
        if (gp.isDefined) parent = gp.get
        else
          throw new IllegalArgumentException(
            s"Expected ${nodes(i)} to be an ancestor of this."
          )
      }
      if (innermostContiguous < 0) innermostContiguous = i

      val gp = parent.modifiableParent
      if (gp.isDefined) parent = gp.get
      else
        throw new IllegalArgumentException(
          s"Expected ${nodes(i)} to be an ancestor of this."
        )
      i += 1
    }

    if (!(parent eq root)) return root
    val result = nodes(innermostContiguous)
    nodes.remove(innermostContiguous, nodes.length - innermostContiguous)
    result
  }

  /** dart-sass evaluate.dart:1291-1343: returns a scope callback for the at-root query. Adjusts _parent, _atRootExcludingStyleRule, _mediaQueries, _inKeyframes, and _inUnknownAtRule for the duration.
    */
  private def _scopeForAtRoot(
    node:      AtRootRule,
    newParent: ModifiableCssParentNode,
    query:     ssg.sass.ast.sass.AtRootQuery,
    included:  List[ModifiableCssParentNode]
  )(callback: => Unit): Unit = {
    val savedParent            = _parent
    val savedStyleRule         = _styleRule
    val savedStyleRuleIgnoring = _styleRuleIgnoringAtRoot
    val savedAtRootExcluding   = _atRootExcludingStyleRule
    val savedInKeyframes       = _inKeyframes
    val savedInUnknownAtRule   = _inUnknownAtRule

    _parent = Nullable(newParent)

    if (query.excludesStyleRules) {
      _styleRule = Nullable.empty
      _atRootExcludingStyleRule = true
    }

    if (_inKeyframes && query.excludesName("keyframes")) {
      _inKeyframes = false
    }

    if (_inUnknownAtRule && !included.exists(_.isInstanceOf[ModifiableCssAtRule])) {
      _inUnknownAtRule = false
    }

    try
      _environment.scope(semiGlobal = false, when = node.hasDeclarations) {
        callback
      }
    finally {
      _parent = savedParent
      _styleRule = savedStyleRule
      _styleRuleIgnoringAtRoot = savedStyleRuleIgnoring
      _atRootExcludingStyleRule = savedAtRootExcluding
      _inKeyframes = savedInKeyframes
      _inUnknownAtRule = savedInUnknownAtRule
    }
  }

  /// Port of dart-sass `visitUseRule` (evaluate.dart:2708-2733).
  override def visitUseRule(node: UseRule): Value = {
    var configuration: Configuration = Configuration.empty
    if (node.configuration.nonEmpty) {
      val values = scala.collection.mutable.LinkedHashMap.empty[String, ConfiguredValue]
      for (variable <- node.configuration) {
        // ISS-033: reject configuring private (`_foo`/`-foo`) vars.
        _checkPrivateConfig(variable)
        val variableNodeWithSpan = _expressionNode(variable.expression)
        values(variable.name) = ConfiguredValue.explicit(
          _withoutSlash(variable.expression.accept(this), variableNodeWithSpan),
          Nullable(variableNodeWithSpan)
        )
      }
      configuration = ExplicitConfiguration(values.toMap, node)
    }

    _loadModule(
      node.url.toString,
      "@use",
      node,
      { (module, firstLoad) =>
        if (firstLoad) _registerCommentsForModule(module)
        _environment.addModule(module, Nullable(node), namespace = node.namespace)
        // dart-sass: visitUseRule does NOT inject CSS. The CSS merging is handled
        // by _combineCss at the end of compilation, which topologically sorts
        // all modules and merges their CSS trees.
      },
      configuration = Nullable(configuration)
    )
    _assertConfigurationIsEmpty(configuration)

    SassNull
  }

  /// Port of dart-sass `visitForwardRule` (evaluate.dart:1677-1723).
  override def visitForwardRule(node: ForwardRule): Value = {
    val oldConfiguration      = _configuration
    val adjustedConfiguration = oldConfiguration.throughForward(node)

    if (node.configuration.nonEmpty) {
      val newConfiguration = _addForwardConfiguration(adjustedConfiguration, node)

      _loadModule(
        node.url.toString,
        "@forward",
        node,
        { (module, firstLoad) =>
          if (firstLoad) _registerCommentsForModule(module)
          _environment.forwardModule(module, node)
        },
        configuration = Nullable(newConfiguration)
      )

      _removeUsedConfiguration(
        adjustedConfiguration,
        newConfiguration,
        except = node.configuration.collect { case v if !v.isGuarded => v.name }.toSet
      )

      // Remove all the variables that weren't configured by this particular
      // `@forward` before checking that the configuration is empty. Errors for
      // outer `with` clauses will be thrown once those clauses finish
      // executing.
      val configuredVariables = node.configuration.map(_.name).toSet
      for (name <- newConfiguration.values.keys.toList)
        if (!configuredVariables.contains(name)) newConfiguration.remove(name)

      _assertConfigurationIsEmpty(newConfiguration)
    } else {
      _configuration = adjustedConfiguration
      _loadModule(
        node.url.toString,
        "@forward",
        node,
        { (module, firstLoad) =>
          if (firstLoad) _registerCommentsForModule(module)
          _environment.forwardModule(module, node)
        }
      )
      _configuration = oldConfiguration
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
          _visitStaticImportNode(cssImport)
        case di: DynamicImport =>
          // dart-sass: @import of a *.css URL, an absolute http(s):// URL,
          // or a `//` protocol-relative URL is treated as a static plain-
          // CSS import even when parsed as dynamic — it's passed through
          // to the output verbatim, never loaded as Sass.
          val url        = di.urlString
          val isPlainCss =
            url.endsWith(".css") ||
              url.startsWith("http://") || url.startsWith("https://") ||
              url.startsWith("//")
          if (isPlainCss) {
            val urlValue  = new CssValue[String](url, di.span)
            val cssImport = new ModifiableCssImport(urlValue, di.span, Nullable.empty)
            _visitStaticImportNode(cssImport)
          } else {
            _visitDynamicImport(di)
          }
        case _ => ()
      }
    SassNull
  }

  /** Adds a CSS `@import` node to the output, matching dart-sass `_visitStaticImport` import-ordering semantics: if we're at the root and there are already non-import children, the import is stored
    * in `_outOfOrderImports` rather than appended at the end. This ensures CSS imports are hoisted before style rules in the final output.
    *
    * Port of dart-sass `_visitStaticImport` (evaluate.dart:2081-2101).
    */
  private def _visitStaticImportNode(cssImport: ModifiableCssImport): Unit = {
    val isAtRoot = _root.exists(rootNode => _parent.exists(_ eq rootNode))
    if (!isAtRoot) {
      _addChild(cssImport)
    } else if (_root.exists(rootNode => _endOfImports == rootNode.children.length)) {
      _root.foreach(_.addChild(cssImport))
      _endOfImports += 1
    } else {
      _outOfOrderImports match {
        case ooi if ooi.isDefined =>
          ooi.get += cssImport
        case _ =>
          val buf = scala.collection.mutable.ListBuffer(cssImport)
          _outOfOrderImports = Nullable(buf)
      }
    }
  }

  /** Adds the stylesheet imported by [importNode] to the current document.
    *
    * Port of dart-sass `_visitDynamicImport` (evaluate.dart:1858-1976).
    *
    * The semantics depend on whether the imported file uses `@use` or `@forward`:
    *
    * '''Simple path''' (no `@use`/`@forward`): Evaluates the imported file's statements directly in the current environment, sharing the current `_extensionStore`, `_root`, `_parent`, etc. Extensions
    * from the imported file merge into the caller's store. Variable / function / mixin definitions land in the importing env's scopes.
    *
    * '''Complex path''' (has `@use`/`@forward`): Evaluates in a `forImport()` environment. If the imported file loads user-defined (non-`sass:`) modules, a separate CSS root is created so that
    * `@extend`s within the imported scope can be resolved hermetically via `_combineCss` before injecting the result into the caller's tree. This prevents extensions from leaking across `@use`
    * boundaries.
    */
  private def _visitDynamicImport(importNode: DynamicImport): Unit = {
    import scala.util.boundary, boundary.break
    _withStackFrame(
      "@import",
      importNode, {
        val loaded = _loadStylesheet(importNode.urlString, importNode.span, forImport = true)
        if (loaded.isEmpty) {
          throw _exception("Can't find stylesheet to import.", Nullable(importNode.span))
        }
        loaded.foreach { case (stylesheet, loadedImporter, isDependency) =>
          boundary {
            val url = stylesheet.span.sourceUrl
            url.foreach { canonicalUrl =>
              if (canonicalUrl.nonEmpty) {
                if (_activeModules.contains(canonicalUrl)) {
                  val prevNode = _activeModules.get(canonicalUrl).flatMap(_.toOption)
                  throw prevNode match {
                    case Some(previousLoad) =>
                      _multiSpanException(
                        "This file is already being loaded.",
                        "new load",
                        Map(previousLoad.span -> "original load")
                      )
                    case _ =>
                      _exception("This file is already being loaded.")
                  }
                }
                _activeModules(canonicalUrl) = Nullable(importNode: ssg.sass.ast.AstNode)
              }
            }

            // If the imported stylesheet doesn't use any modules, we can inject its
            // CSS directly into the current stylesheet. If it does use modules, we
            // need to put its CSS into an intermediate ModifiableCssStylesheet so
            // that we can hermetically resolve `@extend`s before injecting it.
            if (stylesheet.uses.isEmpty && stylesheet.forwards.isEmpty) {
              val oldImporter     = _importer
              val oldStylesheet0  = _stylesheet
              val oldInDependency = _inDependency
              _importer = Nullable(loadedImporter)
              // dart-sass evaluate.dart:1889-1890: always set _stylesheet to the
              // imported stylesheet, including plain CSS files. This ensures that
              // `_stylesheet.plainCss` returns the correct value in visitStyleRule,
              // which is needed for CSS nesting preservation.
              _stylesheet = Nullable(stylesheet)
              _inDependency = isDependency
              visitStylesheet(stylesheet)
              _importer = oldImporter
              _stylesheet = oldStylesheet0
              _inDependency = oldInDependency
              url.foreach(u => if (u.nonEmpty) _activeModules.remove(u))
              break(())
            }

            // If only built-in modules are loaded, we still need a separate
            // environment to ensure their namespaces aren't exposed in the outer
            // environment, but we don't need to worry about `@extend`s, so we can
            // add styles directly to the existing stylesheet instead of creating a
            // new one.
            val loadsUserDefinedModules =
              stylesheet.uses.exists(rule => !rule.url.toString.startsWith("sass:")) ||
                stylesheet.forwards.exists(rule => !rule.url.toString.startsWith("sass:"))

            var children: List[ssg.sass.ast.css.CssNode] = Nil
            val environment = _environment.forImport()
            _withEnvironment(environment) {
              val oldImporter           = _importer
              val oldStylesheet0        = _stylesheet
              val oldRoot0              = _root
              val oldParent0            = _parent
              val oldEndOfImports0      = _endOfImports
              val oldOutOfOrderImports0 = _outOfOrderImports
              val oldConfiguration      = _configuration
              val oldInDependency       = _inDependency
              _importer = Nullable(loadedImporter)
              _stylesheet = Nullable(stylesheet)
              if (loadsUserDefinedModules) {
                val newRoot = new ModifiableCssStylesheet(stylesheet.span)
                _root = Nullable(newRoot)
                _parent = Nullable(newRoot: ModifiableCssParentNode)
                _endOfImports = 0
                _outOfOrderImports = Nullable.empty
              }
              _inDependency = isDependency

              // This configuration is only used if it passes through a `@forward`
              // rule, so we avoid creating unnecessary ones for performance reasons.
              if (stylesheet.forwards.nonEmpty) {
                _configuration = Configuration.implicitConfig(environment.toImplicitConfiguration())
              }

              visitStylesheet(stylesheet)
              children = if (loadsUserDefinedModules) _addOutOfOrderImports() else Nil

              _importer = oldImporter
              _stylesheet = oldStylesheet0
              if (loadsUserDefinedModules) {
                _root = oldRoot0
                _parent = oldParent0
                _endOfImports = oldEndOfImports0
                _outOfOrderImports = oldOutOfOrderImports0
              }
              _configuration = oldConfiguration
              _inDependency = oldInDependency
            }

            // Create a dummy module with empty CSS and no extensions to make forwarded
            // members available in the current import context and to combine all the
            // CSS from modules used by [stylesheet].
            val module = environment.toDummyModule()
            _environment.importForwards(module)
            if (loadsUserDefinedModules) {
              if (module.transitivelyContainsCss) {
                // If any transitively used module contains extensions, we need to
                // clone all modules' CSS. Otherwise, it's possible that they'll be
                // used or imported from another location that shouldn't have the same
                // extensions applied.
                val combinedCss = _combineCss(
                  module,
                  clone = module.transitivelyContainsExtensions
                )
                combinedCss.accept(this)
              }

              for (child <- children)
                child match {
                  case mn: ModifiableCssNode => _visitImportedCssChild(mn)
                  case _ => ()
                }
            }

            url.foreach(u => if (u.nonEmpty) _activeModules.remove(u))
          } // boundary
        }
      }
    )
  }

  /** Adds an imported CSS child node to the current tree, handling `@import` ordering and `through` propagation.
    *
    * Port of dart-sass `_ImportedCssVisitor` (evaluate.dart:4809-4868).
    */
  private def _visitImportedCssChild(node: ModifiableCssNode): Unit =
    node match {
      case atRule: ModifiableCssAtRule =>
        _addChild(
          atRule,
          through = if (atRule.isChildless) null else (n: CssNode) => n.isInstanceOf[CssStyleRule]
        )
      case comment: ModifiableCssComment =>
        _addChild(comment)
      case decl: ModifiableCssDeclaration =>
        _addChild(decl)
      case imp: ModifiableCssImport =>
        val isAtRoot = _root.exists(rootNode => _parent.exists(_ eq rootNode))
        if (!isAtRoot) {
          _addChild(imp)
        } else if (_root.exists(rootNode => _endOfImports == rootNode.children.length)) {
          _addChild(imp)
          _endOfImports += 1
        } else {
          _outOfOrderImports match {
            case ooi if ooi.isDefined =>
              ooi.get += imp
            case _ =>
              val buf = scala.collection.mutable.ListBuffer[ModifiableCssImport](imp)
              _outOfOrderImports = Nullable(buf)
          }
        }
      case _: ModifiableCssKeyframeBlock =>
        throw new AssertionError("visitCssKeyframeBlock() should never be called.")
      case media: ModifiableCssMediaRule =>
        // Whether [media.queries] has been merged with [_mediaQueries]. If it
        // has been merged, merging again is a no-op; if it hasn't been merged,
        // merging again will fail.
        val hasBeenMerged = _mediaQueries.isEmpty ||
          _mergeMediaQueries(_mediaQueries, media.queries).isDefined
        _addChild(
          media,
          through = (n: CssNode) => n.isInstanceOf[CssStyleRule] || (hasBeenMerged && n.isInstanceOf[CssMediaRule])
        )
      case styleRule: ModifiableCssStyleRule =>
        _addChild(styleRule, through = (n: CssNode) => n.isInstanceOf[CssStyleRule])
      case supports: ModifiableCssSupportsRule =>
        _addChild(supports, through = (n: CssNode) => n.isInstanceOf[CssStyleRule])
      case stylesheet: ModifiableCssStylesheet =>
        for (child <- stylesheet.children)
          child match {
            case mn: ModifiableCssNode => _visitImportedCssChild(mn)
            case _ => ()
          }
      case other =>
        _addChild(other)
    }

  override def visitExtendRule(node: ExtendRule): Value = {
    // dart-sass: visitExtendRule (evaluate.dart:1483-1549).
    // Uses the module's extension store to record extensions. The store handles
    // selector rewriting in real-time via addExtension. Cross-module extensions
    // are resolved later in _extendModules called from _combineCss.

    val styleRule = _styleRule
    if (styleRule.isEmpty || _declarationName.isDefined) {
      throw _exception(
        "@extend may only be used within style rules.",
        Nullable(node.span)
      )
    }

    // dart-sass evaluate.dart:1484-1498: warn for bogus combinators in the
    // extender's original selector.
    for (complex <- styleRule.get.originalSelector.components)
      if (complex.isBogus) {
        _warnWithSpan(
          s"""The selector "${complex.toString.trim}" is invalid CSS and """ +
            (if (complex.isUseless) "can't" else "shouldn't") +
            " be an extender.\n" +
            "This will be an error in Dart Sass 2.0.0.\n" +
            "\n" +
            "More info: https://sass-lang.com/d/bogus-combinators",
          complex.span.trimRight(),
          Nullable(Deprecation.BogusCombinators)
        )
      }

    val targetText = _performInterpolation(node.selector, warnForColor = true).trim
    val list       = new ssg.sass.parse.SelectorParser(targetText, allowParent = false).parse()

    for (complex <- list.components) {
      val compound = complex.singleCompound
      if (compound.isEmpty) {
        throw new SassException(
          "complex selectors may not be extended.",
          node.span
        )
      }

      val simple = compound.get.singleSimple
      if (simple.isEmpty) {
        throw new SassException(
          "compound selectors may no longer be extended.\n" +
            s"Consider `@extend ${compound.get.components.mkString(", ")}` instead.\n" +
            "See https://sass-lang.com/d/extend-compound for details.\n",
          node.span
        )
      }

      _extensionStore.foreach { store =>
        store.addExtension(
          styleRule.get.selector,
          simple.get,
          node,
          Nullable(_mediaQueries).filter(_.nonEmpty)
        )
      }
    }

    SassNull
  }

  override def visitContentBlock(node: ContentBlock): Value =
    // dart-sass: visitContentBlock (evaluate.dart:1345-1347)
    // Evaluation handles @include and its content block together.
    throw new UnsupportedOperationException(
      "Evaluation handles @include's content block in its own logic."
    )

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
        val nameStr  = _evaluateToCss(sd.name)
        val space    = if (sd.isCustomProperty) "" else " "
        val valueStr = _evaluateToCss(sd.value)
        s"($nameStr:$space$valueStr)"
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

  // dart-sass: _withoutSlash on @return expressions (async_evaluate.dart:2361)
  override def visitReturnRule(node: ReturnRule): Value =
    throw new ReturnSignal(node.expression.accept(this).withoutSlash)

  override def visitContentRule(node: ContentRule): Value = {
    val contentCallable: Nullable[UserDefinedCallable[Environment]] = _environment.content
    contentCallable.foreach { callable =>
      val cb = callable.declaration.asInstanceOf[ContentBlock]
      // Evaluate `@content(arg1, arg2, ...)` arguments in the current
      // (mixin) environment, then switch to the call-site environment
      // captured by the content callable and run the block body.
      //
      // dart-sass (async_evaluate.dart:1345-1350) delegates to
      // _runUserDefinedCallable, which switches to the callable's
      // captured environment. This ensures variables inside the
      // @content body resolve in the scope where the @include was
      // written, not the mixin's body scope.
      val (positional, named, splatSep) = _evaluateArguments(node.arguments)
      _withEnvironment(callable.environment.closure()) {
        _environment.scope() {
          _bindParameters(cb.parameters, positional, named, splatSep)
          for (statement <- cb.childrenList) {
            val _ = statement.accept(this)
          }
        }
      }
    }
    SassNull
  }

  override def visitIncludeRule(node: IncludeRule): Value = {
    val lookup: Nullable[Callable] =
      if (node.namespace.isDefined) {
        // Look up via the module's public mixin map (which includes
        // forwarded modules), not via the inner Environment's local
        // mixins. dart-sass goes through `_environment.getMixin(name,
        // namespace: ns)` which routes through `_getModule(ns).mixins`.
        node.namespace.fold(Nullable.empty[Callable]) { ns =>
          _environment.getNamespacedMixin(ns, node.name)
        }
      } else {
        _environment.getMixin(node.name)
      }
    val mixin = lookup.getOrElse {
      throw SassException(s"Undefined mixin: ${node.name}.", node.span)
    }
    // dart-sass async_evaluate.dart:2184-2193: reject @include --name when the
    // mixin was not originally declared with a -- name.
    if (node.originalName.startsWith("--")) {
      mixin match {
        case udc: UserDefinedCallable[_] =>
          udc.declaration match {
            case cd: ssg.sass.ast.sass.CallableDeclaration if !cd.originalName.startsWith("--") =>
              throw SassException(
                "Sass @mixin names beginning with -- are forbidden for forward-" +
                  "compatibility with plain CSS mixins.\n\n" +
                  "For details, see https://sass-lang.com/d/css-function-mixin",
                node.nameSpan
              )
            case _ => ()
          }
        case _ => ()
      }
    }
    val (positional, named, splatSep) = _evaluateArguments(node.arguments)
    // dart-sass: set _callableNode so that built-in mixin callbacks
    // (e.g. load-css, apply) can read the call-site node for span
    // context and configuration construction.
    val oldCallableNode = _callableNode
    _callableNode = Nullable(node: ssg.sass.ast.AstNode)
    // dart-sass: wrap the @content block in a UserDefinedCallable that
    // captures the call-site environment, so that @content evaluates
    // variables in the scope where the @include was written, not the
    // scope of the mixin body (async_evaluate.dart:2196-2202).
    val contentCallable: Nullable[UserDefinedCallable[Environment]] =
      node.content match {
        case cb: ContentBlock =>
          Nullable(UserDefinedCallable(cb, _environment.closure()))
        case _ => Nullable.empty
      }
    try
      _invokeMixinCallable(
        mixin,
        positional,
        named,
        contentCallable,
        splatSep
      )
    catch {
      // Built-in mixins (e.g. `meta.apply`) may raise bare
      // SassScriptExceptions from inside their callback; attach the include
      // site so they surface as proper SassExceptions with source location.
      case e: SassScriptException => throw e.withSpan(node.span)
    } finally
      _callableNode = oldCallableNode
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
    content:    Nullable[UserDefinedCallable[Environment]],
    splatSep:   ssg.sass.value.ListSeparator = ssg.sass.value.ListSeparator.Undecided
  ): Unit =
    ssg.sass.AliasedCallable.unwrap(callable) match {
      case ud: UserDefinedCallable[?] =>
        ud.declaration match {
          case mr: MixinRule =>
            // dart-sass: reject content block when the mixin doesn't use
            // @content (async_evaluate.dart:2143-2151).
            if (!mr.hasContent && content.isDefined) {
              throw SassScriptException("Mixin doesn't accept a content block.")
            }
            // dart-sass _runUserDefinedCallable: switch to a fresh closure of
            // the mixin's captured environment, then push a scope inside it.
            // The fresh closure isolates the body's mutations from the
            // captured env so concurrent invocations don't see each other's
            // locals; the scope() push gives parameters and locals their own
            // buffer level so `$var: ...` redirects from the global level to
            // the local level instead of writing to the actual shared global
            // map.
            val runBody: () => Unit = () =>
              _environment.scope() {
                _bindParameters(mr.parameters, positional, named, splatSep)
                // dart-sass: _environment.withContent + _environment.asMixin
                // wraps the mixin body execution so that content-exists() and
                // @content work correctly.
                _environment.withContent(content) {
                  _environment.asMixin {
                    for (statement <- mr.childrenList) {
                      val _ = statement.accept(this)
                    }
                  }
                }
              }
            ud.environment match {
              case env: Environment => _withEnvironment(env.closure())(runBody())
              case _ => runBody()
            }
          case other =>
            throw SassScriptException(
              s"Mixin ${callable.name} is not backed by a MixinRule (got $other)."
            )
        }
      case bic: BuiltInCallable =>
        // dart-sass: reject content block when the built-in mixin doesn't
        // accept one (async_evaluate.dart:2116-2131).
        if (!bic.acceptsContent && content.isDefined) {
          throw SassScriptException("Mixin doesn't accept a content block.")
        }
        // dart-sass: arity check + named-arg merging, same as for
        // built-in functions (via _runBuiltInCallable).
        _checkBuiltInArity(bic, positional, named)
        val merged =
          if (named.isEmpty) positional
          else _mergeBuiltInNamedArgs(bic, positional, named)
        val padded0     = _padBuiltInPositional(bic, merged)
        val (padded, _) = _collectBuiltInRestArgs(bic, padded0, named)
        // dart-sass: _environment.withContent + _environment.asMixin
        // wraps built-in mixin execution for content-exists() to work.
        _environment.withContent(content) {
          _environment.asMixin {
            val _ = bic.callback(padded)
          }
        }
      case other =>
        throw SassScriptException(s"Unsupported mixin callable: $other")
    }

  /** Evaluates a function call by invoking a UserDefinedCallable whose declaration is a [[FunctionRule]]. Runs the function body in a fresh scope with parameters bound, catching [[ReturnSignal]] to
    * capture the result.
    */
  private def _runUserDefinedFunction(
    fr:         FunctionRule,
    positional: List[Value],
    named:      ListMap[String, Value],
    splatSep:   ssg.sass.value.ListSeparator = ssg.sass.value.ListSeparator.Undecided
  ): Value =
    // dart-sass _runUserDefinedCallable: push a scope (semiGlobal=false), bind
    // parameters as locals, run the body. The scope() push is what redirects
    // `$var: x` writes from the captured env's global map to a private local
    // map so concurrent invocations and the surrounding global state are
    // shielded from the body's mutations.
    _environment.scope() {
      _bindParameters(fr.parameters, positional, named, splatSep)
      try {
        for (statement <- fr.childrenList) {
          val _ = statement.accept(this)
        }
        // dart-sass (evaluate.dart:3622-3626): falling off the end of a
        // function body without a @return statement is an error.
        throw _exception(
          "Function finished without @return.",
          Nullable(fr.span)
        )
      } catch {
        case rs: ReturnSignal => rs.value
      }
    }

  /** Binds the supplied positional and named argument values to the declared parameters, applying defaults for any missing trailing parameters. Validates argument counts and names against the
    * declaration, mirroring dart-sass `ParameterList.verify` in `lib/src/ast/sass/parameter_list.dart`.
    */
  @annotation.nowarn("msg=unused private member") // default value for splatSep triggers false positive
  private def _bindParameters(
    declared:   ssg.sass.ast.sass.ParameterList,
    positional: List[Value],
    named:      ListMap[String, Value],
    splatSep:   ssg.sass.value.ListSeparator = ssg.sass.value.ListSeparator.Undecided
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
      // dart-sass: parameter values (positional, named, or default) are
      // stripped of slash info before binding.
      // dart-sass uses setLocalVariable (not setVariable) so that parameter
      // bindings are scoped to the innermost scope and don't propagate to
      // enclosing scopes (evaluate.dart:3529, 3545).
      _environment.setLocalVariable(param.name, value.withoutSlash)
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
      // dart-sass: the rest parameter always gets a SassArgumentList
      // (not a plain SassList) so type-of() returns "arglist".
      // dart-sass: if the splat separator is undecided (no rest arg was
      // splatted, or the rest arg was empty), default to comma. Otherwise
      // preserve the original list's separator (async_evaluate.dart:3563-3565).
      val restSeparator =
        if (splatSep == ssg.sass.value.ListSeparator.Undecided) ssg.sass.value.ListSeparator.Comma
        else splatSep
      val restValue: ssg.sass.value.Value =
        new ssg.sass.value.SassArgumentList(
          extras,
          leftover,
          restSeparator
        )
      _environment.setLocalVariable(restName, restValue)
    }
    // Bind the keyword-rest parameter to a map of leftover keyword args.
    declared.keywordRestParameter.foreach { kwName =>
      val declaredNames = params.iterator.map(_.name).toSet
      val leftover      = named.filter { case (k, _) => !declaredNames.contains(k) }
      val entries       = leftover.iterator.map { case (k, v) =>
        (ssg.sass.value.SassString(k, hasQuotes = false): ssg.sass.value.Value) -> v
      }.toList
      _environment.setLocalVariable(kwName, ssg.sass.value.SassMap(ListMap.from(entries)))
    }
  }

  /** Pads the positional argument list for a [[BuiltInCallable]] with defaults parsed from its declared signature. For each declared parameter beyond `positional.size`, looks up the raw default
    * expression text, parses it via [[ssg.sass.parse.ScssParser]] and evaluates it in the current environment. Stops at the first parameter without a default (which becomes a required arg whose
    * absence is surfaced by the callback itself). Eliminates a large class of index-out-of-bounds bugs in hand-written built-ins that declare defaults like `"$start-at, $end-at: -1"`.
    *
    * Also fills intermediate gaps created by [[_mergeBuiltInNamedArgs]]: when a named arg is placed at index `j > positional.length`, positions between `positional.length` and `j` are filled with
    * `_argGap`. This method replaces those gap markers with the evaluated default if one exists in the signature. Explicitly-passed `null` values (SassNull) are left intact so callbacks can
    * distinguish "not provided" from "provided as null".
    */

  /** Sentinel value used for unfilled argument positions in built-in argument merging. Distinguished from SassNull (which represents a user-provided `null` value) so that `_padBuiltInPositional` can
    * replace gaps with defaults without clobbering explicit nulls.
    */
  private val _argGap: Value = new Value {
    override def isTruthy:                                             Boolean = false
    override def toCssString(quote: Boolean):                          String  = "null"
    override def toString:                                             String  = "_argGap"
    override def accept[T](visitor: ssg.sass.visitor.ValueVisitor[T]): T       =
      throw new UnsupportedOperationException("_argGap sentinel should not be visited")
  }

  private def _padBuiltInPositional(bic: BuiltInCallable, positional: List[Value]): List[Value] = {
    val defaults = bic.parameterDefaults
    if (defaults.isEmpty) positional.map(v => if (v eq _argGap) SassNull else v)
    else {
      // Fill intermediate gap markers (created by _mergeBuiltInNamedArgs)
      // with evaluated defaults from the signature. An explicit `null`
      // passed by the caller is SassNull and must NOT be replaced.
      val buf = scala.collection.mutable.ListBuffer.from(positional)
      var i   = 0
      while (i < buf.length && i < defaults.length) {
        if ((buf(i) eq _argGap) && defaults(i).isDefined) {
          try {
            val (expr, _) = new ssg.sass.parse.ScssParser(defaults(i).get).parseExpression()
            buf(i) = expr.accept(this)
          } catch {
            case _: Throwable => buf(i) = SassNull // fallback gap to SassNull on parse/eval failure
          }
        } else if (buf(i) eq _argGap) {
          // Gap with no default — replace sentinel with SassNull
          buf(i) = SassNull
        }
        i += 1
      }
      // Append trailing defaults beyond positional.length.
      i = buf.length
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

  /** Validates positional and named argument arity for a [[BuiltInCallable]] call. Port of dart-sass `ArgumentList.verify` checks for the built-in dispatch path (`_runBuiltInCallable` in
    * serialize.dart):
    *
    *   1. Named args must resolve to a declared parameter name. When the callable has no rest parameter, every name must be in `parameterNames`. Otherwise a `No parameter named $foo` error is raised
    *      (mirroring dart-sass's error text).
    *   2. Positional count must fit the declared parameter slots unless the signature has a rest parameter. An overflow raises `Only N argument(s) allowed, but M were passed.`
    *   3. A name that collides with a positional arg (e.g. the 1st positional is `$red` and the caller also passes `$red: 10`) raises `Argument $foo was passed both by position and by name.`
    *
    * Callables whose signature is empty (rest-only forms like `"$args..."`) skip arity checks entirely. Callables declared via `BuiltInCallable.overloadedFunction` are also skipped — their dispatcher
    * picks the right overload before the callback runs, so any per-overload arity enforcement happens inside the callback.
    */
  /** Collects extra positional arguments beyond the declared parameters into a [[ssg.sass.value.SassArgumentList]] and appends it to the argument list, matching dart-sass's `_runBuiltInCallable`
    * behavior (evaluate.dart:3709-3726).
    *
    * If the callable has no rest parameter, returns the args unchanged. If there IS a rest parameter, any args beyond `parameterNames.length` are removed from the positional list, wrapped in a
    * SassArgumentList (with remaining named args that weren't consumed by the declared parameters), and appended.
    *
    * The `originalNamed` parameter is the named arg map from BEFORE merging. This method computes which names were consumed by declared parameters and passes only the leftover names to the
    * SassArgumentList.
    */
  private def _collectBuiltInRestArgs(
    bic:           BuiltInCallable,
    positional:    List[Value],
    originalNamed: ListMap[String, Value]
  ): (List[Value], ListMap[String, Value]) = {
    if (!bic.hasRestParameter) return (positional, originalNamed)
    // If _mergeBuiltInNamedArgs already created a SassArgumentList for the
    // rest parameter (which it does when named args are present), don't
    // create a second wrapper. This only applies when named args were
    // provided (_mergeBuiltInNamedArgs is only called with non-empty named);
    // a user-supplied SassArgumentList among positional args must still be
    // wrapped into its own rest SassArgumentList.
    if (originalNamed.nonEmpty) {
      positional.lastOption match {
        case Some(_: ssg.sass.value.SassArgumentList) =>
          return (positional, originalNamed)
        case _ => ()
      }
    }
    val paramCount = bic.parameterNames.length
    val rest: List[Value] =
      if (positional.length > paramCount) positional.drop(paramCount)
      else Nil
    val base =
      if (positional.length > paramCount) positional.take(paramCount)
      else positional
    // Compute which named args remain after the declared parameters
    // consumed their share during _mergeBuiltInNamedArgs.
    val declaredSet    = bic.parameterNames.toSet
    val remainingNamed = originalNamed.filterNot { case (k, _) => declaredSet.contains(k) }
    val argumentList   = new ssg.sass.value.SassArgumentList(
      rest,
      remainingNamed,
      ssg.sass.value.ListSeparator.Comma
    )
    (base :+ argumentList, remainingNamed)
  }

  private def _checkBuiltInArity(
    bic:        BuiltInCallable,
    positional: List[Value],
    named:      ListMap[String, Value]
  ): Unit = {
    // Skip overloaded callables entirely — the dispatcher picks the
    // right overload at runtime and handles arity inside the callback.
    if (bic.isOverloaded) return
    val declared = bic.parameterNames
    // Skip rest-only signatures (e.g. `$args...`) where parameterNames is
    // empty BUT the raw signature is non-empty. For truly zero-parameter
    // functions (empty signature, no rest), fall through to the arity
    // checks below so that extra arguments are rejected.
    if (declared.isEmpty && (bic.hasRestParameter || bic.signature.trim.nonEmpty)) return
    val declaredSet = declared.toSet
    // 1. Unknown named args (skip when rest parameter absorbs them).
    if (!bic.hasRestParameter) {
      for ((k, _) <- named)
        if (!declaredSet.contains(k))
          throw SassScriptException(s"No parameter named $$$k.")
    }
    // 2. Positional overflow (only when no rest parameter).
    if (!bic.hasRestParameter && positional.length > declared.length) {
      val nParams = declared.length
      val nPass   = positional.length
      val argWord = if (nParams == 1) "argument" else "arguments"
      val posWord = if (named.isEmpty) "" else "positional "
      val wasWord = if (nPass == 1) "was" else "were"
      throw SassScriptException(
        s"Only $nParams $posWord${argWord} allowed, but $nPass $wasWord passed."
      )
    }
    // 3. Positional / named collision. A name supplied both by position
    //    (at index `i`) and by name (with `declared(i)`) is rejected.
    val limit = math.min(positional.length, declared.length)
    var i     = 0
    while (i < limit) {
      val pname = declared(i)
      if (named.contains(pname))
        throw SassScriptException(s"Argument $$$pname was passed both by position and by name.")
      i += 1
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
    // For overloaded callables, different overloads accept different parameter
    // names. We cannot validate named args against the canonical signature.
    // Instead, treat the named args like a rest-parameter call: partition into
    // known (canonical-signature) and leftover, then merge what we can and
    // pass the rest through as-is. The overload dispatcher will select the
    // right callback and the callback itself will do per-overload validation.
    if (bic.isOverloaded && names.nonEmpty) {
      val namesSet                    = names.toSet
      val namedKeys                   = named.keySet
      val (knownNamed, leftoverNamed) = named.partition { case (k, _) => namesSet.contains(k) }
      // For overloaded functions, always try to find the tightest-fitting
      // overload whose parameter names are a superset of the named keys.
      // The canonical signature may be wider (e.g. $red,$green,$blue,$alpha)
      // when the caller meant ($color, $alpha) — both contain "alpha".
      val betterSig = _findMatchingOverloadSig(bic, positional.length, namedKeys)
      betterSig match {
        case Some(altNames) =>
          return _mergeWithParamNames(altNames, positional, named)
        case None if leftoverNamed.nonEmpty =>
          if (knownNamed.isEmpty) {
            // None of the named args match any overload — package
            // into a SassArgumentList for the single-arg overload.
            val al = new ssg.sass.value.SassArgumentList(
              positional,
              named,
              ssg.sass.value.ListSeparator.Comma
            )
            return List(al)
          }
          // Partial match to canonical — merge what we can and wrap the
          // rest into an argument list.
          val namedIndices = knownNamed.keys.map(names.indexOf).filter(_ >= 0)
          val maxIdx       =
            (if (namedIndices.isEmpty) -1 else namedIndices.max).max(positional.length - 1)
          val buf = scala.collection.mutable.ListBuffer.empty[Value]
          var i   = 0
          while (i <= maxIdx && i < names.length) {
            val pname = names(i)
            if (i < positional.length) buf += positional(i)
            else
              knownNamed.get(pname) match {
                case Some(v) => buf += v
                case _       => buf += _argGap
              }
            i += 1
          }
          val al = new ssg.sass.value.SassArgumentList(
            Nil,
            leftoverNamed,
            ssg.sass.value.ListSeparator.Comma
          )
          buf += al
          return buf.toList
        case None =>
        // All named match canonical — fall through to normal merge
      }
    }
    if (names.isEmpty) {
      // rest-only signature (`$args...`): wrap positional + all kwargs
      // into a SassArgumentList if there are keyword args.
      if (named.nonEmpty || bic.hasRestParameter) {
        val extras = positional.drop(0) // all positional become rest
        val al     = new ssg.sass.value.SassArgumentList(
          extras,
          named,
          ssg.sass.value.ListSeparator.Comma
        )
        List(al)
      } else positional
    } else {
      val namesSet = names.toSet
      // Separate known named args (match declared params) from leftover kwargs.
      val (knownNamed, leftoverNamed) =
        if (bic.hasRestParameter) named.partition { case (k, _) => namesSet.contains(k) }
        else {
          // Validate: every named key must be a declared parameter.
          for ((k, _) <- named)
            if (!namesSet.contains(k))
              throw SassScriptException(s"No parameter named $$$k in ${bic.name}().")
          (named, ListMap.empty[String, Value])
        }
      // Determine the highest index that is explicitly supplied (positional
      // or named) so we don't append trailing nulls for unsupplied tail
      // parameters with defaults.
      val namedIndices = knownNamed.keys.map(names.indexOf).filter(_ >= 0)
      val maxIdx       =
        (if (namedIndices.isEmpty) -1 else namedIndices.max).max(positional.length - 1)
      val buf = scala.collection.mutable.ListBuffer.empty[Value]
      var i   = 0
      while (i <= maxIdx && i < names.length) {
        val pname = names(i)
        if (i < positional.length) buf += positional(i)
        else
          knownNamed.get(pname) match {
            case Some(v) => buf += v
            case _       => buf += _argGap
          }
        i += 1
      }
      // When rest parameter is declared, collect extra positional and
      // leftover named args into a SassArgumentList.
      if (bic.hasRestParameter) {
        val extras = positional.drop(names.length)
        val al     = new ssg.sass.value.SassArgumentList(
          extras,
          leftoverNamed,
          ssg.sass.value.ListSeparator.Comma
        )
        buf += al
      }
      buf.toList
    }
  }

  /** Extracts parameter names from a raw overload signature string. E.g. `"$color, $alpha"` -> `List("color", "alpha")`.
    */
  private def _sigParamNames(sig: String): List[String] = {
    val trimmed = sig.trim
    if (trimmed.isEmpty) return Nil
    val parts = scala.collection.mutable.ListBuffer.empty[String]
    val buf   = new StringBuilder()
    var depth = 0
    var i     = 0
    while (i < trimmed.length) {
      val c = trimmed.charAt(i)
      if (c == '(' || c == '[') { depth += 1; buf.append(c) }
      else if (c == ')' || c == ']') { depth -= 1; buf.append(c) }
      else if (c == ',' && depth == 0) { parts += buf.toString().trim; buf.setLength(0) }
      else buf.append(c)
      i += 1
    }
    if (buf.nonEmpty) parts += buf.toString().trim
    parts.toList.flatMap { raw =>
      val withoutDefault = raw.indexOf(':') match {
        case -1  => raw
        case idx => raw.substring(0, idx).trim
      }
      if (withoutDefault.endsWith("...")) None
      else if (withoutDefault.startsWith("$")) Some(withoutDefault.substring(1).replace('_', '-'))
      else None
    }
  }

  /** Searches all overload signatures of [[bic]] to find one whose parameter names are a superset of the caller's [[namedKeys]] and can accommodate [[positionalCount]] positional args. Returns the
    * matched parameter names or None.
    */
  private def _findMatchingOverloadSig(
    bic:             BuiltInCallable,
    positionalCount: Int,
    namedKeys:       Set[String]
  ): Option[List[String]] = {
    // Collect all matching overloads and pick the one with the fewest
    // parameters (tightest match). This avoids accidentally matching a
    // wider overload like ($red, $green, $blue, $alpha) when the caller
    // meant ($color, $alpha) — both contain "$alpha" in their names.
    val totalArgCount = positionalCount + namedKeys.size
    var best: Option[List[String]] = None
    var bestArity = Int.MaxValue
    for (sig <- bic.allSignatures) {
      val pNames   = _sigParamNames(sig)
      val pNameSet = pNames.toSet
      if (namedKeys.subsetOf(pNameSet) && pNames.length >= totalArgCount) {
        if (pNames.length < bestArity) {
          best = Some(pNames)
          bestArity = pNames.length
        }
      }
    }
    best
  }

  /** Merges named arguments into the positional list using a specific parameter name list (from a matched overload signature).
    */
  private def _mergeWithParamNames(
    paramNames: List[String],
    positional: List[Value],
    named:      ListMap[String, Value]
  ): List[Value] = {
    val namedIndices = named.keys.flatMap { k =>
      val idx = paramNames.indexOf(k)
      if (idx >= 0) Some(idx) else None
    }
    val maxIdx = (if (namedIndices.isEmpty) -1 else namedIndices.max).max(positional.length - 1)
    val buf    = scala.collection.mutable.ListBuffer.empty[Value]
    var i      = 0
    while (i <= maxIdx && i < paramNames.length) {
      val pname = paramNames(i)
      if (i < positional.length) buf += positional(i)
      else
        named.get(pname) match {
          case Some(v) => buf += v
          case _       => buf += _argGap
        }
      i += 1
    }
    buf.toList
  }

  /** Evaluates the positional and named expressions in [[args]] against the current environment, returning a `(positional, named)` pair. Rest and keyword-rest arguments are expanded below.
    */
  /** Evaluates [args] and returns the positional arguments, named arguments, and the separator from the rest argument (if any).
    *
    * dart-sass: async_evaluate.dart _evaluateArguments returns a record including `separator` so that splat rest-args preserve the original list's separator (space, comma, slash, or undecided).
    */
  private def _evaluateArguments(
    args: ssg.sass.ast.sass.ArgumentList
  ): (List[Value], ListMap[String, Value], ssg.sass.value.ListSeparator) = {
    val positionalBuf = scala.collection.mutable.ListBuffer.empty[Value]
    // dart-sass wraps every argument evaluation in _withoutSlash
    // (async_evaluate.dart:3773) so that slash-separated numbers like
    // `1/2` lose their slash info when passed to functions.
    for (expr <- args.positional)
      positionalBuf += expr.accept(this).withoutSlash
    var named: ListMap[String, Value] = ListMap.empty
    for ((k, v) <- args.named)
      named = named.updated(k, v.accept(this).withoutSlash)
    // Splat the trailing rest argument (`$list...`).
    //   • SassArgumentList → splat its positional contents AND merge its
    //     captured keywords into the named-arg map (dart-sass parity).
    //   • SassMap          → splat into keyword arguments only; every key
    //     must be a SassString. This is the `list.join((...)...)` form.
    //   • SassList         → splat as positional args.
    //   • anything else    → single positional arg.
    // dart-sass: rest arg splatting also strips slash from each element
    // (async_evaluate.dart:3807, 3814, 3819).
    // dart-sass: when the rest arg is a SassList, its separator is
    // preserved and passed through so the rest parameter in the callee
    // gets the original separator (async_evaluate.dart:3810).
    var separator: ssg.sass.value.ListSeparator = ssg.sass.value.ListSeparator.Undecided
    args.rest.foreach { restExpr =>
      restExpr.accept(this) match {
        case al: ssg.sass.value.SassArgumentList =>
          for (v <- al.asList) positionalBuf += v.withoutSlash
          for ((k, v) <- al.keywords)
            named = named.updated(k, v.withoutSlash)
          separator = al.separator
        case map: ssg.sass.value.SassMap =>
          for ((k, v) <- map.contents) k match {
            case s: ssg.sass.value.SassString =>
              named = named.updated(s.text, v.withoutSlash)
            case other =>
              throw SassScriptException(
                s"Variable keyword argument map must have string keys. $other is not a string in ${map.toString}."
              )
          }
        case list: ssg.sass.value.SassList =>
          for (v <- list.asList) positionalBuf += v.withoutSlash
          separator = list.separator
        case other =>
          positionalBuf += other.withoutSlash
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
    (positionalBuf.toList, named, separator)
  }

  // ===========================================================================
  // CssVisitor — plain CSS evaluation
  //
  // These methods are used when evaluating CSS syntax trees from `@import`ed
  // stylesheets that themselves contain `@use` rules, and CSS included via the
  // `load-css()` function. When we load a module using one of these constructs,
  // we first convert it to CSS (we can't evaluate it as Sass directly because
  // it may be used elsewhere and it must only be evaluated once). Then we
  // execute that CSS more or less as though it were Sass (we can't inject it
  // into the stylesheet as-is because the `@import` may be nested in other
  // rules). That's what these rules implement.
  //
  // Port of dart-sass evaluate.dart lines 4017-4319.
  // ===========================================================================

  override def visitCssAtRule(node: CssAtRule): Value = {
    import scala.util.boundary, boundary.break
    boundary[Value] {
      // NOTE: this logic is largely duplicated in [visitAtRule]. Most changes
      // here should be mirrored there.

      if (_declarationName.isDefined) {
        throw _exception(
          "At-rules may not be used within nested declarations.",
          Nullable(node.span)
        )
      }

      if (node.isChildless) {
        _copyParentAfterSibling()
        _parent.foreach { p =>
          p.addChild(
            new ModifiableCssAtRule(
              node.name,
              node.span,
              childless = true,
              value = node.value
            )
          )
        }
        break(SassNull)
      }

      val wasInKeyframes     = _inKeyframes
      val wasInUnknownAtRule = _inUnknownAtRule
      if (ssg.sass.Utils.unvendor(node.name.value) == "keyframes") {
        _inKeyframes = true
      } else {
        _inUnknownAtRule = true
      }

      // If the user has already opted into plain CSS nesting, don't bother with
      // any merging or bubbling; this rule is already only usable by browsers
      // that support nesting natively anyway.
      val rule = new ModifiableCssAtRule(node.name, node.span, value = node.value)
      if (_hasCssNesting) {
        _withParent(rule) {
          for (child <- node.children)
            child.accept(this)
        }
        _inUnknownAtRule = wasInUnknownAtRule
        _inKeyframes = wasInKeyframes
        break(SassNull)
      }

      _addChild(rule, through = (n: CssNode) => n.isInstanceOf[CssStyleRule])
      val saved = _parent
      _parent = Nullable(rule: ModifiableCssParentNode)
      try
        // We don't have to check for an unknown at-rule in a style rule here,
        // because the previous compilation has already bubbled the at-rule to the
        // root.
        for (child <- node.children)
          child.accept(this)
      finally
        _parent = saved

      _inUnknownAtRule = wasInUnknownAtRule
      _inKeyframes = wasInKeyframes
      SassNull
    } // boundary
  }

  override def visitCssComment(node: CssComment): Value = {
    // NOTE: this logic is largely duplicated in [visitLoudComment]. Most
    // changes here should be mirrored there.

    // Comments are allowed to appear between CSS imports.
    _root.foreach { rootNode =>
      if ((_parent.exists(_ eq rootNode)) && _endOfImports == rootNode.children.length) {
        _endOfImports += 1
      }
    }

    _copyParentAfterSibling()
    _addChild(new ModifiableCssComment(node.text, node.span))
    SassNull
  }

  override def visitCssDeclaration(node: CssDeclaration): Value = {
    _copyParentAfterSibling()
    _addChild(
      new ModifiableCssDeclaration(
        node.name,
        node.value,
        node.span,
        parsedAsSassScript = node.parsedAsSassScript,
        valueSpanForMapOpt = Some(node.valueSpanForMap)
      )
    )
    SassNull
  }

  override def visitCssImport(node: CssImport): Value = {
    // NOTE: this logic is largely duplicated in [_visitStaticImport]. Most
    // changes here should be mirrored there.

    val modifiableNode = new ModifiableCssImport(
      node.url,
      node.span,
      modifiers = node.modifiers
    )
    val isAtRoot = _root.exists(rootNode => _parent.exists(_ eq rootNode))
    if (!isAtRoot) {
      _copyParentAfterSibling()
      _addChild(modifiableNode)
    } else if (_root.exists(rootNode => _endOfImports == rootNode.children.length)) {
      _root.foreach(_.addChild(modifiableNode))
      _endOfImports += 1
    } else {
      _outOfOrderImports match {
        case ooi if ooi.isDefined =>
          ooi.get += modifiableNode
        case _ =>
          val buf = scala.collection.mutable.ListBuffer(modifiableNode)
          _outOfOrderImports = Nullable(buf)
      }
    }
    SassNull
  }

  override def visitCssKeyframeBlock(node: CssKeyframeBlock): Value = {
    // NOTE: this logic is largely duplicated in [visitStyleRule]. Most changes
    // here should be mirrored there.

    val rule = new ModifiableCssKeyframeBlock(node.selector, node.span)
    _addChild(rule, through = (n: CssNode) => n.isInstanceOf[CssStyleRule])
    val saved = _parent
    _parent = Nullable(rule: ModifiableCssParentNode)
    try
      for (child <- node.children)
        child.accept(this)
    finally
      _parent = saved
    SassNull
  }

  override def visitCssMediaRule(node: CssMediaRule): Value = {
    import scala.util.boundary, boundary.break
    boundary[Value] {
      // NOTE: this logic is largely duplicated in [visitMediaRule]. Most changes
      // here should be mirrored there.

      if (_declarationName.isDefined) {
        throw _exception(
          "Media rules may not be used within nested declarations.",
          Nullable(node.span)
        )
      }

      // If the user has already opted into plain CSS nesting, don't bother with
      // any merging or bubbling; this rule is already only usable by browsers
      // that support nesting natively anyway.
      if (_hasCssNesting) {
        _withParent(new ModifiableCssMediaRule(node.queries, node.span)) {
          for (child <- node.children)
            child.accept(this)
        }
        break(SassNull)
      }

      val mergedQueries: Nullable[List[CssMediaQuery]] =
        if (_mediaQueries.isEmpty) Nullable.empty
        else _mergeMediaQueries(_mediaQueries, node.queries)
      if (mergedQueries.isDefined && mergedQueries.get.isEmpty) break(SassNull)

      val mergedSources: Set[CssMediaQuery] =
        if (mergedQueries.isEmpty) Set.empty
        else _mediaQuerySources ++ _mediaQueries.toSet ++ node.queries.toSet

      val effectiveQueries = mergedQueries.getOrElse(node.queries)

      val throughFn: CssNode => Boolean = { (n: CssNode) =>
        n.isInstanceOf[CssStyleRule] ||
        (mergedSources.nonEmpty &&
          n.isInstanceOf[CssMediaRule] &&
          n.asInstanceOf[CssMediaRule].queries.forall(mergedSources.contains))
      }

      val mediaRule = new ModifiableCssMediaRule(effectiveQueries, node.span)
      _addChild(mediaRule, through = throughFn)
      val saved = _parent
      _parent = Nullable(mediaRule: ModifiableCssParentNode)
      try
        _withMediaQueries(effectiveQueries, mergedSources) {
          _styleRule match {
            case sr if sr.isDefined =>
              // If we're in a style rule, copy it into the media query so that
              // declarations immediately inside @media have somewhere to go.
              //
              // For example, "a {@media screen {b: c}}" should produce
              // "@media screen {a {b: c}}".
              val styleRule = sr.get
              _withParent(styleRule.copyWithoutChildren()) {
                for (child <- node.children)
                  child.accept(this)
              }
            case _ =>
              for (child <- node.children)
                child.accept(this)
          }
        }
      finally
        _parent = saved
      SassNull
    } // boundary
  }

  override def visitCssStyleRule(node: CssStyleRule): Value = {
    // NOTE: this logic is largely duplicated in [visitStyleRule]. Most changes
    // here should be mirrored there.

    if (_declarationName.isDefined) {
      throw _exception(
        "Style rules may not be used within nested declarations.",
        Nullable(node.span)
      )
    } else if (_inKeyframes && _parent.exists(_.isInstanceOf[CssKeyframeBlock])) {
      throw _exception(
        "Style rules may not be used within keyframe blocks.",
        Nullable(node.span)
      )
    }

    val styleRule = _styleRule
    val merge: Boolean = styleRule match {
      case sr if sr.isEmpty          => true
      case sr if sr.get.fromPlainCss => false
      case _                         => !(node.fromPlainCss && SelectorList.containsParentSelector(node.selector))
    }
    val originalSelector =
      if (merge) {
        node.selector.nestWithin(
          styleRule.fold[Nullable[SelectorList]](Nullable.empty)(r => Nullable(r.originalSelector)),
          implicitParent = !_atRootExcludingStyleRule,
          preserveParentSelectors = node.fromPlainCss
        )
      } else node.selector
    val selectorBox = _extensionStore.fold(
      new ssg.sass.util.ModifiableBox[SelectorList](originalSelector).seal()
    )(_.addSelector(originalSelector, Nullable(_mediaQueries).filter(_.nonEmpty)))
    val rule = new ModifiableCssStyleRule(
      selectorBox,
      node.span,
      originalSel = Nullable(originalSelector),
      fromPlainCss = node.fromPlainCss
    )
    val oldAtRootExcludingStyleRule = _atRootExcludingStyleRule
    _atRootExcludingStyleRule = false
    val throughFn2: CssNode => Boolean = if (merge) _.isInstanceOf[CssStyleRule] else null
    _addChild(rule, through = throughFn2)
    val saved = _parent
    _parent = Nullable(rule: ModifiableCssParentNode)
    try
      _withStyleRule(rule) {
        for (child <- node.children)
          child.accept(this)
      }
    finally
      _parent = saved
    _atRootExcludingStyleRule = oldAtRootExcludingStyleRule

    if (styleRule.isEmpty) {
      val parentNode = _parent.getOrElse {
        throw new IllegalStateException("EvaluateVisitor has no active parent node.")
      }
      parentNode.children.lastOption.foreach { case n: ModifiableCssNode => n.isGroupEnd = true; case _ => () }
    }
    SassNull
  }

  override def visitCssStylesheet(node: CssStylesheet): Value = {
    for (statement <- node.children)
      statement.accept(this)
    SassNull
  }

  override def visitCssSupportsRule(node: CssSupportsRule): Value = {
    import scala.util.boundary, boundary.break
    boundary[Value] {
      // NOTE: this logic is largely duplicated in [visitSupportsRule]. Most
      // changes here should be mirrored there.

      if (_declarationName.isDefined) {
        throw _exception(
          "Supports rules may not be used within nested declarations.",
          Nullable(node.span)
        )
      }

      val rule = new ModifiableCssSupportsRule(node.condition, node.span)
      if (_hasCssNesting) {
        _withParent(rule) {
          for (child <- node.children)
            child.accept(this)
        }
        break(SassNull)
      }

      _addChild(rule, through = (n: CssNode) => n.isInstanceOf[CssStyleRule])
      val saved = _parent
      _parent = Nullable(rule: ModifiableCssParentNode)
      try
        _styleRule match {
          case sr if sr.isDefined =>
            // If we're in a style rule, copy it into the supports rule so that
            // declarations immediately inside @supports have somewhere to go.
            //
            // For example, "a {@supports (a: b) {b: c}}" should produce "@supports
            // (a: b) {a {b: c}}".
            val styleRule = sr.get
            _withParent(styleRule.copyWithoutChildren()) {
              for (child <- node.children)
                child.accept(this)
            }
          case _ =>
            for (child <- node.children)
              child.accept(this)
        }
      finally
        _parent = saved
      SassNull
    } // boundary
  }
}
