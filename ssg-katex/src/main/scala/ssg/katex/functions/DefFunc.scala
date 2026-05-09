/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Macro definition commands: \global, \long, \def, \gdef, \edef, \xdef,
 * \let, \futurelet.
 *
 * Original source: katex src/functions/def.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package functions

import scala.collection.mutable.ArrayBuffer

import ssg.commons.Nullable
import ssg.katex.ParseError
import ssg.katex.parse._

object DefFunc {

  private val globalMap: Map[String, String] = Map(
    "\\global" -> "\\global",
    "\\long" -> "\\\\globallong",
    "\\\\globallong" -> "\\\\globallong",
    "\\def" -> "\\gdef",
    "\\gdef" -> "\\gdef",
    "\\edef" -> "\\xdef",
    "\\xdef" -> "\\xdef",
    "\\let" -> "\\\\globallet",
    "\\futurelet" -> "\\\\globalfuture"
  )

  private def checkControlSequence(tok: Token): String = {
    val name = tok.text
    if ("""^(?:[\\{}$&#^_]|EOF)$""".r.findFirstIn(name).isDefined) {
      throw new ParseError("Expected a control sequence", Nullable(tok))
    }
    name
  }

  private def getRHS(parser: Parser): Token = {
    var tok = parser.gullet.popToken()
    if (tok.text == "=") { // consume optional equals
      tok = parser.gullet.popToken()
      if (tok.text == " ") { // consume one optional space
        tok = parser.gullet.popToken()
      }
    }
    tok
  }

  private def letCommand(parser: Parser, name: String, tok: Token, global: Boolean): Unit = {
    var macroDef = parser.gullet.macros.get(tok.text)
    if (macroDef.isEmpty) {
      // don't expand it later even if a macro with the same name is defined
      // e.g., \let\foo=\frac \def\frac{\relax} \frac12
      tok.noexpand = Nullable(true)
      macroDef = Nullable(MacroDefinition.ExpansionDef(MacroExpansion(
        tokens = Array(tok),
        numArgs = 0,
        // reproduce the same behavior in expansion
        unexpandable = !parser.gullet.isExpandable(tok.text)
      )))
    }
    parser.gullet.macros.set(name, macroDef, global)
  }

