/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Recursive descent parser for the Graphviz DOT language.
 */
package ssg
package graphviz
package parse

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

class DotParser(tokens: Array[Token]) {

  private var pos: Int = 0

  def parse(): DotGraph = {
    val strict    = matchKeyword("strict")
    val graphType =
      if (matchKeyword("digraph")) { DotGraphType.Digraph }
      else {
        expectKeyword("graph")
        DotGraphType.Graph
      }
    val id = tryReadId()
    expectValue("{")
    val stmts = parseStmtList()
    expectValue("}")
    if (peek().tpe != TokenType.Eof) {
      val t = peek()
      throw new DotParseException(
        s"Unexpected token '${t.value}' after graph at line ${t.line}, col ${t.col}",
        t.line,
        t.col
      )
    }
    DotGraph(strict, graphType, id, stmts)
  }

  private def parseStmtList(): Seq[DotStmt] = {
    val stmts = ArrayBuffer.empty[DotStmt]
    boundary {
      while (true) {
        skipSemicolons()
        val t = peek()
        if (t.tpe == TokenType.RBrace || t.tpe == TokenType.Eof) {
          break(())
        }
        stmts += parseStmt()
        skipSemicolons()
      }
    }
    stmts.toSeq
  }

  private def parseStmt(): DotStmt = {
    val t = peek()

    // subgraph: starts with "subgraph" keyword or bare "{"
    if (isKeyword(t, "subgraph") || t.tpe == TokenType.LBrace) {
      val sub = parseSubgraph()
      // After a subgraph, check for edge continuation
      if (isEdgeOp(peek())) {
        return parseEdgeStmtFromSubgraph(sub)
      }
      return sub
    }

    // attr_stmt: "graph" / "node" / "edge" followed by "["
    if (isAttrTarget(t) && lookahead().tpe == TokenType.LBracket) {
      return parseAttrStmt()
    }

    // Must start with an ID (identifier, number, quoted string, or HTML string)
    val id = readId()

    val next = peek()

    // edge_stmt: id followed by edge operator
    if (isEdgeOp(next)) {
      return parseEdgeStmtFromId(id)
    }

    // assign_stmt: id = id
    if (next.tpe == TokenType.Equals) {
      advance() // =
      val (value, valueIsHtml) = readIdWithHtmlFlag()
      return DotAssignStmt(id, value, valueIsHtml)
    }

    // node_stmt: id optionally with port and attr_list
    val nodeId = parseNodeIdFromString(id)

    // After consuming ports (e.g. `a:b -> ...`), re-check for edge operator
    if (isEdgeOp(peek())) {
      return parseEdgeStmtFromNodeId(nodeId)
    }

    val attrs = if (peek().tpe == TokenType.LBracket) { parseAttrList() }
    else { Seq.empty }
    DotNodeStmt(nodeId, attrs)
  }

  private def parseAttrStmt(): DotAttrStmt = {
    val t      = advance()
    val target = t.value.toLowerCase match {
      case "graph" => DotAttrTarget.Graph
      case "node"  => DotAttrTarget.Node
      case "edge"  => DotAttrTarget.Edge
      case _       => throw new DotParseException(s"Unexpected attr target '${t.value}' at line ${t.line}, col ${t.col}", t.line, t.col)
    }
    val attrs = parseAttrList()
    DotAttrStmt(target, attrs)
  }

  private def parseSubgraph(): DotSubgraphStmt = {
    var id: Option[String] = None
    if (isKeyword(peek(), "subgraph")) {
      advance() // subgraph
      if (isId(peek()) && peek().tpe != TokenType.LBrace) {
        id = Some(readId())
      }
    }
    expectValue("{")
    val stmts = parseStmtList()
    expectValue("}")
    DotSubgraphStmt(id, stmts)
  }

  private def parseEdgeStmtFromId(firstId: String): DotEdgeStmt = {
    val nodeId = parseNodeIdFromString(firstId)
    val nodes  = ArrayBuffer[DotNodeId](nodeId)
    boundary {
      while (isEdgeOp(peek())) {
        advance() // -> or --
        val t = peek()
        if (isKeyword(t, "subgraph") || t.tpe == TokenType.LBrace) {
          val sub = parseSubgraph()
          // Subgraph in edge chain: use subgraph id as node id, or generate one
          nodes += DotNodeId(sub.id.getOrElse(""))
        } else {
          val nextId = readId()
          nodes += parseNodeIdFromString(nextId)
        }
      }
    }
    val attrs = if (peek().tpe == TokenType.LBracket) { parseAttrList() }
    else { Seq.empty }
    DotEdgeStmt(nodes.toSeq, attrs)
  }

  /** Like parseEdgeStmtFromId but starts from an already-parsed DotNodeId (with ports consumed). */
  private def parseEdgeStmtFromNodeId(firstNode: DotNodeId): DotEdgeStmt = {
    val nodes = ArrayBuffer[DotNodeId](firstNode)
    boundary {
      while (isEdgeOp(peek())) {
        advance() // -> or --
        val t = peek()
        if (isKeyword(t, "subgraph") || t.tpe == TokenType.LBrace) {
          val sub = parseSubgraph()
          nodes += DotNodeId(sub.id.getOrElse(""))
        } else {
          val nextId = readId()
          nodes += parseNodeIdFromString(nextId)
        }
      }
    }
    val attrs = if (peek().tpe == TokenType.LBracket) { parseAttrList() }
    else { Seq.empty }
    DotEdgeStmt(nodes.toSeq, attrs)
  }

