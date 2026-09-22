/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Two java regex constructs restated in the dialect every platform's engine accepts, for a row whose
 * engine lacks them (Scala.js below ECMAScript 2018): a Unicode category `\p{Xx}` becomes the
 * explicit character ranges of that category, and a case-insensitive group `(?i:…)` becomes the same
 * group with every ASCII letter folded to both cases, which is what java's flag does without
 * UNICODE_CASE. Both rewrites are exact for the characters they cover; a construct outside them is
 * left as written, so the platform's own refusal stays loud. */
package ssg
package md
package util
package sequence

object RegexPortability {

  /** Unicode general categories as BMP ranges, read off JDK 25's `Character.getType` — the categories flexmark names. A supplementary code point is never a single `char`, which is all flexmark asks
    * of these.
    */
  private val categories: Map[String, String] = Map(
    "Pc" -> "\\u005F\\u203F-\\u2040\\u2054\\uFE33-\\uFE34\\uFE4D-\\uFE4F\\uFF3F",
    "Pd" -> "\\u002D\\u058A\\u05BE\\u1400\\u1806\\u2010-\\u2015\\u2E17\\u2E1A\\u2E3A-\\u2E3B\\u2E40\\u2E5D\\u301C\\u3030\\u30A0\\uFE31-\\uFE32\\uFE58\\uFE63\\uFF0D",
    "Pe" -> ("\\u0029\\u005D\\u007D\\u0F3B\\u0F3D\\u169C\\u2046\\u207E\\u208E\\u2309\\u230B\\u232A\\u2769\\u276B\\u276D\\u276F\\u2771\\u2773\\u2775\\u27C6" +
      "\\u27E7\\u27E9\\u27EB\\u27ED\\u27EF\\u2984\\u2986\\u2988\\u298A\\u298C\\u298E\\u2990\\u2992\\u2994\\u2996\\u2998\\u29D9\\u29DB\\u29FD\\u2E23\\u2E25" +
      "\\u2E27\\u2E29\\u2E56\\u2E58\\u2E5A\\u2E5C\\u3009\\u300B\\u300D\\u300F\\u3011\\u3015\\u3017\\u3019\\u301B\\u301E-\\u301F\\uFD3E\\uFE18\\uFE36\\uFE38" +
      "\\uFE3A\\uFE3C\\uFE3E\\uFE40\\uFE42\\uFE44\\uFE48\\uFE5A\\uFE5C\\uFE5E\\uFF09\\uFF3D\\uFF5D\\uFF60\\uFF63"),
    "Pf" -> "\\u00BB\\u2019\\u201D\\u203A\\u2E03\\u2E05\\u2E0A\\u2E0D\\u2E1D\\u2E21",
    "Pi" -> "\\u00AB\\u2018\\u201B-\\u201C\\u201F\\u2039\\u2E02\\u2E04\\u2E09\\u2E0C\\u2E1C\\u2E20",
    "Po" -> ("\\u0021-\\u0023\\u0025-\\u0027\\u002A\\u002C\\u002E-\\u002F\\u003A-\\u003B\\u003F-\\u0040\\u005C\\u00A1\\u00A7\\u00B6-\\u00B7\\u00BF\\u037E\\u0387" +
      "\\u055A-\\u055F\\u0589\\u05C0\\u05C3\\u05C6\\u05F3-\\u05F4\\u0609-\\u060A\\u060C-\\u060D\\u061B\\u061D-\\u061F\\u066A-\\u066D\\u06D4\\u0700-\\u070D" +
      "\\u07F7-\\u07F9\\u0830-\\u083E\\u085E\\u0964-\\u0965\\u0970\\u09FD\\u0A76\\u0AF0\\u0C77\\u0C84\\u0DF4\\u0E4F\\u0E5A-\\u0E5B\\u0F04-\\u0F12\\u0F14" +
      "\\u0F85\\u0FD0-\\u0FD4\\u0FD9-\\u0FDA\\u104A-\\u104F\\u10FB\\u1360-\\u1368\\u166E\\u16EB-\\u16ED\\u1735-\\u1736\\u17D4-\\u17D6\\u17D8-\\u17DA" +
      "\\u1800-\\u1805\\u1807-\\u180A\\u1944-\\u1945\\u1A1E-\\u1A1F\\u1AA0-\\u1AA6\\u1AA8-\\u1AAD\\u1B4E-\\u1B4F\\u1B5A-\\u1B60\\u1B7D-\\u1B7F\\u1BFC-\\u1BFF" +
      "\\u1C3B-\\u1C3F\\u1C7E-\\u1C7F\\u1CC0-\\u1CC7\\u1CD3\\u2016-\\u2017\\u2020-\\u2027\\u2030-\\u2038\\u203B-\\u203E\\u2041-\\u2043\\u2047-\\u2051\\u2053" +
      "\\u2055-\\u205E\\u2CF9-\\u2CFC\\u2CFE-\\u2CFF\\u2D70\\u2E00-\\u2E01\\u2E06-\\u2E08\\u2E0B\\u2E0E-\\u2E16\\u2E18-\\u2E19\\u2E1B\\u2E1E-\\u2E1F\\u2E2A-\\u2E2E" +
      "\\u2E30-\\u2E39\\u2E3C-\\u2E3F\\u2E41\\u2E43-\\u2E4F\\u2E52-\\u2E54\\u3001-\\u3003\\u303D\\u30FB\\uA4FE-\\uA4FF\\uA60D-\\uA60F\\uA673\\uA67E\\uA6F2-\\uA6F7" +
      "\\uA874-\\uA877\\uA8CE-\\uA8CF\\uA8F8-\\uA8FA\\uA8FC\\uA92E-\\uA92F\\uA95F\\uA9C1-\\uA9CD\\uA9DE-\\uA9DF\\uAA5C-\\uAA5F\\uAADE-\\uAADF\\uAAF0-\\uAAF1\\uABEB" +
      "\\uFE10-\\uFE16\\uFE19\\uFE30\\uFE45-\\uFE46\\uFE49-\\uFE4C\\uFE50-\\uFE52\\uFE54-\\uFE57\\uFE5F-\\uFE61\\uFE68\\uFE6A-\\uFE6B\\uFF01-\\uFF03\\uFF05-\\uFF07" +
      "\\uFF0A\\uFF0C\\uFF0E-\\uFF0F\\uFF1A-\\uFF1B\\uFF1F-\\uFF20\\uFF3C\\uFF61\\uFF64-\\uFF65"),
    "Ps" -> ("\\u0028\\u005B\\u007B\\u0F3A\\u0F3C\\u169B\\u201A\\u201E\\u2045\\u207D\\u208D\\u2308\\u230A\\u2329\\u2768\\u276A\\u276C\\u276E\\u2770\\u2772\\u2774" +
      "\\u27C5\\u27E6\\u27E8\\u27EA\\u27EC\\u27EE\\u2983\\u2985\\u2987\\u2989\\u298B\\u298D\\u298F\\u2991\\u2993\\u2995\\u2997\\u29D8\\u29DA\\u29FC\\u2E22\\u2E24" +
      "\\u2E26\\u2E28\\u2E42\\u2E55\\u2E57\\u2E59\\u2E5B\\u3008\\u300A\\u300C\\u300E\\u3010\\u3014\\u3016\\u3018\\u301A\\u301D\\uFD3F\\uFE17\\uFE35\\uFE37\\uFE39" +
      "\\uFE3B\\uFE3D\\uFE3F\\uFE41\\uFE43\\uFE47\\uFE59\\uFE5B\\uFE5D\\uFF08\\uFF3B\\uFF5B\\uFF5F\\uFF62"),
    "Zs" -> "\\u0020\\u00A0\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000",
    "IsAlphabetic" -> UnicodeAlphabetic.ranges
  )

