/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * \@char — internal function that takes a grouped decimal argument like
 * {123} and converts into symbol with code 123. Used by the *macro*
 * \char defined in macros.js.
 *
 * Original source: katex src/functions/char.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package functions

import ssg.commons.Nullable
import ssg.katex.ParseError
import ssg.katex.parse._

object CharFunc {

  def register(): Unit =
    // \@char is an internal function that takes a grouped decimal argument like
    // {123} and converts into symbol with code 123.  It is used by the *macro*
    // \char defined in macros.js.
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "textord",
        names = Array("\\@char"),
        props = FunctionPropSpec(
          numArgs = 1,
          allowedInText = true
        ),
        handler = Nullable { (context, args, optArgs) =>
          val parser = context.parser.asInstanceOf[Parser]
          val arg    = ParseNode.assertNodeType(Nullable(args(0)), "ordgroup").asInstanceOf[ParseNodeOrdgroup]
          val group  = arg.body
          var number = ""
          var i      = 0
          while (i < group.length) {
            val node = ParseNode.assertNodeType(Nullable(group(i)), "textord").asInstanceOf[ParseNodeTextord]
            number += node.text
            i += 1
          }
          var code: Int =
            try number.toInt
            catch { case _: NumberFormatException => -1 }
          val text: String =
            if (code < 0) {
              throw new ParseError(s"\\@char has non-numeric argument $number")
              // If we drop IE support, the following code could be replaced with
              // text = String.fromCodePoint(code)
            } else if (code < 0 || code >= 0x10ffff) {
              throw new ParseError(s"\\@char with invalid code point $number")
            } else if (code <= 0xffff) {
              code.toChar.toString
            } else { // Astral code point; split into surrogate halves
              code -= 0x10000
              val hi = ((code >> 10) + 0xd800).toChar
              val lo = ((code & 0x3ff) + 0xdc00).toChar
              s"$hi$lo"
            }
          ParseNodeTextord(mode = parser.mode, text = text)
        }
      )
    )
}
