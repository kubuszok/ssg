/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Big operators: \sum, \prod, \int, \lim, \sin, \mathop, etc.
 * NOTE: Unlike most `htmlBuilder`s, this one handles not only "op", but also
 * "supsub" since some of them (like \int) can affect super/subscripting.
 *
 * Original source: katex src/functions/op.ts
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
import ssg.katex.build.{ BuildCommon, BuildHTML, BuildMathML, VListElemAndShift, VListParam }
import ssg.katex.data.Units
import ssg.katex.functions.utils.AssembleSupSub
import ssg.katex.parse._
import ssg.katex.tree.{ DomSpan, HtmlDomNode, MathDomNode, MathNode, SymbolNode, TextNode }

object OpFunc {

  // Most operators have a large successor symbol, but these don't.
  private val noSuccessor: Set[String] = Set("\\smallint")

  // NOTE: Unlike most `htmlBuilder`s, this one handles not only "op", but also
  // "supsub" since some of them (like \int) can affect super/subscripting.
  val htmlBuilder: HtmlBuilder = (grp, options) => {
    val opts = options.asInstanceOf[Options]
    // Operators are handled in the TeXbook pg. 443-444, rule 13(a).
    var supGroup: Nullable[AnyParseNode] = Nullable.Null
    var subGroup: Nullable[AnyParseNode] = Nullable.Null
    var hasLimits = false
    val group: ParseNodeOp = if (grp.nodeType == "supsub") {
      val ss = grp.asInstanceOf[ParseNodeSupsub]
      // If we have limits, supsub will pass us its group to handle.
      supGroup = ss.sup
      subGroup = ss.sub
      hasLimits = true
      ParseNode.assertNodeType(ss.base, "op").asInstanceOf[ParseNodeOp]
    } else {
      ParseNode.assertNodeType(Nullable(grp), "op").asInstanceOf[ParseNodeOp]
    }

    val style = opts.style

    var large = false
    if (
      style.size == Style.DISPLAY.size &&
      group.symbol &&
      !noSuccessor.contains(group.name.getOrElse(""))
    ) {
      // Most symbol operators get larger in displaystyle (rule 13)
      large = true
    }

    var base: HtmlDomNode = BuildCommon.makeSpan() // overwritten below in all branches
    if (group.symbol) {
      // If this is a symbol, create the symbol.
      val fontName = if (large) "Size2-Regular" else "Size1-Regular"

      var stash = ""
      val gName = group.name.getOrElse("")
      if (gName == "\\oiint" || gName == "\\oiiint") {
        // No font glyphs yet, so use a glyph w/o the oval.
        stash = gName.substring(1)
        group.name = Nullable(if (stash == "oiint") "\\iint" else "\\iiint")
      }

      base = BuildCommon.makeSymbol(
        group.name.getOrElse(""),
        fontName,
        Mode.Math,
        Nullable(opts),
        ArrayBuffer("mop", "op-symbol", if (large) "large-op" else "small-op")
      )

      if (stash.nonEmpty) {
        // We're in \oiint or \oiiint. Overlay the oval.
        val oval = BuildCommon.staticSvg(stash + "Size" +
                                           (if (large) "2" else "1"),
                                         opts
        )
        base = BuildCommon.makeVList(
          VListParam.IndividualShift(
            children = Array(
              VListElemAndShift(elem = base, shift = 0),
              VListElemAndShift(elem = oval, shift = if (large) 0.08 else 0)
            )
          ),
          opts
        )
        group.name = Nullable("\\" + stash)
        base.asInstanceOf[DomSpan].classes.prepend("mop")
        // TODO(ts)
        // (base as any).italic = italic;
      }
    } else if (group.body.isDefined) {
      // If this is a list, compose that list.
      val inner = BuildHTML.buildExpression(group.body.get, opts, isRealGroup = true)
      if (inner.length == 1 && inner(0).isInstanceOf[SymbolNode]) {
        base = inner(0)
        base.asInstanceOf[SymbolNode].classes(0) = "mop" // replace old mclass
      } else {
        base = BuildCommon.makeSpan(ArrayBuffer("mop"), inner, Nullable(opts))
      }
    } else {
      // Otherwise, this is a text operator. Build the text from the
      // operator's name.
      val output = ArrayBuffer.empty[HtmlDomNode]
      val name   = group.name.getOrElse("")
      var i      = 1
      while (i < name.length) {
        output += BuildCommon.mathsym(name.charAt(i).toString, group.mode, opts)
        i += 1
      }
      base = BuildCommon.makeSpan(ArrayBuffer("mop"), output, Nullable(opts))
    }

    // If content of op is a single symbol, shift it vertically.
    var baseShift = 0.0
    var slant     = 0.0
    if (
      (base.isInstanceOf[SymbolNode] ||
        group.name.exists(n => n == "\\oiint" || n == "\\oiiint")) &&
      !group.suppressBaseShift.getOrElse(false)
    ) {
      // We suppress the shift of the base of \overset and \underset.
      baseShift = (base.height - base.depth) / 2 -
        opts.fontMetrics().axisHeight

      // The slant of the symbol is just its italic correction.
      slant = if (base.isInstanceOf[SymbolNode]) base.asInstanceOf[SymbolNode].italic else 0.0
    }

    if (hasLimits) {
      AssembleSupSub.assembleSupSub(base, supGroup, subGroup, opts, style, slant, baseShift)
    } else {
      if (baseShift != 0) {
        val htmlBase = base.asInstanceOf[HtmlDomNode]
        htmlBase.style = htmlBase.style.copy(position = Nullable("relative"), top = Nullable(Units.makeEm(baseShift)))
      }
      base
    }
  }

