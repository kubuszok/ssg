/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/ReferencingNode.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/ReferencingNode.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package ast

import ssg.md.util.sequence.BasedSequence

trait ReferencingNode[R <: NodeRepository[B], B <: ReferenceNode[?, ?, ?]] {
  def isDefined:                              Boolean
  def reference:                              BasedSequence
  def getReferenceNode(document:   Document): B | Null
  def getReferenceNode(repository: R):        B | Null
}
