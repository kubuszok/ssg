/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This file contains the parser used to parse out a TeX expression from the
 * input. Since TeX isn't context-free, standard parsers don't work particularly
 * well.
 *
 * The strategy of this parser is as such:
 *
 * The main functions (the `.parse...` ones) take a position in the current
 * parse string to parse tokens from. The lexer (found in Lexer.js, stored at
 * this.gullet.lexer) also supports pulling out tokens at arbitrary places. When
 * individual tokens are needed at a position, the lexer is called to pull out a
 * token, which is then used.
 *
 * The parser has a property called "mode" indicating the mode that
 * the parser is currently in. Currently it has to be one of "math" or
 * "text", which denotes whether the current environment is a math-y
 * one or a text-y one (e.g. inside \text). Currently, this serves to
 * limit the functions which can be used in text mode.
 *
 * The main functions then return an object which contains the useful data that
 * was parsed at its given point, and a new position at the end of the parsed
 * data. The main functions can call each other and continue the parsing by
 * using the returned position as a new starting point.
 *
 * There are also extra `.handle...` functions, which pull out some reused
 * functionality into self-contained functions.
 *
 * The functions return ParseNodes.
 *
 * Original source: katex src/Parser.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: Parser -> Parser (same)
 *   Convention: boundary/break for early returns
 *   Idiom: TypeScript null | undefined -> Nullable[A]
 */
package ssg
package katex
package parse

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

import ssg.commons.Nullable
import ssg.katex.{ BreakToken, Mode, ParseError, Settings, SourceLocation, Token }
import ssg.katex.data.{ Measurement, Symbols, UnicodeAccents, UnicodeScripts, UnicodeSupOrSub, UnicodeSymbols, Units }
import ssg.katex.functions.{ FunctionContext, FunctionDef, FunctionSpec }
import ssg.katex.environments.EnvSpec

class Parser(input: String, val settings: Settings) {

  // Start in math mode
  var mode: Mode = Mode.Math

  // Create a new macro expander (gullet) and (indirectly via that) also a
  // new lexer (mouth) for this parser (stomach, in the language of TeX)
  val gullet: MacroExpander = new MacroExpander(input, settings, mode)

  // Count leftright depth (for \middle errors)
  var leftrightDepth: Int = 0

  var nextToken: Nullable[Token] = Nullable.Null

  /** Checks a result to make sure it has the right type, and throws an appropriate error otherwise.
    */
  def expect(text: String, consume: Boolean = true): Unit = {
    if (fetch().text != text) {
      throw new ParseError(s"Expected '$text', got '${fetch().text}'", Nullable(fetch()))
    }
    if (consume) {
      this.consume()
    }
  }

  /** Discards the current lookahead token, considering it consumed.
    */
  def consume(): Unit =
    nextToken = Nullable.Null

  /** Return the current lookahead token, or if there isn't one (at the beginning, or if the previous lookahead token was consume()d), fetch the next token as the new lookahead token and return it.
    */
  def fetch(): Token = {
    if (nextToken.isEmpty) {
      nextToken = Nullable(gullet.expandNextToken())
    }
    nextToken.get
  }

  /** Switches between "text" and "math" modes.
    */
  def switchMode(newMode: Mode): Unit = {
    mode = newMode
    gullet.switchMode(newMode)
  }

  /** Main parsing function, which parses an entire input.
    */
  def parse(): Array[AnyParseNode] = {
    if (!settings.globalGroup) {
      // Create a group namespace for the math expression.
      // (LaTeX creates a new group for every $...$, $$...$$, \[...\].)
      gullet.beginGroup()
    }

    // Use old \color behavior (same as LaTeX's \textcolor) if requested.
    // We do this within the group for the math expression, so it doesn't
    // pollute settings.macros.
    if (settings.colorIsTextColor) {
      gullet.macros.set("\\color", Nullable(MacroDefinition.StringDef("\\textcolor")))
    }

    try {
      // Try to parse the input
      val result = parseExpression(false)

      // If we succeeded, make sure there's an EOF at the end
      expect("EOF")

      // End the group namespace for the expression
      if (!settings.globalGroup) {
        gullet.endGroup()
      }

      result

      // Close any leftover groups in case of a parse error.
    } finally
      gullet.endGroups()
  }

  /** Fully parse a separate sequence of tokens as a separate job. Tokens should be specified in reverse order, as in a MacroDefinition.
    */
  def subparse(tokens: Array[Token]): Array[AnyParseNode] = {
    // Save the next token from the current job.
    val oldToken = nextToken
    consume()

    // Run the new job, terminating it with an excess '}'
    gullet.pushToken(new Token("}"))
    gullet.pushTokens(tokens)
    val result = parseExpression(false)
    expect("}")

    // Restore the next token from the current job.
    nextToken = oldToken

    result
  }

