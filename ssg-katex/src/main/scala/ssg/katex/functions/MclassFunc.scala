/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Math class commands: \mathord, \mathbin, \mathrel, etc.
 * Also \stackrel, \overset, \underset.
 *
 * Original source: katex src/functions/mclass.ts
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
import ssg.katex.build.{BuildCommon, BuildHTML, BuildMathML}
import ssg.katex.parse._
import ssg.katex.tree.MathNode
import ssg.katex.util.{Utils => KatexUtils}

object MclassFunc {

  private val htmlBuilder: HtmlBuilder = (group, options) => {
    val g = group.asInstanceOf[ParseNodeMclass]
    val opts = options.asInstanceOf[Options]
    val elements = BuildHTML.buildExpression(g.body, opts, isRealGroup = true)
    BuildCommon.makeSpan(ArrayBuffer(g.mclass), elements, Nullable(opts))
  }

  private val mathmlBuilder: MathMLBuilder = (group, options) => {
    val g = group.asInstanceOf[ParseNodeMclass]
    val opts = options.asInstanceOf[Options]
    var node: MathNode = new MathNode("mrow") // overwritten below in all branches
    val inner = BuildMathML.buildExpression(g.body, opts)

    if (g.mclass == "minner") {
      node = new MathNode("mpadded", inner)
    } else if (g.mclass == "mord") {
      if (g.isCharacterBox) {
        node = new MathNode("mi", inner(0).asInstanceOf[MathNode].children, inner(0).asInstanceOf[MathNode].classes)
        node.attributes ++= inner(0).asInstanceOf[MathNode].attributes
      } else {
        node = new MathNode("mi", inner)
      }
    } else {
      if (g.isCharacterBox) {
        node = new MathNode("mo", inner(0).asInstanceOf[MathNode].children, inner(0).asInstanceOf[MathNode].classes)
        node.attributes ++= inner(0).asInstanceOf[MathNode].attributes
      } else {
        node = new MathNode("mo", inner)
      }

      // Set spacing based on what is the most likely adjacent atom type.
      // See TeXbook p170.
      if (g.mclass == "mbin") {
        node.attributes("lspace") = "0.22em" // medium space
        node.attributes("rspace") = "0.22em"
      } else if (g.mclass == "mpunct") {
        node.attributes("lspace") = "0em"
        node.attributes("rspace") = "0.17em" // thinspace
      } else if (g.mclass == "mopen" || g.mclass == "mclose") {
        node.attributes("lspace") = "0em"
        node.attributes("rspace") = "0em"
      } else if (g.mclass == "minner") {
        node.attributes("lspace") = "0.0556em" // 1 mu is the most likely option
        node.attributes("width") = "+0.1111em"
      }
      // MathML <mo> default space is 5/18 em, so <mrel> needs no action.
      // Ref: https://developer.mozilla.org/en-US/docs/Web/MathML/Element/mo
    }
    node
  }

  /** Exported for use by font.ts and pmb.ts */
  def binrelClass(arg: AnyParseNode): String = {
    // \binrel@ spacing varies with (bin|rel|ord) of the atom in the argument.
    // (by rendering separately and with {}s before and after, and measuring
    // the change in spacing).  We'll do roughly the same by detecting the
    // atom type directly.
    val atom = if (arg.nodeType == "ordgroup") {
      val og = arg.asInstanceOf[ParseNodeOrdgroup]
      if (og.body.nonEmpty) og.body(0) else arg
    } else arg

    if (atom.nodeType == "atom") {
      val a = atom.asInstanceOf[ParseNodeAtom]
      if (a.family == "bin" || a.family == "rel") {
        "m" + a.family
      } else {
        "mord"
      }
    } else {
      "mord"
    }
  }

  def register(): Unit = {
    // Math class commands except \mathop
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "mclass",
      names = Array(
        "\\mathord", "\\mathbin", "\\mathrel", "\\mathopen",
        "\\mathclose", "\\mathpunct", "\\mathinner"
      ),
      props = FunctionPropSpec(
        numArgs = 1,
        primitive = true
      ),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        val body = args(0)
        ParseNodeMclass(
          mode = parser.mode,
          mclass = "m" + context.funcName.substring(5), // TODO(kevinb): don't prefix with 'm'
          body = FunctionDef.ordargument(body),
          isCharacterBox = KatexUtils.isCharacterBox(body)
        )
      }),
      htmlBuilder = Nullable(htmlBuilder),
      mathmlBuilder = Nullable(mathmlBuilder)
    ))

    // \@binrel{x}{y} renders like y but as mbin/mrel/mord if x is mbin/mrel/mord.
    // This is equivalent to \binrel@{x}\binrel@@{y} in AMSTeX.
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "mclass",
      names = Array("\\@binrel"),
      props = FunctionPropSpec(numArgs = 2),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        ParseNodeMclass(
          mode = parser.mode,
          mclass = binrelClass(args(0)),
          body = FunctionDef.ordargument(args(1)),
          isCharacterBox = KatexUtils.isCharacterBox(args(1))
        )
      })
    ))

    // Build a relation or stacked op by placing one symbol on top of another
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "mclass",
      names = Array("\\stackrel", "\\overset", "\\underset"),
      props = FunctionPropSpec(numArgs = 2),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        val baseArg = args(1)
        val shiftedArg = args(0)

        val mclass = if (context.funcName != "\\stackrel") {
          // LaTeX applies \binrel spacing to \overset and \underset.
          binrelClass(baseArg)
        } else {
          "mrel" // for \stackrel
        }

        val baseOp = ParseNodeOp(
          mode = baseArg.mode,
          limits = true,
          alwaysHandleSupSub = Nullable(true),
          parentIsSupSub = false,
          symbol = false,
          suppressBaseShift = Nullable(context.funcName != "\\stackrel"),
          body = Nullable(FunctionDef.ordargument(baseArg))
        )

        val supsub = ParseNodeSupsub(
          mode = shiftedArg.mode,
          base = Nullable(baseOp),
          sup = if (context.funcName == "\\underset") Nullable.Null else Nullable(shiftedArg),
          sub = if (context.funcName == "\\underset") Nullable(shiftedArg) else Nullable.Null
        )

        ParseNodeMclass(
          mode = parser.mode,
          mclass = mclass,
          body = Array(supsub),
          isCharacterBox = KatexUtils.isCharacterBox(supsub)
        )
      }),
      htmlBuilder = Nullable(htmlBuilder),
      mathmlBuilder = Nullable(mathmlBuilder)
    ))
  }
}
