/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * \mathchoice — choose different content for display/text/script/scriptscript.
 *
 * Original source: katex src/functions/mathchoice.ts
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

object MathchoiceFunc {

  private def chooseMathStyle(group: ParseNodeMathchoice, options: Options): Array[AnyParseNode] =
    options.style.size match {
      case s if s == Style.DISPLAY.size      => group.display
      case s if s == Style.TEXT.size         => group.text
      case s if s == Style.SCRIPT.size       => group.script
      case s if s == Style.SCRIPTSCRIPT.size => group.scriptscript
      case _                                 => group.text
    }

  def register(): Unit =
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "mathchoice",
        names = Array("\\mathchoice"),
        props = FunctionPropSpec(
          numArgs = 4,
          primitive = true
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          ParseNodeMathchoice(
            mode = parser.mode,
            display = FunctionDef.ordargument(args(0)),
            text = FunctionDef.ordargument(args(1)),
            script = FunctionDef.ordargument(args(2)),
            scriptscript = FunctionDef.ordargument(args(3))
          )
        },
        htmlBuilder = Nullable { (group, options) =>
          val g        = group.asInstanceOf[ParseNodeMathchoice]
          val opts     = options.asInstanceOf[Options]
          val body     = chooseMathStyle(g, opts)
          val elements = BuildHTML.buildExpression(body, opts, isRealGroup = false)
          BuildCommon.makeFragment(elements)
        },
        mathmlBuilder = Nullable { (group, options) =>
          val g    = group.asInstanceOf[ParseNodeMathchoice]
          val opts = options.asInstanceOf[Options]
          val body = chooseMathStyle(g, opts)
          BuildMathML.buildExpressionRow(body, opts)
        }
      )
    )
}
