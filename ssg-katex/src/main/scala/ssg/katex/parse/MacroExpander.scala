/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This file contains the "gullet" where macros are expanded
 * until only non-macro tokens remain.
 *
 * Original source: katex src/MacroExpander.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: MacroExpander -> MacroExpander (same)
 *   Convention: JS stack (push/pop from end) -> mutable.ArrayBuffer
 *   Idiom: TypeScript null | undefined -> Nullable[A]
 */
package ssg
package katex
package parse

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

import ssg.commons.Nullable
import ssg.katex.{ MacroArg, MacroContextInterface, MacroDefinition, MacroExpansion, Mode, Namespace, ParseError, Settings, SourceLocation, Token }
import ssg.katex.data.Symbols
import ssg.katex.functions.FunctionDef

// List of commands that act like macros but aren't defined as a macro,
// function, or symbol.  Used in `isDefined`.
object MacroExpander {
  val implicitCommands: Map[String, Boolean] = Map(
    "^" -> true, // Parser.js
    "_" -> true, // Parser.js
    "\\limits" -> true, // Parser.js
    "\\nolimits" -> true // Parser.js
  )
}

class MacroExpander(input: String, val settings: Settings, var mode: Mode) extends MacroContextInterface {

  var expansionCount: Int   = 0
  var lexer:          Lexer = new Lexer(input, settings)
  // Make new global namespace
  val macros: Namespace[MacroDefinition] = new Namespace(
    ssg.katex.MacroDef._macros,
    settings.macros
  )
  val stack: mutable.ArrayBuffer[Token] = mutable.ArrayBuffer.empty // contains tokens in REVERSE order

  /** Feed a new input string to the same MacroExpander (with existing macros etc.).
    */
  def feed(input: String): Unit =
    lexer = new Lexer(input, settings)

  /** Switches between "text" and "math" modes.
    */
  def switchMode(newMode: Mode): Unit =
    mode = newMode

  /** Start a new group nesting within all namespaces.
    */
  def beginGroup(): Unit =
    macros.beginGroup()

  /** End current group nesting within all namespaces.
    */
  def endGroup(): Unit =
    macros.endGroup()

  /** Ends all currently nested groups (if any), restoring values before the groups began. Useful in case of an error in the middle of parsing.
    */
  def endGroups(): Unit =
    macros.endGroups()

  /** Returns the topmost token on the stack, without expanding it. Similar in behavior to TeX's `\futurelet`.
    */
  def future(): Token = {
    if (stack.isEmpty) {
      pushToken(lexer.lex())
    }
    stack(stack.length - 1)
  }

  /** Remove and return the next unexpanded token.
    */
  def popToken(): Token = {
    future() // ensure non-empty stack
    stack.remove(stack.length - 1)
  }

  /** Add a given token to the token stack. In particular, this get be used to put back a token returned from one of the other methods.
    */
  def pushToken(token: Token): Unit =
    stack.addOne(token)

  /** Append an array of tokens to the token stack.
    */
  def pushTokens(tokens: Array[Token]): Unit = {
    var i = 0
    while (i < tokens.length) {
      stack.addOne(tokens(i))
      i += 1
    }
  }

  /** Find an macro argument without expanding tokens and append the array of tokens to the token stack. Uses Token as a container for the result.
    */
  def scanArgument(isOptional: Boolean): Nullable[Token] = boundary {
    if (isOptional) {
      consumeSpaces() // \@ifnextchar gobbles any space following it
      if (future().text != "[") {
        break(Nullable.Null)
      }
      val start  = popToken() // don't include [ in tokens
      val result = consumeArg(Nullable(Array("]")))
      val tokens = result.tokens
      val end    = result.end

      // indicate the end of an argument
      pushToken(new Token("EOF", end.loc))

      pushTokens(tokens)
      Nullable(new Token("", SourceLocation.range(Nullable(start), Nullable(end))))
    } else {
      val result = consumeArg()
      val tokens = result.tokens
      val start  = result.start
      val end    = result.end

      // indicate the end of an argument
      pushToken(new Token("EOF", end.loc))

      pushTokens(tokens)
      Nullable(new Token("", SourceLocation.range(Nullable(start), Nullable(end))))
    }
  }

  /** Consume all following space tokens, without expansion.
    */
  def consumeSpaces(): Unit = {
    var continue = true
    while (continue) {
      val token = future()
      if (token.text == " ") {
        stack.remove(stack.length - 1)
      } else {
        continue = false
      }
    }
  }

