/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/class/classDb.ts
 *              mermaid/packages/mermaid/src/diagrams/class/classTypes.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces module-level mutable state with mutable class
 *   Idiom: Mutable collections for accumulation during parsing; clear() resets state
 *   Renames: classDb module functions → ClassDb methods
 *            classTypes.ts interfaces → case classes in this file
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package class_

import lowlevel.Nullable

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

// --- Data model types from classTypes.ts ---

/** A parsed class member (attribute or method).
  *
  * Ports `ClassMember` from classTypes.ts.
  *
  * @param id
  *   the member name/identifier
  * @param memberType
  *   "method" or "attribute"
  * @param visibility
  *   visibility modifier: "+", "-", "#", "~", or ""
  * @param classifier
  *   classifier modifier: "$" (static) or "*" (abstract) or ""
  * @param parameters
  *   method parameters string (for methods only)
  * @param returnType
  *   method return type string (for methods only)
  */
final case class ClassMember(
  var id:         String = "",
  var memberType: String = "attribute",
  var visibility: String = "",
  var classifier: String = "",
  var parameters: String = "",
  var returnType: String = ""
) {

  /** Returns the display text and CSS style for this member.
    *
    * Ports `getDisplayDetails()` from classTypes.ts.
    */
  def displayDetails: (String, String) = {
    var displayText = visibility + parseGenericTypes(id)
    if (memberType == "method") {
      displayText += s"(${parseGenericTypes(parameters.trim)})"
      if (returnType.nonEmpty) {
        displayText += " : " + parseGenericTypes(returnType)
      }
    }
    displayText = displayText.trim
    val cssStyle = classifier match {
      case "*" => "font-style:italic;"
      case "$" => "text-decoration:underline;"
      case _   => ""
    }
    (displayText, cssStyle)
  }

  /** Parses generic type markers (~ delimiters) to < > angle brackets.
    *
    * Ports `parseGenericTypes()` from common.ts. Splits on commas (keeping them as separate elements), recombines sets that have unmatched tildes across commas, then processes each set by pairing
    * tildes from outside in: the first ~ becomes `<` and the last ~ becomes `>`.
    */
  private def parseGenericTypes(text: String): String = {
    import scala.collection.mutable.ArrayBuffer
    // Split on commas, keeping the comma as a separate element (like JS /(,)/ split)
    val inputSets = ArrayBuffer.empty[String]
    var lastIdx   = 0
    var idx       = text.indexOf(',')
    while (idx >= 0) {
      inputSets += text.substring(lastIdx, idx)
      inputSets += ","
      lastIdx = idx + 1
      idx = text.indexOf(',', lastIdx)
    }
    inputSets += text.substring(lastIdx)

    val output = ArrayBuffer.empty[String]
    var i      = 0
    while (i < inputSets.length) {
      var thisSet = inputSets(i)
      if (thisSet == "," && i > 0 && i + 1 < inputSets.length) {
        val prevCount = countTildes(inputSets(i - 1))
        val nextCount = countTildes(inputSets(i + 1))
        if (prevCount == 1 && nextCount == 1) {
          thisSet = inputSets(i - 1) + "," + inputSets(i + 1)
          i += 1 // skip next
          output.remove(output.length - 1) // remove previously appended prev
        }
      }
      output += processSet(thisSet)
      i += 1
    }
    output.mkString
  }

  private def countTildes(s: String): Int =
    s.count(_ == '~')

  private def processSet(input: String): String = {
    val tildeCount = countTildes(input)
    if (tildeCount <= 1) {
      input
    } else {
      var hasStartingTilde = false
      var working          = input
      // If odd number of tildes and starts with ~, remove it temporarily
      if (tildeCount % 2 != 0 && working.startsWith("~")) {
        working = working.substring(1)
        hasStartingTilde = true
      }

      val chars = working.toCharArray
      var first = chars.indexOf('~')
      var last  = chars.lastIndexOf('~')

      while (first != -1 && last != -1 && first != last) {
        chars(first) = '<'
        chars(last) = '>'
        first = chars.indexOf('~')
        last = chars.lastIndexOf('~')
      }

      val result = new String(chars)
      if (hasStartingTilde) "~" + result else result
    }
  }
}

object ClassMember {

  /** Visibility values that are recognized. */
  val VisibilityValues: Set[String] = Set("#", "+", "~", "-", "")

