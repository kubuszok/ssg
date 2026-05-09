/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * \href and \url commands.
 *
 * Original source: katex src/functions/href.ts
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
import ssg.katex.build.{BuildCommon, BuildHTML, BuildMathML}
import ssg.katex.parse._
import ssg.katex.tree.MathNode

object HrefFunc {

  def register(): Unit = {
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "href",
      names = Array("\\href"),
      props = FunctionPropSpec(
        numArgs = 2,
        argTypes = Nullable(Array(ArgType.Url, ArgType.Original)),
        allowedInText = true
      ),
      handler = Nullable((context, args, optArgs) => boundary {
        val parser = context.parser.asInstanceOf[Parser]
        val body = args(1)
        val href = ParseNode.assertNodeType(Nullable(args(0)), "url").asInstanceOf[ParseNodeUrl].url

        if (!parser.settings.isTrusted(AnyTrustContext.HrefContext(
          command = "\\href",
          url = href
        ))) {
          break(parser.formatUnsupportedCmd("\\href"))
        }

        ParseNodeHref(
          mode = parser.mode,
          href = href,
          body = FunctionDef.ordargument(body)
        )
      }),
      htmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeHref]
        val opts = options.asInstanceOf[Options]
        val elements = BuildHTML.buildExpression(g.body, opts, isRealGroup = false)
        BuildCommon.makeAnchor(g.href, ArrayBuffer.empty, elements, opts)
      }),
      mathmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeHref]
        val opts = options.asInstanceOf[Options]
        var math = BuildMathML.buildExpressionRow(g.body, opts)
        math match {
          case mn: MathNode => mn.setAttribute("href", g.href)
          case _ =>
            val wrapped = new MathNode("mrow", ArrayBuffer(math))
            wrapped.setAttribute("href", g.href)
            math = wrapped
        }
        math
      })
    ))

    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "href",
      names = Array("\\url"),
      props = FunctionPropSpec(
        numArgs = 1,
        argTypes = Nullable(Array(ArgType.Url)),
        allowedInText = true
      ),
      handler = Nullable((context, args, optArgs) => boundary {
        val parser = context.parser.asInstanceOf[Parser]
        val href = ParseNode.assertNodeType(Nullable(args(0)), "url").asInstanceOf[ParseNodeUrl].url

        if (!parser.settings.isTrusted(AnyTrustContext.UrlContext(
          command = "\\url",
          url = href
        ))) {
          break(parser.formatUnsupportedCmd("\\url"))
        }

        val chars = ArrayBuffer.empty[AnyParseNode]
        var i = 0
        while (i < href.length) {
          var c = href.charAt(i).toString
          if (c == "~") {
            c = "\\textasciitilde"
          }
          chars += ParseNodeTextord(mode = Mode.Text, text = c)
          i += 1
        }
        val body: AnyParseNode = ParseNodeText(
          mode = parser.mode,
          font = Nullable("\\texttt"),
          body = chars.toArray
        )
        ParseNodeHref(
          mode = parser.mode,
          href = href,
          body = FunctionDef.ordargument(body)
        )
      })
    ))
  }
}
