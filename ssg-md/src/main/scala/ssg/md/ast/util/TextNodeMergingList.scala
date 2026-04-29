/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/TextNodeMergingList.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/util/TextNodeMergingList.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ast
package util

import ssg.md.util.ast.Node
import ssg.md.util.sequence.BasedSequence

import java.{ util => ju }

class TextNodeMergingList {

  private var list:     ju.ArrayList[Node] = new ju.ArrayList[Node]()
  private var isMerged: Boolean            = true

  def add(node: Node): Unit = {
    list.add(node)
    if (node.isInstanceOf[Text]) isMerged = false
  }

  def add(nodeChars: BasedSequence): Unit =
    if (!nodeChars.isEmpty) {
      add(new Text(nodeChars))
    }

  def addChildrenOf(parent: Node): Unit = {
    var child = parent.firstChild
    while (child.isDefined) {
      val nextChild = child.get.next
      child.get.unlink()
      add(child.get)
      child = nextChild
    }
  }

  def appendMergedTo(parent: Node): Unit = {
    mergeList()
    val iter = list.iterator()
    while (iter.hasNext)
      parent.appendChild(iter.next())
  }

  def clear(): Unit = {
    list.clear()
    isMerged = true
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
    if (!isMerged) {
      // go through and see if some can be combined
      var mergedList: ju.ArrayList[Node] = null
      var lastText:   Node               = null

      val iter = list.iterator()
      while (iter.hasNext) {
        val child = iter.next()
        if (child.isInstanceOf[Text]) {
          if (!child.chars.isEmpty) {
            if (lastText == null) {
              lastText = child
            } else if (lastText.chars.isContinuedBy(child.chars)) {
              // merge their text
              lastText.chars = lastText.chars.spliceAtEnd(child.chars)
            } else {
              if (mergedList == null) mergedList = new ju.ArrayList[Node]()
              mergedList.add(lastText)
              lastText = child
            }
          }
        } else {
          if (mergedList == null) mergedList = new ju.ArrayList[Node]()
          if (lastText != null) {
            mergedList.add(lastText)
            lastText = null
          }
          mergedList.add(child)
        }
      }

      if (lastText != null) {
        if (mergedList == null) {
          list.clear()
          list.add(lastText)
        } else {
          mergedList.add(lastText)
        }
      }

      if (mergedList != null) {
        list = mergedList
      }
    }

  def getMergedList: ju.List[Node] = {
    mergeList()
    list
  }
}
