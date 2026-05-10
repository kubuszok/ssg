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
 *   Convention: Replaces Langium parser with hand-written indentation-based parser
 *   Idiom: Scanner-based parsing; indentation level determines tree structure
 *   Renames: mindmap parser -> MindmapParser
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package mindmap

import scala.util.boundary
import scala.util.boundary.break

/** Hand-written parser for Mermaid mindmap syntax.
  *
  * Supported syntax:
  *   - `mindmap` — diagram header
  *   - Indentation-based tree structure
  *   - `root((Root))` — node with shape (circle)
  *   - `  child[Child]` — child node with shape (square)
  *   - `    grandchild` — grandchild node (default shape)
  *   - `::icon(fa fa-book)` — icon annotation
  */
object MindmapParser {

  /** Parses Mermaid mindmap source text into a [[MindmapDb]].
    *
    * @param input
    *   the raw Mermaid diagram text
    * @return
    *   a populated MindmapDb
    * @throws ParseException
    *   if the input cannot be parsed
    */
  def parse(input: String): MindmapDb = {
    val db      = new MindmapDb
    val cleaned = cleanInput(input)
    val lines   = cleaned.split("\n")

    var headerParsed = false
    var baseIndent   = -1

    for (rawLine <- lines) {
      val trimmed = rawLine.trim

      // Skip empty lines and comments
      if (trimmed.isEmpty || trimmed.startsWith("%%")) {
        // skip
      } else if (!headerParsed) {
        // First non-empty line should be "mindmap"
        if (trimmed.toLowerCase.startsWith("mindmap")) {
          headerParsed = true
        }
      } else if (trimmed.startsWith(":::") || trimmed.startsWith("::icon")) {
        // Decoration line (class or icon) — apply to the last-added node
        if (trimmed.startsWith(":::")) {
          val cls = trimmed.substring(3).trim
          db.root.foreach { r =>
            val node = if (r.children.isEmpty) r else lastNode(r)
            node.cssClass = cls
          }
        } else if (trimmed.startsWith("::icon(")) {
          val iconEnd = trimmed.indexOf(")", 7)
          if (iconEnd >= 0) {
            val ic = trimmed.substring(7, iconEnd)
            db.root.foreach { r =>
              val node = if (r.children.isEmpty) r else lastNode(r)
              node.icon = ic
            }
          }
        }
      } else {
        // Parse indentation-based tree
        val indent = countIndent(rawLine)

        if (baseIndent < 0) {
          baseIndent = indent
        }

        val level = if (baseIndent > 0) (indent - baseIndent) / 2 else indent / 2

        // Reset per-line parsed decorations
        lastParsedIcon = ""
        lastParsedCssClass = ""

        // Parse node text and shape
        val (text, shape) = parseNodeText(trimmed)

        if (text.nonEmpty) {
          if (db.root.isEmpty) {
            db.setRoot(text, shape)
          } else {
            db.addNode(text, math.max(level, 1), shape)
          }

          // Apply inline icon/class if parsed from the same line
          if (lastParsedIcon.nonEmpty || lastParsedCssClass.nonEmpty) {
            db.root.foreach { r =>
              val node = if (r.children.isEmpty) r else lastNode(r)
              if (lastParsedIcon.nonEmpty) node.icon = lastParsedIcon
              if (lastParsedCssClass.nonEmpty) node.cssClass = lastParsedCssClass
            }
          }
        }
      }
    }

    db
  }

  /** Removes directives and comments from input. */
  private def cleanInput(input: String): String = {
    var s = input
    s = s.replaceAll("%%\\{[^}]*\\}%%", "")
    // Don't remove inline comments to preserve indentation structure
    s
  }

  /** Counts the indentation level (number of leading spaces/tabs). */
  private def countIndent(line: String): Int = boundary {
    var count = 0
    var i     = 0
    while (i < line.length) {
      line.charAt(i) match {
        case ' '  => count += 1
        case '\t' => count += 4
        case _    => break(count)
      }
      i += 1
    }
    count
  }

