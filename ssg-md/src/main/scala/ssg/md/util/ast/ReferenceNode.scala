/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/ReferenceNode.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package ast

import ssg.md.Nullable

trait ReferenceNode[R <: NodeRepository[B], B <: Node, N <: Node] extends Comparable[B] {
  def referencingNode(node: Node): Nullable[N]
}
