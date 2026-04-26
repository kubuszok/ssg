/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Cross-platform resource loading abstraction.
 * Delegates to PlatformResourcesImpl which has platform-specific implementations
 * in scalajvm/, scalajs/, and scalanative/ source directories.
 *
 * Covenant: full-port
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package misc

import java.io.InputStream

object PlatformResources {

  /** Cross-platform resource loading. Returns Nullable.empty if the resource is not found or the platform doesn't support classpath resources (Scala.js).
    */
  def getResourceAsStream(cls: Class[?], path: String): Nullable[InputStream] =
    PlatformResourcesImpl.getResourceAsStream(cls, path)
}
