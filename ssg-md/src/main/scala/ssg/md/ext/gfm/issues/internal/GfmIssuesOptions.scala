/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-issues/src/main/java/com/vladsch/flexmark/ext/gfm/issues/internal/GfmIssuesOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-gfm-issues/src/main/java/com/vladsch/flexmark/ext/gfm/issues/internal/GfmIssuesOptions.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package gfm
package issues
package internal

import ssg.md.util.data.{ DataHolder, MutableDataHolder, MutableDataSetter }

import scala.language.implicitConversions

class GfmIssuesOptions(options: DataHolder) extends MutableDataSetter {
  val gitHubIssuesUrlRoot:   String = GfmIssuesExtension.GIT_HUB_ISSUES_URL_ROOT.get(options)
  val gitHubIssueUrlPrefix:  String = GfmIssuesExtension.GIT_HUB_ISSUE_URL_PREFIX.get(options)
  val gitHubIssueUrlSuffix:  String = GfmIssuesExtension.GIT_HUB_ISSUE_URL_SUFFIX.get(options)
  val gitHubIssueTextPrefix: String = GfmIssuesExtension.GIT_HUB_ISSUE_HTML_PREFIX.get(options)
  val gitHubIssueTextSuffix: String = GfmIssuesExtension.GIT_HUB_ISSUE_HTML_SUFFIX.get(options)

  override def setIn(dataHolder: MutableDataHolder): MutableDataHolder = {
    dataHolder.set(GfmIssuesExtension.GIT_HUB_ISSUES_URL_ROOT, gitHubIssuesUrlRoot)
    dataHolder.set(GfmIssuesExtension.GIT_HUB_ISSUE_URL_PREFIX, gitHubIssueUrlPrefix)
    dataHolder.set(GfmIssuesExtension.GIT_HUB_ISSUE_URL_SUFFIX, gitHubIssueUrlSuffix)
    dataHolder.set(GfmIssuesExtension.GIT_HUB_ISSUE_HTML_PREFIX, gitHubIssueTextPrefix)
    dataHolder.set(GfmIssuesExtension.GIT_HUB_ISSUE_HTML_SUFFIX, gitHubIssueTextSuffix)
    dataHolder
  }
}