  /** the `\uXXXX` spelling above, for an engine that reads it (Scala.js) */
  val JavaEscapes: String => String = identity

  /** the `\x{XXXX}` spelling RE2 reads (Scala Native), which has no `\uXXXX` */
  val Re2Escapes: String => String = ranges => ranges.replace("\\u", "\\x{").replaceAll("\\{([0-9A-F]{4})", "{$1}")

  /** `\p{Xx}` for a known category becomes its ranges, spelled by `escapes`: spliced into a surrounding class, or a class of its own. Other `\p` forms are left as written. */
  def expandUnicodeCategories(regex: String, escapes: String => String = JavaEscapes): String = {
    val out    = new java.lang.StringBuilder(regex.length + 64)
    var i      = 0
    var quoted = false
    var depth  = 0
    while (i < regex.length) {
      val c = regex.charAt(i)
      if (quoted) {
        if (c == '\\' && regex.startsWith("\\E", i)) {
          quoted = false
          out.append("\\E")
          i += 2
        } else {
          out.append(c)
          i += 1
        }
      } else if (c == '\\' && i + 1 < regex.length) {
        val next = regex.charAt(i + 1)
        if (next == 'Q') {
          quoted = true
          out.append("\\Q")
          i += 2
        } else if (next == 'p' && i + 2 < regex.length && regex.charAt(i + 2) == '{') {
          val close = regex.indexOf('}', i + 3)
          val name  = if (close < 0) "" else regex.substring(i + 3, close)
          categories.get(name) match {
            case Some(ranges) =>
              val spelled = escapes(ranges)
              if (depth > 0) out.append(spelled) else out.append('[').append(spelled).append(']')
              i = close + 1
            case None =>
              out.append(c).append(next)
              i += 2
          }
        } else {
          out.append(c).append(next)
          i += 2
        }
      } else {
        if (c == '[') depth += 1
        else if (c == ']' && depth > 0) depth -= 1
        out.append(c)
        i += 1
      }
    }
    out.toString
  }

