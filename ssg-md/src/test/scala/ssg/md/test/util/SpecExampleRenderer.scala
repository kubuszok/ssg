/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/SpecExampleRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package test
package util

import ssg.md.Nullable
import ssg.md.test.util.spec.SpecExample
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

trait SpecExampleRenderer {

  def includeExampleInfo: Boolean

  def options: Nullable[DataHolder]
  def example: SpecExample

  def includeDocument(includedText: String): Unit
  def parse(input: CharSequence): Unit

  // after all parsing is complete gives a chance to handle insertion of included doc
  def finalizeDocument(): Unit

  // after all rendering information is collected, give chance to release resources and reset test settings needed for renderHtml or other functions.
  // after this there will be no more calls to renderer for this iteration
  def finalizeRender(): Unit

  // caches values and does not regenerate
  def getHtml: String
  def getAst: Nullable[String]
}

object SpecExampleRenderer {

  val NULL: SpecExampleRenderer = new SpecExampleRenderer {

    override def includeExampleInfo: Boolean = false

    override def options: Nullable[DataHolder] = Nullable.empty

    override def includeDocument(includedText: String): Unit = {}

    override def parse(input: CharSequence): Unit = {}

    override def example: SpecExample = SpecExample.NULL

    override def finalizeDocument(): Unit = {}

    override def getHtml: String = ""

    override def getAst: Nullable[String] = Nullable.empty

    override def finalizeRender(): Unit = {}
  }
}
