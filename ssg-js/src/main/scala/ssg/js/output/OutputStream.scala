/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: BSD-2-Clause
 *
 * JavaScript code generator — converts an AST back to JavaScript source code.
 *
 * Supports minified and beautified output, optional comment preservation,
 * configurable quote styles, number formatting, and correct parenthesization
 * to ensure the output is always parseable.
 *
 * Original source: terser lib/output.js
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: OutputStream function -> ssg.js.output.OutputStream class
 *   Convention: DEFPRINT dispatch -> pattern match in codeGen
 *   Convention: PARENS dispatch -> pattern match in needsParens
 *   Idiom: StringBuilder replaces Rope; boundary/break replaces return
 *   Idiom: Source map support omitted (not needed for first version)
 *
 * Covenant: full-port
 * Covenant-js-reference: terser lib/output.js
 * Covenant-verified: 2026-04-26
 */
package ssg
package js
package output

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break
import ssg.js.ast.*
import ssg.js.parse.Token

/** Main JavaScript code generator.
  *
  * Converts an AST tree back into JavaScript source text. The output is controlled by [[OutputOptions]] — it can produce minified or beautified code, preserve or strip comments, and handle various
  * quoting/formatting preferences.
  *
  * Key invariant: `parse(output(ast))` must produce an equivalent AST. Correct parenthesization, semicolon insertion, and operator spacing are critical to maintaining this invariant.
  */
class OutputStream(val options: OutputOptions = OutputOptions()) {

  // ---- internal state ----
  private var indentation: Int           = 0
  private var currentCol:  Int           = 0
  private var currentLine: Int           = 1
  private var currentPos:  Int           = 0
  private val output:      StringBuilder = new StringBuilder()

  private var _hasParens:          Boolean = false
  private var mightNeedSpace:      Boolean = false
  private var mightNeedSemicolon:  Boolean = false
  private var mightAddNewline:     Int     = 0
  private var needNewlineIndented: Boolean = false
  private var needSpace:           Boolean = false
  private var newlineInsert:       Int     = -1
  private var last:                String  = ""

  private val printedComments: mutable.Set[AnyRef]          = mutable.Set.empty
  private val stack:           mutable.ArrayBuffer[AstNode] = mutable.ArrayBuffer.empty

  /** Track directive context for string escaping. */
  var inDirective: Boolean = false

  /** Track `"use asm"` scope for number formatting. */
  var useAsm: AstScope | Null = null

  /** Track the active scope for use_asm detection. */
  var activeScope: AstScope | Null = null

  // Characters that require a semicolon before them (when semicolons=false)
  private val requireSemicolonChars: Set[Char] = Set('(', '[', '+', '*', '/', '-', ',', '.', '`')

  // Annotation pattern: @__PURE__, @__INLINE__, @__NOINLINE__
  private val annotationPattern = "[@#]__(PURE|INLINE|NOINLINE)__".r

  // ---- comment filter ----
  private val commentFilter: AstToken => Boolean = buildCommentFilter()

  private def buildCommentFilter(): AstToken => Boolean = {
    val base: AstToken => Boolean = if (options.comments == "false" || options.comments.isEmpty) { _ =>
      false
    } else if (options.comments == "some") {
      isSomeComments
    } else if (options.comments == "all") { c =>
      c.tokenType != Token.Comment5
    } else if (options.comments.startsWith("/") && options.comments.lastIndexOf('/') > 0) {
      val lastSlash = options.comments.lastIndexOf('/')
      val pattern   = options.comments.substring(1, lastSlash)
      val flags     = options.comments.substring(lastSlash + 1)
      val regex     = if (flags.contains('i')) ("(?i)" + pattern).r else pattern.r
      c => c.tokenType != Token.Comment5 && regex.findFirstIn(c.value).isDefined
    } else { c =>
      c.tokenType != Token.Comment5
    }

    if (options.preserveAnnotations) { c =>
      annotationPattern.findFirstIn(c.value).isDefined || base(c)
    } else {
      base
    }
  }

  /** Check if a comment is an "important" comment (preserve/copyright/license). */
  private def isSomeComments(comment: AstToken): Boolean =
    (comment.tokenType == Token.Comment2 || comment.tokenType == Token.Comment1) && {
      val v = comment.value
      v.contains("@preserve") || v.contains("@copyright") ||
      v.contains("@lic") || v.contains("@cc_on") ||
      (v.nonEmpty && v.charAt(0) == '!')
    }

  // ========================================================================
  // Core output methods
  // ========================================================================

  /** Encode a string for ASCII-only output or escape lone surrogates. */
  private def toUtf8(str: String, identifier: Boolean = false): String =
    if (!options.asciiOnly) {
      val sb = new StringBuilder(str.length)
      var i  = 0
      while (i < str.length) {
        val c = str.charAt(i)
        if (Character.isHighSurrogate(c) && i + 1 < str.length && Character.isLowSurrogate(str.charAt(i + 1))) {
          sb.append(c)
          sb.append(str.charAt(i + 1))
          i += 2
        } else if (Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
          sb.append("\\u")
          sb.append(String.format("%04x", c.toInt))
          i += 1
        } else {
          sb.append(c)
          i += 1
        }
      }
      sb.toString()
    } else {
      val sb = new StringBuilder(str.length)
      var i  = 0
      while (i < str.length) {
        val c = str.charAt(i)
        if (Character.isHighSurrogate(c) && i + 1 < str.length && Character.isLowSurrogate(str.charAt(i + 1))) {
          if (options.ecma >= 2015) {
            val cp = Character.toCodePoint(c, str.charAt(i + 1))
            sb.append("\\u{")
            sb.append(Integer.toHexString(cp))
            sb.append("}")
          } else {
            sb.append("\\u")
            sb.append(String.format("%04x", c.toInt))
            sb.append("\\u")
            sb.append(String.format("%04x", str.charAt(i + 1).toInt))
          }
          i += 2
        } else if (c.toInt >= 0x80 || c.toInt < 0x20) {
          val hex = Integer.toHexString(c.toInt)
          if (hex.length <= 2 && !identifier) {
            sb.append("\\x")
            sb.append("0" * (2 - hex.length))
            sb.append(hex)
          } else {
            sb.append("\\u")
            sb.append("0" * (4 - hex.length))
            sb.append(hex)
          }
          i += 1
        } else {
          sb.append(c)
          i += 1
        }
      }
      sb.toString()
    }

  /** Escape a string for output, choosing best quote style. */
  private def makeString(str: String, quote: String): String = {
    var dq = 0
    var sq = 0
    val sb = new StringBuilder(str.length)
    var i  = 0
    while (i < str.length) {
      str.charAt(i) match {
        case '"'      => dq += 1; sb.append('"')
        case '\''     => sq += 1; sb.append('\'')
        case '\\'     => sb.append("\\\\")
        case '\n'     => sb.append("\\n")
        case '\r'     => sb.append("\\r")
        case '\t'     => sb.append("\\t")
        case '\b'     => sb.append("\\b")
        case '\f'     => sb.append("\\f")
        case '\u000b' => sb.append("\\v")
        case '\u2028' => sb.append("\\u2028")
        case '\u2029' => sb.append("\\u2029")
        case '\ufeff' => sb.append("\\ufeff")
        case '\u0000' =>
          if (i + 1 < str.length && str.charAt(i + 1) >= '0' && str.charAt(i + 1) <= '9') {
            sb.append("\\x00")
          } else {
            sb.append("\\0")
          }
        case other => sb.append(other)
      }
      i += 1
    }

    val escaped = toUtf8(sb.toString())
    def quoteSingle():   String = "'" + escaped.replace("'", "\\'") + "'"
    def quoteDouble():   String = "\"" + escaped.replace("\"", "\\\"") + "\""
    def quoteTemplate(): String = "`" + escaped.replace("`", "\\`") + "`"

    if (quote == "`") quoteTemplate()
    else
      options.quoteStyle match {
        case 1 => quoteSingle()
        case 2 => quoteDouble()
        case 3 => if (quote == "'") quoteSingle() else quoteDouble()
        case _ => if (dq > sq) quoteSingle() else quoteDouble()
      }
  }

  /** Encode a string for output, handling inline script escaping. */
  private def encodeString(str: String, quote: String): String = {
    var ret = makeString(str, quote)
    if (options.inlineScript) {
      ret = ret.replaceAll("(?i)</(script)([>/ \\t\\n\\f\\r])", "<\\\\/$1$2")
      ret = ret.replace("<!--", "\\x3c!--")
      ret = ret.replace("-->", "--\\x3e")
    }
    ret
  }

  private def makeName(name: String): String = toUtf8(name, identifier = true)

  private def makeIndent(back: Double): String = {
    val count = options.indentStart + indentation - (back * options.indentLevel).toInt
    if (count <= 0) "" else " " * count
  }

  // ========================================================================
  // Beautification / minification helpers
  // ========================================================================

