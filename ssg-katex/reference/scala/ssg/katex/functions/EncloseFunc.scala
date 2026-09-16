/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Enclose commands: \cancel, \bcancel, \xcancel, \sout, \fbox,
 * \colorbox, \fcolorbox, \phase, \angl.
 *
 * Original source: katex src/functions/enclose.ts
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

import lowlevel.Nullable
import ssg.katex.build.{ BuildCommon, BuildHTML, BuildMathML, Stretchy, VListElemAndShift, VListParam }
import ssg.katex.data.{ Measurement, SvgGeometry, Units }
import ssg.katex.parse._
import ssg.katex.tree.{ DomSpan, HtmlDomNode, MathNode, PathNode, SvgNode }
import ssg.katex.util.{ Utils => KatexUtils }

object EncloseFunc {

  private val htmlBuilder: HtmlBuilder = (group, options) => {
    val g    = group.asInstanceOf[ParseNodeEnclose]
    val opts = options.asInstanceOf[Options]
    // \cancel, \bcancel, \xcancel, \sout, \fbox, \colorbox, \fcolorbox, \phase
    // Some groups can return document fragments.  Handle those by wrapping
    // them in a span.
    val inner = BuildCommon.wrapFragment(BuildHTML.buildGroup(Nullable(g.body), opts), opts)

    val label = g.label.substring(1)
    var scale = opts.sizeMultiplier
    var img: HtmlDomNode = BuildCommon.makeSpan() // overwritten below in all branches
    var imgShift = 0.0

    // In the LaTeX cancel package, line geometry is slightly different
    // depending on whether the subject is wider than it is tall, or vice versa.
    // We don't know the width of a group, so as a proxy, we test if
    // the subject is a single character. This captures most of the
    // subjects that should get the "tall" treatment.
    val isSingleChar = KatexUtils.isCharacterBox(g.body)

    if (label == "sout") {
      img = BuildCommon.makeSpan(ArrayBuffer("stretchy", "sout"))
      img.height = opts.fontMetrics().defaultRuleThickness / scale
      imgShift = -0.5 * opts.fontMetrics().xHeight

    } else if (label == "phase") {
      // Set a couple of dimensions from the steinmetz package.
      val lineWeight = Units.calculateSize(Measurement(0.6, "pt"), opts)
      val clearance  = Units.calculateSize(Measurement(0.35, "ex"), opts)

      // Prevent size changes like \Huge from affecting line thickness
      val newOptions = opts.havingBaseSizing()
      scale = scale / newOptions.sizeMultiplier

      val angleHeight = inner.height + inner.depth + lineWeight + clearance
      // Reserve a left pad for the angle.
      inner.asInstanceOf[DomSpan].style = inner.asInstanceOf[DomSpan].style.copy(paddingLeft = Nullable(Units.makeEm(angleHeight / 2 + lineWeight)))

      // Create an SVG
      val viewBoxHeight = Math.floor(1000 * angleHeight * scale).toInt
      val path          = SvgGeometry.phasePath(viewBoxHeight)
      val svgNode       = new SvgNode(
        ArrayBuffer(new PathNode("phase", path)),
        scala.collection.mutable.LinkedHashMap(
          "width" -> "400em",
          "height" -> Units.makeEm(viewBoxHeight.toDouble / 1000),
          "viewBox" -> s"0 0 400000 $viewBoxHeight",
          "preserveAspectRatio" -> "xMinYMin slice"
        )
      )
      // Wrap it in a span with overflow: hidden.
      img = BuildCommon.makeSvgSpan(ArrayBuffer("hide-tail"), ArrayBuffer(svgNode), Nullable(opts))
      img.asInstanceOf[DomSpan].style = img.asInstanceOf[DomSpan].style.copy(height = Nullable(Units.makeEm(angleHeight)))
      imgShift = inner.depth + lineWeight + clearance

    } else {
      // Add horizontal padding
      if ("cancel".r.findFirstIn(label).isDefined) {
        if (!isSingleChar) {
          inner.asInstanceOf[DomSpan].classes += "cancel-pad"
        }
      } else if (label == "angl") {
        inner.asInstanceOf[DomSpan].classes += "anglpad"
      } else {
        inner.asInstanceOf[DomSpan].classes += "boxpad"
      }

      // Add vertical padding
      var topPad        = 0.0
      var bottomPad     = 0.0
      var ruleThickness = 0.0
      // ref: cancel package: \advance\totalheight2\p@ % "+2"
      if ("box".r.findFirstIn(label).isDefined) {
        ruleThickness = Math.max(
          opts.fontMetrics().fboxrule, // default
          opts.minRuleThickness // User override.
        )
        topPad = opts.fontMetrics().fboxsep +
          (if (label == "colorbox") 0 else ruleThickness)
        bottomPad = topPad
      } else if (label == "angl") {
        ruleThickness = Math.max(
          opts.fontMetrics().defaultRuleThickness,
          opts.minRuleThickness
        )
        topPad = 4 * ruleThickness // gap = 3 × line, plus the line itself.
        bottomPad = Math.max(0, 0.25 - inner.depth)
      } else {
        topPad = if (isSingleChar) 0.2 else 0.0
        bottomPad = topPad
      }

      img = Stretchy.stretchyEnclose(inner, label, topPad, bottomPad, opts)
      if ("fbox|boxed|fcolorbox".r.findFirstIn(label).isDefined) {
        img.asInstanceOf[DomSpan].style = img
          .asInstanceOf[DomSpan]
          .style
          .copy(
            borderStyle = Nullable("solid"),
            borderWidth = Nullable(Units.makeEm(ruleThickness))
          )
      } else if (label == "angl" && ruleThickness != 0.049) {
        img.asInstanceOf[DomSpan].style = img
          .asInstanceOf[DomSpan]
          .style
          .copy(
            borderTopWidth = Nullable(Units.makeEm(ruleThickness)),
            borderRightWidth = Nullable(Units.makeEm(ruleThickness))
          )
      }
      imgShift = inner.depth + bottomPad

      if (g.backgroundColor.isDefined) {
        img.asInstanceOf[DomSpan].style = img
          .asInstanceOf[DomSpan]
          .style
          .copy(
            backgroundColor = g.backgroundColor
          )
        g.borderColor.foreach { bc =>
          img.asInstanceOf[DomSpan].style = img
            .asInstanceOf[DomSpan]
            .style
            .copy(
              borderColor = Nullable(bc)
            )
        }
      }
    }

    val vlist: DomSpan = if (g.backgroundColor.isDefined) {
      BuildCommon.makeVList(
        VListParam.IndividualShift(
          children = Array(
            // Put the color background behind inner;
            VListElemAndShift(elem = img, shift = imgShift),
            VListElemAndShift(elem = inner, shift = 0)
          )
        ),
        opts
      )
    } else {
      val classes = if ("cancel|phase".r.findFirstIn(label).isDefined) Array("svg-align") else Array.empty[String]
      BuildCommon.makeVList(
        VListParam.IndividualShift(
          children = Array(
            // Write the \cancel stroke on top of inner.
            VListElemAndShift(elem = inner, shift = 0),
            VListElemAndShift(
              elem = img,
              shift = imgShift,
              wrapperClasses = classes
            )
          )
        ),
        opts
      )
    }

    if ("cancel".r.findFirstIn(label).isDefined) {
      // The cancel package documentation says that cancel lines add their height
      // to the expression, but tests show that isn't how it actually works.
      vlist.height = inner.height
      vlist.depth = inner.depth
    }

    if ("cancel".r.findFirstIn(label).isDefined && !isSingleChar) {
      // cancel does not create horiz space for its line extension.
      BuildCommon.makeSpan(ArrayBuffer("mord", "cancel-lap"), ArrayBuffer(vlist), Nullable(opts))
    } else {
      BuildCommon.makeSpan(ArrayBuffer("mord"), ArrayBuffer(vlist), Nullable(opts))
    }
  }

