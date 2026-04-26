/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Recursive descent parser — converts a token stream into an AST.
 *
 * Original source: terser lib/parse.js (parse function, lines 1100-3630)
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: parse() closure → Parser class, snake_case → camelCase
 *   Convention: Mutable state as class vars, boundary/break for early return
 *   Idiom: embed_tokens pattern → embedTokens, JS constructor dispatch → match + new
 *   Audited: 2026-04-04
 *
 * Covenant: full-port
 * Covenant-js-reference: terser lib/parse.js (parse function, lines 1100-3630)
 * Covenant-verified: 2026-04-26
 */
package ssg
package js
package parse

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary

import ssg.js.ast.*

/** Options that control parser behavior. */
final case class ParserOptions(
  bareReturns:   Boolean = false,
  expression:    Boolean = false,
  filename:      String = "",
  html5Comments: Boolean = true,
  module:        Boolean = false,
  shebang:       Boolean = true,
  strict:        Boolean = false
)

object ParserOptions {
  val Defaults: ParserOptions = ParserOptions()
}

/** Recursive descent JavaScript parser.
  *
  * Port of the `parse()` closure from terser's parse.js. The JS closure's mutable `S` state object is represented as mutable fields on this class. All ~100 nested parsing functions become methods.
  */
class Parser(options: ParserOptions = ParserOptions.Defaults) {

  // ---- Internal state (set per parse) ----
  private var input:        Tokenizer             = null.asInstanceOf[Tokenizer] // @nowarn — set in parse()
  private var token:        AstToken              = AstToken.Empty
  private var prevTok:      AstToken              = AstToken.Empty
  private var peeked:       AstToken | Null       = null
  private var inFunction:   Int                   = 0
  private var inAsync:      Int                   = -1
  private var inGenerator:  Int                   = -1
  private var inDirectives: Boolean               = true
  private var inLoop:       Int                   = 0
  private var inClass:      Boolean               = false
  private var labels:       ArrayBuffer[AstLabel] = ArrayBuffer.empty

  // Maps start tokens to count of comments found outside of their parens.
  // We use a java.util.IdentityHashMap since AstToken is a case class.
  private val outerCommentsBeforeCounts: java.util.IdentityHashMap[AstToken, Integer] =
    new java.util.IdentityHashMap()

  // =========================================================================
  // Public API
  // =========================================================================

  /** Parse JavaScript source text and return the top-level AST node. */
  def parse(text: String): AstToplevel = {
    input = new Tokenizer(text, options.filename, options.html5Comments, options.shebang)
    peeked = null
    inFunction = 0
    inAsync = -1
    inGenerator = -1
    inDirectives = true
    inLoop = 0
    inClass = false
    labels = ArrayBuffer.empty

    token = next()

    if (options.expression) {
      val result = expression(commas = true)
      // Wrap in toplevel
      val tl = new AstToplevel
      tl.start = result.start
      tl.body.addOne(result)
      tl.end = result.end
      tl
    } else {
      parseToplevel()
    }
  }

  /** Parse a single expression from the given source text. */
  def parseExpression(text: String): AstNode = {
    input = new Tokenizer(text, options.filename, options.html5Comments, options.shebang)
    peeked = null
    inFunction = 0
    inAsync = -1
    inGenerator = -1
    inDirectives = true
    inLoop = 0
    inClass = false
    labels = ArrayBuffer.empty

    token = next()
    expression(commas = true)
  }

  // =========================================================================
  // Token helpers
  // =========================================================================

  private def isToken(tok: AstToken, tokenType: String, value: String = null.asInstanceOf[String]): Boolean = // @nowarn — null sentinel
    tok.tokenType == tokenType && (value == null || tok.value == value)

  private def is(tokenType: String, value: String = null.asInstanceOf[String]): Boolean = // @nowarn — null sentinel
    isToken(token, tokenType, value)

  private def peek(): AstToken = {
    val p = peeked
    if (p != null) {
      p.nn
    } else {
      val t = input.nextToken()
      peeked = t
      t
    }
  }

  private def next(): AstToken = {
    prevTok = token
    if (peeked == null) peek()
    token = peeked.nn
    peeked = null
    inDirectives = inDirectives && (
      token.tokenType == Token.StringType || is(Token.Punc, ";")
    )
    token
  }

  private def croak(msg: String, line: Int = -1, col: Int = -1, pos: Int = -1): Nothing = {
    val l = if (line >= 0) line else input.tokline
    val c = if (col >= 0) col else input.tokcol
    val p = if (pos >= 0) pos else input.tokpos
    throw JsParseError(msg, input.filename, l, c, p)
  }

  private def tokenError(tok: AstToken, msg: String): Nothing =
    croak(msg, tok.line, tok.col)

  private def unexpected(tok: AstToken = null.asInstanceOf[AstToken]): Nothing = { // @nowarn — null sentinel
    val t = if (tok != null) tok else token
    tokenError(t, "Unexpected token: " + t.tokenType + " (" + t.value + ")")
  }

  private def expectToken(tokenType: String, value: String): AstToken =
    if (is(tokenType, value)) {
      next()
    } else {
      tokenError(
        token,
        "Unexpected token " + token.tokenType + " \u00ab" + token.value + "\u00bb" +
          ", expected " + tokenType + " \u00ab" + value + "\u00bb"
      )
    }

  private def expect(punc: String): AstToken = expectToken(Token.Punc, punc)

  private def hasNewlineBefore(tok: AstToken): Boolean =
    tok.nlb || tok.commentsBefore.exists(_.nlb)

  private def canInsertSemicolon(): Boolean =
    !options.strict &&
      (is(Token.Eof) || is(Token.Punc, "}") || hasNewlineBefore(token))

  private def isInGenerator(): Boolean = inGenerator == inFunction

  private def isInAsync(): Boolean = inAsync == inFunction

  private def canAwait(): Boolean =
    inAsync == inFunction ||
      (inFunction == 0 && input.hasDirective("use strict"))

  private def semicolon(optional: Boolean = false): Unit =
    if (is(Token.Punc, ";")) next()
    else if (!optional && !canInsertSemicolon()) unexpected()

  private def parenthesised(): AstNode = {
    expect("(")
    val exp = expression(commas = true)
    expect(")")
    exp
  }

  private def handleRegexp(): Unit =
    if (is(Token.Operator, "/") || is(Token.Operator, "/=")) {
      peeked = null
      token = input.nextToken(forceRegexp = token.value.substring(1))
    }

  // =========================================================================
  // embed_tokens pattern
  // =========================================================================

  /** Execute a parsing function, recording start/end tokens on the result. */
  private inline def embedTokens(inline parser: => AstNode): AstNode = {
    val start = token
    val expr  = parser
    expr.start = start
    expr.end = prevTok
    expr
  }

  // =========================================================================
  // Statement parsing
  // =========================================================================

  private def statement(
    isExportDefault: Boolean = false,
    isForBody:       Boolean = false,
    isIfBody:        Boolean = false
  ): AstNode = {
    val stmtStart = token
    val result    = statementInner(isExportDefault, isForBody, isIfBody)
    result.start = stmtStart
    result.end = prevTok
    result
  }

  private def statementInner(
    isExportDefault: Boolean,
    isForBody:       Boolean,
    isIfBody:        Boolean
  ): AstNode = {
    handleRegexp()
    token.tokenType match {
      case Token.StringType =>
        if (inDirectives) {
          val peekTok = peek()
          if (
            !input.latestRaw.contains("\\") &&
            (isToken(peekTok, Token.Punc, ";") ||
              isToken(peekTok, Token.Punc, "}") ||
              hasNewlineBefore(peekTok) ||
              isToken(peekTok, Token.Eof))
          ) {
            input.addDirective(token.value)
          } else {
            inDirectives = false
          }
        }
        val dir  = inDirectives
        val stat = simpleStatement()
        if (dir && stat.body.nn.isInstanceOf[AstString]) {
          val d = new AstDirective
          d.value = stat.body.nn.asInstanceOf[AstString].value
          d.quote = stat.body.nn.asInstanceOf[AstString].quote
          d.start = stat.start
          d.end = stat.end
          d
        } else {
          stat
        }

      case Token.TemplateHead | Token.Num | Token.BigInt | Token.Regexp | Token.Operator | Token.Atom =>
        simpleStatement()

      case Token.Name =>
        if (token.value == "async" && isToken(peek(), Token.Keyword, "function")) {
          next()
          next()
          if (isForBody) {
            croak("functions are not allowed as the body of a loop")
          }
          functionDef(isDefun = true, isGenerator = false, isAsync = true, isExportDefault = isExportDefault)
        } else if (token.value == "import" && !isToken(peek(), Token.Punc, "(") && !isToken(peek(), Token.Punc, ".")) {
          next()
          val node = importStatement()
          semicolon()
          node
        } else if (token.value == "using" && isToken(peek(), Token.Name) && !hasNewlineBefore(peek())) {
          next()
          val node = usingDef()
          semicolon()
          node
        } else if (token.value == "await" && canAwait() && isToken(peek(), Token.Name, "using") && !hasNewlineBefore(peek())) {
          val nextNext = input.peekNextTokenStartOrNewline()
          if (input.chStartsBindingIdentifier(nextNext.char, nextNext.pos)) {
            next()
            val node = awaitUsingDef()
            semicolon()
            node
          } else {
            if (isToken(peek(), Token.Punc, ":")) {
              labeledStatement()
            } else {
              simpleStatement()
            }
          }
        } else if (isToken(peek(), Token.Punc, ":")) {
          labeledStatement()
        } else {
          simpleStatement()
        }

      case Token.PrivateName =>
        if (!inClass) {
          croak("Private field must be used in an enclosing class")
        }
        simpleStatement()

      case Token.Punc =>
        token.value match {
          case "{" =>
            val bs = new AstBlockStatement
            bs.start = token
            bs.body = ArrayBuffer.from(block())
            bs.end = prevTok
            bs
          case "[" | "(" =>
            simpleStatement()
          case ";" =>
            inDirectives = false
            next()
            new AstEmptyStatement
          case _ =>
            unexpected()
        }

      case Token.Keyword =>
        token.value match {
          case "break" =>
            next()
            breakCont(isBreak = true)

          case "continue" =>
            next()
            breakCont(isBreak = false)

          case "debugger" =>
            next()
            semicolon()
            new AstDebugger

          case "do" =>
            next()
            val doBody = inLoop(statement())
            expectToken(Token.Keyword, "while")
            val doCondition = parenthesised()
            semicolon(optional = true)
            val d = new AstDo
            d.body = doBody
            d.condition = doCondition
            d

          case "while" =>
            next()
            val whileCond = parenthesised()
            val w         = new AstWhile
            w.condition = whileCond
            w.body = inLoop(statement(isForBody = true))
            w

          case "for" =>
            next()
            forStatement()

          case "class" =>
            next()
            if (isForBody) {
              croak("classes are not allowed as the body of a loop")
            }
            if (isIfBody) {
              croak("classes are not allowed as the body of an if")
            }
            classDef(isDefClass = true, isExportDefault = isExportDefault)

          case "function" =>
            next()
            if (isForBody) {
              croak("functions are not allowed as the body of a loop")
            }
            functionDef(isDefun = true, isGenerator = false, isAsync = false, isExportDefault = isExportDefault)

          case "if" =>
            next()
            ifStatement()

          case "return" =>
            if (inFunction == 0 && !options.bareReturns) {
              croak("'return' outside of function")
            }
            next()
            var retValue: AstNode | Null = null
            if (is(Token.Punc, ";")) {
              next()
            } else if (!canInsertSemicolon()) {
              retValue = expression(commas = true)
              semicolon()
            }
            val ret = new AstReturn
            ret.value = retValue
            ret

          case "switch" =>
            next()
            val switchExpr = parenthesised()
            val sw         = new AstSwitch
            sw.expression = switchExpr
            sw.body = inLoop(switchBody())
            sw

          case "throw" =>
            next()
            if (hasNewlineBefore(token)) {
              croak("Illegal newline after 'throw'")
            }
            val throwVal = expression(commas = true)
            semicolon()
            val th = new AstThrow
            th.value = throwVal
            th

          case "try" =>
            next()
            tryStatement()

          case "var" =>
            next()
            val varNode = varDef()
            semicolon()
            varNode

          case "let" =>
            next()
            val letNode = letDef()
            semicolon()
            letNode

          case "const" =>
            next()
            val constNode = constDef()
            semicolon()
            constNode

          case "with" =>
            if (input.hasDirective("use strict")) {
              croak("Strict mode may not include a with statement")
            }
            next()
            val withExpr = parenthesised()
            val w        = new AstWith
            w.expression = withExpr
            w.body = statement()
            w

          case "export" =>
            if (!isToken(peek(), Token.Punc, "(")) {
              next()
              val node = exportStatement()
              if (is(Token.Punc, ";")) semicolon()
              node
            } else {
              // export(...) -- treat as expression statement
              simpleStatement()
            }

          case _ =>
            unexpected()
        }

      case _ =>
        unexpected()
    }
  }

