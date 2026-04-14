/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native implementation of FilePath using string-based paths.
 */
package ssg
package commons
package io

/** Native implementation using string-based paths. */
final private[io] class NativeFilePath(val pathString: String) extends FilePath {

  override def parent: Option[FilePath] = {
    val normalized = pathString.replace('\\', '/')
    val lastSep    = normalized.lastIndexOf('/')
    if (lastSep <= 0) None
    else Some(new NativeFilePath(pathString.substring(0, lastSep)))
  }

  override def resolve(other: String): FilePath =
    if (other.isEmpty) this
    else if (other.startsWith("/")) {
      // Absolute path
      new NativeFilePath(other)
    } else {
      val sep = if (pathString.endsWith("/")) "" else "/"
      new NativeFilePath(pathString + sep + other)
    }

  override def resolve(other: FilePath): FilePath =
    resolve(other.pathString)

  override def fileName: String = {
    val lastSep = pathString.lastIndexOf('/')
    if (lastSep < 0) pathString
    else pathString.substring(lastSep + 1)
  }

  override def isAbsolute: Boolean =
    pathString.startsWith("/")

  override def toAbsolute: FilePath =
    if (isAbsolute) this else new NativeFilePath("/" + pathString)

  override def normalize: FilePath = {
    // Simple normalization: remove . and collapse ..
    val parts  = pathString.split("/").toList
    val result = parts
      .foldLeft(List.empty[String]) { (acc, part) =>
        part match {
          case "" | "." => acc
          case ".."     => if (acc.nonEmpty && acc.head != "..") acc.tail else ".." :: acc
          case p        => p :: acc
        }
      }
      .reverse
    new NativeFilePath(result.mkString("/"))
  }

  override def hashCode(): Int = pathString.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case other: NativeFilePath => pathString == other.pathString
    case _ => false
  }
}

object FilePathPlatform {

  def of(path: String): FilePath =
    new NativeFilePath(path)

  def cwd: FilePath =
    new NativeFilePath(".")
}