  /** Parses an "expression", which is a list of atoms.
    *
    * `breakOnInfix`: Should the parsing stop when we hit infix nodes? This happens when functions have higher precedence than infix nodes in implicit parses.
    *
    * `breakOnTokenText`: The text of the token that the expression should end with, or `null` if something else should end the expression.
    */
  def parseExpression(
    breakOnInfix:     Boolean,
    breakOnTokenText: Nullable[String] = Nullable.Null
  ): Array[AnyParseNode] = {
    val body = mutable.ArrayBuffer.empty[AnyParseNode]
    // Keep adding atoms to the body until we can't parse any more atoms (either
    // we reached the end, a }, or a \right)
    var continue = true
    while (continue) {
      // Ignore spaces in math mode
      if (mode == Mode.Math) {
        consumeSpaces()
      }
      val lex = fetch()
      if (Parser.endOfExpression.contains(lex.text)) {
        continue = false
      } else if (breakOnTokenText.isDefined && lex.text == breakOnTokenText.get) {
        continue = false
      } else if (
        breakOnInfix && FunctionDef._functions.contains(lex.text) &&
        FunctionDef._functions(lex.text).infix
      ) {
        continue = false
      } else {
        val atom = parseAtom(breakOnTokenText)
        if (atom.isEmpty) {
          continue = false
        } else if (atom.get.nodeType == "internal") {
          // Internal nodes do not appear in parse tree
          // continue
        } else {
          body.addOne(atom.get)
        }
      }
    }
    if (mode == Mode.Text) {
      formLigatures(body)
    }
    handleInfixNodes(body.toArray)
  }

  /** Rewrites infix operators such as \over with corresponding commands such as \frac.
    *
    * There can only be one infix operator per group. If there's more than one then the expression is ambiguous. This can be resolved by adding {}.
    */
  def handleInfixNodes(body: Array[AnyParseNode]): Array[AnyParseNode] = boundary {
    var overIndex = -1
    var funcName: String = ""

    var i = 0
    while (i < body.length) {
      val node = body(i)
      if (node.nodeType == "infix") {
        if (overIndex != -1) {
          val infixToken = node.asInstanceOf[ParseNodeInfix].token
          throw new ParseError(
            "only one infix operator per group",
            if (infixToken.isDefined) Nullable(infixToken.get: SourceLocation.HasLoc) else Nullable.Null
          )
        }
        overIndex = i
        funcName = node.asInstanceOf[ParseNodeInfix].replaceWith
      }
      i += 1
    }

    if (overIndex != -1 && funcName.nonEmpty) {
      val numerBody = body.slice(0, overIndex)
      val denomBody = body.slice(overIndex + 1, body.length)

      val numerNode: AnyParseNode =
        if (numerBody.length == 1 && numerBody(0).nodeType == "ordgroup") {
          numerBody(0)
        } else {
          ParseNodeOrdgroup(mode = mode, body = numerBody)
        }

      val denomNode: AnyParseNode =
        if (denomBody.length == 1 && denomBody(0).nodeType == "ordgroup") {
          denomBody(0)
        } else {
          ParseNodeOrdgroup(mode = mode, body = denomBody)
        }

      val node =
        if (funcName == "\\\\abovefrac") {
          callFunction(funcName, Array(numerNode, body(overIndex), denomNode), Array.empty)
        } else {
          callFunction(funcName, Array(numerNode, denomNode), Array.empty)
        }
      break(Array(node))
    } else {
      break(body)
    }
  }

  /** Handle a subscript or superscript with nice errors.
    */
  def handleSupSubscript(
    name: String // For error reporting.
  ): AnyParseNode = {
    val symbolToken = fetch()
    val symbol      = symbolToken.text
    consume()
    consumeSpaces() // ignore spaces before sup/subscript argument

    // Skip over allowed internal nodes such as \relax
    var group: Nullable[AnyParseNode] = Nullable.Null
    var continue = true
    while (continue) {
      group = parseGroup(name)
      if (group.isDefined && group.get.nodeType == "internal") {
        // skip and retry
      } else {
        continue = false
      }
    }

    if (group.isEmpty) {
      throw new ParseError("Expected group after '" + symbol + "'", Nullable(symbolToken))
    }

    group.get
  }

  /** Converts the textual input of an unsupported command into a text node contained within a color node whose color is determined by errorColor
    */
  def formatUnsupportedCmd(text: String): UnsupportedCmdParseNode = {
    val textordArray = mutable.ArrayBuffer.empty[AnyParseNode]

    var i = 0
    while (i < text.length) {
      textordArray.addOne(ParseNodeTextord(mode = Mode.Text, text = text.charAt(i).toString))
      i += 1
    }

    val textNode = ParseNodeText(
      mode = mode,
      body = textordArray.toArray
    )
    val colorNode = ParseNodeColor(
      mode = mode,
      color = settings.errorColor,
      body = Array(textNode)
    )

    colorNode
  }