  /** Creates a ClassMember by parsing the input string.
    *
    * Ports the `parseMember()` method from classTypes.ts.
    *
    * @param input
    *   the raw member string
    * @param memberType
    *   "method" or "attribute"
    * @return
    *   a parsed ClassMember
    */
  def parse(input: String, memberType: String): ClassMember = {
    val member    = ClassMember(memberType = memberType)
    val sanitized = sanitizeText(input)

    if (memberType == "method") {
      parseMethodMember(sanitized, member)
    } else {
      parseAttributeMember(sanitized, member)
    }

    member
  }

  /** Parses a method member string. */
  private def parseMethodMember(input: String, member: ClassMember): Unit = {
    val methodRegex = """([#+~-])?(.+)\((.*)\)([\s$*])?(.*)([$*])?""".r
    methodRegex.findFirstMatchIn(input) match {
      case Some(m) =>
        val detectedVisibility = Option(m.group(1)).map(_.trim).getOrElse("")
        if (VisibilityValues.contains(detectedVisibility)) {
          member.visibility = detectedVisibility
        }

        member.id = Option(m.group(2)).map(_.trim).getOrElse("")
        member.parameters = Option(m.group(3)).map(_.trim).getOrElse("")
        var potentialClassifier = Option(m.group(4)).map(_.trim).getOrElse("")
        member.returnType = Option(m.group(5)).map(_.trim).getOrElse("")

        if (potentialClassifier.isEmpty && member.returnType.nonEmpty) {
          val lastChar = member.returnType.substring(member.returnType.length - 1)
          if (lastChar == "$" || lastChar == "*") {
            potentialClassifier = lastChar
            member.returnType = member.returnType.substring(0, member.returnType.length - 1)
          }
        }
        member.classifier = potentialClassifier

      case None =>
        member.id = input
    }
  }

  /** Parses an attribute member string. */
  private def parseAttributeMember(input: String, member: ClassMember): Unit = {
    val length = input.length
    if (length != 0) {
      val firstChar = input.substring(0, 1)
      val lastChar  = input.substring(length - 1)

      if (VisibilityValues.contains(firstChar)) {
        member.visibility = firstChar
      }

      var potentialClassifier = ""
      if (lastChar == "$" || lastChar == "*") {
        potentialClassifier = lastChar
      }

      member.id = input.substring(
        if (member.visibility.isEmpty) 0 else 1,
        if (potentialClassifier.isEmpty) length else length - 1
      )
      member.classifier = potentialClassifier
    }
  }

  /** Simple text sanitizer. */
  private def sanitizeText(txt: String): String =
    txt.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")
}

/** A class node in the class diagram.
  *
  * @param id
  *   unique class identifier
  * @param classType
  *   generic type parameter (extracted from `ClassName~Type~`)
  * @param label
  *   display label (defaults to id)
  * @param cssClasses
  *   CSS classes applied to this node
  * @param methods
  *   parsed method members
  * @param members
  *   parsed attribute members
  * @param annotations
  *   annotations like <<interface>>, <<abstract>>
  * @param domId
  *   DOM element ID
  * @param styles
  *   inline CSS styles
  * @param parent
  *   parent namespace ID
  * @param link
  *   URL link for clickable nodes
  * @param linkTarget
  *   link target attribute
  * @param haveCallback
  *   whether this node has a click callback
  * @param tooltip
  *   tooltip text
  */
final case class ClassNode(
  id:               String,
  var classType:    String = "",
  var label:        String = "",
  cssClasses:       mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty,
  methods:          mutable.ArrayBuffer[ClassMember] = mutable.ArrayBuffer.empty,
  members:          mutable.ArrayBuffer[ClassMember] = mutable.ArrayBuffer.empty,
  annotations:      mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty,
  domId:            String = "",
  styles:           mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty,
  var parent:       Nullable[String] = Nullable.empty,
  var link:         Nullable[String] = Nullable.empty,
  var linkTarget:   Nullable[String] = Nullable.empty,
  var haveCallback: Boolean = false,
  var tooltip:      Nullable[String] = Nullable.empty
)

/** A relation (edge) between two classes.
  *
  * @param id1
  *   source class ID
  * @param id2
  *   destination class ID
  * @param relationTitle1
  *   cardinality/label on the id1 side
  * @param relationTitle2
  *   cardinality/label on the id2 side
  * @param relationType
  *   relationship type details
  * @param title
  *   label on the relation line
  */
final case class ClassRelation(
  var id1:            String,
  var id2:            String,
  var relationTitle1: String = "none",
  var relationTitle2: String = "none",
  var relationType:   RelationDetail = RelationDetail(),
  var title:          String = ""
)

/** Details of a class relation arrow.
  *
  * @param type1
  *   arrowhead type on the source side
  * @param type2
  *   arrowhead type on the destination side
  * @param lineType
  *   line style (solid or dotted)
  */
