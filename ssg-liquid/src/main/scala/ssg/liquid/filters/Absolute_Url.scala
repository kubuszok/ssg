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
 *   Convention: Jekyll-specific URL filter
 */
package ssg
package liquid
package filters

/** Converts a path to an absolute URL using the site's url and baseurl.
  *
  * Jekyll-specific filter.
  */
class Absolute_Url extends Filter("absolute_url") {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    val valAsString = asString(value, context)

    if (Relative_Url.isValidAbsoluteUrl(valAsString)) {
      valAsString
    } else {
      val site    = context.get("site")
      val siteMap = Relative_Url.objectToMap(site, context)

      val baseUrl = if (siteMap != null) asString(siteMap.get("baseurl"), context) else ""
      val siteUrl = if (siteMap != null) {
        val config    = siteMap.get("config")
        val configMap = Relative_Url.objectToMap(config, context)
        if (configMap != null) asString(configMap.get("url"), context) else asString(siteMap.get("url"), context)
      } else {
        ""
      }

      val relativeUrl = Relative_Url.getRelativeUrl(baseUrl, valAsString)

      if (siteUrl == null || siteUrl.isEmpty) {
        relativeUrl
      } else {
        if (siteUrl.endsWith("/") && "/" == relativeUrl) siteUrl
        else siteUrl + relativeUrl
      }
    }
  }
}
