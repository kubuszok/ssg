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
  ConfiguredVariable,
  ContentBlock,
  ContentRule,
  DebugRule,
  Declaration,
  DynamicImport,
  EachRule,
  ErrorRule,
  Expression,
  ExtendRule,
  ForwardRule,
  FunctionExpression,
  FunctionRule,
  Import,
  ImportRule,
  IncludeRule,
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
  ParseTimeWarning,
  ReturnRule,
  SilentComment,
  Statement,
  StaticImport,
  StringExpression,
  StyleRule,
  Stylesheet,
  SupportsAnything,
  SupportsCondition,
  SupportsFunction,
  SupportsRule,
  UnaryOperationExpression,
  UnaryOperator,
  UseRule,
  VariableDeclaration,
  VariableExpression,
  WarnRule
}
import ssg.sass.value.ListSeparator
import ssg.sass.util.{ CharCode, FileSpan }
import ssg.sass.value.SassNumber

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
            val stripped = lastSeg.stripSuffix(".scss").stripSuffix(".sass").stripSuffix(".css").stripPrefix("_")
            if (stripped.isEmpty) Nullable.empty[String]
            else Nullable(stripped)
          }
        whitespace(consumeNewlines = true)
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
            val expr = _expression()
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
        val _   = scanner.scanChar(CharCode.$semicolon)
        val uri = java.net.URI.create(url)
        Nullable(new UseRule(uri, namespace, spanFrom(start), configBuf.toList))
      case "forward" =>
        // Minimal @forward parsing: @forward "url" [show ...|hide ...] [as prefix-*];
        // On any unsupported / malformed clause, swallow to ';' and skip the rule.
        whitespace(consumeNewlines = true)
        val urlOpt: Nullable[String] = if (scanner.peekChar() == CharCode.$double_quote || scanner.peekChar() == CharCode.$single_quote) {
          Nullable(string())
        } else {
          // Skip to ; and emit no rule
          while (!scanner.isDone && scanner.peekChar() != CharCode.$semicolon) {
            val _ = scanner.readChar()
          }
          val _ = scanner.scanChar(CharCode.$semicolon)
          Nullable.empty[String]
        }
        if (urlOpt.isEmpty) {
          Nullable.empty[Statement]
        } else {
          val url = urlOpt.get
          whitespace(consumeNewlines = true)
          var prefix:      Nullable[String]      = Nullable.empty
          var shownVars:   Nullable[Set[String]] = Nullable.empty
          var shownNames:  Nullable[Set[String]] = Nullable.empty
          var hiddenVars:  Nullable[Set[String]] = Nullable.empty
          var hiddenNames: Nullable[Set[String]] = Nullable.empty
          var skip = false
          // Parse optional show/hide list, then optional `as prefix-*`.
          // Member list: comma-separated identifiers and $variables.
          def parseMembers(): (Set[String], Set[String]) = {
            val names = scala.collection.mutable.Set.empty[String]
            val vars  = scala.collection.mutable.Set.empty[String]
            var more  = true
            while (more) {
              whitespace(consumeNewlines = true)
              if (scanner.peekChar() == CharCode.$dollar) {
                val _ = scanner.readChar()
                vars += identifier()
              } else if (CharCode.isNameStart(scanner.peekChar()) || scanner.peekChar() == CharCode.$minus) {
                names += identifier()
              } else {
                more = false
              }
              whitespace(consumeNewlines = true)
              if (scanner.peekChar() == CharCode.$comma) {
                val _ = scanner.readChar()
                more = true
              } else more = false
            }
            (names.toSet, vars.toSet)
          }
          if (scanIdentifier("show")) {
            val (names, vars) = parseMembers()
            shownNames = Nullable(names)
            shownVars = Nullable(vars)
          } else if (scanIdentifier("hide")) {
            val (names, vars) = parseMembers()
            hiddenNames = Nullable(names)
            hiddenVars = Nullable(vars)
          }
          whitespace(consumeNewlines = true)
          if (!skip && scanIdentifier("as")) {
            whitespace(consumeNewlines = true)
            val pBuf = new StringBuilder()
            while (
              !scanner.isDone && scanner.peekChar() != CharCode.$asterisk &&
              scanner.peekChar() != CharCode.$semicolon &&
              scanner.peekChar() != CharCode.$space &&
              scanner.peekChar() != CharCode.$tab &&
              scanner.peekChar() != CharCode.$lf
            )
              pBuf.append(scanner.readChar().toChar)
            if (scanner.peekChar() == CharCode.$asterisk) {
              val _ = scanner.readChar()
              prefix = Nullable(pBuf.toString)
            } else {
              skip = true
            }
          }
          // Optional `with ($name: expr [!default], ...)` configuration —
          // mirrors @use's parsing.
          val fwdConfigBuf = mutable.ListBuffer.empty[ConfiguredVariable]
          if (!skip && scanIdentifier("with")) {
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
              val expr = _expression()
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
          // Swallow remaining content up to ;
          while (!scanner.isDone && scanner.peekChar() != CharCode.$semicolon) {
            val _ = scanner.readChar()
          }
          val _ = scanner.scanChar(CharCode.$semicolon)
          if (skip) {
            Nullable.empty[Statement]
          } else {
            val uri = java.net.URI.create(url)
            Nullable(
              new ForwardRule(
                url = uri,
                span = spanFrom(start),
                prefix = prefix,
                shownMixinsAndFunctions = shownNames,
                shownVariables = shownVars,
                hiddenMixinsAndFunctions = hiddenNames,
                hiddenVariables = hiddenVars,
                configuration = fwdConfigBuf.toList
              )
            )
          }
        }
      case "import" =>
        // @import "url" [, "url2"] ;
        val imports = scala.collection.mutable.ListBuffer.empty[Import]
        var more    = true
        while (more) {
          whitespace(consumeNewlines = true)
          val importStart = scanner.state
          val c           = scanner.peekChar()
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
        val rawText    = selBuf.toString().trim
        var isOptional = false
        val selText    =
          if (rawText.endsWith("!optional")) {
            isOptional = true
            rawText.stripSuffix("!optional").trim
          } else rawText
        val span      = spanFrom(start)
        val selInterp = Interpolation.plain(selText, span)
        if (scanner.peekChar() == CharCode.$semicolon) {
          val _ = scanner.readChar()
        }
        Nullable(new ExtendRule(selInterp, span, isOptional))
      case "mixin" =>
        // @mixin name [(params)] { body }
        whitespace(consumeNewlines = true)
        val mixinName = identifier()
        whitespace(consumeNewlines = true)
        val params = _parseParameterList(start)
        whitespace(consumeNewlines = true)
        val kids = _children()
        Nullable(new MixinRule(mixinName, params, kids, spanFrom(start)))
      case "function" =>
        // @function name(params) { body }
        whitespace(consumeNewlines = true)
        val fnName = identifier()
        whitespace(consumeNewlines = true)
        val params = _parseParameterList(start)
        whitespace(consumeNewlines = true)
        val kids = _children()
        Nullable(new FunctionRule(fnName, params, kids, spanFrom(start)))
      case "return" =>
        // @return <expression> ;
        whitespace(consumeNewlines = true)
        val retExpr = _expression()
        whitespace(consumeNewlines = false)
        val _ = scanner.scanChar(CharCode.$semicolon)
        Nullable(new ReturnRule(retExpr, spanFrom(start)))
      case "content" =>
        // @content [(args)] ;
        whitespace(consumeNewlines = true)
        val cArgs =
          if (scanner.peekChar() == CharCode.$lparen) _parseArgumentList(start)
          else ArgumentList.empty(spanFrom(start))
        whitespace(consumeNewlines = false)
        val _ = scanner.scanChar(CharCode.$semicolon)
        Nullable(new ContentRule(cArgs, spanFrom(start)))
      case "include" =>
        // @include name [(args)] [using ($params)] [{ body } | ;]
        whitespace(consumeNewlines = true)
        val mixName = identifier()
        whitespace(consumeNewlines = true)
        val argList = if (scanner.peekChar() == CharCode.$lparen) {
          _parseArgumentList(start)
        } else {
          ArgumentList.empty(spanFrom(start))
        }
        whitespace(consumeNewlines = true)
        // Optional `using ($p1, $p2, ...)` clause declares parameters for
        // the trailing content block.
        var contentParams: ParameterList = ParameterList.empty(spanFrom(start))
        var hasUsing = false
        if (scanIdentifier("using")) {
          hasUsing = true
          whitespace(consumeNewlines = true)
          contentParams = _parseParameterList(start)
          whitespace(consumeNewlines = true)
        }
        // Optional trailing content block: `{ body }`. Required if `using`
        // was present.
        val contentBlock: Nullable[ContentBlock] =
          if (scanner.peekChar() == CharCode.$lbrace) {
            val cbStart = scanner.state
            val kids    = _children()
            Nullable(new ContentBlock(contentParams, kids, spanFrom(cbStart)))
          } else {
            if (hasUsing) scanner.error("Expected content block.")
            Nullable.empty
          }
        whitespace(consumeNewlines = false)
        if (contentBlock.isEmpty) {
          val _ = scanner.scanChar(CharCode.$semicolon)
        }
        Nullable(new IncludeRule(mixName, argList, spanFrom(start), Nullable.empty, contentBlock))
      case "media" =>
        // @media <query> { body }
        // Collect the raw query text up to the opening `{`, respecting
        // balanced parens, `#{...}` interpolations, and string literals so
        // that a `{` inside interpolation does not terminate the query.
        whitespace(consumeNewlines = true)
        val qStart = scanner.state
        val qBuf   = new StringBuilder()
        var depth  = 0
        var qQuote: Int = 0
        boundary {
          while (!scanner.isDone) {
            val ch = scanner.peekChar()
            if (ch < 0) break(())
            if (qQuote > 0) {
              if (ch == CharCode.$backslash) {
                qBuf.append(scanner.readChar().toChar)
                if (!scanner.isDone) qBuf.append(scanner.readChar().toChar)
              } else {
                if (ch == qQuote) qQuote = 0
                qBuf.append(scanner.readChar().toChar)
              }
            } else if (ch == CharCode.$double_quote || ch == CharCode.$single_quote) {
              qQuote = ch
              qBuf.append(scanner.readChar().toChar)
            } else if (ch == CharCode.$hash && scanner.peekChar(1) == CharCode.$lbrace) {
              // Copy an entire `#{...}` interpolation verbatim, balancing
              // nested braces within so an inner `{` / `}` does not end
              // the query.
              qBuf.append(scanner.readChar().toChar) // '#'
              qBuf.append(scanner.readChar().toChar) // '{'
              var iDepth = 1
              boundary {
                while (!scanner.isDone) {
                  val cc = scanner.peekChar()
                  if (cc < 0) break(())
                  if (cc == CharCode.$lbrace) iDepth += 1
                  else if (cc == CharCode.$rbrace) {
                    iDepth -= 1
                    qBuf.append(scanner.readChar().toChar)
                    if (iDepth == 0) break(())
                  } else {
                    qBuf.append(scanner.readChar().toChar)
                  }
                }
              }
            } else if (ch == CharCode.$lparen) {
              depth += 1
              qBuf.append(scanner.readChar().toChar)
            } else if (ch == CharCode.$rparen) {
              if (depth > 0) depth -= 1
              qBuf.append(scanner.readChar().toChar)
            } else if (depth == 0 && (ch == CharCode.$lbrace || ch == CharCode.$semicolon)) {
              break(())
            } else {
              qBuf.append(scanner.readChar().toChar)
            }
          }
        }
        val queryRaw    = qBuf.toString().trim
        val querySpan   = spanFrom(qStart)
        val queryInterp =
          if (queryRaw.isEmpty) Interpolation.plain("", querySpan)
          else _parseInterpolatedString(queryRaw, querySpan)
        whitespace(consumeNewlines = true)
        val kids = _children()
        Nullable(new MediaRule(queryInterp, kids, spanFrom(start)))
      case "supports" =>
        // @supports <condition> { body }
        // The condition text is collected up to the opening `{`, respecting
        // balanced parens, `#{...}` interpolations, and string literals so
        // that a `{` inside interpolation does not terminate the condition.
        whitespace(consumeNewlines = true)
        val cStart = scanner.state
        val cBuf   = new StringBuilder()
        var cDepth = 0
        var cQuote: Int = 0
        boundary {
          while (!scanner.isDone) {
            val ch = scanner.peekChar()
            if (ch < 0) break(())
            if (cQuote > 0) {
              if (ch == CharCode.$backslash) {
                cBuf.append(scanner.readChar().toChar)
                if (!scanner.isDone) cBuf.append(scanner.readChar().toChar)
              } else {
                if (ch == cQuote) cQuote = 0
                cBuf.append(scanner.readChar().toChar)
              }
            } else if (ch == CharCode.$double_quote || ch == CharCode.$single_quote) {
              cQuote = ch
              cBuf.append(scanner.readChar().toChar)
            } else if (ch == CharCode.$hash && scanner.peekChar(1) == CharCode.$lbrace) {
              // Copy an entire `#{...}` interpolation verbatim, balancing
              // nested braces within so an inner `{` / `}` does not end
              // the condition.
              cBuf.append(scanner.readChar().toChar) // '#'
              cBuf.append(scanner.readChar().toChar) // '{'
              var iDepth = 1
              boundary {
                while (!scanner.isDone) {
                  val cc = scanner.peekChar()
                  if (cc < 0) break(())
                  if (cc == CharCode.$lbrace) iDepth += 1
                  else if (cc == CharCode.$rbrace) {
                    iDepth -= 1
                    cBuf.append(scanner.readChar().toChar)
                    if (iDepth == 0) break(())
                  } else {
                    cBuf.append(scanner.readChar().toChar)
                  }
                }
              }
            } else if (ch == CharCode.$lparen) {
              cDepth += 1
              cBuf.append(scanner.readChar().toChar)
            } else if (ch == CharCode.$rparen) {
              if (cDepth > 0) cDepth -= 1
              cBuf.append(scanner.readChar().toChar)
            } else if (cDepth == 0 && (ch == CharCode.$lbrace || ch == CharCode.$semicolon)) {
              break(())
            } else {
              cBuf.append(scanner.readChar().toChar)
            }
          }
        }
        val condRawAll = cBuf.toString().trim
        val condSpan   = spanFrom(cStart)
        // `SupportsAnything` renders as `(<contents>)` in the evaluator,
        // so strip one balanced outer `(...)` layer when the source
        // already provided one (`@supports (display: grid)`). Conditions
        // like `(a) and (b)` leave the outer pair absent and are kept
        // verbatim.
        val condInnerText =
          if (
            condRawAll.length >= 2 && condRawAll.charAt(0) == '(' &&
            condRawAll.charAt(condRawAll.length - 1) == ')'
          ) {
            var outerBalanced = true
            var pd            = 0
            var i             = 0
            while (i < condRawAll.length && outerBalanced) {
              val ch = condRawAll.charAt(i)
              if (ch == '(') pd += 1
              else if (ch == ')') {
                pd -= 1
                if (pd == 0 && i != condRawAll.length - 1) outerBalanced = false
              }
              i += 1
            }
            if (outerBalanced) condRawAll.substring(1, condRawAll.length - 1).trim
            else condRawAll
          } else condRawAll
        val innerInterp =
          if (condInnerText.isEmpty) Interpolation.plain("", condSpan)
          else _parseInterpolatedString(condInnerText, condSpan)
        whitespace(consumeNewlines = true)
        val supportsKids = _children()
        // Detect function-syntax conditions like `selector(:has(> a))`
        // and build a `SupportsFunction` so the serializer emits the
        // raw form without an extra wrapping `(...)`. Matches
        // `<ident>( ... )` with balanced parens over the whole string.
        val condition: SupportsCondition = {
          val src = condRawAll
          var i   = 0
          while (i < src.length && (CharCode.isName(src.charAt(i).toInt) || src.charAt(i) == '-'))
            i += 1
          if (
            i > 0 && i < src.length && src.charAt(i) == '(' &&
            src.length >= 2 && src.charAt(src.length - 1) == ')'
          ) {
            var pd      = 0
            var matched = true
            var k       = i
            while (k < src.length && matched) {
              val c = src.charAt(k)
              if (c == '(') pd += 1
              else if (c == ')') {
                pd -= 1
                if (pd == 0 && k != src.length - 1) matched = false
              }
              k += 1
            }
            if (matched) {
              val fnName     = src.substring(0, i)
              val fnArgsText = src.substring(i + 1, src.length - 1)
              val nameInterp = Interpolation.plain(fnName, condSpan)
              val argsInterp =
                if (fnArgsText.isEmpty) Interpolation.plain("", condSpan)
                else _parseInterpolatedString(fnArgsText, condSpan)
              SupportsFunction(nameInterp, argsInterp, condSpan)
            } else SupportsAnything(innerInterp, condSpan)
          } else SupportsAnything(innerInterp, condSpan)
        }
        Nullable(new SupportsRule(condition, supportsKids, spanFrom(start)))
      case "keyframes" | "-webkit-keyframes" | "-moz-keyframes" | "-o-keyframes" | "-ms-keyframes" =>
        // @keyframes <name> { <keyframe-block>* }
        // Each keyframe block is `<selector-list> { <declaration>* }`
        // where each selector is `0%`, `50%`, `from`, or `to`. We
        // represent the whole keyframes rule as a generic `AtRule` whose
        // children are `StyleRule`s with the (normalized) selector text.
        whitespace(consumeNewlines = true)
        val kfNameBuf = new StringBuilder()
        while (
          !scanner.isDone && scanner.peekChar() != CharCode.$lbrace &&
          scanner.peekChar() != CharCode.$space && scanner.peekChar() != CharCode.$tab &&
          scanner.peekChar() != CharCode.$lf && scanner.peekChar() != CharCode.$cr
        )
          kfNameBuf.append(scanner.readChar().toChar)
        val kfName = kfNameBuf.toString()
        whitespace(consumeNewlines = true)
        scanner.expectChar(CharCode.$lbrace)
        whitespace(consumeNewlines = true)
        val kfBlocks = mutable.ListBuffer.empty[Statement]
        while (!scanner.isDone && scanner.peekChar() != CharCode.$rbrace) {
          val blockStart = scanner.state
          // Read selector text up to `{`, splitting comma-separated
          // entries and normalizing `from`/`to`.
          val selBuf = new StringBuilder()
          while (!scanner.isDone && scanner.peekChar() != CharCode.$lbrace && scanner.peekChar() != CharCode.$rbrace)
            selBuf.append(scanner.readChar().toChar)
          val rawSel = selBuf.toString().trim
          if (rawSel.isEmpty) {
            // Malformed: abort remainder of body.
            while (!scanner.isDone && scanner.peekChar() != CharCode.$rbrace) {
              val _ = scanner.readChar()
            }
          } else {
            val normSel = rawSel
              .split(',')
              .toList
              .map(_.trim)
              .map {
                case "from" => "0%"
                case "to"   => "100%"
                case other  => other
              }
              .mkString(", ")
            val selSpan   = spanFrom(blockStart)
            val selInterp = Interpolation.plain(normSel, selSpan)
            val blockKids = _children()
            kfBlocks += StyleRule(selInterp, blockKids, spanFrom(blockStart))
          }
          whitespace(consumeNewlines = true)
        }
        scanner.expectChar(CharCode.$rbrace)
        val nameSpan     = spanFrom(start)
        val atNameInterp = Interpolation.plain(name, nameSpan)
        val atValue      =
          if (kfName.isEmpty) Nullable.empty[Interpolation]
          else Nullable(Interpolation.plain(kfName, nameSpan))
        Nullable(
          new AtRule(
            name = atNameInterp,
            span = spanFrom(start),
            value = atValue,
            childStatements = Nullable(kfBlocks.toList)
          )
        )
      case "each" =>
        // @each $var[, $var, ...] in <expression> { body }
        whitespace(consumeNewlines = true)
        val vars = mutable.ListBuffer.empty[String]
        // First variable is required.
        if (scanner.peekChar() != CharCode.$dollar) scanner.error("Expected variable.")
        val _ = scanner.readChar()
        vars += identifier()
        whitespace(consumeNewlines = true)
        // Additional comma-separated variables.
        while (scanner.peekChar() == CharCode.$comma) {
          val _ = scanner.readChar()
          whitespace(consumeNewlines = true)
          if (scanner.peekChar() != CharCode.$dollar) scanner.error("Expected variable.")
          val _ = scanner.readChar()
          vars += identifier()
          whitespace(consumeNewlines = true)
        }
        if (!scanIdentifier("in")) scanner.error("Expected \"in\".")
        whitespace(consumeNewlines = true)
        // Collect the list expression text up to the opening `{`, respecting
        // balanced brackets/parens, quoted strings, and `#{...}`.
        val eStart = scanner.state
        val eBuf   = new StringBuilder()
        var eDepth = 0
        var eQuote: Int = 0
        boundary {
          while (!scanner.isDone) {
            val ch = scanner.peekChar()
            if (ch < 0) break(())
            if (eQuote > 0) {
              if (ch == CharCode.$backslash) {
                eBuf.append(scanner.readChar().toChar)
                if (!scanner.isDone) eBuf.append(scanner.readChar().toChar)
              } else {
                if (ch == eQuote) eQuote = 0
                eBuf.append(scanner.readChar().toChar)
              }
            } else if (ch == CharCode.$double_quote || ch == CharCode.$single_quote) {
              eQuote = ch
              eBuf.append(scanner.readChar().toChar)
            } else if (ch == CharCode.$lparen || ch == CharCode.$lbracket) {
              eDepth += 1
              eBuf.append(scanner.readChar().toChar)
            } else if (ch == CharCode.$rparen || ch == CharCode.$rbracket) {
              if (eDepth > 0) eDepth -= 1
              eBuf.append(scanner.readChar().toChar)
            } else if (eDepth == 0 && ch == CharCode.$lbrace) {
              break(())
            } else {
              eBuf.append(scanner.readChar().toChar)
            }
          }
        }
        val listRaw  = eBuf.toString().trim
        val listExpr = _parseSimpleExpression(listRaw, spanFrom(eStart))
        whitespace(consumeNewlines = true)
        val eachKids = _children()
        Nullable(new EachRule(vars.toList, listExpr, eachKids, spanFrom(start)))
      case "debug" =>
        // @debug <expression> ;
        whitespace(consumeNewlines = true)
        val dExpr = _expression()
        whitespace(consumeNewlines = false)
        val _ = scanner.scanChar(CharCode.$semicolon)
        Nullable(new DebugRule(dExpr, spanFrom(start)))
      case "warn" =>
        // @warn <expression> ;
        whitespace(consumeNewlines = true)
        val wExpr = _expression()
        whitespace(consumeNewlines = false)
        val _ = scanner.scanChar(CharCode.$semicolon)
        Nullable(new WarnRule(wExpr, spanFrom(start)))
      case "error" =>
        // @error <expression> ;
        whitespace(consumeNewlines = true)
        val eExpr = _expression()
        whitespace(consumeNewlines = false)
        val _ = scanner.scanChar(CharCode.$semicolon)
        Nullable(new ErrorRule(eExpr, spanFrom(start)))
      case "at-root" =>
        // @at-root [<selector>] { body }
        // If a selector precedes the `{`, wrap the body in a StyleRule so
        // that children are re-parented under a fresh top-level rule.
        whitespace(consumeNewlines = true)
        val selBuf  = new StringBuilder()
        var arDepth = 0
        var arQuote: Int = 0
        boundary {
          while (!scanner.isDone) {
            val ch = scanner.peekChar()
            if (ch < 0) break(())
            if (arQuote > 0) {
              if (ch == CharCode.$backslash) {
                selBuf.append(scanner.readChar().toChar)
                if (!scanner.isDone) selBuf.append(scanner.readChar().toChar)
              } else {
                if (ch == arQuote) arQuote = 0
                selBuf.append(scanner.readChar().toChar)
              }
            } else if (ch == CharCode.$double_quote || ch == CharCode.$single_quote) {
              arQuote = ch
              selBuf.append(scanner.readChar().toChar)
            } else if (ch == CharCode.$lparen || ch == CharCode.$lbracket) {
              arDepth += 1
              selBuf.append(scanner.readChar().toChar)
            } else if (ch == CharCode.$rparen || ch == CharCode.$rbracket) {
              if (arDepth > 0) arDepth -= 1
              selBuf.append(scanner.readChar().toChar)
            } else if (arDepth == 0 && ch == CharCode.$lbrace) {
              break(())
            } else {
              selBuf.append(scanner.readChar().toChar)
            }
          }
        }
        val selText = selBuf.toString().trim
        val arKids  = _children()
        val wrapped: List[Statement] =
          if (selText.isEmpty) arKids
          else {
            val selSpan   = spanFrom(start)
            val selInterp = Interpolation.plain(selText, selSpan)
            List(StyleRule(selInterp, arKids, selSpan))
          }
        Nullable(new AtRootRule(wrapped, spanFrom(start)))
      case _ =>
        // Generic at-rule: just skip to ; or {
        val valueBuf = new StringBuilder()
        while (!scanner.isDone) {
          val c = scanner.peekChar()
          if (c == CharCode.$semicolon || c == CharCode.$lbrace || c == CharCode.$rbrace) {
            val valueText  = valueBuf.toString().trim
            val nameSpan   = spanFrom(start)
            val nameInterp = Interpolation.plain(name, nameSpan)

            if (c == CharCode.$lbrace) {
              // _children() expects to consume the opening `{` itself.
              val kids        = _children()
              val valueInterp = if (valueText.nonEmpty) Nullable(Interpolation.plain(valueText, nameSpan)) else Nullable.empty
              return Nullable(
                new AtRule(
                  name = nameInterp,
                  span = spanFrom(start),
                  value = valueInterp,
                  childStatements = Nullable(kids)
                )
              )
            } else if (c == CharCode.$semicolon) {
              scanner.readChar()
              val valueInterp = if (valueText.nonEmpty) Nullable(Interpolation.plain(valueText, nameSpan)) else Nullable.empty
              return Nullable(
                new AtRule(
                  name = nameInterp,
                  span = spanFrom(start),
                  value = valueInterp,
                  childStatements = Nullable.empty
                )
              )
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

  /** Parses a parenthesized parameter list for a `@mixin` or `@function` declaration. If the next character is not `(`, returns an empty parameter list (`@mixin foo { }`). Supports rest parameters
    * (`$args...`) and defaults (`$p: expr`). Does not yet support keyword-rest.
    */
  private def _parseParameterList(startState: ssg.sass.util.LineScannerState): ParameterList =
    if (scanner.peekChar() != CharCode.$lparen) {
      ParameterList.empty(spanFrom(startState))
    } else {
      scanner.expectChar(CharCode.$lparen)
      whitespace(consumeNewlines = true)
      val params = scala.collection.mutable.ListBuffer.empty[Parameter]
      var restParam:       Nullable[String] = Nullable.empty
      var kwargsRestParam: Nullable[String] = Nullable.empty
      var more = scanner.peekChar() != CharCode.$rparen
      while (more) {
        whitespace(consumeNewlines = true)
        val paramStart = scanner.state
        val pname      = variableName()
        whitespace(consumeNewlines = true)
        if (
          scanner.peekChar() == CharCode.$dot &&
          scanner.peekChar(1) == CharCode.$dot &&
          scanner.peekChar(2) == CharCode.$dot
        ) {
          val _ = scanner.readChar()
          val _ = scanner.readChar()
          val _ = scanner.readChar()
          restParam = Nullable(pname)
          whitespace(consumeNewlines = true)
          // Optional second rest parameter `, $kwargs...` for keyword args.
          if (scanner.scanChar(CharCode.$comma)) {
            whitespace(consumeNewlines = true)
            val kname = variableName()
            whitespace(consumeNewlines = true)
            if (
              scanner.peekChar() == CharCode.$dot &&
              scanner.peekChar(1) == CharCode.$dot &&
              scanner.peekChar(2) == CharCode.$dot
            ) {
              val _ = scanner.readChar()
              val _ = scanner.readChar()
              val _ = scanner.readChar()
              kwargsRestParam = Nullable(kname)
              whitespace(consumeNewlines = true)
            } else scanner.error("Expected `...` after keyword rest parameter.")
          }
          more = false
        } else if (scanner.peekChar() == CharCode.$colon) {
          val _ = scanner.readChar()
          whitespace(consumeNewlines = true)
          // Collect raw expression text up to the next top-level `,` or `)`,
          // respecting nesting (parens/brackets) and string quoting. This
          // avoids `_expression()` over-consuming past the parameter boundary.
          val defStart = scanner.state
          val defBuf   = new StringBuilder()
          var depth    = 0
          var dQuote: Int = 0
          boundary {
            while (!scanner.isDone) {
              val dch = scanner.peekChar()
              if (dch < 0) break(())
              if (dQuote > 0) {
                if (dch == CharCode.$backslash) {
                  defBuf.append(scanner.readChar().toChar)
                  if (!scanner.isDone) defBuf.append(scanner.readChar().toChar)
                } else {
                  if (dch == dQuote) dQuote = 0
                  defBuf.append(scanner.readChar().toChar)
                }
              } else if (dch == CharCode.$double_quote || dch == CharCode.$single_quote) {
                dQuote = dch
                defBuf.append(scanner.readChar().toChar)
              } else if (dch == CharCode.$lparen || dch == CharCode.$lbracket) {
                depth += 1
                defBuf.append(scanner.readChar().toChar)
              } else if (dch == CharCode.$rparen || dch == CharCode.$rbracket) {
                if (depth == 0) break(())
                depth -= 1
                defBuf.append(scanner.readChar().toChar)
              } else if (depth == 0 && dch == CharCode.$comma) {
                break(())
              } else {
                defBuf.append(scanner.readChar().toChar)
              }
            }
          }
          val defRaw = defBuf.toString().trim
          if (defRaw.isEmpty) scanner.error("Expected expression.")
          val defaultExpr = _parseSimpleExpression(defRaw, spanFrom(defStart))
          params += new Parameter(pname, spanFrom(paramStart), Nullable(defaultExpr))
          whitespace(consumeNewlines = true)
          if (scanner.scanChar(CharCode.$comma)) {
            whitespace(consumeNewlines = true)
            more = scanner.peekChar() != CharCode.$rparen
          } else more = false
        } else {
          params += new Parameter(pname, spanFrom(paramStart))
          whitespace(consumeNewlines = true)
          if (scanner.scanChar(CharCode.$comma)) {
            whitespace(consumeNewlines = true)
            more = scanner.peekChar() != CharCode.$rparen
          } else more = false
        }
      }
      whitespace(consumeNewlines = true)
      scanner.expectChar(CharCode.$rparen)
      new ParameterList(params.toList, spanFrom(startState), restParam, kwargsRestParam)
    }

  /** Parses a parenthesized argument list for a `@include` invocation. Supports positional arguments, named arguments (`$name: value`), and a trailing rest argument (`$list...`, or any expression
    * followed by `...`). Mixed positional + named is allowed; named args may appear after any positional args.
    */
  private def _parseArgumentList(startState: ssg.sass.util.LineScannerState): ArgumentList = {
    scanner.expectChar(CharCode.$lparen)
    whitespace(consumeNewlines = true)
    val positional = scala.collection.mutable.ListBuffer.empty[Expression]
    val named      = scala.collection.mutable.LinkedHashMap.empty[String, Expression]
    var rest: Nullable[Expression] = Nullable.empty
    var more = scanner.peekChar() != CharCode.$rparen
    while (more) {
      whitespace(consumeNewlines = true)
      val exprStart = scanner.state
      // Detect a named argument: `$name: value`. We look ahead through a
      // potential `$ident` followed by optional whitespace and a single `:`
      // (not `::`). On match, consume the name and colon; otherwise leave
      // the scanner where it was and fall through to positional parsing.
      var namedKey: Nullable[String] = Nullable.empty
      if (scanner.peekChar() == CharCode.$dollar) {
        val saved   = scanner.state
        val _       = scanner.readChar() // consume '$'
        val nameBuf = new StringBuilder()
        while (!scanner.isDone && CharCode.isName(scanner.peekChar()))
          nameBuf.append(scanner.readChar().toChar)
        val candidate = nameBuf.toString()
        whitespace(consumeNewlines = true)
        if (
          candidate.nonEmpty && scanner.peekChar() == CharCode.$colon &&
          scanner.peekChar(1) != CharCode.$colon
        ) {
          val _ = scanner.readChar() // consume ':'
          whitespace(consumeNewlines = true)
          namedKey = Nullable(candidate.replace('_', '-'))
        } else {
          scanner.state = saved
        }
      }
      val buf   = new StringBuilder()
      var depth = 0
      var inQuote: Int = 0
      boundary {
        while (!scanner.isDone) {
          val ch = scanner.peekChar()
          if (ch < 0) break(())
          if (inQuote > 0) {
            if (ch == CharCode.$backslash) {
              buf.append(scanner.readChar().toChar)
              if (!scanner.isDone) buf.append(scanner.readChar().toChar)
            } else {
              if (ch == inQuote) inQuote = 0
              buf.append(scanner.readChar().toChar)
            }
          } else if (ch == CharCode.$double_quote || ch == CharCode.$single_quote) {
            inQuote = ch
            buf.append(scanner.readChar().toChar)
          } else if (ch == CharCode.$lparen || ch == CharCode.$lbracket) {
            depth += 1
            buf.append(scanner.readChar().toChar)
          } else if (ch == CharCode.$rparen || ch == CharCode.$rbracket) {
            if (depth == 0) break(())
            depth -= 1
            buf.append(scanner.readChar().toChar)
          } else if (depth == 0 && ch == CharCode.$comma) {
            break(())
          } else if (
            depth == 0 && ch == CharCode.$dot &&
            scanner.peekChar(1) == CharCode.$dot &&
            scanner.peekChar(2) == CharCode.$dot
          ) {
            break(())
          } else {
            buf.append(scanner.readChar().toChar)
          }
        }
      }
      val raw = buf.toString().trim
      if (raw.isEmpty) scanner.error("Expected expression.")
      val expr = _parseSimpleExpression(raw, spanFrom(exprStart))
      whitespace(consumeNewlines = true)
      if (
        scanner.peekChar() == CharCode.$dot &&
        scanner.peekChar(1) == CharCode.$dot &&
        scanner.peekChar(2) == CharCode.$dot
      ) {
        val _ = scanner.readChar()
        val _ = scanner.readChar()
        val _ = scanner.readChar()
        rest = Nullable(expr)
        whitespace(consumeNewlines = true)
        more = false
      } else {
        namedKey.fold {
          positional += expr
        } { k =>
          named.update(k, expr)
        }
        whitespace(consumeNewlines = true)
        if (scanner.scanChar(CharCode.$comma)) {
          whitespace(consumeNewlines = true)
          more = scanner.peekChar() != CharCode.$rparen
        } else more = false
      }
    }
    whitespace(consumeNewlines = true)
    scanner.expectChar(CharCode.$rparen)
    new ArgumentList(positional.toList, named.toMap, Map.empty, spanFrom(startState), rest)
  }

  /** Parses a variable declaration: `$name: value;` */
  private def _variableDeclaration(): VariableDeclaration = {
    val start = scanner.state
    val name  = variableName()
    whitespace(consumeNewlines = true)
    scanner.expectChar(CharCode.$colon)
    whitespace(consumeNewlines = true)

    val expression = _expression()
    whitespace(consumeNewlines = false)

    // Handle !default / !global flags (simplified)
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
    new VariableDeclaration(name, expression, spanFrom(start), Nullable.empty, isGuarded, isGlobal)
  }

  /** Parses a style rule: `selector { children }`. */
  private def _styleRule(): StyleRule = {
    val start          = scanner.state
    val selectorInterp = styleRuleSelector()
    val kids           = _children()
    StyleRule(selectorInterp, kids, spanFrom(start))
  }

  /** Parses a block of children: `{ stmt; stmt; }`. Called after the `{` has NOT yet been consumed.
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
    *   - a nested style rule
    *   - a declaration (name: value;)
    *   - a variable declaration
    *   - a comment
    *   - an at-rule
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
    // A property name may begin with `#{...}` interpolation, e.g.
    // `#{$prefix}-color: red`. In that case we read a mixed name
    // (identifier chunks + interpolation segments) into an Interpolation
    // and parse the rest as a declaration directly.
    if (scanner.peekChar() == CharCode.$hash && scanner.peekChar(1) == CharCode.$lbrace) {
      val savedState = scanner.state
      val nameInterp = _readInterpolatedName()
      whitespace(consumeNewlines = false)
      if (scanner.peekChar() != CharCode.$colon) {
        scanner.state = savedState
        return Nullable(_styleRule())
      }
      val _ = scanner.readChar() // ':'
      whitespace(consumeNewlines = true)
      val expression = _expression()
      whitespace(consumeNewlines = false)
      val important1 = _tryScanImportant()
      scanner.scanChar(CharCode.$semicolon)
      return Nullable(Declaration(nameInterp, expression, spanFrom(start), isImportant = important1))
    }
    // Try to read an identifier followed by `:` to detect a declaration.
    if (!lookingAtIdentifier()) {
      // Selector starts with something other than identifier; treat as style rule
      return Nullable(_styleRule())
    }
    val savedState = scanner.state
      // Peek for an interpolated-in-the-middle name like `border-#{$x}`.
      // We scan the raw source until whitespace/`:`/`;`/`{` to see if a
      // `#{` occurs — if so, use the interpolated-name reader.
      {
        val src       = scanner.string
        val pos0      = savedState.position
        var k         = pos0
        var hasInterp = false
        var done      = false
        while (k < src.length && !done) {
          val ch = src.charAt(k)
          if (ch == ':' || ch == ';' || ch == '{' || ch == '}' || ch == '\n' || ch == '\r') done = true
          else if (ch == '#' && k + 1 < src.length && src.charAt(k + 1) == '{') { hasInterp = true; done = true }
          else k += 1
        }
        if (hasInterp) {
          val nameInterp = _readInterpolatedName()
          whitespace(consumeNewlines = false)
          if (scanner.peekChar() != CharCode.$colon) {
            scanner.state = savedState
            return Nullable(_styleRule())
          }
          val _ = scanner.readChar() // ':'
          whitespace(consumeNewlines = true)
          val expression = _expression()
          whitespace(consumeNewlines = false)
          val important2 = _tryScanImportant()
          scanner.scanChar(CharCode.$semicolon)
          return Nullable(Declaration(nameInterp, expression, spanFrom(start), isImportant = important2))
        }
      }
    val name =
      try identifier()
      catch {
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
      if (
        afterColon == CharCode.$colon || (CharCode.isNameStart(afterColon) && !scanner.isDone &&
          scanner.string.substring(savedState.position).takeWhile(c => c != '{' && c != ';' && c != '}').contains('{') &&
          !scanner.string.substring(savedState.position).takeWhile(c => c != '{' && c != ';' && c != '}').contains(';'))
      ) {
        // Looks like a selector — rewind and parse as style rule
        scanner.state = savedState
        return Nullable(_styleRule())
      }

      // Parse as declaration
      whitespace(consumeNewlines = true)
      val nameSpan = {
        val s        = savedState
        val endLoc   = scanner.sourceFile.location(s.position + name.length)
        val startLoc = scanner.sourceFile.location(s.position)
        scanner.sourceFile.span(startLoc.offset, endLoc.offset)
      }
      val nameInterp = Interpolation.plain(name, nameSpan)

      // CSS custom property (`--foo: value`): everything after the colon
      // is a raw string. SassScript operators (`+`, `-`, ...) are NOT
      // evaluated — `--foo: 1 + 2` emits `--foo: 1 + 2;` literally. Only
      // `#{...}` interpolation is parsed, so `--foo: #{$x}` still works.
      if (name.startsWith("--")) {
        val rawStart    = scanner.state
        val valueInterp = _readCustomPropertyValue(rawStart)
        whitespace(consumeNewlines = false)
        scanner.scanChar(CharCode.$semicolon)
        val strExpr = StringExpression(valueInterp, hasQuotes = false)
        return Nullable(Declaration.notSassScript(nameInterp, strExpr, spanFrom(start)))
      }

      // If we're at end of declaration (no value), it's a nested declaration
      // For simplicity, require a value.
      val expression = _expression()
      whitespace(consumeNewlines = false)
      val important3 = _tryScanImportant()
      scanner.scanChar(CharCode.$semicolon)
      Nullable(Declaration(nameInterp, expression, spanFrom(start), isImportant = important3))
    } else {
      // Not a declaration — rewind and parse as style rule
      scanner.state = savedState
      Nullable(_styleRule())
    }
  }

  /** If the scanner is positioned at `!important` (optionally with whitespace between the `!` and `important`), consume it and return true. Otherwise leave the scanner unchanged and return false.
    */
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
    Nullable(new SilentComment(scanner.substring(start.position), spanFrom(start)))
  }

  /** Parses a loud CSS comment (`/* ... */`). */
  private def _loudComment(): Nullable[Statement] = {
    val start = scanner.state
    loudComment()
    val text   = scanner.substring(start.position)
    val interp = Interpolation.plain(text, spanFrom(start))
    Nullable(new LoudComment(interp))
  }

  /** Parses a single expression. Minimal: handles numbers, strings, identifiers, variables. Multi-value expressions (space-separated lists, comma-separated lists, math operators) are handled as a
    * best effort by collecting raw text as an unquoted string.
    */
  private def _expression(): Expression = {
    val start = scanner.state
    val c     = scanner.peekChar()
    if (c < 0) scanner.error("Expected expression.")

    // Collect until end-of-statement markers. Respects quoted strings and
    // `#{...}` interpolation so braces inside them don't terminate collection.
    val buf      = new StringBuilder()
    var brackets = 0
    var inQuote:     Int = 0 // 0 = not in string, else the opening quote char
    var interpDepth: Int = 0 // brace depth inside #{...}
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

    val raw  = buf.toString().trim
    val span = spanFrom(start)

    if (raw.isEmpty) scanner.error("Expected expression.", start.position, 0)

    // Try to parse as a simple form
    _parseSimpleExpression(raw, span)
  }

  /** Best-effort parsing of a simple expression string into an Expression node. Handles: bare identifiers, variables, numbers with units, quoted strings, booleans (true/false/null). Falls back to
    * unquoted StringExpression.
    */
  private def _parseSimpleExpression(raw: String, span: FileSpan): Expression = {
    val trimmed = raw.trim
    if (trimmed.isEmpty) return new NullExpression(span)

    // Boolean / null literals
    if (trimmed == "true") return new BooleanExpression(value = true, span)
    if (trimmed == "false") return new BooleanExpression(value = false, span)
    if (trimmed == "null") return new NullExpression(span)

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

    // Fallback: unquoted string expression
    StringExpression(Interpolation.plain(trimmed, span), hasQuotes = false)
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
            var depth = 0
            var done  = false
            while (i < n && !done) {
              val cc = s.charAt(i)
              if (cc == '(') depth += 1
              else if (cc == ')') {
                depth -= 1
                if (depth == 0) { i += 1; done = true }
              }
              if (!done) i += 1
            }
            if (!done) break(Nil)
          }
          tokens += s.substring(start, i)
        } else if (c == '(' || c == '[') {
          val open  = c
          val close = if (open == '(') ')' else ']'
          val start = i
          var depth = 0
          var done  = false
          while (i < n && !done) {
            val cc = s.charAt(i)
            if (cc == open) depth += 1
            else if (cc == close) {
              depth -= 1
              if (depth == 0) { i += 1; done = true }
            }
            if (!done) i += 1
          }
          if (!done) break(Nil)
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
    // Special-case: url() — passes through as an unquoted string. Skip for now.
    if (namespace.isEmpty && name == "url") return None

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
    val commaSplit  = if (argsText.isEmpty) Nil else _splitTopLevel(argsText, ',')
    val rawArgs: List[String] =
      if (argsText.isEmpty) Nil
      else if (isColorFn && commaSplit.length <= 1) {
        // No top-level comma: parse as modern space-separated channels with
        // an optional `/ <alpha>` trailing segment.
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
      val valueExpr = _parseSimpleExpression(valueText, span)
      if (keyName.nonEmpty) named.update(keyName, valueExpr)
      else positional += valueExpr
    }

    val arguments = new ArgumentList(positional.toList, named.toMap, Map.empty, span)
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
    val expr = _expression()
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

  /** Parses the contents as a single `@use` rule. */
  def parseUseRule(): (UseRule, List[ParseTimeWarning]) =
    throw new UnsupportedOperationException(
      "StylesheetParser.parseUseRule: UseRule construction not yet supported"
    )

  /** Parses a function signature of the format allowed by Node Sass's functions option and returns its name and parameter list.
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