  // =========================================================================
  // Labeled statements
  // =========================================================================

  private def labeledStatement(): AstNode = {
    val label = asSymbol(classOf[AstLabel]).asInstanceOf[AstLabel]
    if (label.name == "await" && isInAsync()) {
      tokenError(prevTok, "await cannot be used as label inside async function")
    }
    if (labels.exists(_.name == label.name)) {
      croak("Label " + label.name + " defined twice")
    }
    expect(":")
    labels.addOne(label)
    val stat = statement()
    labels.remove(labels.size - 1)
    if (!stat.isInstanceOf[AstIterationStatement]) {
      // check for `continue` that refers to this label
      label.references.foreach { ref =>
        ref match {
          case c: AstContinue =>
            val refLabel = c.label
            if (refLabel != null) {
              croak("Continue label `" + label.name + "` refers to non-IterationStatement.", refLabel.nn.start.line, refLabel.nn.start.col)
            }
          case _ =>
        }
      }
    }
    val ls = new AstLabeledStatement
    ls.body = stat
    ls.label = label
    ls
  }

  // =========================================================================
  // Simple statement
  // =========================================================================

  private def simpleStatement(): AstSimpleStatement = {
    val tmp = expression(commas = true)
    semicolon()
    val ss = new AstSimpleStatement
    ss.body = tmp
    ss
  }

  // =========================================================================
  // Break / Continue
  // =========================================================================

  private def breakCont(isBreak: Boolean): AstNode = {
    var label: AstLabelRef | Null = null
    if (!canInsertSemicolon()) {
      val sym = asSymbolOpt(classOf[AstLabelRef])
      if (sym != null) label = sym.nn.asInstanceOf[AstLabelRef]
    }
    if (label != null) {
      val ldef = labels.find(_.name == label.nn.name)
      if (ldef.isEmpty) {
        croak("Undefined label " + label.nn.name)
      }
      label.nn.thedef = ldef.get
    } else if (inLoop == 0) {
      val typeName = if (isBreak) "Break" else "Continue"
      croak(typeName + " not inside a loop or switch")
    }
    semicolon()
    if (isBreak) {
      val b = new AstBreak
      b.label = label
      if (label != null) {
        labels.find(_.name == label.nn.name).foreach(_.references.addOne(b))
      }
      b
    } else {
      val c = new AstContinue
      c.label = label
      if (label != null) {
        labels.find(_.name == label.nn.name).foreach(_.references.addOne(c))
      }
      c
    }
  }

  // =========================================================================
  // For statement
  // =========================================================================

  private def forStatement(): AstNode = {
    val forAwaitError = "`for await` invalid in this context"
    val awaitTok      = token
    var hasAwait      = false
    if (awaitTok.tokenType == Token.Name && awaitTok.value == "await") {
      if (!canAwait()) {
        tokenError(awaitTok, forAwaitError)
      }
      next()
      hasAwait = true
    }
    expect("(")
    var init: AstNode | Null = null
    if (!is(Token.Punc, ";")) {
      init =
        if (is(Token.Keyword, "var")) { next(); varDef(noIn = true) }
        else if (is(Token.Keyword, "let")) { next(); letDef(noIn = true) }
        else if (is(Token.Keyword, "const")) { next(); constDef(noIn = true) }
        else if (
          is(Token.Name, "using") && isToken(peek(), Token.Name) &&
          (peek().value != "of" || input.peekNextTokenStartOrNewline().char == "=")
        ) {
          next(); usingDef(noIn = true)
        } else if (is(Token.Name, "await") && canAwait() && isToken(peek(), Token.Name, "using")) {
          next(); awaitUsingDef(noIn = true)
        } else expression(commas = true, noIn = true)
      val isIn = is(Token.Operator, "in")
      val isOf = is(Token.Name, "of")
      if (hasAwait && !isOf) {
        tokenError(awaitTok, forAwaitError)
      }
      if (isIn || isOf) {
        init.nn match {
          case dl: AstDefinitionsLike =>
            if (dl.definitions.size > 1) {
              tokenError(dl.asInstanceOf[AstNode].start, "Only one variable declaration allowed in for..in loop")
            }
            if (isIn && dl.isInstanceOf[AstUsing]) {
              tokenError(dl.asInstanceOf[AstNode].start, "Invalid using declaration in for..in loop")
            }
          case other =>
            if (!isAssignable(other)) {
              val destructured = toDestructuring(other)
              if (!destructured.isInstanceOf[AstDestructuring]) {
                tokenError(other.start, "Invalid left-hand side in for..in loop")
              }
              init = destructured
            }
        }
        next()
        if (isIn) {
          forIn(init.nn)
        } else {
          forOf(init.nn, hasAwait)
        }
      } else {
        if (hasAwait) {
          tokenError(awaitTok, forAwaitError)
        }
        regularFor(init)
      }
    } else {
      if (hasAwait) {
        tokenError(awaitTok, forAwaitError)
      }
      regularFor(null)
    }
  }

  private def regularFor(init: AstNode | Null): AstFor = {
    expect(";")
    val test: AstNode | Null = if (is(Token.Punc, ";")) null else expression(commas = true)
    expect(";")
    val step: AstNode | Null = if (is(Token.Punc, ")")) null else expression(commas = true)
    expect(")")
    val f = new AstFor
    f.init = init
    f.condition = test
    f.step = step
    f.body = inLoop(statement(isForBody = true))
    f
  }

  private def forOf(init: AstNode, isAwait: Boolean): AstForOf = {
    val obj = expression(commas = true)
    expect(")")
    val f = new AstForOf
    f.isAwait = isAwait
    f.init = init
    f.obj = obj
    f.body = inLoop(statement(isForBody = true))
    f
  }

  private def forIn(init: AstNode): AstForIn = {
    val obj = expression(commas = true)
    expect(")")
    val f = new AstForIn
    f.init = init
    f.obj = obj
    f.body = inLoop(statement(isForBody = true))
    f
  }

  // =========================================================================
  // Arrow function
  // =========================================================================

  private def arrowFunction(start: AstToken, argnames: ArrayBuffer[AstNode], isAsync: Boolean): AstArrow = {
    if (hasNewlineBefore(token)) {
      croak("Unexpected newline before arrow (=>)")
    }
    expectToken(Token.Arrow, "=>")

    val body = functionBody(block = is(Token.Punc, "{"), generator = false, isAsync = isAsync)

    val arrow = new AstArrow
    arrow.start = start
    arrow.end = body.last.end
    arrow.isAsync = isAsync
    arrow.argnames = argnames
    arrow.body = ArrayBuffer.from(body)
    arrow
  }

  // =========================================================================
  // Function definition
  // =========================================================================

  private def functionDef(
    isDefun:         Boolean,
    isGenerator:     Boolean,
    isAsync:         Boolean,
    isExportDefault: Boolean = false
  ): AstNode = {
    var gen = isGenerator
    if (is(Token.Operator, "*")) {
      gen = true
      next()
    }

    var name: AstNode | Null = null
    if (is(Token.Name)) {
      name = asSymbol(if (isDefun) classOf[AstSymbolDefun] else classOf[AstSymbolLambda])
    }

    var useDefun = isDefun
    if (isDefun && name == null) {
      if (isExportDefault) {
        useDefun = false // becomes AstFunction
      } else {
        unexpected()
      }
    }

    if (name != null && !useDefun && !name.nn.isInstanceOf[AstSymbolDeclaration]) {
      unexpected(prevTok)
    }

    val args      = ArrayBuffer.empty[AstNode]
    val argsStart = token
    val body      = functionBody(block = true, generator = gen, isAsync = isAsync, name = name, args = args)

    val func: AstLambda = if (useDefun) new AstDefun else new AstFunction
    func.start = argsStart
    func.end = body.lastOption.map(_.end).getOrElse(prevTok)
    func.isGenerator = gen
    func.isAsync = isAsync
    func.name = name
    func.argnames = args
    func.body = ArrayBuffer.from(body)
    func.asInstanceOf[AstNode]
  }

  // =========================================================================
  // UsedParametersTracker (inner helper class)
  // =========================================================================

  private class UsedParametersTracker(
    val isParameter:  Boolean,
    var strictMode:   Boolean,
    val duplicatesOk: Boolean = false
  ) {
    val parameters:        scala.collection.mutable.Set[String] = scala.collection.mutable.Set.empty
    var duplicate:         AstToken | Null                      = null
    var defaultAssignment: AstToken | Null                      = null
    var spread:            AstToken | Null                      = null

    def addParameter(tok: AstToken): Unit =
      if (parameters.contains(tok.value)) {
        if (duplicate == null) {
          duplicate = tok
        }
        checkStrict()
      } else {
        parameters.add(tok.value)
        if (isParameter) {
          tok.value match {
            case "arguments" | "eval" | "yield" =>
              if (strictMode) {
                tokenError(tok, "Unexpected " + tok.value + " identifier as parameter inside strict mode")
              }
            case _ =>
              if (Token.RESERVED_WORDS.contains(tok.value)) {
                unexpected()
              }
          }
        }
      }

    def markDefaultAssignment(tok: AstToken): Unit =
      if (defaultAssignment == null) {
        defaultAssignment = tok
      }

    def markSpread(tok: AstToken): Unit =
      if (spread == null) {
        spread = tok
      }

    def markStrictMode(): Unit =
      strictMode = true

    def isStrict: Boolean =
      defaultAssignment != null || spread != null || strictMode

    def checkStrict(): Unit =
      if (isStrict && duplicate != null && !duplicatesOk) {
        tokenError(duplicate.nn, "Parameter " + duplicate.nn.value + " was used already")
      }
  }

  // =========================================================================
  // Parameters
  // =========================================================================

