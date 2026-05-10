/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * \sqrt command — square root.
 *
 * Original source: katex src/functions/sqrt.ts
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
import ssg.katex.build.{ BuildCommon, BuildHTML, BuildMathML, Delimiter, VListElem, VListKern, VListParam }
import ssg.katex.data.Units
import ssg.katex.parse._
import ssg.katex.tree.MathNode

object SqrtFunc {

  def register(): Unit = {
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "sqrt",
        names = Array("\\sqrt"),
        props = FunctionPropSpec(
          numArgs = 1,
          numOptionalArgs = 1
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          val index: Nullable[AnyParseNode] = optArgs(0)
          val body = args(0)
          ParseNodeSqrt(
            mode = parser.mode,
            body = body,
            index = index
          )
        },
        htmlBuilder = Nullable { (group, options) =>
          val g    = group.asInstanceOf[ParseNodeSqrt]
          val opts = options.asInstanceOf[Options]
          // Square roots are handled in the TeXbook pg. 443, Rule 11.

          // First, we do the same steps as in overline to build the inner group
          // and line
          var inner = BuildHTML.buildGroup(Nullable(g.body), opts.havingCrampedStyle())
          if (inner.height == 0) {
            // Render a small surd.
            inner.height = opts.fontMetrics().xHeight
          }

          // Some groups can return document fragments.  Handle those by wrapping
          // them in a span.
          inner = BuildCommon.wrapFragment(inner, opts)

          // Calculate the minimum size for the \surd delimiter
          val metrics = opts.fontMetrics()
          val theta   = metrics.defaultRuleThickness

          var phi = theta
          if (opts.style.id < Style.TEXT.id) {
            phi = opts.fontMetrics().xHeight
          }

          // Calculate the clearance between the body and line
          var lineClearance = theta + phi / 4

          val minDelimiterHeight = inner.height + inner.depth +
            lineClearance + theta

          // Create a sqrt SVG of the required minimum size
          val (img, ruleWidth, advanceWidth) = Delimiter.makeSqrtImage(minDelimiterHeight, opts)

          val delimDepth = img.height - ruleWidth

          // Adjust the clearance based on the delimiter size
          if (delimDepth > inner.height + inner.depth + lineClearance) {
            lineClearance = (lineClearance + delimDepth - inner.height - inner.depth) / 2
          }

          // Shift the sqrt image
          val imgShift = img.height - inner.height - lineClearance - ruleWidth

          inner.style = inner.style.copy(paddingLeft = Nullable(Units.makeEm(advanceWidth)))

          // Overlay the image and the argument.
          val body = BuildCommon.makeVList(
            VListParam.FirstBaseline(
              children = Array(
                VListElem(elem = inner, wrapperClasses = Array("svg-align")),
                VListKern(-(inner.height + imgShift)),
                VListElem(elem = img),
                VListKern(ruleWidth)
              )
            ),
            opts
          )

          if (g.index.isEmpty) {
            BuildCommon.makeSpan(ArrayBuffer("mord", "sqrt"), ArrayBuffer(body), Nullable(opts))
          } else {
            // Handle the optional root index

            // The index is always in scriptscript style
            val newOptions = opts.havingStyle(Style.SCRIPTSCRIPT)
            val rootm      = BuildHTML.buildGroup(g.index, newOptions, Nullable(opts))

            // The amount the index is shifted by. This is taken from the TeX
            // source, in the definition of `\r@@t`.
            val toShift = 0.6 * (body.height - body.depth)

            // Build a VList with the superscript shifted up correctly
            val rootVList = BuildCommon.makeVList(VListParam.Positioned(
                                                    positionType = "shift",
                                                    positionData = -toShift,
                                                    children = Array(VListElem(elem = rootm))
                                                  ),
                                                  opts
            )
            // Add a class surrounding it so we can add on the appropriate
            // kerning
            val rootVListWrap = BuildCommon.makeSpan(ArrayBuffer("root"), ArrayBuffer(rootVList))

            BuildCommon.makeSpan(ArrayBuffer("mord", "sqrt"), ArrayBuffer(rootVListWrap, body), Nullable(opts))
          }
        },
        mathmlBuilder = Nullable { (group, options) =>
          val g    = group.asInstanceOf[ParseNodeSqrt]
          val opts = options.asInstanceOf[Options]
          if (g.index.isDefined) {
            new MathNode("mroot",
                         ArrayBuffer(
                           BuildMathML.buildGroup(g.body, opts),
                           BuildMathML.buildGroup(g.index.get, opts)
                         )
            )
          } else {
            new MathNode("msqrt", ArrayBuffer(BuildMathML.buildGroup(g.body, opts)))
          }
        }
      )
    )
  }
}
