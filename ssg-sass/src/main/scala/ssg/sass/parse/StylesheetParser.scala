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
  AtRule,
  BooleanExpression,
  Declaration,
  Expression,
  Interpolation,
  LoudComment,
  NullExpression,
  NumberExpression,
  ParseTimeWarning,
  SilentComment,
  Statement,
  StringExpression,
  StyleRule,
  Stylesheet,
  UseRule,
  VariableDeclaration,
  VariableExpression
}
import ssg.sass.util.{CharCode, FileSpan}
import ssg.sass.value.SassNumber

import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

/** The base class for both the SCSS and indented syntax parsers. */
abstract class StylesheetParser protected (
  contents: String,
  url: Nullable[String] = Nullable.Null,
  protected val parseSelectors: Boolean = false
) extends Parser(contents, url) {

  /** Warnings discovered while parsing. */
  protected val warnings: mutable.ListBuffer[ParseTimeWarning] = mutable.ListBuffer.empty

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
    if (scanner.peekChar() == 0xFEFF) scanner.readChar()

    val stmts = statements(() => _topLevelStatement())
    scanner.expectDone()

    val span = spanFrom(start)
    new Stylesheet(stmts, span, plainCss, warnings.toList)
  }

  /** Parses a top-level statement (at statement or style rule). */
  private def _topLevelStatement(): Nullable[Statement] = {
    val c = scanner.peekChar()
    if (c == CharCode.$at) _atRule()
    else if (c == CharCode.$dollar) Nullable(_variableDeclaration())
    else if (c == CharCode.$slash && (scanner.peekChar(1) == CharCode.$slash || scanner.peekChar(1) == CharCode.$asterisk)) {
      if (scanner.peekChar(1) == CharCode.$slash) _silentComment()
      else _loudComment()
    } else {
      // Style rule
      Nullable(_styleRule())
    }
  }

  /** Parses a top-level @-rule. Currently only handles @use as a recognized form. */
  private def _atRule(): Nullable[Statement] = {
    val start = scanner.state
    scanner.expectChar(CharCode.$at)
    val name = identifier()
    whitespace(consumeNewlines = true)

    name match {
      case "use" =>
        // Minimal @use parsing: @use "url";
        whitespace(consumeNewlines = true)
        val url = if (scanner.peekChar() == CharCode.$double_quote || scanner.peekChar() == CharCode.$single_quote) {
          string()
        } else {
          scanner.error("Expected string URL.")
        }
        whitespace(consumeNewlines = true)
        scanner.scanChar(CharCode.$semicolon)
        // Placeholder: we don't have a UseRule factory for just URL yet.
        // Fall back to generic AtRule.
        val nameInterp = Interpolation.plain("use", spanFrom(start))
        val valueInterp = Interpolation.plain(s"\"$url\"", spanFrom(start))
        Nullable(new AtRule(
          name = nameInterp,
          span = spanFrom(start),
          value = Nullable(valueInterp),
          childStatements = Nullable.empty
        ))
      case _ =>
        // Generic at-rule: just skip to ; or {
        val valueBuf = new StringBuilder()
        while (!scanner.isDone) {
          val c = scanner.peekChar()
          if (c == CharCode.$semicolon || c == CharCode.$lbrace || c == CharCode.$rbrace) {
            val valueText = valueBuf.toString().trim
            val nameSpan = spanFrom(start)
            val nameInterp = Interpolation.plain(name, nameSpan)

            if (c == CharCode.$lbrace) {
              // _children() expects to consume the opening `{` itself.
              val kids = _children()
              val valueInterp = if (valueText.nonEmpty) Nullable(Interpolation.plain(valueText, nameSpan)) else Nullable.empty
              return Nullable(new AtRule(
                name = nameInterp,
                span = spanFrom(start),
                value = valueInterp,
                childStatements = Nullable(kids)
              ))
            } else if (c == CharCode.$semicolon) {
              scanner.readChar()
              val valueInterp = if (valueText.nonEmpty) Nullable(Interpolation.plain(valueText, nameSpan)) else Nullable.empty
              return Nullable(new AtRule(
                name = nameInterp,
                span = spanFrom(start),
                value = valueInterp,
                childStatements = Nullable.empty
              ))
            } else {
              return Nullable(new AtRule(nameInterp, spanFrom(start), Nullable.empty, Nullable.empty))
            }
          } else {
            valueBuf.append(scanner.readChar().toChar)
          }
        }
        val nameInterp = Interpolation.plain(name, spanFrom(start))
        Nullable(new AtRule(nameInterp, spanFrom(start), Nullable.empty, Nullable.empty))
    }
  }

  /** Parses a variable declaration: `$name: value;` */
  private def _variableDeclaration(): VariableDeclaration = {
    val start = scanner.state
    val name = variableName()
    whitespace(consumeNewlines = true)
    scanner.expectChar(CharCode.$colon)
    whitespace(consumeNewlines = true)

    val expression = _expression()
    whitespace(consumeNewlines = false)

    // Handle !default / !global flags (simplified)
    var isGuarded = false
    var isGlobal = false
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
    new VariableDeclaration(name, expression, spanFrom(start), Nullable.empty, isGuarded, isGlobal)
  }

  /** Parses a style rule: `selector { children }`. */
  private def _styleRule(): StyleRule = {
    val start = scanner.state
    val selectorInterp = styleRuleSelector()
    val kids = _children()
    StyleRule(selectorInterp, kids, spanFrom(start))
  }

  /** Parses a block of children: `{ stmt; stmt; }`. Called after the `{`
    * has NOT yet been consumed.
    */
  private def _children(): List[Statement] = {
    scanner.expectChar(CharCode.$lbrace)
    whitespace(consumeNewlines = true)
    val stmts = mutable.ListBuffer.empty[Statement]
    while (!scanner.isDone && scanner.peekChar() != CharCode.$rbrace) {
      val stmt = _childStatement()
      if (stmt.isDefined) stmts += stmt.get
      whitespace(consumeNewlines = true)
    }
    scanner.expectChar(CharCode.$rbrace)
    stmts.toList
  }

  /** Parses a child statement inside a block. Could be:
    * - a nested style rule
    * - a declaration (name: value;)
    * - a variable declaration
    * - a comment
    * - an at-rule
    */
  private def _childStatement(): Nullable[Statement] = {
    val c = scanner.peekChar()
    if (c == CharCode.$at) _atRule()
    else if (c == CharCode.$dollar) Nullable(_variableDeclaration())
    else if (c == CharCode.$slash && (scanner.peekChar(1) == CharCode.$slash || scanner.peekChar(1) == CharCode.$asterisk)) {
      if (scanner.peekChar(1) == CharCode.$slash) _silentComment()
      else _loudComment()
    } else {
      // Could be a declaration or a nested style rule. Lookahead is needed.
      _declarationOrStyleRule()
    }
  }

  /** Tries to parse a declaration; if that fails, falls back to a style rule. */
  private def _declarationOrStyleRule(): Nullable[Statement] = {
    val start = scanner.state
    // Try to read an identifier followed by `:` to detect a declaration.
    if (!lookingAtIdentifier()) {
      // Selector starts with something other than identifier; treat as style rule
      return Nullable(_styleRule())
    }
    val savedState = scanner.state
    val name = try identifier() catch {
      case _: Exception =>
        scanner.state = savedState
        return Nullable(_styleRule())
    }
    whitespace(consumeNewlines = false)

    if (scanner.peekChar() == CharCode.$colon) {
      // Could still be a pseudo-class selector (e.g. `a:hover`). But if next
      // char after `:` is whitespace or a value-like char, it's a declaration.
      scanner.readChar() // consume ':'
      val afterColon = scanner.peekChar()
      if (afterColon < 0) {
        scanner.error("Expected expression.")
      }
      // Heuristic: if next char is ':' (pseudo-element like `::before`) or
      // looks like an identifier start with no space, it's a selector.
      if (afterColon == CharCode.$colon || (CharCode.isNameStart(afterColon) && !scanner.isDone &&
          scanner.string.substring(savedState.position).takeWhile(c => c != '{' && c != ';' && c != '}').contains('{') &&
          !scanner.string.substring(savedState.position).takeWhile(c => c != '{' && c != ';' && c != '}').contains(';'))) {
        // Looks like a selector — rewind and parse as style rule
        scanner.state = savedState
        return Nullable(_styleRule())
      }

      // Parse as declaration
      whitespace(consumeNewlines = true)
      val nameSpan = {
        val s = savedState
        val endLoc = scanner.sourceFile.location(s.position + name.length)
        val startLoc = scanner.sourceFile.location(s.position)
        scanner.sourceFile.span(startLoc.offset, endLoc.offset)
      }
      val nameInterp = Interpolation.plain(name, nameSpan)

      // If we're at end of declaration (no value), it's a nested declaration
      // For simplicity, require a value.
      val expression = _expression()
      whitespace(consumeNewlines = false)
      scanner.scanChar(CharCode.$semicolon)
      Nullable(Declaration(nameInterp, expression, spanFrom(start)))
    } else {
      // Not a declaration — rewind and parse as style rule
      scanner.state = savedState
      Nullable(_styleRule())
    }
  }

  /** Parses a silent Sass comment (`//...`). */
  private def _silentComment(): Nullable[Statement] = {
    val start = scanner.state
    silentComment()
    Nullable(new SilentComment(scanner.substring(start.position), spanFrom(start)))
  }

  /** Parses a loud CSS comment (`/* ... */`). */
  private def _loudComment(): Nullable[Statement] = {
    val start = scanner.state
    loudComment()
    val text = scanner.substring(start.position)
    val interp = Interpolation.plain(text, spanFrom(start))
    Nullable(new LoudComment(interp))
  }

  /** Parses a single expression. Minimal: handles numbers, strings, identifiers, variables.
    * Multi-value expressions (space-separated lists, comma-separated lists, math operators)
    * are handled as a best effort by collecting raw text as an unquoted string.
    */
  private def _expression(): Expression = {
    val start = scanner.state
    val c = scanner.peekChar()
    if (c < 0) scanner.error("Expected expression.")

    // Collect until end-of-statement markers
    val buf = new StringBuilder()
    var brackets = 0
    boundary {
      while (!scanner.isDone) {
        val ch = scanner.peekChar()
        if (ch < 0) break(())
        if (brackets == 0) {
          if (ch == CharCode.$semicolon || ch == CharCode.$rbrace || ch == CharCode.$lbrace) break(())
          if (ch == CharCode.$exclamation) break(()) // start of flag like !default
        }
        if (ch == CharCode.$lparen || ch == CharCode.$lbracket) brackets += 1
        else if (ch == CharCode.$rparen || ch == CharCode.$rbracket) {
          if (brackets == 0) break(())
          brackets -= 1
        }
        buf.append(scanner.readChar().toChar)
      }
    }

    val raw = buf.toString().trim
    val span = spanFrom(start)

    if (raw.isEmpty) scanner.error("Expected expression.", start.position, 0)

    // Try to parse as a simple form
    _parseSimpleExpression(raw, span)
  }

  /** Best-effort parsing of a simple expression string into an Expression node.
    * Handles: bare identifiers, variables, numbers with units, quoted strings,
    * booleans (true/false/null). Falls back to unquoted StringExpression.
    */
  private def _parseSimpleExpression(raw: String, span: FileSpan): Expression = {
    val trimmed = raw.trim
    if (trimmed.isEmpty) return new NullExpression(span)

    // Boolean / null literals
    if (trimmed == "true") return new BooleanExpression(value = true, span)
    if (trimmed == "false") return new BooleanExpression(value = false, span)
    if (trimmed == "null") return new NullExpression(span)

    // Quoted string
    if ((trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) ||
        (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length >= 2)) {
      val inner = trimmed.substring(1, trimmed.length - 1)
      return StringExpression(Interpolation.plain(inner, span), hasQuotes = true)
    }

    // Variable reference
    if (trimmed.startsWith("$")) {
      val name = trimmed.substring(1)
      if (name.nonEmpty && _allChars(name, (c: Char) => CharCode.isName(c.toInt))) {
        return VariableExpression(name.replace('_', '-'), span)
      }
    }

    // Number literal with optional unit
    _tryParseNumber(trimmed, span) match {
      case Some(num) => return num
      case None      =>
    }

    // Fallback: unquoted string expression
    StringExpression(Interpolation.plain(trimmed, span), hasQuotes = false)
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

    val numStr = raw.substring(0, i)
    val value = try numStr.toDouble
      catch { case _: NumberFormatException => return None }

    val unit = raw.substring(i).trim
    if (unit.isEmpty) Some(NumberExpression(value, span, Nullable.empty))
    else if (unit.startsWith("%")) Some(NumberExpression(value, span, Nullable("%")))
    else if (_allChars(unit, (c: Char) => CharCode.isName(c.toInt))) Some(NumberExpression(value, span, Nullable(unit)))
    else None
  }

  /** Helper: returns true if every character of [s] satisfies [p]. Explicit
    * loop to avoid Nullable implicit conversion hijacking String.forall.
    */
  private def _allChars(s: String, p: Char => Boolean): Boolean = {
    var i = 0
    while (i < s.length) {
      if (!p(s.charAt(i))) return false
      i += 1
    }
    true
  }

  /** Parses the contents as a single expression, returning the expression
    * and any warnings encountered.
    */
  def parseExpression(): (Expression, List[ParseTimeWarning]) = wrapSpanFormatException { () =>
    whitespace(consumeNewlines = true)
    val expr = _expression()
    whitespace(consumeNewlines = true)
    scanner.expectDone()
    (expr, warnings.toList)
  }

  /** Parses the contents as a single number literal. */
  def parseNumber(): SassNumber = wrapSpanFormatException { () =>
    whitespace(consumeNewlines = true)
    val start = scanner.state
    val numExpr = {
      val buf = new StringBuilder()
      while (!scanner.isDone && !CharCode.isWhitespace(scanner.peekChar())) {
        buf.append(scanner.readChar().toChar)
      }
      _tryParseNumber(buf.toString(), spanFrom(start))
        .getOrElse(scanner.error("Expected number."))
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

  /** Parses the contents as a single `@use` rule. */
  def parseUseRule(): (UseRule, List[ParseTimeWarning]) =
    throw new UnsupportedOperationException(
      "StylesheetParser.parseUseRule: UseRule construction not yet supported"
    )

  /** Parses a function signature of the format allowed by Node Sass's
    * functions option and returns its name and parameter list.
    */
  def parseSignature(requireParens: Boolean = true): (String, Any) =
    throw new UnsupportedOperationException(
      "StylesheetParser.parseSignature: not yet implemented"
    )

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
}
