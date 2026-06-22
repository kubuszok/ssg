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

import ssg.mermaid.parse.ParseException

import scala.collection.mutable

/** Hand-written parser for Mermaid tree view syntax.
  *
  * Supported syntax (indentation-based):
  *   - `treeView` — header
  *   - `Root` — root node (indent 0)
  *   - `  Child1` — child node (indent 2)
  *   - `    Grandchild` — deeper child (indent 4)
  */
object TreeViewParser {

  def parse(input: String): TreeViewDb =
    parse(input, new TreeViewDb)

  /** Parses Mermaid tree view source text into the supplied [[TreeViewDb]].
    *
    * Mirrors `Diagram.fromText` holding the db across `setDiagramTitle` + `parser.parse`: the caller may pre-set a frontmatter title on the db, and the parser only overwrites it when an inline
    * `title` directive is present.
    */
  def parse(input: String, db: TreeViewDb): TreeViewDb = {
    val cleaned = cleanInput(input)
    val lines   = cleaned.split("\n")

    var i = 0
    while (i < lines.length && !lines(i).trim.toLowerCase.startsWith("treeview")) i += 1
    if (i >= lines.length) throw new ParseException("Expected 'treeView' keyword", 1, 1)
    i += 1

    // Parse indentation-based tree
    val stack = mutable.ArrayBuffer.empty[(Int, TreeNode)]

    while (i < lines.length) {
      val line    = lines(i); i += 1
      val trimmed = line.trim
      if (trimmed.isEmpty || trimmed.startsWith("%%")) {
        // skip
      } else {
        val indent = line.length - line.stripLeading().length
        val node   = TreeNode(trimmed)

        // Pop stack to find parent
        while (stack.nonEmpty && stack.last._1 >= indent) stack.remove(stack.size - 1)

        if (stack.isEmpty) {
          db.roots += node
        } else {
          stack.last._2.children += node
        }
        stack += ((indent, node))
      }
    }
    db
  }

  private def cleanInput(input: String): String =
    input.replaceAll("%%\\{[^}]*\\}%%", "")
}
