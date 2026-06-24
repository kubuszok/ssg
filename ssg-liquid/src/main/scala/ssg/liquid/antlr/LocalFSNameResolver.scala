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
 *   SSG addition — optional root-jail per ISS-1020/design §6; inert when no jail-root
 *     is set, preserving liqp behavior
 *   Package: ssg.liquid.antlr faithfully mirrors liqp's liqp.antlr package —
 *     see NameResolver.scala header for details.
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/antlr/LocalFSNameResolver.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package antlr

import ssg.commons.io.{ FileOps, FilePath }
import ssg.liquid.tags.IncludeRelative

import scala.util.boundary
import scala.util.boundary.break

/** Resolves template names to file contents from the local filesystem.
  *
  * Uses ssg.commons.io for path resolution and file reading: supported on JVM, Scala Native, and Scala.js (under Node).
  *
  * If the name is an absolute path, it is used directly. Otherwise, the name is resolved relative to the configured root directory. If the name does not contain a dot (no extension), the default
  * extension `.liquid` is appended.
  *
  * When a jail root is set, both the absolute and relative resolution branches verify that the resolved path stays under the jail root before reading the file. If the path escapes the jail, a
  * [[IncludeRelative.JailViolationException]] is thrown. When no jail root is set, behavior is unchanged (faithful to the liqp port for non-pipeline users).
  *
  * @param root
  *   the root directory for relative template name resolution
  * @param jailRoot
  *   optional jail root — when set, resolved paths must stay under this root (SSG addition, ISS-1020)
  */
final class LocalFSNameResolver(private val root: String, val jailRoot: Option[FilePath] = None) extends NameResolver {

  override def resolve(name: String): NameResolver.ResolvedSource =
    boundary {
      val directPath = FilePath.of(name)
      if (directPath.isAbsolute) {
        val absPath = directPath.toAbsolute.normalize
        // SSG addition (ISS-1020): jail check for absolute paths — when a jail root
        // is set, verify the absolute path stays under it BEFORE reading the file.
        jailRoot.foreach { jail =>
          val jailAbs = jail.toAbsolute.normalize
          if (!IncludeRelative.isUnderRoot(absPath, jailAbs)) {
            throw new IncludeRelative.JailViolationException(
              absPath,
              jailAbs,
              s"include path '${name}' resolves to '${absPath.pathString}' which is outside the jail root '${jailAbs.pathString}'"
            )
          }
        }
        val content = FileOps.readString(absPath)
        break(NameResolver.ResolvedSource(content, absPath.pathString))
      }

      val extension    = if (name.indexOf('.') > 0) "" else LocalFSNameResolver.DEFAULT_EXTENSION
      val resolvedName = name + extension
      val path         = FilePath.of(root).resolve(resolvedName).toAbsolute.normalize
      // SSG addition (ISS-1020): jail check for relative paths — when a jail root
      // is set, verify the resolved path stays under it BEFORE reading the file.
      jailRoot.foreach { jail =>
        val jailAbs = jail.toAbsolute.normalize
        if (!IncludeRelative.isUnderRoot(path, jailAbs)) {
          throw new IncludeRelative.JailViolationException(
            path,
            jailAbs,
            s"include path '${name}' resolves to '${path.pathString}' which is outside the jail root '${jailAbs.pathString}'"
          )
        }
      }
      val content = FileOps.readString(path)
      NameResolver.ResolvedSource(content, path.pathString)
    }
}

object LocalFSNameResolver {

  /** Default file extension appended to template names without an extension. */
  val DEFAULT_EXTENSION: String = ".liquid"
}
