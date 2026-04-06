/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/parse/parser.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: parser.dart -> Parser.scala
 *   Convention: Dart @protected -> Scala protected
 *   Skeleton: Phase 6 introduces the parser hierarchy. The base class is
 *     fully structural; most token-level helpers throw UnsupportedOperationException
 *     and will be filled in alongside StylesheetParser in later passes.
 */
package ssg
package sass
package parse

import ssg.sass.util.{SpanScanner, FileSpan}
import ssg.sass.{InterpolationMap, Nullable}
import ssg.sass.Nullable.*

/** The abstract base class for all parsers.
  *
  * Provides utility methods and common token parsing. Unless specified
  * otherwise, a parse method throws a [[SassFormatException]] if it fails
  * to parse.
  */
abstract class Parser protected (
  contents: String,
  url: Nullable[String] = Nullable.Null,
  protected val interpolationMap: Nullable[InterpolationMap] = Nullable.Null
) {

  /** The scanner that scans through the text being parsed. */
  protected val scanner: SpanScanner = new SpanScanner(contents, url)

  // ## Tokens — all skeleton helpers used by subclasses. Real implementations
  // will land in a later pass; for now they throw so compilation succeeds.

  /** Consumes whitespace, including any comments. */
  protected def whitespace(consumeNewlines: Boolean): Unit =
    throw new UnsupportedOperationException("Parser.whitespace: not yet implemented in skeleton")

  /** Consumes whitespace, but not comments. */
  protected def whitespaceWithoutComments(consumeNewlines: Boolean): Unit =
    throw new UnsupportedOperationException("Parser.whitespaceWithoutComments: not yet implemented in skeleton")

  /** Consumes spaces and tabs. */
  protected def spaces(): Unit =
    throw new UnsupportedOperationException("Parser.spaces: not yet implemented in skeleton")

  /** Consumes and ignores a comment if possible. */
  protected def scanComment(): Boolean =
    throw new UnsupportedOperationException("Parser.scanComment: not yet implemented in skeleton")

  /** Consumes a plain CSS identifier and returns it. */
  protected def identifier(
    normalize: Boolean = false,
    unit: Boolean = false
  ): String =
    throw new UnsupportedOperationException("Parser.identifier: not yet implemented in skeleton")

  /** Consumes the next identifier and returns whether it matches [text]. */
  protected def scanIdentifier(text: String, caseSensitive: Boolean = false): Boolean =
    throw new UnsupportedOperationException("Parser.scanIdentifier: not yet implemented in skeleton")

  /** Consumes the next identifier and throws if it doesn't match [text]. */
  protected def expectIdentifier(text: String, name: Nullable[String] = Nullable.Null): Unit =
    throw new UnsupportedOperationException("Parser.expectIdentifier: not yet implemented in skeleton")

  /** Returns whether the scanner is looking at an identifier. */
  protected def lookingAtIdentifier(forward: Int = 0): Boolean =
    throw new UnsupportedOperationException("Parser.lookingAtIdentifier: not yet implemented in skeleton")

  /** Consumes a string literal (quoted) and returns its contents. */
  protected def string(): String =
    throw new UnsupportedOperationException("Parser.string: not yet implemented in skeleton")

  /** Consumes a declaration value and returns its text. */
  protected def declarationValue(allowEmpty: Boolean = false): String =
    throw new UnsupportedOperationException("Parser.declarationValue: not yet implemented in skeleton")

  /** Consumes whitespace and errors if none was found. */
  protected def expectWhitespace(): Unit =
    throw new UnsupportedOperationException("Parser.expectWhitespace: not yet implemented in skeleton")

  /** Creates a span from the given start state to the current position. */
  protected def spanFrom(start: ssg.sass.util.LineScannerState): FileSpan =
    scanner.spanFrom(start)

  /** Throws a SassFormatException with the given message and span. */
  protected def error(message: String, span: FileSpan): Nothing =
    throw new SassFormatException(message, span)

  /** Wraps [body] in a handler that rethrows scanner errors as
    * [[SassFormatException]]. Skeleton: just runs the body.
    */
  protected def wrapSpanFormatException[T](body: () => T): T = body()
}

object Parser {

  /** Parses [text] as a CSS identifier and returns the result. */
  def parseIdentifier(text: String): String =
    throw new UnsupportedOperationException("Parser.parseIdentifier: not yet implemented in skeleton")

  /** Returns whether [text] is a valid CSS identifier. */
  def isIdentifier(text: String): Boolean =
    try {
      parseIdentifier(text)
      true
    } catch {
      case _: SassFormatException => false
      case _: UnsupportedOperationException => false
    }

  /** Returns whether [text] starts like a variable declaration. */
  def isVariableDeclarationLike(text: String): Boolean =
    throw new UnsupportedOperationException("Parser.isVariableDeclarationLike: not yet implemented in skeleton")
}
