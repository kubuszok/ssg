/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This file converts a parse tree into a corresponding MathML tree. The main
 * entry point is the `buildMathML` function, which takes a parse tree from the
 * parser.
 *
 * Original source: katex src/buildMathML.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: buildMathML -> BuildMathML (object)
 *   Convention: TypeScript optional chaining -> explicit checks
 *   Idiom: TypeScript type assertion -> asInstanceOf
 */
package ssg
package katex
package build

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

import lowlevel.Nullable
import ssg.katex.ParseError
import ssg.katex.data.{ FontMetrics, Symbols }
import ssg.katex.functions.FunctionDef
import ssg.katex.parse.{ AnyParseNode, SymbolParseNode }
import ssg.katex.tree.{ DomSpan, MathDomNode, MathNode, Span, TextNode, VirtualNode }

object BuildMathML {

  private val noVariantSymbols: Set[String] = Set("\\imath", "\\jmath")
  private val rowLikeTypes:     Set[String] = Set("mrow", "mtable")

  /** Takes a symbol and converts it into a MathML text node after performing optional replacement from symbols.js.
    */
  def makeText(
    text:    String,
    mode:    Mode,
    options: Nullable[Options] = Nullable.Null
  ): TextNode = {
    var t       = text
    val symbols = Symbols(mode)
    if (
      symbols.contains(t) && symbols(t).replace.isDefined &&
      t.charAt(0).toInt != 0xd835 &&
      !(Symbols.ligatures.contains(t) && options.isDefined &&
        ((options.get.fontFamily.nonEmpty && options.get.fontFamily.substring(4, 6) == "tt") ||
          (options.get.font.nonEmpty && options.get.font.substring(4, 6) == "tt")))
    ) {
      t = symbols(t).replace.get
    }

    new TextNode(t)
  }

  /** Wrap the given array of nodes in an <mrow> node if needed, i.e., unless the array has length 1. Always returns a single node.
    */
  def makeRow(body: ArrayBuffer[MathDomNode]): MathDomNode =
    if (body.length == 1) {
      body(0)
    } else {
      new MathNode("mrow", body)
    }

  /** Returns the math variant as a string or null if none is required.
    */
  def getVariant(
    group:   SymbolParseNode,
    options: Options
  ): Nullable[FontVariant] = boundary {
    // Handle \text... font specifiers as best we can.
    // MathML has a limited list of allowable mathvariant specifiers; see
    // https://www.w3.org/TR/MathML3/chapter3.html#presm.commatt
    if (options.fontFamily == "texttt") {
      break(Nullable(FontVariant.Monospace))
    } else if (options.fontFamily == "textsf") {
      if (
        options.fontShape == "textit" &&
        options.fontWeight == "textbf"
      ) {
        break(Nullable(FontVariant.SansSerifBoldItalic))
      } else if (options.fontShape == "textit") {
        break(Nullable(FontVariant.SansSerifItalic))
      } else if (options.fontWeight == "textbf") {
        break(Nullable(FontVariant.BoldSansSerif))
      } else {
        break(Nullable(FontVariant.SansSerif))
      }
    } else if (
      options.fontShape == "textit" &&
      options.fontWeight == "textbf"
    ) {
      break(Nullable(FontVariant.BoldItalic))
    } else if (options.fontShape == "textit") {
      break(Nullable(FontVariant.Italic))
    } else if (options.fontWeight == "textbf") {
      break(Nullable(FontVariant.Bold))
    }

    val font = options.font
    if (font.isEmpty || font == "mathnormal") {
      break(Nullable.Null)
    }

    val mode = group.mode
    if (font == "mathit") {
      break(Nullable(FontVariant.Italic))
    } else if (font == "boldsymbol") {
      break(Nullable(if (group.nodeType == "textord") FontVariant.Bold else FontVariant.BoldItalic))
    } else if (font == "mathbf") {
      break(Nullable(FontVariant.Bold))
    } else if (font == "mathbb") {
      break(Nullable(FontVariant.DoubleStruck))
    } else if (font == "mathsfit") {
      break(Nullable(FontVariant.SansSerifItalic))
    } else if (font == "mathfrak") {
      break(Nullable(FontVariant.Fraktur))
    } else if (font == "mathscr" || font == "mathcal") {
      // MathML makes no distinction between script and calligraphic
      break(Nullable(FontVariant.ScriptVariant))
    } else if (font == "mathsf") {
      break(Nullable(FontVariant.SansSerif))
    } else if (font == "mathtt") {
      break(Nullable(FontVariant.Monospace))
    }

    var text = group.text
    if (noVariantSymbols.contains(text)) {
      break(Nullable.Null)
    }

    val symbolMap = Symbols(mode)
    if (symbolMap.contains(text)) {
      val replacement = symbolMap(text).replace
      replacement.foreach { r =>
        text = r
      }
    }

    val fontName = BuildCommon.fontMap(font).fontName
    if (FontMetrics.getCharacterMetrics(text, fontName, mode).isDefined) {
      Nullable(BuildCommon.fontMap(font).variant)
    } else {
      Nullable.Null
    }
  }

