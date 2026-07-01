/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Shared diagnostics/result envelope for SSG module facades (ISS-1102).
 *
 * The R0610 review (docs/reviews/codebase-review-2026-06-10.md section 6)
 * found seven incompatible error contracts across modules: SassException
 * hierarchies, bare RuntimeException, ParseError-or-error-span, in-band
 * HTML comments, silent passthrough, and Option conflating four failure
 * conditions. These types give every module facade one uniform envelope:
 *
 *   - [[Severity]]        — Debug < Info < Warning < Error
 *   - [[SourcePosition]]  — a lowest-common-denominator source position that
 *                           every module's native position type maps INTO
 *                           (the native types are ports and stay untouched)
 *   - [[Diagnostic]]      — severity + component + message + position +
 *                           machine-readable code + cause chain
 *   - [[DiagResult]]      — value + accumulated diagnostics, expressing
 *                           success, degraded output, and failure
 *
 * Adoption is facade-level only: ported modules keep their internal
 * exception hierarchies (port fidelity is inviolable) and the envelope
 * wraps at public entry points via SPECIFIC catches — never a blanket
 * catch-everything. The per-module adapter contract lives in
 * docs/architecture/error-contracts.md.
 *
 * This file is SSG-native shared infrastructure (no original source).
 */
package ssg
package commons

/** Severity of a [[Diagnostic]], declared in ascending order so that ordinal comparison expresses "at least this severe".
  *
  * Only [[Severity.Error]] affects [[DiagResult]] success/degraded/failure classification; Warning/Info/Debug are advisory (e.g. sass deprecation warnings, minify passthrough notices).
  */
enum Severity extends java.lang.Enum[Severity] {
  case Debug
  case Info
  case Warning
  case Error

  /** True when this severity is `threshold` or more severe (Debug < Info < Warning < Error). */
  def isAtLeast(threshold: Severity): Boolean =
    this.ordinal >= threshold.ordinal

  /** Stable lowercase label for rendering, independent of platform locale handling. */
  def label: String =
    this match {
      case Severity.Debug   => "debug"
      case Severity.Info    => "info"
      case Severity.Warning => "warning"
      case Severity.Error   => "error"
    }
}

/** A lowest-common-denominator source position.
  *
  * Every field is optional because the module-native position types carry different information. The canonical mappings (see docs/architecture/error-contracts.md for the full table):
  *
  *   - ssg-sass `FileSpan`/`FileLocation` (0-based line/column, char offsets): `line = start.line + 1`, `column = start.column + 1`, `endLine`/`endColumn` likewise from `span.end`, `offset =
  *     start.offset`, `endOffset = end.offset`, `source = span.sourceUrl`.
  *   - ssg-katex `SourceLocation` (0-based char offsets only): `offset = loc.start`, `endOffset = loc.end`; line/column stay `None`.
  *   - ssg-highlight `HighlightSpan` (0-based BYTE offsets): `offset = startByte`, `endOffset = endByte`; line/column stay `None`.
  *   - ssg-liquid `LiquidException` (1-based line, 0-based charPositionInLine): `line = e.line`, `column = e.charPositionInLine + 1`.
  *   - ssg-js `JsParseError` (1-based line, 0-based col, 0-based char pos): `line = e.line`, `column = e.col + 1`, `offset = e.pos`, `source = e.filename`.
  *   - ssg-mermaid `ParseException` (1-based line/col): `line = e.line`, `column = e.col`.
  *   - ssg-graphviz `Token` (1-based line/col): `line = t.line`, `column = t.col`.
  *
  * Convention: `line` and `column` are 1-based when present; `offset`/`endOffset` are 0-based with an exclusive end. The offset UNIT is whatever the producing module counts natively (char offsets
  * everywhere except ssg-highlight, which counts UTF-8 bytes); consumers that need to slice source text must use the producing component to pick the unit.
  *
  * @param source
  *   a label for the input (file path or URL); `None` for anonymous in-memory input
  * @param line
  *   1-based line number
  * @param column
  *   1-based column number
  * @param endLine
  *   1-based line of the end of the affected range
  * @param endColumn
  *   1-based column of the end of the affected range
  * @param offset
  *   0-based offset of the start of the affected range (unit per producing module)
  * @param endOffset
  *   0-based exclusive offset of the end of the affected range
  */
final case class SourcePosition(
  source:    Option[String] = None,
  line:      Option[Int] = None,
  column:    Option[Int] = None,
  endLine:   Option[Int] = None,
  endColumn: Option[Int] = None,
  offset:    Option[Int] = None,
  endOffset: Option[Int] = None
) {

  /** Renders the position for humans: `src:line:column` when line info is present, `src@start..end` when only offsets are, `src` otherwise. Anonymous input renders as `<input>`. */
  def render: String = {
    val src = source.getOrElse("<input>")
    line match {
      case Some(l) =>
        column match {
          case Some(c) => s"$src:$l:$c"
          case None    => s"$src:$l"
        }
      case None =>
        offset match {
          case Some(o) =>
            endOffset match {
              case Some(e) => s"$src@$o..$e"
              case None    => s"$src@$o"
            }
          case None => src
        }
    }
  }
}

