/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/sequence/sequenceDb.ts
 *              mermaid/packages/mermaid/src/diagrams/sequence/types.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces module-level mutable state with mutable class
 *   Idiom: Mutable collections for accumulation during parsing; clear() resets state
 *   Renames: sequenceDb module functions → SequenceDb methods
 *            types.ts interfaces → case classes in this file
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package sequence

import lowlevel.Nullable

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

// --- Data model types from types.ts ---

/** A box grouping actors in the sequence diagram.
  *
  * @param name
  *   display title of the box
  * @param wrap
  *   whether to wrap the box title text
  * @param fill
  *   background fill color
  * @param actorKeys
  *   IDs of actors contained in this box
  */
final case class SeqBox(
  var name:  String,
  var wrap:  Boolean = false,
  var fill:  String = "transparent",
  actorKeys: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty,
  // rendering data set later
  var x:             Double = 0,
  var y:             Double = 0,
  var width:         Double = 0,
  var height:        Double = 0,
  var margin:        Double = 0,
  var textMaxHeight: Double = 0
)

/** An actor (participant) in the sequence diagram.
  *
  * @param name
  *   display name
  * @param description
  *   description text shown in the actor box
  * @param wrap
  *   whether to wrap description text
  * @param actorType
  *   "participant" or "actor" (stick figure)
  */
final case class SeqActor(
  var name:        String,
  var description: String,
  var wrap:        Boolean = false,
  var actorType:   String = "participant",
  var prevActor:   Nullable[String] = Nullable.empty,
  var nextActor:   Nullable[String] = Nullable.empty,
  var box:         Nullable[SeqBox] = Nullable.empty,
  var links:       mutable.LinkedHashMap[String, String] = mutable.LinkedHashMap.empty,
  var properties:  mutable.LinkedHashMap[String, String] = mutable.LinkedHashMap.empty,
  // rendering data set later by the renderer
  var x:      Double = 0,
  var y:      Double = 0,
  var width:  Double = 0,
  var height: Double = 0,
  var margin: Double = 0,
  var starty: Double = 0,
  var stopy:  Double = 0
)

/** A message (signal) between actors.
  *
  * @param from
  *   source actor ID
  * @param to
  *   destination actor ID
  * @param message
  *   message text
  * @param wrap
  *   whether to wrap message text
  * @param answer
  *   for request/reply pairs
  * @param msgType
  *   one of [[LineType]] values
  * @param activate
  *   whether to activate the target actor
  * @param placement
  *   note placement (for NOTE type)
  */
final case class SeqMessage(
  var from:      Nullable[String] = Nullable.empty,
  var to:        Nullable[String] = Nullable.empty,
  var message:   String = "",
  var wrap:      Boolean = false,
  var answer:    Boolean = false,
  var msgType:   Int = LineType.Solid,
  var activate:  Boolean = false,
  var placement: Int = -1
)

/** A note attached to an actor or between actors.
  *
  * @param actor
  *   actor ID(s) this note is attached to
  * @param placement
  *   one of [[Placement]] values
  * @param message
  *   note text content
  * @param wrap
  *   whether to wrap note text
  */
final case class SeqNote(
  var actor:     Array[String] = Array.empty,
  var placement: Int = Placement.Over,
  var message:   String = "",
  var wrap:      Boolean = false
)

/** Message/signal type constants.
  *
  * Ports the LINETYPE object from sequenceDb.ts.
  */
object LineType {
  val Solid:               Int = 0
  val Dotted:              Int = 1
  val Note:                Int = 2
  val SolidCross:          Int = 3
  val DottedCross:         Int = 4
  val SolidOpen:           Int = 5
  val DottedOpen:          Int = 6
  val LoopStart:           Int = 10
  val LoopEnd:             Int = 11
  val AltStart:            Int = 12
  val AltElse:             Int = 13
  val AltEnd:              Int = 14
  val OptStart:            Int = 15
  val OptEnd:              Int = 16
  val ActiveStart:         Int = 17
  val ActiveEnd:           Int = 18
  val ParStart:            Int = 19
  val ParAnd:              Int = 20
  val ParEnd:              Int = 21
  val RectStart:           Int = 22
  val RectEnd:             Int = 23
  val SolidPoint:          Int = 24
  val DottedPoint:         Int = 25
  val Autonumber:          Int = 26
  val CriticalStart:       Int = 27
  val CriticalOption:      Int = 28
  val CriticalEnd:         Int = 29
  val BreakStart:          Int = 30
  val BreakEnd:            Int = 31
  val ParOverStart:        Int = 32
  val BidirectionalSolid:  Int = 33
  val BidirectionalDotted: Int = 34

