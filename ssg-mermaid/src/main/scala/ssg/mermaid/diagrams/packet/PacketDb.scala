/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/packet/packetDb.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package packet

import scala.collection.mutable

/** A single field in a packet header.
  *
  * @param label
  *   field name/label
  * @param startBit
  *   starting bit position
  * @param endBit
  *   ending bit position (inclusive)
  */
final case class PacketField(label: String, startBit: Int, endBit: Int)

/** Mutable database for packet diagram data. */
final class PacketDb {

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""
  var bitsPerRow:     Int    = 32

  val fields: mutable.ArrayBuffer[PacketField] = mutable.ArrayBuffer.empty

  /** Adds a field.
    *
    * Validates that the field range is valid and contiguous with previous fields.
    *
    * @throws IllegalArgumentException
    *   if the end bit is less than the start bit or the block is not contiguous
    */
  def addField(label: String, startBit: Int, endBit: Int): Unit = {
    if (endBit < startBit) {
      throw new IllegalArgumentException(
        s"Packet block $startBit - $endBit is invalid. End must be greater than start."
      )
    }
    if (fields.nonEmpty) {
      val expectedStart = fields.last.endBit + 1
      if (startBit != expectedStart) {
        throw new IllegalArgumentException(
          s"Packet block $startBit - $endBit is not contiguous. It should start from $expectedStart."
        )
      }
    }
    fields += PacketField(label, startBit, endBit)
  }

  /** Clears all state. */
  def clear(): Unit = {
    title = ""; accTitle = ""; accDescription = ""; bitsPerRow = 32; fields.clear()
  }
}