  /** Parses a group with optional super/subscripts.
    */
  def parseAtom(breakOnTokenText: Nullable[String] = Nullable.Null): Nullable[AnyParseNode] = boundary {
    // The body of an atom is an implicit group, so that things like
    // \left(x\right)^2 work correctly.
    val base = parseGroup("atom", breakOnTokenText)

    // Internal nodes (e.g. \relax) cannot support super/subscripts.
    // Instead we will pick up super/subscripts with blank base next round.
    if (base.isDefined && base.get.nodeType == "internal") {
      break(base: Nullable[AnyParseNode])
    }

    // In text mode, we don't have superscripts or subscripts
    if (mode == Mode.Text) {
      break(base: Nullable[AnyParseNode])
    }

    // Note that base may be empty (i.e. null) at this point.

    var superscript: Nullable[AnyParseNode] = Nullable.Null
    var subscript:   Nullable[AnyParseNode] = Nullable.Null
    var continue = true
    while (continue) {
      // Guaranteed in math mode, so eat any spaces first.
      consumeSpaces()

      // Lex the first token
      val lex = fetch()

      if (lex.text == "\\limits" || lex.text == "\\nolimits") {
        // We got a limit control
        if (base.isDefined && base.get.nodeType == "op") {
          val opNode = base.get.asInstanceOf[ParseNodeOp]
          val limits = lex.text == "\\limits"
          opNode.limits = limits
          opNode.alwaysHandleSupSub = Nullable(true)
        } else if (base.isDefined && base.get.nodeType == "operatorname") {
          val opNode = base.get.asInstanceOf[ParseNodeOperatorname]
          if (opNode.alwaysHandleSupSub) {
            opNode.limits = lex.text == "\\limits"
          }
        } else {
          throw new ParseError("Limit controls must follow a math operator", Nullable(lex))
        }
        consume()
      } else if (lex.text == "^") {
        // We got a superscript start
        if (superscript.isDefined) {
          throw new ParseError("Double superscript", Nullable(lex))
        }
        superscript = Nullable(handleSupSubscript("superscript"))
      } else if (lex.text == "_") {
        // We got a subscript start
        if (subscript.isDefined) {
          throw new ParseError("Double subscript", Nullable(lex))
        }
        subscript = Nullable(handleSupSubscript("subscript"))
      } else if (lex.text == "'") {
        // We got a prime
        if (superscript.isDefined) {
          throw new ParseError("Double superscript", Nullable(lex))
        }

        val prime = ParseNodeTextord(mode = mode, text = "\\prime")

        // Many primes can be grouped together, so we handle this here
        val primes = mutable.ArrayBuffer.empty[AnyParseNode]
        primes.addOne(prime)
        consume()
        // Keep lexing tokens until we get something that's not a prime
        while (fetch().text == "'") {
          // For each one, add another prime to the list
          primes.addOne(prime)
          consume()
        }
        // If there's a superscript following the primes, combine that
        // superscript in with the primes.
        if (fetch().text == "^") {
          primes.addOne(handleSupSubscript("superscript"))
        }
        // Put everything into an ordgroup as the superscript
        superscript = Nullable(ParseNodeOrdgroup(mode = mode, body = primes.toArray))
      } else if (UnicodeSupOrSub.uSubsAndSups.contains(lex.text)) {
        // A Unicode subscript or superscript character.
        // We treat these similarly to the unicode-math package.
        // So we render a string of Unicode (sub|super)scripts the
        // same as a (sub|super)script of regular characters.
        val isSub        = UnicodeSupOrSub.unicodeSubRegEx.findFirstIn(lex.text).isDefined
        val subsupTokens = mutable.ArrayBuffer.empty[Token]
        subsupTokens.addOne(new Token(UnicodeSupOrSub.uSubsAndSups(lex.text)))
        consume()
        // Continue fetching tokens to fill out the string.
        var innerContinue = true
        while (innerContinue) {
          val tokenText = fetch().text
          if (!UnicodeSupOrSub.uSubsAndSups.contains(tokenText)) {
            innerContinue = false
          } else if (UnicodeSupOrSub.unicodeSubRegEx.findFirstIn(tokenText).isDefined != isSub) {
            innerContinue = false
          } else {
            subsupTokens.prepend(new Token(UnicodeSupOrSub.uSubsAndSups(tokenText)))
            consume()
          }
        }
        // Now create a (sub|super)script.
        val parsedBody = subparse(subsupTokens.toArray)
        if (isSub) {
          subscript = Nullable(ParseNodeOrdgroup(mode = Mode.Math, body = parsedBody))
        } else {
          superscript = Nullable(ParseNodeOrdgroup(mode = Mode.Math, body = parsedBody))
        }
      } else {
        // If it wasn't ^, _, or ', stop parsing super/subscripts
        continue = false
      }
    }

    // Base must be set if superscript or subscript are set per logic above,
    // but need to check here for type check to pass.
    if (superscript.isDefined || subscript.isDefined) {
      // If we got either a superscript or subscript, create a supsub
      val supsub: AnyParseNode = ParseNodeSupsub(
        mode = mode,
        base = base,
        sup = superscript,
        sub = subscript
      )
      break(Nullable(supsub): Nullable[AnyParseNode])
    } else {
      // Otherwise return the original body
      break(base: Nullable[AnyParseNode])
    }
  }

