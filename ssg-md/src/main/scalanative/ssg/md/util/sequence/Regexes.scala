/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native: its regex engine is RE2, which has no lookaround and no backreferences, and a
 * pattern using one is refused when compiled. Two answers, in this order:
 *
 *   1. flexmark's one lookahead with a regular shape — a link destination that may hold spaces
 *      takes a space only when no quote follows it, `(?:X| (?![\"']))*` — is restated without it:
 *      a run of spaces may be followed by any alternative but a quote, `(?:X| +X')*(?: +)?`, with
 *      `X'` the alternatives whose negated class now excludes the quotes. That is java's language
 *      exactly, except at a loop that ends the pattern, where java stops before a space that a quote
 *      follows and the restatement has taken it: such a pattern is remembered and `Matches` gives
 *      that one space back.
 *   2. any other lookaround or a backreference is refused: flexmark compiles its patterns in class
 *      initialisers, so refusing by throwing would take the whole class with it; the pattern is
 *      answered with null and recorded here, and a use of it fails at that use, by name below. The
 *      parsers that meet one (fences, list markers, one ATX heading form) ask their own helper.
 *
 * Java's `\p{IsAlphabetic}` RE2 does not know; every category is spelled as its ranges. */
package ssg
package md
package util
package sequence

import java.util.regex.Pattern

object Regexes {

  /** the patterns this platform could not compile, in the order they were asked for */
  private val refusedPatterns: java.util.ArrayList[String] = new java.util.ArrayList()

  /** the patterns whose loop ends the pattern and took a trailing space java would not have */
  private val giveBack: java.util.IdentityHashMap[Pattern, java.lang.Boolean] = new java.util.IdentityHashMap()

  def refused: List[String] = {
    val out = List.newBuilder[String]
    refusedPatterns.forEach(p => out += p)
    out.result()
  }

  def givesBackSpaceBeforeQuote(p: Pattern): Boolean = giveBack.containsKey(p)

  def compile(regex: String): Pattern = compile(regex, 0)

  def compile(regex: String, flags: Int): Pattern = {
    var rewritten = regex
    var trailing  = false
    while (rewritten.contains(SpaceNotBeforeQuote)) {
      val (next, atEnd) = withoutSpaceLookahead(rewritten)
      rewritten = next
      trailing = trailing || atEnd
    }
    if (usesLookaroundOrBackreference(rewritten)) {
      refusedPatterns.add(regex)
      null.asInstanceOf[Pattern] // java interop boundary: the refusal token, see above
    } else {
      val p = Pattern.compile(RegexPortability.expandUnicodeCategories(rewritten, RegexPortability.Re2Escapes), flags)
      if (trailing) giveBack.put(p, java.lang.Boolean.TRUE)
      p
    }
  }

  private val SpaceNotBeforeQuote = " (?![\"'])"

  /** the first occurrence of the space lookahead, rewritten at the loop that holds it; whether that loop ends the pattern */
  private def withoutSpaceLookahead(regex: String): (String, Boolean) = {
    val at = regex.indexOf(SpaceNotBeforeQuote)
    // the groups open at `at`, outermost first
    val open = List.newBuilder[Int]
    var stack: List[Int] = Nil
    var i      = 0
    var quoted = false
    var depth  = 0
    while (i < at) {
      val c = regex.charAt(i)
      if (quoted) {
        if (c == '\\' && regex.startsWith("\\E", i)) { quoted = false; i += 1 }
      } else if (c == '\\') {
        if (i + 1 < regex.length && regex.charAt(i + 1) == 'Q') quoted = true
        i += 1
      } else if (c == '[') depth += 1
      else if (c == ']' && depth > 0) depth -= 1
      else if (depth == 0 && c == '(') stack = i :: stack
      else if (depth == 0 && c == ')') stack = stack.tail
      i += 1
    }
    stack.reverse.foreach(open += _)
    val groups = open.result().reverse // innermost first
    // the innermost group followed by a quantifier is the loop
    val loopStart = groups.find { s =>
      val close = RegexPortability.closingParen(regex, s)
      close + 1 < regex.length && (regex.charAt(close + 1) == '*' || regex.charAt(close + 1) == '+')
    }.getOrElse(throw new java.util.regex.PatternSyntaxException("the space lookahead is not inside a loop", regex, at))
    val loopClose   = RegexPortability.closingParen(regex, loopStart)
    val quantifier  = regex.charAt(loopClose + 1)
    val capturing   = !regex.startsWith("(?", loopStart)
    val bodyStart   = if (capturing) loopStart + 1 else loopStart + 3
    val body        = regex.substring(bodyStart, loopClose)
    // the loop's alternatives, with a `(?:…)` alternative holding the lookahead flattened into them
    val alternatives = RegexPortability.splitAlternatives(body).flatMap { alt =>
      if (!alt.contains(SpaceNotBeforeQuote)) List(alt)
      else if (alt == SpaceNotBeforeQuote) Nil
      else if (alt.startsWith("(?:") && RegexPortability.closingParen(alt, 0) == alt.length - 1)
        RegexPortability.splitAlternatives(alt.substring(3, alt.length - 1)).filterNot(_ == SpaceNotBeforeQuote)
      else List(alt) // holds a loop of its own (a parenthesised destination), rewritten on a later pass
    }
    val afterSpaces = alternatives.map { alt =>
      if (alt.startsWith("[^")) {
        val end = RegexPortability.classEnd(alt, 0)
        alt.substring(0, end) + "\"'" + alt.substring(end)
      } else alt
    }
    val loop =
      (if (capturing) "(" else "(?:") + alternatives.mkString("|") + "| +(?:" + afterSpaces.mkString("|") + "))" + quantifier + "(?: +)?"
    val rest = regex.substring(loopClose + 2)
    (regex.substring(0, loopStart) + loop + rest, rest.forall(_ == ')'))
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