  /** Check for <mi>.</mi> which is how a dot renders in MathML, or <mo separator="true" lspace="0em" rspace="0em">,</mo> which is how a braced comma {,} renders in MathML
    */
  private def isNumberPunctuation(group: Nullable[MathNode]): Boolean = boundary {
    if (group.isEmpty) {
      break(false)
    }
    val g = group.get
    if (g.nodeType == "mi" && g.children.length == 1) {
      g.children(0) match {
        case t: TextNode => t.text == "."
        case _ => false
      }
    } else if (
      g.nodeType == "mo" && g.children.length == 1 &&
      g.attributes.get("separator").contains("true") &&
      g.attributes.get("lspace").contains("0em") &&
      g.attributes.get("rspace").contains("0em")
    ) {
      g.children(0) match {
        case t: TextNode => t.text == ","
        case _ => false
      }
    } else {
      false
    }
  }

  /** Takes a list of nodes, builds them, and returns a list of the generated MathML nodes. Also combine consecutive <mtext> outputs into a single <mtext> tag.
    */
  def buildExpression(
    expression: Array[AnyParseNode],
    options:    Options,
    isOrdgroup: Boolean = false
  ): ArrayBuffer[MathDomNode] = boundary {
    if (expression.length == 1) {
      val group = buildGroup(expression(0), options)
      if (
        isOrdgroup && group.isInstanceOf[MathNode] &&
        group.asInstanceOf[MathNode].nodeType == "mo"
      ) {
        // When TeX writers want to suppress spacing on an operator,
        // they often put the operator by itself inside braces.
        group.asInstanceOf[MathNode].setAttribute("lspace", "0em")
        group.asInstanceOf[MathNode].setAttribute("rspace", "0em")
      }
      break(ArrayBuffer(group))
    }

    val groups = ArrayBuffer.empty[MathDomNode]
    var lastGroup: Nullable[MathNode] = Nullable.Null
    var i = 0
    while (i < expression.length) {
      val group = buildGroup(expression(i), options)
      if (group.isInstanceOf[MathNode] && lastGroup.isDefined) {
        val gm  = group.asInstanceOf[MathNode]
        val lgm = lastGroup.get
        // Concatenate adjacent <mtext>s
        if (
          gm.nodeType == "mtext" && lgm.nodeType == "mtext"
          && gm.attributes.get("mathvariant") ==
            lgm.attributes.get("mathvariant")
        ) {
          lgm.children ++= gm.children
          i += 1
          // don't push, don't update lastGroup
        }
        // Concatenate adjacent <mn>s
        else if (gm.nodeType == "mn" && lgm.nodeType == "mn") {
          lgm.children ++= gm.children
          i += 1
        }
        // Concatenate <mn>...</mn> followed by <mi>.</mi>
        else if (isNumberPunctuation(Nullable(gm)) && lgm.nodeType == "mn") {
          lgm.children ++= gm.children
          i += 1
        }
        // Concatenate <mi>.</mi> followed by <mn>...</mn>
        else if (gm.nodeType == "mn" && isNumberPunctuation(lastGroup)) {
          gm.children.insertAll(0, lgm.children)
          groups.remove(groups.length - 1)
          groups += gm
          lastGroup = Nullable(gm)
          i += 1
        }
        // Put preceding <mn>...</mn> or <mi>.</mi> inside base of
        // <msup><mn>...base...</mn>...exponent...</msup> (or <msub>)
        else if (
          (gm.nodeType == "msup" || gm.nodeType == "msub") &&
          gm.children.length >= 1 &&
          (lgm.nodeType == "mn" || isNumberPunctuation(lastGroup))
        ) {
          val base = gm.children(0)
          if (base.isInstanceOf[MathNode] && base.asInstanceOf[MathNode].nodeType == "mn") {
            base.asInstanceOf[MathNode].children.insertAll(0, lgm.children)
            groups.remove(groups.length - 1)
          }
          groups += gm
          lastGroup = Nullable(gm)
          i += 1
        }
        // \not
        else if (lgm.nodeType == "mi" && lgm.children.length == 1) {
          val lastChild = lgm.children(0)
          if (
            lastChild.isInstanceOf[TextNode] &&
            lastChild.asInstanceOf[TextNode].text == "̸" &&
            (gm.nodeType == "mo" || gm.nodeType == "mi" ||
              gm.nodeType == "mn")
          ) {
            val child = gm.children(0)
            if (child.isInstanceOf[TextNode] && child.asInstanceOf[TextNode].text.nonEmpty) {
              // Overlay with combining character long solidus
              val tn      = child.asInstanceOf[TextNode]
              val newText = tn.text.substring(0, 1) + "̸" +
                tn.text.substring(1)
              // Replace the TextNode with a new one with updated text
              gm.children(0) = new TextNode(newText)
              groups.remove(groups.length - 1)
            }
            groups += gm
            lastGroup = Nullable(gm)
            i += 1
          } else {
            groups += group
            lastGroup = if (group.isInstanceOf[MathNode]) Nullable(group.asInstanceOf[MathNode]) else Nullable.Null
            i += 1
          }
        } else {
          groups += group
          lastGroup = if (group.isInstanceOf[MathNode]) Nullable(group.asInstanceOf[MathNode]) else Nullable.Null
          i += 1
        }
      } else {
        groups += group
        lastGroup = if (group.isInstanceOf[MathNode]) Nullable(group.asInstanceOf[MathNode]) else Nullable.Null
        i += 1
      }
    }
    groups
  }

