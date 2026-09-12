/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Markdown engine — Scala 3 port of flexmark-java.
 *
 * Covenant: full-port
 * Covenant-verified: 2026-04-26
 */
package ssg

package object md {

  /** SSG project version (snapshot). */
  val Version = "0.1.0-SNAPSHOT"

  given javaCollToRuntimeColl[T]: Conversion[java.util.Collection[T], balticporter.runtime.JavaCollection[T]] =
    c => balticporter.runtime.JavaCollection.fromJava(c)
}
