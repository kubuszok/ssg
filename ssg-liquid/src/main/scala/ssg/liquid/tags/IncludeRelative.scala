/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/tags/IncludeRelative.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.tags → ssg.liquid.tags
 *   Convention: Resolves includes relative to the current file's root folder
 *   Idiom: Overrides detectSource to resolve via context.getRootFolder()
 *   Audited: 2026-04-10 — ISS-102 fixed: uses getRootFolder() for path resolution
 *   SSG addition — optional root-jail for include_relative per ISS-1214/ISS-1020/design §6;
 *     inert when no jail-root is set, preserving liqp behavior
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/tags/IncludeRelative.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package tags

import ssg.commons.io.{ FileOps, FilePath }
import ssg.liquid.antlr.NameResolver

/** Jekyll-style include_relative tag.
  *
  * Resolves templates relative to the current file location (from context root folder), unlike the standard include tag which uses the configured NameResolver.
  */
class IncludeRelative extends Include("include_relative") {

  /** Resolves the include source relative to the current file's root folder.
    *
    * Uses `context.getRootFolder()` to determine the base path. Falls back to the current working directory if the root folder is not set.
    *
    * When a jail root is set on the context (via `Template.withJailRoot`), verifies that the resolved include path stays under the jail root before reading the file. If the path escapes the jail, a
    * [[IncludeRelative.JailViolationException]] is thrown. When no jail root is set, behavior is unchanged (faithful to the liqp port for non-pipeline users).
    *
    * Requires file system access via FileOps: supported on JVM, Scala Native, and Scala.js (under Node).
    */
  override protected def detectSource(context: TemplateContext, includeResource: String): NameResolver.ResolvedSource = {
    var rootPath = context.getRootFolder
    if (rootPath == null) {
      rootPath = FilePath.cwd.toAbsolute
    }
    val includePath    = rootPath.resolve(includeResource)
    val includePathAbs = includePath.toAbsolute.normalize

    // SSG addition (ISS-1214): jail check — when a jail root is set, verify the
    // resolved path stays under it BEFORE reading the file. Uses the same
    // separator-boundary predicate semantics as ssg.site.RootJail.isUnderRoot
    // (equal-or-startsWith-root+separator) to prevent sibling-prefix false negatives.
    context.getJailRoot.foreach { jailRoot =>
      val jailRootAbs = jailRoot.toAbsolute.normalize
      if (!IncludeRelative.isUnderRoot(includePathAbs, jailRootAbs)) {
        throw new IncludeRelative.JailViolationException(
          includePathAbs,
          jailRootAbs,
          s"include_relative path '${includeResource}' resolves to '${includePathAbs.pathString}' which is outside the source root '${jailRootAbs.pathString}'"
        )
      }
    }

    val content = FileOps.readString(includePathAbs)
    NameResolver.ResolvedSource(content, includePathAbs.pathString)
  }
}

object IncludeRelative {

  /** The path separator used in `pathString` on all platforms (JVM/JS/Native). */
  private val Separator: String = "/"

  /** Checks whether a resolved absolute path stays under the given root.
    *
    * Same separator-boundary predicate as `ssg.site.RootJail.isUnderRoot`: the resolved path must either equal the root or start with root + "/". This prevents sibling-prefix false negatives where
    * e.g. `/src` would match root `/sr`.
    */
  private[liquid] def isUnderRoot(resolvedAbs: FilePath, rootAbs: FilePath): Boolean = {
    val resolvedStr = resolvedAbs.pathString
    val rootStr     = rootAbs.pathString
    resolvedStr == rootStr || resolvedStr.startsWith(rootStr + Separator)
  }

  /** Thrown when an include_relative path escapes the jail root.
    *
    * SSG addition (ISS-1214): not in original liqp. Caught by the site pipeline and converted to a `BuildDiagnostic(stage = Liquid, severity = Error)`.
    *
    * @param resolvedPath
    *   the resolved absolute include path that escaped
    * @param jailRoot
    *   the jail root it escaped from
    * @param message
    *   a human-readable description
    */
  final class JailViolationException(
    val resolvedPath: FilePath,
    val jailRoot:     FilePath,
    message:          String
  ) extends RuntimeException(message)
}
