/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/requirement/requirementDb.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces module-level mutable state with mutable class
 *   Idiom: Mutable collections for accumulation during parsing
 *   Renames: requirementDb module functions -> RequirementDb methods
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package requirement

import scala.collection.mutable

/** A requirement node in the diagram.
  *
  * @param id
  *   unique identifier
  * @param name
  *   display name
  * @param reqType
  *   type: requirement, functionalRequirement, interfaceRequirement, performanceRequirement, physicalRequirement, designConstraint
  * @param risk
  *   risk level: Low, Medium, High
  * @param verifyMethod
  *   verification method: Analysis, Demonstration, Inspection, Test
  * @param text
  *   description text
  */
final case class RequirementNode(
  id:           String,
  name:         String,
  reqType:      String,
  risk:         String = "",
  verifyMethod: String = "",
  text:         String = ""
)

/** An element (system component) in the diagram.
  *
  * @param name
  *   element name
  * @param docRef
  *   documentation reference
  * @param elemType
  *   element type string
  */
final case class ElementNode(
  name:     String,
  docRef:   String = "",
  elemType: String = ""
)

/** A relationship between requirements and/or elements.
  *
  * @param src
  *   source name
  * @param dst
  *   destination name
  * @param relType
  *   relationship type: contains, copies, derives, satisfies, verifies, refines, traces
  */
final case class RequirementRelationship(src: String, dst: String, relType: String)

/** Mutable database for requirement diagram data. */
final class RequirementDb {

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  val requirements:  mutable.LinkedHashMap[String, RequirementNode] = mutable.LinkedHashMap.empty
  val elements:      mutable.LinkedHashMap[String, ElementNode]     = mutable.LinkedHashMap.empty
  val relationships: mutable.ArrayBuffer[RequirementRelationship]   = mutable.ArrayBuffer.empty

  /** Adds a requirement. */
  def addRequirement(name: String, reqType: String): Unit =
    requirements(name) = RequirementNode(id = name, name = name, reqType = reqType)

  /** Sets a property on an existing requirement. */
  def setRequirementProperty(name: String, prop: String, value: String): Unit =
    requirements.get(name).foreach { req =>
      prop.toLowerCase match {
        case "risk"         => requirements(name) = req.copy(risk = value)
        case "verifymethod" => requirements(name) = req.copy(verifyMethod = value)
        case "text"         => requirements(name) = req.copy(text = value)
        case "id"           => requirements(name) = req.copy(id = value)
        case _              => // unknown property
      }
    }

  /** Adds an element. */
  def addElement(name: String): Unit =
    elements(name) = ElementNode(name = name)

  /** Sets a property on an existing element. */
  def setElementProperty(name: String, prop: String, value: String): Unit =
    elements.get(name).foreach { elem =>
      prop.toLowerCase match {
        case "type"   => elements(name) = elem.copy(elemType = value)
        case "docref" => elements(name) = elem.copy(docRef = value)
        case _        => // unknown property
      }
    }

  /** Adds a relationship. */
  def addRelationship(src: String, dst: String, relType: String): Unit =
    relationships += RequirementRelationship(src, dst, relType)

  /** Clears all state. */
  def clear(): Unit = {
    title = ""
    accTitle = ""
    accDescription = ""
    requirements.clear()
    elements.clear()
    relationships.clear()
  }
}
