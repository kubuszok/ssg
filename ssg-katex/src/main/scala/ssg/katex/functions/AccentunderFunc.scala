/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Under-accent commands: \underleftarrow, \underrightarrow, etc.
 *
 * Original source: katex src/functions/accentunder.ts
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
import ssg.katex.build.{BuildCommon, BuildHTML, BuildMathML, Stretchy, VListChild, VListElem, VListKern, VListParam}
import ssg.katex.parse._
import ssg.katex.tree.MathNode

object AccentunderFunc {

  def register(): Unit = {
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "accentUnder",
      names = Array(
        "\\underleftarrow", "\\underrightarrow", "\\underleftrightarrow",
        "\\undergroup", "\\underlinesegment", "\\utilde"
      ),
      props = FunctionPropSpec(numArgs = 1),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        val base = args(0)
        ParseNodeAccentUnder(
          mode = parser.mode,
          label = context.funcName,
          base = base
        )
      }),
      htmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeAccentUnder]
        val opts = options.asInstanceOf[Options]
        // Treat under accents much like underlines.
        val innerGroup = BuildHTML.buildGroup(Nullable(g.base), opts)

        val accentBody = Stretchy.stretchySvg(g, opts)
        val kern = if (g.label == "\\utilde") 0.12 else 0.0

        // Generate the vlist, with the appropriate kerns
        val vlist = BuildCommon.makeVList(VListParam.Positioned(
          positionType = "top",
          positionData = innerGroup.height,
          children = Array(
            VListElem(elem = accentBody, wrapperClasses = Array("svg-align")),
            VListKern(kern),
            VListElem(elem = innerGroup)
          )
        ), opts)

        BuildCommon.makeSpan(ArrayBuffer("mord", "accentunder"), ArrayBuffer(vlist), Nullable(opts))
      }),
      mathmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeAccentUnder]
        val opts = options.asInstanceOf[Options]
        val accentNode = Stretchy.stretchyMathML(g.label)
        val node = new MathNode(
          "munder",
          ArrayBuffer(BuildMathML.buildGroup(g.base, opts), accentNode)
        )
        node.setAttribute("accentunder", "true")
        node
      })
    ))
  }
}
