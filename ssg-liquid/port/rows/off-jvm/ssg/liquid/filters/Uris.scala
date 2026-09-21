/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: src/main/java/liqp/filters/Absolute_Url.java and Relative_Url.java, which
 *   call java.net.URI.normalize()
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: the Scala.js and Scala Native rows. java's normalize removes `.` and `..` segments AND
 *     collapses a run of slashes in the path; Scala Native's does only the first, so the second
 *     is restated here over the normalised URI.
 */
package ssg
package liquid
package filters

/** A URI with its path normalised the way java normalises it. */
object Uris {

  def normalize(uri: java.net.URI): java.net.URI = {
    val normalised = uri.normalize()
    val path       = normalised.getRawPath()
    if (path == null || !path.contains("//")) normalised
    else {
      val text = new java.lang.StringBuilder()
      if (normalised.getScheme() != null) text.append(normalised.getScheme()).append(':')
      if (normalised.getRawAuthority() != null) text.append("//").append(normalised.getRawAuthority())
      text.append(path.replaceAll("/+", "/"))
      if (normalised.getRawQuery() != null) text.append('?').append(normalised.getRawQuery())
      if (normalised.getRawFragment() != null) text.append('#').append(normalised.getRawFragment())
      new java.net.URI(text.toString())
    }
  }
}
