/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/parse/selector.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: selector.dart -> SelectorParser.scala
 *   Idiom: Self-contained recursive-descent parser using plain string
 *          indexing — does not depend on the Parser base class scanner.
 *          Spans are synthesised from the raw input via FileSpan.synthetic.
 *          Covers the common selector grammar: type / class / id / universal
 *          / parent / pseudo / attribute, plus combinators ` `, `>`, `+`, `~`
 *          and comma-separated lists. Pseudo arguments and attribute bodies
 *          are kept as raw text.
 */
package ssg
package sass
package parse

import ssg.sass.{ InterpolationMap, Nullable, SassFormatException }
import ssg.sass.util.CharCode
import ssg.sass.ast.css.CssValue
import ssg.sass.ast.selector.{
  AttributeOperator,
  AttributeSelector,
  ClassSelector,
  Combinator,
  ComplexSelector,
  ComplexSelectorComponent,
  CompoundSelector,
  IDSelector,
  ParentSelector,
  PlaceholderSelector,
  PseudoSelector,
  QualifiedName,
  SelectorList,
  SimpleSelector,
  TypeSelector,
  UniversalSelector
}
import ssg.sass.util.FileSpan

/** A parser for CSS/Sass selectors. */
class SelectorParser(
  contents:         String,
  url:              Nullable[String] = Nullable.Null,
  interpolationMap: Nullable[InterpolationMap] = Nullable.Null,
  allowParent:      Boolean = true,
  plainCss:         Boolean = false
) extends Parser(contents, url, interpolationMap) {

  protected val _allowParent: Boolean = allowParent
  protected val _plainCss:    Boolean = plainCss

  // Self-contained scanner state — independent of the Parser base class.
  private val src:         String   = contents
  private var pos:         Int      = 0
  private val syntheticSp: FileSpan = FileSpan.synthetic(if (contents.isEmpty) " " else contents)

  private def peek():         Int = if (pos >= src.length) -1 else src.charAt(pos).toInt
  private def peekAt(o: Int): Int = {
    val p = pos + o
    if (p >= src.length) -1 else src.charAt(p).toInt
  }
  private def read():   Char    = { val c = src.charAt(pos); pos += 1; c }
  private def isDone(): Boolean = pos >= src.length

  private def isWs(c: Int): Boolean =
    c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f'

  private def skipSpaces(): Unit = {
    var continue_ = true
    while (continue_ && !isDone()) {
      if (isWs(peek())) {
        pos += 1
      } else if (pos + 1 < src.length && src.charAt(pos) == '/' && src.charAt(pos + 1) == '*') {
        // Skip loud comment /* ... */
        pos += 2
        while (pos + 1 < src.length && !(src.charAt(pos) == '*' && src.charAt(pos + 1) == '/')) {
          pos += 1
        }
        if (pos + 1 < src.length) pos += 2 // skip */
      } else if (pos + 1 < src.length && src.charAt(pos) == '/' && src.charAt(pos + 1) == '/') {
        // Skip silent comment // ... to end of line
        pos += 2
        while (!isDone() && src.charAt(pos) != '\n' && src.charAt(pos) != '\r') {
          pos += 1
        }
      } else {
        continue_ = false
      }
    }
  }

  /** Like [[skipSpaces]] but returns `true` if a newline was consumed. */
  @annotation.nowarn("msg=unused private member") // kept for future use
  private def skipSpacesTrackNewline(): Boolean = {
    var hadNewline = false
    var continue_ = true
    while (continue_ && !isDone()) {
      val c = peek()
      if (isWs(c)) {
        if (c == '\n' || c == '\r' || c == '\f') hadNewline = true
        pos += 1
      } else if (pos + 1 < src.length && src.charAt(pos) == '/' && src.charAt(pos + 1) == '*') {
        pos += 2
        while (pos + 1 < src.length && !(src.charAt(pos) == '*' && src.charAt(pos + 1) == '/')) {
          pos += 1
        }
        if (pos + 1 < src.length) pos += 2
      } else if (pos + 1 < src.length && src.charAt(pos) == '/' && src.charAt(pos + 1) == '/') {
        pos += 2
        while (!isDone() && src.charAt(pos) != '\n' && src.charAt(pos) != '\r') {
          pos += 1
        }
      } else {
        continue_ = false
      }
    }
    hadNewline
  }

  private def isNameStart(c: Int): Boolean =
    (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '-' || c >= 0x80

  private def isName(c: Int): Boolean =
    isNameStart(c) || (c >= '0' && c <= '9')

  private def readIdentifier(): String = {
    val sb = new StringBuilder()
    while (!isDone() && (isName(peek()) || peek() == '\\')) {
      if (peek() == '\\') sb.append(readEscape(identifierStart = sb.isEmpty))
      else sb.append(read())
    }
    sb.toString()
  }

  /** Consumes a CSS escape sequence and returns the text to emit.
    * Mirrors `Parser.escape()`: valid name chars are decoded; control
    * chars and non-name codepoints are re-encoded as `\hex ` or `\char`.
    */
  private def readEscape(identifierStart: Boolean = false): String = {
    pos += 1 // consume backslash
    if (isDone()) return "\ufffd"
    val c = peek()
    var value = 0
    if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
      var i = 0
      while (i < 6 && !isDone()) {
        val h = peek()
        val digit =
          if (h >= '0' && h <= '9') h - '0'
          else if (h >= 'a' && h <= 'f') 10 + h - 'a'
          else if (h >= 'A' && h <= 'F') 10 + h - 'A'
          else -1
        if (digit < 0) { i = 6 }
        else { value = (value << 4) | digit; pos += 1; i += 1 }
      }
      if (!isDone() && isWs(peek())) pos += 1
      if (value == 0 || (value >= 0xd800 && value <= 0xdfff) || value > 0x10ffff)
        value = 0xfffd
    } else {
      value = src.codePointAt(pos)
      pos += Character.charCount(value)
    }
    // Decide output form (matches dart-sass escape()):
    if (if (identifierStart) CharCode.isNameStart(value) else CharCode.isName(value)) {
      new String(Character.toChars(value))
    } else if (value <= 0x1f || value == 0x7f || (identifierStart && value >= '0' && value <= '9')) {
      val sb2 = new StringBuilder()
      sb2.append('\\')
      if (value > 0xf) sb2.append(Character.forDigit(value >> 4, 16))
      sb2.append(Character.forDigit(value & 0xf, 16))
      sb2.append(' ')
      sb2.toString()
    } else {
      "\\" + new String(Character.toChars(value))
    }
  }

  private def fail(msg: String): Nothing =
    throw new SassFormatException(msg, syntheticSp)

  /** Parses the contents as a comma-separated [[SelectorList]]. */
  def parse(): SelectorList = {
    val list = parseSelectorList()
    skipSpaces()
    if (!isDone()) fail(s"Unexpected character '${src.charAt(pos)}' at $pos in selector '$src'")
    list
  }

  /** Returns the line number (0-based) for position [p] in the source. */
  private def lineAt(p: Int): Int = {
    var line = 0
    var i    = 0
    while (i < p && i < src.length) {
      if (src.charAt(i) == '\n' || src.charAt(i) == '\r' || src.charAt(i) == '\f') {
        line += 1
        // Skip \r\n as a single newline
        if (src.charAt(i) == '\r' && i + 1 < src.length && src.charAt(i + 1) == '\n') i += 1
      }
      i += 1
    }
    line
  }

  private def parseSelectorList(): SelectorList = {
    val complexes = scala.collection.mutable.ListBuffer.empty[ComplexSelector]
    skipSpaces()
    // dart-sass selector.dart:94: track the line number of the previous
    // complex selector so we can detect line breaks between selectors.
    var previousLine = lineAt(pos)
    complexes += parseComplexSelector()
    skipSpaces()
    while (!isDone() && peek() == ',') {
      pos += 1
      skipSpaces()
      while (!isDone() && peek() == ',') {
        pos += 1
        skipSpaces()
      }
      // dart-sass selector.dart:103-105: lineBreak is true if the current
      // position is on a different line from where the previous complex
      // selector started, mirroring `scanner.line != previousLine`.
      val lineBreak = lineAt(pos) != previousLine
      if (lineBreak) previousLine = lineAt(pos)
      if (!isDone())
        complexes += parseComplexSelector(lineBreak = lineBreak)
      skipSpaces()
    }
    if (complexes.isEmpty) fail("Expected selector.")
    SelectorList(complexes.toList, syntheticSp)
  }

  def parseComplexSelector(lineBreak: Boolean = false): ComplexSelector = {
    val leading    = scala.collection.mutable.ListBuffer.empty[CssValue[Combinator]]
    val components = scala.collection.mutable.ListBuffer.empty[ComplexSelectorComponent]

    // Optional leading combinator(s).
    skipSpaces()
    while (peekCombinator().isDefined && !looksLikeCompoundStart()) {
      leading += new CssValue[Combinator](peekCombinator().get, syntheticSp)
      pos += 1
      skipSpaces()
    }

    // Must have at least one compound (or trailing combinator-only — rare).
    var first        = true
    var continueLoop = true
    while (continueLoop) {
      skipSpaces()
      if (isDone() || peek() == ',') {
        continueLoop = false
      } else {
        // Read a compound (zero or more simples). If we can't read any simple,
        // we may still attach a trailing combinator to the previous component.
        val compoundOpt: Option[CompoundSelector] =
          if (looksLikeCompoundStart()) Some(parseCompoundSelector())
          else None

        // Then collect any number of trailing combinators.
        skipSpaces()
        val combinators   = scala.collection.mutable.ListBuffer.empty[CssValue[Combinator]]
        var sawDescendant = false
        // descendant-combinator is implicit whitespace; we model it as no
        // explicit combinator (Dart-sass leaves descendant implicit, with the
        // distinction encoded by an empty `combinators` list).
        while (peekCombinator().isDefined) {
          combinators += new CssValue[Combinator](peekCombinator().get, syntheticSp)
          pos += 1
          skipSpaces()
          sawDescendant = false
        }
        // No combinator advancement happens for plain whitespace; that's how
        // descendant relationships are inferred from neighbouring components.
        val _ = sawDescendant

        if (compoundOpt.isDefined) {
          components += new ComplexSelectorComponent(compoundOpt.get, combinators.toList, syntheticSp)
        } else if (combinators.nonEmpty) {
          // Trailing combinators with no compound — attach to the previous
          // component, or to the leading combinators if none.
          if (components.isEmpty) {
            leading ++= combinators
          } else {
            val last = components.last
            components(components.length - 1) = last.withAdditionalCombinators(combinators.toList)
          }
        }
        if (compoundOpt.isEmpty && combinators.isEmpty) continueLoop = false
        first = false
      }
    }
    val _ = first
    if (leading.isEmpty && components.isEmpty) fail("Expected selector.")
    new ComplexSelector(leading.toList, components.toList, syntheticSp, lineBreak = lineBreak)
  }

  /** Returns the combinator at the current position, if any (without advancing). */
  private def peekCombinator(): Option[Combinator] = peek() match {
    case '>' => Some(Combinator.Child)
    case '+' => Some(Combinator.NextSibling)
    case '~' =>
      // `~` is the following-sibling combinator unless followed immediately
      // by `=` (which would be the attribute-operator `~=`).
      if (peekAt(1) == '=') None else Some(Combinator.FollowingSibling)
    case _ => None
  }

  private def looksLikeCompoundStart(): Boolean = {
    val c = peek()
    c >= 0 && (isNameStart(c) || c == '*' || c == '#' || c == '.' || c == '[' || c == ':' || c == '&' || c == '%' || c == '\\' || c == '|')
  }

  def parseCompoundSelector(): CompoundSelector = {
    val simples      = scala.collection.mutable.ListBuffer.empty[SimpleSelector]
    var continueLoop = true
    while (continueLoop) {
      val c = peek()
      if (c < 0) continueLoop = false
      else if (isNameStart(c) || c == '*' || c == '#' || c == '.' || c == '[' || c == ':' || c == '&' || c == '%' || c == '\\' || c == '|') {
        simples += parseSimpleSelector()
      } else {
        continueLoop = false
      }
    }
    if (simples.isEmpty) fail(s"Expected compound selector at $pos in '$src'")
    new CompoundSelector(simples.toList, syntheticSp)
  }

  def parseSimpleSelector(): SimpleSelector = {
    val c = peek()
    c match {
      case '*' =>
        pos += 1
        // `*|name`, `*|*`: universal namespace followed by `|`
        if (!isDone() && peek() == '|' && peekAt(1) != '=') {
          pos += 1 // consume '|'
          if (!isDone() && peek() == '*') {
            pos += 1 // `*|*`
            new UniversalSelector(syntheticSp, namespace = Nullable("*"))
          } else {
            val local = readIdentifier()
            if (local.isEmpty) fail("Expected local name after '*|'.")
            new TypeSelector(QualifiedName(local, Nullable("*")), syntheticSp)
          }
        } else {
          new UniversalSelector(syntheticSp)
        }
      case '&' =>
        pos += 1
        // Optional suffix is captured as raw identifier-body characters.
        val sufBuf = new StringBuilder()
        while (!isDone() && (isName(peek()) || peek() == '\\')) {
          if (peek() == '\\') sufBuf.append(readEscape())
          else sufBuf.append(read())
        }
        val suffix: Nullable[String] =
          if (sufBuf.isEmpty) Nullable.Null else Nullable(sufBuf.toString)
        new ParentSelector(syntheticSp, suffix)
      case '#' =>
        pos += 1
        val name = readIdentifier()
        if (name.isEmpty) fail("Expected identifier after '#'")
        new IDSelector(name, syntheticSp)
      case '.' =>
        pos += 1
        val name = readIdentifier()
        if (name.isEmpty) fail("Expected identifier after '.'")
        new ClassSelector(name, syntheticSp)
      case '%' =>
        pos += 1
        val name = readIdentifier()
        if (name.isEmpty) fail("Expected identifier after '%'")
        new PlaceholderSelector(name, syntheticSp)
      case '[' =>
        parseAttributeSelector()
      case ':' =>
        parsePseudoSelector()
      case '|' if peekAt(1) != '=' =>
        // `|*` or `|name`: empty namespace
        pos += 1 // consume '|'
        if (!isDone() && peek() == '*') {
          pos += 1 // `|*`
          new UniversalSelector(syntheticSp, namespace = Nullable(""))
        } else {
          val local = readIdentifier()
          if (local.isEmpty) fail("Expected local name after '|'.")
          new TypeSelector(QualifiedName(local, Nullable("")), syntheticSp)
        }
      case _ if isNameStart(c) || c == '\\' =>
        val name = readIdentifier()
        // Namespace handling: `ns|name` or `ns|*`
        if (!isDone() && peek() == '|' && peekAt(1) != '=') {
          pos += 1 // consume '|'
          if (!isDone() && peek() == '*') {
            pos += 1 // `ns|*`
            new UniversalSelector(syntheticSp, namespace = Nullable(name))
          } else {
            val local = readIdentifier()
            if (local.isEmpty) fail("Expected local name after namespace.")
            new TypeSelector(QualifiedName(local, Nullable(name)), syntheticSp)
          }
        } else {
          new TypeSelector(QualifiedName(name), syntheticSp)
        }
      case _ =>
        fail(s"Unexpected character '${src.charAt(pos)}' at $pos in selector '$src'")
    }
  }

  /** Parses a `[...]` attribute selector, keeping the body as raw text. */
  private def parseAttributeSelector(): AttributeSelector = {
    if (peek() != '[') fail("Expected '['")
    pos += 1
    skipSpaces()
    // Read name (with optional namespace).
    val first = readIdentifier()
    if (first.isEmpty) fail("Expected attribute name.")
    val name: QualifiedName =
      if (!isDone() && peek() == '|' && peekAt(1) != '=') {
        pos += 1
        val local = readIdentifier()
        if (local.isEmpty) fail("Expected local attribute name.")
        QualifiedName(local, Nullable(first))
      } else {
        QualifiedName(first)
      }
    skipSpaces()

    // Optional operator + value + modifier.
    val op: Option[AttributeOperator] = peek() match {
      case '='                     => pos += 1; Some(AttributeOperator.Equal)
      case '~' if peekAt(1) == '=' => pos += 2; Some(AttributeOperator.Include)
      case '|' if peekAt(1) == '=' => pos += 2; Some(AttributeOperator.Dash)
      case '^' if peekAt(1) == '=' => pos += 2; Some(AttributeOperator.Prefix)
      case '$' if peekAt(1) == '=' => pos += 2; Some(AttributeOperator.Suffix)
      case '*' if peekAt(1) == '=' => pos += 2; Some(AttributeOperator.Substring)
      case _                       => None
    }

    if (op.isEmpty) {
      // Just `[name]`.
      skipSpaces()
      if (peek() != ']') fail("Expected ']'.")
      pos += 1
      AttributeSelector(name, syntheticSp)
    } else {
      skipSpaces()
      val value: String = peek() match {
        case '"' | '\'' =>
          val q  = read()
          val sb = new StringBuilder()
          while (!isDone() && peek() != q)
            if (peek() == '\\' && pos + 1 < src.length) {
              sb.append(src.charAt(pos))
              sb.append(src.charAt(pos + 1))
              pos += 2
            } else {
              sb.append(read())
            }
          if (peek() == q) pos += 1
          sb.toString()
        case _ =>
          val sb = new StringBuilder()
          while (!isDone() && !isWs(peek()) && peek() != ']') sb.append(read())
          sb.toString()
      }
      skipSpaces()
      val modifier: Nullable[String] =
        if (!isDone() && peek() != ']') {
          val m = readIdentifier()
          if (m.isEmpty) Nullable.Null else Nullable(m)
        } else Nullable.Null
      skipSpaces()
      if (peek() != ']') fail("Expected ']'.")
      pos += 1
      AttributeSelector.withOperator(name, op.get, value, syntheticSp, modifier)
    }
  }

  /** Parses a `:foo` or `::foo(arg)` pseudo selector. */
  private def parsePseudoSelector(): PseudoSelector = {
    if (peek() != ':') fail("Expected ':'.")
    pos += 1
    val element = if (!isDone() && peek() == ':') { pos += 1; true }
    else false
    val name = readIdentifier()
    if (name.isEmpty) fail("Expected identifier after ':'.")
    var argument: Nullable[String]       = Nullable.Null
    var selector: Nullable[SelectorList] = Nullable.Null
    if (!isDone() && peek() == '(') {
      pos += 1
      // Read raw argument until matching `)`, balancing brackets.
      val sb    = new StringBuilder()
      var depth = 1
      while (!isDone() && depth > 0) {
        val ch = peek()
        if (ch == '(') { depth += 1; sb.append(read()) }
        else if (ch == ')') {
          depth -= 1
          if (depth == 0) pos += 1
          else sb.append(read())
        } else sb.append(read())
      }
      val raw = sb.toString().trim
      // For known selector-pseudos, parse the argument as a selector list.
      if (
        SelectorParser.selectorPseudoClasses.contains(name) ||
        (element && SelectorParser.selectorPseudoElements.contains(name))
      ) {
        try
          selector = Nullable(new SelectorParser(raw, url).parse())
        catch {
          case _: Throwable => argument = Nullable(raw)
        }
      } else {
        argument = Nullable(raw)
      }
    }
    new PseudoSelector(name, syntheticSp, element = element, argument = argument, selector = selector)
  }
}

object SelectorParser {

  /** Pseudo-class selectors that take unadorned selectors as arguments. */
  val selectorPseudoClasses: Set[String] = Set(
    "not",
    "is",
    "matches",
    "where",
    "current",
    "any",
    "has",
    "host",
    "host-context"
  )

  /** Pseudo-element selectors that take unadorned selectors as arguments. */
  val selectorPseudoElements: Set[String] = Set("slotted")

  /** Parses [text] as a selector list. Returns `Nullable.Null` on parse error. */
  def tryParse(text: String): Nullable[SelectorList] =
    try Nullable(new SelectorParser(text).parse())
    catch {
      case _: Throwable => Nullable.Null
    }
}
