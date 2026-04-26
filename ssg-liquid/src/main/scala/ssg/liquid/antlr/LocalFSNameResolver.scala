/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/antlr/LocalFSNameResolver.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.antlr → ssg.liquid.antlr
 *   Convention: Java class → Scala class
 *   Idiom: Returns ResolvedSource(content, sourceName) instead of ANTLR CharStream
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/antlr/LocalFSNameResolver.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package antlr

import ssg.commons.io.{ FileOps, FilePath }

import scala.util.boundary
import scala.util.boundary.break

/** Resolves template names to file contents from the local filesystem.
  *
  * JVM-only: uses ssg.commons.io for path resolution and file reading.
  *
  * If the name is an absolute path, it is used directly. Otherwise, the name is resolved relative to the configured root directory. If the name does not contain a dot (no extension), the default
  * extension `.liquid` is appended.
  */
final class LocalFSNameResolver(private val root: String) extends NameResolver {

  override def resolve(name: String): NameResolver.ResolvedSource =
    boundary {
      val directPath = FilePath.of(name)
      if (directPath.isAbsolute) {
        val absPath = directPath.toAbsolute
        val content = FileOps.readString(absPath)
        break(NameResolver.ResolvedSource(content, absPath.pathString))
      }

      val extension    = if (name.indexOf('.') > 0) "" else LocalFSNameResolver.DEFAULT_EXTENSION
      val resolvedName = name + extension
      val path         = FilePath.of(root).resolve(resolvedName).toAbsolute
      val content      = FileOps.readString(path)
      NameResolver.ResolvedSource(content, path.pathString)
    }
}

object LocalFSNameResolver {

  /** Default file extension appended to template names without an extension. */
  val DEFAULT_EXTENSION: String = ".liquid"
}
