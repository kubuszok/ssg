/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package flowchart

import munit.FunSuite

final class FlowchartDiagramSuite extends FunSuite {

  test("detect: graph TD") {
    assert(FlowchartDiagram.detect("graph TD\n    A-->B"))
  }

  test("detect: flowchart LR") {
    assert(FlowchartDiagram.detect("flowchart LR\n    A-->B"))
  }

  test("detect: not a flowchart") {
    assert(!FlowchartDiagram.detect("sequenceDiagram\n    Alice->>Bob: Hi"))
  }

  test("parse: simple graph TD with two nodes and an arrow") {
    val db = FlowchartParser.parse("graph TD\n    A-->B")
    assertEquals(db.direction, "TB")
    assert(db.nodes.contains("A"), "Node A should exist")
    assert(db.nodes.contains("B"), "Node B should exist")
    assertEquals(db.edges.length, 1)
    assertEquals(db.edges(0).src, "A")
    assertEquals(db.edges(0).dst, "B")
  }

  test("parse: nodes with shape brackets") {
    val db = FlowchartParser.parse("graph TD\n    A[Hello]\n    B(World)\n    C{Decision}\n    D((Circle))")
    assertEquals(db.nodes("A").text, "Hello")
    assertEquals(db.nodes("A").shape, "square")
    assertEquals(db.nodes("B").text, "World")
    assertEquals(db.nodes("B").shape, "round")
    assertEquals(db.nodes("C").text, "Decision")
    assertEquals(db.nodes("C").shape, "diamond")
    assertEquals(db.nodes("D").text, "Circle")
    assertEquals(db.nodes("D").shape, "circle")
  }

  test("parse: labeled edge with pipe syntax") {
    val db = FlowchartParser.parse("graph TD\n    A-->|label|B")
    assertEquals(db.edges.length, 1)
    assertEquals(db.edges(0).label, "label")
  }

  test("parse: dotted arrow") {
    val db = FlowchartParser.parse("graph TD\n    A-.->B")
    assertEquals(db.edges.length, 1)
    assertEquals(db.edges(0).stroke, "dotted")
  }

  test("parse: thick arrow") {
    val db = FlowchartParser.parse("graph TD\n    A==>B")
    assertEquals(db.edges.length, 1)
    assertEquals(db.edges(0).stroke, "thick")
  }

  test("parse: open link (no arrow)") {
    val db = FlowchartParser.parse("graph TD\n    A---B")
    assertEquals(db.edges.length, 1)
    assertEquals(db.edges(0).edgeType.getOrElse("arrow_open"), "arrow_open")
  }

  test("parse: subgraph") {
    val db = FlowchartParser.parse(
      """graph TD
        |    subgraph sub1
        |        A-->B
        |    end
        |    C-->A""".stripMargin
    )
    assert(db.subgraphs.nonEmpty, "Should have at least one subgraph")
    assert(db.nodes.contains("C"), "Node C should exist")
  }

  test("parse: LR direction") {
    val db = FlowchartParser.parse("graph LR\n    A-->B")
    assertEquals(db.direction, "LR")
  }

  test("parse: stadium shape") {
    val db = FlowchartParser.parse("graph TD\n    A([Stadium])")
    assertEquals(db.nodes("A").shape, "stadium")
    assertEquals(db.nodes("A").text, "Stadium")
  }

  test("parse: hexagon shape") {
    val db = FlowchartParser.parse("graph TD\n    A{{Hexagon}}")
    assertEquals(db.nodes("A").shape, "hexagon")
    assertEquals(db.nodes("A").text, "Hexagon")
  }

  test("parse: cylinder shape") {
    val db = FlowchartParser.parse("graph TD\n    A[(Database)]")
    assertEquals(db.nodes("A").shape, "cylinder")
    assertEquals(db.nodes("A").text, "Database")
  }

  test("parse: subroutine shape") {
    val db = FlowchartParser.parse("graph TD\n    A[[Subroutine]]")
    assertEquals(db.nodes("A").shape, "subroutine")
    assertEquals(db.nodes("A").text, "Subroutine")
  }

  test("parse: flag/asymmetric shape") {
    val db = FlowchartParser.parse("graph TD\n    A>Flag]")
    assertEquals(db.nodes("A").shape, "odd")
    assertEquals(db.nodes("A").text, "Flag")
  }

  test("render: simple graph TD produces valid SVG") {
    val svg = Mermaid.render("graph TD\n    A-->B")
    assert(svg.contains("<svg"), s"SVG should contain <svg tag, got: ${svg.take(200)}")
    assert(svg.contains("<rect") || svg.contains("<path"), "SVG should contain shape elements")
  }

  test("render: flowchart LR produces valid SVG") {
    val svg = Mermaid.render("flowchart LR\n    A[Hello]-->B[World]")
    assert(svg.contains("<svg"), "SVG should contain <svg tag")
    assert(svg.contains("viewBox"), "SVG should have viewBox")
  }

  test("render: nodes with text are rendered") {
    val svg = FlowchartDiagram.render("graph TD\n    A[Hello World]-->B[Goodbye]")
    assert(svg.contains("Hello World"), "SVG should contain node text 'Hello World'")
    assert(svg.contains("Goodbye"), "SVG should contain node text 'Goodbye'")
  }

  test("render: edge markers are present") {
    val svg = Mermaid.render("graph TD\n    A-->B")
    assert(svg.contains("<marker"), "SVG should contain marker definitions")
    assert(svg.contains("<defs"), "SVG should contain defs section")
  }

  test("render: styles are embedded") {
    val svg = Mermaid.render("graph TD\n    A-->B")
    assert(svg.contains("<style"), "SVG should contain style element")
    assert(svg.contains(".node"), "CSS should contain .node class")
  }

  test("render: pie chart is now supported via Mermaid dispatch") {
    val result = Mermaid.render("pie\n    title Pets\n    \"Dogs\" : 386")
    assert(result.contains("<svg"), "Pie chart should now render SVG")
  }

  test("destructLink: basic arrow") {
    val db   = new FlowchartDb
    val info = db.destructLink("-->")
    assertEquals(info.edgeType, "arrow_point")
    assertEquals(info.stroke, "normal")
  }

  test("destructLink: thick arrow") {
    val db   = new FlowchartDb
    val info = db.destructLink("==>")
    assertEquals(info.edgeType, "arrow_point")
    assertEquals(info.stroke, "thick")
  }

  test("destructLink: dotted arrow") {
    val db   = new FlowchartDb
    val info = db.destructLink("-.->")
    assertEquals(info.edgeType, "arrow_point")
    assertEquals(info.stroke, "dotted")
  }

  test("destructLink: open link") {
    val db   = new FlowchartDb
    val info = db.destructLink("---")
    assertEquals(info.edgeType, "arrow_open")
    assertEquals(info.stroke, "normal")
  }

  test("FlowchartDb.clear resets all state") {
    val db = new FlowchartDb
    db.addNode("A")
    db.addNode("B")
    db.addEdge("A", "B")
    db.clear()
    assert(db.nodes.isEmpty)
    assert(db.edges.isEmpty)
  }

  test("FlowchartDb.setDirection normalizes TD to TB") {
    val db = new FlowchartDb
    db.setDirection("TD")
    assertEquals(db.direction, "TB")
  }

  test("FlowchartDb.addClass adds styles") {
    val db = new FlowchartDb
    db.addClass("myClass", Array("fill:#f9f", "stroke:#333"))
    assert(db.classes.contains("myClass"))
    assertEquals(db.classes("myClass").styles.length, 2)
  }
}