  private def parseEdgeStmtFromSubgraph(sub: DotSubgraphStmt): DotEdgeStmt = {
    val nodes = ArrayBuffer[DotNodeId](DotNodeId(sub.id.getOrElse("")))
    boundary {
      while (isEdgeOp(peek())) {
        advance() // -> or --
        val t = peek()
        if (isKeyword(t, "subgraph") || t.tpe == TokenType.LBrace) {
          val nextSub = parseSubgraph()
          nodes += DotNodeId(nextSub.id.getOrElse(""))
        } else {
          val nextId = readId()
          nodes += parseNodeIdFromString(nextId)
        }
      }
    }
    val attrs = if (peek().tpe == TokenType.LBracket) { parseAttrList() }
    else { Seq.empty }
    DotEdgeStmt(nodes.toSeq, attrs)
  }

  /** DOT attr_list: one or more bracketed groups `[key=value, ...]` */
  private def parseAttrList(): Seq[DotAttr] = {
    val attrs = ArrayBuffer.empty[DotAttr]
    boundary {
      while (peek().tpe == TokenType.LBracket) {
        advance() // [
        boundary {
          while (peek().tpe != TokenType.RBracket && peek().tpe != TokenType.Eof) {
            if (isId(peek())) {
              val key = readId()
              if (peek().tpe == TokenType.Equals) {
                advance() // =
                val (value, valueIsHtml) = readIdWithHtmlFlag()
                attrs += DotAttr(key, value, valueIsHtml)
              } else {
                // Bare key without value (treated as key=true in some DOT dialects)
                attrs += DotAttr(key, "true")
              }
            }
            // Skip optional separators
            if (peek().tpe == TokenType.Comma || peek().tpe == TokenType.Semicolon) {
              advance()
            }
          }
        }
        expectValue("]")
      }
    }
    attrs.toSeq
  }

  /** Parse port and compass from subsequent colon-delimited tokens. */
  private def parseNodeIdFromString(id: String): DotNodeId = {
    var port:    Option[String] = None
    var compass: Option[String] = None
    if (peek().tpe == TokenType.Colon) {
      advance() // :
      val p = readId()
      if (isCompassPoint(p) && peek().tpe != TokenType.Colon) {
        compass = Some(p)
      } else {
        port = Some(p)
        if (peek().tpe == TokenType.Colon) {
          advance() // :
          val c = readId()
          compass = Some(c)
        }
      }
    }
    DotNodeId(id, port, compass)
  }

  private def isCompassPoint(s: String): Boolean = s match {
    case "n" | "ne" | "e" | "se" | "s" | "sw" | "w" | "nw" | "c" | "_" => true
    case _                                                             => false
  }

  // -- Token helpers --

  private def peek(): Token =
    if (pos < tokens.length) { tokens(pos) }
    else { Token(TokenType.Eof, "", 0, 0) }

  private def lookahead(): Token =
    if (pos + 1 < tokens.length) { tokens(pos + 1) }
    else { Token(TokenType.Eof, "", 0, 0) }

  private def advance(): Token = {
    val t = tokens(pos)
    pos += 1
    t
  }

  private def matchKeyword(kw: String): Boolean = {
    val t = peek()
    if (t.tpe == TokenType.Identifier && t.value.equalsIgnoreCase(kw)) {
      advance()
      true
    } else {
      false
    }
  }

  private def expectKeyword(kw: String): Unit = {
    val t = peek()
    if (t.tpe == TokenType.Identifier && t.value.equalsIgnoreCase(kw)) {
      advance()
    } else {
      throw new DotParseException(
        s"Expected '$kw' but found '${t.value}' at line ${t.line}, col ${t.col}",
        t.line,
        t.col
      )
    }
  }

  private def expectValue(value: String): Unit = {
    val t = peek()
    if (t.value == value) {
      advance()
    } else {
      throw new DotParseException(
        s"Expected '$value' but found '${t.value}' at line ${t.line}, col ${t.col}",
        t.line,
        t.col
      )
    }
  }

  private def isKeyword(t: Token, kw: String): Boolean =
    t.tpe == TokenType.Identifier && t.value.equalsIgnoreCase(kw)

  private def isEdgeOp(t: Token): Boolean =
    t.tpe == TokenType.Arrow || t.tpe == TokenType.DashDash

  private def isAttrTarget(t: Token): Boolean =
    t.tpe == TokenType.Identifier && (
      t.value.equalsIgnoreCase("graph") ||
        t.value.equalsIgnoreCase("node") ||
        t.value.equalsIgnoreCase("edge")
    )

  private def isId(t: Token): Boolean =
    t.tpe == TokenType.Identifier ||
      t.tpe == TokenType.Number ||
      t.tpe == TokenType.QuotedString ||
      t.tpe == TokenType.HtmlString

  private def readId(): String = {
    val t = peek()
    if (isId(t)) {
      advance()
      t.value
    } else {
      throw new DotParseException(
        s"Expected identifier but found '${t.value}' (${t.tpe}) at line ${t.line}, col ${t.col}",
        t.line,
        t.col
      )
    }
  }

  /** Reads an ID and also reports whether the token was an HTML-like string. */
  private def readIdWithHtmlFlag(): (String, Boolean) = {
    val t = peek()
    if (isId(t)) {
      advance()
      (t.value, t.tpe == TokenType.HtmlString)
    } else {
      throw new DotParseException(
        s"Expected identifier but found '${t.value}' (${t.tpe}) at line ${t.line}, col ${t.col}",
        t.line,
        t.col
      )
    }
  }

  private def tryReadId(): Option[String] = {
    val t = peek()
    if (isId(t)) {
      advance()
      Some(t.value)
    } else {
      None
    }
  }

  private def skipSemicolons(): Unit =
    boundary {
      while (peek().tpe == TokenType.Semicolon)
        advance()
    }
}
