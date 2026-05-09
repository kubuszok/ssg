/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * \phantom and \vphantom commands.
 * (Note: \hphantom is defined as a macro using \smash and \phantom.)
 *
 * Original source: katex src/functions/phantom.ts
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
import ssg.katex.parse._
import ssg.katex.tree.{HtmlDomNode, MathNode}

object PhantomFunc {

  def register(): Unit = {
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "phantom",
      names = Array("\\phantom"),
      props = FunctionPropSpec(
        numArgs = 1,
        allowedInText = true
      ),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        val body = args(0)
        ParseNodePhantom(
          mode = parser.mode,
          body = FunctionDef.ordargument(body)
        )
      }),
      htmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodePhantom]
        val opts = options.asInstanceOf[Options]
        val elements = BuildHTML.buildExpression(
          g.body,
          opts.withPhantom(),
          isRealGroup = false
        )
        // \phantom isn't supposed to affect the elements it contains.
        // See "color" for more details.
        BuildCommon.makeFragment(elements)
      }),
      mathmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodePhantom]
        val opts = options.asInstanceOf[Options]
        val inner = BuildMathML.buildExpression(g.body, opts)
        new MathNode("mphantom", inner)
      })
    ))

    // \hphantom is defined as a macro
    MacroDef.defineMacro("\\hphantom", MacroDefinition.StringDef("\\smash{\\phantom{#1}}"))

    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "vphantom",
      names = Array("\\vphantom"),
      props = FunctionPropSpec(
        numArgs = 1,
        allowedInText = true
      ),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        val body = args(0)
        ParseNodeVphantom(
          mode = parser.mode,
          body = body
        )
      }),
      htmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeVphantom]
        val opts = options.asInstanceOf[Options]
        val inner = BuildCommon.makeSpan(
          ArrayBuffer("inner"),
          ArrayBuffer(BuildHTML.buildGroup(Nullable(g.body), opts.withPhantom())))
        val fix = BuildCommon.makeSpan(ArrayBuffer("fix"), ArrayBuffer.empty[HtmlDomNode])
        BuildCommon.makeSpan(
          ArrayBuffer("mord", "rlap"), ArrayBuffer(inner, fix), Nullable(opts))
      }),
      mathmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeVphantom]
        val opts = options.asInstanceOf[Options]
        val inner = BuildMathML.buildExpression(FunctionDef.ordargument(g.body), opts)
        val phantom = new MathNode("mphantom", inner)
        val node = new MathNode("mpadded", ArrayBuffer(phantom))
        node.setAttribute("width", "0px")
        node
      })
    ))
  }
}
