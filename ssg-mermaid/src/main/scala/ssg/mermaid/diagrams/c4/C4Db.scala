/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/c4/c4Db.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package c4

import scala.collection.mutable

/** A C4 entity (person, system, container, component, deployment node).
  *
  * Covers all C4 shape types: Person, PersonExt, System, SystemExt, SystemDb, SystemQueue, Container, ContainerDb, ContainerQueue, ContainerExt, ContainerDbExt, ContainerQueueExt, Component,
  * ComponentDb, ComponentQueue, ComponentExt, ComponentDbExt, ComponentQueueExt.
  *
  * @param alias
  *   unique identifier
  * @param label
  *   display label
  * @param entityType
  *   the C4 shape type string (e.g. "person", "system_ext", "container_db", etc.)
  * @param description
  *   description text
  * @param technology
  *   technology label (for containers and components)
  * @param link
  *   optional URL link
  * @param sprite
  *   optional sprite/icon identifier
  * @param tags
  *   optional tags string
  * @param parentBoundary
  *   the boundary this entity belongs to
  */
final case class C4Entity(
  alias:          String,
  label:          String,
  entityType:     String,
  description:    String = "",
  technology:     String = "",
  link:           String = "",
  sprite:         String = "",
  tags:           String = "",
  parentBoundary: String = "global"
)

/** A C4 relationship between entities.
  *
  * @param from
  *   source entity alias
  * @param to
  *   destination entity alias
  * @param label
  *   relationship label
  * @param technology
  *   technology used
  * @param description
  *   additional description
  * @param relType
  *   relationship type (Rel, BiRel, Rel_U, Rel_D, Rel_L, Rel_R, etc.)
  */
final case class C4Relationship(
  from:        String,
  to:          String,
  label:       String,
  technology:  String = "",
  description: String = "",
  relType:     String = "Rel"
)

/** A C4 boundary (system boundary, container boundary, enterprise boundary, or deployment node).
  *
  * @param alias
  *   unique identifier
  * @param label
  *   display label
  * @param boundaryType
  *   type of boundary ("system", "container", "enterprise", "deployment_node", etc.)
  * @param tags
  *   optional tags string
  * @param link
  *   optional URL link
  * @param parentBoundary
  *   the parent boundary alias
  */
final case class C4Boundary(
  alias:          String,
  label:          String,
  boundaryType:   String,
  tags:           String = "",
  link:           String = "",
  parentBoundary: String = "global"
)

/** Mutable database for C4 diagram data.
  *
  * Ports the module-level mutable state and functions from `c4Db.js`.
  */
final class C4Db {

  var title:           String  = ""
  var accTitle:        String  = ""
  var accDescription:  String  = ""
  var diagramType:     String  = "C4Context" // C4Context, C4Container, C4Component, C4Deployment, C4Dynamic
  var wrapEnabled:     Boolean = false
  var c4ShapeInRow:    Int     = 4
  var c4BoundaryInRow: Int     = 2

  val entities:      mutable.ArrayBuffer[C4Entity]       = mutable.ArrayBuffer.empty
  val relationships: mutable.ArrayBuffer[C4Relationship] = mutable.ArrayBuffer.empty
  val boundaries:    mutable.ArrayBuffer[C4Boundary]     = mutable.ArrayBuffer.empty

  /** Stack for tracking nested boundary parsing.
    *
    * Ports `boundaryParseStack` from `c4Db.js`.
    */
  private val boundaryParseStack: mutable.ArrayBuffer[String] = mutable.ArrayBuffer("") // initially global

  /** The current boundary being parsed.
    *
    * Ports `currentBoundaryParse` from `c4Db.js`.
    */
  var currentBoundaryParse: String = "global"

  /** The parent boundary of the current boundary.
    *
    * Ports `parentBoundaryParse` from `c4Db.js`.
    */
  var parentBoundaryParse: String = ""

  // --- Element addition methods ---

  /** Adds a Person or PersonExt element.
    *
    * Ports `addPersonOrSystem()` from `c4Db.js`.
    */
  def addPerson(alias: String, label: String, description: String = "", sprite: String = "", tags: String = "", link: String = "", typeC4Shape: String = "person"): Unit =
    entities += C4Entity(alias, label, typeC4Shape, description, sprite = sprite, tags = tags, link = link, parentBoundary = currentBoundaryParse)

  /** Adds a System, SystemExt, SystemDb, or SystemQueue element.
    *
    * Ports `addPersonOrSystem()` from `c4Db.js`.
    */
  def addSystem(alias: String, label: String, description: String = "", sprite: String = "", tags: String = "", link: String = "", typeC4Shape: String = "system"): Unit =
    entities += C4Entity(alias, label, typeC4Shape, description, sprite = sprite, tags = tags, link = link, parentBoundary = currentBoundaryParse)

