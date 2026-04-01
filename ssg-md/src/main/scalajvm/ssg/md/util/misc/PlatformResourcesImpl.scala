/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * JVM implementation of platform-specific resource loading.
 * Uses Class.getResourceAsStream which is fully supported on the JVM.
 */
package ssg
package md
package util
package misc

import java.io.InputStream

object PlatformResourcesImpl {

  def getResourceAsStream(cls: Class[?], path: String): Nullable[InputStream] = {
    Nullable(cls.getResourceAsStream(path))
  }
}
