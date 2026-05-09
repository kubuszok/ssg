/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Delimiter sizing/fencing: \bigl, \Big, \left, \right, \middle, etc.
 *
 * Original source: katex src/functions/delimsizing.ts
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
import ssg.katex.build.{BuildCommon, BuildHTML, BuildMathML, Delimiter}
import ssg.katex.data.Units
import ssg.katex.parse._
import ssg.katex.tree.{DomSpan, HtmlDomNode, MathNode}

object DelimsizingFunc {

  // Extra data needed for the delimiter handler down below
  private final case class DelimInfo(mclass: String, size: Int)

  private val delimiterSizes: Map[String, DelimInfo] = Map(
    "\\bigl"  -> DelimInfo("mopen",  1),
    "\\Bigl"  -> DelimInfo("mopen",  2),
    "\\biggl" -> DelimInfo("mopen",  3),
    "\\Biggl" -> DelimInfo("mopen",  4),
    "\\bigr"  -> DelimInfo("mclose", 1),
    "\\Bigr"  -> DelimInfo("mclose", 2),
    "\\biggr" -> DelimInfo("mclose", 3),
    "\\Biggr" -> DelimInfo("mclose", 4),
    "\\bigm"  -> DelimInfo("mrel",   1),
    "\\Bigm"  -> DelimInfo("mrel",   2),
    "\\biggm" -> DelimInfo("mrel",   3),
    "\\Biggm" -> DelimInfo("mrel",   4),
    "\\big"   -> DelimInfo("mord",   1),
    "\\Big"   -> DelimInfo("mord",   2),
    "\\bigg"  -> DelimInfo("mord",   3),
    "\\Bigg"  -> DelimInfo("mord",   4)
  )

  private val delimiters: Set[String] = Set(
    "(", "\\lparen", ")", "\\rparen",
    "[", "\\lbrack", "]", "\\rbrack",
    "\\{", "\\lbrace", "\\}", "\\rbrace",
    "\\lfloor", "\\rfloor", "⌊", "⌋",
    "\\lceil", "\\rceil", "⌈", "⌉",
    "<", ">", "\\langle", "⟨", "\\rangle", "⟩", "\\lt", "\\gt",
    "\\lvert", "\\rvert", "\\lVert", "\\rVert",
    "\\lgroup", "\\rgroup", "⟮", "⟯",
    "\\lmoustache", "\\rmoustache", "⎰", "⎱",
    "/", "\\backslash",
    "|", "\\vert", "\\|", "\\Vert",
    "\\uparrow", "\\Uparrow",
    "\\downarrow", "\\Downarrow",
    "\\updownarrow", "\\Updownarrow",
    "."
  )

  // Delimiter functions
  private def checkDelimiter(
      delim: AnyParseNode,
      context: FunctionContext
  ): SymbolParseNode = {
    val symDelim = ParseNode.checkSymbolNodeType(Nullable(delim))
    if (symDelim.isDefined && delimiters.contains(symDelim.get.text)) {
      symDelim.get
    } else if (symDelim.isDefined) {
      throw new ParseError(
        s"Invalid delimiter '${symDelim.get.text}' after '${context.funcName}'",
        Nullable(delim))
    } else {
      throw new ParseError(s"Invalid delimiter type '${delim.nodeType}'", Nullable(delim))
    }
  }

  private def assertParsed(group: ParseNodeLeftright): Unit = {
    // In the original TypeScript, `!group.body` checks for falsy (null/undefined).
    // An empty array is truthy in JS, so this only triggers for uninitialized nodes.
    // In Scala, body is always initialized (at least to Array.empty) by the handler,
    // so this check is effectively a no-op but kept for documentation.
    if (group.body == null) { // @nowarn — matches original JS truthiness check
      throw new Error("Bug: The leftright ParseNode wasn't fully parsed.")
    }
  }