  private def parameters(params: ArrayBuffer[AstNode]): Unit = {
    val usedParams = new UsedParametersTracker(
      isParameter = true,
      strictMode = input.hasDirective("use strict")
    )
    expect("(")
    while (!is(Token.Punc, ")")) {
      val param = parameter(usedParams)
      params.addOne(param)
      if (!is(Token.Punc, ")")) {
        expect(",")
      }
      if (param.isInstanceOf[AstExpansion]) {
        // rest parameter must be last
        // break out of while — Scala doesn't have break in while
        // but the next iteration check `is("punc", ")")` handles it
      }
    }
    next()
  }

  private def parameter(
    usedParams: UsedParametersTracker = null.asInstanceOf[UsedParametersTracker], // @nowarn — null sentinel
    symbolType: Class[? <: AstNode] = null.asInstanceOf[Class[? <: AstNode]] // @nowarn — null sentinel
  ): AstNode = {
    val up =
      if (usedParams != null) usedParams
      else new UsedParametersTracker(isParameter = true, strictMode = input.hasDirective("use strict"))
    var expand: AstToken | Null = null
    if (is(Token.Expand, "...")) {
      expand = token
      up.markSpread(token)
      next()
    }
    var param = bindingElement(up, symbolType)

    if (is(Token.Operator, "=") && expand == null) {
      up.markDefaultAssignment(token)
      next()
      val da = new AstDefaultAssign
      da.start = param.start
      da.left = param
      da.operator = "="
      da.right = expression(commas = false)
      da.end = token
      param = da
    }

    if (expand != null) {
      if (!is(Token.Punc, ")")) {
        unexpected()
      }
      val exp = new AstExpansion
      exp.start = expand.nn
      exp.expression = param
      exp.end = expand.nn
      param = exp
    }
    up.checkStrict()
    param
  }

  @scala.annotation.nowarn("msg=unused private member")
  private def bindingElement(
    usedParams: UsedParametersTracker = null.asInstanceOf[UsedParametersTracker], // @nowarn — null sentinel
    symbolType: Class[? <: AstNode] = null.asInstanceOf[Class[? <: AstNode]] // @nowarn — null sentinel
  ): AstNode = {
    val up =
      if (usedParams != null) usedParams
      else {
        val strict = input.hasDirective("use strict")
        val dupOk  = symbolType != null && symbolType == classOf[AstSymbolVar]
        new UsedParametersTracker(isParameter = false, strictMode = strict, duplicatesOk = dupOk)
      }
    val symType: Class[? <: AstNode] =
      if (symbolType != null) symbolType else classOf[AstSymbolFunarg]
    val firstToken = token

    if (is(Token.Punc, "[")) {
      next()
      val elements = ArrayBuffer.empty[AstNode]
      var first    = true
      var isExpand = false
      var expandToken: AstToken | Null = null
      // use boundary for the continue semantics
      boundary[Unit] {
        while (!is(Token.Punc, "]")) {
          if (first) { first = false }
          else { expect(",") }

          if (is(Token.Expand, "...")) {
            isExpand = true
            expandToken = token
            up.markSpread(token)
            next()
          }
          if (is(Token.Punc)) {
            token.value match {
              case "," =>
                val hole = new AstHole
                hole.start = token
                hole.end = token
                elements.addOne(hole)
              // This mimics `continue` in the JS — the while loop
              // will hit expect(",") on next iteration which is wrong.
              // Actually in JS it falls through to while check. We need
              // to skip the rest of this iteration.
              // The JS code has `continue` here, so we skip to next iteration.
              case "]"       => // trailing comma after last element — break out
              case "[" | "{" =>
                elements.addOne(bindingElement(up, symType))
              case _ =>
                unexpected()
            }
            // For "," case, we want to continue the while loop (skip default assignment check)
            if (token.value == ",") {
              // continue — don't check default assignment
            } else {
              // Check default assignment
              if (is(Token.Operator, "=") && !isExpand) {
                up.markDefaultAssignment(token)
                next()
                val last = elements(elements.size - 1)
                val da   = new AstDefaultAssign
                da.start = last.start
                da.left = last
                da.operator = "="
                da.right = expression(commas = false)
                da.end = token
                elements(elements.size - 1) = da
              }
              if (isExpand) {
                if (!is(Token.Punc, "]")) {
                  croak("Rest element must be last element")
                }
                val last = elements(elements.size - 1)
                val exp  = new AstExpansion
                exp.start = expandToken.nn
                exp.expression = last
                exp.end = expandToken.nn
                elements(elements.size - 1) = exp
              }
            }
          } else if (is(Token.Name)) {
            up.addParameter(token)
            elements.addOne(asSymbol(symType))
            // Check default assignment
            if (is(Token.Operator, "=") && !isExpand) {
              up.markDefaultAssignment(token)
              next()
              val last = elements(elements.size - 1)
              val da   = new AstDefaultAssign
              da.start = last.start
              da.left = last
              da.operator = "="
              da.right = expression(commas = false)
              da.end = token
              elements(elements.size - 1) = da
            }
            if (isExpand) {
              if (!is(Token.Punc, "]")) {
                croak("Rest element must be last element")
              }
              val last = elements(elements.size - 1)
              val exp  = new AstExpansion
              exp.start = expandToken.nn
              exp.expression = last
              exp.end = expandToken.nn
              elements(elements.size - 1) = exp
            }
          } else {
            croak("Invalid function parameter")
          }
        }
      }
      expect("]")
      up.checkStrict()
      val destr = new AstDestructuring
      destr.start = firstToken
      destr.names = elements
      destr.isArray = true
      destr.end = prevTok
      destr
    } else if (is(Token.Punc, "{")) {
      next()
      val elements = ArrayBuffer.empty[AstNode]
      var first    = true
      var isExpand = false
      var expandToken: AstToken | Null = null
      while (!is(Token.Punc, "}")) {
        if (first) { first = false }
        else { expect(",") }
        if (is(Token.Expand, "...")) {
          isExpand = true
          expandToken = token
          up.markSpread(token)
          next()
        }
        if (
          is(Token.Name) && (isToken(peek(), Token.Punc) || isToken(peek(), Token.Operator)) &&
          Set(",", "}", "=").contains(peek().value)
        ) {
          up.addParameter(token)
          val startTok = prevTok
          val value    = asSymbol(symType)
          if (isExpand) {
            val exp = new AstExpansion
            exp.start = expandToken.nn
            exp.expression = value
            exp.end = value.end
            elements.addOne(exp)
          } else {
            val kv = new AstObjectKeyVal
            kv.start = startTok
            kv.key = value.asInstanceOf[AstSymbol].name
            kv.value = value
            kv.end = value.end
            elements.addOne(kv)
          }
        } else if (is(Token.Punc, "}")) {
          // allow trailing hole — continue to while check
        } else {
          val propertyToken = token
          val property      = asPropertyName()
          if (property == null) {
            unexpected(prevTok)
          } else if (prevTok.tokenType == Token.Name && !is(Token.Punc, ":")) {
            val propStr = property.asInstanceOf[String]
            val sym     = createSymbol(symType)
            sym.asInstanceOf[AstSymbol].name = propStr
            sym.start = prevTok
            sym.end = prevTok
            val kv = new AstObjectKeyVal
            kv.start = prevTok
            kv.key = propStr
            kv.value = sym
            kv.end = prevTok
            elements.addOne(kv)
          } else {
            expect(":")
            val kv = new AstObjectKeyVal
            kv.start = propertyToken
            kv.quote = propertyToken.quote
            kv.key = property match {
              case s: String  => s
              case n: AstNode => n
            }
            kv.value = bindingElement(up, symType)
            kv.end = prevTok
            elements.addOne(kv)
          }
          if (isExpand) {
            if (!is(Token.Punc, "}")) {
              croak("Rest element must be last element")
            }
          } else if (is(Token.Operator, "=")) {
            up.markDefaultAssignment(token)
            next()
            val last      = elements(elements.size - 1)
            val lastValue = last match {
              case kv: AstObjectKeyVal => kv.value
              case other => other
            }
            val da = new AstDefaultAssign
            da.start = lastValue.nn.start
            da.left = lastValue.nn
            da.operator = "="
            da.right = expression(commas = false)
            da.end = token
            last match {
              case kv: AstObjectKeyVal => kv.value = da
              case _ => elements(elements.size - 1) = da
            }
          }
        }
      }
      expect("}")
      up.checkStrict()
      val destr = new AstDestructuring
      destr.start = firstToken
      destr.names = elements
      destr.isArray = false
      destr.end = prevTok
      destr
    } else if (is(Token.Name)) {
      up.addParameter(token)
      asSymbol(symType)
    } else {
      croak("Invalid function parameter")
    }
  }

  // =========================================================================
  // params_or_seq_ — parenthesized expression or arrow params
  // =========================================================================

  private def paramsOrSeq(allowArrows: Boolean, maybeSequence: Boolean): ArrayBuffer[AstNode] = {
    var spreadToken:     AstToken | Null = null
    var invalidSequence: AstToken | Null = null
    var trailingComma:   AstToken | Null = null
    val a = ArrayBuffer.empty[AstNode]
    expect("(")
    while (!is(Token.Punc, ")")) {
      if (spreadToken != null) unexpected(spreadToken.nn)
      if (is(Token.Expand, "...")) {
        spreadToken = token
        if (maybeSequence) invalidSequence = token
        next()
        val exp = new AstExpansion
        exp.start = prevTok
        exp.expression = expression(commas = false)
        exp.end = token
        a.addOne(exp)
      } else {
        a.addOne(expression(commas = false))
      }
      if (!is(Token.Punc, ")")) {
        expect(",")
        if (is(Token.Punc, ")")) {
          trailingComma = prevTok
          if (maybeSequence) invalidSequence = trailingComma
        }
      }
    }
    expect(")")
    if (allowArrows && is(Token.Arrow, "=>")) {
      if (spreadToken != null && trailingComma != null) unexpected(trailingComma.nn)
    } else if (invalidSequence != null) {
      unexpected(invalidSequence.nn)
    }
    a
  }

  // =========================================================================
  // Function body
  // =========================================================================

  private def functionBody(
    block:     Boolean,
    generator: Boolean,
    isAsync:   Boolean,
    name:      AstNode | Null = null,
    args:      ArrayBuffer[AstNode] | Null = null
  ): ArrayBuffer[AstNode] = {
    val savedLoop      = inLoop
    val savedLabels    = labels
    val savedGenerator = inGenerator
    val savedAsync     = inAsync
    inFunction += 1
    if (generator) inGenerator = inFunction
    if (isAsync) inAsync = inFunction
    if (args != null) parameters(args.nn)
    if (block) inDirectives = true
    inLoop = 0
    labels = ArrayBuffer.empty
    val a = if (block) {
      input.pushDirectivesStack()
      val body = this.block()
      if (name != null) verifySymbol(name.nn)
      if (args != null) args.nn.foreach(verifySymbol)
      input.popDirectivesStack()
      body
    } else {
      val ret = new AstReturn
      ret.start = token
      ret.value = expression(commas = false)
      ret.end = token
      ArrayBuffer[AstNode](ret)
    }
    inFunction -= 1
    inLoop = savedLoop
    labels = savedLabels
    inGenerator = savedGenerator
    inAsync = savedAsync
    a
  }

  // =========================================================================
  // Await / Yield expressions
  // =========================================================================

