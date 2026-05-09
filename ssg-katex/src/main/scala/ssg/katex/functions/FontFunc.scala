/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Font commands: \mathrm, \mathbb, \boldsymbol, \rm, etc.
 * TODO(kevinb): implement \\sl and \\sc
 *
 * Original source: katex src/functions/font.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package functions


import ssg.commons.Nullable
import ssg.katex.build.{BuildHTML, BuildMathML}
import ssg.katex.parse._
import ssg.katex.util.{Utils => KatexUtils}

object FontFunc {

  private val htmlBuilder: HtmlBuilder = (group, options) => {
    val g = group.asInstanceOf[ParseNodeFont]
    val opts = options.asInstanceOf[Options]
    val font = g.font
    val newOptions = opts.withFont(font)
    BuildHTML.buildGroup(Nullable(g.body), newOptions)
  }

  private val mathmlBuilder: MathMLBuilder = (group, options) => {
    val g = group.asInstanceOf[ParseNodeFont]
    val opts = options.asInstanceOf[Options]
    val font = g.font
    val newOptions = opts.withFont(font)
    BuildMathML.buildGroup(g.body, newOptions)
  }

  private val fontAliases: Map[String, String] = Map(
    "\\Bbb" -> "\\mathbb",
    "\\bold" -> "\\mathbf",
    "\\frak" -> "\\mathfrak",
    "\\bm" -> "\\boldsymbol"
  )

  def register(): Unit = {
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "font",
      names = Array(
        // styles, except \boldsymbol defined below
        "\\mathrm", "\\mathit", "\\mathbf", "\\mathnormal", "\\mathsfit",
        // families
        "\\mathbb", "\\mathcal", "\\mathfrak", "\\mathscr", "\\mathsf",
        "\\mathtt",
        // aliases, except \bm defined below
        "\\Bbb", "\\bold", "\\frak"
      ),
      props = FunctionPropSpec(
        numArgs = 1,
        allowedInArgument = true
      ),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        val body = FunctionDef.normalizeArgument(args(0))
        var func = context.funcName
        if (fontAliases.contains(func)) {
          func = fontAliases(func)
        }
        ParseNodeFont(
          mode = parser.mode,
          font = func.substring(1),
          body = body
        )
      }),
      htmlBuilder = Nullable(htmlBuilder),
      mathmlBuilder = Nullable(mathmlBuilder)
    ))

    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "mclass",
      names = Array("\\boldsymbol", "\\bm"),
      props = FunctionPropSpec(numArgs = 1),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        val body = args(0)
        // amsbsy.sty's \boldsymbol uses \binrel spacing to inherit the
        // argument's bin|rel|ord status
        ParseNodeMclass(
          mode = parser.mode,
          mclass = MclassFunc.binrelClass(body),
          body = Array(
            ParseNodeFont(
              mode = parser.mode,
              font = "boldsymbol",
              body = body
            )
          ),
          isCharacterBox = KatexUtils.isCharacterBox(body)
        )
      })
    ))

    // Old font changing functions
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "font",
      names = Array("\\rm", "\\sf", "\\tt", "\\bf", "\\it", "\\cal"),
      props = FunctionPropSpec(
        numArgs = 0,
        allowedInText = true
      ),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        val mode = parser.mode
        val body = parser.parseExpression(true, context.breakOnTokenText.map(_.value))
        val style = "math" + context.funcName.substring(1)

        ParseNodeFont(
          mode = mode,
          font = style,
          body = ParseNodeOrdgroup(
            mode = parser.mode,
            body = body
          )
        )
      }),
      htmlBuilder = Nullable(htmlBuilder),
      mathmlBuilder = Nullable(mathmlBuilder)
    ))
  }
}