  /** Equivalent to buildExpression, but wraps the elements in an <mrow> if there's more than one. Returns a single node instead of an array.
    */
  def buildExpressionRow(
    expression: Array[AnyParseNode],
    options:    Options,
    isOrdgroup: Boolean = false
  ): MathDomNode =
    makeRow(buildExpression(expression, options, isOrdgroup))

  /** Takes a group from the parser and calls the appropriate groupBuilders function on it to produce a MathML node.
    */
  def buildGroup(
    group:   Nullable[AnyParseNode],
    options: Options
  ): MathDomNode = boundary {
    if (group.isEmpty) {
      break(new MathNode("mrow"))
    }

    val g             = group.get
    val groupBuilders = FunctionDef._mathmlGroupBuilders

    if (groupBuilders.contains(g.nodeType)) {
      // Call the groupBuilders function
      // The original TypeScript declares this as returning MathNode but
      // uses an unsafe `as` cast. In practice, group builders may return
      // SpaceNode, DocumentFragment, or other MathDomNode subtypes.
      groupBuilders(g.nodeType)(g, options.asInstanceOf[AnyRef]).asInstanceOf[MathDomNode]
    } else {
      throw new ParseError("Got group of unknown type: '" + g.nodeType + "'")
    }
  }

  // Overload that takes a raw AnyParseNode (non-nullable)
  def buildGroup(
    group:   AnyParseNode,
    options: Options
  ): MathDomNode =
    buildGroup(Nullable(group), options)

  /** Takes a full parse tree and settings and builds a MathML representation of it. In particular, we put the elements from building the parse tree into a <semantics> tag so we can also include that
    * TeX source as an annotation.
    *
    * Note that we actually return a domTree element with a `<math>` inside it so we can do appropriate styling.
    */
  def buildMathML(
    tree:          Array[AnyParseNode],
    texExpression: String,
    options:       Options,
    isDisplayMode: Boolean,
    forMathmlOnly: Boolean
  ): DomSpan = {
    val expression = buildExpression(tree, options)

    // TODO: Make a pass thru the MathML similar to buildHTML.traverseNonSpaceNodes
    // and add spacing nodes. This is necessary only adjacent to math operators
    // like \sin or \lim or to subsup elements that contain math operators.
    // MathML takes care of the other spacing issues.

    // Wrap up the expression in an mrow so it is presented in the semantics
    // tag correctly, unless it's a single <mrow> or <mtable>.
    val wrapper: MathDomNode =
      if (
        expression.length == 1 && expression(0).isInstanceOf[MathNode] &&
        rowLikeTypes.contains(expression(0).asInstanceOf[MathNode].nodeType)
      ) {
        expression(0)
      } else {
        new MathNode("mrow", ArrayBuffer.from(expression))
      }

    // Build a TeX annotation of the source
    val annotation = new MathNode("annotation", ArrayBuffer[MathDomNode](new TextNode(texExpression)))

    annotation.setAttribute("encoding", "application/x-tex")

    val semantics = new MathNode("semantics", ArrayBuffer[MathDomNode](wrapper, annotation))

    val math = new MathNode("math", ArrayBuffer[MathDomNode](semantics))
    math.setAttribute("xmlns", "http://www.w3.org/1998/Math/MathML")
    if (isDisplayMode) {
      math.setAttribute("display", "block")
    }

    // You can't style <math> nodes, so we wrap the node in a span.
    // NOTE: The span class is not typed to have <math> nodes as children, and
    // we don't want to make the children type more generic since the children
    // of span are expected to have more fields in `buildHtml` contexts.
    val wrapperClass = if (forMathmlOnly) "katex" else "katex-mathml"
    // TODO(ts)
    // Original TS: makeSpan([wrapperClass], [math as any])
    // MathNode is not HtmlDomNode, but the span just wraps it for styling.
    // We cast through VirtualNode to avoid ClassCastException.
    val mathChildren = ArrayBuffer[VirtualNode](math)
    val span         = new Span[VirtualNode](ArrayBuffer(wrapperClass), mathChildren)
    span.asInstanceOf[DomSpan]
  }
}
