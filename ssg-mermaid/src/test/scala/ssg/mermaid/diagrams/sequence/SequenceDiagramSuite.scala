/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package sequence

import munit.FunSuite

final class SequenceDiagramSuite extends FunSuite {

  test("detect: sequenceDiagram") {
    assert(SequenceDiagram.detect("sequenceDiagram\n    Alice->>Bob: Hello"))
  }

  test("detect: not a sequence diagram") {
    assert(!SequenceDiagram.detect("graph TD\n    A-->B"))
  }

  test("parse: basic message with solid arrow") {
    val db = SequenceParser.parse("sequenceDiagram\n    Alice->>Bob: Hello Bob")
    assert(db.actors.contains("Alice"), "Alice actor should exist")
    assert(db.actors.contains("Bob"), "Bob actor should exist")
    assert(db.messages.nonEmpty, "Should have messages")
  }

  test("parse: dotted arrow reply") {
    val db = SequenceParser.parse(
      """sequenceDiagram
        |    Alice->>Bob: Hello
        |    Bob-->>Alice: Hi back""".stripMargin
    )
    assert(db.actors.contains("Alice"))
    assert(db.actors.contains("Bob"))
    // Check we have message types for both solid and dotted
    val msgTypes = db.messages.map(_.msgType).toSet
    assert(msgTypes.contains(LineType.Solid), "Should have solid arrow message")
    assert(msgTypes.contains(LineType.Dotted), "Should have dotted arrow message")
  }

  test("parse: participant declaration") {
    val db = SequenceParser.parse(
      """sequenceDiagram
        |    participant A
        |    participant B
        |    A->>B: message""".stripMargin
    )
    assertEquals(db.getActorKeys.length, 2)
  }

  test("parse: actor declaration") {
    val db = SequenceParser.parse(
      """sequenceDiagram
        |    actor Alice
        |    actor Bob
        |    Alice->>Bob: hello""".stripMargin
    )
    assert(db.actors("Alice").actorType == "actor")
    assert(db.actors("Bob").actorType == "actor")
  }

  test("parse: participant with alias") {
    val db = SequenceParser.parse(
      """sequenceDiagram
        |    participant A as Alice
        |    participant B as Bob
        |    A->>B: Hello""".stripMargin
    )
    assert(db.actors.contains("A"))
    assert(db.actors("A").description == "Alice")
  }

  test("parse: note right of actor") {
    val db = SequenceParser.parse(
      """sequenceDiagram
        |    participant Alice
        |    Note right of Alice: This is a note""".stripMargin
    )
    assert(db.notes.nonEmpty, "Should have at least one note")
    assertEquals(db.notes.head.placement, Placement.RightOf)
    assert(db.notes.head.message.contains("This is a note"))
  }

  test("parse: note left of actor") {
    val db = SequenceParser.parse(
      """sequenceDiagram
        |    participant Alice
        |    Note left of Alice: Left note""".stripMargin
    )
    assertEquals(db.notes.head.placement, Placement.LeftOf)
  }

  test("parse: note over actor") {
    val db = SequenceParser.parse(
      """sequenceDiagram
        |    participant Alice
        |    participant Bob
        |    Note over Alice,Bob: Shared note""".stripMargin
    )
    assert(db.notes.nonEmpty)
    assertEquals(db.notes.head.placement, Placement.Over)
    assert(db.notes.head.actor.length >= 2, "Over note should have at least 2 actors")
  }

  test("parse: loop block") {
    val db = SequenceParser.parse(
      """sequenceDiagram
        |    Alice->>Bob: Hello
        |    loop Every minute
        |        Bob->>Alice: Ping
        |    end""".stripMargin
    )
    // Check loop start/end messages are present
    val hasLoopStart = db.messages.exists(_.msgType == LineType.LoopStart)
    val hasLoopEnd   = db.messages.exists(_.msgType == LineType.LoopEnd)
    assert(hasLoopStart, "Should have loop start message")
    assert(hasLoopEnd, "Should have loop end message")
  }

  test("parse: alt/else block") {
    val db = SequenceParser.parse(
      """sequenceDiagram
        |    Alice->>Bob: Hello
        |    alt is sick
        |        Bob->>Alice: Not so good
        |    else is well
        |        Bob->>Alice: Feeling fine
        |    end""".stripMargin
    )
    val hasAltStart = db.messages.exists(_.msgType == LineType.AltStart)
    val hasAltElse  = db.messages.exists(_.msgType == LineType.AltElse)
    val hasAltEnd   = db.messages.exists(_.msgType == LineType.AltEnd)
    assert(hasAltStart, "Should have alt start")
    assert(hasAltElse, "Should have alt else")
    assert(hasAltEnd, "Should have alt end")
  }

  test("parse: opt block") {
    val db = SequenceParser.parse(
      """sequenceDiagram
        |    Alice->>Bob: Hello
        |    opt Extra response
        |        Bob->>Alice: Thanks
        |    end""".stripMargin
    )
    val hasOptStart = db.messages.exists(_.msgType == LineType.OptStart)
    val hasOptEnd   = db.messages.exists(_.msgType == LineType.OptEnd)
    assert(hasOptStart, "Should have opt start")
    assert(hasOptEnd, "Should have opt end")
  }

