/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/class/parser/classDiagram.jison
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces JISON-generated parser with hand-written recursive descent parser
 *   Idiom: Scanner-based parsing with boundary/break; ClassDb for accumulation
 *   Renames: classDiagram.jison → ClassParser
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package class_

import ssg.mermaid.parse.{ ParseException, Scanner }

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

/** Hand-written parser for Mermaid class diagram syntax.
  *
  * Parses the grammar defined in `classDiagram.jison`, producing a populated [[ClassDb]].
  *
  * Supported syntax:
  *   - `classDiagram` — header keyword
  *   - `class ClassName` — class declaration
  *   - `class ClassName { members }` — class with body
  *   - `class ClassName["label"]` — class with display label
  *   - `ClassName : member` — standalone member declaration
  *   - `ClassName <|-- ClassName` — relations with various arrow types
  *   - `ClassName "card1" -- "card2" ClassName : label` — relations with cardinality
  *   - `<<interface>> ClassName` — annotations
  *   - `note "text"` / `note for ClassName "text"` — notes
  *   - `namespace Name { classes }` — namespace grouping
  *   - `direction TB/BT/LR/RL` — layout direction
  *   - `click`, `link`, `callback`, `cssClass`, `style` — interactivity and styling
  */
object ClassParser {

  /** Parses Mermaid class diagram source text into a [[ClassDb]].
    *
    * @param input
    *   the raw Mermaid diagram text
    * @return
    *   a populated ClassDb
    * @throws ParseException
    *   if the input cannot be parsed
    */
  def parse(input: String): ClassDb = {
    val db      = new ClassDb
    val cleaned = cleanInput(input)
    val scanner = new Scanner(cleaned)

    scanner.skipWhitespaceAndNewlines()

    // Parse header: classDiagram or classDiagram-v2
    parseHeader(scanner)

    // Parse statements
    parseStatements(scanner, db)

    db
  }

  /** Removes directives and comments from input. */
  private def cleanInput(input: String): String = {
    var s = input
    s = s.replaceAll("%%\\{[^}]*\\}%%", "")
    s = s.replaceAll("%%[^{\\n][^\\n]*", "").replaceAll("%%$", "")
    s
  }

  /** Parses the diagram header keyword. */
  private def parseHeader(scanner: Scanner): Unit = {
    scanner.skipWhitespaceAndNewlines()
    if (
      !scanner.matchStrIgnoreCase("classDiagram-v2") &&
      !scanner.matchStrIgnoreCase("classDiagram")
    ) {
      throw new ParseException("Expected 'classDiagram' keyword", scanner.line, scanner.col)
    }
    skipToNewline(scanner)
  }

