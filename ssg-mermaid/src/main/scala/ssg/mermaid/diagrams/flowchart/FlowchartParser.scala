/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/flowchart/parser/flow.jison
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces JISON-generated parser with hand-written recursive descent parser
 *   Idiom: Scanner-based parsing with boundary/break; FlowchartDb for accumulation
 *   Renames: flow.jison → FlowchartParser
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package flowchart

import lowlevel.Nullable
import ssg.mermaid.parse.{ ParseException, Scanner }

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

/** Hand-written parser for Mermaid flowchart/graph syntax.
  *
  * Parses the grammar defined in `flow.jison`, producing a populated [[FlowchartDb]].
  *
  * Supported syntax:
  *   - `graph TD`, `graph LR`, `flowchart TB`, etc. — graph header with direction
  *   - `A[text]`, `B(text)`, `C{text}`, `D((text))`, etc. — node declarations with shapes
  *   - `A --> B`, `A --- B`, `A -.-> B`, `A ==> B` — edge declarations
  *   - `A -->|label| B`, `A -- text --> B` — labeled edges
  *   - `subgraph title` ... `end` — subgraphs
  *   - `classDef`, `class`, `style`, `linkStyle` — styling
  *   - `click` — interactivity (parsed but not functional server-side)
  */
object FlowchartParser {

  /** Parses Mermaid flowchart source text into a [[FlowchartDb]].
    *
    * @param input
    *   the raw Mermaid diagram text
    * @return
    *   a populated FlowchartDb
    * @throws ParseException
    *   if the input cannot be parsed
    */
  def parse(input: String): FlowchartDb =
    parse(input, new FlowchartDb)

  /** Parses Mermaid flowchart source text into a pre-configured [[FlowchartDb]].
    *
    * This overload allows the caller to set configuration-driven fields (e.g. `maxEdges`) on the Db before parsing populates it with edges. Mirrors the upstream pattern where `flowDb.ts:20` reads
    * `config = getConfig()` at module scope so that `addSingleLink` (line 148) sees the configured limit.
    *
    * @param input
    *   the raw Mermaid diagram text
    * @param db
    *   a pre-configured FlowchartDb instance
    * @return
    *   the same FlowchartDb, now populated
    * @throws ParseException
    *   if the input cannot be parsed
    */
  def parse(input: String, db: FlowchartDb): FlowchartDb = {
    val cleaned = cleanInput(input)
    val scanner = new Scanner(cleaned)

    // Skip leading whitespace/newlines
    scanner.skipWhitespaceAndNewlines()

    // Parse graph config (graph/flowchart keyword + direction)
    parseGraphConfig(scanner, db)

    // Parse document (statements)
    parseDocument(scanner, db)

    db
  }

  /** Removes directives, comments, and front matter from input. */
  private def cleanInput(input: String): String = {
    var s = input
    // Remove %%{...}%% directives
    s = s.replaceAll("%%\\{[^}]*\\}%%", "")
    // Remove %% comments (to end of line)
    s = s.replaceAll("%%[^\n]*", "")
    s
  }

  /** Parses the graph configuration header.
    *
    * Matches: `graph [DIR]`, `flowchart [DIR]`, `flowchart-elk [DIR]`
    */
  private def parseGraphConfig(scanner: Scanner, db: FlowchartDb): Unit = {
    scanner.skipWhitespaceAndNewlines()

    // Match keyword
    if (scanner.matchStrIgnoreCase("flowchart-elk")) {
      // flowchart-elk variant
    } else if (scanner.matchStrIgnoreCase("flowchart")) {
      // flowchart variant
    } else if (scanner.matchStrIgnoreCase("graph")) {
      // graph variant
    } else {
      throw new ParseException("Expected 'graph' or 'flowchart' keyword", scanner.line, scanner.col)
    }

    scanner.skipWhitespace()

    // Parse direction
    if (scanner.isEof || scanner.peek() == '\n') {
      // No direction specified, default to TB
      db.setDirection("TB")
    } else {
      val dir = readDirection(scanner)
      if (dir.nonEmpty) {
        db.setDirection(dir)
      } else {
        db.setDirection("TB")
      }
    }

    // Skip to end of line
    skipToNewline(scanner)
  }

  /** Reads a direction token (LR, RL, TB, BT, TD, BR, <, >, ^, v). */
  private def readDirection(scanner: Scanner): String = boundary {
    scanner.skipWhitespace()
    if (scanner.isEof) break("")

    // Try two-letter directions first
    val twoChar = if (scanner.pos + 1 < scanner.input.length) {
      scanner.input.substring(scanner.pos, scanner.pos + 2).toUpperCase
    } else ""

    twoChar match {
      case "LR" | "RL" | "TB" | "BT" | "TD" | "BR" =>
        scanner.advance()
        scanner.advance()
        twoChar
      case _ =>
        // Single char directions
        scanner.peek() match {
          case '<' => scanner.advance(); "<"
          case '>' => scanner.advance(); ">"
          case '^' => scanner.advance(); "^"
          case 'v' => scanner.advance(); "v"
          case _   => ""
        }
    }
  }