  /** `(?i:…)` becomes `(?:…)` with each ASCII letter in it folded — `a` to `[aA]`, and a class gaining the other case of its ASCII letters and letter ranges — which is what java's flag does without
    * UNICODE_CASE.
    */
  def foldScopedCaseInsensitive(regex: String): String = {
    val marker = "(?i:"
    var start  = indexOfUnescaped(regex, marker, 0)
    if (start < 0) {
      regex
    } else {
      val out  = new java.lang.StringBuilder(regex.length + 64)
      var from = 0
      while (start >= 0) {
        val close = closingParen(regex, start)
        out.append(regex, from, start).append("(?:").append(foldBody(regex.substring(start + marker.length, close))).append(')')
        from = close + 1
        start = indexOfUnescaped(regex, marker, from)
      }
      out.append(regex, from, regex.length)
      out.toString
    }
  }

  private def indexOfUnescaped(regex: String, marker: String, from: Int): Int = {
    var i      = from
    var quoted = false
    var depth  = 0
    var found  = -1
    while (i < regex.length && found < 0) {
      val c = regex.charAt(i)
      if (quoted) {
        if (c == '\\' && regex.startsWith("\\E", i)) { quoted = false; i += 1 }
      } else if (c == '\\') {
        if (i + 1 < regex.length && regex.charAt(i + 1) == 'Q') quoted = true
        i += 1
      } else if (c == '[') depth += 1
      else if (c == ']' && depth > 0) depth -= 1
      else if (depth == 0 && regex.startsWith(marker, i)) found = i
      i += 1
    }
    found
  }

  /** the top-level alternatives of a group body, split at `|` outside any group, class or quoting */
  def splitAlternatives(body: String): List[String] = {
    val out    = List.newBuilder[String]
    var i      = 0
    var from   = 0
    var quoted = false
    var depth  = 0
    var level  = 0
    while (i < body.length) {
      val c = body.charAt(i)
      if (quoted) {
        if (c == '\\' && body.startsWith("\\E", i)) { quoted = false; i += 1 }
      } else if (c == '\\') {
        if (i + 1 < body.length && body.charAt(i + 1) == 'Q') quoted = true
        i += 1
      } else if (c == '[') depth += 1
      else if (c == ']' && depth > 0) depth -= 1
      else if (depth == 0 && c == '(') level += 1
      else if (depth == 0 && c == ')') level -= 1
      else if (depth == 0 && level == 0 && c == '|') {
        out += body.substring(from, i)
        from = i + 1
      }
      i += 1
    }
    out += body.substring(from)
    out.result()
  }

