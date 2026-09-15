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

/** Minimal database for the error diagram. */
final class ErrorDb {

  var errorMessage:   String = "Syntax error in diagram"
  var accTitle:       String = ""
  var accDescription: String = ""

  def clear(): Unit = { errorMessage = "Syntax error in diagram"; accTitle = ""; accDescription = "" }
}