  /** Parses the document body (sequence of statements). */
  private def parseDocument(scanner: Scanner, db: FlowchartDb): Unit = boundary {
    while (!scanner.isEof) {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof) break()

      // Skip comments
      if (scanner.peek() == '%' && scanner.peekAt(1) == '%') {
        skipToNewline(scanner)
      } else if (scanner.peek() == ';') {
        scanner.advance()
      } else {
        parseStatement(scanner, db)
      }
    }
  }

  /** Parses a single statement. */
  private def parseStatement(scanner: Scanner, db: FlowchartDb): Unit = boundary {
    scanner.skipWhitespace()
    if (scanner.isEof || scanner.peek() == '\n') break()

    val saved = scanner.save()

    // Try to match keywords first
    if (tryParseSubgraph(scanner, db)) break()
    if (matchEndKeyword(scanner)) break()
    if (tryParseDirection(scanner, db)) break()
    if (tryParseClassDef(scanner, db)) break()
    if (tryParseClassStatement(scanner, db)) break()
    if (tryParseStyleStatement(scanner, db)) break()
    if (tryParseLinkStyleStatement(scanner, db)) break()
    if (tryParseClick(scanner, db)) break()
    if (tryParseAccTitle(scanner, db)) break()
    if (tryParseAccDescr(scanner, db)) break()

    // Default: vertex statement (node with possible chained edges)
    scanner.restore(saved)
    parseVertexStatement(scanner, db)
  }

  /** Tries to parse a subgraph block. Returns true if matched. */
  private def tryParseSubgraph(scanner: Scanner, db: FlowchartDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("subgraph")) {
      break(false)
    }

    // Must be followed by whitespace or newline
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t' && scanner.peek() != '\n') {
      scanner.restore(saved)
      break(false)
    }

    scanner.skipWhitespace()

    // Parse subgraph title/id
    val titleText = if (scanner.isEof || scanner.peek() == '\n') {
      ""
    } else {
      readTextUntilNewline(scanner).trim
    }

    // Extract id and title: "id [title]" or just "title"
    val (sgId, sgTitle) = if (titleText.contains("[")) {
      val idx          = titleText.indexOf("[")
      val id           = titleText.substring(0, idx).trim
      val bracketTitle = titleText.substring(idx + 1).stripSuffix("]").trim
      (Nullable(id), bracketTitle)
    } else {
      (Nullable(titleText), titleText)
    }

    skipToNewline(scanner)

    // Parse subgraph body — collect new node IDs during body parsing
    val nodeIds = mutable.ArrayBuffer.empty[String]
    parseSubgraphBody(scanner, db, nodeIds)

    db.addSubgraph(sgId, nodeIds.toArray, sgTitle)

    true
  }

  /** Parses the body of a subgraph until "end" keyword. */
  private def parseSubgraphBody(scanner: Scanner, db: FlowchartDb, nodeIds: mutable.ArrayBuffer[String]): Unit = boundary {
    boundary[Unit] {
      while (!scanner.isEof) {
        scanner.skipWhitespaceAndNewlines()
        if (scanner.isEof) break(())

        // Check for "end" keyword
        if (matchEndKeyword(scanner)) {
          break(())
        }

        // Skip comments and semicolons
        if (scanner.peek() == '%' && scanner.peekAt(1) == '%') {
          skipToNewline(scanner)
        } else if (scanner.peek() == ';') {
          scanner.advance()
        } else {
          // Parse statement and collect node IDs
          val prevNodeCount = db.nodes.size
          parseStatement(scanner, db)
          // Collect any new nodes added
          val newNodes = db.nodes.keys.toArray.drop(prevNodeCount)
          nodeIds ++= newNodes
        }
      }
    }
  }

  /** Checks if the current position matches the "end" keyword (word boundary). */
  private def matchEndKeyword(scanner: Scanner): Boolean = boundary {
    val saved = scanner.save()
    if (scanner.matchStrIgnoreCase("end")) {
      // Must be followed by whitespace, newline, semicolon, or EOF
      if (
        scanner.isEof || scanner.peek() == ' ' || scanner.peek() == '\t' ||
        scanner.peek() == '\n' || scanner.peek() == ';'
      ) {
        scanner.skipWhitespace()
        break(true)
      }
    }
    scanner.restore(saved)
    false
  }

  /** Tries to parse a direction statement (direction TB/BT/RL/LR). */
  private def tryParseDirection(scanner: Scanner, db: FlowchartDb): Boolean = boundary {
    val saved = scanner.save()
    if (scanner.matchStrIgnoreCase("direction")) {
      scanner.skipWhitespace()
      val dir = readDirection(scanner)
      if (dir.nonEmpty) {
        skipToNewline(scanner)
        break(true)
      }
    }
    scanner.restore(saved)
    false
  }

  /** Tries to parse a classDef statement. */
  private def tryParseClassDef(scanner: Scanner, db: FlowchartDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("classDef")) {
      break(false)
    }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()
    val className = readIdString(scanner)
    scanner.skipWhitespace()
    val styles = readStyles(scanner)
    db.addClass(className, styles.toArray)
    skipToNewline(scanner)
    true
  }

  /** Tries to parse a class statement. */
  private def tryParseClassStatement(scanner: Scanner, db: FlowchartDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("class")) {
      break(false)
    }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()
    val nodeIds = readIdString(scanner)
    scanner.skipWhitespace()
    val className = readIdString(scanner)
    db.setClass(nodeIds, className)
    skipToNewline(scanner)
    true
  }

  /** Tries to parse a style statement. */
  private def tryParseStyleStatement(scanner: Scanner, db: FlowchartDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStr("style")) {
      break(false)
    }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()
    val nodeIds = readIdString(scanner)
    scanner.skipWhitespace()
    val styles = readStyles(scanner)
    db.setStyle(nodeIds, styles.toArray)
    skipToNewline(scanner)
    true
  }

  /** Tries to parse a linkStyle statement. */
  private def tryParseLinkStyleStatement(scanner: Scanner, db: FlowchartDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("linkStyle")) {
      break(false)
    }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()

    // Parse positions (numbers or "default")
    val positions = mutable.ArrayBuffer.empty[Int]
    if (scanner.matchStrIgnoreCase("default")) {
      positions += -1
    } else {
      val nums = readNumList(scanner)
      positions ++= nums
    }

    scanner.skipWhitespace()

    // Check for interpolate keyword
    if (scanner.matchStrIgnoreCase("interpolate")) {
      scanner.skipWhitespace()
      val interpType = readIdString(scanner)
      db.updateLinkInterpolate(positions.toArray, interpType)
      scanner.skipWhitespace()
    }

    // Read styles if present
    if (!scanner.isEof && scanner.peek() != '\n' && scanner.peek() != ';') {
      val styles = readStyles(scanner)
      if (styles.nonEmpty) {
        db.updateLink(positions.toArray, styles.toArray)
      }
    }

    skipToNewline(scanner)
    true
  }

  /** Tries to parse a click statement.
    *
    * Ports the `clickStatement` grammar rule from `flow.jison:485-500`. After the `click` keyword the lexer (`flow.jison:80-82`) captures the node id as the `CLICK` token (everything up to the next
    * whitespace/newline). The remaining tokens select between the link and callback forms:
    *
    *   - `CLICK HREF STR [SPACE STR] [SPACE LINK_TARGET]` (flow.jison:490-493) and the bare-string forms `CLICK STR [SPACE STR] [SPACE LINK_TARGET]` (flow.jison:496-499) → `setLink` (+ `setTooltip`
    *     when a second STR is present). Both produce static `<a href>` anchors and are fully renderable server-side.
    *   - `CLICK CALLBACKNAME [CALLBACKARGS] [SPACE STR]` (flow.jison:486-489) and `CLICK alphaNum [SPACE STR]` (flow.jison:494-495) → `setClickEvent` (+ `setTooltip`). Callbacks attach browser-side
    *     behaviour only; the server-side `setClickEvent` faithfully just marks the node clickable and records the tooltip — there is no JS execution to perform here.
    */
  private def tryParseClick(scanner: Scanner, db: FlowchartDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("click")) {
      break(false)
    }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }
    scanner.skipWhitespace()

    // flow.jison:82 — `<click>[^\s\n]*` captures the node id (CLICK token).
    val id = readClickId(scanner)
    if (id.isEmpty) {
      // Malformed input — skip gracefully, matching the surrounding parser's tolerance.
      skipToNewline(scanner)
      break(true)
    }
    scanner.skipWhitespace()

    // flow.jison:71 — HREF = the literal `href` followed by whitespace.
    val sawHref =
      if (scanner.matchStrIgnoreCase("href")) {
        if (!scanner.isEof && (scanner.peek() == ' ' || scanner.peek() == '\t')) {
          scanner.skipWhitespace()
          true
        } else {
          // `href` not followed by whitespace is not the HREF token — treat the
          // identifier as a callback name (alphaNum form).
          db.setClickEvent(id, "href")
          skipToNewline(scanner)
          break(true)
        }
      } else {
        false
      }

    if (sawHref) {
      // flow.jison:490-493 — CLICK HREF STR [SPACE STR] [SPACE LINK_TARGET]
      parseClickLink(scanner, db, id)
      break(true)
    }

    if (!scanner.isEof && scanner.peek() == '"') {
      // flow.jison:496-499 — bare-string forms CLICK STR [SPACE STR] [SPACE LINK_TARGET]
      parseClickLink(scanner, db, id)
      break(true)
    }

    // flow.jison:486-489 (callback forms) / :494-495 (alphaNum forms) → setClickEvent.
    parseClickCallback(scanner, db, id)
    true
  }

  /** Reads the node id captured as the `CLICK` token (flow.jison:82).
    *
    * The id is everything up to the next whitespace or newline; quoted segments are read whole so an id is never split inside a string.
    */
  private def readClickId(scanner: Scanner): String = {
    val sb = new StringBuilder()
    while (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t' && scanner.peek() != '\n' && scanner.peek() != '\r')
      if (scanner.peek() == '"') {
        sb.append(scanner.readQuotedString())
      } else {
        sb.append(scanner.advance())
      }
    sb.toString
  }

  /** Parses the shared link tail `STR [SPACE STR] [SPACE LINK_TARGET]`.
    *
    * Ports flow.jison:490-493 (after HREF) and :496-499 (bare-string). The first STR is the link, an optional second STR is the tooltip, and an optional LINK_TARGET sets the anchor target.
    */
  private def parseClickLink(scanner: Scanner, db: FlowchartDb, id: String): Unit =
    if (scanner.isEof || scanner.peek() != '"') {
      // No STR present — nothing renderable; skip gracefully.
      skipToNewline(scanner)
    } else {
      val link = scanner.readQuotedString()
      scanner.skipWhitespace()

      // Optional second STR → tooltip (flow.jison:491,493,497,499).
      val tooltip =
        if (!scanner.isEof && scanner.peek() == '"') {
          val t = scanner.readQuotedString()
          scanner.skipWhitespace()
          Nullable(t)
        } else {
          Nullable.empty[String]
        }

      // Optional LINK_TARGET (flow.jison:492,493,498,499).
      val target = readLinkTarget(scanner)

      target match {
        case t if t.isDefined => db.setLink(id, link, t.get)
        case _                => db.setLink(id, link)
      }
      tooltip.foreach(t => db.setTooltip(id, t))

      skipToNewline(scanner)
    }

  /** Parses the callback forms (flow.jison:486-489, :494-495).
    *
    * `CALLBACKNAME [CALLBACKARGS] [SPACE STR]` and the `alphaNum [SPACE STR]` forms both resolve to `setClickEvent` plus an optional tooltip STR.
    */
  private def parseClickCallback(scanner: Scanner, db: FlowchartDb, id: String): Unit = {
    // Read the callback name up to whitespace, newline, or the `(` that opens args.
    val nameSb = new StringBuilder()
    while (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t' && scanner.peek() != '\n' && scanner.peek() != '\r' && scanner.peek() != '(' && scanner.peek() != '"')
      nameSb.append(scanner.advance())
    val name = nameSb.toString

    // Optional CALLBACKARGS — `(...)` (flow.jison:48-50,488-489).
    val args =
      if (!scanner.isEof && scanner.peek() == '(') {
        scanner.advance() // consume '('
        val argsSb = new StringBuilder()
        while (!scanner.isEof && scanner.peek() != ')' && scanner.peek() != '\n')
          argsSb.append(scanner.advance())
        if (!scanner.isEof && scanner.peek() == ')') {
          scanner.advance() // consume ')'
        }
        argsSb.toString
      } else {
        ""
      }

    if (name.isEmpty) {
      skipToNewline(scanner)
    } else {
      if (args.nonEmpty) {
        db.setClickEvent(id, name, args)
      } else {
        db.setClickEvent(id, name)
      }

      // Optional tooltip STR (flow.jison:487,489,495).
      scanner.skipWhitespace()
      if (!scanner.isEof && scanner.peek() == '"') {
        val tooltip = scanner.readQuotedString()
        db.setTooltip(id, tooltip)
      }

      skipToNewline(scanner)
    }
  }

  /** Reads an optional LINK_TARGET token (flow.jison:90-93).
    *
    * Recognizes exactly `_self`, `_blank`, `_parent`, `_top`. Returns the matched target, or empty when the next token is not a link target (the scanner is left unchanged in that case).
    */
  private def readLinkTarget(scanner: Scanner): Nullable[String] = {
    val saved   = scanner.save()
    val targets = Array("_self", "_blank", "_parent", "_top")
    var result  = Nullable.empty[String]
    var i       = 0
    while (i < targets.length && result.isEmpty) {
      val t = targets(i)
      if (scanner.matchStr(t)) {
        // Must be followed by a token boundary, not part of a longer word.
        if (scanner.isEof || scanner.peek() == ' ' || scanner.peek() == '\t' || scanner.peek() == '\n' || scanner.peek() == '\r' || scanner.peek() == ';') {
          result = Nullable(t)
        } else {
          scanner.restore(saved)
        }
      }
      i += 1
    }
    result
  }

  /** Tries to parse accTitle. */
  private def tryParseAccTitle(scanner: Scanner, db: FlowchartDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("accTitle")) {
      break(false)
    }
    scanner.skipWhitespace()
    if (!scanner.isEof && scanner.peek() == ':') {
      scanner.advance()
      scanner.skipWhitespace()
      db.accTitle = readTextUntilNewline(scanner).trim
      break(true)
    }
    scanner.restore(saved)
    false
  }

  /** Tries to parse accDescr. */
  private def tryParseAccDescr(scanner: Scanner, db: FlowchartDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("accDescr")) {
      break(false)
    }
    scanner.skipWhitespace()
    if (!scanner.isEof && scanner.peek() == ':') {
      scanner.advance()
      scanner.skipWhitespace()
      db.accDescription = readTextUntilNewline(scanner).trim
      break(true)
    } else if (!scanner.isEof && scanner.peek() == '{') {
      scanner.advance()
      val content = scanner.readUntil('}')
      db.accDescription = content.trim
      break(true)
    }
    scanner.restore(saved)
    false
  }

  /** Parses a vertex statement: node definition possibly chained with edges.
    *
    * Grammar: `vertexStatement: vertexStatement link node | node`
    */
  private def parseVertexStatement(scanner: Scanner, db: FlowchartDb): Unit = boundary {
    // Parse first node
    val firstNodes = parseNode(scanner, db)
    if (firstNodes.isEmpty) {
      // Could not parse a node — skip to newline
      skipToNewline(scanner)
      break()
    }

    scanner.skipWhitespace()

    // Chain: node [link node]*
    var currentNodes = firstNodes
    while (!scanner.isEof && scanner.peek() != '\n' && scanner.peek() != ';') {
      scanner.skipWhitespace()
      if (scanner.isEof || scanner.peek() == '\n' || scanner.peek() == ';') {
        break()
      }

      // Try to parse a link
      val linkResult = tryParseLink(scanner, db)
      if (linkResult.isEmpty) {
        // Not a link — done with this statement
        break()
      }
      val (linkInfo, labelText, labelType) = linkResult.get

      scanner.skipWhitespace()

      // Parse next node
      val nextNodes = parseNode(scanner, db)
      if (nextNodes.isEmpty) {
        break()
      }

      // Add edges from current to next
      db.addLink(currentNodes.toArray, nextNodes.toArray, linkInfo, labelText, labelType)
      currentNodes = nextNodes
    }
  }

  /** Parses a node (possibly with `&` for multiple nodes).
    *
    * Grammar: `node: styledVertex | node & styledVertex`
    *
    * @return
    *   list of node IDs
    */
  private def parseNode(scanner: Scanner, db: FlowchartDb): mutable.ArrayBuffer[String] = boundary {
    val nodeIds = mutable.ArrayBuffer.empty[String]

    boundary[mutable.ArrayBuffer[String]] {
      val firstId = parseStyledVertex(scanner, db)
      if (firstId.isEmpty) break(nodeIds)
      nodeIds += firstId

      // Check for & (multiple nodes)
      while (!scanner.isEof) {
        scanner.skipWhitespace()
        if (!scanner.isEof && scanner.peek() == '&') {
          scanner.advance()
          scanner.skipWhitespace()
          val nextId = parseStyledVertex(scanner, db)
          if (nextId.nonEmpty) {
            nodeIds += nextId
          }
        } else {
          break(nodeIds)
        }
      }
      nodeIds
    }
  }

  /** Parses a styled vertex: `vertex [:::className]`.
    *
    * @return
    *   node ID, or empty string if not parseable
    */
  private def parseStyledVertex(scanner: Scanner, db: FlowchartDb): String = boundary {
    val nodeId = parseVertex(scanner, db)
    if (nodeId.isEmpty) break("")

    // Check for ::: style separator
    if (!scanner.isEof && scanner.matchStr(":::")) {
      val className = readIdString(scanner)
      if (className.nonEmpty) {
        db.setClass(nodeId, className)
      }
    }

    nodeId
  }

  /** Parses a single vertex (node) definition.
    *
    * Handles all shape brackets: `[text]`, `(text)`, `{text}`, `((text))`, `([text])`, `[[text]]`, `[(text)]`, `[/text/]`, `[\text\]`, `[/text\]`, `[\text/]`, `{{text}}`, `>text]`, `(((text)))`,
    * `(-text-)`
    *
    * Also handles markdown strings via `"` quoting — text inside double quotes within shape brackets is treated as markdown (labelType = "markdown").
    *
    * @return
    *   the node ID
    */
  private def parseVertex(scanner: Scanner, db: FlowchartDb): String = boundary {
    // Read the ID
    val id = readIdString(scanner)
    if (id.isEmpty) break("")

    // Check what follows — determines shape
    if (
      scanner.isEof || scanner.peek() == ' ' || scanner.peek() == '\t' ||
      scanner.peek() == '\n' || scanner.peek() == ';' || scanner.peek() == '&'
    ) {
      // Bare node, no shape brackets
      db.addNode(id)
      break(id)
    }

    // Try each shape pattern
    if (tryParseShape(scanner, db, id)) {
      break(id)
    }

    // No shape matched — just a bare node
    db.addNode(id)
    id
  }

  /** Tries to parse a shape following a node ID. Returns true if a shape was parsed. */
  private def tryParseShape(scanner: Scanner, db: FlowchartDb, id: String): Boolean = boundary {
    val saved = scanner.save()

    val c = scanner.peek()
    c match {
      case '[' =>
        scanner.advance()
        // Check for multi-char openers: [[ ]], [( )], [/ /], [\ \], [|
        if (!scanner.isEof) {
          scanner.peek() match {
            case '[' =>
              // [[ ]] — subroutine
              scanner.advance()
              val text = readUntilClosing(scanner, "]]")
              db.addNode(id, Nullable(text), shape = Nullable("subroutine"))
              break(true)
            case '(' =>
              // [( )] — cylinder
              scanner.advance()
              val text = readUntilClosing(scanner, ")]")
              db.addNode(id, Nullable(text), shape = Nullable("cylinder"))
              break(true)
            case '/' =>
              // [/ /] or [/ \]
              scanner.advance()
              val text = readShapeText(scanner)
              if (!scanner.isEof) {
                if (scanner.peek() == '/' && scanner.peekAt(1) == ']') {
                  scanner.advance(); scanner.advance()
                  db.addNode(id, Nullable(text), shape = Nullable("lean_right"))
                  break(true)
                } else if (scanner.peek() == '\\' && scanner.peekAt(1) == ']') {
                  scanner.advance(); scanner.advance()
                  db.addNode(id, Nullable(text), shape = Nullable("trapezoid"))
                  break(true)
                }
              }
              scanner.restore(saved)
              break(false)
            case '\\' =>
              // [\ \] or [\ /]
              scanner.advance()
              val text = readShapeText(scanner)
              if (!scanner.isEof) {
                if (scanner.peek() == '\\' && scanner.peekAt(1) == ']') {
                  scanner.advance(); scanner.advance()
                  db.addNode(id, Nullable(text), shape = Nullable("lean_left"))
                  break(true)
                } else if (scanner.peek() == '/' && scanner.peekAt(1) == ']') {
                  scanner.advance(); scanner.advance()
                  db.addNode(id, Nullable(text), shape = Nullable("inv_trapezoid"))
                  break(true)
                }
              }
              scanner.restore(saved)
              break(false)
            case _ =>
              // [ ] — square (rect), with possible markdown string
              val (text, textType) = readUntilClosingWithType(scanner, "]")
              db.addNode(id, Nullable(text), textType = textType, shape = Nullable("square"))
              break(true)
          }
        }
        scanner.restore(saved)
        false

      case '(' =>
        scanner.advance()
        if (!scanner.isEof) {
          scanner.peek() match {
            case '(' =>
              scanner.advance()
              if (!scanner.isEof && scanner.peek() == '(') {
                // ((( ))) — doublecircle
                scanner.advance()
                val text = readUntilClosing(scanner, ")))")
                db.addNode(id, Nullable(text), shape = Nullable("doublecircle"))
                break(true)
              } else {
                // (( )) — circle
                val text = readUntilClosing(scanner, "))")
                db.addNode(id, Nullable(text), shape = Nullable("circle"))
                break(true)
              }
            case '[' =>
              // ([ ]) — stadium
              scanner.advance()
              val text = readUntilClosing(scanner, "])")
              db.addNode(id, Nullable(text), shape = Nullable("stadium"))
              break(true)
            case '-' =>
              // (- -) — ellipse
              scanner.advance()
              val text = readUntilClosing(scanner, "-)")
              db.addNode(id, Nullable(text), shape = Nullable("ellipse"))
              break(true)
            case _ =>
              // ( ) — round, with possible markdown string
              val (text, textType) = readUntilClosingWithType(scanner, ")")
              db.addNode(id, Nullable(text), textType = textType, shape = Nullable("round"))
              break(true)
          }
        }
        scanner.restore(saved)
        false

      case '{' =>
        scanner.advance()
        if (!scanner.isEof && scanner.peek() == '{') {
          // {{ }} — hexagon
          scanner.advance()
          val text = readUntilClosing(scanner, "}}")
          db.addNode(id, Nullable(text), shape = Nullable("hexagon"))
          true
        } else {
          // { } — diamond
          val text = readUntilClosing(scanner, "}")
          db.addNode(id, Nullable(text), shape = Nullable("diamond"))
          true
        }

      case '>' =>
        // > ] — odd/flag/asymmetric
        scanner.advance()
        val text = readUntilClosing(scanner, "]")
        db.addNode(id, Nullable(text), shape = Nullable("odd"))
        true

      case _ =>
        false
    }
  }

  /** Reads text content inside shape brackets until the closing delimiter.
    *
    * @return
    *   the text content (trimmed)
    */
  private def readUntilClosing(scanner: Scanner, closing: String): String = {
    val sb = new StringBuilder()
    while (!scanner.isEof && !scanner.input.startsWith(closing, scanner.pos))
      sb.append(scanner.advance())
    // Consume closing
    if (!scanner.isEof) {
      var i = 0
      while (i < closing.length && !scanner.isEof) {
        scanner.advance()
        i += 1
      }
    }
    sb.toString.trim
  }

  /** Reads text content inside shape brackets until the closing delimiter, detecting markdown strings (text wrapped in `"` quotes).
    *
    * If the content starts with `"`, the text is treated as markdown: the inner text (without quotes) is returned and isMarkdown is set to true.
    *
    * @return
    *   (text, textType) where textType is "markdown" or "text"
    */
  private def readUntilClosingWithType(scanner: Scanner, closing: String): (String, String) = {
    // Peek ahead to see if content starts with a double quote (markdown)
    val isMarkdown = !scanner.isEof && scanner.peek() == '"'
    if (isMarkdown) {
      scanner.advance() // consume opening quote
      val sb = new StringBuilder()
      // Read until closing quote
      while (!scanner.isEof && scanner.peek() != '"')
        sb.append(scanner.advance())
      if (!scanner.isEof) {
        scanner.advance() // consume closing quote
      }
      // Now consume the closing delimiter
      if (!scanner.isEof && scanner.input.startsWith(closing, scanner.pos)) {
        var i = 0
        while (i < closing.length && !scanner.isEof) {
          scanner.advance()
          i += 1
        }
      }
      (sb.toString.trim, "markdown")
    } else {
      (readUntilClosing(scanner, closing), "text")
    }
  }

  /** Reads text for trapezoid/parallelogram shapes (until / or \ followed by ]). */
  private def readShapeText(scanner: Scanner): String = boundary {
    val sb = new StringBuilder()
    while (!scanner.isEof) {
      val c = scanner.peek()
      if ((c == '/' || c == '\\') && scanner.peekAt(1) == ']') {
        break(sb.toString.trim)
      }
      sb.append(scanner.advance())
    }
    sb.toString.trim
  }

  /** Tries to parse a link (edge) between nodes.
    *
    * @return
    *   Some((linkInfo, labelText, labelType)) if a link was parsed
    */
  private def tryParseLink(scanner: Scanner, db: FlowchartDb): Nullable[(FlowLinkInfo, String, String)] = boundary {
    val saved = scanner.save()
    scanner.skipWhitespace()

    if (scanner.isEof) {
      scanner.restore(saved)
      break(Nullable.empty)
    }

    // Collect the edge token(s)
    val result = parseLinkToken(scanner, db)
    if (result.isEmpty) {
      scanner.restore(saved)
      break(Nullable.empty)
    }

    result
  }

  /** Parses a link token, recognizing all edge types.
    *
    * Handles: `-->`, `---`, `-.->`, `==>`, and variants with labels (pipe syntax `|text|` and inline text).
    */
  private def parseLinkToken(scanner: Scanner, db: FlowchartDb): Nullable[(FlowLinkInfo, String, String)] = boundary {
    val saved = scanner.save()
    scanner.skipWhitespace()

    if (scanner.isEof) break(Nullable.empty)

    // Look for edge patterns using peek
    val edgeStr = readEdgeToken(scanner)
    if (edgeStr.isEmpty) {
      scanner.restore(saved)
      break(Nullable.empty)
    }

    // Check for label in pipe syntax: -->|text|
    scanner.skipWhitespace()
    var labelText = ""
    var labelType = "text"

    if (!scanner.isEof && scanner.peek() == '|') {
      scanner.advance()
      val labelContent = readUntilChar(scanner, '|')
      labelText = labelContent.trim
      labelType = "text"
    }

    // Parse the edge string to get type/stroke/length
    val linkInfo = parseEdgeString(edgeStr)
    if (linkInfo.edgeType == "INVALID") {
      scanner.restore(saved)
      break(Nullable.empty)
    }

    Nullable((linkInfo, labelText, labelType))
  }

  /** Reads an edge token string (the raw arrow characters).
    *
    * Recognizes patterns like: `-->`, `---`, `-.->`, `--->`, `==>`, `===`, `~~~ `, `<-->`, `x--x`, `o--o`, `--text-->`
    */
  private def readEdgeToken(scanner: Scanner): String = boundary {
    val sb    = new StringBuilder()
    val saved = scanner.save()

    // Collect characters that are part of an edge: -, =, ., >, <, x, o, ~
    var foundEdgeChar = false

    while (!scanner.isEof) {
      val c = scanner.peek()
      if (c == '-' || c == '=' || c == '.' || c == '>' || c == 'x' || c == 'o' || c == '<' || c == '~') {
        sb.append(scanner.advance())
        foundEdgeChar = true
      } else if (foundEdgeChar && c == ' ') {
        // Might be end of edge or start of inline text
        // Check if there's more edge chars after space (inline text: -- text -->)
        val peekAhead = peekForEdgeEnd(scanner)
        if (peekAhead) {
          // There's inline text between edge parts — read it
          scanner.advance() // consume space
          sb.append(' ')
          // Read text until we find edge end
          while (!scanner.isEof && !isEdgeEnd(scanner))
            sb.append(scanner.advance())
        } else {
          // End of edge
          break(sb.toString.trim)
        }
      } else {
        // Not an edge character — stop
        if (foundEdgeChar) {
          break(sb.toString.trim)
        } else {
          scanner.restore(saved)
          break("")
        }
      }
    }

    if (foundEdgeChar) sb.toString.trim else { scanner.restore(saved); "" }
  }

  /** Peeks ahead to see if there's an edge ending after possible inline text. */
  private def peekForEdgeEnd(scanner: Scanner): Boolean = {
    // Quick check: is there a --> or ==> or -.-> etc. ahead?
    val remaining = scanner.remaining
    // Look for patterns that end an edge
    remaining.matches("(?s).*(-{2,}>|={2,}>|\\.-+>|-{2,}[xo]|={2,}[xo]).*")
  }

  /** Checks if current position is at an edge end pattern (>, x, o preceded by - or = or .). */
  private def isEdgeEnd(scanner: Scanner): Boolean = boundary {
    if (scanner.isEof) break(true)
    val c = scanner.peek()
    (c == '>' || c == 'x' || c == 'o') && scanner.pos > 0 && {
      val prev = scanner.input.charAt(scanner.pos - 1)
      prev == '-' || prev == '=' || prev == '.'
    }
  }

  /** Parses a raw edge string into a FlowLinkInfo.
    *
    * Examples: `-->` -> arrow_point/normal, `==>` -> arrow_point/thick, `-.->` -> arrow_point/dotted
    */
  private def parseEdgeString(edge: String): FlowLinkInfo = boundary {
    val trimmed = edge.trim
    if (trimmed.isEmpty) break(FlowLinkInfo(edgeType = "INVALID", stroke = "INVALID"))

    // Handle invisible links (~~~)
    if (trimmed.matches("~+")) {
      break(FlowLinkInfo(edgeType = "arrow_open", stroke = "invisible", length = 1))
    }

    // Check for inline text (text between edge markers)
    // e.g., "-- text -->" contains text
    val inlineTextPattern = "^([xo<]?)(-{2}|={2}|\\.-)\\s+(.+?)\\s*(-{1,}>|={1,}>|\\.-+>|-{1,}[xo]|={1,}[xo])$".r
    inlineTextPattern.findFirstMatchIn(trimmed) match {
      case Some(m) =>
        // This is handled at a higher level — we just need the edge type
        val startPart = m.group(1) + m.group(2)
        val endPart   = m.group(4)
        break(parseSimpleEdge(startPart + endPart))
      case None => ()
    }

    parseSimpleEdge(trimmed)
  }

  /** Parses a simple edge string (no inline text). */
  private def parseSimpleEdge(edge: String): FlowLinkInfo = boundary {
    val e = edge.trim
    if (e.isEmpty) break(FlowLinkInfo(edgeType = "INVALID", stroke = "INVALID"))

    // Determine stroke type
    val stroke =
      if (e.contains("=")) "thick"
      else if (e.contains(".")) "dotted"
      else "normal"

    // Determine arrow type by looking at end
    val last     = e.last
    val first    = e.head
    var edgeType = "arrow_open"

    // End marker
    last match {
      case '>'                                                                                     => edgeType = "arrow_point"
      case 'x' if e.length > 1 && (e.charAt(e.length - 2) == '-' || e.charAt(e.length - 2) == '=') =>
        edgeType = "arrow_cross"
      case 'o' if e.length > 1 && (e.charAt(e.length - 2) == '-' || e.charAt(e.length - 2) == '=') =>
        edgeType = "arrow_circle"
      case _ => ()
    }

    // Start marker doubles the arrow
    first match {
      case '<' if edgeType == "arrow_point"  => edgeType = "double_arrow_point"
      case 'x' if edgeType == "arrow_cross"  => edgeType = "double_arrow_cross"
      case 'o' if edgeType == "arrow_circle" => edgeType = "double_arrow_circle"
      case _                                 => ()
    }

    // Calculate length: count the number of main chars (- or = or .)
    val mainChars = e.count(c => c == '-' || c == '=' || c == '.')
    val length    = math.max(1, mainChars - 1)

    FlowLinkInfo(edgeType = edgeType, stroke = stroke, length = length)
  }

  /** Reads an ID string (alphanumeric + special chars).
    *
    * Stops at characters that could start an edge (`-`, `=`, `.` followed by edge-forming chars) to avoid consuming arrow tokens as part of the ID.
    */
  private def readIdString(scanner: Scanner): String = boundary {
    val sb = new StringBuilder()
    while (!scanner.isEof) {
      val c = scanner.peek()
      if (c == '-') {
        // Stop if this looks like the start of an edge (-- or -> or -.)
        val next = scanner.peekAt(1)
        if (next == '-' || next == '>' || next == '.') {
          break(sb.toString)
        }
        // A standalone - (e.g., in identifiers like "node-1") is fine
        sb.append(scanner.advance())
      } else if (c == '=') {
        // Stop if this looks like a thick edge (== or =>)
        val next = scanner.peekAt(1)
        if (next == '=' || next == '>') {
          break(sb.toString)
        }
        sb.append(scanner.advance())
      } else if (c == '.') {
        // Stop if this looks like a dotted edge (-.)
        val next = scanner.peekAt(1)
        if (next == '-' || next == '>') {
          break(sb.toString)
        }
        sb.append(scanner.advance())
      } else if (c == '<' || c == '~') {
        // These always start an edge
        break(sb.toString)
      } else if (
        c.isLetterOrDigit || c == '_' || c == '!' || c == '#' ||
        c == '$' || c == '%' || c == '+' || c == '`' || c == '?' || c == '\\' || c == '/' ||
        c == '&' || c == '*' || c == '\'' || c == ',' || c == ':'
      ) {
        sb.append(scanner.advance())
      } else if (c == '"') {
        // Quoted string as part of ID — read the whole quoted string
        sb.append(scanner.readQuotedString())
      } else {
        break(sb.toString)
      }
    }
    sb.toString
  }

  /** Reads a comma-separated list of numbers. */
  private def readNumList(scanner: Scanner): mutable.ArrayBuffer[Int] = {
    val nums   = mutable.ArrayBuffer.empty[Int]
    val numStr = new StringBuilder()
    while (!scanner.isEof && (scanner.peek().isDigit || scanner.peek() == ','))
      if (scanner.peek() == ',') {
        if (numStr.nonEmpty) {
          nums += numStr.toString.toInt
          numStr.clear()
        }
        scanner.advance()
        scanner.skipWhitespace()
      } else {
        numStr.append(scanner.advance())
      }
    if (numStr.nonEmpty) {
      nums += numStr.toString.toInt
    }
    nums
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

  /** Reads text until a specific character. */
  private def readUntilChar(scanner: Scanner, terminator: Char): String = {
    val sb = new StringBuilder()
    while (!scanner.isEof && scanner.peek() != terminator)
      sb.append(scanner.advance())
    if (!scanner.isEof) scanner.advance() // consume terminator
    sb.toString
  }

  /** Reads text until newline or EOF. */
  private def readTextUntilNewline(scanner: Scanner): String = {
    val sb = new StringBuilder()
    while (!scanner.isEof && scanner.peek() != '\n' && scanner.peek() != ';')
      sb.append(scanner.advance())
    sb.toString
  }

  /** Skips to the next newline or EOF. */
  private def skipToNewline(scanner: Scanner): Unit = {
    while (!scanner.isEof && scanner.peek() != '\n')
      scanner.advance()
    if (!scanner.isEof) scanner.advance() // consume newline
  }
}
