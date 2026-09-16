/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Ported from: mermaid/packages/mermaid/src/diagrams/error/errorDiagram.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package error_

/** Trivial parser for error diagram — captures the error text. */
object ErrorParser {

  def parse(input: String): ErrorDb = {
    val db = new ErrorDb
    // The error diagram is shown when parsing of another diagram fails.
    // The input text becomes the error message.
    val cleaned = input.trim
    if (cleaned.nonEmpty) {
      db.errorMessage = cleaned
    }
    db
  }
}
