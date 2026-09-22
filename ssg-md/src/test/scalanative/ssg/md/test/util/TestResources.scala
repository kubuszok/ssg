/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native: how flexmark's test infrastructure finds a spec resource. `Class.getResource` and
 * `java.net.URL` do not exist here, so a resource is proven to exist by opening it — the build embeds
 * the test resources — and its location is the `file:` form of the path it was asked by, which is
 * what the hand-written port of this infrastructure answered. The location is only ever printed. */
package ssg
package md
package test
package util

import java.io.InputStream

object TestResources {

  /** The tail of flexmark's `TestUtils.getSpecResourceFileUrl`: a missing resource fails with java's own message. */
  def fileUrl(resourceClass: Class[?], resolvedResourcePath: String): String = {
    val stream = open(resourceClass, resolvedResourcePath)
    assert(stream != null, "Resource path: '" + resolvedResourcePath + "' not found.") // java interop boundary: getResourceAsStream answers null
    stream.close()
    "file:" + resolvedResourcePath
  }

  /** `Class.getResourceAsStream`: null when there is no such resource. */
  def open(resourceClass: Class[?], path: String): InputStream = resourceClass.getResourceAsStream(path)
}