  /** Parses a node's text and shape from the trimmed line.
    *
    * Shapes:
    *   - `text` — default (rounded rectangle)
    *   - `[text]` — square
    *   - `(text)` — rounded square
    *   - `((text))` — circle
    *   - `)text(` — cloud (bang)
    *   - `{{text}}` — hexagon
    *
    * @return
    *   (text, shape) tuple
    */
  /** Parses a node's text, shape, icon, and CSS class from the trimmed line.
    *
    * Returns (displayText, shape, icon, cssClass).
    *
    * Shapes can appear after an optional node ID:
    *   - `text` or `id` — default
    *   - `[text]` or `id[text]` — square
    *   - `(text)` or `id(text)` — rounded square
    *   - `((text))` or `id((text))` — circle
    *   - `)text(` or `id)text(` — cloud
    *   - `))text((` or `id))text((` — bang
    *   - `{{text}}` or `id{{text}}` — hexagon
    *   - `id["quoted text"]` — square with quoted text
    */
  private def parseNodeText(line: String): (String, MindmapShape) = {
    var text = line.trim

    // Strip CSS class annotations: :::class
    val classIdx = text.indexOf(":::")
    if (classIdx >= 0) {
      val cssClass = text.substring(classIdx + 3).trim
      text = text.substring(0, classIdx).trim
      // Store cssClass for later use by the node
      lastParsedCssClass = cssClass
    }

    // Strip icon annotations: ::icon(...)
    val iconIdx = text.indexOf("::icon(")
    if (iconIdx >= 0) {
      val iconEnd = text.indexOf(")", iconIdx + 7)
      if (iconEnd >= 0) {
        lastParsedIcon = text.substring(iconIdx + 7, iconEnd)
        text = text.substring(0, iconIdx).trim
      }
    } else {
      val simpleIconIdx = text.indexOf("::icon")
      if (simpleIconIdx >= 0) {
        text = text.substring(0, simpleIconIdx).trim
      }
    }

    // Strip comment suffix
    val commentIdx = text.indexOf("%%")
    if (commentIdx >= 0) {
      text = text.substring(0, commentIdx).trim
    }

    if (text.isEmpty) {
      ("", MindmapShape.Default)
    } else {
      // Try to find shape delimiters, potentially after an ID prefix
      // The shape could start at position 0 (no ID) or after an ID

      // Find the position where a shape delimiter starts
      val shapeStart = findShapeStart(text)

      if (shapeStart > 0) {
        // There's an ID prefix before the shape
        val shapePart = text.substring(shapeStart)
        parseShapeContent(shapePart)
      } else if (shapeStart == 0) {
        // Shape starts at beginning (no ID)
        parseShapeContent(text)
      } else {
        // No shape delimiter found — plain text
        (text, MindmapShape.Default)
      }
    }
  }

  /** Finds the position where a shape delimiter starts in the text. Returns -1 if no shape delimiter found.
    */
  private def findShapeStart(text: String): Int = boundary {
    var i = 0
    while (i < text.length) {
      text.charAt(i) match {
        case '[' | '(' | '{' => break(i)
        case ')'             =>
          // ')' as first char of shape for cloud, or after ID for cloud
          // Check if this is the start of )text( pattern
          if (i == 0 || (i > 0 && text.charAt(i - 1) != ')')) break(i)
          else break(i - 1) // )) for bang
        case _ => // continue
      }
      i += 1
    }
    -1
  }

  /** Parses shape content from a string that starts with a shape delimiter. */
  private def parseShapeContent(text: String): (String, MindmapShape) =
    if (text.startsWith("))") && text.endsWith("((")) {
      (text.substring(2, text.length - 2).trim, MindmapShape.Bang)
    } else if (text.startsWith("((") && text.endsWith("))")) {
      (text.substring(2, text.length - 2).trim, MindmapShape.Circle)
    } else if (text.startsWith("{{") && text.endsWith("}}")) {
      (text.substring(2, text.length - 2).trim, MindmapShape.Hexagon)
    } else if (text.startsWith(")") && text.endsWith("(")) {
      (text.substring(1, text.length - 1).trim, MindmapShape.Cloud)
    } else if (text.startsWith("[") && text.endsWith("]")) {
      var content = text.substring(1, text.length - 1).trim
      // Handle quoted content: ["text"]
      if (content.startsWith("\"") && content.endsWith("\"")) {
        content = content.substring(1, content.length - 1)
      }
      (content, MindmapShape.Square)
    } else if (text.startsWith("(") && text.endsWith(")")) {
      (text.substring(1, text.length - 1).trim, MindmapShape.RoundedSquare)
    } else {
      (text, MindmapShape.Default)
    }

  // Temporary state for icon and class parsed from the current line
  private var lastParsedIcon:     String = ""
  private var lastParsedCssClass: String = ""

  /** Returns the deepest last-added node in the tree. */
  private def lastNode(root: MindmapNode): MindmapNode =
    if (root.children.isEmpty) root
    else lastNode(root.children.last)
}
