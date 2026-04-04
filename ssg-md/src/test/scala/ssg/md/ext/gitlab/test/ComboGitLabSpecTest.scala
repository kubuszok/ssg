/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gitlab/.../ComboGitLabSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gitlab
package test

import ssg.md.Nullable
import ssg.md.ext.gitlab.GitLabExtension
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Collections, HashMap }
import scala.language.implicitConversions

final class ComboGitLabSpecTest extends RendererSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboGitLabSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboGitLabSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboGitLabSpecTest.OPTIONS_MAP
}

object ComboGitLabSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/gitlab/test/ext_gitlab_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboGitLabSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(HtmlRenderer.RENDER_HEADER_ID, true).set(Parser.EXTENSIONS, Collections.singletonList(GitLabExtension.create())).toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("no-del", new MutableDataSet().set(GitLabExtension.DEL_PARSER, false).toImmutable)
    map.put("no-ins", new MutableDataSet().set(GitLabExtension.INS_PARSER, false).toImmutable)
    map.put("no-quotes", new MutableDataSet().set(GitLabExtension.BLOCK_QUOTE_PARSER, false).toImmutable)
    map.put("no-math", new MutableDataSet().set(GitLabExtension.RENDER_BLOCK_MATH, false).toImmutable)
    map.put("no-mermaid", new MutableDataSet().set(GitLabExtension.RENDER_BLOCK_MERMAID, false).toImmutable)
    map.put("no-video", new MutableDataSet().set(GitLabExtension.RENDER_VIDEO_IMAGES, false).toImmutable)
    map.put("no-video-link", new MutableDataSet().set(GitLabExtension.RENDER_VIDEO_LINK, false).toImmutable)
    map.put("no-nested-quotes", new MutableDataSet().set(GitLabExtension.NESTED_BLOCK_QUOTES, false).toImmutable)
    map.put("block-delimiters", new MutableDataSet().set(HtmlRenderer.FENCED_CODE_LANGUAGE_DELIMITERS, "-").toImmutable)
    map.put("math-class", new MutableDataSet().set(GitLabExtension.BLOCK_MATH_CLASS, "math-class").toImmutable)
    map.put("math-latex", new MutableDataSet().set(GitLabExtension.MATH_LANGUAGES, Array("math", "latex")).toImmutable)
    map.put("mermaid-class", new MutableDataSet().set(GitLabExtension.BLOCK_MERMAID_CLASS, "mermaid-class").toImmutable)
    map.put("mermaid-alias", new MutableDataSet().set(GitLabExtension.MERMAID_LANGUAGES, Array("mermaid", "alias")).toImmutable)
    map.put("code-content-block", new MutableDataSet().set(Parser.FENCED_CODE_CONTENT_BLOCK, true).toImmutable)
    map.put("video-extensions", new MutableDataSet().set(GitLabExtension.VIDEO_IMAGE_EXTENSIONS, "tst").toImmutable)
    map.put(
      "video-link-format",
      new MutableDataSet().set(GitLabExtension.VIDEO_IMAGE_LINK_TEXT_FORMAT, "Get Video '%s'").toImmutable
    )
    map.put("video-class", new MutableDataSet().set(GitLabExtension.VIDEO_IMAGE_CLASS, "video-class").toImmutable)
    map
  }
}
