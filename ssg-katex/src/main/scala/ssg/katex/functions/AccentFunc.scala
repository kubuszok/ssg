/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Accent commands — both math-mode and text-mode accents.
 * NOTE: Unlike most `htmlBuilder`s, this one handles not only "accent", but
 * also "supsub" since an accent can affect super/subscripting.
 *
 * Original source: katex src/functions/accent.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package functions

import scala.collection.mutable.ArrayBuffer
import scala.util.matching.Regex

import scala.language.implicitConversions

import ssg.commons.Nullable
import ssg.katex.build.{BuildCommon, BuildHTML, BuildMathML, Stretchy, VListChild, VListElem, VListKern, VListParam}
import ssg.katex.data.Units
import ssg.katex.parse._
import ssg.katex.tree.{CssStyle, DomSpan, HtmlDomNode, MathNode, SymbolNode}
import ssg.katex.util.{Utils => KatexUtils}

object AccentFunc {

  private def getBaseSymbol(group: HtmlDomNode): Nullable[SymbolNode] = {
    group match {
      case sn: SymbolNode => Nullable(sn)
      case ds: DomSpan @unchecked if ds.children.length == 1 =>
        getBaseSymbol(ds.children(0))
      case _ => Nullable.Null
    }
  }

  // NOTE: Unlike most `htmlBuilder`s, this one handles not only "accent", but
  // also "supsub" since an accent can affect super/subscripting.
  val htmlBuilder: HtmlBuilder = (grp, options) => {
    val opts = options.asInstanceOf[Options]
    // Accents are handled in the TeXbook pg. 443, rule 12.
    var supSubGroup: Nullable[DomSpan] = Nullable.Null
    val group: ParseNodeAccent = if (grp != null && grp.nodeType == "supsub") {
      val supsub = grp.asInstanceOf[ParseNodeSupsub]
      // If our base is a character box, and we have superscripts and
      // subscripts, the supsub will defer to us. In particular, we want
      // to attach the superscripts and subscripts to the inner body (so
      // that the position of the superscripts and subscripts won't be
      // affected by the height of the accent). We accomplish this by
      // sticking the base of the accent into the base of the supsub, and
      // rendering that, while keeping track of where the accent is.

      // The real accent group is the base of the supsub group
      val g = ParseNode.assertNodeType(supsub.base, "accent").asInstanceOf[ParseNodeAccent]
      // The character box is the base of the accent group
      val b = g.base
      // Stick the character box into the base of the supsub group
      supsub.base = b

      // Rerender the supsub group with its new base, and store that result.
      val built = BuildHTML.buildGroup(Nullable(supsub), opts)
      supSubGroup = Nullable(built.asInstanceOf[DomSpan])

      // reset original base
      supsub.base = g
      g
    } else {
      ParseNode.assertNodeType(Nullable(grp), "accent").asInstanceOf[ParseNodeAccent]
    }
    val base: AnyParseNode = group.base

    // Build the base group
    val body = BuildHTML.buildGroup(Nullable(base), opts.havingCrampedStyle())

    // Does the accent need to shift for the skew of a character?
    val mustShift = group.isShifty.getOrElse(false) && KatexUtils.isCharacterBox(base)

    // Calculate the skew of the accent. This is based on the line "If the
    // nucleus is not a single character, let s = 0; otherwise set s to the
    // kern amount for the nucleus followed by the \skewchar of its font."
    // Note that our skew metrics are just the kern between each character
    // and the skewchar.
    var skew = 0.0
    if (mustShift) {
      // Read the skew from the rendered base symbol.
      // This preserves font metrics from font wrappers like \mathbb.
      skew = getBaseSymbol(body).fold(0.0)(_.skew)
    }

    val accentBelow = group.label == "\\c"

    // calculate the amount of space between the body and the accent
    var clearance: Double = if (accentBelow) {
      body.height + body.depth
    } else {
      Math.min(body.height, opts.fontMetrics().xHeight)
    }

    // Build the accent
    var accentBody: HtmlDomNode = body // overwritten below in all branches
    if (!group.isStretchy.getOrElse(false)) {
      val (accent: HtmlDomNode, width: Double) = if (group.label == "\\vec") {
        // Before version 0.9, \vec used the combining font glyph U+20D7.
        // But browsers, especially Safari, are not consistent in how they
        // render combining characters when not preceded by a character.
        // So now we use an SVG.
        // If Safari reforms, we should consider reverting to the glyph.
        (BuildCommon.staticSvg("vec", opts), BuildCommon.svgData("vec")._2)
      } else {
        val textordNode = ParseNodeTextord(mode = group.mode, text = group.label)
        val a = BuildCommon.makeOrd(textordNode, opts, "textord")
        val accentSym = a.asInstanceOf[SymbolNode]
        // Remove the italic correction of the accent, because it only serves to
        // shift the accent over to a place we don't want.
        accentSym.italic = 0
        val w = accentSym.width
        if (accentBelow) {
          clearance += accentSym.depth
        }
        (a, w)
      }

      accentBody = BuildCommon.makeSpan(ArrayBuffer("accent-body"), ArrayBuffer(accent))

      // "Full" accents expand the width of the resulting symbol to be
      // at least the width of the accent, and overlap directly onto the
      // character without any vertical offset.
      val accentFull = (group.label == "\\textcircled")
      if (accentFull) {
        accentBody.asInstanceOf[DomSpan].classes += "accent-full"
        clearance = body.height
      }

      // Shift the accent over by the skew.
      var left = skew

      // CSS defines `.katex .accent .accent-body:not(.accent-full) { width: 0 }`
      // so that the accent doesn't contribute to the bounding box.
      // We need to shift the character by its width (effectively half
      // its width) to compensate.
      if (!accentFull) {
        left -= width / 2
      }

      accentBody.asInstanceOf[DomSpan].style = accentBody.asInstanceOf[DomSpan].style
        .copy(left = Nullable(Units.makeEm(left)))

      // \textcircled uses the \bigcirc glyph, so it needs some
      // vertical adjustment to match LaTeX.
      if (group.label == "\\textcircled") {
        accentBody.asInstanceOf[DomSpan].style = accentBody.asInstanceOf[DomSpan].style
          .copy(top = Nullable(".2em"))
      }

      accentBody = BuildCommon.makeVList(VListParam.FirstBaseline(
        children = Array(
          VListElem(elem = body),
          VListKern(-clearance),
          VListElem(elem = accentBody)
        )
      ), opts)

    } else {
      accentBody = Stretchy.stretchySvg(group, opts)

      val wStyle: Nullable[CssStyle] = if (skew > 0) {
        Nullable(CssStyle(
          width = Nullable(s"calc(100% - ${Units.makeEm(2 * skew)})"),
          marginLeft = Nullable(Units.makeEm(2 * skew))
        ))
      } else Nullable.Null

      accentBody = BuildCommon.makeVList(VListParam.FirstBaseline(
        children = Array(
          VListElem(elem = body),
          VListElem(
            elem = accentBody,
            wrapperClasses = wStyle.fold(Array.empty[String])(_ => Array("svg-align")),
            wrapperStyle = wStyle.getOrElse(CssStyle())
          )
        )
      ), opts)
    }

    val accentWrap =
      BuildCommon.makeSpan(ArrayBuffer("mord", "accent"), ArrayBuffer(accentBody), Nullable(opts))

    if (supSubGroup.isDefined) {
      // Here, we replace the "base" child of the supsub with our newly
      // generated accent.
      val ssg = supSubGroup.get
      ssg.children(0) = accentWrap

      // Since we don't rerun the height calculation after replacing the
      // accent, we manually recalculate height.
      ssg.height = Math.max(accentWrap.height, ssg.height)

      // Accents should always be ords, even when their innards are not.
      ssg.classes(0) = "mord"

      ssg
    } else {
      accentWrap
    }
  }

