/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala.js implementation of platform-specific filesystem access.
 * java.nio.file.Files is not available on Scala.js, so the read is performed via
 * Node.js fs (existsSync/statSync/readFileSync), mirroring the fs-based approach in
 * ssg.md.util.misc.PlatformResourcesImpl (scalajs). This supports FileUriContentResolver
 * (flexmark/src/main/java/com/vladsch/flexmark/html/renderer/FileUriContentResolver.java)
 * which on the JVM uses java.io.File.isFile()/exists() and FileUtil.getFileContentBytesWithExceptions. */
package ssg
package md
package util
package misc

import java.io.IOException

import scala.scalajs.js
import scala.scalajs.js.typedarray.{ Int8Array, Uint8Array }

object PlatformFilesImpl {

  private lazy val fs: js.Dynamic = js.Dynamic.global.require("fs")

  def isExistingFile(path: String): Boolean =
    try
      if (fs.existsSync(path).asInstanceOf[Boolean]) {
        fs.statSync(path).isFile().asInstanceOf[Boolean]
      } else {
        false
      }
    catch {
      // Node fs.statSync throws (ENOENT etc.) on a missing/inaccessible path, whereas the
      // original java.io.File.isFile()/exists() returns false; catch to preserve that
      // non-throwing semantics. Node errors surface as JavaScriptException (an Exception),
      // so narrowing to Exception (not Throwable) still catches them.
      case _: Exception => false
    }

  def readAllBytes(path: String): Array[Byte] =
    try {
      val buffer = fs.readFileSync(path)
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
      bytes
    } catch {
      case e: IOException => throw e
      case t: Throwable   => throw new IOException(t.getMessage)
    }
}
