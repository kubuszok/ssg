/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala.js: how flexmark's test infrastructure finds a spec resource. There is no classpath, and the
 * build embeds the main resources only, so a test resource is read with Node's `fs` from the
 * directories the test resources live in — the hand-written ones and the ones the port generates —
 * as the hand-written port of this infrastructure did. Anything not found there is a main resource
 * and is left to the library's own lookup. Paths are relative to the build's root directory, which
 * is where sbt starts Node. */
package ssg
package md
package test
package util

import java.io.{ ByteArrayInputStream, InputStream }

import scala.scalajs.js
import scala.scalajs.js.typedarray.{ Int8Array, Uint8Array }

object TestResources {

  private lazy val fs:       js.Dynamic = js.Dynamic.global.require("fs")
  private lazy val nodePath: js.Dynamic = js.Dynamic.global.require("path")

  private val roots: Array[String] = Array(
    "ssg-md/src/test/resources",
    "target/balticporter/ssg-md/src_managed/test/resources",
    "target/balticporter/ssg-md-ext/src_managed/test/resources"
  )

  /** The tail of flexmark's `TestUtils.getSpecResourceFileUrl`: a missing resource fails with java's own message. */
  def fileUrl(resourceClass: Class[?], resolvedResourcePath: String): String = {
    val stream = open(resourceClass, resolvedResourcePath)
    assert(stream != null, "Resource path: '" + resolvedResourcePath + "' not found.") // java interop boundary: the contract of getResourceAsStream is null
    stream.close()
    "file:" + resolvedResourcePath
  }

  /** `Class.getResourceAsStream`'s contract: a leading slash is absolute, any other name is relative to the class's package; null when there is no such resource. */
  def open(resourceClass: Class[?], path: String): InputStream = {
    val relative =
      if (path.startsWith("/")) path.substring(1)
      else ssg.md.util.misc.Classes.packageOf(resourceClass).getName().replace('.', '/') + "/" + path
    roots.iterator
      .map(root => nodePath.join(root, relative).asInstanceOf[String])
      .find(file => fs.existsSync(file).asInstanceOf[Boolean])
      .map(read)
      .getOrElse(ssg.md.util.misc.Classes.resourceAsStream(resourceClass, path))
  }

  private def read(file: String): InputStream = {
    val buffer = fs.readFileSync(file)
    val uint8  = new Uint8Array(
      buffer.buffer.asInstanceOf[js.typedarray.ArrayBuffer],
      buffer.byteOffset.asInstanceOf[Int],
      buffer.length.asInstanceOf[Int]
    )
    val int8  = new Int8Array(uint8.buffer, uint8.byteOffset, uint8.length)
    val bytes = new Array[Byte](int8.length)
    var i     = 0
    while (i < bytes.length) {
      bytes(i) = int8(i)
      i += 1
    }
    new ByteArrayInputStream(bytes)
  }
}