  val mathmlBuilder: MathMLBuilder = (group, options) => {
    val g = group.asInstanceOf[ParseNodeAccent]
    val opts = options.asInstanceOf[Options]
    val accentNode =
      if (g.isStretchy.getOrElse(false))
        Stretchy.stretchyMathML(g.label)
      else
        new MathNode("mo", ArrayBuffer(BuildMathML.makeText(g.label, g.mode)))

    val node = new MathNode(
      "mover",
      ArrayBuffer(BuildMathML.buildGroup(g.base, opts), accentNode))

    node.setAttribute("accent", "true")

    node
  }

  private val NON_STRETCHY_ACCENT_REGEX: Regex = (
    Array("\\\\acute", "\\\\grave", "\\\\ddot", "\\\\tilde", "\\\\bar", "\\\\breve",
      "\\\\check", "\\\\hat", "\\\\vec", "\\\\dot", "\\\\mathring")
      .mkString("|")
  ).r

  def register(): Unit = {
    // Accents
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "accent",
      names = Array(
        "\\acute", "\\grave", "\\ddot", "\\tilde", "\\bar", "\\breve",
        "\\check", "\\hat", "\\vec", "\\dot", "\\mathring", "\\widecheck",
        "\\widehat", "\\widetilde", "\\overrightarrow", "\\overleftarrow",
        "\\Overrightarrow", "\\overleftrightarrow", "\\overgroup",
        "\\overlinesegment", "\\overleftharpoon", "\\overrightharpoon"
      ),
      props = FunctionPropSpec(numArgs = 1),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        val base = FunctionDef.normalizeArgument(args(0))

        val isStretchy = !NON_STRETCHY_ACCENT_REGEX.findFirstIn(context.funcName).isDefined
        val isShifty = !isStretchy ||
          context.funcName == "\\widehat" ||
          context.funcName == "\\widetilde" ||
          context.funcName == "\\widecheck"

        ParseNodeAccent(
          mode = parser.mode,
          label = context.funcName,
          isStretchy = Nullable(isStretchy),
          isShifty = Nullable(isShifty),
          base = base
        )
      }),
      htmlBuilder = Nullable(htmlBuilder),
      mathmlBuilder = Nullable(mathmlBuilder)
    ))

    // Text-mode accents
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "accent",
      names = Array(
        "\\'", "\\`", "\\^", "\\~", "\\=", "\\u", "\\.", "\\\"",
        "\\c", "\\r", "\\H", "\\v", "\\textcircled"
      ),
      props = FunctionPropSpec(
        numArgs = 1,
        allowedInText = true,
        allowedInMath = true, // unless in strict mode
        argTypes = Nullable(Array(ArgType.Primitive))
      ),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        val base = args(0)
        var mode = parser.mode

        if (mode == Mode.Math) {
          parser.settings.reportNonstrict("mathVsTextAccents",
            s"LaTeX's accent ${context.funcName} works only in text mode")
          mode = Mode.Text
        }

        ParseNodeAccent(
          mode = mode,
          label = context.funcName,
          isStretchy = Nullable(false),
          isShifty = Nullable(true),
          base = base
        )
      }),
      htmlBuilder = Nullable(htmlBuilder),
      mathmlBuilder = Nullable(mathmlBuilder)
    ))
  }
}
