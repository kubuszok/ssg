/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/every_css.dart
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: every_css.dart -> EveryCssVisitor.scala
 *   Convention: Skeleton — defaults each visit method to true. Concrete
 *               subclasses override to enforce a predicate.
 */
package ssg
package sass
package visitor

import ssg.sass.ast.css.*

/** A visitor that returns true if every CSS node in the tree matches a predicate.
  *
  * Skeleton — defaults all visit methods to true.
  */
trait EveryCssVisitor extends CssVisitor[Boolean] {
  def visitCssAtRule(node:        CssAtRule):        Boolean = true
  def visitCssComment(node:       CssComment):       Boolean = true
  def visitCssDeclaration(node:   CssDeclaration):   Boolean = true
  def visitCssImport(node:        CssImport):        Boolean = true
  def visitCssKeyframeBlock(node: CssKeyframeBlock): Boolean = true
  def visitCssMediaRule(node:     CssMediaRule):     Boolean = true
  def visitCssStyleRule(node:     CssStyleRule):     Boolean = true
  def visitCssStylesheet(node:    CssStylesheet):    Boolean = true
  def visitCssSupportsRule(node:  CssSupportsRule):  Boolean = true
}
