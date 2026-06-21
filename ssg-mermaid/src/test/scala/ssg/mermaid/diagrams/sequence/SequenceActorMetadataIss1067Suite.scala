/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package sequence

import munit.FunSuite

/** Differential suite for the actor-metadata half of ISS-1067.
  *
  * Upstream mermaid's `sequenceDiagram.jison` recognizes four actor-metadata statements (jison:248-274): `links`, `link`, `properties`, `details`. Each dispatches to the corresponding `sequenceDb.ts`
  * action (`addLinks`/`addALink`/`addProperties`/`addDetails`, lines 369-471) which JSON-parses the `text2` payload and merges it into the actor's `links`/`properties` maps. SSG previously dropped
  * these lines silently. This suite parses each statement and asserts the actor's maps are populated with the REAL parsed values.
  */
final class SequenceActorMetadataIss1067Suite extends FunSuite {

  // `links actor text2` → addLinks (sequenceDb.ts:369-383): JSON.parse the payload into the
  // actor's links map.
  test("Iss1067: links statement populates the actor's links map") {
    val db = SequenceParser.parse(
      "sequenceDiagram\n" +
        "links Alice: {\"GitHub\": \"https://github.com\", \"Docs\": \"https://docs.example\"}"
    )
    val alice = db.actors("Alice")
    assertEquals(alice.links.get("GitHub"), Some("https://github.com"))
    assertEquals(alice.links.get("Docs"), Some("https://docs.example"))
  }

  // `link actor text2` → addALink (sequenceDb.ts:385-403): a single `label @ url` pair, split on '@'
  // with label = slice(0, sep - 1).trim() and link = slice(sep + 1).trim().
  test("Iss1067: link statement adds a single label/url pair") {
    val db = SequenceParser.parse(
      "sequenceDiagram\n" +
        "link Bob: Dashboard @ https://dash.example"
    )
    val bob = db.actors("Bob")
    assertEquals(bob.links.get("Dashboard"), Some("https://dash.example"))
  }

  // `properties actor text2` → addProperties (sequenceDb.ts:419-431): JSON.parse the payload into
  // the actor's properties map.
  test("Iss1067: properties statement populates the actor's properties map") {
    val db = SequenceParser.parse(
      "sequenceDiagram\n" +
        "properties Carol: {\"class\": \"internal\", \"region\": \"eu\"}"
    )
    val carol = db.actors("Carol")
    assertEquals(carol.properties.get("class"), Some("internal"))
    assertEquals(carol.properties.get("region"), Some("eu"))
  }

  // `details actor text2` → addDetails (sequenceDb.ts:451-471): JSON-parse the details object and
  // merge its `properties` and `links` sub-objects into the actor (insertProperties/insertLinks,
  // lines 461-467). SSG is headless, so the inline payload is the details JSON directly.
  test("Iss1067: details statement merges properties and links sub-objects") {
    val db = SequenceParser.parse(
      "sequenceDiagram\n" +
        "details Dave: {\"properties\": {\"team\": \"core\"}, \"links\": {\"Wiki\": \"https://wiki.example\"}}"
    )
    val dave = db.actors("Dave")
    assertEquals(dave.properties.get("team"), Some("core"))
    assertEquals(dave.links.get("Wiki"), Some("https://wiki.example"))
  }

  // The four metadata statements also register the actor as a participant (jison `actor` production
  // → addParticipant, jison:308), matching upstream where the statement's actor token is applied
  // before the metadata action.
  test("Iss1067: metadata statement registers the actor as a participant") {
    val db = SequenceParser.parse(
      "sequenceDiagram\n" +
        "links Erin: {\"GitHub\": \"https://github.com\"}"
    )
    assert(db.actors.contains("Erin"), "Erin actor should be registered")
  }
}