  /** Returns true if the given type is a line type (an actual message arrow). */
  def isLineType(t: Int): Boolean =
    t == Solid || t == Dotted || t == SolidCross || t == DottedCross ||
      t == SolidOpen || t == DottedOpen || t == SolidPoint || t == DottedPoint ||
      t == BidirectionalSolid || t == BidirectionalDotted

  /** Returns true if the given type has a dotted line. */
  def isDotted(t: Int): Boolean =
    t == Dotted || t == DottedCross || t == DottedPoint ||
      t == DottedOpen || t == BidirectionalDotted
}

/** Arrowhead type constants.
  *
  * Ports the ARROWTYPE object from sequenceDb.ts.
  */
object ArrowType {
  val Filled: Int = 0
  val Open:   Int = 1
}

/** Note placement constants.
  *
  * Ports the PLACEMENT object from sequenceDb.ts.
  */
object Placement {
  val LeftOf:  Int = 0
  val RightOf: Int = 1
  val Over:    Int = 2
}

/** Mutable database for sequence diagram data.
  *
  * Accumulates actors, messages, notes, and boxes during parsing. The renderer reads from this database to produce SVG output.
  *
  * Ports the module-level mutable state and functions from `sequenceDb.ts`.
  */
final class SequenceDb {

  private var prevActor:      Nullable[String]                        = Nullable.empty
  val actors:                 mutable.LinkedHashMap[String, SeqActor] = mutable.LinkedHashMap.empty
  val createdActors:          mutable.LinkedHashMap[String, Int]      = mutable.LinkedHashMap.empty
  val destroyedActors:        mutable.LinkedHashMap[String, Int]      = mutable.LinkedHashMap.empty
  val boxes:                  mutable.ArrayBuffer[SeqBox]             = mutable.ArrayBuffer.empty
  val messages:               mutable.ArrayBuffer[SeqMessage]         = mutable.ArrayBuffer.empty
  val notes:                  mutable.ArrayBuffer[SeqNote]            = mutable.ArrayBuffer.empty
  var sequenceNumbersEnabled: Boolean                                 = false
  var wrapEnabled:            Nullable[Boolean]                       = Nullable.empty
  private var currentBox:     Nullable[SeqBox]                        = Nullable.empty
  private var lastCreated:    Nullable[String]                        = Nullable.empty
  private var lastDestroyed:  Nullable[String]                        = Nullable.empty

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  // --- Box methods ---

  /** Adds a new box grouping. */
  def addBox(text: String, color: String, wrap: Boolean): Unit = {
    val box = SeqBox(
      name = text,
      wrap = if (wrap) true else autoWrap,
      fill = color
    )
    boxes += box
    currentBox = Nullable(box)
  }

  /** Ends the current box grouping. */
  def boxEnd(): Unit =
    currentBox = Nullable.empty

  /** Returns true if there is at least one box. */
  def hasAtLeastOneBox: Boolean = boxes.nonEmpty

  /** Returns true if at least one box has a non-empty name. */
  def hasAtLeastOneBoxWithTitle: Boolean = boxes.exists(_.name.nonEmpty)

  // --- Actor methods ---

  /** Adds an actor to the diagram.
    *
    * If the actor already exists, updates its box assignment. Throws if trying to assign the same actor to two different boxes.
    *
    * @param id
    *   unique actor identifier
    * @param name
    *   display name
    * @param description
    *   description text (defaults to name if null/empty)
    * @param actorType
    *   "participant" or "actor"
    */
  def addActor(
    id:          String,
    name:        String,
    description: Nullable[String] = Nullable.empty,
    actorType:   String = "participant"
  ): Unit = boundary {
    val assignedBox = currentBox
    val old         = actors.get(id)

    old match {
      case Some(existingActor) =>
        // If already set and trying to set to a new one throw error
        currentBox.foreach { cb =>
          existingActor.box.foreach { ob =>
            if (cb ne ob) {
              throw new IllegalStateException(
                s"A same participant should only be defined in one Box: ${existingActor.name} " +
                  s"can't be in '${ob.name}' and in '${cb.name}' at the same time."
              )
            }
          }
        }
        // Don't change the box if already set
        val resolvedBox = if (existingActor.box.isDefined) existingActor.box else assignedBox
        existingActor.box = resolvedBox

        // Don't allow description nulling
        if (name == existingActor.name && description.isEmpty) {
          break()
        }

      case None => ()
    }

    // Resolve description: don't allow null descriptions
    val resolvedDescription = description.filter(_.nonEmpty).getOrElse(name)

    actors(id) = SeqActor(
      name = name,
      description = resolvedDescription,
      wrap = autoWrap,
      actorType = if (actorType.nonEmpty) actorType else "participant",
      prevActor = prevActor,
      box = if (old.exists(_.box.isDefined)) old.get.box else assignedBox
    )

    prevActor.foreach { pa =>
      actors.get(pa).foreach { prevActorRec =>
        prevActorRec.nextActor = Nullable(id)
      }
    }

    currentBox.foreach { cb =>
      cb.actorKeys += id
    }

    prevActor = Nullable(id)
  }

