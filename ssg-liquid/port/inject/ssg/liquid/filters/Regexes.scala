/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: src/main/java/liqp/filters/Strip_HTML.java, which compiles its two patterns
 *   with Pattern.MULTILINE
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: Scala.js refuses Pattern.MULTILINE below ECMAScript 2018, and the refusal is raised
 *     from a static initialiser, which takes every template render with it.
 *   Convention: MULTILINE changes what `^` and `$` match and nothing else, so a pattern holding
 *     neither means the same without the flag. That is the only case the flag is dropped in; any
 *     other pattern is compiled exactly as java wrote it.
 */
package ssg
package liquid
package filters

import java.util.regex.Pattern

/** `Pattern.compile(regex, flags)`, without a MULTILINE flag that cannot change the match. */
object Regexes {

  def compile(regex: String, flags: Int): Pattern =
    if ((flags & Pattern.MULTILINE) != 0 && regex.indexOf('^') < 0 && regex.indexOf('$') < 0) {
      val remaining = flags & ~Pattern.MULTILINE
      if (remaining == 0) Pattern.compile(regex) else Pattern.compile(regex, remaining)
    } else {
      Pattern.compile(regex, flags)
    }
}
