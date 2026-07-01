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
 * Migration notes:
 *   Renames: liqp.parser.v4.NodeVisitor (AST-build) + liquid.parser.v4.LiquidParser (ANTLR grammar)
 *            merged into ssg.liquid.parser.LiquidParser (recursive descent)
 *   Divergence: unknown-filter error timing — liqp defers AST construction to render time
 *     (NodeVisitor.visitFilter, NodeVisitor.java:571, runs inside Template.renderToObjectUnguarded,
 *     Template.java:355-357), so FilterNode's null-filter check (FilterNode.java:24-25) throws
 *     IllegalArgumentException at render. SSG's recursive descent parser builds the AST eagerly
 *     during parse (LiquidParser.parseFilter calls FilterNode.apply which checks for null
 *     at FilterNode.scala:61-63), so unknown filters error at parse time instead.
 *     Pinned in .fail tests: FilterMiscExtraSuite "flavored filters: normalize_whitespace ...",
 *     ReadmeSamplesSuite "readme: render tree ...".
 *
 * Covenant: full-port
 * Covenant-verified: 2026-06-14
 */
package ssg
package liquid
package parser

import lowlevel.Nullable
import ssg.data.DataView
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
  // liqp's LiquidParser holds errorMode for its grammar predicates (LiquidParser.g4:25-33).
  // In this port the output-tag errorMode dispatch lives in OutputNode.render (which reads
  // context.parser.errorMode) and the built-in block-ends throw unconditionally via expect,
  // so the parser no longer consults this field directly; it is retained to mirror liqp's
  // constructor surface.
  errorMode: TemplateParser.ErrorMode
) {
  private var pos: Int = 0

  /** Custom block names that are currently "open" (a {% blockid %} whose end tag remains to be consumed). Mirrors liqp's lexer `customBlockState` stack (LiquidLexer.g4:258-259), used to classify end
    * tags as a valid EndBlockId vs a MisMatchedEndBlockId vs an InvalidEndBlockId (LiquidLexer.g4:256-279). The SSG lexer is stateless w.r.t. block nesting, so the classification needed by
    * `error_other_tag` (LiquidParser.g4:98-110) is reconstructed here in the parser, which has the structural context.
    */
  private val openBlocks: ArrayList[String] = new ArrayList[String]()

  /** Parse-time errors recorded while parsing. Mirrors liqp's parser error listener (Template.java:91-98) which routes `reportTokenError` (LiquidParser.g4:43-49) notifications.
    */
  private val parseErrors: ArrayList[LiquidException] = new ArrayList[LiquidException]()

  /** Reports a token error matching liqp's `reportTokenError(String, Token)` (LiquidParser.g4:43-45). In liqp this calls `notifyErrorListeners`, and the registered parser error listener
    * (Template.java:91-98) is installed unconditionally and throws a LiquidException for every notification regardless of errorMode — so an invalid/unknown/mismatched/missing/empty tag always raises.
    * The errorMode flags (`isStrict()`/`isWarn()`/`isLax()`, LiquidLexer.g4:25-33) gate ONLY the output-tag/expression rule (LiquidLexer.g4:231-232), never invalid-TAG structure. We therefore throw
    * unconditionally here, faithful to liqp's always-throwing parser listener.
    */
  private def reportTokenError(message: String, token: Token): Nothing = {
    val ex = new LiquidException(s"$message: '${token.value}'", token.line, token.col)
    parseErrors.add(ex)
    throw ex
  }

  /** Reports a token error without an offending token, matching liqp's `reportTokenError(String)` (LiquidParser.g4:47-49). Throws unconditionally in all error modes, matching liqp's always-throwing
    * parser error listener (Template.java:91-98); errorMode never gates invalid-tag structure (LiquidParser.g4:98-110 `error_other_tag` has no errorMode predicate).
    */
  private def reportTokenError(message: String): Nothing = {
    val t  = peek()
    val ex = new LiquidException(message, t.line, t.col)
    parseErrors.add(ex)
    throw ex
  }

  /** Parses the token stream and returns the root BlockNode. */
  def parse(): BlockNode = {
    val root = parseBlock()
    // A trailing end-block tag at the top level has no matching start block:
    // liqp's lexer classifies it InvalidEndBlockId with an empty customBlockState
    // (LiquidLexer.g4:271-273), reported as "Invalid End Tag"
    // (LiquidParser.g4:102-103).
    if (!isAtEnd && check(TokenType.TAG_START) && peekAfterTagStart() == TokenType.END_BLOCK_ID && openBlocks.isEmpty) {
      consume(TokenType.TAG_START)
      val endToken = peek()
      // A stray top-level end-block tag is InvalidEndBlockId with an empty
      // customBlockState (LiquidLexer.g4:271-273); error_other_tag reports it as
      // "Invalid End Tag" (LiquidParser.g4:102-103) and the parser listener throws
      // unconditionally (Template.java:91-98).
      reportTokenError("Invalid End Tag", endToken)
    }
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
    new AtomNode(DataView.from(t.value))
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
        // Valid empty tag: {% %} reached via an inline comment {% # ... %}, whose
        // comment pops the lexer's IN_TAG_ID mode so the close is a real TagEnd.
        // This is the valid empty_tag rule (LiquidParser.g4:117-119): a no-op.
        advance() // consume TAG_END
        new AtomNode(DataView.from(""))
      case TokenType.INVALID_END_TAG =>
        // A tag with no id and no inline comment: {%%}, {% %}, {%}} . liqp's lexer
        // emits InvalidEndTag (LiquidLexer.g4:196-206) and error_other_tag
        // (LiquidParser.g4:109) reports it as "Invalid Empty Tag" — the parser
        // listener throws unconditionally in all error modes (Template.java:91-98).
        reportTokenError("Invalid Empty Tag")
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
    nodes.add(new AtomNode(DataView.from(id)))
    nodes.add(expr)

    while (check(TokenType.PIPE))
      nodes.add(parseFilter())

    consume(TokenType.TAG_END)
    new InsertionNode(insertions.get("assign").get, nodes)
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
      nodes.add(new AtomNode(DataView.from(true))) // always-true condition for else
      nodes.add(parseBlock())
    }

    // endif
    if (check(TokenType.TAG_START)) {
      consume(TokenType.TAG_START)
    }
    expect(TokenType.ENDIF)
    advance()
    consume(TokenType.TAG_END)

    new InsertionNode(insertions.get("if").get, nodes)
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
      nodes.add(new AtomNode(DataView.from(false)))
      nodes.add(parseBlock())
    }

    if (check(TokenType.TAG_START)) consume(TokenType.TAG_START)
    expect(TokenType.ENDUNLESS)
    advance()
    consume(TokenType.TAG_END)

    new InsertionNode(insertions.get("unless").get, nodes)
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

    new InsertionNode(insertions.get("case").get, nodes)
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
      nodes.add(new AtomNode(DataView.from(false))) // isArray = false
      nodes.add(new AtomNode(DataView.from(id)))
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
      nodes.add(new AtomNode(DataView.from(id))) // index 5: lookup text
      nodes.add(new AtomNode(DataView.from(reversed))) // index 6
      for (attr <- attrs) nodes.add(attr)
    } else {
      // Array: for item in collection
      val lookup = parseLookup()
      nodes.add(new AtomNode(DataView.from(true))) // isArray = true
      nodes.add(new AtomNode(DataView.from(id)))
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
      nodes.add(new AtomNode(DataView.from(peek().value))) // index 5: lookup text (approximate)
      nodes.add(new AtomNode(DataView.from(reversed))) // index 6
      // Re-insert at index 5 the lookup text
      nodes.set(5, new AtomNode(DataView.from(id)))
      for (attr <- attrs) nodes.add(attr)
    }

    // endfor
    if (check(TokenType.TAG_START)) consume(TokenType.TAG_START)
    expect(TokenType.ENDFOR)
    advance()
    consume(TokenType.TAG_END)

    new InsertionNode(insertions.get("for").get, nodes)
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
          attrs.addOne(new AttributeNode(new AtomNode(DataView.from(key)), new AtomNode(DataView.CONTINUE)))
        } else {
          attrs.addOne(new AttributeNode(new AtomNode(DataView.from(key)), parseExpr()))
        }
      } else {
        // Unknown attribute
        val attrKey = peek().value
        advance()
        consume(TokenType.COLON)
        attrs.addOne(new AttributeNode(new AtomNode(DataView.from(attrKey)), parseExpr()))
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
    nodes.add(new AtomNode(DataView.from(id)))
    nodes.add(lookup)

    // Attributes (cols, limit, offset)
    while (check(TokenType.ID) || check(TokenType.OFFSET)) {
      val key = peek().value
      advance()
      consume(TokenType.COLON)
      nodes.add(new AttributeNode(new AtomNode(DataView.from(key)), parseExpr()))
    }

    consume(TokenType.TAG_END)
    val block = parseBlock()
    nodes.add(2, block) // insert block at index 2, pushing attributes after

    if (check(TokenType.TAG_START)) consume(TokenType.TAG_START)
    expect(TokenType.ENDTABLEROW)
    advance()
    consume(TokenType.TAG_END)

    new InsertionNode(insertions.get("tablerow").get, nodes)
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

    new InsertionNode(insertions.get("capture").get, Array[LNode](new AtomNode(DataView.from(id)), block))
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
        break(new InsertionNode(insertions.get("comment").get, Array.empty[LNode]))
      }
      advance()
    }

    new InsertionNode(insertions.get("comment").get, Array.empty[LNode])
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
          break(new InsertionNode(insertions.get("raw").get, Array[LNode](new AtomNode(DataView.from(sb.toString())))))
        }
      }
      sb.append(t.value)
      advance()
    }

    new InsertionNode(insertions.get("raw").get, Array[LNode](new AtomNode(DataView.from(sb.toString()))))
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
    new InsertionNode(insertions.get("cycle").get, nodes)
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
      // file_name_or_output (LiquidParser.g4:219-222) is EITHER an `output`
      // (`{{ expr }}` — #jekyll_include_output) OR a literal `filename`
      // (#jekyll_include_filename). When the include name is given as an output
      // expression, NodeVisitor.visitJekyll_include_output (NodeVisitor.java:498-504)
      // simply delegates to visitOutput, producing the same OutputNode an output
      // tag would. getJekyllIncludeInsertionNode (NodeVisitor.java:478-485) then
      // uses it as nodes[0], which Include (tags/Include.java:26) renders to the
      // filename string — exactly as it renders a literal-name AtomNode.
      if (check(TokenType.OUT_START)) {
        nodes.add(parseOutput())
      } else {
        // file_name_or_output (LiquidParser.g4:219-222): a quoted string, or an
        // unquoted `filename : ( . )+?` (LiquidParser.g4:359-361) — a lazy run of
        // any tokens reassembled into the raw file name (NodeVisitor.java:512-526
        // reads the source-text interval spanning the run).
        val fileName = if (check(TokenType.STR)) {
          val s = peek().value
          advance()
          s
        } else {
          // Unquoted Jekyll file name: assemble the Id/Dot/PathSep/... token run
          // (e.g. `dir/sub/file.html`) up to the params/TagEnd boundary.
          parseJekyllIncludeFileName()
        }
        nodes.add(new AtomNode(DataView.from(fileName)))
      }

      // jekyll_include_params (LiquidParser.g4:224-227): `id '=' expr`
      while (isIdLike(peek().tokenType) && !check(TokenType.TAG_END)) {
        val key = consumeId()
        consume(TokenType.EQ_SIGN)
        val value = parseExpr()
        nodes.add(new KeyValueNode(key, value))
      }
    }

    consume(TokenType.TAG_END)
    new InsertionNode(insertions.get("include").get, nodes)
  }

  /** Assembles an unquoted Jekyll include file name from the token run.
    *
    * Mirrors liqp's `filename : ( . )+?` (LiquidParser.g4:359-361): a non-greedy (lazy) match of any tokens. The match stops as soon as the following `jekyll_include_params` (`id '=' expr`,
    * LiquidParser.g4:224-227) or the closing TagEnd can begin.
    *
    * liqp does NOT concatenate token text. It reassembles the *raw source interval* spanning the run — `Interval.of(filename().start.getStartIndex(), filename().stop.getStopIndex())` — and then
    * rejects any name that contains whitespace (NodeVisitor.java:519-524):
    * {{{
    *   if (filename.matches(".*\\s.*"))
    *     throw new LiquidException("in `{% include filename %}` the `filename` is {" + filename +
    *       "}, but it cannot have spaces for Flavor.JEKYLL", ctx);
    * }}}
    * Whitespace lives in the lexer's hidden channel and so does not surface as a token, but it DOES surface as a positional gap: every token carries its source `line`/`col`, so two consecutive tokens
    * that are *not* physically adjacent (a different line, or a `col` beyond where the prior token's source text ends) prove hidden whitespace between them. We reconstruct the raw interval the same
    * way liqp's `getText(interval)` would: replay each token's source span — restoring the quote characters the lexer stripped from STR tokens (`value` holds the unquoted content, but the source
    * occupied two extra columns) and re-emitting the hidden whitespace between non-adjacent tokens — then apply liqp's whitespace check to the *whole* assembled name. So the run is consumed in full
    * first (matching `start`..`stop`), and only then, if the complete assembled name contains any whitespace — whether between tokens (a reconstructed gap) or inside a token (e.g. a space within a
    * quoted STR run such as `a"b c".html`) — liqp's exact exception is thrown carrying the complete `{name}`, mirroring `filename.matches(".*\\s.*")` over the raw interval (NodeVisitor.java:522).
    */
  private def parseJekyllIncludeFileName(): String = {
    val sb = new java.lang.StringBuilder()
    // Source line/end-column of the previously consumed run token (the end column is exclusive).
    // `prevLine` < 0 marks "no token consumed yet" so the first token never registers a gap.
    var prevLine      = -1
    var prevEndCol    = 0
    var hasWhitespace = false
    // Position of the first reconstructed whitespace (for the thrown exception's line/col).
    var wsLine = 0
    var wsCol  = 0
    while (
      !check(TokenType.TAG_END) && !isAtEnd &&
      // Boundary: an `id '=' ...` run starts a jekyll_include_params, which
      // ends the lazy filename match — but only after at least one token has
      // been consumed so the name is non-empty.
      !(prevLine >= 0 && isIdLike(peek().tokenType) && peekNext().tokenType == TokenType.EQ_SIGN)
    ) {
      val t = peek()
      // Hidden whitespace between this token and the previous one in the run shows up as a line
      // change or a start column past where the previous token's source text ended. We replay it
      // into the assembled name (the interval `getText` would have included it) and remember it, so
      // the whole name is built before liqp's whitespace check (NodeVisitor.java:522-524) runs.
      if (prevLine >= 0 && t.line != prevLine) {
        if (!hasWhitespace) { wsLine = prevLine; wsCol = prevEndCol }
        hasWhitespace = true
        sb.append('\n')
      } else if (prevLine >= 0 && t.col > prevEndCol) {
        if (!hasWhitespace) { wsLine = prevLine; wsCol = prevEndCol }
        hasWhitespace = true
        var gap = t.col - prevEndCol
        while (gap > 0) { sb.append(' '); gap -= 1 }
      }
      sb.append(rawText(t))
      // Advance the source cursor past this token's raw span (STR adds back the two quote columns).
      prevLine = t.line
      prevEndCol = t.col + (if (t.tokenType == TokenType.STR) t.value.length + 2 else t.value.length)
      advance()
    }
    if (prevLine < 0) {
      // No tokens consumed: not a valid file name (matches consumeId's error).
      val t = peek()
      throw new LiquidException(
        s"Expected include file name but got ${t.tokenType} ('${t.value}')",
        t.line,
        t.col
      )
    }
    val fileName = sb.toString
    // valid filename in jekyll doesn't allow whitespaces (NodeVisitor.java:516, 522-524).
    // liqp's check `filename.matches(".*\\s.*")` (NodeVisitor.java:522) runs over the WHOLE
    // assembled raw interval, so it also catches whitespace that lives INSIDE a STR token in
    // an unquoted name run (e.g. `{% include a"b c".html %}` assembles to `a"b c".html`, whose
    // STR value `b c` carries a space with no positional gap between tokens). We scan the entire
    // assembled name rather than only the reconstructed inter-token gaps. `wsLine`/`wsCol` still
    // point at the first reconstructed gap when one exists; for whitespace embedded inside a token
    // there is no gap, so the run's start position (0/0) is reported, mirroring liqp's `ctx`.
    if (fileName.exists(_.isWhitespace)) {
      throw new LiquidException(
        s"in `{% include filename %}` the `filename` is {$fileName}, but it cannot have spaces for Flavor.JEKYLL",
        wsLine,
        wsCol
      )
    }
    fileName
  }

  /** Source-text spelling of a single token within an unquoted Jekyll include file-name run.
    *
    * For most tokens this is the literal `value`. STR tokens are special: the lexer strips the surrounding quotes into `value` (LiquidLexer.scala:386-402), but liqp's raw-interval read keeps them
    * (NodeVisitor.java:519-520), so `{% include a"b" %}` reassembles to `a"b"`. We restore a double quote; the lexer does not record which quote glyph was used, but an unquoted file name embedding a
    * quote is degenerate either way and the only contract that matters is that the quote is not silently dropped.
    */
  private def rawText(t: Token): String =
    if (t.tokenType == TokenType.STR) "\"" + t.value + "\"" else t.value

  /** include_relative_tag */
  private def parseIncludeRelativeTag(): LNode = {
    advance() // consume INCLUDE_RELATIVE
    val nodes = new ArrayList[LNode]()
    // include_relative also uses file_name_or_output (LiquidParser.g4:215), so
    // unquoted dotted/slashed names are assembled the same way as `include`.
    val fileName = if (check(TokenType.STR)) {
      val s = peek().value
      advance()
      s
    } else {
      parseJekyllIncludeFileName()
    }
    nodes.add(new AtomNode(DataView.from(fileName)))

    // key=value params
    while (isIdLike(peek().tokenType) && !check(TokenType.TAG_END)) {
      val key = consumeId()
      consume(TokenType.EQ_SIGN)
      val value = parseExpr()
      nodes.add(new KeyValueNode(key, value))
    }

    consume(TokenType.TAG_END)
    val incRelative = insertions.get("include_relative")
    if (incRelative.isDefined) {
      new InsertionNode(incRelative.get, nodes)
    } else {
      new InsertionNode(insertions.get("include").get, nodes)
    }
  }

  private def parseBreakTag(): LNode = {
    advance() // consume BREAK_TAG
    consume(TokenType.TAG_END)
    new InsertionNode(insertions.get("break").get, Array.empty[LNode])
  }

  private def parseContinueTag(): LNode = {
    advance() // consume CONTINUE_TAG
    consume(TokenType.TAG_END)
    new InsertionNode(insertions.get("continue").get, Array.empty[LNode])
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

    // Track the open block so end-tag classification can mirror liqp's lexer
    // customBlockState stack (LiquidLexer.g4:251, 258-262).
    openBlocks.add(tagName)
    val block = parseBlock()
    params.add(block)

    // Consume end tag. parseBlock stops at the first end-like tag; here we
    // classify it the way liqp's lexer would (LiquidLexer.g4:256-279) and apply
    // the matching error_other_tag case (LiquidParser.g4:99-105).
    if (check(TokenType.TAG_START)) {
      consume(TokenType.TAG_START)
      val endToken = peek()
      val endName  = endToken.value
      if (endToken.tokenType == TokenType.END_BLOCK_ID) {
        val suffix = if (endName.length > 3 && endName.startsWith("end")) endName.substring(3) else endName
        if (suffix == tagName) {
          // EndBlockId matching the open block (LiquidLexer.g4:261-263).
          removeLastOpenBlock()
          advance() // consume END_BLOCK_ID
          consume(TokenType.TAG_END)
        } else {
          // A registered end block that does not match the open block:
          // MisMatchedEndBlockId → "Mismatched End Tag" (LiquidParser.g4:99-100).
          // The parser listener throws unconditionally (Template.java:91-98).
          reportTokenError("Mismatched End Tag", endToken)
        }
      } else if (endName.startsWith("end")) {
        // An `end...` identifier that is not a registered end block:
        // InvalidEndBlockId → "Invalid End Tag" (LiquidParser.g4:102-103).
        // The parser listener throws unconditionally (Template.java:91-98).
        reportTokenError("Invalid End Tag", endToken)
      } else {
        // No end tag at all for this block: the block body ran into some other
        // construct. Missing End Tag (LiquidParser.g4:105). Throws unconditionally
        // (Template.java:91-98).
        reportTokenError("Missing End Tag")
      }
    } else {
      // EOF reached without an end tag → Missing End Tag (LiquidParser.g4:105).
      // Throws unconditionally (Template.java:91-98).
      reportTokenError("Missing End Tag")
    }

    if (insertion.isDefined) {
      new InsertionNode(insertion.get, params)
    } else {
      block // fallback: just render the block content
    }
  }

  /** Removes the most recently opened block from the stack, if any. Mirrors liqp's `customBlockState.pop()` (LiquidLexer.g4:262).
    */
  private def removeLastOpenBlock(): Unit =
    if (!openBlocks.isEmpty) openBlocks.remove(openBlocks.size() - 1)

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

    if (insertion.isDefined) {
      new InsertionNode(insertion.get, params)
    } else {
      new AtomNode(DataView.from("")) // unknown tag, produce empty
    }
  }

  /** Parses increment/decrement tags where the parameter is a variable name literal (not a lookup). */
  private def parseIncrementDecrementTag(tagName: String): LNode = {
    advance() // consume tag name ID (already read by caller via peek)
    val insertion = insertions.get(tagName)
    val varName   = consumeId() // The variable name as a literal string
    consume(TokenType.TAG_END)

    if (insertion.isDefined) {
      new InsertionNode(insertion.get, Array[LNode](new AtomNode(DataView.from(varName))))
    } else {
      new AtomNode(DataView.from(""))
    }
  }

  /** Generic tag handler for unknown tags.
    *
    * The SSG lexer collapses liqp's InvalidTagId / InvalidEndBlockId classification (LiquidLexer.g4:247-280) into a plain `ID` token, so the distinction is reconstructed here using the same rules and
    * reported via the matching error_other_tag case (LiquidParser.g4:102-107).
    */
  private def parseGenericTag(): LNode = {
    val tagToken  = peek()
    val tagName   = tagToken.value
    val insertion = insertions.get(tagName)

    // An unregistered `end...` identifier is an end-tag error, not an invalid
    // (start) tag. liqp's lexer would emit InvalidEndBlockId for it
    // (LiquidLexer.g4:256-273), reported as "Invalid End Tag"
    // (LiquidParser.g4:102-103). Whether or not a block is open, an `end...`
    // that does not close the current block is invalid here: if it closed the
    // open block it would have been an END_BLOCK_ID handled in the block parser.
    if (insertion.isEmpty && tagName.length > 3 && tagName.startsWith("end")) {
      // The parser listener throws unconditionally in all error modes
      // (Template.java:91-98); error_other_tag has no errorMode predicate
      // (LiquidParser.g4:98-110).
      reportTokenError("Invalid End Tag", tagToken)
    } else {
      advance() // consume tag name ID

      val params = new ArrayList[LNode]()
      while (!check(TokenType.TAG_END) && !isAtEnd) {
        params.add(parseExpr())
        if (check(TokenType.COMMA)) advance()
      }
      consume(TokenType.TAG_END)

      if (insertion.isDefined) {
        new InsertionNode(insertion.get, params)
      } else {
        // Unregistered start tag → Invalid Tag (LiquidParser.g4:106-107). The
        // parser listener throws unconditionally in all error modes
        // (Template.java:91-98); error_other_tag has no errorMode predicate.
        reportTokenError("Invalid Tag", tagToken)
      }
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
        new AtomNode(DataView.from(java.lang.Double.parseDouble(t.value)))
      case TokenType.LONG_NUM =>
        advance()
        new AtomNode(DataView.from(java.lang.Long.parseLong(t.value)))
      case TokenType.STR =>
        advance()
        new AtomNode(DataView.from(t.value))
      case TokenType.TRUE =>
        advance()
        new AtomNode(DataView.from(true))
      case TokenType.FALSE =>
        advance()
        new AtomNode(DataView.from(false))
      case TokenType.NIL =>
        advance()
        new AtomNode(DataView.nil)
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
          new AtomNode(DataView.from(t.value))
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
          // liqp IdChain after-dot segment is [a-zA-Z_0-9]+ (LiquidLexer.g4:182-184) and
          // EXCLUDES '-', so a '-'-prefixed numeric key after '.' is a parse error in liqp.
          if ((check(TokenType.LONG_NUM) || check(TokenType.DOUBLE_NUM)) && peek().value.startsWith("-")) {
            val t = peek()
            throw new LiquidException(
              s"Invalid dotted key '${t.value}': numeric path segments after '.' may not begin with '-' (liqp IdChain [a-zA-Z_0-9]+, LiquidLexer.g4:182-184)",
              t.line,
              t.col
            )
          }
          // liqp's IdChain (LiquidLexer.g4:182-184) tokenizes dotted chains like
          // `Data.1.Value` as Id("Data") Dot Id("1") Dot Id("Value"), because the
          // Id rule matches a leading digit (LiquidLexer.g4:186). ssg's lexer lacks
          // IdChain, so a digit after DOT becomes LONG_NUM, and `1.Value` greedily
          // becomes DOUBLE_NUM("1.") + Id("Value") (scanNumber consumes the trailing
          // dot as a decimal point). Handle both numeric tokens to match liqp's
          // Hash-index semantics (NodeVisitor.java:812-813).
          if (check(TokenType.LONG_NUM)) {
            // Simple case: `a.0` → Hash("0"). The number has no embedded dot.
            lookup.add(new LookupNode.Hash(peek().value))
            advance()
          } else if (check(TokenType.DOUBLE_NUM)) {
            // The lexer greedily consumed a '.' as a decimal point (scanNumber). Split
            // the value at '.' to emit separate Hash segments per liqp IdChain semantics.
            addDoubleNumHashSegments(lookup)
          } else {
            val prop = consumeIdOrKeyword()
            lookup.add(new LookupNode.Hash(prop))
          }
        } else {
          advance() // consume [
          val indexExpr = parseExpr()
          consume(TokenType.CBR) // consume ]
          val indexText = indexExpr match {
            case atom: AtomNode => atom.render(null).toString
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

  /** Peeks one token past the current position (single-token lookahead). */
  private def peekNext(): Token =
    if (pos + 1 < tokens.size()) tokens.get(pos + 1)
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

  // Built-in block-end tokens (IfEnd/UnlessEnd/CaseEnd/ForEnd/TablerowEnd/CaptureEnd,
  // LiquidParser.g4:134 etc.) and top-level EOF appear in grammar rules that carry NO
  // errorMode predicate, so the end token is REQUIRED in all modes. A missing/mismatched
  // end produces an ANTLR error and the parser listener (Template.java:91-98) throws
  // unconditionally — regardless of STRICT/WARN/LAX. Mirror that here by throwing in all
  // modes (matching reportTokenError's unconditional throw).
  private def expect(expected: TokenType): Unit =
    if (peek().tokenType != expected) {
      val t = peek()
      throw new LiquidException(
        s"Expected $expected but got ${t.tokenType} ('${t.value}')",
        t.line,
        t.col
      )
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

  /** Handles a DOUBLE_NUM token inside a dotted property chain.
    *
    * ssg's lexer lacks liqp's IdChain rule (LiquidLexer.g4:182-184), so it may greedily scan a digit-dot sequence as a single DOUBLE_NUM. For example, in `Data.1.Value` the lexer emits
    * DOUBLE_NUM("1.") + Id("Value") instead of liqp's Id("1") Dot Id("Value"). This method splits the DOUBLE_NUM value at '.' to produce the correct Hash segments:
    *
    *   - "1.5" -> Hash("1"), Hash("5") (liqp: Id("1") Dot Id("5"))
    *   - "1." -> Hash("1"), then consume next token as another Hash segment (the trailing dot is a separator, not a decimal point)
    */
  private def addDoubleNumHashSegments(lookup: LookupNode): Unit = {
    val raw = peek().value
    advance()
    val dotIdx = raw.indexOf('.')
    if (dotIdx < 0) {
      // No dot — unlikely for DOUBLE_NUM but handle defensively.
      lookup.add(new LookupNode.Hash(raw))
    } else {
      val before = raw.substring(0, dotIdx)
      val after  = raw.substring(dotIdx + 1)
      if (before.nonEmpty) {
        lookup.add(new LookupNode.Hash(before))
      }
      if (after.nonEmpty) {
        // "1.5" → Hash("1"), Hash("5") — matches liqp IdChain splitting
        lookup.add(new LookupNode.Hash(after))
      } else {
        // "1." → Hash("1"), consumed dot is a separator. The next token is
        // the segment after the dot (e.g. Id("Value") in `Data.1.Value`).
        if (check(TokenType.LONG_NUM)) {
          lookup.add(new LookupNode.Hash(peek().value))
          advance()
        } else if (check(TokenType.DOUBLE_NUM)) {
          addDoubleNumHashSegments(lookup)
        } else if (
          !check(TokenType.DOT) && !check(TokenType.OBR) &&
          !check(TokenType.OUT_END) && !check(TokenType.TAG_END) &&
          !check(TokenType.PIPE) && !check(TokenType.EOF)
        ) {
          val prop = consumeIdOrKeyword()
          lookup.add(new LookupNode.Hash(prop))
        }
        // If next is DOT/OBR/OUT_END/TAG_END/PIPE/EOF, the while loop or
        // caller will handle it (e.g. `{{ a.1. | filter }}` — trailing dot
        // with no segment is silently ignored, matching liqp's behavior where
        // `a.1.` would be an error anyway).
      }
    }
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
