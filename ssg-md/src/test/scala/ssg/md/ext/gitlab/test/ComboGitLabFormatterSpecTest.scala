/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gitlab/.../ComboGitLabFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package gitlab
package test

import ssg.md.Nullable
import ssg.md.ext.gitlab.GitLabExtension
import ssg.md.parser.Parser
import ssg.md.test.util.FormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class ComboGitLabFormatterSpecTest extends FormatterSpecTestSuite {
  override def specResource:         ResourceLocation     = ComboGitLabFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions:       Nullable[DataHolder] = Nullable(ComboGitLabFormatterSpecTest.OPTIONS)
  override def knownFailurePrefixes: Set[String]          = Set("Block Quotes -", "Fenced Code Math -", "Fenced Code Mermaid -", "Inline -", "Inline Math -", "Video Images -")
}

object ComboGitLabFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/gitlab/test/ext_gitlab_formatter_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboGitLabFormatterSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(GitLabExtension.create())).set(Parser.LISTS_AUTO_LOOSE, false).toImmutable
}
