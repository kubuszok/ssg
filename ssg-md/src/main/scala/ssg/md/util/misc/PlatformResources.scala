/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Cross-platform resource loading abstraction.
 *
 * Boundary shim over the shared multiarch-resources mechanism
 * (multiarch.resources.PlatformResources), which provides a uniform classpath-style resource lookup on
 * the JVM, Scala.js, and Scala Native. The shared API returns Option; this shim converts the result to
 * the lls Nullable[A] type so the existing ssg-md call sites (Utils, Html5Entities, EmojiReference,
 * AdmonitionExtension) stay unchanged.
 *
 * On Scala.js (which has no classpath) the shared implementation consults a build-time-embedded
 * registry first — ssg-md's main resources are embedded by MultiArchResourcesPlugin.embeddedResourcesSettings
 * (see build.sbt, ssg-md JS axis) into the self-registering ssg.md.util.misc.GeneratedEmbeddedResources
 * object — which makes resource resolution independent of the process working directory (ISS-979).
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
  def getResourceAsStream(cls: Class[?], path: String): Nullable[InputStream] = {
    // On Scala.js, force-initialize the generated embedded-resources object (no-op on JVM/Native) so its
    // self-registration runs before the lookup and DCE does not drop it (ISS-979).
    EmbeddedResourcesInit.ensure()
    Nullable.fromOption(_root_.multiarch.resources.PlatformResources.getResourceAsStream(cls, path))
  }
}
