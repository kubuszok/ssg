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
  // The handoff pinned hearth 0.3.1-54-g83c3eb5-SNAPSHOT, but the hearth 0.3.1 RELEASE is now on
  // Central for all three platforms (JVM/JS/Native) and is binary-identical-enough for our use. Using
  // the release avoids depending on the snapshot repo and pairs with the kindlings-yaml-derivation 0.2.0
  // release below (which is itself built against hearth 0.3.1 — see its POM). The pre-migration hearth
  // 0.3.0-49 broke because hearth 0.3.1 reworked the Method API (see AsDataViewMacrosImpl).
  val hearth              = "0.3.1"
  // kindlings-yaml-derivation 0.2.0 is the release built against hearth 0.3.1 and published for all three
  // platforms. (The locally-published 0.2.0-72-g5725d6b-SNAPSHOT also targets hearth 0.3.1-54 but exists
  // for the JVM axis only, so it cannot satisfy the JS/Native variants.)
  val kindlingsYaml       = "0.2.0"
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