  /** Output a string to the buffer, handling spacing and semicolon insertion. */
  def print(str: String): Unit = {
    val ch = if (str.nonEmpty) str.charAt(0) else '\u0000'

    if (needNewlineIndented && ch != '\u0000') {
      needNewlineIndented = false
      if (ch != '\n') {
        print("\n")
        indent()
      }
    }

    if (needSpace && ch != '\u0000') {
      needSpace = false
      if (ch != ' ' && ch != ';' && ch != '}' && ch != ')') space()
    }

    newlineInsert = -1
    val prev = if (last.nonEmpty) last.charAt(last.length - 1) else '\u0000'

    if (mightNeedSemicolon) {
      mightNeedSemicolon = false
      if ((prev == ':' && ch == '}') || (ch == '\u0000' || !";}".contains(ch)) && prev != ';') {
        if (options.semicolons || (ch != '\u0000' && requireSemicolonChars.contains(ch))) {
          output.append(";")
          currentCol += 1
          currentPos += 1
        } else {
          ensureLineLen()
          if (currentCol > 0) {
            output.append("\n")
            currentPos += 1
            currentLine += 1
            currentCol = 0
          }
          if (str.nonEmpty && str.forall(c => c == ' ' || c == '\t' || c == '\n')) {
            mightNeedSemicolon = true
          }
        }
        if (!options.beautify) mightNeedSpace = false
      }
    }

    if (mightNeedSpace) {
      if (
        (isIdentifierChar(prev) && (isIdentifierChar(ch) || ch == '\\'))
        || (ch == '/' && ch == prev)
        || ((ch == '+' || ch == '-') && ch == last.lastOption.getOrElse('\u0000'))
      ) {
        output.append(" ")
        currentCol += 1
        currentPos += 1
      }
      mightNeedSpace = false
    }

    output.append(str)
    _hasParens = str.nonEmpty && str.charAt(str.length - 1) == '('
    currentPos += str.length
    val lines = str.split("\r?\n", -1)
    val n     = lines.length - 1
    currentLine += n
    currentCol += lines(0).length
    if (n > 0) {
      ensureLineLen()
      currentCol = lines(n).length
    }
    last = str
  }

