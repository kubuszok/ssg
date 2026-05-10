/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/er/erDb.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces module-level mutable state with mutable class
 *   Idiom: Mutable collections for accumulation during parsing; clear() resets state
 *   Renames: erDb module functions -> ErDb methods
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package er

import scala.collection.mutable

/** Cardinality markers for ER relationships.
  *
  * Ports the `Cardinality` enum from the original.
  */
enum Cardinality(val label: String) extends java.lang.Enum[Cardinality] {
  case ZeroOrOne extends Cardinality("zero-or-one")
  case ExactlyOne extends Cardinality("exactly-one")
  case ZeroOrMore extends Cardinality("zero-or-more")
  case OneOrMore extends Cardinality("one-or-more")
  case MdParent extends Cardinality("md-parent")
}

/** Identification type for ER relationships. */
enum Identification(val label: String) extends java.lang.Enum[Identification] {
  case Identifying extends Identification("identifying")
  case NonIdentifying extends Identification("non-identifying")
}

/** An entity in the ER diagram.
  *
  * @param name
  *   entity name
  * @param attributes
  *   list of entity attributes
  */
final case class ErEntity(
  name:       String,
  attributes: mutable.ArrayBuffer[ErAttribute] = mutable.ArrayBuffer.empty
)

/** An entity attribute.
  *
  * @param attributeType
  *   the data type (e.g. "string", "int")
  * @param attributeName
  *   the attribute name
  * @param attributeKeyType
  *   key type (e.g. "PK", "FK", "UK") or empty
  * @param attributeComment
  *   comment text or empty
  */
final case class ErAttribute(
  attributeType:    String,
  attributeName:    String,
  attributeKeyType: String = "",
  attributeComment: String = ""
)

/** A relationship between two entities.
  *
  * @param entityA
  *   first entity name
  * @param roleA
  *   cardinality on entity A's side
  * @param entityB
  *   second entity name
  * @param roleB
  *   cardinality on entity B's side
  * @param relSpec
  *   identification type
  * @param label
  *   relationship label text
  */
final case class ErRelationship(
  entityA: String,
  roleA:   Cardinality,
  entityB: String,
  roleB:   Cardinality,
  relSpec: Identification,
  label:   String
)

/** Mutable database for ER diagram data.
  *
  * Accumulates entities, attributes, and relationships during parsing. The renderer reads from this database to produce SVG output.
  *
  * Ports the module-level mutable state and functions from `erDb.ts`.
  */
final class ErDb {

  val entities:      mutable.LinkedHashMap[String, ErEntity] = mutable.LinkedHashMap.empty
  val relationships: mutable.ArrayBuffer[ErRelationship]     = mutable.ArrayBuffer.empty

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  /** Adds an entity if not already present.
    *
    * Ports `addEntity()` from `erDb.ts`.
    */
  def addEntity(name: String): ErEntity =
    entities.getOrElseUpdate(name, ErEntity(name = name))

  /** Adds an attribute to an entity.
    *
    * Creates the entity if it does not exist yet.
    */
  def addAttribute(entityName: String, attr: ErAttribute): Unit = {
    val entity = addEntity(entityName)
    entity.attributes += attr
  }

  /** Adds a relationship between two entities.
    *
    * Both entities are created if they do not exist. Ports `addRelationship()` from `erDb.ts`.
    */
  def addRelationship(
    entityA: String,
    roleA:   Cardinality,
    entityB: String,
    roleB:   Cardinality,
    relSpec: Identification,
    label:   String
  ): Unit = {
    addEntity(entityA)
    addEntity(entityB)
    relationships += ErRelationship(
      entityA = entityA,
      roleA = roleA,
      entityB = entityB,
      roleB = roleB,
      relSpec = relSpec,
      label = label
    )
  }

  /** Clears all state for parsing a new diagram.
    *
    * Ports `clear()` from `erDb.ts`.
    */
  def clear(): Unit = {
    entities.clear()
    relationships.clear()
    title = ""
    accTitle = ""
    accDescription = ""
  }
}
