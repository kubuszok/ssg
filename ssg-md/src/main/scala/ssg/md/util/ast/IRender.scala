/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/IRender.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/IRender.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package ast

import ssg.md.Nullable
import ssg.md.util.data.DataHolder

/** Render interface for rendering implementation for RenderingTestCase
  */
trait IRender {
  // CAUTION: the reason this is not a Document, which it always is in practice is for tests which generate
  //    a fake NODE and generating a fake Document (unless made into an interface and without extras) would be too difficult
  def render(document: Node, output: Appendable): Unit

  /** Render the tree of nodes to HTML.
    *
    * @param document
    *   the root node
    * @return
    *   the rendered HTML
    */
  def render(document: Node): String = {
    val sb = new java.lang.StringBuilder
    render(document, sb)
    sb.toString
  }

  /** Get Options for parsing
    *
    * @return
    *   DataHolder for options
    */
  def options: Nullable[DataHolder]
}
