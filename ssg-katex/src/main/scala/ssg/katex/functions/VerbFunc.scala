/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * \verb — verbatim text.
 *
 * Original source: katex src/functions/verb.ts
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
import ssg.katex.ParseError
import ssg.katex.build.BuildCommon
import ssg.katex.parse._
import ssg.katex.tree.{HtmlDomNode, MathNode, TextNode}

object VerbFunc {

  /**
   * Converts verb group into body string.
   *
   * \verb* replaces each space with an open box ␣
   * \verb replaces each space with a no-break space \xA0
   */
  private def makeVerb(group: ParseNodeVerb): String = {
    group.body.replace(" ", if (group.star) "␣" else " ")
  }

  def register(): Unit = {
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "verb",
      names = Array("\\verb"),
      props = FunctionPropSpec(
        numArgs = 0,
        allowedInText = true
      ),
      handler = Nullable((context, args, optArgs) => {
        // \verb and \verb* are dealt with directly in Parser.js.
        // If we end up here, it's because of a failure to match the two delimiters
        // in the regex in Lexer.js.  LaTeX raises the following error when \verb is
        // terminated by end of line (or file).
        throw new ParseError(
          "\\verb ended by end of line instead of matching delimiter")
      }),
      htmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeVerb]
        val opts = options.asInstanceOf[Options]
        val text = makeVerb(g)
        val body = ArrayBuffer.empty[HtmlDomNode]
        // \verb enters text mode and therefore is sized like \textstyle
        val newOptions = opts.havingStyle(opts.style.text())
        var i = 0
        while (i < text.length) {
          var c = text.charAt(i).toString
          if (c == "~") {
            c = "\\textasciitilde"
          }
          body += BuildCommon.makeSymbol(c, "Typewriter-Regular",
            g.mode, Nullable(newOptions), ArrayBuffer("mord", "texttt"))
          i += 1
        }
        BuildCommon.makeSpan(
          ArrayBuffer("mord", "text") ++ newOptions.sizingClasses(opts),
          BuildCommon.tryCombineChars(body),
          Nullable(newOptions)
        )
      }),
      mathmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeVerb]
        val text = new TextNode(makeVerb(g))
        val node = new MathNode("mtext", ArrayBuffer(text))
        node.setAttribute("mathvariant", "monospace")
        node
      })
    ))
  }
}
