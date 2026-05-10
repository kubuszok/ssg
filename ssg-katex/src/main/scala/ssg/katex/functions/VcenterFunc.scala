/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * \vcenter — Vertically center the argument group on the math axis.
 *
 * Original source: katex src/functions/vcenter.ts
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
import ssg.katex.build.{ BuildCommon, BuildHTML, BuildMathML, VListElem, VListParam }
import ssg.katex.parse._
import ssg.katex.tree.MathNode

object VcenterFunc {

  def register(): Unit =
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "vcenter",
        names = Array("\\vcenter"),
        props = FunctionPropSpec(
          numArgs = 1,
          argTypes = Nullable(Array(ArgType.Original)), // In LaTeX, \vcenter can act only on a box.
          allowedInText = false
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          ParseNodeVcenter(mode = parser.mode, body = args(0))
        },
        htmlBuilder = Nullable { (group, options) =>
          val g          = group.asInstanceOf[ParseNodeVcenter]
          val opts       = options.asInstanceOf[Options]
          val body       = BuildHTML.buildGroup(Nullable(g.body), opts)
          val axisHeight = opts.fontMetrics().axisHeight
          val dy         = 0.5 * ((body.height - axisHeight) - (body.depth + axisHeight))
          BuildCommon.makeVList(VListParam.Positioned(
                                  positionType = "shift",
                                  positionData = dy,
                                  children = Array(VListElem(elem = body))
                                ),
                                opts
          )
        },
        mathmlBuilder = Nullable { (group, options) =>
          val g    = group.asInstanceOf[ParseNodeVcenter]
          val opts = options.asInstanceOf[Options]
          // There is no way to do this in MathML.
          // Write a class as a breadcrumb in case some post-processor wants
          // to perform a vcenter adjustment.
          // Wrap in mrow to ensure valid MathML when placed inside mo (e.g., \mathrel)
          val mpadded = new MathNode("mpadded", ArrayBuffer(BuildMathML.buildGroup(g.body, opts)), ArrayBuffer("vcenter"))
          new MathNode("mrow", ArrayBuffer(mpadded))
        }
      )
    )
}
