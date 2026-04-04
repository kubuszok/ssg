/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scala.js implementation of platform-specific resource loading.
 * Class.getResourceAsStream is not available on Scala.js, so we use
 * Node.js fs.readFileSync to read resource files from the filesystem.
 *
 * sbt copies resources to ssg-md/target/js-3/classes/ (main) and
 * ssg-md/target/js-3/test-classes/ (test). When JS tests run via
 * Node.js, the working directory is the project root.
 */
package ssg
package md
package util
package misc

import java.io.{ ByteArrayInputStream, InputStream }

import scala.scalajs.js
import scala.scalajs.js.typedarray.{ Int8Array, Uint8Array }
import scala.util.boundary
import scala.util.boundary.break

object PlatformResourcesImpl {

  private lazy val fs:       js.Dynamic = js.Dynamic.global.require("fs")
  private lazy val nodePath: js.Dynamic = js.Dynamic.global.require("path")

  /** Base directories where sbt places resources, in search order. Includes both compiled target directories and source resources directory as a fallback for development environments.
    */
  private val baseDirs: Array[String] = Array(
    "ssg-md/target/js-3/classes",
    "ssg-md/target/js-3/test-classes",
    "ssg-md/src/main/resources",
    "ssg-md/src/test/resources",
    "target/js-3/classes",
    "target/js-3/test-classes"
  )

  def getResourceAsStream(cls: Class[?], path: String): Nullable[InputStream] = {
    val cleanPath = if (path.startsWith("/")) path.substring(1) else path
    boundary[Nullable[InputStream]] {
      var i = 0
      while (i < baseDirs.length) {
        val result = tryReadFile(baseDirs(i), cleanPath)
        if (result.isDefined) {
          break(result)
        }
        i += 1
      }
      Nullable.empty[InputStream]
    }
  }

  private def tryReadFile(baseDir: String, cleanPath: String): Nullable[InputStream] =
    try {
      val filePath = nodePath.join(baseDir, cleanPath).asInstanceOf[String]
      if (fs.existsSync(filePath).asInstanceOf[Boolean]) {
        val buffer = fs.readFileSync(filePath)
        val uint8  = new Uint8Array(
          buffer.buffer.asInstanceOf[js.typedarray.ArrayBuffer],
          buffer.byteOffset.asInstanceOf[Int],
          buffer.length.asInstanceOf[Int]
        )
        val int8  = new Int8Array(uint8.buffer, uint8.byteOffset, uint8.length)
        val bytes = new Array[Byte](int8.length)
        var j     = 0
        while (j < bytes.length) {
          bytes(j) = int8(j)
          j += 1
        }
        Nullable(new ByteArrayInputStream(bytes): InputStream)
      } else {
        Nullable.empty[InputStream]
      }
    } catch {
      case _: Throwable => Nullable.empty[InputStream]
    }
}
