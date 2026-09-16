/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/block/blockDb.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces module-level mutable state with mutable class
 *   Idiom: Mutable collections for accumulation during parsing
 *   Renames: blockDb module functions -> BlockDb methods
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package block

import scala.collection.mutable

/** A block node in the diagram.
  *
  * @param id
  *   unique identifier
  * @param label
  *   display label
  * @param width
  *   column span
  */
final case class BlockNode(id: String, label: String, width: Int = 1)

/** A row of blocks. */
final case class BlockRow(blocks: mutable.ArrayBuffer[BlockNode] = mutable.ArrayBuffer.empty)

/** Mutable database for block diagram data. */
final class BlockDb {

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""
  var columns:        Int    = 1

  val rows:  mutable.ArrayBuffer[BlockRow]                 = mutable.ArrayBuffer.empty
  val edges: mutable.ArrayBuffer[(String, String, String)] = mutable.ArrayBuffer.empty // (from, to, label)

  /** Starts a new row. */
  def newRow(): BlockRow = {
    val row = BlockRow()
    rows += row
    row
  }

  /** Adds a block to the current row. If no row exists, creates one. */
  def addBlock(id: String, label: String, width: Int = 1): Unit = {
    val row = if (rows.isEmpty) newRow() else rows.last
    row.blocks += BlockNode(id, label, width)
  }

  /** Adds an edge between two blocks. */
  def addEdge(from: String, to: String, label: String = ""): Unit =
    edges += ((from, to, label))

  /** Returns all blocks from all rows. */
  def allBlocks: Seq[BlockNode] =
    rows.flatMap(_.blocks).toSeq

  /** Clears all state. */
  def clear(): Unit = {
    title = ""
    accTitle = ""
    accDescription = ""
    columns = 1
    rows.clear()
    edges.clear()
  }
}
