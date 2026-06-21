/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala.js: defeat Scala.js dead-code elimination for the build-time-generated, self-registering
 * embedded-resources object (ssg.md.util.misc.GeneratedEmbeddedResources, produced by
 * MultiArchResourcesPlugin.embeddedResourcesSettings on the ssg-md JS axis).
 *
 * The generated object registers ssg-md's main resources into multiarch.resources.EmbeddedResources at
 * its initializer. Because nothing in the library references it by name, DCE would otherwise drop it and
 * every JS resource lookup would fall through to the cwd-relative Node fs fallback (ISS-979). Touching it
 * once from `ensure()` — invoked by the cross-platform PlatformResources shim — keeps it alive and forces
 * its registration before the first lookup. */
package ssg
package md
package util
package misc

object EmbeddedResourcesInit {

  // Reference the generated object once so DCE retains it; its initializer self-registers the embedded
  // resources. Idempotent — the registry is additive and the object initializes at most once.
  def ensure(): Unit = {
    val _ = GeneratedEmbeddedResources
  }
}
