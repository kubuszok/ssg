/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Tests ported from:
 *   mermaid/packages/mermaid/src/diagrams/packet/packet.spec.ts
 */
package ssg
package mermaid
package diagrams
package packet

import munit.FunSuite

final class PacketDiagramSuite extends FunSuite {

  // ────────────────────────────────────────────────────────────────────────────
  // Detection tests
  // ────────────────────────────────────────────────────────────────────────────

  test("detect: packet-beta keyword") {
    assert(PacketDiagram.detect("packet-beta\n0-15: \"Source Port\""))
  }

  test("detect: not a packet diagram") {
    assert(!PacketDiagram.detect("pie\n    \"A\" : 100"))
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Parse tests (from packet.spec.ts)
  // ────────────────────────────────────────────────────────────────────────────

  test("should handle a packet-beta definition") {
    val db = PacketParser.parse("packet-beta")
    assert(db.fields.isEmpty)
  }

  test("should handle diagram with data and title") {
    val db = PacketParser.parse(
      """packet-beta
        |    title Packet diagram
        |    0-10: "test"
        |    """.stripMargin
    )
    assertEquals(db.title, "Packet diagram")
    assertEquals(db.fields.size, 1)
    assertEquals(db.fields(0).startBit, 0)
    assertEquals(db.fields(0).endBit, 10)
    assertEquals(db.fields(0).label, "test")
  }

  test("should handle single bits") {
    val db = PacketParser.parse(
      """packet-beta
        |    0-10: "test"
        |    11: "single"
        |    """.stripMargin
    )
    assertEquals(db.fields.size, 2)
    assertEquals(db.fields(0).startBit, 0)
    assertEquals(db.fields(0).endBit, 10)
    assertEquals(db.fields(1).startBit, 11)
    assertEquals(db.fields(1).endBit, 11)
    assertEquals(db.fields(1).label, "single")
  }

  test("should throw error if numbers are not continuous") {
    intercept[Exception] {
      PacketParser.parse(
        """packet-beta
          |    0-16: "test"
          |    18-20: "error"
          |    """.stripMargin
      )
    }
  }

  test("should throw error if numbers are not continuous for single packets") {
    intercept[Exception] {
      PacketParser.parse(
        """packet-beta
          |    0-16: "test"
          |    18: "error"
          |    """.stripMargin
      )
    }
  }

  test("should throw error if end is less than start") {
    intercept[Exception] {
      PacketParser.parse(
        """packet-beta
          |    0-16: "test"
          |    25-20: "error"
          |    """.stripMargin
      )
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Original existing tests (preserved)
  // ────────────────────────────────────────────────────────────────────────────

  test("parse: fields with bit ranges") {
    val db = PacketParser.parse("packet-beta\n0-15: \"Source Port\"\n16-31: \"Dest Port\"")
    assertEquals(db.fields.size, 2)
    assertEquals(db.fields(0).label, "Source Port")
    assertEquals(db.fields(0).startBit, 0)
    assertEquals(db.fields(0).endBit, 15)
    assertEquals(db.fields(1).startBit, 16)
  }

  test("render: produces valid SVG") {
    val svg = PacketDiagram.render("packet-beta\n0-15: \"Source Port\"\n16-31: \"Dest Port\"")
    assert(svg.contains("<svg"))
    assert(svg.contains("<rect"))
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("packet-beta\n0-15: \"Source Port\"")
    assert(svg.contains("<svg"))
  }
}