  /** Parses a sequence of statements. */
  private def parseStatements(scanner: Scanner, db: ClassDb): Unit = boundary {
    while (!scanner.isEof) {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof) break()

      parseStatement(scanner, db)
    }
  }

  /** Parses a single statement. */
  private def parseStatement(scanner: Scanner, db: ClassDb): Unit = boundary {
    scanner.skipWhitespace()
    if (scanner.isEof || scanner.peek() == '\n') {
      if (!scanner.isEof) scanner.advance()
      break()
    }

    val saved = scanner.save()

    // Try keyword-based statements
    if (tryParseDirection(scanner, db)) break()
    scanner.restore(saved)

    if (tryParseNamespace(scanner, db)) break()
    scanner.restore(saved)

    if (tryParseClassStatement(scanner, db)) break()
    scanner.restore(saved)

    if (tryParseAnnotation(scanner, db)) break()
    scanner.restore(saved)

    if (tryParseNoteFor(scanner, db)) break()
    scanner.restore(saved)

    if (tryParseNote(scanner, db)) break()
    scanner.restore(saved)

    if (tryParseCssClass(scanner, db)) break()
    scanner.restore(saved)

    if (tryParseCallback(scanner, db)) break()
    scanner.restore(saved)

    if (tryParseClick(scanner, db)) break()
    scanner.restore(saved)

    if (tryParseLinkStatement(scanner, db)) break()
    scanner.restore(saved)

    if (tryParseStyleStatement(scanner, db)) break()
    scanner.restore(saved)

    if (tryParseAccTitle(scanner, db)) break()
    scanner.restore(saved)

    if (tryParseAccDescr(scanner, db)) break()
    scanner.restore(saved)

    // Try relation statement (ClassName relation ClassName)
    if (tryParseRelation(scanner, db)) break()
    scanner.restore(saved)

    // Try member statement (ClassName : member)
    if (tryParseMemberStatement(scanner, db)) break()
    scanner.restore(saved)

    // Skip unrecognized line
    skipToNewline(scanner)
  }

  // --- Keyword parsers ---

  /** Tries to parse `direction TB/BT/LR/RL`. */
  private def tryParseDirection(scanner: Scanner, db: ClassDb): Boolean = {
    val saved = scanner.save()
    // Direction can appear anywhere in the line
    val line       = peekLine(scanner)
    val dirPattern = """direction\s+(TB|BT|RL|LR)""".r
    dirPattern.findFirstMatchIn(line) match {
      case Some(m) =>
        db.setDirection(m.group(1))
        skipToNewline(scanner)
        true
      case None =>
        scanner.restore(saved)
        false
    }
  }

  /** Tries to parse `namespace Name { ... }`. */
  private def tryParseNamespace(scanner: Scanner, db: ClassDb): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("namespace")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val name = readClassName(scanner)
    if (name.isEmpty) break(false)

    db.addNamespace(name)

    scanner.skipWhitespace()

    // Expect opening brace (may be on same line as namespace keyword or next line)
    if (scanner.isEof || scanner.peek() != '{') {
      skipToNewline(scanner)
      scanner.skipWhitespaceAndNewlines()
    }
    if (!scanner.isEof && scanner.peek() == '{') {
      scanner.advance()
      scanner.skipWhitespaceAndNewlines()

      // Parse class statements inside namespace
      val classNames = mutable.ArrayBuffer.empty[String]
      boundary[Unit] {
        while (!scanner.isEof) {
          scanner.skipWhitespaceAndNewlines()
          if (scanner.isEof) break(())
          if (scanner.peek() == '}') {
            scanner.advance()
            break(())
          }

          val saved  = scanner.save()
          val parsed = tryParseClassStatementName(scanner, db)
          if (parsed.nonEmpty) {
            classNames += parsed
          } else {
            scanner.restore(saved)
            skipToNewline(scanner)
          }
        }
      }

      db.addClassesToNamespace(name, classNames.toArray)
    }

    true
  }

  /** Tries to parse `class ClassName { members }` or `class ClassName:::style`.
    *
    * @return
    *   the parsed class name if successful, or empty string if not
    */
  private def tryParseClassStatement(scanner: Scanner, db: ClassDb): Boolean =
    tryParseClassStatementName(scanner, db).nonEmpty

  /** Tries to parse `class ClassName { members }` or `class ClassName:::style`.
    *
    * @return
    *   the parsed class name if successful, or empty string if not
    */
  private def tryParseClassStatementName(scanner: Scanner, db: ClassDb): String = boundary {
    if (!scanner.matchStrIgnoreCase("class")) break("")
    if (!isWordBoundary(scanner)) break("")
    scanner.skipWhitespace()

    val className = readClassName(scanner)
    if (className.isEmpty) break("")

    db.addClass(className)

    scanner.skipWhitespace()

    // Check for generic type: class Name~T~
    if (!scanner.isEof && scanner.peek() == '~') {
      scanner.advance()
      val sb = new StringBuilder
      while (!scanner.isEof && scanner.peek() != '~')
        sb.append(scanner.advance())
      if (!scanner.isEof && scanner.peek() == '~') scanner.advance()
      val (cn, _) = db.splitClassNameAndType(className)
      db.classes.get(cn).foreach(_.classType = sb.toString)
    }

    // Check for label: class Name["label"]
    if (!scanner.isEof && scanner.peek() == '[') {
      scanner.advance()
      if (!scanner.isEof && scanner.peek() == '"') {
        val label = scanner.readQuotedString()
        db.setClassLabel(className, label)
        // Skip closing ]
        if (!scanner.isEof && scanner.peek() == ']') scanner.advance()
      }
    }

    // Check for :::style
    if (!scanner.isEof && scanner.matchStr(":::")) {
      val style = readAlphaNum(scanner)
      if (style.nonEmpty) {
        db.setCssClass(className, style)
      }
    }

    scanner.skipWhitespace()

    // Check for body: { members }
    if (!scanner.isEof && scanner.peek() == '{') {
      scanner.advance()
      val membersList = parseClassBody(scanner)
      db.addMembers(className, membersList.toArray)
    }

    // Skip to end of line, but stop at '}' (which may close a namespace block)
    skipToNewlineOrBrace(scanner)
    className
  }

  /** Parses the body of a class (between { and }). Returns member strings. */
  private def parseClassBody(scanner: Scanner): mutable.ArrayBuffer[String] = boundary {
    val members = mutable.ArrayBuffer.empty[String]
    boundary[mutable.ArrayBuffer[String]] {
      while (!scanner.isEof) {
        scanner.skipWhitespace()
        val c = scanner.peek()
        if (c == '}') {
          scanner.advance()
          break(members)
        } else if (c == '\n') {
          scanner.advance()
        } else {
          // Read member line
          val sb = new StringBuilder()
          while (!scanner.isEof && scanner.peek() != '\n' && scanner.peek() != '}')
            sb.append(scanner.advance())
          val member = sb.toString.trim
          if (member.nonEmpty) {
            members += member
          }
        }
      }
      members
    }
  }

  /** Tries to parse `<<annotation>> ClassName`. */
  private def tryParseAnnotation(scanner: Scanner, db: ClassDb): Boolean = boundary {
    if (!scanner.matchStr("<<")) break(false)
    val annotation = readUntilStr(scanner, ">>")
    if (annotation.isEmpty) break(false)
    scanner.skipWhitespace()
    val className = readClassName(scanner)
    if (className.nonEmpty) {
      db.addClass(className)
      db.addAnnotation(className, annotation)
    }
    skipToNewline(scanner)
    true
  }

  /** Tries to parse `note for ClassName "text"`. */
  private def tryParseNoteFor(scanner: Scanner, db: ClassDb): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("note for")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val className = readClassName(scanner)
    scanner.skipWhitespace()
    val text = if (!scanner.isEof && scanner.peek() == '"') {
      scanner.readQuotedString()
    } else {
      readTextUntilNewline(scanner).trim
    }

    if (text.nonEmpty) {
      db.addNote(text, className)
    }
    skipToNewline(scanner)
    true
  }

  /** Tries to parse standalone `note "text"`. */
  private def tryParseNote(scanner: Scanner, db: ClassDb): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("note")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val text = if (!scanner.isEof && scanner.peek() == '"') {
      scanner.readQuotedString()
    } else {
      readTextUntilNewline(scanner).trim
    }

    if (text.nonEmpty) {
      db.addNote(text)
    }
    skipToNewline(scanner)
    true
  }

  /** Tries to parse `cssClass "id1,id2" className`. */
  private def tryParseCssClass(scanner: Scanner, db: ClassDb): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("cssClass")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val ids = if (!scanner.isEof && scanner.peek() == '"') {
      scanner.readQuotedString()
    } else {
      readAlphaNum(scanner)
    }
    scanner.skipWhitespace()
    val className = readAlphaNum(scanner)

    if (ids.nonEmpty && className.nonEmpty) {
      db.setCssClass(ids, className)
    }
    skipToNewline(scanner)
    true
  }

  /** Tries to parse `callback ClassName func [tooltip]`. */
  private def tryParseCallback(scanner: Scanner, db: ClassDb): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("callback")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val className = readClassName(scanner)
    scanner.skipWhitespace()
    val funcName = if (!scanner.isEof && scanner.peek() == '"') scanner.readQuotedString() else readAlphaNum(scanner)
    scanner.skipWhitespace()
    val tooltip = if (!scanner.isEof && scanner.peek() == '"') scanner.readQuotedString() else ""

    if (className.nonEmpty && funcName.nonEmpty) {
      db.setClickEvent(className, funcName)
      if (tooltip.nonEmpty) db.setTooltip(className, tooltip)
    }
    skipToNewline(scanner)
    true
  }

  /** Tries to parse `click ClassName href/call/func`. */
  private def tryParseClick(scanner: Scanner, db: ClassDb): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("click")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val className = readClassName(scanner)
    scanner.skipWhitespace()

    // Check for href
    if (scanner.matchStrIgnoreCase("href")) {
      scanner.skipWhitespace()
      val link = if (!scanner.isEof && scanner.peek() == '"') scanner.readQuotedString() else readAlphaNum(scanner)
      scanner.skipWhitespace()
      // Optional tooltip and target
      val tooltipOrTarget = if (!scanner.isEof && scanner.peek() == '"') scanner.readQuotedString() else ""
      scanner.skipWhitespace()
      val target =
        if (!scanner.isEof && scanner.peek() == '_') readAlphaNum(scanner)
        else if (!scanner.isEof && scanner.peek() == '"') scanner.readQuotedString()
        else ""

      if (link.nonEmpty) {
        val linkTarget = if (target.nonEmpty) target else "_blank"
        db.setLink(className, link, linkTarget)
        if (tooltipOrTarget.nonEmpty && !tooltipOrTarget.startsWith("_")) {
          db.setTooltip(className, tooltipOrTarget)
        }
      }
    } else if (scanner.matchStrIgnoreCase("call")) {
      scanner.skipWhitespace()
      // Read callback name until ( or space
      val funcName = readUntilChar(scanner, '(')
      // Read args between parentheses
      val args = if (!scanner.isEof && scanner.peek() != ')') readUntilChar(scanner, ')') else { if (!scanner.isEof && scanner.peek() == ')') scanner.advance(); "" }
      scanner.skipWhitespace()
      val tooltip = if (!scanner.isEof && scanner.peek() == '"') scanner.readQuotedString() else ""

      if (funcName.trim.nonEmpty) {
        db.setClickEvent(className, funcName.trim, args)
        if (tooltip.nonEmpty) db.setTooltip(className, tooltip)
      }
    } else {
      // Regular callback
      val funcName = if (!scanner.isEof && scanner.peek() == '"') scanner.readQuotedString() else readAlphaNum(scanner)
      scanner.skipWhitespace()
      val tooltip = if (!scanner.isEof && scanner.peek() == '"') scanner.readQuotedString() else ""

      if (funcName.nonEmpty) {
        db.setClickEvent(className, funcName)
        if (tooltip.nonEmpty) db.setTooltip(className, tooltip)
      }
    }

    skipToNewline(scanner)
    true
  }

  /** Tries to parse `link ClassName "url" ["tooltip"] [target]`. */
  private def tryParseLinkStatement(scanner: Scanner, db: ClassDb): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("link")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val className = readClassName(scanner)
    scanner.skipWhitespace()
    val link = if (!scanner.isEof && scanner.peek() == '"') scanner.readQuotedString() else readAlphaNum(scanner)
    scanner.skipWhitespace()
    // Optional tooltip in quotes
    val tooltip = if (!scanner.isEof && scanner.peek() == '"') scanner.readQuotedString() else ""
    scanner.skipWhitespace()
    val target = if (!scanner.isEof && scanner.peek() == '_') readAlphaNum(scanner) else "_blank"

    if (className.nonEmpty && link.nonEmpty) {
      db.setLink(className, link, target)
      if (tooltip.nonEmpty) {
        db.setTooltip(className, tooltip)
      }
    }
    skipToNewline(scanner)
    true
  }

  /** Tries to parse `style ClassName styles`. */
  private def tryParseStyleStatement(scanner: Scanner, db: ClassDb): Boolean = boundary {
    if (!scanner.matchStr("style")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val className = readAlphaNum(scanner)
    scanner.skipWhitespace()
    val styles = readStyles(scanner)

    if (className.nonEmpty && styles.nonEmpty) {
      db.setCssStyle(className, styles.toArray)
    }
    skipToNewline(scanner)
    true
  }

  /** Tries to parse `accTitle: text`. */
  private def tryParseAccTitle(scanner: Scanner, db: ClassDb): Boolean = boundary {
    val saved = scanner.save()
    if (scanner.matchStrIgnoreCase("accTitle")) {
      scanner.skipWhitespace()
      if (!scanner.isEof && scanner.peek() == ':') {
        scanner.advance()
        scanner.skipWhitespace()
        db.accTitle = readTextUntilNewline(scanner).trim
        skipToNewline(scanner)
        break(true)
      }
    }
    scanner.restore(saved)
    false
  }

  /** Tries to parse `accDescr: text` or `accDescr { text }`. */
  private def tryParseAccDescr(scanner: Scanner, db: ClassDb): Boolean = boundary {
    val saved = scanner.save()
    if (scanner.matchStrIgnoreCase("accDescr")) {
      scanner.skipWhitespace()
      if (!scanner.isEof && scanner.peek() == ':') {
        scanner.advance()
        scanner.skipWhitespace()
        db.accDescription = readTextUntilNewline(scanner).trim
        skipToNewline(scanner)
        break(true)
      } else if (!scanner.isEof && scanner.peek() == '{') {
        scanner.advance()
        val content = scanner.readUntil('}')
        // Normalize multiline: trim each line, then join with \n, then trim the whole thing
        db.accDescription = content.split('\n').map(_.trim).filter(_.nonEmpty).mkString("\n")
        skipToNewline(scanner)
        break(true)
      }
    }
    scanner.restore(saved)
    false
  }

  /** Tries to parse a relation statement: `ClassName [cardinality] relation [cardinality] ClassName [: label]`. */
  private def tryParseRelation(scanner: Scanner, db: ClassDb): Boolean = boundary {
    val saved = scanner.save()
    scanner.skipWhitespace()

    // Read first class name
    val class1 = readClassName(scanner)
    if (class1.isEmpty) {
      scanner.restore(saved)
      break(false)
    }

    scanner.skipWhitespace()

    // Optional cardinality 1 (in quotes)
    val card1 = if (!scanner.isEof && scanner.peek() == '"') {
      scanner.readQuotedString()
    } else ""

    scanner.skipWhitespace()

    // Read relation
    val relation = tryReadRelation(scanner)
    if (relation.isEmpty) {
      scanner.restore(saved)
      break(false)
    }

    scanner.skipWhitespace()

    // Optional cardinality 2 (in quotes)
    val card2 = if (!scanner.isEof && scanner.peek() == '"') {
      scanner.readQuotedString()
    } else ""

    scanner.skipWhitespace()

    // Read second class name
    val class2 = readClassName(scanner)
    if (class2.isEmpty) {
      scanner.restore(saved)
      break(false)
    }

    scanner.skipWhitespace()

    // Optional label after ':'
    val label = if (!scanner.isEof && scanner.peek() == ':') {
      scanner.advance()
      scanner.skipWhitespace()
      db.cleanupLabel(readTextUntilNewline(scanner).trim)
    } else ""

    val classRelation = ClassRelation(
      id1 = class1,
      id2 = class2,
      relationTitle1 = if (card1.nonEmpty) card1 else "none",
      relationTitle2 = if (card2.nonEmpty) card2 else "none",
      relationType = relation.get,
      title = label
    )
    db.addRelation(classRelation)

    skipToNewline(scanner)
    true
  }

  /** Tries to parse a member statement: `ClassName : member`. */
  private def tryParseMemberStatement(scanner: Scanner, db: ClassDb): Boolean = boundary {
    val saved = scanner.save()
    scanner.skipWhitespace()

    val className = readClassName(scanner)
    if (className.isEmpty) {
      scanner.restore(saved)
      break(false)
    }

    scanner.skipWhitespace()

    // Check for : member
    if (!scanner.isEof && scanner.peek() == ':') {
      scanner.advance()
      scanner.skipWhitespace()
      val memberText = readTextUntilNewline(scanner).trim
      if (memberText.nonEmpty) {
        db.addMember(className, db.cleanupLabel(":" + memberText))
      }
      skipToNewline(scanner)
      break(true)
    }

    scanner.restore(saved)
    false
  }

  // --- Relation type parsing ---

  /** Tries to read a relation at the current position.
    *
    * Relation format: [relationType1] lineType [relationType2] Examples: `<|--`, `*--`, `o--`, `-->`, `..>`, `<|..`, `--`
    *
    * @return
    *   Some(RelationDetail) if a relation was read, None otherwise
    */
  private def tryReadRelation(scanner: Scanner): Option[RelationDetail] = boundary {
    val saved = scanner.save()

    // Try to read type1 (left side)
    val type1 = tryReadRelationType(scanner)
    scanner.skipWhitespace()

    // Read line type
    val lineType = tryReadLineType(scanner)
    if (lineType < 0) {
      scanner.restore(saved)
      break(scala.None)
    }

    scanner.skipWhitespace()

    // Try to read type2 (right side)
    val type2 = tryReadRelationType(scanner)

    // At least one side must have a relation or we need a line
    Some(
      RelationDetail(
        type1 = type1,
        type2 = type2,
        lineType = lineType
      )
    )
  }

  /** Tries to read a relation type (<|, >, <, *, o, ()). */
  private def tryReadRelationType(scanner: Scanner): Int = boundary {
    val saved = scanner.save()

    if (scanner.isEof) break(ClassRelationType.None)

    // <| (extension)
    if (scanner.matchStr("<|")) break(ClassRelationType.Extension)
    scanner.restore(saved)

    // |> (extension, reversed)
    if (scanner.matchStr("|>")) break(ClassRelationType.Extension)
    scanner.restore(saved)

    // () (lollipop)
    if (scanner.matchStr("()")) break(ClassRelationType.Lollipop)
    scanner.restore(saved)

    // > (dependency)
    if (!scanner.isEof && scanner.peek() == '>') {
      scanner.advance()
      break(ClassRelationType.Dependency)
    }
    scanner.restore(saved)

    // < (dependency)
    if (!scanner.isEof && scanner.peek() == '<') {
      scanner.advance()
      break(ClassRelationType.Dependency)
    }
    scanner.restore(saved)

    // * (composition)
    if (!scanner.isEof && scanner.peek() == '*') {
      scanner.advance()
      break(ClassRelationType.Composition)
    }
    scanner.restore(saved)

    // o (aggregation)
    if (!scanner.isEof && scanner.peek() == 'o') {
      // Make sure it's not part of a word
      val next = scanner.peekAt(1)
      if (next == '-' || next == '.' || next == ' ' || next == '\n') {
        scanner.advance()
        break(ClassRelationType.Aggregation)
      }
    }

    ClassRelationType.None
  }

  /** Tries to read a line type (-- or ..). */
  private def tryReadLineType(scanner: Scanner): Int = boundary {
    val saved = scanner.save()
    if (scanner.matchStr("--")) break(ClassLineType.Line)
    scanner.restore(saved)
    if (scanner.matchStr("..")) break(ClassLineType.DottedLine)
    scanner.restore(saved)
    -1
  }

  // --- Helpers ---

  /** Reads a class name (alphanumeric + special chars, with generic type support). */
  private def readClassName(scanner: Scanner): String = boundary {
    val sb        = new StringBuilder()
    var inGeneric = false
    while (!scanner.isEof) {
      val c = scanner.peek()
      if (c == '~') {
        // Generic type delimiter
        sb.append(scanner.advance())
        inGeneric = !inGeneric
      } else if (inGeneric) {
        sb.append(scanner.advance())
      } else if (c == '`') {
        // Backtick-quoted literal name
        scanner.advance() // consume opening backtick
        while (!scanner.isEof && scanner.peek() != '`')
          sb.append(scanner.advance())
        if (!scanner.isEof) scanner.advance() // consume closing backtick
      } else if (c.isLetterOrDigit || c == '_' || c == '-' || c == '.' || c == '*') {
        sb.append(scanner.advance())
      } else {
        break(sb.toString.trim)
      }
    }
    sb.toString.trim
  }

  /** Reads an alphanumeric token. */
  private def readAlphaNum(scanner: Scanner): String = boundary {
    val sb = new StringBuilder()
    while (!scanner.isEof) {
      val c = scanner.peek()
      if (c.isLetterOrDigit || c == '_' || c == '-') {
        sb.append(scanner.advance())
      } else {
        break(sb.toString)
      }
    }
    sb.toString
  }

  /** Reads comma-separated styles. */
  private def readStyles(scanner: Scanner): mutable.ArrayBuffer[String] = {
    val styles  = mutable.ArrayBuffer.empty[String]
    val current = new StringBuilder()
    while (!scanner.isEof && scanner.peek() != '\n' && scanner.peek() != ';')
      if (scanner.peek() == ',') {
        if (current.nonEmpty) {
          styles += current.toString.trim
          current.clear()
        }
        scanner.advance()
        scanner.skipWhitespace()
      } else {
        current.append(scanner.advance())
      }
    if (current.nonEmpty) {
      styles += current.toString.trim
    }
    styles
  }

  /** Reads text until newline. */
  private def readTextUntilNewline(scanner: Scanner): String = {
    val sb = new StringBuilder()
    while (!scanner.isEof && scanner.peek() != '\n' && scanner.peek() != ';')
      sb.append(scanner.advance())
    sb.toString
  }

  /** Reads until a string delimiter (non-consuming of the delimiter). */
  private def readUntilStr(scanner: Scanner, delimiter: String): String = {
    val sb = new StringBuilder()
    while (!scanner.isEof && !scanner.input.startsWith(delimiter, scanner.pos))
      sb.append(scanner.advance())
    // Consume delimiter
    if (!scanner.isEof) {
      var i = 0
      while (i < delimiter.length && !scanner.isEof) {
        scanner.advance()
        i += 1
      }
    }
    sb.toString.trim
  }

  /** Reads until a character (consuming the character). */
  private def readUntilChar(scanner: Scanner, ch: Char): String = {
    val sb = new StringBuilder()
    while (!scanner.isEof && scanner.peek() != ch)
      sb.append(scanner.advance())
    if (!scanner.isEof) scanner.advance() // consume
    sb.toString
  }

  /** Peeks at the current line without consuming. */
  private def peekLine(scanner: Scanner): String = {
    val saved = scanner.save()
    val sb    = new StringBuilder()
    while (!scanner.isEof && scanner.peek() != '\n')
      sb.append(scanner.advance())
    scanner.restore(saved)
    sb.toString
  }

  /** Checks if current position is at a word boundary. */
  private def isWordBoundary(scanner: Scanner): Boolean =
    scanner.isEof || scanner.peek() == ' ' || scanner.peek() == '\t' ||
      scanner.peek() == '\n' || scanner.peek() == ';' || scanner.peek() == ':' ||
      scanner.peek() == '{' || scanner.peek() == '['

  /** Skips to the next newline or EOF. */
  private def skipToNewline(scanner: Scanner): Unit = {
    while (!scanner.isEof && scanner.peek() != '\n')
      scanner.advance()
    if (!scanner.isEof) scanner.advance()
  }

  /** Skips to the end of the line, but stops before consuming a `}` character. This allows namespace blocks to properly detect their closing brace when classes are on the same line.
    */
  private def skipToNewlineOrBrace(scanner: Scanner): Unit = {
    while (!scanner.isEof && scanner.peek() != '\n' && scanner.peek() != '}')
      scanner.advance()
    if (!scanner.isEof && scanner.peek() == '\n') scanner.advance()
    // If we stopped at '}', don't consume it -- the namespace parser will handle it
  }
}