  private val mathmlBuilder: MathMLBuilder = (group, options) => {
    val g    = group.asInstanceOf[ParseNodeOp]
    val opts = options.asInstanceOf[Options]
    var node: MathDomNode = new MathNode("mrow") // overwritten below in all branches

    if (g.symbol) {
      // This is a symbol. Just add the symbol.
      node = new MathNode("mo", ArrayBuffer(BuildMathML.makeText(g.name.getOrElse(""), g.mode)))
      if (noSuccessor.contains(g.name.getOrElse(""))) {
        node.asInstanceOf[MathNode].setAttribute("largeop", "false")
      }
    } else if (g.body.isDefined) {
      // This is an operator with children. Add them.
      node = new MathNode("mo", BuildMathML.buildExpression(g.body.get, opts))
    } else {
      // This is a text operator. Add all the characters from the
      // operator's name.
      node = new MathNode("mi", ArrayBuffer(new TextNode(g.name.getOrElse("").substring(1))))
      // Append an <mo>&ApplyFunction;</mo>.
      // ref: https://www.w3.org/TR/REC-MathML/chap3_2.html#sec3.2.4
      val operator = new MathNode("mo", ArrayBuffer(BuildMathML.makeText("⁡", Mode.Text)))
      if (g.parentIsSupSub) {
        node = new MathNode("mrow", ArrayBuffer(node, operator))
      } else {
        node = tree.newDocumentFragment(IndexedSeq(node, operator))
      }
    }

    node
  }

  private val singleCharBigOps: Map[String, String] = Map(
    "∏" -> "\\prod",
    "∐" -> "\\coprod",
    "∑" -> "\\sum",
    "⋀" -> "\\bigwedge",
    "⋁" -> "\\bigvee",
    "⋂" -> "\\bigcap",
    "⋃" -> "\\bigcup",
    "⨀" -> "\\bigodot",
    "⨁" -> "\\bigoplus",
    "⨂" -> "\\bigotimes",
    "⨄" -> "\\biguplus",
    "⨆" -> "\\bigsqcup"
  )

  private val singleCharIntegrals: Map[String, String] = Map(
    "∫" -> "\\int",
    "∬" -> "\\iint",
    "∭" -> "\\iiint",
    "∮" -> "\\oint",
    "∯" -> "\\oiint",
    "∰" -> "\\oiiint"
  )

  def register(): Unit = {
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "op",
        names = Array(
          "\\coprod",
          "\\bigvee",
          "\\bigwedge",
          "\\biguplus",
          "\\bigcap",
          "\\bigcup",
          "\\intop",
          "\\prod",
          "\\sum",
          "\\bigotimes",
          "\\bigoplus",
          "\\bigodot",
          "\\bigsqcup",
          "\\smallint",
          "∏",
          "∐",
          "∑",
          "⋀",
          "⋁",
          "⋂",
          "⋃",
          "⨀",
          "⨁",
          "⨂",
          "⨄",
          "⨆"
        ),
        props = FunctionPropSpec(numArgs = 0),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          var fName  = context.funcName
          if (fName.length == 1) {
            fName = singleCharBigOps(fName)
          }
          ParseNodeOp(
            mode = parser.mode,
            limits = true,
            parentIsSupSub = false,
            symbol = true,
            name = Nullable(fName)
          )
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    // Note: calling defineFunction with a type that's already been defined only
    // works because the same htmlBuilder and mathmlBuilder are being used.
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "op",
        names = Array("\\mathop"),
        props = FunctionPropSpec(
          numArgs = 1,
          primitive = true
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          val body   = args(0)
          ParseNodeOp(
            mode = parser.mode,
            limits = false,
            parentIsSupSub = false,
            symbol = false,
            body = Nullable(FunctionDef.ordargument(body))
          )
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    // No limits, not symbols
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "op",
        names = Array(
          "\\arcsin",
          "\\arccos",
          "\\arctan",
          "\\arctg",
          "\\arcctg",
          "\\arg",
          "\\ch",
          "\\cos",
          "\\cosec",
          "\\cosh",
          "\\cot",
          "\\cotg",
          "\\coth",
          "\\csc",
          "\\ctg",
          "\\cth",
          "\\deg",
          "\\dim",
          "\\exp",
          "\\hom",
          "\\ker",
          "\\lg",
          "\\ln",
          "\\log",
          "\\sec",
          "\\sin",
          "\\sinh",
          "\\sh",
          "\\tan",
          "\\tanh",
          "\\tg",
          "\\th"
        ),
        props = FunctionPropSpec(numArgs = 0),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          ParseNodeOp(
            mode = parser.mode,
            limits = false,
            parentIsSupSub = false,
            symbol = false,
            name = Nullable(context.funcName)
          )
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    // Limits, not symbols
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "op",
        names = Array(
          "\\det",
          "\\gcd",
          "\\inf",
          "\\lim",
          "\\max",
          "\\min",
          "\\Pr",
          "\\sup"
        ),
        props = FunctionPropSpec(numArgs = 0),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          ParseNodeOp(
            mode = parser.mode,
            limits = true,
            parentIsSupSub = false,
            symbol = false,
            name = Nullable(context.funcName)
          )
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )

    // No limits, symbols
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "op",
        names = Array(
          "\\int",
          "\\iint",
          "\\iiint",
          "\\oint",
          "\\oiint",
          "\\oiiint",
          "∫",
          "∬",
          "∭",
          "∮",
          "∯",
          "∰"
        ),
        props = FunctionPropSpec(
          numArgs = 0,
          allowedInArgument = true
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          var fName  = context.funcName
          if (fName.length == 1) {
            fName = singleCharIntegrals(fName)
          }
          ParseNodeOp(
            mode = parser.mode,
            limits = false,
            parentIsSupSub = false,
            symbol = true,
            name = Nullable(fName)
          )
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )
  }
}