  private def awaitExpression(): AstAwait = {
    if (!canAwait()) {
      croak("Unexpected await expression outside async function", prevTok.line, prevTok.col, prevTok.pos)
    }
    val aw = new AstAwait
    aw.start = prevTok
    aw.end = token
    aw.expression = maybeUnary(allowCalls = true)
    aw
  }

  private def yieldExpression(): AstYield = {
    val start         = token
    var star          = false
    var hasExpression = true

    if (
      canInsertSemicolon() ||
      (is(Token.Punc) && Token.PUNC_AFTER_EXPRESSION.contains(token.value.headOption.getOrElse(' '))) ||
      is(Token.TemplateCont)
    ) {
      hasExpression = false
    } else if (is(Token.Operator, "*")) {
      star = true
      next()
    }

    val y = new AstYield
    y.start = start
    y.isStar = star
    y.expression = if (hasExpression) expression() else null
    y.end = prevTok
    y
  }

  // =========================================================================
  // If statement
  // =========================================================================

  private def ifStatement(): AstIf = {
    val cond = parenthesised()
    val body = statement(isIfBody = true)
    var belse: AstNode | Null = null
    if (is(Token.Keyword, "else")) {
      next()
      belse = statement(isIfBody = true)
    }
    val i = new AstIf
    i.condition = cond
    i.body = body
    i.alternative = belse
    i
  }

  // =========================================================================
  // Block
  // =========================================================================

  private def block(): ArrayBuffer[AstNode] = {
    expect("{")
    val a = ArrayBuffer.empty[AstNode]
    while (!is(Token.Punc, "}")) {
      if (is(Token.Eof)) unexpected()
      a.addOne(statement())
    }
    next()
    a
  }

  // =========================================================================
  // Switch body
  // =========================================================================

  private def switchBody(): ArrayBuffer[AstNode] = {
    expect("{")
    val a = ArrayBuffer.empty[AstNode]
    var cur:    ArrayBuffer[AstNode] | Null = null
    var branch: AstNode | Null              = null
    while (!is(Token.Punc, "}")) {
      if (is(Token.Eof)) unexpected()
      if (is(Token.Keyword, "case")) {
        if (branch != null) branch.nn.end = prevTok
        cur = ArrayBuffer.empty
        val tmp = token
        next()
        val c = new AstCase
        c.start = tmp
        c.expression = expression(commas = true)
        c.body = cur.nn
        branch = c
        a.addOne(c)
        expect(":")
      } else if (is(Token.Keyword, "default")) {
        if (branch != null) branch.nn.end = prevTok
        cur = ArrayBuffer.empty
        val tmp = token
        next()
        expect(":")
        val d = new AstDefault
        d.start = tmp
        d.body = cur.nn
        branch = d
        a.addOne(d)
      } else {
        if (cur == null) unexpected()
        cur.nn.addOne(statement())
      }
    }
    if (branch != null) branch.nn.end = prevTok
    next()
    a
  }

  // =========================================================================
  // Try statement
  // =========================================================================

  private def tryStatement(): AstTry = {
    val tryBlock = new AstTryBlock
    tryBlock.start = token
    tryBlock.body = ArrayBuffer.from(block())
    tryBlock.end = prevTok

    var bcatch:   AstCatch | Null   = null
    var bfinally: AstFinally | Null = null

    if (is(Token.Keyword, "catch")) {
      val catchStart = token
      next()
      var argname: AstNode | Null = null
      if (!is(Token.Punc, "{")) {
        expect("(")
        argname = parameter(symbolType = classOf[AstSymbolCatch])
        expect(")")
      }
      val c = new AstCatch
      c.start = catchStart
      c.argname = argname
      c.body = ArrayBuffer.from(block())
      c.end = prevTok
      bcatch = c
    }
    if (is(Token.Keyword, "finally")) {
      val finStart = token
      next()
      val f = new AstFinally
      f.start = finStart
      f.body = ArrayBuffer.from(block())
      f.end = prevTok
      bfinally = f
    }
    if (bcatch == null && bfinally == null) {
      croak("Missing catch/finally blocks")
    }
    val t = new AstTry
    t.body = tryBlock
    t.bcatch = bcatch
    t.bfinally = bfinally
    t
  }

  // =========================================================================
  // Variable definitions
  // =========================================================================

  private def vardefs(noIn: Boolean, kind: String): ArrayBuffer[AstNode] = {
    val varDefs   = ArrayBuffer.empty[AstNode]
    var continue_ = true
    while (continue_) {
      val symType: Class[? <: AstNode] = kind match {
        case "var"                   => classOf[AstSymbolVar]
        case "const"                 => classOf[AstSymbolConst]
        case "let"                   => classOf[AstSymbolLet]
        case "using" | "await using" => classOf[AstSymbolUsing]
        case _                       => classOf[AstSymbolVar]
      }
      val isUsingKind = kind == "using" || kind == "await using"

      if (is(Token.Punc, "{") || is(Token.Punc, "[")) {
        val defNode: AstNode = if (isUsingKind) new AstUsingDef else new AstVarDef
        defNode.start = token
        val nameNode = bindingElement(symbolType = symType)
        val valueNode: AstNode | Null = if (is(Token.Operator, "=")) {
          expectToken(Token.Operator, "=")
          expression(commas = false, noIn = noIn)
        } else null
        defNode match {
          case vd: AstVarDef =>
            vd.name = nameNode
            vd.value = valueNode
          case ud: AstUsingDef =>
            ud.name = nameNode
            ud.value = valueNode
          case _ =>
        }
        defNode.end = prevTok
        varDefs.addOne(defNode)
      } else {
        val defNode: AstNode = if (isUsingKind) new AstUsingDef else new AstVarDef
        defNode.start = token
        val nameNode = asSymbol(symType)
        val valueNode: AstNode | Null = if (is(Token.Operator, "=")) {
          next()
          expression(commas = false, noIn = noIn)
        } else if (!noIn && (kind == "const" || kind == "using" || kind == "await using")) {
          croak("Missing initializer in " + kind + " declaration")
        } else null
        defNode match {
          case vd: AstVarDef =>
            vd.name = nameNode
            vd.value = valueNode
          case ud: AstUsingDef =>
            ud.name = nameNode
            ud.value = valueNode
          case _ =>
        }
        defNode.end = prevTok
        if (nameNode.asInstanceOf[AstSymbol].name == "import") {
          croak("Unexpected token: import")
        }
        varDefs.addOne(defNode)
      }
      if (!is(Token.Punc, ",")) {
        continue_ = false
      } else {
        next()
      }
    }
    varDefs
  }

  private def varDef(noIn: Boolean = false): AstVar = {
    val v = new AstVar
    v.start = prevTok
    v.definitions = vardefs(noIn, "var")
    v.end = prevTok
    v
  }

  private def letDef(noIn: Boolean = false): AstLet = {
    val l = new AstLet
    l.start = prevTok
    l.definitions = vardefs(noIn, "let")
    l.end = prevTok
    l
  }

  private def constDef(noIn: Boolean = false): AstConst = {
    val c = new AstConst
    c.start = prevTok
    c.definitions = vardefs(noIn, "const")
    c.end = prevTok
    c
  }

  private def usingDef(noIn: Boolean = false): AstUsing = {
    val u = new AstUsing
    u.start = prevTok
    u.isAwait = false
    u.definitions = vardefs(noIn, "using")
    u.end = prevTok
    u
  }

  private def awaitUsingDef(noIn: Boolean = false): AstUsing = {
    // When called, only the `await` token has been consumed.
    val u = new AstUsing
    u.start = prevTok
    u.isAwait = true
    next() // consume "using"
    u.definitions = vardefs(noIn, "await using")
    u.end = prevTok
    u
  }

  // =========================================================================
  // New expression
  // =========================================================================

  private def newExpression(allowCalls: Boolean): AstNode = {
    val start = token
    expectToken(Token.Operator, "new")
    if (is(Token.Punc, ".")) {
      next()
      expectToken(Token.Name, "target")
      val nt = new AstNewTarget
      nt.start = start
      nt.end = prevTok
      subscripts(nt, allowCalls)
    } else {
      val newexp = exprAtom(allowCalls = false)
      val args: ArrayBuffer[AstNode] = if (is(Token.Punc, "(")) {
        next()
        exprList(")", allowTrailingComma = true)
      } else {
        ArrayBuffer.empty
      }
      val call = new AstNew
      call.start = start
      call.expression = newexp
      call.args = args
      call.end = prevTok
      annotate(call)
      subscripts(call, allowCalls)
    }
  }

  // =========================================================================
  // as_atom_node — atomic token to AST node
  // =========================================================================

  private def asAtomNode(): AstNode = {
    val tok = token
    val ret: AstNode = tok.tokenType match {
      case Token.Name =>
        makeSymbol(classOf[AstSymbolRef])

      case Token.Num =>
        if (tok.value == "Infinity" || tok.value == "1/0") {
          val inf = new AstInfinity
          inf.start = tok
          inf.end = tok
          inf
        } else {
          val n = new AstNumber
          n.start = tok
          n.end = tok
          n.value =
            try tok.value.toDouble
            catch { case _: NumberFormatException => Double.NaN }
          n.raw = input.latestRaw
          n
        }

      case Token.BigInt =>
        val bi = new AstBigInt
        bi.start = tok
        bi.end = tok
        bi.value = tok.value
        bi.raw = input.latestRaw
        bi

      case Token.StringType =>
        val s = new AstString
        s.start = tok
        s.end = tok
        s.value = tok.value
        s.quote = tok.quote
        annotate(s)
        s

      case Token.Regexp =>
        val regexPattern    = "^/(.*)/([a-zA-Z]*)$".r
        val (source, flags) = tok.value match {
          case regexPattern(src, flg) => (src, flg)
          case _                      => (tok.value, "")
        }
        val r = new AstRegExp
        r.start = tok
        r.end = tok
        r.value = RegExpValue(source, flags)
        r

      case Token.Atom =>
        tok.value match {
          case "false" =>
            val f = new AstFalse
            f.start = tok
            f.end = tok
            f
          case "true" =>
            val t = new AstTrue
            t.start = tok
            t.end = tok
            t
          case "null" =>
            val n = new AstNull
            n.start = tok
            n.end = tok
            n
          case _ => unexpected()
        }

      case _ => unexpected()
    }
    next()
    ret
  }

  // =========================================================================
  // to_fun_args — convert expressions to function parameters
  // =========================================================================

  private def toFunArgs(ex: AstNode, defaultSeenAbove: AstNode | Null = null): AstNode = {
    def insertDefault(node: AstNode, defaultValue: AstNode | Null): AstNode =
      if (defaultValue != null) {
        val da = new AstDefaultAssign
        da.start = node.start
        da.left = node
        da.operator = "="
        da.right = defaultValue.nn
        da.end = defaultValue.nn.end
        da
      } else {
        node
      }

    ex match {
      case obj: AstObject =>
        val destr = new AstDestructuring
        destr.start = obj.start
        destr.end = obj.end
        destr.isArray = false
        destr.names = obj.properties.map(prop => toFunArgs(prop))
        insertDefault(destr, defaultSeenAbove)

      case kv: AstObjectKeyVal =>
        kv.value = toFunArgs(kv.value.nn, null)
        insertDefault(kv, defaultSeenAbove)

      case _: AstHole =>
        ex

      case d: AstDestructuring =>
        d.names = d.names.map(name => toFunArgs(name))
        insertDefault(d, defaultSeenAbove)

      case ref: AstSymbolRef =>
        val funarg = new AstSymbolFunarg
        funarg.name = ref.name
        funarg.start = ref.start
        funarg.end = ref.end
        insertDefault(funarg, defaultSeenAbove)

      case exp: AstExpansion =>
        exp.expression = toFunArgs(exp.expression.nn, null)
        insertDefault(exp, defaultSeenAbove)

      case arr: AstArray =>
        val destr = new AstDestructuring
        destr.start = arr.start
        destr.end = arr.end
        destr.isArray = true
        destr.names = arr.elements.map(elm => toFunArgs(elm))
        insertDefault(destr, defaultSeenAbove)

      case assign: AstAssign =>
        insertDefault(toFunArgs(assign.left.nn, assign.right), defaultSeenAbove)

      case da: AstDefaultAssign =>
        da.left = toFunArgs(da.left.nn, null)
        da

      case _ =>
        croak("Invalid function parameter", ex.start.line, ex.start.col)
    }
  }