  /** Parses an entire function, including its base and all of its arguments.
    */
  def parseFunction(
    breakOnTokenText: Nullable[String] = Nullable.Null,
    name:             Nullable[String] = Nullable.Null // For determining its context
  ): Nullable[AnyParseNode] = boundary {
    val token       = fetch()
    val func        = token.text
    val funcDataOpt = FunctionDef._functions.get(func)
    if (funcDataOpt.isEmpty) {
      break(Nullable.Null)
    }
    val funcData = funcDataOpt.get
    consume() // consume command token

    if (name.isDefined && name.get != "atom" && !funcData.allowedInArgument) {
      throw new ParseError("Got function '" + func + "' with no arguments" +
                             (if (name.isDefined) " as " + name.get else ""),
                           Nullable(token)
      )
    } else if (mode == Mode.Text && !funcData.allowedInText) {
      throw new ParseError("Can't use function '" + func + "' in text mode", Nullable(token))
    } else if (mode == Mode.Math && !funcData.allowedInMath) {
      throw new ParseError("Can't use function '" + func + "' in math mode", Nullable(token))
    }

    val parsed = parseArguments(func, funcData)
    Nullable(callFunction(func, parsed._1, parsed._2, Nullable(token), breakOnTokenText))
  }

  /** Call a function handler with a suitable context and arguments.
    */
  def callFunction(
    name:             String,
    args:             Array[AnyParseNode],
    optArgs:          Array[Nullable[AnyParseNode]],
    token:            Nullable[Token] = Nullable.Null,
    breakOnTokenText: Nullable[String] = Nullable.Null
  ): AnyParseNode = {
    val breakToken: Nullable[BreakToken] = breakOnTokenText.flatMap { txt =>
      BreakToken.values.find(_.value == txt).map(v => Nullable(v)).getOrElse(Nullable.Null)
    }
    val context = FunctionContext(
      funcName = name,
      parser = this,
      token = token,
      breakOnTokenText = breakToken
    )
    val func = FunctionDef._functions.get(name)
    if (func.isDefined && func.get.handler.isDefined) {
      func.get.handler.get(context, args, optArgs)
    } else {
      throw new ParseError(s"No function handler for $name")
    }
  }

  /** Parses the arguments of a function or environment
    */
  def parseArguments(
    func:     String, // Should look like "\name" or "\begin{name}".
    funcData: FunctionSpec
  ): (Array[AnyParseNode], Array[Nullable[AnyParseNode]]) = boundary {
    val totalArgs = funcData.numArgs + funcData.numOptionalArgs
    if (totalArgs == 0) {
      break((Array.empty[AnyParseNode], Array.empty[Nullable[AnyParseNode]]))
    }

    val args    = mutable.ArrayBuffer.empty[AnyParseNode]
    val optArgs = mutable.ArrayBuffer.empty[Nullable[AnyParseNode]]

    var i = 0
    while (i < totalArgs) {
      var argType: Nullable[String] =
        if (funcData.argTypes.isDefined && i < funcData.argTypes.get.length) {
          Nullable(funcData.argTypes.get(i).value)
        } else {
          Nullable.Null
        }
      val isOptional = i < funcData.numOptionalArgs

      if (
        (funcData.primitive && argType.isEmpty) ||
        // \sqrt expands into primitive if optional argument doesn't exist
        (funcData.nodeType == "sqrt" && i == 1 && (optArgs.isEmpty || optArgs(0).isEmpty))
      ) {
        argType = Nullable("primitive")
      }

      val arg = parseGroupOfType(s"argument to '$func'", argType, isOptional)
      if (isOptional) {
        optArgs.addOne(arg)
      } else if (arg.isDefined) {
        args.addOne(arg.get)
      } else { // should be unreachable
        throw new ParseError("Null argument, please report this as a bug")
      }
      i += 1
    }

    (args.toArray, optArgs.toArray)
  }

  /** Overload for EnvSpec. */
  def parseArguments(
    func:    String,
    envData: EnvSpec
  ): (Array[AnyParseNode], Array[Nullable[AnyParseNode]]) = boundary {
    // Convert EnvSpec to something we can use
    val totalArgs = envData.numArgs + envData.numOptionalArgs
    if (totalArgs == 0) {
      break((Array.empty[AnyParseNode], Array.empty[Nullable[AnyParseNode]]))
    }

    val args    = mutable.ArrayBuffer.empty[AnyParseNode]
    val optArgs = mutable.ArrayBuffer.empty[Nullable[AnyParseNode]]

    var i = 0
    while (i < totalArgs) {
      val argType: Nullable[String] =
        if (envData.argTypes.isDefined && i < envData.argTypes.get.length) {
          Nullable(envData.argTypes.get(i).value)
        } else {
          Nullable.Null
        }
      val isOptional = i < envData.numOptionalArgs

      val arg = parseGroupOfType(s"argument to '$func'", argType, isOptional)
      if (isOptional) {
        optArgs.addOne(arg)
      } else if (arg.isDefined) {
        args.addOne(arg.get)
      } else {
        throw new ParseError("Null argument, please report this as a bug")
      }
      i += 1
    }

    (args.toArray, optArgs.toArray)
  }