  def register(): Unit = {
    // <assignment> -> <non-macro assignment>|<macro assignment>
    // <non-macro assignment> -> <simple assignment>|\global<non-macro assignment>
    // <macro assignment> -> <definition>|<prefix><macro assignment>
    // <prefix> -> \global|\long|\outer
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "internal",
      names = Array(
        "\\global", "\\long",
        "\\\\globallong" // can't be entered directly
      ),
      props = FunctionPropSpec(
        numArgs = 0,
        allowedInText = true
      ),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        parser.consumeSpaces()
        val token = parser.fetch()
        if (globalMap.contains(token.text)) {
          // KaTeX doesn't have \par, so ignore \long
          if (context.funcName == "\\global" || context.funcName == "\\\\globallong") {
            token.text = globalMap(token.text)
          }
          ParseNode.assertNodeType(parser.parseFunction(), "internal")
        } else {
          throw new ParseError(s"Invalid token after macro prefix", Nullable(token))
        }
      })
    ))

    // Basic support for macro definitions: \def, \gdef, \edef, \xdef
    // <definition> -> <def><control sequence><definition text>
    // <def> -> \def|\gdef|\edef|\xdef
    // <definition text> -> <parameter text><left brace><balanced text><right brace>
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "internal",
      names = Array("\\def", "\\gdef", "\\edef", "\\xdef"),
      props = FunctionPropSpec(
        numArgs = 0,
        allowedInText = true,
        primitive = true
      ),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        var tok = parser.gullet.popToken()
        val name = tok.text
        if ("""^(?:[\\{}$&#^_]|EOF)$""".r.findFirstIn(name).isDefined) {
          throw new ParseError("Expected a control sequence", Nullable(tok))
        }

        var numArgs = 0
        var insert: Nullable[Token] = Nullable.Null
        val delimiters = ArrayBuffer(ArrayBuffer.empty[String])
        // <parameter text> contains no braces
        while (parser.gullet.future().text != "{") {
          tok = parser.gullet.popToken()
          if (tok.text == "#") {
            // If the very last character of the <parameter text> is #, so that
            // this # is immediately followed by {, TeX will behave as if the {
            // had been inserted at the right end of both the parameter text
            // and the replacement text.
            if (parser.gullet.future().text == "{") {
              insert = Nullable(parser.gullet.future())
              delimiters(numArgs) += "{"
              // break
            } else {
              // A parameter, the first appearance of # must be followed by 1,
              // the next by 2, and so on; up to nine #'s are allowed
              tok = parser.gullet.popToken()
              if (!"""^[1-9]$""".r.findFirstIn(tok.text).isDefined) {
                throw new ParseError(s"""Invalid argument number "${tok.text}"""")
              }
              if (tok.text.toInt != numArgs + 1) {
                throw new ParseError(
                  s"""Argument number "${tok.text}" out of order""")
              }
              numArgs += 1
              delimiters += ArrayBuffer.empty[String]
            }
          } else if (tok.text == "EOF") {
            throw new ParseError("Expected a macro definition")
          } else {
            delimiters(numArgs) += tok.text
          }
          // If insert was set, break out of the loop
          if (insert.isDefined) {
            // break — we set insert above and need to exit the while
            // Scala boundary/break here would require restructuring the loop.
            // Instead we just check after the loop.
          }
        }
        // replacement text, enclosed in '{' and '}' and properly nested
        var tokens = parser.gullet.consumeArg().tokens
        insert.foreach { ins =>
          tokens = Array(ins) ++ tokens
        }

        if (context.funcName == "\\edef" || context.funcName == "\\xdef") {
          tokens = parser.gullet.expandTokens(tokens)
          tokens = tokens.reverse // to fit in with stack order
        }
        // Final arg is the expansion of the macro
        val delimsArray = delimiters.map(_.toArray).toArray
        parser.gullet.macros.set(name, Nullable(MacroDefinition.ExpansionDef(MacroExpansion(
          tokens = tokens,
          numArgs = numArgs,
          delimiters = Nullable(delimsArray)
        ))), context.funcName == globalMap.getOrElse(context.funcName, ""))

        ParseNodeInternal(mode = parser.mode)
      })
    ))

    // <simple assignment> -> <let assignment>
    // <let assignment> -> \futurelet<control sequence><token><token>
    //     | \let<control sequence><equals><one optional space><token>
    // <equals> -> <optional spaces>|<optional spaces>=
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "internal",
      names = Array(
        "\\let",
        "\\\\globallet" // can't be entered directly
      ),
      props = FunctionPropSpec(
        numArgs = 0,
        allowedInText = true,
        primitive = true
      ),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        val name = checkControlSequence(parser.gullet.popToken())
        parser.gullet.consumeSpaces()
        val tok = getRHS(parser)
        letCommand(parser, name, tok, context.funcName == "\\\\globallet")
        ParseNodeInternal(mode = parser.mode)
      })
    ))

    // ref: https://www.tug.org/TUGboat/tb09-3/tb22bechtolsheim.pdf
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "internal",
      names = Array(
        "\\futurelet",
        "\\\\globalfuture" // can't be entered directly
      ),
      props = FunctionPropSpec(
        numArgs = 0,
        allowedInText = true,
        primitive = true
      ),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        val name = checkControlSequence(parser.gullet.popToken())
        val middle = parser.gullet.popToken()
        val tok = parser.gullet.popToken()
        letCommand(parser, name, tok, context.funcName == "\\\\globalfuture")
        parser.gullet.pushToken(tok)
        parser.gullet.pushToken(middle)
        ParseNodeInternal(mode = parser.mode)
      })
    ))
  }
}
