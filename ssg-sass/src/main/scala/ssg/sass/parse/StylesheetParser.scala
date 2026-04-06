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
  AtRule,
  BinaryOperationExpression,
  BinaryOperator,
  BooleanExpression,
  Declaration,
  DynamicImport,
  ExtendRule,
  Expression,
  FunctionExpression,
  Import,
  ImportRule,
  Interpolation,
  ListExpression,
  LoudComment,
  NullExpression,
  NumberExpression,
  ParseTimeWarning,
  SilentComment,
  Statement,
  StaticImport,
  StringExpression,
  StyleRule,
  Stylesheet,
  UnaryOperationExpression,
  UnaryOperator,
  UseRule,
  VariableDeclaration,
  VariableExpression
}
import ssg.sass.value.ListSeparator
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
        // Minimal @use parsing: @use "url" [as namespace|*] [with (...)];
        whitespace(consumeNewlines = true)
        val url = if (scanner.peekChar() == CharCode.$double_quote || scanner.peekChar() == CharCode.$single_quote) {
          string()
        } else {
          scanner.error("Expected string URL.")
        }
        whitespace(consumeNewlines = true)
        val namespace: Nullable[String] =
          if (scanIdentifier("as")) {
            whitespace(consumeNewlines = true)
            if (scanner.scanChar(CharCode.$asterisk)) {
              Nullable.empty[String] // flat: no namespace
            } else {
              Nullable(identifier())
            }
          } else {
            // Default namespace: last path segment without extension/underscore.
            val lastSeg = {
              val segs = url.split('/')
              if (segs.isEmpty) url else segs(segs.length - 1)
            }
            val stripped = lastSeg
              .stripSuffix(".scss")
              .stripSuffix(".sass")
              .stripSuffix(".css")
              .stripPrefix("_")
            if (stripped.isEmpty) Nullable.empty[String]
            else Nullable(stripped)
          }
        whitespace(consumeNewlines = true)
        // Optional `with (...)` — skipped in v1.
        if (scanIdentifier("with")) {
          while (!scanner.isDone && scanner.peekChar() != CharCode.$semicolon) {
            val _ = scanner.readChar()
          }
        }
        whitespace(consumeNewlines = false)
        val _ = scanner.scanChar(CharCode.$semicolon)
        val uri = java.net.URI.create(url)
        Nullable(new UseRule(uri, namespace, spanFrom(start)))
      case "import" =>
        // @import "url" [, "url2"] ;
        val imports = scala.collection.mutable.ListBuffer.empty[Import]
        var more = true
        while (more) {
          whitespace(consumeNewlines = true)
          val importStart = scanner.state
          val c = scanner.peekChar()
          if (c == CharCode.$double_quote || c == CharCode.$single_quote) {
            val url = string()
            // Dynamic import (Sass-style) if URL doesn't look like CSS import
            // (e.g. has `.css` suffix, or `http://`, or is `url(...)`).
            // For now, treat all @import with quoted URLs as dynamic imports
            // that the evaluator will try to resolve via the importer, and
            // fall back to StaticImport semantics if not found.
            val isPlainCss = url.endsWith(".css") || url.startsWith("http://") ||
                             url.startsWith("https://") || url.startsWith("//")
            if (isPlainCss) {
              val urlInterp = Interpolation.plain(s"\"$url\"", spanFrom(importStart))
              imports += StaticImport(urlInterp, spanFrom(importStart))
            } else {
              imports += DynamicImport(url, spanFrom(importStart))
            }
          } else {
            scanner.error("Expected string URL.")
          }
          whitespace(consumeNewlines = true)
          if (scanner.scanChar(CharCode.$comma)) more = true
          else more = false
        }
        scanner.scanChar(CharCode.$semicolon)
        Nullable(new ImportRule(imports.toList, spanFrom(start)))
      case "extend" =>
        // @extend <selector> [!optional] ;
        whitespace(consumeNewlines = true)
        val selBuf = new StringBuilder()
        import scala.util.boundary, boundary.break
        boundary {
          while (!scanner.isDone) {
            val c = scanner.peekChar()
            if (c == CharCode.$semicolon || c == CharCode.$lbrace || c == CharCode.$rbrace) {
              break(())
            } else {
              selBuf.append(scanner.readChar().toChar)
            }
          }
        }
        val rawText = selBuf.toString().trim
        var isOptional = false
        val selText =
          if (rawText.endsWith("!optional")) {
            isOptional = true
            rawText.stripSuffix("!optional").trim
          } else rawText
        val span = spanFrom(start)
        val selInterp = Interpolation.plain(selText, span)
        if (scanner.peekChar() == CharCode.$semicolon) {
          val _ = scanner.readChar()
        }
        Nullable(new ExtendRule(selInterp, span, isOptional))
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

    // Collect until end-of-statement markers. Respects quoted strings and
    // `#{...}` interpolation so braces inside them don't terminate collection.
    val buf = new StringBuilder()
    var brackets = 0
    var inQuote: Int = 0       // 0 = not in string, else the opening quote char
    var interpDepth: Int = 0   // brace depth inside #{...}
    boundary {
      while (!scanner.isDone) {
        val ch = scanner.peekChar()
        if (ch < 0) break(())

        if (interpDepth > 0) {
          // Inside #{...} — may itself be nested within a quoted string.
          if (ch == CharCode.$lbrace) interpDepth += 1
          else if (ch == CharCode.$rbrace) {
            interpDepth -= 1
            if (interpDepth == 0 && inQuote < 0) inQuote = -inQuote // resume string
          }
          buf.append(scanner.readChar().toChar)
        } else if (inQuote > 0) {
          // Inside a quoted string literal.
          if (ch == CharCode.$backslash) {
            buf.append(scanner.readChar().toChar)
            if (!scanner.isDone) buf.append(scanner.readChar().toChar)
          } else if (ch == CharCode.$hash && scanner.peekChar(1) == CharCode.$lbrace) {
            buf.append(scanner.readChar().toChar) // '#'
            buf.append(scanner.readChar().toChar) // '{'
            interpDepth = 1
            inQuote = -inQuote // stash quote, negative => we're in interp-inside-string
          } else {
            if (ch == inQuote) inQuote = 0
            buf.append(scanner.readChar().toChar)
          }
        } else {
          // Top-level expression text.
          if (brackets == 0) {
            if (ch == CharCode.$semicolon || ch == CharCode.$rbrace || ch == CharCode.$lbrace) break(())
            if (ch == CharCode.$exclamation) break(()) // start of flag like !default
          }
          if (ch == CharCode.$double_quote || ch == CharCode.$single_quote) {
            inQuote = ch
            buf.append(scanner.readChar().toChar)
          } else if (ch == CharCode.$hash && scanner.peekChar(1) == CharCode.$lbrace) {
            buf.append(scanner.readChar().toChar) // '#'
            buf.append(scanner.readChar().toChar) // '{'
            interpDepth = 1
          } else {
            if (ch == CharCode.$lparen || ch == CharCode.$lbracket) brackets += 1
            else if (ch == CharCode.$rparen || ch == CharCode.$rbracket) {
              if (brackets == 0) break(())
              brackets -= 1
            }
            buf.append(scanner.readChar().toChar)
          }
        }
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
      return StringExpression(_parseInterpolatedString(inner, span), hasQuotes = true)
    }

    // Variable reference (possibly namespaced: `ns.$var`)
    if (trimmed.startsWith("$")) {
      val name = trimmed.substring(1)
      if (name.nonEmpty && _allChars(name, (c: Char) => CharCode.isName(c.toInt))) {
        return VariableExpression(name.replace('_', '-'), span)
      }
    }
    // Namespaced variable: `ns.$var`
    {
      val dollarIdx = trimmed.indexOf(".$")
      if (dollarIdx > 0) {
        val ns = trimmed.substring(0, dollarIdx)
        val name = trimmed.substring(dollarIdx + 2)
        if (_allChars(ns, (c: Char) => CharCode.isName(c.toInt)) &&
            name.nonEmpty && _allChars(name, (c: Char) => CharCode.isName(c.toInt))) {
          return VariableExpression(name.replace('_', '-'), span, Nullable(ns))
        }
      }
    }

    // Number literal with optional unit
    _tryParseNumber(trimmed, span) match {
      case Some(num) => return num
      case None      =>
    }

    // Function call: identifier followed by (...) with matching closing paren at end
    _tryParseFunctionCall(trimmed, span) match {
      case Some(fn) => return fn
      case None     =>
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
    if (spaceSplit.length >= 2) {
      val parts = spaceSplit.map(p => _parseSimpleExpression(p, span))
      return ListExpression(parts, ListSeparator.Space, span, hasBrackets = false)
    }

    // Fallback: unquoted string expression
    StringExpression(Interpolation.plain(trimmed, span), hasQuotes = false)
  }

  /** Splits [s] at top-level occurrences of [sep] (ignoring separators inside
    * matched parens/brackets/quotes).
    */
  private def _splitTopLevel(s: String, sep: Char): List[String] = {
    val result = scala.collection.mutable.ListBuffer.empty[String]
    val buf = new StringBuilder()
    var depth = 0
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

  /** Attempts to parse a function call `name(args)`. */
  private def _tryParseFunctionCall(raw: String, span: FileSpan): Option[FunctionExpression] = {
    val parenIdx = raw.indexOf('(')
    if (parenIdx <= 0 || !raw.endsWith(")")) return None
    val head = raw.substring(0, parenIdx)
    // Head is either `name` or `namespace.name`
    val dotIdx = head.indexOf('.')
    val (namespace, name): (Nullable[String], String) =
      if (dotIdx > 0 && dotIdx < head.length - 1) {
        val ns = head.substring(0, dotIdx)
        val n = head.substring(dotIdx + 1)
        if (_allChars(ns, (c: Char) => CharCode.isName(c.toInt)) &&
            _allChars(n, (c: Char) => CharCode.isName(c.toInt))) {
          (Nullable(ns), n)
        } else {
          return None
        }
      } else {
        if (!_allChars(head, (c: Char) => CharCode.isName(c.toInt))) return None
        (Nullable.empty[String], head)
      }
    // Special-case: url() — passes through as an unquoted string. Skip for now.
    if (namespace.isEmpty && name == "url") return None

    val argsText = raw.substring(parenIdx + 1, raw.length - 1).trim
    val argExprs = if (argsText.isEmpty) Nil
      else _splitTopLevel(argsText, ',').map(a => _parseSimpleExpression(a, span))

    // Detect named arguments: `$name: value`
    val (positional, named) = argExprs.foldLeft(
      (List.empty[Expression], Map.empty[String, Expression])
    ) { case ((pos, nam), expr) =>
      expr match {
        case _ =>
          // Simplified: no named arguments for now (would need re-parsing `$name: value`)
          (pos :+ expr, nam)
      }
    }

    val arguments = new ArgumentList(positional, named, Map.empty, span)
    Some(FunctionExpression(name, arguments, span, namespace))
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

  /** Parses [raw] into an [[Interpolation]], detecting `#{...}` segments and
    * treating the content of each as an expression (recursively parsed via
    * [[_parseSimpleExpression]]). Literal text segments become [String]
    * elements; interpolated regions become [Expression] elements. Matching
    * braces inside `#{...}` are balanced.
    */
  protected def _parseInterpolatedString(raw: String, span: FileSpan): Interpolation = {
    val contents = scala.collection.mutable.ListBuffer.empty[Any]
    val spans = scala.collection.mutable.ListBuffer.empty[Nullable[FileSpan]]
    val literal = new StringBuilder()
    var i = 0
    val n = raw.length
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
        var j = i + 2
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

  /** Returns true if [t] is a bare arithmetic operator token. */
  private def _isOperatorToken(t: String): Boolean =
    t == "+" || t == "-" || t == "*" || t == "/" || t == "%"

  /** Returns the [BinaryOperator] for an operator token, or `None`. */
  private def _binaryOpFor(t: String): Option[BinaryOperator] = t match {
    case "+" => Some(BinaryOperator.Plus)
    case "-" => Some(BinaryOperator.Minus)
    case "*" => Some(BinaryOperator.Times)
    case "/" => Some(BinaryOperator.DividedBy)
    case "%" => Some(BinaryOperator.Modulo)
    case _   => None
  }

  /** Parses a sequence of whitespace-separated tokens as a left-associative
    * binary expression using operator precedence. Returns `None` if the
    * tokens don't form a valid operator expression (e.g. two operands in a
    * row with no operator between).
    */
  private def _parseBinaryOps(tokens: List[String], span: FileSpan): Option[Expression] =
    boundary[Option[Expression]] {
      // Validate alternating operand/operator/operand/.../operand pattern.
      if (tokens.isEmpty) break(None)
      if (_isOperatorToken(tokens.head)) break(None)
      if (_isOperatorToken(tokens.last)) break(None)
      var i = 0
      while (i < tokens.length) {
        val expectOperator = (i % 2 == 1)
        val tok = tokens(i)
        if (expectOperator != _isOperatorToken(tok)) break(None)
        i += 1
      }

      // Shunting-yard: build left-associative tree honoring precedence.
      val output = scala.collection.mutable.ArrayBuffer.empty[Expression]
      val ops = scala.collection.mutable.ArrayBuffer.empty[BinaryOperator]
      def reduce(): Unit = {
        val r = output.remove(output.length - 1)
        val l = output.remove(output.length - 1)
        val op = ops.remove(ops.length - 1)
        output += BinaryOperationExpression(op, l, r)
      }
      output += _parseSimpleExpression(tokens.head, span)
      var j = 1
      while (j + 1 < tokens.length) {
        val opTok = tokens(j)
        val rhs = tokens(j + 1)
        val op = _binaryOpFor(opTok) match {
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
