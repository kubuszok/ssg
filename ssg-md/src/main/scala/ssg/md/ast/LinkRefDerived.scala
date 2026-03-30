/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/LinkRefDerived.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast

/** Nodes which are textually derived from LinkRef
  */
trait LinkRefDerived {

  /** @return
    *   true if this node will be rendered as text because it depends on a reference which is not defined.
    */
  def isTentative: Boolean
}
