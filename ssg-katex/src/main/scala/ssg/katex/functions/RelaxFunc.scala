/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The \relax function — does nothing, allowed everywhere.
 *
 * Original source: katex src/functions/relax.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package functions

import ssg.commons.Nullable
import ssg.katex.parse._

object RelaxFunc {

  def register(): Unit = {
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "internal",
      names = Array("\\relax"),
      props = FunctionPropSpec(
        numArgs = 0,
        allowedInText = true,
        allowedInArgument = true
      ),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        ParseNodeInternal(mode = parser.mode)
      })
    ))
  }
}
