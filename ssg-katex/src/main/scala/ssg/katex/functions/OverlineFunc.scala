/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * \overline command.
 *
 * Original source: katex src/functions/overline.ts
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
import ssg.katex.build.{ BuildCommon, BuildHTML, BuildMathML, VListChild, VListElem, VListKern, VListParam }
import ssg.katex.parse._
import ssg.katex.tree.{ MathNode, TextNode }

object OverlineFunc {

  def register(): Unit =
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "overline",
        names = Array("\\overline"),
        props = FunctionPropSpec(numArgs = 1),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          val body   = args(0)
          ParseNodeOverline(mode = parser.mode, body = body)
        },
        htmlBuilder = Nullable { (group, options) =>
          val g    = group.asInstanceOf[ParseNodeOverline]
          val opts = options.asInstanceOf[Options]
          // Overlines are handled in the TeXbook pg 443, Rule 9.

          // Build the inner group in the cramped style.
          val innerGroup = BuildHTML.buildGroup(Nullable(g.body), opts.havingCrampedStyle())

          // Create the line above the body
          val line = BuildCommon.makeLineSpan("overline-line", opts)

          // Generate the vlist, with the appropriate kerns
          val defaultRuleThickness = opts.fontMetrics().defaultRuleThickness
          val vlist                = BuildCommon.makeVList(
            VListParam.FirstBaseline(
              children = Array(
                VListElem(elem = innerGroup),
                VListKern(3 * defaultRuleThickness),
                VListElem(elem = line),
                VListKern(defaultRuleThickness)
              )
            ),
            opts
          )

          BuildCommon.makeSpan(ArrayBuffer("mord", "overline"), ArrayBuffer(vlist), Nullable(opts))
        },
        mathmlBuilder = Nullable { (group, options) =>
          val g        = group.asInstanceOf[ParseNodeOverline]
          val opts     = options.asInstanceOf[Options]
          val operator = new MathNode("mo", ArrayBuffer(new TextNode("‾")))
          operator.setAttribute("stretchy", "true")

          val node = new MathNode("mover", ArrayBuffer(BuildMathML.buildGroup(g.body, opts), operator))
          node.setAttribute("accent", "true")

          node
        }
      )
    )
}
