/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/parse/sass.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: sass.dart -> SassParser.scala
 *   Idiom: Faithful state-machine port of the dart-sass indented-syntax
 *     parser. Overrides StylesheetParser's 7 virtual hooks and adds
 *     indentation tracking via _peekIndentation / _readIndentation.
 *   Convention: boundary/break for early returns; Nullable[A] for nullable.
 *   Audited: 2026-04-17
 */
package ssg
package sass
package parse

import ssg.sass.{ InterpolationBuffer, MultiSpanSassFormatException, Nullable }
import ssg.sass.Nullable.*
import ssg.sass.ast.sass.{
  DynamicImport,
  Import,
  Interpolation,
  LoudComment,
  SilentComment,
  Statement,
  StaticImport
}
import ssg.sass.util.{ CharCode, LineScannerState }
import ssg.sass.value.SassString

import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

/** A parser for the whitespace-sensitive indented Sass syntax.
  *
  * Implements the indented Sass syntax by overriding [[StylesheetParser]]'s
  * virtual hooks to use indentation-based block structure instead of `{`/`}`
  * braces and `;` terminators. This is a faithful port of dart-sass's
  * `SassParser` class in `lib/src/parse/sass.dart`.
  */
class SassParser(
  contents:       String,
  url:            Nullable[String] = Nullable.Null,
  parseSelectors: Boolean = false
) extends StylesheetParser(contents, url, parseSelectors) {

  // ---------------------------------------------------------------------------
  // State fields — dart-sass sass.dart lines 17-37
  // ---------------------------------------------------------------------------

  override def currentIndentation: Int = _currentIndentation
  private var _currentIndentation: Int = 0

  /** The indentation level of the next source line after the scanner's
    * position, or `null` if that hasn't been computed yet.
    *
    * A source line is any line that's not entirely whitespace.
    */
  private var _nextIndentation: Nullable[Int] = Nullable.Null

  /** The beginning of the next source line after the scanner's position, or
    * `null` if the next indentation hasn't been computed yet.
    *
    * A source line is any line that's not entirely whitespace.
    */
  private var _nextIndentationEnd: Nullable[LineScannerState] = Nullable.Null

  /** Whether the document is indented using spaces or tabs.
    *
    * If this is `true`, the document is indented using spaces. If it's `false`,
    * the document is indented using tabs. If it's `null`, we haven't yet seen
    * the indentation character used by the document.
    */
  private var _spaces: Nullable[Boolean] = Nullable.Null

  override def indented: Boolean = true

  // ---------------------------------------------------------------------------
  // Simple overrides — dart-sass sass.dart lines 68-71
  // ---------------------------------------------------------------------------

  override protected def atEndOfStatement(): Boolean = {
    val c = scanner.peekChar()
    c < 0 || CharCode.isNewline(c)
  }

  override protected def lookingAtChildren(): Boolean =
    atEndOfStatement() && _peekIndentation() > currentIndentation

  override protected def whitespaceWithoutComments(consumeNewlines: Boolean): Unit = {
    // This overrides whitespace consumption to only consume newlines when
    // `consumeNewlines` is true.
    boundary {
      while (!scanner.isDone) {
        val next = scanner.peekChar()
        if (next < 0) break(())
        if (if (consumeNewlines) !CharCode.isWhitespace(next) else !CharCode.isSpaceOrTab(next)) break(())
        scanner.readChar()
      }
    }
  }

  // ---------------------------------------------------------------------------
  // styleRuleSelector — dart-sass sass.dart lines 43-54
  // ---------------------------------------------------------------------------

  override protected def styleRuleSelector(): Interpolation = {
    val start = scanner.state

    val buffer = new InterpolationBuffer()
    var continue_ = true
    while (continue_) {
      buffer.addInterpolation(almostAnyValue(omitComments = true))
      buffer.writeCharCode(CharCode.$lf)
      continue_ = buffer.trailingString.trim.endsWith(",") &&
        scanCharIf(c => c >= 0 && CharCode.isNewline(c))
    }

    buffer.interpolation(spanFrom(start))
  }

  // ---------------------------------------------------------------------------
  // expectStatementSeparator — dart-sass sass.dart lines 56-66
  // ---------------------------------------------------------------------------

  override protected def expectStatementSeparator(name: Nullable[String] = Nullable.Null): Unit = {
    val trailingSemicolon = _tryTrailingSemicolon()
    if (!atEndOfStatement()) {
      _expectNewline(trailingSemicolon = trailingSemicolon)
    }
    if (_peekIndentation() <= currentIndentation) return
    scanner.error(
      s"Nothing may be indented ${if (name.isEmpty) "here" else s"beneath a ${name.get}"}.",
      _nextIndentationEnd.get.position,
      0
    )
  }

  // ---------------------------------------------------------------------------
  // importArgument — dart-sass sass.dart lines 73-116
  // ---------------------------------------------------------------------------

  /** Consumes an import argument for the indented syntax.
    *
    * Unlike SCSS, the indented syntax supports bare (unquoted) import URLs
    * in addition to quoted strings and `url(...)` syntax.
    *
    * dart-sass sass.dart lines 73-116.
    *
    * Note: This will become an `override` when `importArgument` is extracted
    * as a virtual method in StylesheetParser's `_importRule`.
    */
  @annotation.nowarn("msg=unused private member") // scaffolding: will be wired when importArgument is virtual
  private def _importArgument(): Import = {
    val c = scanner.peekChar()
    // url(...) or URL(...)
    if (c == CharCode.$u || c == CharCode.$U) {
      val start = scanner.state
      if (scanIdentifier("url")) {
        if (scanner.scanChar(CharCode.$lparen)) {
          scanner.state = start
          return _superImportArgument()
        } else {
          scanner.state = start
        }
      }
    }
    // Quoted string
    if (c == CharCode.$single_quote || c == CharCode.$double_quote) {
      return _superImportArgument()
    }

    // Bare URL — consume until comma, semicolon, or newline
    val start = scanner.state
    var next = scanner.peekChar()
    while (next >= 0 &&
           next != CharCode.$comma &&
           next != CharCode.$semicolon &&
           !CharCode.isNewline(next)) {
      scanner.readChar()
      next = scanner.peekChar()
    }
    val url  = scanner.substring(start.position)
    val span = spanFrom(start)

    if (_isPlainImportUrl(url)) {
      // Serialize [url] as a Sass string because [StaticImport] expects it to
      // include quotes.
      StaticImport(
        Interpolation.plain(new SassString(url).toString, span),
        span
      )
    } else {
      try {
        DynamicImport(_parseImportUrl(url), span)
      } catch {
        case e: Exception =>
          error(s"Invalid URL: ${e.getMessage}", span)
      }
    }
  }

  /** Delegate to the SCSS-style import argument parsing in StylesheetParser.
    *
    * Since `importArgument` is not a virtual method in StylesheetParser
    * (import parsing is inlined in `_atRule`), this re-implements the
    * quoted-string / `url()` path that SCSS uses.
    */
  private def _superImportArgument(): Import = {
    val importStart = scanner.state
    val c = scanner.peekChar()
    if (c == CharCode.$u || c == CharCode.$U) {
      // url(...) syntax
      val urlText = _consumeImportUrlForSass()
      val urlInterp = Interpolation.plain(urlText, spanFrom(importStart))
      StaticImport(urlInterp, spanFrom(importStart))
    } else {
      // Quoted string
      val url = string()
      val isPlainCss = url.endsWith(".css") || url.startsWith("http://") ||
        url.startsWith("https://") || url.startsWith("//")
      if (isPlainCss) {
        val urlInterp = Interpolation.plain(s"\"$url\"", spanFrom(importStart))
        StaticImport(urlInterp, spanFrom(importStart))
      } else {
        DynamicImport(url, spanFrom(importStart))
      }
    }
  }

  /** Consumes a `url(...)` token. Returns the full text including `url(` and `)`. */
  private def _consumeImportUrlForSass(): String = {
    val buf = new StringBuilder()
    val ident = identifier()
    if (!ident.equalsIgnoreCase("url")) scanner.error("Expected 'url'.")
    buf.append(ident)
    scanner.expectChar(CharCode.$lparen)
    buf.append('(')
    whitespace(consumeNewlines = false)
    val c = scanner.peekChar()
    if (c == CharCode.$double_quote || c == CharCode.$single_quote) {
      buf.append(c.toChar)
      scanner.readChar()
      while (!scanner.isDone && scanner.peekChar() != c) {
        if (scanner.peekChar() == CharCode.$backslash) {
          buf.append(scanner.readChar().toChar)
          if (!scanner.isDone) buf.append(scanner.readChar().toChar)
        } else {
          buf.append(scanner.readChar().toChar)
        }
      }
      if (!scanner.isDone) buf.append(scanner.readChar().toChar)
    } else {
      var urlDone = false
      while (!scanner.isDone && !urlDone) {
        val ch = scanner.peekChar()
        if (ch == CharCode.$rparen || CharCode.isWhitespace(ch)) {
          urlDone = true
        } else if (ch == CharCode.$backslash) {
          buf.append(scanner.readChar().toChar)
          if (!scanner.isDone) buf.append(scanner.readChar().toChar)
        } else {
          buf.append(scanner.readChar().toChar)
        }
      }
    }
    whitespace(consumeNewlines = false)
    scanner.expectChar(CharCode.$rparen)
    buf.append(')')
    buf.toString()
  }

  /** Returns whether [url] indicates that an `@import` is a plain CSS import.
    *
    * dart-sass: `isPlainImportUrl` (stylesheet.dart:1267-1276).
    */
  private def _isPlainImportUrl(url: String): Boolean = {
    if (url.length < 5) return false
    if (url.endsWith(".css")) return true
    val c0 = url.charAt(0).toInt
    if (c0 == CharCode.$slash) return url.length > 1 && url.charAt(1).toInt == CharCode.$slash
    if (c0 == CharCode.$h) return url.startsWith("http://") || url.startsWith("https://")
    false
  }

  /** Parses [url] as an import URL.
    *
    * dart-sass: `parseImportUrl` (stylesheet.dart:1252-1263).
    */
  private def _parseImportUrl(url: String): String = {
    // Backwards-compatibility for implementations that allow absolute Windows
    // paths in imports. Validates the URL string as a URI.
    val _ = java.net.URI.create(url)
    url
  }

  // ---------------------------------------------------------------------------
  // scanElse — dart-sass sass.dart lines 118-133
  // ---------------------------------------------------------------------------

  override protected def scanElse(ifIndentation: Int): Boolean = {
    if (_peekIndentation() != ifIndentation) return false
    val start = scanner.state
    val startIndentation = currentIndentation
    val startNextIndentation = _nextIndentation
    val startNextIndentationEnd = _nextIndentationEnd

    _readIndentation()
    if (scanner.scanChar(CharCode.$at) && scanIdentifier("else")) return true

    scanner.state = start
    _currentIndentation = startIndentation
    _nextIndentation = startNextIndentation
    _nextIndentationEnd = startNextIndentationEnd
    false
  }

  // ---------------------------------------------------------------------------
  // children — dart-sass sass.dart lines 135-141
  // ---------------------------------------------------------------------------

  override protected def children(child: () => Statement): List[Statement] = {
    val kids = mutable.ListBuffer.empty[Statement]
    _whileIndentedLower { () =>
      val parsed = _child(() => Nullable(child()))
      if (parsed.isDefined) kids += parsed.get
    }
    kids.toList
  }

  // ---------------------------------------------------------------------------
  // statements — dart-sass sass.dart lines 143-159
  // ---------------------------------------------------------------------------

  override protected def statements(statement: () => Nullable[Statement]): List[Statement] = {
    val c = scanner.peekChar()
    if (c == CharCode.$tab || c == CharCode.$space) {
      scanner.error(
        "Indenting at the beginning of the document is illegal.",
        0,
        scanner.position
      )
    }

    val stmts = mutable.ListBuffer.empty[Statement]
    while (!scanner.isDone) {
      val parsed = _child(statement)
      if (parsed.isDefined) stmts += parsed.get
      val indentation = _readIndentation()
      assert(indentation == 0, s"Expected indentation 0 at top level, got $indentation")
    }
    stmts.toList
  }

  // ---------------------------------------------------------------------------
  // _child — dart-sass sass.dart lines 161-176
  // ---------------------------------------------------------------------------

  /** Consumes a child of the current statement.
    *
    * This consumes children that are allowed at all levels of the document; the
    * [child] parameter is called to consume any children that are specifically
    * allowed in the caller's context.
    */
  private def _child(child: () => Nullable[Statement]): Nullable[Statement] = {
    val c = scanner.peekChar()
    c match {
      // Ignore empty lines.
      case CharCode.`$cr` | CharCode.`$lf` | CharCode.`$ff` =>
        Nullable.Null
      case CharCode.`$dollar` =>
        Nullable(variableDeclarationWithoutNamespace())
      case CharCode.`$slash` =>
        scanner.peekChar(1) match {
          case CharCode.`$slash` => Nullable(_silentComment())
          case CharCode.`$asterisk` => Nullable(_loudComment())
          case _ => child()
        }
      case _ => child()
    }
  }

  // ---------------------------------------------------------------------------
  // _silentComment — dart-sass sass.dart lines 178-223
  // ---------------------------------------------------------------------------

  /** Consumes an indented-style silent comment. */
  private def _silentComment(): SilentComment = {
    val start = scanner.state
    scanner.expect("//")
    val buffer = new StringBuilder()
    val parentIndentation = currentIndentation

    var outerContinue = true
    while (outerContinue) {
      val commentPrefix = if (scanner.scanChar(CharCode.$slash)) "///" else "//"

      var innerContinue = true
      while (innerContinue) {
        buffer.append(commentPrefix)

        // Skip the initial characters because we're already writing the
        // slashes.
        var i = commentPrefix.length
        while (i < currentIndentation - parentIndentation) {
          buffer.append(' ')
          i += 1
        }

        while (!scanner.isDone && {
          val c = scanner.peekChar()
          c >= 0 && !CharCode.isNewline(c)
        }) {
          buffer.append(scanner.readChar().toChar)
        }
        buffer.append('\n')

        if (_peekIndentation() < parentIndentation) {
          outerContinue = false
          innerContinue = false
        } else if (_peekIndentation() == parentIndentation) {
          // Look ahead to the next line to see if it starts another comment.
          if (scanner.peekChar(1 + parentIndentation) == CharCode.$slash &&
              scanner.peekChar(2 + parentIndentation) == CharCode.$slash) {
            _readIndentation()
          }
          innerContinue = false
        } else {
          _readIndentation()
        }
      }

      outerContinue = outerContinue && scanner.scan("//")
    }

    val comment = new SilentComment(
      buffer.toString(),
      spanFrom(start)
    )
    lastSilentComment = Nullable(comment)
    comment
  }

  // ---------------------------------------------------------------------------
  // _loudComment — dart-sass sass.dart lines 225-322
  // ---------------------------------------------------------------------------

  /** Consumes an indented-style loud comment. */
  private def _loudComment(): LoudComment = {
    val start = scanner.state
    scanner.expect("/*")

    var first = true
    val buffer = new InterpolationBuffer()
    buffer.write("/*")
    val parentIndentation = currentIndentation
    var loopContinue = true
    while (loopContinue) {
      if (first) {
        // If the first line is empty, ignore it.
        val beginningOfComment = scanner.position
        spaces()
        val c = scanner.peekChar()
        if (c >= 0 && CharCode.isNewline(c)) {
          _readIndentation()
          buffer.writeCharCode(CharCode.$space)
        } else {
          buffer.write(scanner.substring(beginningOfComment))
        }
      } else {
        buffer.writeln()
        buffer.write(" * ")
      }
      first = false

      var i = 3
      while (i < currentIndentation - parentIndentation) {
        buffer.writeCharCode(CharCode.$space)
        i += 1
      }

      var innerContinue = true
      while (!scanner.isDone && innerContinue) {
        val c = scanner.peekChar()
        c match {
          case CharCode.`$lf` | CharCode.`$cr` | CharCode.`$ff` =>
            innerContinue = false

          case CharCode.`$hash` =>
            if (scanner.peekChar(1) == CharCode.$lbrace) {
              val (expression, span) = singleInterpolation()
              buffer.add(expression, span)
            } else {
              buffer.writeCharCode(scanner.readChar())
            }

          case CharCode.`$asterisk` =>
            if (scanner.peekChar(1) == CharCode.$slash) {
              buffer.writeCharCode(scanner.readChar())
              buffer.writeCharCode(scanner.readChar())
              val span = spanFrom(start)
              whitespace(consumeNewlines = false)

              // For backwards compatibility, allow additional comments after
              // the initial comment is closed.
              while ({
                val pc = scanner.peekChar()
                pc >= 0 && CharCode.isNewline(pc) &&
                _peekIndentation() > parentIndentation
              }) {
                while (_lookingAtDoubleNewline()) {
                  _expectNewline()
                }
                _readIndentation()
                whitespace(consumeNewlines = false)
              }

              if (!scanner.isDone && { val pc = scanner.peekChar(); pc >= 0 && !CharCode.isNewline(pc) }) {
                val errorStart = scanner.state
                while (!scanner.isDone && { val pc = scanner.peekChar(); pc >= 0 && !CharCode.isNewline(pc) }) {
                  scanner.readChar()
                }
                throw new MultiSpanSassFormatException(
                  "Unexpected text after end of comment",
                  spanFrom(errorStart),
                  "extra text",
                  Map(span -> "comment")
                )
              } else {
                return new LoudComment(buffer.interpolation(span))
              }
            } else {
              buffer.writeCharCode(scanner.readChar())
            }

          case _ =>
            if (c >= 0) buffer.writeCharCode(scanner.readChar())
            else innerContinue = false
        }
      }

      if (_peekIndentation() <= parentIndentation) {
        loopContinue = false
      } else {
        // Preserve empty lines.
        while (_lookingAtDoubleNewline()) {
          _expectNewline()
          buffer.writeln()
          buffer.write(" *")
        }

        _readIndentation()
      }
    }

    // In the indented syntax, comments without an explicit `*/` closer need
    // one appended. Matches dart-sass _loudComment (sass.dart:243-245):
    //   buffer.write(" */")
    buffer.write(" */")
    new LoudComment(buffer.interpolation(spanFrom(start)))
  }

  // ---------------------------------------------------------------------------
  // Indentation engine — dart-sass sass.dart lines 338-478
  // ---------------------------------------------------------------------------

  /** Expect and consume a single newline character.
    *
    * If [trailingSemicolon] is true, this follows a semicolon, which is used
    * for error reporting.
    *
    * dart-sass sass.dart lines 338-354.
    */
  private def _expectNewline(trailingSemicolon: Boolean = false): Unit = {
    val c = scanner.peekChar()
    c match {
      case CharCode.`$cr` =>
        scanner.readChar()
        if (scanner.peekChar() == CharCode.$lf) scanner.readChar()
      case CharCode.`$lf` | CharCode.`$ff` =>
        scanner.readChar()
      case _ =>
        scanner.error(
          if (trailingSemicolon)
            "multiple statements on one line are not supported in the indented syntax."
          else
            "expected newline."
        )
    }
  }

  /** Returns whether the scanner is immediately before *two* newlines.
    *
    * dart-sass sass.dart lines 357-365.
    */
  private def _lookingAtDoubleNewline(): Boolean = {
    val c = scanner.peekChar()
    c match {
      case CharCode.`$cr` =>
        scanner.peekChar(1) match {
          case CharCode.`$lf` =>
            val c2 = scanner.peekChar(2)
            c2 >= 0 && CharCode.isNewline(c2)
          case CharCode.`$cr` | CharCode.`$ff` => true
          case _ => false
        }
      case CharCode.`$lf` | CharCode.`$ff` =>
        val c1 = scanner.peekChar(1)
        c1 >= 0 && CharCode.isNewline(c1)
      case _ => false
    }
  }

  /** As long as the scanner's position is indented beneath the starting line,
    * runs [body] to consume the next statement.
    *
    * dart-sass sass.dart lines 369-385.
    */
  private def _whileIndentedLower(body: () => Unit): Unit = {
    val parentIndentation = currentIndentation
    var childIndentation: Nullable[Int] = Nullable.Null
    while (_peekIndentation() > parentIndentation) {
      val indentation = _readIndentation()
      if (childIndentation.isEmpty) {
        childIndentation = Nullable(indentation)
      }
      if (childIndentation.get != indentation) {
        scanner.error(
          s"Inconsistent indentation, expected ${childIndentation.get} spaces.",
          scanner.position - scanner.column,
          scanner.column
        )
      }

      body()
    }
  }

  /** Consumes indentation whitespace and returns the indentation level of the
    * next line.
    *
    * dart-sass sass.dart lines 389-396.
    */
  private def _readIndentation(): Int = {
    val currentInd = {
      if (_nextIndentation.isDefined) _nextIndentation.get
      else _peekIndentation()
    }
    _currentIndentation = currentInd
    scanner.state = _nextIndentationEnd.get
    _nextIndentation = Nullable.Null
    _nextIndentationEnd = Nullable.Null
    currentInd
  }

  /** Returns the indentation level of the next line.
    *
    * dart-sass sass.dart lines 399-449.
    */
  private def _peekIndentation(): Int = {
    if (_nextIndentation.isDefined) return _nextIndentation.get

    if (scanner.isDone) {
      _nextIndentation = Nullable(0)
      _nextIndentationEnd = Nullable(scanner.state)
      return 0
    }

    val start = scanner.state
    if (!scanCharIf(c => c >= 0 && CharCode.isNewline(c))) {
      scanner.error("Expected newline.", scanner.position, 0)
    }

    var containsTab = false
    var containsSpace = false
    var nextIndentation = 0
    var lineLoop = true
    while (lineLoop) {
      containsTab = false
      containsSpace = false
      nextIndentation = 0

      var indentLoop = true
      while (indentLoop) {
        val c = scanner.peekChar()
        c match {
          case CharCode.`$space` =>
            containsSpace = true
            nextIndentation += 1
            scanner.readChar()
          case CharCode.`$tab` =>
            containsTab = true
            nextIndentation += 1
            scanner.readChar()
          case _ =>
            indentLoop = false
        }
      }

      if (scanner.isDone) {
        _nextIndentation = Nullable(0)
        _nextIndentationEnd = Nullable(scanner.state)
        scanner.state = start
        return 0
      }

      lineLoop = scanCharIf(c => c >= 0 && CharCode.isNewline(c))
    }

    _checkIndentationConsistency(containsTab, containsSpace)

    _nextIndentation = Nullable(nextIndentation)
    if (nextIndentation > 0) {
      if (_spaces.isEmpty) _spaces = Nullable(containsSpace)
    }
    _nextIndentationEnd = Nullable(scanner.state)
    scanner.state = start
    nextIndentation
  }

  /** Ensures that the document uses consistent characters for indentation.
    *
    * The [containsTab] and [containsSpace] parameters refer to a single line of
    * indentation that has just been parsed.
    *
    * dart-sass sass.dart lines 456-478.
    */
  private def _checkIndentationConsistency(containsTab: Boolean, containsSpace: Boolean): Unit = {
    if (containsTab) {
      if (containsSpace) {
        scanner.error(
          "Tabs and spaces may not be mixed.",
          scanner.position - scanner.column,
          scanner.column
        )
      } else if (_spaces.isDefined && _spaces.get == true) {
        scanner.error(
          "Expected spaces, was tabs.",
          scanner.position - scanner.column,
          scanner.column
        )
      }
    } else if (containsSpace && _spaces.isDefined && _spaces.get == false) {
      scanner.error(
        "Expected tabs, was spaces.",
        scanner.position - scanner.column,
        scanner.column
      )
    }
  }

  /** Consumes a semicolon and trailing whitespace, including comments.
    *
    * Returns whether a semicolon was consumed.
    *
    * dart-sass sass.dart lines 483-489.
    */
  private def _tryTrailingSemicolon(): Boolean = {
    if (scanCharIf(c => c == CharCode.$semicolon)) {
      whitespace(consumeNewlines = false)
      true
    } else {
      false
    }
  }
}
