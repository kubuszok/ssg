/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * HTML extension commands: \htmlClass, \htmlId, \htmlStyle, \htmlData.
 *
 * Original source: katex src/functions/html.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package functions

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

import lowlevel.Nullable
import ssg.katex.{ ParseError, StrictSetting }
import ssg.katex.build.{ BuildCommon, BuildHTML, BuildMathML }
import ssg.katex.parse._

object HtmlFunc {

  def register(): Unit =
    FunctionDef.defineFunction(
      FunctionDefSpec(
        nodeType = "html",
        names = Array("\\htmlClass", "\\htmlId", "\\htmlStyle", "\\htmlData"),
        props = FunctionPropSpec(
          numArgs = 2,
          argTypes = Nullable(Array(ArgType.Raw, ArgType.Original)),
          allowedInText = true
        ),
        handler = Nullable((context, args, optArgs) =>
          boundary {
            val parser = context.parser.asInstanceOf[Parser]
            val value  = ParseNode.assertNodeType(Nullable(args(0)), "raw").asInstanceOf[ParseNodeRaw].string
            val body   = args(1)

            if (parser.settings.strict != StrictSetting.BoolValue(false)) {
              parser.settings.reportNonstrict("htmlExtension", "HTML extension is disabled on strict mode")
            }

            var trustContext: AnyTrustContext = AnyTrustContext.HtmlClassContext(`class` = "") // overwritten below in all branches
            val attributes = mutable.Map.empty[String, String]

            context.funcName match {
              case "\\htmlClass" =>
                attributes("class") = value
                trustContext = AnyTrustContext.HtmlClassContext(
                  command = "\\htmlClass",
                  `class` = value
                )
              case "\\htmlId" =>
                attributes("id") = value
                trustContext = AnyTrustContext.HtmlIdContext(
                  command = "\\htmlId",
                  id = value
                )
              case "\\htmlStyle" =>
                attributes("style") = value
                trustContext = AnyTrustContext.HtmlStyleContext(
                  command = "\\htmlStyle",
                  style = value
                )
              case "\\htmlData" =>
                val data = value.split(",")
                var i    = 0
                while (i < data.length) {
                  val item        = data(i)
                  val firstEquals = item.indexOf("=")
                  if (firstEquals < 0) {
                    throw new ParseError(
                      s"\\htmlData key/value '$item'" +
                        " missing equals sign"
                    )
                  }
                  val key = item.substring(0, firstEquals)
                  val v   = item.substring(firstEquals + 1)
                  attributes("data-" + key.trim) = v
                  i += 1
                }
                val attrsMap = mutable.Map.empty[String, String]
                attrsMap ++= attributes
                trustContext = AnyTrustContext.HtmlDataContext(
                  command = "\\htmlData",
                  attributes = attrsMap
                )
              case _ =>
                throw new Error("Unrecognized html command")
            }

            if (!parser.settings.isTrusted(trustContext)) {
              break(parser.formatUnsupportedCmd(context.funcName))
            }
            ParseNodeHtml(
              mode = parser.mode,
              attributes = attributes,
              body = FunctionDef.ordargument(body)
            )
          }
        ),
        htmlBuilder = Nullable { (group, options) =>
          val g        = group.asInstanceOf[ParseNodeHtml]
          val opts     = options.asInstanceOf[Options]
          val elements = BuildHTML.buildExpression(g.body, opts, isRealGroup = false)

          val classes = ArrayBuffer("enclosing")
          g.attributes.get("class").foreach { cls =>
            classes ++= cls.trim.split("\\s+")
          }

          val span = BuildCommon.makeSpan(classes, elements, Nullable(opts))
          g.attributes.foreach { case (attr, v) =>
            if (attr != "class") {
              span.setAttribute(attr, v)
            }
          }
          span
        },
        mathmlBuilder = Nullable { (group, options) =>
          val g    = group.asInstanceOf[ParseNodeHtml]
          val opts = options.asInstanceOf[Options]
          BuildMathML.buildExpressionRow(g.body, opts)
        }
      )
    )
}
