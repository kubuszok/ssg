/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM: what flexmark asks of `java.lang.Class` beyond its name — the package, the canonical name and a
 * classpath resource — answered by java's own calls. The generated code reaches this object through
 * the port's call-site substitutions (ssg-md/port/main.conf); Scala.js and Scala Native lack some of
 * these members and ship their own answer at the same name. */
package ssg
package md
package util
package misc

import java.io.InputStream

object Classes {

  /** Stands where `java.lang.Package` stood: the generated code only ever asks it for its name. */
  final class PackageOf(pkg: java.lang.Package) {
    def getName(): String = pkg.getName()
  }

  def packageOf(cls: Class[?]): PackageOf = new PackageOf(cls.getPackage())

  def canonicalName(cls: Class[?]): String = cls.getCanonicalName()

  def resourceAsStream(cls: Class[?], name: String): InputStream = cls.getResourceAsStream(name)
}
