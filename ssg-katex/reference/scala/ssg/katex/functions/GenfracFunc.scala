/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Generalized fraction commands: \frac, \dfrac, \binom, \over, \genfrac, etc.
 *
 * Original source: katex src/functions/genfrac.ts
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
import ssg.katex.build.{ BuildCommon, BuildHTML, BuildMathML, Delimiter, VListElemAndShift, VListParam }
import ssg.katex.data.{ Measurement, Units }
import ssg.katex.parse._
import ssg.katex.tree.{ DomSpan, HtmlDomNode, MathNode, TextNode }

object GenfracFunc {

  private val htmlBuilder: HtmlBuilder = (group, options) => {
    val g    = group.asInstanceOf[ParseNodeGenfrac]
    val opts = options.asInstanceOf[Options]
    // Fractions are handled in the TeXbook on pages 444-445, rules 15(a-e).
    val style = opts.style

    val nstyle = style.fracNum()
    val dstyle = style.fracDen()

    var newOptions = opts.havingStyle(nstyle)
    val numerm     = BuildHTML.buildGroup(Nullable(g.numer), newOptions, Nullable(opts))

    if (g.continued) {
      // \cfrac inserts a \strut into the numerator.
      // Get \strut dimensions from TeXbook page 353.
      val hStrut = 8.5 / opts.fontMetrics().ptPerEm
      val dStrut = 3.5 / opts.fontMetrics().ptPerEm
      numerm.height = if (numerm.height < hStrut) hStrut else numerm.height
      numerm.depth = if (numerm.depth < dStrut) dStrut else numerm.depth
    }

    newOptions = opts.havingStyle(dstyle)
    val denomm = BuildHTML.buildGroup(Nullable(g.denom), newOptions, Nullable(opts))

    var rule:        Nullable[DomSpan] = Nullable.Null
    var ruleWidth:   Double            = 0
    var ruleSpacing: Double            = 0
    if (g.hasBarLine) {
      if (g.barSize.isDefined) {
        ruleWidth = Units.calculateSize(g.barSize.get, opts)
        rule = Nullable(BuildCommon.makeLineSpan("frac-line", opts, Nullable(ruleWidth)))
      } else {
        rule = Nullable(BuildCommon.makeLineSpan("frac-line", opts))
      }
      ruleWidth = rule.get.height
      ruleSpacing = rule.get.height
    } else {
      ruleWidth = 0
      ruleSpacing = opts.fontMetrics().defaultRuleThickness
    }

    // Rule 15b
    var numShift:   Double = 0
    var clearance:  Double = 0
    var denomShift: Double = 0
    if (style.size == Style.DISPLAY.size) {
      numShift = opts.fontMetrics().num1
      if (ruleWidth > 0) {
        clearance = 3 * ruleSpacing
      } else {
        clearance = 7 * ruleSpacing
      }
      denomShift = opts.fontMetrics().denom1
    } else {
      if (ruleWidth > 0) {
        numShift = opts.fontMetrics().num2
        clearance = ruleSpacing
      } else {
        numShift = opts.fontMetrics().num3
        clearance = 3 * ruleSpacing
      }
      denomShift = opts.fontMetrics().denom2
    }

    var frac: DomSpan = BuildCommon.makeSpan() // overwritten below in all branches
    if (rule.isEmpty) {
      // Rule 15c
      val candidateClearance =
        (numShift - numerm.depth) - (denomm.height - denomShift)
      if (candidateClearance < clearance) {
        numShift += 0.5 * (clearance - candidateClearance)
        denomShift += 0.5 * (clearance - candidateClearance)
      }

      frac = BuildCommon.makeVList(
        VListParam.IndividualShift(
          children = Array(
            VListElemAndShift(elem = denomm, shift = denomShift),
            VListElemAndShift(elem = numerm, shift = -numShift)
          )
        ),
        opts
      )
    } else {
      // Rule 15d
      val axisHeight = opts.fontMetrics().axisHeight

      if ((numShift - numerm.depth) - (axisHeight + 0.5 * ruleWidth) < clearance) {
        numShift +=
          clearance - ((numShift - numerm.depth) -
            (axisHeight + 0.5 * ruleWidth))
      }

      if ((axisHeight - 0.5 * ruleWidth) - (denomm.height - denomShift) < clearance) {
        denomShift +=
          clearance - ((axisHeight - 0.5 * ruleWidth) -
            (denomm.height - denomShift))
      }

      val midShift = -(axisHeight - 0.5 * ruleWidth)

      frac = BuildCommon.makeVList(
        VListParam.IndividualShift(
          children = Array(
            VListElemAndShift(elem = denomm, shift = denomShift),
            VListElemAndShift(elem = rule.get, shift = midShift),
            VListElemAndShift(elem = numerm, shift = -numShift)
          )
        ),
        opts
      )
    }

    // Since we manually change the style sometimes (with \dfrac or \tfrac),
    // account for the possible size change here.
    newOptions = opts.havingStyle(style)
    frac.height *= newOptions.sizeMultiplier / opts.sizeMultiplier
    frac.depth *= newOptions.sizeMultiplier / opts.sizeMultiplier

    // Rule 15e
    val delimSize: Double =
      if (style.size == Style.DISPLAY.size) opts.fontMetrics().delim1
      else if (style.size == Style.SCRIPTSCRIPT.size) opts.havingStyle(Style.SCRIPT).fontMetrics().delim2
      else opts.fontMetrics().delim2

    val leftDelim: HtmlDomNode =
      if (g.leftDelim.isEmpty) BuildHTML.makeNullDelimiter(opts, Array("mopen"))
      else Delimiter.makeCustomSizedDelim(g.leftDelim.get, delimSize, true, opts.havingStyle(style), g.mode, Array("mopen"))

    val rightDelim: HtmlDomNode =
      if (g.continued) BuildCommon.makeSpan(ArrayBuffer.empty) // zero width for \cfrac
      else if (g.rightDelim.isEmpty) BuildHTML.makeNullDelimiter(opts, Array("mclose"))
      else Delimiter.makeCustomSizedDelim(g.rightDelim.get, delimSize, true, opts.havingStyle(style), g.mode, Array("mclose"))

    BuildCommon.makeSpan(
      ArrayBuffer("mord") ++ newOptions.sizingClasses(opts),
      ArrayBuffer(leftDelim, BuildCommon.makeSpan(ArrayBuffer("mfrac"), ArrayBuffer(frac)), rightDelim),
      Nullable(opts)
    )
  }

