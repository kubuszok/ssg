/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Color commands: \textcolor and \color.
 *
 * Original source: katex src/functions/color.ts
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
import ssg.katex.tree.MathNode

object ColorFunc {

  private val htmlBuilder: HtmlBuilder = (group, options) => {
    val g        = group.asInstanceOf[ParseNodeColor]
    val opts     = options.asInstanceOf[Options]
    val elements = BuildHTML.buildExpression(
      g.body,
      opts.withColor(g.color),
      isRealGroup = false
    )
    // \color isn't supposed to affect the type of the elements it contains.
    // To accomplish this, we wrap the results in a fragment, so the inner
    // elements will be able to directly interact with their neighbors. For
    // example, `\color{red}{2 +} 3` has the same spacing as `2 + 3`
    BuildCommon.makeFragment(elements)
  }

  private val mathmlBuilder: MathMLBuilder = (group, options) => {
    val g     = group.asInstanceOf[ParseNodeColor]
    val opts  = options.asInstanceOf[Options]
    val inner = BuildMathML.buildExpression(g.body, opts.withColor(g.color))
    val node  = new MathNode("mstyle", inner)
    node.setAttribute("mathcolor", g.color)
    node
  }

  def register(): Unit = {
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "color",
        names = Array("\\textcolor"),
        props = FunctionPropSpec(
          numArgs = 2,
          allowedInText = true,
          argTypes = Nullable(Array(ArgType.Color, ArgType.Original))
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          val color  = ParseNode.assertNodeType(Nullable(args(0)), "color-token").asInstanceOf[ParseNodeColorToken].color
          val body   = args(1)
          ParseNodeColor(
            mode = parser.mode,
            color = color,
            body = FunctionDef.ordargument(body)
          )
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "color",
        names = Array("\\color"),
        props = FunctionPropSpec(
          numArgs = 1,
          allowedInText = true,
          argTypes = Nullable(Array(ArgType.Color))
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          val color  = ParseNode.assertNodeType(Nullable(args(0)), "color-token").asInstanceOf[ParseNodeColorToken].color

          // Set macro \current@color in current namespace to store the current
          // color, mimicking the behavior of color.sty.
          // This is currently used just to correctly color a \right
          // that follows a \color command.
          parser.gullet.macros.set("\\current@color", Nullable(MacroDefinition.StringDef(color)))

          // Parse out the implicit body that should be colored.
          val body = parser.parseExpression(true, context.breakOnTokenText.map(_.value))

          ParseNodeColor(
            mode = parser.mode,
            color = color,
            body = body
          )
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )
  }
}
