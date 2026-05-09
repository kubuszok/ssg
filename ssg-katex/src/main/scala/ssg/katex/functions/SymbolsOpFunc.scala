/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Operator ParseNodes created in Parser.js from symbol Groups in src/symbols.js.
 *
 * Original source: katex src/functions/symbolsOp.ts
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
import ssg.katex.build.{BuildCommon, BuildMathML}
import ssg.katex.parse._
import ssg.katex.tree.MathNode

object SymbolsOpFunc {

  def register(): Unit = {
    FunctionDef.defineFunctionBuilders(
      nodeType = "atom",
      htmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeAtom]
        val opts = options.asInstanceOf[Options]
        BuildCommon.mathsym(
          g.text, g.mode, opts, ArrayBuffer("m" + g.family))
      }),
      mathmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeAtom]
        val opts = options.asInstanceOf[Options]
        val node = new MathNode(
          "mo", ArrayBuffer(BuildMathML.makeText(g.text, g.mode)))
        if (g.family == "bin") {
          val variant = BuildMathML.getVariant(g, opts)
          if (variant.isDefined && variant.get == FontVariant.BoldItalic) {
            node.setAttribute("mathvariant", variant.get.value)
          }
        } else if (g.family == "punct") {
          node.setAttribute("separator", "true")
        } else if (g.family == "open" || g.family == "close") {
          // Delims built here should not stretch vertically.
          // See delimsizing.js for stretchy delims.
          node.setAttribute("stretchy", "false")
        }
        node
      })
    )
  }
}
