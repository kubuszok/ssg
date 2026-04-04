/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-wikilink/src/main/java/com/vladsch/flexmark/ext/wikilink/internal/WikiLinkOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package wikilink
package internal

import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class WikiLinkOptions(options: DataHolder) {

  val allowInlines:        Boolean = WikiLinkExtension.ALLOW_INLINES.get(options)
  val allowAnchors:        Boolean = WikiLinkExtension.ALLOW_ANCHORS.get(options)
  val disableRendering:    Boolean = WikiLinkExtension.DISABLE_RENDERING.get(options)
  val imageLinks:          Boolean = WikiLinkExtension.IMAGE_LINKS.get(options)
  val linkFirstSyntax:     Boolean = WikiLinkExtension.LINK_FIRST_SYNTAX.get(options)
  val allowAnchorEscape:   Boolean = WikiLinkExtension.ALLOW_ANCHOR_ESCAPE.get(options)
  val allowPipeEscape:     Boolean = WikiLinkExtension.ALLOW_PIPE_ESCAPE.get(options)
  val imageFileExtension:  String  = WikiLinkExtension.IMAGE_FILE_EXTENSION.get(options)
  val imagePrefix:         String  = WikiLinkExtension.IMAGE_PREFIX.get(options)
  val imagePrefixAbsolute: String  = WikiLinkExtension.IMAGE_PREFIX_ABSOLUTE.get(options)
  val linkFileExtension:   String  = WikiLinkExtension.LINK_FILE_EXTENSION.get(options)
  val linkPrefix:          String  = WikiLinkExtension.LINK_PREFIX.get(options)
  val linkPrefixAbsolute:  String  = WikiLinkExtension.LINK_PREFIX_ABSOLUTE.get(options)
  val linkEscapeChars:     String  = WikiLinkExtension.LINK_ESCAPE_CHARS.get(options)
  val linkReplaceChars:    String  = WikiLinkExtension.LINK_REPLACE_CHARS.get(options)

  def getLinkPrefix(absolute: Boolean): String = if (absolute) linkPrefixAbsolute else linkPrefix

  def getImagePrefix(absolute: Boolean): String = if (absolute) imagePrefixAbsolute else imagePrefix
}
