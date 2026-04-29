/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-issues/.../ComboGfmIssuesSpecTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package ext
package gfm
package issues
package test

import ssg.md.Nullable
import ssg.md.ext.gfm.issues.GfmIssuesExtension
import ssg.md.parser.Parser
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Collections, HashMap }
import scala.language.implicitConversions

final class ComboGfmIssuesSpecTest extends RendererSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboGfmIssuesSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboGfmIssuesSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboGfmIssuesSpecTest.OPTIONS_MAP
}

object ComboGfmIssuesSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/gfm/issues/test/gfm_issues_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboGfmIssuesSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(GfmIssuesExtension.create())).toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put(
      "root",
      new MutableDataSet().set(GfmIssuesExtension.GIT_HUB_ISSUES_URL_ROOT, "https://github.com/vsch/flexmark-java/issues").toImmutable
    )
    map.put("prefix", new MutableDataSet().set(GfmIssuesExtension.GIT_HUB_ISSUE_URL_PREFIX, "?issue=").toImmutable)
    map.put("suffix", new MutableDataSet().set(GfmIssuesExtension.GIT_HUB_ISSUE_URL_SUFFIX, "&").toImmutable)
    map.put(
      "bold",
      new MutableDataSet().set(GfmIssuesExtension.GIT_HUB_ISSUE_HTML_PREFIX, "<strong>").set(GfmIssuesExtension.GIT_HUB_ISSUE_HTML_SUFFIX, "</strong>").toImmutable
    )
    map
  }
}
