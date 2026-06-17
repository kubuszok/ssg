/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Covenant: original
 *
 * Site pipeline module — composes ssg-md, ssg-liquid, ssg-sass, ssg-minify,
 * and ssg-js into a Jekyll-compatible static site generator.
 *
 * This is an SSG-native module (not a port of an external library).
 * See docs/architecture/site-pipeline-design.md for the full design.
 */
package ssg
package site

/** Module version constant for ssg-site. */
object SitePipeline {

  /** Marker value confirming the ssg-site module is on the classpath. */
  val moduleId: String = "ssg-site"
}
