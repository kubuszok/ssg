/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM: how flexmark's test infrastructure finds a spec resource — java's own calls, which is what
 * the bodies this object replaces (ssg-md/port/test.conf) contained. Scala.js and Scala Native have
 * neither `Class.getResource` nor `java.net.URL` and ship their own answer at the same name. */
package ssg
package md
package test
package util

import java.io.InputStream

object TestResources {

  /** The tail of flexmark's `TestUtils.getSpecResourceFileUrl`: the resource's URL, adjusted to the source tree. */
  def fileUrl(resourceClass: Class[?], resolvedResourcePath: String): String = {
    val url = resourceClass.getResource(resolvedResourcePath)
    assert(url != null, "Resource path: '" + resolvedResourcePath + "' not found.") // java interop boundary: getResource answers null
    TestUtils.adjustedFileUrl(url)
  }

  /** `Class.getResourceAsStream`: null when there is no such resource. */
  def open(resourceClass: Class[?], path: String): InputStream = resourceClass.getResourceAsStream(path)
}