  /** Returns the actor with the given ID. */
  def getActor(id: String): SeqActor =
    actors.getOrElse(id, throw new NoSuchElementException(s"Actor not found: $id"))

  /** Returns all actor keys in insertion order. */
  def getActorKeys: Array[String] = actors.keys.toArray

  // --- Activation count ---

  /** Counts the net number of active activations for the given actor. */
  private def activationCount(part: String): Int =
    if (part.isEmpty) 0
    else {
      var count = 0
      for (msg <- messages) {
        if (msg.msgType == LineType.ActiveStart && msg.from.contains(part)) count += 1
        if (msg.msgType == LineType.ActiveEnd && msg.from.contains(part)) count -= 1
      }
      count
    }

  // --- Message methods ---

  /** Adds a simple message (addMessage from the original). */
  def addMessage(
    idFrom:  String,
    idTo:    String,
    message: String,
    wrap:    Boolean,
    answer:  Boolean = false
  ): Unit =
    messages += SeqMessage(
      from = Nullable(idFrom),
      to = Nullable(idTo),
      message = message,
      wrap = if (wrap) true else autoWrap,
      answer = answer
    )

  /** Adds a signal (message with type and activation info).
    *
    * Ports `addSignal()` from sequenceDb.ts.
    */
  def addSignal(
    idFrom:      Nullable[String] = Nullable.empty,
    idTo:        Nullable[String] = Nullable.empty,
    message:     String = "",
    messageType: Int = LineType.Solid,
    activate:    Boolean = false,
    wrap:        Boolean = false
  ): Boolean = {
    if (messageType == LineType.ActiveEnd) {
      val cnt = activationCount(idFrom.getOrElse(""))
      if (cnt < 1) {
        // Bail out as there is an activation signal from an inactive participant
        throw new IllegalStateException(
          s"Trying to inactivate an inactive participant (${idFrom.getOrElse("")})"
        )
      }
    }
    messages += SeqMessage(
      from = idFrom,
      to = idTo,
      message = message,
      wrap = if (wrap) true else autoWrap,
      msgType = messageType,
      activate = activate
    )
    true
  }

  // --- Note methods ---

  /** Adds a note attached to one or more actors.
    *
    * Ports `addNote()` from sequenceDb.ts.
    */
  def addNote(
    actorIds:  Array[String],
    placement: Int,
    message:   String,
    wrap:      Boolean = false
  ): Unit = {
    val note = SeqNote(
      actor = actorIds,
      placement = placement,
      message = message,
      wrap = if (wrap) true else autoWrap
    )
    notes += note

    // Also add as a message for the renderer's vertical positioning
    val actors = if (actorIds.length >= 2) actorIds else actorIds ++ actorIds
    messages += SeqMessage(
      from = Nullable(actors(0)),
      to = Nullable(actors.lift(1).getOrElse(actors(0))),
      message = message,
      wrap = if (wrap) true else autoWrap,
      msgType = LineType.Note,
      placement = placement
    )
  }

  // --- Sequence number methods ---

  /** Enables sequence number display. */
  def enableSequenceNumbers(): Unit =
    sequenceNumbersEnabled = true

  /** Disables sequence number display. */
  def disableSequenceNumbers(): Unit =
    sequenceNumbersEnabled = false

  /** Returns true if sequence numbers are enabled. */
  def showSequenceNumbers: Boolean = sequenceNumbersEnabled

  // --- Wrap methods ---

  /** Sets the global wrap mode. */
  def setWrap(wrapSetting: Nullable[Boolean]): Unit =
    wrapEnabled = wrapSetting

