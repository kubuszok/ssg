/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/AnchorRefTarget.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/AnchorRefTarget.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ast

trait AnchorRefTarget {
  def anchorRefText:                      String
  def anchorRefId:                        String
  def anchorRefId_=(anchorRefId: String): Unit

  def isExplicitAnchorRefId:                 Boolean
  def explicitAnchorRefId_=(value: Boolean): Unit
}
