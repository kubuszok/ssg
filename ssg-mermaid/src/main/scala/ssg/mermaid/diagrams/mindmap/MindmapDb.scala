/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/mindmap/mindmapDb.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces module-level mutable state with mutable class
 *   Idiom: Tree structure with mutable children; clear() resets state
 *   Renames: mindmapDb module functions -> MindmapDb methods
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package mindmap

import lowlevel.Nullable

import scala.collection.mutable

/** Shape types for mindmap nodes. */
enum MindmapShape(val label: String) extends java.lang.Enum[MindmapShape] {
  case Default extends MindmapShape("default")
  case Square extends MindmapShape("square")
  case RoundedSquare extends MindmapShape("rounded_square")
  case Circle extends MindmapShape("circle")
  case Cloud extends MindmapShape("cloud")
  case Bang extends MindmapShape("bang")
  case Hexagon extends MindmapShape("hexagon")
}

/** A node in the mindmap tree.
  *
  * @param id
  *   unique node identifier
  * @param text
  *   display text
  * @param level
  *   indentation level (0 = root)
  * @param shape
  *   node shape
  * @param children
  *   child nodes
  * @param cssClass
  *   CSS class for styling
  * @param icon
  *   optional icon identifier
  */
final case class MindmapNode(
  id:           String,
  var text:     String,
  var level:    Int = 0,
  var shape:    MindmapShape = MindmapShape.Default,
  children:     mutable.ArrayBuffer[MindmapNode] = mutable.ArrayBuffer.empty,
  var cssClass: String = "",
  var icon:     String = ""
)

/** Mutable database for mindmap diagram data.
  *
  * Maintains a tree structure of nodes during parsing. The renderer reads from this database to produce SVG output.
  *
  * Ports the module-level mutable state and functions from `mindmapDb.ts`.
  */
final class MindmapDb {

  var root: Nullable[MindmapNode] = Nullable.empty

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  private var nodeCount: Int = 0

  /** Sets the root node of the mindmap.
    *
    * @param text
    *   root node text
    * @param shape
    *   root node shape
    */
  def setRoot(text: String, shape: MindmapShape = MindmapShape.Default): MindmapNode = {
    nodeCount += 1
    val node = MindmapNode(id = s"node$nodeCount", text = text, level = 0, shape = shape)
    root = Nullable(node)
    node
  }

  /** Adds a child node to a parent based on indentation level.
    *
    * Finds the appropriate parent in the tree based on the level: level 1 = child of root, level 2 = child of last level-1 node, etc.
    *
    * @param text
    *   node display text
    * @param level
    *   indentation level
    * @param shape
    *   node shape
    * @return
    *   the created node
    */
  def addNode(text: String, level: Int, shape: MindmapShape = MindmapShape.Default): MindmapNode = {
    nodeCount += 1
    val node = MindmapNode(id = s"node$nodeCount", text = text, level = level, shape = shape)

    root.foreach { r =>
      val parent = findParentAtLevel(r, level - 1)
      parent.children += node
    }

    node
  }

  /** Finds the last node at the given level (for parent lookup).
    *
    * Traverses the tree depth-first, following last children, to find the most recently added node at the target level.
    */
  private def findParentAtLevel(node: MindmapNode, targetLevel: Int): MindmapNode =
    if (node.level == targetLevel || node.children.isEmpty) {
      node
    } else {
      findParentAtLevel(node.children.last, targetLevel)
    }

  /** Returns all nodes as a flat list (depth-first traversal). */
  def allNodes: mutable.ArrayBuffer[MindmapNode] = {
    val result = mutable.ArrayBuffer.empty[MindmapNode]
    root.foreach { r =>
      collectNodes(r, result)
    }
    result
  }

  /** Recursively collects all nodes depth-first. */
  private def collectNodes(node: MindmapNode, result: mutable.ArrayBuffer[MindmapNode]): Unit = {
    result += node
    for (child <- node.children)
      collectNodes(child, result)
  }

  /** Clears all state for parsing a new diagram. */
  def clear(): Unit = {
    root = Nullable.empty
    nodeCount = 0
    title = ""
    accTitle = ""
    accDescription = ""
  }
}
