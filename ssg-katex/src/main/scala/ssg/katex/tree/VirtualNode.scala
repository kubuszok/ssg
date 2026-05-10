/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This file defines the VirtualNode trait and DocumentFragment class.
 * VirtualNode is the base trait for all virtual DOM nodes in KaTeX.
 * DocumentFragment wraps an array of children without its own DOM
 * representation.
 *
 * Original source: katex src/tree.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: VirtualNode (interface) -> VirtualNode (trait)
 *   Convention: TypeScript class -> Scala class with type parameter bound
 *   Idiom: ReadonlyArray -> IndexedSeq; document.createDocumentFragment -> InMemoryNode
 */
package ssg
package katex
package tree

import scala.collection.mutable.ArrayBuffer

// To ensure that all nodes have compatible signatures for these methods.
trait VirtualNode {
  def toNode():   InMemoryNode
  def toMarkup(): String
}

/** This node represents a document fragment, which contains elements, but when placed into the DOM doesn't have any representation itself. It only contains children and doesn't have any DOM node
  * properties.
  */
class DocumentFragment[ChildType <: VirtualNode](
  val children: IndexedSeq[ChildType]
) extends HtmlDomNode
    with MathDomNode {

  var classes:     ArrayBuffer[String] = ArrayBuffer.empty
  var height:      Double              = 0.0
  var depth:       Double              = 0.0
  var maxFontSize: Double              = 0.0
  var style:       CssStyle            = CssStyle() // Never used; needed for satisfying interface.

  def hasClass(className: String): Boolean =
    classes.contains(className)

  /** Convert the fragment into a node. */
  def toNode(): InMemoryNode = {
    val frag = InMemoryNode.fragment()

    var i = 0
    while (i < children.length) {
      frag.appendChild(children(i).toNode())
      i += 1
    }

    frag
  }

  /** Convert the fragment into HTML markup. */
  def toMarkup(): String = {
    val markup = new StringBuilder

    // Simply concatenate the markup for the children together.
    var i = 0
    while (i < children.length) {
      markup.append(children(i).toMarkup())
      i += 1
    }

    markup.toString
  }

  /** Converts the math node into a string, similar to innerText. Applies to MathDomNode's only.
    */
  def toText(): String =
    // To avoid this, we would subclass documentFragment separately for
    // MathML, but polyfills for subclassing is expensive per PR 1469.
    // TODO(ts): Only works for ChildType = MathDomNode.
    children.map { child =>
      child.asInstanceOf[MathDomNode].toText()
    }.mkString
}
