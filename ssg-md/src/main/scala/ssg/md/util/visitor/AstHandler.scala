/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-visitor/src/main/java/com/vladsch/flexmark/util/visitor/AstHandler.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-visitor/src/main/java/com/vladsch/flexmark/util/visitor/AstHandler.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package visitor

/** Base class for tracking generic node to specific node handler
  *
  * @tparam N
  *   node subclass
  * @tparam A
  *   node action
  */
abstract class AstHandler[N, A <: AstAction[? >: N]](val nodeType: Class[? <: N], val adapter: A) {

  override def equals(o: Any): Boolean =
    o match {
      case that: AstHandler[?, ?] =>
        (this eq that) || (nodeType == that.nodeType && (adapter eq that.adapter))
      case _ => false
    }

  override def hashCode(): Int =
    31 * nodeType.hashCode() + adapter.hashCode()
}
