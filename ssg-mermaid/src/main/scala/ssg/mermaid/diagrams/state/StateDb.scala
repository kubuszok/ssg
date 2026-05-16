/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/state/stateDb.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces module-level mutable state with mutable class
 *   Idiom: Mutable collections for accumulation during parsing; clear() resets state
 *   Renames: stateDb module functions -> StateDb methods
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package state

import lowlevel.Nullable

import scala.collection.mutable

/** State types within the state diagram.
  *
  * Ports the state type constants from the original.
  */
enum StateType(val label: String) extends java.lang.Enum[StateType] {
  case Default extends StateType("default")
  case Start extends StateType("start")
  case End extends StateType("end")
  case Fork extends StateType("fork")
  case Join extends StateType("join")
  case Choice extends StateType("choice")
  case Note extends StateType("note")
  case Divider extends StateType("divider")
}

/** A state node in the state diagram.
  *
  * @param id
  *   unique state identifier
  * @param description
  *   display description (may differ from id)
  * @param stateType
  *   type of state (default, start, end, fork, join, choice, note, divider)
  * @param children
  *   nested states (for composite states)
  * @param note
  *   associated note text
  * @param notePosition
  *   note position relative to state ("left of", "right of")
  * @param cssClasses
  *   CSS classes applied to this state
  * @param styles
  *   inline CSS styles
  */
final case class StateNode(
  id:               String,
  var description:  String = "",
  descriptions:     mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty,
  var stateType:    StateType = StateType.Default,
  children:         mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty,
  var doc:          Nullable[mutable.ArrayBuffer[StateDoc]] = Nullable.empty,
  var note:         Nullable[String] = Nullable.empty,
  var notePosition: String = "right of",
  cssClasses:       mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty,
  styles:           mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty,
  textStyles:       mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty
)

/** A style class definition (`classDef`).
  *
  * Ports the `ClassDef` objects in the `classes` Map from `stateDb.js`.
  *
  * @param id
  *   the classDef identifier
  * @param styles
  *   parsed style attributes
  * @param textStyles
  *   parsed text style attributes
  */
final case class StyleClass(
  id:         String,
  styles:     mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty,
  textStyles: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty
)

/** A document node in the nested document model.
  *
  * Ports the `newDoc()` structure from `stateDb.js`, representing either a state statement, a relation statement, a classDef, a styleDef, or an applyClass.
  *
  * @param stmt
  *   statement type: "state", "relation", "classDef", "styleDef", "applyClass"
  * @param id
  *   node identifier
  * @param stateType
  *   state type string
  * @param doc
  *   nested document for composite states
  * @param description
  *   description text
  * @param state1
  *   first state in a relation
  * @param state2
  *   second state in a relation
  * @param relationTitle
  *   label for a relation
  * @param styleClass
  *   CSS class string for styleDef/applyClass
  * @param classes
  *   class attributes for classDef
  * @param stylesAttr
  *   style attributes
  * @param textStylesAttr
  *   text style attributes
  * @param noteInfo
  *   note info
  * @param start
  *   whether this is a start pseudo-state
  */
final case class StateDoc(
  var stmt:           String = "state",
  var id:             String = "",
  var stateType:      String = "default",
  var doc:            Nullable[mutable.ArrayBuffer[StateDoc]] = Nullable.empty,
  var description:    Nullable[String] = Nullable.empty,
  var state1:         Nullable[StateDoc] = Nullable.empty,
  var state2:         Nullable[StateDoc] = Nullable.empty,
  var relationTitle:  String = "",
  var styleClass:     String = "",
  var classes:        Nullable[String] = Nullable.empty,
  var stylesAttr:     Nullable[String] = Nullable.empty,
  var textStylesAttr: Nullable[String] = Nullable.empty,
  var noteInfo:       Nullable[String] = Nullable.empty,
  var start:          Boolean = false
)

/** A transition between two states.
  *
  * @param from
  *   source state ID
  * @param to
  *   destination state ID
  * @param label
  *   transition label text
  */
final case class StateTransition(
  from:  String,
  to:    String,
  label: String = ""
)

/** Mutable database for state diagram data.
  *
  * Accumulates states, transitions, and nested state information during parsing. The renderer reads from this database to produce SVG output.
  *
  * Ports the module-level mutable state and functions from `stateDb.ts`.
  */
final class StateDb {

  /** Start state pseudo-ID. */
  val StartId: String = "[*]"

