/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scala.js implementation of FilePath.
 *
 * FilePath is modelled as a string (`pathString`), but path operations are delegated to Node's `path` module —
 * specifically the POSIX flavor (`require("path").posix`) so behavior is identical regardless of host OS (a
 * Windows-hosted Node would otherwise apply Win32 separators) — mirroring the JVM reference impl
 * (ssg-commons/src/main/scalajvm/ssg/commons/io/FilePathPlatform.scala), which wraps java.nio.file.Path, and the
 * Native impl (ssg-commons/src/main/scalanative/ssg/commons/io/FilePathPlatform.scala), which delegates to
 * java.nio.file.Paths/Path. This replaces the previous hand-rolled string logic that exhibited the ISS-1127 bug
 * class (the JS twin of ISS-980, fixed on Native): normalize dropping the leading root segment so "/a/../b" became
 * the relative "b", toAbsolute prepending a bare "/" instead of resolving against the working directory, cwd
 * returning the literal ".", and parent of "/a" returning None instead of the root.
 *
 * The `path` module and `process` are acquired through a lazy `require` (same discipline as the sibling
 * FileOpsPlatform.scala and ssg-md's scalajs PlatformResourcesImpl after ISS-979) so that loading this module in a
 * browser bundle — where `require` is absent — does not crash at module-init time; it crashes only when the
 * delegating operation is first invoked. This is therefore Node-only parity, identical to FileOpsPlatform: in a
 * browser there is no `path` module and no `process.cwd()`, so the delegating operations are not available. This is
 * deliberate — the Node `path` module is the JS analogue of java.nio.file.Path the same way Native delegates to
 * java.nio, rather than re-deriving a from-scratch normalization algorithm whose edge cases would have to be
 * re-proven independently. (`of`, `isAbsolute`, and `resolve` are pure string transforms with no Node dependency,
 * matching java.nio's Paths.get(...).toString and Path.resolve, so the common constructor path works everywhere.)
 *
 * Node->JVM semantic differences are mapped deliberately (the ISS-1127 suite and FilePathIss1127ExtraJsSuite pin
 * these against JVM-evaluated values):
 *  - path.dirname returns "." for a bare relative name ("a") and "/" for the root ("/"), whereas java.nio
 *    getParent returns null for both (mapped to None). parent therefore returns None when dirname equals the
 *    input (root has no parent) or when the input contains no separator (a relative single segment has no parent).
 *  - path.normalize PRESERVES a trailing separator ("/a/b/" -> "/a/b/") whereas java.nio.file.Path.normalize strips
 *    it (except the root "/"); and path.normalize returns "." for a path that reduces to nothing (".", "a/..", "")
 *    whereas Paths.get(...).normalize() returns the empty string "". normalize therefore strips the trailing
 *    separator (unless the result is the root) and maps "." -> "" to match java.nio
 *    (Paths.get("/a/b/").normalize() == "/a/b", Paths.get(".").normalize() == "", Paths.get("a/..").normalize() == "").
 *  - path.basename returns "" for the root, already matching java.nio getFileName (null -> "") for the root.
 *  - `of` is Paths.get(path).toString, which collapses duplicate separators and strips a trailing separator but
 *    does NOT resolve "." / ".." segments (Paths.get("a/../b").toString == "a/../b"); path.normalize WOULD resolve
 *    them, so `of` uses a separator-collapse string transform, not path.normalize.
 *  - JVM toAbsolutePath does NOT normalize; it resolves the relative path against the working directory only
 *    (Paths.get("a/../b").toAbsolutePath keeps the ".."). toAbsolute therefore resolves pathString against cwd with
 *    the same append/replace logic as resolve, not via path.resolve which would additionally collapse "." / "..".
 */
package ssg
package commons
package io

import scala.scalajs.js

/** JS implementation. The path is held as a string; path operations are delegated to Node's `path` (POSIX flavor) to match the JVM and Native implementations.
  */
final private[io] class JsFilePath(val pathString: String) extends FilePath {

  // Mirrors JvmFilePath.parent (scalajvm/ssg/commons/io/FilePathPlatform.scala:18-21) via path.dirname.
  override def parent: Option[FilePath] = {
    val dir = FilePathPlatform.path.dirname(pathString).asInstanceOf[String]
    // Map Node's dirname -> JVM getParent: null (=> None) when the root or a relative single segment.
    // path.dirname("/") == "/" (== input) and path.dirname("a") == "." while "a" has no separator; both are null
    // (None) on java.nio. dirname equal to the input means there is no further parent (the root case).
    if (dir == pathString) None
    else if (dir == "." && !pathString.contains('/') && !pathString.contains('\\')) None
    else Some(new JsFilePath(dir))
  }

  // Mirrors JvmFilePath.resolve(String) (scalajvm/ssg/commons/io/FilePathPlatform.scala:23-24):
  // Path.resolve appends a relative child and replaces with an absolute child.
  override def resolve(other: String): FilePath =
    if (other.isEmpty) this
    else if (other.startsWith("/") || (other.length > 1 && other.charAt(1) == ':')) {
      // Absolute child: java.nio.file.Path.resolve returns the child unchanged.
      new JsFilePath(other)
    } else {
      val sep = if (pathString.endsWith("/") || pathString.endsWith("\\")) "" else "/"
      new JsFilePath(pathString + sep + other)
    }

  // Mirrors JvmFilePath.resolve(FilePath) (scalajvm/ssg/commons/io/FilePathPlatform.scala:26-29).
  override def resolve(other: FilePath): FilePath =
    resolve(other.pathString)

  // Mirrors JvmFilePath.fileName (scalajvm/ssg/commons/io/FilePathPlatform.scala:31-34) via path.basename:
  // basename of the root is "", matching java.nio getFileName (null -> "").
  override def fileName: String =
    FilePathPlatform.path.basename(pathString).asInstanceOf[String]

  // Mirrors JvmFilePath.isAbsolute (scalajvm/ssg/commons/io/FilePathPlatform.scala:36).
  override def isAbsolute: Boolean =
    pathString.startsWith("/") || (pathString.length > 1 && pathString.charAt(1) == ':')

  // Mirrors JvmFilePath.toAbsolute (scalajvm/ssg/commons/io/FilePathPlatform.scala:38-39):
  // resolves a relative path against the process working directory. java.nio.toAbsolutePath does NOT normalize, so
  // resolve (append/replace) against cwd, not path.resolve.
  override def toAbsolute: FilePath =
    if (isAbsolute) this
    else FilePathPlatform.cwd.resolve(pathString)

  // Mirrors JvmFilePath.normalize (scalajvm/ssg/commons/io/FilePathPlatform.scala:41-42):
  // Path.normalize preserves the root component (absolute paths stay absolute), strips a trailing separator, and
  // renders a path that reduces to nothing as the empty string.
  override def normalize: FilePath = {
    val normalized = FilePathPlatform.path.normalize(pathString).asInstanceOf[String]
    val stripped   =
      if (normalized.length > 1 && normalized.endsWith("/")) normalized.substring(0, normalized.length - 1)
      else normalized
    // path.normalize returns "." where java.nio.file.Path.normalize returns "" (e.g. ".", "a/..", "").
    new JsFilePath(if (stripped == ".") "" else stripped)
  }

  override def hashCode(): Int = pathString.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case other: JsFilePath => pathString == other.pathString
    case _ => false
  }
}

object FilePathPlatform {

  /** Node's POSIX `path` module, acquired lazily so a browser bundle (no `require`) does not crash at module init. */
  private[io] lazy val path: js.Dynamic =
    js.Dynamic.global.require("path").posix

  /** Node's `process`, acquired lazily for the same reason; `process.cwd()` is the Node analogue of the JVM reference impl's Paths.get(".").toAbsolutePath.normalize().
    */
  private lazy val process: js.Dynamic =
    js.Dynamic.global.require("process")

  /** Pure-string rendering matching java.nio.file.Paths.get(path).toString: collapse duplicate separators and drop a trailing separator (the root "/" keeps its single separator), preserving "." /
    * ".." segments (java.nio does NOT resolve them here — Paths.get("a/../b").toString == "a/../b"). Backslashes are normalised to "/" as the rest of this impl treats either separator
    * interchangeably.
    */
  private def renderPath(p: String): String = {
    val unified = p.replace('\\', '/')
    val abs     = unified.startsWith("/")
    val body    = unified.split("/").iterator.filter(_.nonEmpty).mkString("/")
    if (abs) "/" + body else body
  }

  // Mirrors JvmFilePath.of (scalajvm/ssg/commons/io/FilePathPlatform.scala:54-55).
  def of(path: String): FilePath =
    new JsFilePath(renderPath(path))

  // Mirrors JvmFilePath.cwd (scalajvm/ssg/commons/io/FilePathPlatform.scala:57-58) via process.cwd().
  def cwd: FilePath =
    new JsFilePath(process.cwd().asInstanceOf[String])
}
