/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/parse/css.dart
 * Original: Copyright (c) 2018 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: css.dart -> CssParser.scala
 *   Idiom: dart-sass CssParser overrides individual recursive-descent entry
 *     points (atRule/silentComment/identifierLike/parentheses) to reject
 *     Sass-only syntax as soon as it is scanned. ssg-sass's StylesheetParser
 *     uses a different, mostly-private dispatch (`_topLevelStatement`,
 *     `_atRule`, `_variableDeclaration`) so the per-entry-point override
 *     strategy isn't available without refactoring the base class.
 *
 *     Instead, CssParser runs the ScssParser over the source to build an
 *     AST, then walks that AST and rejects any Sass-only node with a
 *     `SassFormatException("... isn't allowed in plain CSS.")`. This
 *     preserves source spans on errors, matches the dart-sass user-visible
 *     behavior (plain CSS rejects Sass features with a format error), and
 *     keeps the port in line with the no-op skeleton that preceded it.
 */
package ssg
package sass
package parse

import ssg.sass.Nullable
import ssg.sass.SassFormatException
import ssg.sass.ast.sass.{
  AtRootRule,
  AtRule,
  BinaryOperationExpression,
  DebugRule,
  Declaration,
  DynamicImport,
  EachRule,
  ErrorRule,
  Expression,
  ExtendRule,
  ForRule,
  ForwardRule,
  FunctionExpression,
  FunctionRule,
  IfRule,
  ImportRule,
  IncludeRule,
  InterpolatedFunctionExpression,
  Interpolation,
  ListExpression,
  LoudComment,
  MapExpression,
  MediaRule,
  MixinRule,
  ReturnRule,
  SelectorExpression,
  SilentComment,
  Statement,
  StaticImport,
  StringExpression,
  StyleRule,
  Stylesheet,
  SupportsRule,
  UnaryOperationExpression,
  UseRule,
  VariableDeclaration,
  VariableExpression,
  WarnRule,
  WhileRule
}

/** A parser for plain CSS.
  *
  * Extends [[ScssParser]] and, after parsing, walks the resulting AST to reject any Sass-only syntax (variables, `@mixin`/`@include`, `@function`, control flow, `@extend`, `@at-root`,
  * `@use`/`@forward`, `#{...}` interpolation, nested style rules, and parent selector `&`).
  *
  * Standard CSS at-rules (`@media`, `@supports`, `@charset`, `@import`, `@font-face`, `@page`, `@keyframes`, `@namespace`, unknown vendor `@-foo`) are allowed. Custom properties (`--foo: value`) are
  * allowed because they are parsed as [[Declaration]]s, not as Sass [[VariableDeclaration]]s.
  */
