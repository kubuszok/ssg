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

  /** Warns if any of the deprecations options are incompatible or unnecessary. */
  def validate(): Unit = {
    for (deprecation <- fatalDeprecations)
      if (deprecation.isFuture && !futureDeprecations.contains(deprecation)) {
        inner.warn(
          s"Future $deprecation deprecation must be enabled before it can be made fatal."
        )
      } else if (deprecation.obsoleteIn.isDefined) {
        inner.warn(
          s"$deprecation deprecation is obsolete, so does not need to be made fatal."
        )
      } else if (silenceDeprecations.contains(deprecation)) {
        inner.warn(
          s"Ignoring setting to silence $deprecation deprecation, since it has also been made fatal."
        )
      }

    for (deprecation <- silenceDeprecations)
      if (deprecation == Deprecation.UserAuthored) {
        inner.warn("User-authored deprecations should not be silenced.")
      } else if (deprecation.obsoleteIn.isDefined) {
        inner.warn(
          s"$deprecation deprecation is obsolete. If you were previously silencing it, your code may now behave in unexpected ways."
        )
      } else if (deprecation.isFuture && futureDeprecations.contains(deprecation)) {
        inner.warn(
          s"Conflicting options for future $deprecation deprecation cancel each other out."
        )
      } else if (deprecation.isFuture) {
        inner.warn(
          s"Future $deprecation deprecation is not yet active, so silencing it is unnecessary."
        )
      }

    for (deprecation <- futureDeprecations)
      if (!deprecation.isFuture) {
        inner.warn(
          s"$deprecation is not a future deprecation, so it does not need to be explicitly enabled."
        )
      }
  }

  /** Prints a warning indicating the number of deprecation warnings that were omitted due to repetition.
    *
    * @param js
    *   indicates whether this is running in JS mode, in which case it doesn't mention "verbose mode" because the JS API doesn't support that.
    */
  def summarize(js: Boolean = false): Unit = {
    val total = warningCounts.values.filter(_ > maxRepetitions).map(_ - maxRepetitions).sum
    if (total > 0) {
      val verboseHint = if (js) "" else "\nRun in verbose mode to see all warnings."
      inner.warn(s"$total repetitive deprecation warnings omitted.$verboseHint")
    }
  }

  override def warn(
    message:     String,
    span:        Nullable[FileSpan],
    trace:       Nullable[SassTrace],
    deprecation: Nullable[Deprecation]
  ): Unit = {
    deprecation.foreach { dep =>
      handleDeprecation(dep, message, span, trace)
    }
    if (deprecation.isEmpty) {
      inner.warn(message, span, trace, deprecation)
    }
  }

  /** Processes a deprecation warning.
    *
    * If the deprecation is in fatalDeprecations, this throws an error.
    *
    * If it's a future deprecation that hasn't been opted into or it's a deprecation that's already been warned for maxRepetitions times and limitRepetition is true, the warning is dropped.
    *
    * Otherwise, this is passed on to the inner logger.
    */
  private def handleDeprecation(
    deprecation: Deprecation,
    message:     String,
    span:        Nullable[FileSpan],
    trace:       Nullable[SassTrace]
  ): Unit = {
    // Drop future deprecations that haven't been opted into
    if (deprecation.isFuture && !futureDeprecations.contains(deprecation)) {
      return
    }

    if (fatalDeprecations.contains(deprecation)) {
      val fatalMessage = message +
        "\n\nThis is only an error because you've set the " +
        s"$deprecation deprecation to be fatal.\n" +
        "Remove this setting if you need to keep using this feature."

      (span.toOption, trace.toOption) match {
        case (Some(s), Some(t)) => throw SassRuntimeException(fatalMessage, s, t)
        case (Some(s), None)    => throw SassException(fatalMessage, s)
        case _                  => throw SassScriptException(fatalMessage)
      }
    }

    if (silenceDeprecations.contains(deprecation)) {
      return
    }

    if (limitRepetition) {
      val count = warningCounts.getOrElse(deprecation, 0) + 1
      warningCounts(deprecation) = count
      if (count > maxRepetitions) {
        return
      }
    }

    inner.warn(message, span, trace, deprecation = deprecation)
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
