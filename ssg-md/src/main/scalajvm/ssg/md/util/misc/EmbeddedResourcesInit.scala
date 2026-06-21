/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM: no embedded-resources registration is needed — multiarch.resources.PlatformResources reads the
 * classpath directly via Class.getResourceAsStream. `ensure()` is a no-op (counterpart to the Scala.js
 * implementation that defeats DCE for the generated embedded-resources object). */
package ssg
package md
package util
package misc

object EmbeddedResourcesInit {

  def ensure(): Unit = ()
}
