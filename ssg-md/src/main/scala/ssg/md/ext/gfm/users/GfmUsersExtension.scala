/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-users/src/main/java/com/vladsch/flexmark/ext/gfm/users/GfmUsersExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-gfm-users/src/main/java/com/vladsch/flexmark/ext/gfm/users/GfmUsersExtension.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package gfm
package users

import ssg.md.ext.gfm.users.internal.{ GfmUsersInlineParserExtension, GfmUsersNodeRenderer }
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.{ DataKey, MutableDataHolder }

/** Extension for GitHub Users.
  *
  * Create it with [[GfmUsersExtension.create]] and then configure it on the builders.
  *
  * The parsed GitHub user text is turned into [[GfmUser]] nodes.
  */
class GfmUsersExtension private () extends Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(parserBuilder: Parser.Builder): Unit =
    parserBuilder.customInlineParserExtensionFactory(new GfmUsersInlineParserExtension.Factory())

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new GfmUsersNodeRenderer.Factory())
    }
    // Skipping JIRA renderer per conversion rules
  }
}

object GfmUsersExtension {
  val GIT_HUB_USERS_URL_ROOT:   DataKey[String] = new DataKey[String]("GIT_HUB_USERS_URL_ROOT", "https://github.com")
  val GIT_HUB_USER_URL_PREFIX:  DataKey[String] = new DataKey[String]("GIT_HUB_USER_URL_PREFIX", "/")
  val GIT_HUB_USER_URL_SUFFIX:  DataKey[String] = new DataKey[String]("GIT_HUB_USER_URL_SUFFIX", "")
  val GIT_HUB_USER_HTML_PREFIX: DataKey[String] = new DataKey[String]("GIT_HUB_USER_HTML_PREFIX", "<strong>")
  val GIT_HUB_USER_HTML_SUFFIX: DataKey[String] = new DataKey[String]("GIT_HUB_USER_HTML_SUFFIX", "</strong>")

  def create(): GfmUsersExtension = new GfmUsersExtension()
}
