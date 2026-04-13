/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * JVM implementation of FilePath using java.nio.file.Path.
 */
package ssg
package commons
package io

import java.nio.file.{ Path, Paths }

/** JVM implementation wrapping java.nio.file.Path. */
final private[io] class JvmFilePath(val underlying: Path) extends FilePath {

  override def pathString: String = underlying.toString

  override def parent: Option[FilePath] = {
    val p = underlying.getParent
    if (p == null) None else Some(new JvmFilePath(p))
  }

  override def resolve(other: String): FilePath =
    new JvmFilePath(underlying.resolve(other))

  override def resolve(other: FilePath): FilePath = other match {
    case jfp: JvmFilePath => new JvmFilePath(underlying.resolve(jfp.underlying))
    case _ => new JvmFilePath(underlying.resolve(other.pathString))
  }

  override def fileName: String = {
    val fn = underlying.getFileName
    if (fn == null) "" else fn.toString
  }

  override def isAbsolute: Boolean = underlying.isAbsolute

  override def toAbsolute: FilePath =
    new JvmFilePath(underlying.toAbsolutePath)

  override def normalize: FilePath =
    new JvmFilePath(underlying.normalize())

  override def hashCode(): Int = underlying.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case other: JvmFilePath => underlying.equals(other.underlying)
    case _ => false
  }
}

object FilePathPlatform {

  def of(path: String): FilePath =
    new JvmFilePath(Paths.get(path))

  def cwd: FilePath =
    new JvmFilePath(Paths.get(".").toAbsolutePath.normalize())

  /** Unwraps the underlying java.nio.file.Path (JVM-only). */
  def toNioPath(fp: FilePath): Path = fp match {
    case jfp: JvmFilePath => jfp.underlying
    case _ => Paths.get(fp.pathString)
  }

  /** Wraps a java.nio.file.Path (JVM-only). */
  def fromNioPath(path: Path): FilePath =
    new JvmFilePath(path)
}