  /** the index of the `)` closing the group opened at `open`, over escapes, quoting and classes */
  def closingParen(regex: String, open: Int): Int = {
    var i      = open + 1
    var quoted = false
    var depth  = 0
    var level  = 1
    var found  = -1
    while (i < regex.length && found < 0) {
      val c = regex.charAt(i)
      if (quoted) {
        if (c == '\\' && regex.startsWith("\\E", i)) { quoted = false; i += 1 }
      } else if (c == '\\') {
        if (i + 1 < regex.length && regex.charAt(i + 1) == 'Q') quoted = true
        i += 1
      } else if (c == '[') depth += 1
      else if (c == ']' && depth > 0) depth -= 1
      else if (depth == 0 && c == '(') level += 1
      else if (depth == 0 && c == ')') {
        level -= 1
        if (level == 0) found = i
      }
      i += 1
    }
    if (found < 0) throw new java.util.regex.PatternSyntaxException("Unclosed group", regex, open) else found
  }

  private def isAsciiLetter(c: Char): Boolean = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')

  private def otherCase(c: Char): Char = if (c >= 'a' && c <= 'z') (c - 32).toChar else (c + 32).toChar

  private def foldBody(body: String): String = {
    val out    = new java.lang.StringBuilder(body.length * 2)
    var i      = 0
    var quoted = false
    while (i < body.length) {
      val c = body.charAt(i)
      if (quoted) {
        if (c == '\\' && body.startsWith("\\E", i)) { quoted = false; out.append("\\E"); i += 2 }
        else if (isAsciiLetter(c)) { out.append("\\E[").append(c).append(otherCase(c)).append("]\\Q"); i += 1 }
        else { out.append(c); i += 1 }
      } else if (c == '\\' && i + 1 < body.length) {
        val next = body.charAt(i + 1)
        if (next == 'Q') quoted = true
        out.append(c).append(next)
        i += 2
      } else if (c == '[') {
        val end = classEnd(body, i)
        out.append(foldClass(body.substring(i, end + 1)))
        i = end + 1
      } else if (isAsciiLetter(c)) {
        out.append('[').append(c).append(otherCase(c)).append(']')
        i += 1
      } else {
        out.append(c)
        i += 1
      }
    }
    out.toString
  }

  /** the index of the `]` closing the class opened at `open`; a class may nest in java */
  def classEnd(body: String, open: Int): Int = {
    var i     = open + 1
    var depth = 1
    var found = -1
    while (i < body.length && found < 0) {
      val c = body.charAt(i)
      if (c == '\\') i += 1
      else if (c == '[') depth += 1
      else if (c == ']') {
        depth -= 1
        if (depth == 0) found = i
      }
      i += 1
    }
    if (found < 0) throw new java.util.regex.PatternSyntaxException("Unclosed character class", body, open) else found
  }

  /** a class with the other case of every ASCII letter and letter range appended; escapes and non-ASCII pass through */
  private def foldClass(cls: String): String = {
    val inner = cls.substring(1, cls.length - 1)
    val extra = new java.lang.StringBuilder
    var i     = 0
    while (i < inner.length) {
      val c = inner.charAt(i)
      if (c == '\\') {
        i += 2
      } else if (isAsciiLetter(c) && i + 2 < inner.length && inner.charAt(i + 1) == '-' && inner.charAt(i + 2) != ']' && inner.charAt(i + 2) != '\\') {
        val to = inner.charAt(i + 2)
        if (isAsciiLetter(to)) {
          extra.append(otherCase(c)).append('-').append(otherCase(to))
        }
        i += 3
      } else {
        if (isAsciiLetter(c)) extra.append(otherCase(c))
        i += 1
      }
    }
    cls.substring(0, cls.length - 1) + extra.toString + "]"
  }
}
