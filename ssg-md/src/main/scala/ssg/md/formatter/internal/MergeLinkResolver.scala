/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/internal/MergeLinkResolver.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package formatter
package internal

import ssg.md.ast.{ Image, Link, Reference }
import ssg.md.html.{ IndependentLinkResolverFactory, LinkResolver }
import ssg.md.html.renderer.{ LinkResolverBasicContext, LinkStatus, ResolvedLink }
import ssg.md.util.ast.Node
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

class MergeLinkResolver(context: LinkResolverBasicContext) extends LinkResolver {
  private val docRelativeURL: String        = Formatter.DOC_RELATIVE_URL.get(Nullable(context.getOptions))
  private val docRootURL:     String        = Formatter.DOC_ROOT_URL.get(Nullable(context.getOptions))
  private val relativeParts:  Array[String] = {
    val cleaned = if (docRelativeURL.startsWith("/")) docRelativeURL.substring(1) else docRelativeURL
    cleaned.split("/")
  }

  override def resolveLink(node: Node, context: LinkResolverBasicContext, link: ResolvedLink): ResolvedLink =
    if (node.isInstanceOf[Image] || node.isInstanceOf[Link] || node.isInstanceOf[Reference]) {
      val url = link.url

      if (docRelativeURL.isEmpty && docRootURL.isEmpty) {
        link.withStatus(LinkStatus.VALID).withUrl(url)
      } else if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("ftp://") || url.startsWith("sftp://")) {
        link.withStatus(LinkStatus.VALID).withUrl(url)
      } else if (url.startsWith("file:/")) {
        link.withStatus(LinkStatus.VALID).withUrl(url)
      } else if (url.startsWith("/")) {
        if (docRootURL.nonEmpty) {
          val prefix = if (!docRootURL.startsWith("/")) "/" else ""
          link.withStatus(LinkStatus.VALID).withUrl(prefix + docRootURL + url)
        } else {
          link
        }
      } else if (!url.matches("^(?:[a-z]+:|#|\\?)")) {
        // relative URL processing
        var pageRef = url
        var suffix  = ""
        val hashPos = url.indexOf('#')
        if (hashPos == 0) {
          link.withStatus(LinkStatus.VALID)
        } else {
          if (hashPos > 0) {
            suffix = url.substring(hashPos)
            pageRef = url.substring(0, hashPos)
          } else if (url.contains("?")) {
            val qPos = url.indexOf("?")
            suffix = url.substring(qPos)
            pageRef = url.substring(0, qPos)
          }

          val pathParts = pageRef.split("/")
          var docParts  = relativeParts.length
          val resolved  = new StringBuilder()
          var sep       = ""

          boundary {
            for (part <- pathParts)
              if (part == ".") {
                // skip
              } else if (part == "..") {
                if (docParts == 0) break(link)
                docParts -= 1
              } else {
                resolved.append(sep)
                resolved.append(part)
                sep = "/"
              }
          }

          // prefix with remaining docParts
          val resolvedPath = new StringBuilder()
          sep = if (docRelativeURL.startsWith("/")) "/" else ""
          var i = 0
          while (i < docParts) {
            resolvedPath.append(sep)
            resolvedPath.append(relativeParts(i))
            sep = "/"
            i += 1
          }

          resolvedPath.append('/').append(resolved).append(suffix)
          link.withStatus(LinkStatus.VALID).withUrl(resolvedPath.toString)
        }
      } else {
        link
      }
    } else {
      link
    }
}

object MergeLinkResolver {
  class Factory extends IndependentLinkResolverFactory {
    override def apply(context: LinkResolverBasicContext): LinkResolver =
      new MergeLinkResolver(context)
  }
}