  // =========================================================================
  // expr_atom — atomic expressions
  // =========================================================================

  @scala.annotation.nowarn("msg=unused private member")
  private def exprAtom(allowCalls: Boolean = true, allowArrows: Boolean = false): AstNode = {
    if (is(Token.Operator, "new")) {
      return newExpression(allowCalls)
    }
    if (is(Token.Name, "import") && isToken(peek(), Token.Punc, ".")) {
      return importMeta(allowCalls)
    }
    val start = token
    var asyncNode: AstNode | Null = null
    if (is(Token.Name, "async")) {
      val p = peek()
      if (p.value != "[" && p.tokenType != Token.Arrow) {
        asyncNode = asAtomNode()
      }
    }
    if (is(Token.Punc)) {
      token.value match {
        case "(" =>
          if (asyncNode != null && !allowCalls) {
            // break — fall through to later code
          } else {
            val exprs = paramsOrSeq(allowArrows, asyncNode == null)
            if (allowArrows && is(Token.Arrow, "=>")) {
              return arrowFunction(start, exprs.map(e => toFunArgs(e)), asyncNode != null)
            }
            val ex: AstNode = if (asyncNode != null) {
              val call = new AstCall
              call.expression = asyncNode.nn
              call.args = exprs
              call
            } else {
              toExprOrSequence(start, exprs)
            }
            if (ex.start != null) {
              val outerCommentsBefore = start.commentsBefore.size
              outerCommentsBeforeCounts.put(start, Integer.valueOf(outerCommentsBefore))
              // Comment manipulation (simplified — the full comment merging is complex)
            }
            ex.start = start
            val endTok = prevTok
            ex.end = endTok
            ex match {
              case call: AstCall => annotate(call)
              case _ =>
            }
            return subscripts(ex, allowCalls)
          }

        case "[" =>
          if (asyncNode == null || !is(Token.Punc, "[")) {
            return subscripts(arrayLiteral(), allowCalls)
          }

        case "{" =>
          if (asyncNode == null || !is(Token.Punc, "{")) {
            return subscripts(objectOrDestructuring(), allowCalls)
          }

        case _ =>
      }
      if (asyncNode == null) unexpected()
    }

    if (allowArrows && is(Token.Name) && isToken(peek(), Token.Arrow)) {
      val param = new AstSymbolFunarg
      param.name = token.value
      param.start = start
      param.end = start
      next()
      return arrowFunction(start, ArrayBuffer(param), asyncNode != null)
    }
    if (is(Token.Keyword, "function")) {
      next()
      val func = functionDef(isDefun = false, isGenerator = false, isAsync = asyncNode != null)
      func.start = start
      func.end = prevTok
      return subscripts(func, allowCalls)
    }
    if (asyncNode != null) {
      return subscripts(asyncNode.nn, allowCalls)
    }
    if (is(Token.Keyword, "class")) {
      next()
      val cls = classDef(isDefClass = false)
      cls.start = start
      cls.end = prevTok
      return subscripts(cls, allowCalls)
    }
    if (is(Token.TemplateHead)) {
      return subscripts(templateString(), allowCalls)
    }
    if (Token.ATOMIC_START_TOKEN.contains(token.tokenType)) {
      return subscripts(asAtomNode(), allowCalls)
    }
    unexpected()
  }

  // =========================================================================
  // Template string
  // =========================================================================

  private def templateString(): AstTemplateString = {
    val segments = ArrayBuffer.empty[AstNode]
    val start    = token

    val seg1 = new AstTemplateSegment
    seg1.start = token
    seg1.raw = input.templateRaws.getOrElse(token, "")
    seg1.value = token.value
    seg1.end = token
    segments.addOne(seg1)

    while (!token.templateEnd) {
      next()
      handleRegexp()
      segments.addOne(expression(commas = true))

      val seg = new AstTemplateSegment
      seg.start = token
      seg.raw = input.templateRaws.getOrElse(token, "")
      seg.value = token.value
      seg.end = token
      segments.addOne(seg)
    }
    next()

    val ts = new AstTemplateString
    ts.start = start
    ts.segments = segments
    ts.end = token
    ts
  }

  // =========================================================================
  // expr_list
  // =========================================================================

  @scala.annotation.nowarn("msg=unused private member")
  private def exprList(
    closing:            String,
    allowTrailingComma: Boolean = false,
    allowEmpty:         Boolean = false
  ): ArrayBuffer[AstNode] = {
    var first = true
    val a     = ArrayBuffer.empty[AstNode]
    while (!is(Token.Punc, closing)) {
      if (first) first = false else expect(",")
      if (allowTrailingComma && is(Token.Punc, closing)) {
        // break out — while condition will handle it
      } else if (is(Token.Punc, ",") && allowEmpty) {
        val hole = new AstHole
        hole.start = token
        hole.end = token
        a.addOne(hole)
      } else if (is(Token.Expand, "...")) {
        next()
        val exp = new AstExpansion
        exp.start = prevTok
        exp.expression = expression()
        exp.end = token
        a.addOne(exp)
      } else {
        a.addOne(expression(commas = false))
      }
    }
    next()
    a
  }

  // =========================================================================
  // Array literal
  // =========================================================================

  private def arrayLiteral(): AstArray = {
    val start = token
    expect("[")
    val arr = new AstArray
    arr.start = start
    arr.elements = exprList("]", allowTrailingComma = !options.strict, allowEmpty = true)
    arr.end = prevTok
    arr
  }

  // =========================================================================
  // Create accessor
  // =========================================================================

  private def createAccessor(isGenerator: Boolean = false, isAsync: Boolean = false): AstAccessor = {
    val start = token
    val body  = functionBody(block = true, generator = isGenerator, isAsync = isAsync)
    val acc   = new AstAccessor
    acc.start = start
    acc.body = ArrayBuffer.from(body)
    acc.end = prevTok
    acc
  }

  // =========================================================================
  // Object literal
  // =========================================================================

  private def objectOrDestructuring(): AstObject = {
    val outerStart = token
    var first      = true
    val a          = ArrayBuffer.empty[AstNode]
    expect("{")
    while (!is(Token.Punc, "}")) {
      if (first) first = false else expect(",")
      if (!options.strict && is(Token.Punc, "}")) {
        // allow trailing comma — exit loop
      } else {
        val propStart = token
        if (propStart.tokenType == Token.Expand) {
          next()
          val exp = new AstExpansion
          exp.start = propStart
          exp.expression = expression(commas = false)
          exp.end = prevTok
          a.addOne(exp)
        } else {
          if (is(Token.PrivateName)) {
            croak("private fields are not allowed in an object")
          }
          val name = asPropertyName()
          var value: AstNode | Null = null

          // Check property and fetch value
          if (!is(Token.Punc, ":")) {
            val concise = objectOrClassProperty(name, propStart, isClass = false)
            if (concise != null) {
              a.addOne(concise.nn)
            } else {
              // Shorthand property
              val ref = new AstSymbolRef
              ref.start = prevTok
              ref.name = name match {
                case s: String  => s
                case _: AstNode => "" // should not happen for shorthand
              }
              ref.end = prevTok
              value = ref

              // Check for default value
              if (is(Token.Operator, "=")) {
                next()
                val assign = new AstAssign
                assign.start = propStart
                assign.left = value.nn
                assign.operator = "="
                assign.right = expression(commas = false)
                assign.logical = false
                assign.end = prevTok
                value = assign
              }

              // Create property
              val kv = new AstObjectKeyVal
              kv.start = propStart
              kv.quote = propStart.quote
              kv.key = name match {
                case s: String  => s
                case n: AstNode => n
              }
              kv.value = value.nn
              kv.end = prevTok
              a.addOne(annotate(kv))
            }
          } else if (name == null) {
            unexpected(prevTok)
          } else {
            next() // consume ":"
            value = expression(commas = false)

            // Check for default value
            if (is(Token.Operator, "=")) {
              next()
              val assign = new AstAssign
              assign.start = propStart
              assign.left = value.nn
              assign.operator = "="
              assign.right = expression(commas = false)
              assign.logical = false
              assign.end = prevTok
              value = assign
            }

            // Create property
            val kv = new AstObjectKeyVal
            kv.start = propStart
            kv.quote = propStart.quote
            kv.key = name match {
              case s: String  => s
              case n: AstNode => n
            }
            kv.value = value.nn
            kv.end = prevTok
            a.addOne(annotate(kv))
          }
        }
      }
    }
    next()
    val obj = new AstObject
    obj.start = outerStart
    obj.properties = a
    obj.end = prevTok
    obj
  }

  // =========================================================================
  // Class definition
  // =========================================================================

  private def classDef(isDefClass: Boolean, isExportDefault: Boolean = false): AstNode = {
    val properties = ArrayBuffer.empty[AstNode]

    input.pushDirectivesStack()
    input.addDirective("use strict")

    var className: AstNode | Null = null
    if (token.tokenType == Token.Name && token.value != "extends") {
      className = asSymbol(if (isDefClass) classOf[AstSymbolDefClass] else classOf[AstSymbolClass])
    }

    var useDefClass = isDefClass
    if (isDefClass && className == null) {
      if (isExportDefault) {
        useDefClass = false
      } else {
        unexpected()
      }
    }

    var superClass: AstNode | Null = null
    if (token.value == "extends") {
      next()
      superClass = expression(commas = true)
    }

    expect("{")
    val savedInClass = inClass
    inClass = true
    while (is(Token.Punc, ";")) next()
    while (!is(Token.Punc, "}")) {
      val methodStart = token
      val method      = objectOrClassProperty(asPropertyName(), methodStart, isClass = true)
      if (method == null) { unexpected() }
      properties.addOne(method.nn)
      while (is(Token.Punc, ";")) next()
    }
    inClass = savedInClass

    input.popDirectivesStack()
    next()

    val cls: AstClass = if (useDefClass) new AstDefClass else new AstClassExpression
    cls.name = className
    cls.superClass = superClass
    cls.properties = properties
    cls.end = prevTok
    cls
  }

  // =========================================================================
  // Object or class property
  // =========================================================================