  /** Parses a group when the mode is changing.
    */
  def parseGroupOfType(
    name:     String,
    argType:  Nullable[String],
    optional: Boolean
  ): Nullable[AnyParseNode] = {
    val tp = argType.getOrElse("")
    tp match {
      case "color" =>
        val r = parseColorGroup(optional)
        if (r.isDefined) Nullable(r.get) else Nullable.Null
      case "size" =>
        val r = parseSizeGroup(optional)
        if (r.isDefined) Nullable(r.get) else Nullable.Null
      case "url" =>
        val r = parseUrlGroup(optional)
        if (r.isDefined) Nullable(r.get) else Nullable.Null
      case "math" | "text" =>
        val m = if (tp == "math") Mode.Math else Mode.Text
        val r = parseArgumentGroup(optional, Nullable(m))
        if (r.isDefined) Nullable(r.get) else Nullable.Null
      case "hbox" =>
        // hbox argument type wraps the argument in the equivalent of
        // \hbox, which is like \text but switching to \textstyle size.
        val group = parseArgumentGroup(optional, Nullable(Mode.Text))
        if (group.isDefined) {
          Nullable(
            ParseNodeStyling(
              mode = group.get.mode,
              body = Array(group.get),
              style = StyleStr.TextStyle // simulate \textstyle
            )
          )
        } else {
          Nullable.Null
        }
      case "raw" =>
        val token = parseStringGroup("raw", optional)
        if (token.isDefined) {
          Nullable(
            ParseNodeRaw(
              mode = Mode.Text,
              string = token.get.text
            )
          )
        } else {
          Nullable.Null
        }
      case "primitive" =>
        if (optional) {
          throw new ParseError("A primitive argument cannot be optional")
        }
        val group = parseGroup(name)
        if (group.isEmpty) {
          throw new ParseError("Expected group as " + name, Nullable(fetch()))
        }
        group
      case "original" | "" =>
        val r = parseArgumentGroup(optional)
        if (r.isDefined) Nullable(r.get) else Nullable.Null
      case _ =>
        throw new ParseError("Unknown group type as " + name, Nullable(fetch()))
    }
  }

  /** Discard any space tokens, fetching the next non-space token.
    */
  def consumeSpaces(): Unit =
    while (fetch().text == " ")
      consume()

  /** Parses a group, essentially returning the string formed by the brace-enclosed tokens plus some position information.
    */
  def parseStringGroup(
    modeName: String, // Used to describe the mode in error messages.
    optional: Boolean
  ): Nullable[Token] = boundary {
    val argToken = gullet.scanArgument(optional)
    if (argToken.isEmpty) {
      break(Nullable.Null)
    }
    var str     = ""
    var nextTok = fetch()
    while (nextTok.text != "EOF") {
      str += nextTok.text
      consume()
      nextTok = fetch()
    }
    consume() // consume the end of the argument
    val result = argToken.get
    result.text = str
    Nullable(result)
  }

  /** Parses a regex-delimited group: the largest sequence of tokens whose concatenated strings match `regex`. Returns the string formed by the tokens plus some position information.
    */
  def parseRegexGroup(
    regex:    scala.util.matching.Regex,
    modeName: String // Used to describe the mode in error messages.
  ): Token = {
    val firstToken = fetch()
    var lastToken  = firstToken
    var str        = ""
    var nextTok    = fetch()
    while (nextTok.text != "EOF" && regex.findFirstIn(str + nextTok.text).isDefined) {
      lastToken = nextTok
      str += lastToken.text
      consume()
      nextTok = fetch()
    }
    if (str == "") {
      throw new ParseError("Invalid " + modeName + ": '" + firstToken.text + "'", Nullable(firstToken))
    }
    firstToken.range(lastToken, str)
  }

  /** Parses a color description.
    */
  def parseColorGroup(optional: Boolean): Nullable[ParseNodeColorToken] = boundary {
    val res = parseStringGroup("color", optional)
    if (res.isEmpty) {
      break(Nullable.Null)
    }
    val colorRegex = "^(#[a-fA-F0-9]{3,4}|#[a-fA-F0-9]{6}|#[a-fA-F0-9]{8}|[a-fA-F0-9]{6}|[a-zA-Z]+)$".r
    val matchOpt   = colorRegex.findFirstMatchIn(res.get.text)
    if (matchOpt.isEmpty) {
      throw new ParseError("Invalid color: '" + res.get.text + "'", Nullable(res.get))
    }
    var color = matchOpt.get.group(0)
    if ("^[0-9a-fA-F]{6}$".r.findFirstIn(color).isDefined) {
      // We allow a 6-digit HTML color spec without a leading "#".
      // This follows the xcolor package's HTML color model.
      // Predefined color names are all missed by this RegEx pattern.
      color = "#" + color
    }

    Nullable(
      ParseNodeColorToken(
        mode = mode,
        color = color
      )
    )
  }

