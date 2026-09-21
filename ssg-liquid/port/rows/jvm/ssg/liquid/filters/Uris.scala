/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: src/main/java/liqp/filters/Absolute_Url.java and Relative_Url.java, which
 *   call java.net.URI.normalize()
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: the JVM row. This is java's own call and java's answer.
 */
package ssg
package liquid
package filters

/** A URI with its path normalised the way java normalises it. */
object Uris {

  def normalize(uri: java.net.URI): java.net.URI = uri.normalize()
}