  /** Returns the current wrap mode.
    *
    * If setWrap has been called, returns that value; otherwise returns false.
    */
  def autoWrap: Boolean =
    wrapEnabled.getOrElse(false)

  // --- Parse helpers ---

  /** Parses a message string, extracting wrap directives.
    *
    * Ports `parseMessage()` from sequenceDb.ts.
    *
    * @return
    *   (cleanedText, wrapOverride)
    */
  def parseMessage(str: String): (String, Boolean) = {
    val trimmed             = str.trim
    val (cleanedText, wrap) = extractWrap(trimmed)
    (cleanedText, wrap.getOrElse(autoWrap))
  }

  /** Parses a box statement, extracting color and description.
    *
    * Ports `parseBoxData()` from sequenceDb.ts.
    *
    * @return
    *   (text, color, wrap)
    */
  def parseBoxData(str: String): (Nullable[String], String, Boolean) = {
    val pattern     = """^((?:rgba?|hsla?)\s*\(.*\)|\w*)(.*)$""".r
    val matchResult = pattern.findFirstMatchIn(str)
    var color       = matchResult.flatMap(m => Option(m.group(1)).filter(_.trim.nonEmpty)).map(_.trim).getOrElse("transparent")
    var titleText   = matchResult.flatMap(m => Option(m.group(2)).filter(_.trim.nonEmpty)).map(_.trim)

    // Simple CSS color name validation: check a few known color keywords
    if (!isValidCssColor(color)) {
      color = "transparent"
      titleText = Some(str.trim)
    }

    val (cleanedText, wrap) = titleText match {
      case Some(t) => extractWrap(t)
      case None    => ("", None)
    }

    val sanitized = if (cleanedText.nonEmpty) Nullable(sanitizeText(cleanedText)) else Nullable.empty
    (sanitized, color, wrap.getOrElse(autoWrap))
  }

  /** Extracts :wrap: or :nowrap: prefix from text.
    *
    * @return
    *   (cleanedText, wrapOverride) where wrapOverride is None if no directive found
    */
  private def extractWrap(text: String): (String, Option[Boolean]) =
    if (text.isEmpty) {
      ("", None)
    } else {
      val trimmed       = text.trim
      val wrapPattern   = """^:?wrap:""".r
      val nowrapPattern = """^:?nowrap:""".r

      val wrap =
        if (wrapPattern.findFirstIn(trimmed).isDefined) Some(true)
        else if (nowrapPattern.findFirstIn(trimmed).isDefined) Some(false)
        else None

      val cleanedText = wrap match {
        case Some(_) => trimmed.replaceFirst("""^:?(?:no)?wrap:""", "").trim
        case None    => trimmed
      }
      (cleanedText, wrap)
    }

  /** Simple check for known CSS color names. */
  private def isValidCssColor(color: String): Boolean =
    if (color.isEmpty) false
    else if (color == "transparent") true
    else if (color.startsWith("rgb") || color.startsWith("hsl")) true
    else {
      // Allow known CSS color keywords
      val knownColors = Set(
        "red",
        "blue",
        "green",
        "yellow",
        "orange",
        "purple",
        "pink",
        "cyan",
        "magenta",
        "white",
        "black",
        "gray",
        "grey",
        "brown",
        "coral",
        "lightblue",
        "lightgreen",
        "lightyellow",
        "lightgray",
        "darkblue",
        "darkgreen",
        "darkred",
        "aqua",
        "teal",
        "navy",
        "olive",
        "lime",
        "maroon",
        "fuchsia",
        "silver",
        "skyblue",
        "tomato",
        "gold",
        "wheat",
        "salmon",
        "plum",
        "orchid",
        "indigo",
        "ivory",
        "crimson",
        "beige",
        "aliceblue",
        "antiquewhite",
        "blanchedalmond",
        "cornflowerblue",
        "cornsilk",
        "darkgoldenrod",
        "darkolivegreen",
        "darkorange",
        "deeppink",
        "deepskyblue",
        "dodgerblue",
        "firebrick",
        "gainsboro",
        "ghostwhite",
        "honeydew",
        "hotpink",
        "khaki",
        "lavender",
        "lawngreen",
        "lemonchiffon",
        "lightcoral",
        "lightcyan",
        "lightpink",
        "lightsalmon",
        "lightseagreen",
        "lightskyblue",
        "lightsteelblue",
        "limegreen",
        "linen",
        "mediumaquamarine",
        "mediumblue",
        "mediumorchid",
        "mediumpurple",
        "mediumseagreen",
        "mediumslateblue",
        "mediumspringgreen",
        "mediumturquoise",
        "mediumvioletred",
        "midnightblue",
        "mintcream",
        "mistyrose",
        "moccasin",
        "navajowhite",
        "oldlace",
        "orangered",
        "palegoldenrod",
        "palegreen",
        "paleturquoise",
        "palevioletred",
        "papayawhip",
        "peachpuff",
        "peru",
        "powderblue",
        "rosybrown",
        "royalblue",
        "saddlebrown",
        "sandybrown",
        "seagreen",
        "seashell",
        "sienna",
        "slateblue",
        "slategray",
        "slategrey",
        "snow",
        "springgreen",
        "steelblue",
        "tan",
        "thistle",
        "turquoise",
        "violet",
        "yellowgreen"
      )
      knownColors.contains(color.toLowerCase)
    }