  private def objectOrClassProperty(
    name0:   String | AstNode | Null,
    start:   AstToken,
    isClass: Boolean
  ): AstNode | Null = {
    var name: String | AstNode | Null = name0
    val isPrivate = prevTok.tokenType == Token.PrivateName

    def isNotMethodStart(): Boolean =
      !is(Token.Punc, "(") && !is(Token.Punc, ",") && !is(Token.Punc, "}") &&
        !is(Token.Punc, ";") && !is(Token.Operator, "=") && !isPrivate

    var isAsyncProp = false
    var isStatic    = false
    var isGenerator = false
    var accessorType: String | Null = null

    if (isClass && name != null && name.toString == "static" && isNotMethodStart()) {
      val staticBlock = classStaticBlock()
      if (staticBlock != null) {
        return staticBlock
      }
      isStatic = true
      name = asPropertyName()
    }
    if (name != null && name.toString == "async" && isNotMethodStart()) {
      isAsyncProp = true
      name = asPropertyName()
    }
    if (prevTok.tokenType == Token.Operator && prevTok.value == "*") {
      isGenerator = true
      name = asPropertyName()
    }
    if (name != null && (name.toString == "get" || name.toString == "set") && isNotMethodStart()) {
      accessorType = name.toString
      name = asPropertyName()
    }
    val isPrivateNow = !isPrivate && prevTok.tokenType == Token.PrivateName

    val propertyToken = prevTok

    if (accessorType != null) {
      val symName = getSymbolAst(name, classOf[AstSymbolMethod], start)
      if (!isPrivate && !isPrivateNow) {
        val keyNode: AstNode | String = symName match {
          case sm:    AstSymbolMethod => sm
          case other: AstNode         => other
          case s:     String          =>
            val sym = new AstSymbolMethod
            sym.start = start
            sym.name = s
            sym.end = prevTok
            sym
        }
        val quoteStr = symName match {
          case _: AstSymbolMethod => propertyToken.quote
          case _ => ""
        }
        val accValue = createAccessor()
        if (accessorType == "get") {
          val g = new AstObjectGetter
          g.start = start
          g.isStatic = isStatic
          g.key = keyNode
          g.quote = quoteStr
          g.value = accValue
          g.end = prevTok
          return annotate(g)
        } else {
          val s = new AstObjectSetter
          s.start = start
          s.isStatic = isStatic
          s.key = keyNode
          s.quote = quoteStr
          s.value = accValue
          s.end = prevTok
          return annotate(s)
        }
      } else {
        val pKey = getSymbolAst(name, classOf[AstSymbolMethod], start)
        val pVal = createAccessor()
        if (accessorType == "get") {
          val pg = new AstPrivateGetter
          pg.start = start
          pg.isStatic = isStatic
          pg.key = pKey
          pg.value = pVal
          pg.end = prevTok
          return annotate(pg)
        } else {
          val ps = new AstPrivateSetter
          ps.start = start
          ps.isStatic = isStatic
          ps.key = pKey
          ps.value = pVal
          ps.end = prevTok
          return annotate(ps)
        }
      }
    }

    if (is(Token.Punc, "(")) {
      val symName = getSymbolAst(name, classOf[AstSymbolMethod], start)
      val method: AstNode = if (isPrivate || isPrivateNow) {
        val pm = new AstPrivateMethod
        pm.start = start
        pm.isStatic = isStatic
        pm.key = symName
        pm.value = createAccessor(isGenerator, isAsyncProp)
        pm.end = prevTok
        pm
      } else {
        val cm = new AstConciseMethod
        cm.start = start
        cm.isStatic = isStatic
        cm.key = symName
        cm.quote = symName match {
          case _: AstSymbolMethod => propertyToken.quote
          case _ => ""
        }
        cm.value = createAccessor(isGenerator, isAsyncProp)
        cm.end = prevTok
        cm
      }
      return annotate(method)
    }

    if (isClass) {
      val symVariant: Class[? <: AstNode] =
        if (isPrivate || isPrivateNow) classOf[AstSymbolPrivateProperty]
        else classOf[AstSymbolClassProperty]

      val key   = getSymbolAst(name, symVariant, start)
      val quote = key match {
        case _: AstSymbolClassProperty => propertyToken.quote
        case _ => ""
      }

      if (is(Token.Operator, "=")) {
        next()
        val cp: AstNode = if (isPrivate || isPrivateNow) {
          val cpp = new AstClassPrivateProperty
          cpp.start = start
          cpp.isStatic = isStatic
          cpp.key = key
          cpp.value = expression(commas = false)
          cpp.end = prevTok
          cpp
        } else {
          val cp = new AstClassProperty
          cp.start = start
          cp.isStatic = isStatic
          cp.quote = quote
          cp.key = key
          cp.value = expression(commas = false)
          cp.end = prevTok
          cp
        }
        return annotate(cp)
      } else if (
        is(Token.Name) || is(Token.PrivateName) || is(Token.Punc, "[") ||
        is(Token.Operator, "*") || is(Token.Punc, ";") || is(Token.Punc, "}") ||
        is(Token.StringType) || is(Token.Num) || is(Token.BigInt)
      ) {
        val cp: AstNode = if (isPrivate || isPrivateNow) {
          val cpp = new AstClassPrivateProperty
          cpp.start = start
          cpp.isStatic = isStatic
          cpp.key = key
          cpp.end = prevTok
          cpp
        } else {
          val cp = new AstClassProperty
          cp.start = start
          cp.isStatic = isStatic
          cp.quote = quote
          cp.key = key
          cp.end = prevTok
          cp
        }
        return annotate(cp)
      }
    }
    null
  }

  private def getSymbolAst(
    name:        String | AstNode | Null,
    symbolClass: Class[? <: AstNode],
    start:       AstToken
  ): AstNode | String =
    name match {
      case s: String =>
        val sym = createSymbol(symbolClass)
        sym.start = start
        sym.asInstanceOf[AstSymbol].name = s
        sym.end = prevTok
        sym
      case null => unexpected()
      case n: AstNode => n
    }

  private def classStaticBlock(): AstNode | Null = {
    if (!is(Token.Punc, "{")) {
      return null
    }
    val start = token
    val body  = ArrayBuffer.empty[AstNode]
    next()
    while (!is(Token.Punc, "}"))
      body.addOne(statement())
    next()
    val sb = new AstClassStaticBlock
    sb.start = start
    sb.body = body
    sb.end = prevTok
    sb
  }

  // =========================================================================
  // Import / Export
  // =========================================================================

  private def maybeImportAttributes(): AstNode | Null =
    if ((is(Token.Keyword, "with") || is(Token.Name, "assert")) && !hasNewlineBefore(token)) {
      next()
      objectOrDestructuring()
    } else {
      null
    }

  private def importStatement(): AstNode = {
    val start = prevTok

    var importedName: AstNode | Null = null
    if (is(Token.Name)) {
      importedName = asSymbol(classOf[AstSymbolImport])
    }
    if (is(Token.Punc, ",")) {
      next()
    }

    val importedNames = mapNames(isImport = true)

    if (importedNames != null || importedName != null) {
      expectToken(Token.Name, "from")
    }
    val modStr = token
    if (modStr.tokenType != Token.StringType) {
      unexpected()
    }
    next()

    val attributes = maybeImportAttributes()

    val imp = new AstImport
    imp.start = start
    imp.importedName = importedName
    imp.importedNames = importedNames
    val modNameNode = new AstString
    modNameNode.start = modStr
    modNameNode.value = modStr.value
    modNameNode.quote = modStr.quote
    modNameNode.end = modStr
    imp.moduleName = modNameNode
    imp.attributes = attributes
    imp.end = token
    imp
  }

  private def importMeta(allowCalls: Boolean): AstNode = {
    val start = token
    expectToken(Token.Name, "import")
    expectToken(Token.Punc, ".")
    expectToken(Token.Name, "meta")
    val im = new AstImportMeta
    im.start = start
    im.end = prevTok
    subscripts(im, allowCalls)
  }

  private def mapName(isImport: Boolean): AstNameMapping = {
    val foreignType: Class[? <: AstNode] =
      if (isImport) classOf[AstSymbolImportForeign] else classOf[AstSymbolExportForeign]
    val localType: Class[? <: AstNode] =
      if (isImport) classOf[AstSymbolImport] else classOf[AstSymbolExport]
    val start = token

    var foreignName: AstNode | Null = null
    var localName:   AstNode | Null = null

    if (isImport) {
      foreignName = makeSymbolFromPropertyName(foreignType, start.quote)
    } else {
      localName = makeSymbolFromPropertyName(localType, start.quote)
    }

    if (is(Token.Name, "as")) {
      next()
      if (isImport) {
        localName = makeSymbolFromPropertyName(localType)
      } else {
        foreignName = makeSymbolFromPropertyName(foreignType, token.quote)
      }
    } else {
      if (isImport) {
        val fn  = foreignName.nn
        val sym = createSymbol(localType)
        sym.start = fn.start
        sym.end = fn.end
        fn match {
          case s: AstSymbol => sym.asInstanceOf[AstSymbol].name = s.name
          case _ =>
        }
        localName = sym
      } else {
        val ln  = localName.nn
        val sym = createSymbol(foreignType)
        sym.start = ln.start
        sym.end = ln.end
        ln match {
          case s: AstSymbol => sym.asInstanceOf[AstSymbol].name = s.name
          case _ =>
        }
        foreignName = sym
      }
    }

    val nm = new AstNameMapping
    nm.start = start
    nm.foreignName = foreignName
    nm.name = localName
    nm.end = prevTok
    nm
  }

  private def makeSymbolFromPropertyName(
    symbolClass: Class[? <: AstNode],
    quote:       String = ""
  ): AstNode = {
    val propName = asPropertyName()
    val sym      = createSymbol(symbolClass)
    val nameStr  = propName match {
      case s: String  => s
      case _: AstNode => "" // computed property — unusual in import/export
    }
    sym.asInstanceOf[AstSymbol].name = nameStr
    sym match {
      case e: AstSymbolExportForeign => e.quote = quote
      case i: AstSymbolImportForeign => i.quote = quote
      case e: AstSymbolExport        => e.quote = quote
      case _ =>
    }
    sym.start = prevTok
    sym.end = prevTok
    sym
  }

  private def mapNameAsterisk(isImport: Boolean, importOrExportName: AstNode | Null): AstNameMapping = {
    val foreignType: Class[? <: AstNode] =
      if (isImport) classOf[AstSymbolImportForeign] else classOf[AstSymbolExportForeign]
    val localType: Class[? <: AstNode] =
      if (isImport) classOf[AstSymbolImport] else classOf[AstSymbolExport]
    val start  = token
    val endTok = prevTok

    var localName:   AstNode | Null = null
    var foreignName: AstNode | Null = null

    if (isImport) {
      localName = importOrExportName
    } else {
      foreignName = importOrExportName
    }

    if (localName == null) {
      val sym = createSymbol(localType)
      sym.start = start
      sym.asInstanceOf[AstSymbol].name = "*"
      sym.end = endTok
      localName = sym
    }
    if (foreignName == null) {
      val sym = createSymbol(foreignType)
      sym.start = start
      sym.asInstanceOf[AstSymbol].name = "*"
      sym.end = endTok
      foreignName = sym
    }

    val nm = new AstNameMapping
    nm.start = start
    nm.foreignName = foreignName
    nm.name = localName
    nm.end = endTok
    nm
  }