  val states:      mutable.LinkedHashMap[String, StateNode] = mutable.LinkedHashMap.empty
  val transitions: mutable.ArrayBuffer[StateTransition]     = mutable.ArrayBuffer.empty

  /** Stack for tracking nested composite states during parsing. */
  private val stateStack: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty

  var direction:      String = "TB"
  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  private var stateCounter: Int = 0

  /** Adds a state to the diagram.
    *
    * If the state already exists, updates its description and type. Ports `addState()` from `stateDb.ts`.
    */
  def addState(
    id:          String,
    description: Nullable[String] = Nullable.empty,
    stateType:   StateType = StateType.Default
  ): Unit = {
    val resolvedId = if (id == "[*]") {
      // Start/end pseudo-state — generate unique ID based on context
      val uniqueId = if (stateType == StateType.End) s"end_$stateCounter" else s"start_$stateCounter"
      stateCounter += 1
      uniqueId
    } else {
      id
    }

    val node = states.getOrElseUpdate(resolvedId, StateNode(id = resolvedId))
    description.foreach { d =>
      node.description = d
    }

    // Override type if it's a special type
    if (stateType != StateType.Default || node.stateType == StateType.Default) {
      node.stateType = stateType
    }

    // If we're inside a composite state, add this as a child
    if (stateStack.nonEmpty) {
      val parentId = stateStack.last
      states.get(parentId).foreach { parent =>
        if (!parent.children.contains(resolvedId)) {
          parent.children += resolvedId
        }
      }
    }
  }

  /** Adds a state with a specific special type (fork, join, choice). */
  def addSpecialState(id: String, specialType: String): Unit = {
    val sType = specialType.toLowerCase.trim match {
      case "fork"    => StateType.Fork
      case "join"    => StateType.Join
      case "choice"  => StateType.Choice
      case "note"    => StateType.Note
      case "divider" => StateType.Divider
      case _         => StateType.Default
    }
    addState(id, stateType = sType)
  }

  /** Adds a transition between two states.
    *
    * Creates both states if they do not exist. Handles [*] for start/end states.
    *
    * Ports `addRelation()` from `stateDb.ts`.
    */
  def addTransition(from: String, to: String, label: String = ""): Unit = {
    val fromId = resolveSpecialState(from, isSource = true)
    val toId   = resolveSpecialState(to, isSource = false)

    // Ensure both states exist
    if (!states.contains(fromId)) {
      val sType = if (from == "[*]") StateType.Start else StateType.Default
      val node  = StateNode(id = fromId, stateType = sType)
      states(fromId) = node
    }
    if (!states.contains(toId)) {
      val sType = if (to == "[*]") StateType.End else StateType.Default
      val node  = StateNode(id = toId, stateType = sType)
      states(toId) = node
    }

    transitions += StateTransition(from = fromId, to = toId, label = label)
  }

  /** Resolves [*] to a unique start or end state ID. */
  private def resolveSpecialState(id: String, isSource: Boolean): String =
    if (id == "[*]") {
      if (isSource) {
        val uniqueId = s"start_$stateCounter"
        stateCounter += 1
        uniqueId
      } else {
        val uniqueId = s"end_$stateCounter"
        stateCounter += 1
        uniqueId
      }
    } else {
      id
    }

  /** Pushes a composite state onto the nesting stack.
    *
    * Called when entering a `state "Name" { ... }` block.
    */
  def pushState(id: String): Unit = {
    addState(id)
    stateStack += id
  }

  /** Pops the current composite state from the nesting stack.
    *
    * Called when the `}` closing a composite state block is encountered.
    */
  def popState(): Unit =
    if (stateStack.nonEmpty) {
      stateStack.remove(stateStack.length - 1)
    }

  /** Adds a note to a state. */
  def addNote(stateId: String, noteText: String, position: String = "right of"): Unit =
    states.get(stateId).foreach { node =>
      node.note = Nullable(noteText)
      node.notePosition = position
    }

  /** Sets CSS class on a state. */
  def setClass(ids: String, className: String): Unit =
    for (id <- ids.split(",")) {
      val trimId = id.trim
      states.get(trimId).foreach(_.cssClasses += className)
    }

  /** Sets inline styles on a state. */
  def setStyle(ids: String, style: Array[String]): Unit =
    for (id <- ids.split(",")) {
      val trimId = id.trim
      states.get(trimId).foreach { s =>
        style.foreach(st => s.styles += st)
      }
    }