object SourcePosition {

  /** A position carrying no information (all fields absent). */
  val Unknown: SourcePosition = SourcePosition()

  /** A 1-based line/column position with no source label. */
  def lineColumn(line: Int, column: Int): SourcePosition =
    SourcePosition(line = Some(line), column = Some(column))

  /** A 1-based line/column position within a labeled source. */
  def inSource(source: String, line: Int, column: Int): SourcePosition =
    SourcePosition(source = Some(source), line = Some(line), column = Some(column))

  /** A 0-based `[start, end)` offset range (unit per producing module — see class docs). */
  def offsetRange(start: Int, end: Int): SourcePosition =
    SourcePosition(offset = Some(start), endOffset = Some(end))
}

/** One structured diagnostic produced by an SSG component facade.
  *
  * @param severity
  *   how severe the diagnostic is; only [[Severity.Error]] affects [[DiagResult]] classification
  * @param component
  *   the producing component, by module name (e.g. `"ssg-sass"`, `"ssg-liquid"`)
  * @param message
  *   human-readable description (from the module's native exception/error)
  * @param position
  *   where in the input the problem lies, if the module's native error carries position info
  * @param code
  *   machine-readable kind within the component (e.g. `"parse-error"`, `"unknown-language"`), for callers that dispatch on failure kind without string-matching messages
  * @param cause
  *   the module-native exception that produced this diagnostic, if any — preserved so callers can still reach the full native error (port types stay the source of truth)
  */
final case class Diagnostic(
  severity:  Severity,
  component: String,
  message:   String,
  position:  Option[SourcePosition] = None,
  code:      Option[String] = None,
  cause:     Option[Throwable] = None
) {

  /** The cause chain starting at [[cause]], following `getCause` links oldest-wrapper-first, capped at [[Diagnostic.MaxCauseDepth]] links to stay safe on cyclic chains. */
  def causeChain: List[Throwable] = {
    val builder = List.newBuilder[Throwable]
    var current: Option[Throwable] = cause
    var remaining = Diagnostic.MaxCauseDepth
    while (current.isDefined && remaining > 0) {
      val t = current.get
      builder += t
      current = Option(t.getCause)
      remaining -= 1
    }
    builder.result()
  }

  /** Renders the diagnostic for humans: `[component] severity: message (position)`; the position suffix is omitted when [[position]] is absent. */
  def render: String = {
    val suffix = position.map(p => s" (${p.render})").getOrElse("")
    s"[$component] ${severity.label}: $message$suffix"
  }
}

object Diagnostic {

  /** Upper bound on [[Diagnostic.causeChain]] length, guarding against cyclic `getCause` chains. */
  val MaxCauseDepth: Int = 32

  /** Convenience constructor for an [[Severity.Error]] diagnostic. */
  def error(component: String, message: String, position: Option[SourcePosition] = None, code: Option[String] = None, cause: Option[Throwable] = None): Diagnostic =
    Diagnostic(Severity.Error, component, message, position, code, cause)

  /** Convenience constructor for a [[Severity.Warning]] diagnostic. */
  def warning(component: String, message: String, position: Option[SourcePosition] = None, code: Option[String] = None, cause: Option[Throwable] = None): Diagnostic =
    Diagnostic(Severity.Warning, component, message, position, code, cause)

  /** Builds a diagnostic from a caught module-native exception (caught SPECIFICALLY at the facade by its module-native type — never via a blanket catch-everything).
    *
    * The message is the throwable's message, falling back to its class name when the throwable carries no message. The throwable itself is preserved as [[Diagnostic.cause]].
    */
  def fromThrowable(severity: Severity, component: String, throwable: Throwable, position: Option[SourcePosition] = None, code: Option[String] = None): Diagnostic = {
    val message = Option(throwable.getMessage).getOrElse(throwable.getClass.getName)
    Diagnostic(severity, component, message, position, code, Some(throwable))
  }
}

/** The shared result envelope for SSG component facades: an optional value plus accumulated diagnostics.
  *
  * Three states, chosen to fit every existing module contract without rewriting module internals:
  *
  *   - '''success''': `value.isDefined` and no Error diagnostics (Warning/Info/Debug allowed — e.g. sass deprecation warnings).
  *   - '''degraded''': `value.isDefined` WITH Error diagnostics — output was produced but is a substitute for the requested artifact (mermaid renders an error diagram on parse failure; katex with
  *     `throwOnError = false` renders error HTML). Minify/js passthrough-on-failure is NOT degraded: the unoptimized content is still correct, so those facades emit Warning diagnostics and stay in
  *     the success state (see docs/architecture/error-contracts.md section 1.1).
  *   - '''failure''': `value.isEmpty` — no usable output (sass compile errors, liquid parse errors, highlight configuration errors).
  *
  * `flatMap` accumulates diagnostics from both steps in order and short-circuits the value on failure, so a facade pipeline keeps every diagnostic while producing at most one value.
  *
  * @param value
  *   the produced output, absent on failure
  * @param diagnostics
  *   every diagnostic accumulated while producing (or failing to produce) the value, in emission order
  */
