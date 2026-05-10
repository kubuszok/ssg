/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package eventmodeling

import munit.FunSuite

final class EventModelingDiagramSuite extends FunSuite {

  test("detect: eventmodeling keyword") {
    assert(EventModelingDiagram.detect("eventmodeling\nlane Events"))
  }

  test("detect: not an eventmodeling diagram") {
    assert(!EventModelingDiagram.detect("pie\n    \"A\" : 100"))
  }

  test("parse: lanes and events") {
    val db = EventModelingParser.parse(
      "eventmodeling\nlane Commands\nlane Events\ncommand cmd1[\"Create Order\"] in Commands\nevent evt1[\"Order Created\"] in Events"
    )
    assertEquals(db.lanes.size, 2)
    assertEquals(db.events.size, 2)
    assertEquals(db.events(0).label, "Create Order")
    assertEquals(db.events(0).eventType, "command")
  }

  test("render: produces valid SVG") {
    val svg = EventModelingDiagram.render("eventmodeling\nlane Events\nevent e1[\"Test\"] in Events")
    assert(svg.contains("<svg"))
    assert(svg.contains("<rect"))
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("eventmodeling\nlane Events")
    assert(svg.contains("<svg"))
  }
}
