/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native implementation of FilePath.
 *
 * Native FilePath is modelled as a string (`pathString`), but every path
 * operation is delegated to java.nio.file.Paths/Path — which is supported on
 * Scala Native (proven by FileOpsPlatform.scala and FileOpsIss977NativeSuite)
 * — mirroring the JVM reference impl
 * (ssg-commons/src/main/scalajvm/ssg/commons/io/FilePathPlatform.scala)
 * operation-for-operation. Delegation is per-operation and stateless: each call
 * does string -> Paths.get -> Path operation -> String, so the string-logic
 * class of bugs (ISS-980: absolute-path normalize dropping the root, toAbsolute
 * prepending "/", literal "." cwd, parent of "/a") cannot recur.
 */
package ssg
package commons
package io

import java.nio.file.Paths

/** Native implementation. The path is held as a string, but all operations are delegated to java.nio.file.Paths/Path to match the JVM implementation.
  */
final private[io] class NativeFilePath(val pathString: String) extends FilePath {

  /** The string routed through java.nio.file.Paths, matching the JVM impl's underlying Path (scalajvm/ssg/commons/io/FilePathPlatform.scala:55, and the FilePathPlatform.toNioPath fallback
    * `Paths.get(fp.pathString)`).
    */
  private def underlying: java.nio.file.Path = Paths.get(pathString)

  // Mirrors JvmFilePath.parent (scalajvm/ssg/commons/io/FilePathPlatform.scala:18-21).
  override def parent: Option[FilePath] = {
    val p = underlying.getParent
    // Paths.get is non-null and getParent yields null at the root; map null -> None as the JVM impl does.
    if (p eq null) None else Some(new NativeFilePath(p.toString))
  }

  // Mirrors JvmFilePath.resolve(String) (scalajvm/ssg/commons/io/FilePathPlatform.scala:23-24).
  override def resolve(other: String): FilePath =
    new NativeFilePath(underlying.resolve(other).toString)

  // Mirrors JvmFilePath.resolve(FilePath) (scalajvm/ssg/commons/io/FilePathPlatform.scala:26-29):
  // resolve against the other path's value (string-based here, so route through its pathString).
  override def resolve(other: FilePath): FilePath =
    new NativeFilePath(underlying.resolve(other.pathString).toString)

  // Mirrors JvmFilePath.fileName (scalajvm/ssg/commons/io/FilePathPlatform.scala:31-34):
  // getFileName is null for the root, mapped to "".
  override def fileName: String = {
    val fn = underlying.getFileName
    if (fn eq null) "" else fn.toString
  }

  // Mirrors JvmFilePath.isAbsolute (scalajvm/ssg/commons/io/FilePathPlatform.scala:36).
  override def isAbsolute: Boolean = underlying.isAbsolute

  // Mirrors JvmFilePath.toAbsolute (scalajvm/ssg/commons/io/FilePathPlatform.scala:38-39):
  // resolves relative paths against the process working directory.
  override def toAbsolute: FilePath =
    new NativeFilePath(underlying.toAbsolutePath.toString)

  // Mirrors JvmFilePath.normalize (scalajvm/ssg/commons/io/FilePathPlatform.scala:41-42):
  // Path.normalize preserves the root component, so absolute paths stay absolute.
  override def normalize: FilePath =
    new NativeFilePath(underlying.normalize().toString)

  override def hashCode(): Int = pathString.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case other: NativeFilePath => pathString == other.pathString
    case _ => false
  }
}

object FilePathPlatform {

  // Mirrors JvmFilePath.of (scalajvm/ssg/commons/io/FilePathPlatform.scala:54-55).
  // Round-trip through Paths.get so the stored string is normalised the same way the JVM Path renders it.
  def of(path: String): FilePath =
    new NativeFilePath(Paths.get(path).toString)

  // Mirrors JvmFilePath.cwd (scalajvm/ssg/commons/io/FilePathPlatform.scala:57-58).
  def cwd: FilePath =
    new NativeFilePath(Paths.get(".").toAbsolutePath.normalize().toString)
}
