/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Switching from text mode back to math mode: \( and $
 * Also check for extra closing math delimiters: \) and \]
 *
 * Original source: katex src/functions/math.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package functions

import lowlevel.Nullable
import ssg.katex.ParseError
import ssg.katex.parse._

object MathFunc {

  def register(): Unit = {
    // Switching from text mode back to math mode
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "styling",
        names = Array("\\(", "$"),
        props = FunctionPropSpec(
          numArgs = 0,
          allowedInText = true,
          allowedInMath = false
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser    = context.parser.asInstanceOf[Parser]
          val outerMode = parser.mode
          parser.switchMode(Mode.Math)
          val close = if (context.funcName == "\\(") "\\)" else "$"
          val body  = parser.parseExpression(false, Nullable(close))
          parser.expect(close)
          parser.switchMode(outerMode)
          ParseNodeStyling(
            mode = parser.mode,
            style = StyleStr.TextStyle,
            body = body
          )
        }
      )
    )

    // Check for extra closing math delimiters
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "text", // Doesn't matter what this is.
        names = Array("\\)", "\\]"),
        props = FunctionPropSpec(
          numArgs = 0,
          allowedInText = true,
          allowedInMath = false
        ),
        handler = Nullable((context, args, optArgs) => throw new ParseError(s"Mismatched ${context.funcName}"))
      )
    )
  }
}
