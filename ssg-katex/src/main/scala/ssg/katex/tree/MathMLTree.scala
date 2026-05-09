/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * These objects store data about MathML nodes. This is the MathML equivalent
 * of the types in DomTree.scala. Since MathML handles its own rendering, and
 * since we're mainly using MathML to improve accessibility, we don't manage
 * any of the styling state that the plain DOM nodes do.
 *
 * The `toNode` and `toMarkup` functions work similarly to how they do in
 * DomTree.scala, creating InMemoryNode nodes and HTML text markup respectively.
 *
 * Original source: katex src/mathMLTree.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: MathNode -> MathNode (same), TextNode -> TextNode (same)
 *   Convention: MathNodeType string union -> type alias with allowed values
 *   Idiom: document.createElementNS -> InMemoryNode.elementNS
 *   Idiom: document.createTextNode -> InMemoryNode.text
 */
package ssg
package katex
package tree

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.LinkedHashMap

import ssg.katex.data.Units
import ssg.katex.util.Utils

/**
 * MathML node types used in KaTeX. For a complete list of MathML nodes, see
 * https://developer.mozilla.org/en-US/docs/Web/MathML/Element.
 */
type MathNodeType = String
// Valid values:
// "math" | "annotation" | "semantics" |
// "mtext" | "mn" | "mo" | "mi" | "mspace" |
// "mover" | "munder" | "munderover" | "msup" | "msub" | "msubsup" |
// "mfrac" | "mroot" | "msqrt" |
// "mtable" | "mtr" | "mtd" | "mlabeledtr" |
// "mrow" | "menclose" |
// "mstyle" | "mpadded" | "mphantom" | "mglyph"

trait MathDomNode extends VirtualNode {
  def toText(): String
}

type MathDocumentFragment = DocumentFragment[MathDomNode]

def newDocumentFragment(
    children: IndexedSeq[MathDomNode]
): MathDocumentFragment = {
  new DocumentFragment(children)
}

/**
 * This node represents a general purpose MathML node of any type. The
 * constructor requires the type of node to create (for example, `"mo"` or
 * `"mspace"`, corresponding to `<mo>` and `<mspace>` tags).
 */
class MathNode(
    val nodeType: MathNodeType,
    val children: ArrayBuffer[MathDomNode] = ArrayBuffer.empty,
    val classes: ArrayBuffer[String] = ArrayBuffer.empty
) extends MathDomNode {

  val attributes: LinkedHashMap[String, String] = LinkedHashMap.empty

  /**
   * Sets an attribute on a MathML node. MathML depends on attributes to convey a
   * semantic content, so this is used heavily.
   */
  def setAttribute(name: String, value: String): Unit = {
    attributes(name) = value
  }

  /**
   * Gets an attribute on a MathML node.
   */
  def getAttribute(name: String): String = {
    attributes(name)
  }

  /**
   * Converts the math node into a MathML-namespaced InMemoryNode element.
   */
  def toNode(): InMemoryNode = {
    val node = InMemoryNode.elementNS(
      "http://www.w3.org/1998/Math/MathML", nodeType)

    attributes.foreach { case (attr, value) =>
      node.setAttribute(attr, value)
    }

    if (classes.nonEmpty) {
      node.className = DomTree.createClass(classes)
    }

    var i = 0
    while (i < children.length) {
      // Combine multiple TextNodes into one TextNode, to prevent
      // screen readers from reading each as a separate word [#3995]
      if (children(i).isInstanceOf[TextNode] &&
          (i + 1 < children.length) && children(i + 1).isInstanceOf[TextNode]) {
        val textBuilder = new StringBuilder
        textBuilder.append(children(i).toText())
        i += 1
        textBuilder.append(children(i).toText())
        while (i + 1 < children.length && children(i + 1).isInstanceOf[TextNode]) {
          i += 1
          textBuilder.append(children(i).toText())
        }
        node.appendChild(new TextNode(textBuilder.toString).toNode())
      } else {
        node.appendChild(children(i).toNode())
      }
      i += 1
    }

    node
  }

  /**
   * Converts the math node into an HTML markup string.
   */
  def toMarkup(): String = {
    val markup = new StringBuilder
    markup.append('<')
    markup.append(nodeType)

    // Add the attributes
    attributes.foreach { case (attr, value) =>
      markup.append(' ')
      markup.append(attr)
      markup.append("=\"")
      markup.append(Utils.escape(value))
      markup.append('"')
    }

    if (classes.nonEmpty) {
      markup.append(" class =\"")
      markup.append(Utils.escape(DomTree.createClass(classes)))
      markup.append('"')
    }

    markup.append('>')

    var i = 0
    while (i < children.length) {
      markup.append(children(i).toMarkup())
      i += 1
    }

    markup.append("</")
    markup.append(nodeType)
    markup.append('>')

    markup.toString
  }

  /**
   * Converts the math node into a string, similar to innerText, but escaped.
   */
  def toText(): String = {
    children.map(_.toText()).mkString
  }
}