  /** Parses a size specification, consisting of magnitude and unit.
    */
  def parseSizeGroup(optional: Boolean): Nullable[ParseNodeSize] = boundary {
    var res: Nullable[Token] = Nullable.Null
    var isBlank = false
    // don't expand before parseStringGroup
    gullet.consumeSpaces()
    if (!optional && gullet.future().text != "{") {
      res = Nullable(parseRegexGroup("^[-+]? *(?:$|\\d+|\\d+\\.\\d*|\\.\\d*) *[a-z]{0,2} *$".r, "size"))
    } else {
      res = parseStringGroup("size", optional)
    }
    if (res.isEmpty) {
      break(Nullable.Null)
    }
    if (!optional && res.get.text.isEmpty) {
      // Because we've tested for what is !optional, this block won't
      // affect \kern, \hspace, etc. It will capture the mandatory arguments
      // to \genfrac and \above.
      res.get.text = "0pt" // Enable \above{}
      isBlank = true // This is here specifically for \genfrac
    }
    val sizeRegex = "([-+]?) *(\\d+(?:\\.\\d*)?|\\.\\d+) *([a-z]{2})".r
    val matchOpt  = sizeRegex.findFirstMatchIn(res.get.text)
    if (matchOpt.isEmpty) {
      throw new ParseError("Invalid size: '" + res.get.text + "'", Nullable(res.get))
    }
    val m      = matchOpt.get
    val number = (m.group(1) + m.group(2)).toDouble // sign + magnitude, cast to number
    val unit   = m.group(3)
    val data   = Measurement(number, unit)
    if (!Units.validUnit(data)) {
      throw new ParseError("Invalid unit: '" + data.unit + "'", Nullable(res.get))
    }

    break(
      Nullable(
        ParseNodeSize(
          mode = mode,
          value = data,
          isBlank = isBlank
        )
      )
    )
  }

  /** Parses an URL, checking escaped letters and allowed protocols, and setting the catcode of % as an active character (as in \hyperref).
    */
  def parseUrlGroup(optional: Boolean): Nullable[ParseNodeUrl] = boundary {
    gullet.lexer.setCatcode("%", 13) // active character
    gullet.lexer.setCatcode("~", 12) // other character
    val res = parseStringGroup("url", optional)
    gullet.lexer.setCatcode("%", 14) // comment character
    gullet.lexer.setCatcode("~", 13) // active character
    if (res.isEmpty) {
      break(Nullable.Null)
    }
    // hyperref package allows backslashes alone in href, but doesn't
    // generate valid links in such cases; we interpret this as
    // "undefined" behaviour, and keep them as-is. Some browser will
    // replace backslashes with forward slashes.
    val url = res.get.text.replaceAll("\\\\([#$%&~_^{}])", "$1")
    Nullable(
      ParseNodeUrl(
        mode = mode,
        url = url
      )
    )
  }

  /** Parses an argument with the mode specified.
    */
  def parseArgumentGroup(
    optional:     Boolean,
    modeOverride: Nullable[Mode] = Nullable.Null
  ): Nullable[ParseNodeOrdgroup] = boundary {
    val argToken = gullet.scanArgument(optional)
    if (argToken.isEmpty) {
      break(Nullable.Null)
    }
    val outerMode = mode
    if (modeOverride.isDefined) { // Switch to specified mode
      switchMode(modeOverride.get)
    }

    gullet.beginGroup()
    val expression = parseExpression(false, Nullable("EOF"))
    // TODO: find an alternative way to denote the end
    expect("EOF") // expect the end of the argument
    gullet.endGroup()
    val result = ParseNodeOrdgroup(
      mode = mode,
      loc = argToken.get.loc,
      body = expression
    )

    if (modeOverride.isDefined) { // Switch mode back
      switchMode(outerMode)
    }
    Nullable(result)
  }

  /** Parses an ordinary group, which is either a single nucleus (like "x") or an expression in braces (like "{x+y}") or an implicit group, a group that starts at the current position, and ends right
    * before a higher explicit group ends, or at EOF.
    */
  def parseGroup(
    name:             String, // For error reporting.
    breakOnTokenText: Nullable[String] = Nullable.Null
  ): Nullable[AnyParseNode] = boundary {
    val firstToken = fetch()
    val text       = firstToken.text

    var result: Nullable[AnyParseNode] = Nullable.Null
    // Try to parse an open brace or \begingroup
    if (text == "{" || text == "\\begingroup") {
      consume()
      val groupEnd = if (text == "{") "}" else "\\endgroup"

      gullet.beginGroup()
      // If we get a brace, parse an expression
      val expression = parseExpression(false, Nullable(groupEnd))
      val lastToken  = fetch()
      expect(groupEnd) // Check that we got a matching closing brace
      gullet.endGroup()
      result = Nullable(
        ParseNodeOrdgroup(
          mode = mode,
          loc = SourceLocation.range(Nullable(firstToken), Nullable(lastToken)),
          body = expression,
          // A group formed by \begingroup...\endgroup is a semi-simple group
          // which doesn't affect spacing in math mode, i.e., is transparent.
          // https://tex.stackexchange.com/questions/1930/when-should-one-
          // use-begingroup-instead-of-bgroup
          semisimple = if (text == "\\begingroup") Nullable(true) else Nullable.Null
        )
      )
    } else {
      // If there exists a function with this name, parse the function.
      // Otherwise, just return a nucleus
      result = parseFunction(breakOnTokenText, Nullable(name))
      if (result.isEmpty) {
        result = parseSymbol()
      }
      if (
        result.isEmpty && text.nonEmpty && text.charAt(0) == '\\' &&
        !MacroExpander.implicitCommands.contains(text)
      ) {
        if (settings.throwOnError) {
          throw new ParseError("Undefined control sequence: " + text, Nullable(firstToken))
        }
        result = Nullable(formatUnsupportedCmd(text))
        consume()
      }
    }
    break(result)
  }

