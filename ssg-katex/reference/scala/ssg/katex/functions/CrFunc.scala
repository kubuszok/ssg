/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Row breaks within tabular environments, and line breaks at top level.
 *
 * Original source: katex src/functions/cr.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package functions

import scala.collection.mutable.ArrayBuffer

import lowlevel.Nullable
import ssg.katex.build.BuildCommon
import ssg.katex.data.Units
import ssg.katex.parse._
import ssg.katex.tree.MathNode

object CrFunc {

  def register(): Unit =
    // \DeclareRobustCommand\\{...\@xnewline}
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "cr",
        names = Array("\\\\"),
        props = FunctionPropSpec(
          numArgs = 0,
          numOptionalArgs = 0,
          allowedInText = true
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          val size: Nullable[ParseNodeSize] =
            if (parser.gullet.future().text == "[") parser.parseSizeGroup(true)
            else Nullable.Null
          val newLine = !parser.settings.displayMode ||
            !parser.settings.useStrictBehavior("newLineInDisplayMode",
                                               "In LaTeX, \\\\ or \\newline " +
                                                 "does nothing in display mode"
            )
          val sizeValue: Nullable[data.Measurement] =
            size.map(_.value)
          ParseNodeCr(
            mode = parser.mode,
            newLine = newLine,
            size = sizeValue
          )
        },
        // The following builders are called only at the top level,
        // not within tabular/array environments.
        htmlBuilder = Nullable { (group, options) =>
          val g    = group.asInstanceOf[ParseNodeCr]
          val opts = options.asInstanceOf[Options]
          val span = BuildCommon.makeSpan(ArrayBuffer("mspace"), ArrayBuffer.empty, Nullable(opts))
          if (g.newLine) {
            span.classes += "newline"
            g.size.foreach { s =>
              span.style = span.style.copy(marginTop = Nullable(Units.makeEm(Units.calculateSize(s, opts))))
            }
          }
          span
        },
        mathmlBuilder = Nullable { (group, options) =>
          val g    = group.asInstanceOf[ParseNodeCr]
          val opts = options.asInstanceOf[Options]
          val node = new MathNode("mspace")
          if (g.newLine) {
            node.setAttribute("linebreak", "newline")
            g.size.foreach { s =>
              node.setAttribute("height", Units.makeEm(Units.calculateSize(s, opts)))
            }
          }
          node
        }
      )
    )
}
