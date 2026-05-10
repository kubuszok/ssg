/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Super scripts and subscripts, whose precise placement can depend on other
 * functions that precede them.
 *
 * Original source: katex src/functions/supsub.ts
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

import ssg.commons.Nullable
import ssg.katex.build.{ BuildCommon, BuildHTML, BuildMathML, VListChild, VListElem, VListElemAndShift, VListParam }
import ssg.katex.data.Units
import ssg.katex.parse._
import ssg.katex.tree.{ HtmlDomNode, MathDomNode, MathNode, SymbolNode }
import ssg.katex.util.{ Utils => KatexUtils }

object SupsubFunc {

  /** Sometimes, groups perform special rules when they have superscripts or subscripts attached to them. This function lets the `supsub` group know that its inner element should handle the
    * superscripts and subscripts instead of handling them itself.
    */
  private def htmlBuilderDelegate(
    group:   ParseNodeSupsub,
    options: Options
  ): Nullable[HtmlBuilder] = {
    val base = group.base
    if (base.isEmpty) {
      Nullable.Null
    } else {
      val b = base.get
      if (b.nodeType == "op") {
        // Operators handle supsubs differently when they have limits
        val op       = b.asInstanceOf[ParseNodeOp]
        val delegate = op.limits &&
          (options.style.size == Style.DISPLAY.size ||
            op.alwaysHandleSupSub.getOrElse(false))
        if (delegate) Nullable(OpFunc.htmlBuilder) else Nullable.Null
      } else if (b.nodeType == "operatorname") {
        val on       = b.asInstanceOf[ParseNodeOperatorname]
        val delegate = on.alwaysHandleSupSub &&
          (options.style.size == Style.DISPLAY.size || on.limits)
        if (delegate) Nullable(OperatornameFunc.htmlBuilder) else Nullable.Null
      } else if (b.nodeType == "accent") {
        val acc = b.asInstanceOf[ParseNodeAccent]
        if (KatexUtils.isCharacterBox(acc.base)) Nullable(AccentFunc.htmlBuilder) else Nullable.Null
      } else if (b.nodeType == "horizBrace") {
        val hb    = b.asInstanceOf[ParseNodeHorizBrace]
        val isSup = group.sub.isEmpty
        if (isSup == hb.isOver) Nullable(HorizBraceFunc.htmlBuilder) else Nullable.Null
      } else {
        Nullable.Null
      }
    }
  }

