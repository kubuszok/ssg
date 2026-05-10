/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/c4/parser/c4Diagram.jison
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package c4

import ssg.mermaid.parse.ParseException

/** Hand-written parser for Mermaid C4 diagram syntax.
  *
  * Supported syntax:
  *   - `C4Context` / `C4Container` / `C4Component` / `C4Deployment` / `C4Dynamic` — header
  *   - `Person(alias, "Label", "Description")` — person
  *   - `System(alias, "Label", "Description")` — system
  *   - `Container(alias, "Label", "Technology", "Description")` — container
  *   - `Component(alias, "Label", "Technology", "Description")` — component
  *   - `Rel(from, to, "Label", "Technology")` — relationship
  *   - `System_Boundary(alias, "Label") {` ... `}` — boundary
  */
object C4Parser {

  private val DiagramHeaders: Set[String] = Set(
    "c4context",
    "c4container",
    "c4component",
    "c4deployment",
    "c4dynamic"
  )

  def parse(input: String): C4Db = {
    val db      = new C4Db
    val cleaned = cleanInput(input)
    val lines   = cleaned.split("\n").map(_.trim).filter(_.nonEmpty)

    var i = 0
    // Find header
    while (i < lines.length && !DiagramHeaders.contains(lines(i).toLowerCase)) i += 1
    if (i >= lines.length) throw new ParseException("Expected C4 diagram header", 1, 1)
    db.diagramType = lines(i).trim
    i += 1

    while (i < lines.length) {
      val line = lines(i).trim; i += 1
      if (line.startsWith("%%") || line == "{" || line == "}") {
        // skip
      } else if (line.toLowerCase.startsWith("title")) {
        db.title = line.substring(5).trim
      } else {
        parseLine(line, db)
      }
    }
    db
  }

  private def cleanInput(input: String): String =
    input.replaceAll("%%\\{[^}]*\\}%%", "").replaceAll("%%[^\n]*\n", "\n")

  private def parseLine(line: String, db: C4Db): Unit = {
    val lower = line.toLowerCase
    if (lower.startsWith("person(") || lower.startsWith("person_ext(")) {
      val args = extractArgs(line)
      if (args.length >= 2) db.addPerson(args(0), args(1), args.lift(2).getOrElse(""))
    } else if (lower.startsWith("system(") || lower.startsWith("system_ext(")) {
      val args = extractArgs(line)
      if (args.length >= 2) db.addSystem(args(0), args(1), args.lift(2).getOrElse(""))
    } else if (lower.startsWith("container(") || lower.startsWith("container_ext(")) {
      val args = extractArgs(line)
      if (args.length >= 2) db.addContainer(args(0), args(1), args.lift(2).getOrElse(""), args.lift(3).getOrElse(""))
    } else if (lower.startsWith("component(") || lower.startsWith("component_ext(")) {
      val args = extractArgs(line)
      if (args.length >= 2) db.addComponent(args(0), args(1), args.lift(2).getOrElse(""), args.lift(3).getOrElse(""))
    } else if (lower.startsWith("rel(") || lower.startsWith("birel(") || lower.startsWith("rel_")) {
      val args = extractArgs(line)
      if (args.length >= 3) db.addRelationship(args(0), args(1), args(2), args.lift(3).getOrElse(""))
    } else if (
      lower.startsWith("system_boundary(") || lower.startsWith("container_boundary(") ||
      lower.startsWith("enterprise_boundary(") || lower.startsWith("boundary(")
    ) {
      val args = extractArgs(line)
      if (args.length >= 2) db.addBoundary(args(0), args(1))
    }
    // Other lines are ignored
  }

  /** Extracts comma-separated arguments from a function-call-like syntax: `Func(arg1, "arg2", arg3)`. */
  private def extractArgs(line: String): Array[String] = {
    val parenStart = line.indexOf('(')
    val parenEnd   = line.lastIndexOf(')')
    if (parenStart < 0 || parenEnd < 0) Array.empty
    else {

      val content = line.substring(parenStart + 1, parenEnd)
      // Split by comma, respecting quotes
      val result  = scala.collection.mutable.ArrayBuffer.empty[String]
      val sb      = new StringBuilder()
      var inQuote = false
      for (c <- content)
        if (c == '"') { inQuote = !inQuote }
        else if (c == ',' && !inQuote) {
          result += sb.toString.trim; sb.clear()
        } else { sb.append(c) }
      result += sb.toString.trim
      result.toArray
    }
  }
}
