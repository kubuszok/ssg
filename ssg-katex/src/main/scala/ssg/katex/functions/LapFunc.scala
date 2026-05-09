/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Horizontal overlap functions: \mathllap, \mathrlap, \mathclap.
 *
 * Original source: katex src/functions/lap.ts
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
import ssg.katex.build.{BuildCommon, BuildHTML, BuildMathML}
import ssg.katex.data.Units
import ssg.katex.parse._
import ssg.katex.tree.{HtmlDomNode, MathNode}

object LapFunc {

  def register(): Unit = {
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "lap",
      names = Array("\\mathllap", "\\mathrlap", "\\mathclap"),
      props = FunctionPropSpec(
        numArgs = 1,
        allowedInText = true
      ),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        val body = args(0)
        ParseNodeLap(
          mode = parser.mode,
          alignment = context.funcName.substring(5), // "llap", "rlap", "clap"
          body = body
        )
      }),
      htmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeLap]
        val opts = options.asInstanceOf[Options]
        // mathllap, mathrlap, mathclap
        var inner: HtmlDomNode = BuildCommon.makeSpan() // overwritten below in all branches
        if (g.alignment == "clap") {
          // ref: https://www.math.lsu.edu/~aperlis/publications/mathclap/
          inner = BuildCommon.makeSpan(
            ArrayBuffer.empty, ArrayBuffer(BuildHTML.buildGroup(Nullable(g.body), opts)))
          // wrap, since CSS will center a .clap > .inner > span
          inner = BuildCommon.makeSpan(ArrayBuffer("inner"), ArrayBuffer(inner), Nullable(opts))
        } else {
          inner = BuildCommon.makeSpan(
            ArrayBuffer("inner"), ArrayBuffer(BuildHTML.buildGroup(Nullable(g.body), opts)))
        }
        val fix = BuildCommon.makeSpan(ArrayBuffer("fix"), ArrayBuffer.empty[HtmlDomNode])
        var node = BuildCommon.makeSpan(
          ArrayBuffer(g.alignment), ArrayBuffer(inner, fix), Nullable(opts))

        // At this point, we have correctly set horizontal alignment of the
        // two items involved in the lap.
        // Next, use a strut to set the height of the HTML bounding box.
        // Otherwise, a tall argument may be misplaced.
        // This code resolved issue #1153
        val strut = BuildCommon.makeSpan(ArrayBuffer("strut"))
        strut.style = strut.style.copy(height = Nullable(Units.makeEm(node.height + node.depth)))
        if (node.depth != 0) {
          strut.style = strut.style.copy(verticalAlign = Nullable(Units.makeEm(-node.depth)))
        }
        node.children.prepend(strut)

        // Next, prevent vertical misplacement when next to something tall.
        // This code resolves issue #1234
        node = BuildCommon.makeSpan(ArrayBuffer("thinbox"), ArrayBuffer(node), Nullable(opts))
        BuildCommon.makeSpan(ArrayBuffer("mord", "vbox"), ArrayBuffer(node), Nullable(opts))
      }),
      mathmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeLap]
        val opts = options.asInstanceOf[Options]
        // mathllap, mathrlap, mathclap
        val node = new MathNode(
          "mpadded", ArrayBuffer(BuildMathML.buildGroup(g.body, opts)))

        if (g.alignment != "rlap") {
          val offset = if (g.alignment == "llap") "-1" else "-0.5"
          node.setAttribute("lspace", offset + "width")
        }
        node.setAttribute("width", "0px")

        node
      })
    ))
  }
}
