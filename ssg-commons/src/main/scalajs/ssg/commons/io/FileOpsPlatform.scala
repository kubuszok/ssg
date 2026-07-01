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
 *  - isDirectory/isRegularFile use a single statSync (no existsSync pre-check) so there is no window in which the
 *    entry can vanish between two calls (e.g. an external process under watch mode). When statSync fails because the
 *    entry is absent or its attributes cannot be read, the answer is false — exactly the JVM reference behavior:
 *    java.nio.file.Files.isDirectory/isRegularFile are documented to "return false if the file does not exist or it
 *    cannot be determined whether the file is a directory [/regular file]". The catch is that JVM-parity semantic,
 *    not a swallowed operation failure: it is the type-of-entry question answering "no" for an unreadable entry.
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
    // Single statSync, no existsSync pre-check: there is no window in which the entry can be removed between two
    // calls. When statSync fails because the entry is absent or its attributes are unreadable (Node surfaces ENOENT /
    // ENOTDIR / EACCES as js.JavaScriptException), the answer is false — the JVM-parity semantic of
    // java.nio.file.Files.isDirectory, which "returns false if the file does not exist or it cannot be determined
    // whether the file is a directory". This is the type-of-entry question answering "no", not a swallowed operation.
    try
      fs.statSync(path.pathString).isDirectory().asInstanceOf[Boolean]
    catch {
      case _: js.JavaScriptException => false
    }

  def isRegularFile(path: FilePath): Boolean =
    // Single statSync, no existsSync pre-check: there is no window in which the entry can be removed between two
    // calls. When statSync fails because the entry is absent or its attributes are unreadable (Node surfaces ENOENT /
    // ENOTDIR / EACCES as js.JavaScriptException), the answer is false — the JVM-parity semantic of
    // java.nio.file.Files.isRegularFile, which "returns false if the file does not exist or it cannot be determined
    // whether the file is a regular file". This is the type-of-entry question answering "no", not a swallowed operation.
    try
      fs.statSync(path.pathString).isFile().asInstanceOf[Boolean]
    catch {
      case _: js.JavaScriptException => false
    }

  /** Last-modified time in milliseconds since the epoch, read from Node's `statSync(...).mtimeMs` (a Double, floored to a Long). The Node analogue of Files.getLastModifiedTime: statSync throws ENOENT
    * (surfaced as js.JavaScriptException) for a missing path, matching the JVM contract that reading the mtime of an absent file fails.
    */
  def lastModifiedTime(path: FilePath): Long =
    fs.statSync(path.pathString).mtimeMs.asInstanceOf[Double].toLong

  /** True when the entry at `p`, examined without dereferencing, is a directory (a directory symlink reports false, matching the JVM `Files.isDirectory(_, NOFOLLOW_LINKS)` used by the reference
    * impl).
    */
  private def isDirNoFollow(p: String): Boolean =
    fs.lstatSync(p).isDirectory().asInstanceOf[Boolean]

  /** Immediate children, sorted by path string for deterministic output across platforms. Node readdirSync throws ENOENT for a missing path and ENOTDIR for a non-directory path, both propagated (no
    * swallow), matching the missing / non-directory contract of the JVM reference.
    */
  def list(path: FilePath): List[FilePath] = {
    val names = fs.readdirSync(path.pathString).asInstanceOf[js.Array[String]]
    names.toList.map(path.resolve).sortBy(_.pathString)
  }

  /** Recursive pre-order descent mirroring the JVM reference: each level via [[list]] (sorted), directory before its contents, directory symlinks not descended into (lstat tests the link, not its
    * target).
    */
  def walkTree(path: FilePath): List[FilePath] = {
    val builder = List.newBuilder[FilePath]
    def descend(dir: FilePath): Unit =
      list(dir).foreach { child =>
        builder += child
        if (isDirNoFollow(child.pathString)) descend(child)
      }
    descend(path)
    builder.result()
  }

  /** Nested, idempotent directory creation (recursive=true returns normally when the path already exists, the Node analogue of Files.createDirectories).
    */
  def createDirectories(path: FilePath): Unit =
    fs.mkdirSync(path.pathString, js.Dynamic.literal(recursive = true)): Unit

  /** Byte-exact file copy; copyFileSync overwrites the destination by default (static-asset rebuild, per the design). */
  def copy(from: FilePath, to: FilePath): Unit =
    fs.copyFileSync(from.pathString, to.pathString): Unit

  /** Removes a file or an entire tree; a missing path is a no-op. Symlinks are deleted as links — a directory symlink is unlinked directly (its target is never descended into), giving the clean-build
    * safety property (design Q13). The tree is walked and removed by hand rather than via fs.rmSync(recursive) so the symlink rule is explicit and identical to the JVM and Native implementations.
    */
  def deleteRecursively(path: FilePath): Unit = {
    val p = path.pathString
    if (lexists(p)) {
      if (isDirNoFollow(p)) {
        list(path).foreach(deleteRecursively)
        fs.rmdirSync(p): Unit
      } else {
        fs.unlinkSync(p): Unit
      }
    }
  }

  /** Presence test that does not dereference: an lstatSync that succeeds means an entry is there (a regular file, a directory, or a symlink — even one whose target is gone), while a missing path
    * makes Node throw an ENOENT error surfaced as js.JavaScriptException. This is detection only (it answers "is something here to remove?"); it never swallows a failure of an operation actually
    * being performed. existsSync is unsuitable because it follows the link and would report false for a present-but-dangling symlink, defeating the no-follow delete contract.
    */
  private def lexists(p: String): Boolean =
    try {
      fs.lstatSync(p): Unit
      true
    } catch {
      case _: js.JavaScriptException => false
    }

  /** True when Node's fs module is actually available (Node runtime); false in a browser where `require` is absent. */
  lazy val isSupported: Boolean =
    try {
      fs.existsSync(".").asInstanceOf[Boolean]
      true
    } catch {
      case _: Throwable => false
    }
}
