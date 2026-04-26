/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/interface/css.dart, lib/src/visitor/interface/modifiable_css.dart
 * Original: Copyright (c) 2016, 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: interface/css.dart + interface/modifiable_css.dart -> CssVisitor.scala
 *   Convention: Forward declaration -- methods added as CSS node types are ported
 *   Idiom: ModifiableCssVisitor extends CssVisitor with same signatures
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/visitor/interface/css.dart, lib/src/visitor/interface/modifiable_css.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass
package visitor

import ssg.sass.ast.css.*

/** Visitor interface for plain CSS AST nodes.
  */
trait CssVisitor[T] {
  def visitCssAtRule(node:        CssAtRule):        T
  def visitCssComment(node:       CssComment):       T
  def visitCssDeclaration(node:   CssDeclaration):   T
  def visitCssImport(node:        CssImport):        T
  def visitCssKeyframeBlock(node: CssKeyframeBlock): T
  def visitCssMediaRule(node:     CssMediaRule):     T
  def visitCssStyleRule(node:     CssStyleRule):     T
  def visitCssStylesheet(node:    CssStylesheet):    T
  def visitCssSupportsRule(node:  CssSupportsRule):  T
}

/** Visitor interface for modifiable CSS AST nodes used during evaluation. */
trait ModifiableCssVisitor[T] extends CssVisitor[T]
