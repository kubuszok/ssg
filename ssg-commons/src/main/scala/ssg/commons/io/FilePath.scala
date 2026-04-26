/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Cross-platform file path abstraction.
 *
 * On JVM: wraps java.nio.file.Path with full file system access
 * On JS/Native: string-based paths with limited/no file system access
 *
 * Covenant: full-port
 * Covenant-verified: 2026-04-26
 */
package ssg
package commons
package io

/** Cross-platform file path representation.
  *
  * Use `FilePath.of(string)` to create instances. Platform-specific implementations provide the actual behavior.
  */
trait FilePath {

  /** Returns the string representation of this path. */
  def pathString: String

  /** Returns the parent directory, or None if this is a root path. */
  def parent: Option[FilePath]

  /** Resolves a child path against this path. */
  def resolve(other: String): FilePath

  /** Resolves a child path against this path. */
  def resolve(other: FilePath): FilePath

  /** Returns the file name (last component of the path). */
  def fileName: String

  /** Returns true if this path is absolute. */
  def isAbsolute: Boolean

  /** Converts to an absolute path. */
  def toAbsolute: FilePath

  /** Normalizes the path (removes . and .. where possible). */
  def normalize: FilePath

  override def toString: String = pathString
}

object FilePath {

  /** Creates a FilePath from a string. Platform-specific. */
  def of(path: String): FilePath = FilePathPlatform.of(path)

  /** Returns the current working directory. Platform-specific. */
  def cwd: FilePath = FilePathPlatform.cwd
}
