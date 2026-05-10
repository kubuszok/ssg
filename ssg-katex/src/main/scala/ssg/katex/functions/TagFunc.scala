/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Tag builders — no handler, just MathML builder.
 *
 * Original source: katex src/functions/tag.ts
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
import ssg.katex.build.BuildMathML
import ssg.katex.parse._
import ssg.katex.tree.MathNode

object TagFunc {

  private def pad(): MathNode = {
    val padNode = new MathNode("mtd", ArrayBuffer.empty)
    padNode.setAttribute("width", "50%")
    padNode
  }

  def register(): Unit =
    FunctionDef.defineFunctionBuilders(
      nodeType = "tag",
      mathmlBuilder = Nullable((group, options) => {
        val g     = group.asInstanceOf[ParseNodeTag]
        val opts  = options.asInstanceOf[Options]
        val table = new MathNode(
          "mtable",
          ArrayBuffer(
            new MathNode(
              "mtr",
              ArrayBuffer(
                pad(),
                new MathNode("mtd",
                             ArrayBuffer(
                               BuildMathML.buildExpressionRow(g.body, opts)
                             )
                ),
                pad(),
                new MathNode("mtd",
                             ArrayBuffer(
                               BuildMathML.buildExpressionRow(g.tag, opts)
                             )
                )
              )
            )
          )
        )
        table.setAttribute("width", "100%")
        table

        // TODO: Left-aligned tags.
        // Currently, the group and options passed here do not contain
        // enough info to set tag alignment. `leqno` is in Settings but it is
        // not passed to Options. On the HTML side, leqno is
        // set by a CSS class applied in buildTree.js. That would have worked
        // in MathML if browsers supported <mlabeledtr>. Since they don't, we
        // need to rewrite the way this function is called.
      })
    )
}
