/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/git/parser/gitGraph.jison
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces JISON-generated parser with hand-written recursive descent parser
 *   Idiom: Scanner-based parsing with boundary/break; GitDb for accumulation
 *   Renames: gitGraph.jison -> GitParser
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package git

import lowlevel.Nullable
import ssg.mermaid.parse.{ ParseException, Scanner }

import scala.util.boundary
import scala.util.boundary.break

/** Hand-written parser for Mermaid git graph syntax.
  *
  * Supported syntax:
  *   - `gitGraph` — diagram header
  *   - `commit` — create a commit
  *   - `commit id: "abc"` — commit with explicit ID
  *   - `commit msg: "message"` — commit with message
  *   - `commit tag: "v1.0"` — commit with tag
  *   - `commit type: NORMAL|REVERSE|HIGHLIGHT` — commit with type
  *   - `branch name` — create a branch
  *   - `branch name order: N` — create a branch with display order
  *   - `checkout name` — switch to a branch
  *   - `merge name` — merge a branch into current
  *   - `merge name id: "abc" tag: "v1.0"` — merge with options
  *   - `cherry-pick id: "abc"` — cherry-pick a commit
  */
object GitParser {

  /** Parses Mermaid git graph source text into a [[GitDb]].
    *
    * @param input
    *   the raw Mermaid diagram text
    * @return
    *   a populated GitDb
    * @throws ParseException
    *   if the input cannot be parsed
    */
  def parse(input: String): GitDb = {
    val db      = new GitDb
    val cleaned = cleanInput(input)
    val scanner = new Scanner(cleaned)

    scanner.skipWhitespaceAndNewlines()
    parseHeader(scanner, db)
    db.init()
    parseBody(scanner, db)

    db
  }

  /** Removes directives and comments from input. */
  private def cleanInput(input: String): String = {
    var s = input
    s = s.replaceAll("%%\\{[^}]*\\}%%", "")
    s = s.replaceAll("%%[^\n]*", "")
    s
  }

  /** Parses the gitGraph keyword and optional direction. */
  private def parseHeader(scanner: Scanner, db: GitDb): Unit = {
    scanner.skipWhitespaceAndNewlines()
    if (!scanner.matchStrIgnoreCase("gitGraph")) {
      throw new ParseException("Expected 'gitGraph' keyword", scanner.line, scanner.col)
    }

    scanner.skipWhitespace()

    // Optional direction
    if (!scanner.isEof && scanner.peek() != '\n') {
      val dir = readIdentifier(scanner)
      if (dir.nonEmpty) {
        db.direction = dir.toUpperCase
      }
    }

    skipToNewline(scanner)
  }

  /** Parses the body: commit, branch, checkout, merge, cherry-pick statements. */
  private def parseBody(scanner: Scanner, db: GitDb): Unit = boundary {
    while (!scanner.isEof) {
      scanner.skipWhitespaceAndNewlines()
      if (scanner.isEof) break()

      if (scanner.peek() == '%' && scanner.peekAt(1) == '%') {
        skipToNewline(scanner)
      } else if (scanner.peek() == ';') {
        scanner.advance()
      } else {
        parseLine(scanner, db)
      }
    }
  }

  /** Parses a single line/statement. */
  private def parseLine(scanner: Scanner, db: GitDb): Unit = boundary {
    scanner.skipWhitespace()
    if (scanner.isEof || scanner.peek() == '\n') break()

    if (tryParseCommit(scanner, db)) break()
    if (tryParseBranch(scanner, db)) break()
    if (tryParseCheckout(scanner, db)) break()
    if (tryParseMerge(scanner, db)) break()
    if (tryParseCherryPick(scanner, db)) break()
    if (tryParseAccTitle(scanner, db)) break()
    if (tryParseAccDescr(scanner, db)) break()

    // Unknown — skip
    skipToNewline(scanner)
  }

