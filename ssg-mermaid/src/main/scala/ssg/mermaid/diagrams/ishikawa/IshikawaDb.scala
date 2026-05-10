/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Original source: mermaid ishikawa (fishbone) diagram
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package ishikawa

import scala.collection.mutable

/** A cause branch in an Ishikawa (fishbone) diagram. */
final case class CauseBranch(label: String, causes: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty)

/** Mutable database for Ishikawa diagram data. */
final class IshikawaDb {

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""
  var effect:         String = ""

  val branches: mutable.ArrayBuffer[CauseBranch] = mutable.ArrayBuffer.empty

  def setEffect(label: String): Unit = effect = label

  def addBranch(label: String): CauseBranch = {
    val b = CauseBranch(label)
    branches += b
    b
  }

  def addCause(branchLabel: String, cause: String): Unit =
    branches.find(_.label == branchLabel).foreach(_.causes += cause)

  def addCauseToLast(cause: String): Unit =
    if (branches.nonEmpty) branches.last.causes += cause

  def clear(): Unit = {
    title = ""; accTitle = ""; accDescription = ""; effect = ""; branches.clear()
  }
}