  private val mathmlBuilder: MathMLBuilder = (group, options) => {
    val g       = group.asInstanceOf[ParseNodeEnclose]
    val opts    = options.asInstanceOf[Options]
    var fboxsep = 0.0
    val node    = new MathNode(
      if (g.label.contains("colorbox")) "mpadded" else "menclose",
      ArrayBuffer(BuildMathML.buildGroup(g.body, opts))
    )
    g.label match {
      case "\\cancel" =>
        node.setAttribute("notation", "updiagonalstrike")
      case "\\bcancel" =>
        node.setAttribute("notation", "downdiagonalstrike")
      case "\\phase" =>
        node.setAttribute("notation", "phasorangle")
      case "\\sout" =>
        node.setAttribute("notation", "horizontalstrike")
      case "\\fbox" =>
        node.setAttribute("notation", "box")
      case "\\angl" =>
        node.setAttribute("notation", "actuarial")
      case "\\fcolorbox" | "\\colorbox" =>
        // <menclose> doesn't have a good notation option. So use <mpadded>
        // instead. Set some attributes that come included with <menclose>.
        fboxsep = opts.fontMetrics().fboxsep *
          opts.fontMetrics().ptPerEm
        node.setAttribute("width", s"+${2 * fboxsep}pt")
        node.setAttribute("height", s"+${2 * fboxsep}pt")
        node.setAttribute("lspace", s"${fboxsep}pt")
        node.setAttribute("voffset", s"${fboxsep}pt")
        if (g.label == "\\fcolorbox") {
          val thk = Math.max(
            opts.fontMetrics().fboxrule, // default
            opts.minRuleThickness // user override
          )
          node.setAttribute("style", s"border: ${Units.makeEm(thk)} solid ${g.borderColor.getOrElse("")}")
        }
      case "\\xcancel" =>
        node.setAttribute("notation", "updiagonalstrike downdiagonalstrike")
      case _ => // do nothing
    }
    if (g.backgroundColor.isDefined) {
      node.setAttribute("mathbackground", g.backgroundColor.get)
    }
    node
  }

