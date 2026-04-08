/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/logger.dart, lib/src/logger/default.dart, lib/src/logger/stderr.dart, lib/src/logger/tracking.dart
 * Original: Copyright (c) 2017-2018 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: logger.dart + logger/[all].dart -> Logger.scala
 *   Convention: Merged into single file; Dart abstract class -> Scala trait
 *   Idiom: LoggerWithDeprecationType merged into Logger trait
 */
package ssg
package sass

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.util.{ FileSpan, Trace => SassTrace }

import scala.language.implicitConversions

/** Interface for loggers that print messages produced by Sass stylesheets. */
trait Logger {

  /** Emits a warning with the given message. */
  def warn(
    message:     String,
    span:        Nullable[FileSpan] = Nullable.Null,
    trace:       Nullable[SassTrace] = Nullable.Null,
    deprecation: Nullable[Deprecation] = Nullable.Null
  ): Unit

  /** Emits a debugging message associated with the given span. */
  def debug(message: String, span: FileSpan): Unit

  /** Emits a deprecation warning. */
  def warnForDeprecation(
    deprecation: Deprecation,
    message:     String,
    span:        Nullable[FileSpan] = Nullable.Null,
    trace:       Nullable[SassTrace] = Nullable.Null
  ): Unit =
    if (deprecation.isFuture) ()
    else warn(message, span, trace, deprecation = deprecation)
}

object Logger {

  /** A logger that silently ignores all messages. */
  val quiet: Logger = QuietLogger

  /** The default logger (prints to stderr without colors). */
  val default: Logger = StderrLogger(color = false)
}

/** A logger that emits no messages. */
private object QuietLogger extends Logger {
  override def warn(
    message:     String,
    span:        Nullable[FileSpan],
    trace:       Nullable[SassTrace],
    deprecation: Nullable[Deprecation]
  ): Unit = ()

  override def debug(message: String, span: FileSpan): Unit = ()
}

/** A logger that prints warnings to stderr. */
final class StderrLogger(val color: Boolean = false) extends Logger {

  override def warn(
    message:     String,
    span:        Nullable[FileSpan],
    trace:       Nullable[SassTrace],
    deprecation: Nullable[Deprecation]
  ): Unit = {
    val sb              = new StringBuilder()
    val showDeprecation = deprecation.isDefined && !deprecation.contains(Deprecation.UserAuthored)

    if (color) {
      sb.append("\u001b[33m\u001b[1m")
      if (deprecation.isDefined) sb.append("Deprecation ")
      sb.append("Warning\u001b[0m")
      if (showDeprecation) sb.append(s" [\u001b[34m${deprecation.get}\u001b[0m]")
    } else {
      if (deprecation.isDefined) sb.append("DEPRECATION ")
      sb.append("WARNING")
      if (showDeprecation) sb.append(s" [${deprecation.get}]")
    }

    if (span.isEmpty) {
      sb.append(s": $message\n")
    } else if (trace.isDefined) {
      sb.append(s": $message\n\n${span.get.highlight()}\n")
    } else {
      sb.append(s" on ${span.get.message(message)}\n")
    }

    trace.foreach { t =>
      val traceLines = t.toString.stripTrailing().split("\n")
      val buf        = new StringBuilder()
      var i          = 0
      while (i < traceLines.length) {
        if (i > 0) buf.append("\n")
        buf.append("    ")
        buf.append(traceLines(i))
        i += 1
      }
      sb.append(buf.toString())
      sb.append("\n")
    }

    System.err.print(sb.toString())
  }

  override def debug(message: String, span: FileSpan): Unit = {
    val url    = span.sourceUrl.getOrElse("-")
    val prefix = if (color) "\u001b[1mDebug\u001b[0m" else "DEBUG"
    System.err.println(s"$url:${span.start.line + 1} $prefix: $message")
  }
}

/** A logger that wraps another and tracks whether warnings/debug were emitted. */
final class TrackingLogger(private val inner: Logger) extends Logger {

  var emittedWarning: Boolean = false
  var emittedDebug:   Boolean = false

  override def warn(
    message:     String,
    span:        Nullable[FileSpan],
    trace:       Nullable[SassTrace],
    deprecation: Nullable[Deprecation]
  ): Unit = {
    emittedWarning = true
    inner.warn(message, span, trace, deprecation)
  }

  override def debug(message: String, span: FileSpan): Unit = {
    emittedDebug = true
    inner.debug(message, span)
  }
}

/** A logger that handles deprecation processing (silencing, fatal, limits). */
final class DeprecationProcessingLogger(
  private val inner:       Logger,
  val silenceDeprecations: Set[Deprecation] = Set.empty,
  val fatalDeprecations:   Set[Deprecation] = Set.empty,
  val futureDeprecations:  Set[Deprecation] = Set.empty,
  val limitRepetition:     Boolean = true
) extends Logger {

  private val maxRepetitions = 5
  private val warningCounts  = scala.collection.mutable.HashMap.empty[Deprecation, Int]

  override def warn(
    message:     String,
    span:        Nullable[FileSpan],
    trace:       Nullable[SassTrace],
    deprecation: Nullable[Deprecation]
  ): Unit = {
    deprecation.foreach { dep =>
      if (silenceDeprecations.contains(dep)) ()
      else if (fatalDeprecations.contains(dep)) {
        span.fold(
          throw SassScriptException(message).withSpan(
            ssg.sass.util.FileSpan.synthetic("")
          )
        )(s => throw SassException(message, s))
      } else if (dep.isFuture && !futureDeprecations.contains(dep)) ()
      else {
        if (limitRepetition) {
          val count = warningCounts.getOrElse(dep, 0)
          warningCounts(dep) = count + 1
          if (count >= maxRepetitions) ()
          else inner.warn(message, span, trace, deprecation)
        } else {
          inner.warn(message, span, trace, deprecation)
        }
      }
    }
    if (deprecation.isEmpty) {
      inner.warn(message, span, trace, deprecation)
    }
  }

  override def debug(message: String, span: FileSpan): Unit =
    inner.debug(message, span)

  override def warnForDeprecation(
    deprecation: Deprecation,
    message:     String,
    span:        Nullable[FileSpan],
    trace:       Nullable[SassTrace]
  ): Unit =
    warn(message, span, trace, deprecation = deprecation)
}
