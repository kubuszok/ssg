/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * \html@mathml — internal function for dual HTML/MathML rendering.
 *
 * Original source: katex src/functions/htmlmathml.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package functions

import ssg.commons.Nullable
import ssg.katex.build.{ BuildCommon, BuildHTML, BuildMathML }
import ssg.katex.parse._

object HtmlmathmlFunc {

  def register(): Unit =
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "htmlmathml",
        names = Array("\\html@mathml"),
        props = FunctionPropSpec(
          numArgs = 2,
          allowedInArgument = true,
          allowedInText = true
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          ParseNodeHtmlmathml(
            mode = parser.mode,
            html = FunctionDef.ordargument(args(0)),
            mathml = FunctionDef.ordargument(args(1))
          )
        },
        htmlBuilder = Nullable { (group, options) =>
          val g        = group.asInstanceOf[ParseNodeHtmlmathml]
          val opts     = options.asInstanceOf[Options]
          val elements = BuildHTML.buildExpression(
            g.html,
            opts,
            isRealGroup = false
          )
          BuildCommon.makeFragment(elements)
        },
        mathmlBuilder = Nullable { (group, options) =>
          val g    = group.asInstanceOf[ParseNodeHtmlmathml]
          val opts = options.asInstanceOf[Options]
          BuildMathML.buildExpressionRow(g.mathml, opts)
        }
      )
    )
}
