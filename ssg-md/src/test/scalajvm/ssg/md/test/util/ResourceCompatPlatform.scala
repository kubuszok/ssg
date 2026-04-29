/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM implementation of resource loading for tests.
 * Uses Class.getResourceAsStream from the standard JVM classloader. */
package ssg
package md
package test
package util

import java.io.InputStream

object ResourceCompatPlatform {

  def getResourceAsStream(cls: Class[?], path: String): InputStream = {
    val stream = cls.getResourceAsStream(path)
    if (stream == null) { // @nowarn — Java interop: getResourceAsStream returns null when not found
      throw new IllegalStateException("Resource not found: " + path)
    }
    stream
  }
}
