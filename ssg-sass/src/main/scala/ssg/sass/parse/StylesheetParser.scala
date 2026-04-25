/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/parse/stylesheet.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: stylesheet.dart -> StylesheetParser.scala
 *   Idiom: Minimum viable implementation — parses basic SCSS:
 *     - Top-level style rules with declarations
 *     - Variable declarations
 *     - Simple expressions (numbers, strings, identifiers, variables)
 *     - Comments
 *   Full support for @use/@forward/@media/@if/@for/@each/@function/@mixin
 *   is deferred to a later pass. At-rules that aren't recognized fall back
 *   to a generic AtRule parse.
 */
package ssg
package sass
package parse

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.ast.sass.{
  ArgumentList,
  AtRootRule,
  AtRule,
  BinaryOperationExpression,
  BinaryOperator,
  BooleanExpression,
  BooleanOperator,
  ColorExpression,
  ConfiguredVariable,
  ContentBlock,
  ContentRule,
  DebugRule,
  Declaration,
  DynamicImport,
  EachRule,
  ElseClause,
  ErrorRule,
  Expression,
  ExtendRule,
  ForRule,
  ForwardRule,
  FunctionExpression,
  FunctionRule,
  IfClause,
  IfConditionExpression,
  IfConditionFunction,
  IfConditionNegation,
  IfConditionOperation,
  IfConditionParenthesized,
  IfConditionRaw,
  IfConditionSass,
  IfExpression,
  IfRule,
  Import,
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
  Parameter,
  ParameterList,
  ParenthesizedExpression,
  ParseTimeWarning,
  ReturnRule,
  SelectorExpression,
  SilentComment,
  Statement,
  StaticImport,
  StringExpression,
  StyleRule,
  Stylesheet,
  SupportsAnything,
  SupportsCondition,
  SupportsDeclaration,
  SupportsFunction,
  SupportsInterpolation,
  SupportsNegation,
  SupportsOperation,
  SupportsExpression,
  SupportsRule,
  UnaryOperationExpression,
  UnaryOperator,
  UseRule,
  VariableDeclaration,
  VariableExpression,
  WarnRule,
  WhileRule
}
import ssg.sass.value.ListSeparator
import ssg.sass.util.{ CharCode, FileSpan }
import ssg.sass.value.SassNumber
import ssg.sass.value.SassColor
import ssg.sass.ColorNames

import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

