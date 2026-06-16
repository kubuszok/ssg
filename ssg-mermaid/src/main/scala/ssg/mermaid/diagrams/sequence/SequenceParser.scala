/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/sequence/parser/sequenceDiagram.jison
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces JISON-generated parser with hand-written recursive descent parser
 *   Idiom: Scanner-based parsing with boundary/break; SequenceDb for accumulation
 *   Renames: sequenceDiagram.jison → SequenceParser
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package sequence

import lowlevel.Nullable
import ssg.mermaid.parse.{ DirectiveParser, ParseException, Scanner }

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

/** Hand-written parser for Mermaid sequence diagram syntax.
  *
  * Parses the grammar defined in `sequenceDiagram.jison`, producing a populated [[SequenceDb]].
  *
  * Supported syntax:
  *   - `sequenceDiagram` — header keyword
  *   - `participant A`, `actor B` — actor declarations
  *   - `participant A as Alice` — actor with alias
  *   - `A->>B: message` — solid arrow
  *   - `A-->>B: reply` — dotted arrow
  *   - `A->B: message`, `A-->B: reply` — open arrows
  *   - `A-xB: message`, `A--xB: fail` — cross arrows
  *   - `A-)B: message`, `A--)B: async` — point (async) arrows
  *   - `<<->>` / `<<-->>` — bidirectional arrows
  *   - `Note right of A: text`, `Note left of A: text`, `Note over A,B: text`
  *   - `loop`, `alt`/`else`, `opt`, `par`/`and`, `critical`/`option`, `break`, `rect`
  *   - `activate A`, `deactivate A`
  *   - `autonumber [start [step]]`, `autonumber off`
  *   - `create participant A`, `destroy A`
  *   - `box [color] [title]` ... `end`
  *   - `title text`, `accTitle: text`, `accDescr: text`
  */
object SequenceParser {

  /** Parses Mermaid sequence diagram source text into a [[SequenceDb]].
    *
    * @param input
    *   the raw Mermaid diagram text
    * @return
    *   a populated SequenceDb
    * @throws ParseException
    *   if the input cannot be parsed
    */
  def parse(input: String): SequenceDb =
    parse(input, new SequenceDb)

  /** Parses Mermaid sequence diagram source text into the supplied [[SequenceDb]].
    *
    * Mirrors `Diagram.fromText` holding the db across `setDiagramTitle` + `parser.parse`: the caller may pre-set a frontmatter title on the db, and the parser only overwrites it when an inline
    * `title` directive is present.
    *
    * @param input
    *   the raw Mermaid diagram text
    * @param db
    *   the db to parse into
    * @return
    *   the supplied SequenceDb, populated
    * @throws ParseException
    *   if the input cannot be parsed
    */
  def parse(input: String, db: SequenceDb): SequenceDb = {
    val cleaned = cleanInput(input)
    val scanner = new Scanner(cleaned)

    // Skip leading whitespace/newlines
    scanner.skipWhitespaceAndNewlines()

    // Parse diagram header: sequenceDiagram
    parseHeader(scanner)

    // Parse document body
    val actions = parseDocument(scanner, db)

    // Apply all actions to the database
    db.applyAll(actions)

    db
  }

  /** Removes directives and comments from input. */
  private def cleanInput(input: String): String = {
    var s = input
    // Remove %%{...}%% directives
    s = s.replaceAll("%%\\{[^}]*\\}%%", "")
    // Remove %% comments (but not %%{ directives)
    // re2 doesn't support (?!), so match %%[not-brace] instead
    s = s.replaceAll("%%[^{\\n][^\\n]*", "").replaceAll("%%$", "")
    // Remove single-line # comments (within lines, but not inside text)
    s
  }

  /** Parses the diagram header keyword. */
  private def parseHeader(scanner: Scanner): Unit = {
    scanner.skipWhitespaceAndNewlines()
    if (!scanner.matchStrIgnoreCase("sequenceDiagram")) {
      throw new ParseException("Expected 'sequenceDiagram' keyword", scanner.line, scanner.col)
    }
    skipToNewline(scanner)
  }

