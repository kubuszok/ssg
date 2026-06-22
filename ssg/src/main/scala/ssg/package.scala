/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * SSG — Scala Static Site Generator.
 * Aggregator module that depends on all SSG library modules (ssg-commons,
 * ssg-data-commons, ssg-graphs-commons, ssg-graphviz, ssg-highlight,
 * ssg-js, ssg-katex, ssg-liquid, ssg-md, ssg-mermaid, ssg-minify,
 * ssg-sass, ssg-site). Exposes the project Version and hosts cross-module
 * adapters (TerserJsCompressorAdapter, TerserJsCompressorOptions) that
 * bridge ssg-js into ssg-minify.
 */
package object ssg {
  val Version = "0.1.0-SNAPSHOT"
}
