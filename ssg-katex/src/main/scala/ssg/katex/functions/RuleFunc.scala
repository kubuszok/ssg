/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * \rule — draws a rule (filled rectangle).
 *
 * Original source: katex src/functions/rule.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package functions

import scala.collection.mutable.ArrayBuffer

import scala.language.implicitConversions

import ssg.commons.Nullable
import ssg.katex.build.BuildCommon
import ssg.katex.data.Units
import ssg.katex.parse._
import ssg.katex.tree.{HtmlDomNode, MathNode}

object RuleFunc {

  def register(): Unit = {
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "rule",
      names = Array("\\rule"),
      props = FunctionPropSpec(
        numArgs = 2,
        numOptionalArgs = 1,
        allowedInText = true,
        allowedInMath = true,
        argTypes = Nullable(Array(ArgType.Size, ArgType.Size, ArgType.Size))
      ),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        val shift = optArgs(0)
        val width = ParseNode.assertNodeType(Nullable(args(0)), "size").asInstanceOf[ParseNodeSize]
        val height = ParseNode.assertNodeType(Nullable(args(1)), "size").asInstanceOf[ParseNodeSize]
        val shiftValue: Nullable[data.Measurement] =
          if (shift.isDefined) {
            Nullable(ParseNode.assertNodeType(shift, "size").asInstanceOf[ParseNodeSize].value)
          } else Nullable.Null
        ParseNodeRule(
          mode = parser.mode,
          shift = shiftValue,
          width = width.value,
          height = height.value
        )
      }),
      htmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeRule]
        val opts = options.asInstanceOf[Options]
        // Make an empty span for the rule
        val rule = BuildCommon.makeSpan(ArrayBuffer("mord", "rule"), ArrayBuffer.empty[HtmlDomNode], Nullable(opts))

        // Calculate the shift, width, and height of the rule, and account for units
        val width = Units.calculateSize(g.width, opts)
        val height = Units.calculateSize(g.height, opts)
        val shift = g.shift.fold(0.0)(s => Units.calculateSize(s, opts))

        // Style the rule to the right size
        rule.style = rule.style.copy(
          borderRightWidth = Nullable(Units.makeEm(width)),
          borderTopWidth = Nullable(Units.makeEm(height)),
          bottom = Nullable(Units.makeEm(shift))
        )

        // Record the height and width
        rule.width = width
        rule.height = height + shift
        rule.depth = -shift
        // Font size is the number large enough that the browser will
        // reserve at least `absHeight` space above the baseline.
        // The 1.125 factor was empirically determined
        rule.maxFontSize = height * 1.125 * opts.sizeMultiplier

        rule
      }),
      mathmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeRule]
        val opts = options.asInstanceOf[Options]
        val width = Units.calculateSize(g.width, opts)
        val height = Units.calculateSize(g.height, opts)
        val shift = g.shift.fold(0.0)(s => Units.calculateSize(s, opts))
        val color = if (opts.color.isDefined) opts.getColor().getOrElse("black") else "black"

        val rule = new MathNode("mspace")
        rule.setAttribute("mathbackground", color)
        rule.setAttribute("width", Units.makeEm(width))
        rule.setAttribute("height", Units.makeEm(height))

        val wrapper = new MathNode("mpadded", ArrayBuffer(rule))
        if (shift >= 0) {
          wrapper.setAttribute("height", Units.makeEm(shift))
        } else {
          wrapper.setAttribute("height", Units.makeEm(shift))
          wrapper.setAttribute("depth", Units.makeEm(-shift))
        }
        wrapper.setAttribute("voffset", Units.makeEm(shift))

        wrapper
      })
    ))
  }
}
