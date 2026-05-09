/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Non-mathy text, possibly in a font: \text, \textrm, \textbf, etc.
 *
 * Original source: katex src/functions/text.ts
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
import ssg.katex.build.{BuildCommon, BuildHTML, BuildMathML}
import ssg.katex.parse._

object TextFunc {

  private val textFontFamilies: Map[String, Nullable[String]] = Map(
    "\\text" -> Nullable.Null,
    "\\textrm" -> Nullable("textrm"),
    "\\textsf" -> Nullable("textsf"),
    "\\texttt" -> Nullable("texttt"),
    "\\textnormal" -> Nullable("textrm")
  )

  private val textFontWeights: Map[String, String] = Map(
    "\\textbf" -> "textbf",
    "\\textmd" -> "textmd"
  )

  private val textFontShapes: Map[String, String] = Map(
    "\\textit" -> "textit",
    "\\textup" -> "textup"
  )

  private def optionsWithFont(group: ParseNodeText, options: Options): Options = {
    val font = group.font
    // Checks if the argument is a font family or a font style.
    if (font.isEmpty) {
      options
    } else {
      val f = font.get
      if (textFontFamilies.contains(f)) {
        textFontFamilies(f).fold(options)(ff => options.withTextFontFamily(ff))
      } else if (textFontWeights.contains(f)) {
        options.withTextFontWeight(textFontWeights(f))
      } else if (f == "\\emph") {
        if (options.fontShape == "textit")
          options.withTextFontShape("textup")
        else
          options.withTextFontShape("textit")
      } else {
        options.withTextFontShape(textFontShapes(f))
      }
    }
  }

  def register(): Unit = {
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "text",
      names = Array(
        // Font families
        "\\text", "\\textrm", "\\textsf", "\\texttt", "\\textnormal",
        // Font weights
        "\\textbf", "\\textmd",
        // Font Shapes
        "\\textit", "\\textup", "\\emph"
      ),
      props = FunctionPropSpec(
        numArgs = 1,
        argTypes = Nullable(Array(ArgType.TextMode)),
        allowedInArgument = true,
        allowedInText = true
      ),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        val body = args(0)
        ParseNodeText(
          mode = parser.mode,
          body = FunctionDef.ordargument(body),
          font = Nullable(context.funcName)
        )
      }),
      htmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeText]
        val opts = options.asInstanceOf[Options]
        val newOptions = optionsWithFont(g, opts)
        val inner = BuildHTML.buildExpression(g.body, newOptions, isRealGroup = true)
        BuildCommon.makeSpan(ArrayBuffer("mord", "text"), inner, Nullable(newOptions))
      }),
      mathmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeText]
        val opts = options.asInstanceOf[Options]
        val newOptions = optionsWithFont(g, opts)
        BuildMathML.buildExpressionRow(g.body, newOptions)
      })
    ))
  }
}