class CssParser(
  contents:       String,
  url:            Nullable[String] = Nullable.Null,
  parseSelectors: Boolean = false
) extends ScssParser(contents, url, parseSelectors) {

  override def plainCss: Boolean = true

  /** Sass at-rule keywords that are forbidden inside plain CSS even when they reach the AST as a generic [[AtRule]] (i.e. when the base [[StylesheetParser]] has no dedicated node type for them).
    */
  private val _forbiddenAtRuleNames: Set[String] = Set(
    "if",
    "else",
    "while",
    "for",
    "each",
    "function",
    "return",
    "mixin",
    "include",
    "content",
    "debug",
    "warn",
    "error",
    "extend",
    "at-root",
    "use",
    "forward"
  )

  /** The set of all function names disallowed in plain CSS.
    *
    * dart-sass css.dart lines 14-34: `globalFunctions.map(...).toSet()` minus the CSS-native functions that are allowed (abs, alpha, color, grayscale, hsl, hsla, hwb, invert, lab, lch, max, min,
    * oklab, oklch, opacity, rgb, rgba, round, saturate).
    */
  private val _disallowedFunctionNames: Set[String] = Set(
    // color module globals
    "red",
    "green",
    "blue",
    "mix",
    "hue",
    "saturation",
    "lightness",
    "adjust-hue",
    "lighten",
    "darken",
    "desaturate",
    "opacify",
    "fade-in",
    "transparentize",
    "fade-out",
    "complement",
    "ie-hex-str",
    "adjust-color",
    "scale-color",
    "change-color",
    // list module globals
    "length",
    "nth",
    "set-nth",
    "join",
    "append",
    "zip",
    "index",
    "is-bracketed",
    "list-separator",
    // map module globals
    "map-get",
    "map-merge",
    "map-remove",
    "map-keys",
    "map-values",
    "map-has-key",
    // math module globals
    "ceil",
    "floor",
    "percentage",
    "random",
    "unit",
    "comparable",
    "unitless",
    // selector module globals
    "is-superselector",
    "simple-selectors",
    "selector-parse",
    "selector-nest",
    "selector-append",
    "selector-extend",
    "selector-replace",
    "selector-unify",
    // string module globals
    "unquote",
    "quote",
    "to-upper-case",
    "to-lower-case",
    "unique-id",
    "str-length",
    "str-insert",
    "str-index",
    "str-slice",
    // meta module globals
    "feature-exists",
    "inspect",
    "type-of",
    "keywords",
    // special globals
    "if"
  )

  override def parse(): Stylesheet = {
    val sheet = super.parse()
    _validateStatements(sheet.children.get, insideStyleRule = false)
    sheet
  }

  /** Recursively walks [stmts] and throws [[SassFormatException]] for any statement that isn't allowed in plain CSS. If [insideStyleRule] is true, any nested [[StyleRule]] (nesting) is rejected.
    */
  private def _validateStatements(
    stmts:           List[Statement],
    insideStyleRule: Boolean
  ): Unit = {
    val it = stmts.iterator
    while (it.hasNext) _validateStatement(it.next(), insideStyleRule)
  }

  /** Validates a single statement. */
  private def _validateStatement(stmt: Statement, insideStyleRule: Boolean): Unit =
    stmt match {
      // --- Sass-only statement rules ---------------------------------------
      case v: VariableDeclaration =>
        throw new SassFormatException(
          "Sass variables aren't allowed in plain CSS.",
          v.span
        )
      case m: MixinRule =>
        throw new SassFormatException(
          "@mixin isn't allowed in plain CSS.",
          m.span
        )
      case i: IncludeRule =>
        throw new SassFormatException(
          "@include isn't allowed in plain CSS.",
          i.span
        )
      case f: FunctionRule =>
        throw new SassFormatException(
          "@function isn't allowed in plain CSS.",
          f.span
        )
      case r: ReturnRule =>
        throw new SassFormatException(
          "@return isn't allowed in plain CSS.",
          r.span
        )
      case ifr: IfRule =>
        throw new SassFormatException(
          "@if isn't allowed in plain CSS.",
          ifr.span
        )
      case e: EachRule =>
        throw new SassFormatException(
          "@each isn't allowed in plain CSS.",
          e.span
        )
      case f: ForRule =>
        throw new SassFormatException(
          "@for isn't allowed in plain CSS.",
          f.span
        )
      case w: WhileRule =>
        throw new SassFormatException(
          "@while isn't allowed in plain CSS.",
          w.span
        )
      case d: DebugRule =>
        throw new SassFormatException(
          "@debug isn't allowed in plain CSS.",
          d.span
        )
      case w: WarnRule =>
        throw new SassFormatException(
          "@warn isn't allowed in plain CSS.",
          w.span
        )
      case e: ErrorRule =>
        throw new SassFormatException(
          "@error isn't allowed in plain CSS.",
          e.span
        )
      case e: ExtendRule =>
        throw new SassFormatException(
          "@extend isn't allowed in plain CSS.",
          e.span
        )
      case a: AtRootRule =>
        throw new SassFormatException(
          "@at-root isn't allowed in plain CSS.",
          a.span
        )
      case u: UseRule =>
        throw new SassFormatException(
          "@use isn't allowed in plain CSS.",
          u.span
        )
      case f: ForwardRule =>
        throw new SassFormatException(
          "@forward isn't allowed in plain CSS.",
          f.span
        )
      // --- Silent comments (`//`) are not valid in plain CSS ---------------
      case c: SilentComment =>
        throw new SassFormatException(
          "Silent comments aren't allowed in plain CSS.",
          c.span
        )
      // --- Style rules: CSS Nesting (spec) is allowed; `&` and interpolation
      // are still validated. At the top level, `&` is rejected entirely.
      // Inside nested rules, `&` is allowed per CSS Nesting but `&suffix`
      // (e.g. `&b`) is still Sass-only and rejected.
      // Trailing combinators (`a >`, `a +`) with children are also rejected.
      case s: StyleRule =>
        s.selector.foreach(_checkSelectorInterpolation)
        s.selector.foreach(_checkTrailingCombinator)
        s.selector.foreach(_checkPlaceholderSelector)
        if (!insideStyleRule) {
          s.selector.foreach(_checkSelectorParent)
          s.selector.foreach(_checkLeadingCombinator)
        } else {
          s.selector.foreach(_checkParentSelectorSuffix)
        }
        _validateStatements(s.children.get, insideStyleRule = true)
      // --- Declarations: allow custom properties, walk nested children, check expressions -----
      case d: Declaration =>
        _checkInterpolation(d.name, "Interpolation isn't allowed in plain CSS.")
        d.value.foreach(e => _validateExpression(e))
        d.children.foreach { kids =>
          _validateStatements(kids, insideStyleRule)
        }
      // --- Import rules: reject dynamic imports (Sass-style `@import`) -----
      case ir: ImportRule =>
        val imps = ir.imports.iterator
        while (imps.hasNext)
          imps.next() match {
            case si: StaticImport =>
              _checkInterpolation(
                si.url,
                "Interpolation isn't allowed in plain CSS."
              )
            case di: DynamicImport =>
              throw new SassFormatException(
                "Sass imports aren't allowed in plain CSS.",
                di.span
              )
            case _ => ()
          }
      // --- @media / @supports: allowed; walk children ----------------------
      case mr: MediaRule =>
        _checkInterpolation(mr.query, "Interpolation isn't allowed in plain CSS.")
        _validateStatements(mr.children.get, insideStyleRule)
      case sr: SupportsRule =>
        _validateStatements(sr.children.get, insideStyleRule)
      // --- Generic / unknown at-rules: allowed; walk children if any.
      //     The base StylesheetParser only produces dedicated AST nodes for
      //     a subset of Sass at-rules (@mixin, @include, @extend, @use, ...)
      //     — anything else (including `@if`, `@else`, `@while`) lands in a
      //     generic AtRule here, so we also blocklist forbidden names.
      case ar: AtRule =>
        _checkInterpolation(ar.name, "Interpolation isn't allowed in plain CSS.")
        val plainName = ar.name.asPlain
        plainName.foreach { n =>
          if (_forbiddenAtRuleNames.contains(n.toLowerCase)) {
            throw new SassFormatException(
              s"@$n isn't allowed in plain CSS.",
              ar.span
            )
          }
        }
        ar.children.foreach { kids =>
          _validateStatements(kids, insideStyleRule)
        }
      // --- Loud (`/* */`) comments: allowed --------------------------------
      case _: LoudComment => ()
      // --- Anything else: permit (unknown rules may be added later) --------
      case _ => ()
    }

  /** Throws if [interp] contains a Sass `#{...}` interpolation expression. */
  private def _checkInterpolation(
    interp:  Interpolation,
    message: String
  ): Unit =
    if (!interp.isPlain) {
      throw new SassFormatException(message, interp.span)
    }

  /** Throws if [interp] in a style rule selector textually contains an unescaped parent selector `&`.
    */
  private def _checkSelectorInterpolation(interp: Interpolation): Unit =
    if (!interp.isPlain) {
      throw new SassFormatException(
        "Interpolation isn't allowed in plain CSS.",
        interp.span
      )
    }

  private def _checkSelectorParent(interp: Interpolation): Unit = {
    val plain = interp.asPlain
    plain.foreach { text =>
      if (_containsParentSelector(text)) {
        throw new SassFormatException(
          "The parent selector isn't allowed in plain CSS.",
          interp.span
        )
      }
    }
  }

  /** Rejects selectors starting with a combinator (e.g. `> a {b: c}`) at top level. */
  private def _checkLeadingCombinator(interp: Interpolation): Unit = {
    val plain = interp.asPlain
    plain.foreach { text =>
      val trimmed = text.trim
      if (trimmed.nonEmpty) {
        val first = trimmed.charAt(0)
        if (first == '>' || first == '+' || first == '~') {
          throw new SassFormatException(
            "expected selector.",
            interp.span
          )
        }
      }
    }
  }

  /** Rejects `%name` selectors which are Sass-only. */
  private def _checkPlaceholderSelector(interp: Interpolation): Unit = {
    val plain = interp.asPlain
    plain.foreach { text =>
      if (text.contains('%')) {
        throw new SassFormatException(
          "Placeholder selectors aren't allowed in plain CSS.",
          interp.span
        )
      }
    }
  }

  /** Rejects selectors that end with a combinator (`>`, `+`, `~`) which would be invalid CSS — e.g. `a > {b {c: d}}` should error on `a >`.
    */
  private def _checkTrailingCombinator(interp: Interpolation): Unit = {
    val plain = interp.asPlain
    plain.foreach { text =>
      val trimmed = text.trim
      if (trimmed.nonEmpty) {
        val last = trimmed.charAt(trimmed.length - 1)
        if (last == '>' || last == '+' || last == '~') {
          throw new SassFormatException(
            "expected selector.",
            interp.span
          )
        }
      }
    }
  }

  /** Rejects `&suffix` (parent selector with suffix) in nested CSS rules. Plain `&`, `&.class`, `.class&` are all valid CSS Nesting.
    */
  private def _checkParentSelectorSuffix(interp: Interpolation): Unit = {
    val plain = interp.asPlain
    plain.foreach { text =>
      // Check for `&` immediately followed by a name character (letter, digit, hyphen, underscore)
      // This catches `&b`, `&foo` but NOT `&.b`, `& `, `&:hover` etc.
      var i = 0
      while (i < text.length) {
        if (text.charAt(i) == '&' && i + 1 < text.length) {
          val next = text.charAt(i + 1)
          if (ssg.sass.util.CharCode.isNameStart(next.toInt) || next == '-' || next == '_') {
            throw new SassFormatException(
              "Parent selectors can't have suffixes in plain CSS.",
              interp.span
            )
          }
        }
        i += 1
      }
    }
  }

  /** Validates an expression, rejecting Sass-only constructs in plain CSS.
    *
    * dart-sass css.dart: rejects function calls, operations, interpolation, variables, maps, parenthesized expressions, and other Sass-only features.
    *
    * @param insideCalc
    *   if true, arithmetic operators and parentheses are allowed (valid CSS calc() syntax).
    */
  private def _validateExpression(expr: Expression, insideCalc: Boolean = false): Unit =
    expr match {
      case fe: FunctionExpression =>
        if (_disallowedFunctionNames.contains(fe.name)) {
          throw new SassFormatException(
            "This function isn't allowed in plain CSS.",
            fe.span
          )
        }
        // calc/min/max/clamp allow arithmetic operators inside
        val calcFn = _isCalcLikeFunction(fe.name)
        fe.arguments.positional.foreach(e => _validateExpression(e, insideCalc = calcFn))
        fe.arguments.named.foreach { case (_, v) => _validateExpression(v, insideCalc = calcFn) }
      case boe: BinaryOperationExpression if !insideCalc =>
        // `/` (DividedBy) is valid in plain CSS (font shorthand, grid, etc.)
        // `SingleEquals` (=) is valid in IE filter syntax (alpha(opacity=65))
        import ssg.sass.ast.sass.BinaryOperator
        if (boe.operator != BinaryOperator.DividedBy && boe.operator != BinaryOperator.SingleEquals) {
          throw new SassFormatException(
            "Operators aren't allowed in plain CSS.",
            expr.span
          )
        }
        _validateExpression(boe.left, insideCalc)
        _validateExpression(boe.right, insideCalc)
      case _: VariableExpression =>
        throw new SassFormatException(
          "Sass variables aren't allowed in plain CSS.",
          expr.span
        )
      case _: MapExpression =>
        throw new SassFormatException(
          "Sass maps aren't allowed in plain CSS.",
          expr.span
        )
      case se: StringExpression if !se.text.isPlain =>
        throw new SassFormatException(
          "Interpolation isn't allowed in plain CSS.",
          se.span
        )
      case ife: InterpolatedFunctionExpression =>
        throw new SassFormatException(
          "Interpolation isn't allowed in plain CSS.",
          ife.span
        )
      case _: SelectorExpression =>
        throw new SassFormatException(
          "The parent selector isn't allowed in plain CSS.",
          expr.span
        )
      case le: ListExpression =>
        le.contents.foreach(e => _validateExpression(e, insideCalc))
      case ue: UnaryOperationExpression =>
        _validateExpression(ue.operand, insideCalc)
      case be: BinaryOperationExpression =>
        // insideCalc=true: validate children
        _validateExpression(be.left, insideCalc)
        _validateExpression(be.right, insideCalc)
      case _ => ()
    }

  /** Returns true for CSS mathematical functions that allow operators. */
  private def _isCalcLikeFunction(name: String): Boolean = {
    val lower = name.toLowerCase
    lower == "calc" || lower == "min" || lower == "max" || lower == "clamp" ||
    lower == "sin" || lower == "cos" || lower == "tan" || lower == "sqrt" ||
    lower == "exp" || lower == "log" || lower == "pow" || lower == "abs" ||
    lower == "sign" || lower == "mod" || lower == "rem" || lower == "round" ||
    lower == "atan2" || lower == "hypot"
  }

  /** Returns true if [text] contains a parent-selector `&` that isn't inside a string literal or an attribute selector (`[attr="&"]`).
    */
  private def _containsParentSelector(text: String): Boolean = {
    var i = 0
    val n = text.length
    var quote: Char = 0
    var found = false
    import scala.util.boundary, boundary.break
    boundary {
      while (i < n) {
        val c = text.charAt(i)
        if (quote != 0) {
          if (c == '\\' && i + 1 < n) i += 2
          else {
            if (c == quote) quote = 0
            i += 1
          }
        } else if (c == '"' || c == '\'') {
          quote = c
          i += 1
        } else if (c == '&') {
          found = true
          break(())
        } else {
          i += 1
        }
      }
    }
    found
  }
}
