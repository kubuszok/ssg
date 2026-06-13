/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Minification diagnostics channel.
 *
 * jekyll-minifier uses `Jekyll.logger.warn("Jekyll Minifier:", msg)` to surface
 * compression failures before degrading (jekyll-minifier.rb:462, 484, 1014).
 * This trait provides an equivalent injectable channel so callers can observe
 * failures that would otherwise be silently swallowed.
 *
 * Style precedent: ssg-sass/src/main/scala/ssg/sass/Logger.scala
 * (trait Logger + object Logger with a quiet/default).
 * Kept minify-local per the extensible-design preference — a future
 * cross-module unification is out of scope.
 */
package ssg
package minify

/** Injectable diagnostics channel for minification warnings.
  *
  * Mirrors jekyll-minifier's `Jekyll.logger.warn("Jekyll Minifier:", msg)` pattern (jekyll-minifier.rb:462, 484, 1014). Implementations receive a plain message string; the caller prefixes with the
  * subsystem name (e.g. "HTML compression failed").
  */
trait Logger {

  /** Emit a warning diagnostic. */
  def warn(message: String): Unit
}

object Logger {

  /** A logger that silently discards all messages (the default). */
  val quiet: Logger = QuietLogger

  /** A logger that prints warnings to stderr, matching jekyll-minifier's `Jekyll.logger.warn` output style.
    */
  val stderr: Logger = StderrLogger
}

/** No-op logger — discards all messages. */
private object QuietLogger extends Logger {
  override def warn(message: String): Unit = ()
}

/** Stderr logger — prints warnings prefixed with "Jekyll Minifier:" to match the original jekyll-minifier.rb output style (rb:462, 484, 1014).
  */
private object StderrLogger extends Logger {
  override def warn(message: String): Unit =
    System.err.println(s"Jekyll Minifier: $message")
}
