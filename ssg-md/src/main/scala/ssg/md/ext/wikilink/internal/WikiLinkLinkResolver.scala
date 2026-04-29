/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-wikilink/src/main/java/com/vladsch/flexmark/ext/wikilink/internal/WikiLinkLinkResolver.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-wikilink/src/main/java/com/vladsch/flexmark/ext/wikilink/internal/WikiLinkLinkResolver.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package wikilink
package internal

import ssg.md.Nullable
import ssg.md.html.{ LinkResolver, LinkResolverFactory }
import ssg.md.html.renderer.{ LinkResolverBasicContext, LinkStatus, LinkType, ResolvedLink }
import ssg.md.util.ast.Node

import scala.language.implicitConversions

class WikiLinkLinkResolver(context: LinkResolverBasicContext) extends LinkResolver {

  private val options: WikiLinkOptions = new WikiLinkOptions(context.getOptions)

  override def resolveLink(node: Node, context: LinkResolverBasicContext, link: ResolvedLink): ResolvedLink =
    if (link.linkType == WikiLinkExtension.WIKI_LINK) {
      val sb          = new StringBuilder()
      val isWikiImage = node.isInstanceOf[WikiImage]
      val wikiLink    = link.url
      val iMax        = wikiLink.length
      val absolute    = iMax > 0 && wikiLink.charAt(0) == '/'
      sb.append(if (isWikiImage) options.getImagePrefix(absolute) else options.getLinkPrefix(absolute))

      var hadAnchorRef = false
      var isEscaped    = false

      val linkEscapeChars  = options.linkEscapeChars
      val linkReplaceChars = options.linkReplaceChars
      var i                = if (absolute) 1 else 0
      while (i < iMax) {
        val c = wikiLink.charAt(i)

        if (c == '#' && !hadAnchorRef && options.allowAnchors && !(isEscaped && options.allowAnchorEscape)) {
          sb.append(if (isWikiImage) options.imageFileExtension else options.linkFileExtension)
          sb.append(c)
          hadAnchorRef = true
          isEscaped = false
        } else if (c == '\\') {
          if (isEscaped) {
            // need to URL encode \
            sb.append("%5C")
          }
          isEscaped = !isEscaped
        } else {
          isEscaped = false
          if (c == '#' && !hadAnchorRef) {
            // need to URL encode the #
            sb.append("%23")
          } else {
            val pos = linkEscapeChars.indexOf(c)
            if (pos < 0) {
              sb.append(c)
            } else {
              sb.append(linkReplaceChars.charAt(pos))
            }
          }
        }
        i += 1
      }

      if (isEscaped) {
        // need to add dangling URL encoded \
        sb.append("%5C")
      }

      if (!hadAnchorRef) {
        sb.append(if (isWikiImage) options.imageFileExtension else options.linkFileExtension)
      }

      if (isWikiImage) {
        new ResolvedLink(LinkType.IMAGE, sb.toString, Nullable.empty, LinkStatus.UNCHECKED)
      } else {
        new ResolvedLink(LinkType.LINK, sb.toString, Nullable.empty, LinkStatus.UNCHECKED)
      }
    } else {
      link
    }
}

object WikiLinkLinkResolver {

  class Factory extends LinkResolverFactory {

    override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

    override def affectsGlobalScope: Boolean = false

    override def apply(context: LinkResolverBasicContext): LinkResolver = new WikiLinkLinkResolver(context)
  }
}
