/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/SpecExampleRendererBase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package test
package util

import ssg.md.Nullable
import ssg.md.test.util.spec.SpecExample
import ssg.md.util.data.{ DataHolder, DataSet }

import scala.language.implicitConversions

abstract class SpecExampleRendererBase(
  protected val myExample:            SpecExample,
  exampleOptions:                     Nullable[DataHolder],
  protected val myIncludeExampleInfo: Boolean
) extends SpecExampleRenderer {

  def this(example: SpecExample, options: Nullable[DataHolder]) = {
    this(example, options, true)
  }

  protected val myOptions:    DataHolder       = exampleOptions.fold(new DataSet().asInstanceOf[DataHolder])(_.toImmutable)
  private var myIsFinalized:  Boolean          = false
  private var myRenderedHtml: Nullable[String] = Nullable.empty
  private var myRenderedAst:  Nullable[String] = Nullable.empty

  def isFinalized: Boolean = myIsFinalized

  final override def getHtml: String = {
    if (myRenderedHtml.isEmpty || !isFinalized) {
      myRenderedHtml = Nullable(renderHtml())
    }
    myRenderedHtml.get
  }

  final override def getAst: Nullable[String] = {
    if (myRenderedAst.isEmpty || !isFinalized) {
      myRenderedAst = Nullable(renderAst())
    }
    myRenderedAst
  }

  protected def renderHtml(): String

  protected def renderAst(): String

  override def finalizeRender(): Unit =
    myIsFinalized = true

  override def includeExampleInfo: Boolean = myIncludeExampleInfo

  override def example: SpecExample = myExample

  override def options: Nullable[DataHolder] = Nullable(myOptions.toImmutable)
}
