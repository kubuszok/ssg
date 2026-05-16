/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * \hbox — provided for compatibility with LaTeX \vcenter.
 * In LaTeX, \vcenter can act only on a box.
 * This function by itself doesn't do anything but prevent a soft line break.
 *
 * Original source: katex src/functions/hbox.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package functions

import lowlevel.Nullable
import ssg.katex.build.{ BuildCommon, BuildHTML, BuildMathML }
import ssg.katex.parse._
import ssg.katex.tree.MathNode

object HboxFunc {

  def register(): Unit =
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "hbox",
        names = Array("\\hbox"),
        props = FunctionPropSpec(
          numArgs = 1,
          argTypes = Nullable(Array(ArgType.TextMode)),
          allowedInText = true,
          primitive = true
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          ParseNodeHbox(
            mode = parser.mode,
            body = FunctionDef.ordargument(args(0))
          )
        },
        htmlBuilder = Nullable { (group, options) =>
          val g        = group.asInstanceOf[ParseNodeHbox]
          val opts     = options.asInstanceOf[Options]
          val elements = BuildHTML.buildExpression(g.body, opts, isRealGroup = false)
          BuildCommon.makeFragment(elements)
        },
        mathmlBuilder = Nullable { (group, options) =>
          val g    = group.asInstanceOf[ParseNodeHbox]
          val opts = options.asInstanceOf[Options]
          new MathNode(
            "mrow",
            BuildMathML.buildExpression(g.body, opts)
          )
        }
      )
    )
}
