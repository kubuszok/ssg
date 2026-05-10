/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Ported from: mermaid/packages/mermaid/src/diagrams/info/infoDb.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package info

/** Minimal database for the info diagram (version display). */
final class InfoDb {

  var version:        String = ssg.mermaid.Version
  var accTitle:       String = ""
  var accDescription: String = ""

  def clear(): Unit = { version = ssg.mermaid.Version; accTitle = ""; accDescription = "" }
}
