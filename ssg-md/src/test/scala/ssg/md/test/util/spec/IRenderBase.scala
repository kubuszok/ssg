/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/spec/IRenderBase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package test
package util
package spec

import ssg.md.Nullable
import ssg.md.util.ast.{IRender, Node}
import ssg.md.util.data.DataHolder

import java.io.IOException
import scala.language.implicitConversions

abstract class IRenderBase(val options: Nullable[DataHolder]) extends IRender {

  def this() = this(Nullable.empty)

  override def render(document: Node): String = {
    val out = new StringBuilder()
    render(document, out.underlying)
    out.toString()
  }
}

object IRenderBase {

  val NULL_RENDERER: IRender = new IRenderBase() {
    override def render(document: Node, output: Appendable): Unit = {
      // no-op
    }
  }

  val TEXT_RENDERER: IRender = new IRenderBase() {
    override def render(document: Node, output: Appendable): Unit = {
      try {
        output.append(document.chars)
      } catch {
        case e: IOException =>
          e.printStackTrace()
      }
    }
  }

  @deprecated("Use NULL_RENDERER", "0.1.0")
  val NullRenderer: IRender = NULL_RENDERER

  @deprecated("Use TEXT_RENDERER", "0.1.0")
  val TextRenderer: IRender = TEXT_RENDERER
}
