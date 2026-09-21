/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * SSG addition, not in liqp: the optional root jail for `include` (ISS-1020) and
 * `include_relative` (ISS-1214). Inert while no jail root is set, which keeps liqp's behaviour
 * for everyone who never asks for one.
 */
package ssg
package liquid

import ssg.commons.io.FilePath

/** The root jail: a resolved include path must stay under the jail root, checked BEFORE the file is read. */
object IncludeJail {

  /** The path separator used in `pathString` on all platforms (JVM/JS/Native). */
  private val Separator: String = "/"

  /** The context registry the jail root travels in, from the template being rendered down to every nested include. */
  val REGISTRY_JAIL_ROOT: String = "registry_jail_root"

  /** Whether a resolved absolute path stays under the given root.
    *
    * Same separator-boundary predicate as `ssg.site.RootJail.isUnderRoot`: the resolved path must either equal the root or start with root + "/". This prevents sibling-prefix false positives where
    * e.g. `/tmp/srcfoo` would match root `/tmp/src`.
    */
  private[liquid] def isUnderRoot(resolvedAbs: FilePath, rootAbs: FilePath): Boolean = {
    val resolvedStr = resolvedAbs.pathString
    val rootStr     = rootAbs.pathString
    resolvedStr == rootStr || resolvedStr.startsWith(rootStr + Separator)
  }

  /** Thrown when an include path escapes the jail root. Caught by the site pipeline and converted to a `BuildDiagnostic(stage = Liquid, severity = Error)`.
    *
    * @param resolvedPath
    *   the resolved absolute include path that escaped
    * @param jailRoot
    *   the jail root it escaped from
    */
  final class JailViolationException(
    val resolvedPath: FilePath,
    val jailRoot:     FilePath,
    message:          String
  ) extends RuntimeException(message)

  /** Refuses `resolved` when a jail root is set and the path is not under it. `what` names the tag's argument for the message. */
  def check(jailRoot: Option[FilePath], resolved: FilePath, what: String, rootName: String): Unit =
    jailRoot.foreach { jail =>
      val jailAbs     = jail.toAbsolute.normalize
      val resolvedAbs = resolved.toAbsolute.normalize
      if (!isUnderRoot(resolvedAbs, jailAbs)) {
        throw new JailViolationException(
          resolvedAbs,
          jailAbs,
          s"$what resolves to '${resolvedAbs.pathString}' which is outside the $rootName '${jailAbs.pathString}'"
        )
      }
    }

  /** Puts a template's jail root where every include rendered under `context` finds it; the outermost template's root wins. */
  def seed(context: TemplateContext, jailRoot: Option[FilePath]): Unit =
    jailRoot.foreach { root =>
      val registry: scala.collection.mutable.Map[String, Object] = context.getRegistry(REGISTRY_JAIL_ROOT)
      if (!registry.contains(REGISTRY_JAIL_ROOT)) {
        registry.put(REGISTRY_JAIL_ROOT, root)
      }
    }

  /** The jail root set for this render, if any. */
  def of(context: TemplateContext): Option[FilePath] = {
    val registry: scala.collection.mutable.Map[String, Object] = context.getRegistry(REGISTRY_JAIL_ROOT)
    registry.get(REGISTRY_JAIL_ROOT).map(_.asInstanceOf[FilePath])
  }
}