final case class RelationDetail(
  var type1:    Int = ClassRelationType.None,
  var type2:    Int = ClassRelationType.None,
  var lineType: Int = ClassLineType.Line
)

/** A note attached to a class.
  *
  * @param id
  *   unique note ID
  * @param className
  *   the class this note is attached to (empty for standalone notes)
  * @param text
  *   note text content
  */
final case class ClassNote(
  id:        String,
  className: String,
  text:      String
)

/** A namespace grouping classes.
  *
  * @param id
  *   namespace identifier
  * @param domId
  *   DOM element ID
  * @param classes
  *   classes in this namespace
  */
final case class NamespaceNode(
  id:      String,
  domId:   String,
  classes: mutable.LinkedHashMap[String, ClassNode] = mutable.LinkedHashMap.empty
)

/** Line type constants for class relations.
  *
  * Ports the `lineType` object from classDb.ts.
  */
object ClassLineType {
  val Line:       Int = 0
  val DottedLine: Int = 1
}

/** Relation type constants for class diagrams.
  *
  * Ports the `relationType` object from classDb.ts.
  */
object ClassRelationType {
  val None:        Int = -1
  val Aggregation: Int = 0
  val Extension:   Int = 1
  val Composition: Int = 2
  val Dependency:  Int = 3
  val Lollipop:    Int = 4
}

/** Mutable database for class diagram data.
  *
  * Accumulates classes, relations, notes, and namespaces during parsing. The renderer reads from this database to produce SVG output.
  *
  * Ports the module-level mutable state and functions from `classDb.ts`.
  */
final class ClassDb {

  private val MERMAID_DOM_ID_PREFIX = "classId-"
  private var classCounter:     Int = 0
  private var namespaceCounter: Int = 0

  val classes:    mutable.LinkedHashMap[String, ClassNode]     = mutable.LinkedHashMap.empty
  val relations:  mutable.ArrayBuffer[ClassRelation]           = mutable.ArrayBuffer.empty
  val notes:      mutable.ArrayBuffer[ClassNote]               = mutable.ArrayBuffer.empty
  val namespaces: mutable.LinkedHashMap[String, NamespaceNode] = mutable.LinkedHashMap.empty

  var direction:      String = "TB"
  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  // classDb.ts:267 — `const config = getConfig()`; setLink consults config.securityLevel.
  // Mirror that module-level config by exposing the single securityLevel value the db
  // consults; set before parsing by ClassDiagram (classDb.ts captures config in setLink's closure).
  var securityLevel: String = "strict"

  // --- Class methods ---

  /** Splits a class identifier into class name and generic type.
    *
    * `ClassName~Type~` → (ClassName, Type)
    */
  def splitClassNameAndType(rawId: String): (String, String) = {
    val id = sanitizeText(rawId)
    if (id.contains("~")) {
      val parts       = id.split("~", 2)
      val className   = sanitizeText(parts(0))
      val genericType = if (parts.length > 1) sanitizeText(parts(1).stripSuffix("~")) else ""
      (className, genericType)
    } else {
      (id, "")
    }
  }

  /** Adds a class to the diagram.
    *
    * If the class already exists, this is a no-op. Ports `addClass()` from classDb.ts.
    */
  def addClass(rawId: String): Unit = {
    val id                       = sanitizeText(rawId)
    val (className, genericType) = splitClassNameAndType(id)

    if (!classes.contains(className)) {
      val name = sanitizeText(className)
      classes(name) = ClassNode(
        id = name,
        classType = genericType,
        label = name,
        domId = MERMAID_DOM_ID_PREFIX + name + "-" + classCounter
      )
      classCounter += 1
    }
  }

  /** Sets the label for a class.
    *
    * Ports `setClassLabel()` from classDb.ts.
    */
  def setClassLabel(rawId: String, label: String): Unit = {
    val id             = sanitizeText(rawId)
    val sanitizedLabel = if (label.nonEmpty) sanitizeText(label) else ""
    val (className, _) = splitClassNameAndType(id)
    classes.get(className).foreach { node =>
      node.label = sanitizedLabel
    }
  }

  /** Returns the class with the given ID. */
  def getClass(id: String): ClassNode =
    classes.getOrElse(id, throw new NoSuchElementException(s"Class not found: $id"))

  /** Function to lookup domId from id in the graph definition.
    *
    * Ports `lookUpDomId()` from classDb.ts.
    */
  def lookUpDomId(rawId: String): String = {
    val id = sanitizeText(rawId)
    classes.get(id).map(_.domId).getOrElse {
      throw new NoSuchElementException(s"Class not found: $id")
    }
  }

  // --- Relation methods ---

