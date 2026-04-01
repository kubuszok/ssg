/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-issues/src/main/java/com/vladsch/flexmark/ext/gfm/issues/GfmIssuesExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gfm
package issues

import ssg.md.ext.gfm.issues.internal.{GfmIssuesInlineParserExtension, GfmIssuesNodeRenderer}
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.util.data.{DataKey, MutableDataHolder}

/**
 * Extension for GitHub Issues.
 *
 * Create it with [[GfmIssuesExtension.create]] and then configure it on the builders.
 *
 * The parsed GitHub issue text is turned into [[GfmIssue]] nodes.
 */
class GfmIssuesExtension private () extends Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(parserBuilder: Parser.Builder): Unit = {
    parserBuilder.customInlineParserExtensionFactory(new GfmIssuesInlineParserExtension.Factory())
  }

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (htmlRendererBuilder.isRendererType("HTML")) {
      htmlRendererBuilder.nodeRendererFactory(new GfmIssuesNodeRenderer.Factory())
    }
    // Skipping JIRA renderer per conversion rules
  }
}

object GfmIssuesExtension {
  val GIT_HUB_ISSUES_URL_ROOT: DataKey[String] = new DataKey[String]("GIT_HUB_ISSUES_URL_ROOT", "issues")
  val GIT_HUB_ISSUE_URL_PREFIX: DataKey[String] = new DataKey[String]("GIT_HUB_ISSUE_URL_PREFIX", "/")
  val GIT_HUB_ISSUE_URL_SUFFIX: DataKey[String] = new DataKey[String]("GIT_HUB_ISSUE_URL_SUFFIX", "")
  val GIT_HUB_ISSUE_HTML_PREFIX: DataKey[String] = new DataKey[String]("GIT_HUB_ISSUE_HTML_PREFIX", "")
  val GIT_HUB_ISSUE_HTML_SUFFIX: DataKey[String] = new DataKey[String]("GIT_HUB_ISSUE_HTML_SUFFIX", "")

  def create(): GfmIssuesExtension = new GfmIssuesExtension()
}