  /** Simple text sanitizer. */
  private def sanitizeText(txt: String): String =
    txt.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")

  // --- Apply method (dispatches parsed actions) ---

  /** Applies a parsed action to the database.
    *
    * This is the main dispatch method called by the parser after recognizing a production. Ports `apply()` from sequenceDb.ts.
    */
  def apply(action: SeqAction): Unit = {
    action match {
      case SeqAction.AddParticipant(actor, description, draw) =>
        addActor(actor, actor, description, draw)

      case SeqAction.CreateParticipant(actor, description, draw) =>
        if (actors.contains(actor)) {
          throw new IllegalStateException(
            "It is not possible to have actors with the same id, even if one is destroyed " +
              "before the next is created. Use 'AS' aliases to simulate the behavior"
          )
        }
        lastCreated = Nullable(actor)
        addActor(actor, actor, description, draw)
        createdActors(actor) = messages.length

      case SeqAction.DestroyParticipant(actor) =>
        lastDestroyed = Nullable(actor)
        destroyedActors(actor) = messages.length

      case SeqAction.ActiveStart(actor, signalType) =>
        addSignal(Nullable(actor), messageType = signalType)

      case SeqAction.ActiveEnd(actor, signalType) =>
        addSignal(Nullable(actor), messageType = signalType)

      case SeqAction.AddNote(actorIds, placement, text, wrap) =>
        addNote(actorIds, placement, text, wrap)

      case SeqAction.AddMessage(from, to, msg, signalType, activate) =>
        // Validate create/destroy ordering
        lastCreated.foreach { lc =>
          if (to != lc) {
            throw new IllegalStateException(
              s"The created participant $lc does not have an associated creating message " +
                "after its declaration. Please check the sequence diagram."
            )
          } else {
            lastCreated = Nullable.empty
          }
        }
        if (lastCreated.isEmpty) {
          lastDestroyed.foreach { ld =>
            if (to != ld && from != ld) {
              throw new IllegalStateException(
                s"The destroyed participant $ld does not have an associated destroying message " +
                  "after its declaration. Please check the sequence diagram."
              )
            } else {
              lastDestroyed = Nullable.empty
            }
          }
        }
        addSignal(Nullable(from), Nullable(to), msg, signalType, activate)

      case SeqAction.BoxStart(text, color, wrap) =>
        addBox(text, color, wrap)

      case SeqAction.BoxEnd =>
        boxEnd()

      case SeqAction.LoopStart(text, signalType, wrap) =>
        addSignal(message = text, messageType = signalType, wrap = wrap)

      case SeqAction.LoopEnd(signalType) =>
        addSignal(messageType = signalType)

      case SeqAction.RectStart(color, signalType) =>
        addSignal(message = color, messageType = signalType)

      case SeqAction.RectEnd(signalType) =>
        addSignal(messageType = signalType)

      case SeqAction.OptStart(text, signalType, wrap) =>
        addSignal(message = text, messageType = signalType, wrap = wrap)

      case SeqAction.OptEnd(signalType) =>
        addSignal(messageType = signalType)

      case SeqAction.AltStart(text, signalType, wrap) =>
        addSignal(message = text, messageType = signalType, wrap = wrap)

      case SeqAction.AltElse(text, signalType, wrap) =>
        addSignal(message = text, messageType = signalType, wrap = wrap)

      case SeqAction.AltEnd(signalType) =>
        addSignal(messageType = signalType)

      case SeqAction.ParStart(text, signalType, wrap) =>
        addSignal(message = text, messageType = signalType, wrap = wrap)

      case SeqAction.ParAnd(text, signalType, wrap) =>
        addSignal(message = text, messageType = signalType, wrap = wrap)

      case SeqAction.ParEnd(signalType) =>
        addSignal(messageType = signalType)

      case SeqAction.CriticalStart(text, signalType, wrap) =>
        addSignal(message = text, messageType = signalType, wrap = wrap)

      case SeqAction.CriticalOption(text, signalType, wrap) =>
        addSignal(message = text, messageType = signalType, wrap = wrap)

      case SeqAction.CriticalEnd(signalType) =>
        addSignal(messageType = signalType)

      case SeqAction.BreakStart(text, signalType, wrap) =>
        addSignal(message = text, messageType = signalType, wrap = wrap)

      case SeqAction.BreakEnd(signalType) =>
        addSignal(messageType = signalType)

      case SeqAction.SetAccTitle(text) =>
        accTitle = text

      case SeqAction.SetAccDescription(text) =>
        accDescription = text

      case SeqAction.SetTitle(text) =>
        title = text

      case SeqAction.AddLinks(actor, linksMap) =>
        actors.get(actor).foreach { actorRec =>
          actorRec.links ++= linksMap
        }

      case SeqAction.AddProperties(actor, propsMap) =>
        actors.get(actor).foreach { actorRec =>
          actorRec.properties ++= propsMap
        }

      case SeqAction.SequenceIndex(start, step, visible, signalType) =>
        messages += SeqMessage(
          message = s"autonumber:$start:$step:$visible",
          wrap = false,
          msgType = signalType
        )
    }
  }

