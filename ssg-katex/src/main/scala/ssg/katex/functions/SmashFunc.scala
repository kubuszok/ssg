/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * \smash, with optional [tb], as in AMS.
 *
 * Original source: katex src/functions/smash.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package functions

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

import lowlevel.Nullable
import ssg.katex.build.{ BuildCommon, BuildHTML, BuildMathML, VListElem, VListParam }
import ssg.katex.parse._
import ssg.katex.tree.MathNode

object SmashFunc {

  def register(): Unit = {
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "smash",
        names = Array("\\smash"),
        props = FunctionPropSpec(
          numArgs = 1,
          numOptionalArgs = 1,
          allowedInText = true
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser      = context.parser.asInstanceOf[Parser]
          var smashHeight = false
          var smashDepth  = false
          val tbArg: Nullable[ParseNodeOrdgroup] =
            if (optArgs(0).isDefined) {
              val asserted = ParseNode.assertNodeType(optArgs(0), "ordgroup")
              Nullable(asserted.asInstanceOf[ParseNodeOrdgroup])
            } else Nullable.Null

          if (tbArg.isDefined) {
            // Optional [tb] argument is engaged.
            // ref: amsmath: \renewcommand{\smash}[1][tb]{%
            //               def\mb@t{\ht}\def\mb@b{\dp}\def\mb@tb{\ht\z@\z@\dp}%
            var letter = ""
            var i      = 0
            var valid  = true
            while (i < tbArg.get.body.length && valid) {
              val node = tbArg.get.body(i)
              letter = ParseNode.assertSymbolNodeType(Nullable(node)).text
              if (letter == "t") {
                smashHeight = true
              } else if (letter == "b") {
                smashDepth = true
              } else {
                smashHeight = false
                smashDepth = false
                valid = false
              }
              i += 1
            }
          } else {
            smashHeight = true
            smashDepth = true
          }

          val body = args(0)
          ParseNodeSmash(
            mode = parser.mode,
            body = body,
            smashHeight = smashHeight,
            smashDepth = smashDepth
          )
        },
        htmlBuilder = Nullable((group, options) =>
          boundary {
            val g    = group.asInstanceOf[ParseNodeSmash]
            val opts = options.asInstanceOf[Options]
            val node = BuildCommon.makeSpan(ArrayBuffer.empty, ArrayBuffer(BuildHTML.buildGroup(Nullable(g.body), opts)))

            if (!g.smashHeight && !g.smashDepth) {
              break(node)
            }

            if (g.smashHeight) {
              node.height = 0
            }

            if (g.smashDepth) {
              node.depth = 0
            }

            if (g.smashHeight && g.smashDepth) {
              // Symmetric \smash can stay in inline layout.
              break(BuildCommon.makeSpan(ArrayBuffer("mord", "smash"), ArrayBuffer(node), Nullable(opts)))
            }

            // In order to influence makeVList for asymmetric smashing, we have to
            // reset the children.
            if (node.children != null) {
              var i = 0
              while (i < node.children.length) {
                if (g.smashHeight) {
                  node.children(i).height = 0
                }
                if (g.smashDepth) {
                  node.children(i).depth = 0
                }
                i += 1
              }
            }

            // At this point, we've reset the TeX-like height and depth values.
            // But the span still has an HTML line height.
            // makeVList applies "display: table-cell", which prevents the browser
            // from acting on that line height. So we'll call makeVList now.

            val smashedNode = BuildCommon.makeVList(VListParam.FirstBaseline(
                                                      children = Array(VListElem(elem = node))
                                                    ),
                                                    opts
            )

            // For spacing, TeX treats \smash as a math group (same spacing as ord).
            BuildCommon.makeSpan(ArrayBuffer("mord"), ArrayBuffer(smashedNode), Nullable(opts))
          }
        ),
        mathmlBuilder = Nullable { (group, options) =>
          val g    = group.asInstanceOf[ParseNodeSmash]
          val opts = options.asInstanceOf[Options]
          val node = new MathNode("mpadded", ArrayBuffer(BuildMathML.buildGroup(g.body, opts)))

          if (g.smashHeight) {
            node.setAttribute("height", "0px")
          }

          if (g.smashDepth) {
            node.setAttribute("depth", "0px")
          }

          node
        }
      )
    )
  }
}
