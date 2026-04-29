/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/HtmlIdGenerator.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/HtmlIdGenerator.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package html
package renderer

import ssg.md.ast.util.AnchorRefTargetBlockPreVisitor
import ssg.md.util.ast.{ Document, Node }

trait HtmlIdGenerator {
  def generateIds(document: Document): Unit

  def generateIds(document: Document, preVisitor: Nullable[AnchorRefTargetBlockPreVisitor]): Unit =
    generateIds(document)

  def getId(node: Node):         Nullable[String]
  def getId(text: CharSequence): Nullable[String]
}

object HtmlIdGenerator {
  val NULL: HtmlIdGenerator = new HtmlIdGenerator {
    override def generateIds(document: Document): Unit = {}

    override def generateIds(document: Document, preVisitor: Nullable[AnchorRefTargetBlockPreVisitor]): Unit = {}

    override def getId(node: Node): Nullable[String] = Nullable.empty

    override def getId(text: CharSequence): Nullable[String] = Nullable.empty
  }
}