  // --- Root Document model ---

  /** The root document containing parsed statements.
    *
    * Ports `rootDoc` from `stateDb.js`.
    */
  private var rootDoc: mutable.ArrayBuffer[StateDoc] = mutable.ArrayBuffer.empty

  /** Style classes defined by `classDef` statements.
    *
    * Ports the `classes` Map from `stateDb.js`.
    */
  val styleClasses: mutable.LinkedHashMap[String, StyleClass] = mutable.LinkedHashMap.empty

  /** Start/end pseudo-state counter.
    *
    * Ports `startEndCount` from `stateDb.js`.
    */
  private var startEndCount: Int = 0

  /** Divider counter.
    *
    * Ports `dividerCnt` from `stateDb.js`.
    */
  private var dividerCnt: Int = 0

  /** Sets the root document.
    *
    * Ports `setRootDoc()` from `stateDb.js`.
    */
  def setRootDoc(doc: mutable.ArrayBuffer[StateDoc]): Unit =
    rootDoc = doc

  /** Returns the root document.
    *
    * Ports `getRootDoc()` from `stateDb.js`.
    */
  def getRootDoc: mutable.ArrayBuffer[StateDoc] = rootDoc

  /** Recursive tree walker that processes nested state descriptions.
    *
    * Converts `[*]` pseudo-state IDs into unique start/end IDs. Handles concurrency dividers within composite states.
    *
    * Ports `docTranslator()` from `stateDb.js`.
    */
  def docTranslator(parent: StateDoc, node: StateDoc, first: Boolean): Unit =
    if (node.stmt == "relation") {
      node.state1.foreach(s1 => docTranslator(parent, s1, first = true))
      node.state2.foreach(s2 => docTranslator(parent, s2, first = false))
    } else {
      if (node.stmt == "state") {
        if (node.id == "[*]") {
          node.id = if (first) parent.id + "_start" else parent.id + "_end"
          node.start = first
        } else {
          node.id = node.id.trim
        }
      }

      node.doc.foreach { doc =>
        val newDocParts    = mutable.ArrayBuffer.empty[StateDoc]
        var currentDocPart = mutable.ArrayBuffer.empty[StateDoc]
        for (i <- 0 until doc.length)
          if (doc(i).stateType == "divider") {
            val newNode = doc(i).copy(doc = Nullable(mutable.ArrayBuffer.from(currentDocPart)))
            newDocParts += newNode
            currentDocPart = mutable.ArrayBuffer.empty[StateDoc]
          } else {
            currentDocPart += doc(i)
          }

        // If any divider was encountered
        if (newDocParts.nonEmpty && currentDocPart.nonEmpty) {
          dividerCnt += 1
          val newNode = StateDoc(
            stmt = "state",
            id = s"divider-id-$dividerCnt",
            stateType = "divider",
            doc = Nullable(mutable.ArrayBuffer.from(currentDocPart))
          )
          newDocParts += newNode
          node.doc = Nullable(newDocParts)
        }

        node.doc.foreach { d =>
          d.foreach(docNode => docTranslator(node, docNode, first = true))
        }
      }
    }

  /** Returns the root document after processing with `docTranslator()`.
    *
    * Ports `getRootDocV2()` from `stateDb.js`.
    */
  def getRootDocV2: StateDoc = {
    val root = StateDoc(id = "root", doc = Nullable(rootDoc))
    docTranslator(StateDoc(id = "root"), root, first = true)
    root
  }

  // --- Start/End ID helpers ---

  /** If the id is `[*]`, generate a unique start-state ID.
    *
    * Ports `startIdIfNeeded()` from `stateDb.js`.
    */
  def startIdIfNeeded(id: String): String =
    if (id == "[*]") {
      startEndCount += 1
      s"start$startEndCount"
    } else {
      id
    }

  /** If the id is `[*]`, return "start" type; else return given type.
    *
    * Ports `startTypeIfNeeded()` from `stateDb.js`.
    */
  def startTypeIfNeeded(id: String, stateType: StateType = StateType.Default): StateType =
    if (id == "[*]") StateType.Start else stateType

  /** If the id is `[*]`, generate a unique end-state ID.
    *
    * Ports `endIdIfNeeded()` from `stateDb.js`.
    */
  def endIdIfNeeded(id: String): String =
    if (id == "[*]") {
      startEndCount += 1
      s"end$startEndCount"
    } else {
      id
    }