  /** Adds a relation between two classes.
    *
    * Ports `addRelation()` from classDb.ts.
    */
  def addRelation(relation: ClassRelation): Unit = {
    addClass(relation.id1)
    addClass(relation.id2)

    relation.id1 = splitClassNameAndType(relation.id1)._1
    relation.id2 = splitClassNameAndType(relation.id2)._1

    relation.relationTitle1 = sanitizeText(relation.relationTitle1.trim)
    relation.relationTitle2 = sanitizeText(relation.relationTitle2.trim)

    relations += relation
  }

  // --- Annotation methods ---

  /** Adds an annotation to a class.
    *
    * Ports `addAnnotation()` from classDb.ts.
    */
  def addAnnotation(className: String, annotation: String): Unit = {
    val (validatedClassName, _) = splitClassNameAndType(className)
    classes.get(validatedClassName).foreach { node =>
      node.annotations += annotation
    }
  }

  // --- Member methods ---

  /** Adds a member to a class.
    *
    * Ports `addMember()` from classDb.ts.
    */
  def addMember(className: String, member: String): Unit = {
    addClass(className)
    val (validatedClassName, _) = splitClassNameAndType(className)
    classes.get(validatedClassName).foreach { theClass =>
      val memberString = member.trim
      if (memberString.startsWith("<<") && memberString.endsWith(">>")) {
        // It's an annotation
        theClass.annotations += sanitizeText(memberString.substring(2, memberString.length - 2))
      } else if (memberString.contains(")")) {
        // It's a method
        theClass.methods += ClassMember.parse(memberString, "method")
      } else if (memberString.nonEmpty) {
        theClass.members += ClassMember.parse(memberString, "attribute")
      }
    }
  }

  /** Adds multiple members to a class.
    *
    * Ports `addMembers()` from classDb.ts. The original JISON parser produces members in reverse order and the original `addMembers()` reverses them back. Our hand-written parser produces members in
    * source order, so we iterate forward without reversing.
    */
  def addMembers(className: String, membersList: Array[String]): Unit =
    membersList.foreach { member =>
      addMember(className, member)
    }

  // --- Note methods ---

  /** Adds a note, optionally attached to a class.
    *
    * Ports `addNote()` from classDb.ts.
    */
  def addNote(text: String, className: String = ""): Unit = {
    val note = ClassNote(
      id = s"note${notes.length}",
      className = className,
      text = text
    )
    notes += note
  }

  // --- CSS/Style methods ---

  /** Sets CSS class on class nodes.
    *
    * Ports `setCssClass()` from classDb.ts.
    */
  def setCssClass(ids: String, className: String): Unit =
    ids.split(",").foreach { rawId =>
      var id = rawId.trim
      if (id.nonEmpty && id.charAt(0).isDigit) {
        id = MERMAID_DOM_ID_PREFIX + id
      }
      classes.get(id).foreach { classNode =>
        classNode.cssClasses += className
      }
    }

  /** Sets inline CSS styles on a class.
    *
    * Ports `setCssStyle()` from classDb.ts.
    */
  def setCssStyle(id: String, stylesList: Array[String]): Unit =
    classes.get(id).foreach { theClass =>
      for (s <- stylesList)
        if (s.contains(",")) {
          theClass.styles ++= s.split(",").map(_.trim)
        } else {
          theClass.styles += s
        }
    }

  /** Sets a tooltip on a class.
    *
    * Ports `setTooltip()` from classDb.ts.
    */
  def setTooltip(ids: String, tooltip: String): Unit =
    ids.split(",").foreach { id =>
      classes.get(id.trim).foreach { node =>
        node.tooltip = Nullable(sanitizeText(tooltip))
      }
    }

  /** Gets the tooltip for a class.
    *
    * Ports `getTooltip()` from classDb.ts.
    */
  def getTooltip(id: String, namespace: Nullable[String] = Nullable.empty): Nullable[String] = {
    // Check namespace first, then global classes
    val nsResult = namespace.flatMap { ns =>
      val nsNode = namespaces.get(ns)
      nsNode match {
        case Some(n) =>
          n.classes.get(id) match {
            case Some(cls) => cls.tooltip
            case _         => Nullable.empty
          }
        case _ => Nullable.empty
      }
    }
    if (nsResult.isDefined) nsResult
    else {
      classes.get(id) match {
        case Some(cls) => cls.tooltip
        case _         => Nullable.empty
      }
    }
  }