  private def mapNames(isImport: Boolean): ArrayBuffer[AstNode] | Null =
    if (is(Token.Punc, "{")) {
      next()
      val names = ArrayBuffer.empty[AstNode]
      while (!is(Token.Punc, "}")) {
        names.addOne(mapName(isImport))
        if (is(Token.Punc, ",")) {
          next()
        }
      }
      next()
      names
    } else if (is(Token.Operator, "*")) {
      next()
      var name: AstNode | Null = null
      if (is(Token.Name, "as")) {
        next()
        name =
          if (isImport) asSymbol(classOf[AstSymbolImport])
          else asSymbolOrString(classOf[AstSymbolExportForeign])
      }
      ArrayBuffer(mapNameAsterisk(isImport, name))
    } else {
      null
    }

  private def exportStatement(): AstNode = {
    val start     = token
    var isDefault = false
    var exportedNames: ArrayBuffer[AstNode] | Null = null

    if (is(Token.Keyword, "default")) {
      isDefault = true
      next()
    } else {
      exportedNames = mapNames(isImport = false)
      if (exportedNames != null) {
        if (is(Token.Name, "from")) {
          next()
          val modStr = token
          if (modStr.tokenType != Token.StringType) {
            unexpected()
          }
          next()
          val attributes = maybeImportAttributes()

          val exp = new AstExport
          exp.start = start
          exp.isDefault = isDefault
          exp.exportedNames = exportedNames
          val modName = new AstString
          modName.start = modStr
          modName.value = modStr.value
          modName.quote = modStr.quote
          modName.end = modStr
          exp.moduleName = modName
          exp.end = prevTok
          exp.attributes = attributes
          return exp
        } else {
          val exp = new AstExport
          exp.start = start
          exp.isDefault = isDefault
          exp.exportedNames = exportedNames
          exp.end = prevTok
          return exp
        }
      }
    }

    var exportedValue:      AstNode | Null = null
    var exportedDefinition: AstNode | Null = null

    if (
      is(Token.Punc, "{") ||
      (isDefault && (is(Token.Keyword, "class") || is(Token.Keyword, "function")) && isToken(peek(), Token.Punc))
    ) {
      exportedValue = expression(commas = false)
      semicolon()
    } else {
      val node = statement(isExportDefault = isDefault)
      if (node.isInstanceOf[AstDefinitions] && isDefault) {
        unexpected(node.start)
      } else if (node.isInstanceOf[AstDefinitions] || node.isInstanceOf[AstDefun] || node.isInstanceOf[AstDefClass]) {
        exportedDefinition = node
      } else if (node.isInstanceOf[AstClassExpression] || node.isInstanceOf[AstFunction]) {
        exportedValue = node
      } else if (node.isInstanceOf[AstSimpleStatement]) {
        exportedValue = node.asInstanceOf[AstSimpleStatement].body
      } else {
        unexpected(node.start)
      }
    }

    val exp = new AstExport
    exp.start = start
    exp.isDefault = isDefault
    exp.exportedValue = exportedValue
    exp.exportedDefinition = exportedDefinition
    exp.end = prevTok
    exp
  }

  // =========================================================================
  // Property name parsing
  // =========================================================================

  /** Read a property name — returns a String for simple names, or an AstNode for computed [expr]. */
  private def asPropertyName(): String | AstNode | Null = {
    val tmp = token
    tmp.tokenType match {
      case Token.Punc =>
        if (tmp.value == "[") {
          next()
          val ex = expression(commas = false)
          expect("]")
          return ex
        } else {
          unexpected(tmp)
        }
      case Token.Operator =>
        if (tmp.value == "*") {
          next()
          return null
        }
        if (!Set("delete", "in", "instanceof", "new", "typeof", "void").contains(tmp.value)) {
          unexpected(tmp)
        }
        next()
        tmp.value
      case Token.Name | Token.PrivateName | Token.StringType | Token.Keyword | Token.Atom =>
        next()
        tmp.value
      case Token.Num | Token.BigInt =>
        next()
        tmp.value
      case _ =>
        unexpected(tmp)
    }
  }

  private def asName(): String = {
    val tmp = token
    if (tmp.tokenType != Token.Name && tmp.tokenType != Token.PrivateName) {
      unexpected()
    }
    next()
    tmp.value
  }

  // =========================================================================
  // Symbol creation helpers
  // =========================================================================

  private def createSymbol(symbolClass: Class[? <: AstNode]): AstNode =
    if (symbolClass == classOf[AstSymbolVar]) new AstSymbolVar
    else if (symbolClass == classOf[AstSymbolConst]) new AstSymbolConst
    else if (symbolClass == classOf[AstSymbolLet]) new AstSymbolLet
    else if (symbolClass == classOf[AstSymbolUsing]) new AstSymbolUsing
    else if (symbolClass == classOf[AstSymbolFunarg]) new AstSymbolFunarg
    else if (symbolClass == classOf[AstSymbolCatch]) new AstSymbolCatch
    else if (symbolClass == classOf[AstSymbolDefun]) new AstSymbolDefun
    else if (symbolClass == classOf[AstSymbolLambda]) new AstSymbolLambda
    else if (symbolClass == classOf[AstSymbolDefClass]) new AstSymbolDefClass
    else if (symbolClass == classOf[AstSymbolClass]) new AstSymbolClass
    else if (symbolClass == classOf[AstSymbolImport]) new AstSymbolImport
    else if (symbolClass == classOf[AstSymbolExport]) new AstSymbolExport
    else if (symbolClass == classOf[AstSymbolExportForeign]) new AstSymbolExportForeign
    else if (symbolClass == classOf[AstSymbolImportForeign]) new AstSymbolImportForeign
    else if (symbolClass == classOf[AstSymbolRef]) new AstSymbolRef
    else if (symbolClass == classOf[AstSymbolMethod]) new AstSymbolMethod
    else if (symbolClass == classOf[AstSymbolClassProperty]) new AstSymbolClassProperty
    else if (symbolClass == classOf[AstSymbolPrivateProperty]) new AstSymbolPrivateProperty
    else if (symbolClass == classOf[AstLabel]) {
      val l = new AstLabel
      l.initialize()
      l
    } else if (symbolClass == classOf[AstLabelRef]) new AstLabelRef
    else new AstSymbolRef // fallback

  private def makeSymbol(symbolClass: Class[? <: AstNode]): AstNode = {
    val name = token.value
    if (name == "this") {
      val t = new AstThis
      t.name = name
      t.start = token
      t.end = token
      t
    } else if (name == "super") {
      val s = new AstSuper
      s.name = name
      s.start = token
      s.end = token
      s
    } else {
      val sym = createSymbol(symbolClass)
      sym.asInstanceOf[AstSymbol].name = name
      sym.start = token
      sym.end = token
      sym
    }
  }

  private def verifySymbol(sym: AstNode): Unit =
    sym match {
      case s: AstSymbol =>
        val name = s.name
        if (isInGenerator() && name == "yield") {
          tokenError(sym.start, "Yield cannot be used as identifier inside generators")
        }
        if (input.hasDirective("use strict")) {
          if (name == "yield") {
            tokenError(sym.start, "Unexpected yield identifier inside strict mode")
          }
          if (sym.isInstanceOf[AstSymbolDeclaration] && (name == "arguments" || name == "eval")) {
            tokenError(sym.start, "Unexpected " + name + " in strict mode")
          }
        }
      case _ =>
    }

  private def asSymbol(symbolClass: Class[? <: AstNode]): AstNode = {
    if (!is(Token.Name)) {
      croak("Name expected")
    }
    val sym = makeSymbol(symbolClass)
    verifySymbol(sym)
    next()
    sym
  }

  private def asSymbolOpt(symbolClass: Class[? <: AstNode]): AstNode | Null =
    if (!is(Token.Name)) {
      null
    } else {
      val sym = makeSymbol(symbolClass)
      verifySymbol(sym)
      next()
      sym
    }

  private def asSymbolOrString(symbolClass: Class[? <: AstNode]): AstNode =
    if (!is(Token.Name)) {
      if (!is(Token.StringType)) {
        croak("Name or string expected")
      }
      val tok = token
      val sym = createSymbol(symbolClass)
      sym.start = tok
      sym.end = tok
      sym.asInstanceOf[AstSymbol].name = tok.value
      sym match {
        case e: AstSymbolExportForeign => e.quote = tok.quote
        case i: AstSymbolImportForeign => i.quote = tok.quote
        case _ =>
      }
      next()
      sym
    } else {
      val sym = makeSymbol(symbolClass)
      verifySymbol(sym)
      next()
      sym
    }

  // =========================================================================
  // Annotation
  // =========================================================================

  private def annotate[T <: AstNode](node: T, beforeToken: AstToken = null.asInstanceOf[AstToken]): T = { // @nowarn — null sentinel
    val bt       = if (beforeToken != null) beforeToken else node.start
    val comments = bt.commentsBefore
    val commentsOutsideParens: Integer | Null = outerCommentsBeforeCounts.get(bt) // @nowarn — Java interop
    val limit = if (commentsOutsideParens != null) commentsOutsideParens.nn.intValue() else comments.size
    var i     = limit
    while ({ i -= 1; i >= 0 }) {
      val comment = comments(i)
      if (comment.value.contains("@__") || comment.value.contains("#__")) {
        if (comment.value.contains("@__PURE__") || comment.value.contains("#__PURE__")) {
          node.flags |= Annotations.Pure
        } else if (comment.value.contains("@__INLINE__") || comment.value.contains("#__INLINE__")) {
          node.flags |= Annotations.Inline
        } else if (comment.value.contains("@__NOINLINE__") || comment.value.contains("#__NOINLINE__")) {
          node.flags |= Annotations.NoInline
        } else if (comment.value.contains("@__KEY__") || comment.value.contains("#__KEY__")) {
          node.flags |= Annotations.Key
        } else if (comment.value.contains("@__MANGLE_PROP__") || comment.value.contains("#__MANGLE_PROP__")) {
          node.flags |= Annotations.MangleProp
        }
        i = -1 // break
      }
    }
    node
  }

  // =========================================================================
  // Subscripts — member access, calls, optional chaining
  // =========================================================================

