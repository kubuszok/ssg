/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * \pmb — simulation of bold font using CSS text-shadow.
 *
 * Original source: katex src/functions/pmb.ts
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
import ssg.katex.build.{ BuildCommon, BuildHTML, BuildMathML }
import ssg.katex.parse._
import ssg.katex.tree.MathNode

object PmbFunc {

  // \pmb is a simulation of bold font.
  // The version of \pmb in ambsy.sty works by typesetting three copies
  // with small offsets. We use CSS text-shadow.
  // It's a hack. Not as good as a real bold font. Better than nothing.

  def register(): Unit =
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "pmb",
        names = Array("\\pmb"),
        props = FunctionPropSpec(
          numArgs = 1,
          allowedInText = true
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          ParseNodePmb(
            mode = parser.mode,
            mclass = MclassFunc.binrelClass(args(0)),
            body = FunctionDef.ordargument(args(0))
          )
        },
        htmlBuilder = Nullable { (group, options) =>
          val g        = group.asInstanceOf[ParseNodePmb]
          val opts     = options.asInstanceOf[Options]
          val elements = BuildHTML.buildExpression(g.body, opts, isRealGroup = true)
          val node     = BuildCommon.makeSpan(ArrayBuffer(g.mclass), elements, Nullable(opts))
          node.style = node.style.copy(textShadow = Nullable("0.02em 0.01em 0.04px"))
          node
        },
        mathmlBuilder = Nullable { (group, options) =>
          val g     = group.asInstanceOf[ParseNodePmb]
          val opts  = options.asInstanceOf[Options]
          val inner = BuildMathML.buildExpression(g.body, opts)
          // Wrap with an <mstyle> element.
          val node = new MathNode("mstyle", inner)
          node.setAttribute("style", "text-shadow: 0.02em 0.01em 0.04px")
          node
        }
      )
    )
}
