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

import ssg.commons.Diagnostic
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

/** The severity of a build diagnostic (ISS-1382).
  *
  * ssg-site no longer declares its own `Error`/`Warning` enum — per docs/architecture/error-contracts.md section 2.10 step 1 the site pipeline adopts the shared [[ssg.commons.Severity]] (its `Error`
  * and `Warning` cases map 1:1, and it additionally carries `Info`/`Debug`). This package alias re-exports the shared enum under the `ssg.site.Severity` name so that existing call sites and
  * byte-exact diagnostics suites keep resolving `Severity.Error`/`Severity.Warning` unchanged while the underlying type is the commons one.
  */
type Severity = ssg.commons.Severity

/** Companion forwarder for the [[Severity]] alias, so `Severity.Error`/`Severity.Warning` in package `ssg.site` resolve to the shared [[ssg.commons.Severity]] cases. */
val Severity = ssg.commons.Severity

/** A structured diagnostic emitted during a site build.
  *
  * Adapts each engine's native error contract into a uniform shape that callers (CLI, test) can inspect without knowing which engine failed. Per design section 7: no silent swallow — every skipped or
  * failed step produces a diagnostic.
  *
  * Since ISS-1382 (error-contracts.md section 2.10 step 2) it embeds a shared [[ssg.commons.Diagnostic]] rather than duplicating its fields: this lets a module facade's diagnostic (with the mapped
  * source position and machine-readable code — e.g. from [[ssg.sass.Compile.compileStringResult]]) flow into the build result verbatim, while the forwarding [[severity]]/[[message]]/[[cause]] defs
  * and the `apply(file, stage, severity, message, cause)` overload keep every existing construction and read site source-compatible.
  *
  * @param file
  *   the source file that triggered the diagnostic
  * @param stage
  *   the pipeline stage that produced the diagnostic
  * @param diagnostic
  *   the shared diagnostic carrying severity, message, source position, code, and native cause
  */
final case class BuildDiagnostic(
  file:       FilePath,
  stage:      BuildStage,
  diagnostic: Diagnostic
) {

  /** The severity of the embedded diagnostic. */
  def severity: Severity = diagnostic.severity

  /** The human-readable message of the embedded diagnostic. */
  def message: String = diagnostic.message

  /** The underlying throwable of the embedded diagnostic, if any — bridged back to the `Nullable[Throwable]` shape the pre-ISS-1382 field exposed. */
  def cause: Nullable[Throwable] = diagnostic.cause.fold(Nullable.empty[Throwable])(Nullable(_))
}

object BuildDiagnostic {

  /** Builds a [[BuildDiagnostic]] from the pre-ISS-1382 field shape, wrapping the `severity`/`message`/`cause` into a shared [[ssg.commons.Diagnostic]] with `component = "ssg-site"` (the native-site
    * stages — layout, root-jail, write, minify — carry no source position, so `position`/`code` stay absent per the section 1.3 note that ssg-site has no position row). Keeps every existing
    * construction site (`BuildDiagnostic(file = …, stage = …, severity = …, message = …, cause = …)`) source-compatible.
    */
  def apply(
    file:     FilePath,
    stage:    BuildStage,
    severity: Severity,
    message:  String,
    cause:    Nullable[Throwable] = Nullable.empty
  ): BuildDiagnostic =
    BuildDiagnostic(file, stage, Diagnostic(severity, "ssg-site", message, position = None, code = None, cause = cause.toOption))
}

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
