/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/css/keyframe_block.dart
 * Original: Copyright (c) 2016, 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: keyframe_block.dart + modifiable/keyframe_block.dart -> CssKeyframeBlock.scala
 *   Convention: Dart abstract interface class -> Scala trait
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/ast/css/keyframe_block.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package ast
package css

import ssg.sass.util.FileSpan
import ssg.sass.visitor.CssVisitor

/** A block within a `@keyframes` rule.
  *
  * For example, `10% {opacity: 0.5}`.
  */
trait CssKeyframeBlock extends CssParentNode {

  /** The selector for this block. */
  def selector: CssValue[List[String]]
}

/** A modifiable version of CssKeyframeBlock for use in the evaluation step. */
final class ModifiableCssKeyframeBlock(
  val selector: CssValue[List[String]],
  val span:     FileSpan
) extends ModifiableCssParentNode
    with CssKeyframeBlock {

  def accept[T](visitor: CssVisitor[T]): T =
    visitor.visitCssKeyframeBlock(this)

  def equalsIgnoringChildren(other: ModifiableCssNode): Boolean =
    other match {
      case that: ModifiableCssKeyframeBlock => selector.value == that.selector.value
      case _ => false
    }

  def copyWithoutChildren(): ModifiableCssKeyframeBlock =
    ModifiableCssKeyframeBlock(selector, span)
}