  def register(): Unit = {
    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "delimsizing",
      names = Array(
        "\\bigl", "\\Bigl", "\\biggl", "\\Biggl",
        "\\bigr", "\\Bigr", "\\biggr", "\\Biggr",
        "\\bigm", "\\Bigm", "\\biggm", "\\Biggm",
        "\\big", "\\Big", "\\bigg", "\\Bigg"
      ),
      props = FunctionPropSpec(
        numArgs = 1,
        argTypes = Nullable(Array(ArgType.Primitive))
      ),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        val delim = checkDelimiter(args(0), context)
        val info = delimiterSizes(context.funcName)
        ParseNodeDelimsizing(
          mode = parser.mode,
          size = info.size,
          mclass = info.mclass,
          delim = delim.text
        )
      }),
      htmlBuilder = Nullable((group, options) => boundary {
        val g = group.asInstanceOf[ParseNodeDelimsizing]
        val opts = options.asInstanceOf[Options]
        if (g.delim == ".") {
          // Empty delimiters still count as elements, even though they don't
          // show anything.
          break(BuildCommon.makeSpan(ArrayBuffer(g.mclass)))
        }
        Delimiter.makeSizedDelim(
          g.delim, g.size, opts, g.mode, Array(g.mclass))
      }),
      mathmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeDelimsizing]
        val children = ArrayBuffer.empty[tree.MathDomNode]

        if (g.delim != ".") {
          children += BuildMathML.makeText(g.delim, g.mode)
        }

        val node = new MathNode("mo", children)

        if (g.mclass == "mopen" || g.mclass == "mclose") {
          // Only some of the delimsizing functions act as fences, and they
          // return "mopen" or "mclose" mclass.
          node.setAttribute("fence", "true")
        } else {
          // Explicitly disable fencing if it's not a fence, to override the
          // defaults.
          node.setAttribute("fence", "false")
        }

        node.setAttribute("stretchy", "true")
        val size = Units.makeEm(Delimiter.sizeToMaxHeight(g.size))
        node.setAttribute("minsize", size)
        node.setAttribute("maxsize", size)

        node
      })
    ))

    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "leftright-right",
      names = Array("\\right"),
      props = FunctionPropSpec(
        numArgs = 1,
        primitive = true
      ),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        // \left case below triggers parsing of \right in
        //   `const right = parser.parseFunction();`
        // uses this return value.
        val colorMacro = parser.gullet.macros.get("\\current@color")
        val color: Nullable[String] = colorMacro.flatMap {
          case MacroDefinition.StringDef(s) => Nullable(s)
          case _ => throw new ParseError(
            "\\current@color set to non-string in \\right")
        }
        ParseNodeLeftrightRight(
          mode = parser.mode,
          delim = checkDelimiter(args(0), context).text,
          color = color // undefined if not set via \color
        )
      })
    ))

    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "leftright",
      names = Array("\\left"),
      props = FunctionPropSpec(
        numArgs = 1,
        primitive = true
      ),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        val delim = checkDelimiter(args(0), context)

        // Parse out the implicit body
        parser.leftrightDepth += 1
        // parseExpression stops before '\\right'
        val body = parser.parseExpression(false)
        parser.leftrightDepth -= 1
        // Check the next token
        parser.expect("\\right", false)
        val right = ParseNode.assertNodeType(parser.parseFunction(), "leftright-right")
          .asInstanceOf[ParseNodeLeftrightRight]
        ParseNodeLeftright(
          mode = parser.mode,
          body = body,
          left = delim.text,
          right = right.delim,
          rightColor = right.color
        )
      }),
      htmlBuilder = Nullable((group, options) => boundary {
        val g = group.asInstanceOf[ParseNodeLeftright]
        val opts = options.asInstanceOf[Options]
        assertParsed(g)
        // Build the inner expression
        val inner = BuildHTML.buildExpression(g.body, opts, isRealGroup = true,
          surrounding = (Nullable("mopen"), Nullable("mclose")))

        var innerHeight = 0.0
        var innerDepth = 0.0
        var hadMiddle = false

        // Calculate its height and depth
        var i = 0
        while (i < inner.length) {
          // Property `isMiddle` not defined on `span`. See comment in
          // "middle"'s htmlBuilder.
          // TODO(ts)
          val isMiddleProp: Nullable[String] = inner(i) match {
            case ds: DomSpan @unchecked => Nullable.fromOption(ds.attributes.get("isMiddle"))
            case _ => Nullable.Null
          }
          if (isMiddleProp.isDefined) {
            hadMiddle = true
          } else {
            innerHeight = Math.max(inner(i).height, innerHeight)
            innerDepth = Math.max(inner(i).depth, innerDepth)
          }
          i += 1
        }

        // The size of delimiters is the same, regardless of what style we are
        // in. Thus, to correctly calculate the size of delimiter we need around
        // a group, we scale down the inner size based on the size.
        innerHeight *= opts.sizeMultiplier
        innerDepth *= opts.sizeMultiplier

        val leftDelim: HtmlDomNode = if (g.left == ".") {
          // Empty delimiters in \left and \right make null delimiter spaces.
          BuildHTML.makeNullDelimiter(opts, Array("mopen"))
        } else {
          // Otherwise, use leftRightDelim to generate the correct sized
          // delimiter.
          Delimiter.makeLeftRightDelim(
            g.left, innerHeight, innerDepth, opts,
            g.mode, Array("mopen"))
        }
        // Add it to the beginning of the expression
        inner.prepend(leftDelim)

        // Handle middle delimiters
        if (hadMiddle) {
          i = 1
          while (i < inner.length) {
            val middleDelim = inner(i)
            middleDelim match {
              case ds: DomSpan @unchecked =>
                val isMiddleAttr = ds.attributes.get("isMiddle")
                if (isMiddleAttr.isDefined) {
                  // Apply the options that were active when \middle was called
                  inner(i) = Delimiter.makeLeftRightDelim(
                    ds.attributes.getOrElse("isMiddle_delim", "."),
                    innerHeight, innerDepth,
                    opts, // simplified: using current opts
                    g.mode, Array.empty)
                }
              case _ => // do nothing
            }
            i += 1
          }
        }

        val rightDelim: HtmlDomNode = if (g.right == ".") {
          BuildHTML.makeNullDelimiter(opts, Array("mclose"))
        } else {
          // Same for the right delimiter, but using color specified by \color
          val colorOptions = g.rightColor.fold(opts)(c => opts.withColor(c))
          Delimiter.makeLeftRightDelim(
            g.right, innerHeight, innerDepth, colorOptions,
            g.mode, Array("mclose"))
        }
        // Add it to the end of the expression.
        inner += rightDelim

        BuildCommon.makeSpan(ArrayBuffer("minner"), inner, Nullable(opts))
      }),
      mathmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeLeftright]
        val opts = options.asInstanceOf[Options]
        assertParsed(g)
        val inner = BuildMathML.buildExpression(g.body, opts)

        if (g.left != ".") {
          val leftNode = new MathNode(
            "mo", ArrayBuffer(BuildMathML.makeText(g.left, g.mode)))
          leftNode.setAttribute("fence", "true")
          inner.prepend(leftNode)
        }

        if (g.right != ".") {
          val rightNode = new MathNode(
            "mo", ArrayBuffer(BuildMathML.makeText(g.right, g.mode)))
          rightNode.setAttribute("fence", "true")
          g.rightColor.foreach { c =>
            rightNode.setAttribute("mathcolor", c)
          }
          inner += rightNode
        }

        BuildMathML.makeRow(inner)
      })
    ))

    FunctionDef.defineFunction(FunctionDefSpec(
      nodeType = "middle",
      names = Array("\\middle"),
      props = FunctionPropSpec(
        numArgs = 1,
        primitive = true
      ),
      handler = Nullable((context, args, optArgs) => {
        val parser = context.parser.asInstanceOf[Parser]
        val delim = checkDelimiter(args(0), context)
        if (parser.leftrightDepth == 0) {
          throw new ParseError("\\middle without preceding \\left", Nullable(delim))
        }
        ParseNodeMiddle(
          mode = parser.mode,
          delim = delim.text
        )
      }),
      htmlBuilder = Nullable((group, options) => boundary {
        val g = group.asInstanceOf[ParseNodeMiddle]
        val opts = options.asInstanceOf[Options]
        val middleDelim: DomSpan = if (g.delim == ".") {
          BuildHTML.makeNullDelimiter(opts, Array.empty)
        } else {
          val d = Delimiter.makeSizedDelim(
            g.delim, 1, opts, g.mode, Array.empty)

          // Property `isMiddle` not defined on `span`. It is only used in
          // this file above.
          // TODO: Fix this violation of the `span` type and possibly rename
          // things since `isMiddle` sounds like a boolean, but is a struct.
          // TODO(ts)
          d.setAttribute("isMiddle", "true")
          d.setAttribute("isMiddle_delim", g.delim)
          d
        }
        middleDelim
      }),
      mathmlBuilder = Nullable((group, options) => {
        val g = group.asInstanceOf[ParseNodeMiddle]
        // A Firefox \middle will stretch a character vertically only if it
        // is in the fence part of the operator dictionary at:
        // https://www.w3.org/TR/MathML3/appendixc.html.
        // So we need to avoid U+2223 and use plain "|" instead.
        val textNode = if (g.delim == "\\vert" || g.delim == "|")
          BuildMathML.makeText("|", Mode.Text)
        else
          BuildMathML.makeText(g.delim, g.mode)
        val middleNode = new MathNode("mo", ArrayBuffer(textNode))
        middleNode.setAttribute("fence", "true")
        // MathML gives 5/18em spacing to each <mo> element.
        // \middle should get delimiter spacing instead.
        middleNode.setAttribute("lspace", "0.05em")
        middleNode.setAttribute("rspace", "0.05em")
        middleNode
      })
    ))
  }
}