  private def subscripts(expr: AstNode, allowCalls: Boolean, isChain: Boolean = false): AstNode = {
    val start = expr.start
    if (is(Token.Punc, ".")) {
      next()
      if (is(Token.PrivateName) && !inClass) {
        croak("Private field must be used in an enclosing class")
      }
      val dotNode: AstNode = if (is(Token.PrivateName)) {
        val d = new AstDotHash
        d.start = start
        d.expression = expr
        d.optional = false
        d.property = asName()
        d.end = prevTok
        d
      } else {
        val d = new AstDot
        d.start = start
        d.expression = expr
        d.optional = false
        d.property = asName()
        d.end = prevTok
        d
      }
      return annotate(subscripts(dotNode, allowCalls, isChain))
    }
    if (is(Token.Punc, "[")) {
      next()
      val prop = expression(commas = true)
      expect("]")
      val sub = new AstSub
      sub.start = start
      sub.expression = expr
      sub.optional = false
      sub.property = prop
      sub.end = prevTok
      return annotate(subscripts(sub, allowCalls, isChain))
    }
    if (allowCalls && is(Token.Punc, "(")) {
      next()
      val call = new AstCall
      call.start = start
      call.expression = expr
      call.optional = false
      call.args = callArgs()
      call.end = prevTok
      annotate(call)
      return subscripts(call, allowCalls = true, isChain)
    }

    // Optional chain
    if (is(Token.Punc, "?.")) {
      next()

      var chainContents: AstNode | Null = null

      if (allowCalls && is(Token.Punc, "(")) {
        next()
        val call = new AstCall
        call.start = start
        call.optional = true
        call.expression = expr
        call.args = callArgs()
        call.end = prevTok
        annotate(call)
        chainContents = subscripts(call, allowCalls = true, isChain = true)
      } else if (is(Token.Name) || is(Token.PrivateName)) {
        if (is(Token.PrivateName) && !inClass) {
          croak("Private field must be used in an enclosing class")
        }
        val dotNode: AstNode = if (is(Token.PrivateName)) {
          val d = new AstDotHash
          d.start = start
          d.expression = expr
          d.optional = true
          d.property = asName()
          d.end = prevTok
          d
        } else {
          val d = new AstDot
          d.start = start
          d.expression = expr
          d.optional = true
          d.property = asName()
          d.end = prevTok
          d
        }
        chainContents = annotate(subscripts(dotNode, allowCalls, isChain = true))
      } else if (is(Token.Punc, "[")) {
        next()
        val property = expression(commas = true)
        expect("]")
        val sub = new AstSub
        sub.start = start
        sub.expression = expr
        sub.optional = true
        sub.property = property
        sub.end = prevTok
        chainContents = annotate(subscripts(sub, allowCalls, isChain = true))
      }

      if (chainContents == null) unexpected()

      if (chainContents.nn.isInstanceOf[AstChain]) return chainContents.nn

      val chain = new AstChain
      chain.start = start
      chain.expression = chainContents.nn
      chain.end = prevTok
      return chain
    }

    if (is(Token.TemplateHead)) {
      if (isChain) {
        // a?.b`c` is a syntax error
        unexpected()
      }
      val pts = new AstPrefixedTemplateString
      pts.start = start
      pts.prefix = expr
      pts.templateString = templateString()
      pts.end = prevTok
      return subscripts(pts, allowCalls, isChain = false)
    }
    expr
  }

  private def callArgs(): ArrayBuffer[AstNode] = {
    val args = ArrayBuffer.empty[AstNode]
    while (!is(Token.Punc, ")")) {
      if (is(Token.Expand, "...")) {
        next()
        val exp = new AstExpansion
        exp.start = prevTok
        exp.expression = expression(commas = false)
        exp.end = prevTok
        args.addOne(exp)
      } else {
        args.addOne(expression(commas = false))
      }
      if (!is(Token.Punc, ")")) {
        expect(",")
      }
    }
    next()
    args
  }

  // =========================================================================
  // Unary expressions
  // =========================================================================

  @scala.annotation.nowarn("msg=unused private member")
  private def maybeUnary(allowCalls: Boolean = true, allowArrows: Boolean = false): AstNode = {
    val start = token
    if (start.tokenType == Token.Name && start.value == "await" && canAwait()) {
      next()
      return awaitExpression()
    }
    if (is(Token.Operator) && Token.UNARY_PREFIX.contains(start.value)) {
      next()
      handleRegexp()
      val ex = makeUnary(isPrefix = true, start, maybeUnary(allowCalls, allowArrows = false))
      ex.start = start
      ex.end = prevTok
      return ex
    }
    var value = exprAtom(allowCalls, allowArrows)
    while (is(Token.Operator) && Token.UNARY_POSTFIX.contains(token.value) && !hasNewlineBefore(token)) {
      if (value.isInstanceOf[AstArrow]) unexpected()
      value = makeUnary(isPrefix = false, token, value)
      value.start = start
      value.end = token
      next()
    }
    value
  }

  private def makeUnary(isPrefix: Boolean, tok: AstToken, expr: AstNode): AstNode = {
    val op = tok.value
    op match {
      case "++" | "--" =>
        if (!isAssignable(expr)) {
          croak("Invalid use of " + op + " operator", tok.line, tok.col, tok.pos)
        }
      case "delete" =>
        if (expr.isInstanceOf[AstSymbolRef] && input.hasDirective("use strict")) {
          croak("Calling delete on expression not allowed in strict mode", expr.start.line, expr.start.col, expr.start.pos)
        }
      case _ =>
    }
    if (isPrefix) {
      val u = new AstUnaryPrefix
      u.operator = op
      u.expression = expr
      u
    } else {
      val u = new AstUnaryPostfix
      u.operator = op
      u.expression = expr
      u
    }
  }

  // =========================================================================
  // Binary expressions (precedence climbing)
  // =========================================================================

  private def exprOp(left: AstNode, minPrec: Int, noIn: Boolean): AstNode = {
    var op: String | Null = if (is(Token.Operator)) token.value else null
    if (op != null && op.nn == "in" && noIn) op = null
    if (
      op != null && op.nn == "**" && left.isInstanceOf[AstUnaryPrefix] &&
      !isToken(left.start, Token.Punc, "(") &&
      left.asInstanceOf[AstUnaryPrefix].operator != "--" &&
      left.asInstanceOf[AstUnaryPrefix].operator != "++"
    ) {
      unexpected(left.start)
    }
    val prec: Int = if (op != null) Token.PRECEDENCE.getOrElse(op.nn, -1) else -1
    if (prec >= 0 && (prec > minPrec || (op.nn == "**" && minPrec == prec))) {
      next()
      val right = exprOps(noIn, prec, allowCalls = true)
      val bin   = new AstBinary
      bin.start = left.start
      bin.left = left
      bin.operator = op.nn
      bin.right = right
      bin.end = right.end
      exprOp(bin, minPrec, noIn)
    } else {
      left
    }
  }

  @scala.annotation.nowarn("msg=unused private member")
  private def exprOps(
    noIn:        Boolean = false,
    minPrec:     Int = 0,
    allowCalls:  Boolean = true,
    allowArrows: Boolean = false
  ): AstNode =
    if (!noIn && minPrec < Token.PRECEDENCE("in") && is(Token.PrivateName)) {
      if (!inClass) {
        croak("Private field must be used in an enclosing class")
      }
      val start = token
      val key   = new AstSymbolPrivateProperty
      key.start = start
      key.name = start.value
      key.end = start
      next()
      expectToken(Token.Operator, "in")

      val privateIn = new AstPrivateIn
      privateIn.start = start
      privateIn.key = key
      privateIn.value = exprOps(noIn, Token.PRECEDENCE("in"), allowCalls = true, allowArrows = false)
      privateIn.end = prevTok

      exprOp(privateIn, 0, noIn)
    } else {
      exprOp(maybeUnary(allowCalls, allowArrows), minPrec, noIn)
    }

  // =========================================================================
  // Conditional expression
  // =========================================================================

  @scala.annotation.nowarn("msg=unused private member")
  private def maybeConditional(noIn: Boolean = false): AstNode = {
    val start = token
    val expr  = exprOps(noIn, 0, allowCalls = true, allowArrows = true)
    if (is(Token.Operator, "?")) {
      next()
      val yes = expression(commas = false)
      expect(":")
      val cond = new AstConditional
      cond.start = start
      cond.condition = expr
      cond.consequent = yes
      cond.alternative = expression(commas = false, noIn = noIn)
      cond.end = prevTok
      cond
    } else {
      expr
    }
  }

  // =========================================================================
  // Assignment helpers
  // =========================================================================

  private def isAssignable(expr: AstNode): Boolean =
    expr.isInstanceOf[AstPropAccess] || expr.isInstanceOf[AstSymbolRef]

  private def toDestructuring(node: AstNode): AstNode =
    node match {
      case obj: AstObject =>
        val d = new AstDestructuring
        d.start = obj.start
        d.names = obj.properties.map(toDestructuring)
        d.isArray = false
        d.end = obj.end
        d

      case arr: AstArray =>
        val names = ArrayBuffer.empty[AstNode]
        var i     = 0
        while (i < arr.elements.size) {
          arr.elements(i) match {
            case exp: AstExpansion =>
              if (i + 1 != arr.elements.size) {
                tokenError(exp.start, "Spread must the be last element in destructuring array")
              }
              exp.expression = toDestructuring(exp.expression.nn)
            case _ =>
          }
          names.addOne(toDestructuring(arr.elements(i)))
          i += 1
        }
        val d = new AstDestructuring
        d.start = arr.start
        d.names = names
        d.isArray = true
        d.end = arr.end
        d

      case prop: AstObjectProperty =>
        prop.value = toDestructuring(prop.value.nn)
        prop

      case assign: AstAssign =>
        val da = new AstDefaultAssign
        da.start = assign.start
        da.left = assign.left
        da.operator = "="
        da.right = assign.right
        da.end = assign.end
        da

      case _ =>
        node
    }

  // =========================================================================
  // Assignment expression
  // =========================================================================

  @scala.annotation.nowarn("msg=unused private member")
  private def maybeAssign(noIn: Boolean = false): AstNode = {
    handleRegexp()
    val start = token

    if (start.tokenType == Token.Name && start.value == "yield") {
      if (isInGenerator()) {
        next()
        return yieldExpression()
      } else if (input.hasDirective("use strict")) {
        tokenError(token, "Unexpected yield identifier inside strict mode")
      }
    }

    var left  = maybeConditional(noIn)
    val value = token.value

    if (is(Token.Operator) && Token.ASSIGNMENT.contains(value)) {
      if (isAssignable(left)) {
        next()
        val assign = new AstAssign
        assign.start = start
        assign.left = left
        assign.operator = value
        assign.right = maybeAssign(noIn)
        assign.logical = Token.LOGICAL_ASSIGNMENT.contains(value)
        assign.end = prevTok
        return assign
      }
      // try destructuring
      val destructured = toDestructuring(left)
      if (destructured.isInstanceOf[AstDestructuring]) {
        left = destructured
        next()
        val assign = new AstAssign
        assign.start = start
        assign.left = left
        assign.operator = value
        assign.right = maybeAssign(noIn)
        assign.logical = Token.LOGICAL_ASSIGNMENT.contains(value)
        assign.end = prevTok
        return assign
      }
      croak("Invalid assignment")
    }
    left
  }

  // =========================================================================
  // Sequence / comma expression
  // =========================================================================

  private def toExprOrSequence(start: AstToken, exprs: ArrayBuffer[AstNode]): AstNode =
    if (exprs.size == 1) {
      exprs(0)
    } else if (exprs.size > 1) {
      val seq = new AstSequence
      seq.start = start
      seq.expressions = exprs
      seq.end = peek()
      seq
    } else {
      croak("Invalid parenthesized expression")
    }

  private def expression(commas: Boolean = true, noIn: Boolean = false): AstNode = {
    val start = token
    val exprs = ArrayBuffer.empty[AstNode]
    var cont  = true
    while (cont) {
      exprs.addOne(maybeAssign(noIn))
      if (!commas || !is(Token.Punc, ",")) {
        cont = false
      } else {
        next()
      }
    }
    toExprOrSequence(start, exprs)
  }

  // =========================================================================
  // Loop helper
  // =========================================================================

  private inline def inLoop[T](inline cont: T): T = {
    inLoop += 1
    val ret = cont
    inLoop -= 1
    ret
  }

  // =========================================================================
  // Toplevel
  // =========================================================================

  private def parseToplevel(): AstToplevel = {
    val start = token
    val body  = ArrayBuffer.empty[AstNode]
    input.pushDirectivesStack()
    if (options.module) input.addDirective("use strict")
    while (!is(Token.Eof))
      body.addOne(statement())
    input.popDirectivesStack()
    val endTok   = prevTok
    val toplevel = new AstToplevel
    toplevel.start = start
    toplevel.body = body
    toplevel.end = endTok
    toplevel
  }
}
