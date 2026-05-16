/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Stretchy arrows with an optional argument: \xleftarrow, \xrightarrow, etc.
 *
 * Original source: katex src/functions/arrow.ts
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
import ssg.katex.build.{ BuildCommon, BuildHTML, BuildMathML, Stretchy, VListElemAndShift, VListParam }
import ssg.katex.parse._
import ssg.katex.tree.{ DomSpan, HtmlDomNode, MathDomNode, MathNode }

object ArrowFunc {

  // Helper function
  private def paddedNode(group: Nullable[MathDomNode] = Nullable.Null): MathNode = {
    val children: ArrayBuffer[MathDomNode] = group.fold(ArrayBuffer.empty[MathDomNode])(g => ArrayBuffer(g))
    val node = new MathNode("mpadded", children)
    node.setAttribute("width", "+0.6em")
    node.setAttribute("lspace", "0.3em")
    node
  }

  def register(): Unit = {
    // Stretchy arrows with an optional argument
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "xArrow",
        names = Array(
          "\\xleftarrow",
          "\\xrightarrow",
          "\\xLeftarrow",
          "\\xRightarrow",
          "\\xleftrightarrow",
          "\\xLeftrightarrow",
          "\\xhookleftarrow",
          "\\xhookrightarrow",
          "\\xmapsto",
          "\\xrightharpoondown",
          "\\xrightharpoonup",
          "\\xleftharpoondown",
          "\\xleftharpoonup",
          "\\xrightleftharpoons",
          "\\xleftrightharpoons",
          "\\xlongequal",
          "\\xtwoheadrightarrow",
          "\\xtwoheadleftarrow",
          "\\xtofrom",
          // The next 3 functions are here to support the mhchem extension.
          // Direct use of these functions is discouraged and may break someday.
          "\\xrightleftarrows",
          "\\xrightequilibrium",
          "\\xleftequilibrium",
          // The next 3 functions are here only to support the {CD} environment.
          "\\\\cdrightarrow",
          "\\\\cdleftarrow",
          "\\\\cdlongequal"
        ),
        props = FunctionPropSpec(
          numArgs = 1,
          numOptionalArgs = 1
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          // CD environment internal functions (\\\\cdlongequal) may call
          // with empty args/optArgs. In JS, args[0] is undefined for empty arrays.
          ParseNodeXArrow(
            mode = parser.mode,
            label = context.funcName,
            body = if (args.nonEmpty) args(0) else null, // @nowarn — matches JS undefined
            below = if (optArgs.nonEmpty) optArgs(0) else Nullable.Null
          )
        },
        htmlBuilder = Nullable { (group, options) =>
          val g     = group.asInstanceOf[ParseNodeXArrow]
          val opts  = options.asInstanceOf[Options]
          val style = opts.style

          // Build the argument groups in the appropriate style.
          // Ref: amsmath.dtx:   \hbox{$\scriptstyle\mkern#3mu{#6}\mkern#4mu$}%

          // Some groups can return document fragments.  Handle those by wrapping
          // them in a span.
          var newOptions  = opts.havingStyle(style.sup())
          val upperGroup  = BuildCommon.wrapFragment(BuildHTML.buildGroup(Nullable(g.body), newOptions, Nullable(opts)), opts)
          val arrowPrefix = if (g.label.length >= 2 && g.label.substring(0, 2) == "\\x") "x" else "cd"
          upperGroup.asInstanceOf[DomSpan].classes += (arrowPrefix + "-arrow-pad")

          var lowerGroup: Nullable[HtmlDomNode] = Nullable.Null
          if (g.below.isDefined) {
            // Build the lower group
            newOptions = opts.havingStyle(style.sub())
            val lg = BuildCommon.wrapFragment(BuildHTML.buildGroup(g.below, newOptions, Nullable(opts)), opts)
            lg.asInstanceOf[DomSpan].classes += (arrowPrefix + "-arrow-pad")
            lowerGroup = Nullable(lg)
          }

          val arrowBody = Stretchy.stretchySvg(g, opts)

          // Re shift: Note that stretchySvg returned arrowBody.depth = 0.
          // The point we want on the math axis is at 0.5 * arrowBody.height.
          val arrowShift = -opts.fontMetrics().axisHeight +
            0.5 * arrowBody.height
          // 2 mu kern. Ref: amsmath.dtx: #7\if0#2\else\mkern#2mu\fi
          var upperShift = -opts.fontMetrics().axisHeight -
            0.5 * arrowBody.height - 0.111 // 0.111 em = 2 mu
          if (upperGroup.depth > 0.25 || g.label == "\\xleftequilibrium") {
            upperShift -= upperGroup.depth // shift up if depth encroaches
          }

          // Generate the vlist
          val vlist: DomSpan = if (lowerGroup.isDefined) {
            val lg         = lowerGroup.get
            val lowerShift = -opts.fontMetrics().axisHeight +
              lg.height + 0.5 * arrowBody.height + 0.111
            BuildCommon.makeVList(
              VListParam.IndividualShift(
                children = Array(
                  VListElemAndShift(elem = upperGroup, shift = upperShift),
                  VListElemAndShift(elem = arrowBody, shift = arrowShift),
                  VListElemAndShift(elem = lg, shift = lowerShift)
                )
              ),
              opts
            )
          } else {
            BuildCommon.makeVList(
              VListParam.IndividualShift(
                children = Array(
                  VListElemAndShift(elem = upperGroup, shift = upperShift),
                  VListElemAndShift(elem = arrowBody, shift = arrowShift)
                )
              ),
              opts
            )
          }

          // TODO(ts): Replace this with passing "svg-align" into makeVList.
          vlist.children(0).asInstanceOf[DomSpan].children(0).asInstanceOf[DomSpan].children(1).asInstanceOf[DomSpan].classes += "svg-align"

          BuildCommon.makeSpan(ArrayBuffer("mrel", "x-arrow"), ArrayBuffer(vlist), Nullable(opts))
        },
        mathmlBuilder = Nullable { (group, options) =>
          val g         = group.asInstanceOf[ParseNodeXArrow]
          val opts      = options.asInstanceOf[Options]
          val arrowNode = Stretchy.stretchyMathML(g.label)
          arrowNode.setAttribute(
            "minsize",
            if (g.label.charAt(0) == 'x') "1.75em" else "3.0em"
          )
          var node: MathNode = new MathNode("mrow") // overwritten below in all branches

          if (g.body != null) {
            val upperNode = paddedNode(Nullable(BuildMathML.buildGroup(g.body, opts)))
            if (g.below.isDefined) {
              val lowerNode = paddedNode(Nullable(BuildMathML.buildGroup(g.below.get, opts)))
              node = new MathNode(
                "munderover",
                ArrayBuffer(arrowNode, lowerNode, upperNode)
              )
            } else {
              node = new MathNode("mover", ArrayBuffer(arrowNode, upperNode))
            }
          } else if (g.below.isDefined) {
            val lowerNode = paddedNode(Nullable(BuildMathML.buildGroup(g.below.get, opts)))
            node = new MathNode("munder", ArrayBuffer(arrowNode, lowerNode))
          } else {
            // This should never happen.
            // Parser.js throws an error if there is no argument.
            val padded = paddedNode()
            node = new MathNode("mover", ArrayBuffer(arrowNode, padded))
          }
          node
        }
      )
    )
  }
}
