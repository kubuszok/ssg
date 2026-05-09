/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * \includegraphics command.
 *
 * Original source: katex src/functions/includegraphics.ts
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
import ssg.katex.ParseError

import ssg.katex.data.{Measurement, Units}
import ssg.katex.parse._
import ssg.katex.tree.{CssStyle, Img, MathNode}

object IncludegraphicsFunc {

  private def sizeData(str: String): Measurement = {
    if ("""^[-+]? *(\d+(\.\d*)?|\.\d+)$""".r.findFirstIn(str).isDefined) {
      // str is a number with no unit specified.
      // default unit is bp, per graphix package.
      Measurement(str.trim.toDouble, "bp")
    } else {
      val matchResult = """([-+]?) *(\d+(?:\.\d*)?|\.\d+) *([a-z]{2})""".r.findFirstMatchIn(str)
      if (matchResult.isEmpty) {
        throw new ParseError("Invalid size: '" + str
          + "' in \\includegraphics")
      }
      val m = matchResult.get
      val data = Measurement(
        number = (m.group(1) + m.group(2)).toDouble, // sign + magnitude
        unit = m.group(3)
      )
      if (!Units.validUnit(data)) {
        throw new ParseError("Invalid unit: '" + data.unit
          + "' in \\includegraphics.")
      }
      data
    }
  }

  def register(): Unit = {
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "includegraphics",
      names = Array("\\includegraphics"),
      props = FunctionPropSpec(
        numArgs = 1,
        numOptionalArgs = 1,
        argTypes = Nullable(Array(ArgType.Raw, ArgType.Url)),
        allowedInText = false
      ),
      handler = Nullable((context, args, optArgs) => boundary {
        val parser = context.parser.asInstanceOf[Parser]
        var width = Measurement(0, "em")
        var height = Measurement(0.9, "em") // sorta character sized.
        var totalheight = Measurement(0, "em")
        var alt = ""

        if (optArgs(0).isDefined) {
          val attributeStr = ParseNode.assertNodeType(optArgs(0), "raw")
            .asInstanceOf[ParseNodeRaw].string

          // Parser.js does not parse key/value pairs. We get a string.
          val attributes = attributeStr.split(",")
          var i = 0
          while (i < attributes.length) {
            val keyVal = attributes(i).split("=")
            if (keyVal.length == 2) {
              val str = keyVal(1).trim
              keyVal(0).trim match {
                case "alt" =>
                  alt = str
                case "width" =>
                  width = sizeData(str)
                case "height" =>
                  height = sizeData(str)
                case "totalheight" =>
                  totalheight = sizeData(str)
                case other =>
                  throw new ParseError("Invalid key: '" + other +
                    "' in \\includegraphics.")
              }
            }
            i += 1
          }
        }

        val src = ParseNode.assertNodeType(Nullable(args(0)), "url").asInstanceOf[ParseNodeUrl].url

        if (alt == "") {
          // No alt given. Use the file name. Strip away the path.
          alt = src
          alt = alt.replaceAll("^.*[\\\\/]", "")
          val lastDot = alt.lastIndexOf('.')
          if (lastDot >= 0) alt = alt.substring(0, lastDot)
        }

        if (!parser.settings.isTrusted(AnyTrustContext.IncludegraphicsContext(
          command = "\\includegraphics",
          url = src
        ))) {
          break(parser.formatUnsupportedCmd("\\includegraphics"))
        }

        ParseNodeIncludegraphics(
          mode = parser.mode,
          alt = alt,
          width = width,
          height = height,
          totalheight = totalheight,
          src = src
        )
      }),
      htmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeIncludegraphics]
        val opts = options.asInstanceOf[Options]
        val height = Units.calculateSize(g.height, opts)
        var depth = 0.0

        if (g.totalheight.number > 0) {
          depth = Units.calculateSize(g.totalheight, opts) - height
        }

        var width = 0.0
        if (g.width.number > 0) {
          width = Units.calculateSize(g.width, opts)
        }

        val style = CssStyle(
          height = Nullable(Units.makeEm(height + depth)),
          width = if (width > 0) Nullable(Units.makeEm(width)) else Nullable.Null,
          verticalAlign = if (depth > 0) Nullable(Units.makeEm(-depth)) else Nullable.Null
        )

        val node = new Img(g.src, g.alt, style)
        node.height = height
        node.depth = depth

        node
      }),
      mathmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeIncludegraphics]
        val opts = options.asInstanceOf[Options]
        val node = new MathNode("mglyph", ArrayBuffer.empty)
        node.setAttribute("alt", g.alt)

        val height = Units.calculateSize(g.height, opts)
        var depth = 0.0
        if (g.totalheight.number > 0) {
          depth = Units.calculateSize(g.totalheight, opts) - height
          node.setAttribute("valign", Units.makeEm(-depth))
        }
        node.setAttribute("height", Units.makeEm(height + depth))

        if (g.width.number > 0) {
          val width = Units.calculateSize(g.width, opts)
          node.setAttribute("width", Units.makeEm(width))
        }
        node.setAttribute("src", g.src)
        node
      })
    ))
  }
}
