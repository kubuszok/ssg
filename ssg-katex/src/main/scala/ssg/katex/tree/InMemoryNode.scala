/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Platform-independent in-memory DOM node representation. In the original
 * KaTeX code, toNode() creates browser DOM elements via document.createElement.
 * Since SSG is a static site generator and does not depend on a browser DOM,
 * we model the node tree as simple data objects. The toMarkup() path is the
 * critical output path; this representation exists for API fidelity.
 *
 * Original source: (SSG-specific, no upstream equivalent)
 * Original author: SSG contributors
 * Original license: Apache-2.0
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package tree

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.LinkedHashMap

/**
 * A platform-independent in-memory DOM node.
 *
 * Replaces browser APIs like document.createElement, document.createTextNode,
 * and document.createDocumentFragment with a simple data structure.
 */
class InMemoryNode private (
    val nodeType: InMemoryNode.NodeType,
    val tagName: String,
    val namespace: String
) {

  var className: String = ""
  var textContent: String = ""
  val attributes: LinkedHashMap[String, String] = LinkedHashMap.empty
  val children: ArrayBuffer[InMemoryNode] = ArrayBuffer.empty
  val style: LinkedHashMap[String, String] = LinkedHashMap.empty

  def setAttribute(name: String, value: String): Unit = {
    attributes(name) = value
  }

  def appendChild(child: InMemoryNode): Unit = {
    children += child
  }
}

object InMemoryNode {

  enum NodeType {
    case Element
    case Text
    case Fragment
  }

  /** Create an element node (analogous to document.createElement). */
  def element(tagName: String): InMemoryNode = {
    new InMemoryNode(NodeType.Element, tagName, "")
  }

  /** Create a namespaced element node (analogous to document.createElementNS). */
  def elementNS(namespace: String, tagName: String): InMemoryNode = {
    new InMemoryNode(NodeType.Element, tagName, namespace)
  }

  /** Create a text node (analogous to document.createTextNode). */
  def text(content: String): InMemoryNode = {
    val node = new InMemoryNode(NodeType.Text, "", "")
    node.textContent = content
    node
  }

  /** Create a document fragment (analogous to document.createDocumentFragment). */
  def fragment(): InMemoryNode = {
    new InMemoryNode(NodeType.Fragment, "", "")
  }
}
