/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Diagnostics and build result types for the site pipeline.
 *
 * This is an SSG-native module (not a port of an external library).
 * See docs/architecture/site-pipeline-design.md section 7 for design.
 *
 * Each pipeline stage adapts its engine's native error contract into a
 * BuildDiagnostic. The pipeline never silently swallows errors — a
 * skipped or failed minify pass records a Warning diagnostic.
 */
package ssg
package site

import lowlevel.Nullable

import ssg.commons.io.FilePath

/** The stage of the site build pipeline that produced a diagnostic.
  *
  * Each stage wraps its engine call and adapts the engine's native error contract into a [[BuildDiagnostic]] (design section 7).
  */
enum BuildStage extends java.lang.Enum[BuildStage] {
  case Config
  case Scan
  case FrontMatter
  case Liquid
  case Markdown
  case Sass
  case Minify
  case Layout
  case Write
}

/** The severity of a build diagnostic. */
enum Severity extends java.lang.Enum[Severity] {
  case Error
  case Warning
}

/** A structured diagnostic emitted during a site build.
  *
  * Adapts each engine's native error contract into a uniform shape that callers (CLI, test) can inspect without knowing which engine failed. Per design section 7: no silent swallow — every skipped or
  * failed step produces a diagnostic.
  *
  * @param file
  *   the source file that triggered the diagnostic
  * @param stage
  *   the pipeline stage that produced the diagnostic
  * @param severity
  *   whether this is an error or a warning
  * @param message
  *   a human-readable description of the problem
  * @param cause
  *   the underlying throwable, if any
  */
final case class BuildDiagnostic(
  file:     FilePath,
  stage:    BuildStage,
  severity: Severity,
  message:  String,
  cause:    Nullable[Throwable] = Nullable.empty
)

/** The result of a site build.
  *
  * Contains the list of files written to the destination directory and any diagnostics (errors/warnings) collected during the build. Per design section 7 (Q8): `Site.build` returns this instead of
  * throwing, so the caller can decide policy. A `failOnError` flag can promote any Error diagnostic to a thrown exception.
  *
  * @param written
  *   the output file paths written to the destination directory
  * @param diagnostics
  *   structured diagnostics collected during the build
  */
final case class BuildResult(
  written:     Vector[FilePath],
  diagnostics: Vector[BuildDiagnostic]
)
