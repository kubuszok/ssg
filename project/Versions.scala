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
  val scalas: List[String] = List(scala3)
  val platforms: List[VirtualAxis.PlatformAxis] = List(VirtualAxis.jvm, VirtualAxis.js, VirtualAxis.native)

  // Dependencies
  // Track the hearth/kindlings sbt-2.0 dev SNAPSHOTS (not the 0.3.1/0.2.0 releases): the incoming
  // hearth 0.4.0 / kindlings 0.3.0 carry breaking changes we must migrate to anyway, and the snapshots
  // compile faster + generate better code. The pair is consistent — kindlings' feat/sbt2-migration
  // branch (project/Versions.scala) itself pins hearth 0.3.1-54-g83c3eb5-SNAPSHOT, so no hearth eviction.
  // Published by their CI to the Sonatype Central Portal snapshot repo (resolver in build.sbt).
  val hearth              = "0.3.1-54-g83c3eb5-SNAPSHOT"
  val kindlingsYaml       = "0.2.0-79-gf3e5d42-SNAPSHOT"
  val lls                 = "0.2.0"
  val scalaJavaLocales    = "1.5.4"
  val scalaJavaTime       = "2.6.0"

  // Multiarch
  val multiarch           = "0.3.0"
  val treeSitterProviders = "0.1.0"

  // Tests
  val munit           = "1.3.3"
  val munitScalacheck = "1.3.0"
}
