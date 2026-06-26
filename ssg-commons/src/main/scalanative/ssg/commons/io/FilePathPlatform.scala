/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native implementation of FilePath using POSIX string logic.
 *
 * The path is held as a string (like the JS and JVM implementations)
 * with all path-string operations implemented as pure POSIX string
 * transforms — including normalize, which uses a pure-Scala POSIX
 * dot-segment resolver (ISS-1346: Scala Native's nio normalize on
 * Windows does not collapse ".." above root, unlike JVM nio).
 * java.nio.file.Path is used ONLY for the FS I/O bridge
 * (toNioPath/fromNioPath) and for obtaining the host cwd. This makes
 * the path-string API OS-independent: on Windows Native,
 * FilePath.of("/x").isAbsolute is true and pathString uses forward
 * slashes, matching JVM and JS.
 */
package ssg
package commons
package io

import java.nio.file.{ Path, Paths }

/** Native implementation. The path is held as a string; path operations use POSIX string logic to match the JVM and JS implementations.
  */
final private[io] class NativeFilePath(val pathString: String) extends FilePath {

  // Mirrors JvmFilePath.parent (scalajvm/ssg/commons/io/FilePathPlatform.scala:26-34) via POSIX dirname.
  override def parent: Option[FilePath] = {
    val dir = FilePathPlatform.posixDirname(pathString)
    // Map POSIX dirname -> JVM getParent: null (=> None) when the root or a relative single segment.
    // dirname("/") == "/" (== input) and dirname("a") == "." while "a" has no separator; both are null
    // (None) on java.nio. dirname equal to the input means there is no further parent (the root case).
    if (dir == pathString) None
    else if (dir == "." && !pathString.contains('/')) None
    else Some(new NativeFilePath(dir))
  }

  // Mirrors JvmFilePath.resolve(String) (scalajvm/ssg/commons/io/FilePathPlatform.scala:39-47):
  // Path.resolve appends a relative child and replaces with an absolute child.
  // On posix, only a leading '/' makes a child absolute; ':' is a legal filename char (ISS-1128).
  override def resolve(other: String): FilePath =
    if (other.isEmpty) this
    else if (other.startsWith("/")) {
      // Absolute child: java.nio.file.Path.resolve returns the child unchanged.
      new NativeFilePath(other)
    } else {
      val sep = if (pathString.endsWith("/")) "" else "/"
      new NativeFilePath(pathString + sep + other)
    }

  // Mirrors JvmFilePath.resolve(FilePath) (scalajvm/ssg/commons/io/FilePathPlatform.scala:50-51).
  override def resolve(other: FilePath): FilePath =
    resolve(other.pathString)

  // Mirrors JvmFilePath.fileName (scalajvm/ssg/commons/io/FilePathPlatform.scala:55-56) via POSIX basename:
  // basename of the root is "", matching java.nio getFileName (null -> "").
  override def fileName: String =
    FilePathPlatform.posixBasename(pathString)

  // Mirrors JvmFilePath.isAbsolute (scalajvm/ssg/commons/io/FilePathPlatform.scala:60-61):
  // on posix, only a leading '/' makes a path absolute. ':' is a legal filename char (ISS-1128).
  override def isAbsolute: Boolean =
    pathString.startsWith("/")

  // Mirrors JvmFilePath.toAbsolute (scalajvm/ssg/commons/io/FilePathPlatform.scala:66-68):
  // resolves a relative path against the process working directory. java.nio.toAbsolutePath does NOT normalize, so
  // resolve (append/replace) against cwd, not path.resolve.
  override def toAbsolute: FilePath =
    if (isAbsolute) this
    else FilePathPlatform.cwd.resolve(pathString)

  // Mirrors JvmFilePath.normalize (scalajvm/ssg/commons/io/FilePathPlatform.scala:75-87):
  // Path.normalize preserves the root component (absolute paths stay absolute), strips a trailing separator, and
  // renders a path that reduces to nothing as the empty string.
  // Pure-Scala POSIX dot-segment resolution (no nio dependency): resolves "." and ".." segments per POSIX rules,
  // drops ".." above an absolute root, preserves leading ".." on relative paths.  This avoids Scala Native's nio
  // normalize() which on Windows does NOT collapse ".." above the root (ISS-1346).
  override def normalize: FilePath =
    new NativeFilePath(FilePathPlatform.posixNormalize(pathString))

  override def hashCode(): Int = pathString.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case other: NativeFilePath => pathString == other.pathString
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

  /** Pure-Scala POSIX dot-segment normalization matching java.nio.file.Path.normalize on the JVM:
    *   - Split on '/' into segments; discard empty segments (duplicate separators) and "." segments.
    *   - For each ".." segment: if the previous (non-"..") segment exists, cancel both; if the path is absolute, drop the ".." (cannot go above root); if relative, keep the ".." (it escapes upward).
    *   - Reassemble: absolute paths get a leading "/"; a result that reduces to nothing becomes "" (not ".").
    *   - Drive-absolute paths ("/C:/...") are handled: the drive prefix is preserved and ".." above the drive root is dropped.
    *
    * This replaces the previous Paths.get(...).normalize() delegation which broke on Native-Windows (Scala Native's nio normalize does not collapse ".." above the root on Windows — ISS-1346).
    */
  private[io] def posixNormalize(p: String): String =
    if (p.isEmpty) ""
    else {
      val abs      = p.startsWith("/")
      val segments = p.split("/").iterator.filter(_.nonEmpty).toArray
      // Resolve dot-segments in a single forward pass.
      val stack = new scala.collection.mutable.ArrayBuffer[String](segments.length)
      var i     = 0
      while (i < segments.length) {
        val seg = segments(i)
        if (seg == ".") {
          // Skip: "." is a no-op in normalization.
        } else if (seg == "..") {
          if (stack.nonEmpty && stack.last != "..") {
            // Cancel the previous real segment.
            stack.remove(stack.length - 1): Unit
          } else if (!abs) {
            // Relative path: keep leading ".." (it escapes above the start).
            stack += ".."
          }
          // else: absolute path, ".." above root is silently dropped (POSIX/JVM contract).
        } else {
          stack += seg
        }
        i += 1
      }
      val body = stack.mkString("/")
      if (abs) {
        if (body.isEmpty) "/" else "/" + body
      } else {
        // A path that reduces to nothing is the empty string, matching JVM Paths.get(".").normalize() == ""
        // and Paths.get("a/..").normalize() == "".
        body
      }
    }

  /** True if `p` is the POSIX-rendered form of a Windows drive-absolute path: "/X:/..." (a single drive letter then ':' at index 2). The leading '/' makes it POSIX-absolute (isAbsolute) while X: is
    * the drive; java.nio.Paths.get REJECTS this form on Windows (a leading slash before a drive is illegal), so the nio bridge strips it. Never matches a POSIX path (index-2 is ':' only for a drive
    * form), so this is a no-op off Windows.
    */
  private[io] def isDriveAbsolute(p: String): Boolean =
    p.length >= 3 && p.charAt(0) == '/' && p.charAt(2) == ':' && {
      val c = p.charAt(1); (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
    }

  /** Translate the FilePath model form to a string java.nio.Paths.get accepts: strip the leading '/' from a drive-absolute path ("/C:/x" -> "C:/x") so Windows nio parses the drive. Unchanged on
    * POSIX.
    */
  private[io] def toNioString(p: String): String =
    if (isDriveAbsolute(p)) p.substring(1) else p

  def of(path: String): FilePath =
    new NativeFilePath(renderPath(path))

  def cwd: FilePath = {
    // Host cwd rendered POSIX-style. On Windows the host path is drive-absolute (e.g. C:/Users/foo) which is
    // NOT POSIX-absolute (no leading '/'); prefix '/' so FilePath.cwd.isAbsolute holds on every OS (no-op on POSIX).
    val host = Paths.get(".").toAbsolutePath.normalize().toString.replace('\\', '/')
    new NativeFilePath(if (host.startsWith("/")) host else "/" + host)
  }

  /** Unwraps to a java.nio.file.Path for FS I/O (Native-only). On Windows, strips the model's leading '/' from a drive-absolute path ("/C:/x" -> "C:/x") so nio parses the drive letter. */
  def toNioPath(fp: FilePath): Path =
    Paths.get(toNioString(fp.pathString))

  /** Wraps a java.nio.file.Path as a FilePath, POSIX-rendering the string so children from Files.newDirectoryStream match dir.resolve(name). On linux/macOS path.toString is already '/'-separated so
    * the replace is a no-op and renderPath is idempotent; on Windows it converts backslash separators to forward slashes.
    */
  def fromNioPath(path: Path): FilePath = {
    val rendered = renderPath(path.toString.replace('\\', '/'))
    // A Windows-absolute nio path (C:/Users/foo) is not POSIX-absolute after rendering; prefix '/' so the model
    // form is absolute and round-trips via toNioString. No-op on POSIX (rendered already starts with '/').
    new NativeFilePath(if (path.isAbsolute && !rendered.startsWith("/")) "/" + rendered else rendered)
  }
}