  /** Parses the document body (sequence of statements). */
  private def parseDocument(scanner: Scanner, db: SequenceDb): mutable.ArrayBuffer[SeqAction] = boundary {
    val actions = mutable.ArrayBuffer.empty[SeqAction]
    while (!scanner.isEof) {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof) break(actions)

      // Skip comments
      if (scanner.peek() == '#') {
        skipToNewline(scanner)
      } else if (scanner.peek() == ';') {
        scanner.advance()
      } else {
        val stmtActions = parseStatement(scanner, db)
        actions ++= stmtActions
      }
    }
    actions
  }

  /** Parses a single statement, returning zero or more actions. */
  private def parseStatement(scanner: Scanner, db: SequenceDb): mutable.ArrayBuffer[SeqAction] = boundary {
    val actions = mutable.ArrayBuffer.empty[SeqAction]
    scanner.skipWhitespace()
    if (scanner.isEof || scanner.peek() == '\n') break(actions)

    val saved = scanner.save()

    // Try keyword-based statements first
    if (tryParseParticipant(scanner, db, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseActor(scanner, db, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseCreate(scanner, db, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseDestroy(scanner, db, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseActivate(scanner, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseDeactivate(scanner, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseNote(scanner, db, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseLoop(scanner, db, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseRect(scanner, db, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseOpt(scanner, db, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseAlt(scanner, db, actions)) break(actions)
    scanner.restore(saved)

    if (tryParsePar(scanner, db, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseParOver(scanner, db, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseCritical(scanner, db, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseBreak(scanner, db, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseAutonumber(scanner, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseBox(scanner, db, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseTitle(scanner, db, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseAccTitle(scanner, db, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseAccDescr(scanner, db, actions)) break(actions)
    scanner.restore(saved)

    // Actor-metadata statements (sequenceDiagram.jison:248-274). `links` must be tried before
    // `link` because `link` is a prefix of `links`.
    if (tryParseLinks(scanner, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseLink(scanner, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseProperties(scanner, actions)) break(actions)
    scanner.restore(saved)

    if (tryParseDetails(scanner, actions)) break(actions)
    scanner.restore(saved)

    // Default: try to parse as a signal (actor -> actor : text)
    if (tryParseSignal(scanner, db, actions)) break(actions)
    scanner.restore(saved)

    // If nothing matched, the line matches no jison production. Upstream's generated parser raises a
    // parse error on such input (ISS-1067); previously SSG silently dropped the line via
    // skipToNewline. Raise a ParseException with the offending text instead.
    val offending = scanner.readToEndOfLine().trim
    throw new ParseException(s"Unrecognized sequence statement: '$offending'", scanner.line, scanner.col)
  }

  // --- Keyword parsers ---

  /** Tries to parse `participant ACTOR [as ALIAS]`. */
  private def tryParseParticipant(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("participant")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val actorId = readActorName(scanner)
    if (actorId.isEmpty) break(false)
    scanner.skipWhitespace()

    // Check for "as" alias
    val description = if (scanner.matchStrIgnoreCase("as")) {
      scanner.skipWhitespace()
      val (text, _) = readRestOfLine(scanner, db)
      Nullable(text)
    } else {
      Nullable.empty
    }

    actions += SeqAction.AddParticipant(actorId, description, "participant")
    skipToNewline(scanner)
    true
  }

  /** Tries to parse `actor ACTOR [as ALIAS]`. */
  private def tryParseActor(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("actor")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val actorId = readActorName(scanner)
    if (actorId.isEmpty) break(false)
    scanner.skipWhitespace()

    val description = if (scanner.matchStrIgnoreCase("as")) {
      scanner.skipWhitespace()
      val (text, _) = readRestOfLine(scanner, db)
      Nullable(text)
    } else {
      Nullable.empty
    }

    actions += SeqAction.AddParticipant(actorId, description, "actor")
    skipToNewline(scanner)
    true
  }

  /** Tries to parse `create participant/actor ACTOR`. */
  private def tryParseCreate(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("create")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val draw = if (scanner.matchStrIgnoreCase("participant")) {
      scanner.skipWhitespace()
      "participant"
    } else if (scanner.matchStrIgnoreCase("actor")) {
      scanner.skipWhitespace()
      "actor"
    } else {
      "participant"
    }

    val actorId = readActorName(scanner)
    if (actorId.isEmpty) break(false)
    scanner.skipWhitespace()

    val description = if (scanner.matchStrIgnoreCase("as")) {
      scanner.skipWhitespace()
      val (text, _) = readRestOfLine(scanner, db)
      Nullable(text)
    } else {
      Nullable.empty
    }

    actions += SeqAction.CreateParticipant(actorId, description, draw)
    skipToNewline(scanner)
    true
  }

  /** Tries to parse `destroy ACTOR`. */
  private def tryParseDestroy(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("destroy")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val actorId = readActorName(scanner)
    if (actorId.isEmpty) break(false)

    actions += SeqAction.DestroyParticipant(actorId)
    skipToNewline(scanner)
    true
  }

  /** Tries to parse `activate ACTOR`. */
  private def tryParseActivate(
    scanner: Scanner,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("activate")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val actorId = readActorName(scanner)
    if (actorId.isEmpty) break(false)

    actions += SeqAction.ActiveStart(actorId, LineType.ActiveStart)
    skipToNewline(scanner)
    true
  }

  /** Tries to parse `deactivate ACTOR`. */
  private def tryParseDeactivate(
    scanner: Scanner,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("deactivate")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val actorId = readActorName(scanner)
    if (actorId.isEmpty) break(false)

    actions += SeqAction.ActiveEnd(actorId, LineType.ActiveEnd)
    skipToNewline(scanner)
    true
  }

  /** Tries to parse note statements.
    *
    * `Note right of ACTOR: text` `Note left of ACTOR: text` `Note over ACTOR[,ACTOR]: text`
    */
  private def tryParseNote(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("note")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    // Determine placement
    val placement = if (scanner.matchStrIgnoreCase("right of")) {
      scanner.skipWhitespace()
      Placement.RightOf
    } else if (scanner.matchStrIgnoreCase("left of")) {
      scanner.skipWhitespace()
      Placement.LeftOf
    } else if (scanner.matchStrIgnoreCase("over")) {
      scanner.skipWhitespace()
      Placement.Over
    } else {
      break(false)
    }

    // Read actor(s)
    val actorIds   = mutable.ArrayBuffer.empty[String]
    val firstActor = readActorName(scanner)
    if (firstActor.isEmpty) break(false)
    actorIds += firstActor

    // For "over", there may be a comma-separated second actor
    if (placement == Placement.Over) {
      scanner.skipWhitespace()
      if (!scanner.isEof && scanner.peek() == ',') {
        scanner.advance()
        scanner.skipWhitespace()
        val secondActor = readActorName(scanner)
        if (secondActor.nonEmpty) {
          actorIds += secondActor
        }
      }
    }

    // Ensure we have the actor as a participant
    for (aid <- actorIds)
      actions += SeqAction.AddParticipant(aid, Nullable.empty, "participant")

    scanner.skipWhitespace()

    // Read the text after ":"
    if (!scanner.isEof && scanner.peek() == ':') {
      scanner.advance()
    }
    val (text, wrap) = readRestOfLine(scanner, db)

    actions += SeqAction.AddNote(actorIds.toArray, placement, text, wrap)
    skipToNewline(scanner)
    true
  }

  /** Reads the `actor text2` tail shared by the actor-metadata statements.
    *
    * Ports the `<keyword> actor text2` shape from sequenceDiagram.jison:248-274. The lexer's `text2` rule (jison:87,324-325) lexes `":"...` (the `TXT` token) then runs
    * `parseMessage($1.trim().substring(1))`, i.e. strips the leading `:` and trims. Here we read the actor name, skip the `:`, and return the trimmed remainder of the line as the raw text payload.
    *
    * @return
    *   `Some((actorId, text))` on success, or `None` if no actor/`:` is present
    */
  private def readActorAndText2(scanner: Scanner): Option[(String, String)] = boundary {
    val actorId = readActorName(scanner)
    if (actorId.isEmpty) break(None)
    scanner.skipWhitespace()

    // text2 begins with ":" (jison TXT rule, lexer line 87)
    if (scanner.isEof || scanner.peek() != ':') break(None)
    scanner.advance() // consume ":"
    val text = readTextUntilNewline(scanner).trim
    Some((actorId, text))
  }

  /** Tries to parse `links actor text2` (sequenceDiagram.jison:248-252).
    *
    * Emits `addLinks` (sequenceDb.ts:369-383): the text2 payload is JSON-parsed into a links map and merged into the actor's `links` field via `insertLinks` (sequenceDb.ts:409-417). JSON parsing
    * reuses [[DirectiveParser.parseSimpleObject]] for the simple `{"k":"v"}` object subset; upstream catches `JSON.parse` failure and continues without throwing, which is mirrored here because
    * `parseSimpleObject` returns an empty map (no links added) on malformed input.
    */
  private def tryParseLinks(
    scanner: Scanner,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("links")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    readActorAndText2(scanner) match {
      case Some((actorId, text)) =>
        // jison:249 — `actor` production registers the participant (addParticipant) before addLinks.
        actions += SeqAction.AddParticipant(actorId, Nullable.empty, "participant")
        actions += SeqAction.AddLinks(actorId, parseLinksMap(text))
        skipToNewline(scanner)
        true
      case None => break(false)
    }
  }

  /** Tries to parse `link actor text2` (sequenceDiagram.jison:255-259).
    *
    * Emits `addALink` (sequenceDb.ts:385-403): a single `label @ url` pair. Upstream finds the `@` separator (`indexOf('@')`), takes `slice(0, sep - 1).trim()` as the label and
    * `slice(sep + 1).trim()` as the link, then merges via `insertLinks`. Ported faithfully below.
    */
  private def tryParseLink(
    scanner: Scanner,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("link")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    readActorAndText2(scanner) match {
      case Some((actorId, text)) =>
        // jison:256 — `actor` production registers the participant (addParticipant) before addALink.
        actions += SeqAction.AddParticipant(actorId, Nullable.empty, "participant")
        actions += SeqAction.AddLinks(actorId, parseALinkMap(text))
        skipToNewline(scanner)
        true
      case None => break(false)
    }
  }

  /** Tries to parse `properties actor text2` (sequenceDiagram.jison:262-266).
    *
    * Emits `addProperties` (sequenceDb.ts:419-431): the text2 payload is JSON-parsed into a properties map and merged into the actor's `properties` field via `insertProperties`
    * (sequenceDb.ts:437-445). Same `parseSimpleObject` reuse and silent-on-failure behavior as [[tryParseLinks]].
    */
  private def tryParseProperties(
    scanner: Scanner,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("properties")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    readActorAndText2(scanner) match {
      case Some((actorId, text)) =>
        // jison:263 — `actor` production registers the participant (addParticipant) before addProperties.
        actions += SeqAction.AddParticipant(actorId, Nullable.empty, "participant")
        actions += SeqAction.AddProperties(actorId, parseLinksMap(text))
        skipToNewline(scanner)
        true
      case None => break(false)
    }
  }

  /** Tries to parse `details actor text2` (sequenceDiagram.jison:269-273).
    *
    * Emits `addDetails` (sequenceDb.ts:451-471). Upstream resolves `text2` as a DOM element id and JSON-parses that element's `innerHTML` into `{ properties, links }`, merging each sub-object into
    * the actor via `insertProperties`/`insertLinks`. SSG is a headless port with no DOM, so the inline `text2` payload is treated as the details JSON directly (the SSG substitute for the DOM
    * indirection); its `properties` and `links` sub-objects are parsed and merged, faithful to the merge semantics of sequenceDb.ts:461-467.
    */
  private def tryParseDetails(
    scanner: Scanner,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("details")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    readActorAndText2(scanner) match {
      case Some((actorId, text)) =>
        // jison:270 — `actor` production registers the participant (addParticipant) before addDetails.
        actions += SeqAction.AddParticipant(actorId, Nullable.empty, "participant")
        val (props, links) = parseDetailsMaps(text)
        // sequenceDb.ts:461-467 — merge details.properties then details.links into the actor.
        if (props.nonEmpty) actions += SeqAction.AddProperties(actorId, props)
        if (links.nonEmpty) actions += SeqAction.AddLinks(actorId, links)
        skipToNewline(scanner)
        true
      case None => break(false)
    }
  }

  /** Parses a simple `{"k":"v"}` JSON object into a links/properties map.
    *
    * Reuses [[DirectiveParser.parseSimpleObject]] (the existing JSON-object parser used for `%%{init}%%` directives). Mirrors sequenceDb.ts JSON.parse + insertLinks/insertProperties: on malformed
    * input `parseSimpleObject` yields an empty map, matching upstream's catch-and-continue (sequenceDb.ts:380-382, 428-430) which adds nothing.
    */
  private def parseLinksMap(text: String): mutable.LinkedHashMap[String, String] = {
    val result = mutable.LinkedHashMap.empty[String, String]
    for ((k, v) <- DirectiveParser.parseSimpleObject(text))
      result(k) = v
    result
  }

  /** Parses a single `label @ url` pair (sequenceDb.ts:388-399).
    *
    * Faithful port of `addALink`'s body: find `@` via `indexOf`, label = `slice(0, sep - 1).trim()`, link = `slice(sep + 1).trim()`. When no `@` is present (`indexOf` returns -1) upstream still
    * builds `{ label: link }` from the slices; here a missing `@` yields an empty map (no link added), matching the catch-and-continue effect for input that is not a valid `label @ url` pair.
    */
  private def parseALinkMap(text: String): mutable.LinkedHashMap[String, String] = {
    val result = mutable.LinkedHashMap.empty[String, String]
    val sep    = text.indexOf('@')
    if (sep >= 1) {
      val label = text.substring(0, sep - 1).trim
      val link  = text.substring(sep + 1).trim
      result(label) = link
    }
    result
  }

  /** Parses a `details` payload `{"properties": {...}, "links": {...}}` into (properties, links).
    *
    * Faithful to sequenceDb.ts:457-467: JSON-parse the details object, then read its `properties` and `links` sub-objects (`details.properties`, `details.links`). Because the existing
    * [[DirectiveParser.parseSimpleObject]] handles only a single level of `{"k":"v"}` and does not descend into nested objects, the `properties` and `links` sub-objects are first extracted as
    * brace-balanced substrings, then each is parsed via `parseSimpleObject`.
    */
  private def parseDetailsMaps(
    text: String
  ): (mutable.LinkedHashMap[String, String], mutable.LinkedHashMap[String, String]) = {
    val props = extractBraceObject(text, "properties").map(parseLinksMap).getOrElse(mutable.LinkedHashMap.empty)
    val links = extractBraceObject(text, "links").map(parseLinksMap).getOrElse(mutable.LinkedHashMap.empty)
    (props, links)
  }

  /** Extracts the brace-balanced `{...}` value of `"key": {...}` from a JSON-like object string.
    *
    * Supports the single level of nesting that the `details` payload uses (an outer object whose `properties`/`links` values are themselves objects). Returns the inner object substring including its
    * braces, or `None` if the key or a balanced object is not present.
    */
  private def extractBraceObject(text: String, key: String): Option[String] = boundary {
    // Locate the quoted key, then the ':' and the opening '{'.
    val keyToken = "\"" + key + "\""
    val keyIdx   = text.indexOf(keyToken)
    if (keyIdx < 0) break(None)

    var i = keyIdx + keyToken.length
    while (i < text.length && text.charAt(i) != '{' && text.charAt(i) != ',' && text.charAt(i) != '}')
      i += 1
    if (i >= text.length || text.charAt(i) != '{') break(None)

    // Scan forward tracking brace depth to find the matching close brace.
    val start = i
    var depth = 0
    while (i < text.length) {
      val c = text.charAt(i)
      if (c == '{') depth += 1
      else if (c == '}') {
        depth -= 1
        if (depth == 0) break(Some(text.substring(start, i + 1)))
      }
      i += 1
    }
    None
  }

  /** Tries to parse `loop TEXT` ... `end`. */
  private def tryParseLoop(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("loop")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val (text, wrap) = readRestOfLine(scanner, db)
    skipToNewline(scanner)

    val bodyActions = parseUntilEnd(scanner, db)

    actions += SeqAction.LoopStart(text, LineType.LoopStart, wrap)
    actions ++= bodyActions
    actions += SeqAction.LoopEnd(LineType.LoopEnd)
    true
  }

  /** Tries to parse `rect COLOR` ... `end`. */
  private def tryParseRect(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("rect")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val (color, _) = readRestOfLine(scanner, db)
    skipToNewline(scanner)

    val bodyActions = parseUntilEnd(scanner, db)

    actions += SeqAction.RectStart(color, LineType.RectStart)
    actions ++= bodyActions
    actions += SeqAction.RectEnd(LineType.RectEnd)
    true
  }

  /** Tries to parse `opt TEXT` ... `end`. */
  private def tryParseOpt(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("opt")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val (text, wrap) = readRestOfLine(scanner, db)
    skipToNewline(scanner)

    val bodyActions = parseUntilEnd(scanner, db)

    actions += SeqAction.OptStart(text, LineType.OptStart, wrap)
    actions ++= bodyActions
    actions += SeqAction.OptEnd(LineType.OptEnd)
    true
  }

  /** Tries to parse `alt TEXT` ... `else TEXT` ... `end`. */
  private def tryParseAlt(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("alt")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val (text, wrap) = readRestOfLine(scanner, db)
    skipToNewline(scanner)

    actions += SeqAction.AltStart(text, LineType.AltStart, wrap)
    parseAltElseSections(scanner, db, actions)
    actions += SeqAction.AltEnd(LineType.AltEnd)
    true
  }

  /** Parses alt/else sections until 'end'. */
  private def parseAltElseSections(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Unit = boundary {
    boundary[Unit] {
      while (!scanner.isEof) {
        scanner.skipWhitespaceAndNewlines()
        if (scanner.isEof) break(())

        if (matchEndKeyword(scanner)) break(())

        val saved = scanner.save()
        if (scanner.matchStrIgnoreCase("else")) {
          if (isWordBoundary(scanner)) {
            scanner.skipWhitespace()
            val (text, wrap) = readRestOfLine(scanner, db)
            skipToNewline(scanner)
            actions += SeqAction.AltElse(text, LineType.AltElse, wrap)
          } else {
            scanner.restore(saved)
            val stmtActions = parseStatement(scanner, db)
            actions ++= stmtActions
          }
        } else {
          scanner.restore(saved)
          val stmtActions = parseStatement(scanner, db)
          actions ++= stmtActions
        }
      }
    }
  }

  /** Tries to parse `par TEXT` ... `and TEXT` ... `end`. */
  private def tryParsePar(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("par")) break(false)
    // Must not match "par_over"
    if (!scanner.isEof && scanner.peek() == '_') break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val (text, wrap) = readRestOfLine(scanner, db)
    skipToNewline(scanner)

    actions += SeqAction.ParStart(text, LineType.ParStart, wrap)
    parseParAndSections(scanner, db, actions)
    actions += SeqAction.ParEnd(LineType.ParEnd)
    true
  }

  /** Tries to parse `par_over TEXT` ... `and TEXT` ... `end`. */
  private def tryParseParOver(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("par_over")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val (text, wrap) = readRestOfLine(scanner, db)
    skipToNewline(scanner)

    actions += SeqAction.ParStart(text, LineType.ParOverStart, wrap)
    parseParAndSections(scanner, db, actions)
    actions += SeqAction.ParEnd(LineType.ParEnd)
    true
  }

  /** Parses par/and sections until 'end'. */
  private def parseParAndSections(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Unit = boundary {
    boundary[Unit] {
      while (!scanner.isEof) {
        scanner.skipWhitespaceAndNewlines()
        if (scanner.isEof) break(())

        if (matchEndKeyword(scanner)) break(())

        val saved = scanner.save()
        if (scanner.matchStrIgnoreCase("and")) {
          if (isWordBoundary(scanner)) {
            scanner.skipWhitespace()
            val (text, wrap) = readRestOfLine(scanner, db)
            skipToNewline(scanner)
            actions += SeqAction.ParAnd(text, LineType.ParAnd, wrap)
          } else {
            scanner.restore(saved)
            val stmtActions = parseStatement(scanner, db)
            actions ++= stmtActions
          }
        } else {
          scanner.restore(saved)
          val stmtActions = parseStatement(scanner, db)
          actions ++= stmtActions
        }
      }
    }
  }

  /** Tries to parse `critical TEXT` ... `option TEXT` ... `end`. */
  private def tryParseCritical(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("critical")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val (text, wrap) = readRestOfLine(scanner, db)
    skipToNewline(scanner)

    actions += SeqAction.CriticalStart(text, LineType.CriticalStart, wrap)
    parseCriticalOptionSections(scanner, db, actions)
    actions += SeqAction.CriticalEnd(LineType.CriticalEnd)
    true
  }

  /** Parses critical/option sections until 'end'. */
  private def parseCriticalOptionSections(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Unit = boundary {
    boundary[Unit] {
      while (!scanner.isEof) {
        scanner.skipWhitespaceAndNewlines()
        if (scanner.isEof) break(())

        if (matchEndKeyword(scanner)) break(())

        val saved = scanner.save()
        if (scanner.matchStrIgnoreCase("option")) {
          if (isWordBoundary(scanner)) {
            scanner.skipWhitespace()
            val (text, wrap) = readRestOfLine(scanner, db)
            skipToNewline(scanner)
            actions += SeqAction.CriticalOption(text, LineType.CriticalOption, wrap)
          } else {
            scanner.restore(saved)
            val stmtActions = parseStatement(scanner, db)
            actions ++= stmtActions
          }
        } else {
          scanner.restore(saved)
          val stmtActions = parseStatement(scanner, db)
          actions ++= stmtActions
        }
      }
    }
  }

  /** Tries to parse `break TEXT` ... `end`. */
  private def tryParseBreak(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("break")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val (text, wrap) = readRestOfLine(scanner, db)
    skipToNewline(scanner)

    val bodyActions = parseUntilEnd(scanner, db)

    actions += SeqAction.BreakStart(text, LineType.BreakStart, wrap)
    actions ++= bodyActions
    actions += SeqAction.BreakEnd(LineType.BreakEnd)
    true
  }

  /** Tries to parse `autonumber [start [step]]` or `autonumber off`. */
  private def tryParseAutonumber(
    scanner: Scanner,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("autonumber")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    if (scanner.isEof || scanner.peek() == '\n' || scanner.peek() == ';') {
      // autonumber with defaults
      actions += SeqAction.SequenceIndex(1, 1, visible = true, LineType.Autonumber)
    } else if (scanner.matchStrIgnoreCase("off")) {
      actions += SeqAction.SequenceIndex(1, 1, visible = false, LineType.Autonumber)
    } else {
      // Read start number
      val startStr = readNumber(scanner)
      val start    = if (startStr.nonEmpty) startStr.toInt else 1
      scanner.skipWhitespace()

      // Optionally read step
      val stepStr = readNumber(scanner)
      val step    = if (stepStr.nonEmpty) stepStr.toInt else 1

      actions += SeqAction.SequenceIndex(start, step, visible = true, LineType.Autonumber)
    }

    skipToNewline(scanner)
    true
  }

  /** Tries to parse `box [COLOR] [TITLE]` ... `end`. */
  private def tryParseBox(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    if (!scanner.matchStrIgnoreCase("box")) break(false)
    if (!isWordBoundary(scanner)) break(false)
    scanner.skipWhitespace()

    val restOfLine = readTextUntilNewline(scanner).trim
    skipToNewline(scanner)

    val (text, color, wrap) = db.parseBoxData(restOfLine)

    actions += SeqAction.BoxStart(text.getOrElse(""), color, wrap)

    // Parse box body: only participant/actor statements
    boundary[Unit] {
      while (!scanner.isEof) {
        scanner.skipWhitespaceAndNewlines()
        if (scanner.isEof) break(())
        if (matchEndKeyword(scanner)) break(())

        // Inside box, parse participant/actor statements
        val stmtActions = parseStatement(scanner, db)
        actions ++= stmtActions
      }
    }

    actions += SeqAction.BoxEnd
    true
  }

  /** Tries to parse `title TEXT` or `title: TEXT`. */
  private def tryParseTitle(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    val saved = scanner.save()
    if (scanner.matchStrIgnoreCase("title")) {
      if (scanner.isEof || scanner.peek() == ' ' || scanner.peek() == ':' || scanner.peek() == '\n') {
        if (!scanner.isEof && scanner.peek() == ':') scanner.advance()
        scanner.skipWhitespace()
        val text = readTextUntilNewline(scanner).trim
        if (text.nonEmpty) {
          actions += SeqAction.SetTitle(text)
          skipToNewline(scanner)
          break(true)
        }
      }
    }
    scanner.restore(saved)
    false
  }

  /** Tries to parse `accTitle: TEXT`. */
  private def tryParseAccTitle(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    val saved = scanner.save()
    if (scanner.matchStrIgnoreCase("accTitle")) {
      scanner.skipWhitespace()
      if (!scanner.isEof && scanner.peek() == ':') {
        scanner.advance()
        scanner.skipWhitespace()
        val text = readTextUntilNewline(scanner).trim
        actions += SeqAction.SetAccTitle(text)
        skipToNewline(scanner)
        break(true)
      }
    }
    scanner.restore(saved)
    false
  }

  /** Tries to parse `accDescr: TEXT` or `accDescr { TEXT }`. */
  private def tryParseAccDescr(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    val saved = scanner.save()
    if (scanner.matchStrIgnoreCase("accDescr")) {
      scanner.skipWhitespace()
      if (!scanner.isEof && scanner.peek() == ':') {
        scanner.advance()
        scanner.skipWhitespace()
        val text = readTextUntilNewline(scanner).trim
        actions += SeqAction.SetAccDescription(text)
        skipToNewline(scanner)
        break(true)
      } else if (!scanner.isEof && scanner.peek() == '{') {
        scanner.advance()
        val content = scanner.readUntil('}')
        actions += SeqAction.SetAccDescription(content.trim)
        skipToNewline(scanner)
        break(true)
      }
    }
    scanner.restore(saved)
    false
  }

  /** Tries to parse a signal: `ACTOR signalType [+/-] ACTOR : TEXT`. */
  private def tryParseSignal(
    scanner: Scanner,
    db:      SequenceDb,
    actions: mutable.ArrayBuffer[SeqAction]
  ): Boolean = boundary {
    val saved = scanner.save()
    scanner.skipWhitespace()

    // Read source actor
    val from = readActorName(scanner)
    if (from.isEmpty) {
      scanner.restore(saved)
      break(false)
    }

    scanner.skipWhitespace()

    // Try to read signal type
    val signalType = tryReadSignalType(scanner)
    if (signalType < 0) {
      scanner.restore(saved)
      break(false)
    }

    // Check for activation modifiers (+ or -)
    var activateTarget   = false
    var deactivateSource = false
    scanner.skipWhitespace()
    if (!scanner.isEof && scanner.peek() == '+') {
      scanner.advance()
      activateTarget = true
    } else if (!scanner.isEof && scanner.peek() == '-') {
      scanner.advance()
      deactivateSource = true
    }

    scanner.skipWhitespace()

    // Read target actor
    val to = readActorNameBeforeColon(scanner)
    if (to.isEmpty) {
      scanner.restore(saved)
      break(false)
    }

    scanner.skipWhitespace()

    // Read message text after ':'
    var messageText = ""
    if (!scanner.isEof && scanner.peek() == ':') {
      scanner.advance()
      val (text, _) = readRestOfLine(scanner, db)
      messageText = text
    }

    // Add implicit participant declarations
    actions += SeqAction.AddParticipant(from, Nullable.empty, "participant")
    actions += SeqAction.AddParticipant(to, Nullable.empty, "participant")

    // Add the message
    actions += SeqAction.AddMessage(from, to, messageText, signalType, activateTarget)

    // Handle activation/deactivation modifiers
    if (activateTarget) {
      actions += SeqAction.ActiveStart(to, LineType.ActiveStart)
    }
    if (deactivateSource) {
      actions += SeqAction.ActiveEnd(from, LineType.ActiveEnd)
    }

    skipToNewline(scanner)
    true
  }

  // --- Signal type parsing ---

  /** Tries to read a signal type at the current position.
    *
    * @return
    *   the LineType value, or -1 if not a signal type
    */
  private def tryReadSignalType(scanner: Scanner): Int = boundary {
    val saved = scanner.save()

    // Bidirectional arrows first (longer patterns)
    if (scanner.matchStr("<<-->>")) break(LineType.BidirectionalDotted)
    scanner.restore(saved)
    if (scanner.matchStr("<<->>")) break(LineType.BidirectionalSolid)
    scanner.restore(saved)

    // Standard arrows
    if (scanner.matchStr("-->>")) break(LineType.Dotted)
    scanner.restore(saved)
    if (scanner.matchStr("->>")) break(LineType.Solid)
    scanner.restore(saved)

    if (scanner.matchStr("--x")) break(LineType.DottedCross)
    scanner.restore(saved)
    if (scanner.matchStr("-x")) break(LineType.SolidCross)
    scanner.restore(saved)

    if (scanner.matchStr("--)")) break(LineType.DottedPoint)
    scanner.restore(saved)
    if (scanner.matchStr("-)")) break(LineType.SolidPoint)
    scanner.restore(saved)

    if (scanner.matchStr("-->")) break(LineType.DottedOpen)
    scanner.restore(saved)
    if (scanner.matchStr("->")) break(LineType.SolidOpen)
    scanner.restore(saved)

    -1
  }

  // --- Helpers ---

  /** Parses statements until 'end' keyword, returning all collected actions. */
  private def parseUntilEnd(
    scanner: Scanner,
    db:      SequenceDb
  ): mutable.ArrayBuffer[SeqAction] = boundary {
    val actions = mutable.ArrayBuffer.empty[SeqAction]
    boundary[mutable.ArrayBuffer[SeqAction]] {
      while (!scanner.isEof) {
        scanner.skipWhitespaceAndNewlines()
        if (scanner.isEof) break(actions)
        if (matchEndKeyword(scanner)) break(actions)

        if (scanner.peek() == '#') {
          skipToNewline(scanner)
        } else if (scanner.peek() == ';') {
          scanner.advance()
        } else {
          val stmtActions = parseStatement(scanner, db)
          actions ++= stmtActions
        }
      }
      actions
    }
  }

  /** Checks if current position matches 'end' keyword with word boundary. */
  private def matchEndKeyword(scanner: Scanner): Boolean = boundary {
    val saved = scanner.save()
    if (scanner.matchStrIgnoreCase("end")) {
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

  /** Checks if current position is at a word boundary. */
  private def isWordBoundary(scanner: Scanner): Boolean =
    scanner.isEof || scanner.peek() == ' ' || scanner.peek() == '\t' ||
      scanner.peek() == '\n' || scanner.peek() == ';' || scanner.peek() == ':'

  /** Reads an actor name (sequence of non-arrow, non-colon, non-newline, non-space chars).
    *
    * Actor names are typically single tokens like `Alice`, `Bob`, `A`, etc. In the original JISON grammar, actor names in the ID state use complex lookahead to handle "as" aliases. Here we simplify
    * by stopping at whitespace.
    */
  private def readActorName(scanner: Scanner): String = boundary {
    val sb = new StringBuilder()
    while (!scanner.isEof) {
      val c = scanner.peek()
      // Stop at arrow chars, colon, newline, semicolon, comma, space/tab
      if (
        c == '-' || c == '<' || c == '>' || c == ':' || c == '\n' ||
        c == ';' || c == ',' || c == '+' || c == ' ' || c == '\t'
      ) {
        break(sb.toString.trim)
      }
      sb.append(scanner.advance())
    }
    sb.toString.trim
  }

  /** Reads an actor name stopping at colon (for signal targets). */
  private def readActorNameBeforeColon(scanner: Scanner): String = boundary {
    val sb = new StringBuilder()
    while (!scanner.isEof) {
      val c = scanner.peek()
      if (c == ':' || c == '\n' || c == ';') {
        break(sb.toString.trim)
      }
      sb.append(scanner.advance())
    }
    sb.toString.trim
  }

  /** Reads the rest of the line, extracting wrap directives.
    *
    * @return
    *   (text, wrap)
    */
  private def readRestOfLine(scanner: Scanner, db: SequenceDb): (String, Boolean) = {
    val raw = readTextUntilNewline(scanner).trim
    db.parseMessage(raw)
  }

  /** Reads text until newline, semicolon, or comment. */
  private def readTextUntilNewline(scanner: Scanner): String = boundary {
    val sb = new StringBuilder()
    while (!scanner.isEof && scanner.peek() != '\n' && scanner.peek() != ';') {
      if (scanner.peek() == '#') {
        // Comment - skip the rest
        while (!scanner.isEof && scanner.peek() != '\n') scanner.advance()
        break(sb.toString)
      }
      sb.append(scanner.advance())
    }
    sb.toString
  }

  /** Reads a numeric string. */
  private def readNumber(scanner: Scanner): String = {
    val sb = new StringBuilder()
    while (!scanner.isEof && scanner.peek().isDigit)
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