  private val mathmlBuilder: MathMLBuilder = (group, options) => {
    val g    = group.asInstanceOf[ParseNodeGenfrac]
    val opts = options.asInstanceOf[Options]
    val node = new MathNode("mfrac",
                            ArrayBuffer(
                              BuildMathML.buildGroup(g.numer, opts),
                              BuildMathML.buildGroup(g.denom, opts)
                            )
    )

    if (!g.hasBarLine) {
      node.setAttribute("linethickness", "0px")
    } else if (g.barSize.isDefined) {
      val ruleWidth = Units.calculateSize(g.barSize.get, opts)
      node.setAttribute("linethickness", Units.makeEm(ruleWidth))
    }

    if (g.leftDelim.isDefined || g.rightDelim.isDefined) {
      val withDelims = ArrayBuffer.empty[tree.MathDomNode]

      if (g.leftDelim.isDefined) {
        val leftOp = new MathNode(
          "mo",
          ArrayBuffer(new TextNode(g.leftDelim.get.replace("\\", "")))
        )
        leftOp.setAttribute("fence", "true")
        withDelims += leftOp
      }

      withDelims += node

      if (g.rightDelim.isDefined) {
        val rightOp = new MathNode(
          "mo",
          ArrayBuffer(new TextNode(g.rightDelim.get.replace("\\", "")))
        )
        rightOp.setAttribute("fence", "true")
        withDelims += rightOp
      }

      BuildMathML.makeRow(withDelims)
    } else {
      node
    }
  }

