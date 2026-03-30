/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/ParagraphContainer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast

trait ParagraphContainer {
  def isParagraphEndWrappingDisabled(node:   Paragraph): Boolean
  def isParagraphStartWrappingDisabled(node: Paragraph): Boolean
}
