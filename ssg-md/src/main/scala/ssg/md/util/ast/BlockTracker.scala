/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/BlockTracker.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/BlockTracker.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package ast

trait BlockTracker {
  def blockAdded(node:                Block): Unit
  def blockAddedWithChildren(node:    Block): Unit
  def blockAddedWithDescendants(node: Block): Unit

  def blockRemoved(node:                Block): Unit
  def blockRemovedWithChildren(node:    Block): Unit
  def blockRemovedWithDescendants(node: Block): Unit
}