  /** Applies a sequence of parsed actions. */
  def applyAll(actions: collection.Seq[SeqAction]): Unit =
    actions.foreach(apply)

  // --- Clear ---

  /** Clears all state for parsing a new diagram.
    *
    * Ports `clear()` from sequenceDb.ts.
    */
  def clear(): Unit = {
    prevActor = Nullable.empty
    actors.clear()
    createdActors.clear()
    destroyedActors.clear()
    boxes.clear()
    messages.clear()
    notes.clear()
    sequenceNumbersEnabled = false
    wrapEnabled = Nullable.empty
    currentBox = Nullable.empty
    lastCreated = Nullable.empty
    lastDestroyed = Nullable.empty
    title = ""
    accTitle = ""
    accDescription = ""
  }
}

/** Parsed action types for the sequence diagram.
  *
  * These replace the dynamic `param.type` dispatch used in the original's `apply()` function.
  */
enum SeqAction {
  case AddParticipant(actor: String, description: Nullable[String], draw: String)
  case CreateParticipant(actor: String, description: Nullable[String], draw: String)
  case DestroyParticipant(actor: String)
  case ActiveStart(actor: String, signalType: Int)
  case ActiveEnd(actor: String, signalType: Int)
  case AddNote(actorIds: Array[String], placement: Int, text: String, wrap: Boolean)
  case AddMessage(from: String, to: String, msg: String, signalType: Int, activate: Boolean)
  case BoxStart(text: String, color: String, wrap: Boolean)
  case BoxEnd
  case LoopStart(text: String, signalType: Int, wrap: Boolean)
  case LoopEnd(signalType: Int)
  case RectStart(color: String, signalType: Int)
  case RectEnd(signalType: Int)
  case OptStart(text: String, signalType: Int, wrap: Boolean)
  case OptEnd(signalType: Int)
  case AltStart(text: String, signalType: Int, wrap: Boolean)
  case AltElse(text: String, signalType: Int, wrap: Boolean)
  case AltEnd(signalType: Int)
  case ParStart(text: String, signalType: Int, wrap: Boolean)
  case ParAnd(text: String, signalType: Int, wrap: Boolean)
  case ParEnd(signalType: Int)
  case CriticalStart(text: String, signalType: Int, wrap: Boolean)
  case CriticalOption(text: String, signalType: Int, wrap: Boolean)
  case CriticalEnd(signalType: Int)
  case BreakStart(text: String, signalType: Int, wrap: Boolean)
  case BreakEnd(signalType: Int)
  case SetAccTitle(text: String)
  case SetAccDescription(text: String)
  case SetTitle(text: String)
  case AddLinks(actor: String, links: mutable.LinkedHashMap[String, String])
  case AddProperties(actor: String, properties: mutable.LinkedHashMap[String, String])
  case SequenceIndex(start: Int, step: Int, visible: Boolean, signalType: Int)
}