  /** Consume an argument from the token stream, and return the resulting array of tokens and start/end token.
    */
  def consumeArg(delims: Nullable[Array[String]] = Nullable.Null): MacroArg = {
    // The argument for a delimited parameter is the shortest (possibly
    // empty) sequence of tokens with properly nested {...} groups that is
    // followed ... by this particular list of non-parameter tokens.
    // The argument for an undelimited parameter is the next nonblank
    // token, unless that token is '{', when the argument will be the
    // entire {...} group that follows.
    val tokens      = mutable.ArrayBuffer.empty[Token]
    val isDelimited = delims.isDefined && delims.get.length > 0
    if (!isDelimited) {
      // Ignore spaces between arguments.  As the TeXbook says:
      // "After you have said '\def\row#1#2{...}', you are allowed to
      //  put spaces between the arguments (e.g., '\row x n'), because
      //  TeX doesn't use single spaces as undelimited arguments."
      consumeSpaces()
    }
    val start = future()
    var tok: Token = start // overwritten on first loop iteration
    var depth      = 0
    var matchCount = 0
    var loopDone   = false
    while (!loopDone) {
      tok = popToken()
      tokens.addOne(tok)
      if (tok.text == "{") {
        depth += 1
      } else if (tok.text == "}") {
        depth -= 1
        if (depth == -1) {
          throw new ParseError("Extra }", Nullable(tok))
        }
      } else if (tok.text == "EOF") {
        throw new ParseError(
          "Unexpected end of input in a macro argument" +
            ", expected '" + (if (isDelimited) delims.get(matchCount) else "}") + "'",
          Nullable(tok)
        )
      }
      var delimBreak = false
      if (isDelimited) {
        val delimArr = delims.get
        if (
          (depth == 0 || (depth == 1 && delimArr(matchCount) == "{")) &&
          tok.text == delimArr(matchCount)
        ) {
          matchCount += 1
          if (matchCount == delimArr.length) {
            // don't include delims in tokens
            var j = 0
            while (j < matchCount) {
              tokens.remove(tokens.length - 1)
              j += 1
            }
            delimBreak = true
          }
        } else {
          matchCount = 0
        }
      }
      if (delimBreak || (depth == 0 && !isDelimited)) {
        loopDone = true
      }
    }

    // If the argument found ... has the form '{<nested tokens>}',
    // ... the outermost braces enclosing the argument are removed
    if (start.text == "{" && tokens.nonEmpty && tokens(tokens.length - 1).text == "}") {
      tokens.remove(tokens.length - 1)
      if (tokens.nonEmpty) {
        tokens.remove(0)
      }
    }
    val arr = tokens.toArray
    // reverse to fit in with stack order
    var left  = 0
    var right = arr.length - 1
    while (left < right) {
      val tmp = arr(left)
      arr(left) = arr(right)
      arr(right) = tmp
      left += 1
      right -= 1
    }
    MacroArg(arr, start, tok)
  }

  /** Consume the specified number of (delimited) arguments from the token stream and return the resulting array of arguments.
    */
  def consumeArgs(numArgs: Int, delimiters: Nullable[Array[Array[String]]] = Nullable.Null): Array[Array[Token]] = {
    if (delimiters.isDefined) {
      val delimArr = delimiters.get
      if (delimArr.length != numArgs + 1) {
        throw new ParseError("The length of delimiters doesn't match the number of args!")
      }
      val delims = delimArr(0)
      var i      = 0
      while (i < delims.length) {
        val tok = popToken()
        if (delims(i) != tok.text) {
          throw new ParseError("Use of the macro doesn't match its definition", Nullable(tok))
        }
        i += 1
      }
    }

    val args = new Array[Array[Token]](numArgs)
    var i    = 0
    while (i < numArgs) {
      val delimsForArg: Nullable[Array[String]] =
        if (delimiters.isDefined) Nullable(delimiters.get(i + 1))
        else Nullable.Null
      args(i) = consumeArg(delimsForArg).tokens
      i += 1
    }
    args
  }

  /** Overload matching MacroContextInterface (no delimiters). */
  def consumeArgs(numArgs: Int): Array[Array[Token]] =
    consumeArgs(numArgs, Nullable.Null)

