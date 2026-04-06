/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/logger/deprecation_processing.dart
 * Original: Copyright (c) 2023 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: deprecation_processing.dart -> DeprecationProcessing.scala
 *   Convention: Thin shim — the actual logic lives on
 *               `ssg.sass.DeprecationProcessingLogger` in `Logger.scala`.
 *               This file exposes the configuration type and a default
 *               instance so callers in `ssg.sass.logger` can construct
 *               one without importing the Logger file directly.
 */
package ssg
package sass
package logger

/** Configuration + factory for deprecation-processing loggers. Wraps an inner [[Logger]] and tracks silence/fatal/future deprecation sets plus per-deprecation repetition limits.
  */
object DeprecationProcessing {

  /** Builds a [[DeprecationProcessingLogger]] around [[inner]] with the supplied silence/fatal/future sets. */
  def apply(
    inner:               Logger,
    silenceDeprecations: Set[Deprecation] = Set.empty,
    fatalDeprecations:   Set[Deprecation] = Set.empty,
    futureDeprecations:  Set[Deprecation] = Set.empty,
    limitRepetition:     Boolean = true
  ): DeprecationProcessingLogger =
    new DeprecationProcessingLogger(inner, silenceDeprecations, fatalDeprecations, futureDeprecations, limitRepetition)

  /** The default processing logger — wraps [[Logger.default]] with no silenced / fatal / future deprecations and repetition limiting enabled.
    */
  lazy val default: DeprecationProcessingLogger = apply(Logger.default)
}
