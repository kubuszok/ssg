/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM: every regular expression flexmark compiles is java's own — this platform's engine accepts
 * flexmark's whole dialect. The generated code reaches this object through the port's call-site
 * substitutions (ssg-md/port/main.conf); Scala Native's RE2 engine refuses lookaround and answers at
 * the same name. */
package ssg
package md
package util
package sequence

import java.util.regex.Pattern

object Regexes {

  def compile(regex: String): Pattern = Pattern.compile(regex)

  def compile(regex: String, flags: Int): Pattern = Pattern.compile(regex, flags)
}