  /** Sets a link on a class.
    *
    * Ports `setLink()` from classDb.ts.
    */
  def setLink(ids: String, linkStr: String, target: String = "_blank"): Unit = {
    ids.split(",").foreach { rawId =>
      var id = rawId.trim
      if (id.nonEmpty && id.charAt(0).isDigit) {
        id = MERMAID_DOM_ID_PREFIX + id
      }
      classes.get(id).foreach { theClass =>
        // classDb.ts:275 — the URL is sanitized (utils.formatUrl → Utils.sanitizeUrl)
        theClass.link = Nullable(ssg.mermaid.util.Utils.sanitizeUrl(linkStr))
        // classDb.ts:276-281 — under sandbox the link target is forced to '_top'; otherwise the
        // (string) target is sanitized. The upstream `else '_blank'` (non-string target) is handled
        // at the parser (ClassParser.scala passes "_blank" when LINK_TARGET is omitted).
        if (securityLevel == "sandbox") {
          theClass.linkTarget = Nullable("_top")
        } else {
          theClass.linkTarget = Nullable(sanitizeText(target))
        }
      }
    }
    setCssClass(ids, "clickable")
  }

  /** Sets a click event on a class (not functional server-side).
    *
    * Ports `setClickEvent()` from classDb.ts.
    */
  def setClickEvent(ids: String, functionName: String, functionArgs: String = ""): Unit = {
    ids.split(",").foreach { id =>
      classes.get(id.trim).foreach { node =>
        node.haveCallback = true
      }
    }
    setCssClass(ids, "clickable")
  }

  // --- Direction methods ---

  /** Sets the graph direction.
    *
    * Ports `setDirection()` from classDb.ts.
    */
  def setDirection(dir: String): Unit =
    direction = dir

  // --- Namespace methods ---

  /** Adds a namespace.
    *
    * Ports `addNamespace()` from classDb.ts.
    */
  def addNamespace(id: String): Unit =
    if (!namespaces.contains(id)) {
      namespaces(id) = NamespaceNode(
        id = id,
        domId = MERMAID_DOM_ID_PREFIX + id + "-" + namespaceCounter
      )
      namespaceCounter += 1
    }

  /** Adds classes to a namespace.
    *
    * Ports `addClassesToNamespace()` from classDb.ts.
    */
  def addClassesToNamespace(id: String, classNames: Array[String]): Unit =
    if (namespaces.contains(id)) {
      val ns = namespaces(id)
      for (name <- classNames) {
        val (className, _) = splitClassNameAndType(name)
        classes.get(className).foreach { classNode =>
          classNode.parent = Nullable(id)
          ns.classes(className) = classNode
        }
      }
    }

  /** Returns the namespace ID for a given class, if any.
    *
    * Ports `getNamespace()` logic from classDb.ts — searches all namespaces to find which one contains the given class.
    */
  def getNamespace(className: String): Nullable[String] = boundary {
    val (validatedClassName, _) = splitClassNameAndType(className)
    for ((nsId, ns) <- namespaces)
      if (ns.classes.contains(validatedClassName)) {
        break(Nullable(nsId))
      }
    Nullable.empty
  }

  /** Looks up a class by ID, optionally scoped to a namespace.
    *
    * Ports the namespace-aware lookup from classDb.ts. If a namespace is provided, looks in the namespace first before falling back to global.
    */
  def lookupClass(
    rawId:     String,
    namespace: Nullable[String] = Nullable.empty
  ): Nullable[ClassNode] = boundary {
    val id             = sanitizeText(rawId)
    val (className, _) = splitClassNameAndType(id)

    // Try namespace-scoped lookup first
    namespace.foreach { ns =>
      namespaces.get(ns).foreach { nsNode =>
        nsNode.classes.get(className).foreach { cls =>
          break(Nullable(cls))
        }
      }
    }

    // Fall back to global lookup
    classes.get(className) match {
      case Some(cls) => Nullable(cls)
      case _         => Nullable.empty
    }
  }

  // --- Cleanup helper ---

  /** Cleans up a label string (removes leading colon).
    *
    * Ports `cleanupLabel()` from classDb.ts.
    */
  def cleanupLabel(label: String): String = {
    val trimmed = if (label.startsWith(":")) label.substring(1) else label
    sanitizeText(trimmed.trim)
  }

  // --- Clear ---

  /** Clears all state for parsing a new diagram.
    *
    * Ports `clear()` from classDb.ts.
    */
  def clear(): Unit = {
    relations.clear()
    classes.clear()
    notes.clear()
    namespaces.clear()
    classCounter = 0
    namespaceCounter = 0
    direction = "TB"
    title = ""
    accTitle = ""
    accDescription = ""
  }

  /** Simple text sanitizer. */
  private def sanitizeText(txt: String): String =
    txt.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")
}
