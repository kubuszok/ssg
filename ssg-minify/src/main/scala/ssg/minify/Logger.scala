/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Minification diagnostics channel — re-exports ssg.commons.Logger.
 *
 * jekyll-minifier uses `Jekyll.logger.warn("Jekyll Minifier:", msg)` to surface
 * compression failures before degrading (jekyll-minifier.rb:462, 484, 1014).
 * The shared `ssg.commons.Logger` trait provides the cross-module channel;
 * this file re-exports it under `ssg.minify.Logger` for backward compatibility
 * and adds a minify-specific `stderr` implementation that matches the original
 * jekyll-minifier output prefix.
 *
 * Style precedent: ssg-sass/src/main/scala/ssg/sass/Logger.scala
 * (trait Logger + object Logger with a quiet/default).
 * ssg-sass's logger is intentionally NOT unified — it is a faithful port of
 * dart-sass's lib/src/logger.dart with a richer API.
 */
package ssg
package minify

/** Re-export of [[ssg.commons.Logger]] for backward compatibility.
  *
  * Code that imports `ssg.minify.Logger` continues to work unchanged.
  */
type Logger = ssg.commons.Logger

object Logger {

  /** A logger that silently discards all messages (the default). */
  val quiet: ssg.commons.Logger = ssg.commons.Logger.quiet

  /** A logger that prints warnings to stderr, matching jekyll-minifier's `Jekyll.logger.warn` output style (rb:462, 484, 1014).
    */
  val stderr: ssg.commons.Logger = JekyllMinifierStderrLogger
}

/** Stderr logger — prints warnings prefixed with "Jekyll Minifier:" to match the original jekyll-minifier.rb output style (rb:462, 484, 1014).
  */
private object JekyllMinifierStderrLogger extends ssg.commons.Logger {
  override def warn(message: String): Unit =
    System.err.println(s"Jekyll Minifier: $message")
}
