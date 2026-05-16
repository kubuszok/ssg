/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * \raisebox — box manipulation.
 *
 * Original source: katex src/functions/raisebox.ts
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
import ssg.katex.build.{ BuildCommon, BuildHTML, BuildMathML, VListElem, VListParam }
import ssg.katex.data.Units
import ssg.katex.parse._
import ssg.katex.tree.MathNode

object RaiseboxFunc {

  def register(): Unit =
    // Box manipulation
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "raisebox",
        names = Array("\\raisebox"),
        props = FunctionPropSpec(
          numArgs = 2,
          argTypes = Nullable(Array(ArgType.Size, ArgType.Hbox)),
          allowedInText = true
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          val amount = ParseNode.assertNodeType(Nullable(args(0)), "size").asInstanceOf[ParseNodeSize].value
          val body   = args(1)
          ParseNodeRaisebox(
            mode = parser.mode,
            dy = amount,
            body = body
          )
        },
        htmlBuilder = Nullable { (group, options) =>
          val g    = group.asInstanceOf[ParseNodeRaisebox]
          val opts = options.asInstanceOf[Options]
          val body = BuildHTML.buildGroup(Nullable(g.body), opts)
          val dy   = Units.calculateSize(g.dy, opts)
          BuildCommon.makeVList(VListParam.Positioned(
                                  positionType = "shift",
                                  positionData = -dy,
                                  children = Array(VListElem(elem = body))
                                ),
                                opts
          )
        },
        mathmlBuilder = Nullable { (group, options) =>
          val g    = group.asInstanceOf[ParseNodeRaisebox]
          val opts = options.asInstanceOf[Options]
          val node = new MathNode("mpadded", ArrayBuffer(BuildMathML.buildGroup(g.body, opts)))
          val dy   = g.dy.number.toString + g.dy.unit
          node.setAttribute("voffset", dy)
          node
        }
      )
    )
}
