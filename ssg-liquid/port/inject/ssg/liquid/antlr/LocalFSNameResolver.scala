/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: src/main/java/liqp/antlr/LocalFSNameResolver.java
 * Original license: MIT, Copyright (c) 2012 Bart Kiers
 * Adapted from the hand-written Scala port in ssg (Apache-2.0, SSG contributors)
 *
 * Migration notes:
 *   Origin: injected in place of the generated class, because a recorded ssg decision adds a
 *     constructor parameter and a check inside its one method.
 *   ISS-1020: an optional root jail. liqp honours an absolute include name and resolves `../`
 *     outside the root unchecked; ssg, when a jail root is given, refuses a resolved path
 *     outside it BEFORE reading the file. Inert without one, which is liqp's behaviour.
 *   Paths are ssg.commons.io's, as everywhere in this port: there is no java.nio.file on
 *     Scala.js.
 */
package ssg
package liquid
package antlr

import ssg.commons.io.FilePath

/** Resolves template names to files under a root directory.
  *
  * An absolute name is used directly. Otherwise the name is resolved under `root`, with `.liquid` appended when it has no extension.
  *
  * @param jailRoot
  *   when set, every resolved path must stay under this root (ISS-1020)
  */
class LocalFSNameResolver(val root: String, val jailRoot: Option[FilePath] = None) extends NameResolver {

  override def resolve(name: String): CharStreamWithLocation = {
    val directPath = FilePath.of(name)
    if (directPath.isAbsolute) {
      val absPath = directPath.toAbsolute.normalize
      IncludeJail.check(jailRoot, absPath, s"include path '$name'", "jail root")
      new CharStreamWithLocation(absPath)
    } else {
      val extension = if (name.indexOf('.') > 0) "" else LocalFSNameResolver.DEFAULT_EXTENSION
      val path      = FilePath.of(root).resolve(name + extension).toAbsolute.normalize
      IncludeJail.check(jailRoot, path, s"include path '$name'", "jail root")
      new CharStreamWithLocation(path)
    }
  }
}

object LocalFSNameResolver {

  /** Default file extension appended to template names without an extension. */
  var DEFAULT_EXTENSION: String = ".liquid"
}
