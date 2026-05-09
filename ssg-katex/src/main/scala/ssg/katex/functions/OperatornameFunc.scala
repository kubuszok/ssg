/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * \operatorname and \operatornamewithlimits.
 * NOTE: Unlike most `htmlBuilder`s, this one handles not only
 * "operatorname", but also "supsub" since \operatorname* can
 * affect super/subscripting.
 *
 * Original source: katex src/functions/operatorname.ts
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
import ssg.katex.MacroDef
import ssg.katex.functions.utils.AssembleSupSub
import ssg.katex.parse._
import ssg.katex.tree.{MathDomNode, MathNode, SpaceNode, SymbolNode, TextNode}

object OperatornameFunc {

  // NOTE: Unlike most `htmlBuilder`s, this one handles not only
  // "operatorname", but also  "supsub" since \operatorname* can
  // affect super/subscripting.
  val htmlBuilder: HtmlBuilder = (grp, options) => {
    val opts = options.asInstanceOf[Options]
    // Operators are handled in the TeXbook pg. 443-444, rule 13(a).
    var supGroup: Nullable[AnyParseNode] = Nullable.Null
    var subGroup: Nullable[AnyParseNode] = Nullable.Null
    var hasLimits = false
    val group: ParseNodeOperatorname = if (grp.nodeType == "supsub") {
      val ss = grp.asInstanceOf[ParseNodeSupsub]
      supGroup = ss.sup
      subGroup = ss.sub
      hasLimits = true
      ParseNode.assertNodeType(ss.base, "operatorname").asInstanceOf[ParseNodeOperatorname]
    } else {
      ParseNode.assertNodeType(Nullable(grp), "operatorname").asInstanceOf[ParseNodeOperatorname]
    }

    var base: tree.HtmlDomNode = BuildCommon.makeSpan() // overwritten below in all branches
    if (group.body.nonEmpty) {
      val body: Array[AnyParseNode] = group.body.map { child =>
        child match {
          case spn: SymbolParseNode =>
            ParseNodeTextord(
              mode = child.mode,
              text = spn.text
            )
          case _ => child
        }
      }

      // Consolidate function names into symbol characters.
      val expression = BuildHTML.buildExpression(
        body, opts.withFont("mathrm"), isRealGroup = true)

      var i = 0
      while (i < expression.length) {
        val child = expression(i)
        child match {
          case sn: SymbolNode =>
            // Per amsopn package,
            // change minus to hyphen and \ast to asterisk
            sn.text = sn.text.replace("−", "-").replace("∗", "*")
          case _ => // do nothing
        }
        i += 1
      }
      base = BuildCommon.makeSpan(ArrayBuffer("mop"), expression, Nullable(opts))
    } else {
      base = BuildCommon.makeSpan(ArrayBuffer("mop"), ArrayBuffer.empty, Nullable(opts))
    }

    if (hasLimits) {
      AssembleSupSub.assembleSupSub(base, supGroup, subGroup, opts,
        opts.style, 0, 0)
    } else {
      base
    }
  }

  private val mathmlBuilder: MathMLBuilder = (group, options) => {
    val g = group.asInstanceOf[ParseNodeOperatorname]
    val opts = options.asInstanceOf[Options]
    // The steps taken here are similar to the html version.
    var expression: ArrayBuffer[MathDomNode] = BuildMathML.buildExpression(
      g.body, opts.withFont("mathrm"))

    // Is expression a string or has it something like a fraction?
    var isAllString = true // default
    var i = 0
    while (i < expression.length) {
      val node = expression(i)
      node match {
        case _: SpaceNode => // Do nothing
        case mn: MathNode =>
          mn.nodeType match {
            case "mi" | "mn" | "mspace" | "mtext" => // Do nothing yet.
            case "mo" =>
              val child = mn.children(0)
              if (mn.children.length == 1 && child.isInstanceOf[TextNode]) {
                val replaced = child.asInstanceOf[TextNode].text
                  .replace("−", "-")
                  .replace("∗", "*")
                mn.children(0) = new TextNode(replaced)
              } else {
                isAllString = false
              }
            case _ =>
              isAllString = false
          }
        case _ =>
          isAllString = false
      }
      i += 1
    }

    if (isAllString) {
      // Write a single TextNode instead of multiple nested tags.
      val word = expression.map(node => node.toText()).mkString("")
      expression = ArrayBuffer[MathDomNode](new TextNode(word))
    }

    val identifier = new MathNode("mi", expression)
    identifier.setAttribute("mathvariant", "normal")

    // ⁡ is the same as &ApplyFunction;
    // ref: https://www.w3schools.com/charsets/ref_html_entities_a.asp
    val operator = new MathNode("mo",
      ArrayBuffer(BuildMathML.makeText("⁡", Mode.Text)))

    if (g.parentIsSupSub) {
      new MathNode("mrow", ArrayBuffer(identifier, operator))
    } else {
      tree.newDocumentFragment(IndexedSeq(identifier, operator))
    }
  }

  def register(): Unit = {
    // \operatorname
    // amsopn.dtx: \mathop{#1\kern\z@\operator@font#3}\newmcodes@
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "operatorname",
      names = Array("\\operatorname@", "\\operatornamewithlimits"),
      props = FunctionPropSpec(numArgs = 1),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        val body = args(0)
        ParseNodeOperatorname(
          mode = parser.mode,
          body = FunctionDef.ordargument(body),
          alwaysHandleSupSub = (context.funcName == "\\operatornamewithlimits"),
          limits = false,
          parentIsSupSub = false
        )
      }),
      htmlBuilder = Nullable(htmlBuilder),
      mathmlBuilder = Nullable(mathmlBuilder)
    ))

    MacroDef.defineMacro("\\operatorname",
      MacroDefinition.StringDef("\\@ifstar\\operatornamewithlimits\\operatorname@"))
  }
}
