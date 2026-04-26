/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Hand-written recursive descent parser for the Liquid template language.
 * Replaces ANTLR-generated LiquidParser + NodeVisitor.
 *
 * Builds LNode AST directly during parsing (no intermediate parse tree).
 * Grammar specification: original-src/liqp/src/main/antlr4/liquid/parser/v4/LiquidParser.g4
 *
 * Covenant: full-port
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package parser

import ssg.liquid.exceptions.LiquidException
import ssg.liquid.nodes._

import java.util.ArrayList

import scala.util.boundary
import scala.util.boundary.break

/** Recursive descent parser that produces an LNode AST from a token stream.
  *
  * Usage:
  * {{{
  * val lexer = new LiquidLexer(input, ...)
  * val tokens = lexer.tokenize()
  * val parser = new LiquidParser(tokens, insertions, filters, parserConfig)
  * val root = parser.parse()
  * }}}
  */
final class LiquidParser(
  private val tokens:              ArrayList[Token],
  private val insertions:          Insertions,
  private val filtersRegistry:     filters.Filters,
  private val liquidStyleInclude:  Boolean,
  private val evaluateInOutputTag: Boolean,
  private val errorMode:           TemplateParser.ErrorMode
) {
  private var pos: Int = 0

  /** Parses the token stream and returns the root BlockNode. */
  def parse(): BlockNode = {
    val root = parseBlock()
    expect(TokenType.EOF)
    root
  }

  // --- Block parsing ---

  /** parse: block EOF */
  private def parseBlock(): BlockNode = {
    val block = new BlockNode()
    while (!isAtEnd && !isBlockEnd) {
      val node = parseAtom()
      block.add(node)
    }
    block
  }

  /** Checks if current token signals end of a block. Must look ahead past TAG_START to check if the next tag is an end/else/elsif/when tag.
    */
  private def isBlockEnd: Boolean = {
    val t = peek()
    if (t.tokenType == TokenType.EOF) {
      true
    } else if (t.tokenType == TokenType.TAG_START) {
      // Look ahead to the tag identifier
      val next = peekAfterTagStart()
      isEndToken(next)
    } else {
      false
    }
  }

  private def isEndToken(tt: TokenType): Boolean =
    tt == TokenType.ENDIF ||
      tt == TokenType.ENDUNLESS ||
      tt == TokenType.ENDCASE ||
      tt == TokenType.ENDFOR ||
      tt == TokenType.ENDTABLEROW ||
      tt == TokenType.ENDCAPTURE ||
      tt == TokenType.ENDCOMMENT ||
      tt == TokenType.ELSE ||
      tt == TokenType.ELSIF ||
      tt == TokenType.WHEN ||
      tt == TokenType.END_BLOCK_ID

  /** atom: tag | output | assignment | other */
  private def parseAtom(): LNode = {
    val t = peek()
    t.tokenType match {
      case TokenType.TEXT      => parseTextNode()
      case TokenType.OUT_START => parseOutput()
      case TokenType.TAG_START =>
        val next = peekAfterTagStart()
        next match {
          case TokenType.ASSIGN => parseAssignment()
          case _                => parseTag()
        }
      case _ =>
        val unexpected = peek()
        advance()
        throw new ssg.liquid.exceptions.LiquidException(
          s"Unexpected token: ${unexpected.tokenType} '${unexpected.value}' at line ${unexpected.line}"
        )
    }
  }

  /** Parses plain text into an AtomNode. */
  private def parseTextNode(): LNode = {
    val t = consume(TokenType.TEXT)
    new AtomNode(t.value)
  }

  // --- Output parsing ---

  /** output: {{ expr filter* }} */
  private def parseOutput(): LNode = {
    consume(TokenType.OUT_START)
    val expr =
      if (evaluateInOutputTag) parseExpr()
      else parseTerm()

    val filterNodes = new ArrayList[FilterNode]()
    while (check(TokenType.PIPE))
      filterNodes.add(parseFilter())

    // Collect any unparsed content before }}
    var unparsed: String = null
    var unparsedLine = -1
    var unparsedPos  = -1
    if (!check(TokenType.OUT_END)) {
      val startToken = peek()
      unparsedLine = startToken.line
      unparsedPos = startToken.col
      val sb = new StringBuilder()
      while (!check(TokenType.OUT_END) && !isAtEnd) {
        sb.append(peek().value)
        advance()
      }
      unparsed = sb.toString()
    }

    consume(TokenType.OUT_END)

    val outputNode = new OutputNode(expr, unparsed, unparsedLine, unparsedPos)
    var i          = 0
    while (i < filterNodes.size()) {
      outputNode.addFilter(filterNodes.get(i))
      i += 1
    }
    outputNode
  }

  /** filter: | Id params? */
  private def parseFilter(): FilterNode = {
    consume(TokenType.PIPE)
    val filterToken = consume(TokenType.ID)
    val filter      = filtersRegistry.get(filterToken.value)
    val filterNode  = FilterNode(filterToken.line, filterToken.col, filterToken.value, filter)

    // params: : param_expr (, param_expr)*
    if (check(TokenType.COLON)) {
      advance() // consume :
      filterNode.add(parseParamExpr())
      while (check(TokenType.COMMA)) {
        advance() // consume ,
        filterNode.add(parseParamExpr())
      }
    }

    filterNode
  }

  /** param_expr: id2 : expr | expr */
  private def parseParamExpr(): LNode =
    // Look ahead to see if this is key:value
    if (isIdLike(peek().tokenType) && pos + 1 < tokens.size() && tokens.get(pos + 1).tokenType == TokenType.COLON) {
      val key = peek().value
      advance() // consume id
      advance() // consume :
      val value = parseExpr()
      new KeyValueNode(key, value)
    } else {
      parseExpr()
    }

  // --- Tag parsing ---

  /** Parses a tag: {% tagname ... %} */
  private def parseTag(): LNode = {
    consume(TokenType.TAG_START)
    val tagToken = peek()

    tagToken.tokenType match {
      case TokenType.IF               => parseIfTag()
      case TokenType.UNLESS           => parseUnlessTag()
      case TokenType.CASE             => parseCaseTag()
      case TokenType.FOR              => parseForTag()
      case TokenType.TABLEROW         => parseTablerowTag()
      case TokenType.CAPTURE          => parseCaptureTag()
      case TokenType.COMMENT          => parseCommentTag()
      case TokenType.RAW              => parseRawTag()
      case TokenType.CYCLE            => parseCycleTag()
      case TokenType.INCLUDE          => parseIncludeTag()
      case TokenType.INCLUDE_RELATIVE => parseIncludeRelativeTag()
      case TokenType.BREAK_TAG        => parseBreakTag()
      case TokenType.CONTINUE_TAG     => parseContinueTag()
      case TokenType.BLOCK_ID         => parseCustomBlockTag()
      case TokenType.SIMPLE_TAG_ID    => parseSimpleTag()
      case TokenType.TAG_END          =>
        // Empty tag: {% %}
        advance() // consume TAG_END
        null
      case _ =>
        // Check for increment/decrement (tag name is an ID)
        val tagName = tagToken.value
        if (tagName == "increment" || tagName == "decrement") {
          parseIncrementDecrementTag(tagName)
        } else {
          parseGenericTag()
        }
    }
  }

  /** assignment: {% assign id = expr filter* %} */
  private def parseAssignment(): LNode = {
    consume(TokenType.TAG_START)
    consume(TokenType.ASSIGN)
    val id = consumeId()
    consume(TokenType.EQ_SIGN)
    val expr = parseExpr()

    val nodes = new ArrayList[LNode]()
    nodes.add(new AtomNode(id))
    nodes.add(expr)

    while (check(TokenType.PIPE))
      nodes.add(parseFilter())

    consume(TokenType.TAG_END)
    new InsertionNode(insertions.get("assign"), nodes)
  }

  // --- Specific tag parsers ---

  /** if_tag: {% if expr %} block ({% elsif expr %} block)* ({% else %} block)? {% endif %} */
  private def parseIfTag(): LNode = {
    advance() // consume IF
    val nodes = new ArrayList[LNode]()
    nodes.add(parseExpr())
    consume(TokenType.TAG_END)
    nodes.add(parseBlock())

    // elsif branches
    while (check(TokenType.TAG_START) && peekAfterTagStart() == TokenType.ELSIF) {
      consume(TokenType.TAG_START)
      advance() // consume ELSIF
      nodes.add(parseExpr())
      consume(TokenType.TAG_END)
      nodes.add(parseBlock())
    }

    // else branch
    if (check(TokenType.TAG_START) && peekAfterTagStart() == TokenType.ELSE) {
      consume(TokenType.TAG_START)
      advance() // consume ELSE
      consume(TokenType.TAG_END)
      nodes.add(new AtomNode(true)) // always-true condition for else
      nodes.add(parseBlock())
    }

    // endif
    if (check(TokenType.TAG_START)) {
      consume(TokenType.TAG_START)
    }
    expect(TokenType.ENDIF)
    advance()
    consume(TokenType.TAG_END)

    new InsertionNode(insertions.get("if"), nodes)
  }

  /** unless_tag: {% unless expr %} block ({% else %} block)? {% endunless %} */
  private def parseUnlessTag(): LNode = {
    advance() // consume UNLESS
    val nodes = new ArrayList[LNode]()
    nodes.add(parseExpr())
    consume(TokenType.TAG_END)
    nodes.add(parseBlock())

    // else branch — for unless, else renders when condition IS true
    // So we use AtomNode(false) as the guard, since !asBoolean(false) = true
    if (check(TokenType.TAG_START) && peekAfterTagStart() == TokenType.ELSE) {
      consume(TokenType.TAG_START)
      advance() // consume ELSE
      consume(TokenType.TAG_END)
      nodes.add(new AtomNode(false))
      nodes.add(parseBlock())
    }

    if (check(TokenType.TAG_START)) consume(TokenType.TAG_START)
    expect(TokenType.ENDUNLESS)
    advance()
    consume(TokenType.TAG_END)

    new InsertionNode(insertions.get("unless"), nodes)
  }

  /** case_tag: {% case expr %} when_tag+ else_tag? {% endcase %} */
  private def parseCaseTag(): LNode = {
    advance() // consume CASE
    val nodes     = new ArrayList[LNode]()
    val condition = parseExpr()
    consume(TokenType.TAG_END)
    nodes.add(condition)

    // Skip any text between case and first when
    while (check(TokenType.TEXT)) advance()

    // when branches
    while (check(TokenType.TAG_START) && peekAfterTagStart() == TokenType.WHEN) {
      consume(TokenType.TAG_START)
      advance() // consume WHEN
      // term (or|, term)*
      nodes.add(parseTerm())
      while (check(TokenType.OR) || check(TokenType.COMMA)) {
        advance() // consume OR or COMMA
        nodes.add(parseTerm())
      }
      consume(TokenType.TAG_END)
      nodes.add(parseBlock())
    }

    // else branch
    if (check(TokenType.TAG_START) && peekAfterTagStart() == TokenType.ELSE) {
      consume(TokenType.TAG_START)
      advance() // consume ELSE
      consume(TokenType.TAG_END)
      nodes.add(parseBlock())
    }

    if (check(TokenType.TAG_START)) consume(TokenType.TAG_START)
    expect(TokenType.ENDCASE)
    advance()
    consume(TokenType.TAG_END)

    new InsertionNode(insertions.get("case"), nodes)
  }

  /** for_tag: {% for id in lookup/range ... %} block {% else %} block {% endfor %} */
  private def parseForTag(): LNode = {
    advance() // consume FOR
    val id = consumeId()
    consume(TokenType.IN)

    val nodes = new ArrayList[LNode]()

    // Check for range: (from..to)
    if (check(TokenType.OPAR)) {
      // Range: for i in (1..10)
      nodes.add(new AtomNode(false)) // isArray = false
      nodes.add(new AtomNode(id))
      advance() // consume (
      val from = parseExpr()
      consume(TokenType.DOTDOT)
      val to = parseExpr()
      consume(TokenType.CPAR)
      nodes.add(from)
      nodes.add(to)

      // Check for reversed
      val reversed = check(TokenType.REVERSED)
      if (reversed) advance()

      // for_attributes
      val attrs = parseForAttributes()

      consume(TokenType.TAG_END)
      val block = parseBlock()

      nodes.add(block) // index 4 for range
      nodes.add(new AtomNode(id)) // index 5: lookup text
      nodes.add(new AtomNode(reversed)) // index 6
      for (attr <- attrs) nodes.add(attr)
    } else {
      // Array: for item in collection
      val lookup = parseLookup()
      nodes.add(new AtomNode(true)) // isArray = true
      nodes.add(new AtomNode(id))
      nodes.add(lookup)

      // Check for reversed
      val reversed = check(TokenType.REVERSED)
      if (reversed) advance()

      // for_attributes
      val attrs = parseForAttributes()

      consume(TokenType.TAG_END)
      val block = parseBlock()

      // else block
      var elseBlock: LNode = null
      if (check(TokenType.TAG_START) && peekAfterTagStart() == TokenType.ELSE) {
        consume(TokenType.TAG_START)
        advance() // consume ELSE
        consume(TokenType.TAG_END)
        elseBlock = parseBlock()
      }

      nodes.add(block) // index 3
      nodes.add(elseBlock) // index 4 (may be null)
      nodes.add(new AtomNode(peek().value)) // index 5: lookup text (approximate)
      nodes.add(new AtomNode(reversed)) // index 6
      // Re-insert at index 5 the lookup text
      nodes.set(5, new AtomNode(id))
      for (attr <- attrs) nodes.add(attr)
    }

    // endfor
    if (check(TokenType.TAG_START)) consume(TokenType.TAG_START)
    expect(TokenType.ENDFOR)
    advance()
    consume(TokenType.TAG_END)

    new InsertionNode(insertions.get("for"), nodes)
  }

  /** Parses for_attribute entries: offset:expr, limit:expr, etc. */
  private def parseForAttributes(): scala.collection.mutable.ArrayBuffer[LNode] = {
    val attrs = scala.collection.mutable.ArrayBuffer[LNode]()
    while (check(TokenType.OFFSET) || check(TokenType.ID)) {
      val key = peek().value
      if (key == "offset" || key == "limit") {
        advance() // consume key
        consume(TokenType.COLON)
        if (key == "offset" && check(TokenType.CONTINUE)) {
          advance() // consume continue
          attrs.addOne(new AttributeNode(new AtomNode(key), new AtomNode(LValue.CONTINUE)))
        } else {
          attrs.addOne(new AttributeNode(new AtomNode(key), parseExpr()))
        }
      } else {
        // Unknown attribute
        val attrKey = peek().value
        advance()
        consume(TokenType.COLON)
        attrs.addOne(new AttributeNode(new AtomNode(attrKey), parseExpr()))
      }
    }
    attrs
  }

  /** tablerow_tag: {% tablerow id in lookup attribute* %} block {% endtablerow %} */
  private def parseTablerowTag(): LNode = {
    advance() // consume TABLEROW
    val id = consumeId()
    consume(TokenType.IN)
    val lookup = parseLookup()

    val nodes = new ArrayList[LNode]()
    nodes.add(new AtomNode(id))
    nodes.add(lookup)

    // Attributes (cols, limit, offset)
    while (check(TokenType.ID) || check(TokenType.OFFSET)) {
      val key = peek().value
      advance()
      consume(TokenType.COLON)
      nodes.add(new AttributeNode(new AtomNode(key), parseExpr()))
    }

    consume(TokenType.TAG_END)
    val block = parseBlock()
    nodes.add(2, block) // insert block at index 2, pushing attributes after

    if (check(TokenType.TAG_START)) consume(TokenType.TAG_START)
    expect(TokenType.ENDTABLEROW)
    advance()
    consume(TokenType.TAG_END)

    new InsertionNode(insertions.get("tablerow"), nodes)
  }

  /** capture_tag: {% capture id %} block {% endcapture %} */
  private def parseCaptureTag(): LNode = {
    advance() // consume CAPTURE
    val id = if (check(TokenType.STR)) {
      val s = peek().value
      advance()
      s
    } else {
      consumeId()
    }
    consume(TokenType.TAG_END)
    val block = parseBlock()

    if (check(TokenType.TAG_START)) consume(TokenType.TAG_START)
    expect(TokenType.ENDCAPTURE)
    advance()
    consume(TokenType.TAG_END)

    new InsertionNode(insertions.get("capture"), Array[LNode](new AtomNode(id), block))
  }

  /** comment_tag: {% comment %} ... {% endcomment %} */
  private def parseCommentTag(): LNode = boundary {
    advance() // consume COMMENT
    consume(TokenType.TAG_END)

    // Skip everything until we find {% endcomment %}
    while (!isAtEnd) {
      if (check(TokenType.TAG_START) && peekAfterTagStart() == TokenType.ENDCOMMENT) {
        consume(TokenType.TAG_START)
        advance() // consume ENDCOMMENT
        consume(TokenType.TAG_END)
        break(new InsertionNode(insertions.get("comment"), Array.empty[LNode]))
      }
      advance()
    }

    new InsertionNode(insertions.get("comment"), Array.empty[LNode])
  }

  /** raw_tag: The lexer already handles raw body as TEXT between {% raw %} and {% endraw %} */
  private def parseRawTag(): LNode = boundary {
    advance() // consume RAW
    // Consume the TAG_END from {% raw %}
    if (check(TokenType.TAG_END)) consume(TokenType.TAG_END)
    // Now we expect TEXT (raw body) followed by {% endraw %}

    val sb = new StringBuilder()
    while (!isAtEnd) {
      val t = peek()
      if (t.tokenType == TokenType.TAG_START) {
        if (peekAfterTagStart() == TokenType.RAW) {
          // This is the {% endraw %} — the lexer emits RAW token for "endraw"
          consume(TokenType.TAG_START)
          advance() // consume RAW (endraw)
          consume(TokenType.TAG_END)
          break(new InsertionNode(insertions.get("raw"), Array[LNode](new AtomNode(sb.toString()))))
        }
      }
      sb.append(t.value)
      advance()
    }

    new InsertionNode(insertions.get("raw"), Array[LNode](new AtomNode(sb.toString())))
  }

  /** cycle_tag: {% cycle group: expr, expr, ... %} */
  private def parseCycleTag(): LNode = {
    advance() // consume CYCLE
    val nodes = new ArrayList[LNode]()

    // Check for group name: "group_name": value1, value2
    // The group is optional: expr :
    val firstExpr = parseExpr()
    if (check(TokenType.COLON)) {
      advance() // consume :
      nodes.add(firstExpr) // group name
      nodes.add(parseExpr())
    } else {
      nodes.add(null) // no group name
      nodes.add(firstExpr)
    }

    while (check(TokenType.COMMA)) {
      advance() // consume ,
      nodes.add(parseExpr())
    }

    consume(TokenType.TAG_END)
    new InsertionNode(insertions.get("cycle"), nodes)
  }

  /** include_tag: {% include 'file' %} or {% include file var=val %} */
  private def parseIncludeTag(): LNode = {
    advance() // consume INCLUDE
    val nodes = new ArrayList[LNode]()

    if (liquidStyleInclude) {
      // Liquid style: {% include 'template' with expr %}
      nodes.add(parseExpr())
      if (check(TokenType.WITH)) {
        advance()
        nodes.add(parseExpr())
      }
    } else {
      // Jekyll style: {% include filename var=val var=val %}
      val fileName = if (check(TokenType.STR)) {
        val s = peek().value
        advance()
        s
      } else {
        // Read until whitespace/tag end
        consumeId()
      }
      nodes.add(new AtomNode(fileName))

      // key=value params
      while (isIdLike(peek().tokenType) && !check(TokenType.TAG_END)) {
        val key = consumeId()
        consume(TokenType.EQ_SIGN)
        val value = parseExpr()
        nodes.add(new KeyValueNode(key, value))
      }
    }

    consume(TokenType.TAG_END)
    new InsertionNode(insertions.get("include"), nodes)
  }

  /** include_relative_tag */
  private def parseIncludeRelativeTag(): LNode = {
    advance() // consume INCLUDE_RELATIVE
    val nodes    = new ArrayList[LNode]()
    val fileName = if (check(TokenType.STR)) {
      val s = peek().value
      advance()
      s
    } else {
      consumeId()
    }
    nodes.add(new AtomNode(fileName))

    // key=value params
    while (isIdLike(peek().tokenType) && !check(TokenType.TAG_END)) {
      val key = consumeId()
      consume(TokenType.EQ_SIGN)
      val value = parseExpr()
      nodes.add(new KeyValueNode(key, value))
    }

    consume(TokenType.TAG_END)
    val incRelative = insertions.get("include_relative")
    if (incRelative != null) {
      new InsertionNode(incRelative, nodes)
    } else {
      new InsertionNode(insertions.get("include"), nodes)
    }
  }

  private def parseBreakTag(): LNode = {
    advance() // consume BREAK_TAG
    consume(TokenType.TAG_END)
    new InsertionNode(insertions.get("break"), Array.empty[LNode])
  }

  private def parseContinueTag(): LNode = {
    advance() // consume CONTINUE_TAG
    consume(TokenType.TAG_END)
    new InsertionNode(insertions.get("continue"), Array.empty[LNode])
  }

  /** custom block: {% blockid ... %} block {% endblockid %} */
  private def parseCustomBlockTag(): LNode = {
    val tagName = peek().value
    advance() // consume BLOCK_ID
    val insertion = insertions.get(tagName)

    // Consume parameters until TagEnd
    val params = new ArrayList[LNode]()
    while (!check(TokenType.TAG_END) && !isAtEnd) {
      params.add(parseExpr())
      if (check(TokenType.COMMA)) advance()
    }
    consume(TokenType.TAG_END)

    val block = parseBlock()
    params.add(block)

    // Consume end tag
    if (check(TokenType.TAG_START)) consume(TokenType.TAG_START)
    if (check(TokenType.END_BLOCK_ID)) advance()
    consume(TokenType.TAG_END)

    if (insertion != null) {
      new InsertionNode(insertion, params)
    } else {
      block // fallback: just render the block content
    }
  }

  /** simple tag: {% simpletag params %} */
  private def parseSimpleTag(): LNode = {
    val tagName = peek().value
    advance() // consume SIMPLE_TAG_ID
    val insertion = insertions.get(tagName)

    val params = new ArrayList[LNode]()
    while (!check(TokenType.TAG_END) && !isAtEnd) {
      params.add(parseExpr())
      if (check(TokenType.COMMA)) advance()
    }
    consume(TokenType.TAG_END)

    if (insertion != null) {
      new InsertionNode(insertion, params)
    } else {
      new AtomNode("") // unknown tag, produce empty
    }
  }

  /** Parses increment/decrement tags where the parameter is a variable name literal (not a lookup). */
  private def parseIncrementDecrementTag(tagName: String): LNode = {
    advance() // consume tag name ID (already read by caller via peek)
    val insertion = insertions.get(tagName)
    val varName   = consumeId() // The variable name as a literal string
    consume(TokenType.TAG_END)

    if (insertion != null) {
      new InsertionNode(insertion, Array[LNode](new AtomNode(varName)))
    } else {
      new AtomNode("")
    }
  }

  /** Generic tag handler for unknown tags. */
  private def parseGenericTag(): LNode = {
    val tagName = peek().value
    advance() // consume tag name ID
    val insertion = insertions.get(tagName)

    val params = new ArrayList[LNode]()
    while (!check(TokenType.TAG_END) && !isAtEnd) {
      params.add(parseExpr())
      if (check(TokenType.COMMA)) advance()
    }
    consume(TokenType.TAG_END)

    if (insertion != null) {
      new InsertionNode(insertion, params)
    } else {
      new AtomNode("") // unknown tag
    }
  }

  // --- Expression parsing (operator precedence) ---

  /** expr: term ((and|or|==|!=|<|>|<=|>=|contains) term)* */
  def parseExpr(): LNode =
    parseOrExpr()

  /** or_expr: and_expr (or and_expr)* */
  private def parseOrExpr(): LNode = {
    var left = parseAndExpr()
    while (check(TokenType.OR)) {
      advance()
      val right = parseAndExpr()
      left = new OrNode(left, right)
    }
    left
  }

  /** and_expr: comparison (and comparison)* */
  private def parseAndExpr(): LNode = {
    var left = parseComparison()
    while (check(TokenType.AND)) {
      advance()
      val right = parseComparison()
      left = new AndNode(left, right)
    }
    left
  }

  /** comparison: contains_expr ((== | != | < | > | <= | >=) contains_expr)? */
  private def parseComparison(): LNode = {
    var left = parseContainsExpr()
    val t    = peek().tokenType
    t match {
      case TokenType.EQ =>
        advance(); val right = parseContainsExpr(); left = new EqNode(left, right)
      case TokenType.NEQ =>
        advance(); val right = parseContainsExpr(); left = new NEqNode(left, right)
      case TokenType.LT =>
        advance(); val right = parseContainsExpr(); left = new LtNode(left, right)
      case TokenType.GT =>
        advance(); val right = parseContainsExpr(); left = new GtNode(left, right)
      case TokenType.LTEQ =>
        advance(); val right = parseContainsExpr(); left = new LtEqNode(left, right)
      case TokenType.GTEQ =>
        advance(); val right = parseContainsExpr(); left = new GtEqNode(left, right)
      case _ => // no comparison
    }
    left
  }

  /** contains_expr: term (contains term)? */
  private def parseContainsExpr(): LNode = {
    val left = parseTerm()
    if (check(TokenType.CONTAINS)) {
      advance()
      val right = parseTerm()
      new ContainsNode(left, right)
    } else {
      left
    }
  }

  /** term: number | string | true | false | nil | empty | blank | lookup | (expr) */
  private def parseTerm(): LNode = {
    val t = peek()
    t.tokenType match {
      case TokenType.DOUBLE_NUM =>
        advance()
        new AtomNode(java.lang.Double.valueOf(t.value))
      case TokenType.LONG_NUM =>
        advance()
        new AtomNode(java.lang.Long.valueOf(t.value))
      case TokenType.STR =>
        advance()
        new AtomNode(t.value)
      case TokenType.TRUE =>
        advance()
        new AtomNode(java.lang.Boolean.TRUE)
      case TokenType.FALSE =>
        advance()
        new AtomNode(java.lang.Boolean.FALSE)
      case TokenType.NIL =>
        advance()
        new AtomNode(null)
      case TokenType.EMPTY =>
        // Could be a lookup or the empty sentinel
        if (pos + 1 < tokens.size() && (tokens.get(pos + 1).tokenType == TokenType.DOT || tokens.get(pos + 1).tokenType == TokenType.OBR)) {
          parseLookup()
        } else {
          advance()
          AtomNode.EMPTY
        }
      case TokenType.BLANK =>
        advance()
        AtomNode.BLANK
      case TokenType.OPAR =>
        advance() // consume (
        val expr = parseExpr()
        consume(TokenType.CPAR) // consume )
        expr
      case _ =>
        if (isIdLike(t.tokenType)) {
          parseLookup()
        } else {
          // Error recovery: return empty atom
          advance()
          new AtomNode(t.value)
        }
    }
  }

  /** lookup: id index* ?  or  [str] ?  or  [id] ? */
  private def parseLookup(): LNode = {
    val t = peek()

    if (t.tokenType == TokenType.OBR) {
      // [str] or [id] lookup
      advance() // consume [
      val inner = peek().value
      advance() // consume str or id
      consume(TokenType.CBR) // consume ]
      if (check(TokenType.QMARK)) advance()
      new LookupNode(inner)
    } else {
      val id     = consumeId()
      val lookup = new LookupNode(id)

      // Parse indexes: .id or [expr]
      while (check(TokenType.DOT) || check(TokenType.OBR))
        if (check(TokenType.DOT)) {
          advance() // consume .
          val prop = consumeIdOrKeyword()
          lookup.add(new LookupNode.Hash(prop))
        } else {
          advance() // consume [
          val indexExpr = parseExpr()
          consume(TokenType.CBR) // consume ]
          val indexText = indexExpr match {
            case atom: AtomNode => String.valueOf(atom.render(null))
            case _ => "?"
          }
          lookup.add(new LookupNode.Index(indexExpr, indexText))
        }

      if (check(TokenType.QMARK)) advance()

      lookup
    }
  }

  // --- Token utilities ---

  private def peek(): Token =
    if (pos < tokens.size()) tokens.get(pos)
    else Token(TokenType.EOF, "", 0, 0)

  /** Peeks at the token after TAG_START (skipping whitespace). */
  private def peekAfterTagStart(): TokenType =
    if (pos + 1 < tokens.size()) tokens.get(pos + 1).tokenType
    else TokenType.EOF

  private def advance(): Token = {
    val t = peek()
    if (pos < tokens.size()) pos += 1
    t
  }

  private def check(tt: TokenType): Boolean = peek().tokenType == tt

  private def isAtEnd: Boolean = pos >= tokens.size() || peek().tokenType == TokenType.EOF

  private def consume(expected: TokenType): Token = {
    val t = peek()
    if (t.tokenType != expected) {
      throw new LiquidException(
        s"Expected $expected but got ${t.tokenType} ('${t.value}')",
        t.line,
        t.col
      )
    }
    advance()
  }

  private def expect(expected: TokenType): Unit =
    if (peek().tokenType != expected) {
      val t = peek()
      if (errorMode == TemplateParser.ErrorMode.STRICT) {
        throw new LiquidException(
          s"Expected $expected but got ${t.tokenType} ('${t.value}')",
          t.line,
          t.col
        )
      }
    }

  /** Consumes an identifier token (ID or keyword used as identifier). */
  private def consumeId(): String = {
    val t = peek()
    if (isIdLike(t.tokenType)) {
      advance()
      t.value
    } else {
      throw new LiquidException(
        s"Expected identifier but got ${t.tokenType} ('${t.value}')",
        t.line,
        t.col
      )
    }
  }

  /** Consumes an identifier or keyword (used in property access like .size, .first). */
  private def consumeIdOrKeyword(): String = {
    val t = peek()
    advance()
    t.value
  }

  /** Checks if a token type can be used as an identifier. */
  private def isIdLike(tt: TokenType): Boolean =
    tt == TokenType.ID ||
      tt == TokenType.CONTAINS ||
      tt == TokenType.IN ||
      tt == TokenType.AND ||
      tt == TokenType.OR ||
      tt == TokenType.WITH ||
      tt == TokenType.OFFSET ||
      tt == TokenType.CONTINUE ||
      tt == TokenType.REVERSED ||
      tt == TokenType.EMPTY ||
      tt == TokenType.BLANK ||
      tt == TokenType.BLOCK_ID ||
      tt == TokenType.SIMPLE_TAG_ID ||
      tt == TokenType.END_BLOCK_ID ||
      tt == TokenType.IF ||
      tt == TokenType.ELSIF ||
      tt == TokenType.ENDIF ||
      tt == TokenType.UNLESS ||
      tt == TokenType.ENDUNLESS ||
      tt == TokenType.ELSE ||
      tt == TokenType.CASE ||
      tt == TokenType.ENDCASE ||
      tt == TokenType.WHEN ||
      tt == TokenType.CYCLE ||
      tt == TokenType.FOR ||
      tt == TokenType.ENDFOR ||
      tt == TokenType.TABLEROW ||
      tt == TokenType.ENDTABLEROW ||
      tt == TokenType.CAPTURE ||
      tt == TokenType.ENDCAPTURE ||
      tt == TokenType.COMMENT ||
      tt == TokenType.ENDCOMMENT ||
      tt == TokenType.ASSIGN ||
      tt == TokenType.INCLUDE ||
      tt == TokenType.INCLUDE_RELATIVE
}
