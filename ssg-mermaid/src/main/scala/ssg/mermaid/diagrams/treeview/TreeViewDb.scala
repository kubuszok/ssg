/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * SSG-native tree view diagram (not an upstream Mermaid diagram type).
 */
package ssg
package mermaid
package diagrams
package treeview

import scala.collection.mutable

/** A node in a tree view. */
final case class TreeNode(label: String, children: mutable.ArrayBuffer[TreeNode] = mutable.ArrayBuffer.empty)

/** Mutable database for tree view diagram data. */
final class TreeViewDb {

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  val roots: mutable.ArrayBuffer[TreeNode] = mutable.ArrayBuffer.empty

  def addRoot(label: String): TreeNode = {
    val node = TreeNode(label); roots += node; node
  }

  def clear(): Unit = { title = ""; accTitle = ""; accDescription = ""; roots.clear() }
}
