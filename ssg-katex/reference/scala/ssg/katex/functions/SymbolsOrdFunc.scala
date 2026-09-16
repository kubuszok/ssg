/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * "mathord" and "textord" ParseNodes created in Parser.js from symbol Groups.
 *
 * Original source: katex src/functions/symbolsOrd.ts
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
import ssg.katex.build.{ BuildCommon, BuildMathML }
import ssg.katex.parse._
import ssg.katex.tree.MathNode

object SymbolsOrdFunc {

  private val defaultVariant: Map[String, String] = Map(
    "mi" -> "italic",
    "mn" -> "normal",
    "mtext" -> "normal"
  )

  def register(): Unit = {
    FunctionDef.defineFunctionBuilders(
      nodeType = "mathord",
      htmlBuilder = Nullable { (group, options) =>
        val g    = group.asInstanceOf[ParseNodeMathord]
        val opts = options.asInstanceOf[Options]
        BuildCommon.makeOrd(g, opts, "mathord")
      },
      mathmlBuilder = Nullable { (group, options) =>
        val g    = group.asInstanceOf[ParseNodeMathord]
        val opts = options.asInstanceOf[Options]
        val node = new MathNode("mi", ArrayBuffer(BuildMathML.makeText(g.text, g.mode, Nullable(opts))))

        val variant = BuildMathML.getVariant(g, opts).fold("italic")(_.value)
        if (variant != defaultVariant.getOrElse(node.nodeType, "")) {
          node.setAttribute("mathvariant", variant)
        }
        node
      }
    )

    FunctionDef.defineFunctionBuilders(
      nodeType = "textord",
      htmlBuilder = Nullable { (group, options) =>
        val g    = group.asInstanceOf[ParseNodeTextord]
        val opts = options.asInstanceOf[Options]
        BuildCommon.makeOrd(g, opts, "textord")
      },
      mathmlBuilder = Nullable { (group, options) =>
        val g       = group.asInstanceOf[ParseNodeTextord]
        val opts    = options.asInstanceOf[Options]
        val text    = BuildMathML.makeText(g.text, g.mode, Nullable(opts))
        val variant = BuildMathML.getVariant(g, opts).fold("normal")(_.value)

        val node: MathNode = if (g.mode == Mode.Text) {
          new MathNode("mtext", ArrayBuffer(text))
        } else if ("[0-9]".r.findFirstIn(g.text).isDefined) {
          new MathNode("mn", ArrayBuffer(text))
        } else if (g.text == "\\prime") {
          new MathNode("mo", ArrayBuffer(text))
        } else {
          new MathNode("mi", ArrayBuffer(text))
        }
        if (variant != defaultVariant.getOrElse(node.nodeType, "")) {
          node.setAttribute("mathvariant", variant)
        }

        node
      }
    )
  }
}
