/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-visitor/src/main/java/com/vladsch/flexmark/util/visitor/AstNode.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-visitor/src/main/java/com/vladsch/flexmark/util/visitor/AstNode.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package visitor

import ssg.md.Nullable

/** Interface for converting to AstAccess
  *
  * @tparam N
  *   type of node
  */
trait AstNode[N] {
  def firstChild(node: N): Nullable[N]
  def next(node:       N): Nullable[N]
}
