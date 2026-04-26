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
 *   Idiom: URI normalization with query/anchor split, error handling
 *   Audited: 2026-04-10 — ISS-099 fixed: full URI handling ported from Java
 *
 * Covenant: full-port
 * Covenant-java-reference: liqp/src/main/java/liqp/filters/Relative_Url.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package liquid
package filters

import ssg.liquid.parser.{ Inspectable, LiquidSupport }

import java.net.{ URI, URISyntaxException }
import java.util.{ Collections, Map => JMap }

/** Converts a path to a relative URL using the site's baseurl.
  *
  * This filter requires a `baseurl` parameter (from `site.baseurl`) that will be used as base for building the relative URL. Handles URI normalization, query/anchor splitting, and error modes.
  *
  * Jekyll-specific filter.
  */
class Relative_Url extends Filter() {

  override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any = {
    val valAsString = asString(value, context)

    // fast exit for valid absolute urls
    if (isValidAbsoluteUrl(valAsString)) {
      valAsString
    } else {
      val siteMap = objectToMap(context.get(Relative_Url.site), context)
      val baseUrl = asString(siteMap.get(Relative_Url.baseurl), context)
      getRelativeUrl(context, baseUrl, valAsString)
    }
  }

  protected def getRelativeUrl(context: TemplateContext, baseUrl: String, valAsString0: String): String = {
    var valAsString = valAsString0
    if (!valAsString.startsWith("/")) {
      valAsString = "/" + valAsString
    }
    val baseUrlString0 = asString(baseUrl, context)
    if (baseUrlString0.isEmpty) {
      valAsString
    } else {
      var baseUrlString = baseUrlString0
      if (!baseUrlString.startsWith("/")) {
        baseUrlString = "/" + baseUrlString
      }
      var res =
        if ("/" == valAsString) {
          if ("/" == baseUrlString) "/"
          else baseUrlString
        } else {
          baseUrlString + valAsString
        }
      try {
        var query:  String = null // scalastyle:ignore null
        var anchor: String = null // scalastyle:ignore null
        val anchorParts = res.split("#", 2)
        if (anchorParts.length > 1) {
          anchor = anchorParts(1)
          res = anchorParts(0)
        }
        val parts = res.split("\\?", 2)
        val path  =
          if (parts.length > 1) {
            query = parts(1)
            parts(0)
          } else {
            res
          }
        val uri           = new URI(null, null, null, -1, path, query, anchor) // scalastyle:ignore null
        var afterDecoding = uri.normalize().toASCIIString
        if (afterDecoding.isEmpty) {
          afterDecoding = "/"
        }
        afterDecoding
      } catch {
        case e: URISyntaxException =>
          context.addError(e)
          if (context.getErrorMode == TemplateParser.ErrorMode.STRICT) {
            throw new RuntimeException(e.getMessage, e)
          }
          res
      }
    }
  }

  protected def objectToMap(configRoot0: Any, context: TemplateContext): JMap[String, Any] = {
    var configRoot = configRoot0
    configRoot match {
      case _: Inspectable =>
        val evaluated: LiquidSupport = context.parser.evaluate(configRoot)
        configRoot = evaluated.toLiquid()
      case _ =>
    }
    if (isMap(configRoot)) {
      asMap(configRoot)
    } else {
      Collections.emptyMap()
    }
  }

  protected def isValidAbsoluteUrl(valAsString: String): Boolean =
    try {
      val uri = new URI(valAsString)
      uri.getScheme != null
    } catch {
      case _: URISyntaxException => false
    }
}

object Relative_Url {
  val site:    String = "site"
  val baseurl: String = "baseurl"
}
