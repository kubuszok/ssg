/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/filters/Relative_Url.java
 * Original: Copyright (c) 2012 Bart Kiers
 * Original license: MIT
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/filters/Relative_Url.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package filters

import ssg.data.DataView

import java.net.{ URI, URISyntaxException }

import scala.collection.immutable.VectorMap

class Relative_Url extends Filter() {

  override def apply(value: DataView, context: TemplateContext, params: Array[DataView]): DataView = {
    val valAsString = asString(value, context)

    if (isValidAbsoluteUrl(valAsString)) {
      DataView.from(valAsString)
    } else {
      val siteMap = dvToMap(context.get(Relative_Url.site))
      val baseUrl = siteMap.get(Relative_Url.baseurl).map(dv => asString(dv, context)).getOrElse("")
      DataView.from(getRelativeUrl(context, baseUrl, valAsString))
    }
  }

  protected def getRelativeUrl(context: TemplateContext, baseUrl: String, valAsString0: String): String = {
    var valAsString = valAsString0
    if (!valAsString.startsWith("/")) {
      valAsString = "/" + valAsString
    }
    if (baseUrl.isEmpty) {
      valAsString
    } else {
      var baseUrlString = baseUrl
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

  protected def dvToMap(dv: DataView): VectorMap[String, DataView] =
    if (dv.isNull) VectorMap.empty
    else
      dv.view match {
        case m: VectorMap[?, ?] => m.asInstanceOf[VectorMap[String, DataView]]
        case _ => VectorMap.empty
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
