/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package treeview

import munit.FunSuite

final class TreeViewDiagramSuite extends FunSuite {

  test("detect: treeView keyword") {
    assert(TreeViewDiagram.detect("treeView\nRoot\n  Child"))
  }

  test("detect: not a treeview diagram") {
    assert(!TreeViewDiagram.detect("pie\n    \"A\" : 100"))
  }

  test("parse: hierarchical tree") {
    val db = TreeViewParser.parse("treeView\nRoot\n  Child1\n    Grandchild\n  Child2")
    assertEquals(db.roots.size, 1)
    assertEquals(db.roots(0).label, "Root")
    assertEquals(db.roots(0).children.size, 2)
    assertEquals(db.roots(0).children(0).children.size, 1)
  }

  test("render: produces valid SVG") {
    val svg = TreeViewDiagram.render("treeView\nRoot\n  Child1\n  Child2")
    assert(svg.contains("<svg"))
    assert(svg.contains("<circle"))
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("treeView\nRoot")
    assert(svg.contains("<svg"))
  }
}
