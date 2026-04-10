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
 *   Idiom: URI normalization with punycode workaround, error handling
 *   Audited: 2026-04-10 — ISS-099 fixed: full URI handling ported from Java
 */
package ssg
package liquid
package filters

import java.net.URI

/** Converts a path to an absolute URL using the site's url and baseurl.
  *
  * Jekyll-specific filter. Extends [[Relative_Url]] to inherit `objectToMap`, `getRelativeUrl`, and `isValidAbsoluteUrl`.
  */
class Absolute_Url extends Relative_Url {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    val valAsString = asString(value, context)
    if (isValidAbsoluteUrl(valAsString)) {
      valAsString
    } else {
      val configRoot = context.get(Relative_Url.site)
      val siteMap    = objectToMap(configRoot, context)
      val baseUrl    = asString(siteMap.get(Relative_Url.baseurl), context)
      val siteConfig = siteMap.get(Absolute_Url.config)
      val configs    = objectToMap(siteConfig, context)
      val siteUrl    = asString(configs.get(Absolute_Url.url), context)
      val relativeUrl = getRelativeUrl(context, baseUrl, valAsString)
      if ("" == siteUrl) {
        relativeUrl
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
          res
        } catch {
          case e: Exception =>
            context.addError(e)
            if (context.getErrorMode == TemplateParser.ErrorMode.STRICT) {
              throw new RuntimeException(e)
            }
            res
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
    * Note: `java.net.IDN.toASCII` is JVM-only, so we skip punycode conversion and rely on URI normalization which works cross-platform. On JVM, the authority will already be ASCII in most cases.
    */
  def convertUnicodeURLToAscii(url0: String): String = {
    if (url0 != null) {
      val url = url0.trim
      var uri            = new URI(url)
      var includeScheme  = true

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

      // IDN.toASCII is JVM-only; skip punycode conversion for cross-platform compat.
      // Most practical URLs already have ASCII authority.
      val assembled = (if (includeScheme) scheme else "") + authority + path + queryString + fragment

      // Convert path from unicode to ascii encoding
      new URI(assembled).normalize().toASCIIString
    } else {
      url0
    }
  }
}
