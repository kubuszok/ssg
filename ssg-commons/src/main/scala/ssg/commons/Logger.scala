/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Shared diagnostics channel for SSG-native tools.
 *
 * Provides a minimal injectable logger so that SSG modules (ssg-minify,
 * ssg-js, etc.) can surface compression/minification failures instead of
 * swallowing them silently.  Callers inject a Logger implementation; the
 * default is `Logger.quiet` (no-op), preserving backward compatibility.
 *
 * Design precedent: jekyll-minifier.rb uses `Jekyll.logger.warn("Jekyll
 * Minifier:", msg)` to surface errors before degrading (rb:462, 484, 1014).
 * This trait provides an equivalent cross-module channel.
 *
 * Note: ssg-sass has its own `ssg.sass.Logger` which is a faithful port of
 * dart-sass's `lib/src/logger.dart` with a richer API (warn with span,
 * trace, deprecation; debug).  That logger is intentionally NOT unified
 * with this one — unification would break port fidelity and its covenant.
 *
 * Covenant: original
 */
package ssg
package commons

/** Injectable diagnostics channel for SSG-native warnings.
  *
  * Implementations receive a plain message string; the caller prefixes with
  * the subsystem name (e.g. "HTML compression failed", "JS compression
  * failed").
  */
trait Logger {

  /** Emit a warning diagnostic. */
  def warn(message: String): Unit
}

object Logger {

  /** A logger that silently discards all messages (the default). */
  val quiet: Logger = QuietLogger

  /** A logger that prints warnings to stderr. */
  val stderr: Logger = StderrLogger
}

/** No-op logger — discards all messages. */
private object QuietLogger extends Logger {
  override def warn(message: String): Unit = ()
}

/** Stderr logger — prints warnings to standard error. */
private object StderrLogger extends Logger {
  override def warn(message: String): Unit =
    System.err.println(s"[SSG] WARNING: $message")
}