  /** Form ligature-like combinations of characters for text mode. This includes inputs like "--", "---", "``" and "''". The result will simply replace multiple textord nodes with a single character
    * in each value by a single textord node having multiple characters in its value. The representation is still ASCII source. The group will be modified in place.
    */
  def formLigatures(group: mutable.ArrayBuffer[AnyParseNode]): Unit = {
    var n = group.length - 1
    var i = 0
    while (i < n) {
      val a = group(i)
      if (a.nodeType != "textord") {
        i += 1
      } else {
        val aNode = a.asInstanceOf[ParseNodeTextord]
        val v     = aNode.text
        if (i + 1 >= group.length) {
          i += 1
        } else {
          val next = group(i + 1)
          if (next.nodeType != "textord") {
            i += 1
          } else {
            val nextNode = next.asInstanceOf[ParseNodeTextord]
            if (v == "-" && nextNode.text == "-") {
              if (i + 1 < n && i + 2 < group.length) {
                val afterNext = group(i + 2)
                if (afterNext.nodeType == "textord" && afterNext.asInstanceOf[ParseNodeTextord].text == "-") {
                  group.remove(i, 3)
                  group.insert(i,
                               ParseNodeTextord(
                                 mode = Mode.Text,
                                 loc = SourceLocation.range(Nullable(a), Nullable(afterNext)),
                                 text = "---"
                               )
                  )
                  n -= 2
                } else {
                  group.remove(i, 2)
                  group.insert(i,
                               ParseNodeTextord(
                                 mode = Mode.Text,
                                 loc = SourceLocation.range(Nullable(a), Nullable(next)),
                                 text = "--"
                               )
                  )
                  n -= 1
                }
              } else {
                group.remove(i, 2)
                group.insert(i,
                             ParseNodeTextord(
                               mode = Mode.Text,
                               loc = SourceLocation.range(Nullable(a), Nullable(next)),
                               text = "--"
                             )
                )
                n -= 1
              }
            }
            if ((v == "'" || v == "`") && nextNode.text == v) {
              group.remove(i, 2)
              group.insert(i,
                           ParseNodeTextord(
                             mode = Mode.Text,
                             loc = SourceLocation.range(Nullable(a), Nullable(next)),
                             text = v + v
                           )
              )
              n -= 1
            }
            i += 1
          }
        }
      }
    }
  }

