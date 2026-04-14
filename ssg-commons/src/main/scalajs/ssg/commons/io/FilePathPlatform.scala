/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scala.js implementation of FilePath using string-based paths.
 * File I/O is not available on JS.
 */
package ssg
package commons
package io

/** JS implementation using string-based paths. */
final private[io] class JsFilePath(val pathString: String) extends FilePath {

  override def parent: Option[FilePath] = {
    val normalized = pathString.replace('\\', '/')
    val lastSep    = normalized.lastIndexOf('/')
    if (lastSep <= 0) None
    else Some(new JsFilePath(pathString.substring(0, lastSep)))
  }

  override def resolve(other: String): FilePath =
    if (other.isEmpty) this
    else if (other.startsWith("/") || (other.length > 1 && other.charAt(1) == ':')) {
      // Absolute path
      new JsFilePath(other)
    } else {
      val sep = if (pathString.endsWith("/") || pathString.endsWith("\\")) "" else "/"
      new JsFilePath(pathString + sep + other)
    }

  override def resolve(other: FilePath): FilePath =
    resolve(other.pathString)

  override def fileName: String = {
    val normalized = pathString.replace('\\', '/')
    val lastSep    = normalized.lastIndexOf('/')
    if (lastSep < 0) pathString
    else pathString.substring(lastSep + 1)
  }

  override def isAbsolute: Boolean =
    pathString.startsWith("/") || (pathString.length > 1 && pathString.charAt(1) == ':')

  override def toAbsolute: FilePath =
    if (isAbsolute) this else new JsFilePath("/" + pathString)

  override def normalize: FilePath = {
    // Simple normalization: remove . and collapse ..
    val parts  = pathString.replace('\\', '/').split("/").toList
    val result = parts
      .foldLeft(List.empty[String]) { (acc, part) =>
        part match {
          case "" | "." => acc
          case ".."     => if (acc.nonEmpty && acc.head != "..") acc.tail else ".." :: acc
          case p        => p :: acc
        }
      }
      .reverse
    new JsFilePath(result.mkString("/"))
  }

  override def hashCode(): Int = pathString.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case other: JsFilePath => pathString == other.pathString
    case _ => false
  }
}

object FilePathPlatform {

  def of(path: String): FilePath =
    new JsFilePath(path)

  def cwd: FilePath =
    new JsFilePath(".")
}
