/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/clone_css.dart
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: clone_css.dart -> CloneCssVisitor.scala
 *   Convention: Skeleton — full deep clone implementation deferred to the
 *               serializer/evaluator phase
 */
package ssg
package sass
package visitor

import ssg.sass.ast.css.*

/** A visitor that produces a deep copy of a CSS AST tree.
  *
  * Skeleton — concrete clone logic will be added alongside the modifiable
  * CSS tree implementation. For now this trait is a no-op base that returns
  * the input nodes unchanged.
  */
trait CloneCssVisitor extends CssVisitor[CssNode] {
  def visitCssAtRule(node: CssAtRule): CssNode             = node
  def visitCssComment(node: CssComment): CssNode           = node
  def visitCssDeclaration(node: CssDeclaration): CssNode   = node
  def visitCssImport(node: CssImport): CssNode             = node
  def visitCssKeyframeBlock(node: CssKeyframeBlock): CssNode = node
  def visitCssMediaRule(node: CssMediaRule): CssNode       = node
  def visitCssStyleRule(node: CssStyleRule): CssNode       = node
  def visitCssStylesheet(node: CssStylesheet): CssNode     = node
  def visitCssSupportsRule(node: CssSupportsRule): CssNode = node
}
