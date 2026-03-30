/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/TextNodeConverter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast
package util

import ssg.md.Nullable
import ssg.md.util.ast.Node
import ssg.md.util.sequence.BasedSequence

import scala.language.implicitConversions

import java.{ util => ju }

class TextNodeConverter(nodeChars: BasedSequence) {

  private var remainingChars: BasedSequence      = nodeChars
  private val list:           ju.ArrayList[Node] = new ju.ArrayList[Node]()

  def appendChild(child: Node): Unit = {
    val childChars = child.chars
    assert(
      nodeChars.containsAllOf(childChars),
      "child " + child.toAstString(false) + " is not within parent sequence " + Node.toSegmentSpan(nodeChars, Nullable.empty)
    )
    assert(
      remainingChars.containsAllOf(childChars),
      "child " + child.toAstString(false) + " is not within remaining sequence " + Node.toSegmentSpan(remainingChars, Nullable.empty)
    )
    child.unlink()
    if (!child.isInstanceOf[Text]) {
      if (remainingChars.startOffset < childChars.startOffset) {
        // add preceding chars as Text
        list.add(new Text(remainingChars.subSequence(0, childChars.startOffset - remainingChars.startOffset)))
      }

      // punch out remaining node chars
      remainingChars = remainingChars.subSequence(childChars.endOffset - remainingChars.startOffset)
      list.add(child)
    }
  }

  def addChildrenOf(parent: Node): Unit = {
    var child = parent.firstChild
    while (child.isDefined) {
      val nextChild = child.get.next
      appendChild(child.get)
      child = nextChild
    }
  }

  def appendMergedTo(parent: Node): Unit = {
    mergeList()
    val iter = list.iterator()
    while (iter.hasNext)
      parent.appendChild(iter.next())
    clear()
  }

  def clear(): Unit = {
    list.clear()
    remainingChars = BasedSequence.NULL
  }

  // insert and clear list
  def insertMergedBefore(sibling: Node): Unit = {
    mergeList()
    val iter = list.iterator()
    while (iter.hasNext)
      sibling.insertBefore(iter.next())
    clear()
  }

  // insert and clear list
  def insertMergedAfter(sibling: Node): Unit = {
    mergeList()
    var current = sibling
    val iter    = list.iterator()
    while (iter.hasNext) {
      val node = iter.next()
      current.insertAfter(node)
      current = node
    }
    clear()
  }

  private def mergeList(): Unit =
    if (!remainingChars.isEmpty) {
      list.add(new Text(remainingChars))
      remainingChars = BasedSequence.NULL
    }

  def getMergedList: ju.List[Node] = {
    mergeList()
    list
  }
}

object TextNodeConverter {

  // insert and clear list
  def mergeTextNodes(parent: Node): Unit = {
    var prevNode: Nullable[Node] = Nullable.empty
    var child = parent.firstChild
    while (child.isDefined) {
      val nextChild = child.get.next
      if (prevNode.isDefined && prevNode.get.isInstanceOf[Text] && child.get.isInstanceOf[Text] && prevNode.get.chars.isContinuedBy(child.get.chars)) {
        // merge them
        child.get.chars = prevNode.get.chars.spliceAtEnd(child.get.chars)
        prevNode.get.unlink()
      }
      prevNode = child
      child = nextChild
    }
  }
}
