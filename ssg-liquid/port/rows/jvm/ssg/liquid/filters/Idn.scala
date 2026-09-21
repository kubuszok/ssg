/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: src/main/java/liqp/filters/Absolute_Url.java, whose convertUnicodeURLToAscii
 *   hands the URL's authority to java.net.IDN.toASCII
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: the JVM row. java.net.IDN exists here, so this is java's own call and java's answer.
 */
package ssg
package liquid
package filters

/** The ASCII form of an internationalised authority. */
object Idn {

  def toASCII(input: String): String = java.net.IDN.toASCII(input)
}
