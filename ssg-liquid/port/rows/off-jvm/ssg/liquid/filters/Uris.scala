/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: src/main/java/liqp/filters/Absolute_Url.java and Relative_Url.java, which
 *   call java.net.URI.normalize()
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: the Scala.js and Scala Native rows. java's normalize splits the path into segments,
 *     DISCARDING the empty ones a run of slashes makes, and then removes `.` and `..`. Scala
 *     Native's keeps the empty segments and miscounts around them (`/base///css/main.css` lost
 *     its last segment), so the run is collapsed FIRST — java's own order — and the platform's
 *     normalize only ever sees a path it handles.
 */
package ssg
package liquid
package filters

/** A URI with its path normalised the way java normalises it. */
object Uris {

  def normalize(uri: java.net.URI): java.net.URI = {
    val path = uri.getRawPath()
    if (path == null || !path.contains("//")) uri.normalize()
    else {
      val text = new java.lang.StringBuilder()
      if (uri.getScheme() != null) text.append(uri.getScheme()).append(':')
      if (uri.getRawAuthority() != null) text.append("//").append(uri.getRawAuthority())
      text.append(path.replaceAll("/+", "/"))
      if (uri.getRawQuery() != null) text.append('?').append(uri.getRawQuery())
      if (uri.getRawFragment() != null) text.append('#').append(uri.getRawFragment())
      new java.net.URI(text.toString()).normalize()
    }
  }
}