  def register(): Unit = {
    // Super scripts and subscripts, whose precise placement can depend on other
    // functions that precede them.
    FunctionDef.defineFunctionBuilders(
      nodeType = "supsub",
      htmlBuilder = Nullable((group, options) =>
        boundary {
          val g    = group.asInstanceOf[ParseNodeSupsub]
          val opts = options.asInstanceOf[Options]
          // Superscript and subscripts are handled in the TeXbook on page
          // 445-446, rules 18(a-f).

          // Here is where we defer to the inner group if it should handle
          // superscripts and subscripts itself.
          val builderDelegate = htmlBuilderDelegate(g, opts)
          if (builderDelegate.isDefined) {
            break(builderDelegate.get(g, opts))
          }

          val valueBase = g.base
          val valueSup  = g.sup
          val valueSub  = g.sub
          val base      = BuildHTML.buildGroup(valueBase, opts)
          var supm: Nullable[HtmlDomNode] = Nullable.Null
          var subm: Nullable[HtmlDomNode] = Nullable.Null

          val metrics = opts.fontMetrics()

          // Rule 18a
          var supShift = 0.0
          var subShift = 0.0

          val isCharBox = valueBase.isDefined && KatexUtils.isCharacterBox(valueBase.get)
          if (valueSup.isDefined) {
            val newOptions = opts.havingStyle(opts.style.sup())
            supm = Nullable(BuildHTML.buildGroup(valueSup, newOptions, Nullable(opts)))
            if (!isCharBox) {
              supShift = base.height - newOptions.fontMetrics().supDrop *
                newOptions.sizeMultiplier / opts.sizeMultiplier
            }
          }

          if (valueSub.isDefined) {
            val newOptions = opts.havingStyle(opts.style.sub())
            subm = Nullable(BuildHTML.buildGroup(valueSub, newOptions, Nullable(opts)))
            if (!isCharBox) {
              subShift = base.depth + newOptions.fontMetrics().subDrop *
                newOptions.sizeMultiplier / opts.sizeMultiplier
            }
          }

          // Rule 18c
          var minSupShift: Double = 0
          if (opts.style == Style.DISPLAY) {
            minSupShift = metrics.sup1
          } else if (opts.style.cramped) {
            minSupShift = metrics.sup3
          } else {
            minSupShift = metrics.sup2
          }

          // scriptspace is a font-size-independent size, so scale it
          // appropriately for use as the marginRight.
          val multiplier  = opts.sizeMultiplier
          val marginRight = Units.makeEm((0.5 / metrics.ptPerEm) / multiplier)

          var marginLeft: Nullable[String] = Nullable.Null
          if (subm.isDefined) {
            // Subscripts shouldn't be shifted by the base's italic correction.
            // Account for that by shifting the subscript back the appropriate
            // amount. Note we only do this when the base is a single symbol.
            val isOiint =
              g.base.isDefined && g.base.get.nodeType == "op" &&
                g.base.get.asInstanceOf[ParseNodeOp].name.exists(n => n == "\\oiint" || n == "\\oiiint")
            if (base.isInstanceOf[SymbolNode] || isOiint) {
              // In JS, base.italic on a non-SymbolNode returns undefined -> NaN.
              // Use 0.0 when base is not a SymbolNode.
              val italic = if (base.isInstanceOf[SymbolNode]) base.asInstanceOf[SymbolNode].italic else 0.0
              marginLeft = Nullable(Units.makeEm(-italic))
            }
          }

          var supsub: HtmlDomNode = BuildCommon.makeSpan() // overwritten below in all branches
          if (supm.isDefined && subm.isDefined) {
            supShift = Math.max(Math.max(supShift, minSupShift), supm.get.depth + 0.25 * metrics.xHeight)
            subShift = Math.max(subShift, metrics.sub2)

            val ruleWidth = metrics.defaultRuleThickness

            // Rule 18e
            val maxWidth = 4 * ruleWidth
            if ((supShift - supm.get.depth) - (subm.get.height - subShift) < maxWidth) {
              subShift = maxWidth - (supShift - supm.get.depth) + subm.get.height
              val psi = 0.8 * metrics.xHeight - (supShift - supm.get.depth)
              if (psi > 0) {
                supShift += psi
                subShift -= psi
              }
            }

            val vlistElem = Array(
              VListElemAndShift(elem = subm.get, shift = subShift, marginRight = Nullable(marginRight), marginLeft = marginLeft),
              VListElemAndShift(elem = supm.get, shift = -supShift, marginRight = Nullable(marginRight))
            )

            supsub = BuildCommon.makeVList(VListParam.IndividualShift(
                                             children = vlistElem
                                           ),
                                           opts
            )
          } else if (subm.isDefined) {
            // Rule 18b
            subShift = Math.max(Math.max(subShift, metrics.sub1), subm.get.height - 0.8 * metrics.xHeight)

            val vlistElem: Array[VListChild] = Array(
              VListElem(elem = subm.get, marginLeft = marginLeft, marginRight = Nullable(marginRight))
            )

            supsub = BuildCommon.makeVList(VListParam.Positioned(
                                             positionType = "shift",
                                             positionData = subShift,
                                             children = vlistElem
                                           ),
                                           opts
            )
          } else if (supm.isDefined) {
            // Rule 18c, d
            supShift = Math.max(Math.max(supShift, minSupShift), supm.get.depth + 0.25 * metrics.xHeight)

            supsub = BuildCommon.makeVList(
              VListParam.Positioned(
                positionType = "shift",
                positionData = -supShift,
                children = Array(
                  VListElem(elem = supm.get, marginRight = Nullable(marginRight))
                )
              ),
              opts
            )
          } else {
            throw new Error("supsub must have either sup or sub.")
          }

          // Wrap the supsub vlist in a span.msupsub to reset text-align.
          val mclass = BuildHTML.getTypeOfDomTree(Nullable(base), Nullable("right")).getOrElse("mord")
          BuildCommon.makeSpan(ArrayBuffer(mclass), ArrayBuffer(base, BuildCommon.makeSpan(ArrayBuffer("msupsub"), ArrayBuffer(supsub))), Nullable(opts))
        }
      ),
      mathmlBuilder = Nullable { (group, options) =>
        val g    = group.asInstanceOf[ParseNodeSupsub]
        val opts = options.asInstanceOf[Options]
        // Is the inner group a relevant horizontal brace?
        var isBrace = false
        var isOver  = false
        var isSup   = false

        if (g.base.isDefined && g.base.get.nodeType == "horizBrace") {
          isSup = g.sup.isDefined
          if (isSup == g.base.get.asInstanceOf[ParseNodeHorizBrace].isOver) {
            isBrace = true
            isOver = g.base.get.asInstanceOf[ParseNodeHorizBrace].isOver
          }
        }

        if (
          g.base.isDefined &&
          (g.base.get.nodeType == "op" || g.base.get.nodeType == "operatorname")
        ) {
          if (g.base.get.nodeType == "op") {
            g.base.get.asInstanceOf[ParseNodeOp].parentIsSupSub = true
          } else {
            g.base.get.asInstanceOf[ParseNodeOperatorname].parentIsSupSub = true
          }
        }

        val children = ArrayBuffer[MathDomNode](BuildMathML.buildGroup(g.base, opts))

        if (g.sub.isDefined) {
          children += BuildMathML.buildGroup(g.sub.get, opts)
        }

        if (g.sup.isDefined) {
          children += BuildMathML.buildGroup(g.sup.get, opts)
        }

        var nodeType: String = "" // overwritten below in all branches
        if (isBrace) {
          nodeType = if (isOver) "mover" else "munder"
        } else if (g.sub.isEmpty) {
          val base = g.base
          if (
            base.isDefined && base.get.nodeType == "op" &&
            base.get.asInstanceOf[ParseNodeOp].limits &&
            (opts.style == Style.DISPLAY || base.get.asInstanceOf[ParseNodeOp].alwaysHandleSupSub.getOrElse(false))
          ) {
            nodeType = "mover"
          } else if (
            base.isDefined && base.get.nodeType == "operatorname" &&
            base.get.asInstanceOf[ParseNodeOperatorname].alwaysHandleSupSub &&
            (base.get.asInstanceOf[ParseNodeOperatorname].limits || opts.style == Style.DISPLAY)
          ) {
            nodeType = "mover"
          } else {
            nodeType = "msup"
          }
        } else if (g.sup.isEmpty) {
          val base = g.base
          if (
            base.isDefined && base.get.nodeType == "op" &&
            base.get.asInstanceOf[ParseNodeOp].limits &&
            (opts.style == Style.DISPLAY || base.get.asInstanceOf[ParseNodeOp].alwaysHandleSupSub.getOrElse(false))
          ) {
            nodeType = "munder"
          } else if (
            base.isDefined && base.get.nodeType == "operatorname" &&
            base.get.asInstanceOf[ParseNodeOperatorname].alwaysHandleSupSub &&
            (base.get.asInstanceOf[ParseNodeOperatorname].limits || opts.style == Style.DISPLAY)
          ) {
            nodeType = "munder"
          } else {
            nodeType = "msub"
          }
        } else {
          val base = g.base
          if (
            base.isDefined && base.get.nodeType == "op" &&
            base.get.asInstanceOf[ParseNodeOp].limits &&
            opts.style == Style.DISPLAY
          ) {
            nodeType = "munderover"
          } else if (
            base.isDefined && base.get.nodeType == "operatorname" &&
            base.get.asInstanceOf[ParseNodeOperatorname].alwaysHandleSupSub &&
            (opts.style == Style.DISPLAY || base.get.asInstanceOf[ParseNodeOperatorname].limits)
          ) {
            nodeType = "munderover"
          } else {
            nodeType = "msubsup"
          }
        }

        new MathNode(nodeType, children)
      }
    )
  }
}