  def register(): Unit = {
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "enclose",
        names = Array("\\colorbox"),
        props = FunctionPropSpec(
          numArgs = 2,
          allowedInText = true,
          argTypes = Nullable(Array(ArgType.Color, ArgType.TextMode))
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          val color  = ParseNode.assertNodeType(Nullable(args(0)), "color-token").asInstanceOf[ParseNodeColorToken].color
          val body   = args(1)
          ParseNodeEnclose(
            mode = parser.mode,
            label = context.funcName,
            backgroundColor = Nullable(color),
            body = body
          )
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "enclose",
        names = Array("\\fcolorbox"),
        props = FunctionPropSpec(
          numArgs = 3,
          allowedInText = true,
          argTypes = Nullable(Array(ArgType.Color, ArgType.Color, ArgType.TextMode))
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser          = context.parser.asInstanceOf[Parser]
          val borderColor     = ParseNode.assertNodeType(Nullable(args(0)), "color-token").asInstanceOf[ParseNodeColorToken].color
          val backgroundColor = ParseNode.assertNodeType(Nullable(args(1)), "color-token").asInstanceOf[ParseNodeColorToken].color
          val body            = args(2)
          ParseNodeEnclose(
            mode = parser.mode,
            label = context.funcName,
            backgroundColor = Nullable(backgroundColor),
            borderColor = Nullable(borderColor),
            body = body
          )
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "enclose",
        names = Array("\\fbox"),
        props = FunctionPropSpec(
          numArgs = 1,
          argTypes = Nullable(Array(ArgType.Hbox)),
          allowedInText = true
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          ParseNodeEnclose(
            mode = parser.mode,
            label = "\\fbox",
            body = args(0)
          )
        }
      )
    )

    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "enclose",
        names = Array("\\cancel", "\\bcancel", "\\xcancel", "\\phase"),
        props = FunctionPropSpec(numArgs = 1),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          val body   = args(0)
          ParseNodeEnclose(
            mode = parser.mode,
            label = context.funcName,
            body = body
          )
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "enclose",
        names = Array("\\sout"),
        props = FunctionPropSpec(
          numArgs = 1,
          allowedInText = true
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          if (parser.mode == Mode.Math) {
            parser.settings.reportNonstrict("mathVsSout", "LaTeX's \\sout works only in text mode")
          }
          val body = args(0)
          ParseNodeEnclose(
            mode = parser.mode,
            label = context.funcName,
            body = body
          )
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "enclose",
        names = Array("\\angl"),
        props = FunctionPropSpec(
          numArgs = 1,
          argTypes = Nullable(Array(ArgType.Hbox)),
          allowedInText = false
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          ParseNodeEnclose(
            mode = parser.mode,
            label = "\\angl",
            body = args(0)
          )
        }
      )
    )
  }
}