  /** If the id is `[*]`, return "end" type; else return given type.
    *
    * Ports `endTypeIfNeeded()` from `stateDb.js`.
    */
  def endTypeIfNeeded(id: String, stateType: StateType = StateType.Default): StateType =
    if (id == "[*]") StateType.End else stateType

  /** Generates a unique divider ID.
    *
    * Ports `getDividerId()` from `stateDb.js`.
    */
  def getDividerId: String = {
    dividerCnt += 1
    s"divider-id-$dividerCnt"
  }

  // --- Description methods ---

  /** Adds a description line to a state.
    *
    * Ports `addDescription()` from `stateDb.js`.
    */
  def addDescription(id: String, descr: String): Unit =
    states.get(id).foreach { node =>
      val cleaned = if (descr.startsWith(":")) descr.substring(1).trim else descr
      node.descriptions += cleaned
      // Also update the single description field
      if (node.description.isEmpty) {
        node.description = cleaned
      }
    }

  // --- Style class methods ---

  /** Adds a style class definition.
    *
    * Ports `addStyleClass()` from `stateDb.js`.
    *
    * @param id
    *   the classDef identifier
    * @param styleAttributes
    *   comma-separated style attributes
    */
  def addStyleClass(id: String, styleAttributes: String = ""): Unit = {
    if (!styleClasses.contains(id)) {
      styleClasses(id) = StyleClass(id = id)
    }
    val foundClass = styleClasses(id)
    if (styleAttributes.nonEmpty) {
      styleAttributes.split(",").foreach { attrib =>
        // Remove any trailing semicolons
        val fixedAttrib = attrib.replaceAll("""([^;]*);""", "$1").trim
        // Replace color-related keywords
        if (attrib.contains("color")) {
          val newStyle1 = fixedAttrib.replace("fill", "bgFill")
          val newStyle2 = newStyle1.replace("color", "fill")
          foundClass.textStyles += newStyle2
        }
        foundClass.styles += fixedAttrib
      }
    }
  }

  /** Sets a CSS class on state(s).
    *
    * Ports `setCssClass()` from `stateDb.js`. Creates the state if it does not exist.
    */
  def setCssClass(itemIds: String, cssClassName: String): Unit =
    for (id <- itemIds.split(",")) {
      val trimId = id.trim
      if (!states.contains(trimId)) {
        addState(trimId)
      }
      states.get(trimId).foreach(_.cssClasses += cssClassName)
    }

  /** Sets a text style on a state.
    *
    * Ports `setTextStyle()` from `stateDb.js`.
    */
  def setTextStyle(itemId: String, cssClassName: String): Unit =
    states.get(itemId).foreach(_.textStyles += cssClassName)

  /** Returns all style classes.
    *
    * Ports `getClasses()` from `stateDb.js`.
    */
  def getClasses: mutable.LinkedHashMap[String, StyleClass] = styleClasses

  /** Cleans up a label string (removes leading colon).
    *
    * Ports `cleanupLabel()` from `stateDb.js`.
    */
  def cleanupLabel(label: String): String =
    if (label.startsWith(":")) label.substring(2).trim
    else label.trim

  /** Trims an optional leading colon.
    *
    * Ports `trimColon()` from `stateDb.js`.
    */
  def trimColon(str: String): String =
    if (str.nonEmpty && str.charAt(0) == ':') str.substring(1).trim
    else str.trim

  // --- Relation with objects ---

  /** Adds a relation using state document objects.
    *
    * Ports `addRelationObjs()` from `stateDb.js`.
    */
  def addRelationObjs(item1: StateDoc, item2: StateDoc, relationTitle: String): Unit = {
    val id1   = startIdIfNeeded(item1.id.trim)
    val type1 = startTypeIfNeeded(item1.id.trim)
    val id2   = startIdIfNeeded(item2.id.trim)
    val type2 = startTypeIfNeeded(item2.id.trim)

    addState(id1, stateType = type1)
    addState(id2, stateType = type2)

    transitions += StateTransition(from = id1, to = id2, label = relationTitle)
  }

  /** Clears all state for parsing a new diagram.
    *
    * Ports `clear()` from `stateDb.ts`.
    */
  def clear(): Unit = {
    states.clear()
    transitions.clear()
    stateStack.clear()
    styleClasses.clear()
    rootDoc.clear()
    stateCounter = 0
    startEndCount = 0
    dividerCnt = 0
    direction = "TB"
    title = ""
    accTitle = ""
    accDescription = ""
  }
}
