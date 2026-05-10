/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Tests ported from:
 *   mermaid/packages/mermaid/src/diagrams/block/parser/block.spec.ts
 */
package ssg
package mermaid
package diagrams
package block

import munit.FunSuite

final class BlockDiagramSuite extends FunSuite {

  // ────────────────────────────────────────────────────────────────────────────
  // Detection tests
  // ────────────────────────────────────────────────────────────────────────────

  test("detect: block-beta keyword") {
    assert(BlockDiagram.detect("block-beta\n    columns 3\n    A B C"))
  }

  test("detect: not a block diagram") {
    assert(!BlockDiagram.detect("pie\n    \"A\" : 100"))
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Block parsing tests (from block.spec.ts)
  // ────────────────────────────────────────────────────────────────────────────

  test("a diagram with a node") {
    val db     = BlockParser.parse("block-beta\n    id\n")
    val blocks = db.allBlocks
    assert(blocks.nonEmpty)
    assertEquals(blocks(0).id, "id")
    assertEquals(blocks(0).label, "id")
  }

  test("a node with a square shape and a label") {
    val db     = BlockParser.parse("block-beta\n    id[\"A label\"]\n")
    val blocks = db.allBlocks
    assertEquals(blocks.length, 1)
    assertEquals(blocks(0).id, "id")
    assertEquals(blocks(0).label, "A label")
  }

  test("a diagram with multiple nodes") {
    val db     = BlockParser.parse("block-beta\n    id1\n    id2\n")
    val blocks = db.allBlocks
    assertEquals(blocks.length, 2)
    assertEquals(blocks(0).id, "id1")
    assertEquals(blocks(1).id, "id2")
  }

  test("a diagram with three nodes") {
    val db     = BlockParser.parse("block-beta\n    id1\n    id2\n    id3\n")
    val blocks = db.allBlocks
    assertEquals(blocks.length, 3)
    assertEquals(blocks(0).id, "id1")
    assertEquals(blocks(1).id, "id2")
    assertEquals(blocks(2).id, "id3")
  }

  test("a node with a square shape and a label followed by plain node") {
    val db     = BlockParser.parse("block-beta\n    id[\"A label\"]\n    id2")
    val blocks = db.allBlocks
    assertEquals(blocks.length, 2)
    assertEquals(blocks(0).id, "id")
    assertEquals(blocks(0).label, "A label")
    assertEquals(blocks(1).id, "id2")
    assertEquals(blocks(1).label, "id2")
  }

  test("a diagram with column statements") {
    val db = BlockParser.parse("block-beta\n    columns 2\n    block1[\"Block 1\"]\n")
    assertEquals(db.columns, 2)
    val blocks = db.allBlocks
    assertEquals(blocks.length, 1)
  }

  test("blocks next to each other") {
    val db = BlockParser.parse(
      "block-beta\n    columns 2\n    block1[\"Block 1\"]\n    block2[\"Block 2\"]\n"
    )
    assertEquals(db.columns, 2)
    val blocks = db.allBlocks
    assertEquals(blocks.length, 2)
  }

  test("blocks on top of each other") {
    val db = BlockParser.parse(
      "block-beta\n    columns 1\n    block1[\"Block 1\"]\n    block2[\"Block 2\"]\n"
    )
    assertEquals(db.columns, 1)
    val blocks = db.allBlocks
    assertEquals(blocks.length, 2)
  }

  test("a diagram with multiple nodes with edges") {
    val db     = BlockParser.parse("block-beta\n    id1[\"first\"]  -->   id2[\"second\"]\n")
    val blocks = db.allBlocks
    assertEquals(blocks.length, 2)
    val edges = db.edges
    assertEquals(edges.length, 1)
    assertEquals(edges(0)._1, "id1")
    assertEquals(edges(0)._2, "id2")
  }

  test("a diagram with multiple nodes with edges and label") {
    val db = BlockParser.parse(
      "block-beta\n    id1[\"first\"]  -- \"a label\" -->   id2[\"second\"]\n"
    )
    val blocks = db.allBlocks
    assertEquals(blocks.length, 2)
    val edges = db.edges
    assertEquals(edges.length, 1)
    assertEquals(edges(0)._1, "id1")
    assertEquals(edges(0)._2, "id2")
    assertEquals(edges(0)._3, "a label")
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Original existing tests (preserved)
  // ────────────────────────────────────────────────────────────────────────────

  test("parse: columns and blocks") {
    val db = BlockParser.parse("block-beta\n    columns 3\n    A[\"Alpha\"] B[\"Beta\"]")
    assertEquals(db.columns, 3)
    val blocks = db.allBlocks
    assert(blocks.size >= 2)
    assertEquals(blocks(0).label, "Alpha")
    assertEquals(blocks(1).label, "Beta")
  }

  test("render: produces valid SVG") {
    val svg = BlockDiagram.render("block-beta\n    columns 2\n    A[\"Hello\"] B[\"World\"]")
    assert(svg.contains("<svg"))
    assert(svg.contains("<rect"))
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("block-beta\n    columns 2\n    A B")
    assert(svg.contains("<svg"))
  }
}