  /** Tries to parse a `commit` statement. */
  private def tryParseCommit(scanner: Scanner, db: GitDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("commit")) break(false)
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t' && scanner.peek() != '\n' && scanner.peek() != ';') {
      scanner.restore(saved)
      break(false)
    }

    scanner.skipWhitespace()

    // Parse optional attributes: id: "xxx", msg: "xxx", tag: "xxx", type: NORMAL
    var commitId: Nullable[String] = Nullable.empty
    var commitMsg = ""
    var commitTag: Nullable[String] = Nullable.empty
    var commitType = CommitType.Normal

    while (!scanner.isEof && scanner.peek() != '\n' && scanner.peek() != ';') {
      scanner.skipWhitespace()
      if (scanner.isEof || scanner.peek() == '\n' || scanner.peek() == ';') {
        // done
      } else if (scanner.matchStrIgnoreCase("id:")) {
        scanner.skipWhitespace()
        commitId = Nullable(readQuotedOrWord(scanner))
      } else if (scanner.matchStrIgnoreCase("msg:")) {
        scanner.skipWhitespace()
        commitMsg = readQuotedOrWord(scanner)
      } else if (scanner.matchStrIgnoreCase("tag:")) {
        scanner.skipWhitespace()
        commitTag = Nullable(readQuotedOrWord(scanner))
      } else if (scanner.matchStrIgnoreCase("type:")) {
        scanner.skipWhitespace()
        val typeName = readIdentifier(scanner).toUpperCase
        commitType = typeName match {
          case "REVERSE"   => CommitType.Reverse
          case "HIGHLIGHT" => CommitType.Highlight
          case _           => CommitType.Normal
        }
      } else {
        // Unknown attribute — skip
        scanner.advance()
      }
    }

    db.commit(message = commitMsg, id = commitId, tag = commitTag, commitType = commitType)
    true
  }

  /** Tries to parse a `branch` statement. */
  private def tryParseBranch(scanner: Scanner, db: GitDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("branch")) break(false)
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }

    scanner.skipWhitespace()
    val branchName = readIdentifier(scanner)
    if (branchName.isEmpty) {
      scanner.restore(saved)
      break(false)
    }

    scanner.skipWhitespace()

    // Optional: order: N
    var order = -1
    if (scanner.matchStrIgnoreCase("order:")) {
      scanner.skipWhitespace()
      val numStr = readIdentifier(scanner)
      try
        order = numStr.toInt
      catch {
        case _: NumberFormatException => ()
      }
    }

    db.branch(branchName, order)
    skipToNewline(scanner)
    true
  }

  /** Tries to parse a `checkout` or `switch` statement. */
  private def tryParseCheckout(scanner: Scanner, db: GitDb): Boolean = boundary {
    val saved   = scanner.save()
    val matched = scanner.matchStrIgnoreCase("checkout") || scanner.matchStrIgnoreCase("switch")
    if (!matched) {
      scanner.restore(saved)
      break(false)
    }
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }

    scanner.skipWhitespace()
    val branchName = readIdentifier(scanner)
    if (branchName.isEmpty) {
      scanner.restore(saved)
      break(false)
    }

    db.checkout(branchName)
    skipToNewline(scanner)
    true
  }

  /** Tries to parse a `merge` statement. */
  private def tryParseMerge(scanner: Scanner, db: GitDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("merge")) break(false)
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }

    scanner.skipWhitespace()
    val branchName = readIdentifier(scanner)
    if (branchName.isEmpty) {
      scanner.restore(saved)
      break(false)
    }

    scanner.skipWhitespace()

    // Parse optional attributes
    var mergeId:  Nullable[String] = Nullable.empty
    var mergeTag: Nullable[String] = Nullable.empty
    val mergeMsg  = ""
    var mergeType = CommitType.Merge

    while (!scanner.isEof && scanner.peek() != '\n' && scanner.peek() != ';') {
      scanner.skipWhitespace()
      if (scanner.isEof || scanner.peek() == '\n' || scanner.peek() == ';') {
        // done
      } else if (scanner.matchStrIgnoreCase("id:")) {
        scanner.skipWhitespace()
        mergeId = Nullable(readQuotedOrWord(scanner))
      } else if (scanner.matchStrIgnoreCase("tag:")) {
        scanner.skipWhitespace()
        mergeTag = Nullable(readQuotedOrWord(scanner))
      } else if (scanner.matchStrIgnoreCase("type:")) {
        scanner.skipWhitespace()
        val typeName = readIdentifier(scanner).toUpperCase
        mergeType = typeName match {
          case "REVERSE"   => CommitType.Reverse
          case "HIGHLIGHT" => CommitType.Highlight
          case _           => CommitType.Merge
        }
      } else {
        scanner.advance()
      }
    }

    db.merge(branchName, mergeMsg, mergeId, mergeTag, mergeType)
    true
  }

  /** Tries to parse a `cherry-pick` statement. */
  private def tryParseCherryPick(scanner: Scanner, db: GitDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("cherry-pick")) break(false)
    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\t') {
      scanner.restore(saved)
      break(false)
    }

    scanner.skipWhitespace()

    // Expect id: "commitId"
    var sourceId = ""
    var tag: Nullable[String] = Nullable.empty

    while (!scanner.isEof && scanner.peek() != '\n' && scanner.peek() != ';') {
      scanner.skipWhitespace()
      if (scanner.matchStrIgnoreCase("id:")) {
        scanner.skipWhitespace()
        sourceId = readQuotedOrWord(scanner)
      } else if (scanner.matchStrIgnoreCase("tag:")) {
        scanner.skipWhitespace()
        tag = Nullable(readQuotedOrWord(scanner))
      } else {
        scanner.advance()
      }
    }

    if (sourceId.nonEmpty) {
      db.cherryPick(sourceId, tag)
    }
    true
  }

  /** Tries to parse accTitle. */
  private def tryParseAccTitle(scanner: Scanner, db: GitDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("accTitle")) break(false)
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
  private def tryParseAccDescr(scanner: Scanner, db: GitDb): Boolean = boundary {
    val saved = scanner.save()
    if (!scanner.matchStrIgnoreCase("accDescr")) break(false)
    scanner.skipWhitespace()
    if (!scanner.isEof && scanner.peek() == ':') {
      scanner.advance()
      scanner.skipWhitespace()
      db.accDescription = readTextUntilNewline(scanner).trim
      break(true)
    }
    scanner.restore(saved)
    false
  }

  /** Reads a quoted string or a bare identifier. */
  private def readQuotedOrWord(scanner: Scanner): String =
    if (!scanner.isEof && (scanner.peek() == '"' || scanner.peek() == '\'')) {
      scanner.readQuotedString()
    } else {
      readIdentifier(scanner)
    }

  /** Reads an identifier (letters, digits, underscores, hyphens, dots, slashes). */
  private def readIdentifier(scanner: Scanner): String = boundary {
    val sb = new StringBuilder()
    while (!scanner.isEof) {
      val c = scanner.peek()
      if (c.isLetterOrDigit || c == '_' || c == '-' || c == '.' || c == '/') {
        sb.append(scanner.advance())
      } else {
        break(sb.toString)
      }
    }
    sb.toString
  }

  /** Reads text until newline or EOF. */
  private def readTextUntilNewline(scanner: Scanner): String = {
    val sb = new StringBuilder()
    while (!scanner.isEof && scanner.peek() != '\n')
      sb.append(scanner.advance())
    sb.toString
  }

  /** Skips to the next newline or EOF. */
  private def skipToNewline(scanner: Scanner): Unit = {
    while (!scanner.isEof && scanner.peek() != '\n')
      scanner.advance()
    if (!scanner.isEof) scanner.advance()
  }
}
