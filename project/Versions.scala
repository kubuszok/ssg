/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Centralized dependency / cross-build versions.
 *
 * Lives in project/ as a top-level object (rather than an anonymous `new { ... }` refinement in
 * build.sbt) because the sbt-2.0 Scala-3 build dialect drops the structural members of an anonymous
 * refinement, breaking `versions.X` field access.
 */
import sbt._

object Versions {
  // Versions we are publishing for.
  val scala3 = "3.8.4"

  // Which versions should be cross-compiled for publishing.
  val scalas:    List[String]                   = List(scala3)
  val platforms: List[VirtualAxis.PlatformAxis] = List(VirtualAxis.jvm, VirtualAxis.js, VirtualAxis.native)

  // Dependencies
  // hearth 0.4.0 / kindlings 0.3.0 — the released breaking-change line (migrated off the
  // sbt-2.0 dev snapshots we previously tracked). These carry API breaks vs the 0.3.1/0.2.0 snapshots.
  val hearth           = "0.4.0"
  val kindlingsYaml    = "0.3.0"
  val lls              = "0.3.0"
  val scalaJavaLocales = "1.5.4"
  val scalaJavaTime    = "2.6.0"

  // Multiarch
  val multiarch           = "0.4.0"
  val treeSitterProviders = "0.1.0"

  // Tests
  val munit           = "1.3.4"
  val munitScalacheck = "1.3.0"
}
