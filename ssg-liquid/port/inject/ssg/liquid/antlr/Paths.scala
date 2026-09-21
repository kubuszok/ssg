/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: the JDK factory `java.nio.file.Paths`, which liqp names in
 *   `liqp/TemplateParser.java` and `liqp/antlr/NameResolver.java`.
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: injected stand-in for a JDK class Scala.js does not have. This port's path is
 *     ssg.commons.io.FilePath, and this is the factory java's calls are re-pointed at.
 *   Convention: java's own name and arity, so `Paths.get(".")` ports as written.
 */
package ssg
package liquid
package antlr

import ssg.commons.io.FilePath

/** Builds a [[ssg.commons.io.FilePath]] the way `java.nio.file.Paths.get` builds a `Path`. */
object Paths {

  def get(first: String, more: String*): FilePath =
    more.foldLeft(FilePath.of(first))((path, segment) => path.resolve(segment))
}