  /** Parse a single symbol out of the string. Here, we handle single character symbols and special functions like \verb.
    */
  def parseSymbol(): Nullable[AnyParseNode] = boundary {
    val nucleus = fetch()
    var text    = nucleus.text

    if ("^\\\\verb[^a-zA-Z]".r.findFirstIn(text).isDefined) {
      consume()
      var arg  = text.substring(5)
      val star = arg.nonEmpty && arg.charAt(0) == '*'
      if (star) {
        arg = arg.substring(1)
      }
      // Lexer's tokenRegex is constructed to always have matching
      // first/last characters.
      if (arg.length < 2 || arg.charAt(0) != arg.charAt(arg.length - 1)) {
        throw new ParseError("\\verb assertion failed --\n                    please report what input caused this bug")
      }
      arg = arg.substring(1, arg.length - 1) // remove first and last char

      val verbNode: AnyParseNode = ParseNodeVerb(
        mode = Mode.Text,
        body = arg,
        star = star
      )
      break(Nullable(verbNode): Nullable[AnyParseNode])
    }
    // At this point, we should have a symbol, possibly with accents.
    // First expand any accented base symbol according to unicodeSymbols.
    if (
      text.nonEmpty && UnicodeSymbols.unicodeSymbols.contains(text.charAt(0).toString) &&
      !Symbols.getSymbol(mode, text.charAt(0).toString).isDefined
    ) {
      // This behavior is not strict (XeTeX-compatible) in math mode.
      if (
        settings.strict.isInstanceOf[StrictSetting.BoolValue] &&
        settings.strict.asInstanceOf[StrictSetting.BoolValue].value &&
        mode == Mode.Math
      ) {
        settings.reportNonstrict(
          "unicodeTextInMathMode",
          s"Accented Unicode text character \"${text.charAt(0)}\" used in " +
            "math mode",
          Nullable(nucleus)
        )
      } else if (settings.strict.isInstanceOf[StrictSetting.StringValue] && mode == Mode.Math) {
        settings.reportNonstrict(
          "unicodeTextInMathMode",
          s"Accented Unicode text character \"${text.charAt(0)}\" used in " +
            "math mode",
          Nullable(nucleus)
        )
      }
      text = UnicodeSymbols.unicodeSymbols(text.charAt(0).toString) + text.substring(1)
    }
    // Strip off any combining characters
    val matchOpt = Lexer.combiningDiacriticalMarksEndRegex.findFirstMatchIn(text)
    if (matchOpt.isDefined) {
      text = text.substring(0, matchOpt.get.start)
      if (text == "i") {
        text = "ı" // dotless i, in math and text mode
      } else if (text == "j") {
        text = "ȷ" // dotless j, in math and text mode
      }
    }
    // Recognize base symbol
    var symbol: Nullable[AnyParseNode] = Nullable.Null
    val symbolInfo = Symbols.getSymbol(mode, text)
    if (symbolInfo.isDefined) {
      val strict   = settings.strict
      val isStrict = strict match {
        case StrictSetting.BoolValue(b)     => b
        case StrictSetting.StringValue(_)   => true
        case StrictSetting.FunctionValue(_) => true
      }
      if (
        isStrict && mode == Mode.Math &&
        Symbols.extraLatin.contains(text.charAt(0))
      ) {
        settings.reportNonstrict(
          "unicodeTextInMathMode",
          s"Latin-1/Unicode text character \"${text.charAt(0)}\" used in " +
            "math mode",
          Nullable(nucleus)
        )
      }
      val group = symbolInfo.get.group
      val loc   = SourceLocation.range(Nullable(nucleus), Nullable.Null)
      val s: SymbolParseNode =
        if (Symbols.ATOMS.contains(group)) {
          // TODO(ts)
          val family = group
          ParseNodeAtom(
            family = family,
            mode = mode,
            loc = loc,
            text = text
          )
        } else {
          // TODO(ts)
          group match {
            case "mathord"      => ParseNodeMathord(mode = mode, loc = loc, text = text)
            case "textord"      => ParseNodeTextord(mode = mode, loc = loc, text = text)
            case "spacing"      => ParseNodeSpacing(mode = mode, loc = loc, text = text)
            case "accent-token" => ParseNodeAccentToken(mode = mode, loc = loc, text = text)
            case "op-token"     => ParseNodeOpToken(mode = mode, loc = loc, text = text)
            case _              => ParseNodeTextord(mode = mode, loc = loc, text = text) // fallback
          }
        }
      // TODO(ts)
      symbol = Nullable(s)
    } else if (text.nonEmpty && text.charAt(0).toInt >= 0x80) { // no symbol for e.g. ^
      val isStrict = settings.strict match {
        case StrictSetting.BoolValue(b)     => b
        case StrictSetting.StringValue(_)   => true
        case StrictSetting.FunctionValue(_) => true
      }
      if (isStrict) {
        if (!UnicodeScripts.supportedCodepoint(text.charAt(0).toInt)) {
          settings.reportNonstrict(
            "unknownSymbol",
            s"Unrecognized Unicode character \"${text.charAt(0)}\"" +
              s" (${text.charAt(0).toInt})",
            Nullable(nucleus)
          )
        } else if (mode == Mode.Math) {
          settings.reportNonstrict("unicodeTextInMathMode", s"Unicode text character \"${text.charAt(0)}\" used in math mode", Nullable(nucleus))
        }
      }
      // All nonmathematical Unicode characters are rendered as if they
      // are in text mode (wrapped in \text) because that's what it
      // takes to render them in LaTeX.  Setting `mode: this.mode` is
      // another natural choice (the user requested math mode), but
      // this makes it more difficult for getCharacterMetrics() to
      // distinguish Unicode characters without metrics and those for
      // which we want to simulate the letter M.
      symbol = Nullable(
        ParseNodeTextord(
          mode = Mode.Text,
          loc = SourceLocation.range(Nullable(nucleus), Nullable.Null),
          text = text
        )
      )
    } else {
      break(Nullable.Null) // EOF, ^, _, {, }, etc.
    }
    consume()
    // Transform combining characters into accents
    if (matchOpt.isDefined) {
      val matchStr = matchOpt.get.matched
      var idx      = 0
      while (idx < matchStr.length) {
        val accent      = matchStr.charAt(idx).toString
        val accentEntry = UnicodeAccents.unicodeAccents.get(accent)
        if (accentEntry.isEmpty) {
          throw new ParseError(s"Unknown accent ' $accent'", Nullable(nucleus))
        }
        val command: Nullable[String] = mode match {
          case Mode.Math =>
            if (accentEntry.get.math.isDefined) accentEntry.get.math
            else Nullable(accentEntry.get.text)
          case Mode.Text =>
            Nullable(accentEntry.get.text)
        }
        if (command.isEmpty) {
          throw new ParseError(s"Accent $accent unsupported in ${mode.value} mode", Nullable(nucleus))
        }
        symbol = Nullable(
          ParseNodeAccent(
            mode = mode,
            loc = SourceLocation.range(Nullable(nucleus), Nullable.Null),
            label = command.get,
            isStretchy = Nullable(false),
            isShifty = Nullable(true),
            // TODO(ts)
            base = symbol.get
          )
        )
        idx += 1
      }
    }
    // TODO(ts)
    break(symbol)
  }
}

object Parser {
  val endOfExpression: Set[String] =
    Set("}", "\\endgroup", "\\end", "\\right", "&")
}
