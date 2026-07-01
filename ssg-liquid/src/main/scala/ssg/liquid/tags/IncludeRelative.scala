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
 *   DELIBERATE SSG DIVERGENCE (ISS-1259) — NameResolver fallback: liqp resolves
 *     include_relative from the filesystem ONLY (IncludeRelative.java:40-48). SSG
 *     targets JVM/JS/Native, where JS and Native have no on-disk source tree and
 *     includes are provided via the in-memory NameResolver. When the source-relative
 *     path has no real file, detectSource falls back to context.parser.nameResolver
 *     (as the base `include` tag does), so include_relative — including nested — works
 *     cross-platform. The liqp-faithful filesystem path is unchanged when the file exists.
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
    * DELIBERATE SSG DIVERGENCE from liqp (ISS-1259, user-approved): liqp resolves `include_relative` from the filesystem only (IncludeRelative.java:40-48). When the computed source-relative path has
    * no real file (e.g. on Scala.js / Scala Native, or any host where the include is only available in-memory), this falls back to `context.parser.nameResolver` — resolving the include name exactly
    * as the base `include` tag does — so `include_relative` (including nested) works cross-platform. The liqp-faithful filesystem path is unchanged whenever the file exists on disk.
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

    // liqp resolves include_relative from the filesystem ONLY
    // (IncludeRelative.java:40-48 → `new CharStreamWithLocation(includePath)`).
    // SSG keeps that filesystem path faithful for existing users: when the
    // source-relative file IS present on disk, we read it exactly as liqp does.
    if (FileOps.exists(includePathAbs)) {
      val content = FileOps.readString(includePathAbs)
      NameResolver.ResolvedSource(content, includePathAbs.pathString)
    } else {
      // DELIBERATE SSG DIVERGENCE (ISS-1259 — user decision): liqp is
      // filesystem-only, but SSG targets JVM, Scala.js and Scala Native, where
      // the in-memory NameResolver — not a real filesystem — is how includes are
      // provided (JS/Native have no on-disk source tree). When the computed
      // source-relative path does not resolve to a real file, fall back to the
      // context's NameResolver, resolving the include name the same way the base
      // `include` tag does (Include.detectSource → nameResolver.resolve). The
      // SAME context (and therefore the same resolver + root) propagates through
      // nested include_relative calls (Include.render re-renders against `context`),
      // so nested source-relative includes resolve in-memory too. This changes
      // nothing for users whose include_relative files exist on disk.
      context.parser.nameResolver.resolve(includeResource)
    }
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
