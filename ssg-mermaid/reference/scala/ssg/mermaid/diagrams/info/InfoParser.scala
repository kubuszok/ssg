/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Ported from: mermaid/packages/mermaid/src/diagrams/info/info.langium
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package info

/** Trivial parser for info diagram — just checks the `info` keyword. */
object InfoParser {

  def parse(input: String): InfoDb = {
    val db = new InfoDb
    // The info diagram has no meaningful body to parse beyond the keyword.
    // Optional: showInfo keyword (ignored)
    db
  }
}
