/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-users/.../ComboGfmUsersSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gfm
package users
package test

import ssg.md.Nullable
import ssg.md.ext.gfm.users.GfmUsersExtension
import ssg.md.parser.Parser
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Collections, HashMap }
import scala.language.implicitConversions

final class ComboGfmUsersSpecTest extends RendererSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboGfmUsersSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboGfmUsersSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboGfmUsersSpecTest.OPTIONS_MAP
}

object ComboGfmUsersSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/gfm/users/test/gfm_users_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboGfmUsersSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(GfmUsersExtension.create())).toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("root", new MutableDataSet().set(GfmUsersExtension.GIT_HUB_USERS_URL_ROOT, "http://github.com").toImmutable)
    map.put("prefix", new MutableDataSet().set(GfmUsersExtension.GIT_HUB_USER_URL_PREFIX, "?user=").toImmutable)
    map.put("suffix", new MutableDataSet().set(GfmUsersExtension.GIT_HUB_USER_URL_SUFFIX, "&").toImmutable)
    map.put(
      "plain",
      new MutableDataSet().set(GfmUsersExtension.GIT_HUB_USER_HTML_PREFIX, "").set(GfmUsersExtension.GIT_HUB_USER_HTML_SUFFIX, "").toImmutable
    )
    map
  }
}