  private def wrapWithStyle(
    frac:  ParseNodeGenfrac,
    style: Nullable[StyleStr]
  ): AnyParseNode =
    if (style.isEmpty) {
      frac
    } else {
      ParseNodeStyling(
        mode = frac.mode,
        style = style.get,
        body = Array(frac)
      )
    }

  private val stylArray = Array(StyleStr.Display, StyleStr.TextStyle, StyleStr.Script, StyleStr.ScriptScript)

  private def delimFromValue(delimString: String): Nullable[String] =
    if (delimString.nonEmpty) {
      val delim = delimString
      if (delim == ".") Nullable.Null else Nullable(delim)
    } else {
      Nullable.Null
    }

  def register(): Unit = {
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "genfrac",
        names = Array(
          "\\cfrac",
          "\\dfrac",
          "\\frac",
          "\\tfrac",
          "\\dbinom",
          "\\binom",
          "\\tbinom",
          "\\\\atopfrac", // can't be entered directly
          "\\\\bracefrac",
          "\\\\brackfrac" // ditto
        ),
        props = FunctionPropSpec(
          numArgs = 2,
          allowedInArgument = true
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          val numer  = args(0)
          val denom  = args(1)
          var hasBarLine: Boolean          = false
          var leftDelim:  Nullable[String] = Nullable.Null
          var rightDelim: Nullable[String] = Nullable.Null

          context.funcName match {
            case "\\cfrac" | "\\dfrac" | "\\frac" | "\\tfrac" =>
              hasBarLine = true
            case "\\\\atopfrac" =>
              hasBarLine = false
            case "\\dbinom" | "\\binom" | "\\tbinom" =>
              hasBarLine = false
              leftDelim = Nullable("(")
              rightDelim = Nullable(")")
            case "\\\\bracefrac" =>
              hasBarLine = false
              leftDelim = Nullable("\\{")
              rightDelim = Nullable("\\}")
            case "\\\\brackfrac" =>
              hasBarLine = false
              leftDelim = Nullable("[")
              rightDelim = Nullable("]")
            case _ =>
              throw new Error("Unrecognized genfrac command")
          }

          val continued = context.funcName == "\\cfrac"
          val style: Nullable[StyleStr] =
            if (continued || context.funcName.startsWith("\\d")) Nullable(StyleStr.Display)
            else if (context.funcName.startsWith("\\t")) Nullable(StyleStr.TextStyle)
            else Nullable.Null

          wrapWithStyle(
            ParseNodeGenfrac(
              mode = parser.mode,
              numer = numer,
              denom = denom,
              continued = continued,
              hasBarLine = hasBarLine,
              leftDelim = leftDelim,
              rightDelim = rightDelim,
              barSize = Nullable.Null
            ),
            style
          )
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    // Infix generalized fractions -- these are not rendered directly, but replaced
    // immediately by one of the variants above.
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "infix",
        names = Array("\\over", "\\choose", "\\atop", "\\brace", "\\brack"),
        props = FunctionPropSpec(
          numArgs = 0,
          infix = true
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser      = context.parser.asInstanceOf[Parser]
          val replaceWith = context.funcName match {
            case "\\over"   => "\\frac"
            case "\\choose" => "\\binom"
            case "\\atop"   => "\\\\atopfrac"
            case "\\brace"  => "\\\\bracefrac"
            case "\\brack"  => "\\\\brackfrac"
            case _          => throw new Error("Unrecognized infix genfrac command")
          }
          ParseNodeInfix(
            mode = parser.mode,
            replaceWith = replaceWith,
            token = context.token
          )
        }
      )
    )

    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "genfrac",
        names = Array("\\genfrac"),
        props = FunctionPropSpec(
          numArgs = 6,
          allowedInArgument = true,
          argTypes = Nullable(Array(ArgType.MathMode, ArgType.MathMode, ArgType.Size, ArgType.TextMode, ArgType.MathMode, ArgType.MathMode))
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          val numer  = args(4)
          val denom  = args(5)

          // Look into the parse nodes to get the desired delimiters.
          val leftNode = FunctionDef.normalizeArgument(args(0))
          val leftDelim: Nullable[String] =
            if (leftNode.nodeType == "atom" && leftNode.asInstanceOf[ParseNodeAtom].family == "open")
              delimFromValue(leftNode.asInstanceOf[ParseNodeAtom].text)
            else Nullable.Null
          val rightNode = FunctionDef.normalizeArgument(args(1))
          val rightDelim: Nullable[String] =
            if (rightNode.nodeType == "atom" && rightNode.asInstanceOf[ParseNodeAtom].family == "close")
              delimFromValue(rightNode.asInstanceOf[ParseNodeAtom].text)
            else Nullable.Null

          val barNode = ParseNode.assertNodeType(Nullable(args(2)), "size").asInstanceOf[ParseNodeSize]
          var hasBarLine: Boolean               = false
          var barSize:    Nullable[Measurement] = Nullable.Null
          if (barNode.isBlank) {
            // \genfrac acts differently than \above.
            // \genfrac treats an empty size group as a signal to use a
            // standard bar size. \above would see size = 0 and omit the bar.
            hasBarLine = true
          } else {
            barSize = Nullable(barNode.value)
            hasBarLine = barNode.value.number > 0
          }

          // Find out if we want displaystyle, textstyle, etc.
          var size: Nullable[StyleStr] = Nullable.Null
          var styl = args(3)
          if (styl.nodeType == "ordgroup") {
            val og = styl.asInstanceOf[ParseNodeOrdgroup]
            if (og.body.nonEmpty) {
              val textOrd = ParseNode.assertNodeType(Nullable(og.body(0)), "textord").asInstanceOf[ParseNodeTextord]
              val idx     = textOrd.text.toInt
              if (idx >= 0 && idx < stylArray.length) {
                size = Nullable(stylArray(idx))
              }
            }
          } else {
            styl = ParseNode.assertNodeType(Nullable(styl), "textord")
            val idx = styl.asInstanceOf[ParseNodeTextord].text.toInt
            if (idx >= 0 && idx < stylArray.length) {
              size = Nullable(stylArray(idx))
            }
          }

          wrapWithStyle(
            ParseNodeGenfrac(
              mode = parser.mode,
              numer = numer,
              denom = denom,
              continued = false,
              hasBarLine = hasBarLine,
              barSize = barSize,
              leftDelim = leftDelim,
              rightDelim = rightDelim
            ),
            size
          )
        }
      )
    )

    // \above is an infix fraction that also defines a fraction bar size.
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "infix",
        names = Array("\\above"),
        props = FunctionPropSpec(
          numArgs = 1,
          argTypes = Nullable(Array(ArgType.Size)),
          infix = true
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          ParseNodeInfix(
            mode = parser.mode,
            replaceWith = "\\\\abovefrac",
            size = Nullable(ParseNode.assertNodeType(Nullable(args(0)), "size").asInstanceOf[ParseNodeSize].value),
            token = context.token
          )
        }
      )
    )

    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "genfrac",
        names = Array("\\\\abovefrac"),
        props = FunctionPropSpec(
          numArgs = 3,
          argTypes = Nullable(Array(ArgType.MathMode, ArgType.Size, ArgType.MathMode))
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser  = context.parser.asInstanceOf[Parser]
          val numer   = args(0)
          val barSize = ParseNode.assertNodeType(Nullable(args(1)), "infix").asInstanceOf[ParseNodeInfix].size

          if (barSize.isEmpty) {
            throw new Error(s"\\\\abovefrac expected size, but got ${barSize.toString}")
          }

          val denom = args(2)

          val hasBarLine = barSize.get.number > 0
          ParseNodeGenfrac(
            mode = parser.mode,
            numer = numer,
            denom = denom,
            continued = false,
            hasBarLine = hasBarLine,
            barSize = barSize,
            leftDelim = Nullable.Null,
            rightDelim = Nullable.Null
          )
        }
      )
    )
  }
}
