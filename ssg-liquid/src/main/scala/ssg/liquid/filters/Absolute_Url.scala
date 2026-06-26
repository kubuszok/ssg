/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Absolute_Url.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters → ssg.liquid.filters
 *   Convention: Jekyll-specific URL filter, extends Relative_Url
 *   Idiom: URI normalization with pure-Scala Punycode (RFC 3492) for IDN hosts
 *   Audited: 2026-04-10 — ISS-099 fixed: full URI handling ported from Java
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Absolute_Url.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import ssg.data.DataView

import java.net.URI

/** Converts a path to an absolute URL using the site's url and baseurl.
  *
  * Jekyll-specific filter. Extends [[Relative_Url]] to inherit `objectToMap`, `getRelativeUrl`, and `isValidAbsoluteUrl`.
  */
class Absolute_Url extends Relative_Url {

  override def apply(value: DataView, context: TemplateContext, params: Array[DataView]): DataView = {
    val valAsString = asString(value, context)
    if (isValidAbsoluteUrl(valAsString)) {
      DataView.from(valAsString)
    } else {
      val configRoot  = context.get(Relative_Url.site)
      val siteMap     = dvToMap(configRoot)
      val baseUrl     = siteMap.get(Relative_Url.baseurl).map(dv => asString(dv, context)).getOrElse("")
      val siteConfig  = siteMap.getOrElse(Absolute_Url.config, DataView.nil)
      val configs     = dvToMap(siteConfig)
      val siteUrl     = configs.get(Absolute_Url.url).map(dv => asString(dv, context)).getOrElse("")
      val relativeUrl = getRelativeUrl(context, baseUrl, valAsString)
      if ("" == siteUrl) {
        DataView.from(relativeUrl)
      } else {
        var res =
          if ((siteUrl != null && siteUrl.endsWith("/")) && "/" == relativeUrl) siteUrl
          else siteUrl + relativeUrl
        try {
          // punicode java bug work around
          // IDN.toASCII not works if string start with scheme....
          res = Absolute_Url.convertUnicodeURLToAscii(res)
          if (valAsString.endsWith("/") && !res.endsWith("/")) {
            res = res + "/"
          }
          DataView.from(res)
        } catch {
          case e: Exception =>
            context.addError(e)
            if (context.getErrorMode == TemplateParser.ErrorMode.STRICT) {
              throw new RuntimeException(e)
            }
            DataView.from(res)
        }
      }
    }
  }
}

object Absolute_Url {
  val config: String = "config"
  val url:    String = "url"

  /** Converts a Unicode URL to ASCII using URI normalization.
    *
    * Host IDN labels are Punycode-encoded via the pure-Scala [[Punycode]] object (RFC 3492), a cross-platform replacement for `java.net.IDN.toASCII`.
    */
  def convertUnicodeURLToAscii(url0: String): String =
    if (url0 != null) {
      val url           = url0.trim
      var uri           = new URI(url)
      var includeScheme = true

      // URI needs a scheme to work properly with authority parsing
      if (uri.getScheme == null) {
        uri = new URI("http://" + url)
        includeScheme = false
      }

      val scheme      = if (uri.getScheme != null) uri.getScheme + "://" else ""
      val authority   = if (uri.getRawAuthority != null) uri.getRawAuthority else ""
      val path        = if (uri.getRawPath != null) uri.getRawPath else ""
      val queryString = if (uri.getRawQuery != null) "?" + uri.getRawQuery else ""
      val fragment    = if (uri.getRawFragment != null) "#" + uri.getRawFragment else ""

      // Must convert domain to punycode separately from the path
      // see https://gist.github.com/msangel/f2224f72d386db3580ce18e5ef01bcc3
      val asciiAuthority = punycodeAuthority(authority)
      val assembled      = (if (includeScheme) scheme else "") + asciiAuthority + collapseSlashes(path) + queryString + fragment

      // Convert path from unicode to ascii encoding
      new URI(assembled).normalize().toASCIIString
    } else {
      url0
    }

  /** Punycode-encode the host portion of a URI authority string.
    *
    * Handles `userinfo@host:port` by splitting off the optional userinfo prefix and port suffix, applying [[Punycode.toAscii]] only to the host, then reassembling. Mirrors `java.net.IDN.toASCII`
    * applied to the full authority.
    */
  private def punycodeAuthority(authority: String): String =
    if (authority.isEmpty) authority
    else {
      // Split off optional userinfo@ prefix
      val atIdx    = authority.indexOf('@')
      val userinfo = if (atIdx >= 0) authority.substring(0, atIdx + 1) else ""
      val hostPort = if (atIdx >= 0) authority.substring(atIdx + 1) else authority

      // Split off optional :port suffix (find last colon not inside IPv6 brackets)
      val bracketEnd = hostPort.lastIndexOf(']')
      val colonIdx   = hostPort.lastIndexOf(':')
      val hasPort    = colonIdx > bracketEnd
      val host       = if (hasPort) hostPort.substring(0, colonIdx) else hostPort
      val port       = if (hasPort) hostPort.substring(colonIdx) else ""

      userinfo + Punycode.toAscii(host) + port
    }

  /** Collapse runs of consecutive '/' in a URI path to a single '/', mirroring the empty-segment removal that java.net.URI.normalize() performs on the JVM but which Scala Native's normalize() omits.
    * Idempotent: single-slash paths are fixed points, so this is a no-op on JVM/JS where normalize() already collapsed. Operates on raw (un-percent-decoded) path text; '%XX' sequences contain no '/'
    * so they are never touched, and literal '/' separators stay literal.
    */
  def collapseSlashes(path: String): String =
    if (path.indexOf("//") < 0) path
    else {
      val sb        = new StringBuilder(path.length)
      var prevSlash = false
      var i         = 0
      while (i < path.length) {
        val c       = path.charAt(i)
        val isSlash = c == '/'
        if (!(isSlash && prevSlash)) sb.append(c)
        prevSlash = isSlash
        i += 1
      }
      sb.toString
    }
}
