/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Horizontal spacing commands: \kern, \mkern, \hskip, \mskip.
 *
 * Original source: katex src/functions/kern.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package functions

import ssg.commons.Nullable
import ssg.katex.StrictSetting
import ssg.katex.build.BuildCommon
import ssg.katex.data.Units
import ssg.katex.parse._
import ssg.katex.tree.SpaceNode

object KernFunc {

  def register(): Unit =
    // TODO: \hskip and \mskip should support plus and minus in lengths
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "kern",
        names = Array("\\kern", "\\mkern", "\\hskip", "\\mskip"),
        props = FunctionPropSpec(
          numArgs = 1,
          argTypes = Nullable(Array(ArgType.Size)),
          primitive = true,
          allowedInText = true
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          val size   = ParseNode.assertNodeType(Nullable(args(0)), "size").asInstanceOf[ParseNodeSize]
          if (parser.settings.strict != StrictSetting.BoolValue(false)) {
            val mathFunction = context.funcName.charAt(1) == 'm' // \mkern, \mskip
            val muUnit       = size.value.unit == "mu"
            if (mathFunction) {
              if (!muUnit) {
                parser.settings.reportNonstrict("mathVsTextUnits",
                                                s"LaTeX's ${context.funcName} supports only mu units, " +
                                                  s"not ${size.value.unit} units"
                )
              }
              if (parser.mode != Mode.Math) {
                parser.settings.reportNonstrict("mathVsTextUnits", s"LaTeX's ${context.funcName} works only in math mode")
              }
            } else { // !mathFunction
              if (muUnit) {
                parser.settings.reportNonstrict("mathVsTextUnits", s"LaTeX's ${context.funcName} doesn't support mu units")
              }
            }
          }
          ParseNodeKern(
            mode = parser.mode,
            dimension = size.value
          )
        },
        htmlBuilder = Nullable { (group, options) =>
          val g    = group.asInstanceOf[ParseNodeKern]
          val opts = options.asInstanceOf[Options]
          BuildCommon.makeGlue(g.dimension, opts)
        },
        mathmlBuilder = Nullable { (group, options) =>
          val g         = group.asInstanceOf[ParseNodeKern]
          val opts      = options.asInstanceOf[Options]
          val dimension = Units.calculateSize(g.dimension, opts)
          new SpaceNode(dimension)
        }
      )
    )
}
