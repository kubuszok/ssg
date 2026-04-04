/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scala.js implementation of resource loading for tests.
 * Uses Node.js `fs` module to read resource files from the filesystem,
 * since Class.getResourceAsStream is not available on Scala.js.
 *
 * sbt copies test resources to ssg-md/target/js-3/test-classes/.
 * When JS tests run via Node.js, the working directory is the project root,
 * so we resolve resource paths relative to that directory.
 */
package ssg
package md
package test
package util

import java.io.{ ByteArrayInputStream, InputStream }

import scala.scalajs.js
import scala.scalajs.js.typedarray.{ Int8Array, Uint8Array }
import scala.util.boundary
import scala.util.boundary.break

object ResourceCompatPlatform {

  private val fs       = js.Dynamic.global.require("fs")
  private val nodePath = js.Dynamic.global.require("path")

  /** Base directories where sbt places test resources, in search order. */
  private val baseDirs: Array[String] = Array(
    "ssg-md/target/js-3/test-classes"
  )

  def getResourceAsStream(cls: Class[?], path: String): InputStream = {
    val cleanPath = if (path.startsWith("/")) path.substring(1) else path

    boundary[InputStream] {
      var i = 0
      while (i < baseDirs.length) {
        val filePath = nodePath.join(baseDirs(i), cleanPath).asInstanceOf[String]
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
          break(new ByteArrayInputStream(bytes))
        }
        i += 1
      }

      throw new IllegalStateException(
        "Resource not found: " + path + " (searched in " + baseDirs.mkString(", ") + ")"
      )
    }
  }
}
