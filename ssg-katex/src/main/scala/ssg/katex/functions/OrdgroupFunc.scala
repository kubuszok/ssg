/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ordgroup builders — no handler, just HTML and MathML builders.
 *
 * Original source: katex src/functions/ordgroup.ts
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
import ssg.katex.parse._

object OrdgroupFunc {

  def register(): Unit = {
    FunctionDef.defineFunctionBuilders(
      nodeType = "ordgroup",
      htmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeOrdgroup]
        val opts = options.asInstanceOf[Options]
        if (g.semisimple.getOrElse(false)) {
          BuildCommon.makeFragment(
            BuildHTML.buildExpression(g.body, opts, isRealGroup = false))
        } else {
          BuildCommon.makeSpan(
            ArrayBuffer("mord"), BuildHTML.buildExpression(g.body, opts, isRealGroup = true), Nullable(opts))
        }
      }),
      mathmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeOrdgroup]
        val opts = options.asInstanceOf[Options]
        BuildMathML.buildExpressionRow(g.body, opts, isOrdgroup = true)
      })
    )
  }
}