  /** Increment `expansionCount` by the specified amount. Throw an error if it exceeds `maxExpand`.
    */
  def countExpansion(amount: Int): Unit = {
    expansionCount += amount
    if (expansionCount > settings.maxExpand) {
      throw new ParseError(
        "Too many expansions: infinite loop or " +
          "need to increase maxExpand setting"
      )
    }
  }

  /** Expand the next token only once if possible.
    *
    * If the token is expanded, the resulting tokens will be pushed onto the stack in reverse order, and the number of such tokens will be returned. This number might be zero or positive.
    *
    * If not, the return value is `false`, and the next token remains at the top of the stack.
    *
    * In either case, the next token will be on the top of the stack, or the stack will be empty (in case of empty expansion and no other tokens).
    *
    * Used to implement `expandAfterFuture` and `expandNextToken`.
    *
    * If expandableOnly, only expandable tokens are expanded and an undefined control sequence results in an error.
    */
  def expandOnce(expandableOnly: Boolean = false): Int | Boolean = boundary {
    val topToken = popToken()
    val name     = topToken.text
    val noexpand = topToken.noexpand.isDefined && topToken.noexpand.get
    val expansion: Nullable[MacroExpansion] =
      if (!noexpand) _getExpansion(name)
      else Nullable.Null
    if (expansion.isEmpty || (expandableOnly && expansion.get.unexpandable)) {
      if (
        expandableOnly && expansion.isEmpty &&
        name.nonEmpty && name.charAt(0) == '\\' && !isDefined(name)
      ) {
        throw new ParseError("Undefined control sequence: " + name)
      }
      pushToken(topToken)
      break(false: (Int | Boolean))
    }
    countExpansion(1)
    var tokens = expansion.get.tokens
    val args   = consumeArgs(expansion.get.numArgs, expansion.get.delimiters)
    if (expansion.get.numArgs > 0) {
      // paste arguments in place of the placeholders
      tokens = tokens.clone() // make a shallow copy
      var i = tokens.length - 1
      while (i >= 0) {
        var tok = tokens(i)
        if (tok.text == "#") {
          if (i == 0) {
            throw new ParseError("Incomplete placeholder at end of macro body", Nullable(tok))
          }
          i -= 1
          tok = tokens(i) // next token on stack
          if (tok.text == "#") { // ## -> #
            // drop first # (at i+1); keep the second # (at i)
            val newTokens = new Array[Token](tokens.length - 1)
            System.arraycopy(tokens, 0, newTokens, 0, i + 1)
            System.arraycopy(tokens, i + 2, newTokens, i + 1, tokens.length - i - 2)
            tokens = newTokens
          } else if (tok.text.length == 1 && tok.text.charAt(0) >= '1' && tok.text.charAt(0) <= '9') {
            // replace the placeholder with the indicated argument
            val argIndex  = tok.text.charAt(0) - '1'
            val argTokens = args(argIndex)
            val newTokens = new Array[Token](tokens.length - 2 + argTokens.length)
            System.arraycopy(tokens, 0, newTokens, 0, i)
            System.arraycopy(argTokens, 0, newTokens, i, argTokens.length)
            System.arraycopy(tokens, i + 2, newTokens, i + argTokens.length, tokens.length - i - 2)
            tokens = newTokens
          } else {
            throw new ParseError("Not a valid argument number", Nullable(tok))
          }
        }
        i -= 1
      }
    }
    // Concatenate expansion onto top of stack.
    pushTokens(tokens)
    tokens.length: (Int | Boolean)
  }

  /** Expand the next token only once (if possible), and return the resulting top token on the stack (without removing anything from the stack). Similar in behavior to TeX's `\expandafter\futurelet`.
    * Equivalent to expandOnce() followed by future().
    */
  def expandAfterFuture(): Token = {
    expandOnce()
    future()
  }

  /** Recursively expand first token, then return first non-expandable token.
    */
  def expandNextToken(): Token = boundary {
    while (true) {
      val result = expandOnce()
      result match {
        case b: Boolean if !b =>
          // fully expanded
          val token = stack.remove(stack.length - 1)
          // the token after \noexpand is interpreted as if its meaning
          // were '\relax'
          if (token.treatAsRelax.isDefined && token.treatAsRelax.get) {
            token.text = "\\relax"
          }
          break(token)
        case _ => // continue expanding
      }
    }
    throw new Error("unreachable")
  }

