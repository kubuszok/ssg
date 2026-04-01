/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Cross-platform resource loading helper for tests.
 * Delegates to platform-specific ResourceCompatPlatform implementations.
 */
package ssg
package md
package test
package util

import java.io.InputStream

/** Provides `getResourceAsStream`-like functionality across JVM, Scala.js, and Scala Native.
  *
  * On JVM and Native, delegates to `Class.getResourceAsStream`. On Scala.js (where that method
  * does not exist), reads resource files from the filesystem via Node.js.
  */
object ResourceCompat {

  /** Opens an `InputStream` for the given classpath resource path.
    *
    * @param cls
    *   the class whose classloader to use (JVM/Native only)
    * @param path
    *   absolute resource path, e.g. `"/ssg/md/test/specs/spec.txt"`
    * @return
    *   an open `InputStream` — caller must close it
    * @throws IllegalStateException
    *   if the resource is not found
    */
  def getResourceAsStream(cls: Class[?], path: String): InputStream = {
    ResourceCompatPlatform.getResourceAsStream(cls, path)
  }
}
