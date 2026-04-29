/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-admonition/src/main/java/com/vladsch/flexmark/ext/admonition/internal/AdmonitionCollectingVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-admonition/src/main/java/com/vladsch/flexmark/ext/admonition/internal/AdmonitionCollectingVisitor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package admonition
package internal

import ssg.md.util.ast.{ Node, NodeVisitor, VisitHandler }

import java.util.LinkedHashSet
import scala.language.implicitConversions

class AdmonitionCollectingVisitor {

  private var qualifiers: LinkedHashSet[String] = new LinkedHashSet[String]()
  private val myVisitor:  NodeVisitor           = new NodeVisitor(
    new VisitHandler[AdmonitionBlock](classOf[AdmonitionBlock], node => visit(node))
  )

  def getQualifiers: LinkedHashSet[String] = qualifiers

  def collect(node: Node): Unit = {
    qualifiers = new LinkedHashSet[String]()
    myVisitor.visit(node)
  }

  def collectAndGetQualifiers(node: Node): java.util.Set[String] = {
    collect(node)
    qualifiers
  }

  private def visit(node: AdmonitionBlock): Unit =
    qualifiers.add(node.info.toString)
}