/**
 * This node represents a piece of text.
 */
class TextNode(val text: String) extends MathDomNode {

  /**
   * Converts the text node into an InMemoryNode text node.
   */
  def toNode(): InMemoryNode = {
    InMemoryNode.text(text)
  }

  /**
   * Converts the text node into escaped HTML markup
   * (representing the text itself).
   */
  def toMarkup(): String = {
    Utils.escape(toText())
  }

  /**
   * Converts the text node into a string
   * (representing the text itself).
   */
  def toText(): String = {
    text
  }
}

/**
 * This node represents a space, but may render as <mspace.../> or as text,
 * depending on the width.
 */
class SpaceNode(val width: Double) extends MathDomNode {

  val character: ssg.commons.Nullable[String] = {
    import ssg.commons.Nullable
    // See https://www.w3.org/TR/2000/WD-MathML2-20000328/chapter6.html
    // for a table of space-like characters.  We use Unicode
    // representations instead of &LongNames; as it's not clear how to
    // make the latter via document.createTextNode.
    // U+200A = HAIR SPACE, U+2009 = THIN SPACE, U+2005 = FOUR-PER-EM SPACE,
    // U+205F = MEDIUM MATHEMATICAL SPACE, U+2063 = INVISIBLE SEPARATOR
    if (width >= 0.05555 && width <= 0.05556) {
      Nullable(" ")                     // &VeryThinSpace;
    } else if (width >= 0.1666 && width <= 0.1667) {
      Nullable(" ")                     // &ThinSpace;
    } else if (width >= 0.2222 && width <= 0.2223) {
      Nullable(" ")                     // &MediumSpace;
    } else if (width >= 0.2777 && width <= 0.2778) {
      Nullable("  ")               // &ThickSpace;
    } else if (width >= -0.05556 && width <= -0.05555) {
      Nullable(" ⁣")               // &NegativeVeryThinSpace;
    } else if (width >= -0.1667 && width <= -0.1666) {
      Nullable(" ⁣")               // &NegativeThinSpace;
    } else if (width >= -0.2223 && width <= -0.2222) {
      Nullable(" ⁣")               // &NegativeMediumSpace;
    } else if (width >= -0.2778 && width <= -0.2777) {
      Nullable(" ⁣")               // &NegativeThickSpace;
    } else {
      Nullable.Null
    }
  }

  /**
   * Converts the math node into a MathML-namespaced InMemoryNode element.
   */
  def toNode(): InMemoryNode = {
    character.fold {
      val node = InMemoryNode.elementNS(
        "http://www.w3.org/1998/Math/MathML", "mspace")
      node.setAttribute("width", Units.makeEm(width))
      node
    } { ch =>
      InMemoryNode.text(ch)
    }
  }

  /**
   * Converts the math node into an HTML markup string.
   */
  def toMarkup(): String = {
    character.fold {
      s"""<mspace width="${Units.makeEm(width)}"/>"""
    } { ch =>
      s"<mtext>$ch</mtext>"
    }
  }

  /**
   * Converts the math node into a string, similar to innerText.
   */
  def toText(): String = {
    character.getOrElse(" ")
  }
}
