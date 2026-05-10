/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * \underline command.
 *
 * Original source: katex src/functions/underline.ts
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

object UnderlineFunc {

  def register(): Unit =
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "underline",
        names = Array("\\underline"),
        props = FunctionPropSpec(
          numArgs = 1,
          allowedInText = true
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          val body   = args(0)
          ParseNodeUnderline(mode = parser.mode, body = body)
        },
        htmlBuilder = Nullable { (group, options) =>
          val g    = group.asInstanceOf[ParseNodeUnderline]
          val opts = options.asInstanceOf[Options]
          // Underlines are handled in the TeXbook pg 443, Rule 10.
          // Build the inner group.
          val innerGroup = BuildHTML.buildGroup(Nullable(g.body), opts)

          // Create the line to go below the body
          val line = BuildCommon.makeLineSpan("underline-line", opts)

          // Generate the vlist, with the appropriate kerns
          val defaultRuleThickness = opts.fontMetrics().defaultRuleThickness
          val vlist                = BuildCommon.makeVList(
            VListParam.Positioned(
              positionType = "top",
              positionData = innerGroup.height,
              children = Array(
                VListKern(defaultRuleThickness),
                VListElem(elem = line),
                VListKern(3 * defaultRuleThickness),
                VListElem(elem = innerGroup)
              )
            ),
            opts
          )

          BuildCommon.makeSpan(ArrayBuffer("mord", "underline"), ArrayBuffer(vlist), Nullable(opts))
        },
        mathmlBuilder = Nullable { (group, options) =>
          val g        = group.asInstanceOf[ParseNodeUnderline]
          val opts     = options.asInstanceOf[Options]
          val operator = new MathNode("mo", ArrayBuffer(new TextNode("‾")))
          operator.setAttribute("stretchy", "true")

          val node = new MathNode("munder", ArrayBuffer(BuildMathML.buildGroup(g.body, opts), operator))
          node.setAttribute("accentunder", "true")

          node
        }
      )
    )
}
