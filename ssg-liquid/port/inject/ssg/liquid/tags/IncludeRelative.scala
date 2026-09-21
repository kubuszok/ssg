/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: src/main/java/liqp/tags/IncludeRelative.java
 * Original license: MIT, Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Adapted from the hand-written Scala port in ssg (Apache-2.0, SSG contributors)
 *
 * Migration notes:
 *   Origin: injected in place of the generated class, because two recorded ssg decisions sit
 *     inside its one method.
 *   ISS-1214 (with ISS-1020): an optional root jail. liqp reads whatever path the include
 *     resolves to; ssg refuses a path outside the jail root BEFORE reading it. Inert while no
 *     jail root is set (`Template.withJailRoot`).
 *   ISS-1259: liqp resolves include_relative from the filesystem ONLY
 *     (IncludeRelative.java:40-48); ssg falls back to the parser's NameResolver when the
 *     source-relative file does not exist, so include_relative — nested too — works where
 *     includes are held in memory (Scala.js, Scala Native, tests). The filesystem path is
 *     liqp's whenever the file exists.
 */
package ssg
package liquid
package tags

import ssg.commons.io.{ FileOps, FilePath }
import ssg.liquid.antlr.CharStreamWithLocation

/** Jekyll-style include_relative tag: resolves relative to the current file's folder (the context's root folder). */
class IncludeRelative extends Include("include_relative") {

  override protected[tags] def detectSource(context: TemplateContext, includeResource: String): CharStreamWithLocation = {
    var rootPath: FilePath = context.getRootFolder()
    if (rootPath == null) {
      rootPath = FilePath.cwd.toAbsolute
    }
    val includePathAbs = rootPath.resolve(includeResource).toAbsolute.normalize

    // ISS-1214: the jail check comes BEFORE any read
    IncludeJail.check(IncludeJail.of(context), includePathAbs, s"include_relative path '$includeResource'", "source root")

    if (FileOps.isSupported && FileOps.exists(includePathAbs)) {
      new CharStreamWithLocation(includePathAbs)
    } else {
      // ISS-1259: no such file — resolve the name the way the base `include` tag does
      context.getParser().nameResolver.resolve(includeResource)
    }
  }
}

/** The jail's names as the hand port spelled them; the jail itself is [[IncludeJail]]. */
object IncludeRelative {

  type JailViolationException = IncludeJail.JailViolationException

  private[liquid] def isUnderRoot(resolvedAbs: FilePath, rootAbs: FilePath): Boolean =
    IncludeJail.isUnderRoot(resolvedAbs, rootAbs)
}