  private def isIdentifierChar(ch: Char): Boolean =
    (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') ||
      (ch >= '0' && ch <= '9') || ch == '_' || ch == '$' ||
      ch == '\\' || ch.toInt >= 0x80

  private def ensureLineLen(): Unit =
    if (options.maxLineLen > 0) {
      if (currentCol > options.maxLineLen && mightAddNewline > 0) {
        output.insert(mightAddNewline, '\n')
        val lenAfterNewline = output.length - mightAddNewline - 1
        currentLine += 1
        currentPos += 1
        currentCol = lenAfterNewline
      }
      if (mightAddNewline > 0) mightAddNewline = 0
    }

  def star(): Unit = print("*")

  def space(): Unit =
    if (options.beautify) print(" ") else mightNeedSpace = true

  def indent(half: Boolean = false): Unit =
    if (options.beautify) print(makeIndent(if (half) 0.5 else 0))

  def withIndent[T](col: Int, cont: () => T): T =
    if (options.beautify) {
      val save = indentation
      indentation = col
      val ret = cont()
      indentation = save
      ret
    } else cont()

  def newline(): Unit =
    if (options.beautify) {
      if (newlineInsert < 0) print("\n")
      else {
        if (newlineInsert < output.length && output.charAt(newlineInsert) != '\n') {
          output.insert(newlineInsert, '\n')
          currentPos += 1
          currentLine += 1
        }
        newlineInsert += 1
      }
    } else if (options.maxLineLen > 0) {
      ensureLineLen()
      mightAddNewline = output.length
    }

  def semicolon(): Unit =
    if (options.beautify) print(";") else mightNeedSemicolon = true

  def forceSemicolon(): Unit = {
    mightNeedSemicolon = false
    print(";")
  }

  def nextIndent(): Int = indentation + options.indentLevel

  def withBlock[T](cont: () => T): T = {
    print("{")
    newline()
    val ret = withIndent(nextIndent(), cont)
    indent()
    print("}")
    ret
  }

  def withParens[T](cont: () => T): T = {
    print("(")
    val ret = cont()
    print(")")
    ret
  }

  def withSquare[T](cont: () => T): T = {
    print("[")
    val ret = cont()
    print("]")
    ret
  }

  def comma(): Unit = { print(","); space() }
  def colon(): Unit = { print(":"); space() }

  def get(): String = {
    if (mightAddNewline > 0) ensureLineLen()
    output.toString()
  }

  def hasParens:          Boolean = _hasParens
  def lastOutput:         String  = last
  def line:               Int     = currentLine
  def col:                Int     = currentCol
  def pos:                Int     = currentPos
  def currentIndentation: Int     = indentation
  def currentWidth:       Int     = currentCol - indentation
  def shouldBreak:        Boolean = options.width > 0 && currentWidth >= options.width

  def printName(name: String): Unit = print(makeName(name))

  def printString(str: String, quote: String = "\"", escapeDirective: Boolean = false): Unit = {
    val encoded = encodeString(str, quote)
    if (escapeDirective && !encoded.contains("\\")) {
      if (!expectDirective()) forceSemicolon()
      forceSemicolon()
    }
    print(encoded)
  }

  def printTemplateStringChars(str: String): Unit = {
    val encoded = encodeString(str, "`").replaceAll("\\$\\{", "\\\\\\${")
    print(encoded.substring(1, encoded.length - 1))
  }

  private def expectDirective(): Boolean = {
    val n = output.length
    if (n <= 0) true
    else {
      boundary[Boolean] {
        var i = n - 1
        while (i >= 0 && { val ch = output.charAt(i); ch == ' ' || ch == '\n' }) i -= 1
        i < 0 || output.charAt(i) == ';' || output.charAt(i) == '{'
      }
    }
  }

  private def hasNLB: Boolean =
    boundary[Boolean] {
      var j = output.length - 1
      while (j >= 0) {
        val code = output.charAt(j)
        if (code == '\n') break(true)
        if (code != ' ') break(false)
        j -= 1
      }
      true
    }

  // ========================================================================
  // Node stack management
  // ========================================================================

  def pushNode(node: AstNode): Unit    = stack.addOne(node)
  def popNode():               AstNode = stack.remove(stack.length - 1)

  /** Get a parent node from the stack. parent(0) = direct parent, parent(-1) = self. */
  def parent(n: Int): AstNode | Null = {
    val idx = stack.length - 2 - n
    if (idx >= 0 && idx < stack.length) stack(idx) else null
  }

  // ========================================================================
  // Comment output
  // ========================================================================

  private def filterComment(comment: String): String = {
    var result = comment
    if (!options.preserveAnnotations) {
      result = annotationPattern.replaceAllIn(result, " ")
    }
    if (result.trim.isEmpty) ""
    else result.replaceAll("(?i)(<\\s*/\\s*)(script)", "<\\\\/$2")
  }

  def prependComments(node: AstNode): Unit = {
    val startTok = node.start
    if (startTok == AstToken.Empty) {
      if (currentPos == 0 && options.preamble.nonEmpty) {
        print(options.preamble.replaceAll("\\r\\n?|[\\n\\u2028\\u2029]|\\s*$", "\n"))
      }
    } else {
      val commentsBefore = startTok.commentsBefore
      if (commentsBefore.isEmpty) {
        if (currentPos == 0 && options.preamble.nonEmpty) {
          print(options.preamble.replaceAll("\\r\\n?|[\\n\\u2028\\u2029]|\\s*$", "\n"))
        }
      } else if (!printedComments.contains(commentsBefore)) {
        printedComments.add(commentsBefore)

        if (
          currentPos == 0 && commentsBefore.nonEmpty && options.shebang &&
          commentsBefore.head.tokenType == Token.Comment5 && !printedComments.contains(commentsBefore.head)
        ) {
          print("#!" + commentsBefore.head.value + "\n")
          indent()
        }
        if (currentPos == 0 && options.preamble.nonEmpty) {
          print(options.preamble.replaceAll("\\r\\n?|[\\n\\u2028\\u2029]|\\s*$", "\n"))
        }

        val filtered = commentsBefore.filter(c => commentFilter(c) && !printedComments.contains(c))
        if (filtered.nonEmpty) {
          var lastNlb = hasNLB
          filtered.zipWithIndex.foreach { case (c, i) =>
            printedComments.add(c)
            if (!lastNlb) {
              if (c.nlb) { print("\n"); indent(); lastNlb = true }
              else if (i > 0) space()
            }
            if (c.tokenType == Token.Comment1 || c.tokenType == Token.Comment3 || c.tokenType == Token.Comment4) {
              val value = filterComment(c.value)
              if (value.nonEmpty) { print("//" + value + "\n"); indent() }
              lastNlb = true
            } else if (c.tokenType == Token.Comment2) {
              val value = filterComment(c.value)
              if (value.nonEmpty) print("/*" + value + "*/")
              lastNlb = false
            }
          }
          if (!lastNlb) {
            if (startTok.nlb) { print("\n"); indent() }
            else space()
          }
        }
      }
    }
  }

  def appendComments(node: AstNode, tail: Boolean = false): Unit = {
    val endTok = node.end
    if (endTok != AstToken.Empty) {
      val comments = if (tail) endTok.commentsBefore else endTok.commentsAfter
      if (comments.nonEmpty && !printedComments.contains(comments)) {
        if (node.isInstanceOf[AstStatement] || comments.forall(c => c.tokenType != Token.Comment1 && c.tokenType != Token.Comment3 && c.tokenType != Token.Comment4)) {
          printedComments.add(comments)
          val insertPos = output.length
          val filtered  = comments.filter(c => commentFilter(c) && !printedComments.contains(c))
          filtered.zipWithIndex.foreach { case (c, i) =>
            printedComments.add(c)
            needSpace = false
            if (needNewlineIndented) { print("\n"); indent(); needNewlineIndented = false }
            else if (c.nlb && (i > 0 || !hasNLB)) { print("\n"); indent() }
            else if (i > 0 || !tail) space()

            if (c.tokenType == Token.Comment1 || c.tokenType == Token.Comment3 || c.tokenType == Token.Comment4) {
              val value = filterComment(c.value)
              if (value.nonEmpty) print("//" + value)
              needNewlineIndented = true
            } else if (c.tokenType == Token.Comment2) {
              val value = filterComment(c.value)
              if (value.nonEmpty) print("/*" + value + "*/")
              needSpace = true
            }
          }
          if (output.length > insertPos) newlineInsert = insertPos
        }
      }
    }
  }

  // ========================================================================
  // Parenthesization
  // ========================================================================

  def needsParens(node: AstNode): Boolean =
    node match {
      case _:     AstFunction                           => needsParensFunction(node)
      case arrow: AstArrow                              => needsParensArrow(arrow)
      case _:     AstObject                             => !hasParens && FirstInStatement.firstInStatement(this)
      case _:     AstClassExpression                    => FirstInStatement.firstInStatement(this)
      case u:     AstUnary                              => needsParensUnary(u)
      case aw:    AstAwait                              => needsParensAwait(aw)
      case seq:   AstSequence                           => needsParensSequence(seq)
      case bin:   AstBinary                             => needsParensBinary(bin)
      case pi:    AstPrivateIn                          => needsParensPrivateIn(pi)
      case y:     AstYield                              => needsParensYield(y)
      case ch:    AstChain                              => needsParensChain(ch)
      case pa:    AstPropAccess                         => needsParensPropAccess(pa)
      case call:  AstCall if !call.isInstanceOf[AstNew] => needsParensCall(call)
      case nw:    AstNew                                => needsParensNew(nw)
      case num:   AstNumber                             => needsParensNumber(num)
      case bi:    AstBigInt                             => needsParensBigInt(bi)
      case _: AstAssign | _: AstConditional => needsParensAssignConditional(node)
      case _                                => false
    }

  private def needsParensFunction(node: AstNode): Boolean =
    if (!hasParens && FirstInStatement.firstInStatement(this)) true
    else {
      val p = parent(0)
      if (options.webkit && p != null) {
        p.nn match {
          case pa: AstPropAccess if pa.expression != null && (pa.expression.nn eq node) => true
          case _ => checkWrapIifeAndArgs(node)
        }
      } else checkWrapIifeAndArgs(node)
    }

  private def checkWrapIifeAndArgs(node: AstNode): Boolean = {
    val p = parent(0)
    if (p == null) false
    else if (options.wrapIife) {
      p.nn match {
        case call: AstCall if call.expression != null && (call.expression.nn eq node) => true
        case _ => checkWrapFuncArgs(node)
      }
    } else checkWrapFuncArgs(node)
  }

  private def checkWrapFuncArgs(node: AstNode): Boolean =
    if (!options.wrapFuncArgs) false
    else {
      val p = parent(0)
      if (p == null) false
      else
        p.nn match {
          case call: AstCall if call.args.exists(_ eq node) => true
          case _ => false
        }
    }

  private def needsParensArrow(node: AstArrow): Boolean = {
    val p = parent(0)
    if (p == null) false
    else {
      val pp = p.nn
      (options.wrapFuncArgs && pp.isInstanceOf[AstCall] && pp.asInstanceOf[AstCall].args.exists(_ eq node)) ||
      (pp.isInstanceOf[AstPropAccess] && pp.asInstanceOf[AstPropAccess].expression != null &&
        (pp.asInstanceOf[AstPropAccess].expression.nn eq node)) ||
      (pp.isInstanceOf[AstConditional] && pp.asInstanceOf[AstConditional].condition != null &&
        (pp.asInstanceOf[AstConditional].condition.nn eq node))
    }
  }

  private def needsParensUnary(node: AstUnary): Boolean = {
    val p = parent(0)
    if (p == null) false
    else {
      val pp = p.nn
      (pp.isInstanceOf[AstPropAccess] && pp.asInstanceOf[AstPropAccess].expression != null &&
        (pp.asInstanceOf[AstPropAccess].expression.nn eq node)) ||
      (pp.isInstanceOf[AstCall] && pp.asInstanceOf[AstCall].expression != null &&
        (pp.asInstanceOf[AstCall].expression.nn eq node)) ||
      (pp.isInstanceOf[AstBinary] && pp.asInstanceOf[AstBinary].operator == "**" &&
        node.isInstanceOf[AstUnaryPrefix] &&
        pp.asInstanceOf[AstBinary].left != null && (pp.asInstanceOf[AstBinary].left.nn eq node) &&
        node.operator != "++" && node.operator != "--")
    }
  }

  private def needsParensAwait(node: AstAwait): Boolean = {
    val p = parent(0)
    if (p == null) false
    else {
      val pp = p.nn
      (pp.isInstanceOf[AstPropAccess] && pp.asInstanceOf[AstPropAccess].expression != null &&
        (pp.asInstanceOf[AstPropAccess].expression.nn eq node)) ||
      (pp.isInstanceOf[AstCall] && pp.asInstanceOf[AstCall].expression != null &&
        (pp.asInstanceOf[AstCall].expression.nn eq node)) ||
      (pp.isInstanceOf[AstBinary] && pp.asInstanceOf[AstBinary].operator == "**" &&
        pp.asInstanceOf[AstBinary].left != null && (pp.asInstanceOf[AstBinary].left.nn eq node))
    }
  }

  private def needsParensSequence(node: AstSequence): Boolean = {
    val p = parent(0)
    if (p == null) false
    else {
      val pp = p.nn
      pp.isInstanceOf[AstCall] || pp.isInstanceOf[AstUnary] || pp.isInstanceOf[AstBinary] ||
      pp.isInstanceOf[AstVarDefLike] ||
      (pp.isInstanceOf[AstPropAccess] && pp.isInstanceOf[AstSub] && {
        val sub = pp.asInstanceOf[AstSub]
        sub.property match { case n: AstNode => !(n eq node); case _ => true }
      }) ||
      (pp.isInstanceOf[AstPropAccess] && !pp.isInstanceOf[AstSub]) ||
      pp.isInstanceOf[AstArray] || pp.isInstanceOf[AstObjectProperty] ||
      pp.isInstanceOf[AstConditional] || pp.isInstanceOf[AstArrow] ||
      pp.isInstanceOf[AstDefaultAssign] || pp.isInstanceOf[AstExpansion] ||
      (pp.isInstanceOf[AstForOf] && pp.asInstanceOf[AstForOf].obj != null &&
        (pp.asInstanceOf[AstForOf].obj.nn eq node)) ||
      pp.isInstanceOf[AstYield] || pp.isInstanceOf[AstExport]
    }
  }

  private def needsParensBinary(node: AstBinary): Boolean = {
    val p = parent(0)
    if (p == null) false
    else {
      val pp = p.nn
      if (
        pp.isInstanceOf[AstCall] && pp.asInstanceOf[AstCall].expression != null &&
        (pp.asInstanceOf[AstCall].expression.nn eq node)
      ) true
      else if (pp.isInstanceOf[AstUnary]) true
      else if (
        pp.isInstanceOf[AstPropAccess] && pp.asInstanceOf[AstPropAccess].expression != null &&
        (pp.asInstanceOf[AstPropAccess].expression.nn eq node)
      ) true
      else if (pp.isInstanceOf[AstBinary]) {
        val pb       = pp.asInstanceOf[AstBinary]
        val parentOp = pb.operator
        val op       = node.operator
        if (op == "??" && (parentOp == "||" || parentOp == "&&")) true
        else if (parentOp == "??" && (op == "||" || op == "&&")) true
        else {
          val pPrec = Token.PRECEDENCE.getOrElse(parentOp, 0)
          val sPrec = Token.PRECEDENCE.getOrElse(op, 0)
          pPrec > sPrec || (pPrec == sPrec && (pb.right != null && (pb.right.nn eq node) || parentOp == "**"))
        }
      } else if (pp.isInstanceOf[AstPrivateIn]) {
        val pi    = pp.asInstanceOf[AstPrivateIn]
        val pPrec = Token.PRECEDENCE.getOrElse("in", 0)
        val sPrec = Token.PRECEDENCE.getOrElse(node.operator, 0)
        pPrec > sPrec || (pPrec == sPrec && pi.value != null && (pi.value.nn eq node))
      } else false
    }
  }

  private def needsParensPrivateIn(node: AstPrivateIn): Boolean = {
    val p = parent(0)
    if (p == null) false
    else {
      val pp = p.nn
      if (
        pp.isInstanceOf[AstCall] && pp.asInstanceOf[AstCall].expression != null &&
        (pp.asInstanceOf[AstCall].expression.nn eq node)
      ) true
      else if (pp.isInstanceOf[AstUnary]) true
      else if (
        pp.isInstanceOf[AstPropAccess] && pp.asInstanceOf[AstPropAccess].expression != null &&
        (pp.asInstanceOf[AstPropAccess].expression.nn eq node)
      ) true
      else if (pp.isInstanceOf[AstBinary]) {
        val pb    = pp.asInstanceOf[AstBinary]
        val pPrec = Token.PRECEDENCE.getOrElse(pb.operator, 0)
        val sPrec = Token.PRECEDENCE.getOrElse("in", 0)
        pPrec > sPrec || (pPrec == sPrec && (pb.right != null && (pb.right.nn eq node) || pb.operator == "**"))
      } else if (
        pp.isInstanceOf[AstPrivateIn] && pp.asInstanceOf[AstPrivateIn].value != null &&
        (pp.asInstanceOf[AstPrivateIn].value.nn eq node)
      ) true
      else false
    }
  }

  private def needsParensYield(node: AstYield): Boolean = {
    val p = parent(0)
    if (p == null) false
    else {
      val pp = p.nn
      (pp.isInstanceOf[AstBinary] && !pp.isInstanceOf[AstAssign] && pp.asInstanceOf[AstBinary].operator != "=") ||
      (pp.isInstanceOf[AstCall] && pp.asInstanceOf[AstCall].expression != null && (pp.asInstanceOf[AstCall].expression.nn eq node)) ||
      (pp.isInstanceOf[AstConditional] && pp.asInstanceOf[AstConditional].condition != null && (pp.asInstanceOf[AstConditional].condition.nn eq node)) ||
      pp.isInstanceOf[AstUnary] ||
      (pp.isInstanceOf[AstPropAccess] && pp.asInstanceOf[AstPropAccess].expression != null && (pp.asInstanceOf[AstPropAccess].expression.nn eq node))
    }
  }

  private def needsParensChain(node: AstChain): Boolean = {
    val p = parent(0)
    if (p == null) false
    else {
      val pp = p.nn
      if (!pp.isInstanceOf[AstCall] && !pp.isInstanceOf[AstPropAccess]) false
      else
        pp match {
          case c:  AstCall if c.expression != null        => c.expression.nn eq node
          case pa: AstPropAccess if pa.expression != null => pa.expression.nn eq node
          case _ => false
        }
    }
  }

  private def needsParensPropAccess(node: AstPropAccess): Boolean = {
    val p = parent(0)
    if (p == null) false
    else
      p.nn match {
        case nw: AstNew if nw.expression != null && (nw.expression.nn eq node) =>
          walk(node,
               (n, _) =>
                 if (n.isInstanceOf[AstScope]) true
                 else if (n.isInstanceOf[AstCall]) WalkAbort
                 else null
          )
        case _ => false
      }
  }

  private def needsParensCall(node: AstCall): Boolean = {
    val p = parent(0)
    if (p == null) false
    else {
      val pp = p.nn
      if (
        pp.isInstanceOf[AstNew] && pp.asInstanceOf[AstNew].expression != null &&
        (pp.asInstanceOf[AstNew].expression.nn eq node)
      ) true
      else if (
        pp.isInstanceOf[AstExport] && pp.asInstanceOf[AstExport].isDefault &&
        node.expression != null && node.expression.nn.isInstanceOf[AstFunction]
      ) true
      else {
        node.expression != null && node.expression.nn.isInstanceOf[AstFunction] &&
        pp.isInstanceOf[AstPropAccess] && pp.asInstanceOf[AstPropAccess].expression != null &&
        (pp.asInstanceOf[AstPropAccess].expression.nn eq node) && {
          val p1 = parent(1)
          p1 != null && p1.nn.isInstanceOf[AstAssign] && p1.nn.asInstanceOf[AstAssign].left != null &&
          (p1.nn.asInstanceOf[AstAssign].left.nn eq pp)
        }
      }
    }
  }

  private def needsParensNew(node: AstNew): Boolean = {
    val p = parent(0)
    if (p == null) false
    else {
      val pp = p.nn
      node.args.isEmpty && (
        pp.isInstanceOf[AstPropAccess] ||
          (pp.isInstanceOf[AstCall] && pp.asInstanceOf[AstCall].expression != null && (pp.asInstanceOf[AstCall].expression.nn eq node)) ||
          (pp.isInstanceOf[AstPrefixedTemplateString] && pp.asInstanceOf[AstPrefixedTemplateString].prefix != null &&
            (pp.asInstanceOf[AstPrefixedTemplateString].prefix.nn eq node))
      )
    }
  }

  private def needsParensNumber(node: AstNumber): Boolean = {
    val p = parent(0)
    if (p == null) false
    else
      p.nn match {
        case pa: AstPropAccess if pa.expression != null && (pa.expression.nn eq node) =>
          node.value < 0 || makeNum(node.value).startsWith("0")
        case _ => false
      }
  }

  private def needsParensBigInt(node: AstBigInt): Boolean = {
    val p = parent(0)
    if (p == null) false
    else
      p.nn match {
        case pa: AstPropAccess if pa.expression != null && (pa.expression.nn eq node) =>
          node.value.startsWith("-")
        case _ => false
      }
  }

  private def needsParensAssignConditional(node: AstNode): Boolean = {
    val p = parent(0)
    if (p == null) false
    else {
      val pp = p.nn
      if (pp.isInstanceOf[AstUnary]) true
      else if (pp.isInstanceOf[AstBinary] && !pp.isInstanceOf[AstAssign]) true
      else if (pp.isInstanceOf[AstCall] && pp.asInstanceOf[AstCall].expression != null && (pp.asInstanceOf[AstCall].expression.nn eq node)) true
      else if (pp.isInstanceOf[AstConditional] && pp.asInstanceOf[AstConditional].condition != null && (pp.asInstanceOf[AstConditional].condition.nn eq node)) true
      else if (pp.isInstanceOf[AstPropAccess] && pp.asInstanceOf[AstPropAccess].expression != null && (pp.asInstanceOf[AstPropAccess].expression.nn eq node)) true
      else if (node.isInstanceOf[AstAssign]) {
        val assign = node.asInstanceOf[AstAssign]
        assign.left != null && assign.left.nn.isInstanceOf[AstDestructuring] &&
        !assign.left.nn.asInstanceOf[AstDestructuring].isArray
      } else false
    }
  }

  // ========================================================================
  // Main print dispatch
  // ========================================================================

  def printNode(node: AstNode, forceParens: Boolean = false): Unit = {
    node match {
      case scope: AstScope                                                 => activeScope = scope
      case dir:   AstDirective if useAsm == null && dir.value == "use asm" => useAsm = activeScope
      case _ =>
    }

    // Record source map mapping for this node
    addSourceMapping(node)

    pushNode(node)

    def doit(): Unit = {
      prependComments(node)
      codeGen(node)
      appendComments(node)
    }

    if (forceParens || needsParens(node)) withParens(() => doit())
    else doit()

    popNode()
    if (useAsm != null && (node eq useAsm.nn)) useAsm = null
  }

  /** Record a source map mapping for the given node at the current output position. */
  private def addSourceMapping(node: AstNode): Unit = {
    val sm = options.sourceMap
    if (sm == null) return // @nowarn
    val token = node.start
    if (token == null) return // @nowarn
    if (token.file == null || token.file.isEmpty) return // @nowarn
    val name = node match {
      case sym: AstSymbol => sym.name
      case _ => null
    }
    sm.nn.add(
      source = token.file,
      genLine = currentLine,
      genCol = currentCol,
      origLine = token.line,
      origCol = token.col,
      name = name
    )
  }

  // ========================================================================
  // Code generators (ported from DEFPRINT registrations)
  // ========================================================================

  private def codeGen(node: AstNode): Unit = {
    node match {
      case n: AstDirective     => printDirective(n)
      case n: AstExpansion     => printExpansion(n)
      case n: AstDestructuring => printDestructuring(n)
      case _: AstDebugger      => print("debugger"); semicolon()

      // Statements
      case n: AstToplevel         => displayBody(n.body, isToplevel = true, allowDirectives = true); print("")
      case n: AstLabeledStatement => printNode(n.label.nn); colon(); printNode(n.body.nn)
      case n: AstSimpleStatement  => printNode(n.body.nn); semicolon()
      case n: AstBlockStatement   => printBraced(n.body)
      case _: AstEmptyStatement   => semicolon()
      case n: AstDo               => printDo(n)
      case n: AstWhile            => printWhile(n)
      case n: AstFor              => printFor(n)
      case n: AstForIn            => printForIn(n)
      case n: AstWith             => printWith(n)

      // Functions (Arrow before Lambda since Arrow extends Lambda)
      case n: AstArrow  => printArrow(n)
      case n: AstLambda => printLambdaBody(n)

      // Templates
      case n: AstPrefixedTemplateString => printPrefixedTemplateString(n)
      case n: AstTemplateString         => printTemplateString(n)
      case n: AstTemplateSegment        => printTemplateStringChars(n.value)

      // Exits
      case n: AstReturn => printExit(n, "return")
      case n: AstThrow  => printExit(n, "throw")

      // Yield / Await
      case n: AstYield => printYield(n)
      case n: AstAwait => printAwait(n)

      // Loop control
      case n: AstBreak    => printLoopControl(n, "break")
      case n: AstContinue => printLoopControl(n, "continue")

      // If
      case n: AstIf => printIf(n)

      // Switch
      case n: AstSwitch  => printSwitch(n)
      case n: AstDefault => print("default:"); printSwitchBody(n.body)
      case n: AstCase    => printCase(n)

      // Exceptions
      case n: AstTry      => printTry(n)
      case n: AstTryBlock => printBraced(n.body)
      case n: AstCatch    => printCatch(n)
      case n: AstFinally  => print("finally"); space(); printBraced(n.body)

      // Definitions
      case n: AstLet        => printDefinitions(n, "let")
      case n: AstVar        => printDefinitions(n, "var")
      case n: AstConst      => printDefinitions(n, "const")
      case n: AstUsing      => printDefinitions(n, if (n.isAwait) "await using" else "using")
      case n: AstVarDefLike => printVarDef(n)

      // Import/Export
      case n: AstImport      => printImport(n)
      case _: AstImportMeta  => print("import.meta")
      case n: AstNameMapping => printNameMapping(n)
      case n: AstExport      => printExport(n)

      // Calls (New before Call since New extends Call)
      case n: AstNew  => print("new"); space(); printCall(n)
      case n: AstCall => printCall(n)

      // Sequences
      case n: AstSequence => printSequence(n)

      // Property access
      case n: AstDot     => printDot(n)
      case n: AstDotHash => printDotHash(n)
      case n: AstSub     => printSub(n)
      case n: AstChain   => if (n.expression != null) printNode(n.expression.nn)

      // Unary
      case n: AstUnaryPrefix  => printUnaryPrefix(n)
      case n: AstUnaryPostfix => printUnaryPostfix(n)

      // Binary (DefaultAssign/Assign before Binary since they extend it)
      case n: AstDefaultAssign => printBinary(n)
      case n: AstAssign        => printBinary(n)
      case n: AstBinary        => printBinary(n)

      // Conditional
      case n: AstConditional => printConditional(n)

      // Literals
      case n: AstArray     => printArray(n)
      case n: AstObject    => printObject(n)
      case n: AstClass     => printClass(n)
      case _: AstNewTarget => print("new.target")

      // Object properties
      case n: AstObjectKeyVal         => printObjectKeyVal(n)
      case n: AstClassPrivateProperty => printClassPrivateProperty(n)
      case n: AstClassProperty        => printClassProperty(n)
      case n: AstObjectSetter         => printGetterSetter(n, "set", isPrivate = false)
      case n: AstObjectGetter         => printGetterSetter(n, "get", isPrivate = false)
      case n: AstPrivateSetter        => printGetterSetter(n, "set", isPrivate = true)
      case n: AstPrivateGetter        => printGetterSetter(n, "get", isPrivate = true)
      case n: AstConciseMethod        => printConciseMethod(n, isPrivate = false)
      case n: AstPrivateMethod        => printConciseMethod(n, isPrivate = true)

      // Private
      case n: AstPrivateIn             => printNode(n.key.nn); space(); print("in"); space(); printNode(n.value.nn)
      case n: AstSymbolPrivateProperty => print("#" + n.name)

      // Static block
      case n: AstClassStaticBlock => print("static"); space(); printBraced(n.body)

      // Symbols (This/Super before Symbol since they extend it)
      case _: AstSuper  => print("super")
      case _: AstThis   => print("this")
      case n: AstSymbol => printSymbol(n)
      case _: AstHole   => ()

      // Constants / Literals
      case n: AstString    => printString(n.value, n.quote, inDirective)
      case n: AstNumber    => printNumber(n)
      case n: AstBigInt    => printBigInt(n)
      case n: AstRegExp    => printRegExp(n)
      case _: AstTrue      => print("true")
      case _: AstFalse     => print("false")
      case _: AstNull      => print("null")
      case _: AstUndefined => print("undefined")
      case _: AstNaN       => print("NaN")
      case _: AstInfinity  => print("Infinity")

      // Generic statement fallback
      case n: AstStatement if n.isInstanceOf[AstStatementWithBody] =>
        val swb = n.asInstanceOf[AstStatementWithBody]
        if (swb.body != null) { printNode(swb.body.nn); semicolon() }

      case null => ()
    }
  }

  // ========================================================================
  // Individual code generators
  // ========================================================================

  private def printDirective(node: AstDirective): Unit = {
    printString(node.value, node.quote)
    semicolon()
  }

  private def printExpansion(node: AstExpansion): Unit = {
    print("...")
    if (node.expression != null) printNode(node.expression.nn)
  }

  private def printDestructuring(node: AstDestructuring): Unit = {
    print(if (node.isArray) "[" else "{")
    val len = node.names.size
    node.names.zipWithIndex.foreach { case (name, i) =>
      if (i > 0) comma()
      printNode(name)
      if (i == len - 1 && name.isInstanceOf[AstHole]) comma()
    }
    print(if (node.isArray) "]" else "}")
  }

  private def displayBody(body: mutable.Seq[AstNode], isToplevel: Boolean, allowDirectives: Boolean): Unit = {
    val lastIdx = body.length - 1
    inDirective = allowDirectives
    body.zipWithIndex.foreach { case (stmt, i) =>
      if (
        inDirective && !(stmt.isInstanceOf[AstDirective] || stmt.isInstanceOf[AstEmptyStatement] ||
          (stmt.isInstanceOf[AstSimpleStatement] && stmt.asInstanceOf[AstSimpleStatement].body != null &&
            stmt.asInstanceOf[AstSimpleStatement].body.nn.isInstanceOf[AstString]))
      ) {
        inDirective = false
      }
      if (!stmt.isInstanceOf[AstEmptyStatement]) {
        indent()
        printNode(stmt)
        if (!(i == lastIdx && isToplevel)) {
          newline()
          if (isToplevel) newline()
        }
      }
      if (
        inDirective && stmt.isInstanceOf[AstSimpleStatement] &&
        stmt.asInstanceOf[AstSimpleStatement].body != null &&
        stmt.asInstanceOf[AstSimpleStatement].body.nn.isInstanceOf[AstString]
      ) {
        inDirective = false
      }
    }
    inDirective = false
  }

  private def printBraced(body: mutable.Seq[AstNode], allowDirectives: Boolean = false): Unit =
    if (body.nonEmpty) {
      withBlock(() => displayBody(body, isToplevel = false, allowDirectives = allowDirectives))
    } else {
      print("{}")
    }

  private def printDo(node: AstDo): Unit = {
    print("do"); space()
    if (node.body != null) makeBlock(node.body.nn)
    space(); print("while"); space()
    withParens(() => if (node.condition != null) printNode(node.condition.nn))
    semicolon()
  }

  private def printWhile(node: AstWhile): Unit = {
    print("while"); space()
    withParens(() => if (node.condition != null) printNode(node.condition.nn))
    space()
    if (node.body != null) printMaybeBracedBody(node.body.nn)
  }

  private def printFor(node: AstFor): Unit = {
    print("for"); space()
    withParens { () =>
      if (node.init != null) {
        if (node.init.nn.isInstanceOf[AstDefinitionsLike]) printNode(node.init.nn)
        else parenthesizeForNoIn(node.init.nn, noIn = true)
        print(";"); space()
      } else print(";")
      if (node.condition != null) { printNode(node.condition.nn); print(";"); space() }
      else print(";")
      if (node.step != null) printNode(node.step.nn)
    }
    space()
    if (node.body != null) printMaybeBracedBody(node.body.nn)
  }

  private def printForIn(node: AstForIn): Unit = {
    print("for")
    node match {
      case fo: AstForOf if fo.isAwait => space(); print("await")
      case _ =>
    }
    space()
    withParens { () =>
      if (node.init != null) printNode(node.init.nn)
      space()
      print(if (node.isInstanceOf[AstForOf]) "of" else "in")
      space()
      if (node.obj != null) printNode(node.obj.nn)
    }
    space()
    if (node.body != null) printMaybeBracedBody(node.body.nn)
  }

  private def printWith(node: AstWith): Unit = {
    print("with"); space()
    withParens(() => if (node.expression != null) printNode(node.expression.nn))
    space()
    if (node.body != null) printMaybeBracedBody(node.body.nn)
  }

  // ---- Functions ----

  private def printLambdaBody(node: AstLambda, noKeyword: Boolean = false): Unit = {
    if (!noKeyword) {
      if (node.isAsync) { print("async"); space() }
      print("function")
      if (node.isGenerator) star()
      if (node.name != null) space()
    }
    if (node.name != null) {
      node.name.nn match {
        case sym: AstSymbol => printNode(sym)
        case nameNode if noKeyword => withSquare(() => printNode(nameNode))
        case nameNode              => printNode(nameNode)
      }
    }
    withParens(() =>
      node.argnames.zipWithIndex.foreach { case (arg, i) =>
        if (i > 0) comma()
        printNode(arg)
      }
    )
    space()
    printBraced(node.body, allowDirectives = true)
  }

  private def printArrow(node: AstArrow): Unit = {
    val p                = parent(0)
    val needsOuterParens = p != null && {
      val pp = p.nn
      (pp.isInstanceOf[AstBinary] && !pp.isInstanceOf[AstAssign] && !pp.isInstanceOf[AstDefaultAssign]) ||
      pp.isInstanceOf[AstUnary] ||
      (pp.isInstanceOf[AstCall] && pp.asInstanceOf[AstCall].expression != null && (pp.asInstanceOf[AstCall].expression.nn eq node))
    }
    if (needsOuterParens) print("(")
    if (node.isAsync) { print("async"); space() }

    if (node.argnames.size == 1 && node.argnames.head.isInstanceOf[AstSymbol]) {
      printNode(node.argnames.head)
    } else {
      withParens(() => node.argnames.zipWithIndex.foreach { case (arg, i) => if (i > 0) comma(); printNode(arg) })
    }
    space(); print("=>"); space()

    if (node.body.size == 1 && node.body.head.isInstanceOf[AstReturn]) {
      val ret = node.body.head.asInstanceOf[AstReturn]
      if (ret.value == null) print("{}")
      else if (FirstInStatement.leftIsObject(ret.value.nn)) { print("("); printNode(ret.value.nn); print(")") }
      else printNode(ret.value.nn)
    } else printBraced(node.body)

    if (needsOuterParens) print(")")
  }

  // ---- Templates ----

  private def printPrefixedTemplateString(node: AstPrefixedTemplateString): Unit = {
    if (node.prefix != null) {
      val tag       = node.prefix.nn
      val parensTag = tag.isInstanceOf[AstLambda] || tag.isInstanceOf[AstBinary] ||
        tag.isInstanceOf[AstConditional] || tag.isInstanceOf[AstSequence] || tag.isInstanceOf[AstUnary] ||
        (tag.isInstanceOf[AstDot] && tag.asInstanceOf[AstDot].expression != null &&
          tag.asInstanceOf[AstDot].expression.nn.isInstanceOf[AstObject])
      if (parensTag) print("(")
      printNode(tag)
      if (parensTag) print(")")
    }
    if (node.templateString != null) printNode(node.templateString.nn)
  }

  private def printTemplateString(node: AstTemplateString): Unit = {
    val isTagged = parent(0) != null && parent(0).nn.isInstanceOf[AstPrefixedTemplateString]
    print("`")
    node.segments.foreach {
      case seg: AstTemplateSegment =>
        if (isTagged) print(seg.raw) else printTemplateStringChars(seg.value)
      case other =>
        print("${"); printNode(other); print("}")
    }
    print("`")
  }

  // ---- Exits ----

  private def printExit(node: AstExit, kind: String): Unit = {
    print(kind)
    if (node.value != null) {
      space()
      val vn       = node.value.nn
      val comments = vn.start.commentsBefore
      if (comments.nonEmpty && !printedComments.contains(comments)) {
        print("("); printNode(vn); print(")")
      } else printNode(vn)
    }
    semicolon()
  }

  // ---- Yield / Await ----

  private def printYield(node: AstYield): Unit = {
    print("yield" + (if (node.isStar) "*" else ""))
    if (node.expression != null) { space(); printNode(node.expression.nn) }
  }

  private def printAwait(node: AstAwait): Unit = {
    print("await"); space()
    if (node.expression != null) {
      val e      = node.expression.nn
      val parens = !(e.isInstanceOf[AstCall] || e.isInstanceOf[AstSymbolRef] || e.isInstanceOf[AstPropAccess] ||
        e.isInstanceOf[AstUnary] || e.isInstanceOf[AstConstant] || e.isInstanceOf[AstAwait] || e.isInstanceOf[AstObject])
      if (parens) print("(")
      printNode(e)
      if (parens) print(")")
    }
  }

  // ---- Loop control ----

  private def printLoopControl(node: AstLoopControl, kind: String): Unit = {
    print(kind)
    if (node.label != null) { space(); printNode(node.label.nn) }
    semicolon()
  }

  // ---- If ----

  private def printIf(node: AstIf): Unit = {
    print("if"); space()
    withParens(() => if (node.condition != null) printNode(node.condition.nn))
    space()
    if (node.alternative != null) {
      makeThen(node); space(); print("else"); space()
      if (node.alternative.nn.isInstanceOf[AstIf]) printNode(node.alternative.nn)
      else printMaybeBracedBody(node.alternative.nn)
    } else {
      if (node.body != null) printMaybeBracedBody(node.body.nn)
    }
  }

  private def makeThen(node: AstIf): Unit = {
    if (node.body == null) { forceSemicolon(); return } // @nowarn — guarded early exit
    if (options.braces) { makeBlock(node.body.nn); return } // @nowarn — guarded early exit
    var cur: AstNode = node.body.nn
    var needBlock = false
    boundary {
      while (true)
        cur match {
          case ifNode: AstIf =>
            if (ifNode.alternative == null) { needBlock = true; break() }
            else cur = ifNode.alternative.nn
          case swb: AstStatementWithBody => if (swb.body != null) cur = swb.body.nn else break()
          case _ => break()
        }
    }
    if (needBlock) makeBlock(node.body.nn) else printMaybeBracedBody(node.body.nn)
  }

  // ---- Switch ----

  private def printSwitch(node: AstSwitch): Unit = {
    print("switch"); space()
    withParens(() => if (node.expression != null) printNode(node.expression.nn))
    space()
    if (node.body.isEmpty) print("{}")
    else
      withBlock { () =>
        val lastIdx = node.body.size - 1
        node.body.zipWithIndex.foreach { case (branch, i) =>
          indent(half = true); printNode(branch)
          if (i < lastIdx) {
            branch match {
              case sb: AstSwitchBranch if sb.body.nonEmpty => newline()
              case _ =>
            }
          }
        }
      }
  }

  private def printCase(node: AstCase): Unit = {
    print("case"); space()
    if (node.expression != null) printNode(node.expression.nn)
    print(":")
    printSwitchBody(node.body)
  }

  private def printSwitchBody(body: mutable.Seq[AstNode]): Unit = {
    newline()
    body.foreach { stmt => indent(); printNode(stmt); newline() }
  }

  // ---- Exceptions ----

  private def printTry(node: AstTry): Unit = {
    print("try"); space()
    if (node.body != null) printNode(node.body.nn)
    if (node.bcatch != null) { space(); printNode(node.bcatch.nn) }
    if (node.bfinally != null) { space(); printNode(node.bfinally.nn) }
  }

  private def printCatch(node: AstCatch): Unit = {
    print("catch")
    if (node.argname != null) { space(); withParens(() => printNode(node.argname.nn)) }
    space()
    printBraced(node.body)
  }

  // ---- Definitions ----

  private def printDefinitions(node: AstDefinitionsLike, kind: String): Unit = {
    print(kind); space()
    node.definitions.zipWithIndex.foreach { case (defn, i) => if (i > 0) comma(); printNode(defn) }
    val p               = parent(0)
    val inFor           = p != null && (p.nn.isInstanceOf[AstFor] || p.nn.isInstanceOf[AstForIn])
    val outputSemicolon = !inFor || (p != null && {
      p.nn match {
        case f:  AstFor   => f.init == null || !(f.init.nn eq node)
        case fi: AstForIn => fi.init == null || !(fi.init.nn eq node)
        case _ => true
      }
    })
    if (outputSemicolon) semicolon()
  }

  private def printVarDef(node: AstVarDefLike): Unit = {
    if (node.name != null) printNode(node.name.nn)
    if (node.value != null) {
      space(); print("="); space()
      val p    = parent(1)
      val noIn = p != null && (p.nn.isInstanceOf[AstFor] || p.nn.isInstanceOf[AstForIn])
      parenthesizeForNoIn(node.value.nn, noIn)
    }
  }

  private def parenthesizeForNoIn(node: AstNode, noIn: Boolean): Unit = {
    var parens = false
    if (noIn) parens = containsInOperator(node)
    printNode(node, forceParens = parens)
  }

  private def containsInOperator(node: AstNode): Boolean =
    walk(
      node,
      (n, _) =>
        if (n.isInstanceOf[AstScope] && !n.isInstanceOf[AstArrow]) true
        else if (n.isInstanceOf[AstBinary] && n.asInstanceOf[AstBinary].operator == "in") WalkAbort
        else if (n.isInstanceOf[AstPrivateIn]) WalkAbort
        else null
    )

  // ---- Import / Export ----

  private def printImport(node: AstImport): Unit = {
    print("import"); space()
    if (node.importedName != null) printNode(node.importedName.nn)
    if (node.importedName != null && node.importedNames != null) { print(","); space() }
    if (node.importedNames != null) {
      val names = node.importedNames.nn
      if (names.size == 1) {
        val nm = names.head.asInstanceOf[AstNameMapping]
        if (nm.foreignName != null && nm.foreignName.nn.asInstanceOf[AstSymbol].name == "*") {
          printNode(names.head)
        } else { print("{"); names.foreach { n => space(); printNode(n) }; space(); print("}") }
      } else {
        print("{")
        names.zipWithIndex.foreach { case (n, i) => space(); printNode(n); if (i < names.size - 1) print(",") }
        space(); print("}")
      }
    }
    if (node.importedName != null || node.importedNames != null) { space(); print("from"); space() }
    if (node.moduleName != null) printNode(node.moduleName.nn)
    if (node.attributes != null) { print("with"); printNode(node.attributes.nn) }
    semicolon()
  }

  private def printNameMapping(node: AstNameMapping): Unit = {
    val isImport   = parent(0) != null && parent(0).nn.isInstanceOf[AstImport]
    val nameSym    = if (node.name != null) node.name.nn.asInstanceOf[AstSymbol] else null
    val foreignSym = if (node.foreignName != null) node.foreignName.nn.asInstanceOf[AstSymbol] else null
    if (nameSym == null || foreignSym == null) { return } // @nowarn — guarded early exit

    val nameStr           = nameSym.name
    val foreignStr        = foreignSym.name
    val namesAreDifferent = nameStr != foreignStr

    if (namesAreDifferent) {
      if (isImport) print(foreignStr) else printNode(nameSym)
      space(); print("as"); space()
      if (isImport) printNode(nameSym) else print(foreignStr)
    } else {
      printNode(nameSym)
    }
  }

  private def printExport(node: AstExport): Unit = {
    print("export"); space()
    if (node.isDefault) { print("default"); space() }
    if (node.exportedNames != null) {
      val names = node.exportedNames.nn
      if (names.size == 1 && names.head.isInstanceOf[AstNameMapping]) {
        val nm = names.head.asInstanceOf[AstNameMapping]
        if (nm.name != null && nm.name.nn.asInstanceOf[AstSymbol].name == "*") { printNode(names.head) }
        else {
          print("{"); names.zipWithIndex.foreach { case (n, i) => space(); printNode(n); if (i < names.size - 1) print(",") }
          space(); print("}")
        }
      } else {
        print("{"); names.zipWithIndex.foreach { case (n, i) => space(); printNode(n); if (i < names.size - 1) print(",") }
        space(); print("}")
      }
    } else if (node.exportedValue != null) printNode(node.exportedValue.nn)
    else if (node.exportedDefinition != null) {
      printNode(node.exportedDefinition.nn)
      if (node.exportedDefinition.nn.isInstanceOf[AstDefinitions]) {
        if (node.moduleName != null) { space(); print("from"); space(); printNode(node.moduleName.nn) }
        if (node.attributes != null) { print("with"); printNode(node.attributes.nn) }
        return // @nowarn — guarded early exit
      }
    }
    if (node.moduleName != null) { space(); print("from"); space(); printNode(node.moduleName.nn) }
    if (node.attributes != null) { print("with"); printNode(node.attributes.nn) }
    if (
      (node.exportedValue != null && !(node.exportedValue.nn.isInstanceOf[AstDefun] ||
        node.exportedValue.nn.isInstanceOf[AstFunction] || node.exportedValue.nn.isInstanceOf[AstClass])) ||
      node.moduleName != null || node.exportedNames != null
    ) semicolon()
  }

  // ---- Calls ----

  private def printCall(node: AstCall): Unit = {
    if (node.expression != null) printNode(node.expression.nn)
    if (node.isInstanceOf[AstNew] && node.args.isEmpty) { return } // @nowarn — skip parens for `new Foo`
    if (node.optional) print("?.")
    withParens(() => node.args.zipWithIndex.foreach { case (expr, i) => if (i > 0) comma(); printNode(expr) })
  }

  // ---- Sequence ----

  private def printSequence(node: AstSequence): Unit =
    node.expressions.zipWithIndex.foreach { case (expr, i) =>
      if (i > 0) { comma(); if (shouldBreak) { newline(); indent() } }
      printNode(expr)
    }

  // ---- Property access ----

  private def printDot(node: AstDot): Unit = {
    if (node.expression != null) printNode(node.expression.nn)
    val prop          = node.property match { case s: String => s; case _ => "" }
    val printComputed =
      if (Token.ALL_RESERVED_WORDS.contains(prop)) false
      else !isIdentifierString(prop)
    if (node.optional) print("?.")
    if (printComputed) { print("["); printString(prop); print("]") }
    else {
      if (
        node.expression != null && node.expression.nn.isInstanceOf[AstNumber] &&
        node.expression.nn.asInstanceOf[AstNumber].value >= 0
      ) {
        if (!last.exists(c => "xa-fA-F.)eE0123456789".indexOf(c) >= 0)) print(".")
      }
      if (!node.optional) print(".")
      printName(prop)
    }
  }

  private def printDotHash(node: AstDotHash): Unit = {
    if (node.expression != null) printNode(node.expression.nn)
    val prop = node.property match { case s: String => s; case _ => "" }
    if (node.optional) print("?")
    print(".#")
    printName(prop)
  }

  private def printSub(node: AstSub): Unit = {
    if (node.expression != null) printNode(node.expression.nn)
    if (node.optional) print("?.")
    print("[")
    node.property match { case n: AstNode => printNode(n); case _ => }
    print("]")
  }

  // ---- Unary ----

  private def printUnaryPrefix(node: AstUnaryPrefix): Unit = {
    val op = node.operator
    if (op == "--" && last.endsWith("!")) print(" ")
    print(op)
    if (
      op.head.isLetter || ((op.endsWith("+") || op.endsWith("-")) &&
        node.expression != null && node.expression.nn.isInstanceOf[AstUnaryPrefix] && {
          val inner = node.expression.nn.asInstanceOf[AstUnaryPrefix].operator
          inner.startsWith("+") || inner.startsWith("-")
        })
    ) space()
    if (node.expression != null) printNode(node.expression.nn)
  }

  private def printUnaryPostfix(node: AstUnaryPostfix): Unit = {
    if (node.expression != null) printNode(node.expression.nn)
    print(node.operator)
  }

  // ---- Binary ----

  private def printBinary(node: AstBinary): Unit = {
    if (node.left != null) printNode(node.left.nn)
    if (node.operator.charAt(0) == '>' && last.endsWith("--")) print(" ") else space()
    print(node.operator); space()
    if (node.right != null) printNode(node.right.nn)
  }

  // ---- Conditional ----

  private def printConditional(node: AstConditional): Unit = {
    if (node.condition != null) printNode(node.condition.nn)
    space(); print("?"); space()
    if (node.consequent != null) printNode(node.consequent.nn)
    space(); colon()
    if (node.alternative != null) printNode(node.alternative.nn)
  }

  // ---- Literals ----

  private def printArray(node: AstArray): Unit =
    withSquare { () =>
      val len = node.elements.size
      if (len > 0) space()
      node.elements.zipWithIndex.foreach { case (elem, i) =>
        if (i > 0) comma()
        printNode(elem)
        if (i == len - 1 && elem.isInstanceOf[AstHole]) comma()
      }
      if (len > 0) space()
    }

  private def printObject(node: AstObject): Unit =
    if (node.properties.nonEmpty) withBlock { () =>
      node.properties.zipWithIndex.foreach { case (prop, i) =>
        if (i > 0) { print(","); newline() }
        indent(); printNode(prop)
      }
      newline()
    }
    else print("{}")

  private def printClass(node: AstClass): Unit = {
    print("class"); space()
    if (node.name != null) { printNode(node.name.nn); space() }
    if (node.superClass != null) {
      val parens = !(node.superClass.nn.isInstanceOf[AstSymbolRef] || node.superClass.nn.isInstanceOf[AstPropAccess] ||
        node.superClass.nn.isInstanceOf[AstClassExpression] || node.superClass.nn.isInstanceOf[AstFunction])
      print("extends")
      if (parens) print("(") else space()
      printNode(node.superClass.nn)
      if (parens) print(")") else space()
    }
    if (node.properties.nonEmpty) withBlock { () =>
      node.properties.zipWithIndex.foreach { case (prop, i) => if (i > 0) newline(); indent(); printNode(prop) }
      newline()
    }
    else print("{}")
  }

  // ---- Object properties ----

  private def printPropertyName(key: String, quote: String): Boolean =
    if (options.quoteKeys) { printString(key); false }
    else {
      val asNum =
        try key.toDouble
        catch { case _: NumberFormatException => Double.NaN }
      if (!asNum.isNaN && asNum >= 0 && asNum == Math.floor(asNum) && asNum.toLong.toString == key) {
        if (options.keepNumbers) { print(key); false }
        else { print(makeNum(asNum)); false }
      } else {
        val printStr = !isIdentifierString(key)
        if (printStr || (quote != null && quote.nonEmpty && options.keepQuotedProps)) {
          printString(key, if (quote != null) quote else "\""); false
        } else { printName(key); true }
      }
    }

  private def printObjectKeyVal(node: AstObjectKeyVal): Unit = {
    val keyStr       = node.key match { case s: String => s; case _ => "" }
    val tryShorthand = options.shorthand && !node.key.isInstanceOf[AstNode]

    if (
      tryShorthand && node.value != null && node.value.nn.isInstanceOf[AstSymbol] &&
      getSymbolName(node.value.nn.asInstanceOf[AstSymbol]) == keyStr &&
      !Token.ALL_RESERVED_WORDS.contains(keyStr)
    ) {
      val wasShorthand = printPropertyName(keyStr, node.quote)
      if (!wasShorthand) { colon(); printNode(node.value.nn) }
    } else if (
      tryShorthand && node.value != null && node.value.nn.isInstanceOf[AstDefaultAssign] &&
      node.value.nn.asInstanceOf[AstDefaultAssign].left != null &&
      node.value.nn.asInstanceOf[AstDefaultAssign].left.nn.isInstanceOf[AstSymbol] &&
      getSymbolName(node.value.nn.asInstanceOf[AstDefaultAssign].left.nn.asInstanceOf[AstSymbol]) == keyStr
    ) {
      val da           = node.value.nn.asInstanceOf[AstDefaultAssign]
      val wasShorthand = printPropertyName(keyStr, node.quote)
      if (!wasShorthand) { colon(); printNode(da.left.nn) }
      space(); print("="); space()
      if (da.right != null) printNode(da.right.nn)
    } else {
      node.key match {
        case _: AstNode => withSquare(() => printNode(node.key.asInstanceOf[AstNode]))
        case _ => printPropertyName(keyStr, node.quote)
      }
      colon()
      if (node.value != null) printNode(node.value.nn)
    }
  }

  private def getSymbolName(sym: AstSymbol): String =
    sym.thedef match {
      case sd: ssg.js.scope.SymbolDef if sd.mangledName != null => sd.mangledName.nn
      case _ => sym.name
    }

  private def printClassPrivateProperty(node: AstClassPrivateProperty): Unit = {
    if (node.isStatic) { print("static"); space() }
    print("#")
    node.key match { case s: String => printPropertyName(s, null); case n: AstNode => printNode(n) }
    if (node.value != null) { print("="); printNode(node.value.nn) }
    semicolon()
  }

  private def printClassProperty(node: AstClassProperty): Unit = {
    if (node.isStatic) { print("static"); space() }
    node.key match {
      case n: AstSymbolClassProperty => printPropertyName(n.name, node.quote)
      case n: AstNode                => print("["); printNode(n); print("]")
      case s: String                 => printPropertyName(s, node.quote)
    }
    if (node.value != null) { print("="); printNode(node.value.nn) }
    semicolon()
  }

  private def printGetterSetter(node: AstObjectProperty, typ: String, isPrivate: Boolean): Unit = {
    if (
      node.isInstanceOf[AstObjectGetter] && node.asInstanceOf[AstObjectGetter].isStatic ||
      node.isInstanceOf[AstObjectSetter] && node.asInstanceOf[AstObjectSetter].isStatic ||
      node.isInstanceOf[AstPrivateGetter] && node.asInstanceOf[AstPrivateGetter].isStatic ||
      node.isInstanceOf[AstPrivateSetter] && node.asInstanceOf[AstPrivateSetter].isStatic ||
      node.isInstanceOf[AstConciseMethod] && node.asInstanceOf[AstConciseMethod].isStatic ||
      node.isInstanceOf[AstPrivateMethod] && node.asInstanceOf[AstPrivateMethod].isStatic
    ) {
      print("static"); space()
    }
    if (typ != null) { print(typ); space() }
    node.key match {
      case sym: AstSymbolMethod =>
        if (isPrivate) print("#")
        val q = node match {
          case g: AstObjectGetter  => g.quote; case s: AstObjectSetter => s.quote
          case m: AstConciseMethod => m.quote; case _ => ""
        }
        printPropertyName(sym.name, q)
      case n: AstNode => withSquare(() => printNode(n))
      case s: String  => if (isPrivate) print("#"); printPropertyName(s, "")
    }
    if (node.value != null) node.value.nn match {
      case lambda: AstLambda => printLambdaBody(lambda, noKeyword = true)
      case other => printNode(other)
    }
  }

  private def printConciseMethod(node: AstObjectProperty, isPrivate: Boolean): Unit = {
    val lambda = if (node.value != null) node.value.nn match {
      case l: AstLambda => l
      case _ => null
    }
    else null
    val typ =
      if (lambda != null && lambda.isGenerator && lambda.isAsync) "async*"
      else if (lambda != null && lambda.isGenerator) "*"
      else if (lambda != null && lambda.isAsync) "async"
      else null // @nowarn — sentinel
    printGetterSetter(node, typ, isPrivate)
  }

  // ---- Symbols ----

  private def printSymbol(node: AstSymbol): Unit = printName(node.name)

  // ---- Numbers ----

  private def printNumber(node: AstNumber): Unit =
    if ((options.keepNumbers || useAsm != null) && node.raw.nonEmpty) print(node.raw)
    else print(makeNum(node.value))

  private def printBigInt(node: AstBigInt): Unit =
    if (options.keepNumbers && node.raw.nonEmpty) print(node.raw)
    else print(node.value + "n")

  // ---- RegExp ----

  private def printRegExp(node: AstRegExp): Unit = {
    val rv            = node.value
    val sortedFlags   = if (rv.flags.nonEmpty) rv.flags.toSeq.sorted.mkString else ""
    val escapedSource = rv.source.replaceAll("(?i)(<\\s*/\\s*script)", "$1".replace("/", "\\\\/"))
    if (rv.source.matches("(?i)^\\s*script.*") && last.endsWith("<")) print(" ")
    print(toUtf8("/" + escapedSource + "/" + sortedFlags))
    val par = parent(0)
    if (par != null && par.nn.isInstanceOf[AstBinary]) {
      val bin = par.nn.asInstanceOf[AstBinary]
      if (bin.operator.head.isLetter && bin.left != null && (bin.left.nn eq node)) print(" ")
    }
  }

  // ========================================================================
  // Number formatting
  // ========================================================================

  def makeNum(num: Double): String =
    if (num.isNaN) "NaN"
    else if (num.isInfinite) { if (num > 0) "1/0" else "-1/0" }
    else if (num == 0.0 && (1.0 / num) < 0) "-0"
    else {
      val str        = numToString(num).replaceFirst("^0\\.", ".").replace("e+", "e")
      val candidates = mutable.ArrayBuffer(str)
      if (Math.floor(num) == num && !num.isInfinite) {
        if (num < 0) candidates += ("-0x" + (-num).toLong.toHexString)
        else if (num <= Long.MaxValue.toDouble) candidates += ("0x" + num.toLong.toHexString)
      }
      val leadingZeros = "^\\.0+".r.findFirstIn(str)
      if (leadingZeros.isDefined) {
        val zeroLen = leadingZeros.get.length
        val digits  = str.substring(zeroLen)
        candidates += (digits + "e-" + (digits.length + zeroLen - 1))
      }
      val trailingZeros = "0+$".r.findFirstIn(str)
      if (trailingZeros.isDefined && !str.contains('.') && !str.contains('e')) {
        val zeroLen = trailingZeros.get.length
        candidates += (str.substring(0, str.length - zeroLen) + "e" + zeroLen)
      }
      val expMatch = "^(\\d)\\.(\\d+)e(-?\\d+)$".r.findFirstMatchIn(str)
      if (expMatch.isDefined) {
        val m = expMatch.get
        candidates += (m.group(1) + m.group(2) + "e" + (m.group(3).toInt - m.group(2).length))
      }
      candidates.minBy(_.length)
    }

  private def numToString(num: Double): String =
    if (num == Math.floor(num) && !num.isInfinite && Math.abs(num) < 1e15) num.toLong.toString
    else num.toString.replace("E", "e")

  // ========================================================================
  // Utility helpers
  // ========================================================================

  private def isIdentifierString(str: String): Boolean =
    str.nonEmpty && "^[a-zA-Z_$][a-zA-Z0-9_$]*$".r.findFirstIn(str).isDefined

  private def makeBlock(stmt: AstNode): Unit =
    if (stmt.isInstanceOf[AstEmptyStatement]) print("{}")
    else if (stmt.isInstanceOf[AstBlockStatement]) printNode(stmt)
    else withBlock { () => indent(); printNode(stmt); newline() }

  private def printMaybeBracedBody(stat: AstNode): Unit =
    if (options.braces) makeBlock(stat)
    else if (stat.isInstanceOf[AstEmptyStatement]) forceSemicolon()
    else if ((stat.isInstanceOf[AstDefinitionsLike] && !stat.isInstanceOf[AstVar]) || stat.isInstanceOf[AstClass]) makeBlock(stat)
    else printNode(stat)
}

object OutputStream {
  def printToString(node: AstNode, options: OutputOptions = OutputOptions()): String = {
    val out = new OutputStream(options)
    out.printNode(node)
    out.get()
  }
}
