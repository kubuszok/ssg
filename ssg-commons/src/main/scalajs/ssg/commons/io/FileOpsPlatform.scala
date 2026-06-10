/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scala.js implementation of file operations using Node.js' fs module.
 *
 * This mirrors the JVM reference (scalajvm/ssg/commons/io/FileOpsPlatform.scala) operation-for-operation:
 * readAllBytes/writeBytes/exists/isDirectory/isRegularFile, each delegating to the synchronous fs APIs
 * (readFileSync/writeFileSync/existsSync/statSync) that are the Node analogue of java.nio.file.Files.
 *
 * The fs module is acquired through a lazy `require("fs")` (same discipline as ssg-md's
 * scalajs PlatformResourcesImpl after ISS-979) so that loading this module in a browser bundle — where
 * `require` is absent — does not crash at module-init time. `isSupported` reflects exactly that:
 * true when Node fs is actually available (the test/runtime environment), false in a browser.
 *
 * Buffer <-> Array[Byte] conversion is byte-exact for binary content: an Int8Array view is laid over the
 * Buffer's backing ArrayBuffer so each signed byte round-trips without reinterpretation.
 *
 * Semantic differences from the JVM reference (declared honestly):
 *  - A missing-file read throws on JVM as java.nio.file.NoSuchFileException; on Node, readFileSync throws an
 *    ENOENT error that surfaces to Scala as scala.scalajs.js.JavaScriptException. Both propagate (no swallow),
 *    matching the JVM contract that reading an absent file fails rather than returning empty.
 */
package ssg
package commons
package io

import scala.scalajs.js
import scala.scalajs.js.typedarray.{ Int8Array, Uint8Array }

private[io] object FileOpsPlatform {

  /** Node's fs module, acquired lazily so a browser bundle (no `require`) does not crash at module init. */
  private lazy val fs: js.Dynamic = js.Dynamic.global.require("fs")

  def readAllBytes(path: FilePath): Array[Byte] = {
    // Node analogue of Files.readAllBytes: a missing file throws ENOENT, propagated as JavaScriptException.
    val buffer = fs.readFileSync(path.pathString)
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
    bytes
  }

  def writeBytes(path: FilePath, bytes: Array[Byte]): Unit = {
    // Lay an Int8Array view over the Scala bytes so the Buffer Node writes is byte-exact.
    val int8 = new Int8Array(bytes.length)
    var i    = 0
    while (i < bytes.length) {
      int8(i) = bytes(i)
      i += 1
    }
    val uint8 = new Uint8Array(int8.buffer, int8.byteOffset, int8.length)
    fs.writeFileSync(path.pathString, uint8): Unit
  }

  def exists(path: FilePath): Boolean =
    fs.existsSync(path.pathString).asInstanceOf[Boolean]

  def isDirectory(path: FilePath): Boolean =
    fs.existsSync(path.pathString).asInstanceOf[Boolean] &&
      fs.statSync(path.pathString).isDirectory().asInstanceOf[Boolean]

  def isRegularFile(path: FilePath): Boolean =
    fs.existsSync(path.pathString).asInstanceOf[Boolean] &&
      fs.statSync(path.pathString).isFile().asInstanceOf[Boolean]

  /** True when Node's fs module is actually available (Node runtime); false in a browser where `require` is absent. */
  lazy val isSupported: Boolean =
    try {
      fs.existsSync(".").asInstanceOf[Boolean]
      true
    } catch {
      case _: Throwable => false
    }
}
