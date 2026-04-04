/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Relative_Url.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.filters → ssg.liquid.filters
 *   Convention: Jekyll-specific URL filter
 */
package ssg
package liquid
package filters

import java.util.{ Map => JMap }

/** Converts a path to a relative URL using the site's baseurl.
  *
  * Jekyll-specific filter.
  */
class Relative_Url extends Filter("relative_url") {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    val valAsString = asString(value, context)

    // fast exit for valid absolute urls
    if (Relative_Url.isValidAbsoluteUrl(valAsString)) {
      valAsString
    } else {
      val site    = context.get("site")
      val siteMap = Relative_Url.objectToMap(site, context)
      val baseUrl = if (siteMap != null) asString(siteMap.get("baseurl"), context) else ""
      Relative_Url.getRelativeUrl(baseUrl, valAsString)
    }
  }
}

object Relative_Url {

  def isValidAbsoluteUrl(url: String): Boolean =
    url != null && (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("//"))

  def getRelativeUrl(baseUrl: String, path: String): String = {
    val effectiveBase = if (baseUrl == null || baseUrl.isEmpty) "" else baseUrl
    val effectivePath = if (path == null || path.isEmpty) "/" else path

    if (effectivePath.startsWith("/")) {
      effectiveBase + effectivePath
    } else {
      effectiveBase + "/" + effectivePath
    }
  }

  def objectToMap(obj: Any, context: TemplateContext): JMap[String, Any] =
    obj match {
      case map: JMap[?, ?] => map.asInstanceOf[JMap[String, Any]]
      case _ => null
    }
}
