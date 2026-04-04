/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-users/src/main/java/com/vladsch/flexmark/ext/gfm/users/internal/GfmUsersOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gfm
package users
package internal

import ssg.md.util.data.{ DataHolder, MutableDataHolder, MutableDataSetter }

import scala.language.implicitConversions

class GfmUsersOptions(options: DataHolder) extends MutableDataSetter {
  val gitHubIssuesUrlRoot:  String = GfmUsersExtension.GIT_HUB_USERS_URL_ROOT.get(options)
  val gitHubIssueUrlPrefix: String = GfmUsersExtension.GIT_HUB_USER_URL_PREFIX.get(options)
  val gitHubIssueUrlSuffix: String = GfmUsersExtension.GIT_HUB_USER_URL_SUFFIX.get(options)
  val gitHubUserTextPrefix: String = GfmUsersExtension.GIT_HUB_USER_HTML_PREFIX.get(options)
  val gitHubUserTextSuffix: String = GfmUsersExtension.GIT_HUB_USER_HTML_SUFFIX.get(options)

  override def setIn(dataHolder: MutableDataHolder): MutableDataHolder = {
    dataHolder.set(GfmUsersExtension.GIT_HUB_USERS_URL_ROOT, gitHubIssuesUrlRoot)
    dataHolder.set(GfmUsersExtension.GIT_HUB_USER_URL_PREFIX, gitHubIssueUrlPrefix)
    dataHolder.set(GfmUsersExtension.GIT_HUB_USER_URL_SUFFIX, gitHubIssueUrlSuffix)
    dataHolder.set(GfmUsersExtension.GIT_HUB_USER_HTML_PREFIX, gitHubUserTextPrefix)
    dataHolder.set(GfmUsersExtension.GIT_HUB_USER_HTML_SUFFIX, gitHubUserTextSuffix)
    dataHolder
  }
}