  /** Adds a Container, ContainerDb, ContainerQueue, or Ext variants.
    *
    * Ports `addContainer()` from `c4Db.js`.
    */
  def addContainer(
    alias:       String,
    label:       String,
    technology:  String = "",
    description: String = "",
    sprite:      String = "",
    tags:        String = "",
    link:        String = "",
    typeC4Shape: String = "container"
  ): Unit =
    entities += C4Entity(alias, label, typeC4Shape, description, technology, sprite = sprite, tags = tags, link = link, parentBoundary = currentBoundaryParse)

  /** Adds a Component, ComponentDb, ComponentQueue, or Ext variants.
    *
    * Ports `addComponent()` from `c4Db.js`.
    */
  def addComponent(
    alias:       String,
    label:       String,
    technology:  String = "",
    description: String = "",
    sprite:      String = "",
    tags:        String = "",
    link:        String = "",
    typeC4Shape: String = "component"
  ): Unit =
    entities += C4Entity(alias, label, typeC4Shape, description, technology, sprite = sprite, tags = tags, link = link, parentBoundary = currentBoundaryParse)

  /** Adds a deployment node.
    *
    * Ports `addDeploymentNode()` from `c4Db.js`.
    */
  def addDeploymentNode(alias: String, label: String, nodeType: String = "", description: String = "", sprite: String = "", tags: String = "", link: String = ""): Unit =
    entities += C4Entity(
      alias,
      label,
      "deployment_node",
      description,
      technology = nodeType,
      sprite = sprite,
      tags = tags,
      link = link,
      parentBoundary = currentBoundaryParse
    )

  // --- Relationship methods ---

  /** Adds a relationship between entities.
    *
    * Ports `addRel()` from `c4Db.js`.
    */
  def addRelationship(from: String, to: String, label: String, technology: String = "", description: String = "", relType: String = "Rel"): Unit =
    relationships += C4Relationship(from, to, label, technology, description, relType)

  // --- Boundary methods ---

  /** Adds a boundary and pushes into it for nesting.
    *
    * Ports `addPersonOrSystemBoundary()` and `addContainerBoundary()` from `c4Db.js`.
    */
  def addBoundary(alias: String, label: String, boundaryType: String = "system", tags: String = "", link: String = ""): Unit = {
    boundaries += C4Boundary(alias, label, boundaryType, tags, link, parentBoundary = currentBoundaryParse)
    pushBoundaryIn(alias)
  }

  /** Pushes into a boundary (starts nesting).
    *
    * Ports the push logic inside `addPersonOrSystemBoundary()` from `c4Db.js`.
    */
  def pushBoundaryIn(alias: String): Unit = {
    parentBoundaryParse = currentBoundaryParse
    currentBoundaryParse = alias
    boundaryParseStack += alias
  }

  /** Pops out of the current boundary (ends nesting).
    *
    * Ports `popBoundaryParseStack()` from `c4Db.js`.
    */
  def popBoundaryOut(): Unit = {
    if (boundaryParseStack.nonEmpty) {
      boundaryParseStack.remove(boundaryParseStack.length - 1)
    }
    if (boundaryParseStack.nonEmpty) {
      currentBoundaryParse = boundaryParseStack.last
    } else {
      currentBoundaryParse = "global"
    }
    // Restore parent
    parentBoundaryParse = if (boundaryParseStack.length >= 2) {
      boundaryParseStack(boundaryParseStack.length - 2)
    } else {
      ""
    }
  }

  // --- Layout configuration ---

  /** Updates layout configuration.
    *
    * Ports `updateLayoutConfig()` from `c4Db.js`.
    */
  def updateLayoutConfig(shapeInRow: Int = 4, boundaryInRow: Int = 2): Unit = {
    c4ShapeInRow = shapeInRow
    c4BoundaryInRow = boundaryInRow
  }

  // --- Wrap ---

  /** Sets the wrap mode. */
  def setWrap(wrap: Boolean): Unit =
    wrapEnabled = wrap

  /** Returns whether wrapping is enabled. */
  def autoWrap: Boolean = wrapEnabled

  // --- Query methods ---

  /** Returns entities within a specific boundary. */
  def getEntities(parentBoundary: String = "global"): Seq[C4Entity] =
    entities.filter(_.parentBoundary == parentBoundary).toSeq

  /** Returns boundaries within a specific parent boundary. */
  def getBoundaries(parentBoundary: String = "global"): Seq[C4Boundary] =
    boundaries.filter(_.parentBoundary == parentBoundary).toSeq

  /** Returns all relationships. */
  def getRels: Seq[C4Relationship] = relationships.toSeq

  /** Clears all state for parsing a new diagram. */
  def clear(): Unit = {
    title = ""; accTitle = ""; accDescription = ""; diagramType = "C4Context"
    wrapEnabled = false; c4ShapeInRow = 4; c4BoundaryInRow = 2
    entities.clear(); relationships.clear(); boundaries.clear()
    boundaryParseStack.clear()
    boundaryParseStack += ""
    currentBoundaryParse = "global"
    parentBoundaryParse = ""
  }
}
