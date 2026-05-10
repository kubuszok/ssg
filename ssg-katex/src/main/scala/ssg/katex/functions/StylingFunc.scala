/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Display style commands: \displaystyle, \textstyle, \scriptstyle, \scriptscriptstyle.
 *
 * Original source: katex src/functions/styling.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package functions

import ssg.commons.Nullable
import ssg.katex.build.BuildMathML
import ssg.katex.parse._
import ssg.katex.tree.MathNode

object StylingFunc {

  private val styleMap: Map[String, Style] = Map(
    "display" -> Style.DISPLAY,
    "text" -> Style.TEXT,
    "script" -> Style.SCRIPT,
    "scriptscript" -> Style.SCRIPTSCRIPT
  )

  def register(): Unit =
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "styling",
        names = Array(
          "\\displaystyle",
          "\\textstyle",
          "\\scriptstyle",
          "\\scriptscriptstyle"
        ),
        props = FunctionPropSpec(
          numArgs = 0,
          allowedInText = true,
          primitive = true
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          // parse out the implicit body
          val body = parser.parseExpression(true, context.breakOnTokenText.map(_.value))

          // TODO: Refactor to avoid duplicating styleMap in multiple places (e.g.
          // here and in buildHTML and de-dupe the enumeration of all the styles).
          // TODO(ts): The names above exactly match the styles.
          val style    = context.funcName.substring(1, context.funcName.length - 5)
          val styleStr = style match {
            case "display"      => StyleStr.Display
            case "text"         => StyleStr.TextStyle
            case "script"       => StyleStr.Script
            case "scriptscript" => StyleStr.ScriptScript
            case _              => StyleStr.TextStyle
          }
          ParseNodeStyling(
            mode = parser.mode,
            // Figure out what style to use by pulling out the style from
            // the function name
            style = styleStr,
            body = body
          )
        },
        htmlBuilder = Nullable { (group, options) =>
          val g    = group.asInstanceOf[ParseNodeStyling]
          val opts = options.asInstanceOf[Options]
          // Style changes are handled in the TeXbook on pg. 442, Rule 3.
          val newStyle   = styleMap(g.style.value)
          val newOptions = opts.havingStyle(newStyle).withFont("")
          SizingFunc.sizingGroup(g.body, newOptions, opts)
        },
        mathmlBuilder = Nullable { (group, options) =>
          val g    = group.asInstanceOf[ParseNodeStyling]
          val opts = options.asInstanceOf[Options]
          // Figure out what style we're changing to.
          val newStyle   = styleMap(g.style.value)
          val newOptions = opts.havingStyle(newStyle)

          val inner = BuildMathML.buildExpression(g.body, newOptions)

          val node = new MathNode("mstyle", inner)

          val styleAttributes = Map(
            "display" -> Array("0", "true"),
            "text" -> Array("0", "false"),
            "script" -> Array("1", "false"),
            "scriptscript" -> Array("2", "false")
          )

          val attr = styleAttributes(g.style.value)

          node.setAttribute("scriptlevel", attr(0))
          node.setAttribute("displaystyle", attr(1))

          node
        }
      )
    )
}
