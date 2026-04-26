/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/NodeTracker.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/NodeTracker.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package ast

trait NodeTracker {
  def nodeAdded(node:                Node): Unit
  def nodeAddedWithChildren(node:    Node): Unit
  def nodeAddedWithDescendants(node: Node): Unit

  def nodeRemoved(node:                Node): Unit
  def nodeRemovedWithChildren(node:    Node): Unit
  def nodeRemovedWithDescendants(node: Node): Unit
}
