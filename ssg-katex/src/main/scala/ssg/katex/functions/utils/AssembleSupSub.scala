/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * For an operator with limits, assemble the base, sup, and sub into a span.
 *
 * Original source: katex src/functions/utils/assembleSupSub.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: assembleSupSub -> AssembleSupSub.assembleSupSub
 *   Convention: null | undefined -> Nullable[A]
 *   Idiom: TypeScript union DomSpan | SymbolNode -> HtmlDomNode
 */
package ssg
package katex
package functions
package utils

import scala.collection.mutable.ArrayBuffer

import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

import ssg.commons.Nullable
import ssg.katex.build.{ BuildCommon, BuildHTML, VListChild, VListElem, VListKern, VListParam }
import ssg.katex.data.Units
import ssg.katex.parse.AnyParseNode
import ssg.katex.tree.{ DomSpan, HtmlDomNode }
import ssg.katex.util.{ Utils => KatexUtils }

// For an operator with limits, assemble the base, sup, and sub into a span.

object AssembleSupSub {

  def assembleSupSub(
    baseIn:    HtmlDomNode,
    supGroup:  Nullable[AnyParseNode],
    subGroup:  Nullable[AnyParseNode],
    options:   Options,
    style:     Style,
    slant:     Double,
    baseShift: Double
  ): DomSpan = boundary {
    val base                 = BuildCommon.makeSpan(ArrayBuffer.empty, ArrayBuffer(baseIn))
    val subIsSingleCharacter = subGroup.isDefined && KatexUtils.isCharacterBox(subGroup.get)
    var sub: Nullable[(HtmlDomNode, Double)] = Nullable.Null
    var sup: Nullable[(HtmlDomNode, Double)] = Nullable.Null
    // We manually have to handle the superscripts and subscripts. This,
    // aside from the kern calculations, is copied from supsub.
    if (supGroup.isDefined) {
      val elem = BuildHTML.buildGroup(supGroup.get, options.havingStyle(style.sup()), Nullable(options))

      sup = Nullable(
        (
          elem,
          Math.max(options.fontMetrics().bigOpSpacing1, options.fontMetrics().bigOpSpacing3 - elem.depth)
        )
      )
    }

    if (subGroup.isDefined) {
      val elem = BuildHTML.buildGroup(subGroup.get, options.havingStyle(style.sub()), Nullable(options))

      sub = Nullable(
        (
          elem,
          Math.max(options.fontMetrics().bigOpSpacing2, options.fontMetrics().bigOpSpacing4 - elem.height)
        )
      )
    }

    // Build the final group as a vlist of the possible subscript, base,
    // and possible superscript.
    val finalGroup: DomSpan = if (sup.isDefined && sub.isDefined) {
      val (supElem, supKern) = sup.get
      val (subElem, subKern) = sub.get
      val bottom             = options.fontMetrics().bigOpSpacing5 +
        subElem.height + subElem.depth +
        subKern +
        base.depth + baseShift

      BuildCommon.makeVList(
        VListParam.Positioned(
          positionType = "bottom",
          positionData = bottom,
          children = Array(
            VListKern(options.fontMetrics().bigOpSpacing5),
            VListElem(elem = subElem, marginLeft = Nullable(Units.makeEm(-slant))),
            VListKern(subKern),
            VListElem(elem = base),
            VListKern(supKern),
            VListElem(elem = supElem, marginLeft = Nullable(Units.makeEm(slant))),
            VListKern(options.fontMetrics().bigOpSpacing5)
          )
        ),
        options
      )
    } else if (sub.isDefined) {
      val (subElem, subKern) = sub.get
      val top                = base.height - baseShift

      // Shift the limits by the slant of the symbol. Note
      // that we are supposed to shift the limits by 1/2 of the slant,
      // but since we are centering the limits adding a full slant of
      // margin will shift by 1/2 that.
      BuildCommon.makeVList(
        VListParam.Positioned(
          positionType = "top",
          positionData = top,
          children = Array(
            VListKern(options.fontMetrics().bigOpSpacing5),
            VListElem(elem = subElem, marginLeft = Nullable(Units.makeEm(-slant))),
            VListKern(subKern),
            VListElem(elem = base)
          )
        ),
        options
      )
    } else if (sup.isDefined) {
      val (supElem, supKern) = sup.get
      val bottom             = base.depth + baseShift

      BuildCommon.makeVList(
        VListParam.Positioned(
          positionType = "bottom",
          positionData = bottom,
          children = Array(
            VListElem(elem = base),
            VListKern(supKern),
            VListElem(elem = supElem, marginLeft = Nullable(Units.makeEm(slant))),
            VListKern(options.fontMetrics().bigOpSpacing5)
          )
        ),
        options
      )
    } else {
      // This case probably shouldn't occur (this would mean the
      // supsub was sending us a group with no superscript or
      // subscript) but be safe.
      break(base)
    }

    val parts = ArrayBuffer[HtmlDomNode](finalGroup)
    if (sub.isDefined && slant != 0 && !subIsSingleCharacter) {
      // A negative margin-left was applied to the lower limit.
      // Avoid an overlap by placing a spacer on the left on the group.
      val spacer = BuildCommon.makeSpan(ArrayBuffer("mspace"), ArrayBuffer.empty[HtmlDomNode], Nullable.Null)
      spacer.style = spacer.style.copy(marginRight = Nullable(Units.makeEm(slant)))
      parts.prepend(spacer)
    }
    BuildCommon.makeSpan(ArrayBuffer("mop", "op-limits"), parts, Nullable(options))
  }
}
