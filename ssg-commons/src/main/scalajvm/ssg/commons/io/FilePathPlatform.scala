/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * JVM implementation of FilePath using POSIX string logic.
 *
 * The path is held as a string (like the JS and Native implementations)
 * with all path-string operations implemented as pure POSIX string
 * transforms. java.nio.file.Path is used ONLY for normalize (its proven
 * edge-case handling is reused then POSIX-rendered) and for the FS I/O
 * bridge (toNioPath/fromNioPath). This makes the path-string API
 * OS-independent: on Windows JVM, FilePath.of("/x").isAbsolute is true
 * and pathString uses forward slashes, matching JS and Native.
 */
package ssg
package commons
package io

import java.nio.file.{ Path, Paths }

/** JVM implementation. The path is held as a string; path operations use POSIX string logic to match the JS and Native implementations.
  */
final private[io] class JvmFilePath(val pathString: String) extends FilePath {

  // Mirrors JsFilePath.parent (scalajs/ssg/commons/io/FilePathPlatform.scala:56-64) via POSIX dirname.
  override def parent: Option[FilePath] = {
    val dir = FilePathPlatform.posixDirname(pathString)
    // Map POSIX dirname -> JVM getParent: null (=> None) when the root or a relative single segment.
    // dirname("/") == "/" (== input) and dirname("a") == "." while "a" has no separator; both are null
    // (None) on java.nio. dirname equal to the input means there is no further parent (the root case).
    if (dir == pathString) None
    else if (dir == "." && !pathString.contains('/')) None
    else Some(new JvmFilePath(dir))
  }

  // Mirrors JsFilePath.resolve(String) (scalajs/ssg/commons/io/FilePathPlatform.scala:69-77):
  // Path.resolve appends a relative child and replaces with an absolute child.
  // On posix, only a leading '/' makes a child absolute; ':' is a legal filename char (ISS-1128).
  override def resolve(other: String): FilePath =
    if (other.isEmpty) this
    else if (other.startsWith("/")) {
      // Absolute child: java.nio.file.Path.resolve returns the child unchanged.
      new JvmFilePath(other)
    } else {
      val sep = if (pathString.endsWith("/")) "" else "/"
      new JvmFilePath(pathString + sep + other)
    }

  // Mirrors JsFilePath.resolve(FilePath) (scalajs/ssg/commons/io/FilePathPlatform.scala:80-81).
  override def resolve(other: FilePath): FilePath =
    resolve(other.pathString)

  // Mirrors JsFilePath.fileName (scalajs/ssg/commons/io/FilePathPlatform.scala:85-86) via POSIX basename:
  // basename of the root is "", matching java.nio getFileName (null -> "").
  override def fileName: String =
    FilePathPlatform.posixBasename(pathString)

  // Mirrors JsFilePath.isAbsolute (scalajs/ssg/commons/io/FilePathPlatform.scala:90-91):
  // on posix, only a leading '/' makes a path absolute. ':' is a legal filename char (ISS-1128).
  override def isAbsolute: Boolean =
    pathString.startsWith("/")

  // Mirrors JsFilePath.toAbsolute (scalajs/ssg/commons/io/FilePathPlatform.scala:96-98):
  // resolves a relative path against the process working directory. java.nio.toAbsolutePath does NOT normalize, so
  // resolve (append/replace) against cwd, not path.resolve.
  override def toAbsolute: FilePath =
    if (isAbsolute) this
    else FilePathPlatform.cwd.resolve(pathString)

  // Mirrors JsFilePath.normalize (scalajs/ssg/commons/io/FilePathPlatform.scala:103-109):
  // Path.normalize preserves the root component (absolute paths stay absolute), strips a trailing separator, and
  // renders a path that reduces to nothing as the empty string.
  // Uses nio's proven normalize then POSIX-renders the output (replace backslash on Windows, strip trailing sep,
  // map "." -> "").
  override def normalize: FilePath = {
    val normalized = Paths.get(pathString).normalize().toString.replace('\\', '/')
    val stripped   =
      if (normalized.length > 1 && normalized.endsWith("/")) normalized.substring(0, normalized.length - 1)
      else normalized
    // Paths.get(...).normalize() returns "." where it should return "" on some edge cases;
    // map "." -> "" to match the JVM and JS contract (Paths.get(".").normalize().toString == "").
    new JvmFilePath(if (stripped == ".") "" else stripped)
  }

  override def hashCode(): Int = pathString.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case other: JvmFilePath => pathString == other.pathString
    case _ => false
  }
}

object FilePathPlatform {

  /** Pure-string rendering matching java.nio.file.Paths.get(path).toString: collapse duplicate separators and drop a trailing separator (the root "/" keeps its single separator), preserving "." /
    * ".." segments (java.nio does NOT resolve them here -- Paths.get("a/../b").toString == "a/../b"). On posix, backslash is a legal filename char (not a separator) and is preserved as-is (ISS-1128).
    */
  private def renderPath(p: String): String = {
    val abs  = p.startsWith("/")
    val body = p.split("/").iterator.filter(_.nonEmpty).mkString("/")
    if (abs) "/" + body else body
  }

  /** POSIX dirname: everything before the last '/'; root stays '/'; no-slash returns ".". */
  private[io] def posixDirname(p: String): String = {
    val idx = p.lastIndexOf('/')
    if (idx < 0) "."
    else if (idx == 0) "/"
    else p.substring(0, idx)
  }

  /** POSIX basename: the last segment after the final '/'; root '/' and empty string return "". */
  private[io] def posixBasename(p: String): String = {
    val idx = p.lastIndexOf('/')
    if (idx < 0) p // bare name — the entire string is the basename
    else if (idx == p.length - 1) "" // root "/" (after renderPath, only "/" ends with '/')
    else p.substring(idx + 1)
  }

  def of(path: String): FilePath =
    new JvmFilePath(renderPath(path))

  def cwd: FilePath = {
    // Host cwd rendered POSIX-style. On Windows the host path is drive-absolute (e.g. C:/Users/foo) which is
    // NOT POSIX-absolute (no leading '/'); prefix '/' so FilePath.cwd.isAbsolute holds on every OS (no-op on POSIX).
    val host = Paths.get(".").toAbsolutePath.normalize().toString.replace('\\', '/')
    new JvmFilePath(if (host.startsWith("/")) host else "/" + host)
  }

  /** Unwraps to a java.nio.file.Path for FS I/O (JVM-only). */
  def toNioPath(fp: FilePath): Path =
    Paths.get(fp.pathString)

  /** Wraps a java.nio.file.Path as a FilePath, POSIX-rendering the string so children from Files.newDirectoryStream match dir.resolve(name). On linux/macOS path.toString is already '/'-separated so
    * the replace is a no-op and renderPath is idempotent; on Windows it converts backslash separators to forward slashes.
    */
  def fromNioPath(path: Path): FilePath =
    new JvmFilePath(renderPath(path.toString.replace('\\', '/')))
}