  test("parse: activate/deactivate") {
    val db = SequenceParser.parse(
      """sequenceDiagram
        |    Alice->>Bob: Hello
        |    activate Bob
        |    Bob->>Alice: Hi
        |    deactivate Bob""".stripMargin
    )
    val hasActiveStart = db.messages.exists(_.msgType == LineType.ActiveStart)
    val hasActiveEnd   = db.messages.exists(_.msgType == LineType.ActiveEnd)
    assert(hasActiveStart, "Should have activation start")
    assert(hasActiveEnd, "Should have activation end")
  }

  test("parse: activation with + modifier") {
    val db = SequenceParser.parse(
      """sequenceDiagram
        |    Alice->>+Bob: Hello
        |    Bob->>-Alice: Hi""".stripMargin
    )
    val hasActiveStart = db.messages.exists(_.msgType == LineType.ActiveStart)
    val hasActiveEnd   = db.messages.exists(_.msgType == LineType.ActiveEnd)
    assert(hasActiveStart, "Should have activation start from + modifier")
    assert(hasActiveEnd, "Should have activation end from - modifier")
  }

  test("parse: autonumber") {
    val db = SequenceParser.parse(
      """sequenceDiagram
        |    autonumber
        |    Alice->>Bob: Hello
        |    Bob->>Alice: Hi""".stripMargin
    )
    val hasAutonum = db.messages.exists(_.msgType == LineType.Autonumber)
    assert(hasAutonum, "Should have autonumber message")
  }

  test("parse: cross arrow -x") {
    val db = SequenceParser.parse(
      """sequenceDiagram
        |    Alice-xBob: Lost message""".stripMargin
    )
    val hasCross = db.messages.exists(_.msgType == LineType.SolidCross)
    assert(hasCross, "Should have solid cross message")
  }

  test("parse: open arrow ->") {
    val db = SequenceParser.parse(
      """sequenceDiagram
        |    Alice->Bob: Open arrow""".stripMargin
    )
    val hasOpen = db.messages.exists(_.msgType == LineType.SolidOpen)
    assert(hasOpen, "Should have solid open message")
  }

  test("render: basic sequence diagram produces valid SVG") {
    val svg = SequenceDiagram.render(
      """sequenceDiagram
        |    Alice->>Bob: Hello Bob
        |    Bob-->>Alice: Hi Alice""".stripMargin
    )
    assert(svg.contains("<svg"), s"SVG should contain <svg tag")
    assert(svg.contains("viewBox"), "SVG should have viewBox")
    assert(svg.contains("Hello Bob"), "SVG should contain message text")
  }

  test("render: via Mermaid entry point") {
    val svg = Mermaid.render("sequenceDiagram\n    Alice->>Bob: Hello")
    assert(svg.contains("<svg"), "Should produce SVG via Mermaid.render")
    assert(!svg.startsWith("<!--"), "Should not be an unsupported type comment")
  }

  test("SequenceDb.clear resets all state") {
    val db = new SequenceDb
    db.addActor("A", "A")
    db.addActor("B", "B")
    db.addMessage("A", "B", "hello", wrap = false)
    db.clear()
    assert(db.actors.isEmpty)
    assert(db.messages.isEmpty)
    assert(db.notes.isEmpty)
  }

  test("SequenceDb.enableSequenceNumbers") {
    val db = new SequenceDb
    assert(!db.showSequenceNumbers)
    db.enableSequenceNumbers()
    assert(db.showSequenceNumbers)
    db.disableSequenceNumbers()
    assert(!db.showSequenceNumbers)
  }

  test("LineType.isLineType identifies message types") {
    assert(LineType.isLineType(LineType.Solid))
    assert(LineType.isLineType(LineType.Dotted))
    assert(LineType.isLineType(LineType.SolidCross))
    assert(LineType.isLineType(LineType.SolidOpen))
    assert(!LineType.isLineType(LineType.LoopStart))
    assert(!LineType.isLineType(LineType.Note))
  }

  test("LineType.isDotted identifies dotted types") {
    assert(LineType.isDotted(LineType.Dotted))
    assert(LineType.isDotted(LineType.DottedCross))
    assert(LineType.isDotted(LineType.DottedOpen))
    assert(!LineType.isDotted(LineType.Solid))
    assert(!LineType.isDotted(LineType.SolidCross))
  }

  test("parse: par block with and") {
    val db = SequenceParser.parse(
      """sequenceDiagram
        |    par Alice to Bob
        |        Alice->>Bob: Hello
        |    and Alice to John
        |        Alice->>John: Hello
        |    end""".stripMargin
    )
    val hasParStart = db.messages.exists(_.msgType == LineType.ParStart)
    val hasParAnd   = db.messages.exists(_.msgType == LineType.ParAnd)
    val hasParEnd   = db.messages.exists(_.msgType == LineType.ParEnd)
    assert(hasParStart, "Should have par start")
    assert(hasParAnd, "Should have par and")
    assert(hasParEnd, "Should have par end")
  }
}
