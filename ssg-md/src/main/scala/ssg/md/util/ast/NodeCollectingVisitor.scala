/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/NodeCollectingVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package ast

import ssg.md.util.collection.ClassificationBag
import ssg.md.util.collection.SubClassingBag

import java.{ util => ju }

import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

class NodeCollectingVisitor(classes: ju.Set[Class[?]]) {

  private val subClassMap: ju.HashMap[Class[?], ju.List[Class[?]]] = new ju.HashMap[Class[?], ju.List[Class[?]]]()
  private val included:    ju.HashSet[Class[?]]                    = new ju.HashSet[Class[?]]()
  private val excluded:    ju.HashSet[Class[?]]                    = new ju.HashSet[Class[?]]()
  private val nodes:       ClassificationBag[Class[?], Node]       = new ClassificationBag[Class[?], Node]((node: Node) => node.getClass)
  private val _classes:    Array[Class[?]]                         = classes.toArray(NodeCollectingVisitor.EMPTY_CLASSES)

  included.addAll(classes)

  {
    val it = classes.iterator()
    while (it.hasNext) {
      val clazz     = it.next()
      val classList = new ju.ArrayList[Class[?]](1)
      classList.add(clazz)
      subClassMap.put(clazz, classList)
    }
  }

  def collect(node: Node): Unit =
    visitNode(node)

  def getSubClassingBag: SubClassingBag[Node] =
    new SubClassingBag[Node](nodes, subClassMap)

  private def visitNode(node: Node): Unit = boundary {
    val nodeClass = node.getClass
    if (included.contains(nodeClass)) {
      nodes.add(node)
    } else if (!excluded.contains(nodeClass)) {
      // see if implements one of the original classes passed in
      var i = 0
      while (i < _classes.length) {
        val clazz = _classes(i)
        if (clazz.isInstance(node)) {
          // this class is included
          included.add(nodeClass)
          var classList = subClassMap.get(clazz)
          if (classList == null) {
            classList = new ju.ArrayList[Class[?]](2)
            classList.add(clazz)
            classList.add(nodeClass)
            subClassMap.put(clazz, classList)
          } else {
            classList.add(nodeClass)
          }

          nodes.add(node)
          visitChildren(node)
          break(()) // Java: return (skip final visitChildren)
        }
        i += 1
      }

      // not of interest, exclude for next occurrence
      excluded.add(nodeClass)
    }
    visitChildren(node)
  }

  private def visitChildren(parent: Node): Unit = {
    var node = parent.firstChild
    while (node.isDefined) {
      // A subclass of this visitor might modify the node, resulting in getNext returning a different node or no
      // node after visiting it. So get the next node before visiting.
      val next = node.get.next
      visitNode(node.get)
      node = next
    }
  }
}

object NodeCollectingVisitor {
  private val EMPTY_CLASSES: Array[Class[?]] = Array.empty[Class[?]]
}