/** The base class for both the SCSS and indented syntax parsers. */
abstract class StylesheetParser protected (
  contents:                     String,
  url:                          Nullable[String] = Nullable.Null,
  protected val parseSelectors: Boolean = false
) extends Parser(contents, url) {

  /** Warnings discovered while parsing. */
  protected val warnings: mutable.ListBuffer[ParseTimeWarning] = mutable.ListBuffer.empty

  /** Whether we've consumed a rule other than `@charset`, `@forward`, or `@use`. dart-sass: `_isUseAllowed` (line 45).
    */
  private var _isUseAllowed: Boolean = true

  /** Whether the parser is currently parsing the contents of a mixin declaration. dart-sass: `_inMixin` (line 49).
    */
  private var _inMixin: Boolean = false

  /** Whether the parser is currently parsing a content block passed to a mixin. dart-sass: `_inContentBlock` (line 52).
    */
  private var _inContentBlock: Boolean = false

  /** Whether the parser is currently parsing a control directive such as `@if` or `@each`. dart-sass: `_inControlDirective` (line 56).
    */
  private var _inControlDirective: Boolean = false

  /** Whether the parser is currently parsing an unknown rule. dart-sass: `_inUnknownAtRule` (line 59).
    */
  private var _inUnknownAtRule: Boolean = false

  /** Whether the parser is currently parsing a plain-CSS `@function` rule. dart-sass: `_inPlainCssFunction` (line 62).
    */
  private var _inPlainCssFunction: Boolean = false

  /** Whether the parser is currently parsing a style rule. dart-sass: `_inStyleRule` (line 65).
    */
  private var _inStyleRule: Boolean = false

  /** Whether the parser is currently within a parenthesized expression. dart-sass: `_inParentheses` (line 68).
    */
  private var _inParentheses: Boolean = false

  /** Whether the parser is currently within an expression. dart-sass: `_inExpression` (line 73).
    */
  private var _inExpression: Boolean = false

  /** dart-sass: `inExpression` getter (line 72). */
  protected def inExpression: Boolean = _inExpression

  /** A map from all variable names that are assigned with `!global` in the current stylesheet to the spans where they're defined.
    *
    * dart-sass: `_globalVariables` (line 82).
    */
  private val _globalVariables: mutable.Map[String, FileSpan] = mutable.Map.empty

  /** The silent comment this parser encountered previously. dart-sass: `lastSilentComment` (line 91).
    */
  protected var lastSilentComment: Nullable[SilentComment] = Nullable.Null

  /** Record a parse-time deprecation warning for later forwarding to the evaluator / caller. */
  protected def warnDeprecation(deprecation: Deprecation, message: String, span: FileSpan): Unit =
    warnings += ParseTimeWarning(Nullable(deprecation), span, message)

  /** Whether this parser emits plain CSS. Overridden by [[CssParser]]. */
  def plainCss: Boolean = false

  /** Whether this parser is the indented syntax. Overridden by [[SassParser]]. */
  def indented: Boolean

  /** The current indentation level. */
  def currentIndentation: Int

  // ---------------------------------------------------------------------------
  // Public entry points
  // ---------------------------------------------------------------------------

  /** Parses the contents as a full stylesheet. */
  def parse(): Stylesheet = wrapSpanFormatException { () =>
    val start = scanner.state
    // Skip BOM
    if (scanner.peekChar() == 0xfeff) scanner.readChar()

    val stmts = statements(() => _topLevelStatement())
    scanner.expectDone()

    val span = spanFrom(start)
    new Stylesheet(stmts, span, plainCss, warnings.toList, _globalVariables.toMap)
  }

  /** Parses a top-level statement (at statement or style rule).
    *
    * dart-sass: `_statement` (stylesheet.dart:196-224).
    */
  private def _topLevelStatement(): Nullable[Statement] = {
    val c = scanner.peekChar()
    if (c == CharCode.$at) _atRule(root = true)
    else if (c == CharCode.$dollar) Nullable(_variableDeclaration())
    else if (c == CharCode.$slash && (scanner.peekChar(1) == CharCode.$slash || scanner.peekChar(1) == CharCode.$asterisk)) {
      if (scanner.peekChar(1) == CharCode.$slash) _silentComment()
      else _loudComment()
    } else if (c == CharCode.$plus && indented && lookingAtIdentifier(1)) {
      // Indented syntax shorthand: `+include-name` => `@include include-name`
      _isUseAllowed = false
      val start = scanner.state
      scanner.readChar()
      Nullable(_includeRule(start))
    } else if (c == CharCode.$equal && indented) {
      // Indented syntax shorthand: `=mixin-name` => `@mixin mixin-name`
      _isUseAllowed = false
      val start = scanner.state
      scanner.readChar()
      whitespace(consumeNewlines = true)
      Nullable(_mixinRule(start))
    } else {
      // Style rule (or namespaced variable declaration at top level).
      // dart-sass dispatches to _variableDeclarationOrStyleRule() for top-level
      // statements that could be `namespace.$var: value;`.
      _variableDeclarationOrStyleRule()
    }
  }

  /** Parses a top-level @-rule. Currently only handles @use as a recognized form.
    *
    * dart-sass: `atRule` (stylesheet.dart:669-733).
    */
  private def _atRule(root: Boolean = false): Nullable[Statement] = {
    val start = scanner.state
    scanner.expectChar(CharCode.$at)
    // dart-sass uses interpolatedIdentifier() so `@#{$var}-rule` is valid.
    val nameInterp = interpolatedIdentifier()
    val namePlain  = nameInterp.asPlain
    if (namePlain.isEmpty) {
      // If the at-rule name contains interpolation, it's an unknown at-rule.
      whitespace(consumeNewlines = true)
      return Nullable(_unknownAtRule(start, nameInterp))
    }
    val name = namePlain.get
    // dart-sass: individual at-rule handlers consume their own whitespace.
    // We must NOT consume newlines here because the indented syntax parser
    // relies on newlines being present for _peekIndentation to work correctly.
    // Each case branch below is responsible for calling whitespace() as needed.

    // We want to set _isUseAllowed to `false` *unless* we're parsing
    // `@charset`, `@forward`, or `@use`. To avoid double-comparing the rule
    // name, we always set it to `false` and then set it back to its previous
    // value if we're parsing an allowed rule.
    val wasUseAllowed = _isUseAllowed
    _isUseAllowed = false

    name match {
      case "charset" =>
        // dart-sass: @charset is silently consumed and not emitted to the CSS.
        // The charset is used only by the parser for encoding detection;
        // it does not produce any AST node.
        _isUseAllowed = wasUseAllowed
        if (!root) _disallowedAtRule(start)
        // dart-sass parse.dart line 107: uses `consumeNewlines: false` so
        // that in the indented syntax, a newline after `@charset` causes
        // `string()` to error with "Expected string." — the string argument
        // must be on the same line as the directive. The `string()` call is
        // unconditional: dart-sass always expects a string argument.
        whitespace(consumeNewlines = false)
        // dart-sass: `string()` — consume a plain CSS string (quoted).
        // If the next char is not a quote, error with "Expected string."
        val charsetPeek = scanner.peekChar()
        if (charsetPeek != CharCode.$single_quote && charsetPeek != CharCode.$double_quote) {
          scanner.error("Expected string.")
        }
        interpolatedString()
        expectStatementSeparator(Nullable("@charset rule"))
        Nullable.Null
      case "use" =>
        _isUseAllowed = wasUseAllowed
        // dart-sass: @use is only allowed at the root of the stylesheet.
        if (!root) _disallowedAtRule(start)
        Nullable(_useRule(start))
      case "forward" =>
        _isUseAllowed = wasUseAllowed
        // dart-sass: @forward is only allowed at the root of the stylesheet.
        if (!root) _disallowedAtRule(start)
        Nullable(_forwardRule(start))
      case "import" =>
        Nullable(_importRule(start))
      case "extend" =>
        // @extend <selector> [!optional] ;
        // dart-sass: `_extendRule` (stylesheet.dart:929-946).
        whitespace(consumeNewlines = true)
        if (!_inStyleRule && !_inMixin && !_inContentBlock) {
          error("@extend may only be used within style rules.", spanFrom(start))
        }
        val value      = almostAnyValue()
        val isOptional = scanner.scanChar(CharCode.$exclamation)
        if (isOptional) {
          expectIdentifier("optional")
          whitespace(consumeNewlines = false)
        }
        expectStatementSeparator(Nullable("@extend rule"))
        Nullable(new ExtendRule(value, spanFrom(start), isOptional))
      case "mixin" =>
        // @mixin name [(params)] { body }
        // dart-sass: `_mixinRule` (stylesheet.dart:1455-1502).
        whitespace(consumeNewlines = true)
        val precedingMixComment = lastSilentComment
        lastSilentComment = Nullable.Null
        val beforeMixName = scanner.state
        val mixinName     = identifier()
        // Reject @mixin names starting with `--`
        if (mixinName.startsWith("--")) {
          error(
            "Sass @mixin names beginning with -- are forbidden for forward-" +
              "compatibility with plain CSS mixins.\n\n" +
              "For details, see https://sass-lang.com/d/css-function-mixin",
            spanFrom(beforeMixName)
          )
        }
        whitespace(consumeNewlines = false)
        val params = if (scanner.peekChar() == CharCode.$lparen) {
          _parseParameterList(start)
        } else {
          ParameterList.empty(scanner.emptySpan)
        }
        // Context checks
        if (_inMixin || _inContentBlock) {
          error("Mixins may not contain mixin declarations.", spanFrom(start))
        } else if (_inControlDirective) {
          error("Mixins may not be declared in control directives.", spanFrom(start))
        }
        whitespace(consumeNewlines = false)
        _inMixin = true
        val kids = _children()
        _inMixin = false
        Nullable(new MixinRule(mixinName, params, kids, spanFrom(start), precedingMixComment))
      case "function" =>
        // @function name(params) { body }
        // dart-sass: `_functionRule` (stylesheet.dart:951-1011).
        whitespace(consumeNewlines = true)
        val precedingFnComment = lastSilentComment
        lastSilentComment = Nullable.Null
        val beforeFnName = scanner.state
        // dart-sass: if name starts with `--`, fall through to unknownAtRule.
        if (scanner.matches("--")) {
          Nullable(_unknownAtRule(start, nameInterp))
        } else {
          val fnName     = identifier()
          val fnNameSpan = spanFrom(beforeFnName)
          // `type` is reserved for a plain-CSS function, case-insensitive.
          if (ssg.sass.Utils.equalsIgnoreCase(Nullable(fnName), Nullable("type"))) {
            error("This name is reserved for the plain-CSS function.", fnNameSpan)
          }
          // Case-sensitive (lowercase-only) hard errors: `expression`, `url`,
          // `and`, `or`, `not`, plus anything whose unvendored form is `element`.
          val fnUnvendor = ssg.sass.Utils.unvendor(fnName)
          if (
            fnName == "expression" || fnName == "url" || fnName == "and" ||
            fnName == "or" || fnName == "not" || fnUnvendor == "element"
          ) {
            error("Invalid function name.", fnNameSpan)
          } else if (
            fnName.toLowerCase == "expression" || fnName.toLowerCase == "url" ||
            ssg.sass.Utils.unvendor(fnName.toLowerCase) == "element"
          ) {
            // Case-insensitive deprecation warning
            warnDeprecation(
              Deprecation.FunctionName,
              "Custom functions with this name are deprecated and will be removed in a future\n" +
                "release. Please choose a different name.\n" +
                "More info: https://sass-lang.com/d/function-name",
              fnNameSpan
            )
          }
          whitespace(consumeNewlines = true)
          val params = _parseParameterList(start)
          // Context checks
          if (_inMixin || _inContentBlock) {
            error("Mixins may not contain function declarations.", spanFrom(start))
          } else if (_inControlDirective) {
            error("Functions may not be declared in control directives.", spanFrom(start))
          }
          whitespace(consumeNewlines = false)
          // dart-sass: `_withChildren(_functionChild, ...)` — restricts the body
          // to variable declarations and control flow only.
          val kids = _functionChildren()
          Nullable(new FunctionRule(fnName, params, kids, spanFrom(start), precedingFnComment))
        }
      case "return" =>
        // @return <expression> ;
        // dart-sass: `_returnRule` (stylesheet.dart:833-842). In dart-sass,
        // @return in `atRule` dispatch is `_disallowedAtRule`, but here we
        // keep the handler because Scala children always go through _atRule.
        whitespace(consumeNewlines = true)
        val retExpr = _rdExpression()
        val retEnd  = scanner.state
        expectStatementSeparator(Nullable("@return rule"))
        Nullable(new ReturnRule(retExpr, spanFrom(start, retEnd)))
      case "else" =>
        // @else is never valid in the general at-rule dispatch — only
        // consumed as part of @if chain. Matches dart-sass line 694.
        _disallowedAtRule(start)
      case "content" =>
        // @content [(args)] ;
        // dart-sass: `_contentRule` (stylesheet.dart:854-874).
        if (!_inMixin) {
          error("@content is only allowed within mixin declarations.", spanFrom(start))
        }
        val beforeContentWs = scanner.state
        whitespace(consumeNewlines = false)
        val cArgs =
          if (scanner.peekChar() == CharCode.$lparen) {
            val r = _rdArgumentInvocation(start, mixin = true)
            // dart-sass: whitespace(consumeNewlines: false) after args,
            // consuming any trailing comments before the statement separator.
            whitespace(consumeNewlines = false)
            r
          } else {
            ArgumentList.empty(spanFrom(beforeContentWs, beforeContentWs))
          }
        expectStatementSeparator(Nullable("@content rule"))
        Nullable(new ContentRule(cArgs, spanFrom(start)))
      case "include" =>
        // dart-sass: delegates to `_includeRule` (stylesheet.dart:711-712).
        Nullable(_includeRule(start))
      case "media" =>
        // @media <query> { body }
        // Ported from dart-sass stylesheet.dart _mediaRule (line 1389-1403).
        // Uses the structured _mediaQueryList parser instead of ad-hoc
        // StringBuilder scanning.
        val queryInterp = _mediaQueryList()
        whitespace(consumeNewlines = true)
        val kids = _children()
        Nullable(new MediaRule(queryInterp, kids, spanFrom(start)))
      case "supports" =>
        // @supports <condition> { body }
        // Ported from dart-sass stylesheet.dart _supportsRule (line 1600-1614).
        // dart-sass uses consumeNewlines: false so indented syntax preserves
        // the trailing newline for _peekIndentation.
        whitespace(consumeNewlines = false)
        val condition = _supportsCondition()
        whitespace(consumeNewlines = false)
        val supportsKids = _children()
        Nullable(new SupportsRule(condition, supportsKids, spanFrom(start)))
      case "each" =>
        // @each $var[, $var, ...] in <expression> { body }
        // Delegates to _eachRule for faithful expression / children handling
        // in both SCSS and indented syntax.
        Nullable(_eachRule(start, _childStatementAsChild))
      case "for" =>
        // @for $var from <expr> (to|through) <expr> { body }
        // Delegates to _forRule for faithful expression / children handling
        // in both SCSS and indented syntax.
        Nullable(_forRule(start, _childStatementAsChild))
      case "debug" =>
        Nullable(_debugRule(start))
      case "warn" =>
        Nullable(_warnRule(start))
      case "error" =>
        Nullable(_errorRule(start))
      case "while" =>
        // @while <expression> { body }
        // Delegates to _whileRule for faithful expression / children handling
        // in both SCSS and indented syntax.
        Nullable(_whileRule(start, _childStatementAsChild))

      case "if" =>
        // @if <expression> { body } [@else if <expression> { body }]* [@else { body }]
        // Delegates to _ifRule for faithful expression / children handling
        // in both SCSS and indented syntax.
        Nullable(_ifRule(start, _childStatementAsChild))
      case "at-root" =>
        // dart-sass stylesheet.dart:807-826 (_atRootRule).
        // Three forms:
        //   1. `@at-root (with/without: ...) { body }` — query form
        //   2. `@at-root { body }` — bare form (no query, no selector)
        //   3. `@at-root <selector> { body }` — selector form
        whitespace(consumeNewlines = false)
        if (scanner.peekChar() == CharCode.$lparen) {
          // Query form: parse `(keyword: names ...)` using expressions
          // so that quoted values like `"media"` are handled correctly.
          val query = _atRootQuery(start)
          val kids  = _children()
          Nullable(new AtRootRule(kids, spanFrom(start), Nullable(query)))
        } else if (lookingAtChildren() || (indented && atEndOfStatement())) {
          // Bare @at-root (no query, no selector)
          val kids = _children()
          Nullable(new AtRootRule(kids, spanFrom(start), Nullable.empty[Interpolation]))
        } else {
          // Selector form: parse a style rule as the single child
          val child = _styleRule()
          Nullable(new AtRootRule(List[Statement](child), spanFrom(start), Nullable.empty[Interpolation]))
        }
      case "-moz-document" =>
        // @-moz-document <function-call> [, <function-call>]* { body }
        // dart-sass stylesheet.dart:1504-1588 (mozDocumentRule).
        // Gecko's @-moz-document allows url-prefix and domain to omit quotes.
        // The key difference from unknownAtRule is that whitespace() is called
        // between function arguments, which strips comments.
        Nullable(_mozDocumentRule(start, nameInterp))
      case _ =>
        // Deprecation detection for at-rules we don't specially handle.
        name match {
          case "elseif" =>
            warnDeprecation(
              Deprecation.Elseif,
              "@elseif is deprecated and will not be supported in future Sass versions. Recommendation: @else if.",
              spanFrom(start)
            )
          case _ => ()
        }
        // dart-sass: `default: return unknownAtRule(start, name);`
        Nullable(_unknownAtRule(start, nameInterp))
    }
  }

  /** Consumes a parameter list.
    *
    * dart-sass: `_parameterList` (stylesheet.dart:1804-1849).
    * Uses `expressionUntilComma()` for default values (recursive-descent),
    * replacing the old text-based `_parseSimpleExpression` pipeline.
    */
  private def _parseParameterList(startState: ssg.sass.util.LineScannerState): ParameterList = {
    val start = scanner.state
    scanner.expectChar(CharCode.$lparen)
    whitespace(consumeNewlines = true)
    val parameters = scala.collection.mutable.ListBuffer.empty[Parameter]
    val named      = scala.collection.mutable.LinkedHashSet.empty[String]
    var restParameter: Nullable[String] = Nullable.empty
    boundary {
      while (scanner.peekChar() == CharCode.$dollar) {
        val variableStart = scanner.state
        val name          = variableName()
        whitespace(consumeNewlines = true)

        var defaultValue: Nullable[Expression] = Nullable.empty
        if (scanner.scanChar(CharCode.$colon)) {
          whitespace(consumeNewlines = true)
          defaultValue = Nullable(expressionUntilComma())
        } else if (scanner.scanChar(CharCode.$dot)) {
          scanner.expectChar(CharCode.$dot)
          scanner.expectChar(CharCode.$dot)
          whitespace(consumeNewlines = true)
          if (scanner.scanChar(CharCode.$comma)) whitespace(consumeNewlines = true)
          restParameter = Nullable(name)
          break(())
        }

        parameters += new Parameter(name, spanFrom(variableStart), defaultValue)
        if (!named.add(name)) {
          error("Duplicate parameter.", parameters.last.span)
        }

        if (!scanner.scanChar(CharCode.$comma)) break(())
        whitespace(consumeNewlines = true)
      }
    }
    scanner.expectChar(CharCode.$rparen)
    new ParameterList(parameters.toList, spanFrom(start), restParameter)
  }

  /** Parses a variable declaration: `$name: value;` */
  /** Parses a `@use` rule body (after scanning `@use`).
    *
    * dart-sass: `_useRule` (stylesheet.dart:342-412).
    */
  private def _useRule(start: ssg.sass.util.LineScannerState): UseRule = {
    whitespace(consumeNewlines = true)
    val url = if (scanner.peekChar() == CharCode.$double_quote || scanner.peekChar() == CharCode.$single_quote) {
      string()
    } else {
      scanner.error("Expected string URL.")
    }
    // dart-sass line 1621: consumeNewlines: false after URL.
    whitespace(consumeNewlines = false)
    val namespace: Nullable[String] =
      if (scanIdentifier("as")) {
        whitespace(consumeNewlines = true)
        if (scanner.scanChar(CharCode.$asterisk)) {
          Nullable.empty[String] // flat: no namespace
        } else {
          Nullable(identifier())
        }
      } else {
        // Default namespace: for `sass:` URLs, use the module name
        // after the colon (e.g. `sass:list` -> `list`). For file URLs,
        // use the last path segment without extension/underscore.
        if (url.startsWith("sass:")) {
          val moduleName = url.substring("sass:".length)
          if (moduleName.isEmpty) Nullable.empty[String]
          else Nullable(moduleName)
        } else {
          val lastSeg = {
            val segs = url.split('/')
            if (segs.isEmpty) url else segs(segs.length - 1)
          }
          // dart-sass: strip leading `_` and everything from the first `.`
          // onwards. This removes ALL extensions (e.g., `other.foo.bar.scss`
          // -> `other`), matching dart-sass behavior.
          val withoutUnderscore = if (lastSeg.startsWith("_")) lastSeg.substring(1) else lastSeg
          val dot = withoutUnderscore.indexOf('.')
          val stripped = if (dot >= 0) withoutUnderscore.substring(0, dot) else withoutUnderscore
          if (stripped.isEmpty) Nullable.empty[String]
          else Nullable(stripped)
        }
      }
    // dart-sass line 1624: consumeNewlines: false after namespace.
    whitespace(consumeNewlines = false)
    // Optional `with ($name: expr [!default], ...)`.
    val configBuf = mutable.ListBuffer.empty[ConfiguredVariable]
    if (scanIdentifier("with")) {
      whitespace(consumeNewlines = true)
      scanner.expectChar(CharCode.$lparen)
      whitespace(consumeNewlines = true)
      var more = true
      while (more) {
        whitespace(consumeNewlines = true)
        val cvStart = scanner.state
        val varName = variableName()
        whitespace(consumeNewlines = true)
        scanner.expectChar(CharCode.$colon)
        whitespace(consumeNewlines = true)
        val expr = _rdExpression(
          stopAtComma = true,
          consumeNewlines = true,
          until = () => {
            val ch = scanner.peekChar()
            ch == CharCode.$rparen || ch == CharCode.$exclamation
          }
        )
        whitespace(consumeNewlines = true)
        var guarded = false
        if (scanner.scanChar(CharCode.$exclamation)) {
          val flag = identifier()
          if (flag == "default") guarded = true
          else scanner.error(s"Unknown flag !$flag.")
          whitespace(consumeNewlines = true)
        }
        configBuf += ConfiguredVariable(varName, expr, spanFrom(cvStart), guarded)
        whitespace(consumeNewlines = true)
        if (scanner.scanChar(CharCode.$comma)) {
          whitespace(consumeNewlines = true)
          // Allow trailing comma before `)`.
          if (scanner.peekChar() == CharCode.$rparen) more = false
          else more = true
        } else more = false
      }
      whitespace(consumeNewlines = true)
      scanner.expectChar(CharCode.$rparen)
    }
    whitespace(consumeNewlines = false)
    val span = spanFrom(start)
    // dart-sass: @use rules must be written before any other rules.
    if (!_isUseAllowed) {
      error("@use rules must be written before any other rules.", span)
    }
    expectStatementSeparator(Nullable("@use rule"))
    val uri = java.net.URI.create(url)
    new UseRule(uri, namespace, span, configBuf.toList)
  }

  /** Consumes a `@forward` rule body (after scanning `@forward`).
    *
    * dart-sass: `_forwardRule` (stylesheet.dart:1064-1124).
    */
  private def _forwardRule(start: ssg.sass.util.LineScannerState): ForwardRule = {
    whitespace(consumeNewlines = true)
    // dart-sass: _urlString() requires a quoted string.
    val url: String =
      if (scanner.peekChar() == CharCode.$double_quote || scanner.peekChar() == CharCode.$single_quote) {
        string()
      } else {
        scanner.error("Expected string.")
      }
    whitespace(consumeNewlines = false)

    // Optional `as prefix-*` clause.
    var prefix: Nullable[String] = Nullable.empty
    if (scanIdentifier("as")) {
      whitespace(consumeNewlines = true)
      // dart-sass: `prefix = identifier(normalize: true); scanner.expectChar($asterisk);`
      // The identifier must be followed by `*`.
      prefix = Nullable(identifier(normalize = true))
      scanner.expectChar(CharCode.$asterisk)
      whitespace(consumeNewlines = false)
    }

    // Optional `show` or `hide` clause.
    var shownMixinsAndFunctions:  Nullable[Set[String]] = Nullable.empty
    var shownVariables:           Nullable[Set[String]] = Nullable.empty
    var hiddenMixinsAndFunctions: Nullable[Set[String]] = Nullable.empty
    var hiddenVariables:          Nullable[Set[String]] = Nullable.empty
    if (scanIdentifier("show")) {
      whitespace(consumeNewlines = true)
      val (names, vars) = _memberList()
      shownMixinsAndFunctions = Nullable(names)
      shownVariables = Nullable(vars)
    } else if (scanIdentifier("hide")) {
      whitespace(consumeNewlines = true)
      val (names, vars) = _memberList()
      hiddenMixinsAndFunctions = Nullable(names)
      hiddenVariables = Nullable(vars)
    }

    // Optional `with ($name: expr [!default], ...)` configuration.
    val fwdConfigBuf = mutable.ListBuffer.empty[ConfiguredVariable]
    if (scanIdentifier("with")) {
      whitespace(consumeNewlines = true)
      scanner.expectChar(CharCode.$lparen)
      whitespace(consumeNewlines = true)
      var fmore = true
      while (fmore) {
        whitespace(consumeNewlines = true)
        val cvStart = scanner.state
        val varName = variableName()
        whitespace(consumeNewlines = true)
        scanner.expectChar(CharCode.$colon)
        whitespace(consumeNewlines = true)
        val expr = _rdExpression(
          stopAtComma = true,
          consumeNewlines = true,
          until = () => {
            val ch = scanner.peekChar()
            ch == CharCode.$rparen || ch == CharCode.$exclamation
          }
        )
        whitespace(consumeNewlines = true)
        var guarded = false
        if (scanner.scanChar(CharCode.$exclamation)) {
          val flag = identifier()
          if (flag == "default") guarded = true
          else scanner.error(s"Unknown flag !$flag.")
          whitespace(consumeNewlines = true)
        }
        fwdConfigBuf += ConfiguredVariable(varName, expr, spanFrom(cvStart), guarded)
        whitespace(consumeNewlines = true)
        if (scanner.scanChar(CharCode.$comma)) {
          whitespace(consumeNewlines = true)
          if (scanner.peekChar() == CharCode.$rparen) fmore = false
          else fmore = true
        } else fmore = false
      }
      whitespace(consumeNewlines = true)
      scanner.expectChar(CharCode.$rparen)
    }
    whitespace(consumeNewlines = false)

    expectStatementSeparator(Nullable("@forward rule"))
    val fwdSpan = spanFrom(start)
    // dart-sass: @forward rules must be written before any other rules.
    if (!_isUseAllowed) {
      error("@forward rules must be written before any other rules.", fwdSpan)
    }

    val uri = java.net.URI.create(url)
    if (shownMixinsAndFunctions.isDefined) {
      new ForwardRule(
        url = uri,
        span = fwdSpan,
        prefix = prefix,
        shownMixinsAndFunctions = shownMixinsAndFunctions,
        shownVariables = shownVariables,
        hiddenMixinsAndFunctions = Nullable.empty,
        hiddenVariables = Nullable.empty,
        configuration = fwdConfigBuf.toList
      )
    } else if (hiddenMixinsAndFunctions.isDefined) {
      new ForwardRule(
        url = uri,
        span = fwdSpan,
        prefix = prefix,
        shownMixinsAndFunctions = Nullable.empty,
        shownVariables = Nullable.empty,
        hiddenMixinsAndFunctions = hiddenMixinsAndFunctions,
        hiddenVariables = hiddenVariables,
        configuration = fwdConfigBuf.toList
      )
    } else {
      new ForwardRule(
        url = uri,
        span = fwdSpan,
        prefix = prefix,
        configuration = fwdConfigBuf.toList
      )
    }
  }

  /** Consumes a comma-separated list of member names (identifiers or $variables).
    *
    * dart-sass: `_memberList` (stylesheet.dart:1131-1147).
    */
  private def _memberList(): (Set[String], Set[String]) = {
    val identifiers = scala.collection.mutable.Set.empty[String]
    val variables   = scala.collection.mutable.Set.empty[String]
    var more        = true
    while (more) {
      whitespace(consumeNewlines = true)
      withErrorMessage("Expected variable, mixin, or function name") {
        if (scanner.peekChar() == CharCode.$dollar) {
          variables += variableName()
        } else {
          identifiers += identifier(normalize = true)
        }
      }
      whitespace(consumeNewlines = false)
      more = scanner.scanChar(CharCode.$comma)
    }
    (identifiers.toSet, variables.toSet)
  }

  private def _variableDeclaration(): VariableDeclaration = {
    val start = scanner.state
    val name  = variableName()
    whitespace(consumeNewlines = true)
    scanner.expectChar(CharCode.$colon)
    whitespace(consumeNewlines = true)

    // dart-sass: variable value parsed by _expression().
    val expression: Expression = _rdExpression(consumeNewlines = true)
    whitespace(consumeNewlines = false)

    // Handle !default / !global flags.
    var isGuarded = false
    var isGlobal  = false
    while (scanner.scanChar(CharCode.$exclamation)) {
      val flag = identifier()
      flag match {
        case "default" => isGuarded = true
        case "global"  => isGlobal = true
        case _         => scanner.error(s"Unknown flag !$flag.")
      }
      whitespace(consumeNewlines = false)
    }
    scanner.scanChar(CharCode.$semicolon)
    val declaration = new VariableDeclaration(name, expression, spanFrom(start), Nullable.empty, isGuarded, isGlobal)
    if (isGlobal) {
      _globalVariables.getOrElseUpdate(name, declaration.span)
    }
    declaration
  }

  /** Consumes a [StyleRule], optionally with a [buffer] that may contain some text that has already been parsed.
    *
    * dart-sass: `_styleRule` (stylesheet.dart:526-555).
    */

  /** Consumes an @at-root query expression of the form `(keyword: name name ...)`.
    *
    * dart-sass: `_atRootQuery` (stylesheet.dart:828-849).
    * Uses `_expression` to parse the keyword and names, which correctly handles
    * both unquoted identifiers and quoted strings (e.g. `"media"`).
    */
  private def _atRootQuery(start: ssg.sass.util.LineScannerState): Interpolation = {
    val queryStart = scanner.state
    val buffer     = new InterpolationBuffer()
    scanner.expectChar(CharCode.$lparen)
    buffer.writeCharCode(CharCode.$lparen)
    whitespace(consumeNewlines = true)

    _addOrInjectExpr(buffer, _rdExpression(consumeNewlines = true))
    if (scanner.scanChar(CharCode.$colon)) {
      whitespace(consumeNewlines = true)
      buffer.writeCharCode(CharCode.$colon)
      buffer.writeCharCode(CharCode.$space)
      _addOrInjectExpr(buffer, _rdExpression(consumeNewlines = true))
    }

    scanner.expectChar(CharCode.$rparen)
    whitespace(consumeNewlines = false)
    buffer.writeCharCode(CharCode.$rparen)

    buffer.interpolation(spanFrom(queryStart))
  }

  /** Adds the result of an expression to [buffer].
    *
    * If the expression is a StringExpression, it injects its interpolation
    * directly into the buffer. Otherwise, it wraps the expression in the buffer.
    *
    * dart-sass: `_addOrInject` (stylesheet.dart:4694-4703).
    */
  private def _addOrInjectExpr(buffer: InterpolationBuffer, expression: Expression): Unit = {
    expression match {
      case se: StringExpression if !se.hasQuotes =>
        buffer.addInterpolation(se.text)
      case _ =>
        buffer.add(expression, expression.span)
    }
  }

  private def _styleRule(
    buffer:   Nullable[InterpolationBuffer] = Nullable.Null,
    startOpt: Nullable[ssg.sass.util.LineScannerState] = Nullable.Null
  ): StyleRule = {
    _isUseAllowed = false
    val start = if (startOpt.isDefined) startOpt.get else scanner.state

    val interpolation = if (buffer.isDefined) {
      // When a buffer is provided, the _declarationOrBuffer already consumed
      // some text (e.g. an identifier like `b`). Read any remaining selector
      // text using styleRuleSelector(). If the scanner is already at `{`,
      // styleRuleSelector() would throw "Expected selector." because there's
      // nothing to read -- in that case, just use the buffer as-is.
      if (!indented && scanner.peekChar() == CharCode.$lbrace) {
        // SCSS: already at `{`, no more selector text to read
      } else if (indented && atEndOfStatement()) {
        // Sass: at end of statement (newline), no more selector text to read
      } else {
        val moreSelector = styleRuleSelector()
        buffer.get.addInterpolation(moreSelector)
      }
      buffer.get.interpolation(spanFrom(start))
    } else {
      styleRuleSelector()
    }
    if (interpolation.contents.isEmpty) scanner.error("expected \"}\".")
    val wasInStyleRule = _inStyleRule
    _inStyleRule = true
    val kids = _children()
    _inStyleRule = wasInStyleRule
    StyleRule(interpolation, kids, spanFrom(start))
  }

  /** Parses a block of children: `{ stmt; stmt; }` (SCSS) or indentation-based blocks (Sass).
    *
    * Routes through the virtual `children()` hook when the indented syntax is active, so SassParser's indentation-based implementation is used. For SCSS, consumes `{` ... `}` directly.
    *
    * dart-sass: `_withChildren` → `children(child)`.
    */
  private def _children(): List[Statement] = {
    if (indented) {
      // SassParser.children wraps child() inside _child(), which yields
      // Nullable and filters empties. When _childStatement() returns empty
      // the SassParser._child wrapper sees Nullable.Null and discards the
      // entry, so the sentinel SilentComment is never retained.
      val sentinel = new SilentComment("", scanner.emptySpan)
      return children { () =>
        val stmt = _childStatement()
        if (stmt.isDefined) stmt.get
        else sentinel
      }.filterNot(_ eq sentinel)
    }
    _childrenScss()
  }

  /** SCSS-specific children parser: `{ stmt; stmt; }`. */
  private def _childrenScss(): List[Statement] = {
    scanner.expectChar(CharCode.$lbrace)
    // dart-sass scss.dart:65: whitespace-without-comments after opening brace
    whitespaceWithoutComments(consumeNewlines = true)
    val stmts = mutable.ListBuffer.empty[Statement]
    while (!scanner.isDone && scanner.peekChar() != CharCode.$rbrace) {
      val childLoopPos = scanner.position
      // dart-sass scss.dart:68-94: comments are parsed as statements and
      // followed by whitespaceWithoutComments. Bare semicolons are consumed
      // then followed by whitespaceWithoutComments.
      val c = scanner.peekChar()
      if (c == CharCode.$semicolon) {
        scanner.readChar()
        whitespaceWithoutComments(consumeNewlines = true)
      } else {
        val stmt = _childStatement()
        if (stmt.isDefined) stmts += stmt.get
        whitespaceWithoutComments(consumeNewlines = true)
      }
      if (scanner.position == childLoopPos) {
        val ctx =
          if (scanner.isDone) "<EOF>"
          else {
            val end = math.min(scanner.position + 60, scanner.string.length)
            scanner.string.substring(scanner.position, end).replace("\n", "\\n")
          }
        throw new Error(
          s"_children() stall at pos ${scanner.position}: context=\"$ctx\""
        )
      }
    }
    scanner.expectChar(CharCode.$rbrace)
    stmts.toList
  }

  /** Parses a child statement inside a block — mirrors Dart `_statement`.
    *
    * dart-sass: `_statement` (stylesheet.dart:196-224).
    */
  private def _childStatement(): Nullable[Statement] = {
    val c = scanner.peekChar()
    if (c == CharCode.$at) {
      _atRule()
    } else if (c == CharCode.$dollar) {
      Nullable(_variableDeclaration())
    } else if (c == CharCode.$slash && (scanner.peekChar(1) == CharCode.$slash || scanner.peekChar(1) == CharCode.$asterisk)) {
      if (scanner.peekChar(1) == CharCode.$slash) _silentComment()
      else _loudComment()
    } else if (c == CharCode.$plus && indented && lookingAtIdentifier(1)) {
      // Indented syntax shorthand: `+include-name` => `@include include-name`
      _isUseAllowed = false
      val start = scanner.state
      scanner.readChar()
      Nullable(_includeRule(start))
    } else if (c == CharCode.$equal && indented) {
      // Indented syntax shorthand: `=mixin-name` => `@mixin mixin-name`
      _isUseAllowed = false
      val start = scanner.state
      scanner.readChar()
      whitespace(consumeNewlines = true)
      Nullable(_mixinRule(start))
    } else if (c == CharCode.$rbrace) {
      scanner.error("unmatched \"}\".", scanner.position, 1)
    } else {
      // Could be a declaration or a nested style rule. Lookahead is needed.
      _declarationOrStyleRule()
    }
  }

  /** A `() => Statement` child callback for use by `_eachRule`, `_forRule`, `_ifRule`, `_whileRule` when called from `_atRule`. Wraps `_childStatement()` (which returns `Nullable[Statement]`) so that
    * it conforms to the `children(child)` contract.
    *
    * In indented mode, `SassParser._child` handles empty lines and comments *before* calling this callback, so the Nullable‐empty case is a fallback that should rarely be reached.
    */
  private val _childStatementAsChild: () => Statement = () => {
    val stmt = _childStatement()
    if (stmt.isDefined) stmt.get
    else {
      // SassParser._child already filters blank lines; SCSS _childrenScss
      // skips whitespace. This path is reached only for truly empty input.
      scanner.error("Expected statement.")
    }
  }

  /** Consumes a [VariableDeclaration], a [Declaration], or a [StyleRule].
    *
    * dart-sass: `_declarationOrStyleRule` (stylesheet.dart:370-381).
    */
  private def _declarationOrStyleRule(): Nullable[Statement] = {
    // The indented syntax allows a single backslash to distinguish a style rule
    // from old-style property syntax. We don't support old property syntax, but
    // we do support the backslash because it's easy to do.
    if (indented && scanner.scanChar(CharCode.$backslash)) return Nullable(_styleRule())

    val start            = scanner.state
    val declarationOrBuf = _declarationOrBuffer()
    declarationOrBuf match {
      case stmt: Statement =>
        Nullable(stmt)
      case buf: InterpolationBuffer =>
        Nullable(_styleRule(Nullable(buf), Nullable(start)))
    }
  }

  /** Tries to parse a variable or property declaration, and returns the value parsed so far if it fails.
    *
    * This can return either an [[InterpolationBuffer]], indicating that it couldn't consume a declaration and that selector parsing should be attempted; or it can return a [[Declaration]] or a
    * [[VariableDeclaration]], indicating that it successfully consumed a declaration.
    *
    * dart-sass: `_declarationOrBuffer` (stylesheet.dart:390-494).
    */
  private def _declarationOrBuffer(): Statement | InterpolationBuffer = {
    val start      = scanner.state
    val nameBuffer = new InterpolationBuffer()

    var startsWithPunctuation = false
    if (_lookingAtPotentialPropertyHack()) {
      startsWithPunctuation = true
      nameBuffer.writeCharCode(scanner.readChar())
      nameBuffer.write(rawText(() => whitespace(consumeNewlines = false)))
    }

    if (!_lookingAtInterpolatedIdentifier()) return nameBuffer

    val variableOrInterpolation: VariableDeclaration | Interpolation =
      if (startsWithPunctuation) interpolatedIdentifier()
      else _variableDeclarationOrInterpolation()

    variableOrInterpolation match {
      case vd:     VariableDeclaration => return vd
      case interp: Interpolation       => nameBuffer.addInterpolation(interp)
    }

    _isUseAllowed = false
    if (scanner.matches("/*")) nameBuffer.write(rawText(() => loudComment()))

    val midBuffer = new StringBuilder()
    midBuffer.append(rawText(() => whitespace(consumeNewlines = false)))
    val beforeColon = scanner.state
    if (!scanner.scanChar(CharCode.$colon)) {
      if (midBuffer.nonEmpty) nameBuffer.writeCharCode(CharCode.$space)
      return nameBuffer
    }
    midBuffer.append(':')

    // Parse custom properties as declarations no matter what.
    // dart-sass lines 424-438: also parse `result:` as a non-SassScript
    // declaration when inside a plain CSS `@function` rule.
    val name             = nameBuffer.interpolation(spanFrom(start, beforeColon))
    val isCustomProperty = name.initialPlain.startsWith("--")
    val isPlainCssResult = _inPlainCssFunction &&
      name.asPlain.fold(false)(n => ssg.sass.Utils.equalsIgnoreCase(Nullable(n), Nullable("result")))
    if (isCustomProperty || isPlainCssResult) {
      val value = StringExpression(
        if (atEndOfStatement()) Interpolation(List.empty, List.empty, scanner.emptySpan)
        else _interpolatedDeclarationValue(silentComments = false)
      )
      expectStatementSeparator(
        Nullable(if (isCustomProperty) "custom property" else "@function result")
      )
      return Declaration.notSassScript(name, value, spanFrom(start))
    }

    if (scanner.scanChar(CharCode.$colon)) {
      nameBuffer.write(midBuffer.toString)
      nameBuffer.writeCharCode(CharCode.$colon)
      return nameBuffer
    } else if (indented && _lookingAtInterpolatedIdentifier()) {
      // In the indented syntax, `foo:bar` is always considered a selector
      // rather than a property.
      nameBuffer.write(midBuffer.toString)
      return nameBuffer
    }

    val postColonWhitespace = rawText(() => whitespace(consumeNewlines = false))
    val tryNested           = _tryDeclarationChildren(name, start)
    if (tryNested.isDefined) return tryNested.get

    midBuffer.append(postColonWhitespace)
    val couldBeSelector =
      postColonWhitespace.isEmpty && _lookingAtInterpolatedIdentifier()

    val beforeDeclaration = scanner.state
    // Faithful port of the Dart pattern where `value` is declared before the
    // try and assigned inside it. We use Nullable to avoid a raw null cast.
    var value: Nullable[Expression] = Nullable.Null
    try {
      value = Nullable(_rdExpression())

      if (lookingAtChildren()) {
        // Properties that are ambiguous with selectors can't have additional
        // properties nested beneath them, so we force an error. This will be
        // caught below and cause the text to be reparsed as a selector.
        if (couldBeSelector) expectStatementSeparator()
      } else if (!atEndOfStatement()) {
        // Force an exception if there isn't a valid end-of-property character
        // but don't consume that character. This will also cause the text to be
        // reparsed.
        expectStatementSeparator()
      }
    } catch {
      case e: Exception =>
        if (!couldBeSelector) throw e
        // If the value would be followed by a semicolon, it's definitely supposed
        // to be a property, not a selector.
        scanner.state = beforeDeclaration
        val additional = almostAnyValue()
        if (!indented && scanner.peekChar() == CharCode.$semicolon) throw e

        nameBuffer.write(midBuffer.toString)
        nameBuffer.addInterpolation(additional)
        return nameBuffer
    }

    val tryNested2 = _tryDeclarationChildren(name, start, value = value)
    if (tryNested2.isDefined) return tryNested2.get

    expectStatementSeparator()
    Declaration(name, value.get, spanFrom(start))
  }

  /** Tries to parse a namespaced [[VariableDeclaration]], and returns the value parsed so far if it fails.
    *
    * dart-sass: `_variableDeclarationOrInterpolation` (stylesheet.dart:503-522).
    */
  private def _variableDeclarationOrInterpolation(): VariableDeclaration | Interpolation = {
    if (!lookingAtIdentifier()) return interpolatedIdentifier()

    val start = scanner.state
    val ident = identifier()
    if (scanner.matches(".$")) {
      scanner.readChar()
      return variableDeclarationWithoutNamespace(Nullable(ident), Nullable(start))
    } else {
      val buffer = new InterpolationBuffer()
      buffer.write(ident)

      // Parse the rest of an interpolated identifier if one exists, so callers
      // don't have to.
      if (_lookingAtInterpolatedIdentifierBody()) {
        buffer.addInterpolation(interpolatedIdentifier())
      }

      buffer.interpolation(spanFrom(start))
    }
  }

  /** Tries parsing nested children of a declaration whose [name] has already been parsed, and returns Nullable.empty if it doesn't have any.
    *
    * dart-sass: `_tryDeclarationChildren` (stylesheet.dart:636-651).
    */
  private def _tryDeclarationChildren(
    name:  Interpolation,
    start: ssg.sass.util.LineScannerState,
    value: Nullable[Expression] = Nullable.Null
  ): Nullable[Declaration] = {
    if (!lookingAtChildren()) return Nullable.empty
    if (plainCss) {
      scanner.error("Nested declarations aren't allowed in plain CSS.")
    }
    Nullable(_withChildren(
      () => _declarationChild(),
      start,
      (kids, span) => Declaration.nested(name, kids, span, value = value)
    ))
  }

  /** Consumes a variable declaration (with an optional preceding namespace).
    *
    * dart-sass: `variableDeclarationWithoutNamespace` (stylesheet.dart:239-317).
    */
  protected def variableDeclarationWithoutNamespace(
    namespace: Nullable[String] = Nullable.Null,
    startOpt:  Nullable[ssg.sass.util.LineScannerState] = Nullable.Null
  ): VariableDeclaration = {
    val precedingComment = lastSilentComment
    lastSilentComment = Nullable.Null
    val start = if (startOpt.isDefined) startOpt.get else scanner.state

    val name = variableName()
    if (namespace.isDefined) _assertPublic(name, () => spanFrom(start))

    if (plainCss) {
      error("Sass variables aren't allowed in plain CSS.", spanFrom(start))
    }

    whitespace(consumeNewlines = true)
    scanner.expectChar(CharCode.$colon)
    whitespace(consumeNewlines = true)

    // dart-sass: `var value = _expression();` — no consumeNewlines.
    // In SCSS, this is irrelevant (whitespace always consumes newlines).
    // In the indented syntax, consumeNewlines = true would cause the
    // expression parser to eat across line boundaries.
    val value: Expression = _rdExpression()

    var guarded   = false
    var global    = false
    var flagStart = scanner.state
    while (scanner.scanChar(CharCode.$exclamation)) {
      identifier() match {
        case "default" =>
          if (guarded) {
            warnings += ParseTimeWarning(
              Nullable(Deprecation.DuplicateVarFlags),
              spanFrom(flagStart),
              "!default should only be written once for each variable.\n" +
                "This will be an error in Dart Sass 2.0.0."
            )
          }
          guarded = true

        case "global" =>
          if (namespace.isDefined) {
            error(
              "!global isn't allowed for variables in other modules.",
              spanFrom(flagStart)
            )
          } else if (global) {
            warnings += ParseTimeWarning(
              Nullable(Deprecation.DuplicateVarFlags),
              spanFrom(flagStart),
              "!global should only be written once for each variable.\n" +
                "This will be an error in Dart Sass 2.0.0."
            )
          }
          global = true

        case _ =>
          error("Invalid flag name.", spanFrom(flagStart))
      }

      whitespace(consumeNewlines = false)
      flagStart = scanner.state
    }

    expectStatementSeparator(Nullable("variable declaration"))
    val declaration = new VariableDeclaration(
      name,
      value,
      spanFrom(start),
      namespace,
      guarded,
      global,
      precedingComment
    )
    if (global) _globalVariables.getOrElseUpdate(name, declaration.span)
    declaration
  }

  /** Like [identifier], but rejects identifiers that begin with `_` or `-`.
    *
    * dart-sass: `_publicIdentifier` (stylesheet.dart:4796-4800).
    */
  @scala.annotation.nowarn("msg=unused private member")
  private def _publicIdentifier(): String = {
    val start  = scanner.state
    val result = identifier()
    _assertPublic(result, () => spanFrom(start))
    result
  }

  /** Throws an error if [ident] isn't public.
    *
    * dart-sass: `_assertPublic` (stylesheet.dart:4806-4812).
    */
  private def _assertPublic(ident: String, span: () => FileSpan): Unit = {
    if (!CharCode.isPrivate(ident)) return
    error(
      "Private members can't be accessed from outside their modules.",
      span()
    )
  }



  /** Consumes and throws "This at-rule is not allowed here."
    *
    * dart-sass: `_disallowedAtRule` (stylesheet.dart:1797-1801).
    */
  private def _disallowedAtRule(start: ssg.sass.util.LineScannerState): Nothing = {
    whitespace(consumeNewlines = false)
    _interpolatedDeclarationValue(allowEmpty = true, allowOpenBrace = false)
    error("This at-rule is not allowed here.", spanFrom(start))
  }

  /** Consumes a statement allowed within a function body.
    *
    * dart-sass: `_functionChild` (stylesheet.dart:755-795).
    */
  private def _functionChild(): Statement = {
    if (scanner.peekChar() != CharCode.$at) {
      val saved = scanner.state
      try
        return _variableDeclarationWithNamespace()
      catch {
        case variableDeclarationError: Exception =>
          scanner.state = saved
          // If a variable declaration failed to parse, it's possible the user
          // thought they could write a style rule or property declaration in a
          // function. If so, throw a more helpful error message.
          val statement: Statement =
            try
              _declarationOrStyleRule().getOrElse(throw variableDeclarationError)
            catch {
              case _: Exception => throw variableDeclarationError
            }
          val what = statement match {
            case _: StyleRule => "style rules"
            case _ => "declarations"
          }
          error(
            s"@function rules may not contain $what.",
            statement.span
          )
      }
    }

    val fnStart = scanner.state
    scanner.expectChar(CharCode.$at, Nullable("@-rule"))
    val fnRuleName = identifier()
    fnRuleName match {
      case "debug"  => _debugRule(fnStart)
      case "each"   => _eachRule(fnStart, () => _functionChild())
      case "else"   => _disallowedAtRule(fnStart)
      case "error"  => _errorRule(fnStart)
      case "for"    => _forRule(fnStart, () => _functionChild())
      case "if"     => _ifRule(fnStart, () => _functionChild())
      case "return" => _returnRule(fnStart)
      case "warn"   => _warnRule(fnStart)
      case "while"  => _whileRule(fnStart, () => _functionChild())
      case _        => _disallowedAtRule(fnStart)
    }
  }

  /** Parses the children of a `@function` body using `_functionChild`.
    *
    * dart-sass: `_withChildren(_functionChild, ...)`.
    */
  private def _functionChildren(): List[Statement] = {
    if (indented) {
      val sentinel = new SilentComment("", scanner.emptySpan)
      return children(() =>
        try _functionChild()
        catch { case _: Exception => sentinel }
      ).filterNot(_ eq sentinel)
    }
    scanner.expectChar(CharCode.$lbrace)
    whitespace(consumeNewlines = true)
    val stmts = mutable.ListBuffer.empty[Statement]
    while (!scanner.isDone && scanner.peekChar() != CharCode.$rbrace) {
      val childLoopPos = scanner.position
      val c = scanner.peekChar()
      // dart-sass scss.dart:63-96: the SCSS `children()` loop handles `$`,
      // comments, `;`, and `}` directly before delegating to the `child()`
      // callback. We replicate that dispatch here.
      if (c == CharCode.$dollar) {
        // Variable declarations are handled directly by the SCSS children loop,
        // not by _functionChild.
        stmts += variableDeclarationWithoutNamespace()
      } else if (c == CharCode.$slash && scanner.peekChar(1) == CharCode.$slash) {
        silentComment()
      } else if (c == CharCode.$slash && scanner.peekChar(1) == CharCode.$asterisk) {
        loudComment()
      } else if (c == CharCode.$semicolon) {
        scanner.readChar()
      } else {
        stmts += _functionChild()
      }
      whitespace(consumeNewlines = true)
      if (scanner.position == childLoopPos) {
        val ctx =
          if (scanner.isDone) "<EOF>"
          else {
            val end = math.min(scanner.position + 60, scanner.string.length)
            scanner.string.substring(scanner.position, end).replace("\n", "\\n")
          }
        throw new Error(
          s"_functionChildren() stall at pos ${scanner.position}: context=\"$ctx\""
        )
      }
    }
    scanner.expectChar(CharCode.$rbrace)
    stmts.toList
  }

  /** Parses an at-rule allowed within a property declaration.
    *
    * dart-sass: `_declarationAtRule` (stylesheet.dart:737-752).
    */
  private def _declarationAtRule(): Statement = {
    val dStart = scanner.state
    scanner.expectChar(CharCode.$at, Nullable("@-rule"))
    val dName = identifier()
    dName match {
      case "content" => _contentRule(dStart)
      case "debug"   => _debugRule(dStart)
      case "each"    => _eachRule(dStart, () => _declarationChild())
      case "else"    => _disallowedAtRule(dStart)
      case "error"   => _errorRule(dStart)
      case "for"     => _forRule(dStart, () => _declarationChild())
      case "if"      => _ifRule(dStart, () => _declarationChild())
      case "include" => _includeRule(dStart)
      case "warn"    => _warnRule(dStart)
      case "while"   => _whileRule(dStart, () => _declarationChild())
      case _         => _disallowedAtRule(dStart)
    }
  }

  /** Consumes a statement allowed within a declaration.
    *
    * dart-sass: `_declarationChild` (stylesheet.dart:654-656).
    */
  private def _declarationChild(): Statement =
    if (scanner.peekChar() == CharCode.$at) _declarationAtRule()
    else _propertyOrVariableDeclaration()

  /** Consumes either a property declaration or a namespaced variable
    * declaration.
    *
    * This is only used when nested beneath other declarations. Otherwise,
    * [[_declarationOrStyleRule]] is used instead.
    *
    * dart-sass: `_propertyOrVariableDeclaration` (stylesheet.dart:590-629).
    */
  private def _propertyOrVariableDeclaration(): Statement = {
    val start = scanner.state

    val name: Interpolation =
      if (_lookingAtPotentialPropertyHack()) {
        val nameBuffer = new InterpolationBuffer()
        nameBuffer.writeCharCode(scanner.readChar())
        nameBuffer.write(rawText(() => whitespace(consumeNewlines = false)))
        nameBuffer.addInterpolation(interpolatedIdentifier())
        nameBuffer.interpolation(spanFrom(start))
      } else if (!plainCss) {
        val variableOrInterpolation = _variableDeclarationOrInterpolation()
        variableOrInterpolation match {
          case vd: VariableDeclaration => return vd
          case interp: Interpolation   => interp
        }
      } else {
        interpolatedIdentifier()
      }

    whitespace(consumeNewlines = false)
    scanner.expectChar(CharCode.$colon)

    if (name.initialPlain.startsWith("--")) {
      error("Declarations whose names begin with \"--\" may not be nested.", name.span)
    }

    whitespace(consumeNewlines = false)
    val tryNested = _tryDeclarationChildren(name, start)
    if (tryNested.isDefined) return tryNested.get

    val value = _rdExpression()
    val tryNested2 = _tryDeclarationChildren(name, start, value = Nullable(value))
    if (tryNested2.isDefined) return tryNested2.get

    expectStatementSeparator()
    Declaration(name, value, spanFrom(start))
  }

  /** Consumes a namespaced variable declaration.
    *
    * dart-sass: `_variableDeclarationWithNamespace` (stylesheet.dart:227-237).
    */
  private def _variableDeclarationWithNamespace(): VariableDeclaration = {
    val vdStart   = scanner.state
    val namespace = identifier()
    scanner.expectChar(CharCode.$dot)
    variableDeclarationWithoutNamespace(Nullable(namespace), Nullable(vdStart))
  }

  /** Consumes a namespaced [VariableDeclaration] or a [StyleRule].
    *
    * dart-sass: `_variableDeclarationOrStyleRule` (stylesheet.dart:319-339).
    */
  private def _variableDeclarationOrStyleRule(): Nullable[Statement] = {
    if (plainCss) return Nullable(_styleRule())

    // The indented syntax allows a single backslash to distinguish a style rule
    // from old-style property syntax. We don't support old property syntax, but
    // we do support the backslash because it's easy to do.
    if (indented && scanner.scanChar(CharCode.$backslash)) return Nullable(_styleRule())

    if (!lookingAtIdentifier()) return Nullable(_styleRule())

    val vdssStart               = scanner.state
    val variableOrInterpolation = _variableDeclarationOrInterpolation()
    variableOrInterpolation match {
      case vd:     VariableDeclaration => Nullable(vd)
      case _: Interpolation =>
        // Not a namespaced variable — rewind and parse as a style rule.
        // dart-sass resets the scanner when _parseSelectors is true (line 534);
        // we always reset because our styleRuleSelector() reads the full text
        // from the current position.
        scanner.state = vdssStart
        Nullable(_styleRule())
    }
  }

  /** Helper that dispatches a standalone `@include` rule.
    *
    * dart-sass: `_includeRule` (stylesheet.dart:1390-1436).
    */
  private def _includeRule(start: ssg.sass.util.LineScannerState): IncludeRule = {
    whitespace(consumeNewlines = true)
    var incName = identifier()
    var incNamespace: Nullable[String] = Nullable.empty
    if (scanner.scanChar(CharCode.$dot)) {
      incNamespace = Nullable(incName)
      incName = _publicIdentifier()
    }
    whitespace(consumeNewlines = false)
    val argList = if (scanner.peekChar() == CharCode.$lparen) {
      _rdArgumentInvocation(start, mixin = true)
    } else {
      ArgumentList.empty(scanner.emptySpan)
    }
    whitespace(consumeNewlines = false)
    // Optional `using ($p1, $p2, ...)` clause
    var contentParams: ParameterList = ParameterList.empty(scanner.emptySpan)
    var hasUsing = false
    if (scanIdentifier("using")) {
      hasUsing = true
      whitespace(consumeNewlines = true)
      contentParams = _parseParameterList(start)
      whitespace(consumeNewlines = false)
    }
    // Optional trailing content block
    val contentBlock: Nullable[ContentBlock] =
      if (hasUsing || lookingAtChildren()) {
        val cbStart           = scanner.state
        val wasInContentBlock = _inContentBlock
        _inContentBlock = true
        val kids = _children()
        _inContentBlock = wasInContentBlock
        Nullable(new ContentBlock(contentParams, kids, spanFrom(cbStart)))
      } else {
        expectStatementSeparator()
        Nullable.empty
      }
    val endSpan = if (contentBlock.isDefined) contentBlock.get.span else argList.span
    val span    = spanFrom(start, start).expand(endSpan)
    new IncludeRule(incName, argList, span, incNamespace, contentBlock)
  }

  /** Helper that dispatches a standalone `@mixin` rule.
    *
    * dart-sass: `_mixinRule` (stylesheet.dart:1455-1502).
    */
  private def _mixinRule(start: ssg.sass.util.LineScannerState): MixinRule = {
    whitespace(consumeNewlines = true)
    val precedingMixComment = lastSilentComment
    lastSilentComment = Nullable.Null
    val beforeMixName = scanner.state
    val mixName       = identifier()
    // Reject @mixin names starting with `--`
    if (mixName.startsWith("--")) {
      error(
        "Sass @mixin names beginning with -- are forbidden for forward-" +
          "compatibility with plain CSS mixins.\n\n" +
          "For details, see https://sass-lang.com/d/css-function-mixin",
        spanFrom(beforeMixName)
      )
    }
    whitespace(consumeNewlines = false)
    val params = if (scanner.peekChar() == CharCode.$lparen) {
      _parseParameterList(start)
    } else {
      ParameterList.empty(scanner.emptySpan)
    }
    // Context checks
    if (_inMixin || _inContentBlock) {
      error("Mixins may not contain mixin declarations.", spanFrom(start))
    } else if (_inControlDirective) {
      error("Mixins may not be declared in control directives.", spanFrom(start))
    }
    whitespace(consumeNewlines = false)
    _inMixin = true
    val kids = _children()
    _inMixin = false
    new MixinRule(mixName, params, kids, spanFrom(start), precedingMixComment)
  }

  // --- Standalone at-rule helpers (used by _functionChild / _declarationAtRule) ---

  private def _returnRule(start: ssg.sass.util.LineScannerState): ReturnRule = {
    whitespace(consumeNewlines = true)
    val retExpr = _rdExpression()
    val retEnd  = scanner.state
    expectStatementSeparator(Nullable("@return rule"))
    new ReturnRule(retExpr, spanFrom(start, retEnd))
  }

  private def _debugRule(start: ssg.sass.util.LineScannerState): DebugRule = {
    whitespace(consumeNewlines = true)
    val value         = _rdExpression()
    val expressionEnd = scanner.state
    expectStatementSeparator(Nullable("@debug rule"))
    new DebugRule(value, spanFrom(start, expressionEnd))
  }

  private def _errorRule(start: ssg.sass.util.LineScannerState): ErrorRule = {
    whitespace(consumeNewlines = true)
    val value         = _rdExpression()
    val expressionEnd = scanner.state
    expectStatementSeparator(Nullable("@error rule"))
    new ErrorRule(value, spanFrom(start, expressionEnd))
  }

  private def _warnRule(start: ssg.sass.util.LineScannerState): WarnRule = {
    whitespace(consumeNewlines = true)
    val value         = _rdExpression()
    val expressionEnd = scanner.state
    expectStatementSeparator(Nullable("@warn rule"))
    new WarnRule(value, spanFrom(start, expressionEnd))
  }

  private def _contentRule(start: ssg.sass.util.LineScannerState): ContentRule = {
    if (!_inMixin) {
      error("@content is only allowed within mixin declarations.", spanFrom(start))
    }
    val beforeWs = scanner.state
    whitespace(consumeNewlines = false)
    val cArgs =
      if (scanner.peekChar() == CharCode.$lparen) {
        val r = _rdArgumentInvocation(start, mixin = true)
        // dart-sass: whitespace(consumeNewlines: false) after args,
        // consuming any trailing comments before the statement separator.
        whitespace(consumeNewlines = false)
        r
      } else {
        ArgumentList.empty(spanFrom(beforeWs, beforeWs))
      }
    expectStatementSeparator(Nullable("@content rule"))
    new ContentRule(cArgs, spanFrom(start))
  }

  private def _eachRule(start: ssg.sass.util.LineScannerState, child: () => Statement): EachRule = {
    whitespace(consumeNewlines = true)
    val wasInControlDirective = _inControlDirective
    _inControlDirective = true
    val variables = mutable.ListBuffer.empty[String]
    variables += variableName()
    whitespace(consumeNewlines = true)
    while (scanner.scanChar(CharCode.$comma)) {
      whitespace(consumeNewlines = true)
      variables += variableName()
      whitespace(consumeNewlines = true)
    }
    whitespace(consumeNewlines = true)
    expectIdentifier("in")
    whitespace(consumeNewlines = true)
    val list = _rdExpression()
    val kids = children(child)
    _inControlDirective = wasInControlDirective
    new EachRule(variables.toList, list, kids, spanFrom(start))
  }

  private def _forRule(start: ssg.sass.util.LineScannerState, child: () => Statement): ForRule = {
    whitespace(consumeNewlines = true)
    val wasInControlDirective = _inControlDirective
    _inControlDirective = true
    val variable = variableName()
    whitespace(consumeNewlines = true)
    expectIdentifier("from")
    whitespace(consumeNewlines = true)
    var exclusive: Nullable[Boolean] = Nullable.Null
    val from = _rdExpression(
      consumeNewlines = true,
      until = () =>
        if (!lookingAtIdentifier()) false
        else if (scanIdentifier("to")) { exclusive = Nullable(true); true }
        else if (scanIdentifier("through")) { exclusive = Nullable(false); true }
        else false
    )
    if (exclusive.isEmpty) scanner.error("Expected \"to\" or \"through\".")
    whitespace(consumeNewlines = true)
    val to   = _rdExpression()
    val kids = children(child)
    _inControlDirective = wasInControlDirective
    new ForRule(variable, from, to, kids, spanFrom(start), exclusive.get)
  }

  private def _ifRule(start: ssg.sass.util.LineScannerState, child: () => Statement): IfRule = {
    whitespace(consumeNewlines = true)
    val ifIndentation         = currentIndentation
    val wasInControlDirective = _inControlDirective
    _inControlDirective = true
    val condition  = _rdExpression()
    val ifChildren = children(child)
    whitespaceWithoutComments(consumeNewlines = false)
    val clauses = mutable.ListBuffer.empty[IfClause]
    clauses += new IfClause(condition, ifChildren)
    var lastClause: Nullable[ElseClause] = Nullable.empty
    while (scanElse(ifIndentation)) {
      whitespace(consumeNewlines = false)
      if (scanIdentifier("if")) {
        whitespace(consumeNewlines = true)
        clauses += new IfClause(_rdExpression(), children(child))
      } else {
        lastClause = Nullable(new ElseClause(children(child)))
      }
    }
    _inControlDirective = wasInControlDirective
    val span = spanFrom(start)
    whitespaceWithoutComments(consumeNewlines = false)
    new IfRule(clauses.toList, span, lastClause)
  }

  private def _whileRule(start: ssg.sass.util.LineScannerState, child: () => Statement): WhileRule = {
    whitespace(consumeNewlines = true)
    val wasInControlDirective = _inControlDirective
    _inControlDirective = true
    val condition = _rdExpression()
    val kids      = children(child)
    _inControlDirective = wasInControlDirective
    new WhileRule(condition, kids, spanFrom(start))
  }

  /** Builds a generic unknown at-rule from the scanner.
    *
    * dart-sass: `unknownAtRule` (stylesheet.dart:1759-1790).
    */
  private def _unknownAtRule(start: ssg.sass.util.LineScannerState, nameInterp: Interpolation): AtRule = {
    val wasInUnknownAtRule = _inUnknownAtRule
    _inUnknownAtRule = true

    // dart-sass line 1763: consume whitespace (not newlines) before value.
    whitespace(consumeNewlines = false)

    // dart-sass lines 1765-1768: parse the value using _interpolatedDeclarationValue
    // which handles interpolation, brackets, and indented-syntax newlines correctly.
    val value: Nullable[Interpolation] =
      if (scanner.peekChar() != CharCode.$exclamation && !atEndOfStatement()) {
        Nullable(_interpolatedDeclarationValue(allowOpenBrace = false))
      } else {
        Nullable.empty[Interpolation]
      }

    // dart-sass lines 1769-1772: set _inPlainCssFunction when the at-rule name
    // is "function" (case-insensitive).
    val wasInPlainCssFunction = _inPlainCssFunction
    nameInterp.asPlain match {
      case np if np.isDefined && ssg.sass.Utils.equalsIgnoreCase(np, Nullable("function")) =>
        _inPlainCssFunction = true
      case _ => ()
    }

    try {
      // dart-sass lines 1774-1785: either children or statement separator.
      if (lookingAtChildren()) {
        val kids = _children()
        new AtRule(name = nameInterp, span = spanFrom(start), value = value, childStatements = Nullable(kids))
      } else {
        expectStatementSeparator()
        new AtRule(name = nameInterp, span = spanFrom(start), value = value, childStatements = Nullable.empty)
      }
    } finally {
      _inUnknownAtRule = wasInUnknownAtRule
      _inPlainCssFunction = wasInPlainCssFunction
    }
  }

  /** Consumes a `@-moz-document` rule.
    *
    * dart-sass: `mozDocumentRule` (stylesheet.dart:1504-1588).
    * Gecko's `@-moz-document` diverges from the specification; it allows the
    * `url-prefix` and `domain` functions to omit quotation marks.
    *
    * The key difference from `_unknownAtRule` is that `whitespace()` is called
    * between function arguments, which strips comments that would otherwise
    * be preserved verbatim in the at-rule value.
    */
  private def _mozDocumentRule(start: ssg.sass.util.LineScannerState, nameInterp: Interpolation): AtRule = {
    whitespace(consumeNewlines = false)
    val valueStart = scanner.state
    val buffer     = new InterpolationBuffer()
    var needsDeprecationWarning = false

    import scala.util.boundary, boundary.break
    boundary {
      while (true) {
        if (scanner.peekChar() == CharCode.$hash) {
          val (expr, sp) = singleInterpolation()
          buffer.add(expr, sp)
          needsDeprecationWarning = true
        } else {
          val identifierStart = scanner.state
          val ident           = identifier()
          ident match {
            case "url" | "url-prefix" | "domain" =>
              val urlContents = _tryUrlContents(identifierStart, name = ident)
              if (urlContents.isDefined) {
                buffer.addInterpolation(urlContents.get)
              } else {
                scanner.expectChar(CharCode.$lparen)
                whitespace(consumeNewlines = false)
                // dart-sass uses interpolatedStringToken() which includes
                // the quote characters in the output. We emulate this by
                // reading the string and manually wrapping with quotes,
                // preserving the original quote character.
                val argQuote = scanner.peekChar().toChar
                val argStr = interpolatedString()
                scanner.expectChar(CharCode.$rparen)
                buffer.write(ident)
                buffer.writeCharCode(CharCode.$lparen)
                buffer.addInterpolation(argStr.asInterpolation(static = true, quote = Nullable(argQuote)))
                buffer.writeCharCode(CharCode.$rparen)
              }
              // A url-prefix with no argument, or with an empty string as an
              // argument, is not (yet) deprecated.
              val trailing = buffer.trailingString
              if (
                !trailing.endsWith("url-prefix()") &&
                !trailing.endsWith("url-prefix('')") &&
                !trailing.endsWith("url-prefix(\"\")")
              ) {
                needsDeprecationWarning = true
              }
            case "regexp" =>
              buffer.write("regexp(")
              scanner.expectChar(CharCode.$lparen)
              val regexpQuote = scanner.peekChar().toChar
              buffer.addInterpolation(interpolatedString().asInterpolation(static = true, quote = Nullable(regexpQuote)))
              scanner.expectChar(CharCode.$rparen)
              buffer.writeCharCode(CharCode.$rparen)
              needsDeprecationWarning = true
            case _ =>
              error("Invalid function name.", spanFrom(identifierStart))
          }
        }

        whitespace(consumeNewlines = false)
        if (!scanner.scanChar(CharCode.$comma)) break(())

        buffer.writeCharCode(CharCode.$comma)
        buffer.write(rawText(() => whitespace(consumeNewlines = false)))
      }
    }

    val value = buffer.interpolation(spanFrom(valueStart))
    val kids  = _children()
    val span  = spanFrom(start)

    if (needsDeprecationWarning) {
      warnDeprecation(
        Deprecation.MozDocument,
        "@-moz-document is deprecated and support will be removed in Dart Sass 2.0.0.\n\nFor details, see https://sass-lang.com/d/moz-document.",
        span
      )
    }

    new AtRule(name = nameInterp, span = span, value = Nullable(value), childStatements = Nullable(kids))
  }

  /** If the scanner is positioned at `!important` (optionally with whitespace between the `!` and `important`), consume it and return true. Otherwise leave the scanner unchanged and return false.
    */
  @scala.annotation.nowarn("msg=unused private member")
  private def _tryScanImportant(): Boolean = {
    val saved = scanner.state
    if (scanner.peekChar() != CharCode.$exclamation) false
    else {
      scanner.readChar() // '!'
      whitespace(consumeNewlines = false)
      if (scanIdentifier("important", caseSensitive = false)) {
        whitespace(consumeNewlines = false)
        true
      } else {
        scanner.state = saved
        false
      }
    }
  }

  /** Parses a silent Sass comment (`//...`). */
  private def _silentComment(): Nullable[Statement] = {
    val start = scanner.state
    silentComment()
    val comment = new SilentComment(scanner.substring(start.position), spanFrom(start))
    lastSilentComment = Nullable(comment)
    Nullable(comment)
  }

  /** Parses a loud CSS comment (`/* ... */`). */
  private def _loudComment(): Nullable[Statement] = {
    val start = scanner.state
    loudComment()
    val text = scanner.substring(start.position)
    val span = spanFrom(start)
    // Parse #{...} interpolations within the comment text so they're
    // evaluated at runtime (e.g. `/*#{meta.inspect($x)}*/`).
    val interp =
      if (text.contains("#{")) _parseInterpolatedString(text, span)
      else Interpolation.plain(text, span)
    Nullable(new LoudComment(interp))
  }

  // NOTE: The text-based `_expression()` method has been removed (ISS-680).
  // All expression parsing now goes through `_rdExpression()`.
  // `_parseParameterList` and `_parseArgumentList` now use recursive-descent
  // (`expressionUntilComma()` / `_rdArgumentInvocation()`).
  //
  // The text-based helper `_parseSimpleExpression` (and its internal helpers
  // `_splitTopLevel`, `_tryParseFunctionCall`, `_parseBinaryOps`,
  // `_tryParseNumber`, `_trySpecialCssFunction`, `_tokenizeArithmetic`) is
  // retained ONLY for interpolation parsing:
  //   - `_parseInterpolatedString` parses `#{...}` segments inside
  //     text-from-string snippets and delegates each interpolated expression
  //     to `_parseSimpleExpression`.
  //   - `_readCustomPropertyValue` and `_readInterpolatedName` (currently
  //     unused/scaffolded) would similarly delegate to `_parseSimpleExpression`.
  // No other non-interpolation callers remain.

  /** Parses a simple expression string into an Expression node. Handles: bare identifiers, variables, numbers with units, quoted strings, booleans (true/false/null). Falls back to unquoted
    * StringExpression for complex forms.
    */
  /** Removes `/*…*/` and `//…<eol>` comments from a raw expression text, leaving the contents of quoted strings alone. */
  private def _stripCommentsRespectingStrings(raw: String): String = {
    val sb = new StringBuilder(raw.length)
    var i  = 0
    var q: Char = 0
    while (i < raw.length) {
      val c = raw.charAt(i)
      if (q != 0) {
        sb.append(c)
        if (c == '\\' && i + 1 < raw.length) { sb.append(raw.charAt(i + 1)); i += 2 }
        else { if (c == q) q = 0; i += 1 }
      } else if (c == '"' || c == '\'') {
        q = c; sb.append(c); i += 1
      } else if (c == '/' && i + 1 < raw.length && raw.charAt(i + 1) == '*') {
        i += 2
        while (i + 1 < raw.length && !(raw.charAt(i) == '*' && raw.charAt(i + 1) == '/')) i += 1
        i += 2
      } else if (c == '/' && i + 1 < raw.length && raw.charAt(i + 1) == '/') {
        i += 2
        while (i < raw.length && raw.charAt(i) != '\n' && raw.charAt(i) != '\r') i += 1
      } else {
        sb.append(c); i += 1
      }
    }
    sb.toString()
  }

  private def _parseSimpleExpression(raw: String, span: FileSpan): Expression = {
    val trimmed = _stripCommentsRespectingStrings(raw).trim
    if (trimmed.isEmpty) return new NullExpression(span)

    // Boolean / null literals
    if (trimmed == "true") return new BooleanExpression(value = true, span)
    if (trimmed == "false") return new BooleanExpression(value = false, span)
    if (trimmed == "null") return new NullExpression(span)

    // Parent selector expression: bare `&` evaluates to the current selector.
    if (trimmed == "&") return SelectorExpression(span)

    // Quoted string. Only treat as a single string literal if the opening
    // quote's matching close is at the very end (i.e. the entire trimmed
    // text is one quoted token); otherwise something like
    // `"a" + "b"` would be misread as one big string.
    if (
      trimmed.length >= 2 &&
      (trimmed.charAt(0) == '"' || trimmed.charAt(0) == '\'') &&
      trimmed.charAt(trimmed.length - 1) == trimmed.charAt(0) && {
        val q  = trimmed.charAt(0)
        var k  = 1
        var ok = true
        boundary[Boolean] {
          while (k < trimmed.length - 1) {
            val ch = trimmed.charAt(k)
            if (ch == '\\' && k + 1 < trimmed.length - 1) k += 2
            else if (ch == q) {
              ok = false
              break(false)
            } else k += 1
          }
          ok
        }
      }
    ) {
      val inner = trimmed.substring(1, trimmed.length - 1)
      return StringExpression(_parseInterpolatedString(inner, span), hasQuotes = true)
    }

    // Hex color literal: `#RRGGBB`, `#RGB`, `#RRGGBBAA`, `#RGBA`.
    // Must NOT match `#{...}` interpolation — require hex digit after `#`.
    if (
      trimmed.length >= 4 && trimmed.charAt(0) == '#' &&
      trimmed.charAt(1) != '{' &&
      _tryParseHexColor(trimmed, span).isDefined
    ) {
      return _tryParseHexColor(trimmed, span).get
    }

    // Unquoted interpolation: `#{expr}`, possibly with surrounding literal
    // text (e.g. `#{$base * 2}px` or `prefix-#{$x}`). Parse via the shared
    // interpolated-string helper so embedded `#{...}` regions are evaluated
    // as expressions.
    if (trimmed.contains("#{")) {
      return StringExpression(_parseInterpolatedString(trimmed, span), hasQuotes = false)
    }

    // Variable reference: plain `$var`
    if (trimmed.startsWith("$")) {
      val plainName = trimmed.substring(1)
      if (plainName.nonEmpty && _allChars(plainName, (c: Char) => CharCode.isName(c.toInt))) {
        return VariableExpression(plainName.replace('_', '-'), span)
      }
    }
    // Namespaced variable: `ns.$var` — must be outside the `$` guard above
    // so that `mid.$primary`-style references are recognized.
    val nsDollarIdx = trimmed.indexOf(".$")
    if (nsDollarIdx > 0) {
      val ns     = trimmed.substring(0, nsDollarIdx)
      val nsName = trimmed.substring(nsDollarIdx + 2)
      if (
        _allChars(ns, (c: Char) => CharCode.isName(c.toInt)) &&
        nsName.nonEmpty && _allChars(nsName, (c: Char) => CharCode.isName(c.toInt))
      ) {
        if (_isPrivateMember(nsName))
          error(
            "Private members can't be accessed from outside their modules.",
            span
          )
        return VariableExpression(nsName.replace('_', '-'), span, Nullable(ns))
      }
    }

    // Parenthesized comma-separated list or map literal:
    //   `(a, b, c)`      → comma ListExpression
    //   `(k: v, k2: v2)` → MapExpression (if top-level `:` per element)
    //   `(1 2, 3 4)`     → comma list of space-lists
    // A single parenthesized element like `(a)` just recurses on the inner.
    if (trimmed.length >= 2 && trimmed.charAt(0) == '(' && trimmed.charAt(trimmed.length - 1) == ')') {
      // Confirm the outer parens are balanced as a single group.
      var pd            = 0
      var outerBalanced = true
      var i             = 0
      while (i < trimmed.length && outerBalanced) {
        val ch = trimmed.charAt(i)
        if (ch == '(') pd += 1
        else if (ch == ')') {
          pd -= 1
          if (pd == 0 && i != trimmed.length - 1) outerBalanced = false
        }
        i += 1
      }
      if (outerBalanced) {
        val inner = trimmed.substring(1, trimmed.length - 1).trim
        if (inner.isEmpty) {
          return ListExpression(Nil, ListSeparator.Undecided, span, hasBrackets = false)
        }
        val parts = _splitTopLevel(inner, ',').map(_.trim).filter(_.nonEmpty)
        // Detect a map literal: every top-level element must contain a top-level `:`.
        val isMap = parts.nonEmpty && parts.forall { p =>
          val colonSplit = _splitTopLevel(p, ':')
          colonSplit.length == 2
        }
        if (isMap) {
          val pairs = parts.map { p =>
            val kv = _splitTopLevel(p, ':')
            val k  = _parseSimpleExpression(kv(0).trim, span)
            val v  = _parseSimpleExpression(kv(1).trim, span)
            (k, v)
          }
          return MapExpression(pairs, span)
        }
        if (parts.length >= 2) {
          val elts = parts.map(p => _parseSimpleExpression(p, span))
          return ListExpression(elts, ListSeparator.Comma, span, hasBrackets = false)
        }
        // Single element: just recurse on the inner.
        return _parseSimpleExpression(inner, span)
      }
    }

    // Number literal with optional unit
    _tryParseNumber(trimmed, span) match {
      case Some(num) => return num
      case None      =>
    }

    // Special CSS functions (url, element, expression, progid:..., vendor-
    // prefixed element/expression/image-set, etc.) — pass the entire token
    // through as an unquoted string so the arguments are not evaluated as
    // Sass expressions. Must run before _tryParseFunctionCall and before the
    // unary-minus handler so that `-ms-element(...)` is not mis-parsed as
    // `-(ms-element(...))`.
    _trySpecialCssFunction(trimmed, span) match {
      case Some(s) => return s
      case None    =>
    }

    // Function call: identifier followed by (...) with matching closing paren at end
    _tryParseFunctionCall(trimmed, span) match {
      case Some(fn) => return fn
      case None     =>
    }

    // Unary `not <expr>`: parses the rest as an expression and wraps it.
    if (trimmed == "not") {
      // Bare `not` with no operand — fall through.
    } else if (trimmed.startsWith("not ") || trimmed.startsWith("not\t")) {
      val rest = trimmed.substring(3).trim
      if (rest.nonEmpty) {
        val operand = _parseSimpleExpression(rest, span)
        return UnaryOperationExpression(UnaryOperator.Not, operand, span)
      }
    }

    // Unary minus on a variable or function call: `-$x`, `-fn(...)`.
    // (Numbers like `-5px` are already handled by _tryParseNumber.)
    if (trimmed.length >= 2 && trimmed.charAt(0) == '-') {
      val rest = trimmed.substring(1).trim
      if (rest.startsWith("$") || _tryParseFunctionCall(rest, span).isDefined) {
        val operand = _parseSimpleExpression(rest, span)
        return UnaryOperationExpression(UnaryOperator.Minus, operand, span)
      }
    }

    // Space-separated tokens. If any top-level token is a bare arithmetic
    // operator (`+`, `-`, `*`, `/`, `%`), parse as a binary expression.
    val spaceSplit = _splitTopLevel(trimmed, ' ')
    if (spaceSplit.exists(t => _isOperatorToken(t))) {
      _parseBinaryOps(spaceSplit, span) match {
        case Some(expr) => return expr
        case None       =>
      }
    }
    // Tight-binding arithmetic: if the above failed, retry with a tokenizer
    // that splits on `+ - * /` even without surrounding spaces (e.g.
    // `10px+5px`, `$a*2`, `10px-5px`). Unary `-` at the start or directly
    // after an operator stays attached to its operand. Identifiers consume
    // hyphens greedily (so `a-b` stays a single token).
    val tightTokens = _tokenizeArithmetic(trimmed)
    if (tightTokens.nonEmpty && tightTokens.exists(t => _isOperatorToken(t))) {
      _parseBinaryOps(tightTokens, span) match {
        case Some(expr) => return expr
        case None       =>
      }
    }
    if (spaceSplit.length >= 2) {
      val parts = spaceSplit.map(p => _parseSimpleExpression(p, span))
      return ListExpression(parts, ListSeparator.Space, span, hasBrackets = false)
    }

    // Named color keyword: bare identifier matching a CSS named color.
    // Only resolved when the token is a pure identifier (no hyphens that
    // could collide with other unquoted-string usages are special-cased
    // by the map itself — `transparent`, `red`, etc. are all valid CSS
    // color keywords and Sass resolves them to SassColor at parse time).
    if (_isPlainIdentifier(trimmed)) {
      ColorNames.colorsByName.get(trimmed.toLowerCase) match {
        case Some(color) => return ColorExpression(color, span)
        case None        =>
      }
    }

    // Fallback: unquoted string expression
    StringExpression(Interpolation.plain(trimmed, span), hasQuotes = false)
  }

  /** True if [s] is a plain CSS identifier: starts with a letter or `-`, contains only name chars, no dots/parens. */
  private def _isPlainIdentifier(s: String): Boolean = {
    if (s.isEmpty) return false
    val c0 = s.charAt(0)
    if (!(c0.isLetter || c0 == '-' || c0 == '_')) return false
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (!(c.isLetterOrDigit || c == '-' || c == '_')) return false
      i += 1
    }
    true
  }

  /** Tries to parse [s] as a hex color literal (`#RGB`, `#RGBA`, `#RRGGBB`, `#RRGGBBAA`). */
  private def _tryParseHexColor(s: String, span: FileSpan): Option[ColorExpression] = {
    if (s.isEmpty || s.charAt(0) != '#') return None
    val hex = s.substring(1)
    val len = hex.length
    if (len != 3 && len != 4 && len != 6 && len != 8) return None
    var i = 0
    while (i < len) {
      val c  = hex.charAt(i)
      val ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
      if (!ok) return None
      i += 1
    }
    def h2(a: Char, b: Char): Int = Integer.parseInt(s"$a$b", 16)
    val (r, g, b, aOpt) = len match {
      case 3 => (h2(hex.charAt(0), hex.charAt(0)), h2(hex.charAt(1), hex.charAt(1)), h2(hex.charAt(2), hex.charAt(2)), None)
      case 4 =>
        (
          h2(hex.charAt(0), hex.charAt(0)),
          h2(hex.charAt(1), hex.charAt(1)),
          h2(hex.charAt(2), hex.charAt(2)),
          Some(h2(hex.charAt(3), hex.charAt(3)) / 255.0)
        )
      case 6 => (h2(hex.charAt(0), hex.charAt(1)), h2(hex.charAt(2), hex.charAt(3)), h2(hex.charAt(4), hex.charAt(5)), None)
      case 8 =>
        (
          h2(hex.charAt(0), hex.charAt(1)),
          h2(hex.charAt(2), hex.charAt(3)),
          h2(hex.charAt(4), hex.charAt(5)),
          Some(h2(hex.charAt(6), hex.charAt(7)) / 255.0)
        )
    }
    val alpha: Nullable[Double] = aOpt match {
      case Some(a) => Nullable(a)
      case None    => Nullable(1.0)
    }
    // Preserve original hex format for 3- and 6-digit colors (no alpha).
    // Don't emit 4- or 8-digit hex as hex since not well-supported in browsers.
    val format: Nullable[ssg.sass.value.ColorFormat] =
      if (aOpt.isEmpty) Nullable(new ssg.sass.value.SpanColorFormat(s))
      else Nullable.Null
    val color = SassColor.rgbInternal(Nullable(r.toDouble), Nullable(g.toDouble), Nullable(b.toDouble), alpha, format)
    Some(ColorExpression(color, span))
  }

  /** Splits [s] at top-level occurrences of [sep] (ignoring separators inside matched parens/brackets/quotes).
    */
  private def _splitTopLevel(s: String, sep: Char): List[String] = {
    val result = scala.collection.mutable.ListBuffer.empty[String]
    val buf    = new StringBuilder()
    var depth  = 0
    var inQuote: Char = 0
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (inQuote != 0) {
        buf.append(c)
        if (c == inQuote) inQuote = 0
        else if (c == '\\' && i + 1 < s.length) {
          i += 1
          buf.append(s.charAt(i))
        }
      } else if (c == '"' || c == '\'') {
        inQuote = c
        buf.append(c)
      } else if (c == '(' || c == '[') {
        depth += 1
        buf.append(c)
      } else if (c == ')' || c == ']') {
        depth -= 1
        buf.append(c)
      } else if (depth == 0 && c == sep) {
        val chunk = buf.toString().trim
        if (chunk.nonEmpty) result += chunk
        buf.clear()
      } else {
        buf.append(c)
      }
      i += 1
    }
    val last = buf.toString().trim
    if (last.nonEmpty) result += last
    result.toList
  }

  /** Tokenizes [s] into operator-aware tokens. Recognizes numbers with optional unit, identifiers (with embedded hyphens), variables (`$name`), quoted strings, bracketed groups (`(...)` / `[...]`),
    * and the operators `+ - * / %`. A `-` is treated as part of a numeric literal when it appears at the start of the expression or directly after another operator; otherwise it is a binary operator
    * token. Returns an empty list if tokenization fails (e.g. unmatched brackets / unknown characters).
    */
  private def _tokenizeArithmetic(s: String): List[String] = {
    boundary[List[String]] {
      val tokens = scala.collection.mutable.ListBuffer.empty[String]
      var i      = 0
      val n      = s.length
      def lastIsOperator: Boolean = tokens.isEmpty || _isOperatorToken(tokens.last)
      while (i < n) {
        val c = s.charAt(i)
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
          i += 1
        } else if (c == '+' || c == '*' || c == '/' || c == '%') {
          tokens += c.toString
          i += 1
        } else if (c == '=' && i + 1 < n && s.charAt(i + 1) == '=') {
          tokens += "=="
          i += 2
        } else if (c == '!' && i + 1 < n && s.charAt(i + 1) == '=') {
          tokens += "!="
          i += 2
        } else if (c == '<' || c == '>') {
          if (i + 1 < n && s.charAt(i + 1) == '=') {
            tokens += s.substring(i, i + 2)
            i += 2
          } else {
            tokens += c.toString
            i += 1
          }
        } else if (c == '-') {
          // Binary `-` unless we're at the start or the previous token is
          // itself an operator (meaning this `-` starts a new operand).
          if (lastIsOperator) {
            // Fall through to operand reading with the `-` included.
            val start = i
            i += 1
            if (i < n && (CharCode.isDigit(s.charAt(i).toInt) || s.charAt(i) == '.')) {
              // Negative number literal.
              while (i < n && CharCode.isDigit(s.charAt(i).toInt)) i += 1
              if (i < n && s.charAt(i) == '.') {
                i += 1
                while (i < n && CharCode.isDigit(s.charAt(i).toInt)) i += 1
              }
              // Optional unit / `%`.
              if (i < n && s.charAt(i) == '%') {
                i += 1
              } else {
                while (i < n && CharCode.isName(s.charAt(i).toInt)) i += 1
              }
              tokens += s.substring(start, i)
            } else if (i < n && s.charAt(i) == '$') {
              // Negative variable reference: emit unary token verbatim.
              i += 1
              while (i < n && CharCode.isName(s.charAt(i).toInt)) i += 1
              tokens += s.substring(start, i)
            } else {
              // Bare `-` at start with no operand — give up.
              break(Nil)
            }
          } else {
            tokens += "-"
            i += 1
          }
        } else if (CharCode.isDigit(c.toInt) || c == '.') {
          val start = i
          while (i < n && CharCode.isDigit(s.charAt(i).toInt)) i += 1
          if (i < n && s.charAt(i) == '.') {
            i += 1
            while (i < n && CharCode.isDigit(s.charAt(i).toInt)) i += 1
          }
          // Optional unit / percent. Units are letters only — a `-` after
          // a numeric literal is always a binary operator.
          if (i < n && s.charAt(i) == '%') {
            i += 1
          } else {
            while (i < n && s.charAt(i).isLetter) i += 1
          }
          if (i == start) break(Nil)
          tokens += s.substring(start, i)
        } else if (c == '$') {
          val start = i
          i += 1
          while (i < n && CharCode.isName(s.charAt(i).toInt)) i += 1
          tokens += s.substring(start, i)
        } else if (CharCode.isNameStart(c.toInt)) {
          // Identifier — may be a function call `name(...)`, a namespaced
          // variable `ns.$x`, or a namespaced function call. Consume the
          // identifier then any bracket group that immediately follows.
          val start = i
          while (i < n && CharCode.isName(s.charAt(i).toInt)) i += 1
          // Namespaced `ns.$x` or `ns.name(...)`.
          if (i < n && s.charAt(i) == '.') {
            i += 1
            if (i < n && s.charAt(i) == '$') {
              i += 1
              while (i < n && CharCode.isName(s.charAt(i).toInt)) i += 1
            } else {
              while (i < n && CharCode.isName(s.charAt(i).toInt)) i += 1
            }
          }
          if (i < n && s.charAt(i) == '(') {
            var depth   = 0
            val matched = boundary[Boolean] {
              while (i < n) {
                val cc = s.charAt(i)
                if (cc == '(') depth += 1
                else if (cc == ')') {
                  depth -= 1
                  if (depth == 0) { i += 1; break(true) }
                }
                i += 1
              }
              false
            }
            if (!matched) break(Nil)
          }
          tokens += s.substring(start, i)
        } else if (c == '(' || c == '[') {
          val open    = c
          val close   = if (open == '(') ')' else ']'
          val start   = i
          var depth   = 0
          val matched = boundary[Boolean] {
            while (i < n) {
              val cc = s.charAt(i)
              if (cc == open) depth += 1
              else if (cc == close) {
                depth -= 1
                if (depth == 0) { i += 1; break(true) }
              }
              i += 1
            }
            false
          }
          if (!matched) break(Nil)
          tokens += s.substring(start, i)
        } else if (c == '"' || c == '\'') {
          val quote = c
          val start = i
          i += 1
          while (i < n && s.charAt(i) != quote)
            if (s.charAt(i) == '\\' && i + 1 < n) i += 2
            else i += 1
          if (i >= n) break(Nil)
          i += 1 // closing quote
          tokens += s.substring(start, i)
        } else {
          // Unrecognized character — bail out.
          break(Nil)
        }
      }
      tokens.toList
    }
  }

  /** True if [name] is a special CSS function whose arguments must be preserved verbatim rather than evaluated as Sass expressions. Mirrors dart-sass `_trySpecialFunction` in
    * `lib/src/parse/stylesheet.dart`. Covers `url`, `element`, `expression`, legacy IE `progid:...` filters, and vendor-prefixed variants of `element`, `expression`, `calc`, and `image-set`. Note
    * `calc`/`min`/`max`/`clamp` (unprefixed) are intentionally NOT handled here — they flow through the normal function-call path so dart-sass-style simplification runs.
    */
  private def _isSpecialCssFunction(name: String): Boolean = {
    if (name.isEmpty) return false
    val lower = name.toLowerCase
    if (lower == "url" || lower == "element" || lower == "expression" || lower == "type") return true
    if (lower.startsWith("progid:")) return true
    // Vendor prefix: `-<prefix>-<tail>` where tail is one of the special names.
    if (lower.length > 1 && lower.charAt(0) == '-') {
      val rest = lower.substring(1)
      val dash = rest.indexOf('-')
      if (dash > 0 && dash < rest.length - 1) {
        val tail = rest.substring(dash + 1)
        if (
          tail == "element" || tail == "expression" ||
          tail == "calc" || tail == "image-set"
        ) return true
      }
    }
    false
  }

  /** Attempts to match [raw] as a special CSS function call and preserve it verbatim as an unquoted string. Returns `None` if [raw] is not of the form `name(...)` (with matching closing paren) or
    * [name] is not in the special set.
    */
  private def _trySpecialCssFunction(raw: String, span: FileSpan): Option[Expression] = {
    if (!raw.endsWith(")")) return None
    val parenIdx = raw.indexOf('(')
    if (parenIdx <= 0) return None
    // Verify the opening `(` at parenIdx matches the final `)`.
    var depth   = 0
    var i       = parenIdx
    var matched = false
    while (i < raw.length && !matched) {
      val c = raw.charAt(i)
      if (c == '(') depth += 1
      else if (c == ')') {
        depth -= 1
        if (depth == 0) matched = i == raw.length - 1
      }
      i += 1
    }
    if (!matched) return None
    val head = raw.substring(0, parenIdx)
    if (!_isSpecialCssFunction(head)) return None
    // dart-sass normalizes the reserved function name to lowercase on output
    // (sass-spec: directives/function/name.hrx!special/*/uppercase).
    val normalized = head.toLowerCase + raw.substring(parenIdx)
    Some(StringExpression(Interpolation.plain(normalized, span), hasQuotes = false))
  }

  /** Attempts to parse a function call `name(args)`. The bare `if(...)` three-argument call is recognized as a [[LegacyIfExpression]] so that the unchosen branch is never evaluated; everything else
    * becomes a regular [[FunctionExpression]].
    */
  private def _tryParseFunctionCall(raw: String, span: FileSpan): Option[Expression] = {
    val parenIdx = raw.indexOf('(')
    if (parenIdx <= 0 || !raw.endsWith(")")) return None
    val head = raw.substring(0, parenIdx)
    // Head is either `name` or `namespace.name`
    val dotIdx = head.indexOf('.')
    val (namespace, name): (Nullable[String], String) =
      if (dotIdx > 0 && dotIdx < head.length - 1) {
        val ns = head.substring(0, dotIdx)
        val n  = head.substring(dotIdx + 1)
        if (
          _allChars(ns, (c: Char) => CharCode.isName(c.toInt)) &&
          _allChars(n, (c: Char) => CharCode.isName(c.toInt))
        ) {
          (Nullable(ns), n)
        } else {
          return None
        }
      } else {
        if (!_allChars(head, (c: Char) => CharCode.isName(c.toInt))) return None
        (Nullable.empty[String], head)
      }
    // Special-case: url() — passes through as an unquoted string.
    if (namespace.isEmpty && name == "url") return None
    // Namespaced private member access is a hard syntax error. Matches
    // dart-sass `_assertPublic` in `lib/src/parse/stylesheet.dart`.
    if (namespace.isDefined && _isPrivateMember(name)) {
      error(
        "Private members can't be accessed from outside their modules.",
        span
      )
    }
    // Custom-ident function calls (`--name(...)`) are preserved verbatim as
    // plain CSS rather than evaluated, matching dart-sass's handling of
    // CSS custom functions. See sass-spec
    // `directives/function/name.hrx!custom_ident/call`.
    if (namespace.isEmpty && name.startsWith("--")) {
      return Some(StringExpression(Interpolation.plain(raw, span), hasQuotes = false))
    }

    val argsText = raw.substring(parenIdx + 1, raw.length - 1).trim
    // Modern CSS color-function syntax: `rgb/rgba/hsl/hsla/hwb/lab/lch/oklab/
    // oklch/color` accept space-separated channels with optional `/ <alpha>`
    // trailing, in addition to the legacy comma-separated form. When the head
    // name is in the allowlist and the arg text has no top-level commas,
    // reparse the arguments as whitespace-separated with a `/` alpha split.
    val isColorFn = namespace.isEmpty && (
      name == "rgb" || name == "rgba" || name == "hsl" || name == "hsla" ||
        name == "hwb" || name == "lab" || name == "lch" || name == "oklab" ||
        name == "oklch" || name == "color"
    )
    val commaSplit = if (argsText.isEmpty) Nil else _splitTopLevel(argsText, ',')
    // Detect Sass named arguments (`$name: value`). When present, skip
    // the modern CSS space-split — named args must be kept as a single
    // unit so the evaluator can bind them by name (e.g. `$channels`).
    val hasNamedArg = commaSplit.exists { part =>
      val t = part.trim
      t.startsWith("$") && {
        var k = 1
        while (k < t.length && CharCode.isName(t.charAt(k).toInt)) k += 1
        k > 1 && k < t.length && t.charAt(k) == ':' && (k + 1 >= t.length || t.charAt(k + 1) != ':')
      }
    }
    val rawArgs: List[String] =
      if (argsText.isEmpty) Nil
      else if (isColorFn && commaSplit.length <= 1 && !hasNamedArg) {
        // No top-level comma and no named args: parse as modern
        // space-separated channels with optional `/ <alpha>` trailing.
        val normalized = argsText.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
        val slashParts = _splitTopLevel(normalized, '/')
        if (slashParts.length == 2) {
          val channels = _splitTopLevel(slashParts(0), ' ').filter(_.nonEmpty)
          channels :+ slashParts(1).trim
        } else if (slashParts.length == 1) {
          _splitTopLevel(normalized, ' ').filter(_.nonEmpty)
        } else {
          commaSplit
        }
      } else {
        commaSplit
      }
    val positional = scala.collection.mutable.ListBuffer.empty[Expression]
    val named      = scala.collection.mutable.LinkedHashMap.empty[String, Expression]
    // Rest (`$list...`) and keyword-rest (`$kwargs...`) arguments are detected
    // by a trailing `...` on the last (or second-to-last) raw arg text. Any
    // argument that ends with `...` strips the marker and becomes either
    // [[rest]] (first such occurrence) or [[keywordRest]] (second occurrence,
    // which by Sass rules must follow immediately after [[rest]]).
    var restExpr:        Nullable[Expression] = Nullable.empty
    var keywordRestExpr: Nullable[Expression] = Nullable.empty
    val n   = rawArgs.length
    var idx = 0
    for (a <- rawArgs) {
      // Detect a named argument `$name: value`. We match `$` + identifier
      // + `:` (but not `::`, which would be a pseudo-element).
      val trimmed = a.trim
      var keyName:   String = ""
      var valueText: String = trimmed
      if (trimmed.startsWith("$")) {
        var k = 1
        while (k < trimmed.length && CharCode.isName(trimmed.charAt(k).toInt)) k += 1
        if (
          k > 1 && k < trimmed.length && trimmed.charAt(k) == ':' &&
          (k + 1 >= trimmed.length || trimmed.charAt(k + 1) != ':')
        ) {
          keyName = trimmed.substring(1, k).replace('_', '-')
          valueText = trimmed.substring(k + 1).trim
        }
      }
      val isLastTwo = idx >= n - 2
      val isRest    = isLastTwo && keyName.isEmpty && valueText.endsWith("...")
      if (isRest) {
        val stripped = valueText.substring(0, valueText.length - 3).trim
        val expr     = _parseSimpleExpression(stripped, span)
        if (restExpr.isEmpty) restExpr = Nullable(expr)
        else keywordRestExpr = Nullable(expr)
      } else {
        val valueExpr = _parseSimpleExpression(valueText, span)
        if (keyName.nonEmpty) named.update(keyName, valueExpr)
        else positional += valueExpr
      }
      idx += 1
    }

    val arguments = new ArgumentList(
      positional.toList,
      named.toMap,
      Map.empty,
      span,
      restExpr,
      keywordRestExpr
    )
    // Lazy `if($cond, $t, $f)` — only the chosen branch is evaluated.
    if (namespace.isEmpty && name == "if" && positional.length == 3 && named.isEmpty) {
      Some(LegacyIfExpression(arguments, span))
    } else {
      Some(FunctionExpression(name, arguments, span, namespace))
    }
  }

  /** Attempts to parse a number literal with optional unit. */
  private def _tryParseNumber(raw: String, span: FileSpan): Option[NumberExpression] = {
    var i = 0
    // Sign
    if (i < raw.length && (raw.charAt(i) == '+' || raw.charAt(i) == '-')) i += 1
    val digitStart = i
    while (i < raw.length && CharCode.isDigit(raw.charAt(i).toInt)) i += 1
    // Fraction
    if (i < raw.length && raw.charAt(i) == '.') {
      i += 1
      while (i < raw.length && CharCode.isDigit(raw.charAt(i).toInt)) i += 1
    }
    if (i == digitStart) return None
    // Scientific exponent: e|E [+|-] digits
    if (i < raw.length && (raw.charAt(i) == 'e' || raw.charAt(i) == 'E')) {
      var j = i + 1
      if (j < raw.length && (raw.charAt(j) == '+' || raw.charAt(j) == '-')) j += 1
      val expDigitStart = j
      while (j < raw.length && CharCode.isDigit(raw.charAt(j).toInt)) j += 1
      if (j > expDigitStart) i = j
    }

    val numStr = raw.substring(0, i)
    val value  =
      try numStr.toDouble
      catch { case _: NumberFormatException => return None }

    val unit = raw.substring(i).trim
    if (unit.isEmpty) Some(NumberExpression(value, span, Nullable.empty))
    else if (unit == "%") Some(NumberExpression(value, span, Nullable("%")))
    else if (_allChars(unit, (c: Char) => c.isLetter)) Some(NumberExpression(value, span, Nullable(unit)))
    else None
  }

  /** Reads the raw value portion of a CSS custom property declaration (`--foo: <raw>;`). Everything up to the terminating `;` or the closing `}` of the enclosing block is collected verbatim, with one
    * exception: `#{...}` interpolation segments are parsed as expressions so `--foo: #{$x}` still evaluates. Balanced parens/brackets/braces and string literals are respected so `;`/`}` inside them
    * do not end the value.
    */
  @scala.annotation.nowarn("msg=unused private member")
  private def _readCustomPropertyValue(
    rawStart: ssg.sass.util.LineScannerState
  ): Interpolation = {
    val contents = scala.collection.mutable.ListBuffer.empty[Any]
    val spans    = scala.collection.mutable.ListBuffer.empty[Nullable[FileSpan]]
    val literal  = new StringBuilder()
    var pDepth   = 0
    var inQuote: Int = 0
    boundary {
      while (!scanner.isDone) {
        val ch = scanner.peekChar()
        if (ch < 0) break(())
        if (inQuote > 0) {
          if (ch == CharCode.$backslash) {
            literal.append(scanner.readChar().toChar)
            if (!scanner.isDone) literal.append(scanner.readChar().toChar)
          } else {
            if (ch == inQuote) inQuote = 0
            literal.append(scanner.readChar().toChar)
          }
        } else if (ch == CharCode.$double_quote || ch == CharCode.$single_quote) {
          inQuote = ch
          literal.append(scanner.readChar().toChar)
        } else if (ch == CharCode.$hash && scanner.peekChar(1) == CharCode.$lbrace) {
          if (literal.nonEmpty) {
            contents += literal.toString()
            spans += Nullable.empty
            literal.clear()
          }
          val _       = scanner.readChar() // '#'
          val _       = scanner.readChar() // '{'
          val exprBuf = new StringBuilder()
          var depth   = 1
          boundary {
            while (!scanner.isDone) {
              val cc = scanner.peekChar()
              if (cc < 0) break(())
              if (cc == CharCode.$lbrace) depth += 1
              else if (cc == CharCode.$rbrace) {
                depth -= 1
                if (depth == 0) {
                  val _ = scanner.readChar()
                  break(())
                }
              }
              exprBuf.append(scanner.readChar().toChar)
            }
          }
          val exprText = exprBuf.toString().trim
          val exprSpan = spanFrom(rawStart)
          if (exprText.isEmpty) {
            contents += StringExpression(Interpolation.plain("", exprSpan), hasQuotes = false)
          } else {
            contents += _parseSimpleExpression(exprText, exprSpan)
          }
          spans += Nullable(exprSpan)
        } else if (pDepth == 0 && (ch == CharCode.$semicolon || ch == CharCode.$rbrace)) {
          break(())
        } else if (ch == CharCode.$lparen || ch == CharCode.$lbrace || ch == CharCode.$lbracket) {
          pDepth += 1
          literal.append(scanner.readChar().toChar)
        } else if (ch == CharCode.$rparen || ch == CharCode.$rbrace || ch == CharCode.$rbracket) {
          if (pDepth > 0) pDepth -= 1
          literal.append(scanner.readChar().toChar)
        } else {
          literal.append(scanner.readChar().toChar)
        }
      }
    }
    if (literal.nonEmpty || contents.isEmpty) {
      contents += literal.toString().replaceAll("\\s+$", "")
      spans += Nullable.empty
    } else {
      // Trim trailing whitespace from the final literal chunk if any.
      contents.lastOption match {
        case Some(s: String) =>
          contents(contents.length - 1) = s.replaceAll("\\s+$", "")
        case _ => ()
      }
    }
    new Interpolation(contents.toList, spans.toList, spanFrom(rawStart))
  }

  /** Reads an interpolated property name from the scanner: a sequence of identifier characters and `#{...}` segments, stopping at the first character that ends the name (whitespace, `:`, `;`, `{`,
    * `}`). The resulting [[Interpolation]] preserves literal chunks and evaluates each interpolation via [[_parseSimpleExpression]] on its contents.
    */
  @scala.annotation.nowarn("msg=unused private member")
  private def _readInterpolatedName(): Interpolation = {
    val start    = scanner.state
    val contents = scala.collection.mutable.ListBuffer.empty[Any]
    val spans    = scala.collection.mutable.ListBuffer.empty[Nullable[FileSpan]]
    val literal  = new StringBuilder()
    boundary {
      while (!scanner.isDone) {
        val ch = scanner.peekChar()
        if (ch < 0) break(())
        if (
          ch == CharCode.$colon || ch == CharCode.$semicolon ||
          ch == CharCode.$lbrace || ch == CharCode.$rbrace ||
          ch == CharCode.$space || ch == CharCode.$tab ||
          ch == CharCode.$lf || ch == CharCode.$cr
        ) {
          break(())
        } else if (ch == CharCode.$hash && scanner.peekChar(1) == CharCode.$lbrace) {
          if (literal.nonEmpty) {
            contents += literal.toString()
            spans += Nullable.empty
            literal.clear()
          }
          val _       = scanner.readChar() // '#'
          val _       = scanner.readChar() // '{'
          val exprBuf = new StringBuilder()
          var depth   = 1
          boundary {
            while (!scanner.isDone) {
              val cc = scanner.peekChar()
              if (cc < 0) break(())
              if (cc == CharCode.$lbrace) depth += 1
              else if (cc == CharCode.$rbrace) {
                depth -= 1
                if (depth == 0) {
                  val _ = scanner.readChar()
                  break(())
                }
              }
              exprBuf.append(scanner.readChar().toChar)
            }
          }
          val exprText = exprBuf.toString().trim
          val exprSpan = spanFrom(start)
          if (exprText.isEmpty) {
            contents += StringExpression(Interpolation.plain("", exprSpan), hasQuotes = false)
          } else {
            contents += _parseSimpleExpression(exprText, exprSpan)
          }
          spans += Nullable(exprSpan)
        } else {
          literal.append(scanner.readChar().toChar)
        }
      }
    }
    if (literal.nonEmpty || contents.isEmpty) {
      contents += literal.toString()
      spans += Nullable.empty
    }
    new Interpolation(contents.toList, spans.toList, spanFrom(start))
  }

  /** Parses [raw] into an [[Interpolation]], detecting `#{...}` segments and treating the content of each as an expression (recursively parsed via [[_parseSimpleExpression]]). Literal text segments
    * become [String] elements; interpolated regions become [Expression] elements. Matching braces inside `#{...}` are balanced.
    */
  protected def _parseInterpolatedString(raw: String, span: FileSpan): Interpolation = {
    val contents = scala.collection.mutable.ListBuffer.empty[Any]
    val spans    = scala.collection.mutable.ListBuffer.empty[Nullable[FileSpan]]
    val literal  = new StringBuilder()
    var i        = 0
    val n        = raw.length
    while (i < n) {
      val c = raw.charAt(i)
      if (c == '#' && i + 1 < n && raw.charAt(i + 1) == '{') {
        // Flush any accumulated literal text (only if nonempty — adjacent
        // Expressions are allowed in Interpolation contents, only adjacent
        // Strings are forbidden).
        if (literal.nonEmpty) {
          contents += literal.toString()
          spans += Nullable.empty
          literal.clear()
        }
        // Find matching closing brace, balancing nested braces.
        var j     = i + 2
        var depth = 1
        boundary {
          while (j < n) {
            val cc = raw.charAt(j)
            if (cc == '{') depth += 1
            else if (cc == '}') {
              depth -= 1
              if (depth == 0) break(())
            }
            j += 1
          }
        }
        if (depth != 0) scanner.error("Expected '}'.")
        val exprText = raw.substring(i + 2, j).trim
        if (exprText.isEmpty) {
          // Empty interpolation #{} — emit an empty unquoted string expression
          contents += StringExpression(Interpolation.plain("", span), hasQuotes = false)
        } else {
          contents += _parseSimpleExpression(exprText, span)
        }
        spans += Nullable(span)
        i = j + 1
      } else {
        literal.append(c)
        i += 1
      }
    }
    // Flush trailing literal, or ensure contents is non-empty with a string.
    if (literal.nonEmpty || contents.isEmpty) {
      contents += literal.toString()
      spans += Nullable.empty
    }
    new Interpolation(contents.toList, spans.toList, span)
  }

  /** Returns true if [t] is a bare arithmetic / comparison / logical operator token. */
  private def _isOperatorToken(t: String): Boolean =
    t == "+" || t == "-" || t == "*" || t == "/" || t == "%" ||
      t == "==" || t == "!=" ||
      t == "<" || t == "<=" || t == ">" || t == ">=" ||
      t == "and" || t == "or"

  /** Returns the [BinaryOperator] for an operator token, or `None`. */
  private def _binaryOpFor(t: String): Option[BinaryOperator] = t match {
    case "+"   => Some(BinaryOperator.Plus)
    case "-"   => Some(BinaryOperator.Minus)
    case "*"   => Some(BinaryOperator.Times)
    case "/"   => Some(BinaryOperator.DividedBy)
    case "%"   => Some(BinaryOperator.Modulo)
    case "=="  => Some(BinaryOperator.Equals)
    case "!="  => Some(BinaryOperator.NotEquals)
    case "<"   => Some(BinaryOperator.LessThan)
    case "<="  => Some(BinaryOperator.LessThanOrEquals)
    case ">"   => Some(BinaryOperator.GreaterThan)
    case ">="  => Some(BinaryOperator.GreaterThanOrEquals)
    case "and" => Some(BinaryOperator.And)
    case "or"  => Some(BinaryOperator.Or)
    case _     => None
  }

  /** Parses a sequence of whitespace-separated tokens as a left-associative binary expression using operator precedence. Returns `None` if the tokens don't form a valid operator expression (e.g. two
    * operands in a row with no operator between).
    */
  private def _parseBinaryOps(tokens: List[String], span: FileSpan): Option[Expression] =
    boundary[Option[Expression]] {
      // Validate alternating operand/operator/operand/.../operand pattern.
      if (tokens.isEmpty) break(None)
      if (_isOperatorToken(tokens.head)) break(None)
      if (_isOperatorToken(tokens.last)) break(None)
      var i = 0
      while (i < tokens.length) {
        val expectOperator = i % 2 == 1
        val tok            = tokens(i)
        if (expectOperator != _isOperatorToken(tok)) break(None)
        i += 1
      }

      // Shunting-yard: build left-associative tree honoring precedence.
      val output = scala.collection.mutable.ArrayBuffer.empty[Expression]
      val ops    = scala.collection.mutable.ArrayBuffer.empty[BinaryOperator]
      def reduce(): Unit = {
        val r  = output.remove(output.length - 1)
        val l  = output.remove(output.length - 1)
        val op = ops.remove(ops.length - 1)
        output += BinaryOperationExpression(op, l, r)
      }
      output += _parseSimpleExpression(tokens.head, span)
      var j = 1
      while (j + 1 < tokens.length) {
        val opTok = tokens(j)
        val rhs   = tokens(j + 1)
        val op    = _binaryOpFor(opTok) match {
          case Some(o) => o
          case None    => break(None)
        }
        while (ops.nonEmpty && ops.last.precedence >= op.precedence) reduce()
        ops += op
        output += _parseSimpleExpression(rhs, span)
        j += 2
      }
      while (ops.nonEmpty) reduce()
      if (output.length == 1) Some(output.head) else None
    }

  /** Helper: returns true if every character of [s] satisfies [p]. Explicit loop to avoid Nullable implicit conversion hijacking String.forall.
    */
  private def _allChars(s: String, p: Char => Boolean): Boolean = {
    var i = 0
    while (i < s.length) {
      if (!p(s.charAt(i))) return false
      i += 1
    }
    true
  }

  /** Parses the contents as a single expression, returning the expression and any warnings encountered.
    */
  def parseExpression(): (Expression, List[ParseTimeWarning]) = wrapSpanFormatException { () =>
    whitespace(consumeNewlines = true)
    val expr = _rdExpression()
    whitespace(consumeNewlines = true)
    scanner.expectDone()
    (expr, warnings.toList)
  }

  /** Parses the contents as a single number literal. */
  def parseNumber(): SassNumber = wrapSpanFormatException { () =>
    whitespace(consumeNewlines = true)
    val start   = scanner.state
    val numExpr = {
      val buf = new StringBuilder()
      while (!scanner.isDone && !CharCode.isWhitespace(scanner.peekChar()))
        buf.append(scanner.readChar().toChar)
      _tryParseNumber(buf.toString(), spanFrom(start)).getOrElse(scanner.error("Expected number."))
    }
    whitespace(consumeNewlines = true)
    scanner.expectDone()
    numExpr.unit.fold(SassNumber(numExpr.value))(u => SassNumber(numExpr.value, u))
  }

  /** Parses the contents as a single variable declaration. */
  def parseVariableDeclaration(): (VariableDeclaration, List[ParseTimeWarning]) =
    wrapSpanFormatException { () =>
      whitespace(consumeNewlines = true)
      val decl = _variableDeclaration()
      whitespace(consumeNewlines = true)
      scanner.expectDone()
      (decl, warnings.toList)
    }

  /** Parses the contents as a single `@use` rule.
    *
    * dart-sass: `parseUseRule` (stylesheet.dart:156-165).
    */
  def parseUseRule(): (UseRule, List[ParseTimeWarning]) =
    wrapSpanFormatException { () =>
      val start = scanner.state
      scanner.expectChar(CharCode.$at)
      expectIdentifier("use")
      whitespace(consumeNewlines = true)
      val rule = _useRule(start)
      scanner.expectDone()
      (rule, warnings.toList)
    }

  /** Parses a function signature of the format allowed by Node Sass's functions option and returns its name and parameter list.
    *
    * If [requireParens] is `false`, this allows parentheses to be omitted.
    */
  def parseSignature(requireParens: Boolean = true): (String, ParameterList) =
    wrapSpanFormatException { () =>
      val name   = identifier(normalize = true)
      val params =
        if (requireParens || scanner.peekChar() == CharCode.$lparen)
          _parseParameterList(scanner.state)
        else
          ParameterList.empty(scanner.emptySpan)
      scanner.expectDone()
      (name, params)
    }

  // ---------------------------------------------------------------------------
  // Abstract hooks overridden by SCSS / Sass / CSS parsers
  // ---------------------------------------------------------------------------

  /** Consumes an interpolation for the selector portion of a style rule. */
  protected def styleRuleSelector(): Interpolation

  /** Asserts that a statement separator was consumed. */
  protected def expectStatementSeparator(name: Nullable[String] = Nullable.Null): Unit

  /** Returns whether the scanner is at the end of a statement. */
  protected def atEndOfStatement(): Boolean

  /** Returns whether the scanner is looking at the start of a child block. */
  protected def lookingAtChildren(): Boolean

  /** Consumes an `@else` clause at the given indentation. */
  protected def scanElse(ifIndentation: Int): Boolean

  /** Consumes a child block. */
  protected def children(child: () => Statement): List[Statement]

  /** Consumes a sequence of statements. */
  protected def statements(statement: () => Nullable[Statement]): List[Statement]

  // ===========================================================================
  // Recursive-descent expression parser (stage 1 scaffold)
  // ===========================================================================
  //
  // These `_rd*` methods are a new, true recursive-descent port of the
  // dart-sass `_expression` / `_singleExpression` machinery in
  // `lib/src/parse/stylesheet.dart` (around lines 1971-2712). They consume
  // directly from `scanner` rather than pre-collecting raw text like the
  // existing `_expression` + `_parseSimpleExpression` pipeline.
  //
  // Stage 1 goals:
  //   - Port the control-flow skeleton: `_expression` loop with comma/space
  //     list accumulation, operator precedence climbing via an operator
  //     stack, unary +/-/not, parenthesized expressions, numbers, variables,
  //     identifier/function calls with namespace.
  //   - Keep the AST sane on simple inputs (numbers, identifiers, variables,
  //     strings, function calls, arithmetic, comparison, lists).
  //   - NOT wired from any call site yet. Live alongside the text-based
  //     parser. Call sites will be migrated one at a time in stage 2+.
  //
  // Stage 1 limitations (text fallbacks where full AST construction is incomplete):
  //   - `#{...}` interpolation inside expressions is handled by capturing
  //     the raw text and delegating to `_parseInterpolatedString`.
  //   - Quoted strings with embedded interpolation use `Interpolation.plain`
  //     (the interpolation regions are evaluated when the text-based parser
  //     delegates to `_parseInterpolatedString`).
  //   - `@supports` / calc-style parsing and special CSS functions are
  //     handled generically — `_rdFunctionCall` produces a FunctionExpression.
  //   - Bracketed lists `[a b, c]`, maps, the slash-separator nuance,
  //     `!important`, `U+` ranges, and the Microsoft `=` operator are
  //     scheduled for later stages.
  //   - Hex color literals are parsed as function-ish identifiers only
  //     when they lex as such; `#abc` lexing is scheduled for a later stage.

  /** Discards a value (for readChar() calls whose result is unused). */
  private inline def _rdConsume[A](a: A): Unit = { val _ = a }

  /** Recursion depth counter for _rdExpression — crashes at depth > 5000. */
  private var _rdExpressionDepth: Int = 0

  /** Global iteration counter — crashes at > 100000 total loop iterations across all _rdExpression calls to catch runaway parsing.
    */
  private var _rdTotalIterations: Int = 0

  /** dart-sass: `_isSlashOperand`. */
  private def _rdIsSlashOperand(e: Expression): Boolean = e match {
    case _: NumberExpression                           => true
    case _: FunctionExpression                         => true
    case b: BinaryOperationExpression if b.allowsSlash => true
    case _ => false
  }

  /** dart-sass: `_expression` (port). Consumes a full expression from the scanner, handling comma and space-separated lists as well as binary operator precedence.
    */
  protected def _rdExpression(
    stopAtComma:     Boolean = false,
    consumeNewlines: Boolean = false,
    singleEquals:    Boolean = false,
    until:           () => Boolean = null
  ): Expression = {
    _rdExpressionDepth += 1
    if (_rdExpressionDepth == 1) _rdTotalIterations = 0
    if (_rdExpressionDepth > 5000) {
      val ctx =
        if (scanner.isDone) "<EOF>"
        else {
          val end = math.min(scanner.position + 60, scanner.string.length)
          scanner.string.substring(scanner.position, end).replace("\n", "\\n")
        }
      _rdExpressionDepth = 0
      throw new Error(
        s"_rdExpression recursion depth > 5000 at pos ${scanner.position}, " +
          s"stopAtComma=$stopAtComma, consumeNewlines=$consumeNewlines, " +
          s"context=\"$ctx\""
      )
    }
    try {
      val start = scanner.state
      // dart-sass lines 1996-1998: save and set expression state flags.
      val wasInExpression = _inExpression
      val wasInParentheses = _inParentheses
      _inExpression = true

      // Accumulators matching the dart-sass locals. We use `null` sentinels
      // via Option to avoid Nullable implicit collisions.
      var commaExpressions: Option[mutable.ListBuffer[Expression]]     = None
      var spaceExpressions: Option[mutable.ListBuffer[Expression]]     = None
      var operators:        Option[mutable.ListBuffer[BinaryOperator]] = None
      var operands:         Option[mutable.ListBuffer[Expression]]     = None
      var allowSlash = true
      // Set to true when resetState() reparses from the beginning; the stall
      // detector must skip the check for that iteration since the scanner
      // legitimately rewinds.
      var reparsed = false

      var singleExpression: Option[Expression] = Some(_rdSingleExpression())

      def resolveOneOperation(): Unit = {
        val opsBuf   = operators.get
        val operator = opsBuf.remove(opsBuf.length - 1)
        val opdBuf   = operands.get
        val left     = opdBuf.remove(opdBuf.length - 1)
        val right    = singleExpression.getOrElse(scanner.error("Expected expression."))
        // dart-sass lines 2059-2064: slash-separated numbers only allowed
        // outside parentheses and when both operands are valid slash operands.
        val slashish =
          allowSlash && !_inParentheses && operator == BinaryOperator.DividedBy &&
            _rdIsSlashOperand(left) && _rdIsSlashOperand(right)
        singleExpression = Some(
          if (slashish) BinaryOperationExpression(operator, left, right, allowsSlash = true)
          else {
            allowSlash = false
            BinaryOperationExpression(operator, left, right)
          }
        )
      }

      def resolveOperations(): Unit = operators match {
        case Some(buf) => while (buf.nonEmpty) resolveOneOperation()
        case None      => ()
      }

      // Resets the scanner state to the state it was at at the beginning of the
      // expression, except for [_inParentheses].
      // dart-sass lines 2033-2043.
      def resetState(): Unit = {
        commaExpressions = None
        spaceExpressions = None
        operators = None
        operands = None
        scanner.state = start
        allowSlash = true
        singleExpression = Some(_rdSingleExpression())
        reparsed = true
      }

      def addSingleExpression(expr: Expression): Unit = boundary {
        if (singleExpression.isDefined) {
          // If we discover we're parsing a list whose first element is a division
          // operation, and we're in parentheses, reparse outside of a paren
          // context. This ensures that `(1/2 1)` doesn't perform division on its
          // first element.
          // dart-sass lines 2110-2121.
          if (_inParentheses) {
            _inParentheses = false
            if (allowSlash) {
              resetState()
              break(())
            }
          }

          val sp = spaceExpressions.getOrElse {
            val b = mutable.ListBuffer.empty[Expression]
            spaceExpressions = Some(b)
            b
          }
          resolveOperations()
          sp += singleExpression.get
          allowSlash = true
        }
        singleExpression = Some(expr)
      }

      def addOperator(operator: BinaryOperator): Unit = {
        // dart-sass: reject non-arithmetic operators in plain CSS mode.
        // SingleEquals and arithmetic operators (+, -, *, /) are allowed
        // because they may appear in calculations — checked at evaluation time.
        if (plainCss &&
          operator != BinaryOperator.SingleEquals &&
          operator != BinaryOperator.Plus &&
          operator != BinaryOperator.Minus &&
          operator != BinaryOperator.Times &&
          operator != BinaryOperator.DividedBy
        ) {
          scanner.error(
            "Operators aren't allowed in plain CSS.",
            scanner.position - operator.operator.length,
            operator.operator.length
          )
        }
        allowSlash = allowSlash && operator == BinaryOperator.DividedBy
        val ops = operators.getOrElse {
          val b = mutable.ListBuffer.empty[BinaryOperator]
          operators = Some(b)
          b
        }
        val opd = operands.getOrElse {
          val b = mutable.ListBuffer.empty[Expression]
          operands = Some(b)
          b
        }
        while (ops.nonEmpty && ops.last.precedence >= operator.precedence)
          resolveOneOperation()
        val se = singleExpression.getOrElse(scanner.error("Expected expression."))
        // dart-sass line 2168-2169: save operator position, then consume whitespace.
        val operatorEnd = scanner.position
        // dart-sass always uses consumeNewlines = true after operators (line 2169).
        whitespace(consumeNewlines = true)
        // dart-sass lines 2171-2178: if modulo and not looking at an expression,
        // emit `%` as a string literal instead of treating it as a binary operator.
        if (operator == BinaryOperator.Modulo && !_lookingAtExpression()) {
          addSingleExpression(
            StringExpression(
              Interpolation.plain("%", scanner.spanFromPosition(operatorEnd - 1, operatorEnd)),
              hasQuotes = false
            )
          )
        } else {
          ops += operator
          opd += se
          singleExpression = Some(_rdSingleExpression())
        }
      }

      def resolveSpaceExpressions(): Unit = {
        resolveOperations()
        spaceExpressions match {
          case Some(sp) =>
            val se = singleExpression.getOrElse(scanner.error("Expected expression."))
            sp += se
            singleExpression = Some(
              ListExpression(
                sp.toList,
                ListSeparator.Space,
                spanFrom(start),
                hasBrackets = false
              )
            )
            spaceExpressions = None
          case None => ()
        }
      }

      boundary {
        while (true) {
          _rdTotalIterations += 1
          if (_rdTotalIterations > 100000) {
            val ctx =
              if (scanner.isDone) "<EOF>"
              else {
                val end = math.min(scanner.position + 60, scanner.string.length)
                scanner.string.substring(scanner.position, end).replace("\n", "\\n")
              }
            _rdTotalIterations = 0
            throw new Error(
              s"_rdExpression runaway: >100000 total iterations at pos ${scanner.position}, " +
                s"depth=$_rdExpressionDepth, context=\"$ctx\""
            )
          }
          val loopStartPos = scanner.position
          whitespace(consumeNewlines = consumeNewlines)
          val c = scanner.peekChar()
          if (c < 0) break(())
          if (stopAtComma && c == CharCode.$comma) break(())
          if (until != null && until()) break(())
          c match {
            case CharCode.`$lparen` =>
              // Parenthesized numbers can't be slash-separated.
              addSingleExpression(_rdParenthesizedExpression())

            case CharCode.`$lbracket` =>
              addSingleExpression(_rdBracketList())

            case CharCode.`$dollar` =>
              addSingleExpression(_rdVariable())

            case CharCode.`$ampersand` =>
              addSingleExpression(_rdSelector())

            case CharCode.`$double_quote` | CharCode.`$single_quote` =>
              addSingleExpression(_rdString())

            case CharCode.`$hash` =>
              addSingleExpression(_rdHashExpression())

            case CharCode.`$equal` =>
              val _ = scanner.readChar()
              // dart-sass line 2229: single `=` for IE filter functions
              if (singleEquals && scanner.peekChar() != CharCode.$equal) {
                addOperator(BinaryOperator.SingleEquals)
              } else {
                scanner.expectChar(CharCode.$equal)
                addOperator(BinaryOperator.Equals)
              }

            case CharCode.`$exclamation` =>
              val n1 = scanner.peekChar(1)
              if (n1 == CharCode.$equal) {
                _rdConsume(scanner.readChar())
                _rdConsume(scanner.readChar())
                addOperator(BinaryOperator.NotEquals)
              } else if (n1 < 0 || n1 == CharCode.$i || n1 == CharCode.$I || CharCode.isWhitespace(n1)) {
                addSingleExpression(_rdImportantExpression())
              } else break(())

            case CharCode.`$lt` =>
              val _ = scanner.readChar()
              if (scanner.scanChar(CharCode.$equal)) addOperator(BinaryOperator.LessThanOrEquals)
              else addOperator(BinaryOperator.LessThan)

            case CharCode.`$gt` =>
              val _ = scanner.readChar()
              if (scanner.scanChar(CharCode.$equal))
                addOperator(BinaryOperator.GreaterThanOrEquals)
              else addOperator(BinaryOperator.GreaterThan)

            case CharCode.`$asterisk` =>
              val _ = scanner.readChar()
              addOperator(BinaryOperator.Times)

            case CharCode.`$plus` if singleExpression.isEmpty =>
              addSingleExpression(_rdUnaryOperation())
            case CharCode.`$plus` =>
              val _ = scanner.readChar()
              addOperator(BinaryOperator.Plus)

            case CharCode.`$minus` =>
              // dart-sass lines 2276-2288: minus handling in the main loop.
              val n1             = scanner.peekChar(1)
              val n1IsDigitOrDot = (n1 >= 0 && CharCode.isDigit(n1)) || n1 == CharCode.$dot
              if (
                n1IsDigitOrDot &&
                (singleExpression.isEmpty ||
                  (scanner.position > 0 && CharCode.isWhitespace(scanner.peekChar(-1))))
              ) {
                addSingleExpression(_rdNumber())
              } else if (_lookingAtInterpolatedIdentifier()) {
                addSingleExpression(_rdIdentifierLike())
              } else if (singleExpression.isEmpty) {
                addSingleExpression(_rdUnaryOperation())
              } else {
                val _ = scanner.readChar()
                addOperator(BinaryOperator.Minus)
              }

            case CharCode.`$slash` if singleExpression.isEmpty =>
              addSingleExpression(_rdUnaryOperation())
            case CharCode.`$slash` =>
              val _ = scanner.readChar()
              addOperator(BinaryOperator.DividedBy)

            case CharCode.`$percent` =>
              val _ = scanner.readChar()
              addOperator(BinaryOperator.Modulo)

            case _ if c >= CharCode.$0 && c <= CharCode.$9 =>
              addSingleExpression(_rdNumber())

            case CharCode.`$dot` if scanner.peekChar(1) == CharCode.$dot =>
              // `..` at operator level (e.g. rest args) — break the loop
              break(())

            case CharCode.`$dot` =>
              addSingleExpression(_rdNumber())

            case _ if c == 'a'.toInt && !plainCss && scanIdentifier("and") =>
              addOperator(BinaryOperator.And)

            case _ if c == 'o'.toInt && !plainCss && scanIdentifier("or") =>
              addOperator(BinaryOperator.Or)

            case _ if (c == CharCode.$u || c == CharCode.$U) && scanner.peekChar(1) == CharCode.$plus =>
              // dart-sass lines 2320-2321: unicode range
              addSingleExpression(_rdUnicodeRange())

            case _ if CharCode.isNameStart(c) || c == CharCode.$backslash || c >= 0x80 =>
              addSingleExpression(_rdIdentifierLike())

            case CharCode.`$comma` =>
              // If we discover we're parsing a list whose first element is a
              // division operation, and we're in parentheses, reparse outside of
              // a paren context. This ensures that `(1/2, 1)` doesn't perform
              // division on its first element.
              // dart-sass lines 2332-2343.
              val commaReparsed = if (_inParentheses) {
                _inParentheses = false
                if (allowSlash) { resetState(); true }
                else false
              } else false
              if (!commaReparsed) {
                val ce = commaExpressions.getOrElse {
                  val b = mutable.ListBuffer.empty[Expression]
                  commaExpressions = Some(b)
                  b
                }
                if (singleExpression.isEmpty) scanner.error("Expected expression.")
                resolveSpaceExpressions()
                ce += singleExpression.get
                val _ = scanner.readChar()
                allowSlash = true
                singleExpression = None
              }
            case _ =>
              break(())
          }
          if (stopAtComma && scanner.peekChar() == CharCode.$comma) break(())
          // NASA-style stall detector: if the scanner hasn't advanced after
          // processing one full loop iteration, crash with diagnostic info
          // instead of spinning forever. Skip the check when a reparse just
          // occurred — the scanner legitimately rewinds in that case.
          if (reparsed) {
            reparsed = false
          } else if (scanner.position == loopStartPos) {
            val ctx =
              if (scanner.isDone) "<EOF>"
              else {
                val end = math.min(scanner.position + 40, scanner.string.length)
                scanner.string.substring(scanner.position, end).replace("\n", "\\n")
              }
            throw new Error(
              s"_rdExpression stall detected at position ${scanner.position}: " +
                s"peekChar=${scanner.peekChar()}, " +
                s"singleExpr=${singleExpression.isDefined}, " +
                s"context=\"$ctx\""
            )
          }
        }
      }

      val result = commaExpressions match {
        case Some(ce) =>
          resolveSpaceExpressions()
          // dart-sass line 2370: restore _inParentheses before building
          // the comma-separated list expression.
          _inParentheses = wasInParentheses
          singleExpression.foreach(ce += _)
          _inExpression = wasInExpression
          ListExpression(ce.toList, ListSeparator.Comma, spanFrom(start), hasBrackets = false)
        case None =>
          resolveSpaceExpressions()
          _inExpression = wasInExpression
          singleExpression.getOrElse(scanner.error("Expected expression."))
      }
      result
    } finally _rdExpressionDepth -= 1
  }

  /** dart-sass: `_singleExpression` (stylesheet.dart:2425-2455).
    *
    * Dispatches on the next character to parse a single expression without top-level whitespace.
    */
  protected def _rdSingleExpression(): Expression = {
    val c = scanner.peekChar()
    if (c < 0) scanner.error("Expected expression.")
    c match {
      case CharCode.`$lparen`                                  => _rdParenthesizedExpression()
      case CharCode.`$lbracket`                                => _rdBracketList()
      case CharCode.`$slash`                                   => _rdUnaryOperation()
      case CharCode.`$dot`                                     => _rdNumber()
      case CharCode.`$dollar`                                  => _rdVariable()
      case CharCode.`$ampersand`                               => _rdSelector()
      case CharCode.`$double_quote` | CharCode.`$single_quote` => _rdString()
      case CharCode.`$hash`                                    => _rdHashExpression()
      case CharCode.`$plus`                                    =>
        // dart-sass: _plusExpression
        val n1p = scanner.peekChar(1)
        if ((n1p >= 0 && CharCode.isDigit(n1p)) || n1p == CharCode.$dot) _rdNumber()
        else _rdUnaryOperation()
      case CharCode.`$minus` =>
        // dart-sass: _minusExpression (stylesheet.dart:2625-2629)
        val n1m = scanner.peekChar(1)
        if ((n1m >= 0 && CharCode.isDigit(n1m)) || n1m == CharCode.$dot) _rdNumber()
        else if (_lookingAtInterpolatedIdentifier()) _rdIdentifierLike()
        else _rdUnaryOperation()
      case CharCode.`$exclamation` => _rdImportantExpression()
      case CharCode.`$percent`     =>
        // dart-sass: _percentExpression (stylesheet.dart:2644-2649)
        val pctStart = scanner.state
        val _        = scanner.readChar()
        StringExpression(Interpolation.plain("%", spanFrom(pctStart)), hasQuotes = false)
      case _ if (c == CharCode.$u || c == CharCode.$U) && scanner.peekChar(1) == CharCode.$plus =>
        // dart-sass: _unicodeRange (stylesheet.dart:2443)
        _rdUnicodeRange()
      case _ if c >= CharCode.$0 && c <= CharCode.$9                             => _rdNumber()
      case _ if CharCode.isNameStart(c) || c == CharCode.$backslash || c >= 0x80 =>
        _rdIdentifierLike()
      case _ => scanner.error("Expected expression.")
    }
  }

  /** dart-sass: `_selector`. Parses the `&` parent selector reference. */
  private def _rdSelector(): SelectorExpression = {
    val start = scanner.state
    scanner.expectChar(CharCode.$ampersand)
    // If & is followed by name characters, it's a suffix selector (&-foo)
    // and the suffix is handled at the selector level, not expression level.
    SelectorExpression(spanFrom(start))
  }

  /** dart-sass: `_hashExpression` (stylesheet.dart:2532-2554).
    * Dispatches `#` to hex color or `#{...}` interpolation.
    *
    * When the character after `#` is a digit, parse directly as hex color.
    * Otherwise, parse as an interpolated identifier, check whether it's a
    * plain hex color (3/4/6/8 hex digits), and backtrack if so.
    */
  private def _rdHashExpression(): Expression = {
    val start = scanner.state
    if (scanner.peekChar(1) == CharCode.$lbrace) {
      // #{...} interpolation — parse as interpolated identifier
      val ident = interpolatedIdentifier()
      if (scanner.peekChar() == CharCode.$lparen) {
        val args = _rdArgumentInvocation(start)
        return InterpolatedFunctionExpression(ident, args, spanFrom(start))
      }
      return StringExpression(ident, hasQuotes = false)
    }
    scanner.expectChar(CharCode.$hash)

    // dart-sass: if the first char after `#` is a digit, it's unambiguously a
    // hex color (identifiers can't start with a digit).
    val first = scanner.peekChar()
    if (first >= 0 && CharCode.isDigit(first)) {
      val color = _hexColorContents(start)
      return ColorExpression(color, spanFrom(start))
    }

    // Otherwise, parse the full identifier and check if it's a valid hex color.
    val afterHash  = scanner.state
    val identifier = interpolatedIdentifier()
    if (_isHexColor(identifier)) {
      scanner.state = afterHash
      val color = _hexColorContents(start)
      return ColorExpression(color, spanFrom(start))
    }

    // Not a hex color — prepend `#` and return as unquoted string.
    val buf = new InterpolationBuffer()
    buf.writeCharCode(CharCode.$hash)
    buf.addInterpolation(identifier)
    StringExpression(buf.interpolation(spanFrom(start)), hasQuotes = false)
  }

  /** dart-sass: `_hexColorContents` (stylesheet.dart:2557-2599).
    * Consumes the contents of a hex color after the `#`, reading individual
    * hex digits from the scanner.
    */
  private def _hexColorContents(start: ssg.sass.util.LineScannerState): ssg.sass.value.SassColor = {
    import ssg.sass.value.{ SassColor, SpanColorFormat, ColorFormat }
    import ssg.sass.Nullable
    val digit1 = _hexDigit()
    val digit2 = _hexDigit()
    val digit3 = _hexDigit()

    var red:   Int = 0
    var green: Int = 0
    var blue:  Int = 0
    var alpha: Nullable[Double] = Nullable.Null

    val next = scanner.peekChar()
    if (!(next >= 0 && CharCode.isHex(next))) {
      // #abc — 3 digits
      red = (digit1 << 4) + digit1
      green = (digit2 << 4) + digit2
      blue = (digit3 << 4) + digit3
    } else {
      val digit4 = _hexDigit()
      val next2 = scanner.peekChar()
      if (!(next2 >= 0 && CharCode.isHex(next2))) {
        // #abcd — 4 digits
        red = (digit1 << 4) + digit1
        green = (digit2 << 4) + digit2
        blue = (digit3 << 4) + digit3
        alpha = Nullable(((digit4 << 4) + digit4).toDouble / 0xff)
      } else {
        // #abcdef or #abcdefgh — 6 or 8 digits
        red = (digit1 << 4) + digit2
        green = (digit3 << 4) + digit4
        blue = (_hexDigit() << 4) + _hexDigit()

        val next3 = scanner.peekChar()
        if (next3 >= 0 && CharCode.isHex(next3)) {
          alpha = Nullable(((_hexDigit() << 4) + _hexDigit()).toDouble / 0xff)
        }
      }
    }

    val alphaVal = alpha.getOrElse(1.0)
    // Don't emit four- or eight-digit hex colors as hex, since that's not
    // yet well-supported in browsers.
    val format: Nullable[ColorFormat] =
      if (alpha.isEmpty) Nullable(new SpanColorFormat(scanner.substring(start.position, scanner.position)))
      else Nullable.Null
    SassColor.rgbInternal(Nullable(red.toDouble), Nullable(green.toDouble), Nullable(blue.toDouble), Nullable(alphaVal), format)
  }

  /** dart-sass: `_hexDigit` (stylesheet.dart:2613-2615).
    * Consumes a single hexadecimal digit.
    */
  private def _hexDigit(): Int = {
    val c = scanner.peekChar()
    if (c >= 0 && CharCode.isHex(c)) CharCode.asHex(scanner.readChar())
    else scanner.error("Expected hex digit.")
  }

  /** dart-sass: `_isHexColor` (stylesheet.dart:2603-2610).
    * Returns whether [interpolation] is a plain string that can be parsed as
    * a hex color.
    */
  private def _isHexColor(interpolation: Interpolation): Boolean = {
    val plain = interpolation.asPlain
    if (plain.isEmpty) return false
    val s = plain.get
    val len = s.length
    if (len != 3 && len != 4 && len != 6 && len != 8) return false
    var i = 0
    while (i < len) {
      if (!CharCode.isHex(s.charAt(i).toInt)) return false
      i += 1
    }
    true
  }

  /** dart-sass: `_importantExpression`. Parses `!important`. */
  private def _rdImportantExpression(): Expression = {
    val start = scanner.state
    scanner.expectChar(CharCode.$exclamation)
    whitespace(consumeNewlines = true)
    expectIdentifier("important")
    StringExpression(Interpolation.plain("!important", spanFrom(start)), hasQuotes = false)
  }

  /** dart-sass: `_expression(bracketList: true)`. Parses `[...]` bracket lists. */
  private def _rdBracketList(): Expression = {
    val start = scanner.state
    scanner.expectChar(CharCode.$lbracket)
    whitespace(consumeNewlines = true)
    if (scanner.scanChar(CharCode.$rbracket)) {
      return ListExpression(Nil, ListSeparator.Undecided, spanFrom(start), hasBrackets = true)
    }
    // Parse a full expression, then wrap in a bracketed list
    val inner = _rdExpression(consumeNewlines = true)
    scanner.expectChar(CharCode.$rbracket)
    val (elts, sep) = inner match {
      case l: ListExpression if !l.hasBrackets =>
        (l.contents, l.separator)
      case _ => (List(inner), ListSeparator.Undecided)
    }
    ListExpression(elts, sep, spanFrom(start), hasBrackets = true)
  }

  /** dart-sass: `parentheses` (stylesheet.dart:2458-2506). */
  protected def _rdParenthesizedExpression(): Expression = {
    // dart-sass lines 2460-2461: save and set _inParentheses.
    val wasInParentheses = _inParentheses
    _inParentheses = true
    try {
      val start = scanner.state
      scanner.expectChar(CharCode.$lparen)
      whitespace(consumeNewlines = true)
      // dart-sass line 2467: check if looking at expression
      if (!_lookingAtExpression()) {
        scanner.expectChar(CharCode.$rparen)
        return ListExpression(Nil, ListSeparator.Undecided, spanFrom(start), hasBrackets = false)
      }

      val first = expressionUntilComma()
      if (scanner.scanChar(CharCode.$colon)) {
        whitespace(consumeNewlines = true)
        // dart-sass: `_map` (stylesheet.dart:2513-2529)
        return _rdMap(first, start)
      }
      if (!scanner.scanChar(CharCode.$comma)) {
        scanner.expectChar(CharCode.$rparen)
        return ParenthesizedExpression(first, spanFrom(start))
      }
      whitespace(consumeNewlines = true)
      val inside = start // for span
      val elts   = mutable.ListBuffer.empty[Expression]
      elts += first
      while (_lookingAtExpression()) {
        elts += expressionUntilComma()
        if (!scanner.scanChar(CharCode.$comma)) {
          // no more commas
          val list = ListExpression(elts.toList, ListSeparator.Comma, spanFrom(inside), hasBrackets = false)
          scanner.expectChar(CharCode.$rparen)
          return ParenthesizedExpression(list, spanFrom(start))
        }
        whitespace(consumeNewlines = true)
      }
      val list = ListExpression(elts.toList, ListSeparator.Comma, spanFrom(inside), hasBrackets = false)
      scanner.expectChar(CharCode.$rparen)
      ParenthesizedExpression(list, spanFrom(start))
    } finally
      // dart-sass line 2504: restore _inParentheses.
      _inParentheses = wasInParentheses
  }

  /** dart-sass: `_map` (stylesheet.dart:2513-2529).
    *
    * Consumes a map expression after the first key and colon have been read.
    */
  private def _rdMap(first: Expression, start: ssg.sass.util.LineScannerState): MapExpression = {
    val pairs = mutable.ListBuffer.empty[(Expression, Expression)]
    val v     = expressionUntilComma()
    pairs += ((first, v))
    while (scanner.scanChar(CharCode.$comma)) {
      whitespace(consumeNewlines = true)
      if (!_lookingAtExpression()) {
        // trailing comma
      } else {
        val k = expressionUntilComma()
        scanner.expectChar(CharCode.$colon)
        whitespace(consumeNewlines = true)
        val vv = expressionUntilComma()
        pairs += ((k, vv))
      }
    }
    scanner.expectChar(CharCode.$rparen)
    MapExpression(pairs.toList, spanFrom(start))
  }

  /** dart-sass: `_number`. Minimum viable: consumes sign, digits, decimal, exponent, and optional unit identifier.
    */
  protected def _rdNumber(): NumberExpression = {
    val start = scanner.state
    val first = scanner.peekChar()
    if (first == CharCode.$plus || first == CharCode.$minus) {
      val _ = scanner.readChar()
    }
    // natural number
    if (scanner.peekChar() != CharCode.$dot) {
      if (!(scanner.peekChar() >= 0 && CharCode.isDigit(scanner.peekChar())))
        scanner.error("Expected digit.")
      while (scanner.peekChar() >= 0 && CharCode.isDigit(scanner.peekChar())) {
        val _ = scanner.readChar()
      }
    }
    // decimal
    if (scanner.peekChar() == CharCode.$dot) {
      val n1 = scanner.peekChar(1)
      if (n1 >= 0 && CharCode.isDigit(n1)) {
        val _ = scanner.readChar()
        while (scanner.peekChar() >= 0 && CharCode.isDigit(scanner.peekChar())) {
          val _ = scanner.readChar()
        }
      }
    }
    // exponent (e.g. 1e10, 1.5e-3)
    val eCh = scanner.peekChar()
    if (eCh == 'e'.toInt || eCh == 'E'.toInt) {
      val n1 = scanner.peekChar(1)
      if (
        (n1 >= 0 && CharCode.isDigit(n1)) ||
        n1 == CharCode.$plus || n1 == CharCode.$minus
      ) {
        val _ = scanner.readChar()
        if (n1 == CharCode.$plus || n1 == CharCode.$minus) {
          val _ = scanner.readChar()
        }
        while (scanner.peekChar() >= 0 && CharCode.isDigit(scanner.peekChar())) {
          val _ = scanner.readChar()
        }
      }
    }
    val span        = spanFrom(start)
    val text        = span.text
    val numericText =
      if (text.length > 0 && (text.charAt(0) == '+' || text.charAt(0) == '-'))
        text
      else text
    val number = numericText.toDouble
    // Unit
    val unit: Nullable[String] =
      if (scanner.scanChar(CharCode.$percent)) Nullable("%")
      else if (lookingAtIdentifier() &&
        // Disallow units beginning with `--`.
        // dart-sass: _number() (stylesheet.dart:2705-2707).
        (scanner.peekChar() != CharCode.$minus || scanner.peekChar(1) != CharCode.$minus)
      ) Nullable(identifier(unit = true))
      else Nullable.empty
    NumberExpression(number, spanFrom(start), unit)
  }

  /** dart-sass: `_variable` (port). */
  protected def _rdVariable(): VariableExpression = {
    val start = scanner.state
    scanner.expectChar(CharCode.$dollar)
    val name = identifier()
    // dart-sass: `_variable` (stylesheet.dart:2820-2825).
    if (plainCss) {
      error("Sass variables aren't allowed in plain CSS.", spanFrom(start))
    }
    VariableExpression(name.replace('_', '-'), spanFrom(start))
  }

  /** Consumes an `@import` rule.
    *
    * [start] should point before the `@`.
    *
    * dart-sass: `_importRule` (stylesheet.dart:1185-1211).
    */
  private def _importRule(start: ssg.sass.util.LineScannerState): ImportRule = {
    val imports = mutable.ListBuffer.empty[Import]
    var more = true
    while (more) {
      whitespace(consumeNewlines = false)
      val argument = importArgument()
      if (!plainCss) {
        argument match {
          case _: DynamicImport =>
            warnDeprecation(
              Deprecation.Import,
              "Sass @import rules are deprecated and will be removed in Dart " +
                "Sass 3.0.0.\n\n" +
                "More info and automated migrator: " +
                "https://sass-lang.com/d/import",
              argument.span
            )
          case _ => // no warning for static imports
        }
      }
      if ((_inControlDirective || _inMixin) && argument.isInstanceOf[DynamicImport]) {
        _disallowedAtRule(start)
      }
      imports += argument
      whitespace(consumeNewlines = false)
      // In plain CSS mode, dart-sass's `_cssImportRule` handles exactly one
      // import per `@import` rule — no comma loop.
      if (!plainCss && scanner.scanChar(CharCode.$comma)) more = true
      else more = false
    }
    expectStatementSeparator("@import rule")
    new ImportRule(imports.toList, spanFrom(start))
  }

  /** Consumes an argument to an `@import` rule.
    *
    * dart-sass: `importArgument` (stylesheet.dart:1217-1249).
    */
  protected def importArgument(): Import = {
    val start = scanner.state
    val c = scanner.peekChar()
    if (c == CharCode.$u || c == CharCode.$U) {
      val url = dynamicUrl()
      whitespace(consumeNewlines = false)
      val modifiers = tryImportModifiers()
      url match {
        case se: StringExpression =>
          StaticImport(se.text, spanFrom(start), modifiers)
        case ife: InterpolatedFunctionExpression if plainCss =>
          // In plain CSS, url("...") should be treated as a plain static
          // import. Reconstruct the interpolation from the function name
          // and the quoted string argument. Ported from dart-sass
          // css.dart _cssImportRule (lines 97-120).
          val buf = new InterpolationBuffer()
          buf.addInterpolation(ife.name)
          buf.writeCharCode(CharCode.$lparen)
          ife.arguments.positional match {
            case (se: StringExpression) :: Nil =>
              buf.addInterpolation(se.asInterpolation())
            case _ =>
              error("Unsupported plain CSS import.", ife.span)
          }
          buf.writeCharCode(CharCode.$rparen)
          StaticImport(buf.interpolation(ife.span), spanFrom(start), modifiers)
        case other =>
          StaticImport(
            new Interpolation(List(other), List(Nullable(other.span)), other.span),
            spanFrom(start),
            modifiers
          )
      }
    } else {
      val url = string()
      val urlSpan = spanFrom(start)
      whitespace(consumeNewlines = false)
      val modifiers = tryImportModifiers()
      if (plainCss || isPlainImportUrl(url) || modifiers.isDefined) {
        StaticImport(
          Interpolation.plain(urlSpan.text, urlSpan),
          spanFrom(start),
          modifiers
        )
      } else {
        try {
          DynamicImport(parseImportUrl(url), urlSpan)
        } catch {
          case e: Exception =>
            error(s"Invalid URL: ${e.getMessage}", urlSpan)
        }
      }
    }
  }

  /** Parses [url] as an import URL.
    *
    * dart-sass: `parseImportUrl` (stylesheet.dart:1253-1263).
    */
  protected def parseImportUrl(url: String): String = {
    // Backwards-compatibility for implementations that allow absolute Windows
    // paths in imports.
    if (url.length >= 2 && url.charAt(1) == ':' &&
        Character.isLetter(url.charAt(0)) &&
        !url.startsWith("/")) {
      // Looks like an absolute Windows path (e.g. C:\foo\bar) — convert to URI.
      return url.replace('\\', '/')
    }
    // Throw if [url] is invalid.
    new java.net.URI(url)
    url
  }

  /** Returns whether [url] indicates that an `@import` is a plain CSS import.
    *
    * dart-sass: `isPlainImportUrl` (stylesheet.dart:1267-1276).
    */
  protected def isPlainImportUrl(url: String): Boolean = {
    if (url.length < 5) return false
    if (url.endsWith(".css")) return true
    val c0 = url.charAt(0).toInt
    if (c0 == CharCode.$slash) url.charAt(1).toInt == CharCode.$slash
    else if (c0 == CharCode.$h) url.startsWith("http://") || url.startsWith("https://")
    else false
  }

  /** Consumes a `url` token that's allowed to contain SassScript.
    *
    * dart-sass: `dynamicUrl` (stylesheet.dart:3528-3540).
    */
  protected def dynamicUrl(): Expression = {
    val start = scanner.state
    expectIdentifier("url")
    val contents = _tryUrlContents(start)
    if (contents.isDefined) {
      return StringExpression(contents.get)
    }
    InterpolatedFunctionExpression(
      Interpolation.plain("url", spanFrom(start)),
      _rdArgumentInvocation(start),
      spanFrom(start)
    )
  }

  /** Consumes a sequence of modifiers (such as media or supports queries)
    * after an import argument.
    *
    * Returns `Nullable.empty` if there are no modifiers.
    *
    * dart-sass: `tryImportModifiers` (stylesheet.dart:1282-1334).
    */
  protected def tryImportModifiers(): Nullable[Interpolation] = {
    // Exit before allocating anything if we're not looking at any modifiers, as
    // is the most common case.
    if (!_lookingAtInterpolatedIdentifier() && scanner.peekChar() != CharCode.$lparen) {
      return Nullable.empty
    }

    val start = scanner.state
    val buffer = new InterpolationBuffer()
    boundary[Nullable[Interpolation]] {
      while (true) {
        if (_lookingAtInterpolatedIdentifier()) {
          if (!buffer.isEmpty) buffer.writeCharCode(CharCode.$space)

          val ident = interpolatedIdentifier()
          buffer.addInterpolation(ident)

          val name = ident.asPlain.fold(Nullable.empty[String])(s => Nullable(s.toLowerCase))
          if (!Utils.equalsIgnoreCase(name, Nullable("and")) && scanner.scanChar(CharCode.$lparen)) {
            if (Utils.equalsIgnoreCase(name, Nullable("supports"))) {
              val query = _importSupportsQuery()
              if (!query.isInstanceOf[SupportsDeclaration]) buffer.writeCharCode(CharCode.$lparen)
              buffer.add(SupportsExpression(query), query.span)
              if (!query.isInstanceOf[SupportsDeclaration]) buffer.writeCharCode(CharCode.$rparen)
            } else {
              buffer.writeCharCode(CharCode.$lparen)
              buffer.addInterpolation(
                _interpolatedDeclarationValue(
                  allowEmpty = true,
                  allowSemicolon = true,
                  consumeNewlines = true
                )
              )
              buffer.writeCharCode(CharCode.$rparen)
            }
            scanner.expectChar(CharCode.$rparen)
            whitespace(consumeNewlines = false)
          } else {
            whitespace(consumeNewlines = false)
            if (scanner.scanChar(CharCode.$comma)) {
              buffer.write(", ")
              buffer.addInterpolation(_mediaQueryList())
              break(Nullable(buffer.interpolation(spanFrom(start))))
            }
          }
        } else if (scanner.peekChar() == CharCode.$lparen) {
          if (!buffer.isEmpty) buffer.writeCharCode(CharCode.$space)
          buffer.addInterpolation(_mediaQueryList())
          break(Nullable(buffer.interpolation(spanFrom(start))))
        } else {
          break(Nullable(buffer.interpolation(spanFrom(start))))
        }
      }
      // The while(true) never exits naturally; all paths break.
      Nullable.empty
    }
  }

  /** Consumes the contents of a `supports()` function after an `@import` rule
    * (but not the function name or parentheses).
    *
    * dart-sass: `_importSupportsQuery` (stylesheet.dart:1338-1361).
    */
  private def _importSupportsQuery(): SupportsCondition = {
    whitespace(consumeNewlines = true)
    if (scanIdentifier("not")) {
      whitespace(consumeNewlines = true)
      val start = scanner.state
      return SupportsNegation(
        _supportsConditionInParens(),
        spanFrom(start)
      )
    } else if (scanner.peekChar() == CharCode.$lparen) {
      return _supportsCondition(inParentheses = true)
    } else {
      val fn = _tryImportSupportsFunction()
      if (fn.isDefined) return fn.get

      val start = scanner.state
      val name = _rdExpression(consumeNewlines = true)
      scanner.expectChar(CharCode.$colon)
      return SupportsDeclaration(
        name,
        _supportsDeclarationValue(name),
        spanFrom(start)
      )
    }
  }

  /** Consumes a function call within a `supports()` function after an
    * `@import` if available.
    *
    * dart-sass: `_tryImportSupportsFunction` (stylesheet.dart:1365-1385).
    */
  private def _tryImportSupportsFunction(): Nullable[SupportsFunction] = {
    if (!_lookingAtInterpolatedIdentifier()) return Nullable.empty

    val start = scanner.state
    val name = interpolatedIdentifier()
    assert(
      !Utils.equalsIgnoreCase(name.asPlain, Nullable("not")),
      "\"not\" should have been consumed by scanIdentifier"
    )

    if (!scanner.scanChar(CharCode.$lparen)) {
      scanner.state = start
      return Nullable.empty
    }

    val value = _interpolatedDeclarationValue(
      allowEmpty = true,
      allowSemicolon = true,
      consumeNewlines = true
    )
    scanner.expectChar(CharCode.$rparen)

    Nullable(SupportsFunction(name, value, spanFrom(start)))
  }

  // Legacy helpers (_consumeImportUrl, _tryImportModifiers,
  // _normalizeImportSupportsWhitespace, _collapseNewlineWhitespace) have been
  // replaced by the properly-ported methods above matching dart-sass structure:
  // _importRule, importArgument, parseImportUrl, isPlainImportUrl, dynamicUrl,
  // tryImportModifiers, _importSupportsQuery, _tryImportSupportsFunction.

  // =========================================================================
  // Interpolation subsystem — faithful ports from dart-sass
  // stylesheet.dart: singleInterpolation, interpolatedString,
  // interpolatedIdentifier, _interpolatedIdentifierBodyHelper,
  // _lookingAtInterpolatedIdentifier, _lookingAtInterpolatedIdentifierBody.
  // =========================================================================

  /** Consumes `#{...}` interpolation and returns the expression with its span.
    *
    * dart-sass: `singleInterpolation` (stylesheet.dart:3887-3900).
    */
  protected def singleInterpolation(): (Expression, FileSpan) = {
    val start = scanner.state
    scanner.expectChar(CharCode.$hash)
    scanner.expectChar(CharCode.$lbrace)
    whitespace(consumeNewlines = true)
    val contents = _rdExpression(consumeNewlines = true)
    scanner.expectChar(CharCode.$rbrace)
    val span = spanFrom(start)

    // dart-sass: stylesheet.dart:3895-3897
    if (plainCss) {
      error("Interpolation isn't allowed in plain CSS.", span)
    }

    (contents, span)
  }

  /** Parses a quoted string with `#{...}` interpolation support.
    *
    * dart-sass: `interpolatedString` (stylesheet.dart:2856-2897).
    */
  protected def interpolatedString(): StringExpression = {
    val start = scanner.state
    val quote = scanner.readChar()
    if (quote != CharCode.$single_quote && quote != CharCode.$double_quote) {
      scanner.error("Expected string.", start.position)
    }
    val buffer = new InterpolationBuffer()
    boundary {
      while (true) {
        val c = scanner.peekChar()
        if (c < 0 || CharCode.isNewline(c)) {
          scanner.error(s"Expected ${quote.toChar}.")
        }
        if (c == quote) {
          scanner.readChar()
          break(())
        } else if (c == CharCode.$backslash) {
          val second = scanner.peekChar(1)
          if (second >= 0 && CharCode.isNewline(second)) {
            // Line continuation: consume backslash + newline, emit nothing.
            scanner.readChar()
            scanner.readChar()
            if (second == CharCode.$cr) scanner.scanChar(CharCode.$lf)
          } else {
            buffer.writeCharCode(escapeCharacter())
          }
        } else if (c == CharCode.$hash && scanner.peekChar(1) == CharCode.$lbrace) {
          val (expression, span) = singleInterpolation()
          buffer.add(expression, span)
        } else {
          buffer.writeCharCode(scanner.readChar())
        }
      }
    }
    StringExpression(buffer.interpolation(spanFrom(start)), hasQuotes = true)
  }

  /** Parses a CSS identifier that may contain `#{...}` interpolation.
    *
    * dart-sass: `interpolatedIdentifier` (stylesheet.dart:3822-3852).
    */
  protected def interpolatedIdentifier(): Interpolation = {
    val start  = scanner.state
    val buffer = new InterpolationBuffer()

    if (scanner.scanChar(CharCode.$minus)) {
      buffer.writeCharCode(CharCode.$minus)
      if (scanner.scanChar(CharCode.$minus)) {
        buffer.writeCharCode(CharCode.$minus)
        _interpolatedIdentifierBodyHelper(buffer)
        return buffer.interpolation(spanFrom(start))
      }
    }

    val c = scanner.peekChar()
    if (c < 0) scanner.error("Expected identifier.")
    else if (CharCode.isNameStart(c)) {
      buffer.writeCharCode(scanner.readChar())
    } else if (c == CharCode.$backslash) {
      buffer.write(escape(identifierStart = true))
    } else if (c == CharCode.$hash && scanner.peekChar(1) == CharCode.$lbrace) {
      val (expression, span) = singleInterpolation()
      buffer.add(expression, span)
    } else {
      scanner.error("Expected identifier.")
    }

    _interpolatedIdentifierBodyHelper(buffer)
    buffer.interpolation(spanFrom(start))
  }

  /** Consumes the body of a possibly-interpolated identifier into [buffer].
    *
    * dart-sass: `_interpolatedIdentifierBodyHelper` (stylesheet.dart:3865-3882).
    */
  private def _interpolatedIdentifierBodyHelper(buffer: InterpolationBuffer): Unit =
    boundary {
      while (true) {
        val c = scanner.peekChar()
        if (c < 0) break(())
        else if (CharCode.isName(c)) {
          buffer.writeCharCode(scanner.readChar())
        } else if (c == CharCode.$backslash) {
          buffer.write(escape())
        } else if (c == CharCode.$hash && scanner.peekChar(1) == CharCode.$lbrace) {
          val (expression, span) = singleInterpolation()
          buffer.add(expression, span)
        } else {
          break(())
        }
      }
    }

  /** Returns whether the scanner is before an interpolated identifier.
    *
    * dart-sass: `_lookingAtInterpolatedIdentifier` (stylesheet.dart:4706-4719).
    */
  protected def _lookingAtInterpolatedIdentifier(): Boolean = {
    val c = scanner.peekChar()
    if (c < 0) false
    else if (CharCode.isNameStart(c) || c == CharCode.$backslash) true
    else if (c == CharCode.$hash) scanner.peekChar(1) == CharCode.$lbrace
    else if (c == CharCode.$minus) {
      val c1 = scanner.peekChar(1)
      if (c1 < 0) false
      else if (c1 == CharCode.$hash) scanner.peekChar(2) == CharCode.$lbrace
      else CharCode.isNameStart(c1) || c1 == CharCode.$backslash || c1 == CharCode.$minus
    } else false
  }

  /** Returns whether the scanner is before a character that could be part of an interpolated identifier body.
    *
    * dart-sass: `_lookingAtInterpolatedIdentifierBody` (stylesheet.dart:4733-4738).
    */
  protected def _lookingAtInterpolatedIdentifierBody(): Boolean = {
    val c = scanner.peekChar()
    if (c < 0) false
    else if (CharCode.isName(c) || c == CharCode.$backslash) true
    else c == CharCode.$hash && scanner.peekChar(1) == CharCode.$lbrace
  }

  /** Consumes a quoted string and returns the raw text (including quotes) as an Interpolation, preserving `#{...}` expressions.
    *
    * Unlike [[interpolatedString]], this includes the quote characters in the output and doesn't try to decode escape sequences — it preserves literal backslash sequences.
    *
    * dart-sass: `interpolatedStringToken` (stylesheet.dart:2901-2941).
    */
  protected def interpolatedStringToken(): Interpolation = {
    // NOTE: this logic is largely duplicated in [Parser.string] and
    // [interpolatedString]. Most changes here should be mirrored there.

    val start = scanner.state
    val quote = scanner.readChar()

    if (quote != CharCode.$single_quote && quote != CharCode.$double_quote) {
      scanner.error("Expected string.", start.position)
    }

    val buffer = new InterpolationBuffer()
    buffer.writeCharCode(quote)
    boundary {
      while (true) {
        val c = scanner.peekChar()
        if (c == quote) {
          buffer.writeCharCode(scanner.readChar())
          break(())
        } else if (c < 0 || CharCode.isNewline(c)) {
          scanner.error(s"Expected ${quote.toChar}.")
        } else if (c == CharCode.$backslash) {
          val second = scanner.peekChar(1)
          if (second >= 0 && CharCode.isNewline(second)) {
            buffer.writeCharCode(scanner.readChar())
            buffer.writeCharCode(scanner.readChar())
            if (second == CharCode.$cr) {
              if (scanner.scanChar(CharCode.$lf)) buffer.writeCharCode(CharCode.$lf)
            }
          } else {
            buffer.write(rawText { () =>
              val _ = escapeCharacter()
            })
          }
        } else if (c == CharCode.$hash && scanner.peekChar(1) == CharCode.$lbrace) {
          val (expression, span) = singleInterpolation()
          buffer.add(expression, span)
        } else {
          buffer.writeCharCode(scanner.readChar())
        }
      }
    }

    buffer.interpolation(spanFrom(start))
  }

  /** Tries to parse a `url()` or `url-prefix()` and returns Nullable.empty if the URL fails to parse.
    *
    * dart-sass: `_tryUrlContents` (stylesheet.dart:3448-3524).
    */
  protected def _tryUrlContents(
    start:    ssg.sass.util.LineScannerState,
    name:     String = "url",
    vendored: Boolean = false
  ): Nullable[Interpolation] =
    _rdTryUrlContents(start, name, vendored)

  /** Returns whether the scanner is immediately before a character that could start a `*prop: val`, `:prop: val`, `#prop: val`, or `.prop: val` hack.
    *
    * dart-sass: `_lookingAtPotentialPropertyHack` (stylesheet.dart:4723-4727).
    */
  @annotation.nowarn("msg=unused private member") // scaffolding: used when property hack parsing is ported
  private def _lookingAtPotentialPropertyHack(): Boolean = {
    val c = scanner.peekChar()
    c == CharCode.$colon || c == CharCode.$asterisk || c == CharCode.$dot ||
    (c == CharCode.$hash && scanner.peekChar(1) != CharCode.$lbrace)
  }

  // _lookingAtExpression — see the protected def below (line ~4846)

  /** Consumes almost any value until `;` or `}` (or `!`).
    *
    * dart-sass: `almostAnyValue` (stylesheet.dart:3559-3649).
    */
  protected def almostAnyValue(omitComments: Boolean = false): Interpolation = {
    val start  = scanner.state
    val buffer = new InterpolationBuffer()

    val brackets = mutable.ArrayBuffer.empty[Int]

    boundary {
      while (true) {
        val c = scanner.peekChar()
        if (c < 0) {
          break(())
        } else if (c == CharCode.$backslash) {
          // Write a literal backslash because this text will be re-parsed.
          buffer.writeCharCode(scanner.readChar())
          buffer.writeCharCode(scanner.readChar())
        } else if (c == CharCode.$double_quote || c == CharCode.$single_quote) {
          buffer.addInterpolation(interpolatedStringToken())
        } else if (c == CharCode.$slash) {
          val c1 = scanner.peekChar(1)
          if (c1 == CharCode.$asterisk && !omitComments) {
            buffer.write(rawText(() => loudComment()))
          } else if (c1 == CharCode.$asterisk) {
            loudComment()
          } else if (c1 == CharCode.$slash && !omitComments) {
            buffer.write(rawText(() => silentComment()))
          } else if (c1 == CharCode.$slash) {
            silentComment()
          } else {
            buffer.writeCharCode(scanner.readChar())
          }
        } else if (c == CharCode.$hash && scanner.peekChar(1) == CharCode.$lbrace) {
          // Add a full interpolated identifier to handle cases like
          // "#{...}--1", since "--1" isn't a valid identifier on its own.
          buffer.addInterpolation(interpolatedIdentifier())
        } else if (c == CharCode.$cr || c == CharCode.$lf || c == CharCode.$ff) {
          if (indented && brackets.isEmpty) break(())
          buffer.writeCharCode(scanner.readChar())
        } else if (c == CharCode.$exclamation || c == CharCode.$semicolon || c == CharCode.$lbrace || c == CharCode.$rbrace) {
          break(())
        } else if (c == CharCode.$u || c == CharCode.$U) {
          val beforeUrl = scanner.state
          val ident     = identifier()
          if (
            ident != "url" &&
            // This isn't actually a standard CSS feature, but it was
            // supported by the old `@document` rule so we continue to support
            // it for backwards-compatibility.
            ident != "url-prefix"
          ) {
            buffer.write(ident)
          } else {
            val urlContents = _tryUrlContents(beforeUrl, name = ident)
            if (urlContents.isDefined) {
              buffer.addInterpolation(urlContents.get)
            } else {
              scanner.state = beforeUrl
              buffer.writeCharCode(scanner.readChar())
            }
          }
        } else if (c == CharCode.$lparen || c == CharCode.$lbracket) {
          val bracket = scanner.readChar()
          buffer.writeCharCode(bracket)
          brackets += CharCode.opposite(bracket)
        } else if (c == CharCode.$rparen || c == CharCode.$rbracket) {
          if (brackets.isEmpty) {
            scanner.error(s"Unexpected \"${c.toChar}\".")
          }
          val bracket = brackets.remove(brackets.length - 1)
          scanner.expectChar(bracket)
          buffer.writeCharCode(bracket)
        } else if (lookingAtIdentifier()) {
          buffer.write(identifier())
        } else {
          buffer.writeCharCode(scanner.readChar())
        }
      }
    }

    buffer.interpolation(spanFrom(start))
  }

  // ---------------------------------------------------------------------------
  // Media query structured parsing
  // Ported from dart-sass stylesheet.dart _mediaQueryList (line 4315-4327),
  // _mediaQuery (line 4330-4400), _mediaLogicSequence (line 4404-4416),
  // _mediaOrInterp (line 4419-4426), _mediaInParens (line 4429-4491),
  // _expressionUntilComparison (line 4495-4502).
  // ---------------------------------------------------------------------------

  /** Consumes a list of media queries.
    *
    * dart-sass: `_mediaQueryList` (stylesheet.dart:4315-4327).
    */
  protected def _mediaQueryList(): Interpolation = {
    val start  = scanner.state
    val buffer = new InterpolationBuffer()
    var continue_ = true
    while (continue_) {
      whitespace(consumeNewlines = false)
      _mediaQuery(buffer)
      whitespace(consumeNewlines = false)
      if (scanner.scanChar(CharCode.$comma)) {
        buffer.writeCharCode(CharCode.$comma)
        buffer.writeCharCode(CharCode.$space)
      } else {
        continue_ = false
      }
    }
    buffer.interpolation(spanFrom(start))
  }

  /** Consumes a single media query.
    *
    * dart-sass: `_mediaQuery` (stylesheet.dart:4330-4400).
    */
  private def _mediaQuery(buffer: InterpolationBuffer): Unit = {
    // This is somewhat duplicated in MediaQueryParser._mediaQuery.
    if (scanner.peekChar() == CharCode.$lparen) {
      _mediaInParens(buffer)
      whitespace(consumeNewlines = false)
      if (scanIdentifier("and")) {
        buffer.write(" and ")
        expectWhitespace()
        _mediaLogicSequence(buffer, "and")
      } else if (scanIdentifier("or")) {
        buffer.write(" or ")
        expectWhitespace()
        _mediaLogicSequence(buffer, "or")
      }

      return
    }

    val identifier1 = interpolatedIdentifier()
    if (Utils.equalsIgnoreCase(identifier1.asPlain, Nullable("not"))) {
      // For example, "@media not (...) {"
      expectWhitespace()

      if (!_lookingAtInterpolatedIdentifier()) {
        buffer.write("not ")
        _mediaOrInterp(buffer)
        return
      }
    }

    whitespace(consumeNewlines = false)
    buffer.addInterpolation(identifier1)
    if (!_lookingAtInterpolatedIdentifier()) {
      // For example, "@media screen {".
      return
    }

    buffer.writeCharCode(CharCode.$space)
    val identifier2 = interpolatedIdentifier()

    if (Utils.equalsIgnoreCase(identifier2.asPlain, Nullable("and"))) {
      expectWhitespace()
      // For example, "@media screen and ..."
      buffer.write(" and ")
    } else {
      whitespace(consumeNewlines = false)
      buffer.addInterpolation(identifier2)
      if (scanIdentifier("and")) {
        // For example, "@media only screen and ..."
        expectWhitespace()
        buffer.write(" and ")
      } else {
        // For example, "@media only screen {"
        return
      }
    }

    // We've consumed either `IDENTIFIER "and"` or
    // `IDENTIFIER IDENTIFIER "and"`.

    if (scanIdentifier("not")) {
      // For example, "@media screen and not (...) {"
      expectWhitespace()
      buffer.write("not ")
      _mediaOrInterp(buffer)
      return
    }

    _mediaLogicSequence(buffer, "and")
    return
  }

  /** Consumes one or more `MediaOrInterp` expressions separated by [operator]
    * and writes them to [buffer].
    *
    * dart-sass: `_mediaLogicSequence` (stylesheet.dart:4404-4416).
    */
  private def _mediaLogicSequence(buffer: InterpolationBuffer, operator: String): Unit = {
    while (true) {
      _mediaOrInterp(buffer)
      whitespace(consumeNewlines = false)

      if (!scanIdentifier(operator)) return
      expectWhitespace(consumeNewlines = false)

      buffer.writeCharCode(CharCode.$space)
      buffer.write(operator)
      buffer.writeCharCode(CharCode.$space)
    }
  }

  /** Consumes a `MediaOrInterp` expression and writes it to [buffer].
    *
    * dart-sass: `_mediaOrInterp` (stylesheet.dart:4419-4426).
    */
  private def _mediaOrInterp(buffer: InterpolationBuffer): Unit = {
    if (scanner.peekChar() == CharCode.$hash) {
      val (expression, span) = singleInterpolation()
      buffer.add(expression, span)
    } else {
      _mediaInParens(buffer)
    }
  }

  /** Consumes a `MediaInParens` expression and writes it to [buffer].
    *
    * dart-sass: `_mediaInParens` (stylesheet.dart:4429-4491).
    */
  private def _mediaInParens(buffer: InterpolationBuffer): Unit = {
    scanner.expectChar(CharCode.$lparen, name = "media condition in parentheses")
    buffer.writeCharCode(CharCode.$lparen)
    whitespace(consumeNewlines = true)

    if (scanner.peekChar() == CharCode.$lparen) {
      _mediaInParens(buffer)
      whitespace(consumeNewlines = true)
      if (scanIdentifier("and")) {
        buffer.write(" and ")
        expectWhitespace(consumeNewlines = true)
        _mediaLogicSequence(buffer, "and")
      } else if (scanIdentifier("or")) {
        buffer.write(" or ")
        expectWhitespace(consumeNewlines = true)
        _mediaLogicSequence(buffer, "or")
      }
    } else if (scanIdentifier("not")) {
      buffer.write("not ")
      expectWhitespace(consumeNewlines = true)
      _mediaOrInterp(buffer)
    } else {
      val expressionBefore = _expressionUntilComparison()
      buffer.add(expressionBefore, expressionBefore.span)
      if (scanner.scanChar(CharCode.$colon)) {
        whitespace(consumeNewlines = true)
        buffer.writeCharCode(CharCode.$colon)
        buffer.writeCharCode(CharCode.$space)
        val expressionAfter = _rdExpression(consumeNewlines = true)
        buffer.add(expressionAfter, expressionAfter.span)
      } else {
        val next = scanner.peekChar()
        if (next == CharCode.$lt || next == CharCode.$gt || next == CharCode.$equal) {
          buffer.writeCharCode(CharCode.$space)
          buffer.writeCharCode(scanner.readChar())
          if ((next == CharCode.$lt || next == CharCode.$gt) && scanner.scanChar(CharCode.$equal)) {
            buffer.writeCharCode(CharCode.$equal)
          }
          buffer.writeCharCode(CharCode.$space)

          whitespace(consumeNewlines = true)
          val expressionMiddle = _expressionUntilComparison()
          buffer.add(expressionMiddle, expressionMiddle.span)

          // dart-lang/sdk#45356
          if ((next == CharCode.$lt || next == CharCode.$gt) && scanner.scanChar(next)) {
            buffer.writeCharCode(CharCode.$space)
            buffer.writeCharCode(next)
            if (scanner.scanChar(CharCode.$equal)) buffer.writeCharCode(CharCode.$equal)
            buffer.writeCharCode(CharCode.$space)

            whitespace(consumeNewlines = true)
            val expressionAfter = _expressionUntilComparison()
            buffer.add(expressionAfter, expressionAfter.span)
          }
        }
      }
    }

    scanner.expectChar(CharCode.$rparen)
    whitespace(consumeNewlines = false)
    buffer.writeCharCode(CharCode.$rparen)
  }

  /** Consumes an expression until it reaches a top-level `<`, `>`, or a `=`
    * that's not `==`.
    *
    * dart-sass: `_expressionUntilComparison` (stylesheet.dart:4495-4502).
    */
  private def _expressionUntilComparison(): Expression = _rdExpression(
    consumeNewlines = true,
    until = () => {
      val c = scanner.peekChar()
      if (c == CharCode.$equal) scanner.peekChar(1) != CharCode.$equal
      else c == CharCode.$lt || c == CharCode.$gt
    }
  )

  // ---------------------------------------------------------------------------
  // @supports condition parsing
  // Ported from dart-sass stylesheet.dart _supportsCondition (line 4510-4544),
  // _supportsConditionInParens (line 4547-4637),
  // _supportsDeclarationValue (line 4640-4653),
  // _trySupportsOperation (line 4658-4695).
  // ---------------------------------------------------------------------------

  /** Consumes a `@supports` condition.
    *
    * If [inParentheses] is true, the indented syntax will consume newlines where a statement otherwise would end.
    */
  protected def _supportsCondition(inParentheses: Boolean = false): SupportsCondition = {
    val start = scanner.state
    if (scanIdentifier("not")) {
      whitespace(consumeNewlines = inParentheses)
      return SupportsNegation(
        _supportsConditionInParens(),
        spanFrom(start)
      )
    }

    var condition: SupportsCondition = _supportsConditionInParens()
    whitespace(consumeNewlines = inParentheses)
    var operator: Nullable[BooleanOperator] = Nullable.empty
    while (lookingAtIdentifier()) {
      if (operator.isDefined) {
        expectIdentifier(operator.get.toString)
      } else if (scanIdentifier("or")) {
        operator = Nullable(BooleanOperator.Or)
      } else {
        expectIdentifier("and")
        operator = Nullable(BooleanOperator.And)
      }

      whitespace(consumeNewlines = inParentheses)
      val right = _supportsConditionInParens()
      condition = SupportsOperation(
        condition,
        right,
        operator.get,
        spanFrom(start)
      )
      whitespace(consumeNewlines = inParentheses)
    }
    condition
  }

  /** Consumes a parenthesized supports condition, or an interpolation. */
  protected def _supportsConditionInParens(): SupportsCondition = {
    val start = scanner.state

    if (_lookingAtInterpolatedIdentifier()) {
      val identifier = interpolatedIdentifier()
      if (
        identifier.asPlain.isDefined &&
        identifier.asPlain.get.toLowerCase() == "not"
      ) {
        error("\"not\" is not a valid identifier here.", identifier.span)
      }

      if (scanner.scanChar(CharCode.$lparen)) {
        val arguments = _interpolatedDeclarationValue(
          allowEmpty = true,
          allowSemicolon = true,
          consumeNewlines = true
        )
        scanner.expectChar(CharCode.$rparen)
        return SupportsFunction(identifier, arguments, spanFrom(start))
      } else {
        // Check if it's a single-expression interpolation
        identifier.contents match {
          case (expr: Expression) :: Nil =>
            return SupportsInterpolation(expr, spanFrom(start))
          case _ =>
            error("Expected @supports condition.", identifier.span)
        }
      }
    }

    scanner.expectChar(CharCode.$lparen)
    whitespace(consumeNewlines = true)
    if (scanIdentifier("not")) {
      whitespace(consumeNewlines = true)
      val condition = _supportsConditionInParens()
      scanner.expectChar(CharCode.$rparen)
      return SupportsNegation(condition, spanFrom(start))
    } else if (scanner.peekChar() == CharCode.$lparen) {
      val condition = _supportsCondition(inParentheses = true)
      scanner.expectChar(CharCode.$rparen)
      return condition.withSpan(spanFrom(start))
    }

    // Unfortunately, we may have to backtrack here. The grammar is:
    //
    //       Expression ":" Expression
    //     | InterpolatedIdentifier InterpolatedAnyValue?
    //
    // These aren't ambiguous because this `InterpolatedAnyValue` is forbidden
    // from containing a top-level colon, but we still have to parse the full
    // expression to figure out if there's a colon after it.
    //
    // We could avoid the overhead of a full expression parse by looking ahead
    // for a colon (outside of balanced brackets), but in practice we expect the
    // vast majority of real uses to be `Expression ":" Expression`, so it makes
    // sense to parse that case faster in exchange for less code complexity and
    // a slower backtracking case.
    val nameStart        = scanner.state
    val wasInParentheses = _inParentheses
    try {
      val name = _rdExpression(consumeNewlines = true)
      scanner.expectChar(CharCode.$colon)

      val value = _supportsDeclarationValue(name)
      scanner.expectChar(CharCode.$rparen)
      return SupportsDeclaration(name, value, spanFrom(start))
    } catch {
      case _: Exception =>
        scanner.state = nameStart
        _inParentheses = wasInParentheses
    }

    // Backtrack: try parsing as InterpolatedIdentifier + InterpolatedAnyValue
    val identifier = interpolatedIdentifier()
    val tryOp      = _trySupportsOperation(identifier, nameStart)
    if (tryOp.isDefined) {
      scanner.expectChar(CharCode.$rparen)
      return tryOp.get.withSpan(spanFrom(start))
    }

    // If parsing an expression fails, try to parse an
    // `InterpolatedAnyValue` instead. But if that value runs into a
    // top-level colon, then this is probably intended to be a declaration
    // after all, so we rethrow the declaration-parsing error.
    val contentsBuffer = new ssg.sass.InterpolationBuffer()
    contentsBuffer.addInterpolation(identifier)
    try
      contentsBuffer.addInterpolation(
        _interpolatedDeclarationValue(
          allowEmpty = true,
          allowSemicolon = true,
          allowColon = false,
          consumeNewlines = true
        )
      )
    catch {
      case e: Exception =>
        // If we hit a colon, this was probably meant to be a declaration
        if (scanner.peekChar() == CharCode.$colon) throw e
        throw e
    }

    scanner.expectChar(CharCode.$rparen)
    SupportsAnything(contentsBuffer.interpolation(spanFrom(nameStart)), spanFrom(start))
  }

  /** Parses and returns the right-hand side of a declaration in a supports query.
    *
    * dart-sass: `_supportsDeclarationValue` (stylesheet.dart:4640-4653).
    */
  private def _supportsDeclarationValue(name: Expression): Expression =
    name match {
      case se: StringExpression if !se.hasQuotes && se.text.initialPlain.startsWith("--") =>
        StringExpression(_interpolatedDeclarationValue())
      case _ =>
        whitespace(consumeNewlines = true)
        _rdExpression(consumeNewlines = true)
    }

  /** If [interpolation] is followed by `"and"` or `"or"`, parse it as a supports operation.
    *
    * Otherwise, return `null` without moving the scanner position.
    *
    * dart-sass: `_trySupportsOperation` (stylesheet.dart:4658-4695).
    */
  private def _trySupportsOperation(
    interpolation: Interpolation,
    start:         ssg.sass.util.LineScannerState
  ): Nullable[SupportsOperation] = {
    if (interpolation.contents.length != 1) return Nullable.empty
    interpolation.contents.head match {
      case expression: Expression =>
        val beforeWhitespace = scanner.state
        whitespace(consumeNewlines = true)

        var operation: Nullable[SupportsOperation] = Nullable.empty
        var operator:  Nullable[BooleanOperator]   = Nullable.empty
        while (lookingAtIdentifier()) {
          if (operator.isDefined) {
            expectIdentifier(operator.get.toString)
          } else if (scanIdentifier("and")) {
            operator = Nullable(BooleanOperator.And)
          } else if (scanIdentifier("or")) {
            operator = Nullable(BooleanOperator.Or)
          } else {
            scanner.state = beforeWhitespace
            return Nullable.empty
          }

          whitespace(consumeNewlines = true)
          val right = _supportsConditionInParens()
          operation = Nullable(
            SupportsOperation(
              if (operation.isDefined) operation.get
              else SupportsInterpolation(expression, interpolation.span),
              right,
              operator.get,
              spanFrom(start)
            )
          )
          whitespace(consumeNewlines = true)
        }

        operation
      case _ =>
        Nullable.empty
    }
  }

  /** Consumes tokens until it reaches a top-level `";"`, `")"`, `"]"`, or `"}"` and returns their contents as an interpolation with expressions.
    *
    * dart-sass: `_interpolatedDeclarationValue` (stylesheet.dart:3676-3818).
    */
  protected def _interpolatedDeclarationValue(
    allowEmpty:      Boolean = false,
    allowSemicolon:  Boolean = false,
    allowColon:      Boolean = true,
    allowOpenBrace:  Boolean = true,
    endAfterOf:      Boolean = false,
    silentComments:  Boolean = true,
    consumeNewlines: Boolean = false
  ): Interpolation = {
    // NOTE: this logic is largely duplicated in Parser.declarationValue. Most
    // changes here should be mirrored there.

    val start  = scanner.state
    val buffer = new InterpolationBuffer()

    val brackets     = mutable.ArrayBuffer.empty[Int]
    var wroteNewline = false

    boundary {
      while (true) {
        val c = scanner.peekChar()
        if (c < 0) {
          break(())
        } else if (c == CharCode.$backslash) {
          buffer.write(escape(identifierStart = true))
          wroteNewline = false
        } else if (c == CharCode.$double_quote || c == CharCode.$single_quote) {
          buffer.addInterpolation(interpolatedStringToken())
          wroteNewline = false
        } else if (c == CharCode.$slash) {
          val c1 = scanner.peekChar(1)
          if (c1 == CharCode.$asterisk) {
            buffer.write(rawText(() => loudComment()))
            wroteNewline = false
          } else if (c1 == CharCode.$slash && silentComments) {
            silentComment()
            wroteNewline = false
          } else {
            buffer.writeCharCode(scanner.readChar())
            wroteNewline = false
          }
        } else if (c == CharCode.$hash && scanner.peekChar(1) == CharCode.$lbrace) {
          // Add a full interpolated identifier to handle cases like "#{...}--1",
          // since "--1" isn't a valid identifier on its own.
          buffer.addInterpolation(interpolatedIdentifier())
          wroteNewline = false
        } else if (
          (c == CharCode.$space || c == CharCode.$tab) &&
          !wroteNewline && CharCode.isWhitespace(scanner.peekChar(1))
        ) {
          // Collapse whitespace into a single character unless it's following a
          // newline, in which case we assume it's indentation.
          val _ = scanner.readChar()
        } else if (c == CharCode.$space || c == CharCode.$tab) {
          buffer.writeCharCode(scanner.readChar())
        } else if (
          (c == CharCode.$lf || c == CharCode.$cr || c == CharCode.$ff) &&
          indented && !consumeNewlines && brackets.isEmpty
        ) {
          break(())
        } else if (c == CharCode.$lf || c == CharCode.$cr || c == CharCode.$ff) {
          // Collapse multiple newlines into one.
          if (!CharCode.isNewline(scanner.peekChar(-1))) buffer.writeln()
          val _ = scanner.readChar()
          wroteNewline = true
        } else if (c == CharCode.$lbrace && !allowOpenBrace) {
          break(())
        } else if (c == CharCode.$lparen || c == CharCode.$lbrace || c == CharCode.$lbracket) {
          val bracket = scanner.readChar()
          buffer.writeCharCode(bracket)
          brackets += CharCode.opposite(bracket)
          wroteNewline = false
        } else if (c == CharCode.$rparen || c == CharCode.$rbrace || c == CharCode.$rbracket) {
          if (brackets.isEmpty) break(())
          val bracket = brackets.remove(brackets.length - 1)
          scanner.expectChar(bracket)
          buffer.writeCharCode(bracket)
          wroteNewline = false
        } else if (c == CharCode.$semicolon) {
          if (!allowSemicolon && brackets.isEmpty) break(())
          buffer.writeCharCode(scanner.readChar())
          wroteNewline = false
        } else if (c == CharCode.$colon) {
          if (!allowColon && brackets.isEmpty) break(())
          buffer.writeCharCode(scanner.readChar())
          wroteNewline = false
        } else if (c == CharCode.$u || c == CharCode.$U) {
          val beforeUrl = scanner.state
          val ident     = identifier()
          if (
            ident != "url" &&
            // This isn't actually a standard CSS feature, but it was
            // supported by the old `@document` rule so we continue to support
            // it for backwards-compatibility.
            ident != "url-prefix"
          ) {
            buffer.write(ident)
            wroteNewline = false
          } else {
            val urlContents = _tryUrlContents(beforeUrl, name = ident)
            if (urlContents.isDefined) {
              buffer.addInterpolation(urlContents.get)
            } else {
              scanner.state = beforeUrl
              buffer.writeCharCode(scanner.readChar())
            }
            wroteNewline = false
          }
        } else if (c == CharCode.$o || c == CharCode.$O) {
          if (endAfterOf && brackets.isEmpty) {
            val of = rawText(() => scanIdentifier("of", caseSensitive = false))
            if (of != "") {
              buffer.write(of)
              break(())
            }
          }
          buffer.writeCharCode(scanner.readChar())
          wroteNewline = false
        } else if (lookingAtIdentifier()) {
          buffer.write(identifier())
          wroteNewline = false
        } else {
          buffer.writeCharCode(scanner.readChar())
          wroteNewline = false
        }
      }
    }

    if (brackets.nonEmpty) scanner.expectChar(brackets.last)
    if (!allowEmpty && buffer.isEmpty) scanner.error("Expected token.")
    buffer.interpolation(spanFrom(start))
  }

  /** Consumes a block of [child] statements and passes them, as well as the span from [start] to the end of the child block, to [create].
    *
    * dart-sass: `_withChildren` (stylesheet.dart:4770-4778).
    */
  protected def _withChildren[T](
    child:  () => Statement,
    start:  ssg.sass.util.LineScannerState,
    create: (List[Statement], FileSpan) => T
  ): T = {
    val result = create(children(child), spanFrom(start))
    whitespaceWithoutComments(consumeNewlines = false)
    result
  }

  /** dart-sass: `interpolatedString` — delegates to the faithful port above.
    */
  protected def _rdString(): StringExpression = interpolatedString()

  /** dart-sass: `_unaryOperation` (stylesheet.dart:2652-2669). */
  protected def _rdUnaryOperation(): UnaryOperationExpression = {
    val start = scanner.state
    val ch    = scanner.readChar()
    val op    = ch match {
      case CharCode.`$plus`  => UnaryOperator.Plus
      case CharCode.`$minus` => UnaryOperator.Minus
      case CharCode.`$slash` => UnaryOperator.Divide
      case _                 => scanner.error("Expected unary operator.")
    }
    // dart-sass: reject unary operators in plain CSS except `/` (divide).
    if (plainCss && op != UnaryOperator.Divide) {
      scanner.error(
        "Operators aren't allowed in plain CSS.",
        scanner.position - 1,
        1
      )
    }
    whitespace(consumeNewlines = true)
    val operand = _rdSingleExpression()
    UnaryOperationExpression(op, operand, spanFrom(start))
  }

  /** dart-sass: `identifierLike` (stylesheet.dart:2945-3046).
    *
    * Parses an expression starting with an identifier (possibly interpolated). Dispatches to: keywords (`true`/`false`/`null`/`not`), color names, special functions
    * (`calc`/`url`/`element`/`expression`/`progid`), `if()`, namespaced references (`ns.foo`/`ns.$var`), regular function calls, or falls back to a StringExpression.
    */
  protected def _rdIdentifierLike(): Expression = {
    val start    = scanner.state
    val ident    = interpolatedIdentifier()
    val plainOpt = ident.asPlain.toOption

    // dart-sass lines 2951-3017: plain identifier handling
    plainOpt match {
      case Some(plain) =>
        // CSS if() / legacy if() disambiguation (dart-sass lines 2951-2980).
        // Try legacy comma-separated if($cond, $true, $false) first; on parse
        // failure, backtrack and parse as CSS if(condition: value; else: default).
        if (plain == "if" && scanner.peekChar() == CharCode.$lparen) {
          val beforeParen = scanner.state
          try {
            val args      = _rdArgumentInvocation(start)
            val finalArgs = _rdMaybeUnpackColorArgs(plain, args)
            return if (finalArgs.positional.length == 3 && finalArgs.named.isEmpty) {
              LegacyIfExpression(finalArgs, spanFrom(start))
            } else {
              FunctionExpression(plain, finalArgs, spanFrom(start))
            }
          } catch {
            case _: Exception =>
              scanner.state = beforeParen
              return _rdIfExpression(start)
          }
        } else if (plain.toLowerCase == "if" && scanner.peekChar() == CharCode.$lparen) {
          return _rdIfExpression(start)
        }

        // dart-sass line 2981-2989: `not` unary operator
        if (plain == "not") {
          whitespace(consumeNewlines = true)
          val operand = _rdSingleExpression()
          return UnaryOperationExpression(
            UnaryOperator.Not,
            operand,
            ident.span.expand(operand.span)
          )
        }

        val lower = plain.toLowerCase
        // dart-sass lines 2992-3011: non-function keywords and color names
        if (scanner.peekChar() != CharCode.$lparen) {
          plain match {
            case "false" => return BooleanExpression(value = false, ident.span)
            case "null"  => return new NullExpression(ident.span)
            case "true"  => return BooleanExpression(value = true, ident.span)
            case _       =>
              ColorNames.colorsByName.get(lower) match {
                case Some(color) =>
                  val namedColor = ssg.sass.value.SassColor.rgbInternal(
                    Nullable(color.channel0),
                    Nullable(color.channel1),
                    Nullable(color.channel2),
                    Nullable(color.alpha),
                    Nullable(new ssg.sass.value.SpanColorFormat(ident.span.text))
                  )
                  return ColorExpression(namedColor, ident.span)
                case None => ()
              }
          }
        }

        // dart-sass line 3014: trySpecialFunction
        val specialFn = _rdTrySpecialFunction(lower, start)
        if (specialFn.isDefined) return specialFn.get

        // dart-sass lines 3019-3046: dot / function call / string
        scanner.peekChar() match {
          case CharCode.`$dot` if scanner.peekChar(1) == CharCode.$dot =>
            // `foo..` → just an identifier (rest arg context)
            return StringExpression(ident)
          case CharCode.`$dot` =>
            val _ = scanner.readChar()
            return _rdNamespacedExpression(plain, start)
          case CharCode.`$lparen` =>
            val args = _rdArgumentInvocation(start, allowEmptySecondArg = lower == "var")
            return FunctionExpression(plain, _rdMaybeUnpackColorArgs(plain, args), spanFrom(start))
          case _ =>
            return StringExpression(ident)
        }

      case None =>
        // Interpolated identifier — can only be a function call or plain string
        scanner.peekChar() match {
          case CharCode.`$dot` if scanner.peekChar(1) == CharCode.$dot =>
            return StringExpression(ident)
          case CharCode.`$dot` =>
            error("Interpolation isn't allowed in namespaces.", ident.span)
          case CharCode.`$lparen` =>
            val args = _rdArgumentInvocation(start)
            return InterpolatedFunctionExpression(ident, args, spanFrom(start))
          case _ =>
            return StringExpression(ident)
        }
    }
  }

  /** dart-sass: `trySpecialFunction` (stylesheet.dart:3321-3440).
    *
    * If [name] is the name of a function with special syntax, consumes it. Otherwise returns Nullable.empty.
    */
  protected def _rdTrySpecialFunction(name: String, start: ssg.sass.util.LineScannerState): Nullable[Expression] = {
    // dart-sass stylesheet.dart:3328: `type()` is handled before unvendor
    if (name == "type" && scanner.scanChar(CharCode.$lparen)) {
      val buffer = new InterpolationBuffer()
      buffer.write(name)
      buffer.writeCharCode(CharCode.$lparen)
      buffer.addInterpolation(_rdInterpolatedDeclarationValue(allowEmpty = true))
      scanner.expectChar(CharCode.$rparen)
      buffer.writeCharCode(CharCode.$rparen)
      return Nullable(StringExpression(buffer.interpolation(spanFrom(start))))
    }
    val normalized = Utils.unvendor(name)
    val vendored   = normalized != name
    val lower      = normalized.toLowerCase
    lower match {
      case "calc" if vendored && scanner.scanChar(CharCode.$lparen) =>
        val buffer = new InterpolationBuffer()
        buffer.write(name)
        buffer.writeCharCode(CharCode.$lparen)
        buffer.addInterpolation(_rdInterpolatedDeclarationValue(allowEmpty = true))
        scanner.expectChar(CharCode.$rparen)
        buffer.writeCharCode(CharCode.$rparen)
        Nullable(StringExpression(buffer.interpolation(spanFrom(start))))

      case "expression" if scanner.scanChar(CharCode.$lparen) =>
        val buffer = new InterpolationBuffer()
        buffer.write(name)
        buffer.writeCharCode(CharCode.$lparen)
        buffer.addInterpolation(_rdInterpolatedDeclarationValue(allowEmpty = true))
        scanner.expectChar(CharCode.$rparen)
        buffer.writeCharCode(CharCode.$rparen)
        Nullable(StringExpression(buffer.interpolation(spanFrom(start))))

      case "element" if scanner.scanChar(CharCode.$lparen) =>
        val buffer = new InterpolationBuffer()
        buffer.write(name)
        buffer.writeCharCode(CharCode.$lparen)
        buffer.addInterpolation(_rdInterpolatedDeclarationValue(allowEmpty = true))
        scanner.expectChar(CharCode.$rparen)
        buffer.writeCharCode(CharCode.$rparen)
        Nullable(StringExpression(buffer.interpolation(spanFrom(start))))

      case "progid" if scanner.scanChar(CharCode.$colon) =>
        val buffer = new InterpolationBuffer()
        buffer.write(name)
        buffer.writeCharCode(CharCode.$colon)
        var next = scanner.peekChar()
        while (next >= 0 && (CharCode.isAlphabetic(next) || next == CharCode.$dot)) {
          buffer.writeCharCode(scanner.readChar())
          next = scanner.peekChar()
        }
        scanner.expectChar(CharCode.$lparen)
        buffer.writeCharCode(CharCode.$lparen)
        buffer.addInterpolation(_rdInterpolatedDeclarationValue(allowEmpty = true))
        scanner.expectChar(CharCode.$rparen)
        buffer.writeCharCode(CharCode.$rparen)
        Nullable(StringExpression(buffer.interpolation(spanFrom(start))))

      case "url" =>
        val contents = _rdTryUrlContents(start, vendored = vendored)
        if (contents.isDefined)
          Nullable(StringExpression(contents.get))
        else
          Nullable.empty

      case _ => Nullable.empty
    }
  }

  // ---------------------------------------------------------------------------
  // CSS if() expression parsing
  // Ported from dart-sass `ifExpression` / `_ifConditionExpression` /
  // `_ifGroup` in lib/src/parse/stylesheet.dart (lines 3050-3130).
  // ---------------------------------------------------------------------------

  /** Parses a CSS `if()` expression: `if(condition: value; else: default)`. Called after the `if` identifier has been read.
    */
  private def _rdIfExpression(start: ssg.sass.util.LineScannerState): IfExpression = {
    scanner.expectChar(CharCode.$lparen)
    whitespace(consumeNewlines = true)
    val branches = scala.collection.mutable.ListBuffer.empty[(Nullable[IfConditionExpression], Expression)]
    while (scanner.peekChar() != CharCode.$rparen) {
      val condition: Nullable[IfConditionExpression] =
        if (scanIdentifier("else")) Nullable.empty
        else Nullable(_rdIfConditionExpression())
      whitespace(consumeNewlines = true)
      scanner.expectChar(CharCode.$colon)
      whitespace(consumeNewlines = true)
      val value = _rdExpression(
        consumeNewlines = true,
        until = { () =>
          val ch = scanner.peekChar()
          ch == CharCode.$semicolon || ch == CharCode.$rparen
        }
      )
      branches += ((condition, value))
      whitespace(consumeNewlines = true)
      if (!scanner.scanChar(CharCode.$semicolon)) {
        // No semicolon means this is the last branch — break the loop
        // by letting the while condition (peekChar != ')') handle it
        whitespace(consumeNewlines = true)
        // If there are more characters and they aren't ')', that's an error
        // but expectChar($rparen) below will catch it.
      } else {
        whitespace(consumeNewlines = true)
      }
    }
    scanner.expectChar(CharCode.$rparen)
    IfExpression(branches.toList, spanFrom(start))
  }

  /** Parses a condition expression with optional `and`/`or` chaining.
    *
    * dart-sass: `_ifConditionExpression` (stylesheet.dart:3069-3130).
    */
  private def _rdIfConditionExpression(): IfConditionExpression = {
    val start = scanner.state
    // `not` prefix
    if (scanIdentifier("not")) {
      if (scanner.peekChar() == CharCode.$lparen) {
        scanner.error("Whitespace is required between \"not\" and \"(\"")
      }
      whitespace(consumeNewlines = true)
      val group = _rdIfGroup()
      return IfConditionNegation(group, spanFrom(start))
    }

    val groups = scala.collection.mutable.ListBuffer(_rdIfGroup())
    var op: Nullable[BooleanOperator] = Nullable.empty

    whitespace(consumeNewlines = true)
    boundary {
      while (true) {
        if (!op.exists(_ == BooleanOperator.Or) && scanIdentifier("and")) {
          if (scanner.peekChar() == CharCode.$lparen) {
            scanner.error("Whitespace is required between \"and\" and \"(\"")
          }
          whitespace(consumeNewlines = true)
          if (op.isEmpty) op = Nullable(BooleanOperator.And)
          groups += _rdIfGroup()
        } else if (!op.exists(_ == BooleanOperator.And) && scanIdentifier("or")) {
          if (scanner.peekChar() == CharCode.$lparen) {
            scanner.error("Whitespace is required between \"or\" and \"(\"")
          }
          whitespace(consumeNewlines = true)
          if (op.isEmpty) op = Nullable(BooleanOperator.Or)
          groups += _rdIfGroup()
        } else {
          val next = if (!scanner.isDone) scanner.peekChar() else -1
          if (
            next >= 0 && next != CharCode.$rparen && next != CharCode.$colon &&
            groups.last.isArbitrarySubstitution
          ) {
            val preceding =
              if (groups.size == 1) groups.head
              else IfConditionOperation(groups.toList, op.get)
            return _rdIfConditionRaw(preceding, _rdIfGroup())
          } else {
            _rdTryArbitrarySubstitution() match {
              case sub if sub.isDefined =>
                val preceding =
                  if (groups.size == 1) groups.head
                  else IfConditionOperation(groups.toList, op.get)
                return _rdIfConditionRaw(preceding, sub.get)
              case _ =>
                break(())
            }
          }
        }
        whitespace(consumeNewlines = true)
      }
    }

    if (groups.size == 1) groups.head
    else IfConditionOperation(groups.toList, op.get)
  }

  /** Consumes the remainder of a condition expression as raw interpolation when arbitrary substitution forces serialization to text.
    *
    * dart-sass: `_ifConditionRaw` (stylesheet.dart:3142-3210).
    */
  private def _rdIfConditionRaw(
    preceding: IfConditionExpression,
    next:      IfConditionExpression
  ): IfConditionRaw = {
    val substitution: IfConditionExpression =
      if (preceding.isArbitrarySubstitution) preceding
      else
        preceding match {
          case IfConditionOperation(exprs, _) if exprs.last.isArbitrarySubstitution =>
            exprs.last
          case _ if next.isArbitrarySubstitution => next
          case _                                 =>
            throw new IllegalArgumentException(
              s"Either $preceding must end with an arbitrary substitution or $next must be one."
            )
        }

    val buffer = new InterpolationBuffer()
    buffer.addInterpolation(preceding.toInterpolation(substitution))
    buffer.writeCharCode(' ')
    buffer.addInterpolation(next.toInterpolation(substitution))

    var lastGroup: IfConditionExpression     = next
    var op:        Nullable[BooleanOperator] =
      if (preceding.isInstanceOf[IfConditionOperation])
        Nullable(preceding.asInstanceOf[IfConditionOperation].op)
      else Nullable.empty

    whitespace(consumeNewlines = true)
    boundary {
      while (true) {
        if (!op.exists(_ == BooleanOperator.Or) && scanIdentifier("and")) {
          if (scanner.peekChar() == CharCode.$lparen) {
            scanner.error("Whitespace is required between \"and\" and \"(\"")
          }
          whitespace(consumeNewlines = true)
          if (op.isEmpty) op = Nullable(BooleanOperator.And)
          lastGroup = _rdIfGroup()
          buffer.write(" and ")
          buffer.addInterpolation(lastGroup.toInterpolation(substitution))
        } else if (!op.exists(_ == BooleanOperator.And) && scanIdentifier("or")) {
          if (scanner.peekChar() == CharCode.$lparen) {
            scanner.error("Whitespace is required between \"or\" and \"(\"")
          }
          whitespace(consumeNewlines = true)
          if (op.isEmpty) op = Nullable(BooleanOperator.Or)
          lastGroup = _rdIfGroup()
          whitespace(consumeNewlines = true)
          buffer.write(" or ")
          buffer.addInterpolation(lastGroup.toInterpolation(substitution))
        } else {
          val nextCh = if (!scanner.isDone) scanner.peekChar() else -1
          if (
            nextCh >= 0 && nextCh != CharCode.$rparen && nextCh != CharCode.$colon &&
            lastGroup.isArbitrarySubstitution
          ) {
            lastGroup = _rdIfGroup()
            buffer.writeCharCode(' ')
            buffer.addInterpolation(lastGroup.toInterpolation(substitution))
          } else {
            _rdTryArbitrarySubstitution() match {
              case sub if sub.isDefined =>
                lastGroup = sub.get
                buffer.writeCharCode(' ')
                buffer.addInterpolation(lastGroup.toInterpolation(substitution))
              case _ =>
                break(())
            }
          }
        }
        whitespace(consumeNewlines = true)
      }
    }

    IfConditionRaw(buffer.interpolation(preceding.span.expand(scanner.emptySpan)))
  }

  /** Consumes a grouped expression in a CSS-style `if()` condition.
    *
    * dart-sass: `_ifGroup` (stylesheet.dart:3213-3261).
    */
  private def _rdIfGroup(): IfConditionExpression = {
    val start = scanner.state

    scanner.peekChar() match {
      case CharCode.`$lparen` =>
        scanner.expectChar(CharCode.$lparen)
        whitespace(consumeNewlines = true)
        val expression = _rdIfConditionExpression()
        whitespace(consumeNewlines = true)
        scanner.expectChar(CharCode.$rparen)
        IfConditionParenthesized(expression, spanFrom(start))

      case _ if scanIdentifier("sass", caseSensitive = true) =>
        scanner.expectChar(CharCode.$lparen)
        whitespace(consumeNewlines = true)
        val expression = _rdExpression(consumeNewlines = true)
        whitespace(consumeNewlines = true)
        scanner.expectChar(CharCode.$rparen)
        if (plainCss) {
          error("sass() conditions aren't allowed in plain CSS", spanFrom(start))
        }
        IfConditionSass(expression, spanFrom(start))

      case _ =>
        val ident = interpolatedIdentifier()
        // Single-expression interpolation without `(` is raw text
        ident match {
          case interp
              if interp.contents.length == 1 && interp.contents.head.isInstanceOf[Expression] &&
                scanner.peekChar() != CharCode.$lparen =>
            return IfConditionRaw(ident)
          case interp =>
            interp.asPlain match {
              case plain
                  if plain.isDefined &&
                    Set("and", "or", "not").contains(plain.get.toLowerCase) &&
                    scanner.peekChar() == CharCode.$lparen =>
                scanner.error(s"Whitespace is required between \"$ident\" and \"(\"")
              case _ => ()
            }
        }
        scanner.expectChar(CharCode.$lparen)
        whitespace(consumeNewlines = true)
        val expression = _rdInterpolatedDeclarationValue(
          allowEmpty = true,
          allowSemicolon = true
        )
        whitespace(consumeNewlines = true)
        scanner.expectChar(CharCode.$rparen)
        IfConditionFunction(ident, expression, spanFrom(start))
    }
  }

  /** Tries to consume an arbitrary substitution expression. Returns Nullable.empty if there isn't one.
    *
    * dart-sass: `_tryArbitrarySubstitution` (stylesheet.dart:3265-3295).
    */
  private def _rdTryArbitrarySubstitution(): Nullable[IfConditionExpression] = {
    if (scanner.peekChar() == CharCode.$hash) {
      val (expression, espan) = singleInterpolation()
      val buf                 = new InterpolationBuffer()
      buf.add(expression, espan)
      return Nullable(IfConditionRaw(buf.interpolation(espan)))
    }

    val start = scanner.state
    val name: Nullable[Interpolation] =
      if (scanIdentifier("if")) Nullable(Interpolation.plain("if", spanFrom(start)))
      else if (scanIdentifier("var")) Nullable(Interpolation.plain("var", spanFrom(start)))
      else if (scanIdentifier("attr")) Nullable(Interpolation.plain("attr", spanFrom(start)))
      else if (scanner.matches("--")) Nullable(interpolatedIdentifier())
      else Nullable.empty

    if (name.isEmpty) return Nullable.empty
    if (!scanner.scanChar(CharCode.$lparen)) {
      scanner.state = start
      return Nullable.empty
    }

    val arguments = _rdInterpolatedDeclarationValue(
      allowEmpty = true,
      allowSemicolon = true
    )
    scanner.expectChar(CharCode.$rparen)

    Nullable(IfConditionFunction(name.get, arguments, spanFrom(start)))
  }

  /** dart-sass: `_tryUrlContents` (stylesheet.dart:3442-3524).
    *
    * Like `_urlContents`, but returns Nullable.empty if the URL fails to parse.
    */
  protected def _rdTryUrlContents(
    start:    ssg.sass.util.LineScannerState,
    name:     String = "url",
    vendored: Boolean = false
  ): Nullable[Interpolation] = {
    val beginningOfContents = scanner.state
    if (!scanner.scanChar(CharCode.$lparen)) return Nullable.empty

    whitespaceWithoutComments(consumeNewlines = true)

    // Match Ruby Sass's behavior: parse a raw URL() if possible, and if not
    // backtrack and re-parse as a function expression.
    val buffer = new InterpolationBuffer()
    buffer.write(name)
    buffer.writeCharCode(CharCode.$lparen)
    boundary {
      while (true) {
        val c = scanner.peekChar()
        if (c < 0) {
          break(())
        } else if (c == CharCode.$backslash) {
          buffer.write(escape())
        } else if (c == CharCode.$hash && scanner.peekChar(1) == CharCode.$lbrace) {
          val (expr, span) = singleInterpolation()
          buffer.add(expr, span)
        } else if (c == CharCode.$rparen) {
          buffer.writeCharCode(scanner.readChar())
          return Nullable(buffer.interpolation(spanFrom(start)))
        } else if (
          c == CharCode.$exclamation || c == CharCode.$percent ||
          c == CharCode.$ampersand || c == CharCode.$hash ||
          (c >= CharCode.$asterisk && c <= CharCode.$tilde) ||
          c >= 0x80
        ) {
          buffer.writeCharCode(scanner.readChar())
        } else if (CharCode.isWhitespace(c)) {
          whitespaceWithoutComments(consumeNewlines = true)
          if (scanner.peekChar() != CharCode.$rparen) break(())
        } else {
          break(())
        }
      }
    }
    scanner.state = beginningOfContents
    Nullable.empty
  }

  /** dart-sass: `_interpolatedDeclarationValue` (stylesheet.dart:3652-3814).
    *
    * Consumes tokens until it reaches a top-level `;`, `)`, `]`, or `}` and returns their contents as an Interpolation.
    */
  protected def _rdInterpolatedDeclarationValue(
    allowEmpty:     Boolean = false,
    allowSemicolon: Boolean = false,
    allowColon:     Boolean = true
  ): Interpolation = {
    val start        = scanner.state
    val buffer       = new InterpolationBuffer()
    val brackets     = scala.collection.mutable.ListBuffer.empty[Int]
    var wroteNewline = false

    boundary {
      while (true) {
        val c = scanner.peekChar()
        if (c < 0) {
          break(())
        } else if (c == CharCode.$backslash) {
          buffer.write(escape(identifierStart = true))
          wroteNewline = false
        } else if (c == CharCode.$double_quote || c == CharCode.$single_quote) {
          // dart-sass uses interpolatedStringToken() which includes the quote
          // characters in the Interpolation. We wrap the quote chars manually.
          val q = scanner.peekChar()
          buffer.writeCharCode(q)
          buffer.addInterpolation(interpolatedString().text)
          buffer.writeCharCode(q)
          wroteNewline = false
        } else if (c == CharCode.$slash) {
          scanner.peekChar(1) match {
            case CharCode.`$asterisk` =>
              val beforeComment = scanner.state
              loudComment()
              buffer.write(scanner.substring(beforeComment.position))
            case CharCode.`$slash` =>
              silentComment()
              wroteNewline = false
            case _ =>
              buffer.writeCharCode(scanner.readChar())
              wroteNewline = false
          }
        } else if (c == CharCode.$hash && scanner.peekChar(1) == CharCode.$lbrace) {
          buffer.addInterpolation(interpolatedIdentifier())
          wroteNewline = false
        } else if (c == CharCode.$space || c == CharCode.$tab) {
          if (!wroteNewline && scanner.peekChar(1) >= 0 && CharCode.isWhitespace(scanner.peekChar(1))) {
            scanner.readChar() // collapse whitespace
          } else {
            buffer.writeCharCode(scanner.readChar())
          }
        } else if (CharCode.isNewline(c)) {
          if (!scanner.isDone && scanner.position > 0 && !CharCode.isNewline(scanner.peekChar(-1))) {
            buffer.write("\n")
          }
          scanner.readChar()
          wroteNewline = true
        } else if (c == CharCode.$lparen || c == CharCode.$lbrace || c == CharCode.$lbracket) {
          val bracket = scanner.readChar()
          buffer.writeCharCode(bracket)
          brackets += CharCode.opposite(bracket)
          wroteNewline = false
        } else if (c == CharCode.$rparen || c == CharCode.$rbrace || c == CharCode.$rbracket) {
          if (brackets.isEmpty) break(())
          val expected = brackets.remove(brackets.length - 1)
          scanner.expectChar(expected)
          buffer.writeCharCode(expected)
          wroteNewline = false
        } else if (c == CharCode.$semicolon) {
          if (!allowSemicolon && brackets.isEmpty) break(())
          buffer.writeCharCode(scanner.readChar())
          wroteNewline = false
        } else if (c == CharCode.$colon) {
          if (!allowColon && brackets.isEmpty) break(())
          buffer.writeCharCode(scanner.readChar())
          wroteNewline = false
        } else if (c == CharCode.$u || c == CharCode.$U) {
          val beforeUrl = scanner.state
          val ident     = identifier()
          if (ident != "url" && ident != "url-prefix") {
            buffer.write(ident)
            wroteNewline = false
          } else {
            val contents = _rdTryUrlContents(beforeUrl, name = ident)
            if (contents.isDefined) {
              buffer.addInterpolation(contents.get)
            } else {
              scanner.state = beforeUrl
              buffer.writeCharCode(scanner.readChar())
            }
            wroteNewline = false
          }
        } else if (lookingAtIdentifier()) {
          buffer.write(identifier())
          wroteNewline = false
        } else {
          buffer.writeCharCode(scanner.readChar())
          wroteNewline = false
        }
      }
    }

    if (brackets.nonEmpty) scanner.expectChar(brackets.last)
    if (!allowEmpty && buffer.isEmpty) scanner.error("Expected token.")
    buffer.interpolation(spanFrom(start))
  }

  /** dart-sass: `_functionCall` convenience wrapper — given that the scanner is positioned at `name(`, parses the argument list.
    */
  protected def _rdFunctionCall(name: String, start: ssg.sass.util.LineScannerState): FunctionExpression = {
    val args = _rdArgumentInvocation(start)
    FunctionExpression(name, args, spanFrom(start))
  }

  /** dart-sass: `_namespacedExpression` (stylesheet.dart:2715-2763).
    *
    * Parses an expression within a namespace (`ns.$var` or `ns.fn()`). Also used by `_rdIdentifierLike` when a `.` follows an identifier.
    */
  protected def _rdNamespacedExpression(
    namespace: String,
    start:     ssg.sass.util.LineScannerState
  ): Expression =
    if (scanner.peekChar() == CharCode.$dollar) {
      val _ = scanner.readChar()
      val n = identifier()
      if (_isPrivateMember(n))
        error(
          "Private members can't be accessed from outside their modules.",
          spanFrom(start)
        )
      VariableExpression(n.replace('_', '-'), spanFrom(start), Nullable(namespace))
    } else {
      // dart-sass: namespacedExpression (stylesheet.dart:3312-3319) calls
      // _publicIdentifier() then _argumentInvocation(). A bare `namespace.member`
      // without parentheses is invalid — _argumentInvocation expects `(`.
      val member = _publicIdentifier()
      val args = _rdArgumentInvocation(start)
      FunctionExpression(member, args, spanFrom(start), Nullable(namespace))
    }

  /** True if [name] is a module-private member identifier (starts with `_` or `-`). Matches dart-sass `isPrivate` in `lib/src/util/character.dart`.
    */
  private def _isPrivateMember(name: String): Boolean =
    if (name.isEmpty) false
    else {
      val c = name.charAt(0)
      c == '_' || c == '-'
    }

  /** Returns whether the scanner is immediately before a SassScript expression.
    *
    * dart-sass: `_lookingAtExpression` (lines 4741-4764).
    */
  protected def _lookingAtExpression(): Boolean = {
    val c = scanner.peekChar()
    if (c < 0) return false
    c match {
      case CharCode.`$dot` =>
        scanner.peekChar(1) != CharCode.$dot
      case CharCode.`$exclamation` =>
        val c1 = scanner.peekChar(1)
        c1 < 0 || c1 == 'i'.toInt || c1 == 'I'.toInt || CharCode.isWhitespace(c1)
      case CharCode.`$lparen` | CharCode.`$slash` | CharCode.`$lbracket` | CharCode.`$single_quote` | CharCode.`$double_quote` | CharCode.`$hash` | CharCode.`$plus` | CharCode.`$minus` |
          CharCode.`$backslash` | CharCode.`$dollar` | CharCode.`$ampersand` | CharCode.`$percent` =>
        true
      case _ =>
        CharCode.isNameStart(c) || CharCode.isDigit(c)
    }
  }

  /** Consumes an expression until a top-level comma.
    *
    * dart-sass: `expressionUntilComma` (lines 2409-2415).
    */
  protected def expressionUntilComma(singleEquals: Boolean = false): Expression =
    _rdExpression(stopAtComma = true, consumeNewlines = true, singleEquals = singleEquals)

  /** dart-sass: `_argumentInvocation` (lines 1862-1953). Parses `(a, b, $c: d, rest..., kwRest...)`.
    */
  protected def _rdArgumentInvocation(
    start:               ssg.sass.util.LineScannerState,
    mixin:               Boolean = false,
    allowEmptySecondArg: Boolean = false
  ): ArgumentList = {
    scanner.expectChar(CharCode.$lparen)
    whitespace(consumeNewlines = true)

    val positional = mutable.ListBuffer.empty[Expression]
    val named      = mutable.LinkedHashMap.empty[String, Expression]
    val namedSpans = mutable.LinkedHashMap.empty[String, FileSpan]
    var rest:        Nullable[Expression] = Nullable.empty
    var keywordRest: Nullable[Expression] = Nullable.empty

    boundary {
      while (_lookingAtExpression()) {
        val expression = expressionUntilComma(singleEquals = !mixin)
        whitespace(consumeNewlines = true)

        expression match {
          case ve: VariableExpression if scanner.scanChar(CharCode.$colon) =>
            // Named argument: `$name: value`
            whitespace(consumeNewlines = true)
            if (named.contains(ve.name))
              error("Duplicate argument.", ve.span)
            val value = expressionUntilComma(singleEquals = !mixin)
            named.put(ve.name, value)
            namedSpans.put(ve.name, ve.span.expand(value.span))

          case _ if scanner.scanChar(CharCode.$dot) =>
            // Rest argument: `expr...`
            scanner.expectChar(CharCode.$dot)
            scanner.expectChar(CharCode.$dot)
            if (rest.isEmpty) {
              rest = Nullable(expression)
            } else {
              keywordRest = Nullable(expression)
              whitespace(consumeNewlines = true)
              if (scanner.scanChar(CharCode.$comma)) whitespace(consumeNewlines = true)
              break(())
            }

          case _ if named.nonEmpty =>
            error(
              "Positional arguments must come before keyword arguments.",
              expression.span
            )

          case _ =>
            positional += expression
        }

        whitespace(consumeNewlines = true)
        if (!scanner.scanChar(CharCode.$comma)) break(())
        whitespace(consumeNewlines = true)

        if (
          allowEmptySecondArg &&
          positional.length == 1 &&
          named.isEmpty &&
          rest.isEmpty &&
          scanner.peekChar() == CharCode.$rparen
        ) {
          positional += StringExpression.plain("", scanner.emptySpan)
          break(())
        }
      }
    }
    scanner.expectChar(CharCode.$rparen)
    new ArgumentList(
      positional.toList,
      named.toMap,
      namedSpans.toMap,
      spanFrom(start),
      rest,
      keywordRest
    )
  }

  /** dart-sass: `_callableArguments` — alias for `_argumentInvocation`, kept as a separate entry point to mirror the dart naming.
    */
  protected def _rdCallableArguments(start: ssg.sass.util.LineScannerState): ArgumentList =
    _rdArgumentInvocation(start)

  /** Modern CSS color-function argument form: `lab(50% 20 -30)` is parsed as a single space-separated list argument by the generic argument invocation path, but the underlying color built-in expects
    * three (or four, with trailing alpha) positional arguments. For the color-function allowlist, if the call has exactly one positional argument and it's a space-separated ListExpression, unpack its
    * elements into positional arguments. Mirrors `_tryParseFunctionCall`'s `isColorFn` handling in the text-based path.
    */
  private def _rdMaybeUnpackColorArgs(name: String, args: ArgumentList): ArgumentList = {
    // Only unpack legacy color functions that accept both comma-separated and
    // modern space-separated syntax. Modern-only functions (lab, lch, oklab,
    // oklch, color) should NOT be unpacked — they only use single-arg modern
    // syntax and route through parseChannels which handles special values.
    val isColorFn =
      name == "rgb" || name == "rgba" || name == "hsl" || name == "hsla" || name == "hwb"
    if (!isColorFn || args.positional.length != 1 || args.named.nonEmpty) return args
    args.positional.head match {
      case list: ListExpression if list.separator == ListSeparator.Space && !list.hasBrackets && list.contents.size == 3 =>
        // Don't unpack if the list contains a slash-separated element (like
        // `255 / 0.5` which represents channel / alpha). The built-in's
        // parseChannels / parseSlashChannels path handles this correctly
        // at evaluation time by extracting the asSlash pair.
        // Also don't unpack lists with < 3 elements — those can't be a
        // full set of color channels and should route through parseChannels
        // (which handles multi_argument_var correctly).
        // Don't unpack relative color syntax: `rgb(from #aaa r g b)`
        val startsWithFrom = list.contents.headOption match {
          case Some(StringExpression(interp, false)) if interp.isPlain && interp.asPlain.toOption.exists(_.toLowerCase == "from") => true
          case _                                                                                                                  => false
        }
        val hasSlash = list.contents.exists {
          case b: BinaryOperationExpression if b.operator == BinaryOperator.DividedBy => true
          case _ => false
        }
        // Don't unpack if any element is the `none` keyword (unquoted
        // string). `none` is a CSS Color Level 4 missing-channel marker
        // and must route through the `$channels` / `parseChannels` path
        // which handles it correctly.
        val hasNone = list.contents.exists {
          case StringExpression(interp, false) if interp.isPlain && interp.asPlain.toOption.exists(_.toLowerCase == "none") => true
          case _                                                                                                            => false
        }
        if (hasSlash || startsWithFrom || hasNone) args
        else
          new ArgumentList(
            list.contents,
            args.named,
            args.namedSpans,
            args.span,
            args.rest,
            args.keywordRest
          )
      case _ => args
    }
  }

  /** dart-sass: `_unicodeRange` (stylesheet.dart:2764-2813).
    *
    * Consumes a CSS unicode range expression: `U+1234`, `U+0?00`, `U+0020-007F`. Wildcard `?` characters fill remaining positions. The scanner must be positioned at the `U`/`u` character.
    */
  private def _rdUnicodeRange(): StringExpression = {
    val start = scanner.state
    expectIdentChar(CharCode.$u)
    scanner.expectChar(CharCode.$plus)

    var firstRangeLength = 0
    while (scanner.peekChar() >= 0 && CharCode.isHex(scanner.peekChar())) {
      scanner.readChar()
      firstRangeLength += 1
    }

    var hasQuestionMark = false
    while (scanner.scanChar(CharCode.$question)) {
      hasQuestionMark = true
      firstRangeLength += 1
    }

    if (firstRangeLength == 0) {
      scanner.error("Expected hex digit or \"?\".")
    } else if (firstRangeLength > 6) {
      error("Expected at most 6 digits.", spanFrom(start))
    } else if (hasQuestionMark) {
      return StringExpression(
        Interpolation.plain(scanner.substring(start.position), spanFrom(start)),
        hasQuotes = false
      )
    }

    if (scanner.scanChar(CharCode.$minus)) {
      val secondRangeStart  = scanner.state
      var secondRangeLength = 0
      while (scanner.peekChar() >= 0 && CharCode.isHex(scanner.peekChar())) {
        scanner.readChar()
        secondRangeLength += 1
      }

      if (secondRangeLength == 0) {
        scanner.error("Expected hex digit.")
      } else if (secondRangeLength > 6) {
        error("Expected at most 6 digits.", spanFrom(secondRangeStart))
      }
    }

    if (_lookingAtInterpolatedIdentifierBody()) {
      scanner.error("Expected end of identifier.")
    }

    StringExpression(
      Interpolation.plain(scanner.substring(start.position), spanFrom(start)),
      hasQuotes = false
    )
  }

  /** dart-sass: `_unaryOperatorFor` — kept for potential stage-2 use. */
  protected def _rdUnaryOperatorFor(ch: Int): Option[UnaryOperator] = ch match {
    case CharCode.`$plus`  => Some(UnaryOperator.Plus)
    case CharCode.`$minus` => Some(UnaryOperator.Minus)
    case CharCode.`$slash` => Some(UnaryOperator.Divide)
    case _                 => None
  }
}