  /** Fully expand the given macro name and return the resulting list of tokens, or return `Nullable.Null` if no such macro is defined.
    */
  def expandMacro(name: String): Nullable[Array[Token]] =
    if (macros.has(name)) {
      Nullable(expandTokens(Array(new Token(name))))
    } else {
      Nullable.Null
    }

  /** Fully expand the given token stream and return the resulting list of tokens. Note that the input tokens are in reverse order, but the output tokens are in forward order.
    */
  def expandTokens(tokens: Array[Token]): Array[Token] = {
    val output         = mutable.ArrayBuffer.empty[Token]
    val oldStackLength = stack.length
    pushTokens(tokens)
    while (stack.length > oldStackLength) {
      // Expand only expandable tokens
      val result = expandOnce(true)
      result match {
        case b: Boolean if !b =>
          // fully expanded
          val token = stack.remove(stack.length - 1)
          if (token.treatAsRelax.isDefined && token.treatAsRelax.get) {
            // the expansion of \noexpand is the token itself
            token.noexpand = Nullable(false)
            token.treatAsRelax = Nullable(false)
          }
          output.addOne(token)
        case _ => // continue
      }
    }
    // Count all of these tokens as additional expansions, to prevent
    // exponential blowup from linearly many \edef's.
    countExpansion(output.length)
    output.toArray
  }

  /** Fully expand the given macro name and return the result as a string, or return `Nullable.Null` if no such macro is defined.
    */
  def expandMacroAsText(name: String): Nullable[String] = {
    val tokens = expandMacro(name)
    if (tokens.isDefined) {
      Nullable(tokens.get.map(_.text).mkString)
    } else {
      Nullable.Null
    }
  }

  /** Returns the expanded macro as a reversed array of tokens and a macro argument count. Or returns `null` if no such macro.
    */
  private def _getExpansion(name: String): Nullable[MacroExpansion] = boundary {
    val definition = macros.get(name)

    if (definition.isEmpty) { // mainly checking for undefined here
      break(Nullable.Null)
    }
    // If a single character has an associated catcode other than 13
    // (active character), then don't expand it.
    if (name.length == 1) {
      val catcode = lexer.catcodes.getOrElse(name, -1)
      if (catcode != -1 && catcode != 13) {
        break(Nullable.Null)
      }
    }
    val defn = definition.get
    val expansion: String | MacroExpansion = defn match {
      case MacroDefinition.FunctionDef(f)  => f(this)
      case MacroDefinition.StringDef(s)    => s
      case MacroDefinition.ExpansionDef(e) => e
    }
    expansion match {
      case s: String =>
        var numArgs = 0
        if (s.contains("#")) {
          val stripped = s.replace("##", "")
          while (stripped.contains("#" + (numArgs + 1)))
            numArgs += 1
        }
        val bodyLexer = new Lexer(s, settings)
        val tokBuf    = mutable.ArrayBuffer.empty[Token]
        var tok       = bodyLexer.lex()
        while (tok.text != "EOF") {
          tokBuf.addOne(tok)
          tok = bodyLexer.lex()
        }
        val toks = tokBuf.toArray
        // reverse to fit in with stack using push and pop
        var left  = 0
        var right = toks.length - 1
        while (left < right) {
          val tmp = toks(left)
          toks(left) = toks(right)
          toks(right) = tmp
          left += 1
          right -= 1
        }
        Nullable(MacroExpansion(toks, numArgs))
      case e: MacroExpansion =>
        Nullable(e)
    }
  }

  /** Determine whether a command is currently "defined" (has some functionality), meaning that it's a macro (in the current group), a function, a symbol, or one of the special commands listed in
    * `implicitCommands`.
    */
  def isDefined(name: String): Boolean =
    macros.has(name) ||
      FunctionDef._functions.contains(name) ||
      Symbols.math.contains(name) ||
      Symbols.text.contains(name) ||
      MacroExpander.implicitCommands.contains(name)

  /** Determine whether a command is expandable.
    */
  def isExpandable(name: String): Boolean = {
    val macro_ = macros.get(name)
    if (macro_.isDefined) {
      macro_.get match {
        case MacroDefinition.StringDef(_)    => true
        case MacroDefinition.FunctionDef(_)  => true
        case MacroDefinition.ExpansionDef(e) => !e.unexpandable
      }
    } else {
      FunctionDef._functions.get(name) match {
        case Some(f) => !f.primitive
        case None    => false
      }
    }
  }
}