final case class DiagResult[+A](
  value:       Option[A],
  diagnostics: Vector[Diagnostic]
) {

  /** True when a value is present and no diagnostic is [[Severity.Error]]. */
  def isSuccess: Boolean = value.isDefined && !hasErrors

  /** True when a value is present but at least one diagnostic is [[Severity.Error]] (fallback output). */
  def isDegraded: Boolean = value.isDefined && hasErrors

  /** True when no value was produced. */
  def isFailure: Boolean = value.isEmpty

  /** True when at least one diagnostic has severity [[Severity.Error]] (exactly Error — warnings do not count). */
  def hasErrors: Boolean = diagnostics.exists(_.severity == Severity.Error)

  /** All diagnostics with severity [[Severity.Error]], in emission order. */
  def errors: Vector[Diagnostic] = diagnostics.filter(_.severity == Severity.Error)

  /** All diagnostics with severity [[Severity.Warning]], in emission order. */
  def warnings: Vector[Diagnostic] = diagnostics.filter(_.severity == Severity.Warning)

  /** Transforms the value, keeping the diagnostics untouched. */
  def map[B](f: A => B): DiagResult[B] =
    DiagResult(value.map(f), diagnostics)

  /** Chains a dependent step: its diagnostics are appended after this result's; on failure the step is skipped and the accumulated diagnostics are kept. */
  def flatMap[B](f: A => DiagResult[B]): DiagResult[B] =
    value match {
      case Some(a) =>
        val next = f(a)
        DiagResult(next.value, diagnostics ++ next.diagnostics)
      case None =>
        DiagResult(None, diagnostics)
    }

  /** Appends one diagnostic. */
  def withDiagnostic(diagnostic: Diagnostic): DiagResult[A] =
    copy(diagnostics = diagnostics :+ diagnostic)

  /** Appends diagnostics in order. */
  def withDiagnostics(extra: Iterable[Diagnostic]): DiagResult[A] =
    copy(diagnostics = diagnostics ++ extra)

  /** Collapses to `Right(value)` when a value is present, otherwise `Left` of all accumulated diagnostics. Degraded results collapse to `Right` — check [[hasErrors]] to distinguish. */
  def toEither: Either[Vector[Diagnostic], A] =
    value.toRight(diagnostics)

  /** The value, or `fallback` on failure. */
  def getOrElse[B >: A](fallback: => B): B =
    value.getOrElse(fallback)

  /** Folds both cases, giving each access to the accumulated diagnostics. */
  def fold[B](onFailure: Vector[Diagnostic] => B, onValue: (A, Vector[Diagnostic]) => B): B =
    value match {
      case Some(a) => onValue(a, diagnostics)
      case None    => onFailure(diagnostics)
    }
}

object DiagResult {

  /** A clean success with no diagnostics. */
  def success[A](value: A): DiagResult[A] =
    DiagResult(Some(value), Vector.empty)

  /** A failure carrying at least one diagnostic (by convention the first has severity [[Severity.Error]]). */
  def failure(first: Diagnostic, rest: Diagnostic*): DiagResult[Nothing] =
    DiagResult(None, first +: rest.toVector)

  /** A degraded result: fallback output plus the diagnostics explaining why (by convention at least one has severity [[Severity.Error]]). */
  def degraded[A](value: A, first: Diagnostic, rest: Diagnostic*): DiagResult[A] =
    DiagResult(Some(value), first +: rest.toVector)

  /** Adapts an `Either`-based contract (e.g. ssg-highlight's `Either[HighlightError, String]`): `Right` becomes a clean success, `Left` a failure via `toDiagnostic`. */
  def fromEither[E, A](either: Either[E, A])(toDiagnostic: E => Diagnostic): DiagResult[A] =
    either match {
      case Right(a) => success(a)
      case Left(e)  => failure(toDiagnostic(e))
    }

  /** Combines many results: diagnostics are concatenated in order; the value is the vector of all values when EVERY input produced one, absent as soon as any input failed. */
  def sequence[A](results: Iterable[DiagResult[A]]): DiagResult[Vector[A]] = {
    val allDiagnostics = Vector.newBuilder[Diagnostic]
    val values         = Vector.newBuilder[A]
    var allDefined     = true
    results.foreach { result =>
      allDiagnostics ++= result.diagnostics
      result.value match {
        case Some(a) => values += a
        case None    => allDefined = false
      }
    }
    DiagResult(if (allDefined) Some(values.result()) else None, allDiagnostics.result())
  }
}
