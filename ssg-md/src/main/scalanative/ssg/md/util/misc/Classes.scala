/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native: `Class.getPackage` (and `java.lang.Package`) and `Class.getCanonicalName` do not exist
 * here, so both are read off the binary name `Class.getName` does give. `Class.getResourceAsStream`
 * exists and reads the resources the build embeds, so it stays java's own call.
 *
 * The canonical name is an approximation and says so: without reflection a `$` that separates a
 * nested class cannot be told from one that is part of a name. flexmark only prints it in a message. */
package ssg
package md
package util
package misc

import java.io.InputStream

object Classes {

  /** Stands where `java.lang.Package` stood: the generated code only ever asks it for its name. */
  final class PackageOf(name: String) {
    def getName(): String = name
  }

  /** The binary name up to its last dot; the unnamed package is the empty string, as in java. */
  def packageOf(cls: Class[?]): PackageOf = {
    val name    = cls.getName
    val lastDot = name.lastIndexOf('.')
    new PackageOf(if (lastDot < 0) "" else name.substring(0, lastDot))
  }

  /** Nested separators become dots; an anonymous or local class has no canonical name, which java answers with null. */
  def canonicalName(cls: Class[?]): String = {
    val name       = cls.getName
    val lastDollar = name.lastIndexOf('$')
    val tail       = if (lastDollar < 0) "" else name.substring(lastDollar + 1)
    val numbered   = tail.nonEmpty && tail.forall(Character.isDigit)
    if (numbered) {
      null.asInstanceOf[String] // java interop boundary: `Class.getCanonicalName` answers null for anonymous and local classes
    } else {
      val out = new java.lang.StringBuilder(name.length)
      var i   = 0
      while (i < name.length) {
        val c = name.charAt(i)
        out.append(if (c == '$' && i < name.length - 1) '.' else c)
        i += 1
      }
      out.toString
    }
  }

  def resourceAsStream(cls: Class[?], name: String): InputStream = cls.getResourceAsStream(name)
}
