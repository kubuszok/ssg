/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native: its regex engine is RE2, which has no lookaround and no backreferences, and a
 * pattern using one is refused when compiled. flexmark compiles its patterns in class initialisers,
 * so refusing by throwing would take the whole class with it; such a pattern is answered with null
 * and recorded here, and the callers that need it (the fence and list-marker parsers) ask their own
 * lookahead-free helpers instead. A use of a refused pattern fails at that use, by name below. */
package ssg
package md
package util
package sequence

import java.util.regex.Pattern

object Regexes {

  /** the patterns this platform could not compile, in the order they were asked for */
  private val refusedPatterns: java.util.ArrayList[String] = new java.util.ArrayList()

  def refused: List[String] = {
    val out = List.newBuilder[String]
    refusedPatterns.forEach(p => out += p)
    out.result()
  }

  def compile(regex: String): Pattern = compile(regex, 0)

  def compile(regex: String, flags: Int): Pattern =
    if (usesLookaroundOrBackreference(regex)) {
      refusedPatterns.add(regex)
      null.asInstanceOf[Pattern] // java interop boundary: the refusal token, see above
    } else {
      Pattern.compile(regex, flags)
    }

  /** `(?=`, `(?!`, `(?<=`, `(?<!` and `\1`…`\9`, outside `\Q…\E` and not themselves escaped */
  private def usesLookaroundOrBackreference(regex: String): Boolean = {
    var i      = 0
    var quoted = false
    var found  = false
    while (i < regex.length && !found) {
      val c = regex.charAt(i)
      if (quoted) {
        if (c == '\\' && i + 1 < regex.length && regex.charAt(i + 1) == 'E') {
          quoted = false
          i += 1
        }
      } else if (c == '\\' && i + 1 < regex.length) {
        val next = regex.charAt(i + 1)
        if (next == 'Q') quoted = true
        else if (next >= '1' && next <= '9') found = true
        i += 1
      } else if (c == '(' && regex.startsWith("(?", i)) {
        val rest = regex.substring(i + 2)
        if (rest.startsWith("=") || rest.startsWith("!") || rest.startsWith("<=") || rest.startsWith("<!")) found = true
      }
      i += 1
    }
    found
  }
}
