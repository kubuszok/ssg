/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package state

import munit.FunSuite

final class StateDiagramSuite extends FunSuite {

  test("detect: stateDiagram-v2 keyword") {
    assert(StateDiagram.detect("stateDiagram-v2\n    [*] --> s1"))
  }

  test("detect: stateDiagram keyword") {
    assert(StateDiagram.detect("stateDiagram\n    [*] --> s1"))
  }

  test("detect: not a state diagram") {
    assert(!StateDiagram.detect("graph TD\n    A-->B"))
  }

  test("parse: basic transitions") {
    val db = StateParser.parse("stateDiagram-v2\n    [*] --> s1\n    s1 --> s2\n    s2 --> [*]")
    assert(db.transitions.nonEmpty, "Should have transitions")
    assert(db.states.nonEmpty, "Should have states")
  }

  test("parse: labeled transition") {
    val db = StateParser.parse("stateDiagram-v2\n    s1 --> s2 : Go next")
    assertEquals(db.transitions.length, 1)
    assertEquals(db.transitions(0).label, "Go next")
  }

  test("parse: state with description") {
    val db = StateParser.parse("stateDiagram-v2\n    s1 : This is state 1")
    assert(db.states.contains("s1"))
    assertEquals(db.states("s1").description, "This is state 1")
  }

  test("parse: special state fork") {
    val db = StateParser.parse("stateDiagram-v2\n    state fork1 <<fork>>")
    assert(db.states.contains("fork1"))
    assertEquals(db.states("fork1").stateType, StateType.Fork)
  }

  test("parse: direction override") {
    val db = StateParser.parse("stateDiagram-v2\n    direction LR\n    s1 --> s2")
    assertEquals(db.direction, "LR")
  }
}
