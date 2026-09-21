/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: src/main/java/liqp/filters/Absolute_Url.java, whose convertUnicodeURLToAscii
 *   hands the URL's authority to java.net.IDN.toASCII
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: the Scala.js and Scala Native rows, whose java libraries ship no java.net.IDN.
 *   Difference: labels are Punycode-encoded (RFC 3492) as java does, but java's nameprep step
 *     (case folding and normalisation of a non-ASCII label, RFC 3491) is not applied.
 */
package ssg
package liquid
package filters

/** The ASCII form of an internationalised authority. */
object Idn {

  def toASCII(input: String): String = Punycode.toAscii(input)
}
