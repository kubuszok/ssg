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
import ssg.sass.util.{ FileSpan, ModifiableBox }
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
import ssg.sass.{ BuiltInCallable, Callable, Configuration, ConfiguredValue, Environment, ExplicitConfiguration, ImportCache, Logger, Module, Nullable, SassException, SassScriptException, UserDefinedCallable }
import ssg.sass.extend.{ ExtendMode, Extension, ExtensionStore, ExtendUtils, MutableExtensionStore }
import ssg.sass.importer.Importer
import ssg.sass.parse.SelectorParser
import ssg.sass.ast.selector.{ ComplexSelector, ComplexSelectorComponent, CompoundSelector, QualifiedName, SelectorList, TypeSelector }

/** Result of evaluating a Sass stylesheet — a CSS AST plus the set of URLs that were loaded during evaluation.
  */
final case class EvaluateResult(
  stylesheet: CssStylesheet,
  loadedUrls: Set[String],
  warnings:   List[String] = Nil
)

/** A visitor that executes Sass code to produce a CSS AST.
  *
  * Port of dart-sass `_EvaluateVisitor` (evaluate.dart). Implements expression evaluation, statement-level visitors, CssVisitor for plain-CSS re-evaluation, callables, modules, and CSS tree construction.
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
      case (l: SassNumber, r: SassNumber)
          if node.allowsSlash && _operandAllowsSlash(node.left) && _operandAllowsSlash(node.right) =>
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

  /** The name of the current member being evaluated (for stack traces).
    * Corresponds to Dart's `_member`.
    */
  private var _member: String = "root stylesheet"

  /** The evaluation stack, used for stack traces. Each entry is a
    * (member-name, node-with-span) pair. Corresponds to Dart's `_stack`.
    */
  private val _stack: scala.collection.mutable.ListBuffer[(String, ssg.sass.ast.AstNode)] =
    scala.collection.mutable.ListBuffer.empty

  /** The node for the innermost callable invocation being evaluated.
    * Used by `_addExceptionSpan` and `currentCallableSpan`.
    * Corresponds to Dart's `_callableNode`.
    */
  private var _callableNode: Nullable[ssg.sass.ast.AstNode] = Nullable.empty

  /** Whether we're currently in a `@dependency` context. When true and
    * `_quietDeps` is also true, deprecation warnings are suppressed.
    */
  private var _inDependency: Boolean = false

  /** Whether to suppress deprecation warnings for dependencies. */
  private val _quietDeps: Boolean = false

  /** Set of (message, span) pairs that have already been warned about,
    * used to deduplicate warnings. Corresponds to Dart's `_warningsEmitted`.
    */
  private val _warningsEmitted: scala.collection.mutable.Set[(String, ssg.sass.util.FileSpan)] =
    scala.collection.mutable.Set.empty

  /** The configuration map pushed in by an enclosing `@use "mid" with (...)`. `visitForwardRule` — and the recursive `@use` of the target inside a module that is itself being loaded — consult
    * this map so that configured variables flow through `@forward` chains. Keys are unprefixed variable names. Entries are evaluated expressions (not the raw AST) because the outer `with` clause must
    * be evaluated in the *caller's* environment, not the forwarded module's.
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
    BuiltInCallable.mixin("load-css", "$url, $with: null", { (arguments: List[Value]) =>
      val url = arguments.head.assertString("url").text
      val withRaw = arguments(1)
      val withMap: Nullable[SassMap] =
        if (withRaw == SassNull) Nullable.empty
        else Nullable(withRaw.assertMap("with"))

      val callableNode = _callableNode.getOrElse {
        throw SassScriptException("load-css() requires an active callable context.")
      }
      var configuration: Configuration = Configuration.empty
      withMap.foreach { wm =>
        val values = scala.collection.mutable.LinkedHashMap.empty[String, ConfiguredValue]
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
        namesInErrors = true
      )
      _assertConfigurationIsEmpty(configuration, nameInError = true)
      SassNull
    })

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

  /** Index of the end of the leading `@import`/`@use`/`@forward` block in `_root.children`. Used for import ordering and parity with the Dart evaluator.
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

  /** An `@extend` whose target must be matched somewhere in the same media scope, unless it is marked `!optional`. Populated by [[visitExtendRule]] and validated at the end of [[_applyExtends]].
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
    ModifiableBox[SelectorList]
  ] = scala.collection.mutable.LinkedHashMap.empty

  // ---------------------------------------------------------------------------
  // Public entry points
  // ---------------------------------------------------------------------------

  /** Evaluate a parsed [[Stylesheet]] to a CSS AST.
    *
    * The entrypoint sets up top-level state and calls `visitStylesheet`
    * directly (like the pre-module era). `@use`/`@forward` within the
    * stylesheet call `_loadModule` -> `_execute` to load child modules.
    * After the tree walk, `_applyExtends` handles selector rewriting
    * for the legacy path, and `_combineCss` will be wired in later
    * for full module-CSS merging.
    */
  def run(stylesheet: Stylesheet): EvaluateResult = {
    val root = new ModifiableCssStylesheet(stylesheet.span)
    _root = Nullable(root)
    _parent = Nullable(root: ModifiableCssParentNode)
    _endOfImports = 0
    _stylesheet = Nullable(stylesheet)

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
    url:            String,
    stackFrame:     String,
    nodeWithSpan:   ssg.sass.ast.AstNode,
    callback:       (Module[Callable], Boolean) => Unit,
    configuration:  Nullable[Configuration] = Nullable.empty,
    namesInErrors:  Boolean = false
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

    _withStackFrame(stackFrame, nodeWithSpan, {
      val loaded = _loadStylesheet(url, nodeWithSpan.span)
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

        val firstLoad = !_modules.contains(canonicalUrl)
        val oldInDependency = _inDependency
        _inDependency = isDependency
        val module: Module[Callable] =
          try {
            _execute(
              Nullable(imp),
              importedSheet,
              configuration = configuration,
              nodeWithSpan = Nullable(nodeWithSpan),
              namesInErrors = namesInErrors
            )
          } finally {
            if (canonicalUrl.nonEmpty) _activeModules.remove(canonicalUrl)
            _inDependency = oldInDependency
          }

        _addExceptionSpan(nodeWithSpan, callback(module, firstLoad), addStackFrame = false)
      }
    })
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
        if (!_moduleConfigurations.get(url).exists(_.sameOriginal(currentConfiguration)) &&
          currentConfiguration.isInstanceOf[ExplicitConfiguration] &&
          // Don't throw an error if the module being loaded doesn't expose any
          // configurable variables that could have been affected by the
          // configuration in the first place.
          alreadyLoaded.couldHaveBeenConfigured(currentConfiguration.values.keySet)) {
          val message =
            if (namesInErrors) s"$url was already loaded, so it can't be configured using \"with\"."
            else "This module was already loaded, so it can't be configured using \"with\"."

          val existingSpan = _moduleNodes.get(url).map(_.span)
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
    var css: Nullable[CssStylesheet] = Nullable.empty
    var preModuleComments: Nullable[scala.collection.mutable.LinkedHashMap[Module[Callable], scala.collection.mutable.ListBuffer[CssComment]]] = Nullable.empty
    val extensionStore = ExtensionStore()

    _withEnvironment(environment) {
      val oldImporter = _importer
      val oldStylesheet = _stylesheet
      val oldRoot = _root
      val oldPreModuleComments = _preModuleComments
      val oldParent = _parent
      val oldEndOfImports = _endOfImports
      val oldOutOfOrderImports = _outOfOrderImports
      val oldExtensionStore = _extensionStore
      val oldStyleRule = _styleRule
      val oldStyleRuleIgnoring = _styleRuleIgnoringAtRoot
      val oldMediaQueries = _mediaQueries
      val oldDeclarationName = _declarationName
      val oldInUnknownAtRule = _inUnknownAtRule
      val oldAtRootExcludingStyleRule = _atRootExcludingStyleRule
      val oldInKeyframes = _inKeyframes
      val oldConfiguration = _configuration

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
    val module = environment.toModule(css.getOrElse {
      throw new IllegalStateException("_execute: CSS stylesheet was not produced.")
    }, extensionStore, preModuleComments = preModuleCommentsMap)
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
  private def _addOutOfOrderImports(): List[ssg.sass.ast.css.CssNode] = {
    _outOfOrderImports.fold[List[ssg.sass.ast.css.CssNode]](_root.get.children) { outOfOrderImports =>
      val rootChildren = _root.get.children
      (rootChildren.take(_endOfImports) ++ outOfOrderImports.toList ++ rootChildren.drop(_endOfImports))
    }
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
  @annotation.nowarn("msg=unused") // will be wired into run() when module CSS merging is complete
  private def _combineCss(root: Module[Callable], clone: Boolean = false): CssStylesheet = {
    import scala.util.boundary, boundary.break
    boundary[CssStylesheet] {
    if (!root.upstream.exists(_.transitivelyContainsCss)) {
      val selectors = root.extensionStore.simpleSelectors
      root.extensionStore.extensionsWhereTarget(target => !selectors.contains(target))
        .headOption.foreach { unsatisfiedExtension =>
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

      for (upstream <- effectiveMod.upstream) {
        if (upstream.transitivelyContainsCss) {
          effectiveMod.preModuleComments.get(upstream).foreach { comments =>
            // Intermix the top-level comments with plain CSS `@import`s until we
            // start to have actual CSS defined, at which point start treating it as
            // normal CSS.
            (if (cssStatements.isEmpty) imports else cssStatements) ++= comments
          }
          visitModule(upstream)
        }
      }

      sorted.prepend(effectiveMod)
      val statements = effectiveMod.css.children
      val index = _indexAfterImports(statements)
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
      unsatisfiedExtensions ++= module.extensionStore.extensionsWhereTarget(
        target => !originalSelectors.contains(target)
      )

      module.url.foreach { url =>
        downstreamExtensionStores.get(url).foreach { stores =>
          module.extensionStore.addExtensions(stores)
        }
      }
      if (!module.extensionStore.isEmpty) {
        for (upstream <- module.upstream) {
          upstream.url.foreach { url =>
            downstreamExtensionStores.getOrElseUpdate(url, scala.collection.mutable.ListBuffer.empty) +=
              module.extensionStore
          }
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
  private def _throwForUnsatisfiedExtension(extension: Extension): Nothing = {
    throw new SassException(
      "The target selector was not found.\n" +
        s"""Use "@extend ${extension.target} !optional" to avoid this error.""",
      extension.span
    )
  }

  /// Returns the index of the first node in [statements] that comes after all
  /// static imports.
  ///
  /// Port of dart-sass `_indexAfterImports` (evaluate.dart:1149-1163).
  private def _indexAfterImports(statements: List[ssg.sass.ast.css.CssNode]): Int = {
    var lastImport = -1
    var i = 0
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
      _effectiveImportCache.importCanonical(
        cr.importer,
        cr.canonicalUrl,
        originalUrl = Nullable(cr.originalUrl)
      ).map { stylesheet =>
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

  /// Modules whose CSS has already been injected into the root stylesheet.
  /// Used by _injectModuleCss to prevent duplicate CSS output when the same
  /// module is @use'd from multiple files.
  private val _injectedModuleCss: scala.collection.mutable.Set[Module[Callable]] =
    scala.collection.mutable.Set.empty

  /// Injects all CSS from [module] and its transitive upstream modules into
  /// [rootNode]. Uses a global visited set so each module's CSS is injected
  /// exactly once, preventing duplication when a module is @use'd multiple
  /// times.
  private def _injectModuleCss(module: Module[Callable], rootNode: ModifiableCssStylesheet): Unit = {
    def visit(mod: Module[Callable]): Unit = {
      if (!_injectedModuleCss.add(mod)) () // already processed
      else {
        // Visit upstream first (depth-first, same order as _combineCss).
        for (upstream <- mod.upstream) {
          if (upstream.transitivelyContainsCss) visit(upstream)
        }
        // Then inject this module's own CSS.
        for (child <- mod.css.children) {
          child match {
            case mn: ModifiableCssNode => rootNode.addChild(mn)
            case _ => ()
          }
        }
      }
    }
    visit(module)
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
    for (variable <- node.configuration) {
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
  ): Unit = {
    for (name <- upstream.values.keys.toList) {
      if (!except.contains(name) && !downstream.values.contains(name)) {
        upstream.remove(name)
      }
    }
  }

  /// Throws an error if [configuration] contains any values.
  ///
  /// Port of dart-sass `_assertConfigurationIsEmpty` (evaluate.dart:1794-1812).
  private def _assertConfigurationIsEmpty(
    configuration: Configuration,
    nameInError:   Boolean = false
  ): Unit = {
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
        bic.callback(_padBuiltInPositional(bic, merged))
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
        _slash(left, node.right.accept(this), node)
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
        else _addExceptionSpan(node, {
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
              ssg.sass.functions.Functions.lookupGlobal(node.name)
                .fold(Nullable.empty[Callable])(b => Nullable(b: Callable))
            }
          }
        })
      // User-defined functions shadow CSS math functions (dart-sass order).
      // Built-in functions do NOT shadow — CSS calc/min/max/etc. take
      // precedence over same-named built-ins.
      val isUserDefined = callable.exists(c =>
        ssg.sass.AliasedCallable.unwrap(c).isInstanceOf[ssg.sass.UserDefinedCallable[?]]
      )
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
      callable.fold[Value] {
        // Render unknown function as plain CSS: `name(arg1, arg2, ...)`.
        val args = node.arguments.positional.map(a => _evaluateToCss(a))
        new SassString(s"${node.originalName}(${args.mkString(", ")})", hasQuotes = false)
      } { c =>
        // Built-in callable dispatch: evaluate positional args and call.
        // Unwrap any AliasedCallable wrapper (inserted by `@forward ... as
        // prefix-*`) so the underlying BuiltInCallable / UserDefinedCallable
        // is reachable.
        ssg.sass.AliasedCallable.unwrap(c) match {
          case bic: ssg.sass.BuiltInCallable =>
            val (positional, named) = _evaluateArguments(node.arguments)
            _checkBuiltInArity(bic, positional, named)
            val merged              =
              if (named.isEmpty) positional
              else _mergeBuiltInNamedArgs(bic, positional, named)
            // dart-sass: _withoutSlash strips slash-separated info from
            // the result of built-in function calls (async_evaluate.dart:3609).
            // dart-sass: wrap built-in invocations with _addExceptionSpan
            // so SassScriptExceptions from callbacks get proper span context.
            val oldCallableNode = _callableNode
            _callableNode = Nullable(node: ssg.sass.ast.AstNode)
            val bicResult = _addExceptionSpan(node,
              bic.callback(_padBuiltInPositional(bic, merged))
            ).withoutSlash
            _callableNode = oldCallableNode
            bicResult
          case ud: UserDefinedCallable[?] =>
            ud.declaration match {
              case fr: FunctionRule =>
                val (positional, named) = _evaluateArguments(node.arguments)
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
                      _runUserDefinedFunction(fr, positional, named)
                    }
                  case _ =>
                    _runUserDefinedFunction(fr, positional, named)
                }
              case _ =>
                throw SassScriptException(s"Callable ${node.name} is not a function.")
            }
          case other =>
            throw SassScriptException(s"Callable type not yet supported: $other")
        }
      }
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
        val sb = new StringBuilder("if(")
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
        // Attach a span so this surfaces as a proper SassException rather
        // than a bare SassScriptException (which the runner reports as
        // "script-error" because it lacks a source location).
        throw SassScriptException("Duplicate key.").withSpan(key.span)
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
    // Ported from dart-sass visitSupportsExpression (evaluate.dart).
    // Evaluates the condition and returns its CSS text as an unquoted string.
    new SassString(_visitSupportsCondition(node.condition), hasQuotes = false)

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
        case ((_: IfConditionParenthesized, s)) :: Nil =>
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
    try {
      def toArg(expr: Expression): Any = expr match {
        case ParenthesizedExpression(inner, _) =>
          // Ported from dart-sass _visitCalculationExpression ParenthesizedExpression case.
          val result = toArg(inner)
          result match {
            case s: SassString => SassString(s"(${s.text})", hasQuotes = false)
            case _             => result
          }
        case BinaryOperationExpression(op, l, r, _) =>
          // --- Reject non-calc operators (dart-sass _binaryOperatorToCalculationOperator lines 3434-3441) ---
          val co: CalculationOperator = op match {
            case BinaryOperator.Plus      => CalculationOperator.Plus
            case BinaryOperator.Minus     => CalculationOperator.Minus
            case BinaryOperator.Times     => CalculationOperator.Times
            case BinaryOperator.DividedBy => CalculationOperator.DividedBy
            case _ =>
              throw SassException("This operation can't be used in a calculation.", node.span)
          }
          SassCalculation.operateInternal(
            co, toArg(l), toArg(r),
            inLegacySassFunction = Nullable.Null,
            simplify = !_inSupportsDeclaration,
            warn = Nullable.Null
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
        case _ =>
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
            case _: SassNumber                   => v
            case _: SassCalculation              => v
            case s: SassString if !s.hasQuotes   => v
            case result =>
              throw SassException(s"Value $result can't be used in a calculation.", node.span)
          }
      }
      val converted = args.map(toArg)
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
        case "abs" =>
          converted.head match {
            case n: SassNumber if n.hasUnit("%") =>
              warnForDeprecation(
                Deprecation.AbsPercent,
                "Passing percentages to the global abs() function is deprecated. Recommendation: math.abs($number) (with the sass:math module)."
              )
            case _ => ()
          }
          SassCalculation.abs(converted.head)
        case "sign"  => SassCalculation.sign(converted.head)
        case "exp"   => SassCalculation.exp(converted.head)
        case "atan2" => SassCalculation.atan2(converted(0), Nullable(converted(1)))
        case "pow"   => SassCalculation.pow(converted(0), Nullable(converted(1)))
        case "log" if converted.length == 1 => SassCalculation.log(converted.head, Nullable.empty[Any])
        case "log"   => SassCalculation.log(converted(0), Nullable(converted(1)))
        case "mod"   => SassCalculation.mod(converted(0), Nullable(converted(1)))
        case "rem"   => SassCalculation.rem(converted(0), Nullable(converted(1)))
        case "round" if converted.length == 1 => SassCalculation.round(converted.head)
        case "round" if converted.length == 2 => SassCalculation.round(converted(0), Nullable(converted(1)))
        case "round" => SassCalculation.round(converted(0), Nullable(converted(1)), Nullable(converted(2)))
        case _       => return Nullable.empty
      }
      Nullable(result)
    } catch {
      case e: SassException            => throw e
      case _: SassScriptException      => Nullable.empty
      case _: IllegalArgumentException => Nullable.empty
    }
  }

  /** Verifies that the calculation [node] has the correct number of arguments.
    *
    * Ported from: dart-sass `_checkCalculationArguments` (async_evaluate.dart lines 3211-3252).
    */
  private def _checkCalculationArguments(node: FunctionExpression): Unit = {
    val argCount = node.arguments.positional.length
    def check(maxArgs: Int = -1): Unit = {
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
        val currentNode = node.contents(i)
        val isUnaryPlusMinus = currentNode match {
          case UnaryOperationExpression(UnaryOperator.Minus | UnaryOperator.Plus, _, _) => true
          case ne: NumberExpression if ne.value < 0                                     => true
          case _                                                                        => false
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
  @annotation.nowarn("msg=unused private member") // called from visitVariableDeclaration path
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
      while (through(parent)) {
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
  @annotation.nowarn("msg=unused private member") // scaffolding: wired when CSS nesting output is ported
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
      while (current.isDefined) {
        current.get.parent match {
          case p if p.isDefined =>
            p.get match {
              case _: CssStyleRule => break(true)
              case gp: CssParentNode => current = Nullable(gp)
              case _ => break(false)
            }
          case _ => break(false)
        }
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
  @annotation.nowarn("msg=unused private member") // scaffolding: wired when @media query merging is ported
  private def _mergeMediaQueries(
    queries1: Iterable[CssMediaQuery],
    queries2: Iterable[CssMediaQuery]
  ): Nullable[List[CssMediaQuery]] = {
    import scala.util.boundary, boundary.break
    val queries = scala.collection.mutable.ListBuffer.empty[CssMediaQuery]
    boundary[Nullable[List[CssMediaQuery]]] {
      for (query1 <- queries1) {
        for (query2 <- queries2) {
          query1.merge(query2) match {
            case ssg.sass.ast.css.MediaQueryMergeResult.Empty => ()
            case ssg.sass.ast.css.MediaQueryMergeResult.Unrepresentable => break(Nullable.empty)
            case ssg.sass.ast.css.MediaQueryMergeResult.Success(query) => queries += query
          }
        }
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
  @annotation.nowarn("msg=unused private member") // scaffolding: wired when @media query scoping is ported
  private def _withMediaQueries[T](
    queries: List[CssMediaQuery],
    sources: Set[CssMediaQuery],
    body:    => T
  ): T = {
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

  /** Adds a frame to the stack with the given [member] name, and [nodeWithSpan]
    * as the site of the new frame.
    *
    * Runs [callback] with the new stack.
    *
    * This takes an AstNode rather than a FileSpan so it can avoid calling
    * AstNode.span if the span isn't required, since some nodes need to do
    * real work to manufacture a source span.
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

  /** Like [[ssg.sass.value.Value.withoutSlash]], but produces a deprecation warning if [value]
    * was a slash-separated number.
    *
    * Port of dart-sass `_withoutSlash` (evaluate.dart:4635-4657).
    */
  @annotation.nowarn("msg=unused private member") // called from _runUserDefinedCallable path (not yet ported)
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

  /** Returns a [MultiSpanSassRuntimeException] with the given [message],
    * [primaryLabel], and [secondaryLabels].
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

  /** Runs [callback], and converts any [SassScriptException]s it throws to
    * [SassRuntimeException]s with [nodeWithSpan]'s source span.
    *
    * This takes an AstNode rather than a FileSpan so it can avoid calling
    * AstNode.span if the span isn't required, since some nodes need to do
    * real work to manufacture a source span.
    *
    * If [addStackFrame] is true (the default), this will add an innermost stack
    * frame for [nodeWithSpan]. Otherwise, it will use the existing stack as-is.
    *
    * Port of dart-sass `_addExceptionSpan` (evaluate.dart:4734-4750).
    */
  private def _addExceptionSpan[T](
    nodeWithSpan:  ssg.sass.ast.AstNode,
    callback:      => T,
    addStackFrame: Boolean = true
  ): T = {
    try {
      callback
    } catch {
      case error: SassScriptException =>
        val spanToUse = if (addStackFrame) Nullable(nodeWithSpan.span) else Nullable.empty[ssg.sass.util.FileSpan]
        throw error.withSpan(nodeWithSpan.span) match {
          case se: SassException => se.withTrace(_stackTrace(spanToUse))
        }
    }
  }

  /** Runs [callback], and converts any [SassException]s that aren't already
    * [SassRuntimeException]s to [SassRuntimeException]s with the current stack
    * trace.
    *
    * Port of dart-sass `_addExceptionTrace` (evaluate.dart:4755-4767).
    */
  @annotation.nowarn("msg=unused private member") // called from _execute path (wired for @use/@forward)
  private def _addExceptionTrace[T](callback: => T): T = {
    try {
      callback
    } catch {
      case error: SassRuntimeException => throw error
      case error: SassException =>
        throw error.withTrace(_stackTrace(Nullable(error.span)))
    }
  }

  /** Runs [callback], and converts any [SassRuntimeException]s containing an
    * @error to throw a more relevant [SassRuntimeException] with [nodeWithSpan]'s
    * source span.
    *
    * Port of dart-sass `_addErrorSpan` (evaluate.dart:4772-4783).
    */
  @annotation.nowarn("msg=unused private member") // called from visitFunctionExpression / visitInterpolatedFunctionExpression in Dart; full wiring requires _runFunctionCallable port
  private def _addErrorSpan[T](nodeWithSpan: ssg.sass.ast.AstNode, callback: => T): T = {
    try {
      callback
    } catch {
      case error: SassRuntimeException =>
        if (!error.span.text.startsWith("@error")) throw error
        throw SassRuntimeException(error.sassMessage, nodeWithSpan.span, _stackTrace())
    }
  }

  /** Returns the [AstNode] whose span should be used for [expression].
    *
    * If [expression] is a variable reference, AstNode's span will be the span
    * where that variable was originally declared. Otherwise, this will just
    * return [expression].
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
    case e        =>
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
    arguments:   ssg.sass.ast.sass.ArgumentList,
    callable:    BuiltInCallable,
    nodeWithSpan: ssg.sass.ast.AstNode
  ): Value = {
    val (positional, named) = _evaluateArguments(arguments)

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
    val padded = _padBuiltInPositional(callable, merged)

    // Build a SassArgumentList if there's a rest parameter, so the
    // callback can call `wereKeywordsAccessed` to check if all named
    // args were consumed.
    var argumentList: Nullable[ssg.sass.value.SassArgumentList] = Nullable.empty
    if (callable.hasRestParameter) {
      // Find any SassArgumentList in the padded args (from _mergeBuiltInNamedArgs)
      padded.lastOption match {
        case Some(al: ssg.sass.value.SassArgumentList) =>
          argumentList = Nullable(al)
        case _ => ()
      }
    }

    val result: Value =
      try {
        _addExceptionSpan(nodeWithSpan, callable.callback(padded))
      } catch {
        case e: SassException => throw e
        case error: Throwable =>
          throw _exception(_getErrorMessage(error), Nullable(nodeWithSpan.span))
      }
    _callableNode = oldCallableNode

    // dart-sass: if there was a SassArgumentList and keywords remain
    // unconsumed (the callback never called `keywords`), raise an error
    // listing the unexpected parameter names.
    argumentList.foreach { al =>
      if (named.nonEmpty && !al.wereKeywordsAccessed) {
        val unusedNames = named.keys.map(n => s"$$$n")
        val word        = Utils.pluralize("parameter", named.size)
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
  private def _warnForBogusCombinators(rule: ModifiableCssStyleRule): Unit = {
    // dart-sass calls rule.isInvisibleOtherThanBogusCombinators which delegates
    // to the style rule's own visibility check. We use the selector's version.
    if (!rule.selector.isInvisibleOtherThanBogusCombinators) {
      for (complex <- rule.selector.components) {
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
    SassNull
  }

  override def visitStyleRule(node: StyleRule): Value = {
    // Evaluate the selector interpolation. The resolved text is parsed
    // into an AST-based selector matching the ModifiableCssStyleRule contract.
    val childSelectorText: String = node.selector.fold(
      node.parsedSelector.fold("")(ps => ps.toString)
    )(interpolation => _performInterpolation(interpolation))

    // Text-based parent (`&`) expansion. When nested inside another style
    // rule, combine the child selector with the parent's selector text:
    // for each comma-separated child piece, replace `&` with each parent
    // piece, or prepend the parent piece + space if `&` is absent. Cross
    // multiple parent and child commas to flatten the result.
    // dart-sass: use _styleRuleIgnoringAtRoot for & resolution inside @at-root.
    val parentSelectorRule: Nullable[ModifiableCssStyleRule] = _styleRuleIgnoringAtRoot
    val implicitParent: Boolean = !_atRootExcludingStyleRule
    val parentText: Nullable[String] =
      if (implicitParent) _styleRule.fold[Nullable[String]](Nullable.empty)(p => Nullable(p.selector.toString))
      else Nullable.empty
    val expandedSelector: String = _expandSelector(childSelectorText, parentText)

    val parsedExpanded: Nullable[SelectorList] = {
      val childParsed = SelectorParser.tryParse(childSelectorText)
      val parentParsed: Nullable[SelectorList] = parentSelectorRule.map(_.originalSelector)
      if (childParsed.isEmpty) SelectorParser.tryParse(expandedSelector)
      else if (parentParsed.isEmpty) Nullable(childParsed.get)
      else
        try Nullable(childParsed.get.nestWithin(parentParsed, implicitParent = implicitParent))
        catch { case _: Exception => SelectorParser.tryParse(expandedSelector) }
    }

    // In the Dart evaluator, `selector` is always a SelectorList. When
    // AST-based expansion fails, parse the text-expanded selector as a
    // final fallback. If that also fails (e.g. keyframe selectors like
    // `0%` which are not valid CSS selectors), wrap the raw text in a
    // synthetic SelectorList so the type contract is honoured.
    val selectorList: SelectorList = parsedExpanded.getOrElse {
      SelectorParser.tryParse(expandedSelector).getOrElse {
        _syntheticSelectorList(expandedSelector, node.span)
      }
    }
    val modifiableSelectorBox = new ModifiableBox[SelectorList](selectorList)
    val selectorBox           = modifiableSelectorBox.seal()
    val rule                  = new ModifiableCssStyleRule(selectorBox, node.span)
    _selectorBoxes(rule) = modifiableSelectorBox

    // Nested style rules in CSS output must be FLAT — they should be
    // emitted as siblings of the outer style rule rather than children.
    // Walk up `_parent` to the nearest non-CssStyleRule ancestor and add
    // the new rule there, then evaluate children with that as the parent.
    // dart-sass async_evaluate.dart:2470-2472: when a style rule finishes
    // at the top level (no enclosing style rule), mark the last child of the
    // target parent as isGroupEnd so the serializer knows to emit a blank line.
    val outerStyleRule = _styleRule
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
    // Mark group end for blank-line control. Use the last VISIBLE child
    // dart-sass: emit deprecation warnings for bogus combinators (evaluate.dart:2486-2536).
    _warnForBogusCombinators(rule)
    // (dart-sass skips invisible nodes in the serializer, so marking an
    // invisible empty rule would miss the intended group boundary).
    if (outerStyleRule.isEmpty && nearestNonStyle.children.nonEmpty) {
      nearestNonStyle.children.reverseIterator
        .collectFirst { case m: ModifiableCssNode if !m.isInvisible => m }
        .foreach(_.isGroupEnd = true)
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

  /** Creates a synthetic SelectorList for text that cannot be parsed as a CSS
    * selector (e.g. keyframe selectors like `0%`, `from`, `to`). This is a
    * temporary workaround until the evaluator properly routes keyframe blocks
    * through a KeyframeSelectorParser like Dart does.
    */
  private def _syntheticSelectorList(text: String, span: ssg.sass.util.FileSpan): SelectorList = {
    val synSpan   = FileSpan.synthetic(text)
    val typeSelec  = new TypeSelector(QualifiedName(text), synSpan)
    val compound   = new CompoundSelector(List(typeSelec), synSpan)
    val component  = new ComplexSelectorComponent(compound, Nil, synSpan)
    val complex    = new ComplexSelector(Nil, List(component), synSpan)
    new SelectorList(List(complex), synSpan)
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

    // Nested declarations: each child declaration's name is prefixed with
    // the parent name + "-". E.g., `border: { color: red; }` produces
    // `border-color: red`. dart-sass evaluate.dart visitDeclaration.
    node.children.foreach { kids =>
      _withScope {
        for (statement <- kids) {
          statement match {
            case childDecl: Declaration =>
              // Create a prefixed child declaration: parent-child
              val childNameText = _performInterpolation(childDecl.name)
              val prefixedName  = s"$nameText-$childNameText"
              val prefixedNameValue = new CssValue[String](prefixedName, childDecl.name.span)
              childDecl.value.foreach { expression =>
                val rawValue = expression.accept(this)
                val cssVal: Value =
                  if (childDecl.parsedAsSassScript) rawValue
                  else rawValue match {
                    case s: SassString => s
                    case other => new SassString(other.toCssString(quote = false), hasQuotes = false)
                  }
                val valueWrapper = new CssValue[Value](cssVal, expression.span)
                _addChild(new ModifiableCssDeclaration(
                  prefixedNameValue, valueWrapper, childDecl.span,
                  parsedAsSassScript = childDecl.parsedAsSassScript,
                  isImportant = childDecl.isImportant
                ))
              }
              // Recurse for nested-nested declarations
              childDecl.children.foreach { grandkids =>
                val syntheticNode = Declaration.nested(
                  Interpolation.plain(prefixedName, childDecl.name.span),
                  grandkids,
                  childDecl.span,
                  childDecl.value
                )
                syntheticNode.accept(this)
              }
            case other =>
              val _ = other.accept(this)
          }
        }
      }
    }
    SassNull
  }

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

        val existing = _environment.getVariable(node.name)
        if (existing.isDefined && existing.get != SassNull) break(SassNull)
      }

      // dart-sass: _withoutSlash strips slash info on variable assignment
      // (async_evaluate.dart:2687).
      val value = node.expression.accept(this).withoutSlash
      if (node.isGlobal) {
        _environment.setGlobalVariable(node.name, value)
      } else {
        _environment.setVariable(node.name, value)
      }
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
      }
      catch { case e: SassScriptException => throw e.withSpan(node.to.span) }

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
      var iterations = 0
      while (node.condition.accept(this).isTruthy) {
        iterations += 1
        if (iterations > 100000) {
          throw SassException("@while loop exceeded 100000 iterations (possible infinite loop).", node.span)
        }
        for (statement <- node.children.get) {
          val _ = statement.accept(this)
        }
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
      // At-rules like @font-face and @keyframes bubble up from nested
      // style rules. Unlike @media/@supports (which wrap a copy of the
      // enclosing style rule inside), @font-face and @keyframes just
      // hoist to the nearest non-style-rule parent without any selector
      // wrapping.
      val nameLower = nameText.toLowerCase
      val shouldBubble = nameLower == "font-face" || nameLower == "keyframes" ||
        nameLower == "-webkit-keyframes" || nameLower == "-moz-keyframes" ||
        nameLower == "-o-keyframes" || nameLower == "-ms-keyframes"
      val enclosingStyleRule = _styleRule

      if (shouldBubble && enclosingStyleRule.isDefined) {
        val savedParent = _parent
        val nearestNonStyle = _nearestNonStyleRuleParent()
        _parent = Nullable(nearestNonStyle)
        try
          _withParent(rule) {
            _withScope {
              for (statement <- node.children.get) {
                val _ = statement.accept(this)
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
  @annotation.nowarn("msg=unused private member") // scaffolding: used when @media query merge tracking is wired
  private var _mediaQuerySources: Set[CssMediaQuery] = Set.empty

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

    val savedParent              = _parent
    val savedStyleRule           = _styleRule
    val savedStyleRuleIgnoring   = _styleRuleIgnoringAtRoot
    val savedAtRootExcluding     = _atRootExcludingStyleRule
    _parent = Nullable(newParent)
    // dart-sass: set _atRootExcludingStyleRule when excluding style rules.
    // _styleRuleIgnoringAtRoot keeps the real parent for `&` resolution.
    if (query.excludesStyleRules) {
      _styleRule = Nullable.empty
      _atRootExcludingStyleRule = true
    }
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
      _styleRuleIgnoringAtRoot = savedStyleRuleIgnoring
      _atRootExcludingStyleRule = savedAtRootExcluding
    }
    SassNull
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

    _loadModule(node.url.toString, "@use", node, { (module, firstLoad) =>
      if (firstLoad) _registerCommentsForModule(module)
      _environment.addModule(module, Nullable(node), namespace = node.namespace)
      // Inject the module's CSS (and all upstream modules' CSS) into the
      // current tree. The global _injectedModuleCss set ensures each
      // module's CSS is only emitted once even when @use'd multiple times.
      _root.foreach { rootNode =>
        _injectModuleCss(module, rootNode)
      }
    }, configuration = Nullable(configuration))
    _assertConfigurationIsEmpty(configuration)

    SassNull
  }

  /// Port of dart-sass `visitForwardRule` (evaluate.dart:1677-1723).
  override def visitForwardRule(node: ForwardRule): Value = {
    val oldConfiguration = _configuration
    val adjustedConfiguration = oldConfiguration.throughForward(node)

    if (node.configuration.nonEmpty) {
      val newConfiguration = _addForwardConfiguration(adjustedConfiguration, node)

      _loadModule(node.url.toString, "@forward", node, { (module, firstLoad) =>
        if (firstLoad) _registerCommentsForModule(module)
        _environment.forwardModule(module, node)
      }, configuration = Nullable(newConfiguration))

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
      for (name <- newConfiguration.values.keys.toList) {
        if (!configuredVariables.contains(name)) newConfiguration.remove(name)
      }

      _assertConfigurationIsEmpty(newConfiguration)
    } else {
      _configuration = adjustedConfiguration
      _loadModule(node.url.toString, "@forward", node, { (module, firstLoad) =>
        if (firstLoad) _registerCommentsForModule(module)
        _environment.forwardModule(module, node)
      })
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
          _addChild(cssImport)
        case di: DynamicImport =>
          // dart-sass: @import of a *.css URL, an absolute http(s):// URL,
          // or a `//` protocol-relative URL is treated as a static plain-
          // CSS import even when parsed as dynamic — it's passed through
          // to the output verbatim, never loaded as Sass.
          val url = di.urlString
          val isPlainCss =
            url.endsWith(".css") ||
              url.startsWith("http://") || url.startsWith("https://") ||
              url.startsWith("//")
          if (isPlainCss) {
            val urlValue = new CssValue[String](url, di.span)
            val cssImport = new ModifiableCssImport(urlValue, di.span, Nullable.empty)
            _addChild(cssImport)
          } else {
            _loadDynamicImport(url)
          }
        case _ => ()
      }
    SassNull
  }

  /** Loads a dynamic `@import` via the configured importer, parses the
    * contents, and evaluates the resulting stylesheet.
    *
    * dart-sass `_visitDynamicImport` model:
    *   - If the imported file uses no `@use` or `@forward`, evaluate its
    *     children directly in the current environment. Variable / function /
    *     mixin definitions land in the importing env's scopes via the
    *     normal evaluator code paths.
    *   - If the imported file does have `@use` or `@forward`, evaluate it
    *     in a `forImport()` environment that shares variable / function /
    *     mixin storage with the caller but has its own `_modules` /
    *     `_globalModules` / `_forwardedModules`. After the body has run,
    *     seal the forImport env as a dummy module and call
    *     `_environment.importForwards(dummyModule)` on the outer env. This
    *     hoists the imported file's `@forward`ed modules into the outer
    *     env's `_importedModules` (at root) or `_nestedForwardedModules`
    *     (below root) so the importing scope can resolve them via
    *     `_fromOneModule` AND so variable assignments find the right
    *     backing storage in the upstream module.
    */
  private def _loadDynamicImport(url: String): Unit = {
    val loaded = _loadStylesheet(url, ssg.sass.util.FileSpan.synthetic("<@import>"), forImport = true)
    loaded.foreach { case (importedSheet, _, _) =>
      val canonicalUrl = importedSheet.span.sourceUrl.getOrElse("")
      if (canonicalUrl.nonEmpty && _activeImports.contains(canonicalUrl)) {
        // Cycle — skip silently.
      } else {
        if (canonicalUrl.nonEmpty) _activeImports += canonicalUrl
        try {
          val children = importedSheet.children.get
          val hasUseOrForward = children.exists {
            case _: ssg.sass.ast.sass.UseRule     => true
            case _: ssg.sass.ast.sass.ForwardRule => true
            case _                                => false
          }
          // @import always re-evaluates the imported file's CSS, even
          // when the same module was already @use'd. Save and clear
          // the injected-CSS set so modules @use'd within this import
          // can re-inject their CSS into the output.
          val savedInjected = _injectedModuleCss.clone()
          _injectedModuleCss.clear()
          try {
          if (!hasUseOrForward) {
            // Simple inline path: no module-system directives, so the
            // imported file can run against the caller's environment
            // without any `forImport`/`importForwards` plumbing.
            children.foreach { stmt =>
              val _ = stmt.accept(this)
            }
          } else {
            // dart-sass `forImport` path: evaluate in an isolated
            // `_modules`/`_globalModules`/`_forwardedModules` shell that
            // still shares the variable / function / mixin scope chain
            // with the caller, then `importForwards` the resulting
            // dummy module so the caller's lookup chain can reach
            // anything the imported file `@forward`ed.
            val forImportEnv = _environment.forImport()
            _withEnvironment(forImportEnv) {
              children.foreach { stmt =>
                val _ = stmt.accept(this)
              }
            }
            val dummyModule = forImportEnv.toDummyModule()
            _environment.importForwards(dummyModule)
          }
          } finally {
            // Restore the previous injected set, augmented with any
            // modules newly injected during this import, so that
            // subsequent @use's at the same level still deduplicate.
            _injectedModuleCss ++= savedInjected
          }
        } finally {
          if (canonicalUrl.nonEmpty) {
            val _ = _activeImports.remove(canonicalUrl)
          }
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
    * unresolved-extend tracking are the parts that make `!optional` work.
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
          val selectorList: SelectorList = box.value
          if (hasAst) {
            // Mark any unresolved extends whose target simple selector appears
            // in this rule's selector list as "found" in the current scope.
            for (pending <- _pendingExtends)
              if (!pending.found && pending.mediaKey == mediaKey && pending.target.isDefined) {
                val tgt = pending.target.get
                val hit = selectorList.components.exists { complex =>
                  complex.components.exists(_.selector.components.contains(tgt))
                }
                if (hit) pending.found = true
              }
            val extended = astStore.get.extendList(selectorList)
            val filtered = extended.components.filterNot(ExtendUtils.isPlaceholderOnly)
            if (filtered.isEmpty) {
              rule.remove()
              removed = true
            } else if (filtered.length != extended.components.length) {
              val newList = new ssg.sass.ast.selector.SelectorList(filtered, extended.span)
              box.value = newList
            } else {
              box.value = extended
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
            val augmentedText = augmented.distinct.mkString(", ")
            SelectorParser.tryParse(augmentedText).foreach { sl =>
              box.value = sl
            }
          } else {
            // No extensions, but still strip rules containing only `%`-prefixed
            // selectors so bare `%foo { ... }` never leaks into CSS output.
            val filtered = selectorList.components.filterNot(ExtendUtils.isPlaceholderOnly)
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
        val space    = if (sd.isCustomProperty) "" else " "
        val valueStr = _evaluateToCss(sd.value, quote = false)
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
    val block: Nullable[ContentBlock] = _environment.content
    block.foreach { cb =>
      // Evaluate `@content(arg1, arg2, ...)` arguments in the current
      // (mixin) environment, then bind them to the content block's
      // declared parameters (`@include foo using ($p1, $p2)`) before
      // running the block body in a fresh scope.
      //
      // Crucially, while running the block we must NOT see the same content
      // pointer that brought us here: otherwise a nested `@include` whose
      // own body contains `@content` would recurse into itself forever.
      // We clear `_environment.content` for the duration of the block
      // evaluation (dart-sass keeps a per-block closure environment, but
      // clearing is enough to break the recursion — the correct forwarding
      // to the caller's content is a separate feature tracked elsewhere).
      val (positional, named) = _evaluateArguments(node.arguments)
      val savedContent        = _environment.content
      _environment.content = Nullable.empty
      try
        _withScope {
          _bindParameters(cb.parameters, positional, named)
          for (statement <- cb.childrenList) {
            val _ = statement.accept(this)
          }
        }
      finally
        _environment.content = savedContent
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
    val (positional, named) = _evaluateArguments(node.arguments)
    // dart-sass: set _callableNode so that built-in mixin callbacks
    // (e.g. load-css, apply) can read the call-site node for span
    // context and configuration construction.
    val oldCallableNode = _callableNode
    _callableNode = Nullable(node: ssg.sass.ast.AstNode)
    try
      _invokeMixinCallable(
        mixin,
        positional,
        named,
        node.content.asInstanceOf[Nullable[ContentBlock]]
      )
    catch {
      // Built-in mixins (e.g. `meta.apply`) may raise bare
      // SassScriptExceptions from inside their callback; attach the include
      // site so they surface as proper SassExceptions with source location.
      case e: SassScriptException => throw e.withSpan(node.span)
    } finally {
      _callableNode = oldCallableNode
    }
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
    ssg.sass.AliasedCallable.unwrap(callable) match {
      case ud: UserDefinedCallable[?] =>
        ud.declaration match {
          case mr: MixinRule =>
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
                _bindParameters(mr.parameters, positional, named)
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
              case _                => runBody()
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
        val padded = _padBuiltInPositional(bic, merged)
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
    named:      ListMap[String, Value]
  ): Value =
    // dart-sass _runUserDefinedCallable: push a scope (semiGlobal=false), bind
    // parameters as locals, run the body. The scope() push is what redirects
    // `$var: x` writes from the captured env's global map to a private local
    // map so concurrent invocations and the surrounding global state are
    // shielded from the body's mutations.
    _environment.scope() {
      _bindParameters(fr.parameters, positional, named)
      try {
        for (statement <- fr.childrenList) {
          val _ = statement.accept(this)
        }
        // Falling off the end of a function body with no @return is an error
        // in Sass; return null currently (matches "null" result of no-op).
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
      // dart-sass: parameter values (positional, named, or default) are
      // stripped of slash info before binding.
      _environment.setVariable(param.name, value.withoutSlash)
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
    *
    * Also fills intermediate gaps created by [[_mergeBuiltInNamedArgs]]: when a named arg is placed
    * at index `j > positional.length`, positions between `positional.length` and `j` are filled
    * with SassNull. This method replaces those intermediate SassNull values with the evaluated
    * default if one exists in the signature.
    */
  private def _padBuiltInPositional(bic: BuiltInCallable, positional: List[Value]): List[Value] = {
    val defaults = bic.parameterDefaults
    if (defaults.isEmpty) positional
    else {
      // Phase 1: fill intermediate SassNull gaps (created by _mergeBuiltInNamedArgs)
      // with evaluated defaults from the signature.
      val buf = scala.collection.mutable.ListBuffer.from(positional)
      var i = 0
      while (i < buf.length && i < defaults.length) {
        if ((buf(i) eq SassNull) && defaults(i).isDefined) {
          try {
            val (expr, _) = new ssg.sass.parse.ScssParser(defaults(i).get).parseExpression()
            buf(i) = expr.accept(this)
          } catch {
            case _: Throwable => // leave as SassNull on parse/eval failure
          }
        }
        i += 1
      }
      // Phase 2: append trailing defaults beyond positional.length.
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

  /** Validates positional and named argument arity for a [[BuiltInCallable]]
    * call. Port of dart-sass `ArgumentList.verify` checks for the built-in
    * dispatch path (`_runBuiltInCallable` in serialize.dart):
    *
    *   1. Named args must resolve to a declared parameter name. When the
    *      callable has no rest parameter, every name must be in
    *      `parameterNames`. Otherwise a `No parameter named $foo` error is
    *      raised (mirroring dart-sass's error text).
    *   2. Positional count must fit the declared parameter slots unless
    *      the signature has a rest parameter. An overflow raises
    *      `Only N argument(s) allowed, but M were passed.`
    *   3. A name that collides with a positional arg (e.g. the 1st
    *      positional is `$red` and the caller also passes `$red: 10`)
    *      raises `Argument $foo was passed both by position and by name.`
    *
    * Callables whose signature is empty (rest-only forms like
    * `"$args..."`) skip arity checks entirely. Callables declared via
    * `BuiltInCallable.overloadedFunction` are also skipped — their
    * dispatcher picks the right overload before the callback runs, so
    * any per-overload arity enforcement happens inside the callback.
    */
  private def _checkBuiltInArity(
    bic:        BuiltInCallable,
    positional: List[Value],
    named:      ListMap[String, Value]
  ): Unit = {
    // Skip overloaded callables entirely — the dispatcher picks the
    // right overload at runtime and handles arity inside the callback.
    if (bic.isOverloaded) return
    val declared = bic.parameterNames
    if (declared.isEmpty) return // rest-only or unknown signature — skip
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
      val namesSet = names.toSet
      val (knownNamed, leftoverNamed) = named.partition { case (k, _) => namesSet.contains(k) }
      if (leftoverNamed.nonEmpty && knownNamed.isEmpty) {
        // None of the named args match the canonical signature — the call
        // is meant for a different overload. Package everything into a
        // SassArgumentList so the single-arg overload (e.g. $channels)
        // receives the named-arg value correctly.
        val al = new ssg.sass.value.SassArgumentList(
          positional,
          named,
          ssg.sass.value.ListSeparator.Comma
        )
        return List(al)
      }
      // Some match canonical, some don't — merge canonical names into
      // positional and wrap the rest into an argument list.
      if (leftoverNamed.nonEmpty) {
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
              case _       => buf += SassNull
            }
          i += 1
        }
        // Wrap leftover into argument list for rest consumption
        val al = new ssg.sass.value.SassArgumentList(
          Nil,
          leftoverNamed,
          ssg.sass.value.ListSeparator.Comma
        )
        buf += al
        return buf.toList
      }
      // All named match canonical — fall through to normal merge
    }
    if (names.isEmpty) {
      // rest-only signature (`$args...`): wrap positional + all kwargs
      // into a SassArgumentList if there are keyword args.
      if (named.nonEmpty || bic.hasRestParameter) {
        val extras = positional.drop(0) // all positional become rest
        val al = new ssg.sass.value.SassArgumentList(
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
            case _       => buf += SassNull
          }
        i += 1
      }
      // When rest parameter is declared, collect extra positional and
      // leftover named args into a SassArgumentList.
      if (bic.hasRestParameter) {
        val extras = positional.drop(names.length)
        val al = new ssg.sass.value.SassArgumentList(
          extras,
          leftoverNamed,
          ssg.sass.value.ListSeparator.Comma
        )
        buf += al
      }
      buf.toList
    }
  }

  /** Evaluates the positional and named expressions in [[args]] against the current environment, returning a `(positional, named)` pair. Rest and keyword-rest arguments are expanded below.
    */
  private def _evaluateArguments(
    args: ssg.sass.ast.sass.ArgumentList
  ): (List[Value], ListMap[String, Value]) = {
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
    args.rest.foreach { restExpr =>
      restExpr.accept(this) match {
        case al: ssg.sass.value.SassArgumentList =>
          for (v <- al.asList) positionalBuf += v.withoutSlash
          for ((k, v) <- al.keywords)
            named = named.updated(k, v.withoutSlash)
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
    (positionalBuf.toList, named)
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

    val wasInKeyframes = _inKeyframes
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
        for (child <- node.children) {
          child.accept(this)
        }
      }
      _inUnknownAtRule = wasInUnknownAtRule
      _inKeyframes = wasInKeyframes
      break(SassNull)
    }

    _addChild(rule, through = (n: CssNode) => n.isInstanceOf[CssStyleRule])
    val saved = _parent
    _parent = Nullable(rule: ModifiableCssParentNode)
    try {
      // We don't have to check for an unknown at-rule in a style rule here,
      // because the previous compilation has already bubbled the at-rule to the
      // root.
      for (child <- node.children) {
        child.accept(this)
      }
    } finally {
      _parent = saved
    }

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
    try {
      for (child <- node.children) {
        child.accept(this)
      }
    } finally {
      _parent = saved
    }
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
        for (child <- node.children) {
          child.accept(this)
        }
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
    try {
      _withMediaQueries(effectiveQueries, mergedSources,
        _styleRule match {
          case sr if sr.isDefined =>
            // If we're in a style rule, copy it into the media query so that
            // declarations immediately inside @media have somewhere to go.
            //
            // For example, "a {@media screen {b: c}}" should produce
            // "@media screen {a {b: c}}".
            val styleRule = sr.get
            _withParent(styleRule.copyWithoutChildren()) {
              for (child <- node.children) {
                child.accept(this)
              }
            }
          case _ =>
            for (child <- node.children) {
              child.accept(this)
            }
        }
      )
    } finally {
      _parent = saved
    }
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
      case sr if sr.isEmpty => true
      case sr if sr.get.fromPlainCss => false
      case _ => !(node.fromPlainCss && SelectorList.containsParentSelector(node.selector))
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
    _addChild(rule, through = if (merge) (n: CssNode) => n.isInstanceOf[CssStyleRule] else null)
    val saved = _parent
    _parent = Nullable(rule: ModifiableCssParentNode)
    try {
      _withStyleRule(rule) {
        for (child <- node.children) {
          child.accept(this)
        }
      }
    } finally {
      _parent = saved
    }
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
    for (statement <- node.children) {
      statement.accept(this)
    }
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
        for (child <- node.children) {
          child.accept(this)
        }
      }
      break(SassNull)
    }

    _addChild(rule, through = (n: CssNode) => n.isInstanceOf[CssStyleRule])
    val saved = _parent
    _parent = Nullable(rule: ModifiableCssParentNode)
    try {
      _styleRule match {
        case sr if sr.isDefined =>
          // If we're in a style rule, copy it into the supports rule so that
          // declarations immediately inside @supports have somewhere to go.
          //
          // For example, "a {@supports (a: b) {b: c}}" should produce "@supports
          // (a: b) {a {b: c}}".
          val styleRule = sr.get
          _withParent(styleRule.copyWithoutChildren()) {
            for (child <- node.children) {
              child.accept(this)
            }
          }
        case _ =>
          for (child <- node.children) {
            child.accept(this)
          }
      }
    } finally {
      _parent = saved
    }
    SassNull
    } // boundary
  }
}
