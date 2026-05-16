/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Sizing operators: \tiny, \small, \normalsize, \large, \Huge, etc.
 *
 * Original source: katex src/functions/sizing.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package functions

import lowlevel.Nullable
import ssg.katex.build.{ BuildCommon, BuildHTML, BuildMathML }
import ssg.katex.data.Units
import ssg.katex.parse._
import ssg.katex.tree.{ HtmlDocumentFragment, MathNode }

object SizingFunc {

  def sizingGroup(
    value:       Array[AnyParseNode],
    options:     Options,
    baseOptions: Options
  ): HtmlDocumentFragment = {
    val inner      = BuildHTML.buildExpression(value, options, isRealGroup = false)
    val multiplier = options.sizeMultiplier / baseOptions.sizeMultiplier

    // Add size-resetting classes to the inner list and set maxFontSize
    // manually. Handle nested size changes.
    var i = 0
    while (i < inner.length) {
      val pos = inner(i).classes.indexOf("sizing")
      if (pos < 0) {
        inner(i).classes ++= options.sizingClasses(baseOptions)
      } else if (inner(i).classes(pos + 1) == "reset-size" + options.size) {
        // This is a nested size change: e.g., inner[i] is the "b" in
        // `\Huge a \small b`. Override the old size (the `reset-` class)
        // but not the new size.
        inner(i).classes(pos + 1) = "reset-size" + baseOptions.size
      }

      inner(i).height *= multiplier
      inner(i).depth *= multiplier
      i += 1
    }

    BuildCommon.makeFragment(inner)
  }

  private val sizeFuncs = Array(
    "\\tiny",
    "\\sixptsize",
    "\\scriptsize",
    "\\footnotesize",
    "\\small",
    "\\normalsize",
    "\\large",
    "\\Large",
    "\\LARGE",
    "\\huge",
    "\\Huge"
  )

  val htmlBuilder: HtmlBuilder = (group, options) => {
    val g    = group.asInstanceOf[ParseNodeSizing]
    val opts = options.asInstanceOf[Options]
    // Handle sizing operators like \Huge. Real TeX doesn't actually allow
    // these functions inside of math expressions, so we do some special
    // handling.
    val newOptions = opts.havingSize(g.size)
    sizingGroup(g.body, newOptions, opts)
  }

  def register(): Unit =
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "sizing",
        names = sizeFuncs,
        props = FunctionPropSpec(
          numArgs = 0,
          allowedInText = true
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          val body   = parser.parseExpression(false, context.breakOnTokenText.map(_.value))

          ParseNodeSizing(
            mode = parser.mode,
            // Figure out what size to use based on the list of functions above
            size = sizeFuncs.indexOf(context.funcName) + 1,
            body = body
          )
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable { (group, options) =>
          val g          = group.asInstanceOf[ParseNodeSizing]
          val opts       = options.asInstanceOf[Options]
          val newOptions = opts.havingSize(g.size)
          val inner      = BuildMathML.buildExpression(g.body, newOptions)

          val node = new MathNode("mstyle", inner)

          // TODO(emily): This doesn't produce the correct size for nested size
          // changes, because we don't keep state of what style we're currently
          // in, so we can't reset the size to normal before changing it.  Now
          // that we're passing an options parameter we should be able to fix
          // this.
          node.setAttribute("mathsize", Units.makeEm(newOptions.sizeMultiplier))

          node
        }
      )
    )
}
