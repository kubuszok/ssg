/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Horizontal stretchy braces: \overbrace, \underbrace, \overbracket, \underbracket.
 * NOTE: Unlike most `htmlBuilder`s, this one handles not only "horizBrace", but
 * also "supsub" since an over/underbrace can affect super/subscripting.
 *
 * Original source: katex src/functions/horizBrace.ts
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
import ssg.katex.build.{ BuildCommon, BuildHTML, BuildMathML, Stretchy, VListChild, VListElem, VListKern, VListParam }
import ssg.katex.parse._
import ssg.katex.tree.{ DomSpan, HtmlDomNode, MathNode }

object HorizBraceFunc {

  // NOTE: Unlike most `htmlBuilder`s, this one handles not only "horizBrace", but
  // also "supsub" since an over/underbrace can affect super/subscripting.
  val htmlBuilder: HtmlBuilder = (grp, options) => {
    val opts  = options.asInstanceOf[Options]
    val style = opts.style

    // Pull out the `ParseNode<"horizBrace">` if `grp` is a "supsub" node.
    var supSubGroup: Nullable[HtmlDomNode] = Nullable.Null
    val group:       ParseNodeHorizBrace   = if (grp.nodeType == "supsub") {
      val ss = grp.asInstanceOf[ParseNodeSupsub]
      // Ref: LaTeX source2e: }}}}\limits}
      // i.e. LaTeX treats the brace similar to an op and passes it
      // with \limits, so we need to assign supsub style.
      supSubGroup = Nullable(
        if (ss.sup.isDefined)
          BuildHTML.buildGroup(ss.sup, opts.havingStyle(style.sup()), Nullable(opts))
        else
          BuildHTML.buildGroup(ss.sub, opts.havingStyle(style.sub()), Nullable(opts))
      )
      ParseNode.assertNodeType(ss.base, "horizBrace").asInstanceOf[ParseNodeHorizBrace]
    } else {
      ParseNode.assertNodeType(Nullable(grp), "horizBrace").asInstanceOf[ParseNodeHorizBrace]
    }

    // Build the base group
    val body = BuildHTML.buildGroup(Nullable(group.base), opts.havingBaseStyle(Style.DISPLAY))

    // Create the stretchy element
    val braceBody = Stretchy.stretchySvg(group, opts)

    // Generate the vlist, with the appropriate kerns
    var vlist: DomSpan = BuildCommon.makeSpan() // overwritten below in all branches
    if (group.isOver) {
      vlist = BuildCommon.makeVList(VListParam.FirstBaseline(
                                      children = Array(
                                        VListElem(elem = body),
                                        VListKern(0.1),
                                        VListElem(elem = braceBody)
                                      )
                                    ),
                                    opts
      )
      // TODO(ts): Replace this with passing "svg-align" into makeVList.
      vlist.children(0).asInstanceOf[DomSpan].children(0).asInstanceOf[DomSpan].children(1).asInstanceOf[DomSpan].classes += "svg-align"
    } else {
      vlist = BuildCommon.makeVList(
        VListParam.Positioned(
          positionType = "bottom",
          positionData = body.depth + 0.1 + braceBody.height,
          children = Array(
            VListElem(elem = braceBody),
            VListKern(0.1),
            VListElem(elem = body)
          )
        ),
        opts
      )
      // TODO(ts): Replace this with passing "svg-align" into makeVList.
      vlist.children(0).asInstanceOf[DomSpan].children(0).asInstanceOf[DomSpan].children(0).asInstanceOf[DomSpan].classes += "svg-align"
    }

    if (supSubGroup.isDefined) {
      // To write the supsub, wrap the first vlist in another vlist:
      val vSpan = BuildCommon.makeSpan(ArrayBuffer("minner", if (group.isOver) "mover" else "munder"), ArrayBuffer(vlist), Nullable(opts))

      if (group.isOver) {
        vlist = BuildCommon.makeVList(VListParam.FirstBaseline(
                                        children = Array(
                                          VListElem(elem = vSpan),
                                          VListKern(0.2),
                                          VListElem(elem = supSubGroup.get)
                                        )
                                      ),
                                      opts
        )
      } else {
        vlist = BuildCommon.makeVList(
          VListParam.Positioned(
            positionType = "bottom",
            positionData = vSpan.depth + 0.2 + supSubGroup.get.height +
              supSubGroup.get.depth,
            children = Array(
              VListElem(elem = supSubGroup.get),
              VListKern(0.2),
              VListElem(elem = vSpan)
            )
          ),
          opts
        )
      }
    }

    BuildCommon.makeSpan(ArrayBuffer("minner", if (group.isOver) "mover" else "munder"), ArrayBuffer(vlist), Nullable(opts))
  }

  val mathmlBuilder: MathMLBuilder = (group, options) => {
    val g          = group.asInstanceOf[ParseNodeHorizBrace]
    val opts       = options.asInstanceOf[Options]
    val accentNode = Stretchy.stretchyMathML(g.label)
    new MathNode(
      if (g.isOver) "mover" else "munder",
      ArrayBuffer(BuildMathML.buildGroup(g.base, opts), accentNode)
    )
  }

  def register(): Unit =
    // Horizontal stretchy braces
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "horizBrace",
        names = Array("\\overbrace", "\\underbrace", "\\overbracket", "\\underbracket"),
        props = FunctionPropSpec(numArgs = 1),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          ParseNodeHorizBrace(
            mode = parser.mode,
            label = context.funcName,
            isOver = context.funcName.contains("\\over"),
            base = args(0)
          )
        },
        htmlBuilder = Nullable(htmlBuilder),
        mathmlBuilder = Nullable(mathmlBuilder)
      )
    )
}
